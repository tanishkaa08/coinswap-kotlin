package com.example.coinswapmobile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.ui.theme.*

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    HorizontalDivider(color = Divider, thickness = 0.5.dp)
}

@Composable
fun LabeledSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label,    style = MaterialTheme.typography.bodyMedium,  color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,  color = TextSecondary)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors  = SwitchDefaults.colors(
                checkedThumbColor = TorActive,
                checkedTrackColor = TorActive.copy(0.3f)
            )
        )
    }
}

@Composable
fun coinswapTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Divider,
    focusedBorderColor   = TorActive,
    unfocusedTextColor   = TextPrimary,
    focusedTextColor     = TextPrimary,
    cursorColor          = TorActive,
)