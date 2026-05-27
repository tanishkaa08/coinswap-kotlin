package com.example.coinswapmobile.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary        = TorActive,
    onPrimary      = Color.Black,
    secondary      = AccentPurple,
    onSecondary    = Color.White,
    background     = Background,
    onBackground   = TextPrimary,
    surface        = Surface,
    onSurface      = TextPrimary,
    surfaceVariant = SurfaceAlt,
    outline        = Divider,
    error          = TorInactive,
)

@Composable
fun CoinSwapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = CoinSwapTypography,
        content     = content
    )
}