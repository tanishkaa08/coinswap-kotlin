package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coinswapmobile.ui.theme.TextPrimary
import com.example.coinswapmobile.viewmodel.SendViewModel
import com.example.coinswapmobile.viewmodel.WalletViewModel

enum class TransactMode { SEND, RECEIVE }

@Composable
fun TransactScreen(
    onBack: () -> Unit,
    mode: TransactMode = TransactMode.SEND,
    sendViewModel: SendViewModel = viewModel(),
    walletViewModel: WalletViewModel = viewModel(),
) {
    var selectedIndex by remember(mode) {
        mutableIntStateOf(if (mode == TransactMode.SEND) 0 else 1)
    }

    val currentMode = if (selectedIndex == 0) TransactMode.SEND else TransactMode.RECEIVE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = null,
                tint = TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text("Transact", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }

        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedIndex == 0,
                onClick = { selectedIndex = 0 },
                text = { Text("Send") }
            )
            Tab(
                selected = selectedIndex == 1,
                onClick = { selectedIndex = 1 },
                text = { Text("Receive") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (currentMode) {
                TransactMode.SEND -> SendScreen(
                    onBack = onBack,
                    showHeader = false,
                    sendViewModel = sendViewModel
                )
                TransactMode.RECEIVE -> ReceiveScreen(
                    onBack = onBack,
                    showHeader = false,
                    walletViewModel = walletViewModel
                )
            }
        }
    }
}

