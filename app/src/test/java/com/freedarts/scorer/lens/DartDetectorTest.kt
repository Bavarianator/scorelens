package com.freedarts.scorer.lens

import com.freedarts.scorer.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DartDetectorTest {
    private val w = 240; private val h = 320

    private fun cam(bx: Double, by: Double): Pair<Double, Double> {
        val persp = 1.0 + by / 1200.0
        return (120 + bx * 0.55 / persp) to (170 - by * 0.5 / persp)
    }

    private val empty = ByteArray(w * h) { (40 + (it % 7)).toByte() }

    private fun detector(): DartDetector {
        val d = DartDetector(w, h)
        assertTrue(d.calibrate(DartDetector.boardPoints().map { (bx, by) -> cam(bx, by) }))
        d.setReference(empty)
        return d
    }

    private fun drawDart(img: ByteArray, tipBx: Double, tipBy: Double) {
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

    private fun lastEvent(d: DartDetector, frame: ByteArray, n: Int): DartDetector.Event? {
        var ev: DartDetector.Event? = null
        repeat(n) { ev = d.process(frame) ?: ev }
        return ev
    }

    @Test fun briefLargeChangeIsNotATakeout() {
        val d = detector()
        // Ein Frame mit großer Änderung (Dart trifft Dart, Flight wackelt), danach steckt nur der neue Dart
        val hand = empty.copyOf(); for (i in 0 until w * h) if ((i / w) in 100..300) hand[i] = 200.toByte()
        assertEquals(null, d.process(hand))
        val frame = empty.copyOf(); drawDart(frame, 0.0, 103.0)
        val ev = lastEvent(d, frame, 6)
        assertTrue("Erwartet Dart, war $ev", ev is DartDetector.Event.Dart)
        assertEquals(Segment.triple(20), (ev as DartDetector.Event.Dart).segment)
        assertEquals(1, d.dartsOnBoard)
    }

    @Test fun handOverSeveralFramesIsATakeout() {
        val d = detector()
        val frame = empty.copyOf(); drawDart(frame, 0.0, 103.0)
        lastEvent(d, frame, 6)
        assertEquals(1, d.dartsOnBoard)
        val hand = frame.copyOf(); for (i in 0 until w * h) if ((i / w) in 100..300) hand[i] = 200.toByte()
        repeat(d.minHandFrames) { d.process(hand) }
        assertEquals(DartDetector.Phase.TAKEOUT, d.phase)
        assertEquals(DartDetector.Event.Takeout, lastEvent(d, empty, 8))
        assertEquals(0, d.dartsOnBoard)
    }

    @Test fun dartSizedBlobThatVanishesIsABounceOut() {
        val d = detector()
        val frame = empty.copyOf(); drawDart(frame, 0.0, 103.0)
        d.process(frame)                     // Dart erscheint (Bewegung)
        assertEquals(DartDetector.Phase.MOTION, d.phase)
        assertEquals(DartDetector.Event.Bounce, lastEvent(d, empty, 6))
        assertEquals(0, d.dartsOnBoard)
    }

    @Test fun handThatVanishesIsNotABounceOut() {
        val d = detector()
        // Ein Frame Hand (weit über maxBlob), dann wieder leer: kein Bouncer, kein Ereignis
        val hand = empty.copyOf(); for (i in 0 until w * h) if ((i / w) in 100..300) hand[i] = 200.toByte()
        d.process(hand)
        assertEquals(null, lastEvent(d, empty, 6))
        assertEquals(DartDetector.Phase.IDLE, d.phase)
    }

    @Test fun oversizedBlobIsNotADart() {
        val d = detector()
        // 40×40 Pixel Fläche mitten auf dem Board: zu groß für einen Dart, zu klein für eine Hand
        val frame = empty.copyOf()
        val (cx, cy) = cam(0.0, 0.0)
        for (y in -20 until 20) for (x in -20 until 20) frame[(cy.toInt() + y) * w + cx.toInt() + x] = 250.toByte()
        val ev = lastEvent(d, frame, 6)
        assertEquals(DartDetector.Event.ReferenceUpdated, ev)
        assertEquals(0, d.dartsOnBoard)
    }
}
