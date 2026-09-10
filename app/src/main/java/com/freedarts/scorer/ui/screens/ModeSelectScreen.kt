package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.BrandTitle
import com.freedarts.scorer.ui.components.HeaderSwoosh
import com.freedarts.scorer.ui.components.ModeBadge
import com.freedarts.scorer.ui.components.SectionLabel
import com.freedarts.scorer.ui.components.description
import com.freedarts.scorer.ui.theme.DartColors

@Composable
fun ModeSelectScreen(vm: AppViewModel) {
    val gs by vm.lobbySettings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        HeaderSwoosh(Modifier.align(Alignment.TopEnd), height = 120)
        Column(Modifier.fillMaxSize()) {
            AdTopBar("", onBack = { vm.back() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                BrandTitle("Select Game Mode")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeCard(GameMode.X01, gs.mode == GameMode.X01, Modifier.weight(1f)) { vm.setMode(GameMode.X01); vm.back() }
                    ModeCard(GameMode.CRICKET, gs.mode == GameMode.CRICKET, Modifier.weight(1f)) { vm.setMode(GameMode.CRICKET); vm.back() }
                }
                listOf(GameMode.Category.PRACTICE to "Practice", GameMode.Category.PARTY to "Party").forEach { (cat, label) ->
                    SectionLabel(label)
                    val modes = GameMode.entries.filter { it.category == cat }
                    modes.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            pair.forEach { m -> ModeCard(m, gs.mode == m, Modifier.weight(1f)) { vm.setMode(m); vm.back() } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ModeCard(mode: GameMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 96.dp)
            .background(if (selected) DartColors.PrimaryDark.copy(alpha = 0.45f) else DartColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) DartColors.Primary else DartColors.Outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mode.title + (if (selected) " ✓" else ""), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            ModeBadge(mode)
        }
        Spacer(Modifier.height(6.dp))
        Text(mode.description(), color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
