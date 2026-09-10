package com.freedarts.scorer.engine.games

import com.freedarts.scorer.engine.DartGame
import com.freedarts.scorer.engine.GameState
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment

/**
 * Bermuda: Ziele 12, 13, 14, Double, 15, 16, 17, Triple, 18, 19, 20, Bull.
 * Treffer zählen Punkte, kein Treffer in der Runde halbiert den Score.
 */
class BermudaGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    private val targets = listOf("12", "13", "14", "Double", "15", "16", "17", "Triple", "18", "19", "20", "Bull")
    init { resetState() }
    override val totalRounds: Int get() = targets.size
    private val target get() = targets[(round - 1).coerceIn(0, targets.size - 1)]

    private fun hit(seg: Segment, t: String): Boolean = when (t) {
        "Double" -> seg.isDouble && !seg.isBull
        "Triple" -> seg.isTriple
        "Bull" -> seg.isBull
        else -> seg.number == t.toInt()
    }

    override fun onDart(segment: Segment): Boolean {
        if (hit(segment, target)) addPoints(current, segment.score)
        return false
    }

    override fun onVisitEnd() {
        if (visit.isEmpty() || finished) return
        val p = current
        if (visitPoints == 0) {
            val half = score[p] / 2
            val loss = score[p] - half
            score[p] = half
            addHistory(p, "½ (−$loss)", score[p].toString())
        } else addHistory(p, "+$visitPoints", score[p].toString())
        visitPoints = 0
    }

    override fun botTarget(): Segment = when (target) {
        "Double" -> Segment.double(20)
        "Triple" -> Segment.triple(20)
        "Bull" -> Segment.BULL
        else -> Segment.triple(target.toInt())
    }

    override fun buildSnapshot(): GameState = super.buildSnapshot().copy(headline = "Ziel: $target · ${round.coerceAtMost(totalRounds)} / $totalRounds")
}

/**
 * Gotcha: Von 0 exakt auf das Ziel (z.B. 301). Überwerfen = Bust.
 * Landet man genau auf dem Score eines Gegners, wird dieser auf 0 zurückgesetzt.
 */
class GotchaGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : DartGame(players, settings, seed) {
    private val score = IntArray(players.size)
    private var visitStart = 0
    private var visitPoints = 0
    private var closed = false
    private val busts = IntArray(players.size)
    init { resetState() }
    private val target get() = settings.gotchaTarget

    override fun resetState() { score.fill(0); visitStart = 0; visitPoints = 0; closed = false; busts.fill(0) }

    override fun playerStats(index: Int): PlayerMatchStats = super.playerStats(index).copy(busts = busts[index])

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) { visitStart = score[p]; visitPoints = 0; closed = false }
        val after = score[p] + segment.score
        if (after > target) {
            score[p] = visitStart
            pointsScored[p] -= visitPoints; visitPoints = 0
            busts[p]++; flagBust()
            banner = "Bust"; closed = true
            addHistory(p, "Bust", score[p].toString())
            return true
        }
        score[p] = after
        pointsScored[p] += segment.score; visitPoints += segment.score
        if (after > 0) players.indices.filter { it != p && score[it] == after }.forEach {
            score[it] = 0
            banner = "Gotcha! ${players[it].name} zurück auf 0"
            addHistory(it, "Gotcha!", "0")
        }
        if (after == target) {
            closed = true
            addHistory(p, visitPoints.toString(), after.toString())
            finish(p, "Game Shot")
            return true
        }
        return false
    }

    override fun onVisitEnd() {
        if (visit.isEmpty() || closed || finished) return
        addHistory(current, visitPoints.toString(), score[current].toString())
    }

    override fun onRoundCompleted() {
        if (settings.maxRounds > 0 && round > settings.maxRounds) {
            val best = score.max(); val w = players.indices.filter { score[it] == best }
            finish(if (w.size == 1) w.first() else null, if (w.size == 1) "Game Shot" else "Unentschieden")
        }
    }

    override fun botTarget(): Segment {
        val rest = target - score[current]
        if (rest > 60) {
            // Gegner "gotchen", wenn erreichbar
            players.indices.filter { it != current && score[it] > score[current] && score[it] - score[current] <= 60 }.firstOrNull()?.let {
                val d = score[it] - score[current]
                Segment.ALL.firstOrNull { s -> s.score == d }?.let { s -> return s }
            }
            return Segment.triple(20)
        }
        return Segment.ALL.filter { it.score <= rest }.maxByOrNull { if (it.score == rest) 1000 else it.score } ?: Segment.single(1)
    }

    override fun buildSnapshot(): GameState = GameState(
        players = players.indices.map { i -> playerState(i, score[i].toString(), "Rest ${target - score[i]}") },
        currentPlayer = current, currentVisit = visit.toList(), round = round,
        finished = finished, winnerIndex = winner, banner = banner,
        headline = "Ziel: $target · Runde $round" + if (settings.maxRounds > 0) " / ${settings.maxRounds}" else "",
    )
}

/**
 * Killer: Jeder Spieler bekommt eine Zahl. Eigenes Double treffen = Killer.
 * Killer nehmen anderen mit deren Double Leben ab. Letzter mit Leben gewinnt.
 */
class KillerGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : DartGame(players, settings, seed) {
    private val numbers = IntArray(players.size)
    private val lives = IntArray(players.size)
    private val killer = BooleanArray(players.size)
    init { resetState() }

    override fun resetState() {
        val pool = (1..20).shuffled(random)
        players.indices.forEach { numbers[it] = pool[it % pool.size] }
        lives.fill(settings.killerLives); killer.fill(false)
    }

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (!segment.isDouble || segment.isBull) return false
        val n = segment.number
        if (n == numbers[p]) {
            if (!killer[p]) { killer[p] = true; banner = "${players[p].name} ist Killer!"; addHistory(p, "Killer", "${lives[p]} ♥") }
            else { lives[p]--; addHistory(p, "Eigenes Double", "${lives[p]} ♥") ; checkOut(p) }
            return finished
        }
        if (!killer[p]) return false
        players.indices.filter { it != p && !out[it] && numbers[it] == n }.forEach { v ->
            lives[v]--
            pointsScored[p]++
            addHistory(v, "Getroffen von ${players[p].name}", "${lives[v]} ♥")
            checkOut(v)
        }
        return finished
    }

    private fun checkOut(i: Int) {
        if (lives[i] <= 0) {
            out[i] = true
            banner = "${players[i].name} ist raus"
            val alive = players.indices.filter { !out[it] }
            if (alive.size == 1) finish(alive.first(), "Game Shot")
            else if (alive.isEmpty()) finish(null, "Alle raus")
        }
    }

    override fun onLastPlayerStanding(index: Int) { finish(index, "Game Shot") }

    override fun botTarget(): Segment {
        val p = current
        if (!killer[p]) return Segment.double(numbers[p])
        val victim = players.indices.filter { it != p && !out[it] }.maxByOrNull { lives[it] } ?: return Segment.double(numbers[p])
        return Segment.double(numbers[victim])
    }

    override fun buildSnapshot(): GameState = GameState(
        players = players.indices.map { i ->
            playerState(i, "D${numbers[i]}", if (out[i]) "Ausgeschieden" else "♥ ".repeat(lives[i]).trim(), lives = lives[i], isKiller = killer[i])
        },
        currentPlayer = current, currentVisit = visit.toList(), round = round,
        finished = finished, winnerIndex = winner, banner = banner,
        headline = "Runde $round" + if (settings.maxRounds > 0) " / ${settings.maxRounds}" else "",
    )
}
