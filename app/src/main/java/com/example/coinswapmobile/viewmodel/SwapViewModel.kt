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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init { loadWalletData() }

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
                    // SeedCoin / SweptCoin only (IncomingSwapCoin is unsweepable here)
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
                    val makers = coinswapRepo.listMakers().getOrNull().orEmpty().map { m ->
                        SwapMaker(
                            id = m.id,
                            feeRatePct = m.feeRatePct,
                            minSats = m.minSats,
                            maxSats = m.maxSats,
                            liquiditySats = m.liquiditySats,
                            fidelityBondBtc = m.fidelityBondBtc,
                            onionAddress = m.onionAddress,
                            online = m.online,
                        )
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            walletSats = state.balanceSats,
                            utxos = utxos,
                            makers = makers,
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
            // Manual mode: require a selection from a single pool
            if (manual) {
                if (selectedUtxos.isEmpty()) {
                    _state.update { it.copy(swapError = "Select at least one coin") }
                    return@launch
                }
                val pools = selectedUtxos.map { poolOf(it.spendType) }.toSet()
                if (pools.size > 1) {
                    _state.update {
                        it.copy(swapError = "Cannot mix regular and swap coins. Use one pool.")
                    }
                    return@launch
                }
            }
            _state.update { it.copy(isSwapping = true, swapError = null, swapPhase = "Syncing wallet…") }
            coinswapRepo.syncWallet()
                .onFailure { e ->
                    _state.update { it.copy(isSwapping = false, swapError = e.message) }
                    return@launch
                }
            if (manual) {
                val freshUtxos = coinswapRepo.getBalance().getOrNull()?.utxos.orEmpty()
                val invalid = selectedUtxos.filter { sel ->
                    freshUtxos.none { it.txid == sel.txid && it.vout == sel.vout && it.spendable }
                }
                if (invalid.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            isSwapping = false,
                            swapError = "Selected coins are locked or tied to a previous swap. Sync and pick different coins.",
                        )
                    }
                    return@launch
                }
            }
            _state.update { it.copy(swapPhase = "Syncing offerbook…") }
            coinswapRepo.syncOfferbook()
            _state.update { it.copy(swapPhase = "Preparing…") }
            swapRepo.prepareCoinswap(
                amountSats = amountSats,
                makerCount = makerCount,
                selectedUtxos = selectedUtxos,
                txCount = txCount,
                makerIds = makerIds,
                protocol = protocol,
                manualSelection = manual,
            )
                .onSuccess { prepared ->
                    _state.update {
                        it.copy(preparedSwap = prepared, swapPhase = "Executing coinswap…")
                    }
                    swapRepo.startCoinswap(prepared)
                        .onSuccess { report ->
                            _state.update {
                                it.copy(
                                    isSwapping = false,
                                    lastSwapId = report.id,
                                    swapPhase = "Completed: ${report.status}",
                                    preparedSwap = null,
                                )
                            }
                            loadWalletData()
                        }
                        .onFailure { e ->
                            _state.update {
                                it.copy(
                                    isSwapping = false,
                                    swapError = e.message,
                                    swapPhase = "Failed during execution",
                                )
                            }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSwapping = false, swapError = e.message, swapPhase = null) }
                }
        }
    }

    fun clearSwapResult() {
        _state.update { it.copy(swapError = null, swapPhase = null, lastSwapId = null) }
    }

    /** SeedCoin -> regular; otherwise swap pool. */
    private fun poolOf(spendType: String?): String =
        if (spendType == null || spendType == "SeedCoin") "regular" else "swap"
}
