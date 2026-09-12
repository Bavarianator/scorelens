package com.freedarts.scorer.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.Tournament
import com.freedarts.scorer.model.TournamentMatch
import com.freedarts.scorer.model.TournamentMode
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.theme.DartColors

/** Lokales Turnier: Spielplan als Liste, jedes offene Spiel startet ein normales Match. */
@Composable
fun TournamentScreen(vm: AppViewModel) {
    val t by vm.tournament.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) { ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Turnier", onBack = { vm.back() }) {
            if (t != null && vm.canRunTournament) IconButton(onClick = { confirmEnd = true }) { Icon(Icons.Default.Delete, "Turnier beenden") }
        }
        val tour = t
        if (tour == null) { Text("Kein Turnier – in der Lobby „Als Turnier starten“ wählen.", Modifier.padding(16.dp), color = DartColors.TextMuted); return }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AdCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tour.mode.title.uppercase(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Chip("${tour.players.size} Spieler")
                }
                val real = tour.matches.filter { it.b != null || it.round > 1 }
                Text(matchTitle(tour.settings) + " · ${real.count { it.winner != null }}/${real.size} Spiele gespielt",
                    color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (!vm.canRunTournament) Text("Der Host startet die Spiele – der Spielplan aktualisiert sich live.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            tour.champion?.let { c ->
                AdCard(background = DartColors.GreenDark) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(tour.players[c], 40)
                        Spacer(Modifier.width(10.dp))
                        Text("${tour.players[c].name} gewinnt das Turnier", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (tour.mode == TournamentMode.ROUND_ROBIN) {
                SectionLabel("Tabelle")
                AdCard(padding = 12) {
                    tour.standings().forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.", Modifier.width(28.dp), color = DartColors.TextMuted)
                            Text(tour.players[s.player].name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text("${s.wins}/${s.played} Siege", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(10.dp))
                            Text((if (s.legDiff > 0) "+" else "") + s.legDiff, Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            for (r in 1..tour.rounds) {
                SectionLabel(roundName(tour, r))
                tour.matches.forEachIndexed { i, m -> if (m.round == r) MatchRow(tour, m, vm.canRunTournament) { vm.playTournamentMatch(i) } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text("Turnier beenden?") },
        text = { Text("Der Spielplan wird gelöscht. Gespielte Matches bleiben in der Statistik.") },
        confirmButton = { TextButton(onClick = { confirmEnd = false; vm.endTournament(); vm.back() }) { Text("Beenden", color = DartColors.Red) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Abbrechen") } },
    )
}

private fun roundName(t: Tournament, r: Int): String = when {
    t.mode == TournamentMode.ROUND_ROBIN -> "Runde $r"
    r == t.rounds -> "Finale"
    r == t.rounds - 1 -> "Halbfinale"
    r == t.rounds - 2 -> "Viertelfinale"
    r == t.rounds - 3 -> "Achtelfinale"
    else -> "Runde $r"
}

@Composable
private fun MatchRow(t: Tournament, m: TournamentMatch, canPlay: Boolean, onPlay: () -> Unit) {
    val a = m.a?.let { t.players[it].name }; val b = m.b?.let { t.players[it].name }
    AdCard(padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${a ?: "offen"} – ${b ?: if (m.a != null && m.round == 1) "Freilos" else "offen"}", fontWeight = FontWeight.Bold)
                m.winner?.let { w ->
                    Text(if (m.b == null) "${t.players[w].name} kampflos weiter" else "${t.players[w].name} gewinnt ${m.legsA}:${m.legsB}",
                        color = DartColors.Green, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (m.open && canPlay) PrimaryButton("Spielen", height = 40, onClick = onPlay)
        }
    }
}
