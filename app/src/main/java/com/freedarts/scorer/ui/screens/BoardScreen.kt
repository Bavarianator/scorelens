package com.freedarts.scorer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.board.BoardManagerClient
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.theme.DartColors
import kotlinx.coroutines.launch

@Composable
fun BoardScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.board.connection.collectAsStateWithLifecycle()
    val state by vm.board.state.collectAsStateWithLifecycle()
    var host by remember { mutableStateOf(settings.boardManagerHost) }
    var port by remember { mutableStateOf(settings.boardManagerPort.toString()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopBar("Board Manager", onBack = { vm.back() })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Verbinde FreeDarts mit einem Autodarts Board Manager im selben WLAN (Standard-Port 3180). " +
                "Erkannte Würfe werden automatisch in das laufende Spiel übernommen – die Kamera-Erkennung selbst bleibt kostenlos.",
                style = MaterialTheme.typography.bodyMedium, color = DartColors.TextMuted)
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("IP-Adresse / Hostname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = port, onValueChange = { port = it.filter { c -> c.isDigit() } }, label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    testResult = "Teste …"
                    scope.launch { testResult = if (vm.board.testConnection(host, port.toIntOrNull() ?: 3180)) "✓ Board Manager erreichbar" else "✗ Nicht erreichbar" }
                }, Modifier.weight(1f)) { Text("Testen") }
                if (connection == BoardManagerClient.Connection.DISCONNECTED) {
                    Button(onClick = { vm.connectBoard(host, port.toIntOrNull() ?: 3180) }, Modifier.weight(1f)) { Text("Verbinden") }
                } else {
                    Button(onClick = { vm.disconnectBoard() }, Modifier.weight(1f)) { Text("Trennen") }
                }
            }
            testResult?.let { Text(it) }

            Spacer(Modifier.height(8.dp))
            val statusText = when (connection) {
                BoardManagerClient.Connection.DISCONNECTED -> "Nicht verbunden"
                BoardManagerClient.Connection.CONNECTING -> "Verbinde …"
                BoardManagerClient.Connection.CONNECTED -> "Verbunden"
                BoardManagerClient.Connection.ERROR -> "Fehler – Board Manager nicht erreichbar"
            }
            Text("Status: $statusText", style = MaterialTheme.typography.titleMedium,
                color = if (connection == BoardManagerClient.Connection.CONNECTED) DartColors.Green else DartColors.TextMuted)
            if (connection == BoardManagerClient.Connection.CONNECTED) {
                Text("Board-Status: ${state.status.ifEmpty { "–" }}   Event: ${state.event.ifEmpty { "–" }}")
                Text("Aktuelle Würfe: " + (state.throws.joinToString("  ") { it.name }.ifEmpty { "–" }))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.board.command("start") }) { Text("Start") }
                    OutlinedButton(onClick = { vm.board.command("stop") }) { Text("Stop") }
                    OutlinedButton(onClick = { vm.board.command("reset") }) { Text("Reset") }
                    OutlinedButton(onClick = { vm.board.command("calibrate") }) { Text("Kalibrieren") }
                }
            }
        }
    }
}
