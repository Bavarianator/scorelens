package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord

/**
 * Erfolge (Badges) eines Spielers, rein aus dem Match-Verlauf abgeleitet – nichts wird gespeichert.
 * Autodarts hat Badges/Streaks erst angekündigt; hier sind sie eine feste Liste von Schwellen.
 */
object Achievements {

    /** [tier] 1 = Bronze, 2 = Silber, 3 = Gold; [icon] ein Emoji für die Medaille. */
    data class Achievement(val title: String, val description: String, val progress: Int, val goal: Int, val icon: String, val tier: Int) {
        val done: Boolean get() = progress >= goal
    }

    fun of(matches: List<MatchRecord>, playerId: String): List<Achievement> {
        val mine = matches.filter { m -> m.players.any { it.playerId == playerId } }.sortedBy { it.finishedAt }
        val stats = mine.map { m -> m.players.first { it.playerId == playerId } }
        val x01 = mine.indices.filter { mine[it].mode == GameMode.X01 }.map { stats[it] }
        val wins = stats.count { it.won }
        val best = Statistics.streaks(mine, playerId).longestWin
        val highFinish = x01.maxOfOrNull { it.highestCheckout } ?: 0
        val bestLeg = x01.filter { it.bestLegDarts > 0 }.minOfOrNull { it.bestLegDarts } ?: 99
        // Match-Average nur bei mindestens 15 Darts, sonst zählt ein Glücks-Leg
        val bestAvg = x01.filter { it.dartsThrown >= 15 }.maxOfOrNull { it.average3 } ?: 0.0
        fun n(v: Int, goal: Int) = v.coerceAtMost(goal)
        return listOf(
            Achievement("Erstes Match", "Ein Spiel abgeschlossen", n(mine.size, 1), 1, "🎯", 1),
            Achievement("Stammgast", "50 Spiele", n(mine.size, 50), 50, "🍺", 2),
            Achievement("Marathon", "250 Spiele", n(mine.size, 250), 250, "🏃", 3),
            Achievement("Sieger", "10 Siege", n(wins, 10), 10, "🏆", 1),
            Achievement("Dominator", "100 Siege", n(wins, 100), 100, "👑", 3),
            Achievement("Serie", "5 Siege in Folge", n(best, 5), 5, "🔥", 2),
            Achievement("180!", "Erstes Maximum", n(x01.sumOf { it.count180 }, 1), 1, "💯", 2),
            Achievement("180-Sammler", "25 × 180", n(x01.sumOf { it.count180 }, 25), 25, "💎", 3),
            Achievement("Ton-Finish", "Checkout ab 100", if (highFinish >= 100) 1 else 0, 1, "🎰", 2),
            Achievement("Big Fish", "170er Finish", if (highFinish >= 170) 1 else 0, 1, "🐟", 3),
            Achievement("Ø 50", "Match-Average über 50", if (bestAvg >= 50) 1 else 0, 1, "📈", 1),
            Achievement("Ø 70", "Match-Average über 70", if (bestAvg >= 70) 1 else 0, 1, "🚀", 3),
            Achievement("15-Darter", "Leg in 15 Darts oder weniger", if (bestLeg <= 15) 1 else 0, 1, "⚡", 2),
            Achievement("9-Darter", "Das perfekte Leg", if (bestLeg <= 9) 1 else 0, 1, "🦄", 3),
            Achievement("Allrounder", "5 verschiedene Modi gespielt", n(mine.map { it.mode }.distinct().size, 5), 5, "🧩", 1),
            Achievement("Fleißig", "25 Trainingsspiele", n(mine.count { it.mode.category == GameMode.Category.PRACTICE }, 25), 25, "🎓", 2),
        )
    }
}
