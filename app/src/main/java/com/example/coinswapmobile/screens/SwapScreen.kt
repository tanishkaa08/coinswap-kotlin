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
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.ui.theme.*

// ── Data models ──────────────────────────────────────────────────────────────

data class SwapMaker(
    val id: String,
    val feeRatePct: Double,   // e.g. 0.10
    val minSats: Long,         // minimum swap in sats
    val maxSats: Long,         // maximum swap in sats
    val liquiditySats: Long,
    val fidelityBondBtc: Double,
    val online: Boolean
)

data class SwapUtxo(
    val txid: String,
    val amountSats: Long,
    val confirmed: Boolean,
    var selected: Boolean
)

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun SwapScreen() {

    // ── State ─────────────────────────────────────────────────────────────────
    var amountSats        by remember { mutableStateOf("") }
    var makerCount        by remember { mutableIntStateOf(2) }
    var minFidelity       by remember { mutableStateOf("0.0") }
    var maxFeeRatePct     by remember { mutableStateOf("0.5") }
    var showAdvanced      by remember { mutableStateOf(false) }
    var showCoinControl   by remember { mutableStateOf(false) }
    var showRoutePreview  by remember { mutableStateOf(false) }
    var torEnabled        by remember { mutableStateOf(true) }
    var autoSelectMakers  by remember { mutableStateOf(true) }
    var broadcastCount    by remember { mutableIntStateOf(1) }

    // Stub maker list — in real app comes from FFI getMakers()
    val allMakers = remember {
        listOf(
            SwapMaker("mk1q...a3f", 0.10, 100_000, 10_000_000, 23_450_000, 0.005, true),
            SwapMaker("mk2p...7cd", 0.08, 500_000,  5_000_000, 11_200_000, 0.020, true),
            SwapMaker("mk3z...e2b", 0.12, 100_000, 20_000_000, 56_700_000, 0.050, true),
            SwapMaker("mk4w...9gh", 0.15,1_000_000,  2_500_000,  8_900_000, 0.001, false),
            SwapMaker("mk5r...j4i", 0.09, 200_000, 15_000_000, 32_100_000, 0.030, true),
        )
    }

    // Stub UTXOs — in real app comes from FFI syncWallet()
    val utxos = remember {
        mutableStateListOf(
            SwapUtxo("a1b2...3c4d", 4_500_000, true,  true),
            SwapUtxo("e5f6...7g8h", 7_950_000, true,  true),
            SwapUtxo("i9j0...k1l2", 100_000,   true,  false),
            SwapUtxo("m3n4...o5p6", 1_200_000, false, false),
        )
    }

    // Derived calculations
    val amountSatsLong  = amountSats.toLongOrNull() ?: 0L
    val amountBtc       = amountSatsLong / 100_000_000.0
    val eligibleMakers  = allMakers.filter {
        it.online &&
                it.feeRatePct <= (maxFeeRatePct.toDoubleOrNull() ?: 1.0) &&
                it.fidelityBondBtc >= (minFidelity.toDoubleOrNull() ?: 0.0) &&
                it.minSats <= amountSatsLong &&
                it.maxSats >= amountSatsLong
    }
    val selectedMakers  = eligibleMakers.take(makerCount)
    val totalSwapFeeSats = selectedMakers.sumOf { (it.feeRatePct / 100.0 * amountSatsLong).toLong() }
    val miningFeeSats   = 1500L * makerCount   // rough estimate per hop
    val totalFeeSats    = totalSwapFeeSats + miningFeeSats
    val receiveAmtSats  = amountSatsLong - totalFeeSats
    val selectedUtxoTotal = utxos.filter { it.selected }.sumOf { it.amountSats }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        Text("Coinswap",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary)
        Text(
            "Route your Bitcoin through multiple independent makers. " +
                    "No single party sees the full swap path.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        // ── How it works banner ───────────────────────────────────────────────
        HowItWorksBanner()

        // ── Amount ────────────────────────────────────────────────────────────
        SectionCard {
            SectionLabel("SWAP AMOUNT")
            OutlinedTextField(
                value         = amountSats,
                onValueChange = { amountSats = it.filter { c -> c.isDigit() } },
                placeholder   = { Text("e.g. 500000", color = TextSecondary) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors        = coinswapTextFieldColors(),
                suffix        = { Text("sats", color = TextSecondary) },
                supportingText = {
                    if (amountSatsLong > 0)
                        Text("≈ %.8f BTC".format(amountBtc), color = TextSecondary)
                }
            )
            if (amountSatsLong > 0 && amountSatsLong < 100_000) {
                WarningBox("Minimum swap amount is 100,000 sats (0.001 BTC)")
            }
        }

        // ── Maker count ───────────────────────────────────────────────────────
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MAKER COUNT",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    Text("Number of swap hops (makers in route)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                }
                Text("$makerCount",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TorActive)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 3, 4, 5).forEach { n ->
                    val sel = makerCount == n
                    Button(
                        onClick  = { makerCount = n },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (sel) TorActive else SurfaceAlt,
                            contentColor   = if (sel) androidx.compose.ui.graphics.Color.Black else TextSecondary
                        )
                    ) { Text("$n", style = MaterialTheme.typography.labelSmall) }
                }
            }
            // Privacy indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { i ->
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (i < makerCount) TorActive else Divider)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        makerCount <= 1 -> "Low privacy — single hop"
                        makerCount <= 2 -> "Medium privacy — 2 hops"
                        makerCount <= 3 -> "Good privacy — 3 hops"
                        else            -> "Maximum privacy — ${makerCount} hops"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TorActive
                )
            }
        }

        // ── Maker filter ──────────────────────────────────────────────────────
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MAKER FILTERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
                Text("${eligibleMakers.size} eligible",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (eligibleMakers.size >= makerCount) TorActive else TorInactive)
            }
            Spacer(Modifier.height(4.dp))

            // Auto-select toggle
            LabeledSwitch(
                label    = "Auto-select best makers",
                subtitle = "Picks lowest fee makers with valid fidelity bonds",
                checked  = autoSelectMakers,
                onChange = { autoSelectMakers = it }
            )

            HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 4.dp))

            // Max fee rate
            Text("MAX SWAP FEE RATE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = maxFeeRatePct,
                    onValueChange = { maxFeeRatePct = it },
                    modifier      = Modifier.weight(1f),
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors        = coinswapTextFieldColors(),
                    suffix        = { Text("%", color = TextSecondary) }
                )
                Text("per maker", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }

            Spacer(Modifier.height(4.dp))

            // Min fidelity bond
            Text("MIN FIDELITY BOND",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Text("Only use makers with at least this much locked as fidelity",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            OutlinedTextField(
                value         = minFidelity,
                onValueChange = { minFidelity = it },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors        = coinswapTextFieldColors(),
                suffix        = { Text("BTC", color = TextSecondary) }
            )
        }

        // ── Eligible makers list ──────────────────────────────────────────────
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AVAILABLE MAKERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
                Text("${allMakers.count { it.online }} online",
                    style = MaterialTheme.typography.labelSmall,
                    color = TorActive)
            }
            Spacer(Modifier.height(6.dp))
            allMakers.forEach { maker ->
                MakerFilterRow(
                    maker     = maker,
                    selected  = selectedMakers.contains(maker),
                    eligible  = eligibleMakers.contains(maker)
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // ── Coin control ──────────────────────────────────────────────────────
        ExpandableSection(
            title       = "COIN CONTROL",
            subtitle    = "Select which UTXOs to use for this swap",
            expanded    = showCoinControl,
            onToggle    = { showCoinControl = !showCoinControl }
        ) {
            Text("Selected: ${utxos.count { it.selected }} UTXOs  •  " +
                    "${selectedUtxoTotal.toSatsDisplay()} sats total",
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedUtxoTotal >= amountSatsLong && amountSatsLong > 0) TorActive else TextSecondary)
            Spacer(Modifier.height(8.dp))
            utxos.forEachIndexed { i, utxo ->
                CoinControlRow(
                    utxo     = utxo,
                    onToggle = { utxos[i] = utxo.copy(selected = !utxo.selected) }
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // ── Advanced options ──────────────────────────────────────────────────
        ExpandableSection(
            title    = "ADVANCED OPTIONS",
            subtitle = "Tor, broadcast redundancy, timeouts",
            expanded = showAdvanced,
            onToggle = { showAdvanced = !showAdvanced }
        ) {
            LabeledSwitch(
                label    = "Route over Tor",
                subtitle = "All maker communication goes through Tor (recommended)",
                checked  = torEnabled,
                onChange = { torEnabled = it }
            )
            if (!torEnabled) {
                WarningBox("Without Tor, your IP address is visible to makers. Privacy is significantly reduced.")
            }
            HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BROADCAST REDUNDANCY",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    Text("Broadcast final TX to $broadcastCount independent nodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                }
                Text("${broadcastCount}x",
                    style = MaterialTheme.typography.titleMedium,
                    color = TorActive)
            }
            Slider(
                value         = broadcastCount.toFloat(),
                onValueChange = { broadcastCount = it.toInt() },
                valueRange    = 1f..5f,
                steps         = 3,
                colors        = SliderDefaults.colors(
                    thumbColor       = TorActive,
                    activeTrackColor = TorActive
                )
            )
        }

        // ── Route preview ─────────────────────────────────────────────────────
        if (amountSatsLong >= 100_000 && eligibleMakers.size >= makerCount) {
            ExpandableSection(
                title    = "ROUTE PREVIEW",
                subtitle = "See how your swap will be routed",
                expanded = showRoutePreview,
                onToggle = { showRoutePreview = !showRoutePreview }
            ) {
                RoutePreview(makers = selectedMakers, amountSats = amountSatsLong)
            }
        }

        // ── Fee breakdown ─────────────────────────────────────────────────────
        if (amountSatsLong > 0) {
            SectionCard {
                Text("FEE BREAKDOWN",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                selectedMakers.forEachIndexed { i, maker ->
                    FeeRow(
                        label = "Maker ${i+1} (${maker.feeRatePct}%)",
                        value = "${(maker.feeRatePct / 100.0 * amountSatsLong).toLong()} sats"
                    )
                }
                FeeRow("Mining fees ($makerCount hops)", "$miningFeeSats sats")
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
                FeeRow("Total fees", "$totalFeeSats sats", highlight = true)
                FeeRow("You receive", "${if (receiveAmtSats > 0) receiveAmtSats else 0} sats",
                    highlight = true)
            }
        }

        // ── Encryption badge ──────────────────────────────────────────────────
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
                        "Your coins will travel through $makerCount independent makers over Tor. " +
                                "No single party can link sender to receiver.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // ── Validation & submit ───────────────────────────────────────────────
        val canSwap = amountSatsLong >= 100_000
                && eligibleMakers.size >= makerCount
                && (if (!showCoinControl) true else selectedUtxoTotal >= amountSatsLong)

        if (!canSwap && amountSatsLong > 0) {
            when {
                amountSatsLong < 100_000 ->
                    WarningBox("Amount too low. Minimum is 100,000 sats.")
                eligibleMakers.size < makerCount ->
                    WarningBox("Not enough eligible makers (${eligibleMakers.size}/${makerCount}). " +
                            "Try relaxing your fee or fidelity filter.")
                selectedUtxoTotal < amountSatsLong ->
                    WarningBox("Selected UTXOs (${selectedUtxoTotal} sats) are less than swap amount.")
            }
        }

        Button(
            onClick  = { /* TODO: call FFI ffi.beginSwap(params) */ },
            enabled  = canSwap,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
        ) {
            Text(
                text  = if (canSwap) "BEGIN SWAP →" else "FILL IN DETAILS",
                color = androidx.compose.ui.graphics.Color.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(16.dp))
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
            HowItWorksStep("4", "All communication happens over Tor — no one sees the full route")
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
            style    = MaterialTheme.typography.bodyMedium,
            color    = TextSecondary,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MakerFilterRow(maker: SwapMaker, selected: Boolean, eligible: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> TorActive.copy(alpha = 0.10f)
                    eligible -> SurfaceAlt
                    else     -> SurfaceAlt.copy(alpha = 0.5f)
                }
            )
            .border(
                1.dp,
                when {
                    selected -> TorActive
                    eligible -> Divider
                    else     -> Divider.copy(alpha = 0.3f)
                },
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (maker.online) TorActive else TorInactive)
        )
        Spacer(Modifier.width(8.dp))
        Text(maker.id,
            style    = MaterialTheme.typography.labelSmall,
            color    = if (eligible) TextPrimary else TextSecondary,
            modifier = Modifier.weight(1f))
        Text("${maker.feeRatePct}%",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) TorActive else TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text("Bond: ${maker.fidelityBondBtc} BTC",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text("✓",
                style = MaterialTheme.typography.labelSmall,
                color = TorActive)
        }
    }
}

