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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.WalletViewModel

@Composable
fun ReceiveScreen(
    onBack: () -> Unit,
    walletViewModel: WalletViewModel = viewModel(),
) {
    val uiState by walletViewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val address = uiState.receiveAddress

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Receive Bitcoin",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary)
        }

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

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
                    // No address yet — prompt the user to generate one from the wallet.
                    Spacer(Modifier.height(24.dp))
                    Text("No receive address yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center)
                }

                else -> {
                    // QR placeholder
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface)
                            .border(1.dp, Divider, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("▦", fontSize = 72.sp, color = TextPrimary)
                            Text("QR Code",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary)
                        }
                    }

                    // Address box
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

                    Text(
                        "Each address is single-use. Tap New Address for a fresh one.",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // ── Fixed bottom actions ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Generate / next address — both call the wallet for a fresh HD address.
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
