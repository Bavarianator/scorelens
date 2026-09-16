package com.freedarts.scorer.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.board.BoardManagerClient
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.QrCode
import com.freedarts.scorer.ui.components.QrScannerDialog
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.theme.DartColors

/** "Devices" wie bei Autodarts: Avatar → Devices → Start Lens Detection Mode. */
@Composable
fun DevicesScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lens by vm.lens.status.collectAsStateWithLifecycle()
    val connection by vm.board.connection.collectAsStateWithLifecycle()
    val remoteUrl by vm.remoteUrl.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        Column(Modifier.fillMaxSize()) {
            AdTopBar("Geräte", onBack = null)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Meine Geräte")

                DeviceCard(
                    icon = Icons.Default.CameraAlt, title = "Lens", subtitle = "Kamera-Erkennung mit diesem Handy",
                    status = when {
                        lens.setup == LensController.Setup.READY -> "Detecting" to DartColors.Green
                        lens.running -> lens.message to DartColors.Accent
                        else -> "Aus" to DartColors.TextMuted
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(if (lens.running) "Kamera-Erkennung öffnen" else "Kamera-Erkennung starten", Modifier.weight(1f), height = 48) { vm.navigate(Screen.Lens) }
                        if (lens.running) SecondaryButton("Stopp", Modifier.weight(0.45f)) { vm.stopLens() }
                    }
                }

                DeviceCard(
                    icon = Icons.Default.Videocam, title = "Board Manager", subtitle = "Autodarts-Hardware im WLAN (Port 3180)",
                    status = when (connection) {
                        BoardManagerClient.Connection.CONNECTED -> "Verbunden" to DartColors.Green
                        BoardManagerClient.Connection.CONNECTING -> "Verbinde …" to DartColors.Accent
                        BoardManagerClient.Connection.ERROR -> "Nicht erreichbar" to DartColors.Red
                        else -> "Nicht verbunden" to DartColors.TextMuted
                    },
                ) {
                    SecondaryButton(if (settings.boardManagerEnabled) "Verbindung verwalten" else "Board Manager verbinden", Modifier.fillMaxWidth()) { vm.navigate(Screen.Board) }
                }

                var scan by remember { mutableStateOf(false) }
                var showMyCode by remember { mutableStateOf(false) }
                DeviceCard(
                    icon = Icons.Default.Language, title = "Anzeige auf TV oder Tablet", subtitle = "Remote Scoring: Spielansicht im Browser",
                    status = if (remoteUrl != null) "Aktiv" to DartColors.Green else "Aus" to DartColors.TextMuted,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(remoteUrl ?: "Handy bleibt als Kamera am Board, Scores auf Tablet, PC oder TV.", color = if (remoteUrl != null) DartColors.PrimaryLight else DartColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = remoteUrl != null, onCheckedChange = { on -> if (on) vm.startRemote() else vm.stopRemote() })
                    }
                    remoteUrl?.takeIf { it.startsWith("http://") && !it.contains("<") }?.let { u ->
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { QrCode(u, size = 160.dp) }
                        Text("Am Zweitgerät: Scorelens → Devices → „Als Zweitgerät koppeln“ und den Code scannen.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        Text("Stream/TV: $u/overlay als OBS-Browserquelle (transparent) oder im Fernseher-Browser; ?pos=top setzt den Streifen nach oben.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Zweitgerät scannen", Modifier.fillMaxWidth(), icon = Icons.Default.QrCodeScanner) { scan = true }
                        Text("Zeigt das andere Handy seinen Code („Meinen Code zeigen“), scannst du ihn hier – die Kopplung geht in beide Richtungen.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Dieses Gerät als Zweitgerät: Remote-Seite eines anderen Board-Handys in der App
                var manual by remember { mutableStateOf(false) }
                DeviceCard(
                    icon = Icons.Default.Language, title = "Zweites Handy koppeln", subtitle = "Spiel eines anderen Board-Handys hier anzeigen und bedienen",
                    status = if (settings.remotePairedUrl.isNotBlank()) "Gekoppelt" to DartColors.Green else "Nicht gekoppelt" to DartColors.TextMuted,
                ) {
                    if (settings.remotePairedUrl.isNotBlank()) {
                        val u = android.net.Uri.parse(settings.remotePairedUrl)
                        val asBoard = settings.boardManagerEnabled && settings.boardManagerHost == u.host && connection == BoardManagerClient.Connection.CONNECTED
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(settings.remotePairedUrl.removePrefix("http://"), Modifier.weight(1f), height = 48) { vm.navigate(Screen.RemoteView) }
                            SecondaryButton("Trennen", Modifier.weight(0.45f)) { vm.updateSettings { it.copy(remotePairedUrl = "") }; if (asBoard) vm.disconnectBoard() }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (lens.running) "Zwei Kameras: Läuft hier ebenfalls Lens, werden die Spitzen beider Handys zusammengeführt – schneller bestätigt, verdeckte Darts gesehen. Gezählt wird hier."
                            else "Volle App: Das ganze Spiel läuft hier (alle Modi, Statistik, Online), das Board-Handy ist nur noch Kamera und liefert die Würfe wie ein Board Manager.",
                            color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        if (asBoard) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.freedarts.scorer.ui.components.Badge(if (lens.running) "Zwei Kameras aktiv" else "Volle App aktiv", DartColors.OnTint, DartColors.GreenDark, Modifier.weight(1f))
                            SecondaryButton("Würfe nicht mehr übernehmen", Modifier.weight(1.4f)) { vm.disconnectBoard() }
                        }
                        else PrimaryButton("Volle App: Würfe vom Board-Handy übernehmen", Modifier.fillMaxWidth(), height = 48) {
                            vm.connectBoard(u.host ?: "", if (u.port > 0) u.port else com.freedarts.scorer.remote.RemoteServer.PORT)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton("QR-Code scannen", Modifier.weight(1f), height = 48) { scan = true }
                            SecondaryButton("Adresse", Modifier.weight(0.5f)) { manual = true }
                        }
                        Spacer(Modifier.height(8.dp))
                        // Umgekehrt: dieses Handy zeigt seinen Code, das Board-Handy scannt ihn
                        SecondaryButton(if (showMyCode) "Code ausblenden" else "Meinen Code zeigen", Modifier.fillMaxWidth(), icon = Icons.Default.QrCode) {
                            showMyCode = !showMyCode; if (showMyCode && remoteUrl == null) vm.startRemote()
                        }
                        if (showMyCode) remoteUrl?.takeIf { it.startsWith("http://") && !it.contains("<") }?.let { u ->
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { QrCode("$u/pair", size = 160.dp) }
                            Text("Am Board-Handy: Devices → Remote Scoring → „Zweitgerät scannen“. Danach zeigt dieses Handy das Spiel.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        } else if (showMyCode) Text("Keine WLAN-Adresse gefunden – bist du im selben WLAN?", color = DartColors.Red, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (scan) QrScannerDialog(onDismiss = { scan = false }, hint = "Gerätecode scannen") { text -> scan = false; vm.handleDeviceCode(text) }
                if (manual) {
                    var host by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { manual = false },
                        title = { Text("Board-Handy koppeln") },
                        text = { Column {
                            Text("IP-Adresse aus der Remote-Karte des Board-Handys (gleiches WLAN).", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("IP oder Adresse") }, placeholder = { Text("192.168.1.10") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        } },
                        confirmButton = { TextButton(enabled = host.isNotBlank(), onClick = {
                            val h = host.trim().removePrefix("http://").removeSuffix("/")
                            vm.updateSettings { it.copy(remotePairedUrl = "http://" + (if (h.contains(":")) h else "$h:${com.freedarts.scorer.remote.RemoteServer.PORT}")) }
                            manual = false; vm.navigate(Screen.RemoteView)
                        }) { Text("Verbinden") } },
                        dismissButton = { TextButton(onClick = { manual = false }) { Text("Abbrechen") } },
                    )
                }

                Text("Wie bei Autodarts: Ein Gerät bleibt im Detection Mode am Board, das Spiel läuft auf demselben oder einem zweiten Gerät.",
                    color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DeviceCard(icon: ImageVector, title: String, subtitle: String, status: Pair<String, Color>, content: @Composable () -> Unit) {
    AdCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(DartColors.SurfaceHigh, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = DartColors.Text) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.border(1.dp, status.second, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(status.second, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(status.first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = status.second)
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}
