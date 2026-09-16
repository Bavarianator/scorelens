package com.freedarts.scorer.engine

import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Simuliert einen Bot-Wurf: Ziel wählen, Streuung in mm gemäß Stufe, Segment aus der Geometrie ableiten.
 * Elf Stufen wie bei Autodarts, Stufe 11 ≈ Profi.
 */
object Bot {
    /** Standardabweichung der Streuung in mm je Stufe (Index = Stufe). */
    private val SIGMA_MM = doubleArrayOf(0.0, 62.0, 50.0, 42.0, 36.0, 30.0, 26.0, 22.0, 18.5, 15.5, 12.5, 9.5)

    fun sigma(level: Int): Double = SIGMA_MM[level.coerceIn(1, 11)]

    /** Streuung (mm) für einen 3-Dart-Average: linear zwischen den Stufen interpoliert, außerhalb geklemmt (Bot „Wie ich“). */
    fun sigmaForAverage(avg: Double): Double {
        val a = Player.botAverage(1).toDouble(); val z = Player.botAverage(11).toDouble()
        if (avg <= a) return SIGMA_MM[1]
        if (avg >= z) return SIGMA_MM[11]
        for (l in 1 until 11) {
            val lo = Player.botAverage(l).toDouble(); val hi = Player.botAverage(l + 1).toDouble()
            if (avg <= hi) return SIGMA_MM[l] + (SIGMA_MM[l + 1] - SIGMA_MM[l]) * (avg - lo) / (hi - lo)
        }
        return SIGMA_MM[11]
    }

    fun throwAt(target: Segment, level: Int, random: Random = Random.Default): Segment = throwAt(target, sigma(level), random)

    fun throwAt(target: Segment, sigmaMm: Double, random: Random = Random.Default): Segment {
        val (cx, cy) = Board.centerOf(target)
        val (gx, gy) = gaussianPair(random)
        return Board.segmentAt(cx + gx * sigmaMm, cy + gy * sigmaMm)
    }

    internal fun gaussianPair(random: Random): Pair<Double, Double> {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        val r = sqrt(-2.0 * ln(u1))
        return r * cos(2 * Math.PI * u2) to r * sin(2 * Math.PI * u2)
    }
}
