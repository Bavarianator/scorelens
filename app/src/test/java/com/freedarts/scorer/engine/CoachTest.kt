package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.ThrowRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {
    private fun rec(aim: Segment, hit: Segment) = ThrowRecord(0, 1, 1, 1, hit.number, hit.multiplier, aim = aim.name)

    @Test fun weakestDoubleFirstAndMinAttempts() {
        val d16 = Segment.double(16); val d20 = Segment.double(20); val d8 = Segment.double(8)
        val throws = List(10) { rec(d16, if (it < 2) d16 else Segment.single(16)) } +   // 20 %
            List(10) { rec(d20, if (it < 6) d20 else Segment.single(20)) } +              // 60 %
            List(3) { rec(d8, Segment.single(8)) } +                                       // zu wenige Versuche
            List(8) { rec(Segment.triple(20), Segment.triple(20)) }                        // kein Doppel
        val m = MatchRecord("m", GameMode.X01, GameSettings(), 0, 0, null, listOf(PlayerMatchStats("p", "P", true, "0", throws.size, 0)), throws)
        val weak = Coach.weakestDoubles(Coach.targets(listOf(m), "p"))
        assertEquals(listOf(d16, d20), weak.map { it.aim })
        assertEquals(0.2, weak[0].rate, 1e-9)
        assertTrue(Coach.targets(listOf(m), "nobody").isEmpty())
    }
}
