package com.freedarts.scorer.engine

import com.freedarts.scorer.model.Segment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometrie eines Steel-Dartboards nach WDF-Maßen (Millimeter, Mittelpunkt = 0/0, y nach oben).
 * Wird für das virtuelle Board, die Koordinaten des Board Managers und den Bot benutzt.
 */
object Board {
    const val BULL_RADIUS = 6.35
    const val OUTER_BULL_RADIUS = 15.9
    const val TRIPLE_INNER = 99.0
    const val TRIPLE_OUTER = 107.0
    const val DOUBLE_INNER = 162.0
    const val DOUBLE_OUTER = 170.0
    const val BOARD_RADIUS = 225.5

    /** Reihenfolge der Zahlen im Uhrzeigersinn, beginnend oben bei 20. */
    val NUMBERS = listOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

    const val SECTOR_DEG = 18.0

    /** Segment an Position (x, y) in mm, y zeigt nach oben. */
    fun segmentAt(x: Double, y: Double): Segment {
        val r = sqrt(x * x + y * y)
        if (r <= BULL_RADIUS) return Segment.BULL
        if (r <= OUTER_BULL_RADIUS) return Segment.OUTER_BULL
        if (r > DOUBLE_OUTER) return Segment.MISS
        // Winkel im Uhrzeigersinn von oben (0° = 20)
        var deg = Math.toDegrees(atan2(x, y))
        if (deg < 0) deg += 360.0
        val index = floor(((deg + SECTOR_DEG / 2) % 360.0) / SECTOR_DEG).toInt() % 20
        val number = NUMBERS[index]
        val multiplier = when {
            r >= DOUBLE_INNER -> 2
            r >= TRIPLE_INNER && r <= TRIPLE_OUTER -> 3
            else -> 1
        }
        return Segment(number, multiplier)
    }

    /** Idealer Zielpunkt (Mitte des Segments) in mm. */
    fun centerOf(segment: Segment): Pair<Double, Double> {
        if (segment.isMiss) return 0.0 to -(BOARD_RADIUS + 20)
        if (segment.isBull) return 0.0 to (if (segment.multiplier == 2) 0.0 else (BULL_RADIUS + OUTER_BULL_RADIUS) / 2)
        val index = NUMBERS.indexOf(segment.number)
        val deg = index * SECTOR_DEG
        val radius = when (segment.multiplier) {
            2 -> (DOUBLE_INNER + DOUBLE_OUTER) / 2
            3 -> (TRIPLE_INNER + TRIPLE_OUTER) / 2
            else -> (OUTER_BULL_RADIUS + TRIPLE_INNER) / 2 + 20 // großes Single-Feld außen
        }
        val rad = Math.toRadians(deg)
        return radius * sin(rad) to radius * cos(rad)
    }

    /** Startwinkel (Grad, im Uhrzeigersinn ab oben) des Sektors mit Index i. */
    fun sectorStartDeg(index: Int): Double = index * SECTOR_DEG - SECTOR_DEG / 2

    private val RING_RADII = doubleArrayOf(BULL_RADIUS, OUTER_BULL_RADIUS, TRIPLE_INNER, TRIPLE_OUTER, DOUBLE_INNER, DOUBLE_OUTER)

    /** Abstand (mm) von (x, y) zur nächsten Segmentgrenze – Ringkante oder Sektordraht. */
    fun distanceToWire(x: Double, y: Double): Double {
        val r = sqrt(x * x + y * y)
        var d = RING_RADII.minOf { abs(r - it) }
        if (r > OUTER_BULL_RADIUS) {
            var deg = Math.toDegrees(atan2(x, y))
            if (deg < 0) deg += 360.0
            val offset = (deg + SECTOR_DEG / 2) % SECTOR_DEG
            d = min(d, r * Math.toRadians(min(offset, SECTOR_DEG - offset)))
        }
        return d
    }

    fun deg2rad(d: Double) = d * PI / 180.0
}
