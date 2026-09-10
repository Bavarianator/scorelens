@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.Chip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.freedarts.scorer.ui.theme.DartColors
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.freedarts.scorer.remote.CloudRelayClient
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.QrCode
import com.freedarts.scorer.ui.components.SecondaryButton

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { com.freedarts.scorer.ui.components.ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Einstellungen", onBack = { vm.back() })
        val players by vm.players.collectAsStateWithLifecycle()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionLabel("Profil (Dashboard)")
            Text("Spieler, dessen Statistiken auf der Startseite erscheinen.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                players.forEach { p -> Chip(p.name, selected = (s.profilePlayerId ?: players.firstOrNull()?.id) == p.id) { vm.updateSettings { it.copy(profilePlayerId = p.id) } } }
            }
            SectionLabel("Caller & Sound")
            SettingSwitch("Caller (Sprachansage)", s.callerEnabled) { v -> vm.updateSettings { it.copy(callerEnabled = v) } }
            SettingSwitch("Jede Aufnahme ansagen", s.callerCallsEveryVisit) { v -> vm.updateSettings { it.copy(callerCallsEveryVisit = v) } }
            SettingSwitch("Jeden Dart ansagen", s.countEachThrow) { v -> vm.updateSettings { it.copy(countEachThrow = v) } }
            SettingSwitch("Soundeffekte", s.soundEffects) { v -> vm.updateSettings { it.copy(soundEffects = v) } }

            SectionLabel("Match-Anzeige")
            SettingSwitch("Chalkboard anzeigen", s.showChalkboard) { v -> vm.updateSettings { it.copy(showChalkboard = v) } }
            SettingSwitch("Checkout-Guide", s.showCheckoutGuide) { v -> vm.updateSettings { it.copy(showCheckoutGuide = v) } }
            SettingSwitch("Bildschirm im Match anlassen", s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }

            SectionLabel("Remote Scoring")
            val remoteUrl by vm.remoteUrl.collectAsStateWithLifecycle()
            SettingSwitch("Spielansicht im Browser (zweites Gerät)", remoteUrl != null) { on -> if (on) vm.startRemote() else vm.stopRemote() }
            Text(remoteUrl?.let { "Im WLAN öffnen: $it" } ?: "Das Handy bleibt als Lens-Kamera am Board, Scores laufen auf Tablet, PC oder TV.",
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)

            SectionLabel("Online-Remote (Cloudflare)")
            CloudRemoteSection(vm)

            SectionLabel("Bot")
            Text("Wurfpause: ${s.botDelayMillis} ms", color = DartColors.TextMuted)
            Slider(value = s.botDelayMillis.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(botDelayMillis = (v / 100).toInt() * 100L) } },
                valueRange = 200f..2000f)

            SectionLabel("Über")
            Text("FreeDarts ist ein kostenloser, lokaler Darts-Scorer ohne Konto, Abo oder Cloud. " +
                "Eingabe über Lens (Handykamera), virtuelles Board, Gesamtscore oder Dart für Dart – optional über einen Autodarts Board Manager im lokalen Netzwerk.",
                style = MaterialTheme.typography.bodyMedium, color = DartColors.TextMuted)
            Spacer(Modifier.height(8.dp))
            Text("KI-Modell: „dart-sense“ von Ben Willshaw (YOLOv8n), Lizenz CC BY-NC 4.0 – nur nicht-kommerzielle Nutzung. " +
                "Board-Erkennung, Spielmodi und Oberfläche: FreeDarts.", style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
        }
    }
}

/** Online-Remote: Spielansicht von überall über ein eigenes Relay auf Cloudflare (Ordner relay/ im Projekt). */
@Composable
private fun CloudRemoteSection(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cloudUrl by vm.cloudUrl.collectAsStateWithLifecycle()
    val status by vm.cloudStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    var url by remember { mutableStateOf(s.cloudRelayUrl) }

    Text("Spielansicht von überall im Browser, auch außerhalb des WLANs. Braucht ein eigenes, kostenloses Relay auf Cloudflare – Anleitung in relay/README.md.",
        color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = url, onValueChange = { url = it; vm.setCloudRelayUrl(it) },
        label = { Text("Relay-URL") }, placeholder = { Text("https://freedarts-relay.<account>.workers.dev") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); vm.applyCloudRelayUrl() }),
    )
    SettingSwitch("Online-Remote aktiv", cloudUrl != null) { on -> if (on) vm.startCloud() else vm.stopCloud() }
    if (cloudUrl == null && url.isBlank()) {
        Text("Zuerst die Relay-URL eintragen.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
    val link = cloudUrl ?: return
    AdCard(padding = 12) {
        val statusColor = when (status.phase) {
            CloudRelayClient.Phase.ONLINE -> DartColors.Green
            CloudRelayClient.Phase.REJECTED -> DartColors.Red
            else -> DartColors.Accent
        }
        Text(status.message.ifBlank { "Aus" }, color = statusColor, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Zuschauer-Link (Undo/Next möglich):", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        Text(link, fontWeight = FontWeight.Bold)
        Text("Code: ${s.cloudSessionCode}", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCode(link, size = 200.dp) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Teilen", Modifier.weight(1f)) {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "FreeDarts Live: $link") }
                runCatching { context.startActivity(Intent.createChooser(send, "Link teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            SecondaryButton("Kopieren", Modifier.weight(1f)) { clipboard.setText(AnnotatedString(link)) }
            SecondaryButton("Neuer Code", Modifier.weight(1f)) { vm.newCloudSession() }
        }
    }
}
