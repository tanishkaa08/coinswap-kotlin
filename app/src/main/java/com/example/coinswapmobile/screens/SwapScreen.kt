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
import com.example.coinswapmobile.components.OrbotRequiredDialog
import com.example.coinswapmobile.components.TorPromptBanner
import com.example.coinswapmobile.components.SectionCard
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.components.coinswapTextFieldColors
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.service.SwapForegroundService
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.viewmodel.SwapViewModel
import kotlinx.coroutines.launch

data class SwapMaker(
    val id: String,
    val feeRatePct: Double,
    val minSats: Long,
    val maxSats: Long,
    val liquiditySats: Long,
    val fidelityBondBtc: Double,
    val onionAddress: String,
    val online: Boolean,
    val baseFee: Long = 0,
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

enum class CoinPool { REGULAR, SWAP }

fun SwapUtxo.pool(): CoinPool =
    if (spendType == null || spendType == "SeedCoin") CoinPool.REGULAR else CoinPool.SWAP

private enum class NetworkFee(
    val label: String,
    val satPerVbyte: Int,
    val description: String
) {
    LOW(   "Low",    1, "1 sat/vB"),
    MEDIUM("Medium", 2, "2 sat/vB"),
    HIGH(  "High",   4, "4 sat/vB"),
}

private const val TX_VBYTES = 225L
private const val FIXED_MAKER_COUNT = 2
private const val FIXED_TX_COUNT = 1

private enum class SwapState { IDLE, CONFIRMING, IN_PROGRESS, DONE }

@Composable
fun SwapScreen(
    onNavigateToReports: () -> Unit = {},
    onSwapFailed: () -> Unit = {},
    swapViewModel: SwapViewModel = viewModel(),
) {
    val context = LocalContext.current
    val vmState by swapViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showOrbotDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) swapViewModel.loadWalletData()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var amountSats by remember { mutableStateOf("") }
    var networkFee by remember { mutableStateOf(NetworkFee.HIGH) }
    var swapState by remember { mutableStateOf(SwapState.IDLE) }

    val allMakers = vmState.makers

    val regularUtxos = remember(vmState.utxos) { vmState.utxos.filter { it.pool() == CoinPool.REGULAR } }
    val swapUtxos    = remember(vmState.utxos) { vmState.utxos.filter { it.pool() == CoinPool.SWAP } }
    val regularTotal = remember(regularUtxos) { regularUtxos.sumOf { it.amountSats } }
    val swapTotal    = remember(swapUtxos) { swapUtxos.sumOf { it.amountSats } }

    LaunchedEffect(vmState.isSwapping, vmState.lastSwapId, vmState.swapError) {
        when {
            // Always show progress UI when a swap is running (covers confirm race + resume).
            vmState.isSwapping && swapState != SwapState.DONE ->
                swapState = SwapState.IN_PROGRESS
            vmState.lastSwapId != null && !vmState.isSwapping && vmState.swapError == null &&
                (swapState == SwapState.IN_PROGRESS || swapState == SwapState.DONE) ->
                swapState = SwapState.DONE
            // Keep failed overlay open until user taps Close / Recover.
            !vmState.isSwapping && vmState.swapError != null && swapState == SwapState.IN_PROGRESS ->
                Unit
        }
    }

    val amountSatsLong = amountSats.toLongOrNull() ?: 0L
    val makerCount = FIXED_MAKER_COUNT
    val txCount = FIXED_TX_COUNT
    val routeableMakers = allMakers
    val onlineMakers = routeableMakers.filter { it.online }
    val eligibleMakers = routeableMakers.filter {
        CoinswapRepository.makerFitsAmount(
            online = it.online,
            minSats = it.minSats,
            maxSats = it.maxSats,
            liquiditySats = it.liquiditySats,
            amountSats = amountSatsLong,
        )
    }
    val selectedMakers = eligibleMakers.take(makerCount)
    val autoBestPool = maxOf(regularTotal, swapTotal)
    val walletCap = CoinswapRepository.maxSwappableSats(autoBestPool)
    val makerCappedMax = CoinswapRepository.maxAmountFittingMakerCount(
        walletCap = walletCap,
        needed = makerCount,
        makers = routeableMakers.map { m ->
            Triple(
                m.online,
                m.minSats,
                CoinswapRepository.effectiveMakerMax(m.maxSats, m.liquiditySats),
            )
        },
    )
    val swappableSats = if (makerCappedMax > 0L) minOf(walletCap, makerCappedMax) else walletCap
    val feePerMakerSats = if (selectedMakers.isNotEmpty()) {
        selectedMakers.sumOf { m ->
            m.baseFee + ((amountSatsLong * m.feeRatePct) / 100.0).toLong()
        } / selectedMakers.size
    } else {
        0L
    }
    val totalSwapFeeSats = if (selectedMakers.isNotEmpty()) {
        selectedMakers.sumOf { m ->
            m.baseFee + ((amountSatsLong * m.feeRatePct) / 100.0).toLong()
        }
    } else {
        feePerMakerSats * makerCount
    }
    val fundingTxCount   = (makerCount + 1) * txCount
    val miningFeeSats    = networkFee.satPerVbyte * TX_VBYTES * fundingTxCount
    val totalFeeSats     = totalSwapFeeSats + miningFeeSats
    val receiveAmtSats   = amountSatsLong - totalFeeSats

    val estimatedMinutes = makerCount * 10

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Coinswap",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary)
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
            TorPromptBanner(
                onAction = {
                    scope.launch {
                        TorManager.ensureRunning(context)
                        swapViewModel.loadWalletData()
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        SectionCard {
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = coinswapTextFieldColors(),
                suffix = { Text("sats", color = TextSecondary) }
            )
            if (amountSatsLong > 0 && amountSatsLong < CoinswapRepository.MIN_SWAP_SATS) {
                WarningBox("Minimum 100,000 sats")
            }
        }

        SectionCard {
            SectionLabel("ROUTE")
            SummaryRow("Makers needed", "$makerCount")
            SummaryRow("Online now", "${onlineMakers.size}")
            SummaryRow("Fit this amount", "${eligibleMakers.size}")
        }

        SectionCard {
            SectionLabel("EST. NETWORK FEE")
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

        val fundsOk = CoinswapRepository.poolCanFundSwap(autoBestPool, amountSatsLong)
        val canSwap = vmState.capabilities?.coinswap == true
            && !vmState.isSwapping
            && amountSatsLong >= CoinswapRepository.MIN_SWAP_SATS
            && eligibleMakers.size >= makerCount
            && fundsOk
            && receiveAmtSats > 0L

        val validationMessage: String? = when {
            vmState.isSwapping -> "Swap already running"
            amountSatsLong <= 0 -> "Enter an amount"
            amountSatsLong < CoinswapRepository.MIN_SWAP_SATS -> "Minimum 100,000 sats"
            onlineMakers.size < makerCount ->
                "Need $makerCount online makers"
            eligibleMakers.size < makerCount ->
                "Need $makerCount makers for this amount"
            !fundsOk ->
                "Need ${formatSats(amountSatsLong + CoinswapRepository.SWAP_PREPARE_RESERVE_SATS)} sats available"
            receiveAmtSats <= 0L -> "Fees exceed amount"
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
                if (!vmState.torReachable) {
                    scope.launch {
                        TorManager.ensureRunning(context)
                        swapViewModel.loadWalletData()
                    }
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

    if (swapState == SwapState.CONFIRMING) {
        SwapConfirmDialog(
            amountSats    = amountSatsLong,
            makerCount    = makerCount,
            txCount       = txCount,
            feePerMakerSats = feePerMakerSats,
            miningFeeSats = miningFeeSats,
            totalFeeSats  = totalFeeSats,
            receiveSats   = if (receiveAmtSats > 0) receiveAmtSats else 0L,
            manualCoins   = 0,
            onConfirm     = {
                swapState = SwapState.IN_PROGRESS
                swapViewModel.beginSwap(
                    amountSats      = amountSatsLong,
                    makerCount      = makerCount,
                    txCount         = txCount,
                    manual          = false,
                    selectedUtxos   = emptyList(),
                )
            },
            onDismiss     = { swapState = SwapState.IDLE }
        )
    }

    if (swapState == SwapState.IN_PROGRESS || swapState == SwapState.DONE) {
        SwapProgressOverlay(
            makerCount    = makerCount,
            amountSats    = amountSatsLong,
            isSwapping    = vmState.isSwapping,
            swapFinished  = swapState == SwapState.DONE,
            swapPhase     = vmState.swapPhase,
            swapError     = vmState.swapError,
            onClose       = {
                SwapForegroundService.stop(context.applicationContext)
                swapState = SwapState.IDLE
                swapViewModel.clearSwapResult()
            },
            onRecover     = {
                SwapForegroundService.stop(context.applicationContext)
                swapState = SwapState.IDLE
                swapViewModel.clearSwapResult()
                onSwapFailed()
            },
            onStopSwap    = {
                swapViewModel.cancelPreparingSwap()
            },
            onViewReport  = {
                swapState = SwapState.IDLE
                swapViewModel.clearSwapResult()
                onNavigateToReports()
            }
        )
    }

    OrbotRequiredDialog(
        visible = showOrbotDialog,
        reason = "Start Orbot with SocksPort 9050.",
        onDismiss = { showOrbotDialog = false },
        onOpened = {
            scope.launch { swapViewModel.loadWalletData() }
        },
    )
}

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
        HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
        SummaryRow("Est. maker fee",     "${formatSats(feePerMakerSats * makerCount)} sats")
        SummaryRow("Est. network fee",   "${formatSats(miningFeeSats)} sats  ($networkFeeRate sat/vB)")
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
                ConfirmRow("Amount to swap",    "${formatSats(amountSats)} sats")
                ConfirmRow("Makers",            "$makerCount")
                if (txCount > 1) ConfirmRow("Transaction splits", "$txCount")
                ConfirmRow("Maker fee per maker", "${formatSats(feePerMakerSats)} sats")
                ConfirmRow("Est. network fee",  "${formatSats(miningFeeSats)} sats")
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

private enum class SwapStage { PREPARING, SWAPPING, DONE, FAILED }

private fun swapStageOf(
    isSwapping: Boolean,
    finished: Boolean,
    failed: Boolean,
    phase: String?,
): SwapStage = when {
    finished -> SwapStage.DONE
    failed -> SwapStage.FAILED
    !isSwapping -> SwapStage.PREPARING
    phase != null && (
        phase.contains("Syncing", ignoreCase = true) ||
            phase.contains("Preparing", ignoreCase = true)
        ) -> SwapStage.PREPARING
    else -> SwapStage.SWAPPING
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
    onStopSwap: () -> Unit = {},
    onRecover: () -> Unit = {},
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
                Text("${formatSats(amountSats)} sats / $makerCount makers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center)
                swapPhase?.let { phase ->
                    Text(phase,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center)
                }
            }

            SwapRingVisualization(
                makerCount = makerCount,
                stage      = stage
            )

            if (!finished && !failed && stage == SwapStage.PREPARING) {
                OutlinedButton(
                    onClick  = onStopSwap,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, TorInactive)
                ) {
                    Text(
                        "Stop swap",
                        color = TorInactive,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onRecover,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    ) {
                        Text(
                            "Open Recovery",
                            color = androidx.compose.ui.graphics.Color.Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
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
        val sendDone = swapping || finished
        StepNode(
            label  = "YOUR WALLET",
            sub    = if (sendDone) "Sent" else "Preparing…",
            done   = sendDone,
            active = stage == SwapStage.PREPARING,
            color  = if (sendDone) TorActive else AccentPurple
        )

        for (hop in 1..makerCount) {
            val done   = finished
            val active = swapping
            StepConnector(done = sendDone)
            StepNode(
                label  = "MAKER %02d".format(hop),
                sub    = when {
                    done   -> "Done"
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

        StepConnector(done = finished)
        StepNode(
            label  = "YOUR WALLET",
            sub    = when {
                finished -> "Received"
                swapping -> "Settling…"
                else     -> "Waiting"
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
