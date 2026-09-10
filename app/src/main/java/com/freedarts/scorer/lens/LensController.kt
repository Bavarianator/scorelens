package com.freedarts.scorer.lens

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.freedarts.scorer.engine.Board
import com.freedarts.scorer.model.Segment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * "Lens": Kamera starten, Board automatisch finden und kalibrieren, Referenz bei Stillstand setzen,
 * Fokus/Belichtung sperren, danach Darts erkennen.
 *
 * Genauigkeit:
 * - Kalibrierung: KI-Keypoints (6 Punkte) als Start, danach Verfeinerung über Ring-Kanten und Drähte
 *   (hunderte Korrespondenzen, Sub-Millimeter); zeitlicher Median über mehrere Fits.
 * - Darts: Bewegungs-/Stabilitätslogik (klassisch) + KI-Spitzenerkennung auf einem hochaufgelösten
 *   Board-Ausschnitt, bestätigt über mehrere Frames; klassischer Blob als Rückfall.
 * - Takeout: klassisch (leeres Board) und zusätzlich KI-bestätigt (keine Darts mehr sichtbar).
 */
class LensController(private val context: Context) {

    /** Analyse-Auflösung des aufrechten Graubilds (Portrait 3:4). */
    val frameWidth = 360
    val frameHeight = 480

    enum class Setup { OFF, SEARCHING, FOUND, READY }

    data class Status(
        val running: Boolean = false,
        val setup: Setup = Setup.OFF,
        val calibrated: Boolean = false,
        val phase: DartDetector.Phase = DartDetector.Phase.NO_REFERENCE,
        val dartsOnBoard: Int = 0,
        val changeFraction: Double = 0.0,
        val fps: Int = 0,
        val message: String = "Kamera aus",
        val guidance: String = "",
        val quality: BoardFinder.Quality? = null,
        val ellipse: BoardFinder.Ellipse? = null,
        val torch: Boolean = false,
        val autoCalibrate: Boolean = true,
        val autoSensitivity: Boolean = true,
        val ai: Boolean = false,
        val aiMs: Long = 0,
        val aiBackend: String = "-",
        /** Mittlerer Kalibrierfehler in mm (null = unbekannt). */
        val calibResidualMm: Double? = null,
        val cameraSize: String = "",
    )

    data class Detection(val segment: Segment, val imageX: Float, val imageY: Float)

    val detector = DartDetector(frameWidth, frameHeight)
    private val finder = BoardFinder(frameWidth, frameHeight)
    private val yolo = YoloDartModel(context)
    val aiAvailable: Boolean get() = yolo.available

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status
    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections
    private val _throws = MutableSharedFlow<Segment>(extraBufferCapacity = 8)
    val throws: SharedFlow<Segment> = _throws
    private val _takeout = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val takeout: SharedFlow<Unit> = _takeout

    var onCalibrationChanged: ((List<Float>) -> Unit)? = null

    @Volatile var lastFrame: ByteArray = ByteArray(frameWidth * frameHeight); private set
    private val executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var lastFrameTime = 0L
    private var frameCounter = 0
    private var fpsWindowStart = 0L
    private var fpsCache = 0
    @Volatile private var pendingReference = false
    @Volatile var paused = false

    // Automatik / Zustand
    @Volatile private var setup = Setup.OFF
    @Volatile private var autoCalibrate = true
    @Volatile private var autoSensitivity = true
    @Volatile private var preferredTop: Pair<Double, Double>? = null
    private var lastFinderTime = 0L
    private var lastFit: BoardFinder.Fit? = null
    private val fitHistory = ArrayList<List<Pair<Double, Double>>>()
    private var stillFrames = 0
    private var driftCount = 0
    private var torchOn = false
    private var manualSensitivity = 50
    private var aiCalibFails = 0
    private var lastAiMs = 0L
    private var lastAiPoll = 0L
    private var calibResidual: Double? = null
    private var cameraW = 0; private var cameraH = 0

