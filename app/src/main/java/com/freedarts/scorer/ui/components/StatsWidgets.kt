package com.freedarts.scorer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freedarts.scorer.engine.Board
import com.freedarts.scorer.engine.Statistics
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.ui.theme.DartColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val HeatCold = Color(0xFF243B6B)
private val HeatHot = Color(0xFFFF4D4D)
private val HeatNone = Color(0xFF262B36)

/** Trefferbild: Segmente nach Trefferhäufigkeit eingefärbt, dazu die Auftreffpunkte aus der Kamera-Erkennung. */
@Composable
fun HeatmapBoard(heat: Statistics.Heatmap, modifier: Modifier = Modifier) {
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val max = heat.max.coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val scale = (min(size.width, size.height) / 2f) / Board.BOARD_RADIUS.toFloat()
        val center = Offset(cx, cy)
        fun r(mm: Double) = (mm * scale).toFloat()
        fun heatColor(seg: Segment): Color {
            val c = heat.counts[seg] ?: 0
            return if (c == 0) HeatNone else lerp(HeatCold, HeatHot, c.toFloat() / max)
        }
        fun ring(index: Int, inner: Double, outer: Double, color: Color) {
            val start = Board.sectorStartDeg(index) - 90.0
            val ri = r(inner); val ro = r(outer)
            val s = Math.toRadians(start); val e = Math.toRadians(start + Board.SECTOR_DEG)
            val path = Path()
            path.moveTo(cx + ri * cos(s).toFloat(), cy + ri * sin(s).toFloat())
            path.arcTo(Rect(center - Offset(ro, ro), Size(ro * 2, ro * 2)), start.toFloat(), Board.SECTOR_DEG.toFloat(), false)
            path.lineTo(cx + ri * cos(e).toFloat(), cy + ri * sin(e).toFloat())
            path.arcTo(Rect(center - Offset(ri, ri), Size(ri * 2, ri * 2)), (start + Board.SECTOR_DEG).toFloat(), -Board.SECTOR_DEG.toFloat(), false)
            path.close()
            drawPath(path, color)
        }
        drawCircle(DartColors.Black, r(Board.BOARD_RADIUS), center)
        for (i in 0 until 20) {
            val n = Board.NUMBERS[i]
            ring(i, Board.OUTER_BULL_RADIUS, Board.TRIPLE_INNER, heatColor(Segment.single(n)))
            ring(i, Board.TRIPLE_INNER, Board.TRIPLE_OUTER, heatColor(Segment.triple(n)))
            ring(i, Board.TRIPLE_OUTER, Board.DOUBLE_INNER, heatColor(Segment.single(n)))
            ring(i, Board.DOUBLE_INNER, Board.DOUBLE_OUTER, heatColor(Segment.double(n)))
        }
        drawCircle(heatColor(Segment.OUTER_BULL), r(Board.OUTER_BULL_RADIUS), center)
        drawCircle(heatColor(Segment.BULL), r(Board.BULL_RADIUS), center)
        val wire = Color(0x66FFFFFF)
        for (i in 0 until 20) {
            val a = Math.toRadians(Board.sectorStartDeg(i) - 90.0)
            drawLine(wire, center + Offset(r(Board.OUTER_BULL_RADIUS) * cos(a).toFloat(), r(Board.OUTER_BULL_RADIUS) * sin(a).toFloat()),
                center + Offset(r(Board.DOUBLE_OUTER) * cos(a).toFloat(), r(Board.DOUBLE_OUTER) * sin(a).toFloat()), strokeWidth = 1f)
        }
        listOf(Board.OUTER_BULL_RADIUS, Board.TRIPLE_INNER, Board.TRIPLE_OUTER, Board.DOUBLE_INNER, Board.DOUBLE_OUTER).forEach {
            drawCircle(wire, r(it), center, style = Stroke(1f))
        }
        textPaint.textSize = r(20.0)
        val numR = r(Board.DOUBLE_OUTER + 28)
        for (i in 0 until 20) {
            val a = Math.toRadians(i * Board.SECTOR_DEG - 90.0)
            drawContext.canvas.nativeCanvas.drawText(Board.NUMBERS[i].toString(), cx + numR * cos(a).toFloat(), cy + numR * sin(a).toFloat() + textPaint.textSize / 3, textPaint)
        }
        heat.points.forEach { (x, y) -> drawCircle(Color.White.copy(alpha = 0.85f), r(2.2), Offset(cx + r(x.toDouble()), cy - r(y.toDouble()))) }
    }
}

