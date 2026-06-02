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
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.components.SectionCard
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.components.LabeledSwitch
import com.example.coinswapmobile.components.coinswapTextFieldColors

@Composable
fun SendScreen(onBack: () -> Unit) {
    var address  by remember { mutableStateOf("") }
    var amount   by remember { mutableStateOf("") }
    var feeLevel by remember { mutableIntStateOf(1) } // 0=slow,1=medium,2=fast

    val feeSats = when (feeLevel) {
        0    -> "1 sat/vB  ~  60+ min"
        1    -> "5 sat/vB  ~  30 min"
        else -> "12 sat/vB  ~  10 min"
    }

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

        // Address field
        SectionCard {
            Text("RECIPIENT ADDRESS",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = address,
                onValueChange = { address = it },
                placeholder   = { Text("bc1q...", color = TextSecondary) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                colors        = coinswapTextFieldColors()
            )
        }

        // Amount field
        SectionCard {
            Text("AMOUNT",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = amount,
                onValueChange = { amount = it },
                placeholder   = { Text("0.00 BTC", color = TextSecondary) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors        = coinswapTextFieldColors(),
                suffix        = { Text("BTC", color = TextSecondary) }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = if (amount.isNotEmpty()) "≈ ${(amount.toDoubleOrNull() ?: 0.0) * 95000} USD" else "Enter amount",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        // Fee selector
        SectionCard {
            Text("NETWORK FEE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Slow", "Medium", "Fast").forEachIndexed { i, label ->
                    val selected = feeLevel == i
                    OutlinedButton(
                        onClick  = { feeLevel = i },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) TorActive.copy(0.15f) else SurfaceAlt,
                            contentColor   = if (selected) TorActive else TextSecondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (selected) TorActive else Divider
                        )
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(feeSats,
                style = MaterialTheme.typography.labelSmall,
                color = TorActive)
        }

        // Summary
        if (address.isNotEmpty() && amount.isNotEmpty()) {
            SectionCard {
                Text("TRANSACTION SUMMARY",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                SummaryRow("To",     address.take(20) + "...")
                SummaryRow("Amount", "$amount BTC")
                SummaryRow("Fee",    feeSats.substringBefore("~").trim())
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
                SummaryRow(
                    "Total",
                    "${(amount.toDoubleOrNull() ?: 0.0) + 0.00001} BTC",
                    highlight = true
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick  = { /* stub */ },
            enabled  = address.isNotEmpty() && amount.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
        ) {
            Text("CONFIRM & SEND",
                color = androidx.compose.ui.graphics.Color.Black,
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
// Add to SendScreen.kt bottom (or a shared file)

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
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