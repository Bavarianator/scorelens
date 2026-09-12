@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.freedarts.scorer.online.OnlineController
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SecondaryButton
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/**
 * Erster Start in zwei Schritten: Name → Konto (Google/GitHub/Gast, je ein Tap). Sobald eine Sitzung da ist, geht es
 * direkt los. Lens-Setup wie bei Autodarts getrennt unter Devices, nicht im Wizard.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val players by vm.players.collectAsStateWithLifecycle()
    val session by vm.online.session.collectAsStateWithLifecycle()
    val error by vm.online.error.collectAsStateWithLifecycle()
    val busy by vm.online.busy.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(players.firstOrNull()?.name?.takeIf { it != "Spieler 1" } ?: "") }
    var color by remember { mutableStateOf(players.firstOrNull()?.color ?: Player.AVATAR_COLORS[0]) }
    BackHandler(enabled = step > 0) { step-- }
    LaunchedEffect(session, step) { if (step == 1 && session != null) vm.finishOnboarding(name, color) }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 220)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(40.dp))
            BrandTitle("Scorelens", size = 30)
            Spacer(Modifier.height(6.dp))
            Text(if (step == 0) "JUST PLAY" else "ONLINE", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp)
            Text(
                if (step == 0) "Autoscoring mit der Handykamera, alle Spielmodi, keine Abos. Wie sollen wir dich nennen?"
                else "Ein Tap, und du spielst online gegen andere. Deine Statistik bleibt erhalten.",
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { i -> Box(Modifier.weight(1f).height(4.dp).background(if (i <= step) DartColors.Primary else DartColors.Outline, CircleShape)) }
            }
            Spacer(Modifier.height(24.dp))

            if (step == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
                        Text(name.trim().take(2).uppercase().ifEmpty { "S1" }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Player.AVATAR_COLORS.forEach { c ->
                        Box(Modifier.size(34.dp).background(Color(c), CircleShape).border(3.dp, if (c == color) Color.White else Color.Transparent, CircleShape).clickable { color = c })
                    }
                }
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Weiter", Modifier.fillMaxWidth(), height = 56, enabled = name.isNotBlank()) { step = 1 }
            } else {
                OnlineController.PROVIDERS.forEach { (id, label) ->
                    PrimaryButton("Mit $label anmelden", Modifier.fillMaxWidth(), height = 56, enabled = !busy) { vm.online.beginOAuth(id) }
                    Spacer(Modifier.height(10.dp))
                }
                SecondaryButton("Als Gast spielen", Modifier.fillMaxWidth(), enabled = !busy) { vm.online.signInAsGuest(name.trim()) }
                if (busy) Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                error?.let { Text(it, color = DartColors.Accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { vm.finishOnboarding(name, color) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Später – erst mal lokal spielen", color = DartColors.TextMuted) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
