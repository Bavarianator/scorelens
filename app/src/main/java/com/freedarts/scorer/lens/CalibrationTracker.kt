package com.freedarts.scorer.lens

import kotlin.math.hypot

/**
 * Glättet die Kalibrierpunkte über mehrere Fits (zeitlicher Median gegen Zittern) und erkennt,
 * wenn die Kamera bewegt wurde.
 */
class CalibrationTracker(private val frameWidth: Int, private val frameHeight: Int) {

    companion object {
        const val HISTORY = 5
        /** Fits gelten als übereinstimmend, wenn jeder Punkt höchstens so weit (px) vom aktuellen abweicht. */
        const val JITTER_PX = 4.0
        /** Verschiebung (px) eines Kalibrierpunkts, ab der die Kamera als bewegt gilt. */
        const val MOVE_PX = 3.0
    }

    private val history = ArrayList<List<Pair<Double, Double>>>()
    private var driftCount = 0

    fun clear() { history.clear(); driftCount = 0 }

    /** Median der letzten Fits, sobald sie mit [points] übereinstimmen; sonst null. */
    fun stable(points: List<Pair<Double, Double>>): List<Pair<Double, Double>>? {
        history.add(points)
        if (history.size > HISTORY) history.removeAt(0)
        val consistent = history.size >= 2 && history.all { f -> f.indices.all { i -> hypot(f[i].first - points[i].first, f[i].second - points[i].second) < JITTER_PX } }
        if (!consistent) return null
        return points.indices.map { i -> median(history.map { it[i] }) }
    }

    fun normalized(points: List<Pair<Double, Double>>): List<Float> =
        points.flatMap { listOf((it.first / frameWidth).toFloat(), (it.second / frameHeight).toFloat()) }

    /** true, wenn [norm] zum zweiten Mal in Folge deutlich von der aktiven Kalibrierung [current] abweicht. */
    fun moved(current: List<Float>, norm: List<Float>): Boolean {
        val moved = current.size == norm.size && (0 until norm.size / 2).any { i ->
            hypot((current[i * 2] - norm[i * 2]) * frameWidth, (current[i * 2 + 1] - norm[i * 2 + 1]) * frameHeight) > MOVE_PX
        }
        driftCount = if (moved) driftCount + 1 else 0
        if (driftCount < 2) return false
        driftCount = 0
        return true
    }
}
