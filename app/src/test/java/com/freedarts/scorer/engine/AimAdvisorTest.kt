package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.ThrowRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AimAdvisorTest {
    private fun match(throws: List<ThrowRecord>) = MatchRecord("m", GameMode.X01, GameSettings(), 0, 0, null,
        listOf(PlayerMatchStats("p", "P", true, "0", throws.size, 0)), throws)

    private fun throws(sigma: Double, n: Int, aim: Segment = Segment.triple(20)): List<ThrowRecord> {
        val r = Random(3); val (cx, cy) = Board.centerOf(aim)
        return List(n) { val (gx, gy) = Bot.gaussianPair(r); ThrowRecord(0, 1, 1, 1, 20, 3, (cx + gx * sigma).toFloat(), (cy + gy * sigma).toFloat(), aim = aim.name) }
    }

    @Test fun estimatesSigmaFromRadialMedian() {
        val sc = AimAdvisor.scatter(listOf(match(throws(20.0, 300))), "p")!!
        assertEquals(300, sc.darts)
        assertTrue("sigma war ${sc.sigmaMm}", sc.sigmaMm in 16.0..24.0)
        assertNull(AimAdvisor.scatter(listOf(match(throws(20.0, 10))), "p"))
        assertNull(AimAdvisor.scatter(listOf(match(throws(20.0, 300))), "someone-else"))
    }

    @Test fun preciseThrowerAimsT20WideThrowerDoesNot() {
        assertEquals(Segment.triple(20), AimAdvisor.advise(5.0).best)
        val wide = AimAdvisor.advise(45.0)
        assertNotEquals(Segment.triple(20), wide.best)
        assertTrue(wide.expected[wide.best]!! >= wide.expected[Segment.triple(20)]!!)
    }
}
