package com.example.coinswapmobile.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.data.SwapRepository
import com.example.coinswapmobile.data.TakerManager
import com.example.coinswapmobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class RecoveryUiState { InProgress, Complete, Failed }

data class SwapRecoveryInfo(
    val failed: Boolean,
    val amountSats: Long,
    val lastStatus: String
)

object SwapRecovery {
    private const val SWAP_FILE = "swap.json"

    fun readSwapState(context: Context): SwapRecoveryInfo? {
        val file = File(context.filesDir, SWAP_FILE)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val status = json.optString("status", "")
            SwapRecoveryInfo(
                failed = status.equals("failed", ignoreCase = true),
                amountSats = json.optLong("amount_sats", 0),
                lastStatus = json.optString("last_status", status)
            )
        } catch (_: Exception) { null }
    }

    fun isFailedSwap(context: Context): Boolean = readSwapState(context)?.failed == true

    suspend fun performRecovery(onLog: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (!TakerManager.isInitialized) {
                onLog("[recovery] Taker not initialized — cannot recover")
                return@withContext false
            }
            onLog("[recovery] Reading swap state from taker daemon")
            onLog("[recovery] Reconnecting to makers via Tor")
            val result = SwapRepository().recoverActiveSwap()
            if (result.isSuccess) {
                onLog("[recovery] Recovery complete")
                true
            } else {
                onLog("[recovery] Error: ${result.exceptionOrNull()?.message}")
                false
            }
        }
}

private const val GITHUB_ISSUE_URL = "https://github.com/citadel-tech/coinswap/issues/new/choose"

@Composable
fun RecoveryScreen(
    autoStart: Boolean = true,
    onComplete: () -> Unit,
    onAbandon: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context   = LocalContext.current
    val swapInfo  = remember { SwapRecovery.readSwapState(context) }
    var uiState   by remember { mutableStateOf(RecoveryUiState.InProgress) }
    var logs      by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var succeeded by remember { mutableStateOf(false) }
    val scope     = rememberCoroutineScope()
    val logScroll = rememberScrollState()

    fun appendLog(line: String) { logs = logs + line }

    suspend fun runRecovery() {
        if (isRunning) return
        isRunning = true
        uiState   = RecoveryUiState.InProgress
        logs      = emptyList()
        val ok = SwapRecovery.performRecovery(::appendLog)
        succeeded = ok
        uiState   = if (ok) RecoveryUiState.Complete else RecoveryUiState.Failed
        isRunning = false
    }

    // Auto-start immediately
    LaunchedEffect(Unit) { runRecovery() }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) logScroll.scrollTo(logScroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header with back button ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val backAction = onBack ?: onAbandon
            IconButton(onClick = backAction) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Recovery",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        // ── Status card ───────────────────────────────────────────────────────
        val borderColor = when (uiState) {
            RecoveryUiState.Complete -> TorActive
            RecoveryUiState.Failed   -> TorInactive
            else                     -> AccentAmber
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (uiState) {
                RecoveryUiState.InProgress -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = TorActive,
                            strokeWidth = 3.dp
                        )
                        Text("Recovery in progress",
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentAmber)
                    }
                }
                RecoveryUiState.Complete -> {
                    Text("Recovery complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = TorActive)
                }
                RecoveryUiState.Failed -> {
                    Text("Recovery did not complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = TorInactive)
                }
            }

            // Swap status info
            if (swapInfo != null) {
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SWAP AMOUNT",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary)
                        Text("%,d sats".format(swapInfo.amountSats),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SWAP STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary)
                        Text(swapInfo.lastStatus.ifBlank { "Unknown" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary)
                    }
                }
                Text("Source: swap.cwar  •  citadel-tech/coinswap",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
            }

            // Actions
            when (uiState) {
                RecoveryUiState.Complete -> {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TorActive)
                    ) {
                        Text("Continue to Home",
                            color = androidx.compose.ui.graphics.Color.Black,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
                RecoveryUiState.Failed -> {
                    // Copy log button
                    OutlinedButton(
                        onClick = {
                            val logText = logs.joinToString("\n")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Recovery Log", logText)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                    ) {
                        Icon(Icons.Default.ContentCopy, null,
                            tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Recovery Log", color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium)
                    }

                    // Open GitHub issue
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_ISSUE_URL))
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) {
                        Text("Open GitHub Issue",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.titleMedium)
                    }

                    Text("Paste the recovery log in the issue so the team can help.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)

                    // Retry
                    OutlinedButton(
                        onClick = { scope.launch { runRecovery() } },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber)
                    ) {
                        Text("Retry Recovery", color = AccentAmber,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                RecoveryUiState.InProgress -> { /* running — no actions */ }
            }
        }

        // ── Recovery log ─────────────────────────────────────────────────────
        if (logs.isNotEmpty() || uiState == RecoveryUiState.InProgress) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceAlt)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RECOVERY LOG",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    if (logs.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("Recovery Log",
                                        logs.joinToString("\n"))
                                )
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy log",
                                tint = TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(logScroll),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    logs.forEach { line ->
                        Text(line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.contains("error", ignoreCase = true) ||
                                        line.contains("fail", ignoreCase = true))
                                        TorInactive else TextPrimary)
                    }
                }
            }
        }

            Spacer(Modifier.height(16.dp))
        } // inner scroll Column
    } // outer Column
}
