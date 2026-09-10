package com.freedarts.scorer.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Online-Remote-Scoring: ausgehende WebSocket-Verbindung zum FreeDarts-Relay (Cloudflare, Ordner `relay/`).
 * Das Handy schiebt seinen Spielzustand zum Relay, Zuschauer sehen ihn im Browser unter `<relay>/b/<code>`
 * und können Undo/Next senden. Verbindet sich nach Abbrüchen selbstständig neu (Backoff 1 s … 30 s).
 */
class CloudRelayClient(private val scope: CoroutineScope, private val onCommand: (String) -> Unit) {

    enum class Phase { OFF, CONNECTING, ONLINE, RETRYING, REJECTED }
    data class Status(val phase: Phase = Phase.OFF, val message: String = "")

    @Serializable
    private data class Incoming(val type: String = "", @SerialName("do") val command: String? = null)

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    /** Wird aufgerufen, wenn das Relay den Code ablehnt (403: bereits von einem anderen Handy belegt). */
    var onCodeRejected: (() -> Unit)? = null

    @Volatile private var wanted = false
    @Volatile private var socket: WebSocket? = null
    @Volatile private var lastStateJson: String? = null
    private var reconnectJob: Job? = null
    private var attempts = 0
    private var url = ""

    val isActive: Boolean get() = wanted

    fun start(baseUrl: String, code: String, token: String) {
        stop()
        url = baseUrl.trim().trimEnd('/') + "/ws/board/" + code + "?token=" + token
        wanted = true
        attempts = 0
        open()
    }

    fun stop() {
        wanted = false
        reconnectJob?.cancel(); reconnectJob = null
        socket?.let { runCatching { it.close(1000, "bye") } }
        socket = null
        _status.value = Status(Phase.OFF)
    }

    /** Schickt den Zustand, wenn verbunden; sonst wird er beim nächsten Verbindungsaufbau nachgereicht. */
    fun sendState(state: RemoteServer.RemoteState) {
        val encoded = json.encodeToString(state)
        lastStateJson = encoded
        socket?.send(frame(encoded))
    }

    private fun frame(stateJson: String) = "{\"type\":\"state\",\"state\":$stateJson}"

    private fun open() {
        if (!wanted) return
        _status.value = Status(Phase.CONNECTING, "Verbinde mit Relay…")
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempts = 0
                _status.value = Status(Phase.ONLINE, "Online")
                lastStateJson?.let { webSocket.send(frame(it)) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "pong") return
                val msg = runCatching { json.decodeFromString(Incoming.serializer(), text) }.getOrNull() ?: return
                if (msg.type == "cmd" && !msg.command.isNullOrEmpty()) onCommand(msg.command)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                socket = null
                if (response?.code == 403) {
                    wanted = false
                    _status.value = Status(Phase.REJECTED, "Code bereits vergeben")
                    onCodeRejected?.invoke()
                    return
                }
                val reason = when (response?.code) {
                    null -> t.message ?: "Keine Verbindung"
                    else -> "HTTP ${response.code}"
                }
                scheduleReconnect(reason)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket !== webSocket) return
                socket = null
                scheduleReconnect(if (code == 1001) "Session abgelaufen" else "Verbindung getrennt")
            }
        })
    }

    private fun scheduleReconnect(reason: String) {
        if (!wanted) return
        val wait = min(1000L shl min(attempts, 5), 30_000L)
        attempts++
        _status.value = Status(Phase.RETRYING, "$reason · neuer Versuch in ${wait / 1000} s")
        reconnectJob?.cancel()
        reconnectJob = scope.launch { delay(wait); if (wanted && socket == null) open() }
    }

    companion object {
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private val random = SecureRandom()

        /** 6 Zeichen ohne verwechselbare Symbole (0/O, 1/I). */
        fun newCode(): String = (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
        fun newToken(): String = (1..32).map { TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)] }.joinToString("")
        fun viewerUrl(baseUrl: String, code: String) = baseUrl.trim().trimEnd('/') + "/b/" + code
    }
}
