package com.example.coinswapmobile.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.TakerAppConfig
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.ui.theme.AccentAmber
import com.example.coinswapmobile.ui.theme.Divider
import com.example.coinswapmobile.ui.theme.Surface
import com.example.coinswapmobile.ui.theme.TextPrimary
import com.example.coinswapmobile.ui.theme.TextSecondary
import com.example.coinswapmobile.ui.theme.TorActive
import com.example.coinswapmobile.ui.theme.TorInactive
import com.example.coinswapmobile.viewmodel.WalletViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenRecovery: () -> Unit = {},
    onLogout: () -> Unit = {},
    walletViewModel: WalletViewModel = viewModel(),
) {
    val context = LocalContext.current
    val session = remember { UserSession(context) }
    val repo = remember { CoinswapRepository(FfiEnv.takerDataDir(context)) }
    val caps = remember { repo.getCapabilities() }
    val scope = rememberCoroutineScope()
    val cfg = session.config

    var rpcHost by remember { mutableStateOf(cfg.rpcHost) }
    var rpcPort by remember { mutableStateOf(cfg.rpcPort.toString()) }
    var rpcUser by remember { mutableStateOf(cfg.rpcUsername) }
    var rpcPass by remember { mutableStateOf(cfg.rpcPassword) }
    var zmqHost by remember { mutableStateOf(cfg.zmqHost) }
    var zmqPort by remember { mutableStateOf(cfg.zmqPort.toString()) }
    var torControl by remember { mutableStateOf(cfg.torControlPort.toString()) }
    var torAuth by remember { mutableStateOf(cfg.torAuthPassword) }
    var walletName by remember { mutableStateOf(cfg.walletName) }
    var walletPassword by remember { mutableStateOf(cfg.walletPassword) }
    var torStatus by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

        SettingsCard {
            SectionLabel("BITCOIN CORE RPC")
            OutlinedTextField(rpcHost, { rpcHost = it }, label = { Text("RPC host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(rpcPort, { rpcPort = it.filter(Char::isDigit) }, label = { Text("RPC port") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(rpcUser, { rpcUser = it }, label = { Text("RPC username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                rpcPass,
                { rpcPass = it },
                label = { Text("RPC password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(zmqHost, { zmqHost = it }, label = { Text("ZMQ host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(zmqPort, { zmqPort = it.filter(Char::isDigit) }, label = { Text("ZMQ port") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(walletName, { walletName = it }, label = { Text("Wallet name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                walletPassword,
                { walletPassword = it },
                label = { Text("Wallet password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = {
                    val port = rpcPort.toIntOrNull() ?: return@Button
                    val zPort = zmqPort.toIntOrNull() ?: return@Button
                    val cPort = torControl.toIntOrNull() ?: TakerAppConfig.DEFAULT_TOR_CONTROL
                    session.saveConfig(
                        TakerAppConfig(
                            rpcHost = rpcHost.trim(),
                            rpcPort = port,
                            rpcUsername = rpcUser,
                            rpcPassword = rpcPass,
                            zmqHost = zmqHost.trim(),
                            zmqPort = zPort,
                            torControlPort = cPort,
                            // UniFFI hardcodes SOCKS 127.0.0.1:9050 — always persist that.
                            torSocksHost = TakerAppConfig.DEFAULT_SOCKS_HOST,
                            torSocksPort = TakerAppConfig.DEFAULT_SOCKS_PORT,
                            torAuthPassword = torAuth,
                            walletName = walletName.trim(),
                            walletPassword = walletPassword,
                            protocol = session.config.protocol,
                        )
                    )
                    walletViewModel.connectWallet()
                    statusMessage = "Saved. Reconnecting taker…"
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TorActive)
            ) {
                Text("Save & reconnect", color = androidx.compose.ui.graphics.Color.Black)
            }
        }

        SettingsCard {
            SectionLabel("TOR")
            InfoRow(
                "SOCKS",
                "${TakerAppConfig.DEFAULT_SOCKS_HOST}:${TakerAppConfig.DEFAULT_SOCKS_PORT}",
            )
            OutlinedTextField(torControl, { torControl = it.filter(Char::isDigit) }, label = { Text("Control port") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(
                torAuth,
                { torAuth = it },
                label = { Text("Tor auth password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = {
                    scope.launch {
                        val socks = TorManager.checkSocks()
                        val ctrl = TorManager.checkControl(
                            TakerAppConfig.DEFAULT_SOCKS_HOST,
                            torControl.toIntOrNull() ?: TakerAppConfig.DEFAULT_TOR_CONTROL,
                        )
                        torStatus = "${socks.message}\n${ctrl.message}"
                        walletViewModel.refreshTorStatus()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface)
            ) {
                Text("Check Tor ports", color = TorActive)
            }
            torStatus?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        SettingsCard {
            SectionLabel("DEBUG")
            InfoRow("Taker initialized", TakerHolder.isInitialized.toString())
            InfoRow("Native bindings", "org.coinswap UniFFI")
            InfoRow("ABI", repo.deviceAbis)
            InfoRow("RPC", session.config.rpcUrl)
            InfoRow("ZMQ", session.config.zmqAddr)
            InfoRow("Wallet name", session.walletName)
            InfoRow("Data directory", FfiEnv.takerDataDir(context))
            InfoRow("Backend", caps.backend)
            CapabilityRow("Wallet init", caps.walletInit)
            CapabilityRow("Sync", caps.walletSync)
            CapabilityRow("Receive", caps.receive)
            CapabilityRow("List UTXOs", caps.listUtxos)
            CapabilityRow("Send", caps.send)
            CapabilityRow("History", caps.history)
            CapabilityRow("Maker discovery", caps.makerDiscovery)
            CapabilityRow("Coinswap", caps.coinswap)
            CapabilityRow("Swap reports", caps.reports)
            CapabilityRow("Recovery", caps.recovery)
            if (caps.missingApis.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                caps.missingApis.forEach { api ->
                    Text("• $api", style = MaterialTheme.typography.labelSmall, color = AccentAmber)
                }
            }
        }

        SettingsCard {
            SectionLabel("SWAP RECOVERY")
            Button(
                onClick = onOpenRecovery,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber.copy(alpha = 0.2f))
            ) {
                Text("Open Recovery Screen", color = AccentAmber)
            }
        }

        SettingsCard {
            SectionLabel("WALLET DATA")
            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, TorInactive)
            ) {
                Text("Clear local taker", color = TorInactive)
            }
        }

        SettingsCard {
            SectionLabel("SESSION")
            OutlinedButton(
                onClick = {
                    TakerHolder.clear()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Divider)
            ) {
                Text("Log out", color = TextSecondary)
            }
        }

        SettingsCard {
            SectionLabel("ABOUT")
            InfoRow("Version", "0.1.0-alpha")
            InfoRow("Native bindings", "org.coinswap UniFFI")
            InfoRow("Protocol", "Maxwell-Belcher Coinswap")
        }

        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = TorActive)
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Surface,
            title = { Text("Clear local taker?", color = TextPrimary) },
            text = {
                Text("Clear local taker for \"$walletName\"?", color = TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        TakerHolder.clear()
                        statusMessage = "Cleared."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TorInactive)
                ) { Text("Clear", color = androidx.compose.ui.graphics.Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CapabilityRow(label: String, enabled: Boolean) {
    InfoRow(label, if (enabled) "yes" else "no")
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(0.6f))
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
