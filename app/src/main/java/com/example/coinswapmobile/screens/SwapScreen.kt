package com.example.coinswapmobile.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.components.LabeledSwitch
import com.example.coinswapmobile.components.SectionCard
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.components.coinswapTextFieldColors
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.SwapViewModel
import kotlinx.coroutines.delay

// ── Data models ──────────────────────────────────────────────────────────────

data class SwapMaker(
    val id: String,
    val feeRatePct: Double,
    val minSats: Long,
    val maxSats: Long,
    val liquiditySats: Long,
    val fidelityBondBtc: Double,
    val onionAddress: String,
    val online: Boolean
)

data class SwapUtxo(
    val txid: String,
    val amountSats: Long,
    val confirmed: Boolean,
    var selected: Boolean
)

// ── Network fee tiers ────────────────────────────────────────────────────────

private enum class NetworkFee(
    val label: String,
    val satPerVbyte: Int,
    val description: String
) {
    LOW(   "Low",    1, "1 sat/vB  •  60 min"),
    MEDIUM("Medium", 2, "2 sat/vB  •  20 min"),
    HIGH(  "High",   4, "4 sat/vB  •  10 min"),
}

// Taker-app uses 225 vbytes avg per funding tx, (hops + 1) total txs
private const val TX_VBYTES = 225L

// ── Swap state machine ───────────────────────────────────────────────────────

