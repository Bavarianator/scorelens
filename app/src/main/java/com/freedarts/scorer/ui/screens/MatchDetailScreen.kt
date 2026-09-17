package com.freedarts.scorer.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.ThrowRecord
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Badge
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Match-Detail wie bei Autodarts (matches/:id): Ergebnis, Statistik-Tabelle und das Wurfprotokoll Leg für Leg. */
@Composable
fun MatchDetailScreen(vm: AppViewModel) {
    val record by vm.detailRecord.collectAsStateWithLifecycle()
    val r = record
    val context = LocalContext.current
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    Box(Modifier.fillMaxSize()) { ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Match", onBack = { vm.back() }, actions = {
            if (r != null) IconButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(r)), "Ergebnis teilen"))
            }) { Icon(Icons.Default.Share, "Teilen") }
        })
        if (r == null) { Text("Kein Match gewählt", Modifier.padding(16.dp)); return }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            val winner = r.players.firstOrNull { it.won }
            Text(r.players.joinToString(" vs ") { it.playerName }.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.sp)
            Text(r.mode.title + (if (r.mode == GameMode.X01) " ${r.settings.baseScore} · ${matchTitle(r.settings)}" else "") + " · " + df.format(Date(r.finishedAt)) + " · " + formatDuration(r.durationMillis),
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Badge(winner?.let { "${it.playerName} gewinnt" } ?: "Unentschieden", DartColors.OnTint, if (winner != null) DartColors.GreenDark else DartColors.SurfaceHigh)
            Spacer(Modifier.height(12.dp))
            StatsTable(r)

            if (r.throws.isNotEmpty()) {
                SectionLabel("Wurfprotokoll")
                val legs = remember(r.id) { r.throws.filter { it.leg > 0 }.groupBy { it.set to it.leg }.toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second }) }
                legs.forEach { (key, list) -> LegTable(r, key.first, key.second, list) }
                if (r.throws.any { it.leg == 0 }) Text("Ausbullen: " + r.throws.filter { it.leg == 0 }.joinToString(", ") { "${r.players[it.player].playerName} ${it.segment.name}" }, color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Ein Leg als Tafel: pro Runde eine Zeile, pro Spieler die drei Darts, Aufnahme-Summe und (X01) der Rest. */
@Composable
private fun LegTable(r: MatchRecord, set: Int, leg: Int, list: List<ThrowRecord>) {
    val x01 = r.mode == GameMode.X01
    val rounds = list.map { it.round }.distinct().sorted()
    val remaining = IntArray(r.players.size) { r.settings.baseScore }
    val winner = list.lastOrNull()?.takeIf { !it.bust }?.player
    AdCard(Modifier.padding(bottom = 8.dp), padding = 10) {
        Row {
            Text((if (r.settings.sets > 1 || set > 1) "Set $set · " else "") + "Leg $leg", fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
            r.players.forEachIndexed { p, ps ->
                Text(ps.playerName, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                    color = if (winner == p) DartColors.LimeText else DartColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
        }
        rounds.forEach { round ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(round.toString(), Modifier.width(44.dp), color = DartColors.TextMuted, fontSize = 12.sp)
                for (p in r.players.indices) {
                    val visit = list.filter { it.player == p && it.round == round }
                    val bust = visit.any { it.bust }
                    val sum = visit.sumOf { it.score }
                    if (visit.isNotEmpty() && !bust) remaining[p] -= sum
                    Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        if (visit.isNotEmpty()) {
                            Text(visit.joinToString(" ") { it.segment.name }, fontSize = 12.sp, color = if (bust) DartColors.Red else DartColors.Text, maxLines = 1)
                            Row {
                                Text(if (bust) "Bust" else sum.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (bust) DartColors.Red else if (sum >= 100) DartColors.Lime else DartColors.Text)
                                if (x01) Text("  ${remaining[p]}", fontSize = 12.sp, color = DartColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Kurzfassung des Ergebnisses als Text (Teilen-Button). */
fun shareText(r: MatchRecord): String {
    val x01 = r.mode == GameMode.X01
    val head = "🎯 Scorelens · ${r.mode.title}" + (if (x01) " ${r.settings.baseScore} · ${matchTitle(r.settings)}" else "")
    val lines = r.players.map { p ->
        (if (p.won) "🏆 " else "    ") + p.playerName + ": " + p.finalScore +
            (if (r.settings.legs > 1 || r.settings.sets > 1) " · ${p.legsWon} Legs" else "") +
            (if (x01) " · Ø %.1f · Checkout %.0f %%".format(p.average3, p.checkoutRate) + (if (p.highestCheckout > 0) " · Finish ${p.highestCheckout}" else "") + (if (p.count180 > 0) " · ${p.count180}× 180" else "") else "")
    }
    return (listOf(head) + lines).joinToString("\n")
}
