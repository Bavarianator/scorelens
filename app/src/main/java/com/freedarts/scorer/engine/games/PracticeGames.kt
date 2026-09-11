package com.freedarts.scorer.engine.games

import com.freedarts.scorer.engine.Checkout
import com.freedarts.scorer.engine.DartGame
import com.freedarts.scorer.engine.GameState
import com.freedarts.scorer.model.FailMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.HitMode
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.TargetOrder

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

    override fun buildSnapshot(): GameState = GameState(
        players = players.indices.map { i -> playerState(i, score[i].toString(), detail(i)) },
        currentPlayer = current, currentVisit = visit.toList(), round = round,
        finished = finished, winnerIndex = winner, banner = banner, headline = headline(),
    )
}

/** Zahlenfolge 1..n nach [TargetOrder], optional mit Bull am Ende. */
internal fun targetSequence(settings: GameSettings, upTo: Int, random: kotlin.random.Random): List<Int> {
    val base = (1..upTo).toMutableList()
    when (settings.effectiveOrder) {
        TargetOrder.DOWN -> base.reverse()
        TargetOrder.RANDOM -> base.shuffle(random)
        TargetOrder.UP -> {}
    }
    return if (settings.includeBull) base + 25 else base
}

/** Trifft [seg] die Zahl [n] in der verlangten Art? Bull: Single = 25, Double = Bullseye. */
internal fun hitsNumber(seg: Segment, n: Int, mode: HitMode): Boolean {
    if (seg.number != n) return false
    return when (mode) {
        HitMode.ANY -> true
        HitMode.SINGLE -> seg.multiplier == 1
        HitMode.DOUBLE -> seg.multiplier == 2
        HitMode.TRIPLE -> seg.multiplier == 3 || (n == 25 && seg.multiplier == 2)
    }
}

/** Count Up: 8 Runden, alle Punkte zählen. */
class CountUpGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    init { resetState() }
    override fun onDart(segment: Segment): Boolean { addPoints(current, segment.score); return false }
    override fun botTarget(): Segment = Segment.triple(20)
}

/**
 * Around the Clock: 1 bis 20 (oder 20 bis 1, oder zufällig), optional Bull am Ende, der Reihe nach treffen.
 * Pro Zahl sind [GameSettings.hitsRequired] Treffer nötig; Double/Triple zählen als ein Treffer.
 */
class AroundTheClockGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : DartGame(players, settings, seed) {
    private lateinit var order: List<Int>
    private val need get() = settings.hitsRequired.coerceIn(1, 3)
    /** Treffer gesamt; Zielindex = progress / need. */
    private val progress = IntArray(players.size)
    private var visitHits = 0

    init { resetState() }

    override fun resetState() {
        order = targetSequence(settings, 20, random)
        progress.fill(0); visitHits = 0
    }

