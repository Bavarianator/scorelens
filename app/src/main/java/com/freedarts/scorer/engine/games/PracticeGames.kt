package com.freedarts.scorer.engine.games

import com.freedarts.scorer.engine.Checkout
import com.freedarts.scorer.engine.DartGame
import com.freedarts.scorer.engine.GameState
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.HitMode
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment

/** Gemeinsame Basis für rundenbasierte Modi ("Runde x / y", höchste Punktzahl gewinnt). */
abstract class RoundGame(players: List<Player>, settings: GameSettings, seed: Long) : DartGame(players, settings, seed) {
    protected val score = IntArray(players.size)
    protected var visitPoints = 0
    protected open val totalRounds: Int get() = settings.rounds
    protected open val higherWins = true

    override fun resetState() { score.fill(0); visitPoints = 0 }

    protected fun addPoints(p: Int, v: Int) { score[p] += v; visitPoints += v; pointsScored[p] += v }

    override fun onVisitEnd() {
        if (visit.isEmpty() || finished) return
        addHistory(current, visitPoints.toString(), score[current].toString())
        visitPoints = 0
    }

    override fun onRoundCompleted() {
        if (round > totalRounds) endByScore()
    }

    protected fun endByScore() {
        val best = if (higherWins) score.filterIndexed { i, _ -> !out[i] }.maxOrNull() else score.filterIndexed { i, _ -> !out[i] }.minOrNull()
        val w = players.indices.filter { !out[it] && score[it] == best }
        finish(if (w.size == 1) w.first() else null, if (w.size == 1) "Game Shot" else "Unentschieden")
    }

    protected fun headline() = "Runde ${round.coerceAtMost(totalRounds)} / $totalRounds"

    protected open fun detail(i: Int): String = fmtAvg(i)

    override fun snapshot(): GameState = GameState(
        players = players.indices.map { i -> playerState(i, score[i].toString(), detail(i)) },
        currentPlayer = current, currentVisit = visit.toList(), round = round,
        finished = finished, winnerIndex = winner, banner = banner, headline = headline(),
    )
}

/** Count Up: 8 Runden, alle Punkte zählen. */
class CountUpGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    init { resetState() }
    override fun onDart(segment: Segment): Boolean { addPoints(current, segment.score); return false }
    override fun botTarget(): Segment = Segment.triple(20)
}

/** Around the Clock: 1 bis 20 (optional Bull) der Reihe nach treffen. */
class AroundTheClockGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : DartGame(players, settings, seed) {
    private lateinit var order: List<Int>
    private val progress = IntArray(players.size)
    private var visitHits = 0

    init { resetState() }

    override fun resetState() {
        val base = (1..20).toMutableList()
        if (settings.randomOrder) base.shuffle(random)
        order = if (settings.includeBull) base + 25 else base
        progress.fill(0); visitHits = 0
    }

    private fun target(p: Int): Int? = order.getOrNull(progress[p])

    private fun matches(seg: Segment, n: Int): Boolean {
        if (seg.number != n) return false
        if (n == 25) return true
        return when (settings.hitMode) {
            HitMode.ANY -> true
            HitMode.SINGLE -> seg.multiplier == 1
            HitMode.DOUBLE -> seg.multiplier == 2
            HitMode.TRIPLE -> seg.multiplier == 3
        }
    }

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) visitHits = 0
        val t = target(p) ?: return true
        if (matches(segment, t)) {
            progress[p]++
            visitHits++
            pointsScored[p]++
            if (progress[p] >= order.size) {
                addHistory(p, "$visitHits ✓", "Fertig")
                finish(p, "Game Shot")
                return true
            }
        }
        return false
    }

    override fun onVisitEnd() {
        if (visit.isEmpty() || finished) return
        addHistory(current, "$visitHits ✓", target(current)?.let { if (it == 25) "Bull" else it.toString() } ?: "")
    }

    override fun onRoundCompleted() {
        if (settings.maxRounds > 0 && round > settings.maxRounds) {
            val best = progress.max(); val w = players.indices.filter { progress[it] == best }
            finish(if (w.size == 1) w.first() else null, if (w.size == 1) "Game Shot" else "Unentschieden")
        }
    }

    override fun botTarget(): Segment {
        val t = target(current) ?: return Segment.BULL
        if (t == 25) return Segment.BULL
        return when (settings.hitMode) {
            HitMode.DOUBLE -> Segment.double(t)
            HitMode.TRIPLE -> Segment.triple(t)
            else -> Segment.single(t)
        }
    }

    override fun snapshot(): GameState = GameState(
        players = players.indices.map { i ->
            val t = target(i)
            playerState(i, t?.let { if (it == 25) "Bull" else it.toString() } ?: "✓", "${progress[i]} / ${order.size} · ${dartsThrown[i]} Darts")
        },
        currentPlayer = current, currentVisit = visit.toList(), round = round,
        finished = finished, winnerIndex = winner, banner = banner,
        headline = "Runde $round" + if (settings.maxRounds > 0) " / ${settings.maxRounds}" else "",
    )
}

