package com.freedarts.scorer.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.theme.DartColors

/** Umstieg von Autodarts: Begriffe und Wege gegenübergestellt. */
@Composable
fun HelpScreen(vm: AppViewModel) {
    val rows = listOf(
        Triple("Play Now", "Play Now", "Gleicher Ablauf: Spieler, Modus, Autoscoring, Start Game."),
        Triple("Find an opponent", "Gegner finden", "Kein Online-Matchmaking. Stattdessen ein Bot auf deinem Niveau (aus deinem Average)."),
        Triple("Avatar → Devices → Start Lens Detection Mode", "Avatar → Devices → Lens", "Kamera starten, Board wird automatisch erkannt. Grün + Vibration = Ready to play."),
        Triple("Position your device", "Positionierungs-Hinweise", "Ganzes Board ins Bild, mehr von vorn, mehr von der Seite, näher ran."),
        Triple("Remote scoring (Browser)", "Devices → Remote Scoring", "Handy bleibt am Board, Spielansicht im Browser unter der angezeigten Adresse."),
        Triple("Dart korrigieren (Score-Balken antippen)", "Dart-Pill antippen oder Stift", "Segment auf dem virtuellen Board wählen, Bouncer möglich."),
        Triple("Autodarts X / Board Manager", "Devices → Board Manager", "Vorhandene Hardware im WLAN weiterverwenden."),
        Triple("Statistics · Overview / Match History", "Statistics", "Gleiche Tabs, Performance, Breakdown, Verlauf."),
        Triple("Plus: Killer, 121, Lens unbegrenzt", "Kostenlos enthalten", "Alle Modi und Lens ohne Limit."),
        Triple("Referee, Turniere, Online-Play", "Nicht vorhanden", "Braucht Server und Konto. FreeDarts läuft komplett lokal."),
        Triple("Caller Huw Ware", "Sprachausgabe des Systems", "Ansage von Scores, Bust und Game Shot über die Android-Sprachausgabe."),
    )
    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        Column(Modifier.fillMaxSize()) {
            AdTopBar("Umstieg von Autodarts", onBack = { vm.back() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                SectionLabel("Wo finde ich was")
                rows.forEach { (ad, fd, note) ->
                    AdCard(padding = 12) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("AUTODARTS", fontSize = 10.sp, color = DartColors.TextMuted, letterSpacing = 1.sp)
                                Text(ad, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("FREEDARTS", fontSize = 10.sp, color = DartColors.PrimaryLight, letterSpacing = 1.sp)
                                Text(fd, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(note, color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
