package com.freedarts.scorer.lens

import com.freedarts.scorer.engine.Board
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Findet das Dartboard automatisch im Kamerabild (Farbbild, aufrecht, Breite×Höhe, Pixel als 0xRRGGBB).
 *
 * Ablauf:
 * 1. Rot/Grün-Maske (Doppel- und Triple-Ring, Bull).
 * 2. Strahlen vom Schwerpunkt nach außen → äußere Kante des Doppelrings und des Triple-Rings.
 * 3. Robuster Ellipsen-Fit auf die äußere Doppelring-Kante.
 * 4. Drehung/Spiegelung über das Rot/Grün-Sektormuster bestimmen ("20 oben" als Prior).
 * 5. Homographie iterativ aus Ring-Kanten und Draht-Übergängen verfeinern.
 *
 * Reines Kotlin, unit-testbar.
 */
class BoardFinder(val width: Int, val height: Int) {

    enum class Quality { NOT_FOUND, PARTIAL, TOO_SMALL, INACCURATE, GOOD }

    data class Ellipse(val cx: Double, val cy: Double, val a: Double, val b: Double, val psi: Double) {
        fun point(phi: Double): Pair<Double, Double> {
            val c = cos(phi); val s = sin(phi)
            return cx + a * c * cos(psi) - b * s * sin(psi) to cy + a * c * sin(psi) + b * s * cos(psi)
        }

        /** Kurze durch lange Halbachse (1 = Kreis = frontal; ≈ sin des Blickwinkels zur Boardfläche). */
        val axisRatio: Double get() = if (a <= 0 || b <= 0) 0.0 else minOf(a, b) / maxOf(a, b)
        /** Blickwinkel zur Boardfläche in Grad (90 = frontal), aus dem Achsenverhältnis. */
        val viewAngleDeg: Double get() = Math.toDegrees(kotlin.math.asin(axisRatio.coerceIn(0.0, 1.0)))
    }

    data class Fit(
        val boardToImage: Homography,
        val imageToBoard: Homography,
        /** Bildpositionen der vier Kalibrierpunkte (Reihenfolge wie [DartDetector.CALIBRATION_ANGLES]). */
        val points: List<Pair<Double, Double>>,
        val ellipse: Ellipse,
        val residualMm: Double,
        val patternScore: Double,
        val quality: Quality,
        val boardRadiusPx: Double,
    )

    companion object {
        const val OTHER = 0; const val RED = 1; const val GREEN = 2

        fun classify(rgb: Int): Int {
            val r = rgb shr 16 and 0xFF; val g = rgb shr 8 and 0xFF; val b = rgb and 0xFF
            if (r > 70 && r > g * 1.45 && r > b * 1.45) return RED
            if (g > 50 && g > r * 1.2 && g > b * 1.15) return GREEN
            return OTHER
        }

        /** Erwartete Ringfarbe des Sektors mit Index i (20 = rot, 1 = grün, …). */
        fun sectorColor(index: Int): Int = if (index % 2 == 0) RED else GREEN

        private const val R_DOUBLE_OUT = Board.DOUBLE_OUTER
        private const val R_DOUBLE_IN = Board.DOUBLE_INNER
        private const val R_TRIPLE_OUT = Board.TRIPLE_OUTER
        private const val R_TRIPLE_IN = Board.TRIPLE_INNER
        private const val R_DOUBLE_MID = (Board.DOUBLE_INNER + Board.DOUBLE_OUTER) / 2
        private const val R_TRIPLE_MID = (Board.TRIPLE_INNER + Board.TRIPLE_OUTER) / 2

        /**
         * Empfohlenes Achsenverhältnis der Board-Ellipse (≈ 35–55° zur Boardfläche, wie Autodarts es für Kameras
         * vorgibt). Nur ein Hinweis, nie blockierend: zu flach leidet die Genauigkeit (fängt das Residuum ab),
         * zu frontal verdeckt der Dart öfter seine eigene Spitze.
         */
        const val IDEAL_MIN = 0.57
        const val IDEAL_MAX = 0.82
    }

