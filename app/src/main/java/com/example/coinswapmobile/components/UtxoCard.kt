package com.example.coinswapmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.ui.theme.*

enum class PrivacyLevel(val dots: Int, val color: Color, val label: String) {
    LOW(1,  TorInactive,  "EXPOSED"),
    MED(3,  AccentPurple, "MIXED"),
    HIGH(5, TorActive,    "PRIVATE"),
}

data class UtxoItem(
    val address: String,
    val amountBtc: String,
    val privacyLevel: PrivacyLevel
)

@Composable
fun UtxoCard(utxo: UtxoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = utxo.address,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { i ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < utxo.privacyLevel.dots) utxo.privacyLevel.color
                                else Divider
                            )
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text  = utxo.privacyLevel.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = utxo.privacyLevel.color
                )
            }
        }
        Text(
            text  = utxo.amountBtc,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
    }
}