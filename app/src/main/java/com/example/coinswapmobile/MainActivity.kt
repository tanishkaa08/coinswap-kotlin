package com.example.coinswapmobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.navigation.CoinSwapNavGraph
import com.example.coinswapmobile.service.SwapNotificationHelper
import com.example.coinswapmobile.ui.theme.CoinSwapTheme
import com.example.coinswapmobile.util.AppPermissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FfiEnv.ensureHome(FfiEnv.takerDataDir(this))
        SwapNotificationHelper.ensureChannel(this)
        AppPermissions.requestSwapPermissions(this)
        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            CoinSwapTheme {
                CoinSwapNavGraph()
            }
        }
    }
}
