package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.ThrowRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsTest {

    private fun match(id: String, winner: String, aPoints: Int, bPoints: Int, throws: List<ThrowRecord> = emptyList(), mode: GameMode = GameMode.X01) = MatchRecord(
        id = id, mode = mode, settings = GameSettings(mode = mode), startedAt = 0, finishedAt = 1, winnerId = winner,
        players = listOf(
            PlayerMatchStats("a", "Anna", winner == "a", "0", 9, aPoints, legsWon = if (winner == "a") 1 else 0),
            PlayerMatchStats("b", "Ben", winner == "b", "0", 9, bPoints, legsWon = if (winner == "b") 1 else 0),
        ),
        throws = throws,
    )

    @Test fun headToHeadBalance() {
        val matches = listOf(
            match("1", "a", 270, 180),
            match("2", "b", 180, 270),
            match("3", "a", 300, 150),
            match("4", "a", 0, 0, mode = GameMode.CRICKET),
        )
        val h2h = Statistics.headToHead(matches, "a")
        assertEquals(1, h2h.size)
        val vsBen = h2h[0]
        assertEquals("Ben", vsBen.opponentName)
        assertEquals(4, vsBen.played); assertEquals(3, vsBen.wins); assertEquals(1, vsBen.losses)
        assertEquals(3, vsBen.legsWon); assertEquals(1, vsBen.legsLost)
        // Average nur aus den X01-Spielen: (270+180+300)/27*3 = 83.33
        assertEquals(83.33, vsBen.myAverage, 0.01)
        assertEquals(66.67, vsBen.theirAverage, 0.01)
    }

    @Test fun heatmapCountsOnlyOwnDartsAndSkipsBullOff() {
        val throws = listOf(
            ThrowRecord(0, 1, 1, 1, 20, 3, 0f, 103f),
            ThrowRecord(0, 1, 1, 1, 20, 3),
            ThrowRecord(0, 1, 1, 1, 20, 1),
            ThrowRecord(1, 1, 1, 1, 19, 3),
            ThrowRecord(0, 0, 0, 0, 25, 2), // Bull-off zählt nicht
        )
        val heat = Statistics.heatmap(listOf(match("1", "a", 123, 57, throws)), "a")
        assertEquals(3, heat.darts)
        assertEquals(2, heat.counts[Segment.triple(20)])
        assertEquals(1, heat.counts[Segment.single(20)])
        assertEquals(null, heat.counts[Segment.triple(19)])
        assertEquals(2, heat.max)
        assertEquals(listOf(0f to 103f), heat.points)
        assertEquals(2.0 / 3, heat.share(Segment.triple(20)), 1e-9)
    }
}

class MatchRecordRoundTripTest {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Ganzer Weg wie in der App: Spiel spielen → playerStats → MatchRecord → JSON → zurück → Auswertung. */
    @Test fun x01RecordSurvivesJsonAndCountsCorrectly() {
        val a = com.freedarts.scorer.model.Player(id = "a", name = "Anna")
        val b = com.freedarts.scorer.model.Player(id = "b", name = "Ben")
        val g = GameFactory.create(listOf(a, b), GameSettings(mode = GameMode.X01, baseScore = 501, legs = 1), seed = 1)
        fun visit(vararg s: Segment) { s.forEach { g.throwDart(it) }; if (s.size < 3 && !g.finished) g.next() }
        val t20 = Segment.triple(20); val s1 = Segment.single(1)
        visit(t20, t20, t20); visit(s1, s1, s1)          // A 321
        visit(t20, t20, t20); visit(s1, s1, s1)          // A 141
        visit(t20, t20, s1);  visit(s1, s1, s1)          // A 20
        visit(Segment.double(10))                        // A 0 → Game Shot
        assertEquals(true, g.finished); assertEquals(0, g.winner)

        val record = MatchRecord("m1", GameMode.X01, g.settings, g.startedAt, g.startedAt + 1000, "a",
            g.players.indices.map { g.playerStats(it) }, g.throwLog)
        val back = json.decodeFromString(MatchRecord.serializer(), json.encodeToString(MatchRecord.serializer(), record))
        assertEquals(record, back)

        // Konto-ID des Mitspielers ↔ lokaler Profilspieler (share_match / syncHistory)
        val mapped = record.withPlayerId("a", "konto-a")
        assertEquals("konto-a", mapped.winnerId)
        assertEquals(listOf("konto-a", "b"), mapped.players.map { it.playerId })
        assertEquals(record.throws, mapped.throws)
        assertEquals(record, record.withPlayerId("x", "y"))

        val me = back.players[0]
        assertEquals(10, me.dartsThrown); assertEquals(501, me.pointsScored)
        assertEquals(150.3, me.average3, 0.01)
        assertEquals(9, me.first9Darts); assertEquals(481, me.first9Points)
        assertEquals(1, me.checkouts); assertEquals(1, me.dartsAtDouble); assertEquals(20, me.highestCheckout)
        assertEquals(180, me.highestVisit); assertEquals(2, me.count180); assertEquals(3, me.count100Plus)
        assertEquals(1, me.legsWon); assertEquals(10, me.bestLegDarts); assertEquals(true, me.won)
        assertEquals(19, back.throws.size)
        val legs = back.legs()
        assertEquals(1, legs.size); assertEquals(listOf(10, 9), legs[0].darts); assertEquals(0, legs[0].winner)
        assertEquals(10, Statistics.heatmap(listOf(back), "a").darts)
        assertEquals(1, Statistics.headToHead(listOf(back), "a").single().wins)
    }

    /** Bust in einer Aufnahme, die über die 9. Dart-Grenze läuft: ihre First-9-Punkte zählen 0. */
    @Test fun first9IgnoresBustedVisit() {
        val g = GameFactory.create(listOf(com.freedarts.scorer.model.Player(id = "a", name = "Anna")), GameSettings(mode = GameMode.X01, baseScore = 101, legs = 1), seed = 1)
        val s1 = Segment.single(1); val t20 = Segment.triple(20)
        listOf(s1, s1, s1).forEach { g.throwDart(it) }   // Darts 1–3: Rest 98, First-9 = 3
        g.throwDart(t20); g.throwDart(t20)              // Darts 4–5: Bust (Aufnahme endet nach 2 Darts)
        listOf(s1, s1, s1).forEach { g.throwDart(it) }   // Darts 6–8: Rest 95, First-9 = 6
        g.throwDart(s1); g.throwDart(t20); g.throwDart(t20) // Darts 9–11: 94 → 34 → Bust; Dart 9 lag noch in den ersten 9
        val me = g.playerStats(0)
        assertEquals(9, me.first9Darts); assertEquals(6, me.first9Points); assertEquals(2, me.busts)
    }
}
