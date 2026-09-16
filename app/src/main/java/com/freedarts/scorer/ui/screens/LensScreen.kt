package com.freedarts.scorer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.lens.BoardFinder
import com.freedarts.scorer.lens.BoardFinder.Quality
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.lens.LensMessages
import com.freedarts.scorer.lens.LensController.Setup
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.LensPreview
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.defaultCalibration
import com.freedarts.scorer.ui.theme.DartColors

/**
 * Lens (Kamera-Autoscoring) wie Autodarts: ein Modus, Hinweise direkt im Kamerabild, beim Positionieren eine
 * Checkliste (Board im Bild, Abstand, Winkel, Licht), sobald bereit die drei Darts der Aufnahme und ein Knopf ins Spiel.
 * Alles Technische (Empfindlichkeit, Punkte ziehen, Training, Remote) liegt unter „Erweitert“.
 */
@Composable
fun LensScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.lens.status.collectAsStateWithLifecycle()
    val detections by vm.lens.detections.collectAsStateWithLifecycle()
    val remoteUrl by vm.remoteUrl.collectAsStateWithLifecycle()
    val gameState by vm.gameState.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var manual by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var cropView by remember { mutableStateOf(true) }
    var calibration by remember { mutableStateOf(settings.lensCalibration.takeIf { it.size == 8 } ?: defaultCalibration()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) vm.startLens(owner)
    }
    // Kalibrierpunkte aus der Automatik übernehmen, solange nicht manuell gezogen wird
    if (!manual && settings.lensCalibration.size == 8 && settings.lensCalibration != calibration) calibration = settings.lensCalibration
    val ready = status.setup == Setup.READY

    Column(Modifier.fillMaxSize()) {
        AdTopBar("Lens", onBack = { vm.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!granted) {
                AdCard {
                    Text("Kamera-Zugriff", style = MaterialTheme.typography.titleMedium)
                    Text("Scorelens erkennt deine Darts mit der Handykamera – direkt auf dem Gerät, ohne Konto und ohne Limit.", color = DartColors.TextMuted)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Kamera-Zugriff erlauben", Modifier.fillMaxWidth(), icon = Icons.Default.CameraAlt) { launcher.launch(Manifest.permission.CAMERA) }
                }
                return@Column
            }
            if (!status.running) {
                AdCard {
                    Text("So geht's", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Step("1", "Handy aufs Stativ: etwa 1 m vom Bull, seitlich versetzt, Kamera auf Bull-Höhe. Empfohlen 35–55° schräg zur Scheibe, geht aber auch anders.")
                    Step("2", "Ganzes Board mit Zahlenring im Bild, gleichmäßiges Licht, keine wandernden Schatten.")
                    Step("3", "Kamera starten und das Board in den Kreis bringen – die App sagt dir, wohin du das Handy schieben sollst.")
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Kamera starten", Modifier.fillMaxWidth(), icon = Icons.Default.CameraAlt) { vm.startLens(owner) }
                }
                return@Column
            }

            // ---- Kamerabild mit Overlays: Status-Pill, Licht/Zuschnitt, Hinweis unten ----
            Box(Modifier.clip(RoundedCornerShape(16.dp))) {
                LensPreview(
                    vm.lens, calibration, detections, editable = manual, status = status,
                    cropToBoard = cropView && !manual && ready,
                    onTap = { nx, ny -> vm.lens.hintTop(nx, ny) },
                    onCalibrationChange = { calibration = it },
                )
                StatusPill(status, Modifier.align(Alignment.TopStart).padding(10.dp))
                Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    if (ready && !manual) OverlayIcon(Icons.Default.CropFree, "Zuschnitt", active = cropView) { cropView = !cropView }
                    OverlayIcon(if (status.torch) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, "Licht", active = status.torch) { vm.lens.setTorch(!status.torch) }
                }
                val hint = when {
                    manual -> "Ziehe die vier Punkte auf die Außenkante des Doppelrings an den Drähten 20/1, 6/10, 3/19 und 11/14."
                    ready -> null
                    else -> status.guidance.ifEmpty { status.message }
                }
                if (hint != null) Text(
                    hint, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xB3000000)).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            when {
                manual -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Zurücksetzen", Modifier.weight(1f)) { calibration = defaultCalibration() }
                    PrimaryButton("Fertig", Modifier.weight(1f), height = 48) { manual = false; vm.setLensCalibration(calibration) }
                }
                else -> {
                    // Kalibriert → sofort losspielen, noch bevor das Referenzbild steht (READY kommt im Match von selbst,
                    // sobald das Bild ruhig ist); Lobby nur, wenn man etwas ändern will
                    if (status.calibrated) {
                        if (vm.game != null) PrimaryButton("Zum Match", Modifier.fillMaxWidth()) { vm.navigate(Screen.Match) }
                        else {
                            val last = settings.lastGameSettings
                            PrimaryButton("Sofort spielen · ${last.mode.title}" + (if (last.mode == com.freedarts.scorer.model.GameMode.X01) " ${last.baseScore}" else ""), Modifier.fillMaxWidth()) { vm.playNow() }
                            Spacer(Modifier.height(6.dp))
                            SecondaryButton("Neues Spiel einrichten", Modifier.fillMaxWidth()) { vm.navigate(Screen.Lobby) }
                        }
                    }
                    if (ready) {
                        DartTiles(detections, gameState?.currentVisit?.size ?: detections.size)
                        Text("Verdeckt ein Dart einen anderen: vorderen ziehen oder Handy leicht drehen – der fehlende Dart wird nachgetragen.",
                            color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    } else Checklist(status)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Neu kalibrieren", Modifier.weight(1f), icon = Icons.Default.Refresh) { manual = false; vm.lens.startSearch() }
                SecondaryButton("Kamera stoppen", Modifier.weight(1f)) { vm.stopLens() }
            }
            Text("Nach Positionswechsel oder am nächsten Spieltag: „Neu kalibrieren“ sucht das Board frisch. Bei jedem Kamerastart passiert das automatisch, nur manuell gezogene Punkte bleiben stehen.",
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

            TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Erweitert ausblenden" else "Erweitert …") }
            if (showAdvanced) {
                AdCard {
                    SwitchRow("Punkte manuell ziehen", manual) { manual = it; if (!it) vm.setLensCalibration(calibration) }
                    SwitchRow("Empfindlichkeit automatisch", status.autoSensitivity) { vm.lens.setAutoSensitivity(it) }
                    if (!status.autoSensitivity) {
                        Text("Empfindlichkeit: ${settings.lensSensitivity}", color = DartColors.TextMuted)
                        Slider(value = settings.lensSensitivity.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(lensSensitivity = v.toInt()) }; vm.lens.setSensitivity(v.toInt()) }, valueRange = 0f..100f)
                    }
                    SwitchRow("Remote Scoring (Browser am zweiten Gerät)", remoteUrl != null, remoteUrl) { if (it) vm.startRemote() else vm.stopRemote() }
                    SwitchRow("Frontkamera", settings.lensUseFrontCamera) { on -> vm.updateSettings { it.copy(lensUseFrontCamera = on) }; vm.lens.setFrontCamera(on) }
                    if (status.exposureRange.first < status.exposureRange.last) {
                        Text("Belichtung: ${if (settings.lensExposure > 0) "+" else ""}${settings.lensExposure}", color = DartColors.TextMuted)
                        Slider(value = settings.lensExposure.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(lensExposure = v.toInt()) }; vm.lens.exposure = v.toInt() },
                            valueRange = status.exposureRange.first.toFloat()..status.exposureRange.last.toFloat(), steps = (status.exposureRange.last - status.exposureRange.first - 1).coerceAtLeast(0))
                    }
                    SwitchRow("Trainingsdaten sammeln", settings.lensCaptureTraining, "${vm.lens.training.count} Bilder · Android/data/…/files/training") { on ->
                        vm.updateSettings { it.copy(lensCaptureTraining = on) }; vm.lens.training.enabled = on
                    }
                    TextButton(onClick = { vm.lens.requestReference() }) { Text("Referenzbild neu aufnehmen") }
                    Text(
                        (if (status.ai) "KI ${status.aiBackend} ${status.aiInput}px" + (if (status.aiMs > 0) " · ${status.aiMs} ms" else "") else "Klassische Erkennung") +
                            " · ${status.fps} fps" + (status.calibResidualMm?.let { " · Kalibrierung ±%.1f mm".format(it) } ?: "") +
                            (status.ellipse?.let { " · Blick %.0f°".format(it.viewAngleDeg) } ?: "") +
                            (if (status.cameraSize.isNotEmpty()) " · ${status.cameraSize}" else "") +
                            " · Δ ${"%.1f".format(status.changeFraction * 100)} % · Phase ${status.phase}" + (if (status.remoteCamera) " · 2. Kamera" else ""),
                        color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusPill(status: LensController.Status, modifier: Modifier) {
    val (text, col) = when (status.setup) {
        Setup.READY -> status.message to DartColors.Lime
        Setup.FOUND -> "Kalibriert" to DartColors.Primary
        else -> "Suche Board" to DartColors.Accent
    }
    Row(
        modifier.background(Color(0xCC0B1220), RoundedCornerShape(20.dp)).border(1.dp, col, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (status.setup == Setup.READY) Icons.Default.CheckCircle else Icons.Default.Search, null, tint = col, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun OverlayIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(4.dp).background(Color(0xCC0B1220), RoundedCornerShape(20.dp))) {
        Icon(icon, label, tint = if (active) DartColors.Accent else Color.White)
    }
}

/** Positionierungs-Checkliste: welche Bedingung fehlt noch, damit das Board kalibriert werden kann. */
@Composable
private fun Checklist(status: LensController.Status) {
    val q = status.quality
    val found = q != null && q != Quality.NOT_FOUND
    val angle = status.ellipse?.viewAngleDeg
    AdCard {
        CheckRow("Ganzes Board im Bild", found && q != Quality.PARTIAL)
        CheckRow("Abstand passt (≈ 1 m)", found && q != Quality.TOO_SMALL && q != Quality.PARTIAL)
        CheckRow("Blickwinkel" + (angle?.let { " · jetzt %.0f°".format(it) } ?: "") + " · empfohlen 35–55°", found)
        AngleBar(angle)
        CheckRow("Licht passt" + (if (status.brightness in 1 until LensMessages.LIGHT_MIN) " · zu dunkel" else if (status.brightness > LensMessages.LIGHT_MAX) " · zu hell" else ""),
            status.brightness in LensMessages.LIGHT_MIN..LensMessages.LIGHT_MAX)
        CheckRow("Handy ruhig, Kalibrierung genau", q == Quality.GOOD || status.setup == Setup.FOUND)
        Spacer(Modifier.height(6.dp))
        Text("Liegt das Gitter falsch herum, tippe im Bild auf die 20.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

/** Spektrum des Blickwinkels 0–90°: grün = empfohlener Bereich (aus IDEAL_MIN/IDEAL_MAX), Marker = aktueller Winkel. */
@Composable
private fun AngleBar(angle: Double?) {
    val lo = Math.toDegrees(kotlin.math.asin(BoardFinder.IDEAL_MIN)).toFloat(); val hi = Math.toDegrees(kotlin.math.asin(BoardFinder.IDEAL_MAX)).toFloat()
    Canvas(Modifier.fillMaxWidth().padding(start = 34.dp, top = 2.dp, bottom = 6.dp).height(10.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(DartColors.Accent.copy(alpha = 0.35f), size = size, cornerRadius = CornerRadius(h / 2))
        drawRoundRect(DartColors.Lime, topLeft = Offset(w * lo / 90f, 0f), size = Size(w * (hi - lo) / 90f, h), cornerRadius = CornerRadius(h / 2))
        angle?.let { drawCircle(DartColors.Text, radius = h * 0.8f, center = Offset(w * it.toFloat().coerceIn(0f, 90f) / 90f, h / 2)) }
    }
}

@Composable
private fun CheckRow(text: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (ok) DartColors.Lime else DartColors.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (ok) DartColors.Text else DartColors.TextMuted)
    }
}

/** Die drei Darts der aktuellen Aufnahme. */
@Composable
private fun DartTiles(detections: List<LensController.Detection>, visitCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val d = detections.getOrNull(i)
            val filled = i < visitCount || d != null
            Row(
                Modifier.weight(1f).background(if (filled) DartColors.PrimaryDark else DartColors.Surface, RoundedCornerShape(10.dp))
                    .border(1.dp, if (filled) DartColors.Primary else DartColors.Outline, RoundedCornerShape(10.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text("🎯", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(d?.segment?.name ?: if (filled) "✓" else "—", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text)
            if (sub != null) Text(sub, color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(n, color = DartColors.Primary, fontWeight = FontWeight.Black, modifier = Modifier.width(20.dp))
        Text(text, color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
