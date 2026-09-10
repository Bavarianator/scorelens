package com.freedarts.scorer.ui.screens

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.InputMethod
import com.freedarts.scorer.model.MatchMode
import com.freedarts.scorer.model.Segment
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
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.TotalScorePad
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors
import kotlinx.coroutines.delay

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
    val lensOn = lensStatus.running

    // Caller-Einblendung: letzte Aufnahme groß anzeigen (2,5 s)
    val lastEntry = s.players.getOrNull((s.currentPlayer - 1 + s.players.size) % s.players.size)?.history?.lastOrNull()
    val historyCount = s.players.sumOf { it.history.size }
    var callerText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(historyCount) {
        val label = lastEntry?.label
        if (historyCount > 0 && label != null && !label.startsWith("—")) { callerText = label; delay(2500); callerText = null }
    }

    Column(Modifier.fillMaxSize().background(DartColors.Background)) {
        // Kopfzeile
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { confirmAbort = true }) { Icon(Icons.Default.Close, "Spiel beenden") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Chip("$title · ${s.headline}") }
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
                DartRow(correctable, current = s.currentVisit.isNotEmpty(), onTap = { i -> if (!s.finished && i < correctable.size) correctIndex = i })
                Banner(s.banner, Modifier.padding(top = 6.dp))
                s.cricketTargets?.let { Spacer(Modifier.height(6.dp)); CricketTable(s.players, it, Modifier.padding(horizontal = 12.dp)) }
                if (settings.showChalkboard && s.cricketTargets == null && !lensOn) {
                    Spacer(Modifier.height(6.dp))
                    Chalkboard(s.players, Modifier.padding(horizontal = 12.dp), rows = if (landscape) 3 else 4)
                }
            }
        }

        val input: @Composable (Modifier) -> Unit = { mod ->
            Box(mod, contentAlignment = Alignment.Center) {
                if (s.finished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                        Text(s.banner ?: "Spiel beendet", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = DartColors.Lime, textAlign = TextAlign.Center)
                        s.winnerIndex?.let { Text("${s.players[it].player.name} gewinnt!", style = MaterialTheme.typography.headlineMedium) }
                        PrimaryButton("Finish", Modifier.fillMaxWidth()) { vm.finishToResult() }
                        OutlinedButton(onClick = { vm.undo() }) { Text("Letzten Dart zurücknehmen") }
                    }
                } else when (inputMethod) {
                    InputMethod.BOARD -> Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (lensOn && showCamera) {
                            Box(Modifier.clip(RoundedCornerShape(16.dp)).border(3.dp, if (lensStatus.setup == LensController.Setup.READY) DartColors.Green else DartColors.Outline, RoundedCornerShape(16.dp))) {
                                LensPreview(vm.lens, settings.lensCalibration, lensDetections, editable = false, status = lensStatus, cropToBoard = true)
                                LiveOverlays(lensStatus, s.currentVisit.size, s.checkoutHint.takeIf { settings.showCheckoutGuide }, callerText)
                            }
                        } else {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(DartColors.Surface).padding(8.dp)) {
                                Dartboard(Modifier.fillMaxWidth(), darts = s.currentVisit, highlight = highlight, enabled = inputEnabled) { vm.throwDart(it) }
                                LiveOverlays(if (lensOn) lensStatus else null, s.currentVisit.size, s.checkoutHint.takeIf { settings.showCheckoutGuide }, callerText)
                            }
                        }
                    }
                    InputMethod.TOTAL -> if (totalAllowed) TotalScorePad(inputEnabled && s.currentVisit.isEmpty()) { vm.enterVisitTotal(it) } else DartByDartPad(inputEnabled) { vm.throwDart(it) }
                    InputMethod.DART_BY_DART -> DartByDartPad(inputEnabled) { vm.throwDart(it) }
                }
                if (isBotTurn && !s.finished) {
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

        // Untere Leiste
        Row(Modifier.fillMaxWidth().background(DartColors.BottomBar).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lensOn) BarButton(Icons.Default.CameraAlt, "Kamera", active = inputMethod == InputMethod.BOARD && showCamera) { vm.setInputMethod(InputMethod.BOARD); showCamera = true }
            BarButton(Icons.Default.TrackChanges, "Board", active = inputMethod == InputMethod.BOARD && (!lensOn || !showCamera)) { vm.setInputMethod(InputMethod.BOARD); showCamera = false }
            BarButton(Icons.Default.Dialpad, "Score", active = inputMethod == InputMethod.TOTAL, enabled = totalAllowed) { vm.setInputMethod(InputMethod.TOTAL) }
            BarButton(Icons.Default.Keyboard, "Darts", active = inputMethod == InputMethod.DART_BY_DART) { vm.setInputMethod(InputMethod.DART_BY_DART) }
            Spacer(Modifier.weight(1f))
            SecondaryButton("Undo", enabled = game.canUndo, icon = Icons.AutoMirrored.Filled.Undo) { vm.undo() }
            PrimaryButton("Next", Modifier.width(92.dp), enabled = inputEnabled, height = 48) { vm.nextPlayer() }
            BarButton(Icons.Default.Edit, "Korrektur", active = false, enabled = !s.finished && vm.correctableDarts().isNotEmpty()) {
                correctIndex = (if (s.currentVisit.isNotEmpty()) s.currentVisit.size else vm.correctableDarts().size) - 1
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
                Column {
                    Text("Tippe auf das richtige Segment, oder markiere den Dart als Bouncer.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Dartboard(Modifier.fillMaxWidth(), darts = current?.let { listOf(it) } ?: emptyList(), enabled = true) { seg -> vm.correctDart(correctIndex, seg); correctIndex = -1 }
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

/** Overlays im Live-Bild: Detecting-Pill, Dart-Zähler, Checkout, Caller. */
@Composable
private fun LiveOverlays(lens: LensController.Status?, dartsInVisit: Int, checkout: String?, caller: String?) {
    Box(Modifier.fillMaxSize()) {
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
            Text(checkout.replace("  ", " · "), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DartColors.Lime)
        }
        if (caller != null) Column(Modifier.align(Alignment.Center).background(Color(0xB80D1119), RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(caller, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp, color = if (caller == "Bust") DartColors.Red else Color.White)
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

@Composable
private fun LegBox(n: Int, light: Boolean) {
    Box(Modifier.background(if (light) Color.White else Color(0xFF111111), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(n.toString(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (light) Color(0xFF111111) else Color.White)
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
fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}
