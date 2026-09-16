@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.freedarts.scorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    textColor: Color = DartColors.Text,
    enabled: Boolean = true,
    height: Int = 48,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.height(height.dp).clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else color.copy(alpha = 0.4f))
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
                .border(1.dp, DartColors.Primary, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (input.isEmpty()) "Score eingeben" else input, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = if (input.isEmpty()) DartColors.TextMuted else DartColors.Text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(26, 41, 45, 60, 85, 100).forEach { q ->
                PadButton(q.toString(), Modifier.weight(1f), color = DartColors.Surface, enabled = enabled) { onSubmit(q) }
            }
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d -> PadButton(d, Modifier.weight(1f), enabled = enabled) { if (input.length < 3) input += d } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton("⌫", Modifier.weight(1f), color = DartColors.RedDark, enabled = enabled) { input = input.dropLast(1) }
            PadButton("0", Modifier.weight(1f), enabled = enabled) { if (input.length < 3) input += "0" }
            PadButton("OK", Modifier.weight(1f), color = DartColors.Primary, enabled = enabled && input.isNotEmpty() && value <= 180) {
                onSubmit(value); input = ""
            }
        }
    }
}

/**
 * Segment-Grid (Quick Correction / Dart für Dart): Zahl antippen = Single, lange drücken = Double,
 * darunter zwei kleine Tasten D und T. Dazu 25, Bull und Miss. Ein Dart = ein Tap.
 */
@Composable
fun SegmentGrid(enabled: Boolean, compact: Boolean = false, onSegment: (Segment) -> Unit) {
    val cellH = if (compact) 38 else 44
    val subH = if (compact) 22 else 26
    Column(modifier = Modifier.fillMaxWidth().padding(if (compact) 4.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { col ->
                    val n = row * 5 + col
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(cellH.dp).clip(RoundedCornerShape(10.dp, 10.dp, 4.dp, 4.dp))
                                .background(if (enabled) DartColors.SurfaceHigh else DartColors.SurfaceHigh.copy(alpha = 0.4f))
                                .combinedClickable(enabled = enabled, onClick = { onSegment(Segment.single(n)) }, onLongClick = { onSegment(Segment.double(n)) }),
                            contentAlignment = Alignment.Center,
                        ) { Text(n.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.weight(1f).height(subH.dp).clip(RoundedCornerShape(4.dp, 4.dp, 4.dp, 10.dp)).background(Color(0xFF1B3FA8).copy(alpha = if (enabled) 1f else 0.4f))
                                .clickable(enabled = enabled) { onSegment(Segment.double(n)) }, contentAlignment = Alignment.Center) { Text("D", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            Box(Modifier.weight(1f).height(subH.dp).clip(RoundedCornerShape(4.dp, 4.dp, 10.dp, 4.dp)).background(Color(0xFF7A1F5E).copy(alpha = if (enabled) 1f else 0.4f))
                                .clickable(enabled = enabled) { onSegment(Segment.triple(n)) }, contentAlignment = Alignment.Center) { Text("T", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton("25", Modifier.weight(1f), color = DartColors.GreenDark, enabled = enabled, height = cellH) { onSegment(Segment.OUTER_BULL) }
            PadButton("BULL", Modifier.weight(1f), color = DartColors.RedDark, enabled = enabled, height = cellH) { onSegment(Segment.BULL) }
            PadButton("Miss", Modifier.weight(1f), color = DartColors.Surface, enabled = enabled, height = cellH) { onSegment(Segment.MISS) }
        }
        if (!compact) Text("Tipp: Zahl lange drücken = Double", fontSize = 11.sp, color = DartColors.TextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

/** Dart für Dart – jetzt als Segment-Grid mit einem Tap pro Dart. */
@Composable
fun DartByDartPad(enabled: Boolean, onSegment: (Segment) -> Unit) = SegmentGrid(enabled, compact = false, onSegment)
