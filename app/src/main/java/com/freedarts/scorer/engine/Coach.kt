package com.freedarts.scorer.engine

import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.Segment

/** Trainings-Coach: Trefferquote je angepeiltem Ziel (aus [com.freedarts.scorer.model.ThrowRecord.aim]) und daraus die schwächsten Doppel. */
object Coach {
    const val MIN_ATTEMPTS = 5

    data class Target(val aim: Segment, val attempts: Int, val hits: Int) { val rate: Double get() = hits.toDouble() / attempts }

    /** Alle Ziele mit mindestens [MIN_ATTEMPTS] Versuchen, schwächste zuerst. */
    fun targets(matches: List<MatchRecord>, playerId: String): List<Target> {
        val attempts = HashMap<Segment, Int>(); val hits = HashMap<Segment, Int>()
        for (m in matches) {
            val idx = m.players.indexOfFirst { it.playerId == playerId }
            if (idx < 0) continue
            for (t in m.throws) {
                if (t.player != idx || t.leg == 0) continue
                val aim = t.aim?.let(Segment::parse) ?: continue
                if (aim.isMiss) continue
                attempts[aim] = (attempts[aim] ?: 0) + 1
                if (t.segment == aim) hits[aim] = (hits[aim] ?: 0) + 1
            }
        }
        return attempts.filter { it.value >= MIN_ATTEMPTS }.map { (aim, n) -> Target(aim, n, hits[aim] ?: 0) }.sortedWith(compareBy({ it.rate }, { -it.attempts }))
    }

    /** Die schwächsten Doppel (Checkout-Training zuerst). */
    fun weakestDoubles(targets: List<Target>, n: Int = 3): List<Target> = targets.filter { it.aim.multiplier == 2 && !it.aim.isBull }.take(n)
}
