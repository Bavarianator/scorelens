package com.freedarts.scorer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.board.BoardManagerClient
import com.freedarts.scorer.engine.PlayerState
import com.freedarts.scorer.lens.DartDetector
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.InputMethod
import com.freedarts.scorer.model.MatchMode
import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.online.RealtimeClient
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.Banner
import com.freedarts.scorer.ui.components.Chalkboard
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.CricketTable
import com.freedarts.scorer.ui.components.DartByDartPad
import com.freedarts.scorer.ui.components.Dartboard
import com.freedarts.scorer.ui.components.LensPreview
import com.freedarts.scorer.ui.components.LensSnapshot
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.TotalScorePad
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.draw.scale
import com.freedarts.scorer.ui.components.NameRibbon
import com.freedarts.scorer.ui.components.SegmentGrid

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
    var correctIndex by remember { mutableIntStateOf(-1) }
    val s = state ?: return
    val game = vm.game ?: return

    val isBotTurn = s.players[s.currentPlayer].player.isBot
    val online = vm.isOnlineGame
    val myTurn = vm.isMyTurn
    val onlineConnection by vm.online.connection.collectAsStateWithLifecycle()
    val presence by vm.online.presence.collectAsStateWithLifecycle()
    val snapshots by vm.snapshots.collectAsStateWithLifecycle()
    val inputEnabled = !s.finished && !isBotTurn && (!online || myTurn)
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val totalAllowed = game.settings.mode in setOf(GameMode.X01, GameMode.COUNT_UP, GameMode.GOTCHA)
    val highlight: Set<Segment> = if (settings.showCheckoutGuide && !s.finished) {
        s.checkoutHint?.split("  ")?.firstOrNull()?.let { Segment.parse(it) }?.let { setOf(it) } ?: emptySet()
    } else emptySet()
    val gs = game.settings
    val title = when (gs.mode) {
        GameMode.X01 -> matchTitle(gs)
        else -> gs.mode.title
    }
    val lensOn = lensStatus.running
    // Lens automatisch starten: einmal kalibriert + Kamera erlaubt → kein Umweg über den Lens-Tab
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        if (!lensOn && settings.lensAutoStart && settings.lensCalibration.size == 8 && !online &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) vm.startLens(owner)
    }

    // Caller-Einblendung: letzte Aufnahme groß anzeigen (2,5 s)
    val lastEntry = s.players.getOrNull((s.currentPlayer - 1 + s.players.size) % s.players.size)?.history?.lastOrNull()
    val historyCount = s.players.sumOf { it.history.size }
    var callerText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(historyCount) {
        val label = lastEntry?.label
        if (historyCount > 0 && label != null && !label.startsWith("—")) { callerText = label; delay(2500); callerText = null }
    }

    var intro by remember(game) { mutableStateOf(settings.animations && game.eventCount == 0) }
    LaunchedEffect(game) { if (intro) { delay(3200); intro = false } }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(DartColors.Background)) {
        // Kopfzeile
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmAbort = true }) { Icon(Icons.Default.Close, "Spiel beenden") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Chip("$title · ${s.headline}" + if (s.visitLocked) " · Darts entnehmen" else "") }
            if (vm.isSpectator) StatusPill("Zuschauer", if (onlineConnection == RealtimeClient.State.OPEN) DartColors.Green else DartColors.Accent) { }
            else if (online) {
                val opponentsAway = game.players.any { it.id != vm.online.myId && it.id !in presence }
                StatusPill(if (opponentsAway) "Gegner offline" else "Online", when (onlineConnection) {
                    RealtimeClient.State.OPEN -> if (opponentsAway) DartColors.Accent else DartColors.Green
                    RealtimeClient.State.OFF -> DartColors.Red
                    else -> DartColors.Accent
                }) { }
            }
            if (lensOn) StatusPill("Lens", when (lensStatus.phase) {
                DartDetector.Phase.TAKEOUT -> DartColors.Accent; DartDetector.Phase.MOTION -> DartColors.PrimaryLight
                DartDetector.Phase.NO_REFERENCE -> DartColors.Red; else -> DartColors.Green
            }) { vm.navigate(Screen.Lens) }
            if (settings.boardManagerEnabled) StatusPill("Board", if (connection == BoardManagerClient.Connection.CONNECTED) (if (boardState.status == "Takeout") DartColors.Accent else DartColors.Green) else DartColors.Red) { vm.navigate(Screen.Board) }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Einstellungen") }
        }

        val content: @Composable (Modifier) -> Unit = { mod ->
            Column(mod) {
                // Spielerkarten
                if (s.players.size <= 2) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        s.players.forEachIndexed { i, p -> ScoreCard(p, i == s.currentPlayer && !s.finished, s.showLegs, s.showSets, Modifier.weight(1f)) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        s.players.forEachIndexed { i, p -> ScoreCard(p, i == s.currentPlayer && !s.finished, s.showLegs, s.showSets, Modifier.widthIn(min = 170.dp), compact = true) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val correctable = if (s.currentVisit.isNotEmpty()) s.currentVisit else vm.correctableDarts()
                DartRow(correctable, current = s.currentVisit.isNotEmpty(), onTap = { i -> if (!s.finished && !online && i < correctable.size) correctIndex = i })
                if (online) Ticker(game.throwLog, s.players.map { it.player.name }, snapshots)
                Banner(s.banner, Modifier.padding(top = 6.dp))
                s.cricketTargets?.let { Spacer(Modifier.height(6.dp)); CricketTable(s.players, it, Modifier.padding(horizontal = 12.dp), hidden = s.cricketHidden ?: emptySet()) }
                if (settings.showChalkboard && s.cricketTargets == null && !lensOn) {
                    Spacer(Modifier.height(6.dp))
                    Chalkboard(s.players, Modifier.padding(horizontal = 12.dp), rows = if (landscape) 3 else 4)
                }
            }
        }

        val input: @Composable (Modifier) -> Unit = { mod ->
            Box(mod, contentAlignment = Alignment.Center) {
                if (s.finished) {
                    val winScale by animateFloatAsState(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "win")
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                        Text(s.banner ?: "Spiel beendet", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 52.sp, color = DartColors.Lime, textAlign = TextAlign.Center,
                            modifier = Modifier.scale(if (settings.animations) winScale else 1f))
                        s.winnerIndex?.let { Text("${s.players[it].player.name} gewinnt!", style = MaterialTheme.typography.headlineMedium) }
                        PrimaryButton(if (vm.isSpectator) "Zurück" else "Finish", Modifier.fillMaxWidth()) { vm.finishToResult() }
                        if (!online) OutlinedButton(onClick = { vm.undo() }) { Text("Letzten Dart zurücknehmen") }
                    }
                } else when (inputMethod) {
                    InputMethod.BOARD -> Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (lensOn && showCamera) {
                            Box(Modifier.clip(RoundedCornerShape(16.dp)).border(3.dp, if (lensStatus.setup == LensController.Setup.READY) DartColors.Green else DartColors.Outline, RoundedCornerShape(16.dp))) {
                                LensPreview(vm.lens, settings.lensCalibration, lensDetections, editable = false, status = lensStatus, cropToBoard = true)
                                LiveOverlays(lensStatus, s.currentVisit.size, s.checkoutHint.takeIf { settings.showCheckoutGuide }, callerText,
                                    zoom = if (settings.dartsZoom) s.currentVisit else null, animations = settings.animations)
                                TakeoutPanel(visible = lensStatus.phase == DartDetector.Phase.TAKEOUT || (settings.boardManagerEnabled && boardState.status == "Takeout")) { vm.lens.requestReference() }
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(DartColors.Surface).padding(8.dp)) {
                                Dartboard(Modifier.fillMaxWidth(), darts = s.currentVisit, highlight = highlight, enabled = inputEnabled) { vm.throwDart(it) }
                                LiveOverlays(if (lensOn) lensStatus else null, s.currentVisit.size, s.checkoutHint.takeIf { settings.showCheckoutGuide }, callerText, zoom = null, animations = settings.animations)
                                TakeoutPanel(visible = (lensOn && lensStatus.phase == DartDetector.Phase.TAKEOUT) || (settings.boardManagerEnabled && boardState.status == "Takeout")) { vm.lens.requestReference() }
                            }
                        }
                    }
                    InputMethod.TOTAL -> if (totalAllowed) TotalScorePad(inputEnabled && s.currentVisit.isEmpty()) { vm.enterVisitTotal(it) } else DartByDartPad(inputEnabled) { vm.throwDart(it) }
                    InputMethod.DART_BY_DART -> DartByDartPad(inputEnabled) { vm.throwDart(it) }
                }
                if ((isBotTurn || (online && !myTurn)) && !s.finished) {
                    Box(Modifier.background(DartColors.Background.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Text("${s.players[s.currentPlayer].player.name} wirft …", style = MaterialTheme.typography.titleLarge)
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
                input(Modifier.fillMaxWidth())
            }
        }

        // Untere Leiste wie bei Autodarts: Eingabe-Umschalter, Undo, großer Next-Button. Korrektur: Dart in der Aufnahme-Leiste antippen.
        Row(Modifier.fillMaxWidth().background(DartColors.BottomBar).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            var inputMenu by remember { mutableStateOf(false) }
            val cameraShown = inputMethod == InputMethod.BOARD && lensOn && showCamera
            Box {
                BarButton(when {
                    cameraShown -> Icons.Default.CameraAlt
                    inputMethod == InputMethod.BOARD -> Icons.Default.TrackChanges
                    inputMethod == InputMethod.TOTAL -> Icons.Default.Dialpad
                    else -> Icons.Default.Keyboard
                }, "Eingabe wechseln", active = false) { inputMenu = true }
                DropdownMenu(expanded = inputMenu, onDismissRequest = { inputMenu = false }) {
                    val pick: (InputMethod, Boolean) -> Unit = { m, cam -> inputMenu = false; showCamera = cam; vm.setInputMethod(m) }
                    if (lensOn) DropdownMenuItem(text = { Text("Kamera (Lens)") }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) }, onClick = { pick(InputMethod.BOARD, true) })
                    DropdownMenuItem(text = { Text("Board antippen") }, leadingIcon = { Icon(Icons.Default.TrackChanges, null) }, onClick = { pick(InputMethod.BOARD, false) })
                    if (totalAllowed) DropdownMenuItem(text = { Text("Gesamtscore") }, leadingIcon = { Icon(Icons.Default.Dialpad, null) }, onClick = { pick(InputMethod.TOTAL, false) })
                    DropdownMenuItem(text = { Text("Dart für Dart") }, leadingIcon = { Icon(Icons.Default.Keyboard, null) }, onClick = { pick(InputMethod.DART_BY_DART, false) })
                }
            }
            SecondaryButton("Undo", enabled = game.canUndo && vm.onlineCanUndo(), icon = Icons.AutoMirrored.Filled.Undo) { vm.undo() }
            PrimaryButton("Next", Modifier.weight(1f), enabled = inputEnabled, height = 48) { vm.nextPlayer() }
        }
    }

    // Match-Intro wie im Turnier: Spieler, Modus, dann los
    AnimatedVisibility(visible = intro, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xF00B1220)).clickable { intro = false }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("MATCH", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 52.sp, color = DartColors.Lime)
                s.players.forEachIndexed { i, p ->
                    if (i > 0) Text("VS", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = DartColors.TextMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) { Avatar(p.player, 36); Spacer(Modifier.width(10.dp)); NameRibbon(p.player.name, fontSize = 20) }
                }
                Chip(title + (if (gs.mode == GameMode.X01) " · ${gs.baseScore}" else ""))
                Text("Tippen zum Überspringen", fontSize = 11.sp, color = DartColors.TextMuted)
            }
        }
    }
    } // Box
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
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Checkout-Tabelle", Modifier.fillMaxWidth()) { showSettings = false; vm.navigate(Screen.CheckoutTable) }
                    if (lensOn) {
                        Spacer(Modifier.height(8.dp))
                        // Positionswechsel mitten im Match: Board neu suchen, Darts auf dem Board bleiben erhalten
                        SecondaryButton("Lens neu kalibrieren", Modifier.fillMaxWidth()) { vm.lens.startSearch(); showSettings = false }
                    }
                    if (settings.boardManagerEnabled) {
                        Spacer(Modifier.height(8.dp)); Text("Board Controls", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { vm.board.command("start") }) { Text("Start") }
                            TextButton(onClick = { vm.board.command("stop") }) { Text("Stop") }
                            TextButton(onClick = { vm.board.command("reset") }) { Text("Reset") }
                        }
                    }
                    if (lensOn) {
                        Spacer(Modifier.height(8.dp)); Text("Lens", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { vm.lens.requestReference() }) { Text("Referenz neu") }
                            TextButton(onClick = { vm.navigate(Screen.Lens) }) { Text("Detection Mode") }
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
                var useBoard by remember { mutableStateOf(false) }
                Column {
                    // Referee-Bild: Kamera-Ausschnitt der erkannten Spitze, wenn dieser Dart von Lens kam
                    lensDetections.getOrNull(correctIndex)
                        ?.takeIf { s.currentVisit.isNotEmpty() && lensDetections.size == s.currentVisit.size && it.snapshot != null }
                        ?.let { det -> LensSnapshot(det, Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quick Correction: ein Tap = neuer Dart.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Chip(if (useBoard) "Grid" else "Board", selected = false) { useBoard = !useBoard }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (useBoard) Dartboard(Modifier.fillMaxWidth(), darts = current?.let { listOf(it) } ?: emptyList(), enabled = true,
                        onTap = { seg, x, y -> vm.correctDart(correctIndex, seg, x, y); correctIndex = -1 }) { seg -> vm.correctDart(correctIndex, seg); correctIndex = -1 }
                    else SegmentGrid(enabled = true, compact = true) { seg -> vm.correctDart(correctIndex, seg); correctIndex = -1 }
                }
            },
            confirmButton = { TextButton(onClick = { vm.correctDart(correctIndex, Segment.MISS); correctIndex = -1 }) { Text("Bouncer / Miss", color = DartColors.Red) } },
            dismissButton = { TextButton(onClick = { correctIndex = -1 }) { Text("Abbrechen") } },
        )
    }
    if (confirmAbort) {
        AlertDialog(
            onDismissRequest = { confirmAbort = false },
            title = { Text(if (online) "Online-Match abbrechen?" else "Spiel beenden?") },
            text = { Text(if (online) "Das Match wird für alle Spieler abgebrochen und nicht gewertet." else "Das laufende Spiel wird verworfen und nicht in der Statistik gespeichert.") },
            confirmButton = { TextButton(onClick = { confirmAbort = false; vm.abortGame() }) { Text("Beenden", color = DartColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmAbort = false }) { Text("Weiterspielen") } },
        )
    }
}

