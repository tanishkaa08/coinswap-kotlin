package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coinswapmobile.ui.theme.*

@Composable
fun ReceiveScreen(onBack: () -> Unit) {
    val address = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"
    var copied  by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Receive Bitcoin",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary)
        }

        Spacer(Modifier.height(8.dp))

        // QR placeholder
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .border(1.dp, Divider, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▦", fontSize = 80.sp, color = TextPrimary)
                Text("QR Code", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        Text("Your Bitcoin Address",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary)

        // Address box with copy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, Divider, RoundedCornerShape(12.dp))
                .clickable {
                    copied = true
                }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = address,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = TextPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ContentCopy, "Copy",
                    tint     = if (copied) TorActive else TextSecondary,
                    modifier = Modifier.size(18.dp))
            }
        }

        if (copied) {
            Text("✓ Address copied!",
                style = MaterialTheme.typography.labelSmall,
                color = TorActive)
        }

        // Privacy note
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AccentPurple.copy(alpha = 0.10f))
                .padding(14.dp)
        ) {
            Text(
                "Each address is used only once. A new address will be generated after this one receives funds.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = { copied = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
        ) {
            Icon(Icons.Default.ContentCopy, null,
                modifier = Modifier.size(18.dp),
                tint     = androidx.compose.ui.graphics.Color.Black)
            Spacer(Modifier.width(8.dp))
            Text("COPY ADDRESS",
                color = androidx.compose.ui.graphics.Color.Black,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}