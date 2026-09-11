package com.freedarts.scorer.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Supabase Realtime (Phoenix-Channels über WebSocket) ohne SDK: Kanäle mit postgres_changes, Broadcast und Presence.
 * Verbindet nach Abbrüchen selbstständig neu und tritt allen Kanälen wieder bei; danach wird [Channel.onJoined]
 * aufgerufen, damit der Aufrufer verpasste Änderungen nachlädt.
 */
class RealtimeClient(private val scope: CoroutineScope) {

    enum class State { OFF, CONNECTING, OPEN, RETRYING }

    class Channel(
        val topic: String,
        val postgresChanges: List<JsonObject>,
        val presenceKey: String?,
        /** (event, payload) – z. B. "postgres_changes", "broadcast", "presence_state", "presence_diff". */
        val onMessage: (String, JsonObject) -> Unit,
        val onJoined: () -> Unit,
    ) {
        @Volatile var joined = false
        @Volatile var joinRef: String? = null
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS).build()
    private val channels = ConcurrentHashMap<String, Channel>()
    private val refs = AtomicInteger(0)

    private val _state = MutableStateFlow(State.OFF)
    val state: StateFlow<State> = _state

    @Volatile private var socket: WebSocket? = null
    @Volatile private var wanted = false
    @Volatile private var url = ""
    @Volatile private var accessToken: String? = null
    private var heartbeat: Job? = null
    private var reconnect: Job? = null
    private var attempts = 0

    fun connect(url: String, accessToken: String?) {
        this.accessToken = accessToken
        if (wanted && this.url == url && socket != null) return
        disconnect()
        this.url = url
        wanted = true
        attempts = 0
        open()
    }

    fun disconnect() {
        wanted = false
        heartbeat?.cancel(); heartbeat = null
        reconnect?.cancel(); reconnect = null
        socket?.let { runCatching { it.close(1000, "bye") } }
        socket = null
        channels.values.forEach { it.joined = false }
        _state.value = State.OFF
    }

    /** Neues Access-Token (nach Refresh) an alle Kanäle schicken. */
    fun setAccessToken(token: String?) {
        accessToken = token
        if (token == null) return
        for (ch in channels.values) if (ch.joined) push(ch.topic, "access_token", buildJsonObject { put("access_token", token) })
    }

    fun subscribe(topic: String, postgresChanges: List<JsonObject> = emptyList(), presenceKey: String? = null,
                  onMessage: (String, JsonObject) -> Unit, onJoined: () -> Unit = {}): Channel {
        unsubscribe(topic)
        val ch = Channel(topic, postgresChanges, presenceKey, onMessage, onJoined)
        channels[topic] = ch
        if (_state.value == State.OPEN) join(ch)
        return ch
    }

    fun unsubscribe(topic: String) {
        val ch = channels.remove(topic) ?: return
        if (ch.joined) push(topic, "phx_leave", JsonObject(emptyMap()))
    }

    fun broadcast(topic: String, event: String, payload: JsonObject) {
        push(topic, "broadcast", buildJsonObject { put("type", "broadcast"); put("event", event); put("payload", payload) })
    }

    /** Presence: eigene Anwesenheit im Kanal melden. */
    fun track(topic: String, payload: JsonObject) {
        push(topic, "presence", buildJsonObject { put("type", "presence"); put("event", "track"); put("payload", payload) })
    }

    // ---------- intern ----------

    private fun open() {
        if (!wanted) return
        _state.value = if (attempts == 0) State.CONNECTING else State.RETRYING
        socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (socket !== webSocket) return
                attempts = 0
                _state.value = State.OPEN
                startHeartbeat()
                channels.values.forEach { join(it) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket !== webSocket) return
                handle(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                socket = null
                scheduleReconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket !== webSocket) return
                socket = null
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        heartbeat?.cancel(); heartbeat = null
        channels.values.forEach { it.joined = false }
        if (!wanted) return
        val wait = min(1000L shl min(attempts, 5), 30_000L)
        attempts++
        _state.value = State.RETRYING
        reconnect?.cancel()
        reconnect = scope.launch { delay(wait); if (wanted && socket == null) open() }
    }

    private fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive && wanted) {
                delay(25_000)
                val ok = socket?.send(message("phoenix", "heartbeat", JsonObject(emptyMap()), nextRef(), null)) ?: false
                if (!ok) { socket?.cancel(); socket = null; scheduleReconnect(); break }
            }
        }
    }

    private fun nextRef(): String = refs.incrementAndGet().toString()

    private fun message(topic: String, event: String, payload: JsonObject, ref: String, joinRef: String?): String =
        buildJsonObject {
            put("topic", topic); put("event", event); put("payload", payload); put("ref", ref)
            if (joinRef != null) put("join_ref", joinRef)
        }.toString()

    private fun push(topic: String, event: String, payload: JsonObject) {
        val ch = channels[topic]
        socket?.send(message(topic, event, payload, nextRef(), ch?.joinRef))
    }

    private fun join(ch: Channel) {
        val ref = nextRef()
        ch.joinRef = ref
        val config = buildJsonObject {
            put("broadcast", buildJsonObject { put("self", false); put("ack", false) })
            put("presence", buildJsonObject { put("key", ch.presenceKey ?: "") })
            put("postgres_changes", JsonArray(ch.postgresChanges))
            put("private", false)
        }
        val payload = buildJsonObject {
            put("config", config)
            accessToken?.let { put("access_token", it) }
        }
        socket?.send(message(ch.topic, "phx_join", payload, ref, ref))
    }

    private fun handle(text: String) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val topic = obj["topic"]?.jsonPrimitive?.contentOrNull ?: return
        val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: return
        val payload = obj["payload"] as? JsonObject ?: JsonObject(emptyMap())
        if (topic == "phoenix") return
        val ch = channels[topic] ?: return
        when (event) {
            "phx_reply" -> {
                val ref = obj["ref"]?.jsonPrimitive?.contentOrNull
                if (ref == ch.joinRef) {
                    val ok = payload["status"]?.jsonPrimitive?.contentOrNull == "ok"
                    ch.joined = ok
                    if (ok) ch.onJoined()
                    else ch.onMessage("join_error", payload)
                }
            }
            "phx_error", "phx_close" -> { ch.joined = false }
            "system" -> { if (payload["status"]?.jsonPrimitive?.contentOrNull == "error") ch.onMessage("system_error", payload) }
            else -> ch.onMessage(event, payload)
        }
    }
}
