package com.freedarts.scorer.online

import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Angemeldete Sitzung (Supabase Auth / GoTrue). Wird lokal gespeichert und per Refresh-Token verlängert. */
@Serializable
data class OnlineSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    /** Ablauf als Unix-Sekunden. */
    @SerialName("expires_at") val expiresAt: Long = 0,
    val user: SessionUser = SessionUser(),
) {
    val userId: String get() = user.id
    fun expiresSoon(now: Long = System.currentTimeMillis() / 1000): Boolean = expiresAt > 0 && expiresAt - now < 90
}

@Serializable
data class SessionUser(
    val id: String = "",
    val email: String? = null,
    @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    @SerialName("app_metadata") val appMetadata: AppMetadata = AppMetadata(),
) {
    val provider: String get() = appMetadata.provider ?: if (isAnonymous) "anonymous" else "email"
}

@Serializable
data class AppMetadata(val provider: String? = null)

/** Spielerprofil (Tabelle profiles); Kennzahlen aus abgeschlossenen Online-Matches. */
@Serializable
data class Profile(
    val id: String,
    val name: String = "",
    val color: Long = 0xFF3F51B5,
    val avg: Double = 0.0,
    val matches: Int = 0,
    val wins: Int = 0,
    val avatar: String? = null,
) {
    val winRate: Double get() = if (matches == 0) 0.0 else 100.0 * wins / matches
}

/** Online-Lobby (Tabelle lobbies) mit eingebettetem Host und Spielern. */
@Serializable
data class Lobby(
    val id: String,
    val code: String,
    @SerialName("host_id") val hostId: String,
    val name: String = "",
    val settings: GameSettings = GameSettings(),
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("max_players") val maxPlayers: Int = 2,
    val status: String = "open",
    @SerialName("current_match_id") val currentMatchId: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    val host: Profile? = null,
    val players: List<LobbyPlayer> = emptyList(),
    /** Laufendes Turnier der Lobby (Host schreibt, alle lesen); null = keins. */
    val tournament: com.freedarts.scorer.model.Tournament? = null,
) {
    val sortedPlayers: List<LobbyPlayer> get() = players.sortedWith(compareBy({ it.position }, { it.joinedAt }))
    val isFull: Boolean get() = players.size >= maxPlayers
}

@Serializable
data class LobbyPlayer(
    @SerialName("lobby_id") val lobbyId: String,
    @SerialName("user_id") val userId: String,
    val position: Int = 0,
    val ready: Boolean = false,
    @SerialName("joined_at") val joinedAt: String = "",
    val profile: Profile? = null,
) {
    val name: String get() = profile?.name ?: "Spieler"
    val color: Long get() = profile?.color ?: 0xFF3F51B5
    val avatar: String? get() = profile?.avatar
}

/** Spieler eines Matches in Wurfreihenfolge (jsonb-Spalte matches.players). */
@Serializable
data class MatchPlayer(val id: String, val name: String, val color: Long = 0xFF3F51B5, val avatar: String? = null)

/** Ein Spiel einer Lobby (Tabelle matches). Seed + Einstellungen + Spieler ergeben auf allen Geräten dieselbe Engine. */
@Serializable
data class OnlineMatch(
    val id: String,
    @SerialName("lobby_id") val lobbyId: String,
    val seed: Long,
    val settings: GameSettings = GameSettings(),
    val players: List<MatchPlayer> = emptyList(),
    val status: String = "running",
    @SerialName("winner_id") val winnerId: String? = null,
)

/**
 * Ereignis im Wurfprotokoll eines Online-Matches (Tabelle match_events). Jeder Client spielt die Ereignisse in
 * seq-Reihenfolge in seine lokale Spiel-Engine ein; [client] erkennt das eigene Echo.
 */
@Serializable
data class MatchEvent(
    @SerialName("match_id") val matchId: String,
    val seq: Int,
    @SerialName("user_id") val userId: String,
    /** throw | next | undo */
    val kind: String,
    val number: Int? = null,
    val multiplier: Int? = null,
    val x: Float? = null,
    val y: Float? = null,
    val hold: Boolean = false,
    val at: Long = 0,
    val client: String = "",
) {
    val segment: Segment? get() = if (kind == KIND_THROW && number != null && multiplier != null) Segment(number, multiplier) else null

    companion object {
        const val KIND_THROW = "throw"
        const val KIND_NEXT = "next"
        const val KIND_UNDO = "undo"
    }
}

/** Freund aus der RPC friends(): Profil, Status der Anfrage, Kopf-an-Kopf-Bilanz und offene Lobby des Freundes. */
@Serializable
data class Friend(
    val id: String,
    val name: String = "",
    val color: Long = 0xFF3F51B5,
    val avatar: String? = null,
    val avg: Double = 0.0,
    val matches: Int = 0,
    val wins: Int = 0,
    /** pending | accepted */
    val status: String = "pending",
    /** Anfrage kam vom Freund (wartet auf meine Antwort). */
    val incoming: Boolean = false,
    /** Gemeinsame abgeschlossene Online-Matches und meine Siege daraus. */
    val played: Int = 0,
    val won: Int = 0,
    @SerialName("lobby_code") val lobbyCode: String? = null,
    /** Laufendes Online-Match des Freundes (zum Zuschauen), null = spielt gerade nicht. */
    @SerialName("match_id") val matchId: String? = null,
) {
    val accepted: Boolean get() = status == "accepted"
    val winRate: Double get() = if (matches == 0) 0.0 else 100.0 * wins / matches
    fun player(): Player = Player(id = id, name = name, color = color, avatar = avatar)
}

/** Einladung eines Freundes in seine Lobby (Tabelle invites). */
@Serializable
data class Invite(
    @SerialName("lobby_id") val lobbyId: String,
    @SerialName("from_id") val fromId: String,
    @SerialName("to_id") val toId: String,
    val code: String,
)
