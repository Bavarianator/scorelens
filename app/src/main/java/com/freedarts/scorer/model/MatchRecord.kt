package com.freedarts.scorer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ein einzelner Wurf im Wurfprotokoll (wie bei Autodarts: jeder Dart mit Leg, Set, Runde und Position).
 * Kurze JSON-Schlüssel, weil pro Match einige hundert Einträge entstehen.
 * [x]/[y]: Auftreffpunkt in Board-Millimetern (Mitte = 0/0, 20 oben), nur bei Kamera-/Board-Erkennung.
 * set/leg = 0: Wurf beim Ausbullen.
 */
@Serializable
data class ThrowRecord(
    @SerialName("p") val player: Int,
    @SerialName("s") val set: Int,
    @SerialName("l") val leg: Int,
    @SerialName("r") val round: Int,
    @SerialName("n") val number: Int,
    @SerialName("m") val multiplier: Int,
    @SerialName("x") val x: Float? = null,
    @SerialName("y") val y: Float? = null,
    @SerialName("b") val bust: Boolean = false,
    @SerialName("t") val at: Long = 0L,
    /** Angenommenes Ziel (Segmentname): das, was ein Bot in dieser Lage anvisieren würde – Basis der Zielhilfe. */
    @SerialName("a") val aim: String? = null,
) {
    val segment: Segment get() = Segment(number, multiplier)
    val score: Int get() = number * multiplier
}

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
    /** Höchste Aufnahme (X01). */
    val highestVisit: Int = 0,
    /** Anzahl Busts (X01, Gotcha, 121). */
    val busts: Int = 0,
    /** Wenigste / meiste Darts in einem gewonnenen Leg (0 = kein Leg gewonnen). */
    val bestLegDarts: Int = 0,
    val worstLegDarts: Int = 0,
    /** Cricket: Marks gesamt (für MPR). */
    val marks: Int = 0,
    /** Around the Clock / Segment Training: Treffer gesamt. */
    val hits: Int = 0,
) {
    val average3: Double get() = if (dartsThrown == 0) 0.0 else pointsScored.toDouble() / dartsThrown * 3
    val first9Average: Double get() = if (first9Darts == 0) 0.0 else first9Points.toDouble() / first9Darts * 3
    val checkoutRate: Double get() = if (dartsAtDouble == 0) 0.0 else checkouts.toDouble() / dartsAtDouble * 100
    /** Marks per Round (Cricket). */
    val mpr: Double get() = if (dartsThrown == 0) 0.0 else marks.toDouble() / dartsThrown * 3
    /** Trefferquote in Prozent (ATC, Segment Training). */
    val hitRate: Double get() = if (dartsThrown == 0) 0.0 else hits.toDouble() / dartsThrown * 100
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
    /** Vollständiges Wurfprotokoll (leer bei alten Einträgen). */
    val throws: List<ThrowRecord> = emptyList(),
) {
    val durationMillis: Long get() = finishedAt - startedAt

    /** Spieler-ID umbenennen (Online-Konto ↔ lokaler Profilspieler); das Wurfprotokoll nutzt Indizes und bleibt. */
    fun withPlayerId(from: String, to: String): MatchRecord = if (from == to) this else copy(
        winnerId = if (winnerId == from) to else winnerId,
        players = players.map { if (it.playerId == from) it.copy(playerId = to) else it },
    )

    /** Anzahl Darts pro Spieler und Leg (Set, Leg) → Liste je Spieler; nur aus dem Wurfprotokoll. */
    fun legs(): List<LegSummary> = throws.filter { it.leg > 0 }
        .groupBy { it.set to it.leg }
        .toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        .map { (key, list) ->
            LegSummary(
                set = key.first, leg = key.second,
                darts = players.indices.map { p -> list.count { it.player == p } },
                points = players.indices.map { p -> legPoints(list, p) },
                winner = list.lastOrNull()?.takeIf { !it.bust }?.player,
            )
        }

    /** Punkte eines Spielers in einem Leg: Bust-Aufnahmen zählen 0 (wie im Match-Average). */
    private fun legPoints(list: List<ThrowRecord>, p: Int): Int {
        var total = 0; var visitPts = 0; var n = 0
        for (t in list) {
            if (t.player != p) continue
            if (n == 0) visitPts = 0
            visitPts += t.score; n++
            if (t.bust) { visitPts = 0; n = 0; continue }
            if (n == 3) { total += visitPts; n = 0 }
        }
        return total + if (n > 0) visitPts else 0
    }
}

/** Zusammenfassung eines Legs aus dem Wurfprotokoll (X01). */
data class LegSummary(val set: Int, val leg: Int, val darts: List<Int>, val points: List<Int>, val winner: Int?) {
    fun average(p: Int): Double = if (darts[p] == 0) 0.0 else points[p].toDouble() / darts[p] * 3
}
