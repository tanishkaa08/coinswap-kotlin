package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.screens.SwapMaker
import com.example.coinswapmobile.screens.SwapReport
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
    val errorMessage: String? = null,
    val swapResult: SwapReport? = null,
    val swapError: String? = null,
    val isSwapping: Boolean = false,
    val currentSwapId: String? = null,
)

class SwapViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val walletRepo = CoinswapRepository(appDataDir = app.filesDir.absolutePath)

    private val _state = MutableStateFlow(SwapUiState())
    val uiState = _state.asStateFlow()

    init { loadWalletData() }

    fun loadWalletData() {
        if (!session.isLoggedIn) {
            _state.update { it.copy(isLoading = false, walletSats = 0, utxos = emptyList(), makers = emptyList()) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            walletRepo.getBalance()
                .onSuccess { state ->
                    val utxos = state.utxos.map { u ->
                        SwapUtxo(
                            txid = u.txid,
                            amountSats = u.amountSats,
                            confirmed = (u.confirmations ?: 0) > 0,
                            selected = true,
                        )
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            walletSats = state.balanceSats,
                            utxos = utxos,
                            makers = emptyList(),
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
        feeRateSatPerVb: Int,
        selectedUtxos: List<SwapUtxo>,
    ) {
        // TODO: startSwap() — full coinswap flow not wired in this build.
        _state.update { it.copy(swapError = "Swap execution is not available in this build yet.") }
    }

    fun clearSwapResult() {
        _state.update { it.copy(swapResult = null, swapError = null) }
    }
}
