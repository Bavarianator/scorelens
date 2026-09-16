package com.freedarts.scorer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.freedarts.scorer.engine.Board
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.ui.theme.DartColors
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Virtuelles Dartboard: Antippen liefert das Segment (wie das virtuelle Board bei Autodarts).
 * [darts] werden als Markierungen der aktuellen Aufnahme eingezeichnet, [highlight] hebt Ziel-Segmente hervor.
 */
@Composable
fun Dartboard(
    modifier: Modifier = Modifier,
    darts: List<Segment> = emptyList(),
    highlight: Set<Segment> = emptySet(),
    enabled: Boolean = true,
    /** Tipp mit Position in Board-mm (Mitte 0/0, y nach oben); wenn gesetzt, ersetzt es [onSegment]. */
    onTap: ((Segment, Float, Float) -> Unit)? = null,
    onSegment: (Segment) -> Unit,
) {
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { pos ->
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val scale = (min(size.width, size.height) / 2f) / Board.BOARD_RADIUS.toFloat()
                    val xmm = (pos.x - cx) / scale
                    val ymm = -(pos.y - cy) / scale
                    val seg = Board.segmentAt(xmm.toDouble(), ymm.toDouble())
                    onTap?.invoke(seg, xmm, ymm) ?: onSegment(seg)
                }
            }
    ) {
        drawBoard(highlight, darts, textPaint)
    }
}

private fun DrawScope.drawBoard(highlight: Set<Segment>, darts: List<Segment>, textPaint: android.graphics.Paint) {
    val cx = size.width / 2f; val cy = size.height / 2f
    val scale = (min(size.width, size.height) / 2f) / Board.BOARD_RADIUS.toFloat()
    val center = Offset(cx, cy)

    fun r(mm: Double) = (mm * scale).toFloat()

    // Außenring (schwarz) mit Zahlen
    drawCircle(DartColors.Black, radius = r(Board.BOARD_RADIUS), center = center)

    fun ring(index: Int, inner: Double, outer: Double, color: Color) {
        val start = Board.sectorStartDeg(index) - 90.0 // Compose: 0° = rechts
        val path = Path()
        val ri = r(inner); val ro = r(outer)
        val s = Math.toRadians(start); val e = Math.toRadians(start + Board.SECTOR_DEG)
        path.moveTo(cx + ri * cos(s).toFloat(), cy + ri * sin(s).toFloat())
        path.arcTo(
            rect = androidx.compose.ui.geometry.Rect(center - Offset(ro, ro), Size(ro * 2, ro * 2)),
            startAngleDegrees = start.toFloat(), sweepAngleDegrees = Board.SECTOR_DEG.toFloat(), forceMoveTo = false,
        )
        path.lineTo(cx + ri * cos(e).toFloat(), cy + ri * sin(e).toFloat())
        path.arcTo(
            rect = androidx.compose.ui.geometry.Rect(center - Offset(ri, ri), Size(ri * 2, ri * 2)),
            startAngleDegrees = (start + Board.SECTOR_DEG).toFloat(), sweepAngleDegrees = -Board.SECTOR_DEG.toFloat(), forceMoveTo = false,
        )
        path.close()
        drawPath(path, color)
    }

    for (i in 0 until 20) {
        val n = Board.NUMBERS[i]
        val dark = i % 2 == 0
        val single = if (dark) DartColors.Black else DartColors.Cream
        val multi = if (dark) DartColors.Red else DartColors.Green
        fun c(seg: Segment, base: Color) = if (seg in highlight) DartColors.Accent else base
        ring(i, Board.OUTER_BULL_RADIUS, Board.TRIPLE_INNER, c(Segment.single(n), single))
        ring(i, Board.TRIPLE_INNER, Board.TRIPLE_OUTER, c(Segment.triple(n), multi))
        ring(i, Board.TRIPLE_OUTER, Board.DOUBLE_INNER, c(Segment.single(n), single))
        ring(i, Board.DOUBLE_INNER, Board.DOUBLE_OUTER, c(Segment.double(n), multi))
    }
    drawCircle(if (Segment.OUTER_BULL in highlight) DartColors.Accent else DartColors.Green, r(Board.OUTER_BULL_RADIUS), center)
    drawCircle(if (Segment.BULL in highlight) DartColors.Accent else DartColors.Red, r(Board.BULL_RADIUS), center)

    // Drahtlinien
    val wire = Color(0xFFB0B0B0)
    for (i in 0 until 20) {
        val a = Math.toRadians(Board.sectorStartDeg(i) - 90.0)
        drawLine(wire, center + Offset(r(Board.OUTER_BULL_RADIUS) * cos(a).toFloat(), r(Board.OUTER_BULL_RADIUS) * sin(a).toFloat()),
            center + Offset(r(Board.DOUBLE_OUTER) * cos(a).toFloat(), r(Board.DOUBLE_OUTER) * sin(a).toFloat()), strokeWidth = 1.2f)
    }
    listOf(Board.OUTER_BULL_RADIUS, Board.TRIPLE_INNER, Board.TRIPLE_OUTER, Board.DOUBLE_INNER, Board.DOUBLE_OUTER).forEach {
        drawCircle(wire, r(it), center, style = Stroke(1.2f))
    }

    // Zahlen
    textPaint.textSize = r(22.0)
    val numR = r(Board.DOUBLE_OUTER + 28)
    for (i in 0 until 20) {
        val a = Math.toRadians(i * Board.SECTOR_DEG - 90.0)
        val x = cx + numR * cos(a).toFloat()
        val y = cy + numR * sin(a).toFloat() + textPaint.textSize / 3
        drawContext.canvas.nativeCanvas.drawText(Board.NUMBERS[i].toString(), x, y, textPaint)
    }

    // Darts der aktuellen Aufnahme
    darts.forEachIndexed { idx, seg ->
        if (seg.isMiss) return@forEachIndexed
        val (mx, my) = Board.centerOf(seg)
        val p = Offset(cx + r(mx), cy - r(my))
        drawCircle(Color.Black, r(7.0), p)
        drawCircle(DartColors.Blue, r(5.0), p)
        drawCircle(Color.White, r(1.8), p)
        textPaint.textSize = r(9.0)
        drawContext.canvas.nativeCanvas.drawText((idx + 1).toString(), p.x, p.y + r(3.0), textPaint)
    }
}

/** Kleine Vorschau (nicht interaktiv), z.B. in der Lobby. */
@Composable
fun DartboardPreview(modifier: Modifier = Modifier) {
    Dartboard(modifier = modifier, enabled = false, onSegment = {})
}

val DartboardDefaultSize = 320.dp
