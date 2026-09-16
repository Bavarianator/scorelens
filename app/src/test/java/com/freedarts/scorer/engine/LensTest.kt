package com.freedarts.scorer.engine

import com.freedarts.scorer.lens.DartDetector
import com.freedarts.scorer.lens.Homography
import com.freedarts.scorer.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LensTest {
    @Test fun relabelReplacesNearestTipOrRemovesIt() {
        val p = { x: Double, y: Double -> com.freedarts.scorer.lens.YoloDartModel.Point(x, y, 0.7f) }
        val res = com.freedarts.scorer.lens.YoloDartModel.Result(emptyMap(), listOf(p(100.0, 100.0), p(300.0, 300.0)), 0)
        val moved = com.freedarts.scorer.lens.TrainingCapture.relabeled(res, 105.0, 98.0, p(120.0, 110.0))
        assertEquals(listOf(120.0, 300.0), moved.darts.map { it.x })
        val removed = com.freedarts.scorer.lens.TrainingCapture.relabeled(res, 290.0, 310.0, null)
        assertEquals(listOf(100.0), removed.darts.map { it.x })
    }

    private val w = 240; private val h = 320

    /** Synthetische Kamera: Board-mm → Bildpixel (Skalierung, Verschiebung, leichte Perspektive). */
    private fun cam(bx: Double, by: Double): Pair<Double, Double> {
        val persp = 1.0 + by / 1200.0
        return (120 + bx * 0.55 / persp) to (170 - by * 0.5 / persp)
    }

    @Test fun homographyRoundTrip() {
        val src = listOf(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        val dst = listOf(10.0 to 10.0, 200.0 to 20.0, 190.0 to 210.0, 20.0 to 190.0)
        val hm = Homography.from(src, dst)!!
        for (i in src.indices) {
            val (x, y) = hm.map(src[i].first, src[i].second)
            assertEquals(dst[i].first, x, 1e-6); assertEquals(dst[i].second, y, 1e-6)
        }
        val inv = hm.inverse()!!
        val (x, y) = inv.map(200.0, 20.0)
        assertEquals(100.0, x, 1e-6); assertEquals(0.0, y, 1e-6)
    }

    private fun calibratedDetector(): DartDetector {
        val d = DartDetector(w, h)
        val pts = DartDetector.boardPoints().map { (bx, by) -> cam(bx, by) }
        assertTrue(d.calibrate(pts))
        return d
    }

    private fun drawDart(img: ByteArray, tipBx: Double, tipBy: Double) {
        // Dart: Linie von der Spitze (Board) nach unten-außen mit breiterem Flight am Ende
        val (tx, ty) = cam(tipBx, tipBy)
        for (k in 0..40) {
            val x = tx + k * 0.3; val y = ty + k * 0.9
            val width = if (k > 30) 4 else 1
            for (dx in -width..width) {
                val px = (x + dx).toInt(); val py = y.toInt()
                if (px in 0 until w && py in 0 until h) img[py * w + px] = 250.toByte()
            }
        }
    }

    @Test fun detectsTripleTwenty() {
        val d = calibratedDetector()
        val empty = ByteArray(w * h) { (40 + (it % 7)).toByte() }
        d.setReference(empty)
        // Dart auf T20 (0, 103)
        val frame = empty.copyOf()
        drawDart(frame, 0.0, 103.0)
        var ev: DartDetector.Event? = null
        repeat(6) { ev = d.process(frame) ?: ev }
        assertTrue("Erwartet Dart-Ereignis, war $ev", ev is DartDetector.Event.Dart)
        assertEquals(Segment.triple(20), (ev as DartDetector.Event.Dart).segment)
        assertEquals(1, d.dartsOnBoard)

        // Zweiter Dart auf D16: Winkel von 16 = Index 13 → 234°
        val frame2 = frame.copyOf()
        val a = Math.toRadians(13 * 18.0)
        drawDart(frame2, 166 * sin(a), 166 * cos(a))
        ev = null
        repeat(6) { ev = d.process(frame2) ?: ev }
        assertEquals(Segment.double(16), (ev as DartDetector.Event.Dart).segment)

        // Takeout: Hand (große Änderung), danach leeres Board
        val hand = frame2.copyOf(); for (i in 0 until w * h) if ((i / w) in 100..300) hand[i] = 200.toByte()
        repeat(2) { d.process(hand) }
        ev = null
        repeat(8) { ev = d.process(empty) ?: ev }
        assertEquals(DartDetector.Event.Takeout, ev)
        assertEquals(0, d.dartsOnBoard)
    }
}
