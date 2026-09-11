package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.ThrowRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/** Auswertungen der Statistik-Seite (Aufnahmen, Checkouts, Serien, Aktivität, Kennzahl je Modus). */
class StatsAnalyticsTest {
    private fun stats(id: String, won: Boolean, darts: Int = 30, points: Int = 500, score: String = "0") =
        PlayerMatchStats(id, id, won, score, darts, points)

    private fun match(finishedAt: Long, won: Boolean, throws: List<ThrowRecord> = emptyList(), mode: GameMode = GameMode.X01, score: String = "0") =
        MatchRecord("m$finishedAt", mode, GameSettings(mode = mode), finishedAt - 60_000, finishedAt, if (won) "me" else "op",
            listOf(stats("me", won, score = score), stats("op", !won)), throws)

    private fun t(p: Int, round: Int, n: Int, m: Int, bust: Boolean = false, leg: Int = 1) = ThrowRecord(p, 1, leg, round, n, m, bust = bust)

    @Test fun visitsAndDistribution() {
        val m = match(1_000, true, listOf(
            t(0, 1, 20, 3), t(0, 1, 20, 3), t(0, 1, 20, 3),   // 180
            t(1, 1, 20, 1), t(1, 1, 5, 1), t(1, 1, 1, 1),     // Gegner
            t(0, 2, 20, 3), t(0, 2, 19, 1), t(0, 2, 20, 1),   // 99
            t(0, 3, 20, 1), t(0, 3, 20, 1, bust = true),       // Bust → 0
            t(0, 4, 20, 3), t(0, 4, 20, 3), t(0, 4, 20, 2),   // 160 = Checkout D20
        ))
        val v = Statistics.visits(listOf(m), "me")
        assertEquals(listOf(180, 99, 0, 160), v)
        assertEquals(listOf(1, 0, 1, 0, 1, 1), Statistics.visitDistribution(v))
        assertEquals(listOf(Segment.double(20) to 1), Statistics.checkoutSegments(listOf(m), "me"))
        assertEquals(listOf(0, 10, 1), Statistics.dartsPerNumber(listOf(m), "me", listOf(1, 20, 19)))
    }

    @Test fun streaksAndActivity() {
        val day = 86_400_000L
        val now = 20L * day + 12 * 3_600_000L
        val ms = listOf(match(now - 3 * day, false), match(now - 2 * day, true), match(now - day, true), match(now - 3_600_000L, true))
        assertEquals(Statistics.Streaks(current = 3, longestWin = 3), Statistics.streaks(ms, "me"))
        assertEquals(Statistics.Streaks(current = -3, longestWin = 1), Statistics.streaks(ms, "op"))
        val act = Statistics.activity(ms, 7, now)
        assertEquals(7, act.size)
        assertEquals(4, act.sum())
        assertEquals(1, act.last())
        assertEquals(4, Statistics.playTimeMinutes(ms))
    }

    @Test fun metricPerMode() {
        val s = stats("me", true, darts = 30, points = 600, score = "140")
        assertEquals(60.0, Statistics.metric(GameMode.X01, s), 1e-9)
        assertEquals(140.0, Statistics.metric(GameMode.COUNT_UP, s), 1e-9)
        assertEquals(600.0, Statistics.metric(GameMode.KILLER, s), 1e-9)
        assertEquals(121.0, Statistics.metric(GameMode.ONE_TWENTY_ONE, s.copy(finalScore = "x")), 1e-9)
        // dartgewichtet: 600/30 und 0/60 → 600/90*3 = 20
        assertEquals(20.0, Statistics.metricTotal(GameMode.X01, listOf(s, s.copy(dartsThrown = 60, pointsScored = 0))), 1e-9)
        assertEquals(0.0, Statistics.metricTotal(GameMode.BERMUDA, emptyList()), 1e-9)
        GameMode.entries.forEach { Statistics.metricLabel(it); Statistics.formatMetric(it, 1.0) }
    }
}
