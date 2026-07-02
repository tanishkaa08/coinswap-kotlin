package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coinswapmobile.components.coinswapTextFieldColors
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.ui.theme.*

@Composable
fun LoginScreen(onConnected: () -> Unit) {
    val context = LocalContext.current
    val session = remember { UserSession(context) }

    var electrumUrl by remember { mutableStateOf(session.electrumUrl) }
    var walletName by remember { mutableStateOf(session.walletName) }
    var errorMsg by remember { mutableStateOf("") }

    fun connect() {
        val url = electrumUrl.trim()
        if (!url.startsWith("ssl://") && !url.startsWith("tcp://")) {
            errorMsg = "Enter an Electrum server URL (ssl://host:port or tcp://host:port)"
            return
        }
        session.saveSession(url, walletName.trim())
        onConnected()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TorActive.copy(0.12f))
                    .border(1.dp, TorActive.copy(0.3f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text("⇄", fontSize = 24.sp, color = TorActive) }

            Spacer(Modifier.height(12.dp))

            Text("COINSWAP",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                letterSpacing = 4.sp)

            Spacer(Modifier.height(4.dp))

            Text("Bitcoin Privacy Protocol",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)

            Spacer(Modifier.height(36.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(1.dp, Divider, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ELECTRUM SERVER",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    OutlinedTextField(
                        value = electrumUrl,
                        onValueChange = { electrumUrl = it; errorMsg = "" },
                        placeholder = { Text(UserSession.DEFAULT_ELECTRUM_URL, color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = coinswapTextFieldColors()
                    )
                }

                HorizontalDivider(color = Divider, thickness = 0.5.dp)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("WALLET NAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    OutlinedTextField(
                        value = walletName,
                        onValueChange = { walletName = it },
                        placeholder = { Text(UserSession.DEFAULT_WALLET_NAME, color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = coinswapTextFieldColors()
                    )
                }

                if (errorMsg.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TorInactive.copy(0.08f))
                            .border(1.dp, TorInactive.copy(0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✕", style = MaterialTheme.typography.labelSmall, color = TorInactive)
                        Text(errorMsg,
                            style = MaterialTheme.typography.labelSmall,
                            color = TorInactive,
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { connect() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TorActive)
            ) {
                Text("Connect", color = Color.Black, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(32.dp))

            Text("Maxwell-Belcher Coinswap  •  v0.1.0-alpha",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.35f),
                textAlign = TextAlign.Center)

            Spacer(Modifier.height(32.dp))
        }
    }
}
