package com.freedarts.scorer.lens

import com.freedarts.scorer.engine.Board
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Entzerrung des Board-Ausschnitts für das KI-Modell: Das Modell (dart-sense/DeepDarts) wurde überwiegend mit
 * Frontalaufnahmen trainiert. Steht die Kamera – wie von Autodarts vorgesehen – schräg (35–55° zur Boardfläche),
 * wird der Ausschnitt deshalb über die Kalibrier-Homographie in die Frontalansicht gerechnet: Board als Kreis in der
 * Mitte eines Quadrats, 20 oben, x nach rechts. Spitzen liegen in der Boardebene und werden dadurch exakt abgebildet.
 */
object BoardRectifier {
    /** Der Ausschnitt reicht bis zum [MARGIN]-fachen Boardradius (Flights, Zahlenring). */
    const val MARGIN = 1.35
    /** Kantenlänge des entzerrten Quadrats (px); mehr als die Modellgröße 800 bringt nichts, kostet aber Zeit. */
    const val MIN_SIZE = 320
    const val MAX_SIZE = 1000

    /** Kantenlänge, Abbildung entzerrtes Quadrat → aufrechtes Vollbild, Vollbild-Pixel je Quadrat-Pixel. */
    data class Plan(val size: Int, val toUpright: Homography, val scale: Double) {
        /** Boardradius im entzerrten Bild (px). */
        val boardRadiusPx: Double get() = size / (2 * MARGIN)
    }

    /**
     * @param b2i Board (mm) → Analysebild ([frameWidth]×[frameHeight]); das Vollbild ist [rotW]×[rotH] groß.
     * Die Kantenlänge folgt der größten Ausdehnung des Boards im Vollbild, damit keine Auflösung verloren geht.
     */
    fun plan(b2i: Homography, frameWidth: Int, frameHeight: Int, rotW: Int, rotH: Int): Plan {
        val fx = rotW.toDouble() / frameWidth; val fy = rotH.toDouble() / frameHeight
        val (cx, cy) = b2i.map(0.0, 0.0)
        var rPx = 0.0
        for (deg in 0 until 360 step 15) {
            val a = Math.toRadians(deg.toDouble())
            val (x, y) = b2i.map(Board.DOUBLE_OUTER * cos(a), Board.DOUBLE_OUTER * sin(a))
            rPx = max(rPx, hypot((x - cx) * fx, (y - cy) * fy))
        }
        val size = (2 * MARGIN * rPx).toInt().coerceIn(MIN_SIZE, MAX_SIZE)
        val mmPerPx = 2 * MARGIN * Board.DOUBLE_OUTER / size
        val half = MARGIN * Board.DOUBLE_OUTER
        // Quadrat-Pixel → Board-mm (Board-y zeigt nach oben, Bild-y nach unten)
        val rectToBoard = Homography.affine(mmPerPx, 0.0, -half, 0.0, -mmPerPx, half)
        val analysisToUpright = Homography.affine(fx, 0.0, 0.0, 0.0, fy, 0.0)
        return Plan(size, analysisToUpright * b2i * rectToBoard, rPx * 2 * MARGIN / size)
    }
}
