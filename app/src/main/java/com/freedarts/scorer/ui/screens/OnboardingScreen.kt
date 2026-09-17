@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.freedarts.scorer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.online.OnlineController
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.OAuthButton
import com.freedarts.scorer.ui.components.PrimaryButton
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/**
 * Erster Start in zwei Schritten: Profil (Name, Farbe) → Konto (Google/GitHub mit Logo, Gast). Sobald eine Sitzung
 * da ist, geht es direkt los. Während der Browser offen ist, zeigt der Wizard "läuft im Browser" mit Abbrechen.
 * Lens-Setup wie bei Autodarts getrennt unter Devices, nicht im Wizard.
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
    /** Anbieter, dessen Anmeldung gerade im Browser läuft (null = keine). */
    var pending by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = step > 0) { step--; pending = null }
    LaunchedEffect(session, step) { if (step == 1 && session != null) vm.finishOnboarding(name, color) }
    LaunchedEffect(error, busy) { if (error != null || busy) pending = null }

    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 220)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(40.dp))
            BrandTitle("Scorelens", size = 30)
            Spacer(Modifier.height(18.dp))
            Text("SCHRITT ${step + 1} VON 2", color = DartColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { i -> Box(Modifier.weight(1f).height(4.dp).background(if (i <= step) DartColors.Primary else DartColors.Outline, CircleShape)) }
            }
            Spacer(Modifier.height(18.dp))

            AnimatedContent(step, transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260)) { dir * it / 3 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(200)) { -dir * it / 3 } + fadeOut(tween(200)))
            }, label = "onboarding") { s ->
                Column {
                    Text(if (s == 0) "DEIN PROFIL" else "DEIN KONTO", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp)
                    Text(
                        if (s == 0) "Autoscoring mit der Handykamera, alle Spielmodi, keine Abos. Wie sollen wir dich nennen?"
                        else "Ein Tap, und du spielst online gegen andere. Deine Statistik bleibt auf allen Geräten erhalten.",
                        color = DartColors.TextMuted, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    if (s == 0) ProfileStep(name, color, onName = { name = it }, onColor = { color = it }) { step = 1 }
                    else AccountStep(vm, busy, pending, error, onPending = { pending = it }) { vm.finishOnboarding(name, color) }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProfileStep(name: String, color: Long, onName: (String) -> Unit, onColor: (Long) -> Unit, onNext: () -> Unit) {
    AdCard(padding = 16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).background(Color(color), CircleShape).border(3.dp, Color.White.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Text(name.trim().take(2).uppercase().ifEmpty { "?" }, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            OutlinedTextField(
                value = name, onValueChange = { onName(it.take(32)) }, label = { Text("Dein Name") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Farbe", color = DartColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Player.AVATAR_COLORS.forEach { c ->
                Box(Modifier.size(36.dp).background(Color(c), CircleShape).border(3.dp, if (c == color) Color.White else Color.Transparent, CircleShape).clickable(onClickLabel = "Farbe wählen", role = androidx.compose.ui.semantics.Role.RadioButton) { onColor(c) }.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                    if (c == color) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Feature(Icons.Default.CameraAlt, "Kamera-\nScoring")
        Feature(Icons.Default.SportsScore, "Alle\nSpielmodi")
        Feature(Icons.Default.Public, "Online\nspielen")
    }
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Weiter", Modifier.fillMaxWidth(), height = 56, enabled = name.isNotBlank(), onClick = onNext)
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).background(DartColors.SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = DartColors.Teal) }
        Spacer(Modifier.height(6.dp))
        Text(text, color = DartColors.TextMuted, fontSize = 12.sp, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AccountStep(vm: AppViewModel, busy: Boolean, pending: String?, error: String?, onPending: (String?) -> Unit, onSkip: () -> Unit) {
    val locked = busy || pending != null
    AdCard(padding = 16) {
        OnlineController.PROVIDERS.forEach { (id, label) ->
            OAuthButton(id, label, Modifier.fillMaxWidth(), enabled = !locked, height = 54) { if (vm.online.beginOAuth(id)) onPending(id) }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (busy || pending != null) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                if (busy) "Anmeldung wird abgeschlossen …"
                else "Anmeldung bei ${OnlineController.PROVIDERS.toMap()[pending] ?: pending} läuft im Browser …",
                color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!busy) TextButton(onClick = { onPending(null) }, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen", color = DartColors.TextMuted) }
    }
    error?.let {
        AdCard(Modifier.padding(top = 12.dp), background = DartColors.RedDark, padding = 10) { Text(it, color = DartColors.OnTint, style = MaterialTheme.typography.bodySmall) }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Später – erst mal lokal spielen", color = DartColors.TextMuted) }
}
