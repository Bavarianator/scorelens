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
