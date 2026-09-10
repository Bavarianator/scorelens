package com.freedarts.scorer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Farbwelt angelehnt an die Winmau-Autodarts-App: dunkles Navy, Blau als Primärfarbe, Grün/Lila für Kategorien. */
object DartColors {
    val Background = Color(0xFF0A0E17)
    val Surface = Color(0xFF141A26)
    val SurfaceHigh = Color(0xFF1C2331)
    val Outline = Color(0xFF263042)
    val Primary = Color(0xFF2F6BFF)
    val PrimaryDark = Color(0xFF1E4FCC)
    val Teal = Color(0xFF19C6A6)
    val Lime = Color(0xFF7CF06B)
    val Green = Color(0xFF27C26A)
    val GreenDark = Color(0xFF0F5A34)
    val Red = Color(0xFFE5484D)
    val RedDark = Color(0xFF7A1F23)
    val Purple = Color(0xFF8B5CF6)
    val PurpleDark = Color(0xFF3B2A6E)
    val Cream = Color(0xFFF1E9D2)
    val Black = Color(0xFF14161B)
    val Accent = Color(0xFFFFC107)
    val Blue = Primary
    val TextMuted = Color(0xFF8B95A7)
    val Active = Primary
    val Pink = Color(0xFFD946EF)
}

private val scheme = darkColorScheme(
    primary = DartColors.Primary,
    onPrimary = Color.White,
    primaryContainer = DartColors.PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = DartColors.Teal,
    onSecondary = Color.Black,
    tertiary = DartColors.Purple,
    error = DartColors.Red,
    background = DartColors.Background,
    onBackground = Color(0xFFEEF1F6),
    surface = DartColors.Surface,
    onSurface = Color(0xFFEEF1F6),
    surfaceVariant = DartColors.SurfaceHigh,
    onSurfaceVariant = DartColors.TextMuted,
    outline = DartColors.Outline,
    surfaceContainer = DartColors.Surface,
    surfaceContainerHigh = DartColors.SurfaceHigh,
)

private val typography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Black),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

@Composable
fun FreeDartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
