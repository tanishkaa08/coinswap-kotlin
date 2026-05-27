package com.example.coinswapmobile.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.ui.theme.*

@Composable
fun RecoveryScreen(
    onResume:  () -> Unit,
    onAbandon: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.3f))

        // Amber-bordered warning card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, AccentAmber, RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⚠️", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Incomplete swap detected",
                style = MaterialTheme.typography.titleMedium,
                color = AccentAmber
            )
            Text(
                "The system identified an unfinished transaction session.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SWAP AMOUNT",   style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("0.01 BTC",      style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("LAST STATUS",   style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("● Negotiating", style = MaterialTheme.typography.bodyMedium, color = AccentAmber)
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = onResume,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
            ) {
                Text("▶  Resume Swap", color = androidx.compose.ui.graphics.Color.Black,
                    style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick  = onAbandon,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = androidx.compose.foundation.BorderStroke(1.dp, TorInactive)
            ) {
                Text("✕  Abandon Safely", color = TorInactive,
                    style = MaterialTheme.typography.titleMedium)
            }

            // Reassurance line — deliberately placed last
            Text(
                "● Your funds are safe. This swap can be resumed.",
                style = MaterialTheme.typography.labelSmall,
                color = TorActive
            )
        }

        Spacer(Modifier.weight(0.7f))
    }
}