/** Shanghai: Runde n = Zahl n. Single/Double/Triple in einer Aufnahme = sofortiger Sieg. */
class ShanghaiGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    init { resetState() }
    override val totalRounds: Int get() = settings.rounds.coerceIn(1, 20)
    private val target get() = round.coerceAtMost(20)

    override fun onDart(segment: Segment): Boolean {
        if (segment.number == target) {
            addPoints(current, segment.score)
            val mults = visit.filter { it.number == target }.map { it.multiplier }.toSet()
            if (mults.containsAll(setOf(1, 2, 3))) {
                addHistory(current, "SHANGHAI", score[current].toString())
                finish(current, "Shanghai!")
                return true
            }
        }
        return false
    }

    override fun botTarget(): Segment = Segment.triple(target)
    override fun snapshot(): GameState = super.snapshot().copy(headline = "Runde $round / $totalRounds · Ziel: $target")
}

/** Bob's 27: Start 27, Doubles 1..20 und Bull. Treffer +2n, kein Treffer −2n. Unter 0 = ausgeschieden. */
class Bobs27Game(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    private val targets = (1..20).toList() + 25
    init { resetState() }
    override val totalRounds: Int get() = targets.size
    override fun resetState() { super.resetState(); score.fill(27) }
    private val target get() = targets[(round - 1).coerceIn(0, targets.size - 1)]
    private fun value(t: Int) = if (t == 25) 50 else t * 2

    override fun onDart(segment: Segment): Boolean {
        val t = target
        if (segment.number == t && segment.multiplier == 2) addPoints(current, value(t))
        return false
    }

    override fun onVisitEnd() {
        if (visit.isEmpty() || finished) return
        val t = target
        val hits = visit.count { it.number == t && it.multiplier == 2 }
        if (hits == 0) { score[current] -= value(t); visitPoints -= value(t) }
        if (score[current] < 0) { out[current] = true; addHistory(current, "AUS", score[current].toString()) }
        else addHistory(current, (if (visitPoints >= 0) "+" else "") + visitPoints, score[current].toString())
        visitPoints = 0
        if (out.all { it }) finish(null, "Alle ausgeschieden")
    }

    override fun onLastPlayerStanding(index: Int) { finish(index, "Game Shot") }
    override fun botTarget(): Segment = if (target == 25) Segment.BULL else Segment.double(target)
    override fun snapshot(): GameState = super.snapshot().copy(
        headline = "Ziel: " + (if (target == 25) "Bull" else "D$target") + " · ${round.coerceAtMost(totalRounds)} / $totalRounds")
}

/** Random Checkout: zufälliger Rest, 3 Darts zum Finish (Double Out). */
class RandomCheckoutGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    private val targets = ArrayList<Int>()
    private val remaining = IntArray(players.size)
    init { resetState() }

    override fun resetState() {
        super.resetState()
        targets.clear()
        repeat(settings.rounds) {
            var t: Int
            do { t = random.nextInt(settings.checkoutMin, settings.checkoutMax + 1) } while (!Checkout.isFinishable(t, 3, OutMode.DOUBLE))
            targets.add(t)
        }
        remaining.fill(0)
    }

    private val target get() = targets[(round - 1).coerceIn(0, targets.size - 1)]

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) remaining[p] = target
        val after = remaining[p] - segment.score
        if (after < 0 || after == 1 || (after == 0 && !segment.isDouble)) { remaining[p] = target; return true }
        remaining[p] = after
        if (after == 0) { addPoints(p, 1); return true }
        return false
    }

    override fun onVisitEnd() {
        if (visit.isEmpty() || finished) return
        addHistory(current, if (visitPoints > 0) "✓ $target" else "✗ $target", score[current].toString())
        visitPoints = 0
    }

    override fun botTarget(): Segment {
        val r = if (visit.isEmpty()) target else remaining[current]
        return Checkout.bestRoute(r, 3 - visit.size, OutMode.DOUBLE)?.first() ?: Segment.triple(20)
    }

    override fun detail(i: Int): String = "${score[i]} von ${(round - 1).coerceAtMost(totalRounds)} Checkouts"

    override fun snapshot(): GameState {
        val r = if (visit.isEmpty()) target else remaining[current]
        return super.snapshot().copy(
            headline = "Runde ${round.coerceAtMost(totalRounds)} / $totalRounds · Rest: $r",
            checkoutHint = if (finished) null else Checkout.describe(Checkout.bestRoute(r, (3 - visit.size).coerceAtLeast(1), OutMode.DOUBLE)),
        )
    }
}

