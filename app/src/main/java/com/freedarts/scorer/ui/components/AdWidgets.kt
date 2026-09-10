package com.freedarts.scorer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/** Hintergrund mit den diagonalen, leicht helleren Flächen des Designs. */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        fun band(x0: Float, x1: Float, alpha: Float) {
            val p = Path().apply {
                moveTo(w * x0, 0f); lineTo(w * x1, 0f); lineTo(w * (x1 - 0.55f), h); lineTo(w * (x0 - 0.55f), h); close()
            }
            drawPath(p, DartColors.Band.copy(alpha = alpha))
        }
        band(0.45f, 0.75f, 0.55f)
        band(1.05f, 1.35f, 0.35f)
    }
}

/** Blau-grüner Kopf-Swoosh. */
@Composable
fun HeaderSwoosh(modifier: Modifier = Modifier, height: Int = 150) {
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val w = size.width; val h = size.height
        val path = Path().apply { moveTo(w * 0.42f, 0f); lineTo(w, 0f); lineTo(w, h * 0.4f); lineTo(w * 0.74f, h); lineTo(w * 0.55f, h); close() }
        drawPath(path, Brush.linearGradient(listOf(Color(0xFF1E5BFF), Color(0xFF16B8B0), Color(0xFF7CF06B)), start = Offset(w * 0.45f, 0f), end = Offset(w, h)))
    }
}

/** Scorelens-Zeichen: Blendenlamellen (Kamera) um ein Bull. */
@Composable
fun BrandMark(size: Int = 24) {
    Canvas(Modifier.size(size.dp)) {
        val c = center; val r = this.size.minDimension / 2
        for (k in 0 until 6) {
            val a = Math.toRadians(-90.0 + k * 60)
            val a2 = a + Math.toRadians(22.0)
            val tip = Offset(c.x + (r * 0.42f) * Math.cos(a2).toFloat(), c.y + (r * 0.42f) * Math.sin(a2).toFloat())
            val o1 = Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat())
            val o2 = Offset(c.x + r * Math.cos(a + Math.toRadians(30.0)).toFloat(), c.y + r * Math.sin(a + Math.toRadians(30.0)).toFloat())
            val p = Path().apply { moveTo(o1.x, o1.y); lineTo(o2.x, o2.y); lineTo(tip.x, tip.y); close() }
            drawPath(p, if (k % 2 == 0) DartColors.Teal else DartColors.Primary)
        }
        drawCircle(DartColors.Cream, r * 0.34f, c)
        drawCircle(DartColors.Black, r * 0.26f, c)
        drawCircle(DartColors.Green, r * 0.15f, c)
        drawCircle(DartColors.Red, r * 0.07f, c)
    }
}

@Composable
fun BrandTitle(title: String, modifier: Modifier = Modifier, size: Int = 26) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size)
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = size.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
fun AdTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        actions()
        if (onBack != null) Spacer(Modifier.width(48.dp))
    }
}

/** Abschnittsüberschrift in Condensed-Großbuchstaben. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, size: Int = 22, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = size.sp, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun AdCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Int = 14, background: Color = DartColors.Surface, content: @Composable ColumnScope.() -> Unit) {
    var m = modifier.fillMaxWidth().background(background, RoundedCornerShape(16.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Int = 52, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(height.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DartColors.Primary, contentColor = Color.White, disabledContainerColor = DartColors.SurfaceHigh, disabledContentColor = DartColors.TextMuted)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier.height(48.dp).border(1.dp, DartColors.Outline, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) Color.White else DartColors.TextMuted); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (enabled) Color.White else DartColors.TextMuted, maxLines = 1)
    }
}

fun categoryColor(c: GameMode.Category): Pair<Color, Color> = when (c) {
    GameMode.Category.COMPETITIVE -> DartColors.Green to DartColors.GreenDark
    GameMode.Category.PRACTICE -> DartColors.Orange to DartColors.OrangeDark
    GameMode.Category.PARTY -> DartColors.PartyLime to DartColors.PartyDark
}

fun categoryLabel(c: GameMode.Category): String = when (c) {
    GameMode.Category.COMPETITIVE -> "X01"; GameMode.Category.PRACTICE -> "Practice"; GameMode.Category.PARTY -> "Party"
}

/** Pill-Badge (Kategorie, Status). */
@Composable
fun Badge(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ModeBadge(mode: GameMode, modifier: Modifier = Modifier) {
    if (mode == GameMode.CRICKET) Badge("Cricket", DartColors.PrimaryLight, Color(0xFF1B2C5E), modifier)
    else { val (fg, bg) = categoryColor(mode.category); Badge(categoryLabel(mode.category), fg, bg, modifier) }
}

/** Pill-Chip; ausgewählt = weiß mit dunkler Schrift (wie Statistik-Filter). */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    var m = modifier.background(if (selected) Color.White else DartColors.SurfaceHigh, RoundedCornerShape(999.dp))
    if (onClick != null) m = m.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick)
    Box(m.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) DartColors.Background else Color(0xFFC7CDD8))
    }
}

