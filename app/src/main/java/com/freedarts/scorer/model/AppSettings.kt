package com.freedarts.scorer.model

import kotlinx.serialization.Serializable

@Serializable
enum class InputMethod { BOARD, TOTAL, DART_BY_DART }

@Serializable
data class AppSettings(
    val callerEnabled: Boolean = true,
    val callerCallsEveryVisit: Boolean = true,
    val soundEffects: Boolean = true,
    val showChalkboard: Boolean = true,
    val showCheckoutGuide: Boolean = true,
    val countEachThrow: Boolean = false,
    val inputMethod: InputMethod = InputMethod.BOARD,
    val keepScreenOn: Boolean = true,
    val botDelayMillis: Long = 700,

    // Autodarts Board Manager (lokal, Port 3180)
    val boardManagerEnabled: Boolean = false,
    val boardManagerHost: String = "192.168.178.100",
    val boardManagerPort: Int = 3180,

    // Lens (Handykamera-Autoscoring)
    val lensEnabled: Boolean = false,
    /** 4 Kalibrierpunkte als normierte Bildkoordinaten (x0,y0,…,x3,y3), 0..1 des aufrechten Kamerabilds. */
    val lensCalibration: List<Float> = emptyList(),
    /** Empfindlichkeit 0..100 (höher = kleinere Änderungen erkennen). */
    val lensSensitivity: Int = 50,
    val lensUseFrontCamera: Boolean = false,

    /** Remote Scoring: Spielansicht im Browser eines zweiten Geräts (Port 8765). */
    val remoteEnabled: Boolean = false,

    /** Spieler, dessen Statistiken auf dem Dashboard erscheinen (Profil). */
    val profilePlayerId: String? = null,

    /** Zuletzt verwendete Lobby (für "Sofort spielen"). */
    val lastGameSettings: GameSettings = GameSettings(),
    val lastPlayerIds: List<String> = emptyList(),
)
