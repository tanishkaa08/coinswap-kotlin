package com.example.coinswapmobile.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.components.OrbotHelper
import com.example.coinswapmobile.components.OrbotInstallDialog
import com.example.coinswapmobile.components.OrbotPromptBanner
import com.example.coinswapmobile.model.UtxoUiModel
import com.example.coinswapmobile.ui.components.PrivacyLevel
import com.example.coinswapmobile.ui.components.TorStatusBadge
import com.example.coinswapmobile.ui.components.UtxoCard
import com.example.coinswapmobile.ui.components.UtxoItem
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.WalletViewModel

@Composable
fun HomeScreen(
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    walletViewModel: WalletViewModel = viewModel(),
) {
    val uiState by walletViewModel.uiState.collectAsState()
    var balanceVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showOrbotDialog by remember { mutableStateOf(false) }

    OrbotInstallDialog(visible = showOrbotDialog, onDismiss = { showOrbotDialog = false })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("COINSWAP",
                    style = MaterialTheme.typography.titleMedium,
                    color = TorActive)
                Spacer(Modifier.weight(1f))
                TorStatusBadge(isActive = uiState.torReachable && uiState.isInitialized)
            }
        }

        item {
            Text(
                if (uiState.backendLabel.isNotBlank()) uiState.backendLabel
                else "Bitcoin Core RPC • not connected",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
        }

        if (!uiState.isInitialized && uiState.libraryLoadStatus.isNotBlank()) {
            item {
                Text(
                    uiState.libraryLoadStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
            }
        }

        uiState.error?.let { message ->
            item {
                Text("⚠  $message",
                    style = MaterialTheme.typography.labelSmall,
                    color = TorInactive)
            }
        }

        if (uiState.isInitialized && !uiState.torReachable) {
            item {
                OrbotPromptBanner(
                    onInstallClick = {
                        if (OrbotHelper.isOrbotInstalled(context)) {
                            OrbotHelper.openOrbotApp(context)
                        } else {
                            showOrbotDialog = true
                        }
                    },
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("TOTAL BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)

                AnimatedContent(
                    targetState = balanceVisible,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "balance"
                ) { visible ->
                    if (uiState.isLoading && !uiState.isInitialized) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = TorActive,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (visible) "%,d sats".format(uiState.balanceSats) else "●●●●●●",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceAlt)
                        .clickable { balanceVisible = !balanceVisible }
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (balanceVisible) "Tap to hide" else "Tap to reveal",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton("SEND", Icons.AutoMirrored.Filled.CallMade, Modifier.weight(1f), onSendClick)
                ActionButton("RECEIVE", Icons.AutoMirrored.Filled.CallReceived, Modifier.weight(1f), onReceiveClick)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("UTXOs (${uiState.utxos.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(if (uiState.isLoading) "Syncing…" else "Sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = TorActive,
                    modifier = Modifier.clickable { walletViewModel.syncWallet() })
            }
        }

        if (uiState.utxos.isEmpty() && !uiState.isLoading) {
            item {
                Text("No UTXOs yet. Fund a receive address and tap Sync.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
            }
        } else {
            items(uiState.utxos) { utxo -> UtxoCard(utxo.toUtxoItem()) }
        }
    }
}

private fun UtxoUiModel.toUtxoItem(): UtxoItem {
    val privacy = when {
        (confirmations ?: 0) == 0 -> PrivacyLevel.LOW
        (confirmations ?: 0) < 3  -> PrivacyLevel.MED
        else                      -> PrivacyLevel.HIGH
    }
    val shortId = if (txid.length > 8) "${txid.take(8)}…:$vout" else "$txid:$vout"
    return UtxoItem(
        address = shortId,
        amountBtc = "%.8f".format(amountSats / 100_000_000.0),
        privacyLevel = privacy,
    )
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick  = onClick,
        modifier = modifier.height(52.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = SurfaceAlt,
            contentColor   = TextPrimary
        )
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
