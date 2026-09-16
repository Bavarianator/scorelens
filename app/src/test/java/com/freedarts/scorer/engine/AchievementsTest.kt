package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.PlayerMatchStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    private fun match(i: Int, won: Boolean, mode: GameMode = GameMode.X01, me: PlayerMatchStats = PlayerMatchStats("me", "Me", won, "0", 30, 1500)) =
        MatchRecord("m$i", mode, GameSettings(mode = mode), i * 1000L, i * 1000L + 500, if (won) "me" else "op",
            listOf(me.copy(won = won), PlayerMatchStats("op", "Op", !won, "40", 30, 300)))

    @Test fun thresholds() {
        val none = Achievements.of(emptyList(), "me")
        assertTrue(none.none { it.done })
        val ms = (1..6).map { match(it, won = true) } +
            match(7, true, me = PlayerMatchStats("me", "Me", true, "0", 30, 1500, count180 = 1, highestCheckout = 170, bestLegDarts = 12))
        val a = Achievements.of(ms, "me").associateBy { it.title }
        assertTrue(a.getValue("Erstes Match").done)
        assertTrue(a.getValue("Serie").done)
        assertTrue(a.getValue("180!").done)
        assertTrue(a.getValue("Big Fish").done)
        assertTrue(a.getValue("Ton-Finish").done)
        assertTrue(a.getValue("15-Darter").done)
        assertFalse(a.getValue("9-Darter").done)
        assertTrue(a.getValue("Ø 50").done)   // 1500/30*3 = 150
        assertEquals(7, a.getValue("Sieger").progress)
        assertFalse(a.getValue("Sieger").done)
        // Gegner: keine Siege, nichts freigeschaltet außer "Erstes Match"
        assertEquals(listOf("Erstes Match"), Achievements.of(ms, "op").filter { it.done }.map { it.title })
    }
}
