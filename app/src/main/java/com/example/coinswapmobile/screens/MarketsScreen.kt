package com.example.coinswapmobile.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.coinswapmobile.ui.theme.*

@Composable
fun MarketsScreen() {
    Box(
        modifier        = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Maker Marketplace", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("Maker list loads here", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}