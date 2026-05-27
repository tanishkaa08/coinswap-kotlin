package com.example.coinswapmobile.ui.theme

import androidx.compose.ui.graphics.Color

// CoinSwap brand palette — matches the Figma dark mockups
val Background    = Color(0xFF0D1117)   // near-black background
val Surface       = Color(0xFF161B22)   // card surface
val SurfaceAlt    = Color(0xFF1C2128)   // slightly lighter card
val TorActive     = Color(0xFF00E5A0)   // teal — Tor active / high privacy
val TorInactive   = Color(0xFFE05252)   // red — exposed / no Tor
val AccentPurple  = Color(0xFF9B59F5)   // purple — encrypted route
val AccentAmber   = Color(0xFFF0A500)   // amber — warning / recovery
val TextPrimary   = Color(0xFFE6EDF3)   // near-white text
val TextSecondary = Color(0xFF8B949E)   // muted label text
val Divider       = Color(0xFF30363D)   // subtle divider

// Privacy dot colors (UTXO privacy level)
val PrivacyHigh   = TorActive            // all dots teal
val PrivacyMed    = AccentPurple         // mixed
val PrivacyLow    = TorInactive          // exposed