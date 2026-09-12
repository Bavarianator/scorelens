package com.freedarts.scorer.lens

import android.content.Context
import android.graphics.Bitmap
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
 * "Lens": Kamera starten, Board finden und kalibrieren, Referenz bei Stillstand setzen,
 * Fokus/Belichtung sperren, danach Darts erkennen.
 *
 * Bausteine: [FrameConverter] (YUV → Grau/Farbe, entzerrter Ausschnitt), [BoardFinder] + [CalibrationTracker]
 * (Kalibrierung mit Ringkanten-Verfeinerung und zeitlichem Median), [DartDetector] (klassische Differenzbild-Logik),
 * [BoardRectifier] (Board-Ausschnitt in Frontalansicht für die KI, damit eine schräg stehende Kamera wie bei
 * Autodarts funktioniert), [YoloDartModel] + [TipTracker] (KI-Spitzen, verfolgt und über mehrere Auswertungen bestätigt).
 * Die KI-Inferenz läuft auf einem eigenen Thread; Ergebnisse kommen über den Analyse-Thread zurück.
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
        /** Kantenlänge des KI-Eingangsbilds (px). */
        val aiInput: Int = 0,
        /** Mittlerer Kalibrierfehler in mm (null = unbekannt). */
        val calibResidualMm: Double? = null,
        val cameraSize: String = "",
        /** Mittlere Helligkeit des Analysebilds 0..255 (Lichtwarnung). */
        val brightness: Int = 0,
        /** Belichtungskorrektur (EV-Index) und erlaubter Bereich der Kamera. */
        val exposure: Int = 0,
        val exposureRange: IntRange = 0..0,
        val frontCamera: Boolean = false,
    )

    /**
     * Erkannter Dart in Analyse-Koordinaten. [snapshot] = Kamera-Ausschnitt um die Spitze (Referee-Bild),
     * [tipX]/[tipY] = Lage der Spitze darin (0..1).
     */
    data class Detection(val segment: Segment, val imageX: Float, val imageY: Float, val snapshot: Bitmap? = null, val tipX: Float = 0.5f, val tipY: Float = 0.5f,
        /** Auftreffpunkt in Board-Millimetern (Mitte 0/0), für Heatmap und /api/state. */
        val boardX: Float? = null, val boardY: Float? = null)
    /** Erkannter Wurf mit Auftreffpunkt in Board-Millimetern (Mitte 0/0). */
    data class Throw(val segment: Segment, val boardX: Float? = null, val boardY: Float? = null)

    val detector = DartDetector(frameWidth, frameHeight)
    private val finder = BoardFinder(frameWidth, frameHeight)
    private val yolo = YoloDartModel(context)
    private val frames = FrameConverter(frameWidth, frameHeight)
    private val tips = TipTracker(detector)
    private val calibration = CalibrationTracker(frameWidth, frameHeight)
    /** Trainingsdaten fürs Feintuning (tools/finetune/), an/aus über die Lens-Einstellungen. */
    val training = TrainingCapture(context)
    val aiAvailable: Boolean get() = yolo.available

    init {
        // KI-Modell im Hintergrund laden (GPU-Delegate nie auf dem Main-Thread erzeugen!)
        yolo.prepareAsync()
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status
    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections
    private val _throws = MutableSharedFlow<Throw>(extraBufferCapacity = 8)
    val throws: SharedFlow<Throw> = _throws
    private val _takeout = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val takeout: SharedFlow<Unit> = _takeout

    var onCalibrationChanged: ((List<Float>) -> Unit)? = null
    /** Wird aufgerufen, sobald Lens nach einer Suche bereit ist (z.B. für Vibration). */
    var onReady: (() -> Unit)? = null

    @Volatile var lastFrame: ByteArray = ByteArray(frameWidth * frameHeight); private set
    private val executor = Executors.newSingleThreadExecutor()
    /** KI-Inferenz läuft getrennt vom Analyse-Thread, damit die Bewegungslogik keine Frames verpasst. */
    private val aiExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var aiBusy = false
    /** Letzter Board-Ausschnitt in Kameraauflösung (Quelle für Referee-Bilder). */
    @Volatile private var lastCrop: RgbFrame? = null
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
    @Volatile var useFrontCamera = false
    var exposure = 0
        set(v) { field = v; applyExposure(); publish() }
    private var brightness = 0
    private var lastPreview: PreviewView? = null

    @Volatile private var setup = Setup.OFF
    @Volatile private var autoCalibrate = true
    @Volatile private var autoSensitivity = true
    @Volatile private var preferredTop: Pair<Double, Double>? = null
    private var lastFinderTime = 0L
    private var lastFit: BoardFinder.Fit? = null
    private var stillFrames = 0
    private var torchOn = false
    private var manualSensitivity = 50
    private var aiCalibFails = 0
    private var lastAiMs = 0L
    private var calibResidual: Double? = null
    private var cameraW = 0; private var cameraH = 0
    private var lastRotation = 0

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
        lastFit = null; calibration.clear(); stillFrames = 0; aiCalibFails = 0
        tips.reset(); calibResidual = null
        if (setup != Setup.OFF) setup = Setup.SEARCHING
        lockCamera(false)
        publish()
    }

    fun hintTop(nx: Float, ny: Float) {
        preferredTop = (nx * frameWidth).toDouble() to (ny * frameHeight).toDouble()
        lastFit = null; calibration.clear()
        autoCalibrate = true
        if (setup != Setup.OFF) setup = Setup.SEARCHING
        publish()
    }

    fun requestReference() { pendingReference = true }

    fun setFrontCamera(on: Boolean) {
        useFrontCamera = on
        if (provider != null) bind(lastPreview)
    }

    private fun applyExposure() {
        val cam = camera ?: return
        val st = cam.cameraInfo.exposureState
        if (!st.isExposureCompensationSupported) return
        runCatching { cam.cameraControl.setExposureCompensationIndex(exposure.coerceIn(st.exposureCompensationRange.lower, st.exposureCompensationRange.upper)) }
    }

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
        lastPreview = previewView
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
            camera = p.bindToLifecycle(owner, if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
            if (torchOn) camera?.cameraControl?.enableTorch(true)
            applyExposure()
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
                val (sx, sy) = when (lastRotation) { 90 -> ny to 1 - nx; 270 -> 1 - ny to nx; 180 -> 1 - nx to 1 - ny; else -> nx to ny }
                val point = SurfaceOrientedMeteringPointFactory(1f, 1f, a).createPoint(sx, sy)
                cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).disableAutoCancel().build())
            } else if (!lock) cam.cameraControl.cancelFocusAndMetering()
        } catch (_: Exception) { }
    }

    // ---------- Frame-Verarbeitung ----------

    private fun analyze(img: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameTime < 80) return
            lastFrameTime = now
            lastRotation = img.imageInfo.rotationDegrees
            cameraW = img.width; cameraH = img.height
            val idleStill = detector.phase == DartDetector.Phase.IDLE && detector.lastMotionFraction < 0.003 && !tips.checking
            val wantColor = autoCalibrate && (setup == Setup.SEARCHING || (setup == Setup.READY && now - lastFinderTime > 4000 && idleStill))
            val (gray, rgb) = frames.scaled(img, wantColor)
            lastFrame = gray
            // ponytail: Helligkeit = Mittel jedes 61. Pixels; Gegenlicht/Teilschatten bräuchten eine Verteilung
            if (frameCounter % 8 == 0) { var s = 0L; var i = 0; while (i < gray.size) { s += gray[i].toInt() and 0xFF; i += 61 }; brightness = (s / (gray.size / 61 + 1)).toInt() }
            System.arraycopy(gray, 0, recent[recentIdx], 0, gray.size); recentIdx = (recentIdx + 1) % recent.size; recentCount = min(recentCount + 1, recent.size)

            if (pendingReference) { pendingReference = false; setReferenceAveraged(gray) }
            if (rgb != null && now - lastFinderTime > 700) {
                lastFinderTime = now
                calibrateFromFrame(img, rgb, gray)
            }

            var ev = detector.process(gray)

            // Automatische Referenz, sobald das Bild ruhig ist
            if (setup == Setup.FOUND && detector.isCalibrated()) {
                if (detector.lastMotionFraction < 0.003) stillFrames++ else stillFrames = 0
                if (stillFrames >= 8) setReferenceAveraged(gray)
            }
            if (autoSensitivity && detector.lastNoiseMean > 0) {
                detector.pixelThreshold = (detector.lastNoiseMean * 5 + 14).toInt().coerceIn(16, 60)
            }
            if (yolo.available && setup == Setup.READY) {
                ev = tips.step(ev, gray, now)
                if (!aiBusy && tips.wantsInference(now)) boardRgb(img)?.let { crop ->
                    lastCrop = crop
                    aiBusy = true
                    aiExecutor.execute { inferAsync(crop) }
                }
            }
            emit(ev)
            frameCounter++
            if (now - fpsWindowStart > 1000) { fpsCache = frameCounter; frameCounter = 0; fpsWindowStart = now }
            publish()
        } finally {
            img.close()
        }
    }

    private fun emit(ev: DartDetector.Event?) {
        when (ev) {
            is DartDetector.Event.Dart -> {
                val snap = snapshot(ev.imageX, ev.imageY)
                _detections.value = (_detections.value + Detection(ev.segment, ev.imageX, ev.imageY, snap?.first, snap?.second ?: 0.5f, snap?.third ?: 0.5f, ev.boardX.toFloat(), ev.boardY.toFloat())).takeLast(3)
                _throws.tryEmit(Throw(ev.segment, ev.boardX.toFloat(), ev.boardY.toFloat()))
            }
            DartDetector.Event.Takeout -> { _detections.value = emptyList(); tips.reset(); _takeout.tryEmit(Unit) }
            // Bounce-Out: Dart war kurz da und ist wieder weg – zählt als Miss (0 Punkte)
            DartDetector.Event.Bounce -> _throws.tryEmit(Throw(Segment.MISS))
            DartDetector.Event.ReferenceUpdated, null -> {}
        }
    }

    /** Läuft auf [aiExecutor]; das Ergebnis wird auf den Analyse-Thread zurückgegeben. */
    private fun inferAsync(rgb: RgbFrame) {
        val res = try { yolo.detect(rgb.pixels, rgb.width, rgb.height) } catch (e: Exception) { null }
        executor.execute {
            aiBusy = false
            if (res == null) return@execute
            lastAiMs = res.inferenceMs
            if (setup != Setup.READY) return@execute
            val list = res.darts.map { p -> analysisPoint(rgb, p.x, p.y).let { (x, y) -> TipTracker.Tip(x, y, p.conf) } }
            val ev = tips.onTips(list, lastFrame, System.currentTimeMillis())
            // Trainingsdaten: genau der Ausschnitt, den das Modell gesehen hat, sobald ein Dart bestätigt ist
            if (ev is DartDetector.Event.Dart && training.enabled) aiExecutor.execute { training.save(rgb, res) }
            emit(ev)
            publish()
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
        val wasReady = setup == Setup.READY
        setup = Setup.READY
        stillFrames = 0
        tips.reset()
        if (!wasReady) onReady?.invoke()
    }

    /**
     * Referee-Bild: Ausschnitt um die Spitze (Analyse-Koordinaten [ax]/[ay]) aus dem letzten Board-Ausschnitt in
     * Kameraauflösung, sonst aus dem Graubild. Liefert Bitmap und Lage der Spitze darin (0..1).
     */
    private fun snapshot(ax: Float, ay: Float): Triple<Bitmap, Float, Float>? = try {
        val crop = lastCrop
        val rotW = if (lastRotation == 90 || lastRotation == 270) cameraH else cameraW
        val rotH = if (lastRotation == 90 || lastRotation == 270) cameraW else cameraH
        if (crop != null && rotW > 0) {
            val (px, py) = crop.fromUpright.map(ax.toDouble() * rotW / frameWidth, ay.toDouble() * rotH / frameHeight)
            val half = (detector.boardRadiusPx * rotW / frameWidth * 0.16 / crop.scale).toInt().coerceIn(40, 400)
            window(crop.pixels, crop.width, crop.height, px.toInt(), py.toInt(), half)
        } else {
            val gray = lastFrame
            val argb = IntArray(gray.size) { val v = gray[it].toInt() and 0xFF; (v shl 16) or (v shl 8) or v }
            window(argb, frameWidth, frameHeight, ax.toInt(), ay.toInt(), (detector.boardRadiusPx * 0.16).toInt().coerceIn(20, 200))
        }
    } catch (e: Exception) { null }

    private fun window(src: IntArray, w: Int, h: Int, cx: Int, cy: Int, half: Int): Triple<Bitmap, Float, Float> {
        val x0 = (cx - half).coerceIn(0, w - 1); val x1 = (cx + half).coerceIn(x0 + 1, w)
        val y0 = (cy - half).coerceIn(0, h - 1); val y1 = (cy + half).coerceIn(y0 + 1, h)
        val ww = x1 - x0; val hh = y1 - y0
        val out = IntArray(ww * hh)
        for (y in 0 until hh) for (x in 0 until ww) out[y * ww + x] = src[(y + y0) * w + x + x0] or (0xFF shl 24)
        return Triple(Bitmap.createBitmap(out, ww, hh, Bitmap.Config.ARGB_8888), (cx - x0).toFloat() / ww, (cy - y0).toFloat() / hh)
    }

    /** Punkt eines KI-Bilds ([frame]-Pixel) → Analyse-Koordinaten. */
    private fun analysisPoint(frame: RgbFrame, x: Double, y: Double): Pair<Double, Double> {
        val (ux, uy) = frame.toUpright.map(x, y)
        return toAnalysis(ux, uy)
    }

    /**
     * Farbbild fürs KI-Modell in voller Kameraauflösung: mit Kalibrierung der **entzerrte** Board-Ausschnitt
     * (Board als Kreis in der Mitte, 20 oben – so wie das Modell trainiert wurde, egal wie schräg die Kamera
     * steht), ohne Kalibrierung das ganze Bild.
     */
    private fun boardRgb(img: ImageProxy, onlyIfCalibrated: Boolean = false): RgbFrame? {
        val (rotW, rotH) = frames.uprightSize(img)
        val b2i = detector.boardToImage
        if (b2i == null || !detector.isCalibrated()) return if (onlyIfCalibrated) null else frames.crop(img, 0, 0, rotW, rotH)
        val plan = BoardRectifier.plan(b2i, frameWidth, frameHeight, rotW, rotH)
        return frames.warp(img, plan.size, plan.toUpright, plan.scale)
    }

    /** Punkt im aufrechten Vollbild → Analyse-Koordinaten. */
    private fun toAnalysis(fx: Double, fy: Double): Pair<Double, Double> {
        val rotW = if (lastRotation == 90 || lastRotation == 270) cameraH else cameraW
        val rotH = if (lastRotation == 90 || lastRotation == 270) cameraW else cameraH
        return fx * frameWidth / rotW to fy * frameHeight / rotH
    }

    // ---------- Kalibrierung ----------

    /**
     * Kalibrierung: die KI-Inferenz läuft wie die Dart-Erkennung auf [aiExecutor] (teilt sich [aiBusy]), damit der
     * Analyse-Thread keine Frames verpasst; die Auswertung kommt auf den Analyse-Thread zurück.
     */
    private fun calibrateFromFrame(img: ImageProxy, rgb: IntArray, gray: ByteArray) {
        if (!yolo.available) { applyFit(finder.find(rgb, preferredTop), gray, emptyList()); return }
        if (aiBusy) return
        val full = boardRgb(img, onlyIfCalibrated = setup == Setup.READY) ?: return
        aiBusy = true
        aiExecutor.execute {
            val res = try { yolo.detect(full.pixels, full.width, full.height) } catch (e: Exception) { null }
            executor.execute {
                aiBusy = false
                if (setup == Setup.OFF) return@execute
                var fit: BoardFinder.Fit? = null
                if (res != null) {
                    lastAiMs = res.inferenceMs
                    val pts = res.calibration
                    if (pts.size >= 4) {
                        aiCalibFails = 0
                        val classes = pts.keys.sorted()
                        val imagePts = classes.map { analysisPoint(full, pts[it]!!.x, pts[it]!!.y) }
                        val b2i = Homography.from(classes.map { YoloDartModel.boardPoint(it) }, imagePts)
                        // Verfeinerung über Ringkanten; wenn das nicht klappt, KI-Punkte allein
                        if (b2i != null) fit = finder.refine(rgb, b2i) ?: fitFromHomography(b2i)
                    } else aiCalibFails++
                }
                if (fit == null && aiCalibFails >= 3) fit = finder.find(rgb, preferredTop)
                val seen = res?.darts?.map { p -> analysisPoint(full, p.x, p.y).let { (x, y) -> TipTracker.Tip(x, y, p.conf) } } ?: emptyList()
                applyFit(fit, gray, seen)
            }
        }
    }

    /** Fit allein aus KI-Kalibrierpunkten; die Ellipse wird aus der Homographie abgeleitet, damit die Schrägheits-Hinweise greifen. */
    private fun fitFromHomography(b2i: Homography): BoardFinder.Fit? {
        val inv = b2i.inverse() ?: return null
        val pts = DartDetector.boardPoints().map { (bx, by) -> b2i.map(bx, by) }
        val partial = pts.any { it.first < 1 || it.second < 1 || it.first > frameWidth - 2 || it.second > frameHeight - 2 }
        val ell = finder.ellipseFor(b2i) ?: BoardFinder.Ellipse(0.0, 0.0, 0.0, 0.0, 0.0)
        val (cx, cy) = b2i.map(0.0, 0.0); val (ex, ey) = b2i.map(Board.DOUBLE_OUTER, 0.0)
        val quality = when {
            partial -> BoardFinder.Quality.PARTIAL
            ell.a > 0 && maxOf(ell.a, ell.b) < 0.2 * minOf(frameWidth, frameHeight) -> BoardFinder.Quality.TOO_SMALL
            else -> BoardFinder.Quality.GOOD
        }
        return BoardFinder.Fit(b2i, inv, pts, ell, 2.0, 1.0, quality, hypot(ex - cx, ey - cy))
    }

    /** [seen] = KI-Spitzen derselben Inferenz in Analyse-Koordinaten, für die Neuzuordnung nach Kamerabewegung. */
    private fun applyFit(fit: BoardFinder.Fit?, gray: ByteArray, seen: List<TipTracker.Tip>) {
        lastFit = fit
        if (fit == null || fit.quality != BoardFinder.Quality.GOOD) { calibration.clear(); return }
        val median = calibration.stable(fit.points) ?: return
        val norm = calibration.normalized(median)
        when (setup) {
            Setup.SEARCHING -> {
                calibrate(median, norm, fit.residualMm)
                setup = Setup.FOUND
                stillFrames = 0
            }
            Setup.READY -> if (calibration.moved(calibrationNormalized, norm)) {
                // Kamera wurde bewegt: neu kalibrieren, Darts auf dem Board neu zuordnen (nichts geht verloren)
                val boardTips = tips.knownOnBoard()
                calibrate(median, norm, fit.residualMm)
                detector.setReferenceKeepingDarts(gray)
                tips.resync(boardTips, seen, System.currentTimeMillis())
            }
            else -> {}
        }
    }

    private fun calibrate(points: List<Pair<Double, Double>>, norm: List<Float>, residualMm: Double) {
        calibrationNormalized = norm
        detector.calibrate(points)
        calibResidual = residualMm
        applyAutoBlob()
        onCalibrationChanged?.invoke(norm)
    }

    private fun applyAutoBlob() {
        if (autoSensitivity) detector.minBlob = (detector.boardRadiusPx * 0.05).toInt().coerceIn(8, 40)
    }

    private fun publish() {
        val fit = lastFit
        _status.value = Status(
            running = isRunning,
            setup = setup,
            calibrated = detector.isCalibrated(),
            phase = detector.phase,
            dartsOnBoard = detector.dartsOnBoard,
            changeFraction = detector.lastChangeFraction,
            fps = fpsCache,
            message = LensMessages.status(isRunning, setup, tips.checking, detector.phase, detector.dartsOnBoard),
            guidance = LensMessages.guidance(setup, fit, yolo.available, brightness),
            quality = fit?.quality,
            ellipse = fit?.ellipse?.takeIf { it.a > 0 },
            torch = torchOn,
            autoCalibrate = autoCalibrate,
            autoSensitivity = autoSensitivity,
            ai = yolo.available,
            aiMs = lastAiMs,
            aiBackend = yolo.backend,
            aiInput = yolo.inputSize,
            calibResidualMm = calibResidual,
            cameraSize = if (cameraW > 0) "${cameraW}×${cameraH}" else "",
            brightness = brightness,
            exposure = exposure,
            exposureRange = camera?.cameraInfo?.exposureState?.takeIf { it.isExposureCompensationSupported }?.exposureCompensationRange?.let { it.lower..it.upper } ?: 0..0,
            frontCamera = useFrontCamera,
        )
    }
}
