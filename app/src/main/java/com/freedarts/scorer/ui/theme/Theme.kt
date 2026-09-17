@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.freedarts.scorer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.freedarts.scorer.R

/**
 * Farbwelt als Palette: Dunkel = Design-Canvas im Stil der Autodarts-App, Hell = Rework „Hell & klar“
 * (Papier, Tinte, Signalrot). Logo und Dartscheibe sind in beiden gleich ([Black], [Cream], [Red], [Green], [Accent] am Board).
 */
class Palette(
    val isDark: Boolean,
    val Background: Color, val Band: Color, val Surface: Color, val SurfaceHigh: Color, val SurfaceDeep: Color, val Outline: Color,
    val Primary: Color, val PrimaryDark: Color, val PrimaryLight: Color,
    val Teal: Color, val Lime: Color, val Green: Color, val GreenDark: Color, val Red: Color, val RedDark: Color,
    val Purple: Color, val PurpleDark: Color, val Orange: Color, val OrangeDark: Color, val PartyLime: Color, val PartyDark: Color,
    val Magenta: Color, val MagentaMid: Color, val Violet: Color, val Pink: Color, val Cream: Color, val Black: Color, val Accent: Color,
    val TextMuted: Color, val Text: Color, val BottomBar: Color,
    /** Grün/Orange als Schriftfarbe: hell dunkler als die Flächenfarben (auf Papier hatten Lime/Orange nur ~2,4:1), dunkel identisch. */
    val LimeText: Color, val OrangeText: Color,
    /** Schrift auf der aktiven Spielerkarte und Farbe ihres Balkens. */
    val OnActive: Color, val ActiveBar: Color,
    /** Schrift auf getönten Karten (GreenDark/RedDark/OrangeDark) und gedämpfte Schrift auf Home-Kacheln. */
    val OnTint: Color, val OnTileMuted: Color,
    /** Verläufe der Home-Kacheln und des Kopf-Swooshs (hell: flach bzw. unsichtbar). */
    val Hero: List<Color>, val TileNew: List<Color>, val TileFind: List<Color>, val TileOnline: List<Color>, val Swoosh: List<Color>, val HeroPill: Color,
    /** Halbtransparente Overlays im Match (Zoom-Darts, Caller, Checkout) und das Match-Intro. */
    val Overlay: Color, val OnOverlay: Color, val Intro: Color,
    val ChipSelected: Color, val OnChipSelected: Color, val ChipText: Color, val Divider: Color, val BarButton: Color, val PillEmpty: Color,
    val CricketBg: Color, val CricketFg: Color,
    /** 1dp-Kante um Karten und Kacheln (dunkel: unsichtbar). */
    val CardBorder: Color,
) {
    val Blue: Color get() = Primary
    val Active: Color get() = Primary
}

val DarkPalette = Palette(
    isDark = true,
    Background = Color(0xFF0B1220), Band = Color(0x8C162650), Surface = Color(0xFF171C27), SurfaceHigh = Color(0xFF2A3040), SurfaceDeep = Color(0xFF0D1119), Outline = Color(0xFF3A4152),
    Primary = Color(0xFF2B6BFF), PrimaryDark = Color(0xFF1E4FD6), PrimaryLight = Color(0xFF4C8DFF),
    Teal = Color(0xFF19C6A6), Lime = Color(0xFF7CF06B), Green = Color(0xFF22C55E), GreenDark = Color(0xFF123B2A), Red = Color(0xFFE5484D), RedDark = Color(0xFF7A1F23),
    Purple = Color(0xFF8B5CF6), PurpleDark = Color(0xFF3B2A6E), Orange = Color(0xFFF59E5B), OrangeDark = Color(0xFF3A2418), PartyLime = Color(0xFFBEF264), PartyDark = Color(0xFF2E3A16),
    Magenta = Color(0xFFE0287E), MagentaMid = Color(0xFFB21A8E), Violet = Color(0xFF6D28D9), Pink = Color(0xFFE9A8E4), Cream = Color(0xFFF1E9D2), Black = Color(0xFF14161B), Accent = Color(0xFFFFC107),
    TextMuted = Color(0xFF9AA3B5), Text = Color(0xFFF3F5F9), BottomBar = Color(0xFF10192E),
    LimeText = Color(0xFF7CF06B), OrangeText = Color(0xFFF59E5B),
    OnActive = Color.White, ActiveBar = Color(0xFF22C55E),
    OnTint = Color.White, OnTileMuted = Color(0xFFDDE6F5),
    Hero = listOf(Color(0xFF0E3A8C), Color(0xFF0F6E8F), Color(0xFF16B8B0)), TileNew = listOf(Color(0xFF1D2A4A), Color(0xFF22346A)), TileFind = listOf(Color(0xFF15305F), Color(0xFF1D4ED8)),
    TileOnline = listOf(Color(0xFF3B1D5E), Color(0xFF6D28D9)), Swoosh = listOf(Color(0xFF1E5BFF), Color(0xFF16B8B0), Color(0xFF7CF06B)), HeroPill = Color(0x73000000),
    Overlay = Color(0xD90D1119), OnOverlay = Color.White, Intro = Color(0xF00B1220),
    ChipSelected = Color.White, OnChipSelected = Color(0xFF0B1220), ChipText = Color(0xFFC7CDD8), Divider = Color(0xFF1F5A46), BarButton = Color(0xFF1C2740), PillEmpty = Color(0xFF7B8496),
    CricketBg = Color(0xFF1B2C5E), CricketFg = Color(0xFF4C8DFF),
    CardBorder = Color.Transparent,
)