    private fun target(p: Int): Int? = order.getOrNull(progress[p] / need)

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) visitHits = 0
        val t = target(p) ?: return true
        if (hitsNumber(segment, t, settings.hitMode)) {
            progress[p]++
            visitHits++
            pointsScored[p]++
            if (progress[p] >= order.size * need) {
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

    override fun playerStats(index: Int): PlayerMatchStats = super.playerStats(index).copy(hits = progress[index])

    override fun buildSnapshot(): GameState = GameState(
        players = players.indices.map { i ->
            val t = target(i)
            val done = progress[i] / need
            val partial = if (need > 1 && t != null) " · ${progress[i] % need}/$need" else ""
            playerState(i, t?.let { if (it == 25) "Bull" else it.toString() } ?: "✓", "$done / ${order.size}$partial · ${dartsThrown[i]} Darts")
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
    override fun buildSnapshot(): GameState = super.buildSnapshot().copy(headline = "Runde $round / $totalRounds · Ziel: $target")
}

/**
 * Bob's 27: Start 27, Doubles 1..20 (optional Bull). Treffer +2n, kein Treffer −2n.
 * Bei 0 oder weniger ausgeschieden, außer negative Punkte sind erlaubt.
 */
class Bobs27Game(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    private val targets = (1..20).toList() + (if (settings.includeBull) listOf(25) else emptyList())
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
        if (!settings.allowNegative && score[current] <= 0) { out[current] = true; addHistory(current, "AUS", score[current].toString()) }
        else addHistory(current, (if (visitPoints >= 0) "+" else "") + visitPoints, score[current].toString())
        visitPoints = 0
        if (out.all { it }) finish(null, "Alle ausgeschieden")
    }

    override fun onLastPlayerStanding(index: Int) { finish(index, "Game Shot") }
    override fun botTarget(): Segment = if (target == 25) Segment.BULL else Segment.double(target)
    override fun buildSnapshot(): GameState = super.buildSnapshot().copy(
        headline = "Ziel: " + (if (target == 25) "Bull" else "D$target") + " · ${round.coerceAtMost(totalRounds)} / $totalRounds")
}

/**
 * Random Checkout: pro Leg ein zufälliger Rest; [GameSettings.checkoutRounds] Aufnahmen pro Leg, Out-Modus wie
 * eingestellt. Bust = Aufnahme zählt nicht. Wer die meisten Legs auscheckt, gewinnt.
 */
class RandomCheckoutGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    private val targets = ArrayList<Int>()
    private val remaining = IntArray(players.size)
    private val legOf = IntArray(players.size) { -1 }
    private val done = BooleanArray(players.size)
    private var visitStart = 0
    private val roundsPerLeg get() = settings.checkoutRounds.coerceIn(1, 9)
    init { resetState() }

    override val totalRounds: Int get() = settings.rounds * roundsPerLeg

    override fun resetState() {
        super.resetState()
        targets.clear()
        repeat(settings.rounds) {
            var t: Int
            do { t = random.nextInt(settings.checkoutMin, settings.checkoutMax + 1) } while (!Checkout.isFinishable(t, 3, settings.outMode))
            targets.add(t)
        }
        remaining.fill(0); legOf.fill(-1); done.fill(false)
    }

    private val leg get() = ((round - 1) / roundsPerLeg).coerceIn(0, targets.size - 1)
    private val target get() = targets[leg]

    private fun validOut(seg: Segment): Boolean = when (settings.outMode) {
        OutMode.STRAIGHT -> true
        OutMode.DOUBLE -> seg.isDouble
        OutMode.MASTER -> seg.isDouble || seg.isTriple
    }

    /** Rest des aktiven Spielers im aktuellen Leg (neues Leg = volles Ziel). */
    private fun restOf(p: Int): Int = if (legOf[p] != leg) target else remaining[p]

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (visit.size == 1) {
            if (legOf[p] != leg) { legOf[p] = leg; remaining[p] = target; done[p] = false }
            visitStart = remaining[p]
        }
        if (done[p]) return true
        val after = remaining[p] - segment.score
        val minLeft = if (settings.outMode == OutMode.STRAIGHT) 1 else 2
        if (after < 0 || (after in 1 until minLeft) || (after == 0 && !validOut(segment))) {
            remaining[p] = visitStart; flagBust(); banner = "Bust"
            return true
        }
        remaining[p] = after
        if (after == 0) { done[p] = true; addPoints(p, 1); return true }
        return false
    }

    override fun onVisitEnd() {
        if (visit.isEmpty() || finished) return
        addHistory(current, if (visitPoints > 0) "✓ $target" else "Rest ${remaining[current]}", score[current].toString())
        visitPoints = 0
    }

    override fun botTarget(): Segment =
        Checkout.bestRoute(restOf(current), 3 - visit.size, settings.outMode)?.first() ?: Segment.triple(20)

    override fun detail(i: Int): String = "${score[i]} von ${settings.rounds} Legs"

    override fun buildSnapshot(): GameState {
        val r = restOf(current)
        return super.buildSnapshot().copy(
            headline = "Leg ${leg + 1} / ${settings.rounds}" + (if (roundsPerLeg > 1) " · Aufnahme ${(round - 1) % roundsPerLeg + 1} / $roundsPerLeg" else "") + " · Rest: $r",
            checkoutHint = if (finished) null else Checkout.describe(Checkout.bestRoute(r, (3 - visit.size).coerceAtLeast(1), settings.outMode)),
        )
    }
}

