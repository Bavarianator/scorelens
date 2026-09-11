package com.freedarts.scorer.model

import kotlinx.serialization.Serializable

@Serializable
enum class TournamentMode(val title: String) { KO("K.-o.-System"), ROUND_ROBIN("Jeder gegen jeden") }

/** Ein Turnierspiel; [a]/[b] sind Indizes in [Tournament.players], null = noch offen (K.-o.) oder Freilos. */
@Serializable
data class TournamentMatch(val round: Int, val a: Int?, val b: Int?, val winner: Int? = null, val legsA: Int = 0, val legsB: Int = 0) {
    val open: Boolean get() = winner == null && a != null && b != null
}

/**
 * Lokales Turnier (Vereins- oder Kneipenabend): Spieler und Einstellungen aus der Lobby, jedes Spiel ist ein normales
 * Match. K.-o. mit Freilosen auf die nächste Zweierpotenz, Jeder gegen jeden nach dem Rundenverfahren.
 */
@Serializable
data class Tournament(
    val mode: TournamentMode,
    val players: List<Player>,
    val settings: GameSettings,
    val matches: List<TournamentMatch>,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val rounds: Int get() = matches.maxOf { it.round }
    val finished: Boolean get() = matches.all { it.winner != null }
    val champion: Int? get() = if (!finished) null else if (mode == TournamentMode.KO) matches.last().winner else standings().first().player

    data class Standing(val player: Int, val wins: Int, val played: Int, val legDiff: Int)

    /** Tabelle (Jeder gegen jeden): Siege, dann Leg-Differenz. */
    fun standings(): List<Standing> = players.indices.map { p ->
        val mine = matches.filter { it.winner != null && (it.a == p || it.b == p) }
        Standing(p, mine.count { it.winner == p }, mine.size, mine.sumOf { if (it.a == p) it.legsA - it.legsB else it.legsB - it.legsA })
    }.sortedWith(compareByDescending<Standing> { it.wins }.thenByDescending { it.legDiff })

    /** Ergebnis eintragen; im K.-o. rücken die Sieger in die nächste Runde. */
    fun withResult(index: Int, winner: Int, legsA: Int, legsB: Int): Tournament {
        val ms = matches.toMutableList()
        ms[index] = ms[index].copy(winner = winner, legsA = legsA, legsB = legsB)
        if (mode == TournamentMode.KO) {
            val byRound = ms.indices.groupBy { ms[it].round }
            for (r in 2..rounds) byRound.getValue(r).forEachIndexed { j, i ->
                val prev = byRound.getValue(r - 1)
                ms[i] = ms[i].copy(a = ms[prev[2 * j]].winner, b = ms[prev[2 * j + 1]].winner)
            }
        }
        return copy(matches = ms)
    }

    companion object {
        fun create(mode: TournamentMode, players: List<Player>, settings: GameSettings): Tournament {
            require(players.size >= 2) { "Mindestens zwei Spieler" }
            val n = players.size
            val matches = buildList {
                if (mode == TournamentMode.KO) {
                    var size = 2
                    while (size < n) size *= 2
                    for (i in 0 until size / 2) {
                        val a = i; val b = (size - 1 - i).takeIf { it < n }
                        add(TournamentMatch(1, a, b, winner = if (b == null) a else null))
                    }
                    var count = size / 4; var round = 2
                    while (count >= 1) { repeat(count) { add(TournamentMatch(round, null, null)) }; count /= 2; round++ }
                } else {
                    // Rundenverfahren: ein Spieler bleibt stehen, die anderen rotieren; bei ungerader Zahl ein Freilos (-1)
                    val ring = (0 until n).toMutableList().apply { if (n % 2 == 1) add(-1) }
                    for (r in 1 until ring.size) {
                        for (k in 0 until ring.size / 2) {
                            val a = ring[k]; val b = ring[ring.size - 1 - k]
                            if (a >= 0 && b >= 0) add(TournamentMatch(r, a, b))
                        }
                        ring.add(1, ring.removeAt(ring.size - 1))
                    }
                }
            }
            return Tournament(mode, players, settings, matches)
        }
    }
}
