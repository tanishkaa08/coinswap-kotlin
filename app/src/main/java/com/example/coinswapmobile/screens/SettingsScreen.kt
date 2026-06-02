package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.components.SectionCard
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.components.LabeledSwitch
import com.example.coinswapmobile.components.coinswapTextFieldColors
import com.example.coinswapmobile.ui.theme.*

@Composable
fun SettingsScreen() {
    var decoyCount      by remember { mutableFloatStateOf(50f) }
    var rotateServers   by remember { mutableStateOf(true) }
    var broadcastRedund by remember { mutableFloatStateOf(3f) }
    var torMode         by remember { mutableStateOf(true) }
    var backendElectrum by remember { mutableStateOf(true) }
    var electrumServer  by remember { mutableStateOf("ssl://electrum.blockstream.info:50002") }

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

        // Backend
        SettingsCard {
            SectionLabel("BACKEND")
            LabeledSwitch(
                label    = "Use Electrum backend",
                subtitle = if (backendElectrum) "Electrum (no full node required)" else "Bitcoin Core RPC",
                checked  = backendElectrum,
                onChange = { backendElectrum = it }
            )
            if (backendElectrum) {
                Spacer(Modifier.height(8.dp))
                Text("ELECTRUM SERVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = electrumServer,
                    onValueChange = { electrumServer = it },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = coinswapTextFieldColors()
                )
            }
        }

        // Tor
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

        // Address privacy
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

        // Broadcast
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

        // About
        SettingsCard {
            SectionLabel("ABOUT")
            InfoRow("Version",  "0.1.0-alpha")
            InfoRow("Network",  "Mutinynet (testnet)")
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
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
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    HorizontalDivider(color = Divider, thickness = 0.5.dp)
}

@Composable
fun LabeledSwitch(label: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label,    style = MaterialTheme.typography.bodyMedium,  color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,  color = TextSecondary)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor = TorActive,
                checkedTrackColor = TorActive.copy(0.3f)
            )
        )
    }
}

@Composable
fun LabeledSlider(label: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
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