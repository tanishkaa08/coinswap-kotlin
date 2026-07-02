package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.components.LabeledSwitch
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.ui.theme.*

@Composable
fun SettingsScreen(
    onOpenRecovery: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val session = remember { UserSession(context) }

    var decoyCount      by remember { mutableFloatStateOf(50f) }
    var rotateServers   by remember { mutableStateOf(true) }
    var broadcastRedund by remember { mutableFloatStateOf(3f) }
    var torMode         by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary)

        SettingsCard {
            SectionLabel("YOUR SESSION")
            InfoRow("Backend", "Electrum")
            InfoRow("Electrum server", session.electrumUrl)
            InfoRow("Wallet name", session.walletName)
            InfoRow("Logged in", if (session.isLoggedIn) "Yes" else "No")
        }

        SettingsCard {
            SectionLabel("SWAP RECOVERY")
            Text("Resume or inspect an incomplete swap session.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
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
            SectionLabel("TOR ROUTING")
            LabeledSwitch(
                label    = "Enable Tor",
                subtitle = if (torMode) "All traffic routed through Tor" else "Clearnet (privacy reduced)",
                checked  = torMode,
                onChange = { torMode = it }
            )
            LabeledSwitch(
                label    = "Rotate server per query",
                subtitle = "Each batch hits a different server",
                checked  = rotateServers,
                onChange = { rotateServers = it }
            )
        }

        SettingsCard {
            SectionLabel("ADDRESS PRIVACY")
            LabeledSlider(
                label    = "Decoy address count",
                subtitle = "More decoys = stronger privacy",
                value    = decoyCount,
                range    = 20f..80f,
                display  = "${decoyCount.toInt()} DECOYS/QUERY",
                onChange = { decoyCount = it }
            )
        }

        SettingsCard {
            SectionLabel("BROADCAST")
            LabeledSlider(
                label    = "Broadcast redundancy",
                subtitle = "Broadcast to multiple servers to prevent timing correlation",
                value    = broadcastRedund,
                range    = 1f..5f,
                display  = "${broadcastRedund.toInt()}x",
                onChange = { broadcastRedund = it }
            )
        }

        SettingsCard {
            SectionLabel("SESSION")
            OutlinedButton(
                onClick = onLogout,
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
            InfoRow("Network", "Mutinynet (testnet)")
            InfoRow("Protocol", "Maxwell-Belcher Coinswap")
        }
    }
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

@Composable
private fun LabeledSlider(
    label: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label,   style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(display, style = MaterialTheme.typography.labelSmall, color = TorActive)
        }
        Slider(
            value         = value,
            onValueChange = onChange,
            valueRange    = range,
            colors        = SliderDefaults.colors(thumbColor = TorActive, activeTrackColor = TorActive)
        )
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
