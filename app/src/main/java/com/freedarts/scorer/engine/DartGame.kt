package com.freedarts.scorer.engine

import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment
import kotlin.random.Random

sealed class GameEvent {
    data class Throw(val segment: Segment) : GameEvent()
    data object Next : GameEvent()
}

/**
 * Basis aller Spielmodi. Der Zustand ist deterministisch aus der Ereignisliste ableitbar;
 * "Undo" entfernt das letzte Ereignis und spielt alles neu ab.
 */
abstract class DartGame(
    val players: List<Player>,
    val settings: GameSettings,
    protected val seed: Long = System.currentTimeMillis(),
) {
    protected val events = ArrayList<GameEvent>()
    protected var random = Random(seed)

    var current = 0; protected set
    val visit = ArrayList<Segment>()
    var round = 1; protected set
    var finished = false; protected set
    var winner: Int? = null; protected set
    var banner: String? = null; protected set
    protected val history: List<ArrayList<VisitEntry>> = players.map { ArrayList() }
    protected val out = BooleanArray(players.size)

    /** Darts gesamt / Punkte gesamt pro Spieler (für Statistik). */
    protected val dartsThrown = IntArray(players.size)
    protected val pointsScored = IntArray(players.size)

    val startedAt: Long = System.currentTimeMillis()
    val eventCount: Int get() = events.size
    val canUndo: Boolean get() = events.isNotEmpty()
    /** Anzahl Darts der zuletzt abgeschlossenen Aufnahme (für Korrekturen nach dem dritten Dart). */
    var lastVisitThrows = 0; private set

    /** Darts, die korrigiert werden können: laufende Aufnahme, sonst die zuletzt abgeschlossene. */
    fun correctableDarts(): List<Segment> {
        if (visit.isNotEmpty()) return visit.toList()
        val idx = lastThrowIndices(lastVisitThrows)
        return idx.map { (events[it] as GameEvent.Throw).segment }
    }

    private fun lastThrowIndices(n: Int): List<Int> =
        events.indices.reversed().filter { events[it] is GameEvent.Throw }.take(n).reversed()

    /** Ersetzt den Dart mit Index [index] (siehe [correctableDarts]) und spielt das Spiel neu ab. */
    fun correctDart(index: Int, segment: Segment): Boolean {
        val n = if (visit.isNotEmpty()) visit.size else lastVisitThrows
        val idx = lastThrowIndices(n)
        if (index !in idx.indices) return false
        events[idx[index]] = GameEvent.Throw(segment)
        rebuild()
        return true
    }

    init {
        // Unterklassen rufen resetState() im eigenen init auf.
    }

    fun throwDart(segment: Segment) {
        if (finished) return
        events.add(GameEvent.Throw(segment))
        banner = null
        visit.add(segment)
        dartsThrown[current]++
        val endVisit = onDart(segment)
        if (finished) return
        if (endVisit || visit.size >= 3) completeVisit()
    }

    fun next() {
        if (finished) return
        events.add(GameEvent.Next)
        banner = null
        completeVisit()
    }

    fun undo() {
        if (events.isEmpty()) return
        events.removeAt(events.size - 1)
        rebuild()
    }

    private fun rebuild() {
        val copy = events.toList()
        events.clear()
        random = Random(seed)
        visit.clear(); current = 0; round = 1; finished = false; winner = null; banner = null; turnOverridden = false; lastVisitThrows = 0
        history.forEach { it.clear() }
        out.fill(false); dartsThrown.fill(0); pointsScored.fill(0)
        resetState()
        for (e in copy) when (e) {
            is GameEvent.Throw -> throwDart(e.segment)
            GameEvent.Next -> next()
        }
        if (copy.isEmpty()) banner = null
    }

    /** Beendet die aktuelle Aufnahme und gibt den nächsten Spieler frei. */
    protected fun completeVisit() {
        onVisitEnd()
        lastVisitThrows = visit.size
        visit.clear()
        if (finished) return
        if (turnOverridden) { turnOverridden = false; return }
        advancePlayer()
    }

    /** Wird von [startNewLeg] gesetzt: die Unterklasse hat den nächsten Spieler bereits bestimmt. */
    private var turnOverridden = false

    protected fun advancePlayer() {
        val active = players.indices.filter { !out[it] }
        if (active.size <= 1 && players.size > 1 && active.isNotEmpty()) {
            // z.B. Killer: letzter Überlebender
            onLastPlayerStanding(active.first())
            if (finished) return
        }
        var i = current
        repeat(players.size) {
            i = (i + 1) % players.size
            if (i == 0) {
                round++
                onRoundCompleted()
                if (finished) return
                if (turnOverridden) { turnOverridden = false; return }
            }
            if (!out[i]) { current = i; return }
        }
    }

    /** Hilfsfunktion für Unterklassen: Spielerwechsel nach Leg-Ende auf gewünschten Startspieler. */
    protected fun startNewLeg(starter: Int) {
        visit.clear()
        current = starter
        round = 1
        turnOverridden = true
    }

    protected fun finish(winnerIndex: Int?, bannerText: String) {
        finished = true
        winner = winnerIndex
        banner = bannerText
    }

    protected fun addHistory(player: Int, label: String, remaining: String) {
        history[player].add(VisitEntry(label, remaining))
    }

    protected fun visitScore(): Int = visit.sumOf { it.score }

    // ---- Hooks ----

    /** Zustand initialisieren (auch nach Undo). */
    protected abstract fun resetState()

    /** Dart verarbeiten. true = Aufnahme sofort beenden (Bust, Sieg …). */
    protected abstract fun onDart(segment: Segment): Boolean

    /** Aufnahme abgeschlossen (3 Darts, Bust oder manuell "Weiter"). */
    protected open fun onVisitEnd() {}

    /** Alle Spieler haben geworfen; [round] wurde bereits erhöht. */
    protected open fun onRoundCompleted() {}

    protected open fun onLastPlayerStanding(index: Int) {}

    /** Ziel für den Bot bei aktuellem Zustand. */
    abstract fun botTarget(): Segment

    abstract fun snapshot(): GameState

    open fun playerStats(index: Int): PlayerMatchStats = PlayerMatchStats(
        playerId = players[index].id,
        playerName = players[index].name,
        won = winner == index,
        finalScore = snapshot().players[index].score,
        dartsThrown = dartsThrown[index],
        pointsScored = pointsScored[index],
    )

    protected fun playerState(index: Int, score: String, detail: String, marks: Map<Int, Int>? = null, lives: Int? = null, isKiller: Boolean = false, legs: Int = 0, sets: Int = 0) =
        PlayerState(players[index], score, detail, legs, sets, marks, lives, isKiller, out[index], history[index].toList())

    protected fun fmtAvg(index: Int): String {
        val d = dartsThrown[index]
        return if (d == 0) "Ø –" else "Ø %.1f".format(pointsScored[index].toDouble() / d * 3)
    }
}
