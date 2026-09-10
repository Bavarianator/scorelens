package com.freedarts.scorer.lens

import androidx.camera.core.ImageProxy

/** Aufrechter Farbbild-Ausschnitt (0xRRGGBB) in Kameraauflösung mit seiner Lage im aufrechten Vollbild. */
class RgbFrame(val pixels: IntArray, val width: Int, val height: Int, val offsetX: Double, val offsetY: Double)

/**
 * Wandelt CameraX-YUV-Frames in aufrechte Bilder um: skaliert auf die Analysegröße (Grau, optional Farbe)
 * oder als Farb-Ausschnitt in voller Auflösung.
 */
class FrameConverter(private val frameWidth: Int, private val frameHeight: Int) {

    private class Planes(img: ImageProxy, color: Boolean) {
        val srcW = img.width; val srcH = img.height
        val rot = img.imageInfo.rotationDegrees
        val rotW = if (rot == 90 || rot == 270) srcH else srcW
        val rotH = if (rot == 90 || rot == 270) srcW else srcH
        val y: ByteArray; val yRow: Int; val yPix: Int
        val u: ByteArray?; val uRow: Int; val uPix: Int
        val v: ByteArray?; val vRow: Int; val vPix: Int

        init {
            val yp = img.planes[0]; val up = img.planes[1]; val vp = img.planes[2]
            yp.buffer.rewind(); up.buffer.rewind(); vp.buffer.rewind()
            y = ByteArray(yp.buffer.remaining()).also { yp.buffer.get(it) }; yRow = yp.rowStride; yPix = yp.pixelStride
            u = if (color) ByteArray(up.buffer.remaining()).also { up.buffer.get(it) } else null; uRow = up.rowStride; uPix = up.pixelStride
            v = if (color) ByteArray(vp.buffer.remaining()).also { vp.buffer.get(it) } else null; vRow = vp.rowStride; vPix = vp.pixelStride
        }

        fun sx(rx: Int, ry: Int) = when (rot) { 90 -> ry; 180 -> srcW - 1 - rx; 270 -> srcW - 1 - ry; else -> rx }
        fun sy(rx: Int, ry: Int) = when (rot) { 90 -> srcH - 1 - rx; 180 -> srcH - 1 - ry; 270 -> rx; else -> ry }

        fun luma(sx: Int, sy: Int): Int {
            val i = sy * yRow + sx * yPix
            return if (i in y.indices) y[i].toInt() and 0xFF else 0
        }

        fun rgb(sx: Int, sy: Int, yv: Int): Int {
            val ui = (sy / 2) * uRow + (sx / 2) * uPix; val vi = (sy / 2) * vRow + (sx / 2) * vPix
            val uu = (if (u != null && ui in u.indices) u[ui].toInt() and 0xFF else 128) - 128
            val vv = (if (v != null && vi in v.indices) v[vi].toInt() and 0xFF else 128) - 128
            val r = (yv + 1.402 * vv).toInt().coerceIn(0, 255)
            val g = (yv - 0.344 * uu - 0.714 * vv).toInt().coerceIn(0, 255)
            val b = (yv + 1.772 * uu).toInt().coerceIn(0, 255)
            return (r shl 16) or (g shl 8) or b
        }
    }

    /** Breite × Höhe des aufrechten Vollbilds. */
    fun uprightSize(img: ImageProxy): Pair<Int, Int> {
        val rot = img.imageInfo.rotationDegrees
        return if (rot == 90 || rot == 270) img.height to img.width else img.width to img.height
    }

    /** Aufrechtes Graubild in Analysegröße, optional dazu ein Farbbild gleicher Größe. */
    fun scaled(img: ImageProxy, wantColor: Boolean): Pair<ByteArray, IntArray?> {
        val p = Planes(img, wantColor)
        val gray = ByteArray(frameWidth * frameHeight)
        val rgb = if (wantColor) IntArray(frameWidth * frameHeight) else null
        for (y in 0 until frameHeight) {
            val ry = y * p.rotH / frameHeight
            for (x in 0 until frameWidth) {
                val rx = x * p.rotW / frameWidth
                val sx = p.sx(rx, ry); val sy = p.sy(rx, ry)
                val yv = p.luma(sx, sy)
                gray[y * frameWidth + x] = yv.toByte()
                if (rgb != null) rgb[y * frameWidth + x] = p.rgb(sx, sy, yv)
            }
        }
        return gray to rgb
    }

    /**
     * Farbbild in voller Kameraauflösung, beschnitten auf [x0,x1)×[y0,y1) in Koordinaten des aufrechten
     * Vollbilds (wird ins Bild geklemmt).
     */
    fun crop(img: ImageProxy, x0: Int, y0: Int, x1: Int, y1: Int): RgbFrame? = try {
        val p = Planes(img, true)
        val cx0 = x0.coerceIn(0, p.rotW - 1); val cx1 = x1.coerceIn(cx0 + 1, p.rotW)
        val cy0 = y0.coerceIn(0, p.rotH - 1); val cy1 = y1.coerceIn(cy0 + 1, p.rotH)
        val w = cx1 - cx0; val h = cy1 - cy0
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val ry = y + cy0
            for (x in 0 until w) {
                val rx = x + cx0
                val sx = p.sx(rx, ry); val sy = p.sy(rx, ry)
                out[y * w + x] = p.rgb(sx, sy, p.luma(sx, sy))
            }
        }
        RgbFrame(out, w, h, cx0.toDouble(), cy0.toDouble())
    } catch (e: Exception) { null }
}
