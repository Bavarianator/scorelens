@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Badge
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.StatTile
import com.freedarts.scorer.ui.theme.DartColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color

@Composable
fun StatsScreen(vm: AppViewModel, startTab: Int = 0) {
    val players by vm.players.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(startTab) }
    var selected by remember { mutableStateOf(settings.profilePlayerId ?: players.firstOrNull()?.id) }
    var modeFilter by remember { mutableStateOf(GameMode.X01) }
    val df = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY) }

    Box(Modifier.fillMaxSize()) {
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 120)
        Column(Modifier.fillMaxSize()) {
            AdTopBar("", onBack = { vm.back() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                BrandTitle("Statistics")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TabLabel("Overview", tab == 0) { tab = 0 }
                    TabLabel("Match History", tab == 1) { tab = 1 }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    players.forEach { p -> Chip(p.name, selected = selected == p.id) { selected = p.id } }
                }
                Spacer(Modifier.height(8.dp))

                val mine = matches.filter { m -> m.players.any { it.playerId == selected } }
                if (tab == 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(GameMode.X01, GameMode.CRICKET, GameMode.COUNT_UP, GameMode.RANDOM_CHECKOUT, GameMode.AROUND_THE_CLOCK, GameMode.SHANGHAI, GameMode.KILLER).forEach { m ->
                            Chip(m.title, selected = modeFilter == m) { modeFilter = m }
                        }
                    }
                    val filtered = mine.filter { it.mode == modeFilter }
                    val stats = filtered.mapNotNull { m -> m.players.firstOrNull { it.playerId == selected } }
                    SectionLabel("${modeFilter.title} Performance")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(stats.sumOf { it.legsWon.coerceAtLeast(if (it.won) 1 else 0) }.toString(), "Legs gewonnen", Modifier.weight(1f))
                        StatTile(filtered.size.toString(), "Spiele", Modifier.weight(1f))
                        StatTile(filtered.count { it.players.size == 1 }.toString(), "Solo-Spiele", Modifier.weight(1f))
                    }
                    if (modeFilter == GameMode.X01) {
                        val darts = stats.sumOf { it.dartsThrown }
                        val avg = if (darts == 0) 0.0 else stats.sumOf { it.pointsScored }.toDouble() / darts * 3
                        val last10 = stats.takeLast(10); val prev10 = stats.dropLast(10).takeLast(10)
                        fun avgOf(l: List<com.freedarts.scorer.model.PlayerMatchStats>): Double { val d = l.sumOf { it.dartsThrown }; return if (d == 0) 0.0 else l.sumOf { it.pointsScored }.toDouble() / d * 3 }
                        fun coOf(l: List<com.freedarts.scorer.model.PlayerMatchStats>): Double { val d = l.sumOf { it.dartsAtDouble }; return if (d == 0) 0.0 else 100.0 * l.sumOf { it.checkouts } / d }
                        SectionLabel("Breakdown", trailing = { Chip("Letzte 10 Spiele") })
                        Text("Vergleich mit den 10 Spielen davor", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(last10.size.toString(), "Spiele", Modifier.weight(1f))
                            StatTile(last10.count { it.won }.toString(), "Siege", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdCard(Modifier.weight(1f)) {
                                BarCompare(avgOf(last10), avgOf(prev10), maxOf(120.0, avgOf(last10), avgOf(prev10)))
                                Text("%.2f".format(avgOf(last10)), style = MaterialTheme.typography.titleLarge)
                                Text("3-Dart Average", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                                Text("Vorher %.2f".format(avgOf(prev10)), color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            AdCard(Modifier.weight(1f)) {
                                Donut(coOf(last10) / 100.0, "%.0f %%".format(coOf(last10)))
                                Text("${last10.sumOf { it.checkouts }} / ${last10.sumOf { it.dartsAtDouble }}", style = MaterialTheme.typography.titleLarge)
                                Text("Checkout %", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                                Text("Vorher %.0f %%".format(coOf(prev10)), color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        SectionLabel("Gesamt")
                        AdCard {
                            StatLine("3-Dart Average", "%.2f".format(avg))
                            StatLine("Bester Match-Average", "%.2f".format(stats.maxOfOrNull { it.average3 } ?: 0.0))
                            StatLine("First-9 Average", "%.2f".format(stats.filter { it.first9Darts > 0 }.let { l -> val d = l.sumOf { it.first9Darts }; if (d == 0) 0.0 else l.sumOf { it.first9Points }.toDouble() / d * 3 }))
                            StatLine("Checkout-Quote", "%.1f %%".format(coOf(stats)))
                            StatLine("Höchstes Finish", (stats.maxOfOrNull { it.highestCheckout } ?: 0).toString())
                            StatLine("180er", stats.sumOf { it.count180 }.toString())
                            StatLine("170+ / 140+ / 100+ / 60+", "${stats.sumOf { it.count170Plus }} / ${stats.sumOf { it.count140Plus }} / ${stats.sumOf { it.count100Plus }} / ${stats.sumOf { it.count60Plus }}")
                            StatLine("Darts geworfen", darts.toString())
                        }
                    } else {
                        SectionLabel("Gesamt")
                        AdCard {
                            StatLine("Spiele / Siege", "${filtered.size} / ${stats.count { it.won }}")
                            StatLine("Darts geworfen", stats.sumOf { it.dartsThrown }.toString())
                            StatLine("Bestes Ergebnis", stats.maxByOrNull { it.finalScore.toIntOrNull() ?: 0 }?.finalScore ?: "–")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${mine.size} Matches", color = DartColors.TextMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearHistory() }, enabled = matches.isNotEmpty()) { Text("Verlauf löschen", color = DartColors.Red) }
                    }
                    mine.reversed().forEach { m -> MatchRow(m, selected, df) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MatchRow(m: MatchRecord, selectedId: String?, df: SimpleDateFormat) {
    val me = m.players.firstOrNull { it.playerId == selectedId } ?: return
    AdCard(Modifier.padding(bottom = 8.dp), padding = 10) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.players.joinToString(" vs ") { it.playerName }, fontWeight = FontWeight.SemiBold)
                Text(m.mode.title + (if (m.mode == GameMode.X01) " ${m.settings.baseScore} · Ø %.1f".format(me.average3) else "") + " · " + df.format(Date(m.finishedAt)),
                    color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Badge(if (me.won) "Sieg" else if (m.winnerId == null) "Remis" else "Niederlage", Color.White, if (me.won) DartColors.GreenDark else if (m.winnerId == null) DartColors.SurfaceHigh else DartColors.RedDark)
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.White else DartColors.TextMuted)
        Box(Modifier.padding(top = 4.dp).width(if (selected) 40.dp else 0.dp).height(2.dp).background(DartColors.Primary))
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = DartColors.TextMuted); Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarCompare(current: Double, previous: Double, max: Double) {
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val w = size.width; val h = size.height
        val bw = w * 0.28f
        val ch = (current / max).toFloat().coerceIn(0f, 1f) * h
        val ph = (previous / max).toFloat().coerceIn(0f, 1f) * h
        drawRect(DartColors.Primary, topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, h - ch), size = Size(bw, ch))
        drawRect(DartColors.Outline, topLeft = androidx.compose.ui.geometry.Offset(w * 0.55f, h - ph), size = Size(bw, ph), style = Stroke(3f))
    }
}

@Composable
private fun Donut(fraction: Double, label: String) {
    Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(70.dp)) {
            drawArc(DartColors.Outline, 0f, 360f, false, style = Stroke(10f))
            drawArc(DartColors.Primary, -90f, (360 * fraction).toFloat().coerceIn(0f, 360f), false, style = Stroke(10f))
        }
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}
