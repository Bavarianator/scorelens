@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.BullMode
import com.freedarts.scorer.model.CricketVariant
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.HitMode
import com.freedarts.scorer.model.InMode
import com.freedarts.scorer.model.MatchMode
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.LevelBadge
import com.freedarts.scorer.ui.components.ModeBadge
import com.freedarts.scorer.ui.components.PrimaryButton
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
    var showBots by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHowTo by remember { mutableStateOf(false) }

    fun avgOf(p: Player): Double {
        if (p.isBot) return Player.botAverage(p.botLevel).toDouble()
        val st = matches.filter { it.mode == GameMode.X01 }.mapNotNull { m -> m.players.firstOrNull { it.playerId == p.id } }
        val d = st.sumOf { it.dartsThrown }
        return if (d == 0) 0.0 else st.sumOf { it.pointsScored }.toDouble() / d * 3
    }

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
                        Avatar(p, 34); Spacer(Modifier.width(10.dp))
                        Text(p.name.uppercase(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        LevelBadge(avgOf(p))
                        IconButton(onClick = { if (p.isBot) vm.removeBot() else vm.toggleLobbyPlayer(p) }) { Icon(Icons.Default.Close, "Entfernen", tint = DartColors.TextMuted) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Add Player", Modifier.weight(1f), icon = Icons.Default.Add, enabled = lobby.size < 6) { showAddPlayer = true }
                    PrimaryButton("Add Bot", Modifier.weight(1f), icon = Icons.Default.SmartToy, enabled = lobby.size < 6 || lobby.any { it.isBot }) { showBots = true }
                }
            }

            // GAME MODE
            AdCard(onClick = { vm.navigate(Screen.ModeSelect) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (gs.mode == GameMode.X01) gs.baseScore.toString() else gs.mode.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ModeBadge(gs.mode)
                }
                Text(gs.mode.description(), color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                Text("ⓘ How to play", color = DartColors.TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp).clickable { showHowTo = true })
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
                Box(Modifier.fillMaxWidth().background(DartColors.SurfaceHigh, RoundedCornerShape(10.dp)).clickable { vm.navigate(if (settings.boardManagerEnabled) Screen.Board else Screen.Lens) }.padding(10.dp)) {
                    Column {
                        Text(if (settings.boardManagerEnabled) "Autodarts Board Manager" else "Lens (Handykamera)", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                lensStatus.running -> lensStatus.message
                                settings.boardManagerEnabled -> "${settings.boardManagerHost}:${settings.boardManagerPort}"
                                else -> "Nicht aktiv – manuelle Eingabe"
                            }, color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(70.dp))
        }
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            PrimaryButton("Start Game", Modifier.fillMaxWidth().height(52.dp), enabled = lobby.isNotEmpty()) { vm.startGame() }
        }
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
                }
            },
            confirmButton = { TextButton(onClick = { showAddPlayer = false }) { Text("Fertig") } },
        )
    }
    if (showBots) {
        AlertDialog(
            onDismissRequest = { showBots = false },
            title = { Text("Bot hinzufügen") },
            text = {
                Column {
                    Text("Elf Stufen – der Bot wirft mit realistischer Streuung.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..11).forEach { lvl ->
                            FilterChip(selected = lobby.any { it.botLevel == lvl }, onClick = { vm.addBot(lvl); showBots = false },
                                label = { Text("Stufe $lvl · Ø ${Player.botAverage(lvl)}") })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBots = false }) { Text("Schließen") } },
            dismissButton = { if (lobby.any { it.isBot }) TextButton(onClick = { vm.removeBot(); showBots = false }) { Text("Bot entfernen") } },
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("${gs.mode.title} – Einstellungen") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ModeSettings(gs) { vm.setLobbySettings(it) } } },
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

