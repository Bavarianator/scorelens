package com.freedarts.scorer.engine

import com.freedarts.scorer.engine.games.CricketGame
import com.freedarts.scorer.engine.games.X01Game
import com.freedarts.scorer.model.BullOff
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.MatchMode
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.WinMode
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

    @Test fun correctDartReplaysGame() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 501), seed = 1)
        g.throwDart(Segment.triple(20)); g.throwDart(Segment.single(5))
        assertEquals(listOf(Segment.triple(20), Segment.single(5)), g.correctableDarts())
        assertTrue(g.correctDart(1, Segment.triple(5)))
        assertEquals("426", g.snapshot().players[0].score)
        g.throwDart(Segment.single(1)) // Aufnahme abgeschlossen → Spieler B
        assertEquals(1, g.current)
        assertEquals(3, g.correctableDarts().size)
        assertTrue(g.correctDart(0, Segment.single(20))) // T20 → S20
        assertEquals("465", g.snapshot().players[0].score)
        assertEquals(1, g.current)
        assertFalse(g.correctDart(3, Segment.MISS))
    }

    @Test fun botHitsSomething() {
        val counts = (1..200).map { Bot.throwAt(Segment.triple(20), 11) }.count { it == Segment.triple(20) }
        assertTrue("Profi-Bot trifft T20 zu selten: $counts", counts > 40)
        val weak = (1..200).map { Bot.throwAt(Segment.triple(20), 1) }.count { it == Segment.triple(20) }
        assertTrue(weak < counts)
    }
}

/** Ablauf-Logik wie bei Autodarts: Aufnahme-Sperre, Bull-off, Best of, Wurfprotokoll. */
class MatchFlowTest {
    private val a = Player(id = "a", name = "A")
    private val b = Player(id = "b", name = "B")

