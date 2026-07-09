package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.MarketRepository
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.screens.SwapMaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MarketsUiState(
    val makers: List<SwapMaker> = emptyList(),
    val isSyncing: Boolean = false,
    val torReachable: Boolean = false,
    val torStatusMessage: String = "",
    val capabilities: NativeCapabilities? = null,
    val errorMessage: String? = null,
)

class MarketsViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val coinswapRepo = CoinswapRepository(appDataDir = FfiEnv.takerDataDir(app))
    private val marketRepo = MarketRepository(coinswapRepo)

    private val _state = MutableStateFlow(
        MarketsUiState(capabilities = coinswapRepo.getCapabilities())
    )
    val uiState = _state.asStateFlow()

    init {
        refreshTorStatus()
    }

    fun refreshTorStatus() {
        viewModelScope.launch {
            val tor = TorManager.checkSocks(session.socksHost, session.socksPort)
            _state.update { it.copy(torReachable = tor.reachable, torStatusMessage = tor.message) }
        }
    }

    fun syncMarketplace() {
        if (!session.isLoggedIn) return
        viewModelScope.launch {
            val tor = TorManager.checkSocks(session.socksHost, session.socksPort)
            if (!tor.reachable) {
                _state.update {
                    it.copy(
                        isSyncing = false,
                        torReachable = false,
                        torStatusMessage = tor.message,
                        errorMessage = "Tor SOCKS required for maker discovery: ${tor.message}",
                        makers = emptyList(),
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(isSyncing = true, errorMessage = null, torReachable = true, torStatusMessage = tor.message)
            }
            if (!TakerHolder.isInitialized) {
                coinswapRepo.initTaker(session).onFailure { e ->
                    _state.update { it.copy(isSyncing = false, errorMessage = e.message) }
                    return@launch
                }
            }
            marketRepo.syncOfferbookAndWait()
                .onSuccess {
                    marketRepo.fetchOffers()
                        .onSuccess { makers ->
                            _state.update {
                                it.copy(
                                    isSyncing = false,
                                    makers = makers,
                                    errorMessage = if (makers.isEmpty()) {
                                        "No reachable makers found after offerbook sync."
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        .onFailure { e ->
                            _state.update { it.copy(isSyncing = false, errorMessage = e.message) }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSyncing = false, errorMessage = e.message) }
                }
        }
    }
}
