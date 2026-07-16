package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF1C1B1F)       // Elegant Dark Background
val DeepSurface = Color(0xFF2B2930)  // Elegant Dark Surface Container
val BorderColor = Color(0xFF49454F)  // Elegant Dark Outline / Border

// Re-mapping cyber variables to maintain strict compatibility while switching to the Elegant Dark palette
val CyberPink = Color(0xFFFF4B72)    // Vibrant Rose Brand Color (Decline / Error / Rose accents)
val CyberCyan = Color(0xFFCAC4D0)    // Secondary Muted Text / Accents
val NeonGreen = Color(0xFF32D74B)    // Vibrant Neon Green (Accept / Success accents)
val CyberGold = Color(0xFFEADDFF)    // Light Purple Accent

val MutedGrey = Color(0xFFCAC4D0)    // Muted grey text
val PureWhite = Color(0xFFE6E1E5)    // Elegant light grey / off-white text

// Extra accents matching the design specification
val DarkPurpleAccent = Color(0xFF4F378B)
val DeepPurpleAccent = Color(0xFF21005D)

// For standard M3 fallbacks
val Purple80 = CyberPink
val PurpleGrey80 = MutedGrey
val Pink80 = CyberCyan

val Purple40 = DarkPurpleAccent
val PurpleGrey40 = BorderColor
val Pink40 = DeepPurpleAccent
