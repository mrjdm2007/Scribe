package com.mrjdm.scribe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ScribeColors = darkColorScheme(
    primary = Color(0xFFD8BE98),
    onPrimary = Color(0xFF211B12),
    primaryContainer = Color(0xFF3A3328),
    onPrimaryContainer = Color(0xFFF0E4D1),
    secondary = Color(0xFFB8A58A),
    onSecondary = Color(0xFF211B12),
    tertiary = Color(0xFF91A7A0),
    background = Color(0xFF0D0F12),
    surface = Color(0xFF17191D),
    surfaceVariant = Color(0xFF24272C),
    onBackground = Color(0xFFF0EEE9),
    onSurface = Color(0xFFF0EEE9),
    onSurfaceVariant = Color(0xFFAAA69E),
    outline = Color(0xFF41444A),
    outlineVariant = Color(0xFF2B2E33)
)

private val ScribeType = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-1.1).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.35).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.1.sp)
)

@Composable
fun ScribeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ScribeColors, typography = ScribeType, content = content)
}
