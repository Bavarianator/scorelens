package com.freedarts.scorer.model

import kotlinx.serialization.Serializable

/** Statistik eines Spielers für ein abgeschlossenes Spiel. */
@Serializable
data class PlayerMatchStats(
    val playerId: String,
    val playerName: String,
    val won: Boolean,
    val finalScore: String,
    val dartsThrown: Int,
    val pointsScored: Int,
    val first9Points: Int = 0,
    val first9Darts: Int = 0,
    val legsWon: Int = 0,
    val setsWon: Int = 0,
    val checkouts: Int = 0,
    val dartsAtDouble: Int = 0,
    val highestCheckout: Int = 0,
    val count60Plus: Int = 0,
    val count100Plus: Int = 0,
    val count140Plus: Int = 0,
    val count170Plus: Int = 0,
    val count180: Int = 0,
) {
    val average3: Double get() = if (dartsThrown == 0) 0.0 else pointsScored.toDouble() / dartsThrown * 3
    val first9Average: Double get() = if (first9Darts == 0) 0.0 else first9Points.toDouble() / first9Darts * 3
    val checkoutRate: Double get() = if (dartsAtDouble == 0) 0.0 else checkouts.toDouble() / dartsAtDouble * 100
}

@Serializable
data class MatchRecord(
    val id: String,
    val mode: GameMode,
    val settings: GameSettings,
    val startedAt: Long,
    val finishedAt: Long,
    val winnerId: String?,
    val players: List<PlayerMatchStats>,
) {
    val durationMillis: Long get() = finishedAt - startedAt
}
