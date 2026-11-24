package com.mrjdm.scribe.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScribeColors = darkColorScheme(
    primary = Color(0xFFD9BD91),
    onPrimary = Color(0xFF211A10),
    primaryContainer = Color(0xFF3A3022),
    onPrimaryContainer = Color(0xFFF0DFC2),
    secondary = Color(0xFFB7A58A),
    onSecondary = Color(0xFF211B13),
    tertiary = Color(0xFF8FA6A0),
    background = Color(0xFF0D0F12),
    surface = Color(0xFF15171B),
    surfaceDim = Color(0xFF101216),
    surfaceBright = Color(0xFF292C31),
    surfaceVariant = Color(0xFF202329),
    onBackground = Color(0xFFF0EEE9),
    onSurface = Color(0xFFF0EEE9),
    onSurfaceVariant = Color(0xFFB0ADA6),
    outline = Color(0xFF3A3D43),
    outlineVariant = Color(0xFF2A2D32),
    error = Color(0xFFE28C83)
)

private val ScribeType = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-1.1).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.35).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.1.sp)
)

private val ScribeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(17.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ScribeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ScribeColors, typography = ScribeType, shapes = ScribeShapes, content = content)
}
