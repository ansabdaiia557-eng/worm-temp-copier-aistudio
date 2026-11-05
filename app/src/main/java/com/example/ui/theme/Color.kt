package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Core Dark Gaming Utility Palette (from specification)
val ObsidianDark = Color(0xFF0B0D0F)       // اللون الأساسي: أسود داكن "#0B0D0F"
val CarbonCard = Color(0xFF15191D)         // لون البطاقات: "#15191D"
val CarbonCardElevated = Color(0xFF1C2228) // طبقة بطاقات مرتفعة
val CarbonBorder = Color(0xFF262F38)       // حدود دقيقة للبطاقات

val NeonGreen = Color(0xFF00E676)          // اللون المميز: أخضر نيون "#00E676"
val NeonGreenDim = Color(0xFF00C853)       // درجة أخضر داكنة للتدرج
val NeonGreenGlow = Color(0x3300E676)      // توهج أخضر خفيف للعناصر النشطة
val NeonGreenSurface = Color(0xFF0D2418)   // خلفية خضراء داكنة للشارات

val TacticalCyan = Color(0xFF00B0FF)       // لون مميز لعملية MOVE وروابط المسارات
val TacticalCyanSurface = Color(0xFF0C222E)

val TextPrimary = Color(0xFFF5F5F5)        // لون النص الرئيسي: أبيض "#F5F5F5"
val TextSecondary = Color(0xFF92999F)      // لون النص الثانوي: رمادي "#92999F"
val TextMuted = Color(0xFF636B72)

val WarningOrange = Color(0xFFFF9100)      // لون التحذيرات: برتقالي
val WarningOrangeSurface = Color(0xFF2C1E0B)

val ErrorRed = Color(0xFFFF5252)           // لون الأخطاء: أحمر
val ErrorRedSurface = Color(0xFF2D1215)

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
