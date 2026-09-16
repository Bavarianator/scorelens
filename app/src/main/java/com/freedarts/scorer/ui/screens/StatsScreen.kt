@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.freedarts.scorer.ui.components.PrimaryButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.engine.AimAdvisor
import com.freedarts.scorer.engine.Coach
import com.freedarts.scorer.engine.Statistics
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Badge
import com.freedarts.scorer.ui.components.BarChart
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.HeadToHeadCompare
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.HeatmapBoard
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.Sparkline
import com.freedarts.scorer.ui.components.StatTile
import com.freedarts.scorer.ui.components.topSegments
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Statistik-Seite, eine Spalte ohne Tabs: Spieler (Avatare) → Zeitraum (Segmented Control) → Modus (Chips) →
 * Hero-Kennzahl mit Trend → Kennzahlen → je nach Modus Aktivität/Modi-Liste oder Details/Verteilungen →
 * Trefferbild → Head-to-Head → letzte Matches (aufklappbar). [startTab] = 1 öffnet den Verlauf komplett.
 */
@Composable
fun StatsScreen(vm: AppViewModel, startTab: Int = 0) {
    val players by vm.players.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var playerId by remember { mutableStateOf((players.firstOrNull { it.id == settings.profilePlayerId } ?: players.firstOrNull { !it.isBot })?.id) }
    /** null = alle Modi. */
    var mode by remember { mutableStateOf<GameMode?>(null) }
    /** 0 = heute, 1 = 7 Tage, 2 = 30 Tage, 3 = gesamt. */
    var range by remember { mutableIntStateOf(3) }
    var showAll by remember { mutableStateOf(startTab == 1) }
    val df = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY) }
    val since = remember(range) {
        when (range) {
            0 -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            1 -> System.currentTimeMillis() - 7 * 86_400_000L
            2 -> System.currentTimeMillis() - 30 * 86_400_000L
            else -> 0L
        }
    }
    val mine = remember(matches, playerId, since) { matches.filter { m -> m.finishedAt >= since && m.players.any { it.playerId == playerId } }.sortedBy { it.finishedAt } }
    val perMode = remember(mine) { mine.groupBy { it.mode } }
    val ms = mode?.let { perMode[it].orEmpty() } ?: mine
    val id = playerId
    val stats = remember(ms, id) { ms.mapNotNull { m -> m.players.firstOrNull { it.playerId == id } } }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 150)
        Column(Modifier.fillMaxSize()) {
            AdTopBar("", onBack = { vm.back() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                BrandTitle("Statistics")
                Spacer(Modifier.height(12.dp))
                ChipRow(gap = 14) { players.filter { !it.isBot }.forEach { p -> PlayerPick(p, p.id == id) { playerId = p.id } } }
                Spacer(Modifier.height(10.dp))
                Segmented(listOf("Heute", "7 Tage", "30 Tage", "Gesamt"), range) { range = it }
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    Chip("Alle", selected = mode == null) { mode = null }
                    GameMode.entries.forEach { m ->
                        val n = perMode[m]?.size ?: 0
                        Chip(if (n > 0) "${m.title} · $n" else m.title, selected = mode == m) { mode = m }
                    }
                }

                when {
                    id == null -> AdCard(Modifier.padding(top = 10.dp)) { Text("Lege zuerst einen Spieler an.", color = DartColors.TextMuted) }
                    ms.isEmpty() -> AdCard(Modifier.padding(top = 10.dp)) {
                        Text("NOCH NICHTS ZU SEHEN", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(if (mine.isEmpty()) "Spiel ein Match, dann stehen hier deine Zahlen." else "Keine ${mode?.title}-Spiele im Zeitraum.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        Spacer(Modifier.height(10.dp))
                        Hero(mode, ms, stats)
                        Spacer(Modifier.height(8.dp))
                        val streak = remember(ms, id) { Statistics.streaks(ms, id) }
                        AdCard { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(ms.size.toString(), "Spiele", Modifier.weight(1f))
                            StatTile("%.0f %%".format(100.0 * stats.count { it.won } / ms.size), "Siegquote", Modifier.weight(1f), barColor = DartColors.Green)
                            StatTile(if (streak.current >= 0) "${streak.current}" else "${-streak.current}", if (streak.current >= 0) "Siege in Folge" else "Niederlagen in Folge", Modifier.weight(1f), barColor = if (streak.current >= 0) DartColors.Lime else DartColors.Red)
                        } }
                        val m = mode
                        if (m == null) AllModes(mine, perMode, id) { mode = it } else ModeDetails(m, ms, stats, id)

                        val heat = remember(ms, id) { Statistics.heatmap(ms, id) }
                        val advice = remember(ms, id) { AimAdvisor.scatter(ms, id)?.let { it to AimAdvisor.advise(it.sigmaMm) } }
                        if (heat.darts > 0) {
                            SectionLabel("Trefferbild", trailing = { Chip("${heat.darts} Darts") })
                            AdCard {
                                HeatmapBoard(heat, Modifier.padding(4.dp), mark = advice?.second?.best)
                                Text(topSegments(heat), fontWeight = FontWeight.SemiBold)
                                Text("Rot = oft, Blau = selten" + (if (heat.points.isNotEmpty()) " · Punkte = Auftreffpunkte (Lens)" else ""), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                                if (advice != null) {
                                    val (sc, ad) = advice
                                    Text("Zielhilfe: Streuung ø %.0f mm (%d Darts) · bester Zielpunkt %s mit Ø %.1f Punkte pro Dart (T20: %.1f · Bull: %.1f)".format(
                                        sc.sigmaMm, sc.darts, ad.best.name, ad.expected[ad.best] ?: 0.0, ad.expected[Segment.triple(20)] ?: 0.0, ad.expected[Segment.BULL] ?: 0.0),
                                        color = DartColors.Lime, style = MaterialTheme.typography.bodySmall)
                                } else if (heat.points.isNotEmpty()) Text("Zielhilfe ab ${AimAdvisor.MIN_DARTS} Lens-Darts", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        // Trainings-Coach: schwächste Doppel aus den angepeilten Zielen, ein Tipp startet das passende Segment-Training
                        val coach = remember(ms, id) { Coach.weakestDoubles(Coach.targets(ms, id)) }
                        if (coach.isNotEmpty()) {
                            SectionLabel("Trainings-Coach")
                            AdCard {
                                coach.forEach { t -> Text("${t.aim.name} · ${"%.0f".format(t.rate * 100)} % getroffen (${t.hits} von ${t.attempts})", fontWeight = FontWeight.SemiBold) }
                                Text("Deine schwächsten Doppel. Segment-Training bis 10 Treffer – das Training zählt wieder in diese Quote.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                val weakest = coach.first().aim
                                PrimaryButton("Segment-Training auf ${weakest.name} starten", Modifier.fillMaxWidth(), height = 44) { vm.startTraining(weakest) }
                            }
                        }
                        val h2h = remember(ms, id) { Statistics.headToHead(ms, id) }
                        if (h2h.isNotEmpty()) {
                            SectionLabel("Head-to-Head")
                            h2h.forEach { AdCard(Modifier.padding(bottom = 8.dp)) { HeadToHeadCompare(it) } }
                        }

                        SectionLabel("Matches", trailing = { Chip("${ms.size}") })
                        ms.asReversed().take(if (showAll) Int.MAX_VALUE else 5).forEach { MatchRow(it, id, df) { vm.openMatch(it) } }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (ms.size > 5) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Weniger" else "Alle ${ms.size} anzeigen") } else Spacer(Modifier.width(1.dp))
                            TextButton(onClick = { vm.clearHistory() }) { Text("Verlauf löschen", color = DartColors.Red) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Große Kennzahl mit Vergleich der letzten 10 gegen die 10 Spiele davor und Verlauf der letzten 20 Spiele. */
@Composable
private fun Hero(mode: GameMode?, ms: List<MatchRecord>, stats: List<PlayerMatchStats>) {
    AdCard(padding = 16) {
        if (mode == null) {
            Text(ms.size.toString(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
            Text("Spiele · ${stats.count { it.won }} Siege · ${minutes(Statistics.playTimeMinutes(ms))} Spielzeit", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            return@AdCard
        }
        val total = Statistics.metricTotal(mode, stats)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Statistics.formatMetric(mode, total), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 10.dp)) {
                Text(Statistics.metricLabel(mode), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                val prev = stats.dropLast(10).takeLast(10)
                if (prev.isNotEmpty()) {
                    val d = Statistics.metricTotal(mode, stats.takeLast(10)) - Statistics.metricTotal(mode, prev)
                    // alle Kennzahlen: höher = besser
                    Text((if (d >= 0) "▲ +" else "▼ −") + Statistics.formatMetric(mode, kotlin.math.abs(d)) + " vs. 10 davor", color = if (d >= 0) DartColors.Lime else DartColors.Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        val values = stats.takeLast(20).map { Statistics.metric(mode, it) }
        if (values.size >= 2) {
            Spacer(Modifier.height(6.dp))
            Sparkline(values, format = { Statistics.formatMetric(mode, it) })
            Text("Letzte ${values.size} Spiele · gestrichelt = Durchschnitt · grün = Bestwert", color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Alle Modi: Aktivität der letzten 28 Tage und je Modus die Kennzahl; Tipp auf eine Zeile filtert auf den Modus. */
@Composable
private fun AllModes(mine: List<MatchRecord>, perMode: Map<GameMode, List<MatchRecord>>, playerId: String, onPick: (GameMode) -> Unit) {
    SectionLabel("Aktivität", trailing = { Chip("28 Tage") })
    val act = remember(mine) { Statistics.activity(mine, 28) }
    AdCard {
        BarChart(act, labels = List(28) { i -> if ((27 - i) % 7 == 0) Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i - 27) }.get(Calendar.DAY_OF_MONTH).toString() + "." else "" })
        Text("${act.sum()} Spiele in 28 Tagen · aktivster Tag ${act.max()} · heute ${act.last()}", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
    SectionLabel("Modi")
    AdCard(padding = 6) {
        GameMode.entries.forEach { mode ->
            val ms = perMode[mode] ?: return@forEach
            val s = ms.mapNotNull { m -> m.players.firstOrNull { it.playerId == playerId } }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(mode) }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(mode.title, fontWeight = FontWeight.SemiBold)
                    Text("${ms.size} Spiele · ${s.count { it.won }} Siege · ${s.sumOf { it.dartsThrown }} Darts", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Statistics.formatMetric(mode, Statistics.metricTotal(mode, s)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(Statistics.metricLabel(mode), color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                Text("›", color = DartColors.TextMuted, fontSize = 22.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** Ein Modus: Detailzeilen und Verteilungen aus dem Wurfprotokoll. */
@Composable
private fun ModeDetails(mode: GameMode, ms: List<MatchRecord>, stats: List<PlayerMatchStats>, playerId: String) {
    SectionLabel("Details")
    AdCard {
        StatLine("Darts geworfen", stats.sumOf { it.dartsThrown }.toString())
        StatLine("Darts pro Spiel", "%.0f".format(stats.sumOf { it.dartsThrown }.toDouble() / ms.size))
        StatLine("Spielzeit", minutes(Statistics.playTimeMinutes(ms)))
        modeLines(mode, ms, stats).forEach { (l, v) -> StatLine(l, v) }
    }
    when (mode) {
        GameMode.X01, GameMode.COUNT_UP, GameMode.GOTCHA, GameMode.SHANGHAI, GameMode.BERMUDA -> {
            val visits = remember(ms, playerId) { Statistics.visits(ms, playerId) }
            if (visits.isNotEmpty()) {
                SectionLabel("Aufnahmen", trailing = { Chip("${visits.size}") })
                AdCard {
                    BarChart(Statistics.visitDistribution(visits), Statistics.VISIT_BUCKETS)
                    Text("Höchste Aufnahme ${visits.max()} · Ø %.1f · %.0f %% über 60".format(visits.average(), 100.0 * visits.count { it >= 60 } / visits.size), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
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

/** Avatar mit Namen; der gewählte Spieler bekommt einen weißen Ring. */
@Composable
private fun PlayerPick(p: Player, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.border(2.dp, if (selected) DartColors.Text else Color.Transparent, CircleShape).padding(3.dp)) { Avatar(p, 44, online = false) }
        Text(p.name, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) DartColors.Text else DartColors.TextMuted, maxLines = 1)
    }
}

/** Segmented Control wie bei iOS: gleich breite Segmente, das gewählte weiß. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(DartColors.SurfaceHigh, RoundedCornerShape(12.dp)).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == selected) DartColors.ChipSelected else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(o, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (i == selected) DartColors.OnChipSelected else DartColors.ChipText)
            }
        }
    }
}

/** Eine Zeile Chips, horizontal scrollbar. */
@Composable
private fun ChipRow(gap: Int = 6, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(gap.dp), content = content)
}

private fun minutes(min: Long): String = if (min < 60) "$min min" else "%d:%02d h".format(min / 60, min % 60)

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

/** Eine Zeile im Verlauf; Tipp öffnet den Match-Detail-Screen mit Wurfprotokoll. */
@Composable
private fun MatchRow(m: MatchRecord, selectedId: String?, df: SimpleDateFormat, onOpen: () -> Unit) {
    val me = m.players.firstOrNull { it.playerId == selectedId } ?: return
    AdCard(Modifier.padding(bottom = 8.dp), padding = 10, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.players.joinToString(" vs ") { it.playerName }, fontWeight = FontWeight.SemiBold)
                Text(m.mode.title + (if (m.mode == GameMode.X01) " ${m.settings.baseScore} · Ø %.1f".format(me.average3) else " · ${Statistics.metricLabel(m.mode)} ${Statistics.formatMetric(m.mode, Statistics.metric(m.mode, me))}") + " · " + df.format(Date(m.finishedAt)),
                    color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Badge(if (me.won) "Sieg" else if (m.winnerId == null) "Remis" else "Niederlage", DartColors.OnTint, if (me.won) DartColors.GreenDark else if (m.winnerId == null) DartColors.SurfaceHigh else DartColors.RedDark)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = DartColors.TextMuted); Text(value, fontWeight = FontWeight.SemiBold)
    }
}
