package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorTest {
    @Test fun strongPlayerWinsAndProbabilitiesSumToOne() {
        val p = Predictor.leg(intArrayOf(501, 501), 0, 3, doubleArrayOf(Bot.sigma(11), Bot.sigma(1)), GameSettings(), sims = 100)
        assertTrue("Profi gewinnt zu selten: ${p.legWin[0]}", p.legWin[0] > 0.9)
        assertEquals(1.0, p.legWin.sum(), 1e-9)
        assertTrue(p.expectedVisit in 40.0..180.0)
    }

    @Test fun checkoutChanceOnFortyRest() {
        val p = Predictor.leg(intArrayOf(40, 301), 0, 3, doubleArrayOf(15.0, 15.0), GameSettings(), sims = 200)
        assertTrue("Checkout-Chance ${p.checkoutNow}", p.checkoutNow in 0.15..0.95)
        assertTrue(p.legWin[0] > 0.8)
        val oneDart = Predictor.leg(intArrayOf(40, 301), 0, 1, doubleArrayOf(15.0, 15.0), GameSettings(), sims = 200)
        assertTrue(oneDart.checkoutNow < p.checkoutNow)
    }
}
