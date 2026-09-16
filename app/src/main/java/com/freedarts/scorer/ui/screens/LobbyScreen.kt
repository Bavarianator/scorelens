@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.BullMode
import com.freedarts.scorer.model.BullOff
import com.freedarts.scorer.model.CricketBoard
import com.freedarts.scorer.model.CricketVariant
import com.freedarts.scorer.model.FailMode
import com.freedarts.scorer.model.TargetOrder
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.HitMode
import com.freedarts.scorer.model.InMode
import com.freedarts.scorer.model.MatchMode
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.TournamentMode
import com.freedarts.scorer.model.WinMode
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.NameRibbon
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.levelOf
import com.freedarts.scorer.ui.components.ModeBadge
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.QrScannerDialog
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.description
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun LobbyScreen(vm: AppViewModel) {
    val gs by vm.lobbySettings.collectAsStateWithLifecycle()
    val allPlayers by vm.players.collectAsStateWithLifecycle()
    val lobby by vm.lobbyPlayers.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lensStatus by vm.lens.status.collectAsStateWithLifecycle()
    var showAddPlayer by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    val session by vm.online.session.collectAsStateWithLifecycle()
    val friends by vm.online.friends.collectAsStateWithLifecycle()
    val signedIn = session != null && vm.online.configured
    val onlineError by vm.online.error.collectAsStateWithLifecycle()
    LaunchedEffect(showAddPlayer) { if (showAddPlayer && signedIn) vm.online.loadFriends() }
    var showBots by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHowTo by remember { mutableStateOf(false) }
    var showTournament by remember { mutableStateOf(false) }
    var cardPlayer by remember { mutableStateOf<Player?>(null) }
    cardPlayer?.let { p -> com.freedarts.scorer.ui.components.PlayerCardDialog(p, matches, settings.profilePlayerId) { cardPlayer = null } }

    fun avgOf(p: Player): Double {
        if (p.isBot) return Player.botAverage(p.botLevel).toDouble()
        val st = matches.filter { it.mode == GameMode.X01 }.mapNotNull { m -> m.players.firstOrNull { it.playerId == p.id } }
        val d = st.sumOf { it.dartsThrown }
        return if (d == 0) 0.0 else st.sumOf { it.pointsScored }.toDouble() / d * 3
    }

    Box(Modifier.fillMaxSize()) { ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Create Game", onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // PLAYERS
            AdCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PLAYERS", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    Chip("${lobby.size}/6")
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.clickable { vm.shuffleLobbyPlayers() }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shuffle, null, Modifier.width(18.dp)); Spacer(Modifier.width(4.dp)); Text("Shuffle", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                lobby.forEachIndexed { idx, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            IconButton(onClick = { vm.moveLobbyPlayer(idx, idx - 1) }, enabled = idx > 0, modifier = Modifier.height(20.dp)) { Icon(Icons.Default.ArrowUpward, null, Modifier.width(14.dp)) }
                            IconButton(onClick = { vm.moveLobbyPlayer(idx, idx + 1) }, enabled = idx < lobby.size - 1, modifier = Modifier.height(20.dp)) { Icon(Icons.Default.ArrowDownward, null, Modifier.width(14.dp)) }
                        }
                        Spacer(Modifier.width(6.dp))
                        Avatar(p, 36); Spacer(Modifier.width(8.dp))
                        val (lvl, lvlBg, lvlFg) = levelOf(avgOf(p))
                        Box(Modifier.clickable { cardPlayer = p }) { NameRibbon(p.name, lvl, lvlBg, lvlFg) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.toggleLobbyPlayer(p) }) { Icon(Icons.Default.Close, "Entfernen", tint = DartColors.TextMuted) }
                    }
                }
                // Prognose fürs erste Leg aus Streuung/Average der Spieler (nur X01)
                if (gs.mode == GameMode.X01 && lobby.size >= 2) {
                    val forecast by androidx.compose.runtime.produceState<DoubleArray?>(null, lobby, gs) { value = vm.forecast(lobby, gs) }
                    forecast?.let { f -> Text("Prognose: " + lobby.mapIndexed { i, p -> "${p.name} ${"%.0f".format(f[i] * 100)} %" }.joinToString(" · "), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Add Player", Modifier.weight(1f), icon = Icons.Default.Add, enabled = lobby.size < 6) { showAddPlayer = true }
                    PrimaryButton("Add Bot", Modifier.weight(1f), icon = Icons.Default.SmartToy, enabled = lobby.size < 6) { showBots = true }
                }
            }

            // GAME MODE
            AdCard(onClick = { vm.navigate(Screen.ModeSelect) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (gs.mode == GameMode.X01) gs.baseScore.toString() else gs.mode.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ModeBadge(gs.mode)
                }
                Text(gs.mode.description(), color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                Text("ⓘ How to play", color = Color.White, style = MaterialTheme.typography.labelMedium, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, modifier = Modifier.padding(top = 4.dp).clickable { showHowTo = true })
                Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F5A46)))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    settingsChips(gs).forEach { Chip(it) }
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Edit settings", Modifier.fillMaxWidth(), icon = Icons.Default.Settings) { showSettings = true }
                Text("Anderen Modus wählen ›", color = DartColors.Primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            }

            // AUTOSCORING
            AdCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AUTOSCORING", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Switch(checked = lensStatus.running || settings.boardManagerEnabled, onCheckedChange = { on -> if (on) vm.navigate(Screen.Lens) else { vm.stopLens(); vm.disconnectBoard() } })
                }
                Spacer(Modifier.height(6.dp))
                // Geräte wie bei Autodarts: Lens (dieses Handy) und Board Manager, aktives Gerät mit Haken
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().border(1.dp, if (lensStatus.running) DartColors.Teal else DartColors.Outline, RoundedCornerShape(12.dp)).clickable { vm.navigate(Screen.Lens) }.padding(12.dp)) {
                        Column {
                            Text("Lens", fontWeight = FontWeight.SemiBold, color = if (lensStatus.running) Color.White else DartColors.PrimaryLight)
                            Text(if (lensStatus.running) lensStatus.message else "Kamera dieses Handys", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (lensStatus.running) Text("✓", color = DartColors.Teal, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))
                    }
                    Box(Modifier.fillMaxWidth().border(1.dp, if (settings.boardManagerEnabled) DartColors.Teal else DartColors.Outline, RoundedCornerShape(12.dp)).clickable { vm.navigate(Screen.Board) }.padding(12.dp)) {
                        Column {
                            Text("Board Manager", fontWeight = FontWeight.SemiBold, color = if (settings.boardManagerEnabled) Color.White else DartColors.PrimaryLight)
                            Text(if (settings.boardManagerEnabled) "${settings.boardManagerHost}:${settings.boardManagerPort}" else "Autodarts-Hardware im WLAN", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (settings.boardManagerEnabled) Text("✓", color = DartColors.Teal, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
            Spacer(Modifier.height(70.dp))
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Start Game", Modifier.fillMaxWidth(), enabled = lobby.isNotEmpty(), height = 56) { vm.startGame() }
            SecondaryButton("Als Turnier starten", Modifier.fillMaxWidth(), enabled = lobby.size >= 2) { showTournament = true }
        }
    }

    if (showTournament) {
        AlertDialog(
            onDismissRequest = { showTournament = false },
            title = { Text("Turnier mit ${lobby.size} Spielern") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Jedes Spiel läuft mit den Einstellungen dieser Lobby (${matchTitle(gs)}).", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    TournamentMode.entries.forEach { m ->
                        PrimaryButton(m.title, Modifier.fillMaxWidth(), height = 46) { showTournament = false; vm.startTournament(m) }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { showTournament = false }) { Text("Abbrechen") } },
        )
    }

    if (showAddPlayer) {
        AlertDialog(
            onDismissRequest = { showAddPlayer = false },
            title = { Text("Spieler hinzufügen") },
            text = {
                Column {
                    allPlayers.forEach { p ->
                        val checked = lobby.any { it.id == p.id }
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleLobbyPlayer(p) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { vm.toggleLobbyPlayer(p) })
                            Avatar(p, 28); Spacer(Modifier.width(10.dp)); Text(p.name)
                        }
                    }
                    TextButton(onClick = { showAddPlayer = false; vm.navigate(Screen.Players) }) { Text("Neuen Spieler / Gast anlegen") }
                    if (signedIn) {
                        // Mitspieler mit eigenem Scorelens-Konto: das Match landet auch in seinem Verlauf
                        Spacer(Modifier.height(6.dp))
                        Text("MIT EIGENEM KONTO", style = MaterialTheme.typography.labelMedium, color = DartColors.TextMuted)
                        Text("Der Mitspieler zeigt seinen QR-Code (Freunde › Mein QR-Code); das Spiel zählt dann auch in seiner Statistik.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        friends.filter { f -> f.accepted && allPlayers.none { it.id == f.id } }.forEach { f ->
                            Row(Modifier.fillMaxWidth().clickable { vm.addFriendPlayer(f) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = false, onCheckedChange = { vm.addFriendPlayer(f) })
                                Avatar(f.player(), 28); Spacer(Modifier.width(10.dp)); Text(f.name)
                            }
                        }
                        TextButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text("QR-Code des Mitspielers scannen") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddPlayer = false }) { Text("Fertig") } },
        )
    }
    if (showScanner) QrScannerDialog(onDismiss = { showScanner = false }) { text -> showScanner = false; vm.addAccountPlayer(text) }
    if (showBots) {
        AlertDialog(
            onDismissRequest = { showBots = false },
            title = { Text("Bot hinzufügen") },
            text = {
                Column {
                    Text("Elf Stufen, auch mehrere Bots – jeder wirft mit realistischer Streuung.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..11).forEach { lvl ->
                            FilterChip(selected = lobby.any { it.botLevel == lvl }, onClick = { vm.addBot(lvl); showBots = false },
                                label = { Text("Stufe $lvl · Ø ${Player.botAverage(lvl)}") })
                        }
                        FilterChip(selected = lobby.any { it.botLevel == Player.ADAPTIVE }, onClick = { vm.addBot(Player.ADAPTIVE); showBots = false },
                            label = { Text("Wie ich · startet auf deinem Average, wächst mit") })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBots = false }) { Text("Schließen") } },
            dismissButton = { if (lobby.any { it.isBot }) TextButton(onClick = { vm.removeBot(); showBots = false }) { Text("Alle Bots entfernen") } },
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("${gs.mode.title} – Einstellungen") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ModeSettings(gs, lobby) { vm.setLobbySettings(it) } } },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Fertig") } },
        )
    }
    if (showHowTo) {
        AlertDialog(
            onDismissRequest = { showHowTo = false },
            title = { Text(gs.mode.title) },
            text = { Text(howToPlay(gs.mode)) },
            confirmButton = { TextButton(onClick = { showHowTo = false }) { Text("OK") } },
        )
    }
}

