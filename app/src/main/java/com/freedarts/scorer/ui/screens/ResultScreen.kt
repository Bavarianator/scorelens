package com.freedarts.scorer.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.theme.DartColors
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ResultScreen(vm: AppViewModel) {
    val record by vm.lastRecord.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val r = record
    val winner = r?.players?.firstOrNull { it.won }
    val celebrate = winner != null && settings.animations
    // Sieger-Zeile poppt auf, dazu Konfetti über dem ganzen Screen
    var shown by remember(r?.id) { mutableStateOf(false) }
    LaunchedEffect(r?.id) { shown = true }
    val pop by animateFloatAsState(if (shown || !celebrate) 1f else 0.4f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "pop")
    Box(Modifier.fillMaxSize()) { com.freedarts.scorer.ui.components.ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Ergebnis", onBack = { vm.goHome() })
        if (r == null) { Text("Kein Ergebnis vorhanden", Modifier.padding(16.dp)); return }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text((winner?.let { "${it.playerName} gewinnt" } ?: "Unentschieden").uppercase(), fontFamily = com.freedarts.scorer.ui.theme.Condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = DartColors.Lime,
                modifier = Modifier.scale(pop))
            Text("${r.mode.title} · Spielzeit ${formatDuration(r.durationMillis)}", color = DartColors.TextMuted)
            Spacer(Modifier.height(16.dp))
            StatsTable(r)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.freedarts.scorer.ui.components.SecondaryButton("Menü", Modifier.weight(1f)) { vm.goHome() }
            com.freedarts.scorer.ui.components.PrimaryButton(if (vm.inTournament) "Zum Turnier" else "Rematch", Modifier.weight(1f)) { vm.rematch() }
        }
    }
    if (celebrate) Confetti(key = r?.id)
}

/** Konfetti-Regen, ~5 s, 120 Partikel; eigener Frame-Loop, keine Bibliothek. */
@Composable
private fun Confetti(key: Any?) {
    class P(val x: Float, val speed: Float, val phase: Float, val size: Float, val color: Color, val delay: Float)
    val colors = listOf(DartColors.Lime, DartColors.Accent, DartColors.PrimaryLight, Color.White, DartColors.Red)
    val parts = remember(key) { List(120) { P(Random.nextFloat(), 0.25f + Random.nextFloat() * 0.35f, Random.nextFloat() * 6.28f, 8f + Random.nextFloat() * 10f, colors.random(), Random.nextFloat() * 1.5f) } }
    var t by remember(key) { mutableStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (t < 6f) { withFrameNanos { now -> t = (now - start) / 1e9f } }
    }
    if (t >= 6f) return
    Canvas(Modifier.fillMaxSize()) {
        parts.forEach { p ->
            val age = t - p.delay
            if (age < 0) return@forEach
            val y = age * p.speed * size.height - p.size
            if (y > size.height) return@forEach
            val x = p.x * size.width + sin(age * 3f + p.phase) * 30f
            val alpha = if (t > 5f) (6f - t) else 1f
            drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x, y), size = Size(p.size, p.size * 0.6f))
        }
    }
}

@Composable
fun StatsTable(r: MatchRecord) {
    val x01 = r.mode == GameMode.X01
    val rows: List<Pair<String, (com.freedarts.scorer.model.PlayerMatchStats) -> String>> = buildList {
        add("Ergebnis" to { it.finalScore })
        if (r.settings.legs > 1 || r.settings.sets > 1) add("Legs / Sets" to { "${it.legsWon} / ${it.setsWon}" })
        add("Darts" to { it.dartsThrown.toString() })
        if (x01 || r.mode == GameMode.COUNT_UP) add("3-Dart-Average" to { "%.2f".format(it.average3) })
        if (x01) {
            add("First-9-Average" to { "%.2f".format(it.first9Average) })
            add("Checkout %" to { "%.1f %% (${it.checkouts}/${it.dartsAtDouble})".format(it.checkoutRate) })
            add("Höchstes Finish" to { it.highestCheckout.toString() })
            add("Höchster Score" to { it.highestVisit.toString() })
            add("Bestes Leg" to { if (it.bestLegDarts > 0) "${it.bestLegDarts} Darts" else "–" })
            add("Schlechtestes Leg" to { if (it.worstLegDarts > 0) "${it.worstLegDarts} Darts" else "–" })
            add("Busts" to { it.busts.toString() })
            add("60+" to { it.count60Plus.toString() })
            add("100+" to { it.count100Plus.toString() })
            add("140+" to { it.count140Plus.toString() })
            add("170+" to { it.count170Plus.toString() })
            add("180" to { it.count180.toString() })
        }
        if (r.mode == GameMode.CRICKET) add("MPR" to { "%.2f".format(it.mpr) })
        if (r.mode == GameMode.AROUND_THE_CLOCK || r.mode == GameMode.SEGMENT_TRAINING) add("Trefferquote" to { "%.0f %% (${it.hits}/${it.dartsThrown})".format(it.hitRate) })
        if (r.mode == GameMode.GOTCHA || r.mode == GameMode.ONE_TWENTY_ONE) add("Busts" to { it.busts.toString() })
    }
    Column(Modifier.fillMaxWidth().background(DartColors.Surface, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(120.dp))
            r.players.forEach { p -> Text(p.playerName, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
        rows.forEach { (label, f) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(120.dp), color = DartColors.TextMuted)
                r.players.forEach { p -> Text(f(p), Modifier.weight(1f), textAlign = TextAlign.Center) }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d min".format(s / 60, s % 60)
}
