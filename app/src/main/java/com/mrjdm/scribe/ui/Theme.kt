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
    primary = Color(0xFFD5B98C), onPrimary = Color(0xFF211B12), secondary = Color(0xFFB7A58A),
    background = Color(0xFF101113), surface = Color(0xFF191A1D), surfaceVariant = Color(0xFF24262A),
    onBackground = Color(0xFFECE9E2), onSurface = Color(0xFFECE9E2), onSurfaceVariant = Color(0xFFAAA69E), outline = Color(0xFF393A3E)
)
private val ScribeType = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp)
)
@Composable fun ScribeTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = ScribeColors, typography = ScribeType, content = content) }
