package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.components.QrCodeImage
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.WalletViewModel

private fun networkHint(address: String?, rpcLabel: String): String {
    return when {
        address?.startsWith("bcrt1") == true -> "Regtest"
        address?.startsWith("tb1") == true -> "Testnet / signet"
        address?.startsWith("bc1") == true -> "Mainnet"
        rpcLabel.contains("citadelfoss") || rpcLabel.contains("50002") -> "Signet"
        rpcLabel.contains("50001") || rpcLabel.contains("18442") -> "Regtest"
        rpcLabel.contains("38332") -> "Signet"
        else -> rpcLabel.ifBlank { "Bitcoin" }
    }
}

@Composable
fun ReceiveScreen(
    onBack: () -> Unit,
    showHeader: Boolean = true,
    walletViewModel: WalletViewModel = viewModel(),
) {
    val uiState by walletViewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val address = uiState.receiveAddress

    LaunchedEffect(uiState.isInitialized) {
        if (uiState.isInitialized && uiState.receiveAddress == null && uiState.error == null) {
            walletViewModel.generateReceiveAddress()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
                Text("Receive",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                networkHint(address, uiState.rpcLabel.ifBlank { uiState.backendLabel }),
                style = MaterialTheme.typography.labelSmall,
                color = AccentAmber,
                textAlign = TextAlign.Center)

            uiState.error?.let { message ->
                Text("⚠  $message",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = TorInactive,
                    textAlign = TextAlign.Center)
            }

            when {
                uiState.isLoading && address == null -> {
                    CircularProgressIndicator(color = TorActive, modifier = Modifier.padding(32.dp))
                }

                address == null -> {
                    Spacer(Modifier.height(24.dp))
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, Divider, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeImage(
                            data = address,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .border(1.dp, Divider, RoundedCornerShape(12.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(address))
                                copied = true
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = address,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ContentCopy, "Copy",
                            tint     = if (copied) TorActive else TextSecondary,
                            modifier = Modifier.size(18.dp))
                    }

                    if (copied) {
                        Text("Address copied",
                            style = MaterialTheme.typography.labelSmall,
                            color = TorActive)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick  = {
                    copied = false
                    walletViewModel.generateReceiveAddress()
                },
                enabled  = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
            ) {
                Icon(Icons.Default.Refresh, null,
                    modifier = Modifier.size(16.dp), tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (address == null) "Generate receive address" else "New Address",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (address != null) {
                OutlinedButton(
                    onClick  = {
                        clipboard.setText(AnnotatedString(address))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, TorActive)
                ) {
                    Icon(Icons.Default.ContentCopy, null,
                        modifier = Modifier.size(16.dp), tint = TorActive)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Address", color = TorActive,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
