package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coinswapmobile.BuildConfig
import com.example.coinswapmobile.components.coinswapTextFieldColors
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.TakerAppConfig
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.data.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.coinswapmobile.ui.theme.Background
import com.example.coinswapmobile.ui.theme.Divider
import com.example.coinswapmobile.ui.theme.Surface
import com.example.coinswapmobile.ui.theme.TextPrimary
import com.example.coinswapmobile.ui.theme.TextSecondary
import com.example.coinswapmobile.ui.theme.TorActive
import com.example.coinswapmobile.ui.theme.TorInactive
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onConnected: () -> Unit) {
    val context = LocalContext.current
    val session = remember { UserSession(context) }
    val repo = remember { CoinswapRepository(FfiEnv.takerDataDir(context)) }
    val scope = rememberCoroutineScope()
    val initial = remember { session.config }
    val demoHost = remember { BuildConfig.DEMO_REGTEST_HOST.trim() }

    var walletPassword by remember { mutableStateOf(initial.walletPassword) }
    var confirmPassword by remember { mutableStateOf(initial.walletPassword) }
    var showPwd by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var torStatus by remember { mutableStateOf("Starting Tor…") }

    val autoConfig = remember(demoHost, initial, walletPassword) {
        if (demoHost.isNotBlank()) {
            TakerAppConfig(
                electrumUrl = TakerAppConfig.electrumUrlForHost(demoHost),
                torControlPort = initial.torControlPort,
                torSocksHost = TakerAppConfig.DEFAULT_SOCKS_HOST,
                torSocksPort = TakerAppConfig.DEFAULT_SOCKS_PORT,
                torAuthPassword = TakerAppConfig.DEMO_TOR_PASSWORD,
                walletName = TakerAppConfig.DEFAULT_WALLET_NAME,
                walletPassword = walletPassword,
                protocol = initial.protocol,
            )
        } else {
            initial.copy(
                walletName = TakerAppConfig.DEFAULT_WALLET_NAME,
                walletPassword = walletPassword,
            )
        }
    }

    LaunchedEffect(Unit) {
        torStatus = "Starting Tor…"
        val status = TorManager.ensureRunning(context)
        torStatus = status.message
        if (!status.reachable) {
            error = status.message
        }
    }

    fun connect() {
        if (walletPassword.isBlank()) {
            error = "Enter a passcode"
            return
        }
        if (walletPassword != confirmPassword) {
            error = "Passcodes do not match"
            return
        }
        connecting = true
        error = null
        val liveConfig = autoConfig.copy(
            electrumUrl = TakerAppConfig.DEFAULT_ELECTRUM_URL,
            torAuthPassword = TakerAppConfig.DEMO_TOR_PASSWORD,
        )
        session.saveConfig(liveConfig, markLoggedIn = false)
        scope.launch {
            val tor = TorManager.ensureRunning(context)
            torStatus = tor.message
            if (!tor.reachable) {
                connecting = false
                error = tor.message
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                repo.initTaker(session, forceReconnect = true, config = liveConfig)
            }
            connecting = false
            result
                .onSuccess {
                    session.saveConfig(liveConfig, markLoggedIn = true)
                    onConnected()
                }
                .onFailure { e ->
                    error = e.message ?: "Wallet setup failed"
                }
        }
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
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
            Text("COINSWAP", style = MaterialTheme.typography.titleMedium, color = TextPrimary, letterSpacing = 4.sp)

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(1.dp, Divider, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PasswordField("Passcode", walletPassword, { walletPassword = it }, showPwd) { showPwd = !showPwd }
                PasswordField("Confirm", confirmPassword, { confirmPassword = it }, showPwd) { showPwd = !showPwd }

                error?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TorInactive.copy(alpha = 0.10f))
                            .border(1.dp, TorInactive.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(msg, style = MaterialTheme.typography.labelSmall, color = TorInactive)
                    }
                }

                Button(
                    onClick = { connect() },
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TorActive)
                ) {
                    if (connecting) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (connecting) "Opening…" else "Continue", color = Color.Black)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    show: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = TextSecondary,
                    )
                }
            },
            colors = coinswapTextFieldColors(),
        )
    }
}
