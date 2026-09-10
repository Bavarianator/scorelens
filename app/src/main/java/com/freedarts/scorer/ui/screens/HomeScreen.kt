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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.board.BoardManagerClient
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Badge
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.DartboardPreview
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.LevelBadge
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun HomeScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val lobbyPlayers by vm.lobbyPlayers.collectAsStateWithLifecycle()
    val connection by vm.board.connection.collectAsStateWithLifecycle()
    val lensStatus by vm.lens.status.collectAsStateWithLifecycle()

    val profile = players.firstOrNull { it.id == settings.profilePlayerId } ?: players.firstOrNull()
    val myX01 = matches.filter { it.mode == GameMode.X01 }.mapNotNull { m -> m.players.firstOrNull { it.playerId == profile?.id } }
    val allMine = matches.mapNotNull { m -> m.players.firstOrNull { it.playerId == profile?.id } }
    val darts = myX01.sumOf { it.dartsThrown }
    val avg = if (darts == 0) 0.0 else myX01.sumOf { it.pointsScored }.toDouble() / darts * 3
    val winRate = if (allMine.isEmpty()) 0.0 else 100.0 * allMine.count { it.won } / allMine.size
    val dad = myX01.sumOf { it.dartsAtDouble }
    val co = if (dad == 0) 0.0 else 100.0 * myX01.sumOf { it.checkouts } / dad

    Box(Modifier.fillMaxSize()) {
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 170)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            // Kopfzeile
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile != null) Box(Modifier.clickable { vm.navigate(Screen.Players) }) { Avatar(profile, 34) }
                Spacer(Modifier.width(10.dp))
                BrandTitle("FreeDarts")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.navigate(Screen.Players) }) { Icon(Icons.Default.Group, "Spieler") }
                IconButton(onClick = { vm.navigate(Screen.Settings) }) { Icon(Icons.Default.Settings, "Einstellungen") }
            }
            Spacer(Modifier.height(10.dp))

            // Profilkarte
            AdCard(onClick = { vm.navigate(Screen.Stats) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profile != null) Avatar(profile, 40)
                    Spacer(Modifier.width(10.dp))
                    Text(profile?.name?.uppercase() ?: "SPIELER", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    LevelBadge(avg)
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    ProfileStat("%.1f".format(avg), "3 Dart Avg", Modifier.weight(1f))
                    ProfileStat("%.1f %%".format(winRate), "Win Rate", Modifier.weight(1f))
                    ProfileStat("%.1f %%".format(co), "Checkout %", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))

            // Play Now
            HeroCard(
                title = "PLAY NOW",
                subtitle = "Solo, gegen Freunde oder gegen den Bot",
                gradient = listOf(Color(0xFF123B8C), Color(0xFF1FA48A)),
                onClick = { vm.navigate(Screen.Lobby) },
            ) { DartboardPreview(Modifier.size(96.dp)) }
            Spacer(Modifier.height(10.dp))
            HeroCard(
                title = "SOFORT SPIELEN",
                subtitle = settings.lastGameSettings.mode.title + (if (settings.lastGameSettings.mode == GameMode.X01) " ${settings.lastGameSettings.baseScore}" else "") +
                    " · " + lobbyPlayers.joinToString(", ") { it.name }.ifEmpty { profile?.name ?: "" },
                gradient = listOf(Color(0xFF1D2A4A), Color(0xFF243B6B)),
                onClick = { vm.playNow() },
            ) { Badge("LAST SETTINGS", Color.White, DartColors.Primary) }

            SectionLabel("Autoscoring")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceTile(
                    "Lens", if (lensStatus.running) lensStatus.message else "Handykamera", Icons.Default.CameraAlt,
                    active = lensStatus.running, modifier = Modifier.weight(1f),
                ) { vm.navigate(Screen.Lens) }
                DeviceTile(
                    "Board Manager", when (connection) {
                        BoardManagerClient.Connection.CONNECTED -> "Verbunden"
                        BoardManagerClient.Connection.CONNECTING -> "Verbinde …"
                        BoardManagerClient.Connection.ERROR -> "Nicht erreichbar"
                        else -> "Autodarts-Hardware"
                    }, Icons.Default.Videocam, active = connection == BoardManagerClient.Connection.CONNECTED, modifier = Modifier.weight(1f),
                ) { vm.navigate(Screen.Board) }
            }

            SectionLabel("Letzte Matches", trailing = {
                Text("Alle anzeigen", color = DartColors.Primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { vm.navigate(Screen.History) })
            })
            if (matches.isEmpty()) {
                AdCard { Text("Noch keine Matches – starte mit Play Now.", color = DartColors.TextMuted) }
            } else matches.takeLast(3).reversed().forEach { m ->
                AdCard(padding = 10) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.players.joinToString(" vs ") { it.playerName }, fontWeight = FontWeight.SemiBold)
                            Text(m.mode.title + (if (m.mode == GameMode.X01) " ${m.settings.baseScore}" else "") + " · " + formatDuration(m.durationMillis),
                                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        val w = m.players.firstOrNull { it.won }
                        Badge(w?.playerName ?: "Remis", Color.White, if (w != null) DartColors.GreenDark else DartColors.SurfaceHigh)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, gradient: List<Color>, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(96.dp).background(Brush.horizontalGradient(gradient), RoundedCornerShape(14.dp))
            .border(1.dp, DartColors.Outline, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontSize = 22.sp)
            Text(subtitle, color = Color(0xFFDDE6F5), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        trailing()
    }
}

@Composable
private fun DeviceTile(title: String, subtitle: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AdCard(modifier = modifier, onClick = onClick, padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) DartColors.Lime else DartColors.Primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = if (active) DartColors.Lime else DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}