val LightPalette = Palette(
    isDark = false,
    // Band/Swoosh in Hintergrundfarbe statt transparent: Transparent.copy(alpha) wäre schwarz-grau
    Background = Color(0xFFEDE9E1), Band = Color(0xFFEDE9E1), Surface = Color(0xFFF7F4EE), SurfaceHigh = Color(0xFFE3DED4), SurfaceDeep = Color(0xFFD8D2C6), Outline = Color(0xFFCFC8BC),
    Primary = Color(0xFFE5484D), PrimaryDark = Color(0xFFB93A3E), PrimaryLight = Color(0xFFC43A3F),
    Teal = Color(0xFF0C7A6B), Lime = Color(0xFF1FB58F), Green = Color(0xFF15803D), GreenDark = Color(0xFFDCFCE7), Red = Color(0xFFE5484D), RedDark = Color(0xFFFDE2E2),
    Purple = Color(0xFF6D28D9), PurpleDark = Color(0xFFEDE9FE), Orange = Color(0xFFD97706), OrangeDark = Color(0xFFFFEDD5), PartyLime = Color(0xFF4D7C0F), PartyDark = Color(0xFFECFCCB),
    Magenta = Color(0xFFF7F4EE), MagentaMid = Color(0xFFF7F4EE), Violet = Color(0xFFF7F4EE), Pink = Color(0xFFE9A8E4), Cream = Color(0xFFF1E9D2), Black = Color(0xFF14161B), Accent = Color(0xFFB45309),
    TextMuted = Color(0xFF5C6370), Text = Color(0xFF14161B), BottomBar = Color(0xFFF7F4EE),
    LimeText = Color(0xFF0F7A5A), OrangeText = Color(0xFF9A5B06),
    OnActive = Color(0xFF14161B), ActiveBar = Color(0xFFE5484D),
    OnTint = Color(0xFF14161B), OnTileMuted = Color(0xFF5C6370),
    Hero = listOf(Color(0xFFF7F4EE), Color(0xFFF7F4EE)), TileNew = listOf(Color(0xFFF7F4EE), Color(0xFFF7F4EE)), TileFind = listOf(Color(0xFFF7F4EE), Color(0xFFF7F4EE)),
    TileOnline = listOf(Color(0xFF14161B), Color(0xFF14161B)), Swoosh = listOf(Color(0xFFEDE9E1), Color(0xFFEDE9E1), Color(0xFFEDE9E1)), HeroPill = Color(0xFFE3DED4),
    Overlay = Color(0xE6F7F4EE), OnOverlay = Color(0xFF14161B), Intro = Color(0xF0EDE9E1),
    ChipSelected = Color(0xFF14161B), OnChipSelected = Color.White, ChipText = Color(0xFF5C6370), Divider = Color(0xFFD8D2C6), BarButton = Color(0xFFE3DED4), PillEmpty = Color(0xFF9AA3B5),
    CricketBg = Color(0xFFDBEAFE), CricketFg = Color(0xFF1D4ED8),
    CardBorder = Color(0xFFD8D2C6),
)

// ponytail: eine globale, beim Theme-Wechsel getauschte Palette (alle Aufrufer lesen DartColors.X wie bisher);
// CompositionLocal erst, wenn zwei Paletten gleichzeitig auf dem Bildschirm sein müssen
var DartColors: Palette = DarkPalette
    private set

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

private fun scheme(p: Palette) = (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.Primary,
    onPrimary = Color.White,
    primaryContainer = p.PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = p.Teal,
    onSecondary = if (p.isDark) Color.Black else Color.White,
    tertiary = p.Purple,
    error = p.Red,
    background = p.Background,
    onBackground = p.Text,
    surface = p.Surface,
    onSurface = p.Text,
    surfaceVariant = p.SurfaceHigh,
    onSurfaceVariant = p.TextMuted,
    outline = p.Outline,
    surfaceContainer = p.Surface,
    surfaceContainerHigh = p.SurfaceHigh,
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

/** [mode]: "system" | "light" | "dark" (AppSettings.theme). Beim Wechsel wird der ganze Baum neu aufgebaut, damit jede Farbe neu gelesen wird. */
@Composable
fun FreeDartsTheme(mode: String = "dark", content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    DartColors = if (dark) DarkPalette else LightPalette
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(w, view).isAppearanceLightNavigationBars = !dark
            // Fensterhintergrund (Bereich hinter Status-/Navigationsleiste) in Theme-Farbe statt fest dunkel
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(DartColors.Background.toArgb()))
        }
    }
    MaterialTheme(colorScheme = scheme(DartColors), typography = typography, shapes = shapes) { key(dark) { content() } }
}
