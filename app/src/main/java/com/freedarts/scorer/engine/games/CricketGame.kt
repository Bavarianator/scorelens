package com.freedarts.scorer.engine.games

import com.freedarts.scorer.engine.DartGame
import com.freedarts.scorer.engine.GameState
import com.freedarts.scorer.model.CricketVariant
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment

class CricketGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) :
    DartGame(players, settings, seed) {

    val targets: List<Int> = when (settings.cricketVariant) {
        CricketVariant.TACTICS -> (20 downTo 10).toList() + 25
        else -> listOf(20, 19, 18, 17, 16, 15, 25)
    }
    private val cutThroat get() = settings.cricketVariant == CricketVariant.CUT_THROAT

    private val marks = Array(players.size) { HashMap<Int, Int>() }
    private val points = IntArray(players.size)
    private var visitPoints = 0

    init { resetState() }

    override fun resetState() {
        marks.forEach { m -> m.clear(); targets.forEach { t -> m[t] = 0 } }
        points.fill(0); visitPoints = 0
    }

    private fun closedByAllOthers(p: Int, n: Int) = players.indices.all { it == p || (marks[it][n] ?: 0) >= 3 }

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) visitPoints = 0
        val n = segment.number
        if (n !in targets) return false
        var hits = segment.multiplier
        val have = marks[p][n] ?: 0
        val toClose = (3 - have).coerceAtLeast(0)
        val used = minOf(hits, toClose)
        marks[p][n] = have + used
        hits -= used
        if (hits > 0 && !closedByAllOthers(p, n)) {
            val gained = hits * n
            if (cutThroat) {
                players.indices.filter { it != p && (marks[it][n] ?: 0) < 3 }.forEach { points[it] += gained }
            } else {
                points[p] += gained
            }
            visitPoints += gained
            pointsScored[p] += gained
        }
        if (allClosed(p) && leads(p)) {
            addHistory(p, visitPoints.toString(), points[p].toString())
            finish(p, "Game Shot")
            return true
        }
        return false
    }

    private fun allClosed(p: Int) = targets.all { (marks[p][it] ?: 0) >= 3 }
    private fun leads(p: Int) = players.indices.all { it == p || (if (cutThroat) points[p] <= points[it] else points[p] >= points[it]) }

    override fun onVisitEnd() {
        if (visit.isEmpty() || finished) return
        addHistory(current, visitPoints.toString(), points[current].toString())
    }

    override fun onRoundCompleted() {
        if (settings.maxRounds > 0 && round > settings.maxRounds) {
            val best = if (cutThroat) points.min() else points.max()
            val w = players.indices.filter { points[it] == best }
            finish(if (w.size == 1) w.first() else null, if (w.size == 1) "Game Shot" else "Unentschieden")
        }
    }

    override fun botTarget(): Segment {
        val p = current
        // Erst eigene Zahlen schließen (höchste zuerst), dann punkten auf offenen Zahlen
        targets.firstOrNull { (marks[p][it] ?: 0) < 3 && !(closedByAllOthers(p, it) && (marks[p][it] ?: 0) >= 3) }?.let {
            return if (it == 25) Segment.BULL else Segment.triple(it)
        }
        targets.firstOrNull { !closedByAllOthers(p, it) }?.let { return if (it == 25) Segment.BULL else Segment.triple(it) }
        return Segment.triple(20)
    }

    override fun buildSnapshot(): GameState = GameState(
        players = players.indices.map { i ->
            playerState(i, points[i].toString(), mpr(i), marks = marks[i].toMap())
        },
        currentPlayer = current,
        currentVisit = visit.toList(),
        round = round,
        finished = finished,
        winnerIndex = winner,
        banner = banner,
        headline = "Runde $round" + if (settings.maxRounds > 0) " / ${settings.maxRounds}" else "",
        cricketTargets = targets,
    )

    override fun playerStats(index: Int): PlayerMatchStats =
        super.playerStats(index).copy(marks = marks[index].values.sum())

    /** Marks per Round. */
    private fun mpr(i: Int): String {
        val d = dartsThrown[i]
        if (d == 0) return "MPR –"
        val m = marks[i].values.sum()
        return "MPR %.2f".format(m.toDouble() / d * 3)
    }
}
