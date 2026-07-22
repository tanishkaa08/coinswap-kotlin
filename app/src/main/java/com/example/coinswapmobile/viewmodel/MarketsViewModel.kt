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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    // Serializes manual + auto sync so they never run the offerbook poll concurrently.
    private val syncLock = Mutex()
    private var autoSyncJob: Job? = null

    init {
        refreshTorStatus()
    }

    fun refreshTorStatus() {
        viewModelScope.launch {
            val tor = TorManager.checkSocks()
            _state.update { it.copy(torReachable = tor.reachable, torStatusMessage = tor.message) }
        }
    }

    /** Manual "Sync Marketplace": force a sync now with spinner + error surfacing. */
    fun syncMarketplace() {
        viewModelScope.launch { runSync(manual = true) }
    }

    /**
     * Background auto-sync while the Markets screen is visible. The Rust core
     * also refreshes the offerbook on its own timer; this keeps the UI in sync
     * so new makers appear without tapping. Silent: no spinner, no error toast.
     */
    fun startAutoSync() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob = viewModelScope.launch {
            while (isActive) {
                runSync(manual = false)
                delay(AUTO_SYNC_INTERVAL_MS)
            }
        }
    }

    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    private suspend fun runSync(manual: Boolean) {
        if (!session.isLoggedIn) return
        // If a sync is already running, let it finish rather than stacking polls.
        if (syncLock.isLocked && !manual) return

        syncLock.withLock {
            val tor = TorManager.checkSocks()
            if (!tor.reachable) {
                _state.update {
                    it.copy(
                        isSyncing = false,
                        torReachable = false,
                        torStatusMessage = tor.message,
                        errorMessage = if (manual) {
                            "Tor SOCKS required for maker discovery: ${tor.message}"
                        } else {
                            it.errorMessage
                        },
                    )
                }
                return
            }

            if (manual) {
                _state.update {
                    it.copy(isSyncing = true, errorMessage = null, torReachable = true, torStatusMessage = tor.message)
                }
            } else {
                _state.update { it.copy(torReachable = true, torStatusMessage = tor.message) }
            }

            if (!TakerHolder.isInitialized) {
                coinswapRepo.initTaker(session).onFailure { e ->
                    if (manual) _state.update { it.copy(isSyncing = false, errorMessage = e.message) }
                    else _state.update { it.copy(isSyncing = false) }
                    return
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
                                    errorMessage = when {
                                        makers.isNotEmpty() -> null
                                        manual -> "No reachable makers found after offerbook sync."
                                        else -> it.errorMessage
                                    },
                                )
                            }
                        }
                        .onFailure { e ->
                            if (manual) _state.update { it.copy(isSyncing = false, errorMessage = e.message) }
                            else _state.update { it.copy(isSyncing = false) }
                        }
                }
                .onFailure { e ->
                    if (manual) _state.update { it.copy(isSyncing = false, errorMessage = e.message) }
                    else _state.update { it.copy(isSyncing = false) }
                }
        }
    }

    override fun onCleared() {
        stopAutoSync()
        super.onCleared()
    }

    private companion object {
        const val AUTO_SYNC_INTERVAL_MS = 60_000L
    }
}
