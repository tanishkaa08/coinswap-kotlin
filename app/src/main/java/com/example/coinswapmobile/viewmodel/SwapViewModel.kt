package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.MakerOnionProbe
import com.example.coinswapmobile.data.MakerRoutePrefs
import com.example.coinswapmobile.data.SwapRepository
import com.example.coinswapmobile.data.TakerAppConfig
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.model.PreparedSwap
import com.example.coinswapmobile.screens.SwapMaker
import com.example.coinswapmobile.screens.SwapUtxo
import com.example.coinswapmobile.service.SwapExecutionBus
import com.example.coinswapmobile.service.SwapForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SwapUiState(
    val isLoading: Boolean = true,
    val walletSats: Long = 0L,
    /** Confirmed spendable sats available for swap (SeedCoin / SweptCoin). */
    val confirmedSpendableSats: Long = 0L,
    /** Unconfirmed deposit sats — swap waits until these confirm. */
    val pendingSats: Long = 0L,
    val utxos: List<SwapUtxo> = emptyList(),
    val makers: List<SwapMaker> = emptyList(),
    val capabilities: NativeCapabilities? = null,
    val torReachable: Boolean = false,
    val torStatusMessage: String = "",
    val errorMessage: String? = null,
    val swapError: String? = null,
    val isSwapping: Boolean = false,
    val swapPhase: String? = null,
    val preparedSwap: PreparedSwap? = null,
    val lastSwapId: String? = null,
)

class SwapViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val coinswapRepo = CoinswapRepository(appDataDir = FfiEnv.takerDataDir(app))
    private val swapRepo = SwapRepository(coinswapRepo)

    private val _state = MutableStateFlow(
        SwapUiState(capabilities = coinswapRepo.getCapabilities())
    )
    val uiState = _state.asStateFlow()

    private var beginJob: Job? = null
    /** True once native prepare/start has begun — Stop must not cancel that. */
    private var nativeSwapStarted = false

    init {
        loadWalletData()
        viewModelScope.launch {
            SwapExecutionBus.events.collect { event ->
                _state.update {
                    it.copy(
                        isSwapping = event.isRunning,
                        // Clear stale phase when idle so "Stopped" doesn't stick on the form.
                        swapPhase = if (event.isRunning) {
                            event.phase ?: it.swapPhase
                        } else {
                            null
                        },
                        lastSwapId = event.swapId ?: it.lastSwapId,
                        swapError = when {
                            event.failed -> event.errorMessage ?: "Swap failed"
                            event.completed -> null
                            else -> it.swapError
                        },
                        preparedSwap = if (event.completed || event.failed) null else it.preparedSwap,
                    )
                }
                if (event.completed || event.failed) {
                    nativeSwapStarted = false
                    loadWalletData()
                }
            }
        }
    }

    fun loadWalletData() {
        if (_state.value.isSwapping || SwapExecutionBus.active.value) return
        if (!session.isLoggedIn) {
            _state.update { it.copy(isLoading = false, walletSats = 0, utxos = emptyList()) }
            return
        }
        viewModelScope.launch {
            val tor = TorManager.ensureRunning(getApplication())
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    capabilities = coinswapRepo.getCapabilities(),
                    torReachable = tor.reachable,
                    torStatusMessage = tor.message,
                )
            }
            if (!TakerHolder.isInitialized) {
                coinswapRepo.initTaker(
                    session,
                    electrumSocks5 = session.config.electrumSocks5,
                    electrumTimeoutSecs = TakerAppConfig.DEFAULT_ELECTRUM_TIMEOUT_SECS,
                )
                    .onFailure { e ->
                        _state.update {
                            it.copy(isLoading = false, errorMessage = e.message)
                        }
                        return@launch
                    }
            }
            coinswapRepo.getBalance()
                .onSuccess { state ->
                    val utxos = state.utxos
                        .filter { u ->
                            u.spendable &&
                                (u.spendType == null || u.spendType == "SeedCoin" || u.spendType == "SweptCoin")
                        }
                        .map { u ->
                            SwapUtxo(
                                txid = u.txid,
                                vout = u.vout,
                                amountSats = u.amountSats,
                                confirmed = (u.confirmations ?: 0) > 0,
                                spendable = u.spendable,
                                spendType = u.spendType,
                                selected = true,
                            )
                        }
                    val confirmedSpendable = utxos.sumOf { it.amountSats }
                    val pendingSats = utxos.filter { !it.confirmed }.sumOf { it.amountSats }
                    // Prefer the same offerbook Markets just synced; only re-poll
                    // when too few makers are online for a 2-hop route.
                    var makersResult = coinswapRepo.listMakers()
                    val listed = makersResult.getOrNull().orEmpty()
                    if (listed.count { it.online } < 2 && tor.reachable) {
                        withTimeoutOrNull(PRE_SWAP_OFFER_SYNC_MS) {
                            coinswapRepo.syncOfferbook()
                        }
                        makersResult = coinswapRepo.listMakers()
                    }
                    val makersError = makersResult.exceptionOrNull()?.message
                    val makers = makersResult.getOrNull().orEmpty().map { m ->
                        SwapMaker(
                            id = m.id,
                            feeRatePct = m.feeRatePct,
                            minSats = m.minSats,
                            maxSats = m.maxSats,
                            liquiditySats = m.liquiditySats,
                            fidelityBondBtc = m.fidelityBondBtc,
                            onionAddress = m.onionAddress,
                            online = m.online,
                            baseFee = m.baseFee,
                        )
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            walletSats = state.balanceSats,
                            confirmedSpendableSats = confirmedSpendable,
                            pendingSats = pendingSats,
                            utxos = utxos,
                            makers = makers,
                            errorMessage = makersError,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message,
                            walletSats = 0,
                            confirmedSpendableSats = 0,
                            pendingSats = 0,
                            utxos = emptyList(),
                        )
                    }
                }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun beginSwap(
        amountSats: Long,
        makerCount: Int,
        selectedUtxos: List<SwapUtxo>,
        txCount: Int = 1,
        manual: Boolean = false,
        makerIds: List<String> = emptyList(),
        protocol: String = session.config.protocol,
    ) {
        if (_state.value.isSwapping || SwapExecutionBus.active.value) {
            // Already running — keep UI on the progress path instead of no-op.
            _state.update {
                it.copy(
                    isSwapping = true,
                    swapPhase = it.swapPhase ?: "Swap in progress…",
                    swapError = null,
                )
            }
            return
        }
        beginJob?.cancel()
        nativeSwapStarted = false
        beginJob = viewModelScope.launch {
            // Mark running immediately so the progress overlay cannot race back to IDLE
            // while Tor / maker checks are still in flight.
            _state.update {
                it.copy(
                    isSwapping = true,
                    swapError = null,
                    swapPhase = "Starting…",
                    lastSwapId = null,
                    preparedSwap = null,
                )
            }
            SwapExecutionBus.emit(
                SwapExecutionBus.Event(
                    swapId = null,
                    phase = "Starting…",
                    isRunning = true,
                ),
            )
            val tor = TorManager.ensureRunning(getApplication())
            if (!tor.reachable) {
                failSwap(tor.message.ifBlank { "Orbot SOCKS required" }, demote = false)
                return@launch
            }
            // Fresh Orbot circuit before maker probes / PoF (best-effort).
            TorManager.requestNewNym(tor.controlPassword)
            if (!TakerHolder.isInitialized) {
                coinswapRepo.initTaker(
                    session,
                    electrumSocks5 = session.config.electrumSocks5,
                    electrumTimeoutSecs = TakerAppConfig.DEFAULT_ELECTRUM_TIMEOUT_SECS,
                ).onFailure { e ->
                    failSwap(e.message, demote = false)
                    return@launch
                }
            }
            if (manual) {
                if (selectedUtxos.isEmpty()) {
                    failSwap("Select at least one coin", demote = false)
                    return@launch
                }
                val pools = selectedUtxos.map { poolOf(it.spendType) }.toSet()
                if (pools.size > 1) {
                    failSwap("Cannot mix regular and swap coins", demote = false)
                    return@launch
                }
            }
            if (amountSats < CoinswapRepository.MIN_SWAP_SATS) {
                failSwap(
                    "Minimum swap is ${CoinswapRepository.MIN_SWAP_SATS} sats",
                    demote = false,
                )
                return@launch
            }
            _state.update { it.copy(swapPhase = "Syncing…") }
            SwapExecutionBus.emit(
                SwapExecutionBus.Event(
                    swapId = null,
                    phase = "Syncing…",
                    isRunning = true,
                ),
            )
            coinswapRepo.syncWallet()
                .onFailure { e ->
                    failSwap(e.message, demote = false)
                    return@launch
                }
            val funded = coinswapRepo.getBalance().getOrNull()
            val regularPool = funded?.utxos.orEmpty()
                .filter { u ->
                    u.spendable &&
                        (u.spendType == null || u.spendType == "SeedCoin")
                }
                .sumOf { it.amountSats }
            val swapPool = funded?.utxos.orEmpty()
                .filter { u ->
                    u.spendable && u.spendType == "SweptCoin"
                }
                .sumOf { it.amountSats }
            val bestPool = maxOf(regularPool, swapPool)
            if (!CoinswapRepository.poolCanFundSwap(bestPool, amountSats)) {
                failSwap(
                    "Need ${CoinswapRepository.SWAP_PREPARE_RESERVE_SATS} extra sats for fees",
                    demote = false,
                )
                return@launch
            }
            if (manual) {
                val freshUtxos = funded?.utxos.orEmpty()
                val invalid = selectedUtxos.filter { sel ->
                    freshUtxos.none {
                        it.txid == sel.txid && it.vout == sel.vout && it.spendable
                    }
                }
                if (invalid.isNotEmpty()) {
                    failSwap("Selected coins are locked or unconfirmed", demote = false)
                    return@launch
                }
            }
            _state.update { it.copy(swapPhase = "Checking makers…") }
            val appCtx = getApplication<Application>()
            fun eligible(makers: List<com.example.coinswapmobile.model.MakerUiModel>) =
                makers.filter { m ->
                    CoinswapRepository.makerFitsAmount(
                        online = m.online,
                        minSats = m.minSats,
                        maxSats = m.maxSats,
                        liquiditySats = m.liquiditySats,
                        amountSats = amountSats,
                    )
                }

            var makers = coinswapRepo.listMakers().getOrElse { e ->
                failSwap(e.message, demote = false)
                return@launch
            }
            if (eligible(makers).size < makerCount) {
                val offerSync = withTimeoutOrNull(PRE_SWAP_OFFER_SYNC_MS) {
                    coinswapRepo.syncOfferbook()
                }
                if (offerSync == null) {
                    _state.update { it.copy(swapPhase = "Using maker cache…") }
                } else if (offerSync.isFailure) {
                    failSwap(
                        offerSync.exceptionOrNull()?.message
                            ?: "Could not reach makers over Tor",
                        demote = false,
                    )
                    return@launch
                }
                makers = coinswapRepo.listMakers().getOrElse { e ->
                    failSwap(e.message, demote = false)
                    return@launch
                }
            }
            if (eligible(makers).size < makerCount) {
                val online = makers.count { it.online }
                val fit = eligible(makers).size
                failSwap(
                    "Need $makerCount makers ($fit fit / $online online)",
                    demote = false,
                )
                return@launch
            }

            _state.update { it.copy(swapPhase = "Probing makers…") }
            val eligibleList = eligible(makers)
            val fidelity = eligibleList.associate { m ->
                (MakerRoutePrefs.normalize(m.onionAddress) ?: m.onionAddress) to m.fidelityBondBtc
            }
            val candidates = MakerRoutePrefs.orderPreferred(
                context = appCtx,
                onions = eligibleList.map { it.onionAddress },
                needed = (makerCount + 4).coerceAtLeast(5),
                fidelityByOnion = fidelity,
            )
            // Prefer SOCKS-reachable onions, but never shrink below the online pool —
            // a flaky probe must not block swaps when Markets already shows makers.
            val reachable = MakerOnionProbe.reachableOnions(candidates)
            val routePool = if (reachable.size >= makerCount) reachable else candidates
            val preferred = MakerRoutePrefs.orderPreferred(
                context = appCtx,
                onions = routePool,
                needed = (makerCount + 3).coerceAtLeast(4),
                fidelityByOnion = fidelity,
            )
            if (preferred.size < makerCount) {
                failSwap(
                    "Only ${preferred.size} makers available (need $makerCount)",
                    demote = false,
                )
                return@launch
            }
            android.util.Log.i(
                "SwapViewModel",
                "Route hops+spares (${preferred.size}): ${preferred.joinToString()}",
            )

            _state.update { it.copy(swapPhase = "Preparing…") }
            nativeSwapStarted = true
            swapRepo.prepareCoinswap(
                amountSats = amountSats,
                makerCount = makerCount,
                selectedUtxos = selectedUtxos,
                txCount = txCount,
                makerIds = preferred,
                protocol = protocol,
                manualSelection = manual,
            )
                .onSuccess { prepared ->
                    _state.update {
                        it.copy(
                            preparedSwap = prepared,
                            lastSwapId = prepared.swapId,
                            swapPhase = "Executing coinswap…",
                            isSwapping = true,
                        )
                    }
                    SwapForegroundService.start(getApplication(), prepared)
                }
                .onFailure { e ->
                    nativeSwapStarted = false
                    failSwap(e.message, demote = false)
                }
        }
    }

    /** Cancel only before native prepare/start. Aborting mid-protocol locks coins. */
    fun cancelPreparingSwap() {
        if (nativeSwapStarted || _state.value.preparedSwap != null) return
        beginJob?.cancel()
        failSwap("Swap cancelled", demote = false)
    }

    /**
     * @param demote unused. Makers are never banned by the app.
     */
    private fun failSwap(message: String?, demote: Boolean = false) {
        @Suppress("UNUSED_PARAMETER")
        demote
        nativeSwapStarted = false
        SwapExecutionBus.emit(
            SwapExecutionBus.Event(
                swapId = null,
                phase = null,
                isRunning = false,
                failed = true,
                errorMessage = message,
            ),
        )
        _state.update { it.copy(isSwapping = false, swapError = message, swapPhase = null) }
    }

    fun clearSwapResult() {
        _state.update { it.copy(swapError = null, swapPhase = null, lastSwapId = null) }
    }

    private fun poolOf(spendType: String?): String =
        if (spendType == null || spendType == "SeedCoin") "regular" else "swap"

    private companion object {
        const val PRE_SWAP_OFFER_SYNC_MS = 120_000L
    }
}
