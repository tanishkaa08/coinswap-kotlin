package com.example.coinswapmobile.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.coinswapmobile.screens.HomeScreen
import com.example.coinswapmobile.screens.LoginScreen
import com.example.coinswapmobile.screens.MarketsScreen
import com.example.coinswapmobile.screens.RecoveryScreen
import com.example.coinswapmobile.screens.SettingsScreen
import com.example.coinswapmobile.screens.SwapRecovery
import com.example.coinswapmobile.screens.SwapScreen
import com.example.coinswapmobile.screens.SwapReportsScreen
import com.example.coinswapmobile.screens.WalletHistoryScreen
import com.example.coinswapmobile.screens.SendScreen
import com.example.coinswapmobile.screens.ReceiveScreen
import com.example.coinswapmobile.data.TakerHolder
import com.example.coinswapmobile.data.UserSession
import com.example.coinswapmobile.ui.theme.Background
import com.example.coinswapmobile.ui.theme.Surface
import com.example.coinswapmobile.ui.theme.TextSecondary
import com.example.coinswapmobile.ui.theme.TorActive

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Login    : Screen("login",    "Login",    Icons.Default.Home)
    object Home     : Screen("home",     "Home",     Icons.Default.Home)
    object Markets  : Screen("markets",  "Markets",  Icons.AutoMirrored.Filled.ShowChart)
    object Swap     : Screen("swap",     "Swap",     Icons.Default.SwapHoriz)
    object History  : Screen("history",  "History",  Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomNavItems = listOf(
    Screen.Home, Screen.Markets, Screen.Swap, Screen.History, Screen.Settings
)

private val routesWithoutBottomBar = setOf("login", "recovery", "send", "receive", "swap_reports")

private fun resolveStartRoute(context: android.content.Context): String {
    if (!UserSession.isLoggedIn(context)) return Screen.Login.route
    if (SwapRecovery.isFailedSwap(context)) return "recovery"
    return Screen.Home.route
}

@Composable
fun CoinSwapNavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val startDestination = remember { resolveStartRoute(context) }

    fun navigateAfterLogin() {
        val dest = if (SwapRecovery.isFailedSwap(context)) "recovery" else Screen.Home.route
        navController.navigate(dest) {
            popUpTo(Screen.Login.route) { inclusive = true }
        }
    }

    fun logout() {
        TakerHolder.clear()
        UserSession.clearSession(context)
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    fun goToRecovery() {
        navController.navigate("recovery") {
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (currentRoute !in routesWithoutBottomBar) {
                NavigationBar(
                    containerColor = Surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = {
                                Text(screen.label, style = MaterialTheme.typography.labelSmall)
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
            startDestination = startDestination,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(onConnected = { navigateAfterLogin() })
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onSendClick    = { navController.navigate("send") },
                    onReceiveClick = { navController.navigate("receive") }
                )
            }
            composable("send")    { SendScreen(onBack = { navController.popBackStack() }) }
            composable("receive") { ReceiveScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Markets.route)  { MarketsScreen() }
            composable(Screen.Swap.route) {
                // Activity-scoped so an in-flight swap survives tab switches.
                val activity = LocalContext.current as? ComponentActivity
                if (activity == null) {
                    Text("Swap unavailable outside an activity host.")
                } else {
                    SwapScreen(
                        onNavigateToReports = { navController.navigate("swap_reports") },
                        onSwapFailed        = { goToRecovery() },
                        swapViewModel       = viewModel(viewModelStoreOwner = activity),
                    )
                }
            }
            composable(Screen.History.route) {
                WalletHistoryScreen()
            }
            composable("swap_reports") {
                SwapReportsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenRecovery = { goToRecovery() },
                    onLogout       = { logout() },
                )
            }
            composable("recovery") {
                RecoveryScreen(
                    autoStart = true,
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("recovery") { inclusive = true }
                        }
                    },
                    onAbandon = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("recovery") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
