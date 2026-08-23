package com.example.coinswapmobile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.ui.theme.*

data class UtxoItem(
    val id: String,
    val amountSats: Long,
    val kind: String,
    val confirmed: Boolean,
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "%,d sats".format(utxo.amountSats),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = buildString {
                    append(utxo.kind)
                    if (!utxo.confirmed) append(" pending")
                    append(" ")
                    append(utxo.id)
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}
