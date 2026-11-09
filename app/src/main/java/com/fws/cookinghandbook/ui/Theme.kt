package com.fws.cookinghandbook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette: library / knowledge — ink-indigo, parchment, antique gold.
val Ink = Color(0xFF1E2A5A)
val Indigo2 = Color(0xFF3B2F7A)
val Gold = Color(0xFFE3B867)
val GoldDeep = Color(0xFFB07D2B)
val Parchment = Color(0xFFF8F3E7)
val ParchmentCard = Color(0xFFFFFCF5)
val Midnight = Color(0xFF0C1220)
val MidnightCard = Color(0xFF141B30)
val MidnightHigh = Color(0xFF1C2540)

private val LightColors = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E6F7), onPrimaryContainer = Ink,
    secondary = GoldDeep, onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E6C4), onSecondaryContainer = Color(0xFF4A3410),
    tertiary = Color(0xFF2F6F73),
    background = Parchment, onBackground = Color(0xFF1A1D2E),
    surface = ParchmentCard, onSurface = Color(0xFF1A1D2E),
    surfaceVariant = Color(0xFFEFE6D2), onSurfaceVariant = Color(0xFF55506A),
    surfaceContainer = Color(0xFFF3ECDB), surfaceContainerHigh = Color(0xFFEFE6D2),
    outline = Color(0xFFCDB98A), outlineVariant = Color(0xFFE3D6B5),
)

private val DarkColors = darkColorScheme(
    primary = Gold, onPrimary = Color(0xFF1A1405),
    primaryContainer = Color(0xFF2C2A4F), onPrimaryContainer = Color(0xFFF6E7BF),
    secondary = Color(0xFF9DB1EE), onSecondary = Color(0xFF10183A),
    secondaryContainer = MidnightHigh, onSecondaryContainer = Color(0xFFDDE4FF),
    tertiary = Color(0xFF6FC3C7),
    background = Midnight, onBackground = Color(0xFFEDE9DD),
    surface = MidnightCard, onSurface = Color(0xFFEDE9DD),
    surfaceVariant = MidnightHigh, onSurfaceVariant = Color(0xFFB7B4C8),
    surfaceContainer = Color(0xFF111830), surfaceContainerHigh = MidnightHigh,
    outline = Color(0xFF5B5480), outlineVariant = Color(0xFF2B3354),
)

private val Serif = FontFamily.Serif
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

val HeroBrush = Brush.linearGradient(listOf(Ink, Indigo2))

@Composable
fun isAppDark(themeMode: Int): Boolean = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }

@Composable
fun CookingHandbookTheme(themeMode: Int, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isAppDark(themeMode)) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
