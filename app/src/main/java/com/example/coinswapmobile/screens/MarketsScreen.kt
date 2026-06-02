package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.ui.theme.*

data class MakerInfo(
    val shortId:   String,
    val feeRate:   String,
    val minSwap:   String,
    val maxSwap:   String,
    val liquidity: String,
    val online:    Boolean
)

@Composable
fun MarketsScreen() {
    val makers = listOf(
        MakerInfo("mk1...ab3", "0.10%", "0.001 BTC", "1.000 BTC", "2.345 BTC", true),
        MakerInfo("mk2...cd7", "0.08%", "0.005 BTC", "0.500 BTC", "1.120 BTC", true),
        MakerInfo("mk3...ef2", "0.12%", "0.001 BTC", "2.000 BTC", "5.670 BTC", true),
        MakerInfo("mk4...gh9", "0.15%", "0.010 BTC", "0.250 BTC", "0.890 BTC", false),
        MakerInfo("mk5...ij4", "0.09%", "0.002 BTC", "1.500 BTC", "3.210 BTC", true),
    )

    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Maker Marketplace",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("${makers.count { it.online }} makers online",
                style = MaterialTheme.typography.labelSmall,
                color = TorActive)
        }

        // Column headers
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("MAKER", "FEE", "MIN", "MAX", "LIQUIDITY").forEach { h ->
                    Text(h,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = TextSecondary,
                        modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = Divider, modifier = Modifier.padding(top = 6.dp))
        }

        items(makers) { maker -> MakerRow(maker) }
    }
}

@Composable
private fun MakerRow(maker: MakerInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Maker ID with online dot
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (maker.online) TorActive else TorInactive)
            )
            Text(maker.shortId,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary)
        }
        Text(maker.feeRate,   style = MaterialTheme.typography.labelSmall, color = TorActive,    modifier = Modifier.weight(1f))
        Text(maker.minSwap,   style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(maker.maxSwap,   style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(maker.liquidity, style = MaterialTheme.typography.labelSmall, color = TextPrimary,   modifier = Modifier.weight(1f))
    }
}