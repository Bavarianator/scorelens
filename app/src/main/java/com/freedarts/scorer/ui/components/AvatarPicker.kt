package com.freedarts.scorer.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.freedarts.scorer.model.Player
import java.io.ByteArrayOutputStream

/** Profilbild wählen: Foto aufnehmen oder aus der Galerie (Android-Fotoauswahl, keine Speicherberechtigung nötig). */
@Composable
fun AvatarPicker(player: Player, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { u -> loadBitmap(context, u)?.let { onChange(encodeAvatar(it)) } }
    }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> bmp?.let { onChange(encodeAvatar(it)) } }
    // Da die App CAMERA deklariert, verlangt Android die Berechtigung auch für die Kamera-App
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) shoot.launch(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(player, 56, online = false)
        Spacer(Modifier.width(2.dp))
        SecondaryButton("Foto", Modifier.weight(1f), icon = Icons.Default.CameraAlt) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) shoot.launch(null)
            else permission.launch(Manifest.permission.CAMERA)
        }
        SecondaryButton("Galerie", Modifier.weight(1f), icon = Icons.Default.Image) {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (player.avatar != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Default.Delete, "Bild entfernen") }
    }
}

/** Bild quadratisch zuschneiden, auf [AVATAR_PX] verkleinern und als Base64-JPEG kodieren (~5 KB). */
fun encodeAvatar(src: Bitmap): String {
    val side = minOf(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val small = Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true)
    val out = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 75, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private const val AVATAR_PX = 128

/** Galeriebild verkleinert laden (mit EXIF-Drehung ab Android 9). */
private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = maxOf(1, minOf(info.size.width, info.size.height) / (AVATAR_PX * 2))
            d.setTargetSampleSize(scale)
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / (AVATAR_PX * 2)) }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}.getOrNull()
