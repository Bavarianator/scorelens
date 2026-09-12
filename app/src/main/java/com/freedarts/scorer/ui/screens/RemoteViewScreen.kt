package com.freedarts.scorer.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.components.AdTopBar
import com.freedarts.scorer.ui.theme.DartColors

/**
 * Zweitgerät in der App: zeigt die Remote-Seite des Board-Handys (http://<ip>:8765) in einem WebView – dieselbe
 * Ansicht wie im Browser, inklusive Eingabe, Undo und Next. Gekoppelt wird unter Devices per QR-Code oder Adresse.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RemoteViewScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val url = settings.remotePairedUrl
    Column(Modifier.fillMaxSize()) {
        AdTopBar(url.removePrefix("http://"), onBack = { vm.back() }) {
            TextButton(onClick = { vm.updateSettings { it.copy(remotePairedUrl = "") }; vm.back() }) { Text("Trennen", color = DartColors.TextMuted) }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    this.settings.javaScriptEnabled = true
                    this.settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    setBackgroundColor(0xFF0B1220.toInt())
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