fun settingsChips(gs: GameSettings): List<String> = when (gs.mode) {
    GameMode.X01 -> listOf(
        gs.baseScore.toString(),
        if (gs.matchMode == MatchMode.SETS) "First to ${gs.sets} Sets" else "First to ${gs.legs} Leg" + if (gs.legs > 1) "s" else "",
        when (gs.inMode) { InMode.STRAIGHT -> "Straight In"; InMode.DOUBLE -> "Double In"; InMode.MASTER -> "Master In" },
        when (gs.outMode) { OutMode.STRAIGHT -> "Straight Out"; OutMode.DOUBLE -> "Double Out"; OutMode.MASTER -> "Master Out" },
        if (gs.bullMode == BullMode.B25_50) "Bull 25/50" else "Bull 50/50",
    ) + (if (gs.maxRounds > 0) listOf("Max ${gs.maxRounds} Runden") else emptyList())
    GameMode.CRICKET -> listOf(when (gs.cricketVariant) { CricketVariant.STANDARD -> "Standard"; CricketVariant.CUT_THROAT -> "Cut Throat"; CricketVariant.TACTICS -> "Tactics" }) + (if (gs.maxRounds > 0) listOf("Max ${gs.maxRounds} Runden") else emptyList())
    GameMode.AROUND_THE_CLOCK -> listOf(when (gs.hitMode) { HitMode.ANY -> "Beliebig"; HitMode.SINGLE -> "Single"; HitMode.DOUBLE -> "Double"; HitMode.TRIPLE -> "Triple" }, if (gs.includeBull) "Mit Bull" else "Ohne Bull", if (gs.randomOrder) "Zufällig" else "1–20")
    GameMode.COUNT_UP, GameMode.SHANGHAI, GameMode.SEGMENT_TRAINING -> listOf("${gs.rounds} Runden") + (if (gs.mode == GameMode.SEGMENT_TRAINING) listOf("Ziel " + (if (gs.trainingSegment == 25) "Bull" else gs.trainingSegment.toString())) else emptyList())
    GameMode.ROUND_THE_WORLD -> listOf("1–${gs.rounds}", if (gs.includeBull) "Mit Bull" else "Ohne Bull")
    GameMode.RANDOM_CHECKOUT -> listOf("${gs.rounds} Runden", "${gs.checkoutMin}–${gs.checkoutMax}")
    GameMode.ONE_TWENTY_ONE -> listOf("${gs.attempts} Versuche", "9 Darts", "Double Out")
    GameMode.BOBS_27 -> listOf("D1–D20 + Bull")
    GameMode.BERMUDA -> listOf("12 Runden")
    GameMode.GOTCHA -> listOf("Ziel ${gs.gotchaTarget}")
    GameMode.KILLER -> listOf("${gs.killerLives} Leben")
}

