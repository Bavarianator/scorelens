package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.Segment
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Zielhilfe: Aus den eigenen Lens-Würfen wird die Streuung geschätzt und daraus für jeden Zielpunkt die erwartete
 * Punktzahl pro Dart – wer breit streut, holt mit T19 oder dem Bull-Bereich mehr als mit T20.
 */
object AimAdvisor {
    const val MIN_DARTS = 30
    /** Rayleigh-Verteilung: Median des Radialabstands = σ · sqrt(2 ln 2). Median statt Mittel, damit Checkout-Darts nicht verzerren. */
    private const val RAYLEIGH_MEDIAN = 1.17741
    private const val SAMPLES = 600

    data class Scatter(val sigmaMm: Double, val darts: Int)
    data class Advice(val best: Segment, val expected: Map<Segment, Double>)

    // ponytail: eine runde Streuung; Ellipse/Richtung erst, wenn jemand horizontale vs. vertikale Streuung sehen will
    /** Streuung eines Spielers aus allen Würfen mit Auftreffpunkt und (angenommenem) Ziel; null unter [MIN_DARTS]. */
    fun scatter(matches: List<MatchRecord>, playerId: String): Scatter? {
        val radial = ArrayList<Double>()
        for (m in matches) {
            val idx = m.players.indexOfFirst { it.playerId == playerId }
            if (idx < 0) continue
            for (t in m.throws) {
                if (t.player != idx || t.leg == 0 || t.x == null || t.y == null) continue
                // Alte Protokolle ohne Ziel: in X01 wird T20 angenommen
                val aim = t.aim?.let(Segment::parse) ?: (if (m.mode == GameMode.X01) Segment.triple(20) else null) ?: continue
                if (aim.isMiss) continue
                val (cx, cy) = Board.centerOf(aim)
                radial.add(hypot(t.x - cx, t.y - cy))
            }
        }
        if (radial.size < MIN_DARTS) return null
        radial.sort()
        val n = radial.size
        val median = if (n % 2 == 1) radial[n / 2] else (radial[n / 2 - 1] + radial[n / 2]) / 2
        return Scatter(median / RAYLEIGH_MEDIAN, n)
    }

    /** Erwartete Punkte je Zielpunkt (Triple-Mitten, große Single-Felder, Bull) bei Streuung [sigmaMm]; feste Stichprobe, damit das Ergebnis stabil ist. */
    fun advise(sigmaMm: Double): Advice {
        val random = Random(42)
        val offsets = List(SAMPLES) { Bot.gaussianPair(random) }
        val candidates = Board.NUMBERS.flatMap { listOf(Segment.triple(it), Segment.single(it)) } + Segment.BULL
        val expected = candidates.associateWith { c ->
            val (cx, cy) = Board.centerOf(c)
            offsets.sumOf { (gx, gy) -> Board.segmentAt(cx + gx * sigmaMm, cy + gy * sigmaMm).score }.toDouble() / SAMPLES
        }
        return Advice(expected.maxBy { it.value }.key, expected)
    }
}
