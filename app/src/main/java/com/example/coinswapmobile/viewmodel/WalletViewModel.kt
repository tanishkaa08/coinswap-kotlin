package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.UtxoUiModel
import com.example.coinswapmobile.model.WalletState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletUiState(
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val electrumUrl: String = "",
    val walletName: String = "",
    val backendLabel: String = "",
    val nativeStatus: String = "",
    val balanceSats: Long = 0,
    val confirmedSats: Long = 0,
    val unconfirmedSats: Long = 0,
    val receiveAddress: String? = null,
    val utxos: List<UtxoUiModel> = emptyList(),
    val error: String? = null,
)

class WalletViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val repo = CoinswapRepository(appDataDir = app.filesDir.absolutePath)

    private val _uiState = MutableStateFlow(
        WalletUiState(
            electrumUrl = session.electrumUrl,
            walletName = session.walletName,
            nativeStatus = repo.nativeStatus,
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        if (session.isLoggedIn) {
            connectWallet()
        }
    }

    /** Connect / reconnect using the user's saved Electrum server. */
    fun connectWallet() {
        if (!session.isLoggedIn) return
        val url = session.electrumUrl
        val name = session.walletName
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    electrumUrl = url,
                    walletName = name,
                    nativeStatus = repo.nativeStatus,
                )
            }
            repo.initElectrum(url, name)
                .onSuccess { state ->
                    _uiState.update {
                        it.applyState(state, url, name)
                            .copy(isLoading = false, isInitialized = true, nativeStatus = repo.nativeStatus)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isInitialized = false,
                            electrumUrl = url,
                            walletName = name,
                            nativeStatus = repo.nativeStatus,
                            error = e.message,
                        )
                    }
                }
        }
    }

    fun syncWallet() {
        if (!session.isLoggedIn) return
        if (!_uiState.value.isInitialized) {
            connectWallet()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.syncWallet()
                .onSuccess { state ->
                    _uiState.update {
                        it.applyState(state, session.electrumUrl, session.walletName)
                            .copy(isLoading = false)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun generateReceiveAddress() {
        if (!session.isLoggedIn || !_uiState.value.isInitialized) {
            _uiState.update { it.copy(error = "Connect to your Electrum server first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getNewAddress()
                .onSuccess { addr -> _uiState.update { it.copy(isLoading = false, receiveAddress = addr) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun WalletUiState.applyState(
        state: WalletState,
        electrumUrl: String,
        walletName: String,
    ): WalletUiState = copy(
        balanceSats = state.balanceSats,
        confirmedSats = state.confirmedSats,
        unconfirmedSats = state.unconfirmedSats,
        electrumUrl = electrumUrl,
        walletName = walletName,
        backendLabel = buildBackendLabel(state, electrumUrl),
        utxos = state.utxos,
    )

    private fun buildBackendLabel(state: WalletState, electrumUrl: String): String {
        val host = electrumUrl.removePrefix("ssl://").removePrefix("tcp://").substringBefore("/")
        val height = state.blockHeight?.let { " • block $it" } ?: ""
        return "${state.backend} • $host$height"
    }
}
