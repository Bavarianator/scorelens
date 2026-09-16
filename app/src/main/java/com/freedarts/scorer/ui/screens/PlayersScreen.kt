@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun PlayersScreen(vm: AppViewModel) {
    val players by vm.players.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Player?>(null) }
    var creating by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf<Player?>(null) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    card?.let { p -> com.freedarts.scorer.ui.components.PlayerCardDialog(p, matches, settings.profilePlayerId) { card = null } }

    Scaffold(
        topBar = { TopBar("Spieler", onBack = { vm.back() }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Spieler hinzufügen") } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            items(players, key = { it.id }) { p ->
                val played = matches.count { m -> m.players.any { it.playerId == p.id } }
                val won = matches.count { it.winnerId == p.id }
                val x01 = matches.filter { it.mode == com.freedarts.scorer.model.GameMode.X01 }.mapNotNull { m -> m.players.firstOrNull { it.playerId == p.id } }
                val d = x01.sumOf { it.dartsThrown }
                val avg = if (d == 0) 0.0 else x01.sumOf { it.pointsScored }.toDouble() / d * 3
                val (lvl, lvlBg, lvlFg) = com.freedarts.scorer.ui.components.levelOf(avg)
                Row(Modifier.fillMaxWidth().clickable { card = p }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, 40); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        com.freedarts.scorer.ui.components.NameRibbon(p.name, lvl, lvlBg, lvlFg)
                        Text("$played Spiele · $won Siege · Ø %.1f".format(avg), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Bearbeiten") }
                    IconButton(onClick = { vm.removePlayer(p.id) }, enabled = players.size > 1) { Icon(Icons.Default.Delete, "Löschen") }
                }
            }
        }
    }

    if (creating) PlayerDialog(null, onDismiss = { creating = false }) { name, color, avatar -> vm.addPlayer(name, color, avatar); creating = false }
    editing?.let { p -> PlayerDialog(p, onDismiss = { editing = null }) { name, color, avatar -> vm.updatePlayer(p.copy(name = name, color = color, avatar = avatar)); editing = null } }
}

@Composable
private fun PlayerDialog(player: Player?, onDismiss: () -> Unit, onSave: (String, Long, String?) -> Unit) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var color by remember { mutableStateOf(player?.color ?: Player.AVATAR_COLORS.random()) }
    var avatar by remember { mutableStateOf(player?.avatar) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (player == null) "Neuer Spieler" else "Spieler bearbeiten") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.size(12.dp))
                com.freedarts.scorer.ui.components.AvatarPicker(Player(name = name.ifBlank { "?" }, color = color, avatar = avatar)) { avatar = it }
                Spacer(Modifier.size(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Player.AVATAR_COLORS.forEach { c ->
                        Box(
                            Modifier.size(34.dp).background(Color(c), CircleShape)
                                .border(3.dp, if (c == color) DartColors.Text else Color.Transparent, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name, color, avatar) }, enabled = name.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
