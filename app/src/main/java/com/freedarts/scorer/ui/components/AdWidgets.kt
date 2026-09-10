package com.freedarts.scorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.ui.theme.DartColors
import androidx.compose.foundation.Canvas

/** Blau-grüner "Swoosh" wie im Kopfbereich der Autodarts-App. */
@Composable
fun HeaderSwoosh(modifier: Modifier = Modifier, height: Int = 150) {
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val w = size.width; val h = size.height
        val path = Path().apply {
            moveTo(w * 0.35f, 0f); lineTo(w, 0f); lineTo(w, h * 0.55f)
            cubicTo(w * 0.85f, h * 0.9f, w * 0.6f, h * 0.7f, w * 0.35f, 0f); close()
        }
        drawPath(path, Brush.linearGradient(listOf(DartColors.Primary, DartColors.Teal, DartColors.Lime), start = Offset(w * 0.4f, 0f), end = Offset(w, h)))
        val path2 = Path().apply {
            moveTo(w * 0.55f, 0f); lineTo(w, 0f); lineTo(w, h * 0.35f)
            cubicTo(w * 0.9f, h * 0.55f, w * 0.7f, h * 0.45f, w * 0.55f, 0f); close()
        }
        drawPath(path2, Brush.linearGradient(listOf(DartColors.Teal, DartColors.Lime), start = Offset(w * 0.6f, 0f), end = Offset(w, h * 0.4f)))
    }
}

/** Kleines Winmau-artiges Logo-Symbol + Titel. */
@Composable
fun BrandTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp)) {
            val c = center; val r = size.minDimension / 2
            for (k in 0 until 4) {
                val a = Math.toRadians(45.0 + k * 90)
                drawLine(DartColors.Teal, c, Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat()), strokeWidth = 5f)
            }
            drawCircle(DartColors.Lime, r * 0.3f, c)
        }
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium, letterSpacing = 1.sp)
    }
}

@Composable
fun AdTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        actions()
        if (onBack != null) Spacer(Modifier.width(48.dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun AdCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    var m = modifier.fillMaxWidth().background(DartColors.Surface, RoundedCornerShape(14.dp)).border(1.dp, DartColors.Outline, RoundedCornerShape(14.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(48.dp), enabled = enabled, shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DartColors.Primary, contentColor = Color.White)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(44.dp), enabled = enabled, shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DartColors.SurfaceHigh, contentColor = Color.White)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

fun categoryColor(c: GameMode.Category): Pair<Color, Color> = when (c) {
    GameMode.Category.COMPETITIVE -> DartColors.Primary to Color(0xFF1B2C5E)
    GameMode.Category.PRACTICE -> DartColors.Green to DartColors.GreenDark
    GameMode.Category.PARTY -> DartColors.Purple to DartColors.PurpleDark
}

fun categoryLabel(c: GameMode.Category): String = when (c) {
    GameMode.Category.COMPETITIVE -> "X01"; GameMode.Category.PRACTICE -> "Practice"; GameMode.Category.PARTY -> "Party"
}

@Composable
fun Badge(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ModeBadge(mode: GameMode, modifier: Modifier = Modifier) {
    val (fg, bg) = categoryColor(mode.category)
    Badge(if (mode == GameMode.CRICKET) "Cricket" else categoryLabel(mode.category), fg, bg, modifier)
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    var m = modifier.background(if (selected) DartColors.Primary else DartColors.SurfaceHigh, RoundedCornerShape(8.dp))
        .border(1.dp, if (selected) DartColors.Primary else DartColors.Outline, RoundedCornerShape(8.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(horizontal = 10.dp, vertical = 5.dp)) { Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White) }
}

/** Kennzahl-Kachel mit farbigem Balken links (wie "X01 Performance"). */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, barColor: Color = DartColors.Primary) {
    Row(modifier.background(DartColors.Surface, RoundedCornerShape(10.dp)).border(1.dp, DartColors.Outline, RoundedCornerShape(10.dp)).padding(10.dp)) {
        Box(Modifier.width(3.dp).height(36.dp).background(barColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
        }
    }
}

@Composable
fun LevelBadge(average: Double, modifier: Modifier = Modifier) {
    val label = when {
        average <= 0 -> "NEU"
        average >= 100 -> "100+"
        else -> "${(average / 10).toInt() * 10}+"
    }
    val color = when {
        average >= 90 -> DartColors.Purple
        average >= 70 -> DartColors.Primary
        average >= 50 -> DartColors.Teal
        else -> DartColors.TextMuted
    }
    Badge(label, Color.White, color, modifier)
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
