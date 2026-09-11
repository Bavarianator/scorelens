package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment

/** Auswertungen über mehrere Spiele: Head-to-Head und Trefferbild (wie die Autodarts-Statistik). */
object Statistics {

    data class HeadToHead(
        val opponentId: String,
        val opponentName: String,
        val played: Int,
        val wins: Int,
        val losses: Int,
        val legsWon: Int,
        val legsLost: Int,
        /** 3-Dart-Averages über die gemeinsamen X01-Spiele (0 = keine). */
        val myAverage: Double,
        val theirAverage: Double,
    )

    /** Bilanz gegen jeden Gegner aus Zwei-Spieler-Matches, häufigste Gegner zuerst. */
    fun headToHead(matches: List<MatchRecord>, playerId: String): List<HeadToHead> =
        matches.filter { m -> m.players.size == 2 && m.players.any { it.playerId == playerId } }
            .groupBy { m -> m.players.first { it.playerId != playerId }.playerId }
            .map { (oppId, ms) ->
                val me = ms.map { m -> m.players.first { it.playerId == playerId } }
                val them = ms.map { m -> m.players.first { it.playerId == oppId } }
                val x01 = ms.indices.filter { ms[it].mode == GameMode.X01 }
                HeadToHead(
                    opponentId = oppId, opponentName = them.first().playerName, played = ms.size,
                    wins = me.count { it.won }, losses = them.count { it.won },
                    legsWon = me.sumOf { it.legsWon }, legsLost = them.sumOf { it.legsWon },
                    myAverage = average(x01.map { me[it] }), theirAverage = average(x01.map { them[it] }),
                )
            }
            .sortedWith(compareByDescending<HeadToHead> { it.played }.thenBy { it.opponentName })

    private fun average(l: List<PlayerMatchStats>): Double {
        val d = l.sumOf { it.dartsThrown }
        return if (d == 0) 0.0 else l.sumOf { it.pointsScored }.toDouble() / d * 3
    }

    /** Trefferbild: Treffer je Segment und, wo bekannt (Lens/Board Manager), die Auftreffpunkte in Board-mm. */
    data class Heatmap(val counts: Map<Segment, Int>, val points: List<Pair<Float, Float>>, val darts: Int) {
        val max: Int get() = counts.values.maxOrNull() ?: 0
        fun share(segment: Segment): Double = if (darts == 0) 0.0 else (counts[segment] ?: 0).toDouble() / darts
    }

    fun heatmap(matches: List<MatchRecord>, playerId: String): Heatmap {
        val counts = HashMap<Segment, Int>()
        val points = ArrayList<Pair<Float, Float>>()
        var darts = 0
        for (m in matches) {
            val idx = m.players.indexOfFirst { it.playerId == playerId }
            if (idx < 0) continue
            for (t in m.throws) {
                if (t.player != idx || t.leg == 0) continue
                darts++
                val seg = t.segment
                if (!seg.isMiss) counts[seg] = (counts[seg] ?: 0) + 1
                if (t.x != null && t.y != null) points.add(t.x to t.y)
            }
        }
        return Heatmap(counts, points, darts)
    }

    // ---------- Kennzahl je Modus (Trend, Vergleich, Übersicht aller Modi) ----------

    /** Kennzahl eines Spiels je Modus; Beschriftung in [metricLabel]. */
    fun metric(mode: GameMode, s: PlayerMatchStats): Double = when (mode) {
        GameMode.X01, GameMode.GOTCHA -> s.average3
        GameMode.CRICKET -> s.mpr
        GameMode.AROUND_THE_CLOCK, GameMode.SEGMENT_TRAINING -> s.hitRate
        GameMode.COUNT_UP, GameMode.SHANGHAI, GameMode.BERMUDA, GameMode.BOBS_27 -> s.finalScore.toDoubleOrNull() ?: 0.0
        GameMode.ROUND_THE_WORLD, GameMode.RANDOM_CHECKOUT, GameMode.KILLER -> s.pointsScored.toDouble()
        GameMode.ONE_TWENTY_ONE -> s.finalScore.toDoubleOrNull() ?: 121.0
    }

    fun metricLabel(mode: GameMode): String = when (mode) {
        GameMode.X01, GameMode.GOTCHA -> "3-Dart Average"
        GameMode.CRICKET -> "MPR"
        GameMode.AROUND_THE_CLOCK, GameMode.SEGMENT_TRAINING -> "Trefferquote %"
        GameMode.COUNT_UP, GameMode.SHANGHAI, GameMode.BERMUDA, GameMode.BOBS_27 -> "Punkte"
        GameMode.ROUND_THE_WORLD -> "Treffer"
        GameMode.RANDOM_CHECKOUT -> "Legs gecheckt"
        GameMode.ONE_TWENTY_ONE -> "Erreichtes Ziel"
        GameMode.KILLER -> "Treffer auf Gegner"
    }

    /** Kennzahl über mehrere Spiele: Averages und Quoten dartgewichtet, sonst Mittelwert je Spiel. */
    fun metricTotal(mode: GameMode, l: List<PlayerMatchStats>): Double {
        if (l.isEmpty()) return 0.0
        val d = l.sumOf { it.dartsThrown }
        return when (mode) {
            GameMode.X01, GameMode.GOTCHA -> if (d == 0) 0.0 else l.sumOf { it.pointsScored }.toDouble() / d * 3
            GameMode.CRICKET -> if (d == 0) 0.0 else l.sumOf { it.marks }.toDouble() / d * 3
            GameMode.AROUND_THE_CLOCK, GameMode.SEGMENT_TRAINING -> if (d == 0) 0.0 else 100.0 * l.sumOf { it.hits } / d
            else -> l.map { metric(mode, it) }.average()
        }
    }

