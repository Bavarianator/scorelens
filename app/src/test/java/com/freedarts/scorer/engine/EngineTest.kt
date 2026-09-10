package com.freedarts.scorer.engine

import com.freedarts.scorer.engine.games.CricketGame
import com.freedarts.scorer.engine.games.X01Game
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val a = Player(id = "a", name = "A")
    private val b = Player(id = "b", name = "B")

    @Test fun boardGeometry() {
        assertEquals(Segment.BULL, Board.segmentAt(0.0, 0.0))
        assertEquals(Segment.OUTER_BULL, Board.segmentAt(10.0, 0.0))
        assertEquals(Segment.triple(20), Board.segmentAt(0.0, 103.0))
        assertEquals(Segment.double(3), Board.segmentAt(0.0, -166.0))
        assertEquals(Segment.single(6), Board.segmentAt(130.0, 0.0))
        assertEquals(Segment.single(11), Board.segmentAt(-130.0, 0.0))
        assertEquals(Segment.MISS, Board.segmentAt(200.0, 0.0))
        for (s in Segment.ALL) { val (x, y) = Board.centerOf(s); assertEquals(s, Board.segmentAt(x, y)) }
    }

    @Test fun checkoutRoutes() {
        assertEquals("T20  T20  BULL", Checkout.describe(Checkout.bestRoute(170, 3, OutMode.DOUBLE)))
        assertEquals("D20", Checkout.describe(Checkout.bestRoute(40, 1, OutMode.DOUBLE)))
        assertEquals("D16", Checkout.describe(Checkout.bestRoute(32, 3, OutMode.DOUBLE)))
        assertNull(Checkout.bestRoute(169, 3, OutMode.DOUBLE))
        assertNull(Checkout.bestRoute(1, 3, OutMode.DOUBLE))
        assertNotNull(Checkout.bestRoute(100, 2, OutMode.DOUBLE))
        assertEquals(2, Checkout.bestRoute(100, 3, OutMode.DOUBLE)!!.size)
    }

    @Test fun x01BustAndCheckout() {
        val g = X01Game(listOf(a, b), GameSettings(mode = GameMode.X01, baseScore = 101), seed = 1)
        g.throwDart(Segment.triple(20)); g.throwDart(Segment.single(20)); g.throwDart(Segment.single(1)) // 20 Rest
        assertEquals("20", g.snapshot().players[0].score)
        assertEquals(1, g.current)
        g.throwDart(Segment.triple(20)); g.throwDart(Segment.triple(20)) // 101-120 → Bust
        assertEquals("101", g.snapshot().players[1].score)
        assertEquals("Bust", g.snapshot().banner)
        assertEquals(0, g.current)
        g.throwDart(Segment.single(20)) // 0 aber kein Double → Bust
        assertEquals("20", g.snapshot().players[0].score)
        assertEquals(1, g.current)
        g.throwDart(Segment.single(1)); g.throwDart(Segment.single(1)); g.throwDart(Segment.single(1))
        g.throwDart(Segment.double(10))
        assertTrue(g.finished); assertEquals(0, g.winner)
        val st = g.playerStats(0)
        assertEquals(1, st.checkouts); assertEquals(20, st.highestCheckout); assertEquals(5, st.dartsThrown)
    }

    @Test fun undoRestoresState() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 501), seed = 1)
        g.throwDart(Segment.triple(20)); g.throwDart(Segment.triple(20)); g.throwDart(Segment.triple(20))
        g.throwDart(Segment.single(5))
        assertEquals(1, g.current)
        g.undo(); g.undo()
        assertEquals(0, g.current)
        assertEquals(2, g.visit.size)
        assertEquals("381", g.snapshot().players[0].score)
        g.undo(); g.undo(); g.undo()
        assertFalse(g.canUndo)
        assertEquals("501", g.snapshot().players[0].score)
    }

    @Test fun legsAlternateStarter() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 40, legs = 2), seed = 1)
        g.throwDart(Segment.double(20))
        assertFalse(g.finished)
        assertEquals("Leg gewonnen", g.snapshot().banner)
        assertEquals(1, g.current) // B beginnt Leg 2
        assertEquals(1, g.snapshot().players[0].legs)
        g.throwDart(Segment.single(1)); g.throwDart(Segment.single(1)); g.throwDart(Segment.single(1))
        g.throwDart(Segment.double(20))
        assertTrue(g.finished); assertEquals(0, g.winner)
    }

    @Test fun cricketScoring() {
        val g = CricketGame(listOf(a, b), GameSettings(mode = GameMode.CRICKET), seed = 1)
        g.throwDart(Segment.triple(20)); g.throwDart(Segment.triple(20)); g.throwDart(Segment.single(20))
        val s = g.snapshot()
        assertEquals(3, s.players[0].marks!![20]); assertEquals("80", s.players[0].score)
        assertEquals(1, g.current)
    }

    @Test fun botHitsSomething() {
        val counts = (1..200).map { Bot.throwAt(Segment.triple(20), 11) }.count { it == Segment.triple(20) }
        assertTrue("Profi-Bot trifft T20 zu selten: $counts", counts > 40)
        val weak = (1..200).map { Bot.throwAt(Segment.triple(20), 1) }.count { it == Segment.triple(20) }
        assertTrue(weak < counts)
    }
}
