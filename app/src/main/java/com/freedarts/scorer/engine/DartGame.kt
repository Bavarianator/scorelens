package com.freedarts.scorer.engine

import com.freedarts.scorer.model.BullOff
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.model.ThrowRecord
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

sealed class GameEvent {
    /** Ein Dart; [x]/[y] = Auftreffpunkt in Board-Millimetern (nur bei Autoscoring bekannt). */
    data class Throw(val segment: Segment, val x: Float? = null, val y: Float? = null, val at: Long = 0L) : GameEvent()
    /** Aufnahme beenden / Spielerwechsel. [auto] = von der App nach dem dritten Dart eingefügt (manuelle Eingabe, Bots). */
    data class Next(val auto: Boolean = false) : GameEvent()
}

/**
 * Basis aller Spielmodi. Der Zustand ist deterministisch aus der Ereignisliste ableitbar;
 * "Undo" entfernt das letzte Ereignis und spielt alles neu ab.
 *
 * Ablauf einer Aufnahme wie bei Autodarts: Nach dem dritten Dart, einem Bust oder einem Checkout ist die Aufnahme
 * abgeschlossen ([visitComplete]); weitere Darts werden ignoriert. Erst [next] (Takeout bzw. "Next") gibt den
 * nächsten Spieler frei. Bei manueller Eingabe fügt die App das [GameEvent.Next] sofort automatisch ein.
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
    /** Aufnahme abgeschlossen, Spielerwechsel wartet auf [next]. */
    var visitComplete = false; private set
    protected val history: List<ArrayList<VisitEntry>> = players.map { ArrayList() }
    protected val out = BooleanArray(players.size)

    /** Darts gesamt / Punkte gesamt pro Spieler (für Statistik). */
    protected val dartsThrown = IntArray(players.size)
    protected val pointsScored = IntArray(players.size)

    /** Wurfprotokoll (jeder Dart mit Set, Leg, Runde, Position). */
    private val _throwLog = ArrayList<ThrowRecord>()
    val throwLog: List<ThrowRecord> get() = _throwLog
    /** Set / Leg für das Wurfprotokoll; X01 überschreibt. */
    protected open val currentSet: Int get() = 1
    protected open val currentLeg: Int get() = 1

    /** Startspieler des Spiels (Einstellung, Zufall oder Bull-off). */
    var starter = 0; protected set
    /** Ausbullen läuft; jeder Spieler in [bullOffQueue] wirft einen Dart auf Bull. */
    var bullOffActive = false; private set
    private val bullOffQueue = ArrayList<Int>()
    private var bullOffOrder: List<Int> = emptyList()
    private val bullOffDistance = HashMap<Int, Double>()

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
        val old = events[idx[index]] as GameEvent.Throw
        // Korrigierter Dart: Position ist nicht mehr bekannt
        events[idx[index]] = GameEvent.Throw(segment, null, null, old.at)
        rebuild()
        return true
    }

    init {
        // Unterklassen rufen resetState() im eigenen init auf; die Basis bestimmt vorher den Startspieler.
        startBase()
    }

    /** Startspieler festlegen bzw. Ausbullen beginnen (bei Spielstart und nach jedem Neuaufbau). */
    private fun startBase() {
        starter = if (settings.randomStarter && players.size > 1) random.nextInt(players.size) else 0
        current = starter
        if (settings.bullOff != BullOff.OFF && players.size > 1) beginBullOff(players.indices.toList())
    }

    private fun beginBullOff(order: List<Int>) {
        bullOffActive = true
        bullOffOrder = order
        bullOffQueue.clear(); bullOffQueue.addAll(order)
        bullOffDistance.clear()
        current = order.first()
    }

    fun throwDart(segment: Segment, x: Float? = null, y: Float? = null, at: Long = System.currentTimeMillis()) {
        if (finished || visitComplete) return
        events.add(GameEvent.Throw(segment, x, y, at))
        if (bullOffActive) { bullOffThrow(segment, x, y, at); return }
        banner = null
        visit.add(segment)
        dartsThrown[current]++
        _throwLog.add(ThrowRecord(current, currentSet, currentLeg, round, segment.number, segment.multiplier, x, y, false, at))
        val endVisit = onDart(segment)
        if (finished) return
        if (endVisit || visit.size >= 3) visitComplete = true
    }

    /** Markiert den zuletzt geworfenen Dart im Wurfprotokoll als Bust. */
    protected fun flagBust() {
        if (_throwLog.isEmpty()) return
        _throwLog[_throwLog.size - 1] = _throwLog.last().copy(bust = true)
    }

    // ---- Bull-off ----

    /** Abstand zum Bull in mm: aus der Position, sonst Ringmitte des Segments. */
    private fun bullDistance(seg: Segment, x: Float?, y: Float?): Double {
        if (x != null && y != null && !seg.isMiss) return sqrt((x * x + y * y).toDouble())
        return when {
            seg.isBullseye -> 0.0
            seg.isBull -> Board.OUTER_BULL_RADIUS - 4
            seg.isMiss -> 999.0
            seg.multiplier == 1 -> (Board.OUTER_BULL_RADIUS + Board.TRIPLE_INNER) / 2
            seg.isTriple -> (Board.TRIPLE_INNER + Board.TRIPLE_OUTER) / 2
            else -> (Board.DOUBLE_INNER + Board.DOUBLE_OUTER) / 2
        }
    }

    private fun bullOffThrow(seg: Segment, x: Float?, y: Float?, at: Long) {
        val p = current
        _throwLog.add(ThrowRecord(p, 0, 0, 0, seg.number, seg.multiplier, x, y, false, at))
        bullOffDistance[p] = bullDistance(seg, x, y)
        addHistory(p, "Bull-off: ${seg.name}", "")
        bullOffQueue.remove(p)
        if (bullOffQueue.isNotEmpty()) { current = bullOffQueue.first(); return }
        val best = bullOffDistance.values.min()
        val tied = bullOffOrder.filter { abs((bullOffDistance[it] ?: 999.0) - best) < 0.5 }
        if (tied.size == 1) {
            bullOffActive = false
            starter = tied.first()
            current = starter
            banner = "${players[starter].name} beginnt"
            onStarterDecided(starter)
        } else {
            banner = "Bull-off: Gleichstand"
            beginBullOff(if (settings.bullOff == BullOff.OFFICIAL) tied.reversed() else tied)
        }
    }

    /** Ziel für den Bot inklusive Ausbullen. */
    fun botAim(): Segment = if (bullOffActive) Segment.BULL else botTarget()

    // ---- Ablauf ----

    fun next(auto: Boolean = false) {
        if (finished) return
        events.add(GameEvent.Next(auto))
        if (bullOffActive) return
        if (!auto) banner = null
        completeVisit()
    }

    fun undo() {
        if (events.isEmpty()) return
        val last = events.removeAt(events.size - 1)
        // Automatisch eingefügtes "Next" gehört zum Dart davor: beides zurücknehmen
        if (last is GameEvent.Next && last.auto && events.isNotEmpty()) events.removeAt(events.size - 1)
        rebuild()
    }

    private fun rebuild() {
        val copy = events.toList()
        events.clear()
        random = Random(seed)
        visit.clear(); current = 0; round = 1; finished = false; winner = null; banner = null; turnOverridden = false; lastVisitThrows = 0
        visitComplete = false; bullOffActive = false; bullOffQueue.clear(); bullOffDistance.clear(); bullOffOrder = emptyList()
        _throwLog.clear()
        history.forEach { it.clear() }
        out.fill(false); dartsThrown.fill(0); pointsScored.fill(0)
        startBase()
        resetState()
        for (e in copy) when (e) {
            is GameEvent.Throw -> throwDart(e.segment, e.x, e.y, e.at)
            is GameEvent.Next -> next(e.auto)
        }
        if (copy.isEmpty()) banner = null
    }

    /** Beendet die aktuelle Aufnahme und gibt den nächsten Spieler frei. */
    protected fun completeVisit() {
        onVisitEnd()
        lastVisitThrows = visit.size
        visit.clear()
        visitComplete = false
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
            if (i == starter) {
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
        this.starter = starter
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

    /** Zustand initialisieren (auch nach Undo). [starter] ist dann bereits gesetzt. */
    protected abstract fun resetState()

    /** Dart verarbeiten. true = Aufnahme sofort beenden (Bust, Sieg …). */
    protected abstract fun onDart(segment: Segment): Boolean

    /** Aufnahme abgeschlossen (3 Darts, Bust oder manuell "Weiter"). */
    protected open fun onVisitEnd() {}

    /** Alle Spieler haben geworfen; [round] wurde bereits erhöht. */
    protected open fun onRoundCompleted() {}

    protected open fun onLastPlayerStanding(index: Int) {}

    /** Bull-off entschieden: [index] beginnt das Spiel. */
    protected open fun onStarterDecided(index: Int) {}

    /** Ziel für den Bot bei aktuellem Zustand. */
    abstract fun botTarget(): Segment

    /** Spielzustand der Unterklasse; [snapshot] ergänzt Sperre und Bull-off. */
    protected abstract fun buildSnapshot(): GameState

    fun snapshot(): GameState {
        val s = buildSnapshot()
        return if (bullOffActive) s.copy(
            currentPlayer = current, headline = "Bull-off · ${players[current].name}", checkoutHint = null, bullOff = true,
        ) else s.copy(visitLocked = visitComplete)
    }

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
