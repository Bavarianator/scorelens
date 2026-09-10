package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.board.BoardManagerClient
import com.freedarts.scorer.engine.PlayerState
import com.freedarts.scorer.lens.DartDetector
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.InputMethod
import com.freedarts.scorer.model.MatchMode
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Banner
import com.freedarts.scorer.ui.components.Chalkboard
import com.freedarts.scorer.ui.components.CricketTable
import com.freedarts.scorer.ui.components.DartByDartPad
import com.freedarts.scorer.ui.components.Dartboard
import com.freedarts.scorer.ui.components.LensPreview
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.TotalScorePad
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun MatchScreen(vm: AppViewModel) {
    val state by vm.gameState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val inputMethod by vm.inputMethod.collectAsStateWithLifecycle()
    val connection by vm.board.connection.collectAsStateWithLifecycle()
    val boardState by vm.board.state.collectAsStateWithLifecycle()
    val lensStatus by vm.lens.status.collectAsStateWithLifecycle()
    val lensDetections by vm.lens.detections.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var confirmAbort by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(true) }
    var correctIndex by remember { mutableStateOf(-1) }
    val s = state ?: return
    val game = vm.game ?: return

    val isBotTurn = s.players[s.currentPlayer].player.isBot
    val inputEnabled = !s.finished && !isBotTurn
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val totalAllowed = game.settings.mode in setOf(GameMode.X01, GameMode.COUNT_UP, GameMode.GOTCHA)
    val highlight: Set<Segment> = if (settings.showCheckoutGuide && !s.finished) {
        s.checkoutHint?.split("  ")?.firstOrNull()?.let { Segment.parse(it) }?.let { setOf(it) } ?: emptySet()
    } else emptySet()
    val gs = game.settings
    val title = when (gs.mode) {
        GameMode.X01 -> if (gs.matchMode == MatchMode.SETS) "First to ${gs.sets} Sets" else "First to ${gs.legs} Leg" + (if (gs.legs > 1) "s" else "")
        else -> gs.mode.title
    }

    Column(Modifier.fillMaxSize()) {
        // Kopfzeile
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmAbort = true }) { Icon(Icons.Default.Close, "Spiel beenden") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(s.headline, color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (lensStatus.running) {
                val color = when (lensStatus.phase) {
                    DartDetector.Phase.TAKEOUT -> DartColors.Accent
                    DartDetector.Phase.MOTION -> DartColors.Primary
                    DartDetector.Phase.NO_REFERENCE -> DartColors.Red
                    else -> DartColors.Lime
                }
                StatusChip("Lens", color) { vm.navigate(Screen.Lens) }
            }
            if (settings.boardManagerEnabled) {
                val (label, color) = when {
                    connection != BoardManagerClient.Connection.CONNECTED -> "Board" to DartColors.Red
                    boardState.status == "Takeout" -> "Takeout" to DartColors.Accent
                    else -> "Board" to DartColors.Lime
                }
                StatusChip(label, color) { vm.navigate(Screen.Board) }
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Einstellungen") }
        }

        val content: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.padding(horizontal = 10.dp)) {
                // Spielerkarten
                if (s.players.size <= 2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.players.forEachIndexed { i, p -> ScoreCard(p, i == s.currentPlayer && !s.finished, s.showLegs, s.showSets, Modifier.weight(1f)) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.players.forEachIndexed { i, p -> ScoreCard(p, i == s.currentPlayer && !s.finished, s.showLegs, s.showSets, Modifier.widthIn(min = 150.dp), compact = true) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Dart-Slots der Aufnahme
                val correctable = if (s.currentVisit.isNotEmpty()) s.currentVisit else vm.correctableDarts()
                DartSlots(correctable, onTap = { i -> if (!s.finished && i < correctable.size) correctIndex = i })
                Banner(s.banner, Modifier.padding(top = 6.dp))
                s.cricketTargets?.let { Spacer(Modifier.height(6.dp)); CricketTable(s.players, it) }
                if (settings.showCheckoutGuide && s.checkoutHint != null && !s.finished) {
                    Text("Checkout: ${s.checkoutHint}", color = DartColors.Lime, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
                }
                if (settings.showChalkboard && s.cricketTargets == null) {
                    Spacer(Modifier.height(6.dp))
                    Chalkboard(s.players, rows = if (landscape) 3 else 4)
                }
            }
        }

        val input: @Composable (Modifier) -> Unit = { mod ->
            Box(mod, contentAlignment = Alignment.Center) {
                if (s.finished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                        Text(s.banner ?: "Spiel beendet", style = MaterialTheme.typography.displayMedium, color = DartColors.Lime, textAlign = TextAlign.Center)
                        s.winnerIndex?.let { Text("${s.players[it].player.name} gewinnt!", style = MaterialTheme.typography.headlineMedium) }
                        PrimaryButton("Finish", Modifier.fillMaxWidth()) { vm.finishToResult() }
                        OutlinedButton(onClick = { vm.undo() }) { Text("Letzten Dart zurücknehmen") }
                    }
                } else when (inputMethod) {
                    InputMethod.BOARD -> Column(Modifier.padding(6.dp)) {
                        if (lensStatus.running && showCamera) {
                            Box(Modifier.background(DartColors.Surface, RoundedCornerShape(14.dp)).border(1.dp, DartColors.Outline, RoundedCornerShape(14.dp)).padding(4.dp)) {
                                LensPreview(vm.lens, settings.lensCalibration, lensDetections, editable = false, status = lensStatus, cropToBoard = true)
                            }
                            Spacer(Modifier.height(6.dp))
                            Dartboard(Modifier.padding(horizontal = 90.dp).fillMaxWidth(), darts = s.currentVisit, highlight = highlight, enabled = inputEnabled) { vm.throwDart(it) }
                        } else {
                            Box(Modifier.background(DartColors.Surface, RoundedCornerShape(14.dp)).border(1.dp, DartColors.Outline, RoundedCornerShape(14.dp)).padding(8.dp)) {
                                Dartboard(Modifier.fillMaxWidth(), darts = s.currentVisit, highlight = highlight, enabled = inputEnabled) { vm.throwDart(it) }
                            }
                        }
                    }
                    InputMethod.TOTAL -> if (totalAllowed) TotalScorePad(inputEnabled && s.currentVisit.isEmpty()) { vm.enterVisitTotal(it) } else DartByDartPad(inputEnabled) { vm.throwDart(it) }
                    InputMethod.DART_BY_DART -> DartByDartPad(inputEnabled) { vm.throwDart(it) }
                }
                if (isBotTurn && !s.finished) {
                    Box(Modifier.background(DartColors.Background.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Text("🤖 ${s.players[s.currentPlayer].player.name} wirft …", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        if (landscape) {
            Row(Modifier.weight(1f)) {
                content(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                input(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                content(Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                input(Modifier.fillMaxWidth())
            }
        }

        // Untere Leiste
        Row(Modifier.fillMaxWidth().background(DartColors.Surface).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BarIcon(Icons.Default.TrackChanges, "Board", selected = inputMethod == InputMethod.BOARD) { vm.setInputMethod(InputMethod.BOARD) }
            BarIcon(Icons.Default.Dialpad, "Score", selected = inputMethod == InputMethod.TOTAL, enabled = totalAllowed) { vm.setInputMethod(InputMethod.TOTAL) }
            BarIcon(Icons.Default.Keyboard, "Darts", selected = inputMethod == InputMethod.DART_BY_DART) { vm.setInputMethod(InputMethod.DART_BY_DART) }
            if (lensStatus.running) BarIcon(Icons.Default.CameraAlt, "Cam", selected = showCamera) { showCamera = !showCamera }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { vm.undo() }, enabled = game.canUndo, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo"); Spacer(Modifier.width(4.dp)); Text("Undo")
            }
            Button(onClick = { vm.nextPlayer() }, enabled = inputEnabled, contentPadding = PaddingValues(horizontal = 14.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DartColors.Primary)) {
                Text("Next", fontWeight = FontWeight.Bold); Spacer(Modifier.width(2.dp)); Icon(Icons.Default.SkipNext, null)
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Match-Einstellungen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingSwitch("Caller (Sprachansage)", settings.callerEnabled) { v -> vm.updateSettings { it.copy(callerEnabled = v) } }
                    SettingSwitch("Jede Aufnahme ansagen", settings.callerCallsEveryVisit) { v -> vm.updateSettings { it.copy(callerCallsEveryVisit = v) } }
                    SettingSwitch("Soundeffekte", settings.soundEffects) { v -> vm.updateSettings { it.copy(soundEffects = v) } }
                    SettingSwitch("Chalkboard anzeigen", settings.showChalkboard) { v -> vm.updateSettings { it.copy(showChalkboard = v) } }
                    SettingSwitch("Checkout-Guide", settings.showCheckoutGuide) { v -> vm.updateSettings { it.copy(showCheckoutGuide = v) } }
                    SettingSwitch("Jeden Dart ansagen", settings.countEachThrow) { v -> vm.updateSettings { it.copy(countEachThrow = v) } }
                    if (settings.boardManagerEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text("Board Controls", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { vm.board.command("start") }) { Text("Start") }
                            TextButton(onClick = { vm.board.command("stop") }) { Text("Stop") }
                            TextButton(onClick = { vm.board.command("reset") }) { Text("Reset") }
                        }
                    }
                    if (lensStatus.running) {
                        Spacer(Modifier.height(8.dp))
                        Text("Lens", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { vm.lens.requestReference() }) { Text("Referenz neu") }
                            TextButton(onClick = { vm.navigate(Screen.Lens) }) { Text("Kalibrieren") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Schließen") } },
        )
    }
    if (correctIndex >= 0) {
        val darts = if (s.currentVisit.isNotEmpty()) s.currentVisit else vm.correctableDarts()
        val current = darts.getOrNull(correctIndex)
        AlertDialog(
            onDismissRequest = { correctIndex = -1 },
            title = { Text("Dart ${correctIndex + 1} korrigieren" + (current?.let { " (${it.name})" } ?: "")) },
            text = {
                Column {
                    Text("Tippe auf das richtige Segment, oder markiere den Dart als Bouncer.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Dartboard(Modifier.fillMaxWidth(), darts = current?.let { listOf(it) } ?: emptyList(), enabled = true) { seg ->
                        vm.correctDart(correctIndex, seg); correctIndex = -1
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.correctDart(correctIndex, Segment.MISS); correctIndex = -1 }) { Text("Bouncer / Miss", color = DartColors.Red) } },
            dismissButton = { TextButton(onClick = { correctIndex = -1 }) { Text("Abbrechen") } },
        )
    }
    if (confirmAbort) {
        AlertDialog(
            onDismissRequest = { confirmAbort = false },
            title = { Text("Spiel beenden?") },
            text = { Text("Das laufende Spiel wird verworfen und nicht in der Statistik gespeichert.") },
            confirmButton = { TextButton(onClick = { confirmAbort = false; vm.abortGame() }) { Text("Beenden", color = DartColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmAbort = false }) { Text("Weiterspielen") } },
        )
    }
}

/** Spielerkarte wie in der Autodarts-Match-Ansicht: farbige Kopfzeile, großer Score, Leg/Match-Average, Legs-Zähler. */
@Composable
fun ScoreCard(p: PlayerState, active: Boolean, showLegs: Boolean, showSets: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(
        modifier.background(DartColors.Surface, RoundedCornerShape(12.dp))
            .border(if (active) 2.dp else 1.dp, if (active) DartColors.Primary else DartColors.Outline, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (active) DartColors.Primary else DartColors.SurfaceHigh, RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(p.player, 20)
            Spacer(Modifier.width(6.dp))
            Text(p.player.name.uppercase(), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                color = if (p.isOut) DartColors.TextMuted else Color.White)
            if (p.isKiller) Text("🔪", fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.score, fontSize = if (compact) 34.sp else 48.sp, fontWeight = FontWeight.Black, lineHeight = if (compact) 36.sp else 50.sp,
                    color = if (p.isOut) DartColors.TextMuted else Color.White, maxLines = 1)
                Text(p.detail, style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showLegs || showSets) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showSets) LegCounter(p.sets, "S")
                    if (showLegs) LegCounter(p.legs, "L")
                }
            }
        }
    }
}

@Composable
private fun LegCounter(n: Int, unit: String) {
    Box(Modifier.padding(2.dp).background(DartColors.SurfaceHigh, RoundedCornerShape(6.dp)).border(1.dp, DartColors.Outline, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text("$n $unit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DartSlots(darts: List<Segment>, onTap: (Int) -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(DartColors.Surface, RoundedCornerShape(12.dp)).border(1.dp, DartColors.Outline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 3) {
            val d = darts.getOrNull(i)
            Row(Modifier.weight(1f).clickable(enabled = d != null) { onTap(i) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp).background(if (d != null) DartColors.Primary else DartColors.Outline, RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(6.dp))
                Text(d?.name ?: "—", fontWeight = FontWeight.Bold, color = if (d != null) Color.White else DartColors.TextMuted)
            }
        }
        Text(if (darts.isEmpty()) "0" else darts.sumOf { it.score }.toString(), style = MaterialTheme.typography.titleLarge, color = DartColors.Lime)
    }
}

@Composable
private fun StatusChip(label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.padding(end = 4.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.background(if (selected) DartColors.Primary.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (!enabled) DartColors.Outline else if (selected) DartColors.Primary else DartColors.TextMuted)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (!enabled) DartColors.Outline else if (selected) Color.White else DartColors.TextMuted)
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}
