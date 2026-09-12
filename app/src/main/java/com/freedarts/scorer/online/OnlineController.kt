package com.freedarts.scorer.online

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.freedarts.scorer.data.Repository
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.PlayerMatchStats
import com.freedarts.scorer.model.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Online-Modus wie bei Autodarts auf Basis von Supabase: Konto (E-Mail, Supabase OAuth, Gast), Profil, Lobbys
 * (öffentlich oder per Code), "Gegner finden" und synchronisierte Matches.
 *
 * Match-Synchronisation: Alle Geräte bauen aus Seed, Einstellungen und Spielerliste dieselbe Spiel-Engine und
 * spielen das Ereignisprotokoll (match_events: throw / next / undo) in seq-Reihenfolge ein. Eigene Ereignisse
 * werden sofort lokal angewendet und dann eingefügt; das Echo über Realtime bestätigt sie. Bei Lücken oder
 * Konflikten (jemand anderes war schneller) wird das Protokoll neu geladen und die Engine neu aufgebaut.
 */
class OnlineController(private val context: Context, private val repo: Repository, private val scope: CoroutineScope) {

    enum class Connection { OFF, CONNECTING, ONLINE, RETRYING }

    val session: StateFlow<OnlineSession?> = repo.onlineSession
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    /** Letzte Fehlermeldung für die Oberfläche (null = keine). */
    val error = MutableStateFlow<String?>(null)
    /** Kurze Hinweise ("Host hat das Match abgebrochen"). */
    val notice = MutableStateFlow<String?>(null)

    private val _lobbies = MutableStateFlow<List<Lobby>>(emptyList())
    val lobbies: StateFlow<List<Lobby>> = _lobbies
    private val _lobby = MutableStateFlow<Lobby?>(null)
    val lobby: StateFlow<Lobby?> = _lobby
    private val _match = MutableStateFlow<OnlineMatch?>(null)
    val match: StateFlow<OnlineMatch?> = _match
    /** Spieler-IDs, die gerade im Match-Kanal anwesend sind (Presence). */
    private val _presence = MutableStateFlow<Set<String>>(emptySet())
    val presence: StateFlow<Set<String>> = _presence

    private val realtime = RealtimeClient(scope)
    val connection: StateFlow<RealtimeClient.State> = realtime.state

    private var api: SupabaseApi? = null
    val configured: Boolean get() = api != null
    val myId: String? get() = session.value?.userId
    val isHost: Boolean get() = _lobby.value?.hostId != null && _lobby.value?.hostId == myId

    /** Zufällige Kennung dieser App-Instanz: erkennt das eigene Echo im Ereignisprotokoll. */
    val clientId: String = UUID.randomUUID().toString().substring(0, 8)

    // Ereignisprotokoll des laufenden Matches
    private val eventLock = Mutex()
    private var serverSeq = 0
    private var pending = 0
    private var lastEventUser: String? = null
    private var lastEventKind: String? = null

    /** Match beginnt (neu oder nach Beitritt): Engine aufbauen und Ereignisse einspielen. */
    var onMatchStarted: ((OnlineMatch, List<MatchEvent>) -> Unit)? = null
    /** Ereignis eines anderen Spielers. */
    var onRemoteEvent: ((MatchEvent) -> Unit)? = null
    /** Protokoll neu geladen: Engine neu aufbauen. */
    var onResync: ((OnlineMatch, List<MatchEvent>) -> Unit)? = null
    /** Match wurde von einem anderen Gerät beendet oder abgebrochen. */
    var onMatchEnded: ((OnlineMatch, aborted: Boolean) -> Unit)? = null
    /** Lobby existiert nicht mehr (Host hat sie verlassen). */
    var onLobbyClosed: (() -> Unit)? = null

    private var lobbyTopic: String? = null
    private var matchTopic: String? = null
    private var refreshJob: Job? = null

    // ---------- Konfiguration und Konto ----------

    /** Server setzen (supabase.com-Projekt oder eigener Stack). Bestehende Sitzung wird weiterverwendet. */
    fun configure(url: String, anonKey: String) {
        val u = url.trim().trimEnd('/'); val k = anonKey.trim()
        if (u.isBlank() || k.isBlank()) { api = null; realtime.disconnect(); return }
        if (api?.baseUrl == u && api?.anonKey == k) return
        if (session.value?.user?.isAnonymous == true) setSession(null) // alte Gastsitzung aus früheren Versionen
        api = SupabaseApi(u, k).also { it.accessToken = session.value?.accessToken }
        realtime.disconnect()
        if (session.value != null) scope.launch { runCatching { ensureFresh(); loadProfile(); connectRealtime(); connectUserChannel() } }
    }

    private fun requireApi(): SupabaseApi = api ?: throw OnlineException(0, "Kein Server eingetragen (Supabase-URL und Anon-Key).")

    private suspend fun <T> guarded(block: suspend () -> T): T? {
        _busy.value = true
        return try { block() } catch (e: Exception) { error.value = e.message ?: e.toString(); null } finally { _busy.value = false }
    }