    private class RayHit(val x: Double, val y: Double, val radiusMm: Double)

    /** Optionale Diagnoseausgabe (Tests). */
    var log: ((String) -> Unit)? = null

    /**
     * @param preferredTop optionaler Bildpunkt, der in Richtung der 20 liegt (Nutzer-Tipp); sonst gilt "20 ist oben".
     */
    fun find(rgb: IntArray, preferredTop: Pair<Double, Double>? = null): Fit? {
        val cls = IntArray(width * height) { classify(rgb[it]) }
        var n = 0; var sx = 0.0; var sy = 0.0
        for (i in cls.indices) if (cls[i] != OTHER) { n++; sx += i % width; sy += i / width }
        log?.invoke("ringPixels=$n")
        if (n < 150) return null
        var cx = sx / n; var cy = sy / n
        log?.invoke("centroid=($cx, $cy)")

        // Zwei Durchläufe: Zentrum aus der Ellipse verbessern
        var ellipse: Ellipse? = null
        var hits: List<RayHit> = emptyList()
        repeat(2) {
            val (e, h) = castRays(cls, cx, cy) ?: run { log?.invoke("castRays: null"); return@repeat }
            ellipse = e; hits = h
            cx = e.cx; cy = e.cy
            log?.invoke("ellipse=$e hits=${h.size}")
        }
        val ell = ellipse ?: return null
        if (hits.size < 40) return null

        // 1) Radiale Verfeinerung (unabhängig von Drehung/Spiegelung): korrigiert die Perspektive
        var h = affineFor(ell, 0.0, 1.0)
        for ((iter, maxErr) in doubleArrayOf(30.0, 25.0, 20.0, 15.0, 12.0, 10.0, 8.0, 7.0, 6.0, 6.0).withIndex()) {
            val (bp, ip, res) = correspondences(cls, hits, h, maxErr, useWires = false)
            log?.invoke("radial $iter: pairs=${bp.size} residual=$res")
            if (bp.size < 12) break
            h = Homography.from(bp, ip) ?: break
        }
        // 2) Drehung + Spiegelung über das Rot/Grün-Muster
        val (h1, _) = orientation(cls, h, preferredTop) ?: run { log?.invoke("orientation: null"); return null }
        h = h1
        // 3) Endverfeinerung mit Draht-Übergängen
        var residual = Double.MAX_VALUE
        for (iter in 0 until 8) {
            val (bp, ip, res) = correspondences(cls, hits, h, 6.0, useWires = true)
            log?.invoke("final $iter: pairs=${bp.size} residual=$res")
            if (bp.size < 12) break
            val next = Homography.from(bp, ip) ?: break
            h = next
            residual = res
        }
        val score = patternScore(cls, h)
        log?.invoke("patternScore=$score residual=$residual")
        val inv = h.inverse() ?: return null
        val pts = DartDetector.boardPoints().map { (bx, by) -> h.map(bx, by) }
        val (rx, ry) = h.map(0.0, 0.0)
        val (ex, ey) = h.map(Board.DOUBLE_OUTER, 0.0)
        val radiusPx = hypot(ex - rx, ey - ry)
        val quality = when {
            score < 0.45 -> Quality.NOT_FOUND
            pts.any { it.first < 1 || it.second < 1 || it.first > width - 2 || it.second > height - 2 } ||
                ell.cx - ell.a < 1 || ell.cx + ell.a > width - 2 || ell.cy - ell.b < 1 || ell.cy + ell.b > height - 2 -> Quality.PARTIAL
            maxOf(ell.a, ell.b) < 0.2 * minOf(width, height) -> Quality.TOO_SMALL
            residual > 4.5 -> Quality.INACCURATE
            else -> Quality.GOOD
        }
        return Fit(h, inv, pts, ell, residual, score, quality, radiusPx)
    }

