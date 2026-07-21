package com.example.coinswapmobile.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.viewmodel.SwapReportsViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.coinswapmobile.ui.theme.*

// ── Models ────────────────────────────────────────────────────────────────────

enum class ReportStatus { COMPLETED, FAILED }

data class SwapReport(
    val id:            String,
    val timeAgo:       String,
    val duration:      String,
    val status:        ReportStatus,
    val hops:          Int,
    val protocol:      String,   // TAPROOT / LEGACY
    val amountSats:    Long,
    val makerCount:    Int,
    val totalFeeSats:  Long,
    val outputSats:    Long,
    val errorMessage:  String? = null
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SwapReportsScreen(
    onBack: (() -> Unit)? = null,
    reportsViewModel: SwapReportsViewModel = viewModel(),
) {
    val vmState by reportsViewModel.uiState.collectAsState()
    val reports = vmState.reports

    val totalReports  = reports.size
    val failedCount   = reports.count { it.status == ReportStatus.FAILED }
    val totalVolume   = reports.filter { it.status == ReportStatus.COMPLETED }.sumOf { it.amountSats }
    val totalFees     = reports.filter { it.status == ReportStatus.COMPLETED }.sumOf { it.totalFeeSats }
    val avgHops       = if (reports.isEmpty()) 0.0 else reports.map { it.hops }.average()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                }
                Column {
                    Text("Swap History",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary)
                }
            }
        }

        // Stats grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Total",    "$totalReports",          Modifier.weight(1f))
                StatCard("Failed",   "$failedCount",           Modifier.weight(1f), AccentAmber)
                StatCard("Avg Makers", "%.1f".format(avgHops),  Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Volume", "%,d sats".format(totalVolume), Modifier.weight(1f))
                StatCard("Fees",   "%,d sats".format(totalFees),   Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Swap list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (reports.isEmpty()) {
                item {
                    val msg = vmState.errorMessage ?: "No swap history yet."
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(reports) { report ->
                    SwapReportRow(report)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, valueColor: Color = TextPrimary) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value,
            style    = MaterialTheme.typography.bodyMedium,
            color    = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SwapReportRow(report: SwapReport) {
    val isFailed   = report.status == ReportStatus.FAILED
    val borderColor = if (isFailed) AccentAmber.copy(0.4f) else Divider
    val bgColor     = if (isFailed) AccentAmber.copy(0.05f) else Surface

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top row: ID + expand toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Swap ${report.id}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide details" else "Show details",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${report.timeAgo}  •  ${report.duration}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary)
        }

        // Badges row
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge(report.status)
            Badge("${report.makerCount} MAKERS", TorActive)
            Badge(report.protocol, AccentPurple)
        }

        // Error message
        if (isFailed && report.errorMessage != null) {
            Text(report.errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = AccentAmber)
        }

        // Details revealed on tap
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = Divider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DataCol("AMOUNT",  "%,d".format(report.amountSats))
                    DataCol("MAKERS",  "${report.makerCount}")
                    DataCol("TOTAL FEE","%,d".format(report.totalFeeSats))
                    DataCol("OUTPUT",  if (isFailed) "0" else "%,d".format(report.outputSats), TorActive)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DataCol("HOPS",     "${report.hops}")
                    DataCol("PROTOCOL", report.protocol)
                    DataCol("DURATION", report.duration)
                }
                DataCol("SWAP ID", report.id)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ReportStatus) {
    val (text, color) = when (status) {
        ReportStatus.COMPLETED -> "COMPLETED" to TorActive
        ReportStatus.FAILED    -> "FAILED"    to AccentAmber
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun DataCol(label: String, value: String, valueColor: Color = TextPrimary) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