/** Overlays im Live-Bild: Detecting-Pill, Dart-Zähler, Checkout, Caller. */
@Composable
private fun LiveOverlays(lens: LensController.Status?, dartsInVisit: Int, checkout: String?, caller: String?, zoom: List<Segment>?, animations: Boolean) {
    Box(Modifier.fillMaxSize()) {
        // Darts Zoom: aktuelle Aufnahme groß, von der Abwurflinie lesbar
        if (zoom != null && zoom.isNotEmpty()) Row(
            Modifier.align(Alignment.TopCenter).padding(top = 44.dp).background(Color(0xD90D1119), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            zoom.forEach { d -> Text(d.name, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 34.sp) }
            Text("= ${zoom.sumOf { it.score }}", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 34.sp, color = DartColors.Orange)
        }
        if (lens != null) {
            val ready = lens.setup == LensController.Setup.READY
            Row(Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xD90D1119), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (ready) DartColors.Green else DartColors.Accent, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(6.dp))
                Text(if (ready) "Detecting" else lens.message, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(10.dp).background(Color(0xD90D1119), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text("$dartsInVisit / 3 Darts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (checkout != null) Row(Modifier.align(Alignment.BottomCenter).padding(10.dp).background(Color(0xD90D1119), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Checkout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DartColors.TextMuted)
            Spacer(Modifier.width(8.dp))
            Text(checkout.replace("  ", " · "), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DartColors.Orange)
        }
        val callerScale by animateFloatAsState(if (caller != null && animations) 1f else 0.6f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "caller")
        if (caller != null) Column(Modifier.align(Alignment.Center).scale(if (animations) callerScale else 1f).background(Color(0xB80D1119), RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(caller, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = if (caller == "180") 56.sp else 44.sp, lineHeight = 56.sp,
                color = when { caller == "Bust" -> DartColors.Red; caller == "180" -> DartColors.Lime; else -> Color.White })
            Text("CALLER", fontSize = 11.sp, letterSpacing = 1.sp, color = DartColors.TextMuted)
        }
    }
}

/** Spielerkarte: aktiver Spieler im Magenta-Verlauf, großer Score, Sets/Legs-Kästchen, Leg-/Match-Average, Darts. */
@Composable
fun ScoreCard(p: PlayerState, active: Boolean, showLegs: Boolean, showSets: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val bg = if (active) Modifier.background(Brush.linearGradient(listOf(DartColors.Magenta, DartColors.MagentaMid, DartColors.Violet)), RoundedCornerShape(6.dp))
    else Modifier.background(DartColors.Surface, RoundedCornerShape(6.dp))
    val parts = p.detail.split("|")
    val avgText = parts[0]; val dartsText = parts.getOrNull(1)
    Column(modifier.then(bg).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)) {
        Row(Modifier.background(DartColors.SurfaceDeep, RoundedCornerShape(999.dp)).padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.player, 20)
            Spacer(Modifier.width(6.dp))
            Text(p.player.name.uppercase(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (p.isOut) DartColors.TextMuted else Color.White)
            if (p.isKiller) Text(" K", fontSize = 12.sp, color = DartColors.Accent)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(p.score, fontSize = if (compact) 46.sp else 62.sp, fontWeight = FontWeight.ExtraBold, lineHeight = if (compact) 48.sp else 64.sp, letterSpacing = (-2).sp,
                color = if (p.isOut) DartColors.TextMuted else Color.White, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (showLegs || showSets) {
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (showSets) LegBox(p.sets, light = true)
                    if (showLegs) LegBox(p.legs, light = !showSets)
                }
            }
        }
        Text(avgText, fontSize = 13.sp, color = if (active) Color.White else DartColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (dartsText != null) Text("Darts $dartsText", fontSize = 13.sp, color = if (active) Color.White else DartColors.TextMuted)
        if (active) { Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth().height(3.dp).background(DartColors.Green)) }
    }
}

/** Sets blau, Legs orange – Farbkonvention von Autodarts. */
@Composable
private fun LegBox(n: Int, light: Boolean) {
    Box(Modifier.background(if (light) DartColors.Primary else DartColors.Orange, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(n.toString(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (light) Color.White else Color(0xFF1A1206))
    }
}

/** Vollflächiges Takeout-Panel in Warnfarbe, solange Darts gezogen werden. */
@Composable
private fun TakeoutPanel(visible: Boolean, onReset: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xB3F59E5B)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TAKEOUT", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 48.sp, color = Color(0xFF1A1206))
                Text("Darts entfernen", fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1206))
                SecondaryButton("Reset", Modifier.width(120.dp)) { onReset() }
            }
        }
    }
}

