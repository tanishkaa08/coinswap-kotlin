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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.components.OrbotHelper
import com.example.coinswapmobile.components.OrbotInstallDialog
import com.example.coinswapmobile.components.OrbotPromptBanner
import com.example.coinswapmobile.components.TorStatusBadge
import com.example.coinswapmobile.components.UtxoCard
import com.example.coinswapmobile.components.UtxoItem
import com.example.coinswapmobile.model.UtxoUiModel
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.WalletUiState
import com.example.coinswapmobile.viewmodel.WalletViewModel

@Composable
fun HomeScreen(
    onSendClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
    walletViewModel: WalletViewModel = viewModel(),
) {
    val uiState by walletViewModel.uiState.collectAsState()
    var balanceVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showOrbotDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                walletViewModel.refreshBalances()
                walletViewModel.refreshTorStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OrbotInstallDialog(visible = showOrbotDialog, onDismiss = { showOrbotDialog = false })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HomeTorHeader(
                backendLabel = uiState.backendLabel,
                libraryLoadStatus = uiState.libraryLoadStatus,
                isInitialized = uiState.isInitialized,
                torActive = uiState.torReachable && uiState.isInitialized,
                error = uiState.error,
            )
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
            HomeBalanceCard(
                uiState = uiState,
                balanceVisible = balanceVisible,
                onToggleVisibility = { balanceVisible = !balanceVisible },
            )
        }

        item {
            HomeActionRow(onSendClick = onSendClick, onReceiveClick = onReceiveClick)
        }

        item {
            HomeUtxoSectionHeader(
                count = uiState.utxos.size,
                isLoading = uiState.isLoading,
                onSync = { walletViewModel.syncWallet() },
            )
        }

        if (uiState.utxos.isEmpty() && !uiState.isLoading) {
            item {
                Text(
                    "No UTXOs yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        } else {
            items(uiState.utxos, key = { "${it.txid}:${it.vout}" }) { utxo ->
                UtxoCard(utxo.toUtxoItem())
            }
        }
    }
}

@Composable
private fun HomeTorHeader(
    backendLabel: String,
    libraryLoadStatus: String,
    isInitialized: Boolean,
    torActive: Boolean,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COINSWAP", style = MaterialTheme.typography.titleMedium, color = TorActive)
            Spacer(Modifier.weight(1f))
            TorStatusBadge(isActive = torActive)
        }
        Text(
            if (backendLabel.isNotBlank()) backendLabel else "Bitcoin Core RPC • not connected",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        if (!isInitialized && libraryLoadStatus.isNotBlank()) {
            Text(libraryLoadStatus, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        error?.let { message ->
            Text("⚠  $message", style = MaterialTheme.typography.labelSmall, color = TorInactive)
        }
    }
}

@Composable
private fun HomeBalanceCard(
    uiState: WalletUiState,
    balanceVisible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("TOTAL BALANCE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        AnimatedContent(
            targetState = balanceVisible,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "balance",
            modifier = Modifier.clickable(onClick = onToggleVisibility),
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
    }
}

@Composable
private fun HomeActionRow(onSendClick: () -> Unit, onReceiveClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionButton("SEND", Icons.AutoMirrored.Filled.CallMade, Modifier.weight(1f), onSendClick)
        ActionButton("RECEIVE", Icons.AutoMirrored.Filled.CallReceived, Modifier.weight(1f), onReceiveClick)
    }
}

@Composable
private fun HomeUtxoSectionHeader(count: Int, isLoading: Boolean, onSync: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("UTXOs ($count)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            if (isLoading) "Syncing…" else "Sync",
            style = MaterialTheme.typography.labelSmall,
            color = TorActive,
            modifier = Modifier.clickable(onClick = onSync),
        )
    }
}

private fun UtxoUiModel.toUtxoItem(): UtxoItem {
    val kind = when {
        spendType == null || spendType == "SeedCoin" -> "Regular"
        spendType.contains("Swap", ignoreCase = true) ||
            spendType.contains("Swept", ignoreCase = true) -> "From swap"
        spendType.contains("Contract", ignoreCase = true) -> "Locked"
        else -> "Coin"
    }
    val shortId = if (txid.length > 8) "${txid.take(8)}…:$vout" else "$txid:$vout"
    return UtxoItem(
        id = shortId,
        amountSats = amountSats,
        kind = kind,
        confirmed = (confirmations ?: 0) > 0,
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
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SurfaceAlt,
            contentColor = TextPrimary
        )
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
