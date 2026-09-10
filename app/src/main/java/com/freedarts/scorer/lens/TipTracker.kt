package com.freedarts.scorer.lens

import com.freedarts.scorer.engine.Board
import kotlin.math.hypot

/**
 * KI-Stufe der Dart-Erkennung.
 *
 * - Bekannte (gezählte) Darts werden verfolgt: jede KI-Auswertung ordnet die erkannten Spitzen den bekannten
 *   Darts per Nächster-Nachbar-Zuordnung zu (kürzeste Abstände zuerst). Was übrig bleibt, ist neu – auch
 *   wenn es wenige Millimeter neben einem bekannten Dart steckt (Gruppierung).
 * - Ein neuer Dart zählt erst, wenn seine Spitze über mehrere Auswertungen an derselben Stelle liegt
 *   (Median der Messungen). Nahe an einem Draht werden mehr Messungen verlangt; ein KI-Fund ohne
 *   klassisches Ereignis ebenfalls (Schutz vor Fehlerkennungen).
 * - Landet ein weiterer Dart, während der vorige noch geprüft wird, wird der vorige mit dem bisherigen
 *   Stand abgeschlossen – es geht kein Dart verloren.
 * - In ruhigen Phasen wird nach übersehenen Darts und nach dem leeren Board (Takeout) gesucht.
 *
 * Reines Kotlin. Die Inferenz läuft außerhalb: [wantsInference] sagt, wann eine Auswertung sinnvoll ist,
 * [onTips] nimmt das Ergebnis entgegen (Spitzen in Analyse-Koordinaten).
 */
class TipTracker(private val detector: DartDetector) {

    companion object {
        const val CHECK_INTERVAL_MS = 120L
        const val IDLE_POLL_MS = 1500L
        /** Messungen für einen Dart, den die klassische Erkennung gemeldet hat. */
        const val HITS = 3
        /** Messungen für einen Dart, den nur die KI gefunden hat. */
        const val HITS_UNPROMPTED = 4
        const val HITS_NEAR_WIRE = 5
        const val MAX_TRIES = 6
        const val MAX_TRIES_NEAR_WIRE = 9
        /** Abstand zur Segmentgrenze (mm), unterhalb dessen zusätzliche Messungen verlangt werden. */
        const val WIRE_MM = 1.5
        const val MAX_DARTS = 3
        /** Mindestkonfidenz, damit ein KI-Fund ohne Bewegungsereignis überhaupt Kandidat wird. */
        const val MIN_UNPROMPTED_CONF = 0.5f
    }

    /** KI-Spitze in Analyse-Koordinaten. */
    data class Tip(val x: Double, val y: Double, val conf: Float = 1f)

    private class Known(var x: Double, var y: Double) { var missing = 0 }

    private class Candidate(val fallback: DartDetector.Event.Dart?, private val initial: Pair<Double, Double>, var lastCheck: Long) {
        val samples = ArrayList<Pair<Double, Double>>()
        var tries = 0
        val prompted: Boolean get() = fallback != null
        fun center(): Pair<Double, Double> = if (samples.isEmpty()) initial else median(samples)
    }

    private val known = ArrayList<Known>()
    /** Bereits gezählte Dartspitzen der aktuellen Aufnahme (Analyse-Koordinaten). */
    val knownTips: List<Pair<Double, Double>> get() = known.map { it.x to it.y }
    private var candidate: Candidate? = null
    /** Ein Dart wird gerade über mehrere Auswertungen bestätigt. */
    val checking: Boolean get() = candidate != null
    private var emptyPolls = 0
    private var lastPoll = 0L
    private var afterReference = false

    fun reset() { known.clear(); candidate = null; emptyPolls = 0; afterReference = false }

    /**
     * Klassisches Ereignis verarbeiten. Ein Dart-Ereignis wird zurückgehalten, bis die KI es bestätigt hat;
     * zurück kommt höchstens ein sofort gültiges Ereignis (Takeout oder der zuvor wartende Dart).
     */
    fun step(ev: DartDetector.Event?, gray: ByteArray, now: Long): DartDetector.Event? = when (ev) {
        is DartDetector.Event.Dart -> {
            val previous = candidate?.takeIf { it.prompted || it.samples.size >= 2 }?.let { finish(it, gray) }
            candidate = Candidate(ev, ev.imageX.toDouble() to ev.imageY.toDouble(), 0L)
            previous
        }
        DartDetector.Event.Takeout -> { reset(); ev }
        DartDetector.Event.ReferenceUpdated -> { if (known.size < MAX_DARTS && candidate == null) afterReference = true; null }
        else -> null
    }

    /** true, wenn jetzt eine KI-Auswertung sinnvoll ist: Kandidat prüfen, nach Referenzwechsel oder in ruhiger Phase. */
    fun wantsInference(now: Long): Boolean {
        val c = candidate
        if (c != null) return now - c.lastCheck >= CHECK_INTERVAL_MS
        if (afterReference) return true
        return detector.phase == DartDetector.Phase.IDLE && detector.lastMotionFraction < 0.003 && now - lastPoll > IDLE_POLL_MS
    }

    /** Ergebnis einer KI-Auswertung; [gray] ist der aktuelle Frame (wird bei einer Zählung Referenz). */
    fun onTips(tips: List<Tip>, gray: ByteArray, now: Long): DartDetector.Event? {
        afterReference = false
        lastPoll = now
        val fresh = assign(tips)
        candidate?.let { return check(it, fresh, gray, now) }
        val best = fresh.filter { it.conf >= MIN_UNPROMPTED_CONF }.maxByOrNull { it.conf }
        if (known.size < MAX_DARTS && best != null) {
            candidate = Candidate(null, best.x to best.y, now).also { it.samples.add(best.x to best.y) }
            return null
        }
        if (known.isNotEmpty() && tips.isEmpty()) {
            emptyPolls++
            if (emptyPolls >= 2) { emptyPolls = 0; detector.setReference(gray); known.clear(); return DartDetector.Event.Takeout }
        } else emptyPolls = 0
        return null
    }

