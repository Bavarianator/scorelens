package com.freedarts.scorer.lens

import com.freedarts.scorer.engine.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class BoardRectifierTest {
    private val fw = 360; private val fh = 480; private val rotW = 960; private val rotH = 1280

    /** Schräge Kamera (45°) auf das Board, Analyse-Koordinaten. */
    private fun sideView(): Homography {
        val t = Math.toRadians(45.0)
        val bp = ArrayList<Pair<Double, Double>>(); val ip = ArrayList<Pair<Double, Double>>()
        for (deg in 0 until 360 step 30) for (r in doubleArrayOf(60.0, 170.0)) {
            val a = Math.toRadians(deg.toDouble()); val bx = r * sin(a); val by = r * cos(a)
            val zc = bx * sin(t) + 1000.0
            bp.add(bx to by); ip.add(180 + 760 * bx * cos(t) / zc to 240 - 760 * by / zc)
        }
        return Homography.from(bp, ip)!!
    }

    @Test fun boardBecomesCenteredCircleWithTwentyOnTop() {
        val b2i = sideView()
        val plan = BoardRectifier.plan(b2i, fw, fh, rotW, rotH)
        val fromUpright = plan.toUpright.inverse()!!
        fun rectOf(bx: Double, by: Double): Pair<Double, Double> {
            val (ax, ay) = b2i.map(bx, by)
            return fromUpright.map(ax * rotW / fw, ay * rotH / fh)
        }
        val c = plan.size / 2.0
        val (cx, cy) = rectOf(0.0, 0.0)
        assertEquals(c, cx, 1e-6); assertEquals(c, cy, 1e-6)
        // Doppelring-Außenkante liegt auf einem Kreis mit Radius size/(2·MARGIN)
        for (deg in 0 until 360 step 20) {
            val a = Math.toRadians(deg.toDouble())
            val (x, y) = rectOf(Board.DOUBLE_OUTER * sin(a), Board.DOUBLE_OUTER * cos(a))
            assertEquals(plan.boardRadiusPx, hypot(x - c, y - c), 1e-6)
        }
        // 20 oben (Board-y positiv → kleineres Bild-y), 6 rechts, kein Spiegel
        val (_, yTop) = rectOf(0.0, 150.0); val (xRight, _) = rectOf(150.0, 0.0)
        assertTrue(yTop < c); assertTrue(xRight > c)
        // Auflösung: Kantenlänge entspricht der größten Board-Ausdehnung im Vollbild (mal Rand), gedeckelt
        assertTrue(plan.size in BoardRectifier.MIN_SIZE..BoardRectifier.MAX_SIZE)
        assertTrue(plan.scale > 0.5 && plan.scale < 2.0)
    }

    @Test fun roundTripThroughUpright() {
        val plan = BoardRectifier.plan(sideView(), fw, fh, rotW, rotH)
        val (ux, uy) = plan.toUpright.map(123.0, 456.0)
        val (rx, ry) = plan.toUpright.inverse()!!.map(ux, uy)
        assertEquals(123.0, rx, 1e-6); assertEquals(456.0, ry, 1e-6)
    }
}
