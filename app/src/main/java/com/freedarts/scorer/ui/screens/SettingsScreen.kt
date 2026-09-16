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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Palette
import com.freedarts.scorer.ui.components.Chip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.BuildConfig
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Avatar
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.theme.DartColors

/** Einstellungen als Karten im Stil der Devices-Seite: Icon, Titel, Untertitel, dann die Schalter. */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val session by vm.online.session.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        Column(Modifier.fillMaxSize()) {
            AdTopBar("Einstellungen", onBack = { vm.back() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.height(2.dp))

                SettingsCard(Icons.Default.Person, "Profil", "Wessen Statistiken auf der Startseite stehen") {
                    val chosen = s.profilePlayerId ?: players.firstOrNull()?.id
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        players.forEach { p ->
                            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.updateSettings { it.copy(profilePlayerId = p.id) } }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.border(2.dp, if (p.id == chosen) DartColors.Text else Color.Transparent, CircleShape).padding(3.dp)) { Avatar(p, 44, online = false) }
                                Text(p.name, fontSize = 11.sp, fontWeight = if (p.id == chosen) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (p.id == chosen) DartColors.Text else DartColors.TextMuted, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Spieler verwalten", Modifier.fillMaxWidth()) { vm.navigate(Screen.Players) }
                    val export = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportTo(it) } }
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton("Spieler und Verlauf exportieren (JSON)", Modifier.fillMaxWidth()) {
                        export.launch("scorelens-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY).format(java.util.Date()) + ".json")
                    }
                }

                val user = session?.user
                SettingsCard(Icons.Default.AccountCircle, "Online-Konto", user?.email ?: user?.provider?.replaceFirstChar { it.uppercase() } ?: "Nicht angemeldet",
                    status = if (user != null) "Angemeldet" to DartColors.Green else "Nicht angemeldet" to DartColors.TextMuted) {
                    if (user != null) SecondaryButton("Abmelden", Modifier.fillMaxWidth()) { vm.online.signOut() }
                    else SecondaryButton("Anmelden und online spielen", Modifier.fillMaxWidth()) { vm.navigate(Screen.Online) }
                }

                SettingsCard(Icons.Default.Palette, "Darstellung", "Hell, Dunkel oder wie das System") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system" to "System", "light" to "Hell", "dark" to "Dunkel").forEach { (k, l) ->
                            Chip(l, selected = s.theme == k) { vm.updateSettings { it.copy(theme = k) } }
                        }
                    }
                }

                SettingsCard(Icons.Default.VolumeUp, "Caller & Sound", "Sprachansage und Effekte") {
                    SettingSwitch("Caller (Sprachansage)", s.callerEnabled) { v -> vm.updateSettings { it.copy(callerEnabled = v) } }
                    if (s.callerEnabled) {
                        Text("Aufnahmen ansagen", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        // „Aus“ = callerCallsEveryVisit false; sonst Mindest-Score
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Aus" to -1, "Alle" to 0, "ab 100" to 100, "nur 180" to 180).forEach { (label, min) ->
                                val on = if (min < 0) !s.callerCallsEveryVisit else s.callerCallsEveryVisit && s.callerMinScore == min
                                FilterChip(selected = on, label = { Text(label) }, onClick = {
                                    vm.updateSettings { it.copy(callerCallsEveryVisit = min >= 0, callerMinScore = min.coerceAtLeast(0)) }
                                })
                            }
                        }
                        Text("Sprache und Stimme", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Deutsch" to false, "English" to true).forEach { (label, en) ->
                                FilterChip(selected = s.callerEnglish == en, label = { Text(label) }, onClick = {
                                    vm.updateSettings { it.copy(callerEnglish = en, callerVoice = "") }; vm.caller.sample()
                                })
                            }
                        }
                        // Stimmen der installierten Sprachausgabe; Antippen wählt und spielt eine Hörprobe
                        val voices = remember(s.callerEnglish) { vm.caller.voices().map { it.name } }
                        if (voices.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("") + voices).forEachIndexed { i, name ->
                                FilterChip(selected = s.callerVoice == name, label = { Text(if (i == 0) "Standard" else "Stimme $i") }, onClick = {
                                    vm.updateSettings { it.copy(callerVoice = name) }; vm.caller.sample()
                                })
                            }
                        }
                    }
                    SettingSwitch("Jeden Dart ansagen", s.countEachThrow, enabled = s.callerEnabled) { v -> vm.updateSettings { it.copy(countEachThrow = v) } }
                    SettingSwitch("Soundeffekte", s.soundEffects) { v -> vm.updateSettings { it.copy(soundEffects = v) } }
                }

                SettingsCard(Icons.Default.SportsScore, "Match", "Anzeige und Ablauf im Spiel") {
                    SettingSwitch("Checkout-Guide", s.showCheckoutGuide) { v -> vm.updateSettings { it.copy(showCheckoutGuide = v) } }
                    SecondaryButton("Checkout-Tabelle", Modifier.fillMaxWidth()) { vm.navigate(Screen.CheckoutTable) }
                    SettingSlider("Automatisch nächster Spieler", if (s.autoNextDelayMs == 0L) "Aus" else "nach ${s.autoNextDelayMs / 1000} s",
                        s.autoNextDelayMs.toFloat(), 0f..20000f, steps = 19) { v -> vm.updateSettings { it.copy(autoNextDelayMs = (v / 1000).toInt() * 1000L) } }
                    // Selten gebraucht: erst auf Tipp sichtbar
                    var advanced by remember { mutableStateOf(false) }
                    TextButton(onClick = { advanced = !advanced }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = DartColors.PrimaryLight)
                        Spacer(Modifier.width(4.dp)); Text(if (advanced) "Weniger" else "Erweitert", color = DartColors.PrimaryLight, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    if (advanced) {
                        SettingSwitch("Chalkboard anzeigen", s.showChalkboard) { v -> vm.updateSettings { it.copy(showChalkboard = v) } }
                        SettingSwitch("Bildschirm anlassen", s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
                        SettingSwitch("Darts Zoom im Kamerabild", s.dartsZoom) { v -> vm.updateSettings { it.copy(dartsZoom = v) } }
                        SettingSwitch("Animationen und Match-Intro", s.animations) { v -> vm.updateSettings { it.copy(animations = v) } }
                        SettingSwitch("Vibration bei Dart, Undo, Next", s.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
                        SettingSlider("Bot-Wurfpause", "%.1f s".format(s.botDelayMillis / 1000f), s.botDelayMillis.toFloat(), 200f..2000f, steps = 17) { v ->
                            vm.updateSettings { it.copy(botDelayMillis = (v / 100).toInt() * 100L) }
                        }
                    }
                }

                SettingsCard(Icons.Default.Devices, "Geräte", "Lens-Kamera, Board Manager, Remote Scoring") {
                    SettingSwitch("Lens beim Match automatisch starten", s.lensAutoStart, enabled = s.lensCalibration.size == 8) { v -> vm.updateSettings { it.copy(lensAutoStart = v) } }
                    if (s.lensCalibration.size != 8) Text("Erst einmal unter Geräte › Lens kalibrieren.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton("Geräte verwalten", Modifier.fillMaxWidth()) { vm.navigate(Screen.Devices) }
                }

                SettingsCard(Icons.Default.Info, "Scorelens", "Version ${BuildConfig.VERSION_NAME}") {
                    Text("Kostenloser Darts-Scorer ohne Abo. Lokal ohne Konto; der Online-Modus ist optional. " +
                        "Eingabe über Lens (Handykamera), virtuelles Board, Gesamtscore oder Dart für Dart – optional über einen Autodarts Board Manager im lokalen Netzwerk.",
                        style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Text("KI-Modell: „dart-sense“ von Ben Willshaw (YOLOv8n), Lizenz CC BY-NC 4.0 – nur nicht-kommerzielle Nutzung. Icons: Lucide (ISC).",
                        style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
                    Spacer(Modifier.height(6.dp))
                    SettingSwitch("Nutzungsdaten und Absturzberichte senden", s.analyticsEnabled) { v -> vm.updateSettings { it.copy(analyticsEnabled = v) } }
                    Text("Anonym über Google Firebase: welche Bildschirme und Modi genutzt werden, Match-Ende und Abstürze. Keine Namen, Scores oder Kamerabilder.",
                        style = MaterialTheme.typography.bodySmall, color = DartColors.TextMuted)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Umstieg von Autodarts", Modifier.fillMaxWidth()) { vm.navigate(Screen.Help) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, subtitle: String, status: Pair<String, Color>? = null, content: @Composable () -> Unit) {
    AdCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(DartColors.SurfaceHigh, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = DartColors.Text) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (status != null) Row(Modifier.border(1.dp, status.second, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(status.second, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(status.first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = status.second)
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Beschriftung links, aktueller Wert rechts in Primary, darunter der Regler. */
@Composable
private fun SettingSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(value, color = DartColors.PrimaryLight, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
    Slider(value = current, onValueChange = onChange, valueRange = range, steps = steps)
}
