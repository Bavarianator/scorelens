package com.freedarts.scorer.engine

import com.freedarts.scorer.model.BullMode
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.InMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.ThrowRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Gewertete Punkte im Wurfprotokoll: das rohe Segment zeigt nicht, dass Bull 50/50 die 25 als 50 zählt und
 * dass die Darts vor dem Eröffnungs-Dart (Double-In) nichts zählen.
 */
class CountedScoresTest {

    private fun record(settings: GameSettings, throws: List<ThrowRecord>) = MatchRecord(
        id = "m", mode = GameMode.X01, settings = settings, startedAt = 0, finishedAt = 1, winnerId = "a",
        players = listOf(
            PlayerMatchStats("a", "Anna", true, "0", 3, 0),
            PlayerMatchStats("b", "Ben", false, "0", 3, 0),
        ),
        throws = throws,
    )

    @Test fun bull50CountsAsFifty() {
        val throws = listOf(ThrowRecord(0, 1, 1, 1, 25, 1), ThrowRecord(0, 1, 1, 1, 25, 2), ThrowRecord(0, 1, 1, 1, 20, 1))
        val plain = Statistics.countedScores(GameSettings(mode = GameMode.X01), 2, throws)
        assertEquals(listOf(25, 50, 20), plain.toList())
        val fifty = Statistics.countedScores(GameSettings(mode = GameMode.X01, bullMode = BullMode.B50_50), 2, throws)
        assertEquals(listOf(50, 50, 20), fifty.toList())
    }

    @Test fun dartsBeforeDoubleInCountNothing() {
        val settings = GameSettings(mode = GameMode.X01, inMode = InMode.DOUBLE)
        val throws = listOf(
            ThrowRecord(0, 1, 1, 1, 20, 1), // zählt nicht, noch nicht eröffnet
            ThrowRecord(0, 1, 1, 1, 20, 2), // D20 eröffnet
            ThrowRecord(0, 1, 1, 1, 20, 3), // ab jetzt zählt alles
            ThrowRecord(1, 1, 1, 1, 19, 1), // anderer Spieler, eigene Eröffnung
        )
        assertEquals(listOf(0, 40, 60, 0), Statistics.countedScores(settings, 2, throws).toList())
    }

    @Test fun visitDistributionUsesCountedScores() {
        val settings = GameSettings(mode = GameMode.X01, bullMode = BullMode.B50_50)
        // eine Aufnahme: 25 (zählt 50) + 25 (zählt 50) + T20
        val throws = listOf(ThrowRecord(0, 1, 1, 1, 25, 1), ThrowRecord(0, 1, 1, 1, 25, 1), ThrowRecord(0, 1, 1, 1, 20, 3))
        assertEquals(listOf(160), Statistics.visits(listOf(record(settings, throws)), "a"))
    }

    @Test fun bustVisitCountsAsZero() {
        val throws = listOf(ThrowRecord(0, 1, 1, 1, 20, 3), ThrowRecord(0, 1, 1, 1, 20, 3, bust = true))
        assertEquals(listOf(0), Statistics.visits(listOf(record(GameSettings(mode = GameMode.X01), throws)), "a"))
    }

    /** Sperre der Gesamtscore-Eingabe: 179 und Co. lassen sich mit drei Darts nicht werfen. */
    @Test fun impossibleTotalsHaveNoRoute() {
        listOf(180, 170, 141, 26, 3).forEach { assertNotNull("$it sollte werfbar sein", Checkout.bestRoute(it, 3, OutMode.STRAIGHT)) }
        listOf(179, 178, 176, 175, 173, 172, 169, 166, 163).forEach { assertNull("$it ist nicht werfbar", Checkout.bestRoute(it, 3, OutMode.STRAIGHT)) }
    }
}
