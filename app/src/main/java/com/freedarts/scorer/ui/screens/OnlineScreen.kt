@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.online.Lobby
import com.freedarts.scorer.online.OnlineController
import com.freedarts.scorer.online.Profile
import com.freedarts.scorer.online.RealtimeClient
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.AvatarPicker
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.ModeBadge
import com.freedarts.scorer.ui.components.OAuthButton
import com.freedarts.scorer.ui.components.NameRibbon
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.levelOf
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/**
 * Online-Modus wie bei Autodarts: Konto (E-Mail, Supabase OAuth, Gast), Profil, "Gegner finden", Lobby erstellen,
 * per Code beitreten und öffentliche Lobbys. Server = supabase.com-Projekt oder eigener Stack (selfhost/).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScreen(vm: AppViewModel) {
    val online = vm.online
    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by online.session.collectAsStateWithLifecycle()
    val profile by online.profile.collectAsStateWithLifecycle()
    val busy by online.busy.collectAsStateWithLifecycle()
    val error by online.error.collectAsStateWithLifecycle()
    val notice by online.notice.collectAsStateWithLifecycle()
    val lobbies by online.lobbies.collectAsStateWithLifecycle()
    val currentLobby by online.lobby.collectAsStateWithLifecycle()
    val connection by online.connection.collectAsStateWithLifecycle()
    val friends by online.friends.collectAsStateWithLifecycle()
    val invite by online.invite.collectAsStateWithLifecycle()
    var showServer by remember { mutableStateOf(!online.configured) }
    var showCreate by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    val loggedIn = session != null && online.configured
    LaunchedEffect(loggedIn) { if (loggedIn && profile == null) runCatching { online.loadProfile() } }
    LaunchedEffect(loggedIn) { if (loggedIn) online.loadLeaderboard() }
    val leaderboard by online.leaderboard.collectAsStateWithLifecycle()
    val offline by online.offline.collectAsStateWithLifecycle()
    // Push bei geschlossener App (Einladungen, Freundschaftsanfragen) braucht ab Android 13 die Erlaubnis für Mitteilungen.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(loggedIn) { if (loggedIn && android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    DisposableEffect(loggedIn) {
        if (loggedIn) online.startLobbyPolling()
        onDispose { online.stopLobbyPolling() }
    }

    Box(Modifier.fillMaxSize()) { ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Online", onBack = { vm.back() }) {
            IconButton(onClick = { showServer = true }) { Icon(Icons.Default.Settings, "Server") }
        }
        PullToRefreshBox(isRefreshing = busy, onRefresh = { online.refreshLobbies(); online.loadLeaderboard() }, modifier = Modifier.weight(1f)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (offline) AdCard(background = DartColors.OrangeDark, padding = 10) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Offline – keine Verbindung zum Server.", color = DartColors.Orange, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { online.refreshLobbies(); online.loadLeaderboard() }) { Text("Erneut versuchen") }
                }
            }
            if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }

            when {
                !online.configured -> {
                    AdCard {
                        Text("SERVER EINRICHTEN", style = MaterialTheme.typography.headlineSmall)
                        Text("Der Online-Modus läuft über Supabase – entweder ein kostenloses Projekt auf supabase.com oder ein eigener Server per Docker (Ordner selfhost/ im Projekt). Beide brauchen nur URL und Anon-Key.",
                            color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OnlineServerFields(vm)
                    }
                }
                session == null -> LoginCard(vm)
                else -> {
                    // Profil
                    val p = profile
                    AdCard(onClick = { showProfile = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val player = Player(id = p?.id ?: "", name = p?.name ?: "…", color = p?.color ?: 0xFF3F51B5, avatar = p?.avatar)
                            Avatar(player, 44)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                val (lvl, bg, fg) = levelOf(p?.avg ?: 0.0)
                                NameRibbon(p?.name ?: "Profil wird geladen …", lvl, bg, fg)
                                Text(session?.user?.email ?: session?.user?.provider ?: "",
                                    color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            ConnectionDot(connection)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            OnlineStat("%.1f".format(p?.avg ?: 0.0), "3 Dart Avg", Modifier.weight(1f))
                            OnlineStat("${p?.matches ?: 0}", "Matches", Modifier.weight(1f))
                            OnlineStat("%.0f%%".format(p?.winRate ?: 0.0), "Win Rate", Modifier.weight(1f))
                        }
                    }

                    invite?.let { InviteCard(it, friends, onAccept = { vm.acceptInvite(it) }, onDismiss = { online.dismissInvite(it) }) }
                    val requests = friends.count { !it.accepted && it.incoming }
                    SecondaryButton(if (requests > 0) "Freunde · $requests Anfrage${if (requests > 1) "n" else ""}" else "Freunde (${friends.count { it.accepted }})",
                        Modifier.fillMaxWidth(), icon = Icons.Default.Group) { vm.openFriends() }

                    currentLobby?.let { l ->
                        AdCard(background = DartColors.GreenDark, onClick = { vm.openOnlineLobby() }) {
                            Text("DEINE LOBBY · ${l.code}", style = MaterialTheme.typography.headlineSmall)
                            Text("${l.players.size}/${l.maxPlayers} Spieler · " + lobbyTitle(l), color = DartColors.OnTileMuted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton("Zur Lobby", Modifier.fillMaxWidth(), height = 44) { vm.openOnlineLobby() }
                        }
                    }

                    // Aktionen
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Gegner finden", Modifier.weight(1f), enabled = !busy) { vm.findOpponent() }
                        SecondaryButton("Lobby erstellen", Modifier.weight(1f), enabled = !busy) { showCreate = true }
                    }
                    AdCard {
                        Text("MIT CODE BEITRETEN", style = MaterialTheme.typography.headlineSmall)
                        var code by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = code, onValueChange = { code = it.uppercase().take(6) }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters))
                            PrimaryButton("Beitreten", enabled = code.length >= 4 && !busy, height = 48) { online.joinByCode(code); vm.openOnlineLobby() }
                        }
                    }

                    SectionLabel("Öffentliche Lobbys", trailing = {
                        IconButton(onClick = { online.refreshLobbies() }) { Icon(Icons.Default.Refresh, "Aktualisieren", tint = DartColors.PrimaryLight) }
                    })
                    val others = lobbies.filter { it.id != currentLobby?.id }
                    if (others.isEmpty()) AdCard { Text("Gerade keine offenen Lobbys – erstelle eine oder nutze „Gegner finden“.", color = DartColors.TextMuted) }
                    others.forEach { l ->
                        if (l.status == "running") LobbyRow(l, enabled = !busy && l.currentMatchId != null) { vm.spectate(l) }
                        else LobbyRow(l, enabled = !busy && !l.isFull) { online.joinByCode(l.code); vm.openOnlineLobby() }
                    }

                    SectionLabel("Rangliste")
                    if (leaderboard.isEmpty()) AdCard { Text("Noch keine gewerteten Online-Matches.", color = DartColors.TextMuted) }
                    else AdCard(padding = 8) {
                        leaderboard.forEachIndexed { i, p -> LeaderboardRow(i + 1, p, mine = p.id == profile?.id) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }

    if (showServer && online.configured) {
        AlertDialog(
            onDismissRequest = { showServer = false },
            title = { Text("Supabase-Server") },
            text = { Column { OnlineServerFields(vm); if (session != null) TextButton(onClick = { online.signOut(); showServer = false }) { Text("Abmelden", color = DartColors.Red) } } },
            confirmButton = { TextButton(onClick = { showServer = false }) { Text("Schließen") } },
        )
    }
    if (showCreate) CreateLobbyDialog(settings.lastGameSettings, onDismiss = { showCreate = false }) { gs, public, max ->
        showCreate = false
        online.createLobby(gs, public, max)
        vm.openOnlineLobby()
    }
    if (showProfile) {
        val p = profile
        var name by remember { mutableStateOf(p?.name ?: "") }
        var color by remember { mutableStateOf(p?.color ?: 0xFF3F51B5) }
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("Online-Profil") },
            text = {
                Column {
                    AvatarPicker(Player(id = p?.id ?: "", name = name.ifBlank { "?" }, color = color, avatar = p?.avatar)) { online.updateAvatar(it) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, label = { Text("Anzeigename") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Player.AVATAR_COLORS.forEach { c ->
                            Box(Modifier.size(32.dp).background(Color(c), CircleShape).border(if (c == color) 3.dp else 0.dp, DartColors.Text, CircleShape).clickable { color = c })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { online.signOut(); showProfile = false }) { Text("Abmelden", color = DartColors.Red) }
                }
            },
            confirmButton = { TextButton(onClick = { online.updateProfile(name, color); showProfile = false }, enabled = name.isNotBlank()) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { showProfile = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun LeaderboardRow(rank: Int, p: Profile, mine: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(if (mine) DartColors.SurfaceHigh else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", fontWeight = FontWeight.Bold, color = if (rank <= 3) DartColors.Accent else DartColors.TextMuted, modifier = Modifier.width(28.dp))
        Avatar(Player(id = p.id, name = p.name, color = p.color, avatar = p.avatar), 32)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val (lvl, bg, fg) = levelOf(p.avg)
            NameRibbon(p.name, lvl, bg, fg, fontSize = 13)
            Text("${p.matches} Matches · %.0f%% Siege".format(p.winRate), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text("%.1f".format(p.avg), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

/** Anzeigename einer Lobby: Modus + Startwert + Match-Titel. */
fun lobbyTitle(l: Lobby): String {
    val gs = l.settings
    return when (gs.mode) {
        GameMode.X01 -> "${gs.baseScore} · ${matchTitle(gs)}"
        else -> gs.mode.title
    }
}

