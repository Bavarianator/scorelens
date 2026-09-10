package com.freedarts.scorer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedarts.scorer.engine.PlayerState
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun Avatar(player: Player, size: Int = 36, online: Boolean = size >= 32) {
    Box(Modifier.size(size.dp)) {
        Box(
            modifier = Modifier.size(size.dp).background(Color(player.color), CircleShape)
                .border((size / 18).coerceAtLeast(1).dp, Color(player.color).copy(alpha = 0.55f).compositeOver(Color.White), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (player.isBot) "B" else player.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp)
        }
        if (online && !player.isBot) Box(
            Modifier.align(Alignment.BottomEnd).size((size / 3.2).dp).background(DartColors.Green, CircleShape).border(2.dp, DartColors.Surface, CircleShape),
        )
    }
}

@Composable
fun PlayerCard(state: PlayerState, active: Boolean, compact: Boolean, showLegs: Boolean, showSets: Boolean, modifier: Modifier = Modifier) {
    val border = if (active) DartColors.Active else Color.Transparent
    Column(
        modifier = modifier
            .background(if (active) DartColors.SurfaceHigh else DartColors.Surface, RoundedCornerShape(14.dp))
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Avatar(state.player, 24)
            Spacer(Modifier.width(6.dp))
            Text(state.player.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (state.isOut) DartColors.TextMuted else Color.White)
            if (state.isKiller) Text(" 🔪", fontSize = 14.sp)
        }
        Text(
            state.score,
            fontSize = if (compact) 34.sp else 46.sp,
            fontWeight = FontWeight.Bold,
            color = if (state.isOut) DartColors.TextMuted else Color.White,
            textAlign = TextAlign.Center,
        )
        Text(state.detail, style = MaterialTheme.typography.bodyMedium, color = DartColors.TextMuted, maxLines = 1)
        if (showLegs || showSets) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showSets) Text("Sets ${state.sets}", style = MaterialTheme.typography.labelSmall, color = DartColors.Accent)
                if (showLegs) Text("Legs ${state.legs}", style = MaterialTheme.typography.labelSmall, color = DartColors.Accent)
            }
        }
    }
}

/** Die drei Dart-Slots der aktuellen Aufnahme. */
@Composable
fun VisitRow(darts: List<Segment>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        for (i in 0 until 3) {
            val d = darts.getOrNull(i)
            Box(
                modifier = Modifier.width(76.dp).height(40.dp)
                    .background(if (d != null) DartColors.SurfaceHigh else DartColors.Surface, RoundedCornerShape(10.dp))
                    .border(1.dp, if (d != null) DartColors.Blue else DartColors.SurfaceHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(d?.name ?: "–", fontWeight = FontWeight.Bold, color = if (d != null) Color.White else DartColors.TextMuted)
            }
        }
        val sum = darts.sumOf { it.score }
        Box(modifier = Modifier.width(56.dp).height(40.dp), contentAlignment = Alignment.Center) {
            Text(if (darts.isEmpty()) "" else "= $sum", color = DartColors.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Banner(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = text != null, modifier = modifier) {
        val isBust = text == "Bust"
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .background(if (isBust) DartColors.RedDark else DartColors.GreenDark, RoundedCornerShape(12.dp)).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** Kreidetafel: die letzten Aufnahmen aller Spieler. */
@Composable
fun Chalkboard(players: List<PlayerState>, modifier: Modifier = Modifier, rows: Int = 4) {
    Row(modifier = modifier.fillMaxWidth().background(DartColors.Surface, RoundedCornerShape(12.dp)).padding(8.dp)) {
        players.forEach { p ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                p.history.takeLast(rows).forEach { e ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(e.label, color = if (e.label == "Bust") DartColors.Red else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (e.remaining.isNotEmpty()) Text(e.remaining, color = DartColors.TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Cricket-Tafel mit Marks (/, X, ⊗) pro Zahl und Spieler. */
@Composable
fun CricketTable(players: List<PlayerState>, targets: List<Int>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(DartColors.Surface, RoundedCornerShape(12.dp)).padding(6.dp)) {
        targets.forEach { t ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val closedByAll = players.all { (it.marks?.get(t) ?: 0) >= 3 }
                players.forEachIndexed { idx, p ->
                    if (idx == players.size / 2) {
                        Text(if (t == 25) "B" else t.toString(), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold, color = if (closedByAll) DartColors.TextMuted else DartColors.Accent)
                    }
                    val m = p.marks?.get(t) ?: 0
                    Text(
                        when (m) { 0 -> ""; 1 -> "/"; 2 -> "X"; else -> "⊗" },
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = if (m >= 3) DartColors.Green else Color.White,
                    )
                }
            }
        }
    }
}