/** "First to 3 Legs" / "Best of 5 Sets" wie in der Autodarts-Lobby. */
fun matchTitle(gs: GameSettings): String {
    val prefix = if (gs.winMode == WinMode.BEST_OF) "Best of" else "First to"
    return if (gs.matchMode == MatchMode.SETS) "$prefix ${gs.sets} Set" + (if (gs.sets > 1) "s" else "")
    else "$prefix ${gs.legs} Leg" + (if (gs.legs > 1) "s" else "")
}

private fun starterChips(gs: GameSettings): List<String> = when {
    gs.bullOff == BullOff.OFFICIAL -> listOf("Bull-off (offiziell)")
    gs.bullOff == BullOff.NORMAL -> listOf("Bull-off")
    gs.randomStarter -> listOf("Zufälliger Start")
    else -> emptyList()
}

fun settingsChips(gs: GameSettings): List<String> = when (gs.mode) {
    GameMode.X01 -> listOf(
        gs.baseScore.toString(),
        matchTitle(gs),
        when (gs.inMode) { InMode.STRAIGHT -> "Straight In"; InMode.DOUBLE -> "Double In"; InMode.MASTER -> "Master In" },
        when (gs.outMode) { OutMode.STRAIGHT -> "Straight Out"; OutMode.DOUBLE -> "Double Out"; OutMode.MASTER -> "Master Out" },
        if (gs.bullMode == BullMode.B25_50) "Bull 25/50" else "Bull 50/50",
    ) + (if (gs.maxRounds > 0) listOf("Max ${gs.maxRounds} Runden") else emptyList()) + starterChips(gs)
    GameMode.CRICKET -> listOf(
        when (gs.effectiveCricketBoard) { CricketBoard.CRICKET -> "Cricket 15–20"; CricketBoard.TACTICS -> "Tactics 10–20"; CricketBoard.HIDDEN -> "Hidden" },
        when (gs.cricketScoring) { CricketVariant.CUT_THROAT -> "Cut Throat"; CricketVariant.NO_SCORE -> "No Score"; else -> "Standard" },
    ) + (if (gs.maxRounds > 0) listOf("Max ${gs.maxRounds} Runden") else emptyList()) + starterChips(gs)
    GameMode.AROUND_THE_CLOCK -> listOf(hitModeLabel(gs.hitMode), orderLabel(gs), if (gs.includeBull) "Mit Bull" else "Ohne Bull") + (if (gs.hitsRequired > 1) listOf("${gs.hitsRequired} Treffer") else emptyList())
    GameMode.COUNT_UP, GameMode.SHANGHAI -> listOf("${gs.rounds} Runden")
    GameMode.SEGMENT_TRAINING -> listOf("Ziel " + (if (gs.trainingSegment == 25) "Bull" else gs.trainingSegment.toString()), hitModeLabel(gs.hitMode), if (gs.endAfterHits) "${gs.hitCount} Treffer" else "${gs.hitCount} Darts")
    GameMode.ROUND_THE_WORLD -> listOf(if (gs.effectiveOrder == TargetOrder.DOWN) "${gs.rounds}–1" else if (gs.effectiveOrder == TargetOrder.RANDOM) "Zufällig" else "1–${gs.rounds}", if (gs.includeBull) "Mit Bull" else "Ohne Bull")
    GameMode.RANDOM_CHECKOUT -> listOf("${gs.rounds} Legs", "${gs.checkoutMin}–${gs.checkoutMax}", outLabel(gs.outMode)) + (if (gs.checkoutRounds > 1) listOf("${gs.checkoutRounds} Runden/Leg") else emptyList())
    GameMode.ONE_TWENTY_ONE -> listOf("${gs.attempts} Versuche", "${gs.dartsPerAttempt} Darts", when (gs.failMode) { FailMode.SOFT -> "Soft"; FailMode.HARD_RESET -> "Hard Reset"; FailMode.SAFEHOUSE -> "Safehouse" }) + (if (gs.step > 1) listOf("Schritt ${gs.step}") else emptyList())
    GameMode.BOBS_27 -> listOf(if (gs.includeBull) "D1–D20 + Bull" else "D1–D20") + (if (gs.allowNegative) listOf("Negativ erlaubt") else emptyList())
    GameMode.BERMUDA -> listOf("12 Runden")
    GameMode.GOTCHA -> listOf("Ziel ${gs.gotchaTarget}", outLabel(gs.outMode))
    GameMode.KILLER -> listOf("${gs.killerLives} Leben", when (gs.killerHitMode) { HitMode.DOUBLE -> "Doubles"; HitMode.TRIPLE -> "Triples"; else -> "Beliebig" })
}

