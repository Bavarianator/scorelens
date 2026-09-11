package com.freedarts.scorer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.ui.theme.DartColors
import kotlin.math.min

/**
 * Referee-Bild: der Kamera-Ausschnitt um die erkannte Dartspitze mit Markierung – zum Nachprüfen einer
 * Erkennung, bevor man sie korrigiert (Ersatz für den AI Referee von Autodarts, offline).
 */
@Composable
fun LensSnapshot(detection: LensController.Detection, modifier: Modifier = Modifier) {
    val bmp = detection.snapshot ?: return
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
            Image(bmp.asImageBitmap(), contentDescription = "Kamerabild der Dartspitze", modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
            Canvas(Modifier.fillMaxSize()) {
                val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
                val s = min(size.width / bw, size.height / bh)
                val dw = bw * s; val dh = bh * s
                val ox = (size.width - dw) / 2; val oy = (size.height - dh) / 2
                val p = Offset(ox + detection.tipX * dw, oy + detection.tipY * dh)
                drawCircle(Color.Black.copy(alpha = 0.45f), 15f, p)
                drawCircle(DartColors.Lime, 12f, p, style = Stroke(3f))
                for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
                    drawLine(DartColors.Lime, p + Offset(dx * 14f, dy * 14f), p + Offset(dx * 26f, dy * 26f), strokeWidth = 3f)
                }
            }
        }
        Text("Referee-Bild: erkannte Spitze (${detection.segment.name}) im Kamera-Ausschnitt", color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}
