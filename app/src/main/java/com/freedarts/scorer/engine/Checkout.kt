package com.freedarts.scorer.engine

import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Segment

/**
 * Berechnet Checkout-Vorschläge (wie der Checkout-Guide bei Autodarts).
 */
object Checkout {

    /** Bevorzugte Finish-Doubles (Rang = Position). */
    private val PREFERRED_DOUBLES = listOf(20, 16, 8, 4, 2, 1, 18, 12, 10, 14, 6, 32, 24, 5, 3, 7, 9, 11, 13, 15, 17, 19)

    private val cache = HashMap<Triple<Int, Int, OutMode>, List<Segment>?>()

    fun maxFinish(outMode: OutMode): Int = when (outMode) {
        OutMode.DOUBLE, OutMode.MASTER -> 170
        OutMode.STRAIGHT -> 180
    }

    /** Ist der Rest mit [darts] Darts überhaupt beendbar? */
    fun isFinishable(score: Int, darts: Int, outMode: OutMode): Boolean =
        bestRoute(score, darts, outMode) != null

    /** Ist ein "Wurf aufs Double" möglich (für Checkout-Statistik)? */
    fun isOnFinish(score: Int, outMode: OutMode): Boolean = when (outMode) {
        OutMode.DOUBLE -> score == 50 || (score in 2..40 && score % 2 == 0)
        OutMode.MASTER -> score == 50 || score == 25 || (score in 2..40 && score % 2 == 0) || (score in 3..60 && score % 3 == 0)
        OutMode.STRAIGHT -> score in 1..60 || score == 25 || score == 50
    }

    private fun isValidLast(seg: Segment, outMode: OutMode): Boolean = when (outMode) {
        OutMode.STRAIGHT -> true
        OutMode.DOUBLE -> seg.isDouble
        OutMode.MASTER -> seg.isDouble || seg.isTriple
    }

    private fun lastRank(seg: Segment): Int = when {
        seg.isBullseye -> 6
        seg.isDouble -> PREFERRED_DOUBLES.indexOf(seg.number).let { if (it < 0) 30 else it }
        seg.isTriple -> 12
        seg.isBull -> 12
        else -> 20 + (20 - seg.number)
    }

    /** Beste Route (Segmentliste) für [score] mit maximal [darts] Darts, oder null wenn unmöglich. */
    fun bestRoute(score: Int, darts: Int, outMode: OutMode): List<Segment>? {
        if (score <= 0 || darts <= 0 || score > maxFinish(outMode)) return null
        val key = Triple(score, darts, outMode)
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null

        var best: List<Segment>? = null
        var bestCost = Int.MAX_VALUE

        fun consider(route: List<Segment>) {
            var cost = route.size * 1000 + lastRank(route.last()) * 10
            // Setup-Darts: hohe Tripel bevorzugen, Bull als Setup vermeiden
            for (i in 0 until route.size - 1) {
                val s = route[i]
                cost += when {
                    s.isTriple -> (20 - s.number)
                    s.isBull -> 25
                    s.isDouble -> 8
                    else -> 4 + (20 - s.number) / 4
                }
            }
            if (cost < bestCost) { bestCost = cost; best = route }
        }

        fun search(remaining: Int, left: Int, route: List<Segment>) {
            if (left == 0) return
            for (seg in Segment.ALL) {
                val after = remaining - seg.score
                if (after == 0) {
                    if (isValidLast(seg, outMode)) consider(route + seg)
                } else if (after > 0 && left > 1) {
                    val minLast = if (outMode == OutMode.STRAIGHT) 1 else 2
                    if (after >= minLast && after <= maxFinish(outMode)) {
                        // Bei Double-Out: Rest muss weiterhin machbar sein
                        search(after, left - 1, route + seg)
                    }
                }
            }
        }
        // kürzeste Routen zuerst suchen
        for (d in 1..darts) {
            search(score, d, emptyList())
            if (best != null) break
        }
        cache[key] = best
        return best
    }

    fun describe(route: List<Segment>?): String? = route?.joinToString("  ") { it.name }
}
