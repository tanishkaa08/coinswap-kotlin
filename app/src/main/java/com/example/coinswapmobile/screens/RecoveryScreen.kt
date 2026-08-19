package com.example.coinswapmobile.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.SwapRepository
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

enum class RecoveryUiState { InProgress, Complete, Failed }

data class SwapRecoveryInfo(
    val failed: Boolean,
    val amountSats: Long,
    val lastStatus: String
)

object SwapRecovery {
    private val SWAP_FILES = listOf("swap.json", "swap_state.json")

    fun readSwapState(context: Context): SwapRecoveryInfo? {
        val dir = FfiEnv.takerDataDir(context)
        for (name in SWAP_FILES) {
            val file = File(dir, name)
            if (!file.exists()) continue
            try {
                val json = JSONObject(file.readText())
                val status = json.optString(
                    "status",
                    json.optString("phase", json.optString("last_status", "")),
                )
                val failed = status.contains("fail", ignoreCase = true) ||
                    status.contains("recover", ignoreCase = true) ||
                    status.contains("incomplete", ignoreCase = true) ||
                    File(dir, "recovery_in_progress").exists()
                return SwapRecoveryInfo(
                    failed = failed,
                    amountSats = json.optLong("amount_sats", json.optLong("amountSats", 0)),
                    lastStatus = json.optString("last_status", status),
                )
            } catch (_: Exception) {
            }
        }
        if (File(dir, "recovery_in_progress").exists()) {
            return SwapRecoveryInfo(failed = true, amountSats = 0, lastStatus = "recovery_in_progress")
        }
        return null
    }

    fun isFailedSwap(context: Context): Boolean = readSwapState(context)?.failed == true
}

private const val GITHUB_ISSUE_URL = "https://github.com/citadel-tech/coinswap/issues/new/choose"

