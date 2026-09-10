@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.NameRibbon
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/** Erster Start: Profil anlegen – danach "Just play". */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val players by vm.players.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(players.firstOrNull()?.name?.takeIf { it != "Spieler 1" } ?: "") }
    var color by remember { mutableStateOf(players.firstOrNull()?.color ?: Player.AVATAR_COLORS[0]) }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 220)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(40.dp))
            BrandTitle("FreeDarts", size = 30)
            Spacer(Modifier.height(6.dp))
            Text("JUST PLAY", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp)
            Text("Autoscoring mit der Handykamera, alle Spielmodi, keine Abos. Lokal auf deinem Gerät.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))

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
            Spacer(Modifier.height(10.dp))
            AdCard {
                Text("2 · Devices", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Lens (Handykamera) richtest du unter Avatar → Devices ein: Kamera starten, Board wird automatisch erkannt.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            AdCard {
                Text("3 · Play Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Spieler und Bots hinzufügen, Modus wählen, Autoscoring einschalten, Start Game.", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Los geht's", Modifier.fillMaxWidth(), height = 56) { vm.finishOnboarding(name, color) }
            Spacer(Modifier.height(30.dp))
        }
    }
}