/**
 * Segment Training: eine Zahl in der gewählten Trefferart üben. Endet nach [GameSettings.hitCount] Treffern
 * (Sieger: wenigste Darts) oder Darts (Sieger: meiste Treffer).
 */
class SegmentTrainingGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    private val done = BooleanArray(players.size)
    init { resetState() }
    private val t get() = settings.trainingSegment
    private val count get() = settings.hitCount.coerceAtLeast(1)
    override val totalRounds: Int get() = if (settings.endAfterHits) Int.MAX_VALUE else (count + 2) / 3

    override fun resetState() { super.resetState(); done.fill(false) }

    override fun onDart(segment: Segment): Boolean {
        val p = current
        if (settings.endAfterHits && done[p]) return true
        if (hitsNumber(segment, t, settings.hitMode)) addPoints(p, 1)
        if (settings.endAfterHits && score[p] >= count) {
            done[p] = true
            if (done.all { it }) {
                val best = players.indices.minOf { dartsThrown[it] }
                val w = players.indices.filter { dartsThrown[it] == best }
                finish(if (w.size == 1) w.first() else null, if (w.size == 1) "Game Shot" else "Unentschieden")
            }
            return true
        }
        if (!settings.endAfterHits && dartsThrown[p] >= count) return true
        return false
    }

    override fun botTarget(): Segment = when {
        t == 25 -> if (settings.hitMode == HitMode.SINGLE) Segment.OUTER_BULL else Segment.BULL
        settings.hitMode == HitMode.SINGLE -> Segment.single(t)
        settings.hitMode == HitMode.DOUBLE -> Segment.double(t)
        else -> Segment.triple(t)
    }

    override fun playerStats(index: Int): PlayerMatchStats = super.playerStats(index).copy(hits = score[index])

    override fun detail(i: Int): String {
        val d = dartsThrown[i]
        val rate = if (d == 0) "Trefferquote –" else "Trefferquote %.0f %%".format(100.0 * score[i] / d)
        return if (settings.endAfterHits) "$rate · $d Darts" else "$rate · ${(count - d).coerceAtLeast(0)} Darts übrig"
    }

    override fun buildSnapshot(): GameState = super.buildSnapshot().copy(
        headline = (if (settings.endAfterHits) "Bis $count Treffer" else "Runde ${round.coerceAtMost(totalRounds)} / $totalRounds") +
            " · Ziel: " + (if (t == 25) "Bull" else t.toString()) +
            when (settings.hitMode) { HitMode.SINGLE -> " Single"; HitMode.DOUBLE -> " Double"; HitMode.TRIPLE -> " Triple"; HitMode.ANY -> "" },
    )
}

/** Round the World: Runde n = Zahl n (1..20 oder umgekehrt, optional Bull); Single = 1, Double = 2, Triple = 3 Punkte. */
class RoundTheWorldGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : RoundGame(players, settings, seed) {
    private var targets: List<Int> = emptyList()
    init { resetState() }
    override fun resetState() { super.resetState(); targets = targetSequence(settings, settings.rounds.coerceIn(1, 20), random) }
    override val totalRounds: Int get() = targets.size
    private val target get() = targets[(round - 1).coerceIn(0, targets.size - 1)]
    override fun onDart(segment: Segment): Boolean {
        if (segment.number == target) addPoints(current, segment.multiplier)
        return false
    }
    override fun botTarget(): Segment = if (target == 25) Segment.BULL else Segment.triple(target)
    override fun buildSnapshot(): GameState = super.buildSnapshot().copy(headline = "Ziel: " + (if (target == 25) "Bull" else target.toString()) + " · ${round.coerceAtMost(totalRounds)} / $totalRounds")
}

/**
 * 121 wie bei Autodarts: Ziel mit 9 (oder 6) Darts auschecken (Double Out). Erfolg: Ziel + Schritt, bis 170.
 * Misserfolg: Soft (−1), Hard Reset (zurück auf 121) oder Safehouse (nie unter das zuletzt gesicherte Ziel).
 * Sieg bei 170 oder mit dem höchsten Ziel nach allen Versuchen – aber nur oberhalb von 121.
 */