/** Segment Training: eine Zahl wählen, Treffer zählen (S=1, D=2, T=3). */
class SegmentTrainingGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    init { resetState() }
    private val t get() = settings.trainingSegment
    override fun onDart(segment: Segment): Boolean {
        if (segment.number == t) addPoints(current, if (t == 25) segment.multiplier else segment.multiplier)
        return false
    }
    override fun botTarget(): Segment = if (t == 25) Segment.BULL else Segment.triple(t)
    override fun detail(i: Int): String {
        val d = dartsThrown[i]
        return if (d == 0) "Trefferquote –" else "Trefferquote %.0f %%".format(100.0 * score[i] / d)
    }
    override fun snapshot(): GameState = super.snapshot().copy(headline = headline() + " · Ziel: " + (if (t == 25) "Bull" else t.toString()))
}

/** Round the World: Runde n = Zahl n (1..20, optional Bull), jeder Treffer zählt seinen Wert. */
class RoundTheWorldGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    private val targets = (1..settings.rounds.coerceIn(1, 20)).toList() + (if (settings.includeBull) listOf(25) else emptyList())
    init { resetState() }
    override val totalRounds: Int get() = targets.size
    private val target get() = targets[(round - 1).coerceIn(0, targets.size - 1)]
    override fun onDart(segment: Segment): Boolean {
        if (segment.number == target) addPoints(current, segment.score)
        return false
    }
    override fun botTarget(): Segment = if (target == 25) Segment.BULL else Segment.triple(target)
    override fun snapshot(): GameState = super.snapshot().copy(headline = "Ziel: " + (if (target == 25) "Bull" else target.toString()) + " · ${round.coerceAtMost(totalRounds)} / $totalRounds")
}

/**
 * 121: Ziel mit maximal 9 Darts (Double Out) auschecken. Erfolg: Ziel +1, Misserfolg: Ziel −1.
 * Punktestand = höchstes erreichtes Ziel; Gewinner hat nach allen Versuchen das höchste Ziel.
 */
class OneTwentyOneGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : DartGame(players, settings, seed) {
    private val target = IntArray(players.size)
    private val remaining = IntArray(players.size)
    private val attemptDarts = IntArray(players.size)
    private val attemptsDone = IntArray(players.size)
    private val successes = IntArray(players.size)
    private var visitStart = 0
    init { resetState() }

    override fun resetState() {
        target.fill(121); remaining.fill(121); attemptDarts.fill(0); attemptsDone.fill(0); successes.fill(0)
    }

    private fun endAttempt(p: Int, success: Boolean) {
        attemptsDone[p]++
        if (success) { successes[p]++; pointsScored[p]++; addHistory(p, "✓ ${target[p]}", "${attemptDarts[p]} Darts"); target[p]++ }
        else { addHistory(p, "✗ ${target[p]}", ""); target[p] = (target[p] - 1).coerceAtLeast(2) }
        remaining[p] = target[p]; attemptDarts[p] = 0
        if (players.indices.all { attemptsDone[it] >= settings.attempts }) {
            val best = target.max(); val w = players.indices.filter { target[it] == best }
            finish(if (w.size == 1) w.first() else null, if (w.size == 1) "Game Shot" else "Unentschieden")
        }
    }

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) visitStart = remaining[p]
        attemptDarts[p]++
        val after = remaining[p] - segment.score
        if (after < 0 || after == 1 || (after == 0 && !segment.isDouble)) {
            // Bust: Aufnahme zählt nicht, die restlichen Darts der Aufnahme sind verbraucht
            remaining[p] = visitStart
            attemptDarts[p] = ((attemptDarts[p] + 2) / 3) * 3
            banner = "Bust"
            if (attemptDarts[p] >= 9) endAttempt(p, false)
            return true
        }
        remaining[p] = after
        if (after == 0) { endAttempt(p, true); return true }
        if (attemptDarts[p] >= 9) { endAttempt(p, false); return true }
        return false
    }

    override fun onVisitEnd() {
        val p = current
        if (visit.isEmpty() || finished || attemptDarts[p] == 0) return
        // Manuell "Weiter": angefangene Aufnahme gilt als verbraucht
        attemptDarts[p] = ((attemptDarts[p] + 2) / 3) * 3
        if (attemptDarts[p] >= 9) endAttempt(p, false)
    }

    override fun botTarget(): Segment =
        Checkout.bestRoute(remaining[current], 3 - visit.size, OutMode.DOUBLE)?.first()
            ?: Checkout.bestRoute(remaining[current], 3, OutMode.DOUBLE)?.first() ?: Segment.triple(20)

    override fun snapshot(): GameState = GameState(
        players = players.indices.map { i -> playerState(i, target[i].toString(), "Rest ${remaining[i]} · ${successes[i]}/${attemptsDone[i]} ✓") },
        currentPlayer = current, currentVisit = visit.toList(), round = round, finished = finished, winnerIndex = winner, banner = banner,
        headline = "Versuch ${(attemptsDone[current] + 1).coerceAtMost(settings.attempts)} / ${settings.attempts} · Dart ${attemptDarts[current] + 1} / 9",
        checkoutHint = if (finished) null else Checkout.describe(Checkout.bestRoute(remaining[current], (3 - visit.size).coerceAtLeast(1), OutMode.DOUBLE)),
    )
}
