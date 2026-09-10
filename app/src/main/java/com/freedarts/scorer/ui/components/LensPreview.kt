package com.freedarts.scorer.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.freedarts.scorer.engine.Board
import com.freedarts.scorer.lens.BoardFinder
import com.freedarts.scorer.lens.DartDetector
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.ui.theme.DartColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Kamera-Vorschau (3:4) mit Overlay: erkannte Board-Ellipse, projizierte Drähte, erkannte Darts,
 * optional ziehbare Kalibrierpunkte. Koordinaten normiert (0..1) auf das aufrechte Analysebild.
 */
@Composable
fun LensPreview(
    lens: LensController,
    calibration: List<Float>,
    detections: List<LensController.Detection>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    status: LensController.Status? = null,
    /** Live-Bild auf die Scheibe zuschneiden (quadratisch), sobald kalibriert. */
    cropToBoard: Boolean = false,
    onTap: ((Float, Float) -> Unit)? = null,
    onCalibrationChange: (List<Float>) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var dragging by remember { mutableIntStateOf(-1) }

    DisposableEffect(previewView) {
        lens.attachPreview(previewView, owner)
        onDispose { lens.attachPreview(null, owner) }
    }

    val b2i = if (lens.detector.isCalibrated()) lens.detector.boardToImage else null
    // Ausschnitt: Quadrat um das Board (Radius 170 mm = boardRadiusPx, plus Zahlenring)
    val crop = if (cropToBoard && b2i != null && lens.detector.boardRadiusPx > 0) {
        val (cx, cy) = b2i.map(0.0, 0.0)
        val r = lens.detector.boardRadiusPx * 1.32
        Triple(cx, cy, r)
    } else null
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(if (crop != null) 1f else 3f / 4f).clipToBounds()) {
        val outerW = maxWidth
        val innerW = outerW
        val innerH = outerW * 4f / 3f
        val innerWpx = with(LocalDensity.current) { innerW.toPx() }
        // Frame → innere Box: Faktor k; Zuschnitt: Skalierung s und Verschiebung t (Ursprung oben links)
        val k = innerWpx / lens.frameWidth
        val scale = if (crop != null) (lens.frameWidth / (2 * crop.third)).toFloat() else 1f
        val tx = if (crop != null) (innerWpx / 2 - scale * crop.first * k).toFloat() else 0f
        val ty = if (crop != null) (innerWpx / 2 - scale * crop.second * k).toFloat() else 0f
        Box(
            Modifier.requiredSize(innerW, innerH).graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale; scaleY = scale; translationX = tx; translationY = ty
            },
        ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        val textPaint = remember {
            android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; textSize = 30f; isFakeBoldText = true; setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK) }
        }
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(editable, calibration) {
                    if (!editable) return@pointerInput
                    detectDragGestures(
                        onDragStart = { pos ->
                            var best = -1; var bestD = 60f * density
                            for (i in 0 until 4) {
                                val px = calibration.getOrElse(i * 2) { 0.5f } * size.width
                                val py = calibration.getOrElse(i * 2 + 1) { 0.5f } * size.height
                                val d = hypot(pos.x - px, pos.y - py)
                                if (d < bestD) { bestD = d; best = i }
                            }
                            dragging = best
                        },
                        onDragEnd = { dragging = -1 },
                        onDragCancel = { dragging = -1 },
                    ) { change, drag ->
                        change.consume()
                        val i = dragging
                        if (i >= 0) {
                            val list = calibration.toMutableList()
                            list[i * 2] = (list[i * 2] + drag.x / size.width).coerceIn(0f, 1f)
                            list[i * 2 + 1] = (list[i * 2 + 1] + drag.y / size.height).coerceIn(0f, 1f)
                            onCalibrationChange(list)
                        }
                    }
                }
                .pointerInput(editable, onTap) {
                    if (editable || onTap == null) return@pointerInput
                    detectTapGestures { pos -> onTap(pos.x / size.width, pos.y / size.height) }
                }
        ) {
            val w = size.width; val h = size.height
            fun toView(ix: Double, iy: Double) = Offset((ix / lens.frameWidth * w).toFloat(), (iy / lens.frameHeight * h).toFloat())

            // Positionierungshilfe während der Suche: Zielkreis, in den das Board passen soll
            if (status?.setup == LensController.Setup.SEARCHING && b2i == null) {
                drawCircle(Color(0x80FFFFFF), radius = w * 0.36f, center = Offset(w / 2, h / 2),
                    style = Stroke(3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f, 14f))))
            }
            // Erkannte Ellipse (Suchphase)
            val ell = status?.ellipse
            if (ell != null && b2i == null) {
                val col = if (status.quality == BoardFinder.Quality.GOOD) DartColors.Lime else DartColors.Accent
                var prev: Offset? = null
                for (k in 0..72) {
                    val (ix, iy) = ell.point(Math.toRadians(k * 5.0))
                    val p = toView(ix, iy)
                    prev?.let { drawLine(col, it, p, strokeWidth = 4f) }
                    prev = p
                }
            }

            // Projizierte Board-Geometrie
            if (b2i != null) {
                val ready = status?.setup == LensController.Setup.READY
                val wire = if (ready) Color(0xAAFFFFFF) else Color(0xCCFFC107)
                listOf(Board.OUTER_BULL_RADIUS, Board.TRIPLE_INNER, Board.TRIPLE_OUTER, Board.DOUBLE_INNER, Board.DOUBLE_OUTER).forEach { r ->
                    var prev: Offset? = null
                    for (k in 0..72) {
                        val a = Math.toRadians(k * 5.0)
                        val (ix, iy) = b2i.map(r * sin(a), r * cos(a))
                        val p = toView(ix, iy)
                        prev?.let { drawLine(if (r == Board.DOUBLE_OUTER && ready) DartColors.Lime else wire, it, p, strokeWidth = if (r == Board.DOUBLE_OUTER) 3f else 1.5f) }
                        prev = p
                    }
                }
                for (i in 0 until 20) {
                    val a = Math.toRadians(Board.sectorStartDeg(i))
                    val (x1, y1) = b2i.map(Board.OUTER_BULL_RADIUS * sin(a), Board.OUTER_BULL_RADIUS * cos(a))
                    val (x2, y2) = b2i.map(Board.DOUBLE_OUTER * sin(a), Board.DOUBLE_OUTER * cos(a))
                    drawLine(wire, toView(x1, y1), toView(x2, y2), strokeWidth = 1.2f)
                }
                // Zahlen 20, 6, 3, 11 zur Orientierung
                listOf(0 to "20", 5 to "6", 10 to "3", 15 to "11").forEach { (idx, label) ->
                    val a = Math.toRadians(idx * Board.SECTOR_DEG)
                    val (tx, ty) = b2i.map((Board.DOUBLE_OUTER + 22) * sin(a), (Board.DOUBLE_OUTER + 22) * cos(a))
                    val tp = toView(tx, ty)
                    drawContext.canvas.nativeCanvas.drawText(label, tp.x, tp.y + 10f, textPaint)
                }
            }

            // Kalibrierpunkte (manueller Modus)
            if (editable && calibration.size == 8) {
                for (i in 0 until 4) {
                    val p = Offset(calibration[i * 2] * w, calibration[i * 2 + 1] * h)
                    drawCircle(if (dragging == i) DartColors.Accent else DartColors.Primary, radius = 22f, center = p, style = Stroke(4f))
                    drawCircle(Color.White, radius = 4f, center = p)
                    drawContext.canvas.nativeCanvas.drawText(DartDetector.CALIBRATION_LABELS[i], p.x, p.y - 30f, textPaint)
                }
            }

            // Rahmen wie bei Autodarts: grün = bereit, blau = kalibriert (wartet auf Ruhe)
            when (status?.setup) {
                LensController.Setup.READY -> drawRect(DartColors.Lime, style = Stroke(10f))
                LensController.Setup.FOUND -> drawRect(DartColors.Primary, style = Stroke(10f))
                else -> {}
            }

            // Erkannte Darts
            detections.forEachIndexed { idx, d ->
                val p = toView(d.imageX.toDouble(), d.imageY.toDouble())
                drawCircle(Color.Black, 16f, p)
                drawCircle(DartColors.Lime, 12f, p)
                drawContext.canvas.nativeCanvas.drawText("${idx + 1}", p.x, p.y + 10f, textPaint)
                drawContext.canvas.nativeCanvas.drawText(d.segment.name, p.x, p.y - 22f, textPaint)
            }
        }
        }
    }
}

/** Standard-Kalibrierung: Kreis in der Bildmitte. */
fun defaultCalibration(): List<Float> = DartDetector.CALIBRATION_ANGLES.flatMap { deg ->
    val a = Math.toRadians(deg)
    listOf((0.5 + 0.35 * sin(a)).toFloat(), (0.5 - 0.35 * 0.75 * cos(a)).toFloat())
}
