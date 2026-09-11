@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.engine.Statistics
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Badge
import com.freedarts.scorer.ui.components.BarChart
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.HeadToHeadRow
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.HeatmapBoard
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.Sparkline
import com.freedarts.scorer.ui.components.StatTile
import com.freedarts.scorer.ui.components.topSegments
import com.freedarts.scorer.ui.theme.DartColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Statistik wie bei Autodarts, für alle Modi: Übersicht über alle Modi (Aktivität, Serien, Spielzeit) oder je Modus
 * Kennzahlen, Trend über die letzten Spiele, Vergleich der letzten 10 Spiele, Details, Verteilungen aus dem
 * Wurfprotokoll, Trefferbild und Head-to-Head. Zweiter Tab: Match-Verlauf mit Leg-Details.
 */
@Composable
fun StatsScreen(vm: AppViewModel, startTab: Int = 0) {
    val players by vm.players.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(startTab) }
    var selected by remember { mutableStateOf((players.firstOrNull { it.id == settings.profilePlayerId } ?: players.firstOrNull())?.id) }
    /** null = alle Modi. */
    var modeFilter by remember { mutableStateOf<GameMode?>(null) }
    /** Zeitraum: 0 = heute, 1 = 7 Tage, 2 = 30 Tage, 3 = gesamt (wie der Zeitraum-Filter bei Autodarts). */
    var range by remember { mutableIntStateOf(3) }
    val df = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY) }
    val since = remember(range) {
        val now = System.currentTimeMillis()
        when (range) {
            0 -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            1 -> now - 7 * 86_400_000L
            2 -> now - 30 * 86_400_000L
            else -> 0L
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 150)
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
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Heute", "7 Tage", "30 Tage", "Gesamt").forEachIndexed { i, label -> Chip(label, selected = range == i) { range = i } }
                }
                Spacer(Modifier.height(6.dp))

                val playerId = selected
                val mine = matches.filter { m -> m.finishedAt >= since && m.players.any { it.playerId == playerId } }.sortedBy { it.finishedAt }
                val perMode = mine.groupBy { it.mode }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Alle", selected = modeFilter == null) { modeFilter = null }
                    GameMode.entries.forEach { m ->
                        val n = perMode[m]?.size ?: 0
                        Chip(if (n > 0) "${m.title} · $n" else m.title, selected = modeFilter == m) { modeFilter = m }
                    }
                }
                val filtered = modeFilter?.let { perMode[it].orEmpty() } ?: mine
                if (playerId == null) {
                    AdCard(Modifier.padding(top = 8.dp)) { Text("Lege zuerst einen Spieler an.", color = DartColors.TextMuted) }
                } else if (tab == 0) {
                    val mode = modeFilter
                    if (mode == null) AllModesOverview(mine, perMode, playerId) else ModeOverview(mode, filtered, playerId)
                    val heat = remember(filtered, playerId) { Statistics.heatmap(filtered, playerId) }
                    if (heat.darts > 0) {
                        SectionLabel("Trefferbild", trailing = { Chip("${heat.darts} Darts") })
                        AdCard {
                            HeatmapBoard(heat, Modifier.padding(4.dp))
                            Text(topSegments(heat), fontWeight = FontWeight.SemiBold)
                            Text("Rot = oft, Blau = selten" + (if (heat.points.isNotEmpty()) " · Punkte = Auftreffpunkte (Lens)" else ""),
                                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val h2h = remember(filtered, playerId) { Statistics.headToHead(filtered, playerId) }
                    if (h2h.isNotEmpty()) {
                        SectionLabel("Head-to-Head")
                        AdCard { h2h.forEach { HeadToHeadRow(it) } }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("${filtered.size} Matches", color = DartColors.TextMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearHistory() }, enabled = matches.isNotEmpty()) { Text("Verlauf löschen", color = DartColors.Red) }
                    }
                    filtered.reversed().forEach { m -> MatchRow(m, playerId, df) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun minutes(min: Long): String = if (min < 60) "$min min" else "%d:%02d h".format(min / 60, min % 60)

/** Übersicht über alle Modi: Gesamtzahlen, Aktivität, Serien und je Modus die Kennzahl. */
@Composable
private fun AllModesOverview(mine: List<MatchRecord>, perMode: Map<GameMode, List<MatchRecord>>, playerId: String) {
    val stats = mine.mapNotNull { m -> m.players.firstOrNull { it.playerId == playerId } }
    val wins = stats.count { it.won }
    SectionLabel("Gesamt")
    AdCard { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(mine.size.toString(), "Spiele", Modifier.weight(1f))
        StatTile(if (mine.isEmpty()) "–" else "%.0f %%".format(100.0 * wins / mine.size), "Siegquote", Modifier.weight(1f), barColor = DartColors.Green)
        StatTile(minutes(Statistics.playTimeMinutes(mine)), "Spielzeit", Modifier.weight(1f), barColor = DartColors.Accent)
    } }
    Spacer(Modifier.height(8.dp))
    StreakCard(mine, playerId, stats)
    if (mine.isNotEmpty()) {
        SectionLabel("Aktivität", trailing = { Chip("28 Tage") })
        val act = remember(mine) { Statistics.activity(mine, 28) }
        AdCard {
            BarChart(act, labels = List(28) { i -> if ((27 - i) % 7 == 0) Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i - 27) }.get(Calendar.DAY_OF_MONTH).toString() + "." else "" })
            Text("${act.sum()} Spiele in 28 Tagen · aktivster Tag ${act.max()} Spiele · heute ${act.last()}", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    SectionLabel("Alle Modi")
    AdCard {
        if (perMode.isEmpty()) Text("Noch keine Spiele im Zeitraum.", color = DartColors.TextMuted)
        GameMode.entries.forEach { mode ->
            val ms = perMode[mode] ?: return@forEach
            val s = ms.mapNotNull { m -> m.players.firstOrNull { it.playerId == playerId } }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(mode.title, fontWeight = FontWeight.SemiBold)
                    Text("${ms.size} Spiele · ${s.count { it.won }} Siege · ${s.sumOf { it.dartsThrown }} Darts", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Statistics.formatMetric(mode, Statistics.metricTotal(mode, s)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(Statistics.metricLabel(mode), color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(ms: List<MatchRecord>, playerId: String, stats: List<PlayerMatchStats>) {
    if (ms.isEmpty()) return
    val streak = remember(ms) { Statistics.streaks(ms, playerId) }
    AdCard { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(if (streak.current >= 0) "${streak.current}" else "${-streak.current} ✗", if (streak.current >= 0) "Siege in Folge" else "Niederlagen in Folge", Modifier.weight(1f), barColor = if (streak.current >= 0) DartColors.Green else DartColors.Red)
        StatTile(streak.longestWin.toString(), "Längste Siegesserie", Modifier.weight(1f))
        StatTile("%.0f".format(stats.sumOf { it.dartsThrown }.toDouble() / ms.size), "Darts pro Spiel", Modifier.weight(1f), barColor = DartColors.Accent)
    } }
}

/** Ein Modus: Kennzahlen, Trend, Vergleich letzte 10, Details, Verteilungen. */
@Composable
private fun ModeOverview(mode: GameMode, ms: List<MatchRecord>, playerId: String) {
    val stats = ms.mapNotNull { m -> m.players.firstOrNull { it.playerId == playerId } }
    val label = Statistics.metricLabel(mode)
    SectionLabel("${mode.title} Performance")
    AdCard { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(ms.size.toString(), "Spiele", Modifier.weight(1f))
        StatTile(if (ms.isEmpty()) "–" else "%.0f %%".format(100.0 * stats.count { it.won } / ms.size), "Siegquote", Modifier.weight(1f), barColor = DartColors.Green)
        StatTile(Statistics.formatMetric(mode, Statistics.metricTotal(mode, stats)), label, Modifier.weight(1f), barColor = DartColors.Accent)
    } }
    if (ms.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        AdCard { Text("Noch keine ${mode.title}-Spiele im Zeitraum.", color = DartColors.TextMuted) }
        return
    }
    Spacer(Modifier.height(8.dp))
    StreakCard(ms, playerId, stats)

    // Trend: Kennzahl je Spiel, chronologisch (letzte 20)
    val values = stats.takeLast(20).map { Statistics.metric(mode, it) }
    if (values.size >= 2) {
        SectionLabel("Trend", trailing = { Chip("Letzte ${values.size} Spiele") })
        AdCard {
            Sparkline(values, format = { Statistics.formatMetric(mode, it) })
            Text("$label je Spiel · gestrichelt = Durchschnitt · grün = Bestwert", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }

    // Vergleich: letzte 10 gegen die 10 davor (wie der Autodarts-Breakdown)
    val last10 = stats.takeLast(10); val prev10 = stats.dropLast(10).takeLast(10)
    if (prev10.isNotEmpty()) {
        val cur = Statistics.metricTotal(mode, last10); val prev = Statistics.metricTotal(mode, prev10)
        SectionLabel("Breakdown", trailing = { Chip("Letzte 10 Spiele") })
        Text("Vergleich mit den 10 Spielen davor", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdCard(Modifier.weight(1f)) {
                BarCompare(cur, prev, maxOf(cur, prev, 1.0))
                Text(Statistics.formatMetric(mode, cur), style = MaterialTheme.typography.titleLarge)
                Text(label, color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                Text("Vorher " + Statistics.formatMetric(mode, prev), color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            AdCard(Modifier.weight(1f)) {
                val w = last10.count { it.won }; val pw = prev10.count { it.won }
                Donut(w.toDouble() / last10.size, "$w / ${last10.size}")
                Text("%.0f %%".format(100.0 * w / last10.size), style = MaterialTheme.typography.titleLarge)
                Text("Siegquote", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                Text("Vorher %.0f %%".format(100.0 * pw / prev10.size), color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    SectionLabel("Details")
    AdCard {
        StatLine("Spiele / Siege", "${ms.size} / ${stats.count { it.won }}")
        StatLine("Darts geworfen", stats.sumOf { it.dartsThrown }.toString())
        StatLine("Spielzeit", minutes(Statistics.playTimeMinutes(ms)))
        modeLines(mode, ms, stats).forEach { (l, v) -> StatLine(l, v) }
    }

    // Verteilungen aus dem Wurfprotokoll
    when (mode) {
        GameMode.X01, GameMode.COUNT_UP, GameMode.GOTCHA, GameMode.SHANGHAI, GameMode.BERMUDA -> {
            val visits = remember(ms, playerId) { Statistics.visits(ms, playerId) }
            if (visits.isNotEmpty()) {
                SectionLabel("Aufnahmen", trailing = { Chip("${visits.size} Aufnahmen") })
                AdCard {
                    BarChart(Statistics.visitDistribution(visits), Statistics.VISIT_BUCKETS)
                    Text("Höchste Aufnahme ${visits.max()} · Ø %.1f pro Aufnahme · %.0f %% über 60".format(visits.average(), 100.0 * visits.count { it >= 60 } / visits.size),
                        color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (mode == GameMode.X01) {
                val outs = remember(ms, playerId) { Statistics.checkoutSegments(ms, playerId) }
                if (outs.isNotEmpty()) {
                    SectionLabel("Checkouts")
                    AdCard {
                        BarChart(outs.take(8).map { it.second }, outs.take(8).map { it.first.name })
                        Text("Lieblings-Double ${outs.first().first.name} (${outs.first().second}×) · ${outs.sumOf { it.second }} Legs ausgecheckt", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        GameMode.CRICKET -> {
            val numbers = listOf(15, 16, 17, 18, 19, 20, 25)
            val perNumber = remember(ms, playerId) { Statistics.dartsPerNumber(ms, playerId, numbers) }
            if (perNumber.sum() > 0) {
                SectionLabel("Darts je Zahl")
                AdCard { BarChart(perNumber, numbers.map { if (it == 25) "Bull" else it.toString() }) }
            }
        }
        GameMode.BOBS_27, GameMode.AROUND_THE_CLOCK, GameMode.ROUND_THE_WORLD -> {
            val numbers = (1..20).toList() + 25
            val perNumber = remember(ms, playerId) { Statistics.dartsPerNumber(ms, playerId, numbers) }
            if (perNumber.sum() > 0) {
                SectionLabel("Darts je Zahl")
                AdCard {
                    BarChart(perNumber, numbers.map { if (it == 25) "B" else if (it % 5 == 0 || it == 1) it.toString() else "" }, showValues = false)
                    val hardest = numbers[perNumber.indexOf(perNumber.max())]
                    Text("Meiste Darts auf ${if (hardest == 25) "Bull" else hardest}", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        else -> {}
    }
}

/** Modus-spezifische Detailzeilen (Label → Wert). */
private fun modeLines(mode: GameMode, ms: List<MatchRecord>, stats: List<PlayerMatchStats>): List<Pair<String, String>> {
    val darts = stats.sumOf { it.dartsThrown }
    fun avg3() = if (darts == 0) 0.0 else stats.sumOf { it.pointsScored }.toDouble() / darts * 3
    fun bestScore() = stats.maxByOrNull { it.finalScore.toIntOrNull() ?: Int.MIN_VALUE }?.finalScore ?: "–"
    fun meanScore() = "%.0f".format(stats.mapNotNull { it.finalScore.toDoubleOrNull() }.average().takeIf { !it.isNaN() } ?: 0.0)
    return when (mode) {
        GameMode.X01 -> {
            val atDouble = stats.sumOf { it.dartsAtDouble }
            listOf(
                "3-Dart Average" to "%.2f".format(avg3()),
                "Bester Match-Average" to "%.2f".format(stats.maxOfOrNull { it.average3 } ?: 0.0),
                "First-9 Average" to "%.2f".format(stats.filter { it.first9Darts > 0 }.let { l -> val d = l.sumOf { it.first9Darts }; if (d == 0) 0.0 else l.sumOf { it.first9Points }.toDouble() / d * 3 }),
                "Checkout-Quote" to "%.1f %% (${stats.sumOf { it.checkouts }} / $atDouble)".format(if (atDouble == 0) 0.0 else 100.0 * stats.sumOf { it.checkouts } / atDouble),
                "Höchstes Finish" to (stats.maxOfOrNull { it.highestCheckout } ?: 0).toString(),
                "Höchster Score" to (stats.maxOfOrNull { it.highestVisit } ?: 0).toString(),
                "Legs gewonnen / verloren" to "${stats.sumOf { it.legsWon }} / ${ms.sumOf { m -> m.players.filter { it.playerId != stats.first().playerId }.sumOf { it.legsWon } }}",
                "Bestes Leg" to (stats.filter { it.bestLegDarts > 0 }.minOfOrNull { it.bestLegDarts }?.let { "$it Darts" } ?: "–"),
                "Schlechtestes Leg" to (stats.maxOfOrNull { it.worstLegDarts }?.takeIf { it > 0 }?.let { "$it Darts" } ?: "–"),
                "180er" to stats.sumOf { it.count180 }.toString(),
                "170+ / 140+ / 100+ / 60+" to "${stats.sumOf { it.count170Plus }} / ${stats.sumOf { it.count140Plus }} / ${stats.sumOf { it.count100Plus }} / ${stats.sumOf { it.count60Plus }}",
                "Busts" to stats.sumOf { it.busts }.toString(),
            )
        }
        GameMode.CRICKET -> listOf(
            "MPR (Marks per Round)" to "%.2f".format(if (darts == 0) 0.0 else stats.sumOf { it.marks }.toDouble() / darts * 3),
            "Beste MPR" to "%.2f".format(stats.maxOfOrNull { it.mpr } ?: 0.0),
            "Marks gesamt / pro Spiel" to "${stats.sumOf { it.marks }} / %.1f".format(stats.sumOf { it.marks }.toDouble() / ms.size),
            "Punkte pro Spiel" to "%.0f".format(stats.sumOf { it.pointsScored }.toDouble() / ms.size),
        )
        GameMode.AROUND_THE_CLOCK -> {
            val hits = stats.sumOf { it.hits }
            val complete = ms.count { m -> val s = m.players.first { it.playerId == stats.first().playerId }; s.hits >= m.settings.hitsRequired.coerceAtLeast(1) * (20 + if (m.settings.includeBull) 1 else 0) }
            listOf(
                "Trefferquote" to "%.1f %%".format(if (darts == 0) 0.0 else 100.0 * hits / darts),
                "Beste Trefferquote" to "%.1f %%".format(stats.maxOfOrNull { it.hitRate } ?: 0.0),
                "Darts pro Treffer" to (if (hits == 0) "–" else "%.1f".format(darts.toDouble() / hits)),
                "Board komplett geschafft" to "$complete / ${ms.size}",
                "Schnellster Durchlauf" to (ms.filter { m -> m.players.first { it.playerId == stats.first().playerId }.let { s -> s.hits >= m.settings.hitsRequired.coerceAtLeast(1) * (20 + if (m.settings.includeBull) 1 else 0) } }
                    .minOfOrNull { m -> m.players.first { it.playerId == stats.first().playerId }.dartsThrown }?.let { "$it Darts" } ?: "–"),
            )
        }
        GameMode.SEGMENT_TRAINING -> {
            val hits = stats.sumOf { it.hits }
            listOf(
                "Treffer gesamt" to hits.toString(),
                "Trefferquote" to "%.1f %%".format(if (darts == 0) 0.0 else 100.0 * hits / darts),
                "Beste Trefferquote" to "%.1f %%".format(stats.maxOfOrNull { it.hitRate } ?: 0.0),
                "Darts pro Treffer" to (if (hits == 0) "–" else "%.1f".format(darts.toDouble() / hits)),
                "Meist trainiertes Segment" to (ms.groupBy { it.settings.trainingSegment }.maxByOrNull { it.value.size }?.key?.let { if (it == 25) "Bull" else it.toString() } ?: "–"),
            )
        }
        GameMode.ROUND_THE_WORLD -> listOf(
            "Treffer gesamt (× Multiplikator)" to stats.sumOf { it.pointsScored }.toString(),
            "Ø pro Spiel" to "%.1f".format(stats.sumOf { it.pointsScored }.toDouble() / ms.size),
            "Bestes Ergebnis" to bestScore(),
            "Treffer pro Dart" to "%.2f".format(if (darts == 0) 0.0 else stats.sumOf { it.pointsScored }.toDouble() / darts),
        )
        GameMode.COUNT_UP, GameMode.SHANGHAI, GameMode.BERMUDA -> listOf(
            "Bestes Ergebnis" to bestScore(),
            "Ø Ergebnis" to meanScore(),
            "3-Dart Average" to "%.2f".format(avg3()),
            "Punkte gesamt" to stats.sumOf { it.pointsScored }.toString(),
        )
        GameMode.RANDOM_CHECKOUT -> {
            val legs = ms.sumOf { it.settings.rounds }; val done = stats.sumOf { it.pointsScored }
            listOf(
                "Legs ausgecheckt" to "$done / $legs (%.0f %%)".format(if (legs == 0) 0.0 else 100.0 * done / legs),
                "Bestes Spiel" to bestScore(),
                "Ø Legs pro Spiel" to "%.1f".format(done.toDouble() / ms.size),
                "Typischer Bereich" to "${ms.minOf { it.settings.checkoutMin }}–${ms.maxOf { it.settings.checkoutMax }}",
            )
        }
        GameMode.BOBS_27 -> listOf(
            "Bester Endstand" to bestScore(),
            "Ø Endstand" to meanScore(),
            "Ausgeschieden (≤ 0)" to "${stats.count { (it.finalScore.toIntOrNull() ?: 1) <= 0 }} / ${ms.size}",
            "Doppel getroffen" to ms.sumOf { m -> val i = m.players.indexOfFirst { it.playerId == stats.first().playerId }; m.throws.count { it.player == i && it.leg > 0 && it.multiplier == 2 } }.toString(),
        )
        GameMode.ONE_TWENTY_ONE -> listOf(
            "Höchstes erreichtes Ziel" to bestScore(),
            "Ø erreichtes Ziel" to meanScore(),
            "Erfolgreiche Versuche" to stats.sumOf { it.pointsScored }.toString(),
            "Versuche gesamt" to ms.sumOf { it.settings.attempts }.toString(),
            "170 erreicht" to "${stats.count { (it.finalScore.toIntOrNull() ?: 0) >= 170 }} / ${ms.size}",
            "Busts" to stats.sumOf { it.busts }.toString(),
        )
        GameMode.GOTCHA -> listOf(
            "3-Dart Average" to "%.2f".format(avg3()),
            "Busts" to stats.sumOf { it.busts }.toString(),
            "Darts pro Spiel" to "%.0f".format(darts.toDouble() / ms.size),
            "Schnellster Sieg" to (stats.filter { it.won }.minOfOrNull { it.dartsThrown }?.let { "$it Darts" } ?: "–"),
        )
        GameMode.KILLER -> listOf(
            "Treffer auf Gegner" to stats.sumOf { it.pointsScored }.toString(),
            "Ø pro Spiel" to "%.1f".format(stats.sumOf { it.pointsScored }.toDouble() / ms.size),
            "Darts pro Spiel" to "%.0f".format(darts.toDouble() / ms.size),
            "Mitspieler Ø" to "%.1f".format(ms.map { it.players.size }.average()),
        )
    }
}

@Composable
private fun MatchRow(m: MatchRecord, selectedId: String?, df: SimpleDateFormat) {
    val me = m.players.firstOrNull { it.playerId == selectedId } ?: return
    var open by remember(m.id) { mutableStateOf(false) }
    val legs = remember(m.id) { if (m.mode == GameMode.X01) m.legs() else emptyList() }
    AdCard(Modifier.padding(bottom = 8.dp), padding = 10, onClick = if (legs.isNotEmpty()) ({ open = !open }) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.players.joinToString(" vs ") { it.playerName }, fontWeight = FontWeight.SemiBold)
                Text(m.mode.title + (if (m.mode == GameMode.X01) " ${m.settings.baseScore} · Ø %.1f".format(me.average3) else " · ${Statistics.metricLabel(m.mode)} ${Statistics.formatMetric(m.mode, Statistics.metric(m.mode, me))}") + " · " + df.format(Date(m.finishedAt)),
                    color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Badge(if (me.won) "Sieg" else if (m.winnerId == null) "Remis" else "Niederlage", Color.White, if (me.won) DartColors.GreenDark else if (m.winnerId == null) DartColors.SurfaceHigh else DartColors.RedDark)
        }
        // Leg-für-Leg-Verlauf aus dem Wurfprotokoll
        if (open) {
            Spacer(Modifier.height(6.dp))
            legs.forEach { leg ->
                val label = (if (m.settings.sets > 1 || leg.set > 1) "Set ${leg.set} · " else "") + "Leg ${leg.leg}"
                val detail = m.players.indices.joinToString("   ") { p ->
                    "${m.players[p].playerName.take(10)} ${leg.darts[p]} Darts Ø %.1f".format(leg.average(p)) + (if (leg.winner == p) " ✓" else "")
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(label, Modifier.width(90.dp), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    // Breite = Textbreite, sonst nimmt der fillMaxWidth-Unterstrich des ersten Tabs die ganze Zeile
    Column(Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick)) {
        Text(text, fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) Color.White else DartColors.TextMuted)
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp).background(if (selected) Color.White else Color.Transparent))
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