    /** Bereits gezählte Dartspitzen der aktuellen Aufnahme (Analyse-Koordinaten). */
    private val knownTips = ArrayList<Pair<Double, Double>>()
    /** Kandidat für einen neuen Dart, der über mehrere Frames bestätigt wird. */
    private class Candidate(var x: Double, var y: Double, var hits: Int, var tries: Int, val fallback: DartDetector.Event.Dart?, var lastCheck: Long)
    private var candidate: Candidate? = null
    private var aiEmptyPolls = 0

    /** Ringpuffer der letzten Graubilder (für gemittelte Referenz). */
    private val recent = Array(4) { ByteArray(frameWidth * frameHeight) }
    private var recentIdx = 0
    private var recentCount = 0

    var calibrationNormalized: List<Float> = emptyList(); private set
    val isRunning: Boolean get() = provider != null && analysis != null

    // ---------- Öffentliche Steuerung ----------

    fun setSensitivity(percent: Int) {
        manualSensitivity = percent.coerceIn(0, 100)
        if (!autoSensitivity) applyManualSensitivity()
    }

    fun setAutoSensitivity(on: Boolean) {
        autoSensitivity = on
        if (!on) applyManualSensitivity()
        publish()
    }

    private fun applyManualSensitivity() {
        val p = manualSensitivity
        detector.pixelThreshold = 60 - (p * 40 / 100)
        detector.minBlob = 40 - (p * 28 / 100)
    }

    fun setCalibration(points: List<Float>, manual: Boolean = true): Boolean {
        if (points.size != 8) return false
        calibrationNormalized = points
        val img = (0 until 4).map { (points[it * 2] * frameWidth).toDouble() to (points[it * 2 + 1] * frameHeight).toDouble() }
        val ok = detector.calibrate(img)
        if (manual) { autoCalibrate = false; calibResidual = null; if (setup != Setup.OFF) setup = Setup.FOUND; stillFrames = 0 }
        if (ok) { detector.reset(); applyAutoBlob() }
        publish()
        return ok
    }

    fun startSearch() {
        autoCalibrate = true
        preferredTop = null
        lastFit = null; fitHistory.clear(); stillFrames = 0; driftCount = 0; aiCalibFails = 0
        knownTips.clear(); candidate = null; calibResidual = null
        if (setup != Setup.OFF) setup = Setup.SEARCHING
        lockCamera(false)
        publish()
    }

    fun hintTop(nx: Float, ny: Float) {
        preferredTop = (nx * frameWidth).toDouble() to (ny * frameHeight).toDouble()
        lastFit = null; fitHistory.clear()
        autoCalibrate = true
        if (setup != Setup.OFF) setup = Setup.SEARCHING
        publish()
    }

    fun requestReference() { pendingReference = true }

    fun setTorch(on: Boolean) {
        torchOn = on
        camera?.cameraControl?.enableTorch(on)
        publish()
    }

