package com.freedarts.scorer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.freedarts.scorer.engine.Achievements
import com.freedarts.scorer.engine.Statistics
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/**
 * Player Card wie bei Autodarts (Tipp auf einen Spielernamen): Banner in Spielerfarbe, Level, X01-Kennzahlen,
 * Form, Head-to-Head gegen das eigene Profil und Erfolge als Medaillen. Alles aus dem Verlauf, kein eigener Zustand.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlayerCardDialog(player: Player, matches: List<MatchRecord>, profileId: String?, onDismiss: () -> Unit, onAllAchievements: (() -> Unit)? = null) {
    val mine = remember(matches, player.id) { matches.filter { m -> m.players.any { it.playerId == player.id } } }
    val stats = remember(mine) { mine.map { m -> m.players.first { it.playerId == player.id } } }
    val x01 = remember(mine) { mine.filter { it.mode == GameMode.X01 }.map { m -> m.players.first { it.playerId == player.id } } }
    val avg = if (player.isBot && x01.isEmpty()) Player.botAverage(player.botLevel).toDouble() else Statistics.metricTotal(GameMode.X01, x01)
    val (lvl, lvlBg, lvlFg) = levelOf(avg)
    val atDouble = x01.sumOf { it.dartsAtDouble }
    val h2h = remember(matches, profileId, player.id) { if (profileId == null || profileId == player.id) null else Statistics.headToHead(matches, profileId).firstOrNull { it.opponentId == player.id } }
    val badges = remember(mine) { Achievements.of(mine, player.id) }
    var picked by remember { mutableStateOf<Achievements.Achievement?>(null) }
    val tint = Color(player.color)

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(DartColors.Surface).verticalScroll(rememberScrollState())) {
            // Banner in Spielerfarbe, Avatar hängt über die Kante
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                Box(Modifier.fillMaxWidth().height(84.dp).background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.35f), DartColors.Surface))))
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, "Schließen", tint = Color.White) }
                Box(Modifier.align(Alignment.BottomCenter).border(3.dp, DartColors.Surface, CircleShape).padding(3.dp)) { Avatar(player, 72, online = false) }
            }
            Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                NameRibbon(player.name, lvl, lvlBg, lvlFg, fontSize = 20)
                Text(
                    if (mine.isEmpty()) "Noch kein Spiel" else "${mine.size} Spiele · ${stats.count { it.won }} Siege · %.0f %% Siegquote".format(100.0 * stats.count { it.won } / mine.size),
                    color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                )
                if (x01.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("%.1f".format(avg), "Ø X01", DartColors.Primary, Modifier.weight(1f))
                        Tile("%.0f %%".format(if (atDouble == 0) 0.0 else 100.0 * x01.sumOf { it.checkouts } / atDouble), "Checkout", DartColors.Green, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(x01.sumOf { it.count180 }.toString(), "180er", DartColors.Lime, Modifier.weight(1f))
                        Tile((x01.maxOfOrNull { it.highestCheckout } ?: 0).toString(), "Höchstes Finish", DartColors.Orange, Modifier.weight(1f))
                    }
                    if (x01.size >= 2) {
                        SectionLabel("Form", size = 16, trailing = { Chip("letzte ${x01.takeLast(10).size}") })
                        Sparkline(x01.takeLast(10).map { it.average3 })
                    }
                }
                if (h2h != null) {
                    SectionLabel("Head-to-Head", size = 16)
                    HeadToHeadCompare(h2h)
                }
                val done = badges.count { it.done }
                SectionLabel("Erfolge", size = 16, trailing = { Chip("$done / ${badges.size}") })
                // Fortschrittsbalken über alle Erfolge
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DartColors.SurfaceHigh)) {
                    Box(Modifier.fillMaxWidth(done.toFloat() / badges.size).height(6.dp).background(Brush.horizontalGradient(listOf(DartColors.Orange, DartColors.Accent))))
                }
                Spacer(Modifier.height(12.dp))
                // Erst die erreichten, dann die nächsten offenen – der Rest im Erfolge-Screen
                val shown = remember(badges) { (badges.filter { it.done } + badges.filter { !it.done }).take(4) }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
                    shown.forEach { a -> Medal(a, selected = picked == a) { picked = if (picked == a) null else a } }
                }
                // Tipp auf eine Medaille: Beschreibung und Stand
                val p = picked
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).background(DartColors.SurfaceHigh, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            p == null -> "Medaille antippen für Details"
                            p.done -> "${p.icon} ${p.title} · ${p.description} · ${tierName(p.tier)}"
                            else -> "${p.title} · ${p.description} · ${p.progress} / ${p.goal}"
                        },
                        fontSize = 12.sp, color = if (p == null) DartColors.TextMuted else DartColors.Text, textAlign = TextAlign.Center,
                    )
                }
                if (onAllAchievements != null) { Spacer(Modifier.height(10.dp)); SecondaryButton("Alle Erfolge", Modifier.fillMaxWidth()) { onAllAchievements() } }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Kennzahl-Kachel mit farbigem Wert auf dunklem Grund. */
@Composable
private fun Tile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(DartColors.SurfaceHigh, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp, color = color)
        Text(label, fontSize = 11.sp, color = DartColors.TextMuted)
    }
}

fun tierName(t: Int) = when (t) { 1 -> "Bronze"; 2 -> "Silber"; else -> "Gold" }

/** Farbpaar (Rand, Mitte) je Stufe. */
fun tierColors(t: Int): Pair<Color, Color> = when (t) {
    1 -> Color(0xFF9C5A22) to Color(0xFFE39B5A)
    2 -> Color(0xFF7C8694) to Color(0xFFE6EBF2)
    else -> Color(0xFFB8860B) to Color(0xFFFFE082)
}

/**
 * Medaille: freigeschaltet = Metall-Verlauf mit Glanzpunkt und Ring, sonst dunkle Scheibe mit Fortschrittsbogen.
 * Emoji als Motiv, kein Icon-Set nötig.
 */
@Composable
fun Medal(a: Achievements.Achievement, selected: Boolean, diameter: Int = 60, onClick: () -> Unit) {
    val (edge, center) = tierColors(a.tier)
    Column(Modifier.width((diameter + 12).dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(diameter.dp)) {
                val r = size.minDimension / 2
                val stroke = 4.dp.toPx()
                if (a.done) {
                    drawCircle(Brush.radialGradient(listOf(center, edge), center = Offset(r * 0.8f, r * 0.7f), radius = r * 1.2f), radius = r - stroke)
                    drawCircle(edge, radius = r - stroke / 2, style = Stroke(stroke))
                    // Glanzpunkt oben links
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 0.28f, center = Offset(r * 0.62f, r * 0.55f))
                } else {
                    drawCircle(DartColors.SurfaceHigh, radius = r - stroke)
                    drawCircle(DartColors.Outline, radius = r - stroke / 2, style = Stroke(stroke))
                    drawArc(edge, startAngle = -90f, sweepAngle = 360f * a.progress / a.goal, useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                }
                if (selected) drawCircle(Color.White, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            Text(a.icon, fontSize = (diameter * 0.43f).sp, modifier = Modifier.alpha(if (a.done) 1f else 0.35f))
        }
        Text(a.title, fontSize = 11.sp, fontWeight = if (a.done) FontWeight.SemiBold else FontWeight.Normal, color = if (a.done) DartColors.Text else DartColors.TextMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