    @Test fun heldVisitWaitsForTakeout() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 501), seed = 1)
        repeat(3) { g.throwDart(Segment.single(20), hold = true) }
        assertTrue(g.visitComplete)
        assertTrue(g.snapshot().visitLocked)
        assertEquals(0, g.current)
        g.throwDart(Segment.triple(20), hold = true) // vierter Dart wird ignoriert
        assertEquals(3, g.visit.size)
        assertEquals("441", g.snapshot().players[0].score)
        g.next() // Takeout
        assertFalse(g.visitComplete)
        assertEquals(1, g.current)
        assertEquals("60", g.snapshot().players[0].history.last().label)
    }

    @Test fun heldBustStaysLockedUntilNext() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 40), seed = 1)
        g.throwDart(Segment.triple(20), hold = true) // Bust
        assertEquals("Bust", g.snapshot().banner)
        assertTrue(g.visitComplete)
        assertEquals(0, g.current)
        g.throwDart(Segment.double(20), hold = true) // ignoriert
        assertEquals("40", g.snapshot().players[0].score)
        g.next()
        assertEquals(1, g.current)
        assertEquals(1, g.playerStats(0).busts)
        assertTrue(g.throwLog.first().bust)
    }

    @Test fun manualInputAdvancesImmediatelyAndUndoRemovesDart() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 501), seed = 1)
        repeat(3) { g.throwDart(Segment.single(20)) }
        assertEquals(1, g.current)
        assertFalse(g.visitComplete)
        g.undo()
        assertEquals(0, g.current)
        assertEquals(2, g.visit.size)
    }

    @Test fun bestOfLegs() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 40, legs = 3, winMode = WinMode.BEST_OF), seed = 1)
        g.throwDart(Segment.double(20)) // A 1:0
        assertFalse(g.finished)
        g.throwDart(Segment.single(1)); g.throwDart(Segment.single(1)); g.throwDart(Segment.single(1)) // B
        g.throwDart(Segment.double(20)) // A 2:0 → Best of 3 entschieden
        assertTrue(g.finished); assertEquals(0, g.winner)
        assertEquals(2, g.playerStats(0).legsWon)
    }

    @Test fun legsWonCountsAcrossSets() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 40, matchMode = MatchMode.SETS, legs = 1, sets = 2), seed = 1)
        g.throwDart(Segment.double(20)) // A gewinnt Leg = Set 1
        assertEquals("Set gewonnen", g.snapshot().banner)
        assertEquals(1, g.current)
        g.throwDart(Segment.single(1)); g.throwDart(Segment.single(1)); g.throwDart(Segment.single(1))
        g.throwDart(Segment.double(20)) // A gewinnt Set 2 → Match
        assertTrue(g.finished)
        assertEquals(2, g.playerStats(0).legsWon)
        assertEquals(2, g.playerStats(0).setsWon)
        assertEquals(1, g.playerStats(0).bestLegDarts)
    }

    @Test fun bullOffDecidesStarter() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 501, bullOff = BullOff.NORMAL), seed = 1)
        assertTrue(g.bullOffActive)
        assertTrue(g.snapshot().bullOff)
        assertEquals(Segment.BULL, g.botAim())
        g.throwDart(Segment.single(20)) // A
        assertEquals(1, g.current)
        g.throwDart(Segment.BULL) // B näher
        assertFalse(g.bullOffActive)
        assertEquals(1, g.current)
        assertEquals("B beginnt", g.snapshot().banner)
        assertEquals(0, g.playerStats(1).dartsThrown) // Bull-off-Darts zählen nicht
        repeat(3) { g.throwDart(Segment.single(20)) }
        assertEquals(0, g.current)
        repeat(3) { g.throwDart(Segment.single(20)) }
        assertEquals(1, g.current); assertEquals(2, g.round) // Runde zählt ab Startspieler B
        g.undo()
        assertEquals(0, g.current)
        assertEquals(2, g.visit.size)
    }

    @Test fun bullOffTieRethrows() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 501, bullOff = BullOff.OFFICIAL), seed = 1)
        g.throwDart(Segment.OUTER_BULL); g.throwDart(Segment.OUTER_BULL)
        assertTrue(g.bullOffActive)
        assertEquals(1, g.current) // offiziell: umgekehrte Reihenfolge
        g.throwDart(Segment.BULL); g.throwDart(Segment.single(5))
        assertFalse(g.bullOffActive)
        assertEquals(1, g.current)
    }

    @Test fun bullOffUsesCoordinates() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 501, bullOff = BullOff.NORMAL), seed = 1)
        g.throwDart(Segment.single(20), 0f, 30f)
        g.throwDart(Segment.single(3), 0f, -20f)
        assertEquals(1, g.starter)
    }

    @Test fun throwLogCarriesLegAndPosition() {
        val g = X01Game(listOf(a, b), GameSettings(baseScore = 40, legs = 2), seed = 1)
        g.throwDart(Segment.double(20), 10f, 160f)
        g.throwDart(Segment.single(1), 5f, 5f)
        val log = g.throwLog
        assertEquals(2, log.size)
        assertEquals(1, log[0].leg); assertEquals(0, log[0].player); assertEquals(10f, log[0].x)
        assertEquals(2, log[1].leg); assertEquals(1, log[1].player)
        assertEquals(1, log[1].set)
        assertTrue(g.correctDart(0, Segment.single(5))) // laufende Aufnahme korrigiert: Position unbekannt
        assertNull(g.throwLog.last().x)
        assertEquals(5, g.throwLog.last().number)
    }

    @Test fun onFinishAndVisitTotals() {
        // "Wurf aufs Double" nur, wenn ein einzelner gültiger letzter Dart den Rest exakt trifft
        assertTrue(Checkout.isOnFinish(40, OutMode.DOUBLE)); assertTrue(Checkout.isOnFinish(50, OutMode.DOUBLE))
        assertFalse(Checkout.isOnFinish(41, OutMode.DOUBLE)); assertFalse(Checkout.isOnFinish(25, OutMode.MASTER))
        assertTrue(Checkout.isOnFinish(57, OutMode.MASTER)); assertFalse(Checkout.isOnFinish(43, OutMode.STRAIGHT))
        // Gesamtscore-Eingabe: jede werfbare Summe wird exakt zerlegt, unmögliche nicht
        for (t in 1..180) {
            val route = Checkout.bestRoute(t, 3, OutMode.STRAIGHT)
            if (route != null) assertEquals(t, route.sumOf { it.score })
        }
        assertEquals(170, Checkout.bestRoute(170, 3, OutMode.STRAIGHT)!!.sumOf { it.score })
        assertEquals(null, Checkout.bestRoute(179, 3, OutMode.STRAIGHT))
    }
}
