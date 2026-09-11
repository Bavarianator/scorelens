package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Tournament
import com.freedarts.scorer.model.TournamentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentTest {
    private fun players(n: Int) = (1..n).map { Player(name = "P$it") }

    @Test fun `KO mit fuenf Spielern - Freilose, Aufruecken, Sieger`() {
        var t = Tournament.create(TournamentMode.KO, players(5), GameSettings())
        assertEquals(7, t.matches.size)                       // 4 + 2 + 1
        assertEquals(3, t.matches.count { it.round == 1 && it.winner != null }) // drei Freilose
        assertEquals(1, t.matches.count { it.open })
        val open = t.matches.indexOfFirst { it.open }
        t = t.withResult(open, t.matches[open].a!!, 3, 1)
        assertEquals(2, t.matches.count { it.open })          // Halbfinale gefüllt
        while (!t.finished) { val i = t.matches.indexOfFirst { it.open }; t = t.withResult(i, t.matches[i].b!!, 3, 0) }
        assertEquals(t.matches.last().winner, t.champion)
    }

    @Test fun `Jeder gegen jeden mit vier Spielern - sechs Spiele, Tabelle`() {
        var t = Tournament.create(TournamentMode.ROUND_ROBIN, players(4), GameSettings())
        assertEquals(6, t.matches.size)
        assertEquals(3, t.rounds)
        (0 until 4).forEach { p -> assertEquals(3, t.matches.count { it.a == p || it.b == p }) }
        assertNull(t.champion)
        t.matches.indices.forEach { i -> t = t.withResult(i, minOf(t.matches[i].a!!, t.matches[i].b!!), 3, 2) }
        assertTrue(t.finished)
        assertEquals(0, t.champion)
        assertEquals(listOf(3, 2, 1, 0), t.standings().map { it.wins })
        assertFalse(Tournament.create(TournamentMode.ROUND_ROBIN, players(5), GameSettings()).matches.any { it.a == it.b })
    }
}
