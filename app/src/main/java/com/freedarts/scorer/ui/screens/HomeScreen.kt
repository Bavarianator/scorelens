package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.engine.Statistics
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.DartboardPreview
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.PlayerCardDialog
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.Sparkline
import com.freedarts.scorer.ui.components.StatTile
import com.freedarts.scorer.ui.components.levelOf
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val lobbyPlayers by vm.lobbyPlayers.collectAsStateWithLifecycle()
    val lensStatus by vm.lens.status.collectAsStateWithLifecycle()
    val gameState by vm.gameState.collectAsStateWithLifecycle()
    /** Laufendes Match (per Tab verlassen): Hero führt zurück statt neu zu starten. */
    val running = gameState?.finished == false
    val onlineSession by vm.online.session.collectAsStateWithLifecycle()
    val onlineProfile by vm.online.profile.collectAsStateWithLifecycle()
    val onlineLobby by vm.online.lobby.collectAsStateWithLifecycle()
    val tournament by vm.tournament.collectAsStateWithLifecycle()
    val loggedIn = onlineSession != null && vm.online.configured

    val friends by vm.online.friends.collectAsStateWithLifecycle()
    val invite by vm.online.invite.collectAsStateWithLifecycle()
    var showInbox by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    val requests = friends.filter { !it.accepted && it.incoming }
    val unread = requests.size + (if (invite != null) 1 else 0)
    val profile = players.firstOrNull { it.id == settings.profilePlayerId } ?: players.firstOrNull()
    val myX01 = matches.filter { it.mode == GameMode.X01 }.mapNotNull { m -> m.players.firstOrNull { it.playerId == profile?.id } }
    val last10 = myX01.takeLast(10)
    val avg = Statistics.metricTotal(GameMode.X01, last10)
    val atDouble = last10.sumOf { it.dartsAtDouble }
    val checkout = if (atDouble == 0) 0.0 else 100.0 * last10.sumOf { it.checkouts } / atDouble
    val lvl = levelOf(avg).first
    val onlineIds by vm.online.online.collectAsStateWithLifecycle()
    val friendsOnline = friends.count { it.accepted && it.id in onlineIds }
    val df = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY) }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 200)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile != null) Box(Modifier.clickable { showCard = true }) { Avatar(profile, 40) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { BrandTitle("Scorelens", size = 22) }
                IconButton(onClick = { showInbox = true }) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) { Icon(Icons.Default.Notifications, "Mitteilungen") }
                }
                IconButton(onClick = { vm.navigate(Screen.Players) }) { Icon(Icons.Default.Group, "Spieler") }
            }
            Spacer(Modifier.height(12.dp))

            // Weiter spielen: letzte Einstellungen und Spieler, ein Tipp
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(DartColors.Hero)).border(1.dp, DartColors.CardBorder, RoundedCornerShape(16.dp))
                    .clickable { if (running) vm.navigate(Screen.Match) else vm.playNow() },
            ) {
                // Karte wächst mit dem Inhalt, Board bleibt komplett innerhalb (kein Offset, kein Abschneiden)
                DartboardPreview(Modifier.size(112.dp).align(Alignment.CenterEnd).padding(end = 12.dp))
                Column(Modifier.padding(start = 16.dp, top = 18.dp, bottom = 16.dp, end = 136.dp)) {
                    Text(if (running) "WEITER SPIELEN" else "NOCHMAL SPIELEN", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp)
                    val last = settings.lastGameSettings
                    Text((if (running) "Match läuft · " else "") + last.mode.title + (if (last.mode == GameMode.X01) " ${last.baseScore}" else "") + " · " + lobbyPlayers.joinToString(", ") { it.name }.ifEmpty { profile?.name ?: "" },
                        fontSize = 13.sp, color = DartColors.OnTileMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.background(DartColors.HeroPill, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (lensStatus.running) DartColors.Green else DartColors.TextMuted, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(if (lensStatus.running) "Lens bereit" else if (settings.lensCalibration.size == 8) "Lens startet im Match" else "Lens aus", fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Neues Spiel und Gegner finden nebeneinander
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(DartColors.TileNew)).border(1.dp, DartColors.CardBorder, RoundedCornerShape(16.dp)).clickable { vm.navigate(Screen.Lobby) },
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
                        Text("NEUES SPIEL", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Modus, Spieler, Bot", fontSize = 12.sp, color = DartColors.OnTileMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(
                    Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(DartColors.TileFind)).border(1.dp, DartColors.CardBorder, RoundedCornerShape(16.dp)).clickable { vm.findOpponent() },
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
                        Text("GEGNER FINDEN", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(if (loggedIn) "Online · 501 · First to 3" else "Bot · $lvl", fontSize = 12.sp, color = DartColors.OnTileMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Online spielen (Lobbys wie bei Autodarts)
            Box(
                Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(DartColors.TileOnline)).clickable { if (onlineLobby != null) vm.openOnlineLobby() else vm.navigate(Screen.Online) },
            ) {
                Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("ONLINE SPIELEN", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Color.White)
                        Text(
                            when {
                                onlineLobby != null -> "Deine Lobby ${onlineLobby?.code} · ${onlineLobby?.players?.size}/${onlineLobby?.maxPlayers} Spieler"
                                friendsOnline > 0 -> "$friendsOnline ${if (friendsOnline == 1) "Freund" else "Freunde"} online · Lobbys, Turniere"
                                loggedIn -> "Lobbys, Freunde, Turniere · ${onlineProfile?.name ?: "…"}"
                                vm.online.configured -> "Mit Google oder GitHub anmelden"
                                else -> "Server eintragen"
                            },
                            fontSize = 13.sp, color = Color(0xFFDDE6F5), maxLines = 2, overflow = TextOverflow.Ellipsis, // Online-Kachel ist in beiden Themes dunkel
                        )
                    }
                    Chip(if (onlineLobby != null) "Lobby" else if (loggedIn) "Online" else "Login")
                }
            }

            Spacer(Modifier.height(10.dp))
            // Mit Freunden spielen (Freundesliste, Einladen) und Geräte (Remote Scoring, zweites Handy, Lens)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(DartColors.TileFind)).border(1.dp, DartColors.CardBorder, RoundedCornerShape(16.dp))
                        .clickable { vm.navigate(if (loggedIn) Screen.Friends else Screen.Online) },
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
                        Text("MIT FREUNDEN", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(if (friendsOnline > 0) "$friendsOnline online · einladen" else if (loggedIn) "Einladen, zuschauen" else "Anmelden, dann einladen", fontSize = 12.sp, color = DartColors.OnTileMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(
                    Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(DartColors.TileNew)).border(1.dp, DartColors.CardBorder, RoundedCornerShape(16.dp))
                        .clickable { vm.navigate(Screen.Devices) },
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
                        Text("GERÄTE", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Remote, 2. Handy, Lens", fontSize = 12.sp, color = DartColors.OnTileMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            tournament?.let { t ->
                Spacer(Modifier.height(10.dp))
                AdCard(background = DartColors.GreenDark, onClick = { vm.navigate(Screen.Tournament) }) {
                    Text("TURNIER · ${t.mode.title.uppercase()}", style = MaterialTheme.typography.headlineSmall)
                    val open = t.matches.count { it.open }
                    Text(t.champion?.let { "${t.players[it].name} hat gewonnen" } ?: "${t.players.size} Spieler · $open ${if (open == 1) "Spiel" else "Spiele"} offen", fontSize = 13.sp, color = Color(0xFFDDE6F5))
                }
            }

            // Deine Form: Ø, Checkout, Serie der letzten 10 X01-Spiele
            SectionLabel("Deine Form", trailing = { Chip(lvl) })
            AdCard(onClick = { vm.navigate(Screen.Stats) }) {
                if (myX01.isEmpty()) Text("Spiel dein erstes Match, dann steht hier deine Form.", color = DartColors.TextMuted)
                else {
                    val streak = Statistics.streaks(matches, profile?.id ?: "")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("%.1f".format(avg), "Ø letzte ${last10.size}", Modifier.weight(1f))
                        StatTile("%.0f %%".format(checkout), "Checkout", Modifier.weight(1f), barColor = DartColors.Green)
                        StatTile("${kotlin.math.abs(streak.current)}", if (streak.current >= 0) "Siege in Folge" else "Niederlagen", Modifier.weight(1f), barColor = if (streak.current >= 0) DartColors.Lime else DartColors.Red)
                    }
                    if (last10.size >= 2) { Spacer(Modifier.height(6.dp)); Sparkline(last10.map { it.average3 }) }
                }
            }

            // Training des Tages: eine feste Regel aus Checkout und Average – ponytail: drei Schwellen, Trainingspläne erst bei Bedarf
            val (training, why) = when {
                myX01.isEmpty() -> GameMode.SEGMENT_TRAINING to "Zum Einwerfen: 10 Runden auf die 20"
                checkout < 30 -> GameMode.RANDOM_CHECKOUT to "Checkout-Quote %.0f %% – Finishes üben".format(checkout)
                avg < 50 -> GameMode.SEGMENT_TRAINING to "Ø %.1f – die T20 festigen".format(avg)
                else -> GameMode.BOBS_27 to "Doppel unter Druck"
            }
            SectionLabel("Training des Tages")
            AdCard(onClick = { vm.startTraining(training) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(training.title.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text(why, color = DartColors.TextMuted, fontSize = 13.sp)
                    }
                    Chip("Start", selected = true)
                }
            }

            if (matches.isNotEmpty()) {
                SectionLabel("Letzte Matches", trailing = {
                    Text("Alle", color = DartColors.PrimaryLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { vm.navigate(Screen.History) })
                })
                AdCard { matches.takeLast(3).reversed().forEach { RecentMatchRow(it, profile?.id, df) { vm.openMatch(it) } } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCard && profile != null) PlayerCardDialog(profile, matches, profile.id, onDismiss = { showCard = false }, onAllAchievements = { showCard = false; vm.navigate(Screen.Achievements(profile.id)) })
    // Mitteilungen: Einladungen, Freundschaftsanfragen und letzte Ergebnisse an einem Ort (alles aus vorhandenem Zustand, keine eigene Ablage)
    if (showInbox) {
        AlertDialog(
            onDismissRequest = { showInbox = false },
            title = { Text("Mitteilungen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    invite?.let { i -> InviteCard(i, friends, onAccept = { showInbox = false; vm.acceptInvite(i) }, onDismiss = { vm.online.dismissInvite(i) }) }
                    if (requests.isNotEmpty()) {
                        Text("Freundschaftsanfragen", fontWeight = FontWeight.Bold)
                        requests.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Avatar(f.player(), 32, online = false)
                                Text(f.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                PrimaryButton("Annehmen", height = 36) { vm.online.acceptFriend(f.id) }
                                SecondaryButton("Nein") { vm.online.removeFriend(f.id) }
                            }
                        }
                    }
                    Text("Letzte Ergebnisse", fontWeight = FontWeight.Bold)
                    if (matches.isEmpty()) Text("Noch keine Matches.", color = DartColors.TextMuted)
                    matches.takeLast(5).reversed().forEach { RecentMatchRow(it, profile?.id, df) { showInbox = false; vm.openMatch(it) } }
                    if (unread == 0 && matches.isEmpty()) Text("Nichts Neues.", color = DartColors.TextMuted)
                }
            },
            confirmButton = { TextButton(onClick = { showInbox = false }) { Text("Schließen") } },
        )
    }
}

/** Eine Zeile Match-Ergebnis: Sieg/Niederlage, Paarung, Modus und Zeit. */
@Composable
private fun RecentMatchRow(m: MatchRecord, profileId: String?, df: SimpleDateFormat, onClick: () -> Unit) {
    val me = m.players.firstOrNull { it.playerId == profileId }
    val label = if (me == null) "Match" else if (me.won) "Sieg" else if (m.winnerId == null) "Remis" else "Niederlage"
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (me?.won == true) DartColors.Green else DartColors.TextMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(84.dp))
        Column(Modifier.weight(1f)) {
            Text(m.players.joinToString(" vs ") { it.playerName }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.mode.title + " · " + df.format(Date(m.finishedAt)), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
