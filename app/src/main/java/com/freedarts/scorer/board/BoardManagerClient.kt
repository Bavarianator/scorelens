package com.freedarts.scorer.board

import com.freedarts.scorer.model.Segment
import com.freedarts.scorer.remote.RemoteServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Verbindung zum lokalen Autodarts Board Manager (Port 3180).
 * Es wird GET /api/state gepollt (wie die Home-Assistant- und ioBroker-Integrationen):
 * {
 *   "status": "Throw" | "Takeout" | "Stopped" | ...,
 *   "event": "...",
 *   "numThrows": 2,
 *   "throws": [ { "segment": { "name": "T20", "number": 20, "multiplier": 3, "bed": "Triple" }, "coords": { "x": .., "y": .. } } ]
 * }
 * Neue Würfe werden als [Segment] über [throws] weitergegeben. Der Board Manager selbst
 * bleibt kostenlos nutzbar – so kann die vorhandene Kamera-Hardware weiterverwendet werden.
 */
class BoardManagerClient(private val scope: CoroutineScope) {

    enum class Connection { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    /** Wurf mit Auftreffpunkt in Board-Millimetern, sofern der Board-Server ihn mit unit=mm liefert (Scorelens-Board-Handy). */
    data class Throw(val segment: Segment, val x: Float? = null, val y: Float? = null)

    data class BoardState(val status: String = "", val event: String = "", val numThrows: Int = 0, val throws: List<Throw> = emptyList())

    /** Rohe KI-Spitze der Kamera des Board-Handys in Board-mm (Scorelens-Erweiterung, Feld "tips"). */
    data class Tip(val x: Float, val y: Float, val conf: Float)
    /** Spitzen einer KI-Auswertung; [seq] ist deren laufende Nummer, jede Auswertung wird nur einmal weitergegeben. */
    data class TipBatch(val seq: Int, val tips: List<Tip>)

    private val http = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _connection = MutableStateFlow(Connection.DISCONNECTED)
    val connection: StateFlow<Connection> = _connection
    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state
    private val _throws = MutableSharedFlow<Throw>(extraBufferCapacity = 16)
    val throws: SharedFlow<Throw> = _throws
    /** Wird ausgelöst, wenn das Board von "Throw" auf "Takeout" wechselt (Aufnahme beendet). */
    private val _takeout = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val takeout: SharedFlow<Unit> = _takeout
    private val _tips = MutableSharedFlow<TipBatch>(extraBufferCapacity = 16)
    val tips: SharedFlow<TipBatch> = _tips

    private var job: Job? = null
    private var baseUrl = ""
    private var seenThrows = 0
    private var lastStatus = ""
    private var lastTipSeq = -1

    // ponytail: Scorelens-Board-Handy (Port 8765) wird alle 150 ms gepollt, damit seine Roh-Spitzen zeitnah ankommen;
    // Long-Poll/WebSocket erst, wenn Akku oder Latenz messbar stören
    fun connect(host: String, port: Int, intervalMillis: Long = if (port == RemoteServer.PORT) 150 else 400) {
        disconnect()
        baseUrl = "http://${host.trim()}:$port"
        seenThrows = 0
        lastStatus = ""
        lastTipSeq = -1
        _connection.value = Connection.CONNECTING
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val body = get("/api/state")
                    parse(body)
                    _connection.value = Connection.CONNECTED
                } catch (e: Exception) {
                    _connection.value = Connection.ERROR
                }
                delay(intervalMillis)
            }
        }
    }

    fun disconnect() {
        job?.cancel(); job = null
        _connection.value = Connection.DISCONNECTED
    }

    private fun get(path: String): String {
        val req = Request.Builder().url(baseUrl + path).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    internal fun parse(body: String) {
        val obj = json.parseToJsonElement(body).jsonObject
        val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: ""
        val event = obj["event"]?.jsonPrimitive?.contentOrNull ?: ""
        val throwsArr = obj["throws"]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonArray } ?: emptyList()
        val segs = throwsArr.mapNotNull { t ->
            val seg = t.jsonObject["segment"]?.jsonObject ?: return@mapNotNull null
            val coords = t.jsonObject["coords"]?.let { it as? kotlinx.serialization.json.JsonObject }?.takeIf { it["unit"]?.jsonPrimitive?.contentOrNull == "mm" }
            val x = coords?.get("x")?.jsonPrimitive?.floatOrNull; val y = coords?.get("y")?.jsonPrimitive?.floatOrNull
            val number = seg["number"]?.jsonPrimitive?.intOrNull
            val mult = seg["multiplier"]?.jsonPrimitive?.intOrNull
            val name = seg["name"]?.jsonPrimitive?.contentOrNull
            val segment = when {
                number != null && mult != null -> if (number == 0 || mult == 0) Segment.MISS else Segment(number, mult)
                name != null -> Segment.parse(name)
                else -> null
            } ?: return@mapNotNull null
            Throw(segment, x, y)
        }
        val num = obj["numThrows"]?.jsonPrimitive?.intOrNull ?: segs.size

        // Neue Aufnahme erkannt (Board hat Würfe zurückgesetzt)
        if (segs.size < seenThrows) seenThrows = 0
        while (seenThrows < segs.size) {
            _throws.tryEmit(segs[seenThrows])
            seenThrows++
        }
        if (status == "Takeout" && lastStatus != "Takeout") _takeout.tryEmit(Unit)
        lastStatus = status
        _state.value = BoardState(status, event, num, segs)
        val tipSeq = obj["tipSeq"]?.jsonPrimitive?.intOrNull
        if (tipSeq != null && tipSeq != lastTipSeq) {
            lastTipSeq = tipSeq
            val tips = obj["tips"]?.let { it as? kotlinx.serialization.json.JsonArray }?.mapNotNull { t ->
                val o = t.jsonObject
                val x = o["x"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
                val y = o["y"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
                Tip(x, y, o["conf"]?.jsonPrimitive?.floatOrNull ?: 1f)
            } ?: emptyList()
            _tips.tryEmit(TipBatch(tipSeq, tips))
        }
    }

    /** Steuerbefehle des Board Managers (Start/Stop/Reset/Kalibrieren). */
    fun command(name: String) {
        if (baseUrl.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                val url = "$baseUrl/api/$name"
                val post = Request.Builder().url(url).post(ByteArray(0).toRequestBody(null)).build()
                val ok = http.newCall(post).execute().use { it.isSuccessful }
                if (!ok) http.newCall(Request.Builder().url(url).get().build()).execute().close()
            } catch (_: Exception) { }
        }
    }

    suspend fun testConnection(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("http://${host.trim()}:$port/api/state").get().build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }
}
