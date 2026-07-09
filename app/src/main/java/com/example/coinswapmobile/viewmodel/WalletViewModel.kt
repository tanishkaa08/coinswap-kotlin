package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.model.UtxoUiModel
import com.example.coinswapmobile.model.WalletState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletUiState(
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val rpcLabel: String = "",
    val walletName: String = "",
    val zmqLabel: String = "",
    val backendLabel: String = "",
    val libraryLoadStatus: String = "",
    val capabilities: NativeCapabilities? = null,
    val balanceSats: Long = 0,
    val confirmedSats: Long = 0,
    val unconfirmedSats: Long = 0,
    val regularSats: Long = 0,
    val swapSats: Long = 0,
    val contractSats: Long = 0,
    val fidelitySats: Long = 0,
    val receiveAddress: String? = null,
    val utxos: List<UtxoUiModel> = emptyList(),
    val torReachable: Boolean = false,
    val torStatusMessage: String = "",
    val error: String? = null,
)

class WalletViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val repo = CoinswapRepository(appDataDir = FfiEnv.takerDataDir(app))

    private val _uiState = MutableStateFlow(
        WalletUiState(
            walletName = session.walletName,
            libraryLoadStatus = repo.libraryLoadStatus,
            capabilities = repo.getCapabilities(),
            backendLabel = session.config.rpcUrl,
            isInitialized = TakerHolder.isInitialized,
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        if (session.isLoggedIn) {
            if (TakerHolder.isInitialized) {
                refreshBalances()
            } else {
                connectWallet()
            }
        }
    }

    /** Initialize / reconnect UniFFI Taker (RPC+ZMQ+Tor). Tor SOCKS is soft-checked only. */
    fun connectWallet() {
        if (!session.isLoggedIn) return
        viewModelScope.launch {
            val cfg = session.config
            val tor = TorManager.checkSocks(cfg.torSocksHost, cfg.torSocksPort)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    walletName = cfg.walletName,
                    rpcLabel = cfg.rpcUrl,
                    zmqLabel = cfg.zmqAddr,
                    libraryLoadStatus = repo.libraryLoadStatus,
                    capabilities = repo.getCapabilities(),
                    torReachable = tor.reachable,
                    torStatusMessage = tor.message,
                )
            }
            // Wallet RPC works without Tor; warn but still init (markets/swaps need Tor later).
            repo.initTaker(session)
                .onSuccess { state ->
                    _uiState.update {
                        it.applyState(state, cfg.rpcUrl, cfg.walletName, cfg.zmqAddr)
                            .copy(
                                isLoading = false,
                                isInitialized = true,
                                error = if (!tor.reachable) {
                                    "Wallet connected. Tor SOCKS not reachable; markets/swaps need Orbot."
                                } else {
                                    null
                                },
                            )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, isInitialized = false, error = e.message)
                    }
                }
        }
    }

    fun refreshTorStatus() {
        viewModelScope.launch {
            val tor = TorManager.checkSocks(session.socksHost, session.socksPort)
            _uiState.update {
                it.copy(torReachable = tor.reachable, torStatusMessage = tor.message)
            }
        }
    }

    fun refreshBalances() {
        if (!TakerHolder.isInitialized) {
            connectWallet()
            return
        }
        syncWallet()
    }

    fun syncWallet() {
        if (!session.isLoggedIn) return
        if (!TakerHolder.isInitialized) {
            connectWallet()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val cfg = session.config
            repo.syncWallet()
                .onSuccess { state ->
                    _uiState.update {
                        it.applyState(state, cfg.rpcUrl, cfg.walletName, cfg.zmqAddr)
                            .copy(isLoading = false, isInitialized = true, error = null)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun generateReceiveAddress(addrType: String = "P2WPKH") {
        if (!session.isLoggedIn || !TakerHolder.isInitialized) {
            _uiState.update { it.copy(error = "Initialize the taker wallet first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getNewAddress(addrType)
                .onSuccess { addr -> _uiState.update { it.copy(isLoading = false, receiveAddress = addr) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun WalletUiState.applyState(
        state: WalletState,
        rpcUrl: String,
        walletName: String,
        zmqAddr: String,
    ): WalletUiState = copy(
        balanceSats = state.balanceSats,
        confirmedSats = state.confirmedSats,
        unconfirmedSats = state.unconfirmedSats,
        regularSats = state.regularSats,
        swapSats = state.swapSats,
        contractSats = state.contractSats,
        fidelitySats = state.fidelitySats,
        rpcLabel = rpcUrl,
        walletName = walletName,
        zmqLabel = zmqAddr,
        backendLabel = "Bitcoin Core RPC • $rpcUrl • ZMQ $zmqAddr",
        utxos = state.utxos,
        libraryLoadStatus = repo.libraryLoadStatus,
    )
}
