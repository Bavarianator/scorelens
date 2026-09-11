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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.DartboardPreview
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.NameRibbon
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.levelOf
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun HomeScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val lobbyPlayers by vm.lobbyPlayers.collectAsStateWithLifecycle()
    val lensStatus by vm.lens.status.collectAsStateWithLifecycle()
    val onlineSession by vm.online.session.collectAsStateWithLifecycle()
    val onlineProfile by vm.online.profile.collectAsStateWithLifecycle()
    val onlineLobby by vm.online.lobby.collectAsStateWithLifecycle()
    val loggedIn = onlineSession != null && vm.online.configured

    val profile = players.firstOrNull { it.id == settings.profilePlayerId } ?: players.firstOrNull()
    val myX01 = matches.filter { it.mode == GameMode.X01 }.mapNotNull { m -> m.players.firstOrNull { it.playerId == profile?.id } }
    val allMine = matches.mapNotNull { m -> m.players.firstOrNull { it.playerId == profile?.id } }
    val darts = myX01.sumOf { it.dartsThrown }
    val avg = if (darts == 0) 0.0 else myX01.sumOf { it.pointsScored }.toDouble() / darts * 3
    val winRate = if (allMine.isEmpty()) 0.0 else 100.0 * allMine.count { it.won } / allMine.size
    val dad = myX01.sumOf { it.dartsAtDouble }
    val co = if (dad == 0) 0.0 else 100.0 * myX01.sumOf { it.checkouts } / dad
    val (lvl, lvlBg, lvlFg) = levelOf(avg)

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 200)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile != null) Box(Modifier.clickable { vm.navigate(Screen.Players) }) { Avatar(profile, 40) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { BrandTitle("Scorelens", size = 22) }
                IconButton(onClick = { vm.navigate(Screen.Players) }) { Icon(Icons.Default.Group, "Spieler") }
            }
            Spacer(Modifier.height(12.dp))

            AdCard(onClick = { vm.navigate(Screen.Stats) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profile != null) Avatar(profile, 40)
                    Spacer(Modifier.width(10.dp))
                    NameRibbon(profile?.name ?: "Spieler", lvl, lvlBg, lvlFg)
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    ProfileStat("%.1f".format(avg), "3 Dart Avg", Modifier.weight(1f))
                    ProfileStat("%.1f%%".format(winRate), "Win Rate", Modifier.weight(1f))
                    ProfileStat("%.1f%%".format(co), "Checkout %", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))

            // Play Now
            Box(
                Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0E3A8C), Color(0xFF0F6E8F), Color(0xFF16B8B0))))
                    .clickable { vm.navigate(Screen.Lobby) },
            ) {
                DartboardPreview(Modifier.size(150.dp).align(Alignment.TopEnd).offset(x = 10.dp, y = (-9).dp))
                Column(Modifier.padding(start = 16.dp, top = 20.dp)) {
                    Text("PLAY NOW", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    Text("Solo, gegen Freunde oder gegen den Bot", fontSize = 13.sp, color = Color(0xFFDDE6F5))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.background(Color(0x73000000), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (lensStatus.running) DartColors.Green else DartColors.TextMuted, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(if (lensStatus.running) "Lens bereit" else "Lens aus", fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Gegner finden: online (Matchmaking über Supabase), sonst Bot auf eigenem Niveau
            Box(
                Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF15305F), Color(0xFF1D4ED8)))).clickable { vm.findOpponent() },
            ) {
                Column(Modifier.padding(start = 16.dp, top = 18.dp)) {
                    Text("GEGNER FINDEN", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Text(if (loggedIn) "Online-Gegner auf deinem Niveau · 501 · First to 3 Legs" else "Bot auf deinem Niveau · 501 · First to 3 Legs", fontSize = 13.sp, color = Color(0xFFDDE6F5))
                }
                Chip(if (loggedIn) "Online" else lvl, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
            }
            Spacer(Modifier.height(10.dp))

            // Online spielen (Lobbys wie bei Autodarts)
            Box(
                Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3B1D5E), Color(0xFF6D28D9)))).clickable { if (onlineLobby != null) vm.openOnlineLobby() else vm.navigate(Screen.Online) },
            ) {
                Column(Modifier.padding(start = 16.dp, top = 18.dp)) {
                    Text("ONLINE SPIELEN", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Text(
                        when {
                            onlineLobby != null -> "Deine Lobby ${onlineLobby?.code} · ${onlineLobby?.players?.size}/${onlineLobby?.maxPlayers} Spieler"
                            loggedIn -> "Angemeldet als ${onlineProfile?.name ?: "…"} · Lobbys, Code, Gegner finden"
                            vm.online.configured -> "Anmelden mit E-Mail, Google, GitHub, Discord oder als Gast"
                            else -> "Supabase-Server eintragen (supabase.com oder selbst gehostet)"
                        },
                        fontSize = 13.sp, color = Color(0xFFDDE6F5), maxLines = 1,
                    )
                }
                Chip(if (onlineLobby != null) "Lobby" else if (loggedIn) "Online" else "Login", Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
            }
            Spacer(Modifier.height(10.dp))

            // Sofort spielen
            Box(
                Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1D2A4A), Color(0xFF22346A)))).clickable { vm.playNow() },
            ) {
                Column(Modifier.padding(start = 16.dp, top = 18.dp)) {
                    Text("SOFORT SPIELEN", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    val last = settings.lastGameSettings
                    Text(last.mode.title + (if (last.mode == GameMode.X01) " ${last.baseScore}" else "") + " · " + lobbyPlayers.joinToString(", ") { it.name }.ifEmpty { profile?.name ?: "" },
                        fontSize = 13.sp, color = Color(0xFFDDE6F5), maxLines = 1)
                }
                Chip("Last settings", Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
            }

            SectionLabel("Geräte")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdCard(Modifier.weight(1f), onClick = { vm.navigate(Screen.Board) }, padding = 12) {
                    Text("Board Manager", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (settings.boardManagerEnabled) settings.boardManagerHost else "Autodarts-Hardware", fontSize = 12.sp, color = DartColors.TextMuted, maxLines = 1)
                }
                AdCard(Modifier.weight(1f), onClick = { vm.navigate(Screen.Devices) }, padding = 12) {
                    Text("Devices", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Zweiter Bildschirm, Remote", fontSize = 12.sp, color = DartColors.TextMuted, maxLines = 1)
                }
            }

            SectionLabel("Letzte Matches", trailing = {
                Text("Alle anzeigen", color = DartColors.PrimaryLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { vm.navigate(Screen.History) })
            })
            if (matches.isEmpty()) {
                AdCard { Text("Noch keine Matches – starte mit Play Now.", color = DartColors.TextMuted) }
            } else matches.takeLast(3).reversed().forEach { m ->
                val me = m.players.firstOrNull { it.playerId == profile?.id }
                AdCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val won = me?.won == true
                        val label = if (me == null) "Match" else if (won) "Sieg" else if (m.winnerId == null) "Remis" else "Niederlage"
                        val col = if (won) DartColors.Green else DartColors.TextMuted
                        Box(Modifier.border(1.dp, col, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) { Text(label, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.weight(1f))
                        Chip("${m.players.size} Spieler")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(m.players.joinToString(" vs ") { it.playerName }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(m.mode.title + (if (m.mode == GameMode.X01) " ${m.settings.baseScore}" else "") + " • " + formatDuration(m.durationMillis) +
                        (me?.takeIf { m.mode == GameMode.X01 }?.let { " • Ø %.1f".format(it.average3) } ?: ""), color = DartColors.TextMuted, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
    }
}
