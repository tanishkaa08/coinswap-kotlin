package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.coinswapmobile.components.OrbotHelper
import com.example.coinswapmobile.components.OrbotInstallDialog
import com.example.coinswapmobile.components.coinswapTextFieldColors
import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.TakerAppConfig
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.ui.theme.Background
import com.example.coinswapmobile.ui.theme.Divider
import com.example.coinswapmobile.ui.theme.Surface
import com.example.coinswapmobile.ui.theme.TextPrimary
import com.example.coinswapmobile.ui.theme.TextSecondary
import com.example.coinswapmobile.ui.theme.TorActive
import com.example.coinswapmobile.ui.theme.TorInactive
import kotlinx.coroutines.launch

/** First-time setup: Bitcoin Core RPC, ZMQ, Tor ports, wallet password. */
@Composable
fun LoginScreen(onConnected: () -> Unit) {
    val context = LocalContext.current
    val session = remember { UserSession(context) }
    val repo = remember { CoinswapRepository(context.filesDir.absolutePath) }
    val scope = rememberCoroutineScope()
    val initial = remember { session.config }
    val demoHost = remember { BuildConfig.DEMO_REGTEST_HOST.trim() }

    var rpcHost by remember {
        mutableStateOf(
            when {
                demoHost.isNotBlank() -> demoHost
                else -> initial.rpcHost
            }
        )
    }
    var rpcPort by remember { mutableStateOf(initial.rpcPort.toString()) }
    var rpcUser by remember { mutableStateOf(initial.rpcUsername) }
    var rpcPass by remember { mutableStateOf(initial.rpcPassword) }
    var zmqHost by remember {
        mutableStateOf(
            when {
                demoHost.isNotBlank() -> demoHost
                else -> initial.zmqHost
            }
        )
    }
    var zmqPort by remember { mutableStateOf(initial.zmqPort.toString()) }
    var torControl by remember { mutableStateOf(initial.torControlPort.toString()) }
    var socksHost by remember { mutableStateOf(initial.torSocksHost) }
    var socksPort by remember { mutableStateOf(initial.torSocksPort.toString()) }
    var torAuth by remember { mutableStateOf(initial.torAuthPassword) }
    var walletName by remember { mutableStateOf(initial.walletName) }
    var walletPassword by remember { mutableStateOf(initial.walletPassword) }
    var showPwd by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showOrbotDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!OrbotHelper.isOrbotInstalled(context)) {
            showOrbotDialog = true
        }
    }

    fun connect() {
        val port = rpcPort.toIntOrNull()
        val zPort = zmqPort.toIntOrNull()
        val cPort = torControl.toIntOrNull()
        val sPort = socksPort.toIntOrNull()
        if (rpcHost.isBlank() || port == null || zPort == null || cPort == null || sPort == null) {
            error = "Fill RPC, ZMQ, and Tor ports with valid numbers"
            return
        }
        if (rpcHost == "127.0.0.1" || rpcHost == "localhost") {
            error = "127.0.0.1 points at the phone; enter server IP"
            return
        }
        if (walletName.isBlank()) {
            error = "Enter a wallet name"
            return
        }
        connecting = true
        error = null
        session.saveConfig(
            TakerAppConfig(
                rpcHost = rpcHost.trim(),
                rpcPort = port,
                rpcUsername = rpcUser,
                rpcPassword = rpcPass,
                zmqHost = zmqHost.trim(),
                zmqPort = zPort,
                torControlPort = cPort,
                torSocksHost = socksHost.trim(),
                torSocksPort = sPort,
                torAuthPassword = torAuth,
                walletName = walletName.trim(),
                walletPassword = walletPassword,
            )
        )
        scope.launch {
            val result = repo.initTaker(session)
            connecting = false
            result
                .onSuccess { onConnected() }
                .onFailure { e ->
                    error = e.message ?: "Taker.init failed"
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
            Spacer(Modifier.height(6.dp))
            Text("Taker setup", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

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
                Text("Bitcoin Core RPC", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Field("RPC host", rpcHost, { rpcHost = it }, KeyboardType.Uri)
                Field("RPC port", rpcPort, { rpcPort = it.filter(Char::isDigit) }, KeyboardType.Number)
                Field("RPC username", rpcUser, { rpcUser = it })
                PasswordField("RPC password", rpcPass, { rpcPass = it }, showPwd) { showPwd = !showPwd }

                Text("ZMQ", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Field("ZMQ host", zmqHost, { zmqHost = it }, KeyboardType.Uri)
                Field("ZMQ port", zmqPort, { zmqPort = it.filter(Char::isDigit) }, KeyboardType.Number)

                Text("Tor", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Field("SOCKS host", socksHost, { socksHost = it }, KeyboardType.Uri)
                Field("SOCKS port", socksPort, { socksPort = it.filter(Char::isDigit) }, KeyboardType.Number)
                Field("Control port", torControl, { torControl = it.filter(Char::isDigit) }, KeyboardType.Number)
                Field("Tor auth password (optional)", torAuth, { torAuth = it })

                Text("Wallet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Field("Wallet name", walletName, { walletName = it })
                PasswordField("Wallet password", walletPassword, { walletPassword = it }, showPwd) { showPwd = !showPwd }

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
                    Text(if (connecting) "Initializing taker…" else "Continue", color = Color.Black)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    OrbotInstallDialog(
        visible = showOrbotDialog,
        onDismiss = { showOrbotDialog = false },
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = coinswapTextFieldColors(),
        )
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
