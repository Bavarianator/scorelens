package com.freedarts.scorer.lens

import com.freedarts.scorer.lens.TipTracker.Tip
import com.freedarts.scorer.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TipTrackerTest {
    private val w = 240; private val h = 320
    private val gray = ByteArray(w * h) { 40 }

    /** Synthetische Kamera: Board-mm → Bildpixel (Skalierung, Verschiebung, leichte Perspektive). */
    private fun cam(bx: Double, by: Double): Pair<Double, Double> {
        val persp = 1.0 + by / 1200.0
        return (120 + bx * 0.55 / persp) to (170 - by * 0.5 / persp)
    }

    private fun detector(): DartDetector {
        val d = DartDetector(w, h)
        assertTrue(d.calibrate(DartDetector.boardPoints().map { (bx, by) -> cam(bx, by) }))
        d.setReference(gray)
        return d
    }

    /** KI-Spitze (Analyse-Koordinaten) für einen Board-Punkt. */
    private fun tip(bx: Double, by: Double, conf: Float = 0.8f): Tip { val (x, y) = cam(bx, by); return Tip(x - 0.5, y - 0.5, conf) }

    private fun classic(t: Tip, seg: Segment) = DartDetector.Event.Dart(seg, t.x.toFloat(), t.y.toFloat(), 0.0, 0.0)

    /** Eine KI-Auswertung einspeisen (prüft, dass sie überhaupt angefordert würde). */
    private fun feed(t: TipTracker, tips: List<Tip>, now: Long): DartDetector.Event? {
        assertTrue("Tracker wollte bei $now keine Auswertung", t.wantsInference(now))
        return t.onTips(tips, gray, now)
    }

    @Test fun classicDartConfirmedAfterThreeSamples() {
        val d = detector(); val t = TipTracker(d)
        val t20 = tip(0.0, 103.0)
        var now = 10_000L
        assertNull(t.step(classic(t20, Segment.triple(20)), gray, now)) // wird zurückgehalten
        assertTrue(t.checking)
        repeat(2) { now += 130; assertNull(feed(t, listOf(t20), now)) }
        now += 130
        val ev = feed(t, listOf(t20), now)
        assertTrue("Erwartet Dart, war $ev", ev is DartDetector.Event.Dart)
        assertEquals(Segment.triple(20), (ev as DartDetector.Event.Dart).segment)
        assertEquals(1, t.knownTips.size)
        assertFalse(t.checking)
    }

    @Test fun unpromptedDartNeedsFourSamplesAndIsNotCountedTwice() {
        val d = detector(); val t = TipTracker(d)
        val t20 = tip(0.0, 103.0)
        var now = 10_000L
        assertNull(t.step(null, gray, now))
        assertNull(feed(t, listOf(t20), now)) // ruhige Phase → Kandidat (1 Messung)
        assertTrue(t.checking)
        repeat(2) { now += 130; assertNull(feed(t, listOf(t20), now)) } // 2, 3
        now += 130
        val ev = feed(t, listOf(t20), now) // 4 → Dart
        assertEquals(Segment.triple(20), (ev as DartDetector.Event.Dart).segment)
        assertEquals(1, d.dartsOnBoard)
        now += 2000
        assertNull(feed(t, listOf(t20), now)) // dieselbe Spitze bleibt zugeordnet, kein zweiter Dart
        assertFalse(t.checking)
        assertEquals(1, t.knownTips.size)
    }

    @Test fun needsMoreSamplesNearWire() {
        val d = detector(); val t = TipTracker(d)
        val nearWire = tip(0.0, 99.5) // 0,5 mm innerhalb der Triple-Innenkante
        var now = 10_000L
        t.step(classic(nearWire, Segment.triple(20)), gray, now)
        repeat(4) { now += 130; assertNull("zu früh bestätigt", feed(t, listOf(nearWire), now)) }
        now += 130
        val ev = feed(t, listOf(nearWire), now) // 5. Messung
        assertEquals(Segment.triple(20), (ev as DartDetector.Event.Dart).segment)
    }

    @Test fun usesMedianAgainstOutliers() {
        val d = detector(); val t = TipTracker(d)
        val good = tip(0.0, 103.0)
        var now = 10_000L
        t.step(classic(good, Segment.triple(20)), gray, now)
        for (s in listOf(good, Tip(good.x + 3.0, good.y - 2.0), good)) { now += 130; feed(t, listOf(s), now) }
        assertEquals(1, t.knownTips.size)
        assertEquals(good.x, t.knownTips[0].first, 1e-9); assertEquals(good.y, t.knownTips[0].second, 1e-9)
    }

    @Test fun fallsBackToClassicDartWhenAiSeesNothing() {
        val d = detector(); val t = TipTracker(d)
        val cl = classic(tip(0.0, 103.0), Segment.triple(20))
        var now = 10_000L
        assertNull(t.step(cl, gray, now))
        var ev: DartDetector.Event? = null
        repeat(TipTracker.MAX_TRIES) { now += 130; ev = feed(t, emptyList(), now) ?: ev }
        assertEquals(cl, ev)
        assertEquals(1, t.knownTips.size)
    }

    @Test fun secondDartWhileFirstPendingLosesNothing() {
        val d = detector(); val t = TipTracker(d)
        val t20 = tip(0.0, 103.0)
        val a = Math.toRadians(13 * 18.0); val d16 = tip(166 * sin(a), 166 * cos(a))
        var now = 10_000L
        t.step(classic(t20, Segment.triple(20)), gray, now)
        repeat(2) { now += 130; feed(t, listOf(t20), now) } // 2 Messungen, noch nicht bestätigt
        val first = t.step(classic(d16, Segment.double(16)), gray, now) // Dart 2 landet
        assertEquals(Segment.triple(20), (first as DartDetector.Event.Dart).segment)
        assertTrue(t.checking)
        var second: DartDetector.Event? = null
        repeat(3) { now += 130; second = feed(t, listOf(t20, d16), now) ?: second }
        assertEquals(Segment.double(16), (second as DartDetector.Event.Dart).segment)
        assertEquals(2, t.knownTips.size)
    }

    @Test fun groupedDartsAreToldApart() {
        val d = detector(); val t = TipTracker(d)
        val first = tip(0.0, 103.0); val second = tip(2.0, 104.0) // 2,2 mm daneben (≈ 1,2 px)
        var now = 10_000L
        t.step(classic(first, Segment.triple(20)), gray, now)
        repeat(3) { now += 130; feed(t, listOf(first), now) }
        assertEquals(1, t.knownTips.size)
        t.step(classic(second, Segment.triple(20)), gray, now)
        var ev: DartDetector.Event? = null
        repeat(3) { now += 130; ev = feed(t, listOf(first, second), now) ?: ev }
        val dart = ev as DartDetector.Event.Dart
        assertEquals(2.0, dart.boardX, 1e-6); assertEquals(104.0, dart.boardY, 1e-6)
        assertEquals(2, t.knownTips.size)
    }

    @Test fun ignoresFourthDartEvent() {
        val d = detector(); val t = TipTracker(d)
        var now = 10_000L
        val tips = listOf(tip(0.0, 103.0), tip(-8.0, 100.0), tip(6.0, 101.0))
        for (tp in tips) {
            t.step(classic(tp, Segment.triple(20)), gray, now)
            repeat(3) { now += 130; feed(t, tips.take(tips.indexOf(tp) + 1), now) }
        }
        assertEquals(3, t.knownTips.size)
        assertNull(t.step(classic(tip(0.0, 50.0), Segment.single(20)), gray, now))
        assertFalse(t.checking)
    }

    @Test fun aiConfirmsTakeout() {
        val d = detector(); val t = TipTracker(d)
        val t20 = tip(0.0, 103.0)
        var now = 10_000L
        t.step(classic(t20, Segment.triple(20)), gray, now)
        repeat(3) { now += 130; feed(t, listOf(t20), now) }
        assertEquals(1, t.knownTips.size)
        now += 2000; assertNull(feed(t, emptyList(), now)) // 1. leere Auswertung
        now += 2000
        assertEquals(DartDetector.Event.Takeout, feed(t, emptyList(), now)) // 2. → Takeout
        assertEquals(0, t.knownTips.size)
    }

    @Test fun resyncKeepsHiddenDartsAndFindsNewOnes() {
        val d = detector(); val t = TipTracker(d)
        val t20 = tip(0.0, 103.0)
        var now = 10_000L
        t.step(classic(t20, Segment.triple(20)), gray, now)
        repeat(3) { now += 130; feed(t, listOf(t20), now) }
        val onBoard = t.knownOnBoard()
        assertEquals(1, onBoard.size)
        assertEquals(103.0, onBoard[0].second, 1e-6)
        // Nach "Kamerabewegung": T20 nicht sichtbar, dafür ein neuer Dart auf D16
        val a = Math.toRadians(13 * 18.0)
        t.resync(onBoard, listOf(tip(166 * sin(a), 166 * cos(a))), now)
        assertEquals(1, t.knownTips.size)
        assertTrue(t.checking)
    }
}
