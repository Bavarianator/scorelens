package com.freedarts.scorer.lens

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Sammelt Trainingsdaten für das Feintuning des KI-Modells (siehe tools/finetune/): Bei jedem erkannten Dart wird
 * der Board-Ausschnitt, der dem Modell vorlag, als JPEG gespeichert und dazu ein YOLO-Label
 * (`klasse cx cy 0.025 0.025`, normiert) aus den aktuellen Erkennungen (Kalibrierpunkte + Spitzen).
 * Die Labels sind Vorschläge des jetzigen Modells und werden am PC geprüft (tools/finetune/prelabel.py, Review-Bilder).
 *
 * Ablage: `Android/data/<paket>/files/training/{images,labels}`; abholen per
 * `adb pull /sdcard/Android/data/com.freedarts.scorer/files/training`.
 */
class TrainingCapture(context: Context) {
    private val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "training")
    private val images = File(root, "images")
    private val labels = File(root, "labels")

    /** Wird über die Lens-Einstellung „Trainingsdaten sammeln“ gesetzt. */
    @Volatile var enabled = false
    /** Anzahl gespeicherter Bilder (Obergrenze [MAX_FILES]). */
    @Volatile var count = images.listFiles()?.size ?: 0
        private set

    companion object {
        const val MAX_FILES = 3000
        /** Boxgröße wie bei dart-sense / DeepDarts (2,5 % der Bildkante). */
        const val BOX = 0.025
        const val JPEG_QUALITY = 92
    }

    /** Speichert [frame] (Koordinaten von [res] in Frame-Pixeln) samt Label. Läuft im Aufrufer-Thread. */
    fun save(frame: RgbFrame, res: YoloDartModel.Result) {
        if (!enabled || count >= MAX_FILES) return
        try {
            images.mkdirs(); labels.mkdirs()
            val name = String.format(Locale.US, "lens_%d", System.currentTimeMillis())
            // Alpha erzwingen, sonst würde ARGB_8888 die Farben vormultiplizieren (schwarzes Bild)
            val px = IntArray(frame.pixels.size) { frame.pixels[it] or 0xFF000000.toInt() }
            val bmp = Bitmap.createBitmap(px, frame.width, frame.height, Bitmap.Config.ARGB_8888)
            FileOutputStream(File(images, "$name.jpg")).use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bmp.recycle()
            val sb = StringBuilder()
            fun line(cls: Int, x: Double, y: Double) {
                sb.append(String.format(Locale.US, "%d %.6f %.6f %.3f %.3f\n", cls, x / frame.width, y / frame.height, BOX, BOX))
            }
            res.calibration.forEach { (cls, p) -> line(cls, p.x, p.y) }
            res.darts.forEach { p -> line(YoloDartModel.DART_CLASS, p.x, p.y) }
            File(labels, "$name.txt").writeText(sb.toString())
            count++
        } catch (_: Exception) { }
    }
}
