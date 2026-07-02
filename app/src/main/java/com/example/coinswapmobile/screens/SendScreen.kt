package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.components.SectionCard
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.components.coinswapTextFieldColors

// Taker-app fee rates: low=1, medium=2, high=4 sat/vB; 225 vbytes avg tx
private enum class SendFee(
    val label: String,
    val satPerVbyte: Int,
    val timeLabel: String
) {
    LOW(   "Low",    1, "1 sat/vB  60 min"),
    MEDIUM("Medium", 2, "2 sat/vB  20 min"),
    HIGH(  "High",   4, "4 sat/vB  10 min"),
}
private const val SEND_TX_VBYTES = 225L

@Composable
fun SendScreen(
    onBack: () -> Unit,
) {
    var address  by remember { mutableStateOf("") }
    var amountSats by remember { mutableStateOf("") }
    var feeLevel by remember { mutableStateOf(SendFee.MEDIUM) }
    // Send is UI-only for now — real broadcast is a follow-up (see TODO on the button).
    var notice by remember { mutableStateOf<String?>(null) }

    val amountLong   = amountSats.toLongOrNull() ?: 0L
    val networkFee   = feeLevel.satPerVbyte * SEND_TX_VBYTES
    val totalSats    = amountLong + networkFee

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Send Bitcoin",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary)
        }

        // Address
        SectionCard {
            SectionLabel("RECIPIENT ADDRESS")
            OutlinedTextField(
                value         = address,
                onValueChange = { address = it },
                placeholder   = { Text("bc1q...", color = TextSecondary) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                colors        = coinswapTextFieldColors()
            )
        }

        // Amount in sats
        SectionCard {
            SectionLabel("AMOUNT")
            OutlinedTextField(
                value           = amountSats,
                onValueChange   = { amountSats = it.filter { c -> c.isDigit() } },
                placeholder     = { Text("e.g. 500000", color = TextSecondary) },
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors          = coinswapTextFieldColors(),
                suffix          = { Text("sats", color = TextSecondary) }
            )
        }

        // Network fee Low / Medium / High
        SectionCard {
            SectionLabel("NETWORK FEE")
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SendFee.entries.forEach { tier ->
                    val sel = feeLevel == tier
                    Button(
                        onClick  = { feeLevel = tier },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (sel) TorActive else SurfaceAlt,
                            contentColor   = if (sel) androidx.compose.ui.graphics.Color.Black else TextSecondary
                        )
                    ) {
                        Text(tier.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(feeLevel.timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TorActive)
        }

        // Summary — shown once both fields are filled
        if (address.isNotEmpty() && amountLong > 0) {
            SectionCard {
                SectionLabel("TRANSACTION SUMMARY")
                Spacer(Modifier.height(4.dp))
                SummaryRow("To",          address.take(22) + if (address.length > 22) "…" else "")
                SummaryRow("Amount",      "%,d sats".format(amountLong))
                SummaryRow("Network fee", "%,d sats".format(networkFee))
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
                SummaryRow("Total",       "%,d sats".format(totalSats), highlight = true)
            }
        }

        Spacer(Modifier.height(8.dp))

        notice?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = AccentAmber)
        }

        Button(
            onClick  = {
                // TODO: sendBitcoin(address.trim(), amountLong, feeLevel.satPerVbyte)
                //   Wire this to a repository call once the Electrum wallet exposes
                //   transaction building + broadcast (coinswap PR #874 send path).
                notice = "Sending is not enabled in this build yet."
            },
            enabled  = address.isNotEmpty() && amountLong > 0,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
        ) {
            Text("CONFIRM AND SEND",
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) TorActive else TextPrimary)
    }
}

// Local SectionCard / coinswapTextFieldColors fallbacks kept for standalone compilation
@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(com.example.coinswapmobile.ui.theme.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
fun coinswapTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = com.example.coinswapmobile.ui.theme.Divider,
    focusedBorderColor   = com.example.coinswapmobile.ui.theme.TorActive,
    unfocusedTextColor   = com.example.coinswapmobile.ui.theme.TextPrimary,
    focusedTextColor     = com.example.coinswapmobile.ui.theme.TextPrimary,
    cursorColor          = com.example.coinswapmobile.ui.theme.TorActive,
)
