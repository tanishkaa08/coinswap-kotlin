package com.example.coinswapmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.MarketRepository
import com.example.coinswapmobile.data.TakerAppConfig
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.model.NativeCapabilities
import com.example.coinswapmobile.screens.SwapMaker
import com.example.coinswapmobile.service.SwapExecutionBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

data class MarketsUiState(
    val makers: List<SwapMaker> = emptyList(),
    val isSyncing: Boolean = false,
    val syncStatus: String? = null,
    val torReachable: Boolean = false,
    val torStatusMessage: String = "",
    val capabilities: NativeCapabilities? = null,
    val errorMessage: String? = null,
    /** Popup when Orbot SOCKS is missing or makers stay offline. */
    val showOrbotPrompt: Boolean = false,
    val orbotPromptReason: String? = null,
    /** True after max Orbot sync attempts until a maker comes online or user taps Sync. */
    val syncExhausted: Boolean = false,
)

class MarketsViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)
    private val coinswapRepo = CoinswapRepository(appDataDir = FfiEnv.takerDataDir(app))
    private val marketRepo = MarketRepository(coinswapRepo)

    private val _state = MutableStateFlow(
        MarketsUiState(capabilities = coinswapRepo.getCapabilities())
    )
    val uiState = _state.asStateFlow()

    private val syncLock = Mutex()
    private var autoSyncJob: Job? = null
    private var untilOnlineJob: Job? = null

    init {
        refreshTorStatus()
    }

    fun refreshTorStatus() {
        viewModelScope.launch {
            val tor = TorManager.ensureRunning(getApplication(), timeoutMs = 5_000L)
            _state.update { it.copy(torReachable = tor.reachable, torStatusMessage = tor.message) }
        }
    }

    fun dismissOrbotPrompt() {
        _state.update { it.copy(showOrbotPrompt = false, orbotPromptReason = null) }
    }

    fun promptOrbotFromUi(reason: String) {
        promptOrbot(reason)
    }

    private fun promptOrbot(reason: String) {
        _state.update {
            it.copy(
                showOrbotPrompt = true,
                orbotPromptReason = reason,
            )
        }
    }

    /** Retry onion polls over Orbot SOCKS until makers are online (or attempts exhausted). */
    fun syncMarketplace() {
        untilOnlineJob?.cancel()
        _state.update { it.copy(syncExhausted = false, showOrbotPrompt = false) }
        untilOnlineJob = viewModelScope.launch {
            runUntilOnline(surfaceErrors = true)
        }
    }

    fun startAutoSync() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob = viewModelScope.launch {
            if (untilOnlineJob?.isActive != true && !_state.value.syncExhausted) {
                untilOnlineJob = viewModelScope.launch {
                    runUntilOnline(surfaceErrors = false)
                }
            }
            var tick = 0
            while (isActive) {
                val online = _state.value.makers.count { it.online }
                val needFull = online == 0 || tick == 0 || tick % FULL_POLL_EVERY_N_TICKS == 0
                if (online == 0 &&
                    untilOnlineJob?.isActive != true &&
                    !SwapExecutionBus.active.value &&
                    !_state.value.syncExhausted
                ) {
                    untilOnlineJob = viewModelScope.launch {
                        runUntilOnline(surfaceErrors = false)
                    }
                } else if (needFull &&
                    untilOnlineJob?.isActive != true &&
                    online > 0 &&
                    !SwapExecutionBus.active.value
                ) {
                    runSync(manual = false, pollTor = true)
                } else if (!needFull && !SwapExecutionBus.active.value) {
                    runSync(manual = false, pollTor = false)
                }
                delay(if (online == 0) AGGRESSIVE_RETRY_MS else AUTO_SYNC_INTERVAL_MS)
                tick++
            }
        }
    }

    fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
        untilOnlineJob?.cancel()
        untilOnlineJob = null
        _state.update { it.copy(isSyncing = false, syncStatus = null) }
    }

    private suspend fun runUntilOnline(surfaceErrors: Boolean) {
        if (!session.isLoggedIn) return
        var attempt = 0
        while (coroutineContext.isActive && session.isLoggedIn && attempt < MAX_ORBOT_ATTEMPTS) {
            if (SwapExecutionBus.active.value) {
                _state.update {
                    it.copy(isSyncing = false, syncStatus = "Paused while swap uses Tor")
                }
                delay(AGGRESSIVE_RETRY_MS)
                continue
            }

            attempt++
            val online = runSyncAttempt(attempt = attempt, surfaceErrors = surfaceErrors)
            if (online > 0) {
                _state.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = null,
                        errorMessage = null,
                        showOrbotPrompt = false,
                        orbotPromptReason = null,
                        syncExhausted = false,
                    )
                }
                return
            }

            if (attempt >= MAX_ORBOT_ATTEMPTS) break

            TorManager.requestNewNym(TorManager.probeControlPassword())

            val listed = _state.value.makers.size
            _state.update {
                it.copy(
                    isSyncing = true,
                    syncStatus = if (listed > 0) {
                        "Attempt $attempt/$MAX_ORBOT_ATTEMPTS — 0 of $listed online, retrying…"
                    } else {
                        "Attempt $attempt/$MAX_ORBOT_ATTEMPTS — waiting for Orbot / Nostr…"
                    },
                    errorMessage = if (surfaceErrors) {
                        if (listed > 0) {
                            "Makers listed but unreachable over Orbot. Retrying…"
                        } else {
                            "Still discovering makers. Retrying…"
                        }
                    } else {
                        it.errorMessage
                    },
                )
            }
            delay(RETRY_GAP_MS)
        }

        val listed = _state.value.makers.size
        _state.update {
            it.copy(
                isSyncing = false,
                syncExhausted = true,
                syncStatus = if (listed > 0) {
                    "Stopped after $MAX_ORBOT_ATTEMPTS attempts — 0 of $listed online"
                } else {
                    "Stopped after $MAX_ORBOT_ATTEMPTS attempts — check Orbot"
                },
                errorMessage = "Could not reach makers over Orbot after $MAX_ORBOT_ATTEMPTS tries. " +
                    "Confirm SocksPort 9050, then Sync.",
            )
        }
        promptOrbot(
            "Could not reach makers after $MAX_ORBOT_ATTEMPTS attempts" +
                (if (listed > 0) " (0 of $listed makers online)." else ".") +
                " Keep Orbot running with local SocksPort 9050.",
        )
    }

    /**
     * One discovery + onion poll. Lists Nostr makers even when Orbot is not ready
     * so the Markets screen is never stuck on an empty list.
     */
    private suspend fun runSyncAttempt(attempt: Int, surfaceErrors: Boolean): Int {
        if (syncLock.isLocked) return _state.value.makers.count { it.online }
        return syncLock.withLock {
            val tor = TorManager.ensureRunning(getApplication())
            if (!tor.reachable) {
                _state.update {
                    it.copy(
                        isSyncing = true,
                        syncStatus = "Attempt $attempt — waiting for Orbot SOCKS…",
                        torReachable = false,
                        torStatusMessage = tor.message,
                        errorMessage = if (surfaceErrors) tor.message else it.errorMessage,
                    )
                }
                if (surfaceErrors && attempt == 1) {
                    promptOrbot(tor.message)
                }
                return@withLock 0
            }

            if (!TakerHolder.isInitialized) {
                coinswapRepo.initTaker(
                    session,
                    electrumSocks5 = session.config.electrumSocks5,
                    electrumTimeoutSecs = TakerAppConfig.DEFAULT_ELECTRUM_TIMEOUT_SECS,
                ).onFailure { e ->
                    _state.update {
                        it.copy(
                            isSyncing = true,
                            syncStatus = "Attempt $attempt — taker init failed",
                            errorMessage = if (surfaceErrors) e.message else it.errorMessage,
                        )
                    }
                    return@withLock 0
                }
            }

            marketRepo.fetchOffers().onSuccess { cached ->
                if (cached.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            makers = cached,
                            isSyncing = true,
                            syncStatus = "Attempt $attempt — ${cached.count { it.online }} of ${cached.size} online",
                        )
                    }
                }
            }

            _state.update {
                it.copy(
                    isSyncing = true,
                    syncStatus = "Attempt $attempt — discovering makers…",
                    torReachable = true,
                    torStatusMessage = tor.message,
                    errorMessage = null,
                )
            }

            suspend fun pollOnce(label: String, timeoutMs: Long): List<SwapMaker> {
                _state.update { it.copy(syncStatus = "Attempt $attempt — $label") }
                val deferred = viewModelScope.async {
                    marketRepo.syncOfferbookAndWait(forceClearCache = false)
                }
                withTimeoutOrNull(timeoutMs) { deferred.await() }
                if (deferred.isActive) deferred.cancel()
                return marketRepo.fetchOffers().getOrElse { _state.value.makers }
            }

            var makers = pollOnce("syncing offerbook…", LIST_SYNC_MS)
            if (makers.isEmpty()) {
                delay(NOSTR_CATCHUP_MS)
                makers = pollOnce("waiting for Nostr makers…", LIST_SYNC_MS)
            }
            if (makers.isNotEmpty()) {
                _state.update {
                    it.copy(
                        makers = makers,
                        syncStatus = "Attempt $attempt — ${makers.count { it.online }} of ${makers.size} listed",
                    )
                }
            }

            _state.update {
                it.copy(syncStatus = "Attempt $attempt — polling makers over Orbot…")
            }
            TorManager.requestNewNym(tor.controlPassword)
            delay(1_500)
            makers = pollOnce("polling makers over Tor…", SYNC_TIMEOUT_MS)
            val online = makers.count { it.online }
            _state.update {
                it.copy(
                    makers = makers.ifEmpty { it.makers },
                    isSyncing = online == 0,
                    syncStatus = when {
                        online > 0 -> null
                        makers.isNotEmpty() ->
                            "Attempt $attempt — 0 of ${makers.size} online"
                        else ->
                            "Attempt $attempt — no makers in offerbook yet"
                    },
                    errorMessage = when {
                        online > 0 -> null
                        surfaceErrors && makers.isNotEmpty() ->
                            "Makers found but unreachable over Orbot SOCKS."
                        surfaceErrors && makers.isEmpty() ->
                            "No makers discovered yet. Keep Markets open."
                        else -> it.errorMessage
                    },
                )
            }
            online
        }
    }

    private suspend fun runSync(manual: Boolean, pollTor: Boolean = true) {
        if (!session.isLoggedIn) return
        if (syncLock.isLocked && !manual) return
        val doTorPoll = pollTor && (manual || !SwapExecutionBus.active.value)

        syncLock.withLock {
            val tor = TorManager.ensureRunning(getApplication())
            if (!tor.reachable) {
                _state.update {
                    it.copy(torReachable = false, torStatusMessage = tor.message)
                }
                return
            }

            if (!TakerHolder.isInitialized) {
                coinswapRepo.initTaker(
                    session,
                    electrumSocks5 = session.config.electrumSocks5,
                    electrumTimeoutSecs = TakerAppConfig.DEFAULT_ELECTRUM_TIMEOUT_SECS,
                ).onFailure { return }
            }

            marketRepo.fetchOffers().onSuccess { cached ->
                if (cached.isNotEmpty()) {
                    _state.update { it.copy(makers = cached, errorMessage = null) }
                }
            }

            if (!doTorPoll) return

            val syncDeferred = viewModelScope.async {
                marketRepo.syncOfferbookAndWait(forceClearCache = false)
            }
            withTimeoutOrNull(SYNC_TIMEOUT_MS) { syncDeferred.await() }
            if (syncDeferred.isActive) syncDeferred.cancel()

            marketRepo.fetchOffers().onSuccess { makers ->
                _state.update {
                    it.copy(
                        makers = makers.ifEmpty { it.makers },
                        errorMessage = null,
                        isSyncing = false,
                        syncStatus = null,
                        torReachable = true,
                        torStatusMessage = tor.message,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        stopAutoSync()
        super.onCleared()
    }

    private companion object {
        const val AUTO_SYNC_INTERVAL_MS = 45_000L
        const val AGGRESSIVE_RETRY_MS = 25_000L
        const val RETRY_GAP_MS = 12_000L
        const val FULL_POLL_EVERY_N_TICKS = 7
        const val SYNC_TIMEOUT_MS = 240_000L
        const val LIST_SYNC_MS = 25_000L
        const val NOSTR_CATCHUP_MS = 6_000L
        const val MAX_ORBOT_ATTEMPTS = 3
    }
}