private val RibbonShape = GenericShape { size, _ ->
    val cut = size.height * 0.36f
    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width - cut, size.height); lineTo(0f, size.height); close()
}

/** Schräges Namens-Ribbon in Condensed-Großbuchstaben, optional mit Level-Badge. */
@Composable
fun NameRibbon(name: String, level: String? = null, levelColor: Color = DartColors.Teal, levelText: Color = Color(0xFF04211C), fontSize: Int = 16) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(DartColors.SurfaceDeep, RibbonShape).padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
            Text(name.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = fontSize.sp, letterSpacing = 0.5.sp, maxLines = 1)
        }
        if (level != null) {
            Box(Modifier.offset(x = (-8).dp).background(levelColor, RibbonShape).padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
                Text(level, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = (fontSize - 1).sp, color = levelText)
            }
        }
    }
}

/** Level aus dem Average: Text und Farbe wie im Design. */
fun levelOf(average: Double): Triple<String, Color, Color> = when {
    average <= 0 -> Triple("NEU", DartColors.SurfaceHigh, Color.White)
    average >= 100 -> Triple("100+", DartColors.Purple, Color.White)
    average >= 90 -> Triple("90+", DartColors.Pink, Color(0xFF4A1046))
    average >= 70 -> Triple("${(average / 10).toInt() * 10}+", DartColors.Teal, Color(0xFF04211C))
    else -> Triple("${(average / 10).toInt() * 10}+", DartColors.PrimaryLight, Color.White)
}

@Composable
fun LevelBadge(average: Double, modifier: Modifier = Modifier) {
    val (label, bg, fg) = levelOf(average)
    Box(modifier.background(bg, RibbonShape).padding(start = 12.dp, end = 16.dp, top = 5.dp, bottom = 5.dp)) {
        Text(label, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fg)
    }
}

/** Kennzahl-Kachel mit blauem Balken links. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, barColor: Color = DartColors.Primary) {
    Row(modifier) {
        Box(Modifier.width(2.dp).height(40.dp).background(barColor))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 11.sp, color = DartColors.TextMuted)
        }
    }
}

/** Beschreibung "How to play" je Modus. */
fun GameMode.description(): String = when (this) {
    GameMode.X01 -> "Von einem Startwert exakt auf null werfen."
    GameMode.CRICKET -> "Zahlen 15–20 und Bull dreimal treffen und schließen, dabei den Gegner am Punkten hindern."
    GameMode.AROUND_THE_CLOCK -> "Die Zahlen 1 bis 20 der Reihe nach treffen."
    GameMode.ROUND_THE_WORLD -> "Einmal ums Board: pro Runde eine Zahl, jeder Treffer zählt Punkte."
    GameMode.COUNT_UP -> "In einer festen Anzahl Runden so viele Punkte wie möglich sammeln."
    GameMode.RANDOM_CHECKOUT -> "Einen zufällig vorgegebenen Rest mit drei Darts auschecken."
    GameMode.BOBS_27 -> "Doubles treffen, um Punkte zu sammeln – Fehlrunden kosten Punkte."
    GameMode.SEGMENT_TRAINING -> "Gezielt ein bestimmtes Segment trainieren."
    GameMode.ONE_TWENTY_ONE -> "121 mit neun Darts auschecken. Geschafft: Ziel +1, sonst −1."
    GameMode.SHANGHAI -> "Single, Double und Triple derselben Zahl in einer Runde = Shanghai und sofortiger Sieg."
    GameMode.GOTCHA -> "Exakt auf das Ziel hochzählen – wer den Score eines Gegners trifft, setzt ihn auf 0."
    GameMode.BERMUDA -> "Jede Runde ein Ziel treffen, sonst wird der Score halbiert."
    GameMode.KILLER -> "Eigene Zahl als Double treffen, dann den Gegnern Leben nehmen."
}