@Composable
fun RecoveryScreen(
    autoStart: Boolean = true,
    onComplete: () -> Unit,
    onAbandon: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val swapInfo = remember { SwapRecovery.readSwapState(context) }
    var uiState by remember { mutableStateOf(RecoveryUiState.InProgress) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repo = remember { CoinswapRepository(FfiEnv.takerDataDir(context)) }
    val swapRepo = remember { SwapRepository(repo) }
    val session = remember { UserSession(context) }

    BackHandler(enabled = uiState == RecoveryUiState.InProgress) { }

    fun appendLog(line: String) { logs = logs + line }

    suspend fun runRecovery() {
        if (isRunning) return
        isRunning = true
        logs = emptyList()
        uiState = RecoveryUiState.InProgress

        appendLog("[recovery] Checking Tor SOCKS…")
        val tor = TorManager.checkSocks()
        appendLog("[recovery] ${tor.message}")
        if (!tor.reachable) {
            appendLog("[recovery] Tor required")
            uiState = RecoveryUiState.Failed
            isRunning = false
            return
        }

        if (!session.isLoggedIn) {
            appendLog("[recovery] Not logged in")
            uiState = RecoveryUiState.Failed
            isRunning = false
            return
        }

        if (!TakerHolder.isInitialized) {
            appendLog("[recovery] Initializing Taker…")
            val init = repo.initTaker(session)
            if (init.isFailure) {
                appendLog("[recovery] Taker.init failed: ${init.exceptionOrNull()?.message}")
                uiState = RecoveryUiState.Failed
                isRunning = false
                return
            }
            appendLog("[recovery] Taker ready")
        }

        appendLog("[recovery] Checking local swap state + calling UniFFI recoverActiveSwap…")
        val detect = repo.detectRecoverableSwaps()
        val swaps = detect.getOrDefault(emptyList())
        swaps.forEach { s ->
            appendLog("[recovery] Candidate ${s.swapId} phase=${s.phase}")
        }
        if (swaps.isEmpty()) {
            appendLog("[recovery] No local failure markers; still attempting recoverActiveSwap()")
        }

        appendLog("[recovery] Running recoverActiveSwap…")
        val swapId = swaps.firstOrNull { it.recoverable }?.swapId.orEmpty()
        val before = repo.getBalance().getOrNull()
        val spendableBefore = before?.balanceSats ?: 0L
        val result = swapRepo.recoverActiveSwap(swapId)
        if (result.isFailure) {
            appendLog("[recovery] Error: ${result.exceptionOrNull()?.message}")
            uiState = RecoveryUiState.Failed
            isRunning = false
            return
        }
        appendLog("[recovery] ${result.getOrNull()}")

        val after = repo.getBalance().getOrNull()
        val lockedAfter = after?.contractSats ?: 0L
        val spendableAfter = after?.balanceSats ?: spendableBefore
        // Failed swaps from a previous regtest chain have outgoing swapcoins
        // whose funding txs are gone. Wallet contract balance stays 0; waiting
        // cannot reclaim them. Leave recovery so the user can swap again.
        if (lockedAfter == 0L && (before?.contractSats ?: 0L) == 0L) {
            appendLog(
                "[recovery] No locked contract coins on this chain (spendable=$spendableAfter). Nothing to wait for.",
            )
            repo.clearRecoveryMarker()
            uiState = RecoveryUiState.Complete
            isRunning = false
            return
        }

        var sawLocked = (before?.contractSats ?: 0L) > 0L
        var reclaimed = false
        var remaining = 120
        while (remaining-- > 0) {
            val bal = repo.getBalance().getOrNull()
            val locked = bal?.contractSats ?: -1L
            val spendable = bal?.balanceSats ?: spendableBefore
            if (locked > 0L) sawLocked = true
            if (spendable > spendableBefore + 50_000L) {
                appendLog("[recovery] Spendable increased to $spendable sats")
                reclaimed = true
                break
            }
            if (sawLocked && locked == 0L) {
                reclaimed = true
                break
            }
            if (locked > 0L) {
                appendLog("[recovery] Waiting for locked coins… $locked sats")
            } else {
                appendLog("[recovery] Waiting for reclaim… spendable=$spendable")
            }
            delay(5_000)
        }

        if (!reclaimed) {
            appendLog("[recovery] Coins still in outgoing contracts")
            uiState = RecoveryUiState.Failed
            isRunning = false
            return
        }

        repo.clearRecoveryMarker()
        uiState = RecoveryUiState.Complete
        isRunning = false
    }

    LaunchedEffect(Unit) {
        if (autoStart) runRecovery()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val canLeave = uiState != RecoveryUiState.InProgress
            IconButton(
                onClick = { if (canLeave) (onBack ?: onAbandon)() },
                enabled = canLeave,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Back",
                    tint = if (canLeave) TextPrimary else TextSecondary,
                )
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
        val borderColor = when (uiState) {
            RecoveryUiState.Complete -> TorActive
            RecoveryUiState.Failed -> TorInactive
            RecoveryUiState.InProgress -> AccentAmber
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
                    Text("Recovery completed",
                        style = MaterialTheme.typography.titleMedium,
                        color = TorActive)
                }
                RecoveryUiState.Failed -> {
                    Text("Recovery did not complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = TorInactive)
                }
            }

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
            }

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
                    Button(
                        onClick = {
                            repo.clearRecoveryMarker()
                            onAbandon()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TorActive)
                    ) {
                        Text(
                            "Continue to Home",
                            color = androidx.compose.ui.graphics.Color.Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Recovery Log", logs.joinToString("\n"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                    ) {
                        Icon(Icons.Default.ContentCopy, null,
                            tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Recovery Log", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_ISSUE_URL)))
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) {
                        Text("Open GitHub Issue", color = androidx.compose.ui.graphics.Color.White)
                    }

                    OutlinedButton(
                        onClick = { scope.launch { runRecovery() } },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber)
                    ) {
                        Text("Retry Recovery", color = AccentAmber)
                    }
                }
                RecoveryUiState.InProgress -> { }
            }
        }

        if (logs.isNotEmpty() || uiState == RecoveryUiState.InProgress) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceAlt)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("RECOVERY LOG",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    logs.forEach { line ->
                        Text(line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.contains("error", ignoreCase = true) ||
                                        line.contains("fail", ignoreCase = true) ||
                                        line.contains("not implemented", ignoreCase = true))
                                        TorInactive else TextPrimary)
                    }
                }
        }

            Spacer(Modifier.height(16.dp))
        }
    }
}
