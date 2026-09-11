package com.freedarts.scorer.engine.games

import com.freedarts.scorer.engine.DartGame
import com.freedarts.scorer.engine.GameState
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.HitMode
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment

/**
 * Bermuda: Ziele 12, 13, 14, Double, 15, 16, 17, Triple, 18, 19, 20, Bullseye.
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
        "Bull" -> seg.isBullseye
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

    override fun buildSnapshot(): GameState = super.buildSnapshot().copy(headline = "Ziel: ${if (target == "Bull") "Bullseye" else target} · ${round.coerceAtMost(totalRounds)} / $totalRounds")
}

/**
 * Gotcha: Von 0 exakt auf das Ziel (z.B. 301), Out-Modus wie eingestellt. Überwerfen = Bust.
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

    private fun validOut(seg: Segment): Boolean = when (settings.outMode) {
        OutMode.STRAIGHT -> true
        OutMode.DOUBLE -> seg.isDouble
        OutMode.MASTER -> seg.isDouble || seg.isTriple
    }

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) { visitStart = score[p]; visitPoints = 0; closed = false }
        val after = score[p] + segment.score
        val minLeft = if (settings.outMode == OutMode.STRAIGHT) 0 else 1
        if (after > target || (target - after in 1..minLeft) || (after == target && !validOut(segment))) {
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
        val finishers = Segment.ALL.filter { it.score == rest && validOut(it) }
        if (finishers.isNotEmpty()) return finishers.first()
        return Segment.ALL.filter { it.score < rest - (if (settings.outMode == OutMode.STRAIGHT) 0 else 1) }.maxByOrNull { it.score } ?: Segment.single(1)
    }

    override fun buildSnapshot(): GameState = GameState(
        players = players.indices.map { i -> playerState(i, score[i].toString(), "Rest ${target - score[i]}") },
        currentPlayer = current, currentVisit = visit.toList(), round = round,
        finished = finished, winnerIndex = winner, banner = banner,
        headline = "Ziel: $target · Runde $round" + if (settings.maxRounds > 0) " / ${settings.maxRounds}" else "",
    )
}

/**
 * Killer wie bei Autodarts: Jeder Spieler bekommt eine Zahl. Treffer auf die eigene Zahl füllen ein Konto
 * (Single 1, Double 2, Triple 3); ab [GameSettings.killerLives] ist man Killer. Killer nehmen anderen mit
 * deren Zahl Leben vom Konto; fällt ein Killer unter die Schwelle, ist er kein Killer mehr. Wer als Killer die
 * eigene Zahl trifft, verliert Leben. Wer bei 0 noch einmal getroffen wird, scheidet aus. Letzter gewinnt.
 */
class KillerGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : DartGame(players, settings, seed) {
    private val numbers = IntArray(players.size)
    private val tally = IntArray(players.size)
    private val killer = BooleanArray(players.size)
    private val lives get() = settings.killerLives.coerceAtLeast(1)
    init { resetState() }

    override fun resetState() {
        val pool = (1..20).shuffled(random)
        players.indices.forEach { numbers[it] = pool[it % pool.size] }
        tally.fill(0); killer.fill(false)
    }

    /** Wert eines Treffers nach Trefferart (0 = zählt nicht). */
    private fun value(seg: Segment): Int = when (settings.killerHitMode) {
        HitMode.DOUBLE -> if (seg.isDouble) 2 else 0
        HitMode.TRIPLE -> if (seg.isTriple) 3 else 0
        else -> seg.multiplier
    }

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (segment.isMiss || segment.isBull) return false
        val v = value(segment)
        if (v == 0) return false
        val n = segment.number
        if (n == numbers[p]) {
            if (!killer[p]) {
                tally[p] = minOf(tally[p] + v, lives)
                if (tally[p] >= lives) { killer[p] = true; banner = "${players[p].name} ist Killer!"; addHistory(p, "Killer", "${tally[p]} ♥") }
                else addHistory(p, "+$v", "${tally[p]} ♥")
            } else reduce(p, v, "Eigene Zahl")
            return finished
        }
        if (!killer[p]) return false
        players.indices.firstOrNull { it != p && !out[it] && numbers[it] == n }?.let { victim ->
            pointsScored[p] += v
            reduce(victim, v, "Getroffen von ${players[p].name}")
        }
        return finished
    }

    private fun reduce(i: Int, v: Int, label: String) {
        if (tally[i] == 0) { eliminate(i); return }
        tally[i] = (tally[i] - v).coerceAtLeast(0)
        if (killer[i] && tally[i] < lives) { killer[i] = false; addHistory(i, "$label · kein Killer mehr", "${tally[i]} ♥") }
        else addHistory(i, label, "${tally[i]} ♥")
    }

    private fun eliminate(i: Int) {
        out[i] = true
        addHistory(i, "Ausgeschieden", "")
        banner = "${players[i].name} ist raus"
        val alive = players.indices.filter { !out[it] }
        if (alive.size == 1) finish(alive.first(), "Game Shot")
        else if (alive.isEmpty()) finish(null, "Alle raus")
    }

    override fun onLastPlayerStanding(index: Int) { finish(index, "Game Shot") }

    override fun onRoundCompleted() {
        if (settings.maxRounds > 0 && round > settings.maxRounds) {
            val alive = players.indices.filter { !out[it] }
            val best = alive.maxOf { tally[it] }; val w = alive.filter { tally[it] == best }
            finish(if (w.size == 1) w.first() else null, if (w.size == 1) "Game Shot" else "Unentschieden")
        }
    }

    override fun botTarget(): Segment {
        val p = current
        val n = if (!killer[p]) numbers[p]
            else numbers[players.indices.filter { it != p && !out[it] }.maxByOrNull { tally[it] } ?: return Segment.triple(numbers[p])]
        return when (settings.killerHitMode) { HitMode.DOUBLE -> Segment.double(n); else -> Segment.triple(n) }
    }

    override fun buildSnapshot(): GameState = GameState(
        players = players.indices.map { i ->
            playerState(i, numbers[i].toString(), if (out[i]) "Ausgeschieden" else "${tally[i]} / $lives ♥" + (if (killer[i]) " · Killer" else ""), lives = tally[i], isKiller = killer[i])
        },
        currentPlayer = current, currentVisit = visit.toList(), round = round,
        finished = finished, winnerIndex = winner, banner = banner,
        headline = "Runde $round" + if (settings.maxRounds > 0) " / ${settings.maxRounds}" else "",
    )
}
