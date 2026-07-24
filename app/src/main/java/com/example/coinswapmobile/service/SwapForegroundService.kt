package com.example.coinswapmobile.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.SwapTrackerProgress
import com.example.coinswapmobile.model.PreparedSwap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Keeps [startCoinswap] alive with a foreground notification and polls
 * core-lib swap_tracker.cbor for live phase text (taker-app pattern).
 */
class SwapForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var swapJob: Job? = null
    private var pollJob: Job? = null
    private var cleared = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                requestStop(userCancelled = true)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val swapId = intent.getStringExtra(EXTRA_SWAP_ID).orEmpty()
                val protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "Legacy"
                val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
                if (swapId.isBlank()) {
                    clearAndStop()
                    return START_NOT_STICKY
                }
                cleared = false
                startInForeground("Starting coinswap…")
                runSwap(PreparedSwap(swapId = swapId, sendAmountSats = amount, protocol = protocol))
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(text: String) {
        SwapNotificationHelper.ensureChannel(this)
        val notification = SwapNotificationHelper.build(
            this,
            title = "CoinSwap in progress",
            text = text,
            ongoing = true,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                SwapNotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(SwapNotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun runSwap(prepared: PreparedSwap) {
        if (swapJob?.isActive == true) return
        val dataDir = File(FfiEnv.takerDataDir(this))
        val repo = CoinswapRepository(appDataDir = dataDir.absolutePath)

        SwapExecutionBus.emit(
            SwapExecutionBus.Event(
                swapId = prepared.swapId,
                phase = "Executing coinswap…",
                isRunning = true,
            ),
        )

        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val snap = SwapTrackerProgress.read(dataDir, prepared.swapId)
                val label = SwapTrackerProgress.phaseLabel(snap?.phase)
                SwapNotificationHelper.notify(
                    this@SwapForegroundService,
                    title = "CoinSwap in progress",
                    text = label,
                    ongoing = true,
                )
                SwapExecutionBus.emit(
                    SwapExecutionBus.Event(
                        swapId = prepared.swapId,
                        phase = label,
                        isRunning = true,
                    ),
                )
                delay(2_000)
            }
        }

        swapJob = scope.launch {
            val result = repo.startCoinswap(prepared)
            pollJob?.cancel()
            if (cleared) {
                clearAndStop()
                return@launch
            }
            result
                .onSuccess { report ->
                    val doneText = "Completed"
                    SwapExecutionBus.emit(
                        SwapExecutionBus.Event(
                            swapId = report.id,
                            phase = doneText,
                            isRunning = false,
                            completed = true,
                        ),
                    )
                }
                .onFailure { e ->
                    val msg = e.message ?: "Swap failed"
                    val snap = SwapTrackerProgress.read(dataDir, prepared.swapId)
                    val phase = snap?.phase?.let { SwapTrackerProgress.phaseLabel(it) }
                    SwapExecutionBus.emit(
                        SwapExecutionBus.Event(
                            swapId = prepared.swapId,
                            phase = phase ?: "Failed",
                            isRunning = false,
                            failed = true,
                            errorMessage = msg,
                        ),
                    )
                }
            clearAndStop()
        }
    }

    private fun requestStop(userCancelled: Boolean) {
        if (userCancelled) {
            SwapExecutionBus.emit(
                SwapExecutionBus.Event(
                    swapId = null,
                    phase = "Stopped",
                    isRunning = false,
                    failed = true,
                    errorMessage = "Swap stopped",
                ),
            )
        }
        clearAndStop()
    }

    private fun clearAndStop() {
        if (cleared) {
            stopSelf()
            return
        }
        cleared = true
        pollJob?.cancel()
        swapJob?.cancel()
        SwapNotificationHelper.cancel(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground swap running — killing it mid-protocol locks coins.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        SwapNotificationHelper.cancel(this)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.coinswapmobile.action.START_SWAP"
        const val ACTION_STOP = "com.example.coinswapmobile.action.STOP_SWAP"
        const val EXTRA_SWAP_ID = "swap_id"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_AMOUNT = "amount_sats"

        fun start(context: Context, prepared: PreparedSwap) {
            val intent = Intent(context, SwapForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SWAP_ID, prepared.swapId)
                putExtra(EXTRA_PROTOCOL, prepared.protocol)
                putExtra(EXTRA_AMOUNT, prepared.sendAmountSats)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SwapForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            // startService so ACTION_STOP is delivered even if FGS already torn down
            try {
                context.startService(intent)
            } catch (_: Exception) {
                SwapNotificationHelper.cancel(context)
            }
        }
    }
}
