package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
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
fun SwapScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Text("Swap", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

        // Amount input card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AMOUNT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            OutlinedTextField(
                value         = "",
                onValueChange = {},
                placeholder   = { Text("0.00 BTC", color = TextSecondary) },
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Divider,
                    focusedBorderColor   = TorActive,
                    unfocusedTextColor   = TextPrimary,
                    focusedTextColor     = TextPrimary,
                )
            )
        }

        // Encryption layer info card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AccentPurple.copy(alpha = 0.12f))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🔒", style = MaterialTheme.typography.bodyMedium)
                Column {
                    Text(
                        "Encryption Layer Active",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentPurple
                    )
                    Text(
                        "Your address is shielded using 3-party coinswap protocols.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = { /* navigate to confirm step */ },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
        ) {
            Text("BEGIN SWAP →", color = androidx.compose.ui.graphics.Color.Black,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}