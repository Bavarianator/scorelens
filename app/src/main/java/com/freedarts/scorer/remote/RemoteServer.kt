package com.freedarts.scorer.remote

import com.freedarts.scorer.lens.LensController
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
class RemoteServer(private val stateProvider: () -> RemoteState, private val frameProvider: () -> ByteArray?, private val onCommand: (String) -> Unit) {

    @Serializable
    data class RemotePlayer(val name: String, val score: String, val detail: String, val legs: Int, val sets: Int, val active: Boolean, val isOut: Boolean, val history: List<String>,
        val color: String = "#3F51B5", val avatar: String? = null, val isBot: Boolean = false)

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
        val winner: String? = null,
        val lens: String = "",
        val lensReady: Boolean = false,
        /** Board-Manager-Sicht der Lens (GET /api/state): Throw / Takeout / Stopped und die Segmente der Aufnahme. */
        val boardStatus: String = "Stopped",
        val boardThrows: List<BoardThrow> = emptyList(),
        /** Rohe KI-Spitzen der letzten Auswertung (Board-mm) und deren laufende Nummer – Futter für ein zweites Kamera-Handy. */
        val boardTips: List<LensController.BoardTip> = emptyList(),
        val tipSeq: Int = 0,
    )

    /** Ein Wurf für /api/state: Segmentname plus Auftreffpunkt in Board-Millimetern (null = unbekannt). */
    @Serializable
    data class BoardThrow(val name: String, val x: Float? = null, val y: Float? = null)

    companion object { const val PORT = 8765 }

    private val json = Json { encodeDefaults = true }

    /**
     * Schlüssel dieses Serverlaufs: steht im QR-Code (`/?k=…`) und ist Pflicht für Steuern (/cmd), Kamerabild
     * (/board.jpg) und Kopplung (/pair). Vorher konnte jeder im WLAN dieses Handy mitten im Spiel in die
     * Zweitgeräte-Ansicht zwingen und das Kamerabild abrufen.
     */
    val token: String = (1..10).map { "abcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")
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
            val authed = path.substringAfter('?', "").split("&").any { it == "k=$token" }
            val out = c.getOutputStream()
            when {
                path.startsWith("/state") -> respond(out, "application/json; charset=utf-8", json.encodeToString(stateProvider()))
                path.startsWith("/board.jpg") -> when {
                    !authed -> respond(out, "text/plain", ByteArray(0), status = "403 Forbidden")
                    else -> frameProvider()?.let { respond(out, "image/jpeg", it) } ?: respond(out, "text/plain", ByteArray(0), status = "404 Not Found")
                }
                // Autodarts-Board-Manager-Format: ein zweites Handy verbindet sich unter Devices → Board Manager mit diesem Gerät
                path.startsWith("/api/state") -> {
                    val s = stateProvider()
                    // coords mit unit=mm: Scorelens-Erweiterung, der Client übernimmt sie nur mit dieser Angabe (Autodarts-Koordinaten sind anders skaliert)
                    val throwsJson = s.boardThrows.joinToString(",") { t ->
                        "{\"segment\":{\"name\":\"${t.name}\"}" + (if (t.x != null && t.y != null) ",\"coords\":{\"x\":${t.x},\"y\":${t.y},\"unit\":\"mm\"}" else "") + "}"
                    }
                    val tipsJson = s.boardTips.joinToString(",") { "{\"x\":${it.x},\"y\":${it.y},\"conf\":${it.conf}}" }
                    respond(out, "application/json; charset=utf-8", "{\"status\":\"${s.boardStatus}\",\"numThrows\":${s.boardThrows.size},\"throws\":[$throwsJson],\"tipSeq\":${s.tipSeq},\"tips\":[$tipsJson]}")
                }
                // Umgekehrte Kopplung: das Board-Handy ruft /pair?url=<seine Adresse> auf, dieses Gerät wird Zweitgerät
                path.startsWith("/pair") -> {
                    val url = java.net.URLDecoder.decode(path.substringAfter("url=", "").substringBefore("&"), "UTF-8")
                    if (url.startsWith("http://")) onCommand("pair:$url")
                    respond(out, "application/json; charset=utf-8", "{\"ok\":true}")
                }
                // ponytail: /state, /api/* und /overlay bleiben ohne Schlüssel lesbar (Autodarts-Board-Manager-Protokoll,
                // TV/OBS); Schlüssel auch dort, sobald der Board-Manager-Client ihn mitschicken kann
                path.startsWith("/api/") -> {
                    onCommand("board:" + path.removePrefix("/api/").substringBefore("?").substringBefore("/"))
                    respond(out, "application/json; charset=utf-8", "{\"ok\":true}")
                }
                path.startsWith("/cmd") -> {
                    val cmd = path.substringAfter("do=", "").substringBefore("&")
                    if (cmd.isNotEmpty()) onCommand(cmd)
                    respond(out, "application/json; charset=utf-8", "{\"ok\":true}")
                }
                // Streaming-/TV-Overlay: transparenter Scoreboard-Streifen für OBS-Browserquelle oder Fernseher
                path.startsWith("/overlay") -> respond(out, "text/html; charset=utf-8", OVERLAY)
                else -> respond(out, "text/html; charset=utf-8", PAGE)
            }
        }
    }

    private fun respond(out: OutputStream, type: String, body: String) = respond(out, type, body.toByteArray(Charsets.UTF_8))

    private fun respond(out: OutputStream, type: String, bytes: ByteArray, status: String = "200 OK") {
        val head = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray()); out.write(bytes); out.flush()
    }

    /** Overlay für OBS/TV: transparenter Hintergrund, Streifen unten (oder `?pos=top`) mit Spielern, Score, Legs/Sets, Aufnahme und Banner. */
    private val OVERLAY = """<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Scorelens Overlay</title>
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@700;800&family=DM+Sans:wght@600;700&display=swap" rel="stylesheet">
<style>
:root{--sur:rgba(17,22,36,.88);--line:#3A4152;--pri:#2B6BFF;--gold:#FFC107;--mut:#9AA3B5;--txt:#F3F5F9}
*{box-sizing:border-box}html,body{height:100%;margin:0;background:transparent;overflow:hidden}
body{font-family:"DM Sans",system-ui,sans-serif;color:var(--txt);display:flex;flex-direction:column;justify-content:flex-end}
body.top{justify-content:flex-start}
.cond{font-family:"Barlow Condensed","Arial Narrow",sans-serif;text-transform:uppercase;letter-spacing:.5px}
#bar{display:flex;align-items:stretch;gap:8px;padding:10px 14px}
.p{display:flex;align-items:center;gap:12px;background:var(--sur);border:2px solid var(--line);border-radius:14px;padding:8px 16px;min-width:220px;backdrop-filter:blur(6px)}
.p.active{border-color:var(--pri);box-shadow:0 0 0 2px var(--pri)}.p.win{border-color:var(--gold)}
.av{width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;color:#fff;flex:none;border:2px solid rgba(255,255,255,.55);background-size:cover;background-position:center}
.p .n{font-weight:700;font-size:18px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:180px}
.p .ls{font-size:12px;color:var(--gold);font-weight:700}
.p .s{font-size:44px;font-weight:800;line-height:1;margin-left:auto;font-variant-numeric:tabular-nums}
#info{display:flex;align-items:center;gap:10px;background:var(--sur);border:2px solid var(--line);border-radius:14px;padding:8px 16px;font-weight:700;backdrop-filter:blur(6px)}
#info .v span{display:inline-block;min-width:44px;text-align:center;background:rgba(255,255,255,.08);border-radius:8px;padding:2px 6px;margin-right:4px}
#info .b{color:var(--gold)}#info .c{color:var(--mut);font-weight:600}
.hide{display:none!important}
</style></head><body>
<div id="bar"></div>
<script>
var q=function(id){return document.getElementById(id)},last='';
if(location.search.indexOf('pos=top')>=0)document.body.classList.add('top');
function esc(s){return String(s==null?'':s).replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
function ini(n){return n.split(/\s+/).map(function(w){return w[0]||''}).join('').slice(0,2).toUpperCase()}
function av(p){var st='background-color:'+esc(p.color)+(p.avatar?';background-image:url(data:image/jpeg;base64,'+p.avatar+')':'');
 return '<div class="av" style="'+st+'">'+(p.avatar?'':esc(p.isBot?'B':ini(p.name)))+'</div>'}
function card(p,s){var cls='p'+(p.active?' active':'')+(s.finished&&s.winner===p.name?' win':'');
 var ls=(p.sets?'S '+p.sets+' ':'')+(p.legs?'L '+p.legs:'');
 return '<div class="'+cls+'">'+av(p)+'<div><div class="n">'+esc(p.name)+'</div><div class="ls">'+ls+'</div></div><div class="s cond">'+esc(p.score)+'</div></div>'}
function render(s){var b=q('bar');if(!s.hasGame){b.innerHTML='';return}
 var info='<div class="v">'+[0,1,2].map(function(i){return '<span class="cond">'+esc(s.visit[i]||'–')+'</span>'}).join('')+'</div><span class="cond">'+s.visitSum+'</span>'+
  (s.banner?'<span class="b cond">'+esc(s.banner)+'</span>':'')+(s.checkout?'<span class="c">'+esc(s.checkout)+'</span>':'');
 b.innerHTML=s.players.map(function(p){return card(p,s)}).join('')+'<div id="info">'+info+'</div>'}
function tick(){fetch('/state').then(function(r){return r.text()}).then(function(t){if(t===last)return;last=t;render(JSON.parse(t))}).catch(function(){})}
setInterval(tick,500);tick();
</script></body></html>
"""

    /** Spielansicht: Karten wie in der App, Live-Board aus der Lens, Tastenkürzel U/Leertaste/F. */
    private val PAGE = """<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Scorelens Remote</title>
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@600;700;800&family=DM+Sans:wght@400;600;700&display=swap" rel="stylesheet">
<style>
:root{--bg:#0B1220;--sur:#171C27;--hi:#2A3040;--line:#3A4152;--pri:#2B6BFF;--lime:#7CF06B;--gold:#FFC107;--mut:#9AA3B5;--txt:#F3F5F9;--green:#22C55E;--red:#E5484D}
*{box-sizing:border-box}
html,body{height:100%}
body{margin:0;background:var(--bg);color:var(--txt);font-family:"DM Sans",system-ui,sans-serif;display:flex;flex-direction:column;
 background-image:linear-gradient(115deg,transparent 42%,rgba(22,38,80,.55) 42%,rgba(22,38,80,.55) 62%,transparent 62%);background-attachment:fixed}
.cond{font-family:"Barlow Condensed","Arial Narrow",sans-serif;text-transform:uppercase;letter-spacing:.5px}
header{display:flex;align-items:center;gap:14px;padding:10px 18px;border-bottom:1px solid var(--line);background:rgba(11,18,32,.7);backdrop-filter:blur(6px)}
header .brand{font-weight:800;font-size:22px}
header .brand b{color:var(--pri)}
header .head{color:var(--mut);font-size:15px;flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.pill{display:inline-flex;align-items:center;gap:6px;border:1px solid var(--line);border-radius:999px;padding:3px 10px;font-size:12px;font-weight:600;color:var(--mut)}
.pill i{width:8px;height:8px;border-radius:50%;background:var(--mut)}
.pill.on{border-color:var(--green);color:var(--green)}.pill.on i{background:var(--green)}
.pill.bad{border-color:var(--red);color:var(--red)}.pill.bad i{background:var(--red)}
#fs{background:none;border:1px solid var(--line);color:var(--mut);border-radius:8px;padding:4px 10px;font-size:12px;font-weight:600;cursor:pointer}
main{flex:1;display:grid;grid-template-columns:1fr;gap:14px;padding:14px;align-content:start}
body.cam main{grid-template-columns:1fr minmax(240px,32vw)}
@media(max-width:820px){body.cam main{grid-template-columns:1fr}}
.players{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:12px}
.p{position:relative;background:var(--sur);border:1px solid var(--line);border-radius:16px;padding:14px 16px;transition:border-color .2s,box-shadow .2s}
.p.active{border-color:var(--pri);box-shadow:0 0 0 2px var(--pri),0 12px 40px -12px rgba(43,107,255,.7)}
.p.out{opacity:.45}
.p.win{border-color:var(--gold);box-shadow:0 0 0 2px var(--gold)}
.p .row{display:flex;align-items:center;gap:10px}
.av{width:38px;height:38px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:15px;color:#fff;flex:none;border:2px solid rgba(255,255,255,.55);overflow:hidden;background-size:cover;background-position:center}
.p .name{font-weight:700;font-size:17px;flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.p .ls{display:flex;gap:6px}
.p .ls span{background:var(--hi);border-radius:8px;padding:2px 8px;font-size:12px;font-weight:700;color:var(--gold)}
.p .s{font-size:clamp(64px,9vw,128px);font-weight:800;line-height:.95;margin:8px 0 2px;font-variant-numeric:tabular-nums}
.p.active .s{color:#fff}
.p .d{color:var(--mut);font-size:14px;min-height:18px}
.p .hist{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px}
.p .hist span{background:var(--hi);border-radius:6px;padding:2px 7px;font-size:12px;color:#C7CDD8;font-variant-numeric:tabular-nums}
.p .hist span:last-child{background:var(--pri);color:#fff}
.p .tag{position:absolute;top:-10px;right:14px;background:var(--pri);color:#fff;border-radius:999px;padding:2px 10px;font-size:11px;font-weight:700}
.p.win .tag{background:var(--gold);color:#14161B}
.visit{display:flex;align-items:center;gap:10px;margin-top:14px;background:var(--sur);border:1px solid var(--line);border-radius:16px;padding:12px 14px}
.visit .slot{flex:1;text-align:center;background:var(--hi);border-radius:12px;padding:10px 4px;font-size:clamp(22px,3.5vw,34px);font-weight:700;color:var(--mut);font-variant-numeric:tabular-nums}
.visit .slot.f{color:#fff;background:#1E4FD6}
.visit .sum{min-width:84px;text-align:right;font-size:clamp(30px,4.5vw,44px);font-weight:800;color:var(--lime);font-variant-numeric:tabular-nums}
.chk{margin-top:10px;text-align:center;color:var(--lime);font-weight:600;font-size:16px;min-height:20px}
aside{background:var(--sur);border:1px solid var(--line);border-radius:16px;padding:10px;display:none;flex-direction:column;gap:8px}
body.cam aside{display:flex}
aside .cap{font-size:12px;color:var(--mut);font-weight:600}
aside img{width:100%;border-radius:10px;background:#000;aspect-ratio:3/4;object-fit:cover}
#banner{position:fixed;inset:0;display:none;align-items:center;justify-content:center;pointer-events:none;background:rgba(11,18,32,.55)}
#banner span{font-size:clamp(48px,12vw,140px);font-weight:800;color:#fff;text-shadow:0 8px 40px rgba(43,107,255,.8);animation:pop .35s cubic-bezier(.2,1.4,.4,1)}
@keyframes pop{from{transform:scale(.6);opacity:0}to{transform:scale(1);opacity:1}}
#empty{display:none;text-align:center;color:var(--mut);padding:60px 20px}
#empty b{display:block;color:#fff;font-size:36px;margin-bottom:8px}
footer{display:flex;gap:10px;padding:12px 14px;border-top:1px solid var(--line);background:rgba(11,18,32,.7)}
footer button{flex:1;background:var(--sur);color:#fff;border:1px solid var(--line);border-radius:14px;padding:16px;font-size:18px;font-weight:600;font-family:inherit;cursor:pointer;min-height:56px}
footer button:active{transform:scale(.98)}
footer button.pri{background:var(--pri);border-color:var(--pri)}
.pad{display:none;margin-top:14px;grid-template-columns:repeat(5,1fr);gap:6px}.pad.on{display:grid}
.pad button{background:var(--sur);color:#fff;border:1px solid var(--line);border-radius:10px;padding:12px 0;font-size:18px;font-weight:700;font-family:inherit;cursor:pointer;min-height:48px}
.pad button.m{background:var(--hi)}.pad button.m.sel{background:var(--pri);border-color:var(--pri)}.pad button.x{color:var(--red)}
footer kbd{margin-left:8px;font-family:inherit;font-size:11px;color:rgba(255,255,255,.6);border:1px solid rgba(255,255,255,.3);border-radius:4px;padding:1px 5px}
@media(max-width:600px){footer kbd{display:none}header .head{display:none}}
</style></head><body>
<header><div class="brand cond">Score<b>lens</b> <span id="title"></span></div><div class="head" id="head"></div>
<span class="pill" id="lens" style="display:none"><i></i><span></span></span><span class="pill bad" id="conn" style="display:none"><i></i>Keine Verbindung</span>
<button id="fs" onclick="fs()">Vollbild</button></header>
<main>
<section><div id="empty"><b class="cond">Kein laufendes Spiel</b>Starte ein Match auf dem Handy – die Anzeige folgt automatisch.</div>
<div class="players" id="players"></div><div class="visit" id="visit"></div><div class="chk" id="chk"></div><div class="pad" id="pad"></div></section>
<aside><span class="cap">LENS · BOARD</span><img id="board" alt="Board"></aside>
</main>
<footer><button onclick="cmd('undo')">Undo<kbd>U</kbd></button><button class="pri" onclick="cmd('next')">Next<kbd>Leertaste</kbd></button></footer>
<div id="banner"><span class="cond"></span></div>
<script>
var q=function(id){return document.getElementById(id)},last='',camOn=false;
function cmd(c){fetch('/cmd?do='+c).then(tick)}
var mult='S';
function pad(){var h='';['S','D','T'].forEach(function(m){h+='<button class="m'+(mult===m?' sel':'')+'" onclick="setM(\''+m+'\')">'+{S:'Single',D:'Double',T:'Triple'}[m]+'</button>'});
 h+='<button onclick="hit(\'25\')">25</button><button onclick="hit(\'50\')">Bull</button>';
 for(var n=1;n<=20;n++)h+='<button onclick="hit(\''+n+'\')">'+n+'</button>';
 h+='<button class="x" onclick="hit(\'MISS\')">Miss</button>';q('pad').innerHTML=h}
function setM(m){mult=m;pad()}
function hit(s){cmd(s==='25'||s==='50'||s==='MISS'?s:mult+s);mult='S';pad()}
function fs(){document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen()}
function esc(s){return String(s==null?'':s).replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
function ini(n){return n.split(/\s+/).map(function(w){return w[0]||''}).join('').slice(0,2).toUpperCase()}
function av(p){var st='background-color:'+esc(p.color)+(p.avatar?';background-image:url(data:image/jpeg;base64,'+p.avatar+')':'');
 return '<div class="av" style="'+st+'">'+(p.avatar?'':esc(p.isBot?'B':ini(p.name)))+'</div>'}
function card(p,s){var cls='p'+(p.active?' active':'')+(p.isOut?' out':'')+(s.finished&&s.winner===p.name?' win':'');
 var tag=s.finished&&s.winner===p.name?'<span class="tag cond">Winner</span>':(p.active?'<span class="tag cond">Am Zug</span>':'');
 var ls=(p.sets?'<span>S '+p.sets+'</span>':'')+(p.legs?'<span>L '+p.legs+'</span>':'');
 return '<div class="'+cls+'">'+tag+'<div class="row">'+av(p)+'<div class="name">'+esc(p.name)+'</div><div class="ls">'+ls+'</div></div>'+
  '<div class="s cond">'+esc(p.score)+'</div><div class="d">'+esc(p.detail)+'</div><div class="hist">'+p.history.slice(-6).map(function(h){return '<span>'+esc(h)+'</span>'}).join('')+'</div></div>'}
function render(s){
 q('title').textContent=s.hasGame?'· '+s.title:'';q('head').textContent=s.hasGame?s.headline:'';
 var l=q('lens');l.style.display=s.lens?'':'none';l.className='pill'+(s.lensReady?' on':'');l.lastChild.textContent=s.lens;
 camOn=!!s.lens;document.body.classList.toggle('cam',camOn);
 q('empty').style.display=s.hasGame?'none':'block';q('visit').style.display=s.hasGame?'':'none';
 q('players').innerHTML=s.players.map(function(p){return card(p,s)}).join('');
 q('visit').innerHTML=[0,1,2].map(function(i){return '<div class="slot cond'+(s.visit[i]?' f':'')+'">'+esc(s.visit[i]||'–')+'</div>'}).join('')+'<div class="sum cond">'+s.visitSum+'</div>';
 q('chk').textContent=s.checkout?'Checkout: '+s.checkout:'';
 q('pad').className='pad'+(s.hasGame&&!s.finished?' on':'');
 var b=q('banner');b.style.display=s.banner?'flex':'none';if(b.firstChild.textContent!==(s.banner||'')){b.firstChild.textContent=s.banner||''}
}
function tick(){fetch('/state').then(function(r){return r.text()}).then(function(t){q('conn').style.display='none';if(t===last)return;last=t;render(JSON.parse(t))}).catch(function(){q('conn').style.display=''})}
setInterval(tick,500);tick();pad();
var img=q('board');setInterval(function(){if(camOn&&img.complete)img.src='/board.jpg?t='+Date.now()+key()},700);
document.addEventListener('keydown',function(e){if(e.target.tagName==='INPUT')return;var k=e.key.toLowerCase();
 if(k==='u')cmd('undo');else if(k===' '||k==='n'){e.preventDefault();cmd('next')}else if(k==='f')fs()});
</script></body></html>
"""
}
