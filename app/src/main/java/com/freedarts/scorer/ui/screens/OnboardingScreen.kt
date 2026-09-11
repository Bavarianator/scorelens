@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.online.OnlineController
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.LensPreview
import com.freedarts.scorer.ui.components.NameRibbon
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.defaultCalibration
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/** Erster Start in vier Schritten: Profil → Lens (Kamera live) → Online-Konto → fertig; Lens und Konto sind überspringbar. */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val players by vm.players.collectAsStateWithLifecycle()
    val status by vm.lens.status.collectAsStateWithLifecycle()
    val session by vm.online.session.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(players.firstOrNull()?.name?.takeIf { it != "Spieler 1" } ?: "") }
    var color by remember { mutableStateOf(players.firstOrNull()?.color ?: Player.AVATAR_COLORS[0]) }
    val lensReady = status.setup == LensController.Setup.READY
    BackHandler(enabled = step > 0) { step-- }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        if (step == 0) HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 220)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(if (step == 0) 40.dp else 24.dp))
            BrandTitle("Scorelens", size = 30)
            Spacer(Modifier.height(6.dp))
            Text(
                when (step) { 0 -> "JUST PLAY"; 1 -> "LENS"; 2 -> "ONLINE"; else -> "READY" },
                fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp,
            )
            Text(
                when (step) {
                    0 -> "Autoscoring mit der Handykamera, alle Spielmodi, keine Abos. Lokal auf deinem Gerät."
                    1 -> "Handy aufs Stativ, Board wird automatisch erkannt. Du kannst das auch später unter Devices machen."
                    2 -> "Mit Konto spielst du online gegen andere und deine Statistik bleibt erhalten. Optional."
                    else -> "Alles eingerichtet. Spieler und Bots hinzufügen, Modus wählen, Start Game."
                },
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i -> Box(Modifier.weight(1f).height(4.dp).background(if (i <= step) DartColors.Primary else DartColors.Outline, CircleShape)) }
            }
            Spacer(Modifier.height(16.dp))

            when (step) {
                0 -> {
                    AdCard {
                        Text("1 · Dein Profil", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Player.AVATAR_COLORS.forEach { c ->
                                Box(Modifier.size(34.dp).background(Color(c), CircleShape).border(3.dp, if (c == color) Color.White else Color.Transparent, CircleShape).clickable { color = c })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(Color(color), CircleShape), contentAlignment = Alignment.Center) { Text(name.trim().take(2).uppercase().ifEmpty { "S1" }, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(10.dp))
                            NameRibbon(name.trim().ifEmpty { "Spieler 1" }, "NEU", DartColors.SurfaceHigh, Color.White)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Weiter", Modifier.fillMaxWidth(), height = 56) { step = 1 }
                }
                1 -> {
                    LensSetupStep(vm, status)
                    Spacer(Modifier.height(20.dp))
                    if (lensReady) PrimaryButton("Weiter", Modifier.fillMaxWidth(), height = 56, icon = Icons.Default.CheckCircle) { step = 2 }
                    else TextButton(onClick = { step = 2 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Später einrichten – ohne Lens weiter", color = DartColors.TextMuted) }
                }
                2 -> {
                    val error by vm.online.error.collectAsStateWithLifecycle()
                    val busy by vm.online.busy.collectAsStateWithLifecycle()
                    val user = session?.user
                    AdCard(background = if (user != null) DartColors.GreenDark else DartColors.Surface) {
                        if (user != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = DartColors.Lime)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Angemeldet", fontWeight = FontWeight.Bold)
                                    Text(user.email ?: if (user.isAnonymous) "als Gast" else "über ${user.provider}", color = Color(0xFFDDE6F5), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text("3 · Online-Konto", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Anmeldung im Browser; danach geht es hier automatisch weiter.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OnlineController.PROVIDERS.forEach { (id, label) -> PrimaryButton(label, Modifier.weight(1f), enabled = !busy) { vm.online.beginOAuth(id) } }
                            }
                            Spacer(Modifier.height(8.dp))
                            SecondaryButton("Als Gast spielen", Modifier.fillMaxWidth(), enabled = !busy) { vm.online.signInAsGuest(name.trim().ifEmpty { "Gast" }) }
                            if (error != null) Text(error!!, color = DartColors.Accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    if (user != null) PrimaryButton("Weiter", Modifier.fillMaxWidth(), height = 56, icon = Icons.Default.CheckCircle) { step = 3 }
                    else TextButton(onClick = { step = 3 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ohne Konto weiter – nur lokal spielen", color = DartColors.TextMuted) }
                }
                else -> {
                    AdCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(Color(color), CircleShape), contentAlignment = Alignment.Center) { Text(name.trim().take(2).uppercase().ifEmpty { "S1" }, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(10.dp))
                            NameRibbon(name.trim().ifEmpty { "Spieler 1" }, "NEU", DartColors.SurfaceHigh, Color.White)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AdCard(background = if (lensReady) DartColors.GreenDark else DartColors.Surface) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (lensReady) Icons.Default.CheckCircle else Icons.Default.CameraAlt, null, tint = if (lensReady) DartColors.Lime else DartColors.TextMuted)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(if (lensReady) "Lens bereit" else "Lens noch nicht eingerichtet", fontWeight = FontWeight.Bold)
                                Text(
                                    if (lensReady) "Board kalibriert" + (status.calibResidualMm?.let { " · ±%.1f mm".format(it) } ?: "") + " – Autoscoring in der Lobby einschalten."
                                    else "Jederzeit unter Avatar → Devices nachholen; bis dahin Punkte antippen.",
                                    color = if (lensReady) Color(0xFFDDE6F5) else DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AdCard(background = if (session != null) DartColors.GreenDark else DartColors.Surface) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (session != null) Icons.Default.CheckCircle else Icons.Default.Public, null, tint = if (session != null) DartColors.Lime else DartColors.TextMuted)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(if (session != null) "Online-Konto verbunden" else "Kein Online-Konto", fontWeight = FontWeight.Bold)
                                Text(
                                    if (session != null) session?.user?.email ?: "Online spielen ist freigeschaltet." else "Jederzeit unter Startseite → Online spielen anmelden.",
                                    color = if (session != null) Color(0xFFDDE6F5) else DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Los geht's", Modifier.fillMaxWidth(), height = 56) { vm.finishOnboarding(name, color) }
                }
            }
            if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Zurück", color = DartColors.TextMuted) }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** Schritt 2: Kamera-Berechtigung → Kamera starten → Live-Bild mit Positionierungs-Hinweisen bis „Ready“. */
@Composable
private fun LensSetupStep(vm: AppViewModel, status: LensController.Status) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val detections by vm.lens.detections.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; if (ok) vm.startLens(owner) }
    val ready = status.setup == LensController.Setup.READY

    if (!status.running) {
        AdCard {
            Text("2 · Kamera aufstellen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Step("1", "Stativ, etwa 1 m vom Bull, seitlich versetzt: Blick ≈ 45° schräg auf die Scheibe, Kamera auf Bull-Höhe.")
            Step("2", "Ganzes Board mit Zahlenring im Bild, gleichmäßiges Licht, Board leer lassen.")
            Step("3", "Kamera starten – die App sagt dir, wohin du das Handy schieben sollst, und meldet „Ready“.")
            Spacer(Modifier.height(10.dp))
            if (granted) PrimaryButton("Kamera starten", Modifier.fillMaxWidth(), icon = Icons.Default.CameraAlt) { vm.startLens(owner) }
            else PrimaryButton("Kamera-Zugriff erlauben", Modifier.fillMaxWidth(), icon = Icons.Default.CameraAlt) { launcher.launch(Manifest.permission.CAMERA) }
        }
        return
    }
    LensPreview(
        vm.lens, settings.lensCalibration.takeIf { it.size == 8 } ?: defaultCalibration(), detections,
        editable = false, status = status, onTap = { nx, ny -> vm.lens.hintTop(nx, ny) },
    )
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().background(if (ready) DartColors.GreenDark else DartColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, if (ready) DartColors.Lime else DartColors.Outline, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Search, null, tint = if (ready) DartColors.Lime else DartColors.Accent)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (ready) "Ready to play ✓" else status.guidance.ifEmpty { status.message }, fontWeight = FontWeight.Bold)
            Text(
                if (ready) "Board erkannt" + (status.calibResidualMm?.let { " · Kalibrierung ±%.1f mm".format(it) } ?: "") + (status.ellipse?.let { " · Blick %.0f°".format(it.viewAngleDeg) } ?: "")
                else "Liegt das Gitter falsch herum, tippe im Bild auf die 20.",
                color = if (ready) Color(0xFFDDE6F5) else DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Neu erkennen", Modifier.weight(1f)) { vm.lens.startSearch() }
        SecondaryButton(if (status.torch) "Licht aus" else "Licht", Modifier.weight(1f)) { vm.lens.setTorch(!status.torch) }
    }
}