    /**
     * Verfeinert eine vorhandene Homographie (z.B. aus KI-Kalibrierpunkten) über Ring-Kanten und Draht-Übergänge.
     * Liefert null, wenn im Bild keine brauchbaren Ringkanten gefunden werden.
     */
    fun refine(rgb: IntArray, h0: Homography): Fit? {
        val cls = IntArray(width * height) { classify(rgb[it]) }
        val (cx, cy) = h0.map(0.0, 0.0)
        if (cx < 0 || cy < 0 || cx >= width || cy >= height) return null
        val (ell, hits) = castRays(cls, cx, cy) ?: return null
        if (hits.size < 40) return null
        var h = h0
        var residual = Double.MAX_VALUE
        for (maxErr in doubleArrayOf(12.0, 8.0, 6.0, 5.0, 5.0, 5.0)) {
            val (bp, ip, res) = correspondences(cls, hits, h, maxErr, useWires = true)
            if (bp.size < 24) break
            h = Homography.from(bp, ip) ?: break
            residual = res
        }
        if (residual == Double.MAX_VALUE) return null
        val score = patternScore(cls, h)
        val inv = h.inverse() ?: return null
        val pts = DartDetector.boardPoints().map { (bx, by) -> h.map(bx, by) }
        val (ex, ey) = h.map(Board.DOUBLE_OUTER, 0.0)
        val radiusPx = hypot(ex - cx, ey - cy)
        val ellH = ellipseFor(h) ?: ell
        val quality = when {
            score < 0.45 -> Quality.NOT_FOUND
            pts.any { it.first < 1 || it.second < 1 || it.first > width - 2 || it.second > height - 2 } -> Quality.PARTIAL
            maxOf(ellH.a, ellH.b) < 0.2 * minOf(width, height) -> Quality.TOO_SMALL
            residual > 4.5 -> Quality.INACCURATE
            else -> Quality.GOOD
        }
        return Fit(h, inv, pts, ellH, residual, score, quality, radiusPx)
    }

    /**
     * Bild-Ellipse des äußeren Doppelrings zu einer Board→Bild-Homographie (für Schrägheits-Hinweise, auch wenn die
     * Kalibrierung aus KI-Punkten statt aus Ringkanten stammt).
     */
    fun ellipseFor(boardToImage: Homography): Ellipse? {
        val pts = (0 until 72).map { i ->
            val a = i * 2 * PI / 72
            boardToImage.map(R_DOUBLE_OUT * cos(a), R_DOUBLE_OUT * sin(a))
        }
        return fitEllipse(pts)
    }

    // ---- Schritt 2/3: Strahlen + Ellipse ----