/** Aufnahme-Leiste: drei Dart-Pills mit Segment, Zwischensumme rechts. */
@Composable
private fun DartRow(darts: List<Segment>, current: Boolean, onTap: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(DartColors.Surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 3) {
            val d = darts.getOrNull(i)
            Row(
                Modifier.background(if (d != null) DartColors.Primary else DartColors.SurfaceHigh, RoundedCornerShape(999.dp)).clip(RoundedCornerShape(999.dp))
                    .clickable(enabled = d != null) { onTap(i) }.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DartIcon(if (d != null) Color.White else Color(0xFF7B8496))
                Spacer(Modifier.width(6.dp))
                Text(d?.name ?: "—", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (d != null) Color.White else Color(0xFF7B8496))
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(darts.sumOf { it.score }.toString(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 24.sp, color = DartColors.Lime)
            Text(if (current) "Aufnahme" else "Letzte", fontSize = 10.sp, color = DartColors.TextMuted)
        }
    }
}

@Composable
private fun DartIcon(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.width(26.dp).height(12.dp)) {
        val h = size.height; val w = size.width
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.42f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.16f))
        val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.6f, h * 0.2f); lineTo(w * 0.8f, h * 0.5f); lineTo(w * 0.6f, h * 0.8f); close() }
        drawPath(p, color)
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.42f), size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.16f))
    }
}