private fun hitModeLabel(m: HitMode) = when (m) { HitMode.ANY -> "Beliebig"; HitMode.SINGLE -> "Single"; HitMode.DOUBLE -> "Double"; HitMode.TRIPLE -> "Triple" }
private fun outLabel(m: OutMode) = when (m) { OutMode.STRAIGHT -> "Straight Out"; OutMode.DOUBLE -> "Double Out"; OutMode.MASTER -> "Master Out" }
private fun orderLabel(gs: GameSettings) = when (gs.effectiveOrder) { TargetOrder.UP -> "1–20"; TargetOrder.DOWN -> "20–1"; TargetOrder.RANDOM -> "Zufällig" }

fun howToPlay(mode: GameMode): String = when (mode) {
    GameMode.X01 -> "Jeder Spieler startet mit dem Startwert (z.B. 501). Die geworfenen Punkte werden abgezogen. Wer zuerst exakt auf 0 kommt, gewinnt das Leg. Bei Double Out muss der letzte Dart ein Double (oder Bullseye) sein. Wer unter 0 (oder bei Double Out auf 1) fällt, hat einen Bust – die Aufnahme zählt nicht."
    GameMode.CRICKET -> "Ziele sind 15–20 und Bull (Tactics: 10–20, Hidden: sieben zufällige Zahlen, die erst beim ersten Treffer sichtbar werden). Single = 1 Treffer, Double = 2, Triple = 3. Nach drei Treffern ist die Zahl für dich geschlossen; weitere Treffer bringen Punkte, solange ein Gegner sie noch nicht geschlossen hat. Wer alle Zahlen geschlossen hat und mindestens gleich viele Punkte besitzt, gewinnt. Cut Throat: Punkte gehen an die Gegner, wenigste Punkte gewinnen. No Score: nur Schließen zählt."
    GameMode.AROUND_THE_CLOCK -> "Triff die Zahlen 1 bis 20 (oder 20 bis 1, oder zufällig), optional Bull am Ende, der Reihe nach. Pro Zahl sind 1–3 Treffer nötig; Double und Triple zählen als ein Treffer. Wer zuerst fertig ist, gewinnt."
    GameMode.ROUND_THE_WORLD -> "In Runde n ist die Zahl n das Ziel (oder umgekehrt), am Ende optional Bull. Single = 1, Double = 2, Triple = 3 Punkte, also höchstens 9 pro Runde. Höchste Punktzahl nach der letzten Runde gewinnt."
    GameMode.COUNT_UP -> "Acht Runden, alle Punkte zählen. Die höchste Gesamtpunktzahl gewinnt."
    GameMode.RANDOM_CHECKOUT -> "Pro Leg wird ein zufälliger Rest vorgegeben. Du hast eine oder mehrere Aufnahmen, um ihn im eingestellten Out-Modus auszuchecken; Überwerfen = Bust, die Aufnahme zählt nicht. Wer die meisten Legs auscheckt, gewinnt."
    GameMode.BOBS_27 -> "Start bei 27 Punkten. Pro Runde drei Darts auf das Double der Reihe nach (D1 bis D20, optional Bull). Jeder Treffer bringt 2×Zahl, kein Treffer in der Runde kostet 2×Zahl. Bei 0 oder weniger scheidet man aus – außer negative Punkte sind erlaubt."
    GameMode.SEGMENT_TRAINING -> "Wähle eine Zahl und die Trefferart (beliebig, Single, Double oder Triple). Die Übung endet nach einer festen Zahl Treffer (wenigste Darts gewinnen) oder Darts (meiste Treffer gewinnen)."
    GameMode.ONE_TWENTY_ONE -> "Checke das Ziel (Start 121) mit maximal neun oder sechs Darts (Double Out) aus. Erfolg: Ziel steigt um den Schritt, bis 170. Misserfolg: Soft (Ziel −1), Hard Reset (zurück auf 121) oder Safehouse (nie unter das zuletzt gesicherte Ziel). Sieg bei 170 oder mit dem höchsten Ziel über 121."
    GameMode.SHANGHAI -> "Runde 1 zielt auf die 1, Runde 2 auf die 2 usw. Nur Treffer auf die aktuelle Zahl zählen. Single, Double und Triple in einer Aufnahme = Shanghai und sofortiger Sieg."
    GameMode.GOTCHA -> "Alle starten bei 0 und zählen bis exakt zum Ziel hoch; der letzte Dart muss zum Out-Modus passen. Überwerfen = Bust. Landest du genau auf dem Score eines Gegners, fällt er auf 0 zurück."
    GameMode.BERMUDA -> "Zwölf Runden mit festen Zielen (12, 13, 14, Double, 15, 16, 17, Triple, 18, 19, 20, Bullseye). Treffer zählen Punkte; keine Treffer in einer Runde halbieren den Score."
    GameMode.KILLER -> "Jeder bekommt eine Zahl. Treffer auf die eigene Zahl füllen dein Konto (Single 1, Double 2, Triple 3); mit vollen Leben bist du Killer. Killer nehmen Gegnern mit deren Zahl Leben ab; fällst du unter die Schwelle, bist du kein Killer mehr, die eigene Zahl kostet dich Leben. Wer bei 0 noch einmal getroffen wird, ist raus. Der Letzte gewinnt."
}