private enum class SwapState { IDLE, CONFIRMING, IN_PROGRESS, DONE }

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun SwapScreen(
    onNavigateToReports: () -> Unit = {},
    onSwapFailed: () -> Unit = {},
    swapViewModel: SwapViewModel = viewModel(),
) {
    val vmState by swapViewModel.uiState.collectAsState()

    var amountSats       by remember { mutableStateOf("") }
    var makerCount       by remember { mutableIntStateOf(2) }
    var networkFee       by remember { mutableStateOf(NetworkFee.MEDIUM) }
    var minFidelity      by remember { mutableStateOf("0") }
    var feeRatePerHop    by remember { mutableStateOf("0.10") }
    var customOnion      by remember { mutableStateOf("") }
    var showMakerFilters by remember { mutableStateOf(false) }

    var autoSelectMakers by remember { mutableStateOf(true) }
    var swapState        by remember { mutableStateOf(SwapState.IDLE) }

    val allMakers = vmState.makers

    val utxos = remember(vmState.utxos) {
        mutableStateListOf<SwapUtxo>().also { list ->
            list.addAll(vmState.utxos)
        }
    }

    LaunchedEffect(vmState.utxos) {
        utxos.clear()
        utxos.addAll(vmState.utxos)
    }

    // Observe swap completion / failure from ViewModel
    LaunchedEffect(vmState.swapResult, vmState.swapError) {
        when {
            vmState.swapResult != null -> {
                val ok = vmState.swapResult!!.status == com.example.coinswapmobile.screens.ReportStatus.COMPLETED
                swapState = if (ok) SwapState.DONE else SwapState.IDLE
                if (!ok) onSwapFailed()
                swapViewModel.clearSwapResult()
            }
            vmState.swapError != null -> {
                swapState = SwapState.IDLE
                onSwapFailed()
                swapViewModel.clearSwapResult()
            }
        }
    }

    val amountSatsLong   = amountSats.toLongOrNull() ?: 0L
    val feeRatePerHopPct = feeRatePerHop.toDoubleOrNull() ?: 0.0
    val minFidelitySats  = minFidelity.toLongOrNull() ?: 0L
    val eligibleMakers   = allMakers.filter {
        it.online &&
            it.feeRatePct <= feeRatePerHopPct &&
            (it.fidelityBondBtc * 100_000_000).toLong() >= minFidelitySats &&
            it.minSats <= amountSatsLong &&
            it.maxSats >= amountSatsLong &&
            (customOnion.isBlank() || it.onionAddress.contains(customOnion, ignoreCase = true))
    }
    val selectedMakers   = eligibleMakers.take(makerCount)
    val feePerHopSats    = (feeRatePerHopPct / 100.0 * amountSatsLong).toLong()
    val totalSwapFeeSats = feePerHopSats * makerCount
    // Taker-app formula: (hops + 1) funding txs × 225 vbytes × sat/vB
    val fundingTxCount   = makerCount + 1
    val miningFeeSats    = networkFee.satPerVbyte * TX_VBYTES * fundingTxCount
    val totalFeeSats     = totalSwapFeeSats + miningFeeSats
    val receiveAmtSats   = amountSatsLong - totalFeeSats
    val selectedUtxoTotal = utxos.filter { it.selected }.sumOf { it.amountSats }
    // Estimated time: ~10 min per hop based on taker-app block interval estimate
    val estimatedMinutes = makerCount * 10

    val swappableSats    = vmState.walletSats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Coinswap",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary)
                Text("Route Bitcoin privately through multiple makers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onNavigateToReports,
                shape  = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Reports",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
            }
        }

        SectionCard {
            // Label row — do NOT put SectionLabel inside a Row (it renders Text + Divider, breaks Row layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SWAP AMOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("${formatSats(swappableSats)} sats",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    TextButton(
                        onClick = { amountSats = swappableSats.toString() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("USE MAX",
                            style = MaterialTheme.typography.labelSmall,
                            color = TorActive)
                    }
                }
            }
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            OutlinedTextField(
                value = amountSats,
                onValueChange = { amountSats = it.filter { c -> c.isDigit() } },
                placeholder = { Text("e.g. 500000", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = coinswapTextFieldColors(),
                suffix = { Text("sats", color = TextSecondary) }
            )
            if (amountSatsLong > 0 && amountSatsLong < 100_000) {
                WarningBox("Minimum swap amount is 100,000 sats")
            }
        }

        // Maker count with 5+ popup dialog
        var showMakerDialog by remember { mutableStateOf(false) }
        var customMakerInput by remember { mutableStateOf("") }
        val presetCounts = listOf(2, 3, 4, 5)
        val isCustomSelected = makerCount !in presetCounts

        SectionCard {
            Text("MAKER COUNT",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Text("Number of swap hops",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetCounts.forEach { n ->
                    val sel = makerCount == n
                    Button(
                        onClick = { makerCount = n; customMakerInput = "" },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) TorActive else SurfaceAlt,
                            contentColor   = if (sel) androidx.compose.ui.graphics.Color.Black else TextSecondary
                        )
                    ) { Text("$n", style = MaterialTheme.typography.labelSmall) }
                }
                // 5+ opens popup
                val customSel = isCustomSelected
                Button(
                    onClick = { showMakerDialog = true },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (customSel) TorActive else SurfaceAlt,
                        contentColor   = if (customSel) androidx.compose.ui.graphics.Color.Black else TextSecondary
                    )
                ) { Text(if (customSel) "$makerCount" else "5+",
                        style = MaterialTheme.typography.labelSmall) }
            }
        }

        // 5+ custom hop count dialog
        if (showMakerDialog) {
            AlertDialog(
                onDismissRequest = { showMakerDialog = false },
                containerColor   = Surface,
                title = {
                    Text("Custom hop count",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter number of makers (5–20):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary)
                        OutlinedTextField(
                            value = customMakerInput,
                            onValueChange = { customMakerInput = it.filter { c -> c.isDigit() }.take(2) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = coinswapTextFieldColors(),
                            suffix = { Text("hops", color = TextSecondary) }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            customMakerInput.toIntOrNull()?.let { v ->
                                if (v in 1..20) makerCount = v
                            }
                            showMakerDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TorActive),
                        shape  = RoundedCornerShape(10.dp)
                    ) { Text("Set", color = androidx.compose.ui.graphics.Color.Black) }
                },
                dismissButton = {
                    TextButton(onClick = { showMakerDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        SectionCard {
            SectionLabel("NETWORK FEE")
            Text(networkFee.description,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NetworkFee.entries.forEach { tier ->
                    val sel = networkFee == tier
                    Button(
                        onClick = { networkFee = tier },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) TorActive else SurfaceAlt,
                            contentColor   = if (sel) androidx.compose.ui.graphics.Color.Black else TextSecondary
                        )
                    ) { Text(tier.label, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        ExpandableSection(
            title    = "MAKER FILTERS",
            subtitle = "${eligibleMakers.size} eligible makers",
            expanded = showMakerFilters,
            onToggle = { showMakerFilters = !showMakerFilters }
        ) {
            LabeledSwitch(
                label    = "Auto-select best makers",
                subtitle = "Picks lowest fee makers with valid fidelity bonds",
                checked  = autoSelectMakers,
                onChange = { autoSelectMakers = it }
            )

            HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 4.dp))

            Text("SWAP FEE RATE PER HOP",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = feeRatePerHop,
                    onValueChange = { feeRatePerHop = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = coinswapTextFieldColors(),
                    suffix = { Text("%", color = TextSecondary) },
                    label = { Text("Max fee per maker", style = MaterialTheme.typography.labelSmall) }
                )
            }

            Text("MIN FIDELITY BOND",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            OutlinedTextField(
                value = minFidelity,
                onValueChange = { minFidelity = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = coinswapTextFieldColors(),
                suffix = { Text("sats", color = TextSecondary) },
                label = { Text("Min fidelity bond", style = MaterialTheme.typography.labelSmall) }
            )

            Text("CUSTOM ONION ADDRESS",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            OutlinedTextField(
                value = customOnion,
                onValueChange = { customOnion = it },
                placeholder = { Text("Filter by maker onion address", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = coinswapTextFieldColors()
            )
        }

        // UTXO coin control — compact row + dialog
        var showUtxoDialog by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, Divider, RoundedCornerShape(12.dp))
                .clickable { showUtxoDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("COIN CONTROL",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${utxos.count { it.selected }} UTXOs selected  •  ${formatSats(selectedUtxoTotal)} sats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedUtxoTotal >= amountSatsLong && amountSatsLong > 0) TorActive else TextPrimary
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = "Select UTXOs",
                tint     = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        if (showUtxoDialog) {
            AlertDialog(
                onDismissRequest = { showUtxoDialog = false },
                containerColor   = Surface,
                title = {
                    Text("Select UTXOs",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tap to toggle. Selected total must cover swap amount.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary)
                        UtxoGrid(
                            utxos    = utxos,
                            onToggle = { i -> utxos[i] = utxos[i].copy(selected = !utxos[i].selected) }
                        )
                        Text("Total: ${formatSats(selectedUtxoTotal)} sats",
                            style = MaterialTheme.typography.labelSmall,
                            color = TorActive)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showUtxoDialog = false },
                        colors  = ButtonDefaults.buttonColors(containerColor = TorActive),
                        shape   = RoundedCornerShape(10.dp)
                    ) { Text("Done", color = androidx.compose.ui.graphics.Color.Black) }
                }
            )
        }

        if (amountSatsLong > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentPurple.copy(alpha = 0.12f))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🔒", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text("Encryption Layer Active",
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentPurple)
                        Text(
                            "Your coins will travel through $makerCount independent makers. " +
                                "No single party can link sender to receiver.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        val canSwap = amountSatsLong >= 100_000
            && eligibleMakers.size >= makerCount
            && selectedUtxoTotal >= amountSatsLong

        val validationMessage: String? = when {
            amountSatsLong <= 0 -> "Enter an amount to swap"
            amountSatsLong < 100_000 -> "Amount too low — minimum is 100,000 sats"
            eligibleMakers.size < makerCount ->
                "Not enough eligible makers (${eligibleMakers.size}/${makerCount}) — relax fee filter"
            selectedUtxoTotal < amountSatsLong && amountSatsLong > 0 ->
                "Selected UTXOs (${formatSats(selectedUtxoTotal)} sats) < swap amount"
            else -> null
        }

        if (validationMessage != null) {
            WarningBox(validationMessage)
        }

        if (amountSatsLong > 0) {
            SwapSummaryCard(
                amountSats    = amountSatsLong,
                makerCount    = makerCount,
                fundingTxCount= fundingTxCount,
                feePerHopSats = feePerHopSats,
                miningFeeSats = miningFeeSats,
                totalFeeSats  = totalFeeSats,
                receiveSats   = if (receiveAmtSats > 0) receiveAmtSats else 0L,
                estimatedMin  = estimatedMinutes,
                networkFeeRate= networkFee.satPerVbyte
            )
        }

        Button(
            onClick = {
                if (canSwap) swapState = SwapState.CONFIRMING
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canSwap) TorActive else SurfaceAlt,
                disabledContainerColor = SurfaceAlt
            )
        ) {
            Text(
                text  = "BEGIN SWAP",
                color = if (canSwap) androidx.compose.ui.graphics.Color.Black else TextSecondary,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────
    if (swapState == SwapState.CONFIRMING) {
        SwapConfirmDialog(
            amountSats    = amountSatsLong,
            makerCount    = makerCount,
            feePerHopSats = feePerHopSats,
            miningFeeSats = miningFeeSats,
            totalFeeSats  = totalFeeSats,
            receiveSats   = if (receiveAmtSats > 0) receiveAmtSats else 0L,
            onConfirm     = {
                swapState = SwapState.IN_PROGRESS
                swapViewModel.beginSwap(
                    amountSats      = amountSatsLong,
                    makerCount      = makerCount,
                    feeRateSatPerVb = networkFee.satPerVbyte,
                    selectedUtxos   = utxos.toList(),
                )
            },
            onDismiss     = { swapState = SwapState.IDLE }
        )
    }

    // ── In-progress overlay ───────────────────────────────────────────────────
    if (swapState == SwapState.IN_PROGRESS || swapState == SwapState.DONE) {
        SwapProgressOverlay(
            makerCount    = makerCount,
            amountSats    = amountSatsLong,
            isSwapping    = vmState.isSwapping,
            swapFinished  = swapState == SwapState.DONE,
            onClose       = { swapState = SwapState.IDLE },
            onViewReport  = { swapState = SwapState.IDLE; onNavigateToReports() }
        )
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun HowItWorksBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = TorActive, modifier = Modifier.size(16.dp))
                Text("How Coinswap works",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary)
            }
            HowItWorksStep("1", "Your coins are sent to Maker 1's swap address")
            HowItWorksStep("2", "Each maker atomically forwards to the next hop")
            HowItWorksStep("3", "Final maker sends fresh coins to your destination")
            HowItWorksStep("4", "All communication is encrypted so no one sees the full route")
        }
    }
}

@Composable
private fun HowItWorksStep(num: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(TorActive.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(num,
                style = MaterialTheme.typography.labelSmall,
                color = TorActive)
        }
        Text(text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun UtxoGrid(utxos: List<SwapUtxo>, onToggle: (Int) -> Unit) {
    val maxAmount = utxos.maxOfOrNull { it.amountSats } ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        utxos.chunked(2).forEachIndexed { rowIndex, rowUtxos ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowUtxos.forEachIndexed { colIndex, utxo ->
                    val index = rowIndex * 2 + colIndex
                    val fraction = (utxo.amountSats.toFloat() / maxAmount.toFloat()).coerceIn(0.35f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (utxo.selected) TorActive.copy(0.15f) else SurfaceAlt)
                                .border(
                                    1.dp,
                                    if (utxo.selected) TorActive else Divider,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onToggle(index) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    formatSats(utxo.amountSats),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Text("sats",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    utxo.txid,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (rowUtxos.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SwapSummaryCard(
    amountSats:     Long,
    makerCount:     Int,
    fundingTxCount: Int,
    feePerHopSats:  Long,
    miningFeeSats:  Long,
    totalFeeSats:   Long,
    receiveSats:    Long,
    estimatedMin:   Int,
    networkFeeRate: Int
) {
    SectionCard {
        Text("SWAP SUMMARY",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        // Estimated time chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(TorActive.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("EST. TIME  ~$estimatedMin min",
                style = MaterialTheme.typography.labelSmall,
                color = TorActive)
        }
        Spacer(Modifier.height(8.dp))
        SummaryRow("Swap amount",        "${formatSats(amountSats)} sats")
        SummaryRow("Makers",             "$makerCount makers")
        SummaryRow("Funding transactions","$fundingTxCount")
        SummaryRow("Avg funding TX size", "${TX_VBYTES} vB")
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
        SummaryRow("Est. maker fee",     "${formatSats(feePerHopSats * makerCount)} sats")
        SummaryRow("Network fee",        "${formatSats(miningFeeSats)} sats  ($networkFeeRate sat/vB)")
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
        SummaryRow("Total est. fee",     "${formatSats(totalFeeSats)} sats")
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
        SummaryRow("You receive",        "${formatSats(receiveSats)} sats", highlight = true)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) TorActive else TextPrimary)
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = TextSecondary
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun RoutePreview(makers: List<SwapMaker>, amountSats: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RouteNode("YOU", "Send ${formatSats(amountSats)} sats", TorActive, isFirst = true)
        makers.forEachIndexed { i, maker ->
            RouteArrow()
            RouteNode("MAKER ${i + 1}", maker.id, AccentPurple)
        }
        RouteArrow()
        RouteNode(
            "YOU",
            "Receive ≈${formatSats(amountSats - 1500L * makers.size)} sats (new address)",
            TorActive,
            isLast = true
        )
        Spacer(Modifier.height(4.dp))
        Text("Each arrow = 1 atomic swap. No maker sees the full path.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary)
    }
}

@Composable
private fun RouteNode(
    label: String,
    sub: String,
    color: androidx.compose.ui.graphics.Color,
    isFirst: Boolean = false,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(color)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}

@Composable
private fun RouteArrow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("↓  encrypted  ↓",
            style = MaterialTheme.typography.labelSmall,
            color = AccentPurple)
    }
}

@Composable
private fun WarningBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AccentAmber.copy(alpha = 0.10f))
            .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(message,
            style = MaterialTheme.typography.bodyMedium,
            color = AccentAmber)
    }
}

private fun formatSats(sats: Long): String = "%,d".format(sats)

// ── Confirmation dialog ──────────────────────────────────────────────────────

@Composable
private fun SwapConfirmDialog(
    amountSats: Long,
    makerCount: Int,
    feePerHopSats: Long,
    miningFeeSats: Long,
    totalFeeSats: Long,
    receiveSats: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        title = {
            Text("Confirm Swap",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Review the details before proceeding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                ConfirmRow("Amount to swap",    "${formatSats(amountSats)} sats")
                ConfirmRow("Hops (makers)",     "$makerCount")
                ConfirmRow("Maker fee per hop", "${formatSats(feePerHopSats)} sats")
                ConfirmRow("Network fee",       "${formatSats(miningFeeSats)} sats")
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 4.dp))
                ConfirmRow("Total fees",        "${formatSats(totalFeeSats)} sats")
                ConfirmRow("You receive",       "${formatSats(receiveSats)} sats", highlight = true)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = TorActive),
                shape   = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm and Start",
                    color = androidx.compose.ui.graphics.Color.Black,
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) TorActive else TextPrimary)
    }
}

// ── In-progress overlay ──────────────────────────────────────────────────────

@Composable
private fun SwapProgressOverlay(
    makerCount: Int,
    amountSats: Long,
    isSwapping: Boolean,
    swapFinished: Boolean,
    onClose: () -> Unit,
    onViewReport: () -> Unit
) {
    var completedHops by remember { mutableIntStateOf(0) }
    val finished = swapFinished
    val totalSteps = makerCount + 1

    // Animate hop progress while the real swap is running; stop when swap completes
    LaunchedEffect(isSwapping, swapFinished) {
        if (!isSwapping && !swapFinished) return@LaunchedEffect
        completedHops = 0
        if (isSwapping) {
            // Walk through hops at estimated pace; swap result from ViewModel overrides
            repeat(makerCount) {
                delay(3_000)
                if (completedHops < makerCount) completedHops++
            }
        }
        if (swapFinished) {
            completedHops = makerCount
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.97f))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stepLabel = if (finished) "STEP $totalSteps OF $totalSteps  •  SWAP COMPLETE"
                            else "STEP ${completedHops + 1} OF $totalSteps  •  IN PROGRESS"
            Text(stepLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (finished) TorActive else TextSecondary)
            /* View Swap Report removed — access via Reports button on swap screen */
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (finished) {
                Text("Swap Complete.",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary)
            } else {
                Text("Swap in Progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary)
                Text("${formatSats(amountSats)} sats routing through $makerCount makers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center)
            }

            // Ring visualization
            SwapRingVisualization(
                makerCount     = makerCount,
                completedHops  = completedHops,
                finished       = finished
            )

            if (finished) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick  = onViewReport,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
                    ) {
                        Text("View Swap Report",
                            color = androidx.compose.ui.graphics.Color.Black,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(
                        onClick  = onClose,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                    ) {
                        Text("Done",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapRingVisualization(
    makerCount: Int,
    completedHops: Int,
    finished: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // YOUR WALLET (start)
        StepNode(
            label  = "YOUR WALLET",
            sub    = "Sending",
            done   = true,
            active = false,
            isLast = false,
            color  = TorActive
        )

        // Each maker hop
        for (hop in 1..makerCount) {
            val done   = hop <= completedHops || finished
            val active = hop == completedHops + 1 && !finished
            StepConnector(done = done || (hop == 1 && !finished))
            StepNode(
                label  = "MAKER %02d".format(hop),
                sub    = when {
                    done   -> "Complete"
                    active -> "In progress..."
                    else   -> "Waiting"
                },
                done   = done,
                active = active,
                isLast = hop == makerCount,
                color  = when {
                    done   -> TorActive
                    active -> AccentPurple
                    else   -> TextSecondary
                }
            )
        }

        // YOUR WALLET (receive)
        StepConnector(done = finished)
        StepNode(
            label  = "YOUR WALLET",
            sub    = if (finished) "Received" else "Awaiting",
            done   = finished,
            active = false,
            isLast = true,
            color  = if (finished) TorActive else TextSecondary
        )
    }
}

@Composable
private fun StepNode(
    label: String, sub: String, done: Boolean, active: Boolean,
    isLast: Boolean, color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (done || active) 0.2f else 0.06f))
                .border(1.5.dp, color.copy(alpha = if (done || active) 0.8f else 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Text("✓", style = MaterialTheme.typography.labelSmall, color = color)
            } else if (active) {
                CircularProgressIndicator(
                    color    = color,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color.copy(0.3f)))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(sub,   style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun StepConnector(done: Boolean) {
    Row(modifier = Modifier.padding(start = 15.dp)) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(if (done) TorActive.copy(0.5f) else Divider)
        )
    }
}
