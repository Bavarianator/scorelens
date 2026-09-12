package com.freedarts.scorer.data

import android.content.Context
import android.util.Log
import com.freedarts.scorer.model.AppSettings
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Tournament
import com.freedarts.scorer.online.OnlineSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.io.File

/**
 * Einfache, lokale Persistenz als JSON-Dateien im App-Speicher.
 * Alles bleibt auf dem Gerät; nur der optionale Online-Modus (Supabase) hält zusätzlich eine Sitzung.
 */
class Repository private constructor(context: Context) {

    private val dir: File = context.filesDir
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    // Ein Schreiber: zwei gleichzeitige save() derselben Datei würden sich sonst in der .tmp überschreiben
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _players = MutableStateFlow(loadList("players.json", Player.serializer()) ?: defaultPlayers())
    val players: StateFlow<List<Player>> = _players

    private val _settings = MutableStateFlow(load("settings.json", AppSettings.serializer()) ?: AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _matches = MutableStateFlow(loadList("matches.json", MatchRecord.serializer()) ?: emptyList())
    val matches: StateFlow<List<MatchRecord>> = _matches

    /** Laufendes lokales Turnier; null = keins. */
    private val _tournament = MutableStateFlow(load("tournament.json", Tournament.serializer()))
    val tournament: StateFlow<Tournament?> = _tournament

    /** Export: Spieler + Verlauf (ohne Einstellungen, die enthalten Schlüssel). */
    @kotlinx.serialization.Serializable
    data class Backup(val players: List<Player>, val matches: List<MatchRecord>, val exportedAt: Long = System.currentTimeMillis())
    fun exportJson(): String = json.encodeToString(Backup.serializer(), Backup(_players.value, _matches.value))

    fun setTournament(t: Tournament?) {
        _tournament.value = t
        if (t == null) scope.launch { File(dir, "tournament.json").delete() } else save("tournament.json", Tournament.serializer(), t)
    }

    /** Sitzung des Online-Modus (Supabase); null = abgemeldet. */
    private val _onlineSession = MutableStateFlow(load("online_session.json", OnlineSession.serializer()))
    val onlineSession: StateFlow<OnlineSession?> = _onlineSession

    fun setOnlineSession(session: OnlineSession?) {
        _onlineSession.value = session
        if (session == null) scope.launch { File(dir, "online_session.json").delete() }
        else save("online_session.json", OnlineSession.serializer(), session)
    }

    private fun defaultPlayers() = listOf(Player(name = "Spieler 1", color = Player.AVATAR_COLORS[0]))

    fun addPlayer(player: Player) { _players.update { it + player }; persistPlayers() }
    fun updatePlayer(player: Player) { _players.update { list -> list.map { if (it.id == player.id) player else it } }; persistPlayers() }
    fun removePlayer(id: String) { _players.update { list -> list.filter { it.id != id } }; persistPlayers() }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        save("settings.json", AppSettings.serializer(), _settings.value)
    }

    fun addMatch(record: MatchRecord) {
        _matches.update { (it + record).takeLast(500) }
        save("matches.json", ListSerializer(MatchRecord.serializer()), _matches.value)
    }

    /** Aus der Cloud geladene Matches ergänzen (nur unbekannte IDs), nach Endzeit sortiert. */
    fun mergeMatches(records: List<MatchRecord>) {
        val known = _matches.value.map { it.id }.toSet()
        val fresh = records.filter { it.id !in known }
        if (fresh.isEmpty()) return
        _matches.update { (it + fresh).sortedBy { m -> m.finishedAt }.takeLast(500) }
        save("matches.json", ListSerializer(MatchRecord.serializer()), _matches.value)
    }

    fun clearMatches() {
        _matches.value = emptyList()
        save("matches.json", ListSerializer(MatchRecord.serializer()), emptyList())
    }

    private fun persistPlayers() = save("players.json", ListSerializer(Player.serializer()), _players.value)

    private fun <T> load(name: String, serializer: kotlinx.serialization.KSerializer<T>): T? = try {
        val f = File(dir, name)
        if (f.exists()) json.decodeFromString(serializer, f.readText()) else null
    } catch (e: Exception) { Log.w(TAG, "$name unlesbar", e); null }

    /** Liste laden; ein einzelner unlesbarer Eintrag (z. B. altes Format) verwirft nicht die ganze Datei. */
    private fun <T> loadList(name: String, serializer: kotlinx.serialization.KSerializer<T>): List<T>? = try {
        val f = File(dir, name)
        if (!f.exists()) null else json.parseToJsonElement(f.readText()).jsonArray.mapNotNull { el ->
            runCatching { json.decodeFromJsonElement(serializer, el) }.onFailure { Log.w(TAG, "$name: Eintrag übersprungen", it) }.getOrNull()
        }
    } catch (e: Exception) { Log.w(TAG, "$name unlesbar", e); null }

    private fun <T> save(name: String, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        val text = json.encodeToString(serializer, value)
        scope.launch {
            val f = File(dir, name)
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        }
    }

    companion object {
        private const val TAG = "Repository"
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }
}
