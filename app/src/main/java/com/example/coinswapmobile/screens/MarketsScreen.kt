package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.components.OrbotHelper
import com.example.coinswapmobile.components.OrbotRequiredDialog
import com.example.coinswapmobile.components.TorPromptBanner
import com.example.coinswapmobile.data.TorManager
import com.example.coinswapmobile.ui.theme.*
import com.example.coinswapmobile.viewmodel.MarketsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(marketsViewModel: MarketsViewModel = viewModel()) {
    val context = LocalContext.current
    val vmState      by marketsViewModel.uiState.collectAsState()
    val makers       = vmState.makers
    val isSyncing    = vmState.isSyncing
    var selectedMaker by remember { mutableStateOf<SwapMaker?>(null) }
    val scope        = rememberCoroutineScope()
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> marketsViewModel.startAutoSync()
                Lifecycle.Event.ON_PAUSE -> marketsViewModel.stopAutoSync()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            marketsViewModel.stopAutoSync()
        }
    }

    fun retryTorThenSync() {
        scope.launch {
            TorManager.ensureRunning(context)
            marketsViewModel.refreshTorStatus()
            marketsViewModel.syncMarketplace()
        }
    }

    OrbotRequiredDialog(
        visible = vmState.showOrbotPrompt,
        reason = vmState.orbotPromptReason,
        onDismiss = { marketsViewModel.dismissOrbotPrompt() },
    )

    if (selectedMaker != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedMaker = null },
            sheetState       = sheetState,
            containerColor   = Surface
        ) {
            MakerDetailSheet(maker = selectedMaker!!, onDismiss = { selectedMaker = null })
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Maker Marketplace",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("${makers.count { it.online }} of ${makers.size} makers online",
                        style = MaterialTheme.typography.labelSmall,
                        color = TorActive)
                }
            }
        }

        if (!vmState.torReachable) {
            item {
                TorPromptBanner(
                    onAction = {
                        scope.launch {
                            TorManager.ensureRunning(context)
                            marketsViewModel.refreshTorStatus()
                            if (!OrbotHelper.isOrbotInstalled(context)) {
                                marketsViewModel.promptOrbotFromUi(
                                    "Install Orbot",
                                )
                            }
                        }
                    },
                )
            }
        }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { retryTorThenSync() },
                    enabled  = !isSyncing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = TorActive)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = androidx.compose.ui.graphics.Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Syncing…", color = androidx.compose.ui.graphics.Color.Black,
                            style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("Sync Marketplace", color = androidx.compose.ui.graphics.Color.Black,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
                vmState.syncStatus?.let { status ->
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MAKER",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = TextSecondary,
                    modifier = Modifier.weight(1f))
                Text("FEE / HOP",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
            }
            HorizontalDivider(color = Divider, modifier = Modifier.padding(top = 6.dp))
        }
        if (makers.isEmpty()) {
            item {
                val message = vmState.errorMessage
                    ?: if (!vmState.torReachable) vmState.torStatusMessage else null
                    ?: "No makers loaded."
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (vmState.errorMessage != null) AccentAmber else TextSecondary,
                )
            }
        } else {
            items(makers) { maker ->
                MakerRow(maker = maker, onClick = { selectedMaker = maker })
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MakerRow(maker: SwapMaker, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (maker.online) TorActive else TorInactive)
            )
            Text(
                maker.id,
                style    = MaterialTheme.typography.bodyMedium,
                color    = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(TorActive.copy(alpha = 0.12f))
                .border(1.dp, TorActive.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                "${maker.feeRatePct}%",
                style = MaterialTheme.typography.labelSmall,
                color = TorActive
            )
        }
    }
}

@Composable
private fun MakerDetailSheet(maker: SwapMaker, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (maker.online) TorActive else TorInactive)
            )
            Text(
                if (maker.online) "Online" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (maker.online) TorActive else TorInactive
            )
        }

        Spacer(Modifier.height(12.dp))

        Text("Maker Details",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary)

        Spacer(Modifier.height(16.dp))
        SheetDetailRow("Maker ID",       maker.id)
        SheetDetailRow("Fee per maker",  "${maker.feeRatePct}%")
        SheetDetailRow("Min swap",       "%,d sats".format(maker.minSats))
        SheetDetailRow("Max swap",       "%,d sats".format(maker.maxSats))
        SheetDetailRow("Onion address",  maker.onionAddress)

        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape    = RoundedCornerShape(10.dp),
            border   = androidx.compose.foundation.BorderStroke(1.dp, Divider)
        ) {
            Text("Close", color = TextSecondary)
        }
    }
}

@Composable
private fun SheetDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f))
            Text(value,
                style    = MaterialTheme.typography.bodyMedium,
                color    = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.8f).wrapContentWidth(Alignment.End))
        }
    }
}