fun howToPlay(mode: GameMode): String = when (mode) {
    GameMode.X01 -> "Jeder Spieler startet mit dem Startwert (z.B. 501). Die geworfenen Punkte werden abgezogen. Wer zuerst exakt auf 0 kommt, gewinnt das Leg. Bei Double Out muss der letzte Dart ein Double (oder Bullseye) sein. Wer unter 0 (oder bei Double Out auf 1) fällt, hat einen Bust – die Aufnahme zählt nicht."
    GameMode.CRICKET -> "Ziele sind 15–20 und Bull. Single = 1 Treffer, Double = 2, Triple = 3. Nach drei Treffern ist die Zahl für dich geöffnet; weitere Treffer bringen Punkte, solange ein Gegner die Zahl noch nicht geschlossen hat. Wer alle Zahlen geschlossen hat und die meisten Punkte besitzt, gewinnt. Cut Throat: Punkte gehen an die Gegner, wenigste Punkte gewinnen."
    GameMode.AROUND_THE_CLOCK -> "Triff die Zahlen 1 bis 20 (optional Bull) in aufsteigender Reihenfolge. Jeder Treffer bringt dich zur nächsten Zahl. Wer zuerst fertig ist, gewinnt."
    GameMode.ROUND_THE_WORLD -> "In Runde n ist die Zahl n das Ziel. Jeder Treffer zählt seinen Wert (Single, Double, Triple). Höchste Punktzahl nach der letzten Runde gewinnt."
    GameMode.COUNT_UP -> "Acht Runden, alle Punkte zählen. Die höchste Gesamtpunktzahl gewinnt."
    GameMode.RANDOM_CHECKOUT -> "Pro Runde wird ein zufälliger Rest vorgegeben. Du hast drei Darts, um ihn mit Double auszuchecken. Anzahl der Checkouts entscheidet."
    GameMode.BOBS_27 -> "Start bei 27 Punkten. Pro Runde drei Darts auf das Double der Reihe nach (D1 bis D20, dann Bull). Jeder Treffer bringt 2×Zahl, kein Treffer in der Runde kostet 2×Zahl. Unter 0 scheidet man aus."
    GameMode.SEGMENT_TRAINING -> "Wähle ein Segment und wirf eine feste Anzahl Runden darauf. Single = 1, Double = 2, Triple = 3 Punkte."
    GameMode.ONE_TWENTY_ONE -> "Checke 121 mit maximal neun Darts (Double Out) aus. Gelingt es, steigt das Ziel um 1, sonst sinkt es um 1. Nach allen Versuchen gewinnt das höchste Ziel."
    GameMode.SHANGHAI -> "Runde 1 zielt auf die 1, Runde 2 auf die 2 usw. Nur Treffer auf die aktuelle Zahl zählen. Single, Double und Triple in einer Aufnahme = Shanghai und sofortiger Sieg."
    GameMode.GOTCHA -> "Alle starten bei 0 und zählen bis exakt zum Ziel hoch. Überwerfen = Bust. Landest du genau auf dem Score eines Gegners, fällt er auf 0 zurück."
    GameMode.BERMUDA -> "Zwölf Runden mit festen Zielen (12, 13, 14, Double, 15, 16, 17, Triple, 18, 19, 20, Bull). Treffer zählen Punkte; keine Treffer in einer Runde halbieren den Score."
    GameMode.KILLER -> "Jeder bekommt eine Zahl. Triff dein eigenes Double, um Killer zu werden. Als Killer nimmst du Gegnern mit deren Double ein Leben ab. Der letzte Spieler mit Leben gewinnt."
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

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ModeSettings(gs: GameSettings, onChange: (GameSettings) -> Unit) {
    when (gs.mode) {
        GameMode.X01 -> {
            OptionRow("Startwert", listOf(121, 170, 301, 501, 701, 901, 1001).map { it to it.toString() }, gs.baseScore) { onChange(gs.copy(baseScore = it)) }
            OptionRow("Match-Modus", listOf(MatchMode.LEGS to "Legs", MatchMode.SETS to "Sets"), gs.matchMode) { onChange(gs.copy(matchMode = it)) }
            NumberRow(if (gs.matchMode == MatchMode.SETS) "Legs pro Set (First to)" else "Legs (First to)", gs.legs, 1..21) { onChange(gs.copy(legs = it)) }
            if (gs.matchMode == MatchMode.SETS) NumberRow("Sets (First to)", gs.sets, 1..13) { onChange(gs.copy(sets = it)) }
            OptionRow("In-Modus", listOf(InMode.STRAIGHT to "Straight In", InMode.DOUBLE to "Double In", InMode.MASTER to "Master In"), gs.inMode) { onChange(gs.copy(inMode = it)) }
            OptionRow("Out-Modus", listOf(OutMode.STRAIGHT to "Straight Out", OutMode.DOUBLE to "Double Out", OutMode.MASTER to "Master Out"), gs.outMode) { onChange(gs.copy(outMode = it)) }
            OptionRow("Bull-Modus", listOf(BullMode.B25_50 to "25 / 50", BullMode.B50_50 to "50 / 50"), gs.bullMode) { onChange(gs.copy(bullMode = it)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
        }
        GameMode.CRICKET -> {
            OptionRow("Variante", listOf(CricketVariant.STANDARD to "Standard", CricketVariant.CUT_THROAT to "Cut Throat", CricketVariant.TACTICS to "Tactics (10–20)"), gs.cricketVariant) { onChange(gs.copy(cricketVariant = it)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
        }
        GameMode.AROUND_THE_CLOCK -> {
            OptionRow("Trefferart", listOf(HitMode.ANY to "Beliebig", HitMode.SINGLE to "Single", HitMode.DOUBLE to "Double", HitMode.TRIPLE to "Triple"), gs.hitMode) { onChange(gs.copy(hitMode = it)) }
            SwitchRow("Bull am Ende", gs.includeBull) { onChange(gs.copy(includeBull = it)) }
            SwitchRow("Zufällige Reihenfolge", gs.randomOrder) { onChange(gs.copy(randomOrder = it)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
        }
        GameMode.ROUND_THE_WORLD -> {
            NumberRow("Bis Zahl", gs.rounds, 1..20) { onChange(gs.copy(rounds = it)) }
            SwitchRow("Bull am Ende", gs.includeBull) { onChange(gs.copy(includeBull = it)) }
        }
        GameMode.COUNT_UP -> NumberRow("Runden", gs.rounds, 1..20) { onChange(gs.copy(rounds = it)) }
        GameMode.SHANGHAI -> NumberRow("Runden (Zahlen 1–n)", gs.rounds, 1..20) { onChange(gs.copy(rounds = it)) }
        GameMode.SEGMENT_TRAINING -> {
            NumberRow("Runden", gs.rounds, 1..30) { onChange(gs.copy(rounds = it)) }
            OptionRow("Ziel", ((1..20).toList() + 25).map { it to (if (it == 25) "Bull" else it.toString()) }, gs.trainingSegment) { onChange(gs.copy(trainingSegment = it)) }
        }
        GameMode.RANDOM_CHECKOUT -> {
            NumberRow("Runden", gs.rounds, 1..30) { onChange(gs.copy(rounds = it)) }
            NumberRow("Min. Checkout", gs.checkoutMin, 2..170) { onChange(gs.copy(checkoutMin = it, checkoutMax = maxOf(it, gs.checkoutMax))) }
            NumberRow("Max. Checkout", gs.checkoutMax, 2..170) { onChange(gs.copy(checkoutMax = it, checkoutMin = minOf(it, gs.checkoutMin))) }
        }
        GameMode.ONE_TWENTY_ONE -> NumberRow("Versuche", gs.attempts, 1..30) { onChange(gs.copy(attempts = it)) }
        GameMode.BOBS_27 -> Text("Start 27 Punkte, Doubles 1–20 und Bull. Treffer +2×Zahl, Fehlrunde −2×Zahl.", color = DartColors.TextMuted)
        GameMode.BERMUDA -> Text("Ziele: 12, 13, 14, Double, 15, 16, 17, Triple, 18, 19, 20, Bull. Ohne Treffer wird der Score halbiert.", color = DartColors.TextMuted)
        GameMode.GOTCHA -> {
            OptionRow("Ziel", listOf(101, 201, 301, 501).map { it to it.toString() }, gs.gotchaTarget) { onChange(gs.copy(gotchaTarget = it)) }
            NumberRow("Max. Runden (0 = ∞)", gs.maxRounds, 0..50) { onChange(gs.copy(maxRounds = it)) }
        }
        GameMode.KILLER -> NumberRow("Leben", gs.killerLives, 1..9) { onChange(gs.copy(killerLives = it)) }
    }
}
