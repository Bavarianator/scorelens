package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.engine.Achievements
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdCard
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Badge
import com.freedarts.scorer.ui.components.Medal
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.tierColors
import com.freedarts.scorer.ui.components.tierName
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors

/** Alle Erfolge eines Spielers: Fortschrittsring, Stufen-Zähler, Gitter „Erreicht“ und „Offen“, Details im Bottom Sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(vm: AppViewModel, playerId: String) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    val players by vm.players.collectAsStateWithLifecycle()
    val name = players.firstOrNull { it.id == playerId }?.name ?: "Spieler"
    val badges = remember(matches, playerId) { Achievements.of(matches.filter { m -> m.players.any { it.playerId == playerId } }, playerId) }
    val done = badges.filter { it.done }; val open = badges.filter { !it.done }
    var picked by remember { mutableStateOf<Achievements.Achievement?>(null) }

    Box(Modifier.fillMaxSize()) { ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Erfolge · $name", onBack = { vm.back() })
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(span = { GridItemSpan(3) }) {
                AdCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Fortschrittsring über alle Erfolge
                        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(84.dp)) {
                                val stroke = 8.dp.toPx()
                                drawArc(DartColors.SurfaceHigh, 0f, 360f, false, style = Stroke(stroke))
                                drawArc(DartColors.Accent, -90f, 360f * done.size / badges.size.coerceAtLeast(1), false, style = Stroke(stroke, cap = StrokeCap.Round))
                            }
                            Text("${done.size}/${badges.size}", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${done.size} von ${badges.size} Erfolgen", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                (1..3).forEach { t ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(tierColors(t).second))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${done.count { it.tier == t }} ${tierName(t)}", fontSize = 12.sp, color = DartColors.TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (done.isNotEmpty()) item(span = { GridItemSpan(3) }) { SectionLabel("Erreicht", size = 18) }
            items(done, key = { it.title }) { a -> Tile(a) { picked = a } }
            if (open.isNotEmpty()) item(span = { GridItemSpan(3) }) { SectionLabel("Offen", size = 18) }
            items(open, key = { it.title }) { a -> Tile(a) { picked = a } }
        }
    }

    picked?.let { a ->
        ModalBottomSheet(onDismissRequest = { picked = null }, containerColor = DartColors.Surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Medal(a, selected = false, diameter = 96) { }
                Spacer(Modifier.height(4.dp))
                Text(a.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(a.description, color = DartColors.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                Badge(if (a.done) "${tierName(a.tier)} · erreicht" else "${tierName(a.tier)} · ${a.progress} / ${a.goal}", DartColors.OnTint, if (a.done) DartColors.GreenDark else DartColors.SurfaceHigh)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DartColors.SurfaceHigh)) {
                    Box(Modifier.fillMaxWidth((a.progress.toFloat() / a.goal).coerceIn(0f, 1f)).height(6.dp).background(tierColors(a.tier).second))
                }
            }
        }
    }
}

@Composable
private fun Tile(a: Achievements.Achievement, onClick: () -> Unit) {
    AdCard(onClick = onClick, padding = 8) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Medal(a, selected = false, diameter = 64, onClick = onClick)
            Text(if (a.done) tierName(a.tier) else "${a.progress} / ${a.goal}", fontSize = 11.sp, color = DartColors.TextMuted)
        }
    }
}
