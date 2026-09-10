package com.freedarts.scorer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedarts.scorer.R

/** Farbwelt nach dem FreeDarts-Design-Canvas (Stil der Autodarts-App). */
object DartColors {
    val Background = Color(0xFF0B1220)
    val Band = Color(0x8C162650)
    val Surface = Color(0xFF171C27)
    val SurfaceHigh = Color(0xFF2A3040)
    val SurfaceDeep = Color(0xFF0D1119)
    val Outline = Color(0xFF3A4152)
    val Primary = Color(0xFF2B6BFF)
    val PrimaryDark = Color(0xFF1E4FD6)
    val PrimaryLight = Color(0xFF4C8DFF)
    val Teal = Color(0xFF19C6A6)
    val Lime = Color(0xFF7CF06B)
    val Green = Color(0xFF22C55E)
    val GreenDark = Color(0xFF123B2A)
    val Red = Color(0xFFE5484D)
    val RedDark = Color(0xFF7A1F23)
    val Purple = Color(0xFF8B5CF6)
    val PurpleDark = Color(0xFF3B2A6E)
    val Orange = Color(0xFFF59E5B)
    val OrangeDark = Color(0xFF3A2418)
    val PartyLime = Color(0xFFBEF264)
    val PartyDark = Color(0xFF2E3A16)
    val Magenta = Color(0xFFE0287E)
    val MagentaMid = Color(0xFFB21A8E)
    val Violet = Color(0xFF6D28D9)
    val Pink = Color(0xFFE9A8E4)
    val Cream = Color(0xFFF1E9D2)
    val Black = Color(0xFF14161B)
    val Accent = Color(0xFFFFC107)
    val Blue = Primary
    val TextMuted = Color(0xFF9AA3B5)
    val Text = Color(0xFFF3F5F9)
    val Active = Primary
    val BottomBar = Color(0xFF10192E)
}

val Condensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_bold, FontWeight.Black),
)

val Body = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.dm_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.dm_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.dm_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.dm_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.dm_sans, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

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
    onBackground = DartColors.Text,
    surface = DartColors.Surface,
    onSurface = DartColors.Text,
    surfaceVariant = DartColors.SurfaceHigh,
    onSurfaceVariant = DartColors.TextMuted,
    outline = DartColors.Outline,
    surfaceContainer = DartColors.Surface,
    surfaceContainerHigh = DartColors.SurfaceHigh,
)

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Body, fontSize = 66.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontFamily = Condensed, fontSize = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = Condensed, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontFamily = Condensed, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = Body, fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun FreeDartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
