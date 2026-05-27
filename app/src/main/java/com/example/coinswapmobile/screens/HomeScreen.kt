package com.example.coinswapmobile.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.components.PrivacyLevel
import com.example.coinswapmobile.components.TorStatusBadge
import com.example.coinswapmobile.components.UtxoCard
import com.example.coinswapmobile.components.UtxoItem
import com.example.coinswapmobile.ui.theme.*

@Composable
fun HomeScreen(
    onSendClick:    () -> Unit,
    onReceiveClick: () -> Unit,
) {
    var balanceVisible by remember { mutableStateOf(false) }
    val torActive    = true
    val backendLabel = "BACKEND: ELECTRUM"

    val utxos = listOf(
        UtxoItem("bc1q...xµ3", "0.0450 BTC", PrivacyLevel.HIGH),
        UtxoItem("bc1p...z8w", "0.0795 BTC", PrivacyLevel.MED),
        UtxoItem("bc1q...m2k", "0.0001 BTC", PrivacyLevel.LOW),
    )

    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top bar
        item {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = "COINSWAP",
                    style = MaterialTheme.typography.titleMedium,
                    color = TorActive
                )
                Spacer(Modifier.weight(1f))
                TorStatusBadge(isActive = torActive)
            }
        }

        // Backend badge
        item {
            Text(
                text  = backendLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        // Balance card
        item {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text  = "TOTAL BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                AnimatedContent(
                    targetState  = balanceVisible,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label        = "balance"
                ) { visible ->
                    Text(
                        text  = if (visible) "0.1245 BTC" else "••••••",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceAlt)
                        .clickable { balanceVisible = !balanceVisible }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text  = if (balanceVisible) "Tap to hide" else "Tap to reveal",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Send / Receive buttons
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    label    = "SEND",
                    icon     = Icons.AutoMirrored.Filled.CallMade,
                    modifier = Modifier.weight(1f),
                    onClick  = onSendClick
                )
                ActionButton(
                    label    = "RECEIVE",
                    icon     = Icons.AutoMirrored.Filled.CallReceived,
                    modifier = Modifier.weight(1f),
                    onClick  = onReceiveClick
                )
            }
        }

        // UTXOs header
        item {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = "UTXOs",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text  = "Manage All",
                    style = MaterialTheme.typography.labelSmall,
                    color = TorActive
                )
            }
        }

        // UTXO list
        items(utxos) { utxo ->
            UtxoCard(utxo)
        }
    }
}

@Composable
private fun ActionButton(
    label:    String,
    icon:     ImageVector,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    Button(
        onClick  = onClick,
        modifier = modifier.height(52.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = SurfaceAlt,
            contentColor   = TextPrimary
        )
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}