    private fun castRays(cls: IntArray, cx: Double, cy: Double): Pair<Ellipse, List<RayHit>>? {
        val outer = ArrayList<Pair<Double, Double>>()
        val hits = ArrayList<RayHit>()
        val rays = 180
        for (k in 0 until rays) {
            val ang = 2 * PI * k / rays
            val dx = cos(ang); val dy = sin(ang)
            // Läufe (runs) von Ringpixeln entlang des Strahls sammeln
            val runs = ArrayList<DoubleArray>() // [start, end]
            var runStart = -1.0; var last = -10.0; var gap = 0
            var d = 2.0
            while (true) {
                val px = (cx + dx * d).toInt(); val py = (cy + dy * d).toInt()
                if (px < 0 || py < 0 || px >= width || py >= height) break
                val on = cls[py * width + px] != OTHER
                if (on) {
                    if (runStart < 0) runStart = d
                    last = d; gap = 0
                } else if (runStart >= 0) {
                    gap++
                    if (gap > 1) { runs.add(doubleArrayOf(runStart, last)); runStart = -1.0 }
                }
                d += 1.0
            }
            if (runStart >= 0) runs.add(doubleArrayOf(runStart, last))
            val good = runs.filter { it[1] - it[0] >= 1.0 }
            if (good.size < 2) continue
            // Von außen nach innen: Doppelring-Kandidat, dazu ein Triple-Kandidat mit passendem Radiusverhältnis
            var chosen: Pair<DoubleArray, DoubleArray>? = null
            for (i in good.indices.reversed()) {
                val dbl = good[i]
                val trip = good.take(i).lastOrNull { val ratio = it[1] / dbl[1]; ratio in 0.52..0.76 }
                if (trip != null) { chosen = dbl to trip; break }
            }
            val (dbl, trip) = chosen ?: continue
            outer.add(cx + dx * (dbl[1] + 0.5) to cy + dy * (dbl[1] + 0.5))
            hits.add(RayHit(cx + dx * (dbl[1] + 0.5), cy + dy * (dbl[1] + 0.5), R_DOUBLE_OUT))
            hits.add(RayHit(cx + dx * (dbl[0] - 0.5), cy + dy * (dbl[0] - 0.5), R_DOUBLE_IN))
            hits.add(RayHit(cx + dx * (trip[1] + 0.5), cy + dy * (trip[1] + 0.5), R_TRIPLE_OUT))
            hits.add(RayHit(cx + dx * (trip[0] - 0.5), cy + dy * (trip[0] - 0.5), R_TRIPLE_IN))
        }
        log?.invoke("rays with double+triple: ${outer.size}")
        if (outer.size < 30) return null
        var pts: List<Pair<Double, Double>> = outer
        var e: Ellipse? = null
        repeat(3) {
            val fit = fitEllipse(pts) ?: return@repeat
            e = fit
            // Ausreißer entfernen
            val res = pts.map { residual(fit, it) }
            val med = res.sorted()[res.size / 2]
            val keep = pts.filterIndexed { i, _ -> res[i] <= maxOf(2.5 * med, 1.5) }
            if (keep.size >= 20) pts = keep
        }
        val ell = e ?: return null
        // Treffer nur von Strahlen behalten, deren Doppelring-Punkt zur Ellipse passt
        val keptHits = hits.chunked(4).filter { residual(ell, it[0].x to it[0].y) <= 3.0 }.flatten()
        return ell to keptHits
    }

    /** Näherungsweise Distanz eines Punkts zur Ellipse (radial). */
    private fun residual(e: Ellipse, p: Pair<Double, Double>): Double {
        val dx = p.first - e.cx; val dy = p.second - e.cy
        val c = cos(e.psi); val s = sin(e.psi)
        val u = (dx * c + dy * s) / e.a; val v = (-dx * s + dy * c) / e.b
        val r = hypot(u, v)
        return abs(r - 1.0) * minOf(e.a, e.b)
    }

    /** Algebraischer Kegelschnitt-Fit (kleinster Eigenvektor der Streumatrix), dann Ellipsenparameter. */
    internal fun fitEllipse(pts: List<Pair<Double, Double>>): Ellipse? {
        if (pts.size < 6) return null
        val mx = pts.sumOf { it.first } / pts.size; val my = pts.sumOf { it.second } / pts.size
        val sc = pts.sumOf { hypot(it.first - mx, it.second - my) } / pts.size
        if (sc < 1e-6) return null
        val s = Array(6) { DoubleArray(6) }
        for ((px, py) in pts) {
            val x = (px - mx) / sc; val y = (py - my) / sc
            val row = doubleArrayOf(x * x, x * y, y * y, x, y, 1.0)
            for (i in 0 until 6) for (j in 0 until 6) s[i][j] += row[i] * row[j]
        }
        val (values, vectors) = jacobiEigen(s)
        val k = values.indices.minByOrNull { values[it] } ?: return null
        val a = vectors[0][k]; val b = vectors[1][k]; val c = vectors[2][k]; val d = vectors[3][k]; val e = vectors[4][k]; val f = vectors[5][k]
        val denom = b * b - 4 * a * c
        if (denom >= 0) return null
        val cx = (2 * c * d - b * e) / denom
        val cy = (2 * a * e - b * d) / denom
        val fp = a * cx * cx + b * cx * cy + c * cy * cy + d * cx + e * cy + f
        val theta = 0.5 * atan2(b, a - c)
        val ct = cos(theta); val st = sin(theta)
        val l1 = a * ct * ct + b * ct * st + c * st * st
        val l2 = a * st * st - b * ct * st + c * ct * ct
        if (l1 == 0.0 || l2 == 0.0) return null
        val ra = -fp / l1; val rb = -fp / l2
        if (ra <= 0 || rb <= 0) return null
        return Ellipse(cx * sc + mx, cy * sc + my, sqrt(ra) * sc, sqrt(rb) * sc, theta)
    }

