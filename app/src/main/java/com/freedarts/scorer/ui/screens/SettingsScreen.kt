@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) { ScreenBackground() }
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
            SettingSwitch("Darts Zoom (Aufnahme groß im Kamerabild)", s.dartsZoom) { v -> vm.updateSettings { it.copy(dartsZoom = v) } }
            SettingSwitch("Animationen und Match-Intro", s.animations) { v -> vm.updateSettings { it.copy(animations = v) } }
            Text("Automatic Next Player: " + (if (s.autoNextDelayMs == 0L) "aus" else "nach ${s.autoNextDelayMs / 1000} s ohne Dart"), color = DartColors.TextMuted)
            Slider(value = s.autoNextDelayMs.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(autoNextDelayMs = (v / 1000).toInt() * 1000L) } }, valueRange = 0f..20000f, steps = 19)

            SectionLabel("Remote Scoring")
            val remoteUrl by vm.remoteUrl.collectAsStateWithLifecycle()
            SettingSwitch("Spielansicht im Browser (zweites Gerät)", remoteUrl != null) { on -> if (on) vm.startRemote() else vm.stopRemote() }
            Text(remoteUrl?.let { "Im WLAN öffnen: $it" } ?: "Das Handy bleibt als Lens-Kamera am Board, Scores laufen auf Tablet, PC oder TV.",
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)

            SectionLabel("Online-Konto")
            val onlineSession by vm.online.session.collectAsStateWithLifecycle()
            if (onlineSession != null) {
                Text("Angemeldet (${onlineSession?.user?.email ?: onlineSession?.user?.provider ?: "Konto"})", color = DartColors.Green, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                SecondaryButton("Abmelden", Modifier.fillMaxWidth()) { vm.online.signOut() }
            } else {
                Text("Nicht angemeldet – Startseite › Online spielen.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }

            SectionLabel("Bot")
            Text("Wurfpause: ${s.botDelayMillis} ms", color = DartColors.TextMuted)
            Slider(value = s.botDelayMillis.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(botDelayMillis = (v / 100).toInt() * 100L) } },
                valueRange = 200f..2000f)

            SectionLabel("Hilfe")
            SecondaryButton("Umstieg von Autodarts", Modifier.fillMaxWidth()) { vm.navigate(Screen.Help) }

            SectionLabel("Über")
            Text("Scorelens ist ein kostenloser Darts-Scorer ohne Abo. Lokal ohne Konto; der Online-Modus ist optional. " +
                "Eingabe über Lens (Handykamera), virtuelles Board, Gesamtscore oder Dart für Dart – optional über einen Autodarts Board Manager im lokalen Netzwerk.",
                style = MaterialTheme.typography.bodyMedium, color = DartColors.TextMuted)
            Spacer(Modifier.height(8.dp))
            Text("KI-Modell: „dart-sense“ von Ben Willshaw (YOLOv8n), Lizenz CC BY-NC 4.0 – nur nicht-kommerzielle Nutzung. " +
                "Board-Erkennung, Spielmodi und Oberfläche: Scorelens.", style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
        }
    }
}
