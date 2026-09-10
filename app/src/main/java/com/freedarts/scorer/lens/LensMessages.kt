package com.freedarts.scorer.lens

/** Texte für Status-Pill und Positionierungshinweise der Lens-Ansicht. */
object LensMessages {

    fun guidance(setup: LensController.Setup, fit: BoardFinder.Fit?, ai: Boolean): String = when {
        setup == LensController.Setup.OFF -> ""
        setup == LensController.Setup.READY -> "Bereit – wirf!"
        setup == LensController.Setup.FOUND -> "Board erkannt ✓ – kurz ruhig halten"
        fit == null -> if (ai) "Suche Board … ganzes Board ins Bild, gutes Licht" else "Board nicht gefunden: ganzes Board ins Bild, mehr Licht"
        else -> when (fit.quality) {
            BoardFinder.Quality.GOOD -> {
                val ratio = if (fit.ellipse.a > 0) minOf(fit.ellipse.a, fit.ellipse.b) / maxOf(fit.ellipse.a, fit.ellipse.b) else 0.85
                if (ratio > 0.97) "Board erkannt ✓ – etwas mehr von der Seite ist besser" else "Board erkannt ✓"
            }
            BoardFinder.Quality.PARTIAL -> "Das ganze Board muss sichtbar sein – weiter zurück"
            BoardFinder.Quality.TOO_SMALL -> "Näher ans Board"
            BoardFinder.Quality.TOO_SKEWED -> "Mehr von vorn – weniger schräg"
            BoardFinder.Quality.INACCURATE -> "Ruhig halten, Licht gleichmäßiger"
            BoardFinder.Quality.NOT_FOUND -> "Board nicht gefunden: ganzes Board ins Bild, mehr Licht"
        }
    }

    fun status(running: Boolean, setup: LensController.Setup, paused: Boolean, checking: Boolean, phase: DartDetector.Phase, darts: Int): String = when {
        !running -> "Kamera aus"
        setup == LensController.Setup.SEARCHING -> "Suche Board …"
        setup == LensController.Setup.FOUND -> "Kalibriert – warte auf Ruhe"
        paused -> "Pausiert"
        checking -> "Prüfe Dart …"
        phase == DartDetector.Phase.TAKEOUT -> "Darts entfernen"
        phase == DartDetector.Phase.MOTION -> "Bewegung …"
        else -> "Bereit · $darts Dart(s)"
    }
}