@Composable
private fun <T> OptionRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DartColors.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (v, l) -> Chip(l, selected = selected == v) { onSelect(v) } }
        }
    }
}

@Composable
private fun NumberRow(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text(value.toString(), modifier = Modifier.width(48.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        OutlinedButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

/** Startspieler: Bull-off (Aus / Normal / Offiziell) oder zufällig – wie in der Autodarts-Lobby. */
@Composable
private fun StarterSettings(gs: GameSettings, onChange: (GameSettings) -> Unit) {
    OptionRow("Bull-off", listOf(BullOff.OFF to "Aus", BullOff.NORMAL to "Normal", BullOff.OFFICIAL to "Offiziell"), gs.bullOff) { onChange(gs.copy(bullOff = it)) }
    if (gs.bullOff == BullOff.OFF) SwitchRow("Zufälliger Startspieler", gs.randomStarter) { onChange(gs.copy(randomStarter = it)) }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ModeSettings(gs: GameSettings, players: List<Player> = emptyList(), onChange: (GameSettings) -> Unit) {
    when (gs.mode) {
        GameMode.X01 -> {
            OptionRow("Startwert", listOf(121, 170, 301, 501, 701, 901, 1001).map { it to it.toString() }, gs.baseScore) { onChange(gs.copy(baseScore = it, handicaps = emptyMap())) }
            // Handicap: eigener Startwert je Spieler (z.B. 401 gegen 501)
            if (players.size > 1) players.forEach { p ->
                OptionRow("Start ${p.name}", listOf(121, 170, 201, 301, 401, 501, 601, 701, 901, 1001).map { it to it.toString() }, gs.handicaps[p.id] ?: gs.baseScore) { onChange(gs.copy(handicaps = gs.handicaps + (p.id to it))) }
            }
            OptionRow("Match-Modus", listOf(MatchMode.LEGS to "Legs", MatchMode.SETS to "Sets"), gs.matchMode) { onChange(gs.copy(matchMode = it)) }
            OptionRow("Wertung", listOf(WinMode.FIRST_TO to "First to", WinMode.BEST_OF to "Best of"), gs.winMode) { onChange(gs.copy(winMode = it)) }
            val winLabel = if (gs.winMode == WinMode.BEST_OF) "Best of" else "First to"
            NumberRow(if (gs.matchMode == MatchMode.SETS) "Legs pro Set ($winLabel)" else "Legs ($winLabel)", gs.legs, 1..21) { onChange(gs.copy(legs = it)) }
            if (gs.matchMode == MatchMode.SETS) NumberRow("Sets ($winLabel)", gs.sets, 1..13) { onChange(gs.copy(sets = it)) }
            OptionRow("In-Modus", listOf(InMode.STRAIGHT to "Straight In", InMode.DOUBLE to "Double In", InMode.MASTER to "Master In"), gs.inMode) { onChange(gs.copy(inMode = it)) }
            OptionRow("Out-Modus", listOf(OutMode.STRAIGHT to "Straight Out", OutMode.DOUBLE to "Double Out", OutMode.MASTER to "Master Out"), gs.outMode) { onChange(gs.copy(outMode = it)) }
            OptionRow("Bull-Modus", listOf(BullMode.B25_50 to "25 / 50", BullMode.B50_50 to "50 / 50"), gs.bullMode) { onChange(gs.copy(bullMode = it)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
            StarterSettings(gs, onChange)
        }
        GameMode.CRICKET -> {
            OptionRow("Zahlen", listOf(CricketBoard.CRICKET to "Cricket (15–20)", CricketBoard.TACTICS to "Tactics (10–20)", CricketBoard.HIDDEN to "Hidden (7 zufällige)"), gs.effectiveCricketBoard) { onChange(gs.copy(cricketBoard = it, cricketVariant = gs.cricketScoring)) }
            OptionRow("Wertung", listOf(CricketVariant.STANDARD to "Standard", CricketVariant.CUT_THROAT to "Cut Throat", CricketVariant.NO_SCORE to "No Score"), gs.cricketScoring) { onChange(gs.copy(cricketVariant = it, cricketBoard = gs.effectiveCricketBoard)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
            StarterSettings(gs, onChange)
        }
        GameMode.AROUND_THE_CLOCK -> {
            OptionRow("Trefferart", listOf(HitMode.ANY to "Beliebig", HitMode.SINGLE to "Single", HitMode.DOUBLE to "Double", HitMode.TRIPLE to "Triple"), gs.hitMode) { onChange(gs.copy(hitMode = it)) }
            OrderRow(gs, onChange)
            SwitchRow("Bull am Ende", gs.includeBull) { onChange(gs.copy(includeBull = it)) }
            OptionRow("Treffer pro Zahl", listOf(1, 2, 3).map { it to it.toString() }, gs.hitsRequired) { onChange(gs.copy(hitsRequired = it)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
        }
        GameMode.ROUND_THE_WORLD -> {
            NumberRow("Bis Zahl", gs.rounds, 1..20) { onChange(gs.copy(rounds = it)) }
            OrderRow(gs, onChange)
            SwitchRow("Bull am Ende", gs.includeBull) { onChange(gs.copy(includeBull = it)) }
        }
        GameMode.COUNT_UP -> NumberRow("Runden", gs.rounds, 1..20) { onChange(gs.copy(rounds = it)) }
        GameMode.SHANGHAI -> NumberRow("Runden (Zahlen 1–n)", gs.rounds, 1..20) { onChange(gs.copy(rounds = it)) }
        GameMode.SEGMENT_TRAINING -> {
            OptionRow("Ziel", ((1..20).toList() + 25).map { it to (if (it == 25) "Bull" else it.toString()) }, gs.trainingSegment) { onChange(gs.copy(trainingSegment = it)) }
            OptionRow("Trefferart", listOf(HitMode.ANY to "Beliebig", HitMode.SINGLE to "Single", HitMode.DOUBLE to "Double", HitMode.TRIPLE to "Triple"), gs.hitMode) { onChange(gs.copy(hitMode = it)) }
            OptionRow("Ende nach", listOf(false to "Darts", true to "Treffern"), gs.endAfterHits) { onChange(gs.copy(endAfterHits = it, hitCount = if (it) 10 else 30)) }
            OptionRow(if (gs.endAfterHits) "Treffer" else "Darts", (if (gs.endAfterHits) listOf(1, 3, 5, 10, 15, 20) else listOf(9, 15, 30, 60, 99)).map { it to it.toString() }, gs.hitCount) { onChange(gs.copy(hitCount = it)) }
        }
        GameMode.RANDOM_CHECKOUT -> {
            NumberRow("Legs", gs.rounds, 1..30) { onChange(gs.copy(rounds = it)) }
            OptionRow("Aufnahmen pro Leg", listOf(1, 2, 3, 6, 9).map { it to it.toString() }, gs.checkoutRounds) { onChange(gs.copy(checkoutRounds = it)) }
            OptionRow("Out-Modus", listOf(OutMode.STRAIGHT to "Straight Out", OutMode.DOUBLE to "Double Out", OutMode.MASTER to "Master Out"), gs.outMode) { onChange(gs.copy(outMode = it)) }
            NumberRow("Min. Checkout", gs.checkoutMin, 2..170) { onChange(gs.copy(checkoutMin = it, checkoutMax = maxOf(it, gs.checkoutMax))) }
            NumberRow("Max. Checkout", gs.checkoutMax, 2..170) { onChange(gs.copy(checkoutMax = it, checkoutMin = minOf(it, gs.checkoutMin))) }
        }
        GameMode.ONE_TWENTY_ONE -> {
            NumberRow("Versuche", gs.attempts, 1..100) { onChange(gs.copy(attempts = it)) }
            OptionRow("Darts pro Versuch", listOf(9 to "9 (3 Aufnahmen)", 6 to "6 (2 Aufnahmen)"), gs.dartsPerAttempt) { onChange(gs.copy(dartsPerAttempt = it)) }
            OptionRow("Bei Fehlschlag", listOf(FailMode.SOFT to "Soft (−1)", FailMode.HARD_RESET to "Hard Reset (121)", FailMode.SAFEHOUSE to "Safehouse"), gs.failMode) { onChange(gs.copy(failMode = it)) }
            OptionRow("Schritt nach Erfolg", listOf(1, 3, 5).map { it to "+$it" }, gs.step) { onChange(gs.copy(step = it)) }
            if (gs.failMode == FailMode.SAFEHOUSE) OptionRow("Sichern nach jedem", listOf(1, 2, 3, 5).map { it to "$it. Erfolg" }, gs.safehouseEvery) { onChange(gs.copy(safehouseEvery = it)) }
        }
        GameMode.BOBS_27 -> {
            SwitchRow("Bull am Ende", gs.includeBull) { onChange(gs.copy(includeBull = it)) }
            SwitchRow("Negative Punkte erlauben", gs.allowNegative) { onChange(gs.copy(allowNegative = it)) }
        }
        GameMode.BERMUDA -> Text("Ziele: 12, 13, 14, Double, 15, 16, 17, Triple, 18, 19, 20, Bullseye. Ohne Treffer wird der Score halbiert.", color = DartColors.TextMuted)
        GameMode.GOTCHA -> {
            OptionRow("Ziel", listOf(101, 201, 301, 401, 501, 601, 701).map { it to it.toString() }, gs.gotchaTarget) { onChange(gs.copy(gotchaTarget = it)) }
            OptionRow("Out-Modus", listOf(OutMode.STRAIGHT to "Straight Out", OutMode.DOUBLE to "Double Out", OutMode.MASTER to "Master Out"), gs.outMode) { onChange(gs.copy(outMode = it)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
        }
        GameMode.KILLER -> {
            NumberRow("Leben", gs.killerLives, 1..9) { onChange(gs.copy(killerLives = it)) }
            OptionRow("Trefferart", listOf(HitMode.ANY to "Beliebig", HitMode.DOUBLE to "Nur Doubles", HitMode.TRIPLE to "Nur Triples"), gs.killerHitMode) { onChange(gs.copy(killerHitMode = it)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
        }
    }
}

@Composable
private fun OrderRow(gs: GameSettings, onChange: (GameSettings) -> Unit) {
    OptionRow("Reihenfolge", listOf(TargetOrder.UP to "1 → 20", TargetOrder.DOWN to "20 → 1", TargetOrder.RANDOM to "Zufällig"), gs.effectiveOrder) { onChange(gs.copy(targetOrder = it, randomOrder = false)) }
}
