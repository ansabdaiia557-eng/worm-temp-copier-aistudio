package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val DarkGamingColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = ObsidianDark,
    primaryContainer = NeonGreenSurface,
    onPrimaryContainer = NeonGreen,
    secondary = TacticalCyan,
    onSecondary = ObsidianDark,
    secondaryContainer = TacticalCyanSurface,
    onSecondaryContainer = TacticalCyan,
    tertiary = WarningOrange,
    onTertiary = ObsidianDark,
    tertiaryContainer = WarningOrangeSurface,
    onTertiaryContainer = WarningOrange,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedSurface,
    onErrorContainer = ErrorRed,
    background = ObsidianDark,
    onBackground = TextPrimary,
    surface = CarbonCard,
    onSurface = TextPrimary,
    surfaceVariant = CarbonCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = CarbonBorder
)

private val LightUtilityColorScheme = lightColorScheme(
    primary = Color(0xFF009E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7FCE7),
    onPrimaryContainer = Color(0xFF005227),
    secondary = Color(0xFF0077B6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F0FF),
    onSecondaryContainer = Color(0xFF003B5C),
    tertiary = Color(0xFFE67700),
    error = Color(0xFFD32F2F),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightCard,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFE7ECEF),
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun WormTempCopierTheme(
    darkTheme: Boolean = true,
    isRtl: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkGamingColorScheme else LightUtilityColorScheme
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
