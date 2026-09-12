package com.freedarts.scorer.lens

import com.freedarts.scorer.engine.Board
import com.freedarts.scorer.model.Segment
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reine Bildverarbeitung für die Handykamera-Erkennung ("Lens"):
 * Graubild gegen Referenzbild vergleichen, stabile Veränderung abwarten, Blob suchen,
 * Spitze bestimmen und über die Homographie auf das Board abbilden.
 *
 * Arbeitet auf einem aufrechten Graubild (width × height, ein Byte je Pixel).
 * Keine Android-Abhängigkeiten – unit-testbar.
 */
class DartDetector(val width: Int, val height: Int) {

    enum class Phase { NO_REFERENCE, IDLE, MOTION, TAKEOUT }

    sealed class Event {
        data class Dart(val segment: Segment, val imageX: Float, val imageY: Float, val boardX: Double, val boardY: Double) : Event()
        data object Takeout : Event()
        data object Bounce : Event()
        data object ReferenceUpdated : Event()
    }

    /** Kalibrierpunkte am äußeren Doppelring (Winkel im Uhrzeigersinn ab oben). Reihenfolge: 20/1, 6/10, 3/19, 11/14. */
    companion object {
        val CALIBRATION_ANGLES = doubleArrayOf(9.0, 99.0, 189.0, 279.0)
        val CALIBRATION_LABELS = listOf("20/1", "6/10", "3/19", "11/14")
        fun boardPoints(): List<Pair<Double, Double>> = CALIBRATION_ANGLES.map { deg ->
            val r = Math.toRadians(deg)
            Board.DOUBLE_OUTER * sin(r) to Board.DOUBLE_OUTER * cos(r)
        }
    }

    var imageToBoard: Homography? = null; private set
    var boardToImage: Homography? = null; private set
    /** Board-Radius (170 mm) in Pixeln – Maß für die Bildgröße des Boards. */
    var boardRadiusPx = 0.0; private set
    /** Bewegungsanteil (Pixel mit Änderung gegenüber dem Vorframe) im letzten Frame. */
    var lastMotionFraction = 0.0; private set
    /** Mittlere absolute Änderung gegenüber dem Vorframe (Rauschmaß) im letzten ruhigen Frame. */
    var lastNoiseMean = 0.0; private set
    private var roi = BooleanArray(width * height)
    private var roiCount = 0

    private val reference = ByteArray(width * height)
    private val emptyBoard = ByteArray(width * height)
    private var hasEmpty = false
    /** Anzahl Pixel, in denen sich die Referenz (mit Darts) vom leeren Board unterscheidet. */
    private var refVsEmpty = 0
    private val previous = ByteArray(width * height)
    private val mask = BooleanArray(width * height)

    var phase = Phase.NO_REFERENCE; private set
    var dartsOnBoard = 0; private set
    private var stableFrames = 0
    private var motionFrames = 0
    /** Größte Änderung während der Bewegungsphase; über [maxBlob] war es eine Hand, kein Bouncer. */
    private var peakChange = 0

    /** Schwellwert für Pixelveränderung (0..255). */
    var pixelThreshold = 38
    /** Anteil des ROI, ab dem eine Veränderung als Hand/Takeout gilt. */
    var handFraction = 0.10
    /** Mindestgröße eines Dart-Blobs (Pixel). */
    var minBlob = 18
    /** Höchstgröße eines Dart-Blobs (Pixel); größere Änderungen sind Hand, Arm oder Licht. Wird bei der Kalibrierung gesetzt. */
    var maxBlob = Int.MAX_VALUE
    /** Frames ohne Änderung, bis ein Ereignis ausgewertet wird. */
    var stableNeeded = 3
    /** Frames in Folge mit Hand im Bild, bevor ein Takeout beginnt (ein Dart, der einen anderen trifft, ist nur kurz „groß“). */
    var minHandFrames = 2
    private var handFrames = 0

    /** Kalibrierung setzen: 4 Bildpunkte (Pixelkoordinaten des Graubilds) in Reihenfolge [CALIBRATION_LABELS]. */
    fun calibrate(imagePoints: List<Pair<Double, Double>>): Boolean = calibrateWith(imagePoints, boardPoints())

