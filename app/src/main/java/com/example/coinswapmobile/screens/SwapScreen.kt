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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.components.LabeledSwitch
import com.example.coinswapmobile.components.OrbotHelper
import com.example.coinswapmobile.components.OrbotInstallDialog
import com.example.coinswapmobile.components.OrbotPromptBanner
import com.example.coinswapmobile.components.SectionCard
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.components.coinswapTextFieldColors
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.SwapViewModel

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
    val vout: Int,
    val amountSats: Long,
    val confirmed: Boolean,
    val spendable: Boolean = true,
    val spendType: String? = null,
    var selected: Boolean,
) {
    val outpoint: String get() = "$txid:$vout"
}

/**
 * Regular (seed) coins and coins from a prior coinswap are separate pools.
 */
enum class CoinPool { REGULAR, SWAP }

fun SwapUtxo.pool(): CoinPool =
    if (spendType == null || spendType == "SeedCoin") CoinPool.REGULAR else CoinPool.SWAP

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
    val context = LocalContext.current
    val vmState by swapViewModel.uiState.collectAsState()
    var showOrbotDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) swapViewModel.loadWalletData()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OrbotInstallDialog(visible = showOrbotDialog, onDismiss = { showOrbotDialog = false })

    var amountSats       by remember { mutableStateOf("") }
    var makerCount       by remember { mutableIntStateOf(1) }
    var txCountInput     by remember { mutableStateOf("1") }
    var networkFee       by remember { mutableStateOf(NetworkFee.MEDIUM) }
    var minFidelity      by remember { mutableStateOf("0") }
    var feeRatePerHop    by remember { mutableStateOf("0.10") }
    var customOnion      by remember { mutableStateOf("") }
    var showMakerFilters by remember { mutableStateOf(false) }

    var autoSelectMakers by remember { mutableStateOf(true) }
    var swapState        by remember { mutableStateOf(SwapState.IDLE) }

    var useManualUtxos   by remember { mutableStateOf(false) }
    var coinPool         by remember { mutableStateOf(CoinPool.REGULAR) }
    var selectedOutpoints by remember { mutableStateOf<Set<String>>(emptySet()) }

    val allMakers = vmState.makers

    val regularUtxos = remember(vmState.utxos) { vmState.utxos.filter { it.pool() == CoinPool.REGULAR } }
    val swapUtxos    = remember(vmState.utxos) { vmState.utxos.filter { it.pool() == CoinPool.SWAP } }
    val regularTotal = remember(regularUtxos) { regularUtxos.sumOf { it.amountSats } }
    val swapTotal    = remember(swapUtxos) { swapUtxos.sumOf { it.amountSats } }

    LaunchedEffect(regularUtxos.isEmpty(), swapUtxos.isEmpty()) {
        coinPool = if (regularUtxos.isEmpty() && swapUtxos.isNotEmpty()) CoinPool.SWAP else CoinPool.REGULAR
    }

    LaunchedEffect(vmState.isSwapping, vmState.lastSwapId, vmState.swapError) {
        if (swapState != SwapState.IN_PROGRESS && swapState != SwapState.DONE) return@LaunchedEffect
        when {
            vmState.lastSwapId != null && !vmState.isSwapping && vmState.swapError == null ->
                swapState = SwapState.DONE
        }
    }

    val amountSatsLong   = amountSats.toLongOrNull() ?: 0L
    val txCount          = txCountInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
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
    val feePerMakerSats  = (feeRatePerHopPct / 100.0 * amountSatsLong).toLong()
    val totalSwapFeeSats = feePerMakerSats * makerCount
    // (makers + 1) funding txs × 225 vbytes × sat/vB, scaled by tx splits.
    val fundingTxCount   = (makerCount + 1) * txCount
    val miningFeeSats    = networkFee.satPerVbyte * TX_VBYTES * fundingTxCount
    val totalFeeSats     = totalSwapFeeSats + miningFeeSats
    val receiveAmtSats   = amountSatsLong - totalFeeSats

    val activePoolUtxos  = if (coinPool == CoinPool.REGULAR) regularUtxos else swapUtxos
    val manualSelected   = activePoolUtxos.filter { it.outpoint in selectedOutpoints }
    val manualTotal      = manualSelected.sumOf { it.amountSats }
    val autoBestPool     = maxOf(regularTotal, swapTotal)

    val estimatedMinutes = makerCount * 10
    val swappableSats    = autoBestPool

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

        vmState.swapError?.let { err ->
            Text(
                err,
                style = MaterialTheme.typography.labelSmall,
                color = TorInactive,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        vmState.swapPhase?.let { phase ->
            Text(
                "Status: $phase",
                style = MaterialTheme.typography.labelSmall,
                color = TorActive,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (!vmState.torReachable) {
            OrbotPromptBanner(
                onInstallClick = {
                    if (OrbotHelper.isOrbotInstalled(context)) {
                        OrbotHelper.openOrbotApp(context)
                    } else {
                        showOrbotDialog = true
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        SectionCard {
            // Amount label (SectionLabel is a column; keep it out of this Row)
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
        val presetCounts = listOf(1, 2, 3, 4)
        val isCustomSelected = makerCount !in presetCounts

        SectionCard {
            Text("MAKER COUNT",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Text("Number of makers your coins route through",
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
                    Text("Custom maker count",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter number of makers (1-20):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary)
                        OutlinedTextField(
                            value = customMakerInput,
                            onValueChange = { customMakerInput = it.filter { c -> c.isDigit() }.take(2) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = coinswapTextFieldColors(),
                            suffix = { Text("makers", color = TextSecondary) }
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

            Text("MAX FEE PER MAKER",
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

            Text("TRANSACTION SPLITS (tx_count)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Text("Splits each funding into multiple txs (Taproot). Leave at 1 for Legacy.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            OutlinedTextField(
                value = txCountInput,
                onValueChange = { txCountInput = it.filter { c -> c.isDigit() }.take(2) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = coinswapTextFieldColors(),
                suffix = { Text("txs", color = TextSecondary) },
                label = { Text("tx_count", style = MaterialTheme.typography.labelSmall) }
            )

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

        // Coin control
        SectionCard {
            LabeledSwitch(
                label    = "Choose specific coins",
                subtitle = "Off: automatic. On: pick which coins to spend.",
                checked  = useManualUtxos,
                onChange = {
                    useManualUtxos = it
                    if (!it) selectedOutpoints = emptySet()
                },
            )

            if (!useManualUtxos) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Wallet selects coins from one pool. Available: ${formatSats(autoBestPool)} sats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                Spacer(Modifier.height(10.dp))

                // Regular and swap coins stay in separate pools.
                if (regularUtxos.isNotEmpty() && swapUtxos.isNotEmpty()) {
                    Text("COIN POOL",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PoolChip(
                            label    = "Regular (${regularUtxos.size})",
                            selected = coinPool == CoinPool.REGULAR,
                            modifier = Modifier.weight(1f),
                            onClick  = { coinPool = CoinPool.REGULAR; selectedOutpoints = emptySet() },
                        )
                        PoolChip(
                            label    = "From swaps (${swapUtxos.size})",
                            selected = coinPool == CoinPool.SWAP,
                            modifier = Modifier.weight(1f),
                            onClick  = { coinPool = CoinPool.SWAP; selectedOutpoints = emptySet() },
                        )
                    }
                    Text(
                        "Regular and swap coins cannot be mixed in one swap.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { selectedOutpoints = activePoolUtxos.map { it.outpoint }.toSet() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Select all", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { selectedOutpoints = emptySet() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
                }
                Spacer(Modifier.height(8.dp))

                if (activePoolUtxos.isEmpty()) {
                    Text("No spendable coins in this pool.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activePoolUtxos.forEach { utxo ->
                            val isSelected = utxo.outpoint in selectedOutpoints
                            UtxoListRow(
                                utxo = utxo.copy(selected = isSelected),
                                onToggle = {
                                    selectedOutpoints = if (isSelected) {
                                        selectedOutpoints - utxo.outpoint
                                    } else {
                                        selectedOutpoints + utxo.outpoint
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Forcing ${manualSelected.size} coin(s) • ${formatSats(manualTotal)} sats",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (manualTotal >= amountSatsLong && amountSatsLong > 0) TorActive else TextPrimary,
                )
            }
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

        val fundsOk = if (useManualUtxos) manualTotal >= amountSatsLong else autoBestPool >= amountSatsLong
        val canSwap = vmState.capabilities?.coinswap == true
            && amountSatsLong >= 100_000
            && eligibleMakers.size >= makerCount
            && fundsOk

        val validationMessage: String? = when {
            amountSatsLong <= 0 -> "Enter an amount to swap"
            amountSatsLong < 100_000 -> "Amount too low; minimum is 100,000 sats"
            eligibleMakers.size < makerCount ->
                "Not enough eligible makers (${eligibleMakers.size}/${makerCount}); relax fee filter"
            useManualUtxos && manualSelected.isEmpty() ->
                "Select at least one coin"
            useManualUtxos && manualTotal < amountSatsLong ->
                "Selected coins (${formatSats(manualTotal)} sats) < swap amount"
            !useManualUtxos && autoBestPool < amountSatsLong ->
                "Not enough spendable coins in one pool"
            else -> null
        }

        if (validationMessage != null) {
            WarningBox(validationMessage)
        }

        if (amountSatsLong > 0) {
            SwapSummaryCard(
                amountSats     = amountSatsLong,
                makerCount     = makerCount,
                txCount        = txCount,
                fundingTxCount = fundingTxCount,
                feePerMakerSats= feePerMakerSats,
                miningFeeSats  = miningFeeSats,
                totalFeeSats   = totalFeeSats,
                receiveSats    = if (receiveAmtSats > 0) receiveAmtSats else 0L,
                estimatedMin   = estimatedMinutes,
                networkFeeRate = networkFee.satPerVbyte
            )
        }

        Button(
            onClick = {
                if (!OrbotHelper.isOrbotInstalled(context)) {
                    showOrbotDialog = true
                    return@Button
                }
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
            txCount       = txCount,
            feePerMakerSats = feePerMakerSats,
            miningFeeSats = miningFeeSats,
            totalFeeSats  = totalFeeSats,
            receiveSats   = if (receiveAmtSats > 0) receiveAmtSats else 0L,
            manualCoins   = if (useManualUtxos) manualSelected.size else 0,
            onConfirm     = {
                swapState = SwapState.IN_PROGRESS
                swapViewModel.beginSwap(
                    amountSats      = amountSatsLong,
                    makerCount      = makerCount,
                    txCount         = txCount,
                    feeRateSatPerVb = networkFee.satPerVbyte,
                    manual          = useManualUtxos,
                    selectedUtxos   = if (useManualUtxos) manualSelected else emptyList(),
                    makerIds        = if (customOnion.isNotBlank()) {
                        selectedMakers.map { it.onionAddress }
                    } else {
                        emptyList()
                    },
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
            swapPhase     = vmState.swapPhase,
            swapError     = vmState.swapError,
            onClose       = {
                swapState = SwapState.IDLE
                swapViewModel.clearSwapResult()
            },
            onViewReport  = {
                swapState = SwapState.IDLE
                swapViewModel.clearSwapResult()
                onNavigateToReports()
            }
        )
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun UtxoListRow(utxo: SwapUtxo, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (utxo.selected) TorActive.copy(alpha = 0.12f) else SurfaceAlt)
            .border(
                1.dp,
                if (utxo.selected) TorActive else Divider,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${utxo.txid.take(16)}…:${utxo.vout}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (utxo.confirmed) "Confirmed" else "Unconfirmed",
                style = MaterialTheme.typography.labelSmall,
                color = if (utxo.confirmed) TorActive else TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${formatSats(utxo.amountSats)} sats",
            style = MaterialTheme.typography.bodyMedium,
            color = if (utxo.selected) TorActive else TextPrimary,
        )
    }
}

@Composable
private fun SwapSummaryCard(
    amountSats:      Long,
    makerCount:      Int,
    txCount:         Int,
    fundingTxCount:  Int,
    feePerMakerSats: Long,
    miningFeeSats:   Long,
    totalFeeSats:    Long,
    receiveSats:     Long,
    estimatedMin:    Int,
    networkFeeRate:  Int
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
        SummaryRow("Makers",             "$makerCount")
        if (txCount > 1) SummaryRow("Transaction splits", "$txCount")
        SummaryRow("Funding transactions","$fundingTxCount")
        SummaryRow("Avg funding TX size", "${TX_VBYTES} vB")
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
        SummaryRow("Est. maker fee",     "${formatSats(feePerMakerSats * makerCount)} sats")
        SummaryRow("Network fee",        "${formatSats(miningFeeSats)} sats  ($networkFeeRate sat/vB)")
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
        SummaryRow("Total est. fee",     "${formatSats(totalFeeSats)} sats")
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
        SummaryRow("You receive",        "${formatSats(receiveSats)} sats", highlight = true)
    }
}

@Composable
private fun PoolChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) TorActive else SurfaceAlt,
            contentColor   = if (selected) androidx.compose.ui.graphics.Color.Black else TextSecondary,
        ),
    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
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
    txCount: Int,
    feePerMakerSats: Long,
    miningFeeSats: Long,
    totalFeeSats: Long,
    receiveSats: Long,
    manualCoins: Int,
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
                ConfirmRow("Makers",            "$makerCount")
                if (txCount > 1) ConfirmRow("Transaction splits", "$txCount")
                ConfirmRow("Maker fee per maker", "${formatSats(feePerMakerSats)} sats")
                ConfirmRow("Network fee",       "${formatSats(miningFeeSats)} sats")
                ConfirmRow("Coin selection", if (manualCoins > 0) "Manual ($manualCoins coins)" else "Automatic")
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

private enum class SwapStage { PREPARING, SWAPPING, DONE, FAILED }

private fun swapStageOf(
    isSwapping: Boolean,
    finished: Boolean,
    failed: Boolean,
    phase: String?,
): SwapStage = when {
    finished -> SwapStage.DONE
    failed -> SwapStage.FAILED
    phase != null && phase.contains("Executing", ignoreCase = true) -> SwapStage.SWAPPING
    else -> SwapStage.PREPARING
}

@Composable
private fun SwapProgressOverlay(
    makerCount: Int,
    amountSats: Long,
    isSwapping: Boolean,
    swapFinished: Boolean,
    swapPhase: String?,
    swapError: String?,
    onClose: () -> Unit,
    onViewReport: () -> Unit
) {
    val finished = swapFinished
    val failed = !isSwapping && !finished && swapError != null
    val stage = swapStageOf(isSwapping, finished, failed, swapPhase)

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
            val stepLabel = when (stage) {
                SwapStage.DONE -> "SWAP COMPLETE"
                SwapStage.FAILED -> "SWAP FAILED"
                SwapStage.SWAPPING -> "EXECUTING COINSWAP"
                SwapStage.PREPARING -> "PREPARING"
            }
            Text(stepLabel,
                style = MaterialTheme.typography.labelSmall,
                color = when (stage) {
                    SwapStage.DONE -> TorActive
                    SwapStage.FAILED -> TorInactive
                    else -> TextSecondary
                })
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
            } else if (failed) {
                Text("Swap Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = TorInactive)
                Text(
                    swapError ?: "Unknown error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(if (stage == SwapStage.PREPARING) "Preparing Swap" else "Swap in Progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary)
                Text("${formatSats(amountSats)} sats routing through $makerCount makers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center)
                swapPhase?.let { phase ->
                    Text(phase,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center)
                }
                if (stage == SwapStage.SWAPPING) {
                    Text(
                        "Broadcasting funding and waiting for confirmations. Keep the app open.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // wallet -> makers -> wallet
            SwapRingVisualization(
                makerCount = makerCount,
                stage      = stage
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
            } else if (failed) {
                OutlinedButton(
                    onClick  = onClose,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                ) {
                    Text("Close",
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SwapRingVisualization(
    makerCount: Int,
    stage: SwapStage
) {
    val finished = stage == SwapStage.DONE
    val swapping = stage == SwapStage.SWAPPING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Wallet out (after funding broadcast)
        val sendDone = swapping || finished
        StepNode(
            label  = "YOUR WALLET",
            sub    = if (sendDone) "Funds sent" else "Preparing…",
            done   = sendDone,
            active = stage == SwapStage.PREPARING,
            color  = if (sendDone) TorActive else AccentPurple
        )

        // Makers (relaying while swap runs)
        for (hop in 1..makerCount) {
            val done   = finished
            val active = swapping
            StepConnector(done = sendDone)
            StepNode(
                label  = "MAKER %02d".format(hop),
                sub    = when {
                    done   -> "Complete"
                    active -> "Relaying…"
                    else   -> "Waiting"
                },
                done   = done,
                active = active,
                color  = when {
                    done   -> TorActive
                    active -> AccentPurple
                    else   -> TextSecondary
                }
            )
        }

        // Wallet receive (settlement)
        StepConnector(done = finished)
        StepNode(
            label  = "YOUR WALLET",
            sub    = when {
                finished -> "Received"
                swapping -> "Settling (confirmations)…"
                else     -> "Awaiting"
            },
            done   = finished,
            active = swapping,
            color  = when {
                finished -> TorActive
                swapping -> AccentPurple
                else     -> TextSecondary
            }
        )
    }
}

@Composable
private fun StepNode(
    label: String, sub: String, done: Boolean, active: Boolean,
    color: androidx.compose.ui.graphics.Color
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
