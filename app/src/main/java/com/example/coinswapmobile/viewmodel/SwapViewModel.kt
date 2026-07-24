package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.SwapRepository
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.model.PreparedSwap
import com.example.coinswapmobile.screens.SwapMaker
import com.example.coinswapmobile.screens.SwapUtxo
import com.example.coinswapmobile.service.SwapExecutionBus
import com.example.coinswapmobile.service.SwapForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SwapUiState(
    val isLoading: Boolean = true,
    val walletSats: Long = 0L,
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

    init {
        loadWalletData()
        viewModelScope.launch {
            SwapExecutionBus.events.collect { event ->
                _state.update {
                    it.copy(
                        isSwapping = event.isRunning,
                        swapPhase = event.phase ?: it.swapPhase,
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
                    loadWalletData()
                }
            }
        }
    }

    fun loadWalletData() {
        if (!session.isLoggedIn) {
            _state.update { it.copy(isLoading = false, walletSats = 0, utxos = emptyList()) }
            return
        }
        viewModelScope.launch {
            val tor = TorManager.checkSocks()
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
                coinswapRepo.initTaker(session)
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
                            u.spendable && (u.spendType == "SeedCoin" || u.spendType == "SweptCoin")
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
                    val makersResult = coinswapRepo.listMakers()
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
                            utxos = utxos,
                            makers = makers,
                            errorMessage = makersError,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, errorMessage = e.message, walletSats = 0, utxos = emptyList())
                    }
                }
        }
    }

    fun beginSwap(
        amountSats: Long,
        makerCount: Int,
        selectedUtxos: List<SwapUtxo>,
        txCount: Int = 1,
        manual: Boolean = false,
        makerIds: List<String> = emptyList(),
        protocol: String = session.config.protocol,
    ) {
        viewModelScope.launch {
            val tor = TorManager.checkSocks()
            if (!tor.reachable) {
                _state.update {
                    it.copy(swapError = "Tor SOCKS required for swaps: ${tor.message}")
                }
                return@launch
            }
            if (!TakerHolder.isInitialized) {
                coinswapRepo.initTaker(session).onFailure { e ->
                    _state.update { it.copy(swapError = e.message) }
                    return@launch
                }
            }
            if (manual) {
                if (selectedUtxos.isEmpty()) {
                    _state.update { it.copy(swapError = "Select at least one coin") }
                    return@launch
                }
                val pools = selectedUtxos.map { poolOf(it.spendType) }.toSet()
                if (pools.size > 1) {
                    _state.update {
                        it.copy(swapError = "Cannot mix regular and swap coins")
                    }
                    return@launch
                }
            }
            _state.update { it.copy(isSwapping = true, swapError = null, swapPhase = "Syncing wallet…") }
            SwapExecutionBus.emit(
                SwapExecutionBus.Event(
                    swapId = null,
                    phase = "Syncing wallet…",
                    isRunning = true,
                ),
            )
            coinswapRepo.syncWallet()
                .onFailure { e ->
                    failSwap(e.message)
                    return@launch
                }
            if (manual) {
                val balance = coinswapRepo.getBalance()
                if (balance.isFailure) {
                    failSwap(balance.exceptionOrNull()?.message)
                    return@launch
                }
                val freshUtxos = balance.getOrNull()?.utxos.orEmpty()
                val invalid = selectedUtxos.filter { sel ->
                    freshUtxos.none { it.txid == sel.txid && it.vout == sel.vout && it.spendable }
                }
                if (invalid.isNotEmpty()) {
                    failSwap("Selected coins are locked")
                    return@launch
                }
            }
            _state.update { it.copy(swapPhase = "Checking makers…") }
            // Fresh Tor poll so we don't start against stale "online" makers.
            val offerSync = withTimeoutOrNull(PRE_SWAP_OFFER_SYNC_MS) {
                coinswapRepo.syncOfferbook()
            }
            if (offerSync == null) {
                _state.update { it.copy(swapPhase = "Maker check timed out — using cache…") }
            } else if (offerSync.isFailure) {
                failSwap(
                    offerSync.exceptionOrNull()?.message
                        ?: "Could not reach makers over Tor",
                )
                return@launch
            }

            val makers = coinswapRepo.listMakers().getOrElse { e ->
                failSwap(e.message)
                return@launch
            }
            val online = makers.filter { it.online }
            val preferred = if (makerIds.isNotEmpty()) {
                makerIds.filter { id ->
                    online.any { it.onionAddress == id || it.id == id }
                }
            } else {
                online.take(makerCount).map { it.onionAddress }
            }
            if (preferred.size < makerCount) {
                failSwap(
                    "Only ${preferred.size} reachable maker(s); need $makerCount. Sync Markets and retry with 1 maker.",
                )
                return@launch
            }

            _state.update { it.copy(swapPhase = "Preparing…") }
            swapRepo.prepareCoinswap(
                amountSats = amountSats,
                makerCount = makerCount,
                selectedUtxos = selectedUtxos,
                txCount = txCount,
                makerIds = preferred.take(makerCount),
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
                    failSwap(e.message)
                }
        }
    }

    private fun failSwap(message: String?) {
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
