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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.components.SectionCard
import com.example.coinswapmobile.components.SectionLabel
import com.example.coinswapmobile.components.coinswapTextFieldColors
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.SendViewModel

private enum class SendFee(
    val label: String,
    val satPerVbyte: Int,
    val timeLabel: String
) {
    LOW("Low", 1, "1 sat/vB  60 min"),
    MEDIUM("Medium", 2, "2 sat/vB  20 min"),
    HIGH("High", 4, "4 sat/vB  10 min"),
}
private const val SEND_TX_VBYTES = 225L

@Composable
fun SendScreen(
    onBack: () -> Unit,
    sendViewModel: SendViewModel = viewModel(),
) {
    val vmState by sendViewModel.uiState.collectAsState()
    var address by remember { mutableStateOf("") }
    var amountSats by remember { mutableStateOf("") }
    var feeLevel by remember { mutableStateOf(SendFee.MEDIUM) }

    val sendEnabled = vmState.capabilities?.send != false
    val amountLong = amountSats.toLongOrNull() ?: 0L
    val networkFee = feeLevel.satPerVbyte * SEND_TX_VBYTES
    val totalSats = amountLong + networkFee

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Send",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary)
        }

        vmState.error?.let { err ->
            Text(err, style = MaterialTheme.typography.labelSmall, color = TorInactive)
        }

        SectionCard {
            SectionLabel("RECIPIENT ADDRESS")
            OutlinedTextField(
                value         = address,
                onValueChange = { address = it },
                placeholder   = { Text("tb1… or bc1…", color = TextSecondary) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                enabled       = sendEnabled,
                colors        = coinswapTextFieldColors()
            )
        }

        SectionCard {
            SectionLabel("AMOUNT")
            OutlinedTextField(
                value           = amountSats,
                onValueChange   = { amountSats = it.filter { c -> c.isDigit() } },
                placeholder     = { Text("e.g. 500000", color = TextSecondary) },
                modifier        = Modifier.fillMaxWidth(),
                singleLine      = true,
                enabled         = sendEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors          = coinswapTextFieldColors(),
                suffix          = { Text("sats", color = TextSecondary) }
            )
        }

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
                        enabled  = sendEnabled,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (sel) TorActive else SurfaceAlt,
                            contentColor   = if (sel) Color.Black else TextSecondary
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

        if (address.isNotEmpty() && amountLong > 0) {
            SectionCard {
                SectionLabel("TRANSACTION SUMMARY")
                Spacer(Modifier.height(4.dp))
                SummaryRow("To", address.take(22) + if (address.length > 22) "…" else "")
                SummaryRow("Amount", "%,d sats".format(amountLong))
                SummaryRow("Network fee (est.)", "%,d sats".format(networkFee))
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 6.dp))
                SummaryRow("Total (est.)", "%,d sats".format(totalSats), highlight = true)
            }
        }

        vmState.lastResult?.let { result ->
            SectionCard {
                SectionLabel("BROADCAST SUCCESS")
                SummaryRow("Txid", result.txid.take(20) + "…")
                SummaryRow("Amount", "%,d sats".format(result.amountSats))
                SummaryRow("Fee (est.)", "%,d sats".format(result.feeSats))
            }
        }

        Button(
            onClick  = {
                sendViewModel.send(address.trim(), amountLong, feeLevel.satPerVbyte.toLong())
            },
            enabled  = sendEnabled && address.isNotEmpty() && amountLong > 0 && !vmState.isSending,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
        ) {
            if (vmState.isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
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