@Composable
private fun StatusPill(label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.padding(end = 4.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(999.dp)).border(1.dp, color, RoundedCornerShape(999.dp))
        .clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp, 44.dp).background(if (active) DartColors.Primary else Color(0xFF1C2740), RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (!enabled) DartColors.Outline else Color.White) }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = if (enabled) Color.Unspecified else DartColors.TextMuted); Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Live-Ticker im Online-Match: die letzten Darts aller Spieler, neueste zuerst, mit Referee-Bild wo vorhanden (Tippen vergrößert). */
@Composable
private fun Ticker(log: List<com.freedarts.scorer.model.ThrowRecord>, names: List<String>, snapshots: Map<Long, ImageBitmap>) {
    if (log.isEmpty()) return
    var big by remember { mutableStateOf<ImageBitmap?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in log.indices.reversed().take(10)) {
            val t = log[i]; val img = snapshots[t.at]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(if (t.bust) DartColors.RedDark else DartColors.SurfaceHigh).clickable(enabled = img != null) { big = img }, contentAlignment = Alignment.Center) {
                    if (img != null) Image(img, t.segment.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Text(t.segment.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (img != null) DartColors.Lime else DartColors.Text)
                }
                Text(names.getOrNull(t.player)?.take(6) ?: "", fontSize = 10.sp, color = DartColors.TextMuted)
            }
        }
    }
    big?.let { img ->
        AlertDialog(onDismissRequest = { big = null }, confirmButton = { TextButton(onClick = { big = null }) { Text("OK") } },
            text = { Image(img, "Referee-Bild", Modifier.fillMaxWidth().height(260.dp), contentScale = ContentScale.Fit) })
    }
}
