package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Glassmorphism palette: deep purple / cyan gradients, frosted translucent surfaces.
// Legacy identifiers are kept so screens compile unchanged; only their values changed.
val GlassPurpleDeep = Color(0xFF1A0B4B)
val GlassIndigo = Color(0xFF0B0A2E)
val GlassTealDeep = Color(0xFF04222F)
val GlassViolet = Color(0xFF8F7BFF)
val GlassCyan = Color(0xFF22D3FF)
val GlassMint = Color(0xFF4DFFB0)

val ObsidianDark = Color(0xFF0A0620)       // text on bright accent + base background
val CarbonCard = Color(0xFF1B1440)         // opaque surface (dialogs)
val CarbonCardElevated = Color(0x1AFFFFFF) // frosted fill
val CarbonBorder = Color(0x33FFFFFF)       // glass hairline

val NeonGreen = GlassViolet                // primary accent (was neon green)
val NeonGreenDim = Color(0xFF6C5CE7)
val NeonGreenGlow = Color(0x338F7BFF)
val NeonGreenSurface = Color(0x338F7BFF)

val TacticalCyan = GlassCyan
val TacticalCyanSurface = Color(0x3322D3FF)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xB3E6E3FF)
val TextMuted = Color(0x80E6E3FF)

val WarningOrange = Color(0xFFFFB74D)
val WarningOrangeSurface = Color(0x33FFB74D)

val ErrorRed = Color(0xFFFF6B7A)
val ErrorRedSurface = Color(0x33FF6B7A)

// Aliases for Dark Gaming Theme
val ObsidianCard = CarbonCard
val ObsidianStroke = CarbonBorder
val WarningYellow = WarningOrange

// Light fallback colors if user toggles Dark Mode off in Settings
val LightBackground = Color(0xFFF2F5F7)
val LightCard = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF111518)
val LightTextSecondary = Color(0xFF525C65)
val LightBorder = Color(0xFFDCE2E8)
