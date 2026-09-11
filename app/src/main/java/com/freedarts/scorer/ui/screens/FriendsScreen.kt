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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.online.Friend
import com.freedarts.scorer.online.Invite
import com.freedarts.scorer.online.OnlineController
import com.freedarts.scorer.online.Profile
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.NameRibbon
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.QrCode
import com.freedarts.scorer.ui.components.QrScannerDialog
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.levelOf
import com.freedarts.scorer.ui.theme.DartColors

/** Freunde wie bei Autodarts: per QR-Code oder Namen hinzufügen, Anfragen, Statistik und schnell zusammen spielen. */
@Composable
fun FriendsScreen(vm: AppViewModel) {
    val online = vm.online
    val friends by online.friends.collectAsStateWithLifecycle()
    val invite by online.invite.collectAsStateWithLifecycle()
    val busy by online.busy.collectAsStateWithLifecycle()
    val error by online.error.collectAsStateWithLifecycle()
    val notice by online.notice.collectAsStateWithLifecycle()
    var showScanner by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Profile>?>(null) }
    LaunchedEffect(query) { results = if (query.trim().length < 2) null else runCatching { online.searchProfiles(query) }.getOrNull() }

    Box(Modifier.fillMaxSize()) { ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Freunde", onBack = { vm.back() }) {
            IconButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, "QR-Code scannen") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { msg -> Banner(msg, DartColors.RedDark) { online.error.value = null } }
            notice?.let { msg -> Banner(msg, DartColors.GreenDark) { online.notice.value = null } }
            invite?.let { InviteCard(it, friends, onAccept = { vm.acceptInvite(it) }, onDismiss = { online.dismissInvite(it) }) }
            if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }

            AdCard {
                Text("MEIN QR-CODE", style = MaterialTheme.typography.headlineSmall)
                Text("Freunde scannen diesen Code in ihrer App (Symbol oben rechts) und schicken dir eine Anfrage.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                online.friendLink?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { QrCode(it, size = 200.dp) } }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("QR-Code eines Freundes scannen", Modifier.fillMaxWidth(), icon = Icons.Default.QrCodeScanner, height = 44) { showScanner = true }
            }

            AdCard {
                Text("PER NAME HINZUFÜGEN", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(value = query, onValueChange = { query = it.take(32) }, label = { Text("Anzeigename") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                results?.let { list ->
                    if (list.isEmpty()) Text("Niemand gefunden", color = DartColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
                    list.forEach { p ->
                        val known = friends.firstOrNull { it.id == p.id }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Player(id = p.id, name = p.name, color = p.color, avatar = p.avatar), 36, online = false)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text("Ø %.1f · %d Matches".format(p.avg, p.matches), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            when {
                                known == null -> TextButton(onClick = { online.addFriend(p.id); query = "" }, enabled = !busy) { Text("Hinzufügen") }
                                known.accepted -> Text("Freund", color = DartColors.TextMuted)
                                else -> Text("Angefragt", color = DartColors.TextMuted)
                            }
                        }
                    }
                }
            }

            val incoming = friends.filter { !it.accepted && it.incoming }
            val outgoing = friends.filter { !it.accepted && !it.incoming }
            val accepted = friends.filter { it.accepted }
            if (incoming.isNotEmpty()) {
                SectionLabel("Anfragen")
                incoming.forEach { f ->
                    AdCard(padding = 12) {
                        FriendHeader(f)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton("Annehmen", Modifier.weight(1f), enabled = !busy, height = 42) { online.acceptFriend(f.id) }
                            SecondaryButton("Ablehnen", Modifier.weight(1f), enabled = !busy) { online.removeFriend(f.id) }
                        }
                    }
                }
            }
            if (outgoing.isNotEmpty()) {
                SectionLabel("Gesendete Anfragen")
                outgoing.forEach { f ->
                    AdCard(padding = 12) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { FriendHeader(f) }
                            IconButton(onClick = { online.removeFriend(f.id) }, enabled = !busy) { Icon(Icons.Default.Close, "Zurückziehen", tint = DartColors.TextMuted) }
                        }
                    }
                }
            }

            SectionLabel("Freunde (${accepted.size})")
            if (accepted.isEmpty()) AdCard { Text("Noch keine Freunde – scanne einen QR-Code oder suche nach dem Namen.", color = DartColors.TextMuted) }
            accepted.forEach { f -> FriendRow(f, online, busy, onInvite = { vm.inviteFriend(f.id) }, onJoin = { code -> online.joinByCode(code); vm.openOnlineLobby() }) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showScanner) QrScannerDialog(onDismiss = { showScanner = false }) { text ->
        showScanner = false
        val id = OnlineController.friendIdFrom(text)
        if (id != null) online.addFriend(id) else online.error.value = "Das ist kein Scorelens-Freundescode"
    }
}

@Composable
private fun Banner(msg: String, background: Color, onClose: () -> Unit) {
    AdCard(background = background, padding = 10) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(msg, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClose) { Text("OK") }
        }
    }
}

/** Einladung eines Freundes in seine Lobby (auf Online- und Freunde-Seite). */
@Composable
fun InviteCard(invite: Invite, friends: List<Friend>, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val name = friends.firstOrNull { it.id == invite.fromId }?.name ?: "Ein Freund"
    AdCard(background = DartColors.GreenDark) {
        Text("EINLADUNG", style = MaterialTheme.typography.headlineSmall)
        Text("$name lädt dich in eine Lobby ein (Code ${invite.code}).", color = Color(0xFFDDE6F5), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Beitreten", Modifier.weight(1f), height = 44, onClick = onAccept)
            SecondaryButton("Ablehnen", Modifier.weight(1f), onClick = onDismiss)
        }
    }
}

@Composable
private fun FriendHeader(f: Friend) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(f.player(), 40, online = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val (lvl, bg, fg) = levelOf(f.avg)
            NameRibbon(f.name, lvl, bg, fg)
            Text("Ø %.1f · %d Matches · %.0f%% Siege".format(f.avg, f.matches, f.winRate), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FriendRow(f: Friend, online: OnlineController, busy: Boolean, onInvite: () -> Unit, onJoin: (String) -> Unit) {
    AdCard(padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { FriendHeader(f) }
            IconButton(onClick = { online.removeFriend(f.id) }, enabled = !busy) { Icon(Icons.Default.Close, "Freund entfernen", tint = DartColors.TextMuted) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (f.played == 0) "Noch kein Duell gegeneinander" else "Duelle: ${f.played} · du hast ${f.won} gewonnen, ${f.name} ${f.played - f.won}",
            color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val code = f.lobbyCode
            if (code != null) PrimaryButton("Lobby beitreten", Modifier.weight(1f), enabled = !busy, height = 42) { onJoin(code) }
            PrimaryButton("Einladen", Modifier.weight(1f), enabled = !busy, height = 42, onClick = onInvite)
        }
    }
}
