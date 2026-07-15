package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Transaction History",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Wallet activity via UniFFI Taker (Bitcoin Core RPC)",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary)
        }

        vmState.errorMessage?.let { msg ->
            item {
                Text("⚠  $msg", style = MaterialTheme.typography.labelSmall, color = TorInactive)
            }
        }

        if (vmState.isLoading) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier.padding(24.dp),
                    color = TorActive,
                    strokeWidth = 2.dp
                )
            }
        } else {
            if (vmState.swaps.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text("COINSWAPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                }
                items(
                    items = vmState.swaps,
                    key = { "swap-${it.id}-${it.status}-${it.startTimestamp}" },
                ) { swap -> SwapRow(swap) }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text("WALLET TRANSACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
            }
            if (vmState.transactions.isEmpty() && !vmState.isLoading) {
                item {
                    Text("No transactions yet. Fund a receive address and tap Sync on Home.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                }
            } else {
                itemsIndexed(
                    items = vmState.transactions,
                    key = { i, tx -> "tx-$i-${tx.txid}-${tx.amountSats}-${tx.confirmations}" },
                ) { _, tx -> TxRow(tx) }
            }
        }
    }
}

private enum class TxKind(val label: String) {
    MINING("Mining reward"),
    RECEIVED("Received"),
    SENT("Sent"),
}

private fun TxUiModel.kind(): TxKind {
    val cat = category?.lowercase().orEmpty()
    return when {
        cat.contains("generate") || cat.contains("immature") || cat.contains("coinbase") -> TxKind.MINING
        direction == "outgoing" || direction == "sent" || amountSats < 0 -> TxKind.SENT
        else -> TxKind.RECEIVED
    }
}

@Composable
private fun SwapRow(swap: SwapReportUiModel) {
    val (statusLabel, statusColor) = when (swap.status) {
        SwapReportUiModel.Status.COMPLETED -> "Completed" to TorActive
        SwapReportUiModel.Status.RECOVERED -> "Recovered" to AccentAmber
        SwapReportUiModel.Status.FAILED -> "Failed" to TorInactive
    }
    val timeLabel = swap.startTimestamp?.let { ts ->
        if (ts > 0) SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts * 1000)) else null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Coinswap • $statusLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor)
            Text("%,d sats".format(swap.amountSats),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary)
        }
        Text(
            "${swap.makerCount} maker(s) • fee ${"%,d".format(swap.totalFeeSats)} sats",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        (timeLabel ?: swap.errorMessage)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun TxRow(tx: TxUiModel) {
    val timeLabel = tx.timestamp?.let { ts ->
        if (ts > 0) SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts * 1000))
        else "unconfirmed"
    } ?: if (tx.confirmed) "confirmed" else "unconfirmed"

    val kind = tx.kind()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                kind.label,
                style = MaterialTheme.typography.bodyMedium,
                color = when (kind) {
                    TxKind.SENT -> AccentAmber
                    TxKind.MINING -> AccentPurple
                    TxKind.RECEIVED -> TorActive
                }
            )
            Text("%,d sats".format(tx.amountSats),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary)
        }
        Text(
            (if (tx.txid.length > 16) tx.txid.take(16) + "…" else tx.txid).ifBlank { "(no txid)" },
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            "$timeLabel • ${tx.confirmations} conf",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
