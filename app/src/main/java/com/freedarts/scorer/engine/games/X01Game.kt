package com.freedarts.scorer.engine.games

import com.freedarts.scorer.engine.Checkout
import com.freedarts.scorer.engine.DartGame
import com.freedarts.scorer.engine.GameState
import com.freedarts.scorer.model.BullMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.InMode
import com.freedarts.scorer.model.MatchMode
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment

class X01Game(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) :
    DartGame(players, settings, seed) {

    private val scores = IntArray(players.size)
    private val opened = BooleanArray(players.size)
    private val legsWon = IntArray(players.size)
    private val setsWon = IntArray(players.size)
    private var legStarter = 0
    private var visitStart = 0
    private var visitPoints = 0
    private var legDarts = IntArray(players.size)
    private val legPoints = IntArray(players.size)
    private var legNumber = 1

    // Statistik
    private val first9Points = IntArray(players.size)
    private val first9Darts = IntArray(players.size)
    private val checkouts = IntArray(players.size)
    private val dartsAtDouble = IntArray(players.size)
    private val highestCheckout = IntArray(players.size)
    private val c60 = IntArray(players.size)
    private val c100 = IntArray(players.size)
    private val c140 = IntArray(players.size)
    private val c170 = IntArray(players.size)
    private val c180 = IntArray(players.size)

    init { resetState() }

    override fun resetState() {
        scores.fill(settings.baseScore)
        opened.fill(settings.inMode == InMode.STRAIGHT)
        legsWon.fill(0); setsWon.fill(0)
        legStarter = 0; visitStart = settings.baseScore; visitPoints = 0
        legDarts.fill(0); legPoints.fill(0); legNumber = 1
        first9Points.fill(0); first9Darts.fill(0); checkouts.fill(0); dartsAtDouble.fill(0); highestCheckout.fill(0)
        c60.fill(0); c100.fill(0); c140.fill(0); c170.fill(0); c180.fill(0)
    }

    private fun effective(seg: Segment): Segment =
        if (settings.bullMode == BullMode.B50_50 && seg == Segment.OUTER_BULL) Segment.BULL else seg

    private fun opensIn(seg: Segment): Boolean = when (settings.inMode) {
        InMode.STRAIGHT -> true
        InMode.DOUBLE -> seg.isDouble
        InMode.MASTER -> seg.isDouble || seg.isTriple
    }

    private fun validOut(seg: Segment): Boolean = when (settings.outMode) {
        OutMode.STRAIGHT -> true
        OutMode.DOUBLE -> seg.isDouble
        OutMode.MASTER -> seg.isDouble || seg.isTriple
    }

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) { visitStart = scores[p]; visitPoints = 0; visitClosed = false }
        val seg = effective(segment)
        legDarts[p]++
        if (legDarts[p] <= 9) first9Darts[p]++

        if (Checkout.isOnFinish(scores[p], settings.outMode) && opened[p]) dartsAtDouble[p]++

        if (!opened[p]) {
            if (opensIn(seg)) opened[p] = true else return false
        }
        val after = scores[p] - seg.score
        val minRemaining = if (settings.outMode == OutMode.STRAIGHT) 1 else 2
        if (after < 0 || (after in 1 until minRemaining) || (after == 0 && !validOut(seg))) {
            // Bust: Aufnahme zählt 0
            pointsScored[p] -= visitPoints
            legPoints[p] -= visitPoints
            if (legDarts[p] <= 9) first9Points[p] -= visitPoints
            visitPoints = 0
            scores[p] = visitStart
            banner = "Bust"
            addHistory(p, "Bust", scores[p].toString())
            visitClosed = true
            return true
        }
        scores[p] = after
        pointsScored[p] += seg.score
        legPoints[p] += seg.score
        visitPoints += seg.score
        if (legDarts[p] <= 9) first9Points[p] += seg.score

        if (after == 0) {
            checkouts[p]++
            if (visitPoints > highestCheckout[p]) highestCheckout[p] = visitPoints
            countVisit(p)
            addHistory(p, visitPoints.toString(), "0")
            visitClosed = true
            legWon(p)
            return true
        }
        return false
    }

    override fun onVisitEnd() {
        val p = current
        if (visit.isEmpty() || visitClosed) return
        countVisit(p)
        addHistory(p, visitPoints.toString(), scores[p].toString())
    }

    private fun countVisit(p: Int) {
        val v = visitPoints
        if (v >= 60) c60[p]++
        if (v >= 100) c100[p]++
        if (v >= 140) c140[p]++
        if (v >= 170) c170[p]++
        if (v == 180) c180[p]++
    }

    private fun legWon(p: Int) {
        legsWon[p]++
        if (settings.matchMode == MatchMode.SETS) {
            if (legsWon[p] >= settings.legs) {
                setsWon[p]++
                legsWon.fill(0)
                if (setsWon[p] >= settings.sets) { finish(p, "Game Shot"); return }
                banner = "Set gewonnen"
            } else banner = "Leg gewonnen"
        } else {
            if (legsWon[p] >= settings.legs) { finish(p, "Game Shot"); return }
            banner = "Leg gewonnen"
        }
        nextLeg()
    }

    private fun nextLeg() {
        scores.fill(settings.baseScore)
        opened.fill(settings.inMode == InMode.STRAIGHT)
        legDarts.fill(0); legPoints.fill(0)
        legNumber++
        legStarter = (legStarter + 1) % players.size
        players.indices.forEach { addHistory(it, "— Leg $legNumber —", "") }
        startNewLeg(legStarter)
    }

    private var visitClosed = false

    override fun onRoundCompleted() {
        if (settings.maxRounds > 0 && round > settings.maxRounds) {
            // Niedrigster Rest gewinnt das Leg; Gleichstand = niemand
            val min = scores.min()
            val winners = players.indices.filter { scores[it] == min }
            if (winners.size == 1) {
                legWon(winners.first())
            } else {
                banner = "Unentschieden – neues Leg"
                nextLeg()
            }
        }
    }

    override fun botTarget(): Segment {
        val p = current
        val s = scores[p]
        if (!opened[p]) return if (settings.inMode == InMode.MASTER) Segment.triple(20) else Segment.double(20)
        val left = 3 - visit.size
        Checkout.bestRoute(s, left, settings.outMode)?.let { return it.first() }
        Checkout.bestRoute(s, 3, settings.outMode)?.let { return it.first() }
        return Segment.triple(20)
    }

    override fun snapshot(): GameState {
        val left = 3 - visit.size
        val hint = if (finished) null else Checkout.describe(Checkout.bestRoute(scores[current], left.coerceAtLeast(1), settings.outMode))
        val headline = buildString {
            if (settings.matchMode == MatchMode.SETS) append("Set ${setsWon.sum() + 1} · ")
            append("Leg $legNumber · Runde $round")
            if (settings.maxRounds > 0) append(" / ${settings.maxRounds}")
        }
        return GameState(
            players = players.indices.map { i ->
                playerState(i, scores[i].toString(), detailFor(i), legs = legsWon[i], sets = setsWon[i])
            },
            currentPlayer = current,
            currentVisit = visit.toList(),
            round = round,
            finished = finished,
            winnerIndex = winner,
            banner = banner,
            checkoutHint = hint,
            headline = headline,
            showLegs = settings.legs > 1 || settings.matchMode == MatchMode.SETS,
            showSets = settings.matchMode == MatchMode.SETS,
        )
    }

    /** "Leg 64.2 / Match 71.8 · 9 Darts" wie in der Live-Ansicht. */
    private fun detailFor(i: Int): String {
        val leg = if (legDarts[i] == 0) 0.0 else legPoints[i].toDouble() / legDarts[i] * 3
        val match = if (dartsThrown[i] == 0) 0.0 else pointsScored[i].toDouble() / dartsThrown[i] * 3
        return "Leg %.1f / Match %.1f|%d".format(leg, match, legDarts[i])
    }

    override fun playerStats(index: Int): PlayerMatchStats = super.playerStats(index).copy(
        first9Points = first9Points[index],
        first9Darts = first9Darts[index],
        legsWon = legsWon[index],
        setsWon = setsWon[index],
        checkouts = checkouts[index],
        dartsAtDouble = dartsAtDouble[index],
        highestCheckout = highestCheckout[index],
        count60Plus = c60[index],
        count100Plus = c100[index],
        count140Plus = c140[index],
        count170Plus = c170[index],
        count180 = c180[index],
    )
}
