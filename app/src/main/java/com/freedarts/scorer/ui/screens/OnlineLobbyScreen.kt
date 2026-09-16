@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.ModeBadge
import com.freedarts.scorer.ui.components.NameRibbon
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.description
import com.freedarts.scorer.ui.components.levelOf
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/** Wartebereich einer Online-Lobby: Code teilen, Spieler, Modus (Host bearbeitet), Start. */
@Composable
fun OnlineLobbyScreen(vm: AppViewModel) {
    val online = vm.online
    val lobby by online.lobby.collectAsStateWithLifecycle()
    val busy by online.busy.collectAsStateWithLifecycle()
    val error by online.error.collectAsStateWithLifecycle()
    val notice by online.notice.collectAsStateWithLifecycle()
    val connection by online.connection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showSettings by remember { mutableStateOf(false) }
    var showHowTo by remember { mutableStateOf(false) }
    var showTournament by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val isHost = online.isHost
    val me = online.myId

    Box(Modifier.fillMaxSize()) { ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Online-Lobby", onBack = { confirmLeave = true }) { ConnectionDot(connection) }
        val l = lobby
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (l == null) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator() else Text("Keine Lobby – zurück zur Übersicht.", color = DartColors.TextMuted)
                }
                if (!busy) SecondaryButton("Zurück", Modifier.fillMaxWidth()) { vm.leaveOnlineLobby() }
                return@Column
            }

            // Code
            AdCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LOBBY-CODE", style = MaterialTheme.typography.labelSmall, color = DartColors.TextMuted)
                        Text(l.code, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 4.sp, color = DartColors.Lime)
                    }
                    Chip(if (l.isPublic) "Öffentlich" else "Privat")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Teilen", Modifier.weight(1f)) {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Spiel mit mir Darts in Scorelens! Lobby-Code: ${l.code}") }
                        runCatching { context.startActivity(Intent.createChooser(send, "Code teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    SecondaryButton("Kopieren", Modifier.weight(1f)) { clipboard.setText(AnnotatedString(l.code)) }
                }
            }

            // Spieler
            AdCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PLAYERS", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp)); Chip("${l.players.size}/${l.maxPlayers}")
                }
                Spacer(Modifier.height(8.dp))
                l.sortedPlayers.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Player(id = p.userId, name = p.name, color = p.color, avatar = p.avatar), 36)
                        Spacer(Modifier.width(8.dp))
                        val (lvl, bg, fg) = levelOf(p.profile?.avg ?: 0.0)
                        NameRibbon(p.name + (if (p.userId == me) " (du)" else ""), lvl, bg, fg)
                        Spacer(Modifier.weight(1f))
                        if (p.userId == l.hostId) Chip("Host")
                        else if (p.ready) Text("✓ bereit", color = DartColors.Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (isHost && p.userId != me) IconButton(onClick = { online.kick(p.userId) }) { Icon(Icons.Default.Close, "Entfernen", tint = DartColors.TextMuted) }
                    }
                }
                repeat((l.maxPlayers - l.players.size).coerceAtLeast(0)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).border(1.dp, DartColors.Outline, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("Warten auf Spieler …", color = DartColors.TextMuted)
                    }
                }
            }

            // Modus
            val gs = l.settings
            AdCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (gs.mode == GameMode.X01) gs.baseScore.toString() else gs.mode.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ModeBadge(gs.mode)
                }
                Text(gs.mode.description(), color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                Text("ⓘ How to play", color = Color.White, style = MaterialTheme.typography.labelMedium, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.padding(top = 4.dp).clickable { showHowTo = true })
                Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F5A46))); Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { settingsChips(gs).forEach { Chip(it) } }
                if (isHost) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Edit settings", Modifier.fillMaxWidth(), icon = Icons.Default.Settings) { showSettings = true }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Öffentlich", Modifier.weight(1f)); Switch(checked = l.isPublic, onCheckedChange = { online.updateLobby(isPublic = it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Spieler", Modifier.width(80.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (2..6).forEach { n -> Chip("$n", selected = l.maxPlayers == n) { if (n >= l.players.size) online.updateLobby(maxPlayers = n) } }
                        }
                    }
                } else Text("Der Host legt Modus und Einstellungen fest.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            Text("Jeder wirft an seinem eigenen Board (Lens, Board Manager oder manuell); die Scores laufen live auf allen Geräten.",
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(70.dp))
        }
        if (l != null) Box(Modifier.fillMaxWidth().padding(12.dp)) {
            if (l.tournament != null) PrimaryButton("Turnier-Spielplan", Modifier.fillMaxWidth(), height = 56) { vm.navigate(Screen.Tournament) }
            else if (isHost) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (l.players.size < 2) "Warten auf Spieler …" else "Start Game", Modifier.weight(1f), enabled = l.players.size >= 2 && !busy, height = 56) { online.startMatch() }
                if (l.players.size >= 3) SecondaryButton("Turnier", enabled = !busy) { showTournament = true }
            }
            else {
                val meReady = l.players.firstOrNull { it.userId == me }?.ready == true
                PrimaryButton(if (meReady) "Bereit ✓ – warten auf Host" else "Bereit", Modifier.fillMaxWidth(), height = 56) { online.setReady(!meReady) }
            }
        }
    }

    val l = lobby
    if (showTournament) AlertDialog(
        onDismissRequest = { showTournament = false },
        title = { Text("Als Turnier starten") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alle Spieler der Lobby; jedes Spiel ist ein Online-Match der beiden Beteiligten, die anderen schauen zu.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            com.freedarts.scorer.model.TournamentMode.entries.forEach { m -> PrimaryButton(m.title, Modifier.fillMaxWidth(), height = 46) { showTournament = false; vm.startOnlineTournament(m) } }
        } },
        confirmButton = {}, dismissButton = { TextButton(onClick = { showTournament = false }) { Text("Abbrechen") } },
    )
    if (showSettings && l != null) {
        var draft by remember(l.settings) { mutableStateOf(l.settings) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("${draft.mode.title} – Einstellungen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Modus", style = MaterialTheme.typography.labelSmall, color = DartColors.TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GameMode.entries.forEach { m -> Chip(m.title, selected = draft.mode == m) { draft = draft.withModeDefaults(m) } }
                    }
                    Spacer(Modifier.height(6.dp))
                    ModeSettings(draft) { draft = it }
                }
            },
            confirmButton = { TextButton(onClick = { online.updateLobby(settings = draft); showSettings = false }) { Text("Übernehmen") } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Abbrechen") } },
        )
    }
    if (showHowTo && l != null) {
        AlertDialog(onDismissRequest = { showHowTo = false }, title = { Text(l.settings.mode.title) }, text = { Text(howToPlay(l.settings.mode)) },
            confirmButton = { TextButton(onClick = { showHowTo = false }) { Text("OK") } })
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Lobby verlassen?") },
            text = { Text(if (isHost) "Ein anderer Spieler wird Host; ist niemand mehr da, wird die Lobby geschlossen." else "Du kannst mit dem Code jederzeit wieder beitreten.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leaveOnlineLobby() }) { Text("Verlassen", color = DartColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Bleiben") } },
        )
    }
}