    private fun setSession(s: OnlineSession?) {
        repo.setOnlineSession(s)
        api?.accessToken = s?.accessToken
        realtime.setAccessToken(s?.accessToken)
    }

    /** Token vor Ablauf erneuern; bei ungültigem Refresh-Token wird abgemeldet. */
    private suspend fun ensureFresh() {
        val s = session.value ?: return
        if (!s.expiresSoon()) return
        val a = requireApi()
        try { setSession(a.refresh(s.refreshToken)) } catch (e: OnlineException) { if (e.status in 400..401) setSession(null); throw e }
    }

    private suspend fun afterLogin(s: OnlineSession) {
        // Nur per OAuth registrierte Konten (Google/GitHub); Gastsitzungen werden verworfen
        if (s.user.isAnonymous) { setSession(null); throw OnlineException(0, "Bitte mit Google oder GitHub anmelden.") }
        setSession(s)
        loadProfile()
        connectRealtime()
        connectUserChannel()
    }

    fun signOut() = scope.launch {
        leaveLobby()
        myId?.let { realtime.unsubscribe("realtime:user:$it") }
        realtime.unsubscribe(ONLINE_TOPIC)
        pushToken?.let { t -> runCatching { ensureFresh(); requireApi().delete("push_tokens", "user_id=eq.$myId&token=eq.${SupabaseApi.enc(t)}") } }
        runCatching { requireApi().signOut() }
        setSession(null); _profile.value = null; _lobbies.value = emptyList(); _friends.value = emptyList(); invite.value = null; _online.value = emptySet()
        realtime.disconnect()
    }

    /** Supabase OAuth im Browser (PKCE). Der Anbieter leitet auf scorelens://auth/callback zurück ([handleRedirect]). */
    fun beginOAuth(provider: String): Boolean {
        val a = api ?: run { error.value = "Kein Server eingetragen."; return false }
        error.value = null
        val verifier = randomString(64)
        repo.updateSettings { it.copy(onlinePkceVerifier = verifier) }
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val url = a.authorizeUrl(provider, REDIRECT_URI, challenge)
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { error.value = "Kein Browser gefunden" }.isSuccess
    }

