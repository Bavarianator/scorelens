package com.freedarts.scorer.lens

import android.content.Context
import com.freedarts.scorer.engine.Board
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

/**
 * KI-Erkennung mit dem YOLOv8n-Modell aus dem Projekt "dart-sense" (Ben Willshaw, CC BY-NC 4.0):
 * Klassen 0..3 = Kalibrierpunkte an den Doppelsegment-Ecken von 20, 3, 11, 6; Klasse 4 = Dartspitze.
 * Die Box-Mitte ist jeweils der Punkt (Keypoints als Objekte, wie bei DeepDarts).
 *
 * Läuft vollständig auf dem Gerät (TensorFlow Lite). Fehlt die Modelldatei, ist [available] false
 * und die klassische Erkennung übernimmt.
 */
class YoloDartModel(context: Context) {

    companion object {
        const val ASSET = "dartsense_yolov8n.tflite"
        const val DART_CLASS = 4
        /** Board-Winkel (im Uhrzeigersinn ab oben) der Kalibrierpunkte je Klasse: 20, 3, 11, 6, (dart), 9, 15. */
        val CLASS_ANGLES = mapOf(0 to -9.0, 1 to 171.0, 2 to 261.0, 3 to 81.0, 5 to 297.0, 6 to 117.0)
        val CALIBRATION_CLASSES = listOf(0, 1, 2, 3, 5, 6)
        fun boardPoint(cls: Int): Pair<Double, Double> {
            val a = Math.toRadians(CLASS_ANGLES[cls] ?: 0.0)
            return Board.DOUBLE_OUTER * sin(a) to Board.DOUBLE_OUTER * cos(a)
        }
    }

    data class Point(val x: Double, val y: Double, val conf: Float)
    data class Result(
        /** Kalibrierpunkte je Klasse aus [CALIBRATION_CLASSES] (null = nicht erkannt), Koordinaten im Eingabebild. */
        val calibration: Map<Int, Point>,
        /** Dartspitzen, nach Konfidenz absteigend. */
        val darts: List<Point>,
        val inferenceMs: Long,
    )

    private class Box(val cls: Int, val conf: Float, val cx: Float, val cy: Float, val w: Float, val h: Float)

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    /** "GPU" oder "CPU" – welcher Beschleuniger aktiv ist. */
    var backend: String = "-"; private set
    /** Kantenlänge des quadratischen Modelleingangs, aus dem Modell gelesen (dart-sense: 800). */
    var inputSize = 640; private set
    private var input: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var outShape: IntArray = intArrayOf(1, 11, 8400)
    private var out: Array<Array<FloatArray>>? = null
    /** true = Eingabe [1,3,H,W] (PyTorch-Layout), false = [1,H,W,3]. */
    private var channelsFirstInput = false

