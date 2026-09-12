package com.freedarts.scorer.lens

/** Texte für Status-Pill und Positionierungshinweise der Lens-Ansicht. */
object LensMessages {
    /** Mittlere Helligkeit (0..255), innerhalb derer Board-Suche und Erkennung verlässlich laufen. */
    const val LIGHT_MIN = 50
    const val LIGHT_MAX = 205

    fun guidance(setup: LensController.Setup, fit: BoardFinder.Fit?, ai: Boolean, brightness: Int = 0): String = when {
        setup == LensController.Setup.OFF -> ""
        setup == LensController.Setup.READY -> "Bereit – wirf!"
        setup == LensController.Setup.FOUND -> "Board erkannt ✓ – kurz ruhig halten"
        brightness in 1 until LIGHT_MIN -> "Zu dunkel – mehr Licht oder Taschenlampe an"
        brightness > LIGHT_MAX -> "Zu hell – Lampe oder Fenster aus dem Bild nehmen"
        fit == null -> if (ai) "Suche Board … ganzes Board ins Bild, gutes Licht" else "Board nicht gefunden: ganzes Board ins Bild, mehr Licht"
        else -> when (fit.quality) {
            BoardFinder.Quality.GOOD -> {
                val ratio = fit.ellipse.axisRatio.takeIf { it > 0 } ?: 0.7
                when {
                    ratio > BoardFinder.IDEAL_MAX -> "Board erkannt ✓ – Tipp: etwas mehr von der Seite (empfohlen 35–55°)"
                    ratio < BoardFinder.IDEAL_MIN -> "Board erkannt ✓ – Tipp: etwas mehr von vorn (empfohlen 35–55°)"
                    else -> "Board erkannt ✓"
                }
            }
            BoardFinder.Quality.PARTIAL -> "Das ganze Board muss sichtbar sein – weiter zurück"
            BoardFinder.Quality.TOO_SMALL -> "Näher ans Board"
            BoardFinder.Quality.INACCURATE -> "Ruhig halten, Licht gleichmäßiger"
            BoardFinder.Quality.NOT_FOUND -> "Board nicht gefunden: ganzes Board ins Bild, mehr Licht"
        }
    }

    fun status(running: Boolean, setup: LensController.Setup, checking: Boolean, phase: DartDetector.Phase, darts: Int): String = when {
        !running -> "Kamera aus"
        setup == LensController.Setup.SEARCHING -> "Suche Board …"
        setup == LensController.Setup.FOUND -> "Kalibriert – warte auf Ruhe"
        checking -> "Prüfe Dart …"
        phase == DartDetector.Phase.TAKEOUT -> "Darts entfernen"
        phase == DartDetector.Phase.MOTION -> "Bewegung …"
        else -> "Bereit · $darts Dart(s)"
    }
}
