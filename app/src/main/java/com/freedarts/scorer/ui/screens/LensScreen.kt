package com.freedarts.scorer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.LensPreview
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.defaultCalibration
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun LensScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.lens.status.collectAsStateWithLifecycle()
    val detections by vm.lens.detections.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var manual by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var cropView by remember { mutableStateOf(true) }
    var showTools by remember { mutableStateOf(false) }
    val remoteUrl by vm.remoteUrl.collectAsStateWithLifecycle()
    val cloudUrl by vm.cloudUrl.collectAsStateWithLifecycle()
    val gameState by vm.gameState.collectAsStateWithLifecycle()
    var calibration by remember { mutableStateOf(settings.lensCalibration.takeIf { it.size == 8 } ?: defaultCalibration()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) vm.startLens(owner)
    }
    // Kalibrierpunkte aus der Automatik übernehmen, solange nicht manuell gezogen wird
    if (!manual && settings.lensCalibration.size == 8 && settings.lensCalibration != calibration) calibration = settings.lensCalibration

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
                    Step("1", "Handy auf ein Stativ, etwa 1 m vom Bull entfernt und deutlich seitlich versetzt (links oder rechts, egal): Blick etwa 45° schräg auf die Scheibe – wie bei Autodarts 35–55°. Kamera ungefähr auf Bull-Höhe.")
                    Step("2", "Nicht frontal (der Dart verdeckt sonst seine Spitze) und nicht so flach, dass das Board zur schmalen Ellipse wird. Die App sagt, in welche Richtung du das Handy verschieben sollst.")
                    Step("3", "Das ganze Board inklusive Zahlenring muss im Bild sein. Gleichmäßiges Licht (Lichtring), keine wandernden Schatten, Vorhänge zu.")
                    Step("4", "Kamera starten – das Board wird automatisch erkannt und kalibriert. Board leer lassen, bis „Ready to play“ erscheint.")
                    if (vm.lens.aiAvailable) Step("✓", "KI-Erkennung (dart-sense, YOLOv8n) ist geladen: Darts und Kalibrierpunkte werden per neuronalem Netz erkannt.")
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Kamera starten", Modifier.fillMaxWidth(), icon = Icons.Default.CameraAlt) { vm.startLens(owner) }
                }
                return@Column
            }

            // ---- Detection Mode (wie Autodarts): nur Kamera, Status-Pill, drei Dart-Symbole, Tipps ----
            if (!showTools && !manual) {
                DetectionMode(
                    vm, status, detections, calibration, cropView, remoteUrl, cloudUrl,
                    visitCount = gameState?.currentVisit?.size ?: detections.size,
                    onTools = { showTools = true },
                )
                return@Column
            }
            LensPreview(
                vm.lens, calibration, detections, editable = manual, status = status,
                cropToBoard = cropView && !manual && status.setup == LensController.Setup.READY,
                onTap = { nx, ny -> vm.lens.hintTop(nx, ny) },
                onCalibrationChange = { calibration = it },
            )
            TextButton(onClick = { showTools = false; manual = false; vm.setLensCalibration(calibration) }) { Text("← Zurück zum Detection Mode") }
            if (status.setup == LensController.Setup.READY && !manual) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Live-Bild auf die Scheibe zuschneiden", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
                    Switch(checked = cropView, onCheckedChange = { cropView = it })
                }
            }

            // Statuskarte
            val ready = status.setup == LensController.Setup.READY
            val cardColor = when (status.setup) {
                LensController.Setup.READY -> DartColors.GreenDark
                LensController.Setup.FOUND -> DartColors.PrimaryDark
                else -> DartColors.Surface
            }
            Row(
                Modifier.fillMaxWidth().background(cardColor, RoundedCornerShape(14.dp)).border(1.dp, DartColors.Outline, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Search, null, tint = if (ready) DartColors.Lime else DartColors.Accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(status.guidance.ifEmpty { status.message }, fontWeight = FontWeight.Bold)
                    Text(
                        (if (ready) status.message + " · ${status.fps} fps" else "Status: ${status.message}") +
                            (if (status.ai) " · KI ${status.aiBackend} ${status.aiInput}px" + (if (status.aiMs > 0) " ${status.aiMs} ms" else "") else " · klassisch") +
                            (status.calibResidualMm?.let { " · Kalibrierung ±%.1f mm".format(it) } ?: "") +
                            (status.ellipse?.let { " · Blick %.0f°".format(it.viewAngleDeg) } ?: "") +
                            (if (status.cameraSize.isNotEmpty()) " · ${status.cameraSize}" else ""),
                        color = Color(0xFFDDE6F5), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (ready) {
                Text("Zuletzt erkannt: " + (detections.joinToString("  ") { it.segment.name }.ifEmpty { "–" }), color = DartColors.TextMuted)
                if (vm.game != null) PrimaryButton("Zurück zum Match", Modifier.fillMaxWidth()) { vm.navigate(Screen.Match) }
                else PrimaryButton("Spiel starten", Modifier.fillMaxWidth()) { vm.navigate(Screen.Lobby) }
            } else if (status.setup == LensController.Setup.SEARCHING) {
                Text("Tipp: Liegt das Gitter falsch herum, tippe im Bild auf die 20.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Neu erkennen", Modifier.weight(1f), icon = Icons.Default.Refresh) { manual = false; vm.lens.startSearch() }
                SecondaryButton(if (status.torch) "Licht aus" else "Licht", Modifier.weight(1f), icon = Icons.Default.FlashlightOn) { vm.lens.setTorch(!status.torch) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Referenz neu", Modifier.weight(1f)) { vm.lens.requestReference() }
                SecondaryButton("Kamera stoppen", Modifier.weight(1f)) { vm.stopLens() }
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Erweitert ausblenden" else "Erweitert …") }
            if (showAdvanced) {
                AdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Punkte manuell ziehen", Modifier.weight(1f))
                        Switch(checked = manual, onCheckedChange = { on ->
                            manual = on
                            if (!on) vm.setLensCalibration(calibration)
                        })
                    }
                    if (manual) {
                        Text("Ziehe die vier Punkte auf die Außenkante des Doppelrings an den Drähten 20/1, 6/10, 3/19 und 11/14. Danach Schalter aus.",
                            color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { calibration = defaultCalibration() }) { Text("Punkte zurücksetzen") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Empfindlichkeit automatisch", Modifier.weight(1f))
                        Switch(checked = status.autoSensitivity, onCheckedChange = { vm.lens.setAutoSensitivity(it) })
                    }
                    if (!status.autoSensitivity) {
                        Text("Empfindlichkeit: ${settings.lensSensitivity}", color = DartColors.TextMuted)
                        Slider(value = settings.lensSensitivity.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(lensSensitivity = v.toInt()) }; vm.lens.setSensitivity(v.toInt()) }, valueRange = 0f..100f)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Trainingsdaten sammeln")
                            Text("${vm.lens.training.count} Bilder · Android/data/…/files/training", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = settings.lensCaptureTraining, onCheckedChange = { on -> vm.updateSettings { it.copy(lensCaptureTraining = on) }; vm.lens.training.enabled = on })
                    }
                    Text("Δ ${"%.1f".format(status.changeFraction * 100)} % · ${status.fps} fps · Phase ${status.phase}", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetectionMode(
    vm: AppViewModel,
    status: LensController.Status,
    detections: List<LensController.Detection>,
    calibration: List<Float>,
    crop: Boolean,
    remoteUrl: String?,
    cloudUrl: String?,
    visitCount: Int,
    onTools: () -> Unit,
) {
    val ready = status.setup == LensController.Setup.READY
    Box {
        LensPreview(vm.lens, calibration, detections, editable = false, status = status,
            cropToBoard = crop && ready, onTap = { nx, ny -> vm.lens.hintTop(nx, ny) })
        // Status-Pill oben
        Row(
            Modifier.padding(12.dp).background(if (ready) DartColors.GreenDark else DartColors.SurfaceHigh, RoundedCornerShape(20.dp))
                .border(1.dp, if (ready) DartColors.Lime else DartColors.Accent, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Search, null, tint = if (ready) DartColors.Lime else DartColors.Accent, modifier = Modifier.width(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (ready) "Detecting" else "Position your device", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
    // Meldung (Positionierung / Bereit)
    Text(if (ready) "Ready to play ✓" else status.guidance.ifEmpty { status.message }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
        color = if (ready) DartColors.Green else Color.White)
    // Drei Dart-Symbole der aktuellen Aufnahme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val d = detections.getOrNull(i)
            val filled = i < visitCount || d != null
            Row(
                Modifier.weight(1f).background(if (filled) DartColors.PrimaryDark else DartColors.Surface, RoundedCornerShape(10.dp))
                    .border(1.dp, if (filled) DartColors.Primary else DartColors.Outline, RoundedCornerShape(10.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎯", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(d?.segment?.name ?: if (filled) "✓" else "—", fontWeight = FontWeight.Bold)
            }
        }
    }
    Text(
        (if (status.ai) "KI ${status.aiBackend} ${status.aiInput}px" + (if (status.aiMs > 0) " · ${status.aiMs} ms" else "") else "Klassische Erkennung") +
            " · ${status.fps} fps" + (status.calibResidualMm?.let { " · Kalibrierung ±%.1f mm".format(it) } ?: "") +
            (status.ellipse?.let { " · Blick %.0f°".format(it.viewAngleDeg) } ?: "") +
            (if (status.cameraSize.isNotEmpty()) " · ${status.cameraSize}" else ""),
        color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
    )
    if (remoteUrl != null) {
        AdCard(padding = 10) {
            Text("Remote Scoring aktiv", fontWeight = FontWeight.Bold)
            Text("Spielansicht im Browser eines zweiten Geräts: $remoteUrl", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (cloudUrl != null) {
        AdCard(padding = 10) {
            Text("Online-Remote aktiv", fontWeight = FontWeight.Bold)
            Text("Von überall im Browser: $cloudUrl", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(
        when {
            !ready -> "Tipp: Stativ, etwa 1 m vom Bull, seitlich versetzt (Blick ≈ 45° zur Scheibe), ganzes Board im Bild, gleichmäßiges Licht. Liegt das Gitter falsch, auf die 20 tippen."
            else -> "Tipp: Verdeckt ein Dart einen anderen, den vorderen ziehen oder das Handy leicht drehen – der fehlende Dart wird nachgetragen. App offen lassen."
        },
        color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.game != null) PrimaryButton("Zum Match", Modifier.weight(1f)) { vm.navigate(Screen.Match) }
        else PrimaryButton("Spiel starten", Modifier.weight(1f)) { vm.navigate(Screen.Lobby) }
        SecondaryButton("Done", Modifier.weight(1f)) { vm.back() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Neu erkennen", Modifier.weight(1f), icon = Icons.Default.Refresh) { vm.lens.startSearch() }
        SecondaryButton(if (status.torch) "Licht aus" else "Licht", Modifier.weight(1f), icon = Icons.Default.FlashlightOn) { vm.lens.setTorch(!status.torch) }
        SecondaryButton("Werkzeuge", Modifier.weight(1f)) { onTools() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(if (remoteUrl != null) "Remote aus" else "Remote Scoring", Modifier.weight(1f)) { if (remoteUrl != null) vm.stopRemote() else vm.startRemote() }
        SecondaryButton("Kamera stoppen", Modifier.weight(1f)) { vm.stopLens() }
    }
}

@Composable
internal fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(n, color = DartColors.Primary, fontWeight = FontWeight.Black, modifier = Modifier.width(20.dp))
        Text(text, color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
