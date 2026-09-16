package com.freedarts.scorer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** QR-Code (z. B. Zuschauer-Link des Online-Remotes), gerendert ohne Bitmap direkt auf einem Canvas. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val matrix = remember(text) {
        runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }.getOrNull()
    } ?: return
    Canvas(modifier.size(size).background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp)) {
        val cell = this.size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/**
 * Vollbild-QR-Scanner (CameraX + ZXing) für Freundes-Links. Bindet nur eigene Use-Cases und löst sie beim Schließen
 * wieder, damit eine laufende Lens-Kamera nicht gestört wird. Liefert den ersten erkannten Text genau einmal.
 */
@androidx.compose.runtime.Composable
fun QrScannerDialog(onDismiss: () -> Unit, hint: String = "QR-Code eines Freundes scannen", onResult: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var granted by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted = it; if (!it) onDismiss() }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!granted) permission.launch(android.Manifest.permission.CAMERA) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                val previewView = remember { androidx.camera.view.PreviewView(context) }
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val preview = androidx.camera.core.Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = androidx.camera.core.ImageAnalysis.Builder()
                        .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    val reader = com.google.zxing.MultiFormatReader().apply {
                        setHints(mapOf(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                    }
                    var done = false
                    analysis.setAnalyzer(java.util.concurrent.Executors.newSingleThreadExecutor()) { image ->
                        image.use {
                            if (done) return@use
                            val plane = it.planes[0]
                            val bytes = ByteArray(plane.buffer.remaining()).also { b -> plane.buffer.get(b) }
                            val source = com.google.zxing.PlanarYUVLuminanceSource(bytes, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
                            val text = runCatching { reader.decodeWithState(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))).text }.getOrNull()
                            if (text != null) { done = true; previewView.post { onResult(text) } }
                        }
                    }
                    val future = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                    var provider: androidx.camera.lifecycle.ProcessCameraProvider? = null
                    future.addListener({
                        provider = future.get().also { runCatching { it.bindToLifecycle(owner, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) } }
                    }, androidx.core.content.ContextCompat.getMainExecutor(context))
                    onDispose { provider?.unbind(preview, analysis) }
                }
                androidx.compose.ui.viewinterop.AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            }
            androidx.compose.material3.Text(
                hint, color = Color.White,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 48.dp),
            )
            androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(8.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, "Schließen", tint = Color.White)
            }
        }
    }
}