    /** Kalibrierung aus beliebigen Punktpaaren (Bild ↔ Board in mm), mindestens vier. */
    fun calibrateWith(imagePoints: List<Pair<Double, Double>>, bp: List<Pair<Double, Double>>): Boolean {
        val i2b = Homography.from(imagePoints, bp) ?: return false
        val b2i = Homography.from(bp, imagePoints) ?: return false
        imageToBoard = i2b; boardToImage = b2i
        val (cx, cy) = b2i.map(0.0, 0.0); val (ex, ey) = b2i.map(Board.DOUBLE_OUTER, 0.0)
        boardRadiusPx = Math.hypot(ex - cx, ey - cy)
        // ROI: alle Pixel, die auf dem Board (Radius ≤ 185 mm) liegen
        var c = 0
        val r = BooleanArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val (bx, by) = i2b.map(x + 0.5, y + 0.5)
            if (bx * bx + by * by <= 185.0 * 185.0) { r[y * width + x] = true; c++ }
        }
        roi = r; roiCount = c
        maxBlob = (boardRadiusPx * boardRadiusPx * 0.08).toInt().coerceAtLeast(200)
        return c > 100
    }

    fun isCalibrated() = imageToBoard != null && roiCount > 100

    /** Aktuelles Bild als Referenz (leeres Board) übernehmen. */
    fun setReference(gray: ByteArray) {
        System.arraycopy(gray, 0, reference, 0, reference.size)
        System.arraycopy(gray, 0, emptyBoard, 0, emptyBoard.size)
        System.arraycopy(gray, 0, previous, 0, previous.size)
        hasEmpty = true
        dartsOnBoard = 0
        refVsEmpty = 0
        phase = Phase.IDLE
        stableFrames = 0
    }

    /** Neue Referenz (z.B. nach Kamerabewegung), Darts bleiben gezählt; leeres Board bleibt unbekannt bis zum Takeout. */
    fun setReferenceKeepingDarts(gray: ByteArray) {
        System.arraycopy(gray, 0, reference, 0, reference.size)
        System.arraycopy(gray, 0, previous, 0, previous.size)
        if (dartsOnBoard == 0) System.arraycopy(gray, 0, emptyBoard, 0, emptyBoard.size)
        refVsEmpty = if (dartsOnBoard == 0) 0 else countDiff(reference, emptyBoard, pixelThreshold)
        phase = Phase.IDLE
        stableFrames = 0
    }

    fun reset() { phase = if (hasEmpty) Phase.IDLE else Phase.NO_REFERENCE; dartsOnBoard = 0; stableFrames = 0 }

    /** Ein extern (KI) erkannter Dart: aktuelles Bild wird Referenz, Zähler steigt. */
    fun registerExternalDart(gray: ByteArray) {
        if (phase == Phase.NO_REFERENCE) return
        System.arraycopy(gray, 0, reference, 0, reference.size)
        System.arraycopy(gray, 0, previous, 0, previous.size)
        dartsOnBoard++
        refVsEmpty = countDiff(reference, emptyBoard, pixelThreshold)
        phase = Phase.IDLE
        stableFrames = 0
    }

    /** Anteil veränderter ROI-Pixel im letzten Frame (für die Anzeige). */
    var lastChangeFraction = 0.0; private set

    /**
     * Verarbeitet ein Graubild. Gibt ein Ereignis zurück oder null.
     */
    fun process(gray: ByteArray): Event? {
        if (!isCalibrated()) {
            System.arraycopy(gray, 0, previous, 0, previous.size)
            return null
        }
        val thr = pixelThreshold
        if (phase == Phase.NO_REFERENCE) {
            // Nur Bewegung messen (für automatische Referenz)
            var movingPixels = 0; var sum = 0L
            for (i in 0 until width * height) {
                if (!roi[i]) continue
                val mv = abs((gray[i].toInt() and 0xFF) - (previous[i].toInt() and 0xFF))
                sum += mv
                if (mv > thr) movingPixels++
            }
            System.arraycopy(gray, 0, previous, 0, previous.size)
            lastMotionFraction = movingPixels.toDouble() / roiCount
            if (movingPixels <= roiCount * 0.004) lastNoiseMean = sum.toDouble() / roiCount
            return null
        }
        // Änderung gegenüber dem vorherigen Frame (Bewegung) und gegenüber der Referenz
        var movingPixels = 0
        var changed = 0
        var moveSum = 0L
        for (i in 0 until width * height) {
            if (!roi[i]) { mask[i] = false; continue }
            val g = gray[i].toInt() and 0xFF
            val d = abs(g - (reference[i].toInt() and 0xFF))
            val mv = abs(g - (previous[i].toInt() and 0xFF))
            val ch = d > thr
            mask[i] = ch
            if (ch) changed++
            if (mv > thr) movingPixels++
            moveSum += mv
        }
        System.arraycopy(gray, 0, previous, 0, previous.size)
        lastChangeFraction = changed.toDouble() / roiCount
        lastMotionFraction = movingPixels.toDouble() / roiCount
        val moving = movingPixels > roiCount * 0.004
        if (!moving) lastNoiseMean = moveSum.toDouble() / roiCount
        if (moving) { stableFrames = 0; motionFrames++ } else stableFrames++

        when (phase) {
            Phase.IDLE -> {
                if (changed >= minBlob) {
                    phase = Phase.MOTION; stableFrames = 0; motionFrames = 0; peakChange = changed
                    handFrames = if (changed > roiCount * handFraction) 1 else 0
                }
                return null
            }
            Phase.MOTION -> {
                if (changed > peakChange) peakChange = changed
                // Hand im Bild: erst nach [minHandFrames] Frames in Folge Takeout-Phase
                if (changed > roiCount * handFraction) handFrames++ else handFrames = 0
                if (handFrames >= minHandFrames) { phase = Phase.TAKEOUT; stableFrames = 0; handFrames = 0; return null }
                if (stableFrames < stableNeeded) return null
                // Stabil: auswerten
                return when {
                    changed < minBlob -> { phase = Phase.IDLE; if (peakChange <= maxBlob) Event.Bounce else null }
                    dartsOnBoard > 0 && looksEmpty(gray, thr) -> {
                        // Darts wurden entfernt, ohne dass eine Hand erkannt wurde
                        System.arraycopy(gray, 0, reference, 0, reference.size)
                        System.arraycopy(gray, 0, emptyBoard, 0, emptyBoard.size)
                        dartsOnBoard = 0; refVsEmpty = 0; phase = Phase.IDLE
                        Event.Takeout
                    }
                    else -> {
                        val ev = findDart(gray)
                        if (ev != null) {
                            dartsOnBoard++
                            System.arraycopy(gray, 0, reference, 0, reference.size)
                            refVsEmpty = countDiff(reference, emptyBoard, thr)
                            phase = Phase.IDLE
                            ev
                        } else {
                            // Unklare Veränderung (z.B. Lichtwechsel): neue Referenz
                            System.arraycopy(gray, 0, reference, 0, reference.size)
                            if (dartsOnBoard == 0) System.arraycopy(gray, 0, emptyBoard, 0, emptyBoard.size)
                            refVsEmpty = countDiff(reference, emptyBoard, thr)
                            phase = Phase.IDLE
                            Event.ReferenceUpdated
                        }
                    }
                }
            }
            Phase.TAKEOUT -> {
                if (stableFrames < stableNeeded + 2) return null
                // Ist das Board wieder leer?
                val wasEmptyLike = looksEmpty(gray, thr)
                System.arraycopy(gray, 0, reference, 0, reference.size)
                if (wasEmptyLike || changed > roiCount * handFraction) {
                    // Board leer oder komplett anders (Beleuchtung) → neue Runde
                    System.arraycopy(gray, 0, emptyBoard, 0, emptyBoard.size)
                    val had = dartsOnBoard
                    dartsOnBoard = 0
                    refVsEmpty = 0
                    phase = Phase.IDLE
                    return if (had > 0 || wasEmptyLike) Event.Takeout else Event.ReferenceUpdated
                }
                refVsEmpty = countDiff(reference, emptyBoard, thr)
                phase = Phase.IDLE
                return Event.ReferenceUpdated
            }
            else -> return null
        }
    }

    private fun countDiff(a: ByteArray, b: ByteArray, thr: Int): Int {
        var c = 0
        for (i in 0 until width * height) {
            if (!roi[i]) continue
            if (abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)) > thr) c++
        }
        return c
    }

    /** Sieht das Bild wieder wie das leere Board aus? Maßstab ist die Größe der Darts in der Referenz. */
    private fun looksEmpty(gray: ByteArray, thr: Int): Boolean {
        val diffEmpty = countDiff(gray, emptyBoard, thr)
        return diffEmpty < refVsEmpty * 0.35 + minBlob / 2
    }

    /** Größten zusammenhängenden Blob in [mask] suchen und die Dartspitze bestimmen. */
    private fun findDart(gray: ByteArray): Event.Dart? {
        val labels = IntArray(width * height)
        var bestSize = 0
        var bestLabel = 0
        var label = 0
        val stack = IntArray(width * height)
        for (start in 0 until width * height) {
            if (!mask[start] || labels[start] != 0) continue
            label++
            var sp = 0
            stack[sp++] = start; labels[start] = label
            var size = 0
            while (sp > 0) {
                val i = stack[--sp]; size++
                val x = i % width; val y = i / width
                if (x > 0) sp = push(i - 1, label, labels, stack, sp)
                if (x < width - 1) sp = push(i + 1, label, labels, stack, sp)
                if (y > 0) sp = push(i - width, label, labels, stack, sp)
                if (y < height - 1) sp = push(i + width, label, labels, stack, sp)
            }
            if (size > bestSize) { bestSize = size; bestLabel = label }
        }
        if (bestSize < minBlob || bestSize > maxBlob) return null

        // Pixel des Blobs sammeln
        val xs = DoubleArray(bestSize); val ys = DoubleArray(bestSize)
        var n = 0
        var mx = 0.0; var my = 0.0
        for (i in 0 until width * height) if (labels[i] == bestLabel) {
            xs[n] = (i % width).toDouble(); ys[n] = (i / width).toDouble(); mx += xs[n]; my += ys[n]; n++
        }
        mx /= n; my /= n
        // Hauptachse (PCA)
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (k in 0 until n) { val dx = xs[k] - mx; val dy = ys[k] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        val theta = 0.5 * Math.atan2(2 * sxy, sxx - syy)
        val dx = cos(theta); val dy = sin(theta)
        var tMin = Double.MAX_VALUE; var tMax = -Double.MAX_VALUE
        val t = DoubleArray(n); val p = DoubleArray(n)
        for (k in 0 until n) {
            t[k] = (xs[k] - mx) * dx + (ys[k] - my) * dy
            p[k] = -(xs[k] - mx) * dy + (ys[k] - my) * dx
            if (t[k] < tMin) tMin = t[k]; if (t[k] > tMax) tMax = t[k]
        }
        val range = tMax - tMin
        if (range < 3.0) {
            // Punktförmiger Blob: Schwerpunkt nehmen
            return toDart(mx, my)
        }
        // Breite an beiden Enden vergleichen: die Spitze ist das schmale Ende
        fun spread(lo: Double, hi: Double): Double {
            var s = 0.0; var c = 0
            for (k in 0 until n) if (t[k] in lo..hi) { s += p[k] * p[k]; c++ }
            return if (c == 0) 0.0 else sqrt(s / c)
        }
        val endLen = range * 0.25
        val spreadMax = spread(tMax - endLen, tMax)
        val spreadMin = spread(tMin, tMin + endLen)
        val tipAtMax = spreadMax < spreadMin
        // Extrempunkt am schmalen Ende (Mittel der 3 äußersten Pixel)
        val idx = (0 until n).sortedBy { if (tipAtMax) -t[it] else t[it] }.take(3)
        val tx = idx.map { xs[it] }.average(); val ty = idx.map { ys[it] }.average()
        return toDart(tx, ty)
    }

    private fun push(i: Int, label: Int, labels: IntArray, stack: IntArray, sp: Int): Int {
        if (mask[i] && labels[i] == 0) { labels[i] = label; stack[sp] = i; return sp + 1 }
        return sp
    }

    private fun toDart(x: Double, y: Double): Event.Dart? {
        val h = imageToBoard ?: return null
        val (bx, by) = h.map(x + 0.5, y + 0.5)
        val seg = Board.segmentAt(bx, by)
        return Event.Dart(seg, x.toFloat(), y.toFloat(), bx, by)
    }
}
