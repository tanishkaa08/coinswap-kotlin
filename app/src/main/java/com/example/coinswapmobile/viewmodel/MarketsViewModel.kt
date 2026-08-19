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
import com.example.coinswapmobile.service.SwapExecutionBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class MarketsUiState(
    val makers: List<SwapMaker> = emptyList(),
    val isSyncing: Boolean = false,
    val syncStatus: String? = null,
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
            val tor = TorManager.ensureRunning(getApplication())
            _state.update { it.copy(torReachable = tor.reachable, torStatusMessage = tor.message) }
        }
    }

    /** Manual "Sync Marketplace": force a sync now with spinner + error surfacing. */
    fun syncMarketplace() {
        viewModelScope.launch { runSync(manual = true) }
    }

    /**
     * Background refresh while Markets is visible.
     * Loads cached makers immediately; full Tor poll only periodically (not every minute).
     */
    fun startAutoSync() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob = viewModelScope.launch {
            runSync(manual = false, pollTor = false)
            var tick = 0
            while (isActive) {
                val fullPoll = tick == 0 || tick % FULL_POLL_EVERY_N_TICKS == 0
                runSync(manual = false, pollTor = fullPoll)
                delay(AUTO_SYNC_INTERVAL_MS)
                tick++
            }
        }
    }

    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    private suspend fun runSync(manual: Boolean, pollTor: Boolean = true) {
        if (!session.isLoggedIn) return
        if (syncLock.isLocked && !manual) return
        // Don't open competing Tor circuits while a swap is using Tor.
        val doTorPoll = pollTor && (manual || !SwapExecutionBus.active.value)

        syncLock.withLock {
            val tor = TorManager.ensureRunning(getApplication())
            if (!tor.reachable) {
                _state.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = null,
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
                    it.copy(
                        isSyncing = true,
                        syncStatus = "Loading…",
                        errorMessage = null,
                        torReachable = true,
                        torStatusMessage = tor.message,
                    )
                }
            } else {
                _state.update { it.copy(torReachable = true, torStatusMessage = tor.message) }
            }

            if (!TakerHolder.isInitialized) {
                coinswapRepo.initTaker(session).onFailure { e ->
                    if (manual) {
                        _state.update {
                            it.copy(isSyncing = false, syncStatus = null, errorMessage = e.message)
                        }
                    } else {
                        _state.update { it.copy(isSyncing = false, syncStatus = null) }
                    }
                    return
                }
            }

            marketRepo.fetchOffers().onSuccess { cached ->
                if (cached.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            makers = cached,
                            errorMessage = null,
                            syncStatus = if (manual || doTorPoll) "Syncing…" else null,
                        )
                    }
                } else if (manual || doTorPoll) {
                    _state.update {
                        it.copy(syncStatus = "Syncing…")
                    }
                }
            }

            if (!doTorPoll && !manual) {
                _state.update { it.copy(isSyncing = false, syncStatus = null) }
                return
            }

            if (manual) {
                _state.update { it.copy(isSyncing = true) }
            }

            val syncResult = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                marketRepo.syncOfferbookAndWait()
            }

            when {
                syncResult == null -> {
                    marketRepo.fetchOffers().onSuccess { makers ->
                        _state.update {
                            it.copy(
                                isSyncing = false,
                                syncStatus = null,
                                makers = makers.ifEmpty { it.makers },
                                errorMessage = if (manual) {
                                    "Tor poll timed out — showing cached makers."
                                } else {
                                    it.errorMessage
                                },
                            )
                        }
                    }.onFailure {
                        _state.update {
                            it.copy(
                                isSyncing = false,
                                syncStatus = null,
                                errorMessage = if (manual) {
                                    "Tor poll timed out; could not refresh makers."
                                } else {
                                    it.errorMessage
                                },
                            )
                        }
                    }
                }
                syncResult.isFailure -> {
                    if (manual) {
                        _state.update {
                            it.copy(
                                isSyncing = false,
                                syncStatus = null,
                                errorMessage = syncResult.exceptionOrNull()?.message,
                            )
                        }
                    } else {
                        _state.update { it.copy(isSyncing = false, syncStatus = null) }
                    }
                }
                else -> {
                    marketRepo.fetchOffers()
                        .onSuccess { makers ->
                            _state.update {
                                it.copy(
                                    isSyncing = false,
                                    syncStatus = null,
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
                            if (manual) {
                                _state.update {
                                    it.copy(isSyncing = false, syncStatus = null, errorMessage = e.message)
                                }
                            } else {
                                _state.update { it.copy(isSyncing = false, syncStatus = null) }
                            }
                        }
                }
            }
        }
    }

    override fun onCleared() {
        stopAutoSync()
        super.onCleared()
    }

    private companion object {
        /** Cache re-read while Markets is open. */
        const val AUTO_SYNC_INTERVAL_MS = 45_000L
        /** Full Tor poll about every ~5 min (45s × 7). */
        const val FULL_POLL_EVERY_N_TICKS = 7
        const val SYNC_TIMEOUT_MS = 180_000L
    }
}
