package com.freedarts.scorer.engine

import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment

/** Eintrag auf der Kreidetafel (Chalkboard). */
data class VisitEntry(val label: String, val remaining: String)

data class PlayerState(
    val player: Player,
    /** Hauptanzeige, z.B. Restpunkte. */
    val score: String,
    /** Zweite Zeile, z.B. Average oder aktuelles Ziel. */
    val detail: String,
    val legs: Int = 0,
    val sets: Int = 0,
    /** Cricket: Marks pro Zahl (0..3). */
    val marks: Map<Int, Int>? = null,
    /** Killer: Leben. */
    val lives: Int? = null,
    val isKiller: Boolean = false,
    val isOut: Boolean = false,
    val history: List<VisitEntry> = emptyList(),
)

data class GameState(
    val players: List<PlayerState>,
    val currentPlayer: Int,
    val currentVisit: List<Segment>,
    val round: Int,
    val finished: Boolean,
    val winnerIndex: Int?,
    /** Einblendung wie "Game Shot", "Bust", "Shanghai!". */
    val banner: String? = null,
    /** Vorschlag des Checkout-Guides für den aktiven Spieler. */
    val checkoutHint: String? = null,
    /** Kopfzeile, z.B. "Runde 3 / 8" oder "Ziel: 15". */
    val headline: String,
    /** Zahlen für die Cricket-Tafel. */
    val cricketTargets: List<Int>? = null,
    val showLegs: Boolean = false,
    val showSets: Boolean = false,
)
