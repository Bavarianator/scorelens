package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedarts.scorer.engine.Checkout
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.components.Chip
import com.freedarts.scorer.ui.components.ScreenBackground
import com.freedarts.scorer.ui.theme.Condensed
import com.freedarts.scorer.ui.theme.DartColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Checkout-Tabelle zum Nachschlagen: dieselben Wege, die der Checkout-Guide im Match und die Bots nutzen. */
@Composable
fun CheckoutTableScreen(vm: AppViewModel) {
    var outMode by remember { mutableStateOf(OutMode.DOUBLE) }
    // Suche über alle Reste dauert auf dem Handy einen Moment → nicht im Composition-Thread
    val rows by produceState<List<Pair<Int, String?>>>(emptyList(), outMode) {
        value = withContext(Dispatchers.Default) { (Checkout.maxFinish(outMode) downTo 2).map { it to Checkout.describe(Checkout.bestRoute(it, 3, outMode)) } }
    }
    Box(Modifier.fillMaxSize()) { ScreenBackground() }
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Checkout-Tabelle", onBack = { vm.back() })
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(OutMode.DOUBLE to "Double Out", OutMode.MASTER to "Master Out", OutMode.STRAIGHT to "Straight Out").forEach { (m, label) -> Chip(label, selected = outMode == m) { outMode = m } }
        }
        Text("Rest · bester Weg mit 3 Darts · Grau = nicht mit 3 Darts machbar", color = DartColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        if (rows.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            items(rows, key = { it.first }) { (score, route) ->
                val darts = route?.split("  ")?.size ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(DartColors.Surface, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(score.toString(), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.width(52.dp), color = if (route == null) DartColors.TextMuted else DartColors.Text)
                    Text(route?.replace("  ", "   ") ?: "–", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (route == null) DartColors.TextMuted else DartColors.Orange, modifier = Modifier.weight(1f))
                    if (darts in 1..2) Chip("$darts ${if (darts == 1) "Dart" else "Darts"}")
                }
            }
        }
    }
}
