package com.example.coinswapmobile.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.example.coinswapmobile.screens.HomeScreen
import com.example.coinswapmobile.screens.MarketsScreen
import com.example.coinswapmobile.screens.RecoveryScreen
import com.example.coinswapmobile.screens.SettingsScreen
import com.example.coinswapmobile.screens.SwapScreen
import com.example.coinswapmobile.ui.theme.Background
import com.example.coinswapmobile.ui.theme.Surface
import com.example.coinswapmobile.ui.theme.TextSecondary
import com.example.coinswapmobile.ui.theme.TorActive

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",    Icons.Default.Home)
    object Markets  : Screen("markets",  "Markets", Icons.AutoMirrored.Filled.ShowChart)
    object Swap     : Screen("swap",     "Swap",    Icons.Default.SwapHoriz)
    object Settings : Screen("settings", "Settings",Icons.Default.Settings)
}

private val bottomNavItems = listOf(Screen.Home, Screen.Markets, Screen.Swap, Screen.Settings)

@Composable
fun CoinSwapNavGraph(startWithRecovery: Boolean = false) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (currentRoute != "recovery") {
                NavigationBar(
                    containerColor = Surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick  = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(screen.icon, contentDescription = screen.label) },
                            label = {
                                Text(
                                    screen.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = TorActive,
                                selectedTextColor   = TorActive,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor      = TorActive.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = if (startWithRecovery) "recovery" else Screen.Home.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onSendClick    = { },
                    onReceiveClick = { }
                )
            }
            composable(Screen.Markets.route)  { MarketsScreen() }
            composable(Screen.Swap.route)     { SwapScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable("recovery") {
                RecoveryScreen(
                    onResume  = { navController.navigate(Screen.Home.route) },
                    onAbandon = { navController.navigate(Screen.Home.route) }
                )
            }
        }
    }
}