@Composable
private fun CoinControlRow(utxo: SwapUtxo, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (utxo.selected) TorActive.copy(0.08f) else SurfaceAlt)
            .border(1.dp, if (utxo.selected) TorActive else Divider, RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked         = utxo.selected,
            onCheckedChange = { onToggle() },
            colors          = CheckboxDefaults.colors(
                checkedColor   = TorActive,
                uncheckedColor = TextSecondary
            )
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(utxo.txid,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary)
            Text(if (utxo.confirmed) "confirmed" else "unconfirmed",
                style = MaterialTheme.typography.labelSmall,
                color = if (utxo.confirmed) TorActive else AccentAmber)
        }
        Text("${utxo.amountSats.toSatsDisplay()} sats",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary)
    }
}

@Composable
private fun ExpandableSection(
    title:    String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content:  @Composable ColumnScope.() -> Unit
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
                Text(title,  style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
            enter   = expandVertically(),
            exit    = shrinkVertically()
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
        // You
        RouteNode("YOU", "Send $amountSats sats", TorActive, isFirst = true)
        makers.forEachIndexed { i, maker ->
            RouteArrow()
            RouteNode("MAKER ${i+1}", maker.id, AccentPurple)
        }
        RouteArrow()
        RouteNode("YOU", "Receive ≈${amountSats - 1500L * makers.size} sats (new address)", TorActive, isLast = true)
        Spacer(Modifier.height(4.dp))
        Text("Each arrow = 1 atomic swap. No maker sees the full path.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary)
    }
}

@Composable
private fun RouteNode(
    label: String,
    sub:   String,
    color: androidx.compose.ui.graphics.Color,
    isFirst: Boolean = false,
    isLast:  Boolean = false
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
            Text(sub,   style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
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
        Text("↓  Tor encrypted  ↓",
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

@Composable
private fun FeeRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) TorActive else TextPrimary)
    }
}

// Extension helpers
private fun Long.toSatsDisplay(): String {
    return if (this >= 1_000_000)
        "%.4f BTC".format(this / 100_000_000.0)
    else
        "%,d".format(this)
}