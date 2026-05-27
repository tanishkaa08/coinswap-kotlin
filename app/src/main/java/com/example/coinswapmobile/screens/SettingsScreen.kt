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
import com.example.coinswapmobile.ui.theme.*

@Composable
fun SettingsScreen() {
    var decoyCount       by remember { mutableFloatStateOf(50f) }
    var rotateServers    by remember { mutableStateOf(true) }
    var broadcastRedund  by remember { mutableFloatStateOf(3f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Privacy Settings", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            "Configure your cryptographic anonymity layers and routing protocols.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        SettingsCard {
            SectionLabel("TOR ROUTING")
            LabeledSwitch(
                label    = "Rotate server per query",
                subtitle = "Each batch hits a different server. Recommended.",
                checked  = rotateServers,
                onChange = { rotateServers = it }
            )
        }

        SettingsCard {
            SectionLabel("ADDRESS PRIVACY")
            LabeledSlider(
                label    = "Decoy address count",
                subtitle = "More decoys = stronger privacy, slightly more data usage",
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
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    HorizontalDivider(color = Divider, thickness = 0.5.dp)
}

@Composable
private fun LabeledSwitch(label: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label,    style = MaterialTheme.typography.bodyMedium,  color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onChange,
            colors          = SwitchDefaults.colors(checkedThumbColor = TorActive, checkedTrackColor = TorActive.copy(0.3f))
        )
    }
}

@Composable
private fun LabeledSlider(label: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
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