    init {
        try {
            val afd = context.assets.openFd(ASSET)
            val channel = FileInputStream(afd.fileDescriptor).channel
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            // Wie Autodarts Lens: Beschleuniger (GPU) nutzen, wenn das Gerät ihn unterstützt; sonst CPU (XNNPACK)
            var created: Interpreter? = null
            try {
                val compat = CompatibilityList()
                if (compat.isDelegateSupportedOnThisDevice) {
                    val delegate = GpuDelegate(compat.bestOptionsForThisDevice)
                    created = Interpreter(buffer, Interpreter.Options().addDelegate(delegate))
                    gpuDelegate = delegate
                    backend = "GPU"
                }
            } catch (e: Throwable) {
                created = null; gpuDelegate = null
            }
            if (created == null) {
                created = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(4) })
                backend = "CPU"
            }
            interpreter = created.also {
                outShape = it.getOutputTensor(0).shape()
                out = Array(1) { Array(outShape[1]) { FloatArray(outShape[2]) } }
                val sh = it.getInputTensor(0).shape()
                channelsFirstInput = sh.size == 4 && sh[1] == 3
                inputSize = if (channelsFirstInput) sh[2] else sh[1]
                input = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
            }
        } catch (e: Exception) {
            interpreter = null
        }
    }

    val available: Boolean get() = interpreter != null

    /**
     * @param rgb aufrechtes Bild (0xRRGGBB), [width]×[height]; Ergebnis-Koordinaten in diesem Bild.
     */
    @Synchronized
    fun detect(rgb: IntArray, width: Int, height: Int, dartConf: Float = 0.3f, calConf: Float = 0.5f): Result? {
        val interp = interpreter ?: return null
        val output = out ?: return null
        val t0 = System.currentTimeMillis()
        val n = inputSize
        // Letterbox wie ultralytics (bilineare Skalierung, Rand 114) – bei Verkleinerung zusätzlich
        // Flächenmittelung, damit dünne Dartspitzen nicht durch Aliasing springen
        val scale = min(n.toDouble() / width, n.toDouble() / height)
        val newW = (width * scale).roundToInt(); val newH = (height * scale).roundToInt()
        val padX = (n - newW) / 2; val padY = (n - newH) / 2
        input.rewind()
        val grayFill = 114f / 255f
        val plane = n * n
        val inv = 1.0 / scale
        val box = if (inv > 1.15) inv else 0.0 // Kantenlänge des Quellfensters (px) bei Verkleinerung
        for (y in 0 until n) {
            val rowOk = y >= padY && y < padY + newH
            val fy = (y - padY + 0.5) * inv - 0.5
            for (x in 0 until n) {
                val r: Float; val g: Float; val b: Float
                if (rowOk && x >= padX && x < padX + newW) {
                    val fx = (x - padX + 0.5) * inv - 0.5
                    val p = if (box > 0) areaSample(rgb, width, height, fx, fy, box) else bilinear(rgb, width, height, fx, fy)
                    r = (p shr 16 and 0xFF) / 255f; g = (p shr 8 and 0xFF) / 255f; b = (p and 0xFF) / 255f
                } else { r = grayFill; g = grayFill; b = grayFill }
                if (channelsFirstInput) {
                    val i = y * n + x
                    input.putFloat(i * 4, r); input.putFloat((plane + i) * 4, g); input.putFloat((2 * plane + i) * 4, b)
                } else { input.putFloat(r); input.putFloat(g); input.putFloat(b) }
            }
        }
        input.rewind()
        interp.run(input, output)

        val channelsFirst = outShape[1] < outShape[2]
        val nBoxes = if (channelsFirst) outShape[2] else outShape[1]
        val nCh = if (channelsFirst) outShape[1] else outShape[2]
        val nCls = nCh - 4
        fun v(ch: Int, i: Int) = if (channelsFirst) output[0][ch][i] else output[0][i][ch]
        // Normierte Koordinaten (0..1) oder Pixel?
        var maxCoord = 0f
        for (i in 0 until nBoxes step 97) maxCoord = max(maxCoord, max(v(0, i), v(1, i)))
        val normalized = maxCoord <= 1.5f
        val f = if (normalized) n.toFloat() else 1f

        val boxes = ArrayList<Box>()
        for (i in 0 until nBoxes) {
            var best = -1; var bestS = 0f
            for (c in 0 until nCls) { val s = v(4 + c, i); if (s > bestS) { bestS = s; best = c } }
            val thr = if (best == DART_CLASS) dartConf else calConf
            if (best < 0 || bestS < min(thr, 0.25f)) continue
            boxes.add(Box(best, bestS, v(0, i) * f, v(1, i) * f, v(2, i) * f, v(3, i) * f))
        }
        boxes.sortByDescending { it.conf }
        // NMS je Klasse
        val kept = ArrayList<Box>()
        for (b in boxes) {
            val overlaps = kept.any { k -> k.cls == b.cls && iou(k, b) > 0.5f }
            if (!overlaps) kept.add(b)
        }
        fun toSrc(b: Box) = Point((b.cx - padX) / scale, (b.cy - padY) / scale, b.conf)
        val calibration = HashMap<Int, Point>()
        for (c in CALIBRATION_CLASSES) kept.filter { it.cls == c && it.conf >= calConf }.maxByOrNull { it.conf }?.let { calibration[c] = toSrc(it) }
        val darts = kept.filter { it.cls == DART_CLASS && it.conf >= dartConf }.map { toSrc(it) }
            .filter { it.x >= 0 && it.y >= 0 && it.x < width && it.y < height }
        return Result(calibration, darts, System.currentTimeMillis() - t0)
    }

    /** Bilinear interpolierter Pixel (0xRRGGBB) an der Position (fx, fy). */
    private fun bilinear(rgb: IntArray, w: Int, h: Int, fx: Double, fy: Double): Int {
        val x0 = fx.toInt().coerceIn(0, w - 1); val y0 = fy.toInt().coerceIn(0, h - 1)
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val ax = (fx - x0).coerceIn(0.0, 1.0); val ay = (fy - y0).coerceIn(0.0, 1.0)
        val p00 = rgb[y0 * w + x0]; val p10 = rgb[y0 * w + x1]; val p01 = rgb[y1 * w + x0]; val p11 = rgb[y1 * w + x1]
        fun ch(shift: Int): Int {
            val top = (p00 shr shift and 0xFF) * (1 - ax) + (p10 shr shift and 0xFF) * ax
            val bottom = (p01 shr shift and 0xFF) * (1 - ax) + (p11 shr shift and 0xFF) * ax
            return (top * (1 - ay) + bottom * ay + 0.5).toInt().coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Mittelwert des Quellfensters mit Kantenlänge [box] um (fx, fy) – Verkleinerung ohne Aliasing. */
    private fun areaSample(rgb: IntArray, w: Int, h: Int, fx: Double, fy: Double, box: Double): Int {
        val half = box / 2
        val x0 = (fx - half + 0.5).toInt().coerceIn(0, w - 1); val x1 = (fx + half + 0.5).toInt().coerceIn(x0 + 1, w)
        val y0 = (fy - half + 0.5).toInt().coerceIn(0, h - 1); val y1 = (fy + half + 0.5).toInt().coerceIn(y0 + 1, h)
        var r = 0; var g = 0; var b = 0; var c = 0
        for (y in y0 until y1) {
            var i = y * w + x0
            for (x in x0 until x1) { val p = rgb[i++]; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; c++ }
        }
        return ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
    }

    private fun iou(a: Box, b: Box): Float {
        val ax1 = a.cx - a.w / 2; val ay1 = a.cy - a.h / 2; val ax2 = a.cx + a.w / 2; val ay2 = a.cy + a.h / 2
        val bx1 = b.cx - b.w / 2; val by1 = b.cy - b.h / 2; val bx2 = b.cx + b.w / 2; val by2 = b.cy + b.h / 2
        val iw = max(0f, min(ax2, bx2) - max(ax1, bx1)); val ih = max(0f, min(ay2, by2) - max(ay1, by1))
        val inter = iw * ih
        val union = (ax2 - ax1) * (ay2 - ay1) + (bx2 - bx1) * (by2 - by1) - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun close() { interpreter?.close(); interpreter = null; gpuDelegate?.close(); gpuDelegate = null }
}
