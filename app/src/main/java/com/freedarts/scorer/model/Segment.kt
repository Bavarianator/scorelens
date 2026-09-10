package com.freedarts.scorer.model

import kotlinx.serialization.Serializable

/**
 * Ein Segment des Dartboards.
 * number: 1..20, 25 für Bull, 0 für Miss.
 * multiplier: 1 = Single, 2 = Double, 3 = Triple. Bull: 25×1 = Single Bull (25), 25×2 = Bullseye (50).
 */
@Serializable
data class Segment(val number: Int, val multiplier: Int) {

    val score: Int get() = number * multiplier
    val isMiss: Boolean get() = number == 0
    val isBull: Boolean get() = number == 25
    val isBullseye: Boolean get() = number == 25 && multiplier == 2
    val isDouble: Boolean get() = multiplier == 2 && !isMiss
    val isTriple: Boolean get() = multiplier == 3

    /** Kurzname wie bei Autodarts: S20, D16, T20, 25, BULL, Miss */
    val name: String
        get() = when {
            isMiss -> "Miss"
            isBull && multiplier == 2 -> "BULL"
            isBull -> "25"
            multiplier == 2 -> "D$number"
            multiplier == 3 -> "T$number"
            else -> "S$number"
        }

    companion object {
        val MISS = Segment(0, 1)
        val BULL = Segment(25, 2)
        val OUTER_BULL = Segment(25, 1)

        fun single(n: Int) = Segment(n, 1)
        fun double(n: Int) = Segment(n, 2)
        fun triple(n: Int) = Segment(n, 3)

        /** Alle werfbaren Segmente (ohne Miss). */
        val ALL: List<Segment> by lazy {
            buildList {
                for (n in 1..20) { add(single(n)); add(double(n)); add(triple(n)) }
                add(OUTER_BULL); add(BULL)
            }
        }

        /** Parst Namen wie "T20", "D16", "S5", "25", "BULL", "Miss", "M". */
        fun parse(name: String): Segment? {
            val s = name.trim().uppercase()
            return when {
                s == "MISS" || s == "M" || s == "OUT" || s == "OUTSIDE" || s == "0" -> MISS
                s == "BULL" || s == "DBULL" || s == "D25" || s == "50" -> BULL
                s == "25" || s == "SBULL" || s == "S25" -> OUTER_BULL
                s.length >= 2 && s[0] in "SDT" -> {
                    val n = s.substring(1).toIntOrNull() ?: return null
                    if (n !in 1..20) return null
                    Segment(n, when (s[0]) { 'S' -> 1; 'D' -> 2; else -> 3 })
                }
                else -> null
            }
        }
    }
}
