package com.freedarts.scorer.engine

import com.freedarts.scorer.engine.games.X01Game
import com.freedarts.scorer.model.BullOff
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.InMode
import com.freedarts.scorer.model.MatchMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.WinMode
import kotlin.random.Random

/**
 * Live-Vorhersage fürs laufende X01-Leg per Monte-Carlo: jeder Spieler wirft wie ein Bot mit seiner Streuung
 * (Checkout-Logik des X01-Motors), das Leg wird ab dem aktuellen Stand viele Male zu Ende gespielt.
 */
object Predictor {
    /** [legWin] je Spielerindex; [checkoutNow]/[expectedVisit] gelten für den Werfer [onThrow] und seine restlichen Darts. */
    data class Prediction(val legWin: DoubleArray, val checkoutNow: Double, val expectedVisit: Double, val onThrow: Int)

    // ponytail: feste 300 Simulationen (~10 ms); Fortschreibung übers ganze Match (Legs/Sets) erst, wenn jemand danach fragt
    fun leg(remaining: IntArray, onThrow: Int, dartsLeft: Int, sigmaMm: DoubleArray, settings: GameSettings, sims: Int = 300, seed: Long = 1): Prediction {
        val n = remaining.size
        // Spieler so drehen, dass der Werfer Index 0 ist (der Motor beginnt mit Spieler 0)
        val order = (0 until n).map { (onThrow + it) % n }
        val players = order.map { Player(id = "sim-$it", name = "S$it", botLevel = 1) }
        val simSettings = settings.copy(
            handicaps = order.mapIndexed { i, p -> "sim-$p" to remaining[p] }.toMap(),
            inMode = InMode.STRAIGHT, matchMode = MatchMode.LEGS, winMode = WinMode.FIRST_TO, legs = 1, sets = 1,
            bullOff = BullOff.OFF, randomStarter = false, maxRounds = 0,
        )
        val wins = IntArray(n); var checkouts = 0; var visitSum = 0.0
        val rnd = Random(seed)
        repeat(sims) {
            val g = X01Game(players, simSettings, seed = seed + it)
            repeat((3 - dartsLeft).coerceIn(0, 3)) { g.throwDart(Segment.MISS) } // bereits geworfene Darts dieser Aufnahme
            var firstVisit = true
            while (!g.finished) {
                val p = g.current
                g.throwDart(Bot.throwAt(g.botAim(), sigmaMm[order[p]], rnd))
                if (firstVisit && (g.current != 0 || g.finished)) {
                    firstVisit = false
                    val after = if (g.finished && g.winner == 0) 0 else g.snapshot().players[0].score.toIntOrNull() ?: remaining[onThrow]
                    if (after == 0) checkouts++
                    visitSum += remaining[onThrow] - after
                }
            }
            g.winner?.let { wins[order[it]]++ }
        }
        return Prediction(DoubleArray(n) { wins[it].toDouble() / sims }, checkouts.toDouble() / sims, visitSum / sims, onThrow)
    }
}