class OneTwentyOneGame(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()) : DartGame(players, settings, seed) {
    private val target = IntArray(players.size)
    private val safe = IntArray(players.size)
    private val remaining = IntArray(players.size)
    private val attemptDarts = IntArray(players.size)
    private val attemptsDone = IntArray(players.size)
    private val successes = IntArray(players.size)
    private val busts = IntArray(players.size)
    private var visitStart = 0
    private val maxDarts get() = if (settings.dartsPerAttempt <= 6) 6 else 9
    init { resetState() }

    override fun resetState() {
        target.fill(121); safe.fill(121); remaining.fill(121); attemptDarts.fill(0); attemptsDone.fill(0); successes.fill(0); busts.fill(0)
    }

    override fun playerStats(index: Int): PlayerMatchStats = super.playerStats(index).copy(busts = busts[index])

    private fun endAttempt(p: Int, success: Boolean) {
        attemptsDone[p]++
        if (success) {
            successes[p]++; pointsScored[p]++
            addHistory(p, "✓ ${target[p]}", "${attemptDarts[p]} Darts")
            if (target[p] >= 170) { finish(p, "Game Shot"); return }
            target[p] = (target[p] + settings.step.coerceAtLeast(1)).coerceAtMost(170)
            if (settings.failMode == FailMode.SAFEHOUSE && successes[p] % settings.safehouseEvery.coerceAtLeast(1) == 0) safe[p] = target[p]
        } else {
            addHistory(p, "✗ ${target[p]}", "")
            target[p] = when (settings.failMode) {
                FailMode.SOFT -> (target[p] - 1).coerceAtLeast(121)
                FailMode.HARD_RESET -> 121
                FailMode.SAFEHOUSE -> (target[p] - 1).coerceAtLeast(safe[p])
            }
        }
        remaining[p] = target[p]; attemptDarts[p] = 0
        if (players.indices.all { attemptsDone[it] >= settings.attempts }) {
            val best = target.max(); val w = players.indices.filter { target[it] == best }
            val won = w.size == 1 && best > 121
            finish(if (won) w.first() else null, if (won) "Game Shot" else "Kein Sieger")
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
            busts[p]++; flagBust()
            banner = "Bust"
            if (attemptDarts[p] >= maxDarts) endAttempt(p, false)
            return true
        }
        remaining[p] = after
        if (after == 0) { endAttempt(p, true); return true }
        if (attemptDarts[p] >= maxDarts) { endAttempt(p, false); return true }
        return false
    }

    override fun onVisitEnd() {
        val p = current
        if (visit.isEmpty() || finished || attemptDarts[p] == 0) return
        // Manuell "Weiter": angefangene Aufnahme gilt als verbraucht
        attemptDarts[p] = ((attemptDarts[p] + 2) / 3) * 3
        if (attemptDarts[p] >= maxDarts) endAttempt(p, false)
    }

    override fun botTarget(): Segment =
        Checkout.bestRoute(remaining[current], 3 - visit.size, OutMode.DOUBLE)?.first()
            ?: Checkout.bestRoute(remaining[current], 3, OutMode.DOUBLE)?.first() ?: Segment.triple(20)

    override fun buildSnapshot(): GameState = GameState(
        players = players.indices.map { i ->
            val safeText = if (settings.failMode == FailMode.SAFEHOUSE) " · sicher ${safe[i]}" else ""
            playerState(i, target[i].toString(), "Rest ${remaining[i]} · ${successes[i]}/${attemptsDone[i]} ✓$safeText")
        },
        currentPlayer = current, currentVisit = visit.toList(), round = round, finished = finished, winnerIndex = winner, banner = banner,
        headline = "Versuch ${(attemptsDone[current] + 1).coerceAtMost(settings.attempts)} / ${settings.attempts} · Dart ${attemptDarts[current] + 1} / $maxDarts",
        checkoutHint = if (finished) null else Checkout.describe(Checkout.bestRoute(remaining[current], (3 - visit.size).coerceAtLeast(1), OutMode.DOUBLE)),
    )
}
