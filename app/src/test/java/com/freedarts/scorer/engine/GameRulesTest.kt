package com.freedarts.scorer.engine

import com.freedarts.scorer.engine.games.AroundTheClockGame
import com.freedarts.scorer.engine.games.BermudaGame
import com.freedarts.scorer.engine.games.Bobs27Game
import com.freedarts.scorer.engine.games.CricketGame
import com.freedarts.scorer.engine.games.GotchaGame
import com.freedarts.scorer.engine.games.KillerGame
import com.freedarts.scorer.engine.games.OneTwentyOneGame
import com.freedarts.scorer.engine.games.RandomCheckoutGame
import com.freedarts.scorer.engine.games.RoundTheWorldGame
import com.freedarts.scorer.engine.games.SegmentTrainingGame
import com.freedarts.scorer.model.CricketBoard
import com.freedarts.scorer.model.CricketVariant
import com.freedarts.scorer.model.FailMode
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.HitMode
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.TargetOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regeln der Spielmodi wie in der Autodarts-Dokumentation. */
class GameRulesTest {
    private val a = Player(id = "a", name = "A")
    private val b = Player(id = "b", name = "B")

    /** Eine Aufnahme: Darts werfen, danach (falls noch offen) „Weiter“. */
    private fun turn(g: DartGame, vararg darts: Segment) {
        darts.forEach { g.throwDart(it) }
        endVisit(g)
    }

    private fun endVisit(g: DartGame) { if (g.visit.isNotEmpty()) g.next() }

    private fun score(g: DartGame, p: Int) = g.snapshot().players[p].score

    @Test fun hiddenCricketRevealsNumbersAndNoScoreWinsByClosing() {
        val g = CricketGame(listOf(a, b), GameSettings(mode = GameMode.CRICKET, cricketBoard = CricketBoard.HIDDEN, cricketVariant = CricketVariant.NO_SCORE), seed = 5)
        val targets = g.targets
        assertEquals(7, targets.size)
        assertEquals(targets.toSet(), g.snapshot().cricketHidden)
        for (t in targets) {
            if (t == 25) turn(g, Segment.BULL, Segment.OUTER_BULL) else turn(g, Segment.triple(t))
            assertFalse(t in g.snapshot().cricketHidden!!)
            if (!g.finished) turn(g, Segment.MISS)
        }
        assertTrue(g.finished); assertEquals(0, g.winner)
        assertEquals("7 / 7", score(g, 0)) // No Score: keine Punkte, nur geschlossene Zahlen
    }

    @Test fun aroundTheClockDownwardsWithTwoHitsPerNumber() {
        val g = AroundTheClockGame(listOf(a), GameSettings(mode = GameMode.AROUND_THE_CLOCK, targetOrder = TargetOrder.DOWN, hitsRequired = 2, includeBull = false), seed = 1)
        assertEquals("20", score(g, 0))
        g.throwDart(Segment.single(20))
        assertEquals("20", score(g, 0))
        g.throwDart(Segment.triple(20)) // Triple zählt als ein Treffer
        assertEquals("19", score(g, 0))
    }

    @Test fun roundTheWorldScoresOneTwoThree() {
        val g = RoundTheWorldGame(listOf(a), GameSettings(mode = GameMode.ROUND_THE_WORLD, rounds = 20, includeBull = false), seed = 1)
        turn(g, Segment.triple(1), Segment.double(1), Segment.single(1))
        assertEquals("6", score(g, 0))
    }

    @Test fun randomCheckoutWithTwoRoundsPerLegAndStraightOut() {
        val g = RandomCheckoutGame(listOf(a), GameSettings(mode = GameMode.RANDOM_CHECKOUT, rounds = 1, checkoutRounds = 2, outMode = OutMode.STRAIGHT, checkoutMin = 60, checkoutMax = 60), seed = 1)
        turn(g, Segment.single(20), Segment.single(20), Segment.single(10)) // Rest 10
        assertFalse(g.finished)
        turn(g, Segment.single(10)) // zweite Aufnahme: Straight Out
        assertEquals("1", score(g, 0))
        assertTrue(g.finished); assertEquals(0, g.winner)
    }

    @Test fun segmentTrainingEndsAfterHits() {
        val g = SegmentTrainingGame(listOf(a), GameSettings(mode = GameMode.SEGMENT_TRAINING, trainingSegment = 20, hitMode = HitMode.TRIPLE, endAfterHits = true, hitCount = 2), seed = 1)
        g.throwDart(Segment.triple(20)); assertEquals("1", score(g, 0))
        g.throwDart(Segment.single(20)); assertEquals("1", score(g, 0))
        g.throwDart(Segment.triple(20))
        assertTrue(g.finished); assertEquals(0, g.winner)
    }