    /** Rückkehr aus dem Browser: Code (PKCE) oder Token (Implicit Flow) auswerten. */
    fun handleRedirect(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "scorelens") return false
        if (uri.host == "friend") { friendIdFrom(uri.toString())?.let { addFriend(it) }; return true }
        val code = uri.getQueryParameter("code")
        val fragment = uri.fragment?.split("&")?.mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to Uri.decode(it[1]) } }?.toMap().orEmpty()
        val errorDescription = uri.getQueryParameter("error_description") ?: fragment["error_description"]
        scope.launch {
            guarded {
                if (errorDescription != null) throw OnlineException(0, errorDescription)
                val a = requireApi()
                val s = when {
                    code != null -> {
                        val verifier = repo.settings.value.onlinePkceVerifier
                        if (verifier.isBlank()) throw OnlineException(0, "Anmeldung abgelaufen – bitte erneut versuchen.")
                        a.exchangeCode(code, verifier)
                    }
                    fragment["access_token"] != null -> OnlineSession(
                        accessToken = fragment.getValue("access_token"),
                        refreshToken = fragment["refresh_token"] ?: "",
                        expiresAt = fragment["expires_at"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 + (fragment["expires_in"]?.toLongOrNull() ?: 3600)),
                    ).let { s -> s.copy(user = fetchUser(s.accessToken) ?: s.user) }
                    else -> throw OnlineException(0, "Ungültige Rückleitung")
                }
                repo.updateSettings { it.copy(onlinePkceVerifier = "") }
                afterLogin(s)
            }
        }
        return true
    }

    private suspend fun fetchUser(token: String): SessionUser? {
        val a = requireApi()
        val old = a.accessToken
        a.accessToken = token
        return try { a.user() } catch (_: Exception) { null } finally { a.accessToken = old }
    }

    /** Top-Liste nach Online-Average (View leaderboard); Spalten passen auf [Profile]. */
    val leaderboard = MutableStateFlow<List<Profile>>(emptyList())

    fun loadLeaderboard() = scope.launch {
        runCatching {
            ensureFresh()
            leaderboard.value = SupabaseApi.json.decodeFromString(ListSerializer(Profile.serializer()), requireApi().select("leaderboard", "select=*&limit=25"))
        }
    }

    suspend fun loadProfile() {
        val id = myId ?: return
        val text = requireApi().select("profiles", "id=eq.$id&select=*")
        _profile.value = SupabaseApi.json.decodeFromString(ListSerializer(Profile.serializer()), text).firstOrNull()
    }

    fun updateProfile(name: String, color: Long) = scope.launch {
        guarded {
            ensureFresh()
            val id = myId ?: return@guarded
            val body = buildJsonObject { put("name", name.trim().take(32)); put("color", color) }.toString()
            requireApi().update("profiles", "id=eq.$id", body)
            runCatching { requireApi().updateUserName(name) }
            loadProfile()
        }
    }

    private fun connectRealtime() {
        val a = api ?: return
        realtime.connect(a.realtimeUrl, session.value?.accessToken)
    }

    // ---------- Lobbys ----------

    // profiles!host_id: lobbies→profiles gibt es direkt (host_id) und über lobby_players – PostgREST braucht die Angabe
    private val lobbySelect = "select=*,host:profiles!host_id(*),players:lobby_players(*,profile:profiles(*))"

    fun refreshLobbies() = scope.launch {
        guarded {
            ensureFresh()
            val text = requireApi().select("lobbies", "$lobbySelect&status=in.(open,running)&is_public=eq.true&order=created_at.desc&limit=50")
            _lobbies.value = SupabaseApi.json.decodeFromString(ListSerializer(Lobby.serializer()), text)
        }
    }

    fun createLobby(settings: GameSettings, isPublic: Boolean, maxPlayers: Int, name: String = "") = scope.launch {
        guarded {
            ensureFresh()
            val args = buildJsonObject {
                put("p_settings", SupabaseApi.json.encodeToJsonElement(GameSettings.serializer(), settings))
                put("p_public", isPublic); put("p_max_players", maxPlayers); put("p_name", name)
            }
            val id = requireApi().rpc("create_lobby", args).trim('"', ' ', '\n')
            enterLobby(id)
        }
    }

    fun joinByCode(code: String) = scope.launch {
        guarded {
            ensureFresh()
            val id = requireApi().rpc("join_lobby", buildJsonObject { put("p_code", code.trim().uppercase()) }).trim('"', ' ', '\n')
            enterLobby(id)
        }
    }

    /** "Gegner finden": passende offene Lobby beitreten, sonst selbst eine öffentliche Lobby aufmachen. */
    fun quickMatch(settings: GameSettings) = scope.launch {
        guarded {
            ensureFresh()
            val res = requireApi().rpc("quick_match", buildJsonObject { put("p_mode", settings.mode.name) }).trim(' ', '\n')
            val id = res.trim('"').takeIf { res != "null" && it.isNotBlank() }
            if (id != null) enterLobby(id)
            else {
                val args = buildJsonObject {
                    put("p_settings", SupabaseApi.json.encodeToJsonElement(GameSettings.serializer(), settings))
                    put("p_public", true); put("p_max_players", 2); put("p_name", "Gegner gesucht")
                }
                enterLobby(requireApi().rpc("create_lobby", args).trim('"', ' ', '\n'))
                notice.value = "Warte auf einen Gegner …"
            }
        }
    }

    private suspend fun fetchLobby(id: String): Lobby? {
        val text = requireApi().select("lobbies", "id=eq.$id&$lobbySelect")
        return SupabaseApi.json.decodeFromString(ListSerializer(Lobby.serializer()), text).firstOrNull()
    }

    private suspend fun enterLobby(id: String) {
        spectating = false
        leaveLobbyChannel()
        val l = fetchLobby(id) ?: throw OnlineException(404, "Lobby nicht gefunden")
        _lobby.value = l
        val topic = "realtime:lobby:$id"
        lobbyTopic = topic
        val changes = listOf(
            pgChange("*", "lobbies", "id=eq.$id"),
            pgChange("*", "lobby_players", "lobby_id=eq.$id"),
        )
        realtime.subscribe(topic, changes, presenceKey = myId, onMessage = { event, payload -> onLobbyMessage(event, payload) },
            onJoined = { scope.launch { refreshLobby() } })
        connectRealtime()
        if (l.status == "running" && l.currentMatchId != null) joinMatch(l.currentMatchId)
    }

    private fun onLobbyMessage(event: String, payload: JsonObject) {
        if (event != "postgres_changes") return
        val data = payload["data"]?.jsonObject ?: return
        val table = data["table"]?.jsonPrimitive?.contentOrNull
        val type = data["type"]?.jsonPrimitive?.contentOrNull
        if (table == "lobbies" && type == "DELETE") {
            scope.launch { closeLobbyLocally(); onLobbyClosed?.invoke() }
            return
        }
        scope.launch { refreshLobby() }
    }

    /** Lobby neu laden (nach jeder Realtime-Änderung) und auf Matchstart / Matchende reagieren. */
    private suspend fun refreshLobby() {
        val id = _lobby.value?.id ?: return
        val l = runCatching { fetchLobby(id) }.getOrNull()
        if (l == null) { closeLobbyLocally(); onLobbyClosed?.invoke(); return }
        val me = myId
        if (me != null && l.players.none { it.userId == me }) {
            closeLobbyLocally(); notice.value = "Du wurdest aus der Lobby entfernt"; onLobbyClosed?.invoke(); return
        }
        val before = _lobby.value
        _lobby.value = l
        val current = _match.value
        if (l.status == "running" && l.currentMatchId != null && l.currentMatchId != current?.id) joinMatch(l.currentMatchId)
        else if (l.status != "running" && current != null && before?.status == "running") {
            // Match wurde beendet oder abgebrochen (von einem anderen Gerät)
            val m = runCatching { fetchMatch(current.id) }.getOrNull()
            leaveMatchChannel()
            _match.value = null
            onMatchEnded?.invoke(m ?: current, m?.status != "finished")
        }
    }

    private fun leaveLobbyChannel() {
        leaveMatchChannel()
        lobbyTopic?.let { realtime.unsubscribe(it) }
        lobbyTopic = null
    }

    private fun closeLobbyLocally() {
        leaveLobbyChannel()
        _lobby.value = null
        _match.value = null
    }

    fun leaveLobby() = scope.launch {
        val id = _lobby.value?.id ?: return@launch
        closeLobbyLocally()
        runCatching { ensureFresh(); requireApi().rpc("leave_lobby", buildJsonObject { put("p_lobby", id) }) }
    }

    fun setReady(ready: Boolean) = scope.launch {
        val l = _lobby.value ?: return@launch
        val me = myId ?: return@launch
        guarded { ensureFresh(); requireApi().update("lobby_players", "lobby_id=eq.${l.id}&user_id=eq.$me", buildJsonObject { put("ready", ready) }.toString()) }
    }

    /** Host: Einstellungen, Sichtbarkeit oder Spielerzahl ändern. */
    fun updateLobby(settings: GameSettings? = null, isPublic: Boolean? = null, maxPlayers: Int? = null) = scope.launch {
        val l = _lobby.value ?: return@launch
        if (!isHost) return@launch
        guarded {
            ensureFresh()
            val body = buildJsonObject {
                settings?.let { put("settings", SupabaseApi.json.encodeToJsonElement(GameSettings.serializer(), it)) }
                isPublic?.let { put("is_public", it) }
                maxPlayers?.let { put("max_players", it) }
            }.toString()
            requireApi().update("lobbies", "id=eq.${l.id}", body)
            refreshLobby()
        }
    }

    /** Host: Turnier im Lobby-Datensatz setzen oder löschen; alle Clients sehen es über den Lobby-Kanal. */
    fun setTournament(t: com.freedarts.scorer.model.Tournament?) = scope.launch {
        val l = _lobby.value ?: return@launch
        if (!isHost) return@launch
        guarded {
            ensureFresh()
            val body = buildJsonObject { put("tournament", t?.let { SupabaseApi.json.encodeToJsonElement(com.freedarts.scorer.model.Tournament.serializer(), it) } ?: kotlinx.serialization.json.JsonNull) }.toString()
            requireApi().update("lobbies", "id=eq.${l.id}", body)
            refreshLobby()
        }
    }

    fun kick(userId: String) = scope.launch {
        val l = _lobby.value ?: return@launch
        if (!isHost || userId == myId) return@launch
        guarded { ensureFresh(); requireApi().delete("lobby_players", "lobby_id=eq.${l.id}&user_id=eq.$userId") }
    }

    /** Host: Match starten. Spielerreihenfolge = Lobby-Reihenfolge, oder [userIds] (Turnierspiel: nur die beiden). */
    fun startMatch(userIds: List<String>? = null) = scope.launch {
        val l = _lobby.value ?: return@launch
        if (!isHost) return@launch
        guarded {
            ensureFresh()
            val chosen = userIds?.mapNotNull { id -> l.players.firstOrNull { it.userId == id } } ?: l.sortedPlayers
            val players = chosen.map { MatchPlayer(it.userId, it.name, it.color, it.avatar) }
            if (players.size < 2) throw OnlineException(0, "Mindestens zwei Spieler")
            val args = buildJsonObject {
                put("p_lobby", l.id)
                put("p_seed", SecureRandom().nextLong() and Long.MAX_VALUE)
                put("p_settings", SupabaseApi.json.encodeToJsonElement(GameSettings.serializer(), l.settings))
                put("p_players", buildJsonArray { players.forEach { add(SupabaseApi.json.encodeToJsonElement(MatchPlayer.serializer(), it)) } })
            }
            val id = requireApi().rpc("start_match", args).trim('"', ' ', '\n')
            joinMatch(id)
        }
    }

    // ---------- Match ----------

    private suspend fun fetchMatch(id: String): OnlineMatch? {
        val text = requireApi().select("matches", "id=eq.$id&select=*")
        return SupabaseApi.json.decodeFromString(ListSerializer(OnlineMatch.serializer()), text).firstOrNull()
    }

    private suspend fun fetchEvents(matchId: String): List<MatchEvent> {
        val text = requireApi().select("match_events", "match_id=eq.$matchId&select=*&order=seq.asc&limit=5000")
        return SupabaseApi.json.decodeFromString(ListSerializer(MatchEvent.serializer()), text)
    }

    private suspend fun joinMatch(id: String) {
        if (_match.value?.id == id) return
        leaveMatchChannel()
        val m = fetchMatch(id) ?: throw OnlineException(404, "Match nicht gefunden")
        if (m.status != "running") return
        val events = fetchEvents(id)
        eventLock.withLock { serverSeq = events.lastOrNull()?.seq ?: 0; pending = 0; noteLast(events.lastOrNull()) }
        _match.value = m
        _presence.value = emptySet()
        val topic = "realtime:match:$id"
        matchTopic = topic
        realtime.subscribe(topic, listOf(pgChange("INSERT", "match_events", "match_id=eq.$id")), presenceKey = myId,
            onMessage = { event, payload -> onMatchMessage(event, payload) },
            onJoined = { if (!spectating) myId?.let { realtime.track(topic, buildJsonObject { put("uid", it) }) }; scope.launch { resync() } })
        connectRealtime()
        onMatchStarted?.invoke(m, events)
    }

    // ---------- Zuschauen ----------

    /** true: Match-Kanal nur lesend (öffentliches Match eines anderen), keine Presence, kein Ergebnis, keine Ereignisse. */
    var spectating = false; private set

    /** Laufendes öffentliches Match live mitverfolgen (matches, match_events und Realtime sind für alle Angemeldeten lesbar). */
    // ponytail: Abbruch durch den Host wird nicht bemerkt (kein Lobby-Kanal) – der Zuschauer verlässt das Match selbst;
    //           bei Bedarf zusätzlich pgChange auf matches id=eq.<id> abonnieren
    fun spectate(matchId: String) = scope.launch {
        if (_lobby.value != null) { error.value = "Erst die eigene Lobby verlassen"; return@launch }
        guarded {
            ensureFresh(); spectating = true; joinMatch(matchId)
            if (_match.value == null) { spectating = false; notice.value = "Das Match ist schon vorbei"; refreshLobbies() }
        }
    }

    fun stopSpectating() { spectating = false; leaveMatchChannel(); _match.value = null }

    private fun leaveMatchChannel() {
        matchTopic?.let { realtime.unsubscribe(it) }
        matchTopic = null
        _presence.value = emptySet()
    }

    private fun noteLast(e: MatchEvent?) { lastEventUser = e?.userId; lastEventKind = e?.kind }

    private fun onMatchMessage(event: String, payload: JsonObject) {
        when (event) {
            "postgres_changes" -> {
                val record = payload["data"]?.jsonObject?.get("record")?.jsonObject ?: return
                val e = runCatching { SupabaseApi.json.decodeFromJsonElement(MatchEvent.serializer(), record) }.getOrNull() ?: return
                scope.launch { applyIncoming(e) }
            }
            "broadcast" -> if (payload["event"]?.jsonPrimitive?.contentOrNull == "snap") payload["payload"]?.jsonObject?.let { p ->
                val at = p["t"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return
                val jpg = p["jpg"]?.jsonPrimitive?.contentOrNull ?: return
                onSnapshot?.invoke(at, jpg)
            }
            else -> presenceUpdate(_presence, event, payload)
        }
    }

    /** Live-Ticker: Referee-Bild eines Darts (kleines JPEG, Base64) von einem Mitspieler; [Long] = Wurfzeitstempel (MatchEvent.at). */
    var onSnapshot: ((Long, String) -> Unit)? = null

    /** Eigenes Referee-Bild an alle im Match-Kanal senden (Broadcast, nicht in der Datenbank). */
    fun sendSnapshot(at: Long, jpegBase64: String) {
        val t = matchTopic?.takeIf { !spectating } ?: return
        realtime.broadcast(t, "snap", buildJsonObject { put("t", at); put("jpg", jpegBase64) })
    }

    /** presence_state / presence_diff auf eine Menge anwesender Presence-Schlüssel (Nutzer-IDs) anwenden. */
    private fun presenceUpdate(target: MutableStateFlow<Set<String>>, event: String, payload: JsonObject) {
        when (event) {
            "presence_state" -> target.value = payload.keys.toSet()
            "presence_diff" -> target.value = target.value + payload["joins"]?.jsonObject?.keys.orEmpty() - payload["leaves"]?.jsonObject?.keys.orEmpty()
        }
    }

    private suspend fun applyIncoming(e: MatchEvent) {
        val m = _match.value ?: return
        if (e.matchId != m.id) return
        val action: (() -> Unit)? = eventLock.withLock {
            when {
                e.seq <= serverSeq -> null
                e.seq == serverSeq + 1 -> {
                    serverSeq = e.seq
                    noteLast(e)
                    if (e.client == clientId) { pending = (pending - 1).coerceAtLeast(0); null }
                    else ({ onRemoteEvent?.invoke(e); Unit })
                }
                else -> ({ scope.launch { resync() }; Unit })
            }
        }
        action?.invoke()
    }

    /** Protokoll vollständig neu laden und die Engine neu aufbauen. */
    private suspend fun resync() {
        val m = _match.value ?: return
        val events = runCatching { fetchEvents(m.id) }.getOrElse { error.value = it.message; return }
        eventLock.withLock { serverSeq = events.lastOrNull()?.seq ?: 0; pending = 0; noteLast(events.lastOrNull()) }
        onResync?.invoke(m, events)
    }

    /** Eigenes Ereignis (bereits lokal angewendet) ins Protokoll schreiben. */
    fun sendEvent(kind: String, segment: Segment? = null, x: Float? = null, y: Float? = null, hold: Boolean = false, at: Long = 0L) {
        val m = _match.value?.takeIf { !spectating } ?: return
        val me = myId ?: return
        scope.launch {
            val seq = eventLock.withLock { pending++; serverSeq + pending }
            val e = MatchEvent(m.id, seq, me, kind, segment?.number, segment?.multiplier, x, y, hold, at, clientId)
            eventLock.withLock { lastEventUser = me; lastEventKind = kind }
            try {
                ensureFresh()
                requireApi().insert("match_events", SupabaseApi.json.encodeToString(MatchEvent.serializer(), e), returning = false)
            } catch (ex: Exception) {
                // Konflikt (jemand anderes war schneller) oder Netzfehler: Stand vom Server übernehmen
                if (ex !is OnlineException || ex.status != 409) error.value = ex.message
                resync()
            }
        }
    }

    /** Undo ist nur für das eigene letzte Ereignis erlaubt. */
    fun canUndo(): Boolean = lastEventUser != null && lastEventUser == myId && lastEventKind != MatchEvent.KIND_UNDO

    fun finishMatch(winnerId: String?, stats: List<PlayerMatchStats>) = scope.launch {
        val m = _match.value ?: return@launch
        runCatching {
            ensureFresh()
            val args = buildJsonObject {
                put("p_match", m.id)
                put("p_winner", winnerId?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("p_stats", SupabaseApi.json.encodeToJsonElement(ListSerializer(PlayerMatchStats.serializer()), stats))
            }
            requireApi().rpc("finish_match", args)
            loadProfile()
        }.onFailure { error.value = it.message }
        leaveMatchChannel()
        _match.value = null
    }

    fun abortMatch() = scope.launch {
        val m = _match.value ?: return@launch
        leaveMatchChannel()
        _match.value = null
        runCatching { ensureFresh(); requireApi().rpc("abort_match", buildJsonObject { put("p_match", m.id) }) }.onFailure { error.value = it.message }
    }

    // ---------- Profilbild, Freunde und Einladungen ----------

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends
    /** Offene Einladung eines Freundes in seine Lobby (kommt per Realtime); null = keine. */
    val invite = MutableStateFlow<Invite?>(null)

    /** Inhalt des eigenen QR-Codes; wird von [friendIdFrom] wieder gelesen. */
    val friendLink: String? get() = myId?.let { "$FRIEND_LINK$it" }
    /** Teilbarer https-Link; der Server leitet in die App weiter (supabase/functions/friend bzw. selfhost/Caddyfile). */
    val friendShareUrl: String? get() = myId?.let { id -> api?.let { "${it.baseUrl}$FRIEND_PATH$id" } }
    /** Nutzer-IDs, die gerade angemeldet und verbunden sind (Presence im Kanal realtime:online). */
    private val _online = MutableStateFlow<Set<String>>(emptySet())
    val online: StateFlow<Set<String>> = _online
    private var pushToken: String? = null

    /** Profilbild setzen (Base64-JPEG aus [com.freedarts.scorer.ui.components.encodeAvatar]) oder mit null entfernen. */
    fun updateAvatar(base64: String?) = scope.launch {
        guarded {
            ensureFresh()
            val id = myId ?: return@guarded
            requireApi().update("profiles", "id=eq.$id", buildJsonObject { put("avatar", base64) }.toString())
            loadProfile()
        }
    }

    fun loadFriends() = scope.launch {
        runCatching {
            ensureFresh()
            _friends.value = SupabaseApi.json.decodeFromString(ListSerializer(Friend.serializer()), requireApi().rpc("friends", JsonObject(emptyMap())))
        }.onFailure { error.value = it.message }
    }

    /** Spieler nach Anzeigename suchen (Teilstring, ohne Groß/Klein). */
    suspend fun searchProfiles(query: String): List<Profile> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        ensureFresh()
        val text = requireApi().select("profiles", "select=*&id=neq.$myId&name=ilike.${SupabaseApi.enc("*$q*")}&order=name&limit=20")
        return SupabaseApi.json.decodeFromString(ListSerializer(Profile.serializer()), text)
    }

    /** Freundschaftsanfrage (Nutzer-ID aus QR-Code, Link oder Suche); Gegenanfrage wird direkt angenommen. */
    fun addFriend(userId: String) = scope.launch {
        guarded {
            ensureFresh()
            requireApi().rpc("request_friend", buildJsonObject { put("p_user", userId) })
            notice.value = "Freundschaftsanfrage gesendet"
            loadFriends().join()
        }
    }

    fun acceptFriend(userId: String) = scope.launch {
        guarded {
            ensureFresh()
            requireApi().update("friendships", "requester=eq.$userId&addressee=eq.$myId", """{"status":"accepted"}""")
            loadFriends().join()
        }
    }

    /** Anfrage ablehnen / zurückziehen oder Freund entfernen (beide Richtungen). */
    fun removeFriend(userId: String) = scope.launch {
        guarded {
            ensureFresh()
            val me = myId ?: return@guarded
            requireApi().delete("friendships", "or=(and(requester.eq.$me,addressee.eq.$userId),and(requester.eq.$userId,addressee.eq.$me))")
            loadFriends().join()
        }
    }

    /** Freund in die eigene offene Lobby einladen; gibt es keine, wird eine private Lobby angelegt. */
    fun inviteFriend(friendId: String, settings: GameSettings) = scope.launch {
        guarded {
            ensureFresh()
            val l = _lobby.value?.takeIf { it.status == "open" } ?: run {
                val args = buildJsonObject {
                    put("p_settings", SupabaseApi.json.encodeToJsonElement(GameSettings.serializer(), settings))
                    put("p_public", false); put("p_max_players", 2); put("p_name", "Mit Freunden")
                }
                enterLobby(requireApi().rpc("create_lobby", args).trim('"', ' ', '\n'))
                _lobby.value ?: return@guarded
            }
            val me = myId ?: return@guarded
            try {
                requireApi().insert("invites", SupabaseApi.json.encodeToString(Invite.serializer(), Invite(l.id, me, friendId, l.code)), returning = false)
                notice.value = "Einladung gesendet"
            } catch (e: OnlineException) {
                if (e.status == 409) notice.value = "Schon eingeladen" else throw e
            }
        }
    }

    fun acceptInvite(i: Invite) { dismissInvite(i); joinByCode(i.code) }

    fun dismissInvite(i: Invite) = scope.launch {
        invite.value = null
        runCatching { ensureFresh(); requireApi().delete("invites", "lobby_id=eq.${i.lobbyId}&to_id=eq.${i.toId}") }
    }

    /** Eigener Kanal: Einladungen und Änderungen an Freundschaften kommen sofort an. */
    private fun connectUserChannel() {
        val me = myId ?: return
        val changes = listOf(
            pgChange("INSERT", "invites", "to_id=eq.$me"),
            pgChange("*", "friendships", "addressee=eq.$me"),
            pgChange("*", "friendships", "requester=eq.$me"),
        )
        realtime.subscribe("realtime:user:$me", changes, onMessage = { event, payload -> onUserMessage(event, payload) }, onJoined = { loadFriends(); loadInvite() })
        // ponytail: ein Presence-Kanal für alle Angemeldeten – presence_state trägt alle Online-Nutzer;
        //           ab einigen tausend gleichzeitig auf Kanäle je Freundeskreis umstellen
        realtime.subscribe(ONLINE_TOPIC, presenceKey = me, onMessage = { event, payload -> presenceUpdate(_online, event, payload) },
            onJoined = { realtime.track(ONLINE_TOPIC, buildJsonObject { put("uid", me) }) })
        registerPushToken()
    }

    /** Offene Einladung nachholen, die per Push kam, während die App zu war. */
    private fun loadInvite() = scope.launch {
        val me = myId ?: return@launch
        runCatching {
            ensureFresh()
            val text = requireApi().select("invites", "select=*,lobbies!inner(status)&to_id=eq.$me&lobbies.status=eq.open&order=created_at.desc&limit=1")
            SupabaseApi.json.decodeFromString(ListSerializer(Invite.serializer()), text).firstOrNull()?.let { invite.value = it }
        }
    }

    /** FCM-Token für Push bei geschlossener App hinterlegen (Tabelle push_tokens, Versand durch push/). Ohne google-services.json passiert nichts. */
    // ponytail: kein FirebaseMessagingService/onNewToken – das Token wird bei jedem Login und App-Start neu gemeldet;
    //           es rotiert praktisch nur bei Neuinstallation, die auch die Sitzung löscht
    private fun registerPushToken() {
        val me = myId ?: return
        runCatching { com.google.firebase.messaging.FirebaseMessaging.getInstance().token }.getOrNull()?.addOnSuccessListener { token ->
            pushToken = token
            scope.launch { runCatching { ensureFresh(); requireApi().upsert("push_tokens", buildJsonObject { put("user_id", me); put("token", token) }.toString()) } }
        }
    }

    private fun onUserMessage(event: String, payload: JsonObject) {
        if (event != "postgres_changes") return
        val data = payload["data"]?.jsonObject ?: return
        when (data["table"]?.jsonPrimitive?.contentOrNull) {
            "invites" -> data["record"]?.jsonObject?.let { r ->
                runCatching { SupabaseApi.json.decodeFromJsonElement(Invite.serializer(), r) }.getOrNull()?.let { if (it.fromId != myId) invite.value = it }
            }
            "friendships" -> loadFriends()
        }
    }

    // ---------- Verlauf in der Cloud (Tabelle saved_matches) ----------

    @kotlinx.serialization.Serializable
    private data class SavedMatch(val id: String, @kotlinx.serialization.SerialName("user_id") val userId: String, val mode: String, @kotlinx.serialization.SerialName("played_at") val playedAt: Long, val record: MatchRecord)

    /** Einzelnes Match sichern (nach jedem Spiel); Fehler werden still ignoriert, der nächste [syncMatches] holt es nach. */
    fun saveMatch(record: MatchRecord) = scope.launch { runCatching { ensureFresh(); upload(listOf(record)) } }

    private suspend fun upload(records: List<MatchRecord>) {
        val me = myId ?: return
        if (records.isEmpty()) return
        val rows = records.map { SavedMatch(it.id, me, it.mode.name, it.finishedAt, it) }
        requireApi().upsert("saved_matches", SupabaseApi.json.encodeToString(ListSerializer(SavedMatch.serializer()), rows))
    }

    /**
     * Verlauf abgleichen: lokale Matches hochladen, die der Server nicht kennt, und Matches vom Server zurückgeben,
     * die lokal fehlen (nach Login oder Gerätewechsel). Abgleich per Match-ID, kein weiterer Zustand nötig.
     */
    suspend fun syncMatches(local: List<MatchRecord>): List<MatchRecord> {
        ensureFresh()
        val a = requireApi()
        val remoteIds = SupabaseApi.json.parseToJsonElement(a.select("saved_matches", "select=id&limit=5000")).let { arr ->
            (arr as kotlinx.serialization.json.JsonArray).map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
        }
        // ponytail: alle fehlenden auf einmal (max. 500 lokale Einträge); in Häppchen schicken, falls PostgREST-Bodygrenze greift
        upload(local.filter { it.id !in remoteIds })
        val localIds = local.map { it.id }.toSet()
        val missing = remoteIds.filter { it !in localIds }
        if (missing.isEmpty()) return emptyList()
        val q = "select=record&id=in.(${missing.joinToString(",") { SupabaseApi.enc("\"$it\"") }})"
        return SupabaseApi.json.decodeFromString(ListSerializer(SavedMatch.serializer()), a.select("saved_matches", q)).map { it.record }
    }

    @kotlinx.serialization.Serializable
    data class UserData(val settings: kotlinx.serialization.json.JsonElement, val players: kotlinx.serialization.json.JsonElement, @kotlinx.serialization.SerialName("updated_at") val updatedAt: Long)

    /** Einstellungen und Spielerliste des Kontos (Tabelle user_data), null = noch nichts gesichert. */
    suspend fun pullUserData(): UserData? {
        ensureFresh()
        val me = myId ?: return null
        return SupabaseApi.json.decodeFromString(ListSerializer(UserData.serializer()), requireApi().select("user_data", "user_id=eq.$me&select=settings,players,updated_at")).firstOrNull()
    }

    fun pushUserData(d: UserData) = scope.launch {
        val me = myId ?: return@launch
        runCatching {
            ensureFresh()
            val body = buildJsonObject { put("user_id", me); put("settings", d.settings); put("players", d.players); put("updated_at", d.updatedAt) }
            requireApi().upsert("user_data", body.toString())
        }
    }

    /** Verlauf auch in der Cloud löschen (Nutzer hat "Verlauf löschen" gewählt). */
    fun deleteAllMatches() = scope.launch { val me = myId ?: return@launch; runCatching { ensureFresh(); requireApi().delete("saved_matches", "user_id=eq.$me") } }

    /** Lobby-Liste regelmäßig aktualisieren, solange die Liste sichtbar ist. */
    fun startLobbyPolling() {
        refreshJob?.cancel()
        refreshJob = scope.launch { while (true) { refreshLobbies(); delay(8_000) } }
    }

    fun stopLobbyPolling() { refreshJob?.cancel(); refreshJob = null }

    fun shutdown() { realtime.disconnect(); refreshJob?.cancel() }

    companion object {
        const val REDIRECT_URI = "scorelens://auth/callback"
        /** Freundes-Link (QR-Code): scorelens://friend/<Nutzer-ID> */
        const val FRIEND_LINK = "scorelens://friend/"
        /** Pfad des teilbaren Links: https://<server>/functions/v1/friend/<Nutzer-ID> */
        const val FRIEND_PATH = "/functions/v1/friend/"
        /** Nutzer-ID aus QR-Inhalt oder geteiltem Link; null, wenn es kein Scorelens-Freundeslink ist. */
        fun friendIdFrom(text: String): String? {
            val t = text.trim()
            if (!t.startsWith(FRIEND_LINK) && !t.contains(FRIEND_PATH)) return null
            return t.substringAfterLast('/').takeIf { it.length == 36 }
        }
        val PROVIDERS = listOf("google" to "Google", "github" to "GitHub")
        private const val ONLINE_TOPIC = "realtime:online"

        private fun pgChange(event: String, table: String, filter: String): JsonObject =
            buildJsonObject { put("event", event); put("schema", "public"); put("table", table); put("filter", filter) }

        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        private fun randomString(n: Int): String { val r = SecureRandom(); return (1..n).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("") }
        private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
