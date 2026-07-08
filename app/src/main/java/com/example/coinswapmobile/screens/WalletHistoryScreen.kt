package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
        } else if (vmState.transactions.isEmpty()) {
            item {
                Text("No transactions yet. Fund a receive address and tap Sync on Home.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
            }
        } else {
            items(vmState.transactions) { tx -> TxRow(tx) }
        }
    }
}

@Composable
private fun TxRow(tx: TxUiModel) {
    val timeLabel = tx.timestamp?.let { ts ->
        if (ts > 0) SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts * 1000))
        else "unconfirmed"
    } ?: if (tx.confirmed) "confirmed" else "unconfirmed"

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
                if (tx.direction == "sent") "Sent" else "Received",
                style = MaterialTheme.typography.bodyMedium,
                color = if (tx.direction == "sent") AccentAmber else TorActive
            )
            Text("%,d sats".format(tx.amountSats),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary)
        }
        Text(
            tx.txid.take(16) + "…",
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