    private fun jacobiEigen(m: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val n = m.size
        val a = Array(n) { m[it].clone() }
        val v = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }
        for (sweep in 0 until 60) {
            var off = 0.0
            for (i in 0 until n) for (j in i + 1 until n) off += a[i][j] * a[i][j]
            if (off < 1e-22) break
            for (p in 0 until n) for (q in p + 1 until n) {
                if (abs(a[p][q]) < 1e-300) continue
                val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
                val c = 1 / sqrt(t * t + 1); val s = t * c
                for (k in 0 until n) {
                    val akp = a[k][p]; val akq = a[k][q]
                    a[k][p] = c * akp - s * akq; a[k][q] = s * akp + c * akq
                }
                for (k in 0 until n) {
                    val apk = a[p][k]; val aqk = a[q][k]
                    a[p][k] = c * apk - s * aqk; a[q][k] = s * apk + c * aqk
                }
                for (k in 0 until n) {
                    val vkp = v[k][p]; val vkq = v[k][q]
                    v[k][p] = c * vkp - s * vkq; v[k][q] = s * vkp + c * vkq
                }
            }
        }
        return DoubleArray(n) { a[it][it] } to v
    }

    // ---- Schritt 4: Drehung/Spiegelung über das Sektormuster ----

    private fun sample(cls: IntArray, x: Double, y: Double): Int {
        val px = x.toInt(); val py = y.toInt()
        if (px < 0 || py < 0 || px >= width || py >= height) return OTHER
        return cls[py * width + px]
    }

    private fun affineFor(e: Ellipse, rho: Double, flip: Double): Homography {
        // Bild = C + Rψ · diag(a,b)/170 · Rρ · diag(1, flip) · Board
        val cψ = cos(e.psi); val sψ = sin(e.psi)
        val cρ = cos(rho); val sρ = sin(rho)
        val sa = e.a / R_DOUBLE_OUT; val sb = e.b / R_DOUBLE_OUT
        // M = Rψ · S · Rρ · F
        val m00 = cρ; val m01 = -sρ * flip; val m10 = sρ; val m11 = cρ * flip
        val n00 = sa * m00; val n01 = sa * m01; val n10 = sb * m10; val n11 = sb * m11
        val a = cψ * n00 - sψ * n10; val b = cψ * n01 - sψ * n11
        val c = sψ * n00 + cψ * n10; val d = sψ * n01 + cψ * n11
        return Homography.affine(a, b, e.cx, c, d, e.cy)
    }

    private fun patternScore(cls: IntArray, h: Homography): Double {
        var match = 0; var total = 0
        for (i in 0 until 20) {
            val expected = sectorColor(i)
            for (off in doubleArrayOf(-6.0, 0.0, 6.0)) {
                val ang = Math.toRadians(i * Board.SECTOR_DEG + off)
                for (r in doubleArrayOf(R_DOUBLE_MID, R_TRIPLE_MID)) {
                    val (x, y) = h.map(r * sin(ang), r * cos(ang))
                    val c = sample(cls, x, y)
                    total++
                    if (c == expected) match++ else if (c != OTHER) match--
                }
            }
        }
        return match.toDouble() / total
    }

    private fun orientation(cls: IntArray, base: Homography, preferredTop: Pair<Double, Double>?): Pair<Homography, Double>? {
        data class Cand(val h: Homography, val score: Double)
        val cands = ArrayList<Cand>()
        // Das Rot/Grün-Muster ist spiegelsymmetrisch. Die Spiegelung wird deshalb physikalisch festgelegt:
        // eine echte Kamera-Projektion von Board (y nach oben) ins Bild (y nach unten) hat negative Jacobi-Determinante.
        val flip = if (jacobianDet(base) < 0) 1.0 else -1.0
        for (deg in 0 until 360) {
            val r = Math.toRadians(deg.toDouble())
            val t = Homography.affine(cos(r), -sin(r) * flip, 0.0, sin(r), cos(r) * flip, 0.0)
            val h = base * t
            cands.add(Cand(h, patternScore(cls, h)))
        }
        val best = cands.maxByOrNull { it.score } ?: return null
        log?.invoke("bestPattern=${best.score}")
        if (best.score < 0.35) return null
        // Mehrdeutigkeit (alle 36°): Kandidaten nahe am Maximum, dann Prior "20 oben" bzw. Nutzer-Tipp
        val top = cands.filter { it.score >= best.score - 0.05 }
        val (c0x, c0y) = base.map(0.0, 0.0)
        fun upness(c: Cand): Double {
            val (x, y) = c.h.map(0.0, R_DOUBLE_OUT)
            return if (preferredTop != null) -hypot(x - preferredTop.first, y - preferredTop.second)
            else (c0y - y) / (abs(x - c0x) + 1.0)
        }
        val chosen = top.maxByOrNull { upness(it) } ?: best
        return chosen.h to best.score
    }

    /** Determinante der Jacobi-Matrix von [h] im Board-Ursprung. */
    private fun jacobianDet(h: Homography): Double {
        val m = h.normalized().m
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val hh = m[7]
        return (a - c * g) * (e - f * hh) - (b - c * hh) * (d - f * g)
    }

    // ---- Schritt 5: Korrespondenzen für die Verfeinerung ----

    private fun correspondences(cls: IntArray, hits: List<RayHit>, h: Homography, maxErrMm: Double, useWires: Boolean): Triple<List<Pair<Double, Double>>, List<Pair<Double, Double>>, Double> {
        val inv = h.inverse() ?: return Triple(emptyList(), emptyList(), Double.MAX_VALUE)
        val bp = ArrayList<Pair<Double, Double>>(); val ip = ArrayList<Pair<Double, Double>>()
        var errSum = 0.0; var errN = 0
        for (hit in hits) {
            val (bx, by) = inv.map(hit.x, hit.y)
            val r = hypot(bx, by)
            val err = abs(r - hit.radiusMm)
            if (err > maxErrMm) continue
            val ang = atan2(bx, by)
            bp.add(hit.radiusMm * sin(ang) to hit.radiusMm * cos(ang)); ip.add(hit.x to hit.y)
            errSum += err; errN++
        }
        // Draht-Übergänge (rot↔grün) auf Doppel- und Triple-Ring
        if (useWires) for (r in doubleArrayOf(R_DOUBLE_MID, R_TRIPLE_MID)) {
            var lastColor = OTHER; var lastAng = 0.0
            var deg = 0.0
            while (deg < 360.0) {
                val ang = Math.toRadians(deg)
                val (x, y) = h.map(r * sin(ang), r * cos(ang))
                val c = sample(cls, x, y)
                if (c != OTHER) {
                    if (lastColor != OTHER && c != lastColor) {
                        val mid = (lastAng + deg) / 2
                        val wire = Math.round((mid - 9.0) / 18.0) * 18.0 + 9.0
                        if (abs(mid - wire) < 4.0) {
                            val wr = Math.toRadians(wire)
                            val (wx, wy) = h.map(r * sin(Math.toRadians(mid)), r * cos(Math.toRadians(mid)))
                            bp.add(r * sin(wr) to r * cos(wr)); ip.add(wx to wy)
                        }
                    }
                    lastColor = c; lastAng = deg
                }
                deg += 0.5
            }
        }
        return Triple(bp, ip, if (errN == 0) Double.MAX_VALUE else errSum / errN)
    }
}