@Composable
private fun LobbyRow(l: Lobby, enabled: Boolean, onJoin: () -> Unit) {
    AdCard(padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val host = l.host
            Avatar(Player(id = host?.id ?: "", name = host?.name ?: "?", color = host?.color ?: 0xFF546E7A, avatar = host?.avatar), 36)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(l.name.ifBlank { host?.name ?: "Lobby" }, fontWeight = FontWeight.Bold)
                Text(lobbyTitle(l) + " · Host Ø %.1f".format(host?.avg ?: 0.0), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            ModeBadge(l.settings.mode)
            Spacer(Modifier.width(8.dp))
            Chip("${l.players.size}/${l.maxPlayers}")
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { settingsChips(l.settings).take(4).forEach { Chip(it) } }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(if (l.status == "running") "Zuschauen" else if (l.isFull) "Voll" else "Beitreten", Modifier.fillMaxWidth(), enabled = enabled, height = 42, onClick = onJoin)
    }
}

@Composable
private fun LoginCard(vm: AppViewModel) {
    val online = vm.online
    val busy by online.busy.collectAsStateWithLifecycle()
    AdCard {
        Text("ONLINE SPIELEN", style = MaterialTheme.typography.headlineSmall)
        Text("Online spielen geht nur mit Konto: ein Tap mit Google oder GitHub, deine Statistik bleibt auf allen Geräten.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        // Supabase OAuth im Browser; Anbieter müssen im Projekt bzw. in selfhost/.env aktiviert sein
        OnlineController.PROVIDERS.forEach { (id, label) ->
            OAuthButton(id, label, Modifier.fillMaxWidth(), enabled = !busy) { online.beginOAuth(id) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Supabase-URL und Anon-Key (auch in den Einstellungen verwendet). */
@Composable
fun OnlineServerFields(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(s.onlineUrl.ifBlank { com.freedarts.scorer.BuildConfig.SUPABASE_URL }) }
    var key by remember { mutableStateOf(s.onlineAnonKey.ifBlank { com.freedarts.scorer.BuildConfig.SUPABASE_ANON_KEY }) }
    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Supabase-URL") }, placeholder = { Text("https://xyz.supabase.co oder http://192.168.1.10:8000") },
        singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Anon-Key / Publishable Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    PrimaryButton("Speichern", Modifier.fillMaxWidth(), enabled = url.startsWith("http") && key.length > 10, height = 44) { vm.setOnlineServer(url, key) }
}

@Composable
private fun CreateLobbyDialog(initial: GameSettings, onDismiss: () -> Unit, onCreate: (GameSettings, Boolean, Int) -> Unit) {
    var gs by remember { mutableStateOf(if (initial.mode.category == GameMode.Category.COMPETITIVE) initial else GameSettings(mode = GameMode.X01, baseScore = 501, legs = 3)) }
    var public by remember { mutableStateOf(true) }
    var max by remember { mutableIntStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Online-Lobby erstellen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Modus", style = MaterialTheme.typography.labelSmall, color = DartColors.TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GameMode.entries.forEach { m -> Chip(m.title, selected = gs.mode == m) { gs = gs.withModeDefaults(m) } }
                }
                Spacer(Modifier.height(6.dp))
                ModeSettings(gs) { gs = it }
                Spacer(Modifier.height(6.dp))
                Text("Spieler", style = MaterialTheme.typography.labelSmall, color = DartColors.TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (2..6).forEach { n -> Chip("$n", selected = max == n) { max = n } } }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Öffentlich (in der Liste sichtbar)", Modifier.weight(1f)); Switch(checked = public, onCheckedChange = { public = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(gs, public, max) }) { Text("Erstellen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun ConnectionDot(state: RealtimeClient.State) {
    val (label, color) = when (state) {
        RealtimeClient.State.OPEN -> "Online" to DartColors.Green
        RealtimeClient.State.CONNECTING -> "Verbinde" to DartColors.Accent
        RealtimeClient.State.RETRYING -> "Neu verbinden" to DartColors.Orange
        RealtimeClient.State.OFF -> "Offline" to DartColors.TextMuted
    }
    Row(Modifier.border(1.dp, color, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape)); Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OnlineStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontFamily = Condensed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
    }
}