/** Die meistgetroffenen Segmente als Text, z.B. „T20 18 % · S20 12 % · S1 6 %“. */
fun topSegments(heat: Statistics.Heatmap, n: Int = 3): String =
    heat.counts.entries.sortedByDescending { it.value }.take(n)
        .joinToString(" · ") { (seg, _) -> "${seg.name} %.0f %%".format(heat.share(seg) * 100) }

@Composable
fun HeadToHeadRow(h: Statistics.HeadToHead) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(h.opponentName, fontWeight = FontWeight.SemiBold)
            Text(
                "${h.played} Spiele · Legs ${h.legsWon}:${h.legsLost}" + (if (h.myAverage > 0) " · Ø %.1f vs %.1f".format(h.myAverage, h.theirAverage) else ""),
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("${h.wins}:${h.losses}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
            color = if (h.wins > h.losses) DartColors.Green else if (h.wins < h.losses) DartColors.Red else Color.White)
    }
}

/** Verlaufslinie einer Kennzahl über die letzten Spiele (chronologisch), mit Mittelwertlinie und Bestwert. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = DartColors.Primary, format: (Double) -> String = { "%.1f".format(it) }) {
    if (values.isEmpty()) return
    val textPaint = remember {
        android.graphics.Paint().apply { this.color = android.graphics.Color.LTGRAY; isAntiAlias = true; textSize = 26f }
    }
    // Achse um 10 % (min. 1) gepolstert: flache Verläufe liegen mittig statt am Boden, Punkte nicht am Rand
    val pad = ((values.max() - values.min()) * 0.1).coerceAtLeast(1.0)
    val lo = values.min() - pad; val hi = values.max() + pad
    val mean = values.average()
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val padTop = 28f; val padBottom = 8f; val padX = 8f
        val w = size.width - 2 * padX; val h = size.height - padTop - padBottom
        val span = hi - lo
        fun x(i: Int) = padX + if (values.size == 1) w / 2 else w * i / (values.size - 1)
        fun y(v: Double) = padTop + h - ((v - lo) / span * h).toFloat()
        val ym = y(mean)
        drawLine(Color(0x55FFFFFF), Offset(padX, ym), Offset(padX + w, ym), strokeWidth = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, color, style = Stroke(4f))
        val top = values.max()
        values.forEachIndexed { i, v -> drawCircle(if (v == top) DartColors.Green else color, 5f, Offset(x(i), y(v))) }
        val best = values.indexOf(top)
        drawContext.canvas.nativeCanvas.drawText("Best ${format(top)}", (x(best) - 40f).coerceIn(0f, size.width - 120f), y(top) - 10f, textPaint)
        // Ø-Label auf der dem Bestpunkt abgewandten Seite, damit sich die Texte nicht überlagern
        textPaint.textAlign = if (best < values.size / 2) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("Ø ${format(mean)}", if (best < values.size / 2) padX + w else padX, (ym - 6f).coerceAtLeast(padTop), textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
    }
}

/** Säulendiagramm mit Wert über und Beschriftung unter jeder Säule (leere Beschriftung = keine). */
@Composable
fun BarChart(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier, color: Color = DartColors.Primary, showValues: Boolean = true) {
    if (values.isEmpty()) return
    val textPaint = remember {
        android.graphics.Paint().apply { this.color = android.graphics.Color.LTGRAY; isAntiAlias = true; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER }
    }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val padTop = if (showValues) 26f else 6f; val padBottom = 26f
        val h = size.height - padTop - padBottom
        val slot = size.width / values.size
        val bw = slot * 0.62f
        values.forEachIndexed { i, v ->
            val bh = h * v / max
            val left = slot * i + (slot - bw) / 2
            drawRect(if (v == 0) DartColors.SurfaceHigh else color, Offset(left, padTop + h - bh), Size(bw, bh.coerceAtLeast(2f)))
            if (showValues && v > 0) drawContext.canvas.nativeCanvas.drawText(v.toString(), left + bw / 2, padTop + h - bh - 6f, textPaint)
            labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { drawContext.canvas.nativeCanvas.drawText(it, left + bw / 2, size.height - 4f, textPaint) }
        }
    }
}