    @Test fun bobs27EliminatesAtZeroUnlessNegativeAllowed() {
        val g = Bobs27Game(listOf(a), GameSettings(mode = GameMode.BOBS_27), seed = 1)
        repeat(5) { turn(g, Segment.MISS, Segment.MISS, Segment.MISS) } // 27 − 2 − 4 − 6 − 8 − 10 = −3
        assertTrue(g.finished)
        val n = Bobs27Game(listOf(a), GameSettings(mode = GameMode.BOBS_27, allowNegative = true), seed = 1)
        repeat(5) { turn(n, Segment.MISS, Segment.MISS, Segment.MISS) }
        assertFalse(n.finished); assertEquals("-3", score(n, 0))
    }

    @Test fun oneTwentyOneStepAndHardReset() {
        val g = OneTwentyOneGame(listOf(a), GameSettings(mode = GameMode.ONE_TWENTY_ONE, attempts = 3, dartsPerAttempt = 6, failMode = FailMode.HARD_RESET, step = 3), seed = 1)
        turn(g, Segment.triple(20), Segment.triple(11), Segment.double(14)) // 121 ausgecheckt
        assertEquals("124", score(g, 0))
        turn(g, Segment.MISS, Segment.MISS, Segment.MISS); turn(g, Segment.MISS, Segment.MISS, Segment.MISS) // 6 Darts verfehlt
        assertEquals("121", score(g, 0))
        assertFalse(g.finished)
    }

    @Test fun oneTwentyOneSafehouseKeepsBankedTarget() {
        val g = OneTwentyOneGame(listOf(a), GameSettings(mode = GameMode.ONE_TWENTY_ONE, attempts = 5, dartsPerAttempt = 6, failMode = FailMode.SAFEHOUSE, safehouseEvery = 1), seed = 1)
        turn(g, Segment.triple(20), Segment.triple(11), Segment.double(14)) // 121 → 122, gesichert
        assertEquals("122", score(g, 0))
        turn(g, Segment.MISS, Segment.MISS, Segment.MISS); turn(g, Segment.MISS, Segment.MISS, Segment.MISS)
        assertEquals("122", score(g, 0))
    }

    @Test fun gotchaNeedsDoubleOut() {
        val g = GotchaGame(listOf(a, b), GameSettings(mode = GameMode.GOTCHA, gotchaTarget = 140, outMode = OutMode.DOUBLE), seed = 1)
        turn(g, Segment.triple(20), Segment.triple(20), Segment.single(10)) // 130
        turn(g, Segment.MISS)
        g.throwDart(Segment.single(10)) // 140 ohne Double → Bust
        assertEquals("130", score(g, 0)); assertEquals("Bust", g.snapshot().banner)
        endVisit(g)
        turn(g, Segment.MISS)
        g.throwDart(Segment.double(5))
        assertTrue(g.finished); assertEquals(0, g.winner)
    }

    @Test fun killerTallyRules() {
        val g = KillerGame(listOf(a, b), GameSettings(mode = GameMode.KILLER, killerLives = 3), seed = 7)
        val nA = score(g, 0).toInt(); val nB = score(g, 1).toInt()
        g.throwDart(Segment.triple(nA)) // Konto 3 → Killer
        assertTrue(g.snapshot().players[0].isKiller)
        g.throwDart(Segment.single(nA)) // eigene Zahl als Killer: Leben weg, kein Killer mehr
        assertEquals(2, g.snapshot().players[0].lives); assertFalse(g.snapshot().players[0].isKiller)
        g.throwDart(Segment.single(nA)) // wieder Killer
        assertTrue(g.snapshot().players[0].isKiller)
        endVisit(g)
        turn(g, Segment.MISS) // B
        g.throwDart(Segment.single(nB)) // B hat 0 Leben und wird getroffen → raus
        assertTrue(g.finished); assertEquals(0, g.winner)
    }

    @Test fun bermudaLastRoundNeedsBullseye() {
        val g = BermudaGame(listOf(a), GameSettings(mode = GameMode.BERMUDA), seed = 1)
        repeat(11) { turn(g, Segment.MISS) }
        turn(g, Segment.OUTER_BULL, Segment.BULL, Segment.MISS)
        assertEquals("50", score(g, 0))
        assertTrue(g.finished)
    }

    @Test fun legacyTacticsSettingStillMapsToTacticsBoard() {
        val gs = GameSettings(mode = GameMode.CRICKET, cricketVariant = CricketVariant.TACTICS)
        assertEquals(CricketBoard.TACTICS, gs.effectiveCricketBoard)
        assertEquals(CricketVariant.STANDARD, gs.cricketScoring)
        assertEquals(12, CricketGame(listOf(a, b), gs, seed = 1).targets.size)
        assertNull(CricketGame(listOf(a, b), gs, seed = 1).snapshot().cricketHidden)
    }
}
