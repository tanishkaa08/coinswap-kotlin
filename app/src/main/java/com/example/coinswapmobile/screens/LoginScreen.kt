package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var password by remember { mutableStateOf("") }
    var showPwd by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun connect() {
        val url = electrumUrl.trim()
        if (!url.startsWith("ssl://") && !url.startsWith("tcp://")) {
            error = "Enter an Electrum server URL (ssl://host:port or tcp://host:port)"
            return
        }
        connecting = true
        error = null
        session.saveSession(url, walletName.trim())
        connecting = false
        onConnected()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TorActive.copy(alpha = 0.12f))
                    .border(1.dp, TorActive.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⇄", fontSize = 36.sp, color = TorActive)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "COINSWAP",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Bitcoin Privacy Protocol",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentAmber.copy(alpha = 0.12f))
                    .border(1.dp, AccentAmber.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "MUTINYNET  •  TESTNET",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentAmber,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(40.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(1.dp, Divider, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Connect to Node",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ELECTRUM SERVER",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    OutlinedTextField(
                        value = electrumUrl,
                        onValueChange = { electrumUrl = it; error = null },
                        placeholder = { Text(UserSession.DEFAULT_ELECTRUM_URL, color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !connecting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = coinswapTextFieldColors()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("WALLET NAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    OutlinedTextField(
                        value = walletName,
                        onValueChange = { walletName = it; error = null },
                        placeholder = { Text(UserSession.DEFAULT_WALLET_NAME, color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !connecting,
                        colors = coinswapTextFieldColors()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("WALLET PASSWORD",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        placeholder = { Text("Enter password", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !connecting,
                        visualTransformation = if (showPwd) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPwd = !showPwd }) {
                                Icon(
                                    imageVector = if (showPwd) Icons.Default.VisibilityOff
                                                  else Icons.Default.Visibility,
                                    contentDescription = if (showPwd) "Hide" else "Show",
                                    tint = TextSecondary
                                )
                            }
                        },
                        colors = coinswapTextFieldColors()
                    )
                }

                if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TorInactive.copy(alpha = 0.10f))
                            .border(1.dp, TorInactive.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(error!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = TorInactive)
                    }
                }

                Button(
                    onClick = { connect() },
                    enabled = !connecting && electrumUrl.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TorActive)
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Connecting…",
                            color = Color.Black,
                            style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("Connect  →",
                            color = Color.Black,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Maxwell-Belcher Coinswap  •  v0.1.0-alpha",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
