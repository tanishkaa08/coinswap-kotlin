package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.model.SwapReportUiModel
import com.example.coinswapmobile.model.TxUiModel
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.WalletHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun WalletHistoryScreen(
    historyViewModel: WalletHistoryViewModel = viewModel(),
) {
    val vmState by historyViewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) historyViewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val failed = remember(vmState.swaps) {
        vmState.swaps.filter { it.status == SwapReportUiModel.Status.FAILED }
    }
    val recovered = remember(vmState.swaps) {
        vmState.swaps.filter { it.status == SwapReportUiModel.Status.RECOVERED }
    }
    val completed = remember(vmState.swaps) {
        vmState.swaps.filter { it.status == SwapReportUiModel.Status.COMPLETED }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("History", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }

        vmState.errorMessage?.let { msg ->
            item {
                Text(msg, style = MaterialTheme.typography.labelSmall, color = TorInactive)
            }
        }

        if (vmState.isLoading && vmState.swaps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = TorActive,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        } else {
            if (failed.isNotEmpty()) {
                item { SectionLabel("FAILED") }
                items(failed, key = { "fail-${it.id}-${it.startTimestamp}" }) {
                    SwapCard(it)
                }
            }
            if (recovered.isNotEmpty()) {
                item { SectionLabel("RECOVERED") }
                items(recovered, key = { "rec-${it.id}-${it.startTimestamp}" }) {
                    SwapCard(it)
                }
            }
            if (completed.isNotEmpty()) {
                item { SectionLabel("COMPLETED") }
                items(completed, key = { "ok-${it.id}-${it.startTimestamp}" }) {
                    SwapCard(it)
                }
            }
            if (vmState.swaps.isEmpty()) {
                item { EmptyHint("No swaps yet") }
            }

            item { SectionLabel("TRANSACTIONS") }
            when {
                vmState.isLoadingTxs && vmState.transactions.isEmpty() -> {
                    item { EmptyHint("Loading…") }
                }
                vmState.transactions.isEmpty() -> {
                    item { EmptyHint("No transactions yet") }
                }
                else -> {
                    itemsIndexed(
                        items = vmState.transactions,
                        key = { i, tx -> "tx-$i-${tx.txid}-${tx.amountSats}-${tx.confirmations}" },
                    ) { _, tx -> TxRow(tx) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SwapCard(swap: SwapReportUiModel) {
    val statusColor = when (swap.status) {
        SwapReportUiModel.Status.COMPLETED -> TorActive
        SwapReportUiModel.Status.RECOVERED -> AccentAmber
        SwapReportUiModel.Status.FAILED -> TorInactive
    }
    val timeLabel = formatUnix(swap.startTimestamp)
    val meta = buildList {
        if (swap.makerCount > 0) add("${swap.makerCount} makers")
        if (swap.totalFeeSats > 0) add("%,d fee".format(swap.totalFeeSats))
        if (timeLabel != null) add(timeLabel)
    }.joinToString(" / ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(statusColor),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = if (swap.amountSats > 0) "%,d sats".format(swap.amountSats) else "-",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (swap.status == SwapReportUiModel.Status.FAILED && !swap.errorMessage.isNullOrBlank()) {
                Text(
                    swap.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = TorInactive,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class TxKind { IN, OUT, MINING }

private fun TxUiModel.kind(): TxKind {
    val cat = category?.lowercase().orEmpty()
    return when {
        cat.contains("generate") || cat.contains("immature") || cat.contains("coinbase") -> TxKind.MINING
        direction == "outgoing" || direction == "sent" || amountSats < 0 -> TxKind.OUT
        else -> TxKind.IN
    }
}

@Composable
private fun TxRow(tx: TxUiModel) {
    val kind = tx.kind()
    val absAmount = abs(tx.amountSats)
    val (label, amountText, amountColor) = when (kind) {
        TxKind.OUT -> Triple("Sent", "−%,d".format(absAmount), AccentAmber)
        TxKind.MINING -> Triple("Mined", "+%,d".format(absAmount), TorActive)
        TxKind.IN -> Triple("Received", "+%,d".format(absAmount), TorActive)
    }
    val timeLabel = formatUnix(tx.timestamp)
    val meta = buildList {
        if (timeLabel != null) add(timeLabel)
        if (tx.confirmed) add("${tx.confirmations} conf") else add("Pending")
    }.joinToString(" / ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(meta, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Text(
            "$amountText sats",
            style = MaterialTheme.typography.bodyMedium,
            color = amountColor,
        )
    }
}

private fun formatUnix(ts: Long?): String? {
    if (ts == null || ts <= 0L) return null
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts * 1000))
}
