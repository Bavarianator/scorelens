package com.freedarts.scorer.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** ARGB-Farbe für Avatar. */
    val color: Long = 0xFF3F51B5,
    /** 0 = Mensch, 1..11 = Bot-Stufe (wie bei Autodarts elf Stufen), [ADAPTIVE] = passt sich dem Spieler an. */
    val botLevel: Int = 0,
    /** Profilbild als Base64-JPEG (max. 128 px), null = Initialen. */
    val avatar: String? = null,
) {
    val isBot: Boolean get() = botLevel > 0
    val initials: String get() = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

    companion object {
        /** Bot „Wie ich“: startet auf dem eigenen Average und wird nach jedem Leg stärker oder schwächer. */
        const val ADAPTIVE = 12

        val AVATAR_COLORS = listOf(
            0xFF3F51B5, 0xFFE53935, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA,
            0xFF00ACC1, 0xFFD81B60, 0xFF6D4C41, 0xFF039BE5, 0xFF7CB342,
        )

        /** [n] > 1: weiterer Bot derselben Stufe in einer Lobby (eigene ID, Statistik des ersten bleibt unter bot-<Stufe>). */
        fun bot(level: Int, n: Int = 1): Player = Player(
            id = if (n == 1) "bot-$level" else "bot-$level-$n",
            name = (if (level == ADAPTIVE) "Bot: Wie ich" else "Bot Stufe $level") + (if (n > 1) " #$n" else ""),
            color = 0xFF546E7A,
            botLevel = level,
        )

        /** Ungefährer 3-Dart-Average der Bot-Stufen (an Autodarts angelehnt); 0 für [ADAPTIVE]. */
        fun botAverage(level: Int): Int = if (level == ADAPTIVE) 0 else listOf(0, 25, 32, 40, 47, 55, 62, 70, 78, 86, 95, 105)[level.coerceIn(0, 11)]
    }
}