    /** Erkannte Spitzen den bekannten Darts zuordnen (kürzeste Abstände zuerst); Rückgabe: die neuen Spitzen. */
    private fun assign(tips: List<Tip>): List<Tip> {
        val tol = (detector.boardRadiusPx * 0.05).coerceAtLeast(5.0)
        val pairs = ArrayList<Triple<Double, Int, Int>>()
        for ((ki, k) in known.withIndex()) for ((ti, t) in tips.withIndex()) {
            val d = hypot(t.x - k.x, t.y - k.y)
            if (d < tol) pairs.add(Triple(d, ki, ti))
        }
        pairs.sortBy { it.first }
        val kDone = BooleanArray(known.size); val tDone = BooleanArray(tips.size)
        for ((_, ki, ti) in pairs) {
            if (kDone[ki] || tDone[ti]) continue
            kDone[ki] = true; tDone[ti] = true
            val k = known[ki]; val t = tips[ti]
            k.x = k.x * 0.7 + t.x * 0.3; k.y = k.y * 0.7 + t.y * 0.3; k.missing = 0
        }
        for ((ki, k) in known.withIndex()) if (!kDone[ki]) k.missing++
        return tips.filterIndexed { i, _ -> !tDone[i] }
    }

    private fun check(c: Candidate, fresh: List<Tip>, gray: ByteArray, now: Long): DartDetector.Event? {
        c.lastCheck = now; c.tries++
        val center = c.center()
        fresh.minByOrNull { hypot(it.x - center.first, it.y - center.second) }?.let { t ->
            if (c.samples.isEmpty() || hypot(t.x - center.first, t.y - center.second) < detector.boardRadiusPx * 0.06) c.samples.add(t.x to t.y)
        }
        val nearWire = c.samples.isNotEmpty() && wireDistanceMm(c.center()) < WIRE_MM
        val needed = when { nearWire -> HITS_NEAR_WIRE; !c.prompted -> HITS_UNPROMPTED; else -> HITS }
        val maxTries = if (nearWire) MAX_TRIES_NEAR_WIRE else MAX_TRIES
        if (c.samples.size < needed && c.tries < maxTries) return null
        candidate = null
        return finish(c, gray)
    }

    /** Kandidat mit dem vorhandenen Stand abschließen: KI-Median, sonst klassische Spitze, sonst verwerfen. */
    private fun finish(c: Candidate, gray: ByteArray): DartDetector.Event? = when {
        c.samples.isNotEmpty() -> {
            if (c.fallback == null) detector.registerExternalDart(gray)
            accept(c.center())
        }
        c.fallback != null -> { known.add(Known(c.fallback.imageX.toDouble(), c.fallback.imageY.toDouble())); c.fallback }
        else -> null
    }

    private fun accept(tip: Pair<Double, Double>): DartDetector.Event.Dart? {
        val h = detector.imageToBoard ?: return null
        val (bx, by) = h.map(tip.first + 0.5, tip.second + 0.5)
        known.add(Known(tip.first, tip.second))
        return DartDetector.Event.Dart(Board.segmentAt(bx, by), tip.first.toFloat(), tip.second.toFloat(), bx, by)
    }

    private fun wireDistanceMm(tip: Pair<Double, Double>): Double {
        val h = detector.imageToBoard ?: return Double.MAX_VALUE
        val (bx, by) = h.map(tip.first + 0.5, tip.second + 0.5)
        return Board.distanceToWire(bx, by)
    }

    /** Board-Positionen (mm) der gezählten Spitzen mit der aktuellen Kalibrierung. */
    fun knownOnBoard(): List<Pair<Double, Double>> {
        val inv = detector.imageToBoard ?: return emptyList()
        return known.map { inv.map(it.x, it.y) }
    }

    /**
     * Nach einer Kamerabewegung: bekannte Darts (Board-mm) den jetzt sichtbaren Spitzen [seen] zuordnen;
     * nicht (mehr) sichtbare behalten ihre Board-Position, neu sichtbare (vorher verdeckte) werden Kandidat.
     */
    fun resync(boardTips: List<Pair<Double, Double>>, seen: List<Tip>, now: Long) {
        val inv = detector.imageToBoard ?: return
        val b2i = detector.boardToImage ?: return
        known.clear()
        val unmatched = ArrayList(seen)
        for (bt in boardTips) {
            val (px, py) = b2i.map(bt.first, bt.second)
            val near = unmatched.minByOrNull { hypot(it.x - px, it.y - py) }
            if (near != null) {
                val (bx, by) = inv.map(near.x, near.y)
                if (hypot(bx - bt.first, by - bt.second) < 14.0) { known.add(Known(near.x, near.y)); unmatched.remove(near); continue }
            }
            known.add(Known(px, py))
        }
        if (unmatched.isNotEmpty() && known.size < MAX_DARTS) {
            val t = unmatched.first()
            candidate = Candidate(null, t.x to t.y, now).also { it.samples.add(t.x to t.y) }
        }
    }
}

internal fun median(pts: List<Pair<Double, Double>>): Pair<Double, Double> {
    val xs = pts.map { it.first }.sorted(); val ys = pts.map { it.second }.sorted()
    val n = xs.size
    return if (n % 2 == 1) xs[n / 2] to ys[n / 2] else (xs[n / 2 - 1] + xs[n / 2]) / 2 to (ys[n / 2 - 1] + ys[n / 2]) / 2
}
