package com.freedarts.scorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.ui.theme.DartColors

@Composable
private fun PadButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = DartColors.SurfaceHigh,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(if (enabled) color else color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center)
    }
}

/** Gesamtscore einer Aufnahme über den Nummernblock eingeben. */
@Composable
fun TotalScorePad(enabled: Boolean, onSubmit: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    val value = input.toIntOrNull() ?: 0
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp).background(DartColors.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, DartColors.Green, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (input.isEmpty()) "Score eingeben" else input, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = if (input.isEmpty()) DartColors.TextMuted else Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(26, 41, 45, 60, 85, 100).forEach { q ->
                PadButton(q.toString(), Modifier.weight(1f), color = DartColors.Surface, enabled = enabled) { onSubmit(q) }
            }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d -> PadButton(d, Modifier.weight(1f), enabled = enabled) { if (input.length < 3) input += d } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton("⌫", Modifier.weight(1f), color = DartColors.RedDark, enabled = enabled) { input = input.dropLast(1) }
            PadButton("0", Modifier.weight(1f), enabled = enabled) { if (input.length < 3) input += "0" }
            PadButton("OK", Modifier.weight(1f), color = DartColors.Green, enabled = enabled && input.isNotEmpty() && value <= 180) {
                onSubmit(value); input = ""
            }
        }
    }
}

/** Dart für Dart: Zahl + Single/Double/Triple, 25, Bull, Miss. */
@Composable
fun DartByDartPad(enabled: Boolean, onSegment: (Segment) -> Unit) {
    var multiplier by remember { mutableIntStateOf(1) }
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "Single", 2 to "Double", 3 to "Triple").forEach { (m, label) ->
                PadButton(label, Modifier.weight(1f), color = if (multiplier == m) DartColors.Green else DartColors.Surface, enabled = enabled) { multiplier = m }
            }
        }
        (0 until 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { col ->
                    val n = row * 5 + col
                    PadButton(if (multiplier == 1) "$n" else "${"SDT"[multiplier - 1]}$n", Modifier.weight(1f), enabled = enabled) {
                        onSegment(Segment(n, multiplier)); multiplier = 1
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton("25", Modifier.weight(1f), color = DartColors.GreenDark, enabled = enabled) { onSegment(Segment.OUTER_BULL); multiplier = 1 }
            PadButton("BULL", Modifier.weight(1f), color = DartColors.RedDark, enabled = enabled) { onSegment(Segment.BULL); multiplier = 1 }
            PadButton("Miss", Modifier.weight(1f), color = DartColors.Surface, enabled = enabled) { onSegment(Segment.MISS); multiplier = 1 }
        }
    }
}
