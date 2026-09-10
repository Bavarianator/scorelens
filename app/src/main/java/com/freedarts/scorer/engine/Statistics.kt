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
}
