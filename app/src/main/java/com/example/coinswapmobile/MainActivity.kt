package com.example.coinswapmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.coinswapmobile.navigation.CoinSwapNavGraph
import com.example.coinswapmobile.ui.theme.CoinSwapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoinSwapTheme {
                // Change startWithRecovery = true to test the recovery screen
                CoinSwapNavGraph(startWithRecovery = false)
            }
        }
    }
}