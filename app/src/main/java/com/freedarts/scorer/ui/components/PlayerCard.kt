package com.freedarts.scorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedarts.scorer.engine.Achievements
import com.freedarts.scorer.engine.Statistics
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.ui.theme.DartColors

/**
 * Player Card wie bei Autodarts (Tipp auf einen Spielernamen): Level, X01-Kennzahlen, Form, Head-to-Head gegen
 * das eigene Profil und Erfolge. Alles aus dem vorhandenen Verlauf, kein eigener Zustand.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlayerCardDialog(player: Player, matches: List<MatchRecord>, profileId: String?, onDismiss: () -> Unit) {
    val mine = remember(matches, player.id) { matches.filter { m -> m.players.any { it.playerId == player.id } } }
    val stats = remember(mine) { mine.map { m -> m.players.first { it.playerId == player.id } } }
    val x01 = remember(mine) { mine.filter { it.mode == GameMode.X01 }.map { m -> m.players.first { it.playerId == player.id } } }
    val avg = if (player.isBot && x01.isEmpty()) Player.botAverage(player.botLevel).toDouble() else Statistics.metricTotal(GameMode.X01, x01)
    val (lvl, lvlBg, lvlFg) = levelOf(avg)
    val atDouble = x01.sumOf { it.dartsAtDouble }
    val h2h = remember(matches, profileId, player.id) { if (profileId == null || profileId == player.id) null else Statistics.headToHead(matches, profileId).firstOrNull { it.opponentId == player.id } }
    val badges = remember(mine) { Achievements.of(mine, player.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(player, 56, online = false)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        NameRibbon(player.name, lvl, lvlBg, lvlFg, fontSize = 18)
                        Text("${mine.size} Spiele · ${stats.count { it.won }} Siege" + (if (mine.isNotEmpty()) " · %.0f %% Siegquote".format(100.0 * stats.count { it.won } / mine.size) else ""),
                            color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (x01.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("%.1f".format(avg), "Ø X01", Modifier.weight(1f))
                        StatTile("%.0f %%".format(if (atDouble == 0) 0.0 else 100.0 * x01.sumOf { it.checkouts } / atDouble), "Checkout", Modifier.weight(1f), barColor = DartColors.Green)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(x01.sumOf { it.count180 }.toString(), "180er", Modifier.weight(1f), barColor = DartColors.Lime)
                        StatTile((x01.maxOfOrNull { it.highestCheckout } ?: 0).toString(), "Höchstes Finish", Modifier.weight(1f), barColor = DartColors.Orange)
                    }
                    if (x01.size >= 2) { Spacer(Modifier.height(8.dp)); Sparkline(x01.takeLast(10).map { it.average3 }); Text("Form der letzten ${x01.takeLast(10).size} X01-Spiele", color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall) }
                }
                if (h2h != null) {
                    SectionLabel("Head-to-Head", size = 16)
                    HeadToHeadCompare(h2h)
                }
                SectionLabel("Erfolge", size = 16, trailing = { Chip("${badges.count { it.done }} / ${badges.size}") })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { a ->
                        Column(Modifier.background(if (a.done) DartColors.GreenDark else DartColors.SurfaceHigh, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(a.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (a.done) androidx.compose.ui.graphics.Color.White else DartColors.TextMuted)
                            Text(if (a.done) a.description else "${a.description} · ${a.progress}/${a.goal}", fontSize = 10.sp, color = if (a.done) androidx.compose.ui.graphics.Color(0xFFDDE6F5) else DartColors.TextMuted)
                        }
                    }
                }
            }
        },
    )
}