    fun start(owner: LifecycleOwner, previewView: PreviewView?) {
        lifecycleOwner = owner
        setup = if (detector.isCalibrated() && !autoCalibrate) Setup.FOUND else Setup.SEARCHING
        stillFrames = 0
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get()
            bind(previewView)
        }, ContextCompat.getMainExecutor(context))
    }

    fun attachPreview(previewView: PreviewView?, owner: LifecycleOwner? = null) {
        if (owner != null) lifecycleOwner = owner
        if (provider == null) return
        bind(previewView)
    }

    fun stop() {
        provider?.unbindAll()
        camera = null; analysis = null; preview = null
        setup = Setup.OFF
        _status.value = _status.value.copy(running = false, setup = Setup.OFF, message = "Kamera aus", guidance = "")
    }

    // ---------- Kamera ----------

    private fun bind(previewView: PreviewView?) {
        val p = provider ?: return
        val owner = lifecycleOwner ?: return
        p.unbindAll()
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        val a = ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        a.setAnalyzer(executor) { img -> analyze(img) }
        analysis = a
        val useCases = ArrayList<androidx.camera.core.UseCase>()
        useCases.add(a)
        if (previewView != null) {
            val pv = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                .build()
            pv.surfaceProvider = previewView.surfaceProvider
            preview = pv
            useCases.add(pv)
        } else preview = null
        try {
            camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
            if (torchOn) camera?.cameraControl?.enableTorch(true)
            lockCamera(setup == Setup.READY)
            publish()
        } catch (e: Exception) {
            _status.value = _status.value.copy(running = false, message = "Kamera-Fehler: ${e.message}")
        }
    }

    /** Fokus, Belichtung und Weißabgleich auf das Board festlegen (bzw. wieder freigeben). */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun lockCamera(lock: Boolean) {
        val cam = camera ?: return
        try {
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lock)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, lock)
                // Objektivverzerrung vom Kamera-HAL korrigieren lassen (falls unterstützt)
                .setCaptureRequestOption(CaptureRequest.DISTORTION_CORRECTION_MODE, CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY)
                .build()
            Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(opts)
            val a = analysis
            if (lock && a != null && detector.isCalibrated()) {
                val (cx, cy) = detector.boardToImage!!.map(0.0, 0.0)
                val nx = (cx / frameWidth).toFloat().coerceIn(0.05f, 0.95f); val ny = (cy / frameHeight).toFloat().coerceIn(0.05f, 0.95f)
                // Aufrechte Koordinaten → Sensor-Orientierung
                val rot = a.targetRotation
                val (sx, sy) = when (lastRotation) { 90 -> ny to 1 - nx; 270 -> 1 - ny to nx; 180 -> 1 - nx to 1 - ny; else -> nx to ny }
                val point = SurfaceOrientedMeteringPointFactory(1f, 1f, a).createPoint(sx, sy)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).disableAutoCancel().build()
                cam.cameraControl.startFocusAndMetering(action)
                @Suppress("UNUSED_VARIABLE") val unused = rot
            } else if (!lock) cam.cameraControl.cancelFocusAndMetering()
        } catch (_: Exception) { }
    }

    private var lastRotation = 0

    // ---------- Frame-Verarbeitung ----------

    private fun analyze(img: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameTime < 80) return
            lastFrameTime = now
            lastRotation = img.imageInfo.rotationDegrees
            cameraW = img.width; cameraH = img.height
            val idleStill = detector.phase == DartDetector.Phase.IDLE && detector.dartsOnBoard == 0 && detector.lastMotionFraction < 0.003
            val wantColor = autoCalibrate && (setup == Setup.SEARCHING || (setup == Setup.READY && now - lastFinderTime > 4000 && idleStill))
            val (gray, rgb) = extract(img, wantColor)
            lastFrame = gray
            System.arraycopy(gray, 0, recent[recentIdx], 0, gray.size); recentIdx = (recentIdx + 1) % recent.size; recentCount = min(recentCount + 1, recent.size)

            if (pendingReference) { pendingReference = false; setReferenceAveraged(gray); }

            if (rgb != null && now - lastFinderTime > 700) {
                lastFinderTime = now
                calibrateFromFrame(img, rgb, gray)
            }
            if (paused) { publish(); return }

            var ev = detector.process(gray)

            // Automatische Referenz, sobald das Bild ruhig ist
            if (setup == Setup.FOUND && detector.isCalibrated()) {
                if (detector.lastMotionFraction < 0.003) stillFrames++ else stillFrames = 0
                if (stillFrames >= 8) setReferenceAveraged(gray)
            }
            if (autoSensitivity && detector.lastNoiseMean > 0) {
                detector.pixelThreshold = (detector.lastNoiseMean * 5 + 14).toInt().coerceIn(16, 60)
            }

            if (yolo.available && setup == Setup.READY) ev = aiStage(img, gray, ev, now)

            when (ev) {
                is DartDetector.Event.Dart -> {
                    _detections.value = (_detections.value + Detection(ev.segment, ev.imageX, ev.imageY)).takeLast(3)
                    _throws.tryEmit(ev.segment)
                }
                DartDetector.Event.Takeout -> { _detections.value = emptyList(); knownTips.clear(); candidate = null; _takeout.tryEmit(Unit) }
                DartDetector.Event.ReferenceUpdated, DartDetector.Event.Bounce, null -> {}
            }
            frameCounter++
            if (now - fpsWindowStart > 1000) { fpsCache = frameCounter; frameCounter = 0; fpsWindowStart = now }
            publish()
        } finally {
            img.close()
        }
    }

    /** Referenz aus dem Mittel der letzten Frames (rauschärmer). */
    private fun setReferenceAveraged(gray: ByteArray) {
        val avg = ByteArray(gray.size)
        val n = max(1, recentCount)
        for (i in avg.indices) {
            var s = 0
            for (k in 0 until n) s += recent[k][i].toInt() and 0xFF
            avg[i] = (s / n).toByte()
        }
        detector.setReference(if (n >= 2) avg else gray)
        lockCamera(true)
        setup = Setup.READY
        stillFrames = 0
        knownTips.clear(); candidate = null; aiEmptyPolls = 0
    }

    /**
     * KI-Stufe: Dart-Kandidaten über mehrere Frames bestätigen, verpasste Darts nachholen, Takeout bestätigen.
     */
    private fun aiStage(img: ImageProxy, gray: ByteArray, ev: DartDetector.Event?, now: Long): DartDetector.Event? {
        var result: DartDetector.Event? = ev
        when (ev) {
            is DartDetector.Event.Dart -> {
                // Klassisch erkannter Dart → Kandidat, KI bestätigt und präzisiert
                candidate = Candidate(ev.imageX.toDouble(), ev.imageY.toDouble(), 0, 0, ev, 0L)
                result = null
            }
            DartDetector.Event.ReferenceUpdated -> if (detector.dartsOnBoard < 3 && candidate == null) {
                aiNewTip(img, null)?.let { tip -> candidate = Candidate(tip.first, tip.second, 1, 1, null, now) }
                result = null
            }
            DartDetector.Event.Takeout -> { candidate = null; return ev }
            else -> {}
        }
        val c = candidate
        if (c != null) {
            if (now - c.lastCheck >= 120) {
                c.lastCheck = now; c.tries++
                val tip = aiNewTip(img, c.x to c.y)
                if (tip != null) {
                    val d = hypot(tip.first - c.x, tip.second - c.y)
                    if (c.hits == 0 || d < detector.boardRadiusPx * 0.06) {
                        c.hits++
                        c.x = (c.x * (c.hits - 1) + tip.first) / c.hits; c.y = (c.y * (c.hits - 1) + tip.second) / c.hits
                    }
                }
                if (c.hits >= 2) {
                    candidate = null
                    if (c.fallback == null) detector.registerExternalDart(gray)
                    return tipToEvent(c.x to c.y)
                }
                if (c.tries >= 4) {
                    candidate = null
                    return if (c.fallback != null) { knownTips.add(c.fallback.imageX.toDouble() to c.fallback.imageY.toDouble()); c.fallback }
                    else if (c.hits >= 1) { detector.registerExternalDart(gray); tipToEvent(c.x to c.y) } else null
                }
            }
            return result
        }
        // Ruhige Phase: gelegentlich nachsehen, ob die KI etwas sieht, was die Differenz übersehen hat,
        // oder ob alle Darts entfernt wurden.
        if (ev == null && detector.phase == DartDetector.Phase.IDLE && detector.lastMotionFraction < 0.003 && now - lastAiPoll > 1500) {
            lastAiPoll = now
            val (rgb, w, h, ox, oy) = extractBoardRgb(img) ?: return null
            val res = yolo.detect(rgb, w, h) ?: return null
            lastAiMs = res.inferenceMs
            val tips = res.darts.map { toAnalysis(it.x + ox, it.y + oy) }
            val minDist = (detector.boardRadiusPx * 0.045).coerceAtLeast(5.0)
            val fresh = tips.filter { t -> knownTips.none { k -> hypot(k.first - t.first, k.second - t.second) < minDist } }
            if (detector.dartsOnBoard < 3 && fresh.isNotEmpty()) {
                candidate = Candidate(fresh.first().first, fresh.first().second, 1, 1, null, now)
                return null
            }
            if (detector.dartsOnBoard > 0 && tips.isEmpty()) {
                aiEmptyPolls++
                if (aiEmptyPolls >= 2) { aiEmptyPolls = 0; detector.setReference(gray); return DartDetector.Event.Takeout }
            } else aiEmptyPolls = 0
        }
        return result
    }

    private fun tipToEvent(tip: Pair<Double, Double>): DartDetector.Event.Dart? {
        val h = detector.imageToBoard ?: return null
        val (bx, by) = h.map(tip.first + 0.5, tip.second + 0.5)
        knownTips.add(tip)
        return DartDetector.Event.Dart(Board.segmentAt(bx, by), tip.first.toFloat(), tip.second.toFloat(), bx, by)
    }

    private fun toAnalysis(fx: Double, fy: Double): Pair<Double, Double> {
        val rotW = if (lastRotation == 90 || lastRotation == 270) cameraH else cameraW
        val rotH = if (lastRotation == 90 || lastRotation == 270) cameraW else cameraH
        return fx * frameWidth / rotW to fy * frameHeight / rotH
    }

    /** Neue (noch nicht gezählte) Dartspitze per KI, in Analyse-Koordinaten. */
    private fun aiNewTip(img: ImageProxy, near: Pair<Double, Double>?): Pair<Double, Double>? {
        val (rgb, w, h, ox, oy) = extractBoardRgb(img) ?: return null
        val res = yolo.detect(rgb, w, h) ?: return null
        lastAiMs = res.inferenceMs
        val minDist = (detector.boardRadiusPx * 0.045).coerceAtLeast(5.0)
        val fresh = res.darts.map { toAnalysis(it.x + ox, it.y + oy) }.filter { t -> knownTips.none { k -> hypot(k.first - t.first, k.second - t.second) < minDist } }
        if (fresh.isEmpty()) return null
        return if (near != null) fresh.minByOrNull { hypot(it.first - near.first, it.second - near.second) } else fresh.first()
    }

    // ---------- Kalibrierung ----------

    private fun calibrateFromFrame(img: ImageProxy, rgb: IntArray, gray: ByteArray) {
        var fit: BoardFinder.Fit? = null
        if (yolo.available) {
            val (full, w, h, ox, oy) = extractBoardRgb(img, onlyIfCalibrated = setup == Setup.READY) ?: return
            val res = yolo.detect(full, w, h)
            if (res != null) {
                lastAiMs = res.inferenceMs
                val pts = res.calibration
                if (pts.size >= 4) {
                    aiCalibFails = 0
                    val classes = pts.keys.sorted()
                    val imagePts = classes.map { toAnalysis(pts[it]!!.x + ox, pts[it]!!.y + oy) }
                    val boardPts = classes.map { YoloDartModel.boardPoint(it) }
                    val b2i = Homography.from(boardPts, imagePts)
                    if (b2i != null) {
                        // Verfeinerung über Ringkanten; wenn das nicht klappt, KI-Punkte allein
                        fit = finder.refine(rgb, b2i) ?: run {
                            val inv = b2i.inverse()
                            val std = DartDetector.boardPoints().map { (bx, by) -> b2i.map(bx, by) }
                            if (inv == null) null else BoardFinder.Fit(b2i, inv, std, BoardFinder.Ellipse(0.0, 0.0, 0.0, 0.0, 0.0), 2.0, 1.0,
                                if (std.any { it.first < 1 || it.second < 1 || it.first > frameWidth - 2 || it.second > frameHeight - 2 }) BoardFinder.Quality.PARTIAL else BoardFinder.Quality.GOOD, 0.0)
                        }
                    }
                } else aiCalibFails++
            }
        }
        if (fit == null && (!yolo.available || aiCalibFails >= 3)) fit = finder.find(rgb, preferredTop)
        applyFit(fit, gray)
    }

    private fun applyFit(fit: BoardFinder.Fit?, gray: ByteArray) {
        lastFit = fit
        if (fit == null || fit.quality != BoardFinder.Quality.GOOD) { fitHistory.clear(); return }
        // Zeitlicher Median der Kalibrierpunkte (gegen Zittern)
        fitHistory.add(fit.points); if (fitHistory.size > 5) fitHistory.removeAt(0)
        val consistent = fitHistory.size >= 2 && fitHistory.all { f -> f.indices.all { i -> hypot(f[i].first - fit.points[i].first, f[i].second - fit.points[i].second) < 4.0 } }
        if (!consistent) return
        val median = (0 until 4).map { i ->
            val xs = fitHistory.map { it[i].first }.sorted(); val ys = fitHistory.map { it[i].second }.sorted()
            xs[xs.size / 2] to ys[ys.size / 2]
        }
        val norm = median.flatMap { listOf((it.first / frameWidth).toFloat(), (it.second / frameHeight).toFloat()) }
        val current = calibrationNormalized
        when (setup) {
            Setup.SEARCHING -> {
                calibrationNormalized = norm
                detector.calibrate(median)
                calibResidual = fit.residualMm
                applyAutoBlob()
                setup = Setup.FOUND
                stillFrames = 0
                onCalibrationChanged?.invoke(norm)
            }
            Setup.READY -> {
                val moved = current.size == 8 && (0 until 4).any { i ->
                    hypot((current[i * 2] - norm[i * 2]) * frameWidth, (current[i * 2 + 1] - norm[i * 2 + 1]) * frameHeight) > 3.0
                }
                driftCount = if (moved) driftCount + 1 else 0
                if (driftCount >= 2) {
                    calibrationNormalized = norm
                    detector.calibrate(median)
                    calibResidual = fit.residualMm
                    applyAutoBlob()
                    detector.setReference(gray)
                    knownTips.clear(); candidate = null
                    driftCount = 0
                    onCalibrationChanged?.invoke(norm)
                }
            }
            else -> {}
        }
    }

    private fun applyAutoBlob() {
        if (autoSensitivity) detector.minBlob = (detector.boardRadiusPx * 0.05).toInt().coerceIn(8, 40)
    }

    private fun publish() {
        val ph = detector.phase
        val fit = lastFit
        val guidance = when {
            setup == Setup.OFF -> ""
            setup == Setup.READY -> "Bereit – wirf!"
            setup == Setup.FOUND -> "Board erkannt ✓ – kurz ruhig halten"
            fit == null -> if (yolo.available) "Suche Board … ganzes Board ins Bild, gutes Licht" else "Board nicht gefunden: ganzes Board ins Bild, mehr Licht"
            else -> when (fit.quality) {
                BoardFinder.Quality.GOOD -> "Board erkannt ✓"
                BoardFinder.Quality.PARTIAL -> "Weiter weg – das ganze Board muss sichtbar sein"
                BoardFinder.Quality.TOO_SMALL -> "Näher ans Board"
                BoardFinder.Quality.TOO_SKEWED -> "Weniger schräg aufstellen"
                BoardFinder.Quality.INACCURATE -> "Ruhig halten, Licht gleichmäßiger"
                BoardFinder.Quality.NOT_FOUND -> "Board nicht gefunden: ganzes Board ins Bild, mehr Licht"
            }
        }
        _status.value = Status(
            running = isRunning,
            setup = setup,
            calibrated = detector.isCalibrated(),
            phase = ph,
            dartsOnBoard = detector.dartsOnBoard,
            changeFraction = detector.lastChangeFraction,
            fps = fpsCache,
            message = when {
                !isRunning -> "Kamera aus"
                setup == Setup.SEARCHING -> "Suche Board …"
                setup == Setup.FOUND -> "Kalibriert – warte auf Ruhe"
                paused -> "Pausiert"
                candidate != null -> "Prüfe Dart …"
                ph == DartDetector.Phase.TAKEOUT -> "Darts entfernen"
                ph == DartDetector.Phase.MOTION -> "Bewegung …"
                else -> "Bereit · ${detector.dartsOnBoard} Dart(s)"
            },
            guidance = guidance,
            quality = fit?.quality,
            ellipse = fit?.ellipse?.takeIf { it.a > 0 },
            torch = torchOn,
            autoCalibrate = autoCalibrate,
            autoSensitivity = autoSensitivity,
            ai = yolo.available,
            aiMs = lastAiMs,
            aiBackend = yolo.backend,
            calibResidualMm = calibResidual,
            cameraSize = if (cameraW > 0) "${cameraW}×${cameraH}" else "",
        )
    }

    // ---------- Bildzugriff ----------

    private data class Rgb(val pixels: IntArray, val width: Int, val height: Int, val offsetX: Double, val offsetY: Double)

    /**
     * Farbbild des Board-Ausschnitts in voller Kameraauflösung (aufrecht). Ohne Kalibrierung das ganze Bild.
     */
    private fun extractBoardRgb(img: ImageProxy, onlyIfCalibrated: Boolean = false): Rgb? {
        val rot = img.imageInfo.rotationDegrees
        val srcW = img.width; val srcH = img.height
        val rotW = if (rot == 90 || rot == 270) srcH else srcW
        val rotH = if (rot == 90 || rot == 270) srcW else srcH
        var x0 = 0; var y0 = 0; var x1 = rotW; var y1 = rotH
        val b2i = detector.boardToImage
        if (b2i != null && detector.isCalibrated()) {
            val (cx, cy) = b2i.map(0.0, 0.0)
            val r = detector.boardRadiusPx * 1.35
            val fx = rotW.toDouble() / frameWidth; val fy = rotH.toDouble() / frameHeight
            x0 = ((cx - r) * fx).toInt().coerceIn(0, rotW - 1); x1 = ((cx + r) * fx).toInt().coerceIn(x0 + 1, rotW)
            y0 = ((cy - r) * fy).toInt().coerceIn(0, rotH - 1); y1 = ((cy + r) * fy).toInt().coerceIn(y0 + 1, rotH)
        } else if (onlyIfCalibrated) return null
        return try {
            val yPlane = img.planes[0]; val uPlane = img.planes[1]; val vPlane = img.planes[2]
            yPlane.buffer.rewind(); uPlane.buffer.rewind(); vPlane.buffer.rewind()
            val yBytes = ByteArray(yPlane.buffer.remaining()).also { yPlane.buffer.get(it) }
            val uBytes = ByteArray(uPlane.buffer.remaining()).also { uPlane.buffer.get(it) }
            val vBytes = ByteArray(vPlane.buffer.remaining()).also { vPlane.buffer.get(it) }
            val yRow = yPlane.rowStride; val yPix = yPlane.pixelStride
            val uRow = uPlane.rowStride; val uPix = uPlane.pixelStride
            val vRow = vPlane.rowStride; val vPix = vPlane.pixelStride
            val w = x1 - x0; val h = y1 - y0
            val out = IntArray(w * h)
            for (y in 0 until h) {
                val ry = y + y0
                for (x in 0 until w) {
                    val rx = x + x0
                    val sx: Int; val sy: Int
                    when (rot) {
                        90 -> { sx = ry; sy = srcH - 1 - rx }
                        180 -> { sx = srcW - 1 - rx; sy = srcH - 1 - ry }
                        270 -> { sx = srcW - 1 - ry; sy = rx }
                        else -> { sx = rx; sy = ry }
                    }
                    val yi = sy * yRow + sx * yPix
                    val yv = if (yi in yBytes.indices) yBytes[yi].toInt() and 0xFF else 0
                    val ui = (sy / 2) * uRow + (sx / 2) * uPix
                    val vi = (sy / 2) * vRow + (sx / 2) * vPix
                    val u = (if (ui in uBytes.indices) uBytes[ui].toInt() and 0xFF else 128) - 128
                    val v = (if (vi in vBytes.indices) vBytes[vi].toInt() and 0xFF else 128) - 128
                    val r = (yv + 1.402 * v).toInt().coerceIn(0, 255)
                    val g = (yv - 0.344 * u - 0.714 * v).toInt().coerceIn(0, 255)
                    val b = (yv + 1.772 * u).toInt().coerceIn(0, 255)
                    out[y * w + x] = (r shl 16) or (g shl 8) or b
                }
            }
            Rgb(out, w, h, x0.toDouble(), y0.toDouble())
        } catch (e: Exception) { null }
    }

    /**
     * Y-Ebene (und optional Farbe) auf frameWidth×frameHeight skalieren und aufrecht drehen.
     */
    private fun extract(img: ImageProxy, wantColor: Boolean): Pair<ByteArray, IntArray?> {
        val yPlane = img.planes[0]; val uPlane = img.planes[1]; val vPlane = img.planes[2]
        val yBuf = yPlane.buffer; val yRow = yPlane.rowStride; val yPix = yPlane.pixelStride
        val uBuf = uPlane.buffer; val uRow = uPlane.rowStride; val uPix = uPlane.pixelStride
        val vBuf = vPlane.buffer; val vRow = vPlane.rowStride; val vPix = vPlane.pixelStride
        yBuf.rewind(); uBuf.rewind(); vBuf.rewind()
        val yBytes = ByteArray(yBuf.remaining()).also { yBuf.get(it) }
        val uBytes = if (wantColor) ByteArray(uBuf.remaining()).also { uBuf.get(it) } else null
        val vBytes = if (wantColor) ByteArray(vBuf.remaining()).also { vBuf.get(it) } else null
        val srcW = img.width; val srcH = img.height
        val rot = img.imageInfo.rotationDegrees
        val rotW = if (rot == 90 || rot == 270) srcH else srcW
        val rotH = if (rot == 90 || rot == 270) srcW else srcH
        val gray = ByteArray(frameWidth * frameHeight)
        val rgb = if (wantColor) IntArray(frameWidth * frameHeight) else null
        for (y in 0 until frameHeight) {
            val ry = y * rotH / frameHeight
            for (x in 0 until frameWidth) {
                val rx = x * rotW / frameWidth
                val sx: Int; val sy: Int
                when (rot) {
                    90 -> { sx = ry; sy = srcH - 1 - rx }
                    180 -> { sx = srcW - 1 - rx; sy = srcH - 1 - ry }
                    270 -> { sx = srcW - 1 - ry; sy = rx }
                    else -> { sx = rx; sy = ry }
                }
                val yi = sy * yRow + sx * yPix
                val yv = if (yi in yBytes.indices) yBytes[yi].toInt() and 0xFF else 0
                gray[y * frameWidth + x] = yv.toByte()
                if (rgb != null) {
                    val ui = (sy / 2) * uRow + (sx / 2) * uPix
                    val vi = (sy / 2) * vRow + (sx / 2) * vPix
                    val u = (if (ui in uBytes!!.indices) uBytes[ui].toInt() and 0xFF else 128) - 128
                    val v = (if (vi in vBytes!!.indices) vBytes[vi].toInt() and 0xFF else 128) - 128
                    val r = (yv + 1.402 * v).toInt().coerceIn(0, 255)
                    val g = (yv - 0.344 * u - 0.714 * v).toInt().coerceIn(0, 255)
                    val b = (yv + 1.772 * u).toInt().coerceIn(0, 255)
                    rgb[y * frameWidth + x] = (r shl 16) or (g shl 8) or b
                }
            }
        }
        return gray to rgb
    }
}
