package com.freedarts.scorer.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Remote Scoring wie bei Autodarts Lens: Das Handy bleibt als Kamera am Board, die Spielansicht läuft im
 * Browser eines zweiten Geräts im selben WLAN (http://<ip>:8765). Minimaler HTTP-Server ohne Abhängigkeiten.
 */
class RemoteServer(private val stateProvider: () -> RemoteState, private val onCommand: (String) -> Unit) {

    @Serializable
    data class RemotePlayer(val name: String, val score: String, val detail: String, val legs: Int, val sets: Int, val active: Boolean, val isOut: Boolean, val history: List<String>)

    @Serializable
    data class RemoteState(
        val hasGame: Boolean,
        val title: String = "",
        val headline: String = "",
        val banner: String? = null,
        val checkout: String? = null,
        val visit: List<String> = emptyList(),
        val visitSum: Int = 0,
        val players: List<RemotePlayer> = emptyList(),
        val finished: Boolean = false,
        val lens: String = "",
    )

    companion object { const val PORT = 8765 }

    private val json = Json { encodeDefaults = true }
    private var server: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    val isRunning: Boolean get() = running.get()

    fun start(): Boolean {
        if (running.get()) return true
        return try {
            val s = ServerSocket(PORT)
            s.soTimeout = 1000
            server = s
            running.set(true)
            Thread({
                while (running.get()) {
                    try {
                        val client = s.accept()
                        pool.execute { handle(client) }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) { if (!running.get()) break }
                }
            }, "freedarts-remote").apply { isDaemon = true }.start()
            true
        } catch (e: Exception) { false }
    }

    fun stop() {
        running.set(false)
        try { server?.close() } catch (_: Exception) { }
        server = null
    }

    /** IPv4-Adresse im WLAN (für die Anzeige der URL). */
    fun localAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    } catch (e: Exception) { null }

    private fun handle(client: Socket) {
        client.use { c ->
            c.soTimeout = 3000
            val reader = BufferedReader(InputStreamReader(c.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (true) { val l = reader.readLine() ?: break; if (l.isEmpty()) break }
            val parts = requestLine.split(" ")
            val path = parts.getOrNull(1) ?: "/"
            val out = c.getOutputStream()
            when {
                path.startsWith("/state") -> respond(out, "application/json; charset=utf-8", json.encodeToString(stateProvider()))
                path.startsWith("/cmd") -> {
                    val cmd = path.substringAfter("do=", "").substringBefore("&")
                    if (cmd.isNotEmpty()) onCommand(cmd)
                    respond(out, "application/json; charset=utf-8", "{\"ok\":true}")
                }
                else -> respond(out, "text/html; charset=utf-8", PAGE)
            }
        }
    }

    private fun respond(out: OutputStream, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray()); out.write(bytes); out.flush()
    }

    private val PAGE = """<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Scorelens Remote</title>
<style>
body{margin:0;background:#0A0E17;color:#EEF1F6;font-family:system-ui,sans-serif}
.top{display:flex;justify-content:space-between;align-items:center;padding:12px 18px;color:#8B95A7}
.players{display:flex;gap:12px;padding:0 12px;flex-wrap:wrap}
.p{flex:1 1 260px;background:#141A26;border:1px solid #263042;border-radius:14px;overflow:hidden}
.p.active{border-color:#2F6BFF;box-shadow:0 0 0 2px #2F6BFF inset}
.p .h{background:#1C2331;padding:8px 14px;font-weight:700;letter-spacing:1px}
.p.active .h{background:#2F6BFF}
.p .s{font-size:96px;font-weight:900;padding:6px 14px;line-height:1}
.p .d{padding:0 14px 10px;color:#8B95A7}
.p .legs{float:right;color:#FFC107}
.visit{display:flex;gap:10px;margin:14px 12px;background:#141A26;border:1px solid #263042;border-radius:12px;padding:12px 16px;font-size:24px;font-weight:700}
.visit .sum{margin-left:auto;color:#7CF06B}
.banner{margin:0 12px;padding:10px;border-radius:12px;background:#0F5A34;text-align:center;font-size:26px;font-weight:800}
.chk{color:#7CF06B;text-align:center;font-weight:600;margin:6px}
.hist{padding:0 14px 12px;color:#8B95A7;font-size:14px}
.btns{display:flex;gap:10px;padding:12px}
button{flex:1;background:#1C2331;color:#fff;border:1px solid #263042;border-radius:10px;padding:14px;font-size:18px}
button.p{background:#2F6BFF;border-color:#2F6BFF}
</style></head><body>
<div class="top"><div id="title">Scorelens</div><div id="lens"></div></div>
<div id="banner" class="banner" style="display:none"></div>
<div class="players" id="players"></div>
<div class="visit" id="visit"></div>
<div class="chk" id="chk"></div>
<div class="btns"><button onclick="cmd('undo')">Undo</button><button class="p" onclick="cmd('next')">Next</button></div>
<script>
function cmd(c){fetch('/cmd?do='+c).then(tick)}
function esc(s){return String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]))}
function tick(){fetch('/state').then(r=>r.json()).then(s=>{
 document.getElementById('title').textContent=s.hasGame?(s.title+' · '+s.headline):'Kein laufendes Spiel';
 document.getElementById('lens').textContent=s.lens;
 var b=document.getElementById('banner');b.style.display=s.banner?'block':'none';b.textContent=s.banner||'';
 document.getElementById('players').innerHTML=s.players.map(p=>'<div class="p'+(p.active?' active':'')+'"><div class="h">'+esc(p.name).toUpperCase()+
  '<span class="legs">'+(p.sets?('S '+p.sets+' '):'')+(p.legs?('L '+p.legs):'')+'</span></div><div class="s">'+esc(p.score)+'</div><div class="d">'+esc(p.detail)+'</div><div class="hist">'+p.history.slice(-5).map(esc).join(' · ')+'</div></div>').join('');
 var v=[0,1,2].map(i=>'<span>'+(s.visit[i]||'—')+'</span>').join('')+'<span class="sum">'+s.visitSum+'</span>';
 document.getElementById('visit').innerHTML=v;
 document.getElementById('chk').textContent=s.checkout?('Checkout: '+s.checkout):'';
}).catch(()=>{})}
setInterval(tick,500);tick();
</script></body></html>"""
}
