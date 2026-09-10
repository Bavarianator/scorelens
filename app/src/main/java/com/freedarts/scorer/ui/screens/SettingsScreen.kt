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

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
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
