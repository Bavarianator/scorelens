package com.freedarts.scorer.engine

import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BotTest {
    @Test fun sigmaForAverageInterpolatesAndClamps() {
        assertEquals(Bot.sigma(5), Bot.sigmaForAverage(Player.botAverage(5).toDouble()), 1e-9)
        assertEquals(Bot.sigma(1), Bot.sigmaForAverage(5.0), 1e-9)
        assertEquals(Bot.sigma(11), Bot.sigmaForAverage(150.0), 1e-9)
        var last = Double.MAX_VALUE
        for (avg in 20..110 step 5) { val s = Bot.sigmaForAverage(avg.toDouble()); assertTrue("nicht monoton bei $avg", s <= last); last = s }
    }

    @Test fun smallerSigmaScoresMore() {
        val r = Random(7)
        fun avg(sigma: Double) = (1..2000).sumOf { Bot.throwAt(Segment.triple(20), sigma, r).score }.toDouble() / 2000
        assertTrue(avg(9.5) > avg(30.0) && avg(30.0) > avg(62.0))
    }
}