    /** Format der Kennzahl: Quoten und Averages mit Nachkommastellen, Zähler ganzzahlig. */
    fun formatMetric(mode: GameMode, v: Double): String = when (mode) {
        GameMode.X01, GameMode.GOTCHA, GameMode.CRICKET -> "%.2f".format(v)
        GameMode.AROUND_THE_CLOCK, GameMode.SEGMENT_TRAINING -> "%.1f %%".format(v)
        else -> "%.0f".format(v)
    }

    // ---------- Auswertungen aus dem Wurfprotokoll ----------

    /** Aufnahmen (bis zu 3 Darts) eines Spielers in Spielreihenfolge; eine Bust-Aufnahme zählt 0. */
    fun visits(matches: List<MatchRecord>, playerId: String): List<Int> {
        val out = ArrayList<Int>()
        for (m in matches) {
            val idx = m.players.indexOfFirst { it.playerId == playerId }
            if (idx < 0) continue
            m.throws.filter { it.player == idx && it.leg > 0 }.groupBy { Triple(it.set, it.leg, it.round) }
                .values.forEach { v -> out.add(if (v.any { it.bust }) 0 else v.sumOf { it.score }) }
        }
        return out
    }

    val VISIT_BUCKETS = listOf("0–39", "40–59", "60–99", "100–139", "140–179", "180")

    /** Verteilung der Aufnahmen auf [VISIT_BUCKETS]. */
    fun visitDistribution(visits: List<Int>): List<Int> {
        val c = IntArray(VISIT_BUCKETS.size)
        for (v in visits) c[when { v < 40 -> 0; v < 60 -> 1; v < 100 -> 2; v < 140 -> 3; v < 180 -> 4; else -> 5 }]++
        return c.toList()
    }

    /** Checkout-Darts (letzter Dart eines gewonnenen Legs) je Segment, häufigste zuerst. */
    fun checkoutSegments(matches: List<MatchRecord>, playerId: String): List<Pair<Segment, Int>> {
        val counts = HashMap<Segment, Int>()
        for (m in matches) {
            val idx = m.players.indexOfFirst { it.playerId == playerId }
            if (idx < 0) continue
            m.throws.filter { it.leg > 0 }.groupBy { it.set to it.leg }.values.forEach { leg ->
                val last = leg.last()
                if (last.player == idx && !last.bust && (last.segment.isDouble || m.settings.outMode != com.freedarts.scorer.model.OutMode.DOUBLE))
                    counts[last.segment] = (counts[last.segment] ?: 0) + 1
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** Darts je Zahl (nur die angegebenen Zahlen, z. B. Cricket-Ziele), in der Reihenfolge von [numbers]. */
    fun dartsPerNumber(matches: List<MatchRecord>, playerId: String, numbers: List<Int>): List<Int> {
        val c = HashMap<Int, Int>()
        for (m in matches) {
            val idx = m.players.indexOfFirst { it.playerId == playerId }
            if (idx < 0) continue
            for (t in m.throws) if (t.player == idx && t.leg > 0 && t.number in numbers) c[t.number] = (c[t.number] ?: 0) + 1
        }
        return numbers.map { c[it] ?: 0 }
    }

    // ---------- Serien, Aktivität, Spielzeit ----------

    /** [current] > 0: Siege in Folge, < 0: Niederlagen in Folge (chronologisch letzte Spiele). */
    data class Streaks(val current: Int, val longestWin: Int)

    fun streaks(matches: List<MatchRecord>, playerId: String): Streaks {
        var cur = 0; var best = 0
        for (m in matches.sortedBy { it.finishedAt }) {
            val won = m.players.firstOrNull { it.playerId == playerId }?.won ?: continue
            cur = if (won) (if (cur > 0) cur + 1 else 1) else (if (cur < 0) cur - 1 else -1)
            if (cur > best) best = cur
        }
        return Streaks(cur, best)
    }

    /** Spiele je Kalendertag für die letzten [days] Tage (Index 0 = ältester Tag, letzter = heute). */
    fun activity(matches: List<MatchRecord>, days: Int, now: Long = System.currentTimeMillis()): List<Int> {
        val dayMs = 86_400_000L
        val zone = java.util.TimeZone.getDefault()
        fun dayIndex(t: Long): Long = Math.floorDiv(t + zone.getOffset(t), dayMs)
        val today = dayIndex(now)
        val c = IntArray(days)
        for (m in matches) {
            val i = (m.finishedAt.let { dayIndex(it) } - today + days - 1).toInt()
            if (i in 0 until days) c[i]++
        }
        return c.toList()
    }

    /** Spielzeit in Minuten; unplausible Dauern (Uhr verstellt, App im Hintergrund) werden je Spiel auf 3 h gekappt. */
    fun playTimeMinutes(matches: List<MatchRecord>): Long =
        matches.sumOf { it.durationMillis.coerceIn(0L, 3 * 3_600_000L) } / 60_000L
}
