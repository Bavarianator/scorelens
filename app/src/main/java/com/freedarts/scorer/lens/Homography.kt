package com.freedarts.scorer.lens

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Projektive Abbildung (3×3) zwischen Kamerabild und Board-Ebene.
 * Berechnet aus Punktpaaren per DLT (direkte lineare Transformation) mit Koordinaten-Normalisierung.
 */
class Homography(val m: DoubleArray) {

    fun map(x: Double, y: Double): Pair<Double, Double> {
        val w = m[6] * x + m[7] * y + m[8]
        if (abs(w) < 1e-12) return 0.0 to 0.0
        return (m[0] * x + m[1] * y + m[2]) / w to (m[3] * x + m[4] * y + m[5]) / w
    }

    operator fun times(o: Homography): Homography {
        val a = m; val b = o.m
        val r = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) s += a[i * 3 + k] * b[k * 3 + j]
            r[i * 3 + j] = s
        }
        return Homography(r)
    }

    fun inverse(): Homography? {
        val a = m
        val det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])
        if (abs(det) < 1e-18) return null
        val inv = doubleArrayOf(
            (a[4] * a[8] - a[5] * a[7]) / det, (a[2] * a[7] - a[1] * a[8]) / det, (a[1] * a[5] - a[2] * a[4]) / det,
            (a[5] * a[6] - a[3] * a[8]) / det, (a[0] * a[8] - a[2] * a[6]) / det, (a[2] * a[3] - a[0] * a[5]) / det,
            (a[3] * a[7] - a[4] * a[6]) / det, (a[1] * a[6] - a[0] * a[7]) / det, (a[0] * a[4] - a[1] * a[3]) / det,
        )
        return Homography(inv)
    }

    /** Normiert so, dass m[8] = 1 (falls möglich). */
    fun normalized(): Homography = if (abs(m[8]) > 1e-12) Homography(DoubleArray(9) { m[it] / m[8] }) else this

    companion object {
        fun affine(a: Double, b: Double, tx: Double, c: Double, d: Double, ty: Double) =
            Homography(doubleArrayOf(a, b, tx, c, d, ty, 0.0, 0.0, 1.0))

        /**
         * src[i] → dst[i]; jeweils Paare (x, y). Mindestens 4 Punkte, mehr Punkte = Ausgleichslösung.
         */
        fun from(src: List<Pair<Double, Double>>, dst: List<Pair<Double, Double>>): Homography? {
            if (src.size < 4 || src.size != dst.size) return null
            val (ts, s2) = normalize(src)
            val (td, d2) = normalize(dst)
            val h = solve(s2, d2) ?: return null
            val tdInv = td.inverse() ?: return null
            return (tdInv * h * ts).normalized()
        }

        private fun normalize(pts: List<Pair<Double, Double>>): Pair<Homography, List<Pair<Double, Double>>> {
            val mx = pts.sumOf { it.first } / pts.size
            val my = pts.sumOf { it.second } / pts.size
            var meanDist = pts.sumOf { sqrt((it.first - mx) * (it.first - mx) + (it.second - my) * (it.second - my)) } / pts.size
            if (meanDist < 1e-9) meanDist = 1.0
            val s = sqrt(2.0) / meanDist
            val t = Homography(doubleArrayOf(s, 0.0, -s * mx, 0.0, s, -s * my, 0.0, 0.0, 1.0))
            return t to pts.map { (it.first - mx) * s to (it.second - my) * s }
        }

        private fun solve(src: List<Pair<Double, Double>>, dst: List<Pair<Double, Double>>): Homography? {
            val n = src.size
            val rows = 2 * n
            val a = Array(rows) { DoubleArray(8) }
            val b = DoubleArray(rows)
            for (i in 0 until n) {
                val (x, y) = src[i]; val (u, v) = dst[i]
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y); b[2 * i] = u
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y); b[2 * i + 1] = v
            }
            val h = solveLeastSquares(a, b) ?: return null
            return Homography(doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0))
        }

        /** Löst (AᵀA)x = Aᵀb per Gauß-Elimination mit Pivotisierung. */
        private fun solveLeastSquares(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = 8
            val ata = Array(n) { DoubleArray(n) }
            val atb = DoubleArray(n)
            for (r in a.indices) for (i in 0 until n) {
                atb[i] += a[r][i] * b[r]
                for (j in 0 until n) ata[i][j] += a[r][i] * a[r][j]
            }
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(ata[r][col]) > abs(ata[pivot][col])) pivot = r
                if (abs(ata[pivot][col]) < 1e-14) return null
                if (pivot != col) { val t = ata[pivot]; ata[pivot] = ata[col]; ata[col] = t; val tb = atb[pivot]; atb[pivot] = atb[col]; atb[col] = tb }
                for (r in col + 1 until n) {
                    val f = ata[r][col] / ata[col][col]
                    if (f == 0.0) continue
                    for (c in col until n) ata[r][c] -= f * ata[col][c]
                    atb[r] -= f * atb[col]
                }
            }
            val x = DoubleArray(n)
            for (r in n - 1 downTo 0) {
                var s = atb[r]
                for (c in r + 1 until n) s -= ata[r][c] * x[c]
                x[r] = s / ata[r][r]
            }
            return x
        }
    }
}
