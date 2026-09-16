package com.freedarts.scorer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freedarts.scorer.audio.Caller
import com.freedarts.scorer.board.BoardManagerClient
import com.freedarts.scorer.data.Repository
import com.freedarts.scorer.engine.AimAdvisor
import com.freedarts.scorer.engine.Bot
import com.freedarts.scorer.engine.Predictor
import com.freedarts.scorer.engine.Checkout
import com.freedarts.scorer.engine.DartGame
import com.freedarts.scorer.engine.GameFactory
import com.freedarts.scorer.engine.GameState
import com.freedarts.scorer.lens.LensController
import com.freedarts.scorer.remote.RemoteServer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.LifecycleOwner
import com.freedarts.scorer.model.AppSettings
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.InputMethod
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import androidx.compose.ui.graphics.asImageBitmap
import com.freedarts.scorer.model.Tournament
import com.freedarts.scorer.model.TournamentMode
import com.freedarts.scorer.BuildConfig
import com.freedarts.scorer.online.Invite
import com.freedarts.scorer.online.Lobby
import com.freedarts.scorer.online.MatchEvent
import com.freedarts.scorer.online.OnlineController
import com.freedarts.scorer.online.OnlineMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.launch
import java.util.UUID

sealed class Screen {
    data object Home : Screen()
    data object Lobby : Screen()
    data object Players : Screen()
    data object Match : Screen()
    data object Result : Screen()
    data object Stats : Screen()
    data object Settings : Screen()
    data object Board : Screen()
    data object Lens : Screen()
    data object ModeSelect : Screen()
    data object History : Screen()
    data object Devices : Screen()
    data object Onboarding : Screen()
    data object Help : Screen()
    /** Online-Modus: Konto, Lobby-Liste, Beitritt per Code. */
    data object Online : Screen()
    /** Wartebereich einer Online-Lobby. */
    data object OnlineLobby : Screen()
    /** Freunde: QR-Code, Suche, Anfragen, Statistik, Einladen. */
    data object Friends : Screen()
    /** Lokales Turnier (K.-o. oder Jeder gegen jeden). */
    data object Tournament : Screen()
    /** Zweitgerät: Remote-Seite des Board-Handys im WebView. */
    data object RemoteView : Screen()
    /** Ein Match aus dem Verlauf: Statistik und Wurfprotokoll. */
    data object MatchDetail : Screen()
    /** Nachschlagetabelle der Checkout-Wege. */
    data object CheckoutTable : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    // Firebase Analytics über track(): Konsole → Analytics → Echtzeit. Nur Nutzung, keine Namen, IDs oder Scores; Opt-out in den Einstellungen.
    private val analytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(app)

    private fun track(event: String, vararg params: Pair<String, Any>) = analytics.logEvent(event, android.os.Bundle().apply {
        // Firebase wertet nur String, Long und Double aus
        for ((k, v) in params) when (v) {
            is String -> putString(k, v)
            is Boolean -> putLong(k, if (v) 1 else 0)
            is Double -> putDouble(k, v)
            is Number -> putLong(k, v.toLong())
        }
    })

    private fun inputSource(): String = settings.value.let { s ->
        when { lens.status.value.running -> "lens"; s.boardManagerEnabled -> "board_manager"; else -> _inputMethod.value.name.lowercase() }
    }

    private fun gameParams(g: DartGame) = arrayOf<Pair<String, Any>>(
        "mode" to g.settings.mode.name, "players" to g.players.size, "bots" to g.players.count { it.isBot },
        "online" to (onlineMatch != null), "input" to inputSource(),
    )

    val repo = Repository.get(app)
    val caller = Caller(app)
    val board = BoardManagerClient(viewModelScope)
    val lens = LensController(app)
    val remote = RemoteServer({ remoteState() }, { lens.frameJpeg() }) { cmd -> viewModelScope.launch { when (cmd) { "undo" -> undo(); "next" -> nextPlayer(); "board:reset" -> lens.requestReference(); "board:calibrate" -> lens.startSearch(); else -> Segment.parse(cmd)?.let { s -> game?.let { g -> if (!g.finished && !g.players[g.current].isBot) throwDart(s) } } } } }
    private val _remoteUrl = MutableStateFlow<String?>(null)
    /** Letzter Lens-Takeout; /api/state meldet danach ~1 s lang „Takeout“, damit ein pollendes Zweithandy ihn sicher sieht. */
    @Volatile private var lensTakeoutAt = 0L
    val remoteUrl: StateFlow<String?> = _remoteUrl

    /** Online-Modus (Supabase): Konto, Lobbys, synchronisierte Matches. */
    val online = OnlineController(app, repo, viewModelScope)
    /** Laufendes Online-Match (null = lokales Spiel). */
    var onlineMatch: OnlineMatch? = null; private set
    val isOnlineGame: Boolean get() = onlineMatch != null
    val isSpectator: Boolean get() = onlineMatch != null && online.spectating

    val players: StateFlow<List<Player>> = repo.players
    val settings: StateFlow<AppSettings> = repo.settings
    val matches: StateFlow<List<MatchRecord>> = repo.matches
    /** Laufendes Turnier: das der Online-Lobby (steht im Lobby-Datensatz) oder das lokale. */
    val tournament: StateFlow<Tournament?> = kotlinx.coroutines.flow.combine(repo.tournament, online.lobby) { local, lobby -> lobby?.tournament ?: local }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, repo.tournament.value)
    /** Spiele starten und Turnier beenden darf lokal jeder, online nur der Host. */
    val canRunTournament: Boolean get() = online.lobby.value?.tournament == null || online.isHost
    /** Index des laufenden Turnierspiels in [Tournament.matches]; null = normales Match. */
    private var tournamentMatch: Int? = null
    val inTournament: Boolean get() = tournamentMatch != null

    private val _screen = MutableStateFlow<Screen>(if (repo.settings.value.onboardingDone) Screen.Home else Screen.Onboarding)
    val screen: StateFlow<Screen> = _screen
    private val backStack = ArrayDeque<Screen>()

    // Lobby
    private val _lobbySettings = MutableStateFlow(settings.value.lastGameSettings)
    val lobbySettings: StateFlow<GameSettings> = _lobbySettings
    private val _lobbyPlayers = MutableStateFlow<List<Player>>(
        settings.value.lastPlayerIds.mapNotNull { id -> players.value.find { it.id == id } ?: id.removePrefix("bot-").toIntOrNull()?.takeIf { id.startsWith("bot-") }?.let { Player.bot(it) } }
            .ifEmpty { players.value.take(1) }
    )
    val lobbyPlayers: StateFlow<List<Player>> = _lobbyPlayers

    // Match
    var game: DartGame? = null; private set
    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState
    private val _lastRecord = MutableStateFlow<MatchRecord?>(null)
    val lastRecord: StateFlow<MatchRecord?> = _lastRecord
    private val _detailRecord = MutableStateFlow<MatchRecord?>(null)
    /** Match, das der Detail-Screen zeigt. */
    val detailRecord: StateFlow<MatchRecord?> = _detailRecord
    fun openMatch(r: MatchRecord) { _detailRecord.value = r; navigate(Screen.MatchDetail) }
    private var botJob: Job? = null
    private var autoNextJob: Job? = null
    private var recorded = false
    /** Live-Ticker im Online-Match: Referee-Bilder je Dart, Schlüssel = Wurfzeitstempel [ThrowRecord.at] (überlebt Undo und Resync). */
    private val _snapshots = MutableStateFlow<Map<Long, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap())
    val snapshots: StateFlow<Map<Long, androidx.compose.ui.graphics.ImageBitmap>> = _snapshots
    private val _inputMethod = MutableStateFlow(settings.value.inputMethod)
    val inputMethod: StateFlow<InputMethod> = _inputMethod

    init {
        applySettings()
        // Ein Ort für alle Bildschirmwechsel (navigate, back, Tabs, direkte Sprünge)
        viewModelScope.launch {
            _screen.collect { track(com.google.firebase.analytics.FirebaseAnalytics.Event.SCREEN_VIEW, com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_NAME to (it::class.simpleName ?: "?")) }
        }
        viewModelScope.launch {
            online.session.map { it?.user?.provider }.distinctUntilChanged().drop(1).collect { provider ->
                if (provider != null) track(com.google.firebase.analytics.FirebaseAnalytics.Event.LOGIN, com.google.firebase.analytics.FirebaseAnalytics.Param.METHOD to provider)
            }
        }
        // Läuft hier selbst Lens, ist das Board-Handy die zweite Kamera: seine Roh-Spitzen fließen in die eigene
        // Bestätigung ein, seine fertigen Würfe/Takeouts nicht (sonst doppelt)
        viewModelScope.launch { board.throws.collect { t -> if (!lens.status.value.running) onBoardThrow(t.segment, t.x, t.y) } }
        viewModelScope.launch { board.takeout.collect { if (!lens.status.value.running) onBoardTakeout() } }
        viewModelScope.launch { board.tips.collect { b -> lens.onRemoteTips(b.tips.map { LensController.BoardTip(it.x, it.y, it.conf) }) } }
        viewModelScope.launch { lens.throws.collect { t -> onBoardThrow(t.segment, t.boardX, t.boardY) } }
        viewModelScope.launch { lens.takeout.collect { lensTakeoutAt = System.currentTimeMillis(); onBoardTakeout() } }
        lens.setSensitivity(settings.value.lensSensitivity)
        val s = settings.value
        if (s.boardManagerEnabled) board.connect(s.boardManagerHost, s.boardManagerPort)
        if (s.remoteEnabled) startRemote()
        lens.onReady = { vibrate() }

        // Online-Modus: Server aus den Einstellungen (oder Build-Voreinstellung), Match-Ereignisse in die Engine
        online.configure(s.onlineUrl.ifBlank { BuildConfig.SUPABASE_URL }, s.onlineAnonKey.ifBlank { BuildConfig.SUPABASE_ANON_KEY })
        // Verlauf mit der Cloud abgleichen, sobald ein Konto angemeldet ist (auch nach Gerätewechsel)
        viewModelScope.launch {
            online.session.map { it?.userId }.distinctUntilChanged().collect { if (it != null && online.configured) { syncHistory(); syncUserData() } }
        }
        // Einstellungen/Spieler bei Änderung in die Cloud (3 s gesammelt), solange ein Konto angemeldet ist
        viewModelScope.launch {
            combine(settings, players) { s, p -> s to p }.collectLatest { (s, p) ->
                if (online.session.value == null || !online.configured || s.changedAt == 0L) return@collectLatest
                delay(3_000)
                online.pushUserData(userData(s, p))
            }
        }
        online.onMatchStarted = { m, events -> startOnlineGame(m, events) }
        online.onResync = { m, events -> if (onlineMatch?.id == m.id) rebuildOnlineGame(m, events) }
        online.onRemoteEvent = { e -> applyRemoteEvent(e) }
        online.onSnapshot = { at, jpg -> com.freedarts.scorer.ui.components.decodeAvatar(jpg)?.let { bmp -> _snapshots.update { it + (at to bmp) } } }
        online.onMatchEnded = { m, aborted ->
            if (onlineMatch?.id == m.id && game?.finished != true) {
                botJob?.cancel(); autoNextJob?.cancel(); caller.stop()
                game = null; _gameState.value = null; onlineMatch = null
                online.notice.value = if (aborted) "Das Match wurde abgebrochen" else "Das Match ist beendet"
                if (_screen.value == Screen.Match) { backStack.clear(); _screen.value = Screen.OnlineLobby }
            }
        }
        online.onLobbyClosed = {
            if (onlineMatch != null) { caller.stop(); game = null; _gameState.value = null; onlineMatch = null }
            if (_screen.value == Screen.OnlineLobby || _screen.value == Screen.Match) { backStack.clear(); _screen.value = Screen.Online }
        }
    }

    // ---------- Online-Modus ----------

    /** Lokalen Verlauf hochladen und fehlende Matches vom Konto holen (auch von Freunden geteilte lokale Matches). */
    fun syncHistory() = viewModelScope.launch {
        runCatching { repo.mergeMatches(online.syncMatches(matches.value).map { toLocalProfile(it) }) }.onFailure { online.error.value = it.message }
    }

    /** Das eigene Online-Konto (Spieler-ID = Nutzer-ID) in der lokalen Statistik dem Profil-Spieler zuordnen. */
    private fun toLocalProfile(r: MatchRecord): MatchRecord {
        val me = online.myId ?: return r
        val local = settings.value.profilePlayerId ?: players.value.firstOrNull()?.id ?: return r
        return r.withPlayerId(me, local)
    }

    /**
     * Mitspieler mit eigenem Konto in die lokale Lobby: QR-Code (Freundes-Link) gescannt → Profil laden, als Spieler
     * mit der Nutzer-ID anlegen (oder Name/Bild auffrischen). Nach dem Match landet es per share_match in seinem Verlauf.
     */
    fun addAccountPlayer(qrText: String) = viewModelScope.launch {
        val id = OnlineController.friendIdFrom(qrText) ?: run { online.error.value = "Das ist kein Scorelens-Freundescode"; return@launch }
        if (id == online.myId) {
            players.value.firstOrNull { it.id == settings.value.profilePlayerId }?.let { p -> _lobbyPlayers.update { l -> if (l.any { it.id == p.id }) l else l + p } }
            return@launch
        }
        val profile = runCatching { online.fetchProfile(id) }.getOrElse { online.error.value = it.message; return@launch }
            ?: run { online.error.value = "Spieler nicht gefunden"; return@launch }
        addLinkedPlayer(Player(id = profile.id, name = profile.name, color = profile.color, avatar = profile.avatar))
    }

    /** Freund aus der Freundesliste als lokalen Spieler (mit seiner Nutzer-ID) übernehmen und in die Lobby setzen. */
    fun addFriendPlayer(f: com.freedarts.scorer.online.Friend) = addLinkedPlayer(f.player())

    private fun addLinkedPlayer(p: Player) {
        if (players.value.any { it.id == p.id }) repo.updatePlayer(p) else repo.addPlayer(p)
        _lobbyPlayers.update { l -> if (l.any { it.id == p.id }) l.map { if (it.id == p.id) p else it } else if (l.size >= 6) l else l + p }
    }

    private fun userData(s: AppSettings, p: List<Player>) = OnlineController.UserData(
        settings = com.freedarts.scorer.online.SupabaseApi.json.encodeToJsonElement(AppSettings.serializer(), s),
        players = com.freedarts.scorer.online.SupabaseApi.json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(Player.serializer()), p),
        updatedAt = s.changedAt,
    )

    /** Einstellungen und Spieler mit dem Konto abgleichen: neuere Seite gewinnt; gerätespezifische Felder bleiben lokal. */
    fun syncUserData() = viewModelScope.launch {
        runCatching {
            val remote = online.pullUserData()
            val local = settings.value
            if (remote == null || remote.updatedAt <= local.changedAt) { online.pushUserData(userData(local, players.value)); return@launch }
            val json = com.freedarts.scorer.online.SupabaseApi.json
            val r = json.decodeFromJsonElement(AppSettings.serializer(), remote.settings)
            val l = local
            repo.setSettingsFromCloud(r.copy(
                lensEnabled = l.lensEnabled, lensCalibration = l.lensCalibration, lensSensitivity = l.lensSensitivity, lensUseFrontCamera = l.lensUseFrontCamera,
                lensExposure = l.lensExposure, lensCaptureTraining = l.lensCaptureTraining, boardManagerEnabled = l.boardManagerEnabled, boardManagerHost = l.boardManagerHost,
                boardManagerPort = l.boardManagerPort, remoteEnabled = l.remoteEnabled, remotePairedUrl = l.remotePairedUrl, onlineUrl = l.onlineUrl, onlineAnonKey = l.onlineAnonKey,
                onlinePkceVerifier = l.onlinePkceVerifier, onboardingDone = l.onboardingDone, changedAt = remote.updatedAt,
            ))
            val ps = json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(Player.serializer()), remote.players)
            if (ps.isNotEmpty()) repo.replacePlayers(ps)
        }.onFailure { online.error.value = it.message }
    }

    fun setOnlineServer(url: String, anonKey: String) {
        updateSettings { it.copy(onlineUrl = url.trim(), onlineAnonKey = anonKey.trim()) }
        online.configure(url.trim().ifBlank { BuildConfig.SUPABASE_URL }, anonKey.trim().ifBlank { BuildConfig.SUPABASE_ANON_KEY })
    }

    /** Ist im Online-Match der lokale Spieler am Zug? (Lokal: immer.) */
    val isMyTurn: Boolean
        get() {
            val g = game ?: return false
            if (onlineMatch == null) return true
            return g.players[g.current].id == online.myId
        }

    /** "Gegner finden": angemeldet → Online-Matchmaking, sonst Bot auf dem eigenen Niveau. */
    fun findOpponent() {
        if (online.session.value != null && online.configured) {
            val gs = GameSettings(mode = GameMode.X01, baseScore = 501, legs = 3)
            online.quickMatch(gs)
            track("quick_match")
            navigate(Screen.OnlineLobby)
        } else playVsMatchedBot()
    }

    fun openOnlineLobby() { navigate(Screen.OnlineLobby) }

    fun openFriends() { online.loadFriends(); navigate(Screen.Friends) }

    /** Freund einladen: eigene offene Lobby oder neue private Lobby, dann in die Lobby wechseln. */
    fun inviteFriend(friendId: String) {
        val last = settings.value.lastGameSettings
        val gs = if (last.mode.category == GameMode.Category.COMPETITIVE) last else GameSettings(mode = GameMode.X01, baseScore = 501, legs = 3)
        online.inviteFriend(friendId, gs)
        track("friend_invite")
        navigate(Screen.OnlineLobby)
    }

    fun acceptInvite(i: Invite) { online.acceptInvite(i); track("invite_accepted"); navigate(Screen.OnlineLobby) }

    /** Öffentliches Match eines anderen live mitverfolgen; der Match-Screen bleibt ohne Eingabe. */
    fun spectate(l: Lobby) { l.currentMatchId?.let { spectate(it) } }
    fun spectate(matchId: String) { online.spectate(matchId); track("spectate") }

    private fun stopSpectating() {
        caller.stop(); botJob?.cancel(); autoNextJob?.cancel()
        online.stopSpectating()
        onlineMatch = null; game = null; _gameState.value = null
        backStack.clear(); _screen.value = Screen.Online
    }

    fun leaveOnlineLobby() {
        if (onlineMatch != null) { caller.stop(); game = null; _gameState.value = null; onlineMatch = null }
        online.leaveLobby()
        backStack.clear(); _screen.value = Screen.Online
    }

    private fun onlinePlayers(m: OnlineMatch): List<Player> = m.players.map { Player(id = it.id, name = it.name, color = it.color, avatar = it.avatar) }

    private fun replay(g: DartGame, events: List<MatchEvent>) {
        for (e in events) when (e.kind) {
            MatchEvent.KIND_THROW -> e.segment?.let { g.throwDart(it, e.x, e.y, e.at, e.hold) }
            MatchEvent.KIND_NEXT -> g.next()
            MatchEvent.KIND_UNDO -> g.undo()
        }
    }

    private fun startOnlineGame(m: OnlineMatch, events: List<MatchEvent>) {
        botJob?.cancel(); autoNextJob?.cancel()
        recorded = false
        _lastRecord.value = null
        _snapshots.value = emptyMap()
        onlineMatch = m
        // Average der Mitspieler fürs Vorhersagen: aus der Lobby, sonst Profil nachladen
        online.lobby.value?.players?.forEach { lp -> lp.profile?.let { opponentAvg[lp.userId] = it.avg } }
        viewModelScope.launch {
            var changed = false
            for (p in m.players) if (p.id !in opponentAvg) online.fetchProfile(p.id)?.let { opponentAvg[p.id] = it.avg; changed = true }
            if (changed) predict()
        }
        val g = GameFactory.create(onlinePlayers(m), m.settings, m.seed)
        replay(g, events)
        game = g
        refresh()
        if (events.isEmpty() && !isSpectator) track("match_start", *gameParams(g))
        if (g.finished) recordMatch() else caller.callPlayer(g.players[g.current].name)
        if (_screen.value != Screen.Match) { navigate(Screen.Match) }
    }

    private fun rebuildOnlineGame(m: OnlineMatch, events: List<MatchEvent>) {
        val g = GameFactory.create(onlinePlayers(m), m.settings, m.seed)
        replay(g, events)
        game = g
        refresh()
        if (g.finished) recordMatch()
    }

    private fun applyRemoteEvent(e: MatchEvent) {
        val g = game ?: return
        if (onlineMatch?.id != e.matchId) return
        val before = g.snapshot()
        when (e.kind) {
            MatchEvent.KIND_THROW -> e.segment?.let { g.throwDart(it, e.x, e.y, e.at, e.hold) }
            MatchEvent.KIND_NEXT -> g.next()
            MatchEvent.KIND_UNDO -> { g.undo(); refresh(); caller.beep(); return }
        }
        afterEvent(before)
        if (!g.finished && isMyTurn && before.currentPlayer != g.current) caller.callPlayer(g.players[g.current].name)
    }

    fun onlineCanUndo(): Boolean = onlineMatch == null || online.canUndo()

    // ---------- Onboarding / Umstieg ----------

    /** Erster Start: Profil anlegen (oder ersten Spieler umbenennen) und als Dashboard-Profil setzen. */
    fun finishOnboarding(name: String, color: Long) {
        val trimmed = name.trim().ifEmpty { "Spieler 1" }
        // Einziger Spieler (Standard "Spieler 1" oder bestehende Ein-Personen-Installation) wird das Profil – sonst
        // entsteht ein Duplikat ohne Verlauf und die Statistik des Profils bleibt leer.
        val existing = players.value.filter { !it.isBot }.singleOrNull() ?: players.value.firstOrNull { it.name == "Spieler 1" }
        val profile = if (existing != null) existing.copy(name = trimmed, color = color).also { repo.updatePlayer(it) }
        else Player(name = trimmed, color = color).also { repo.addPlayer(it) }
        _lobbyPlayers.value = listOf(profile)
        repo.updateSettings { it.copy(onboardingDone = true, profilePlayerId = profile.id) }
        track(com.google.firebase.analytics.FirebaseAnalytics.Event.TUTORIAL_COMPLETE)
        backStack.clear()
        _screen.value = Screen.Home
    }

    /** Trainingsmodus direkt mit dem Profilspieler starten (Home: „Training des Tages“). */
    fun startTraining(mode: GameMode) {
        _lobbyPlayers.value = listOfNotNull(players.value.firstOrNull { it.id == settings.value.profilePlayerId } ?: players.value.firstOrNull { !it.isBot })
        setMode(mode); startGame()
    }

    /** "Gegner finden" ohne Online-Matchmaking: Bot auf dem Niveau des Profils. */
    fun playVsMatchedBot() {
        val s = settings.value
        val profile = players.value.firstOrNull { it.id == s.profilePlayerId } ?: players.value.firstOrNull() ?: return
        val x01 = matches.value.filter { it.mode == GameMode.X01 }.mapNotNull { m -> m.players.firstOrNull { it.playerId == profile.id } }
        val darts = x01.sumOf { it.dartsThrown }
        val avg = if (darts == 0) 45.0 else x01.sumOf { it.pointsScored }.toDouble() / darts * 3
        val level = (1..11).minByOrNull { kotlin.math.abs(Player.botAverage(it) - avg) } ?: 4
        _lobbyPlayers.value = listOf(profile, Player.bot(level))
        track("matched_bot", "level" to level)
        if (_lobbySettings.value.mode != GameMode.X01) _lobbySettings.value = GameSettings(mode = GameMode.X01, baseScore = 501, legs = 3)
        startGame()
    }

    // ---------- Remote Scoring ----------

    fun startRemote() {
        if (remote.start()) {
            track("remote_started")
            _remoteUrl.value = remote.localAddress()?.let { "http://$it:${RemoteServer.PORT}" } ?: "http://<IP>:${RemoteServer.PORT}"
            repo.updateSettings { it.copy(remoteEnabled = true) }
        }
    }

    fun stopRemote() {
        remote.stop(); _remoteUrl.value = null
        repo.updateSettings { it.copy(remoteEnabled = false) }
    }

    private fun remoteState(): RemoteServer.RemoteState {
        val g = game; val st = _gameState.value
        val lensStatus = lens.status.value
        val boardStatus = when {
            !lensStatus.running -> "Stopped"
            System.currentTimeMillis() - lensTakeoutAt < 1200 || lensStatus.phase == com.freedarts.scorer.lens.DartDetector.Phase.TAKEOUT -> "Takeout"
            else -> "Throw"
        }
        val boardThrows = lens.detections.value.map { RemoteServer.BoardThrow(it.segment.name, it.boardX, it.boardY) }
        val boardTips = if (lensStatus.running) lens.lastTips else emptyList()
        if (g == null || st == null) return RemoteServer.RemoteState(false, lens = if (lensStatus.running) "Lens: ${lensStatus.message}" else "", boardStatus = boardStatus, boardThrows = boardThrows, boardTips = boardTips, tipSeq = lens.tipSeq)
        val title = g.settings.mode.title + (if (g.settings.mode == GameMode.X01) " ${g.settings.baseScore}" else "")
        val headline = st.headline + (if (st.visitLocked) " · Darts entnehmen" else "")
        return RemoteServer.RemoteState(
            hasGame = true, title = title, headline = headline, banner = st.banner, checkout = st.checkoutHint,
            visit = st.currentVisit.map { it.name }, visitSum = st.currentVisit.sumOf { it.score },
            players = st.players.mapIndexed { i, p ->
                RemoteServer.RemotePlayer(p.player.name, p.score, p.detail, p.legs, p.sets, i == st.currentPlayer && !st.finished, p.isOut, p.history.map { it.label },
                    color = "#%06X".format(p.player.color and 0xFFFFFF), avatar = p.player.avatar, isBot = p.player.isBot)
            },
            finished = st.finished, winner = st.winnerIndex?.let { st.players.getOrNull(it)?.player?.name },
            lens = if (lensStatus.running) "Lens: ${lensStatus.message}" else "", lensReady = lensStatus.setup == LensController.Setup.READY,
            boardStatus = boardStatus, boardThrows = boardThrows, boardTips = boardTips, tipSeq = lens.tipSeq,
        )
    }

    /** Kurzes haptisches Feedback (Dart erkannt/eingegeben, Undo, Next); [ms] 15 = Tick, 40 = Leg/Set. */
    private fun haptic(ms: Long = 15) { if (settings.value.haptics) vibrate(ms) }

    private fun vibrate(ms: Long = 90) {
        try {
            val app = getApplication<Application>()
            val v = if (Build.VERSION.SDK_INT >= 31) (app.getSystemService(Application.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") app.getSystemService(Application.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
    }

    /** Dart der laufenden (oder zuletzt abgeschlossenen) Aufnahme korrigieren – wie die Dart-Korrektur bei Autodarts. */
    fun correctDart(index: Int, segment: Segment, x: Float? = null, y: Float? = null) {
        val g = game ?: return
        if (onlineMatch != null) return // Online: Protokoll ist für alle verbindlich, nur Undo
        botJob?.cancel()
        // Lens-Dart der laufenden Aufnahme: Trainingsbild mit der korrigierten Spitze sichern (Position aus dem Tipp aufs Board)
        val fromLens = _gameState.value?.currentVisit?.let { it.isNotEmpty() && lens.detections.value.size == it.size } == true
        if (g.correctDart(index, segment, x, y)) {
            refresh(); caller.beep(); track("dart_corrected", "input" to inputSource())
            if (fromLens) lens.relabel(index, segment, x, y)
        }
        if (g.players[g.current].isBot && !g.finished) scheduleBot()
    }

    fun correctableDarts(): List<Segment> = game?.correctableDarts() ?: emptyList()

    // ---------- Navigation ----------

    fun navigate(target: Screen) {
        if (_screen.value != target) backStack.addLast(_screen.value)
        _screen.value = target
        analytics.logEvent(com.google.firebase.analytics.FirebaseAnalytics.Event.SCREEN_VIEW, android.os.Bundle().apply {
            putString(com.google.firebase.analytics.FirebaseAnalytics.Param.SCREEN_NAME, target::class.simpleName ?: "?")
        })
    }

    /** true = konsumiert, false = App darf beendet werden. */
    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: if (_screen.value != Screen.Home) Screen.Home else return false
        // Nach dem Ergebnis nicht wieder ins Match springen
        _screen.value = if (prev == Screen.Match && game?.finished != false) Screen.Home else prev
        return true
    }

    fun goHome() { caller.stop(); backStack.clear(); _screen.value = Screen.Home }

    /** Tab der unteren Navigation: kein Backstack, Zurück führt immer auf Home. */
    fun switchTab(target: Screen) { backStack.clear(); _screen.value = target }

    // ---------- Einstellungen ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        repo.updateSettings(transform)
        applySettings()
    }

    private fun applySettings() {
        analytics.setAnalyticsCollectionEnabled(settings.value.analyticsEnabled)
        runCatching { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = settings.value.analyticsEnabled }
        caller.enabled = settings.value.callerEnabled
        caller.soundEffects = settings.value.soundEffects
        caller.minScore = settings.value.callerMinScore
        caller.setVoice(settings.value.callerEnglish, settings.value.callerVoice)
    }

    fun setInputMethod(m: InputMethod) {
        _inputMethod.value = m
        repo.updateSettings { it.copy(inputMethod = m) }
    }

    // ---------- Spieler ----------

    fun addPlayer(name: String, color: Long, avatar: String? = null) = repo.addPlayer(Player(name = name.trim(), color = color, avatar = avatar))
    fun updatePlayer(player: Player) {
        repo.updatePlayer(player)
        _lobbyPlayers.update { list -> list.map { if (it.id == player.id) player else it } }
    }
    fun removePlayer(id: String) {
        repo.removePlayer(id)
        _lobbyPlayers.update { list -> list.filter { it.id != id } }
    }

    // ---------- Lobby ----------

    fun setLobbySettings(s: GameSettings) { _lobbySettings.value = s }
    fun setMode(mode: GameMode) { _lobbySettings.update { it.withModeDefaults(mode) } }

    fun toggleLobbyPlayer(p: Player) {
        _lobbyPlayers.update { list -> if (list.any { it.id == p.id }) list.filter { it.id != p.id } else list + p }
    }
    fun addBot(level: Int) { _lobbyPlayers.update { list -> if (list.size >= 6) list else list + Player.bot(level, list.count { it.isBot } + 1) } }
    fun removeBot() { _lobbyPlayers.update { list -> list.filter { !it.isBot } } }
    fun moveLobbyPlayer(from: Int, to: Int) {
        _lobbyPlayers.update { list ->
            if (from !in list.indices || to !in list.indices) list
            else list.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    fun shuffleLobbyPlayers() { _lobbyPlayers.update { it.shuffled() } }

    fun canStart(): Boolean = _lobbyPlayers.value.isNotEmpty()

    fun startGame() {
        val ps = _lobbyPlayers.value
        if (ps.isEmpty()) return
        val gs = _lobbySettings.value
        repo.updateSettings { it.copy(lastGameSettings = gs, lastPlayerIds = ps.map { p -> p.id }) }
        launchGame(ps, gs)
        navigate(Screen.Match)
    }

    /** "Sofort spielen": letzte Lobby-Einstellungen. */
    fun playNow() {
        if (_lobbyPlayers.value.isEmpty()) _lobbyPlayers.value = players.value.take(1)
        startGame()
    }

    // ---------- Turnier ----------

    fun startTournament(mode: TournamentMode) {
        val ps = _lobbyPlayers.value
        if (ps.size < 2) return
        repo.updateSettings { it.copy(lastGameSettings = _lobbySettings.value, lastPlayerIds = ps.map { p -> p.id }) }
        repo.setTournament(Tournament.create(mode, ps, _lobbySettings.value))
        track("tournament_start", "format" to mode.name, "players" to ps.size, "online" to false)
        navigate(Screen.Tournament)
    }

    /** Host: Turnier mit den Spielern der Online-Lobby; jedes Spiel ist ein Online-Match der beiden Beteiligten. */
    fun startOnlineTournament(mode: TournamentMode) {
        val l = online.lobby.value ?: return
        val ps = l.sortedPlayers.map { Player(id = it.userId, name = it.name, color = it.color, avatar = it.avatar) }
        if (ps.size < 2 || !online.isHost) return
        online.setTournament(Tournament.create(mode, ps, l.settings))
        track("tournament_start", "format" to mode.name, "players" to ps.size, "online" to true)
        navigate(Screen.Tournament)
    }

    fun playTournamentMatch(index: Int) {
        val t = tournament.value ?: return
        val m = t.matches.getOrNull(index)?.takeIf { it.open } ?: return
        if (online.lobby.value?.tournament != null) {
            if (!online.isHost) return
            tournamentMatch = index
            online.startMatch(listOf(t.players[m.a!!].id, t.players[m.b!!].id))
            return
        }
        launchGame(listOf(t.players[m.a!!], t.players[m.b!!]), t.settings)
        tournamentMatch = index
        navigate(Screen.Match)
    }

    fun endTournament() {
        if (online.lobby.value?.tournament != null) online.setTournament(null) else repo.setTournament(null)
        tournamentMatch = null
    }

    /** Nach Ergebnis oder Abbruch zurück zur Turnierübersicht. */
    private fun backToTournament() {
        tournamentMatch = null; game = null; _gameState.value = null
        backStack.clear(); _screen.value = Screen.Tournament
    }

    fun rematch() {
        val g = game ?: return
        caller.stop()
        if (tournamentMatch != null) return backToTournament()
        if (onlineMatch != null) {
            // Online: zurück in die Lobby, der Host startet das nächste Match
            onlineMatch = null; game = null; _gameState.value = null
            backStack.clear(); _screen.value = Screen.OnlineLobby
            return
        }
        launchGame(g.players, g.settings)
        track("rematch")
        backStack.clear()
        _screen.value = Screen.Match
    }

    private fun launchGame(ps: List<Player>, gs: GameSettings) {
        botJob?.cancel()
        recorded = false
        tournamentMatch = null
        _lastRecord.value = null
        game = GameFactory.create(ps, gs)
        refresh()
        game?.let { track("match_start", *gameParams(it)) }
        caller.callPlayer(ps.first().name)
        if (ps.first().isBot) scheduleBot()
    }

    fun abortGame() {
        botJob?.cancel(); autoNextJob?.cancel(); caller.stop()
        game?.takeIf { !it.finished && !isSpectator }?.let { track("match_abort", *gameParams(it), "darts" to it.throwLog.size) }
        game = null
        _gameState.value = null
        if (isSpectator) return stopSpectating()
        if (onlineMatch != null) {
            onlineMatch = null
            online.abortMatch()
            backStack.clear(); _screen.value = Screen.OnlineLobby
            return
        }
        if (tournamentMatch != null) return backToTournament()
        goHome()
    }

    // ---------- Match ----------

    /**
     * Dart eintragen. Manuelle Eingabe ([fromBoard] = false) schließt die Aufnahme nach dem dritten Dart, Bust oder
     * Checkout sofort ab. Autoscoring (Lens / Board Manager) lässt die Aufnahme gesperrt, bis der Takeout erkannt
     * wird – weitere erkannte Darts werden bis dahin ignoriert (wie bei Autodarts).
     */
    fun throwDart(segment: Segment, x: Float? = null, y: Float? = null, fromBoard: Boolean = false) {
        val g = game ?: return
        if (g.finished || g.visitComplete || !isMyTurn) return
        val before = g.snapshot()
        val hold = fromBoard && !g.players[g.current].isBot
        val at = System.currentTimeMillis()
        g.throwDart(segment, x, y, at, hold = hold)
        haptic()
        if (onlineMatch != null) {
            online.sendEvent(MatchEvent.KIND_THROW, segment, x, y, hold, at)
            if (fromBoard) lens.detections.value.lastOrNull()?.snapshot?.let { shareSnapshot(at, it) }
        }
        afterEvent(before)
    }

    /** Referee-Bild verkleinern (max. 160 px, JPEG) und als Ticker-Bild lokal zeigen und an die Mitspieler senden. */
    private fun shareSnapshot(at: Long, bmp: android.graphics.Bitmap) {
        _snapshots.update { it + (at to bmp.asImageBitmap()) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val scale = 160f / maxOf(bmp.width, bmp.height)
            val small = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
            val out = java.io.ByteArrayOutputStream()
            small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
            online.sendSnapshot(at, android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP))
        }
    }

    /** Gesamtscore einer Aufnahme (Total-Score-Eingabe). Zerlegt in bis zu 3 Darts. */
    fun enterVisitTotal(total: Int) {
        val g = game ?: return
        if (g.finished || g.visit.isNotEmpty() || g.visitComplete || g.bullOffActive || !isMyTurn) return
        val darts = decompose(total, g)
        val before = g.snapshot()
        for (d in darts) {
            if (g.finished || g.visitComplete) break
            val cur = g.current
            val at = System.currentTimeMillis()
            g.throwDart(d, at = at)
            if (onlineMatch != null) online.sendEvent(MatchEvent.KIND_THROW, d, null, null, false, at)
            if (g.current != cur || g.visit.isEmpty()) break
        }
        afterEvent(before)
    }

    private fun decompose(total: Int, g: DartGame): List<Segment> {
        val st = g.snapshot()
        val remaining = st.players[st.currentPlayer].score.toIntOrNull()
        val outMode = g.settings.outMode
        if (g.settings.mode == GameMode.X01 && remaining != null) {
            if (total == remaining) Checkout.bestRoute(total, 3, outMode)?.let { return it }
            if (total > remaining) return listOf(Segment.triple(20), Segment.triple(20), Segment.triple(20))
        }
        // Beliebige 3-Dart-Zerlegung (Straight Out = jede Kombination); nicht werfbare Summen (z. B. 179) → nichts eintragen
        if (total <= 0) return listOf(Segment.MISS, Segment.MISS, Segment.MISS)
        val route = Checkout.bestRoute(total, 3, OutMode.STRAIGHT) ?: return emptyList()
        return route + List(3 - route.size) { Segment.MISS }
    }

    fun undo() {
        val g = game ?: return
        if (onlineMatch != null && !online.canUndo()) return
        botJob?.cancel(); autoNextJob?.cancel()
        val removed = g.undo()
        haptic()
        if (onlineMatch != null) online.sendEvent(MatchEvent.KIND_UNDO)
        when (removed) {
            is com.freedarts.scorer.engine.GameEvent.Throw -> toasts.tryEmit(Toast("Rückgängig: ${removed.segment.name}", "Wiederherstellen") { throwDart(removed.segment, removed.x, removed.y, fromBoard = removed.hold) })
            null -> {}
            else -> toasts.tryEmit(Toast("Rückgängig: Spielerwechsel"))
        }
        refresh()
        caller.beep()
        if (g.players[g.current].isBot && !g.finished) scheduleBot()
    }

    fun nextPlayer() {
        val g = game ?: return
        if (g.finished || !isMyTurn) return
        val before = g.snapshot()
        g.next()
        haptic()
        if (onlineMatch != null) online.sendEvent(MatchEvent.KIND_NEXT)
        afterEvent(before)
    }

    /** Automatic Next Player: läuft nach jedem Dart neu an, wenn die Aufnahme noch nicht voll ist. */
    private fun scheduleAutoNext() {
        autoNextJob?.cancel()
        val delayMs = settings.value.autoNextDelayMs
        val g = game ?: return
        if (delayMs <= 0 || g.finished || g.visit.isEmpty() || g.players[g.current].isBot) return
        autoNextJob = viewModelScope.launch {
            delay(delayMs)
            val cur = game ?: return@launch
            if (!cur.finished && cur.visit.isNotEmpty() && !cur.players[cur.current].isBot) nextPlayer()
        }
    }

    private fun afterEvent(before: GameState) {
        val g = game ?: return
        refresh()
        scheduleAutoNext()
        val after = _gameState.value ?: return
        val s = settings.value
        val playerName = before.players[before.currentPlayer].player.name
        adaptBot(before, after)

        if (after.finished) {
            caller.ding()
            caller.callGameShot(after.winnerIndex?.let { g.players[it].name } ?: "Niemand")
            recordMatch()
            return
        }
        if (before.bullOff) {
            // Ausbullen: Ergebnis bzw. nächsten Werfer ansagen
            after.banner?.let { caller.say(it) } ?: caller.say("Bull-off: ${after.players[after.currentPlayer].player.name}")
            if (g.players[g.current].isBot) scheduleBot()
            return
        }
        val visitEnded = after.currentPlayer != before.currentPlayer || after.currentVisit.isEmpty()
        when {
            after.banner == "Bust" -> { caller.error(); caller.callBust() }
            after.banner == "Leg gewonnen" || after.banner == "Set gewonnen" -> { caller.ding(); caller.callLeg(playerName); haptic(40) }
            after.banner == "Set unentschieden" -> { caller.ding(); caller.say(after.banner) }
            visitEnded && g.settings.mode == GameMode.X01 -> {
                val lastVisit = before.players[before.currentPlayer].let { p -> after.players[before.currentPlayer].history.lastOrNull() }
                val score = lastVisit?.label?.toIntOrNull()
                if (score != null && s.callerCallsEveryVisit) caller.callScore(score)
                val rem = after.players[before.currentPlayer].score.toIntOrNull()
                if (rem != null && Checkout.isFinishable(rem, 3, g.settings.outMode) && s.callerEnabled) caller.callRemaining(playerName, rem)
            }
            !visitEnded && s.countEachThrow -> caller.say(after.currentVisit.lastOrNull()?.name ?: "")
            visitEnded && after.banner != null -> caller.say(after.banner)
        }
        if (visitEnded) caller.beep()
        if (g.players[g.current].isBot) scheduleBot()
    }

    private fun refresh() { _gameState.value = game?.snapshot(); predict() }

    // ---------- Vorhersage ----------

    /** Kurze Rückmeldung als Snackbar (App-weit), optional mit Aktion. */
    data class Toast(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)
    val toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 4)

    /** Live-Vorhersage fürs laufende X01-Leg (Siegchance, erwartete Aufnahme, Checkout-Chance des Werfers). */
    val prediction = MutableStateFlow<Predictor.Prediction?>(null)
    private var predictJob: Job? = null
    /** Online-Average der Mitspieler (Profil), Schlüssel = Spieler-ID. */
    private val opponentAvg = HashMap<String, Double>()

    private fun recentAverage(playerId: String): Double? = matches.value.filter { it.mode == GameMode.X01 }
        .mapNotNull { m -> m.players.firstOrNull { it.playerId == playerId }?.takeIf { it.dartsThrown > 0 }?.average3 }
        .take(10).takeIf { it.isNotEmpty() }?.average()

    /** Streuung eines Spielers: Bot-Stufe; sonst echte Lens-Würfe (Verlauf + laufendes Spiel); sonst Average (Profil oder Verlauf); sonst 45 mm. */
    private fun sigmaFor(p: Player, g: DartGame?): Double {
        if (p.botLevel == Player.ADAPTIVE) return adaptiveSigma()
        if (p.isBot) return Bot.sigma(p.botLevel)
        val live = g?.let { AimAdvisor.radials(it.throwLog, it.players.indexOf(p), assumeT20 = it.settings.mode == GameMode.X01) } ?: emptyList()
        AimAdvisor.sigmaOf(AimAdvisor.radials(matches.value, p.id) + live)?.let { return it }
        val avg = opponentAvg[p.id]?.takeIf { it > 0 } ?: recentAverage(p.id) ?: return 45.0
        return Bot.sigmaForAverage(avg)
    }

    private fun predict() {
        predictJob?.cancel()
        val g = game; val s = _gameState.value
        if (g == null || s == null || g.settings.mode != GameMode.X01 || s.finished || s.bullOff || g.players.size < 2) { prediction.value = null; return }
        val remaining = IntArray(g.players.size) { s.players[it].score.toIntOrNull() ?: -1 }
        if (remaining.any { it <= 0 }) { prediction.value = null; return }
        val onThrow = s.currentPlayer
        val dartsLeft = if (s.visitLocked) 0 else 3 - s.currentVisit.size
        predictJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val sigmas = DoubleArray(g.players.size) { sigmaFor(g.players[it], g) }
            prediction.value = Predictor.leg(remaining, onThrow, dartsLeft, sigmas, g.settings)
        }
    }

    /** Prognose fürs erste Leg einer Lobby (Siegchance je Spieler), nur X01. */
    suspend fun forecast(players: List<Player>, gs: GameSettings): DoubleArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        if (gs.mode != GameMode.X01 || players.size < 2) return@withContext null
        val remaining = IntArray(players.size) { gs.handicaps[players[it].id] ?: gs.baseScore }
        Predictor.leg(remaining, 0, 3, DoubleArray(players.size) { sigmaFor(players[it], null) }, gs, sims = 200).legWin
    }

    private fun scheduleBot() {
        botJob?.cancel()
        val g = game ?: return
        botJob = viewModelScope.launch {
            val delayMs = settings.value.botDelayMillis
            delay(delayMs)
            while (!g.finished && g.players[g.current].isBot) {
                val bot = g.players[g.current]
                val seg = Bot.throwAt(g.botAim(), if (bot.botLevel == Player.ADAPTIVE) adaptiveSigma() else Bot.sigma(bot.botLevel))
                val before = g.snapshot()
                g.throwDart(seg)
                afterEventQuiet(before)
                if (g.finished) break
                delay(delayMs)
            }
        }
    }

    /** Streuung des Bots „Wie ich“: gespeicherter Wert, sonst aus dem X01-Average des ersten Menschen im Spiel (Fallback 45 mm). */
    private fun adaptiveSigma(): Double {
        val saved = settings.value.adaptiveBotSigma
        if (saved > 0) return saved
        val human = game?.players?.firstOrNull { !it.isBot }
        val avgs = matches.value.filter { it.mode == GameMode.X01 }.mapNotNull { m -> m.players.firstOrNull { it.playerId == human?.id }?.average3 }.take(10)
        val sigma = if (avgs.isEmpty()) 45.0 else Bot.sigmaForAverage(avgs.average())
        updateSettings { it.copy(adaptiveBotSigma = sigma) }
        return sigma
    }

    /** Leg zu Ende: Bot „Wie ich“ wird stärker, wenn er gewonnen hat, sonst schwächer. */
    // ponytail: ±6 % pro Leg; Elo-artige Anpassung erst, wenn das zu träge oder zu nervös wirkt
    private fun adaptBot(before: GameState, after: GameState) {
        val g = game ?: return
        if (after.banner != "Leg gewonnen" && after.banner != "Set gewonnen" && !after.finished) return
        if (g.players.none { it.botLevel == Player.ADAPTIVE }) return
        val botWon = g.players[before.currentPlayer].botLevel == Player.ADAPTIVE
        val sigma = (adaptiveSigma() * (if (botWon) 1.06 else 0.94)).coerceIn(Bot.sigma(11), Bot.sigma(1))
        updateSettings { it.copy(adaptiveBotSigma = sigma) }
    }

    private fun afterEventQuiet(before: GameState) {
        // Wie afterEvent, aber ohne erneutes Bot-Scheduling (läuft bereits in der Schleife)
        val g = game ?: return
        refresh()
        val after = _gameState.value ?: return
        val playerName = before.players[before.currentPlayer].player.name
        adaptBot(before, after)
        if (after.finished) {
            caller.ding(); caller.callGameShot(after.winnerIndex?.let { g.players[it].name } ?: "Niemand")
            recordMatch(); return
        }
        if (before.bullOff) { after.banner?.let { caller.say(it) }; return }
        val visitEnded = after.currentPlayer != before.currentPlayer || after.currentVisit.isEmpty()
        if (after.banner == "Bust") caller.callBust()
        else if (visitEnded && g.settings.mode == GameMode.X01 && settings.value.callerCallsEveryVisit) {
            after.players[before.currentPlayer].history.lastOrNull()?.label?.toIntOrNull()?.let { caller.callScore(it) }
        }
        if (visitEnded) {
            caller.beep()
            if (!g.players[g.current].isBot) caller.callPlayer(g.players[g.current].name)
        }
    }

    private fun recordMatch() {
        val g = game ?: return
        if (recorded || isSpectator) return
        recorded = true
        val stats = g.players.indices.map { g.playerStats(it) }
        val om = onlineMatch
        if (om != null) online.finishMatch(g.winner?.let { g.players[it].id }, stats)
        var record = MatchRecord(
            id = UUID.randomUUID().toString(),
            mode = g.settings.mode,
            settings = g.settings,
            startedAt = g.startedAt,
            finishedAt = System.currentTimeMillis(),
            winnerId = g.winner?.let { g.players[it].id },
            players = stats,
            throws = g.throwLog,
        )
        // Online: das eigene Konto in der lokalen Statistik dem Profil-Spieler zuordnen
        if (om != null) record = toLocalProfile(record)
        repo.addMatch(record)
        if (online.session.value != null && online.configured) online.saveMatch(record)
        _lastRecord.value = record
        track("match_finished", *gameParams(g), "tournament" to (tournamentMatch != null), "darts" to record.throws.size,
            "minutes" to (record.finishedAt - record.startedAt) / 60_000)
        // Turnier: Sieger eintragen (Unentschieden lässt das Spiel offen, es wird wiederholt)
        val ti = tournamentMatch; val t = tournament.value; val w = g.winner
        if (ti != null && t != null && w != null) {
            val tm = t.matches[ti]
            val result = t.withResult(ti, if (w == 0) tm.a!! else tm.b!!, stats[0].legsWon, stats[1].legsWon)
            if (om != null) { online.setTournament(result); tournamentMatch = null } else repo.setTournament(result)
        }
    }

    fun finishToResult() {
        if (isSpectator) return stopSpectating()
        if (_lastRecord.value == null) recordMatch()
        navigate(Screen.Result)
    }

    /** Spieler und Verlauf als JSON in eine vom Nutzer gewählte Datei (Storage Access Framework). */
    fun exportTo(uri: android.net.Uri) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson().toByteArray()) } }
            .onSuccess { track("data_export") }
    }

    // ---------- Board Manager ----------

    fun connectBoard(host: String, port: Int) {
        updateSettings { it.copy(boardManagerEnabled = true, boardManagerHost = host, boardManagerPort = port) }
        board.connect(host, port)
        track("board_manager_connect")
    }

    fun disconnectBoard() {
        updateSettings { it.copy(boardManagerEnabled = false) }
        board.disconnect()
    }

    private fun onBoardThrow(seg: Segment, x: Float?, y: Float?) {
        val g = game ?: return
        if (_screen.value != Screen.Match || g.finished) return
        if (g.players[g.current].isBot || !isMyTurn) return
        throwDart(seg, x, y, fromBoard = true)
    }

    /** Takeout erkannt: gesperrte oder angefangene Aufnahme beenden, nächster Spieler. */
    private fun onBoardTakeout() {
        val g = game ?: return
        if (_screen.value != Screen.Match || g.finished || g.bullOffActive || !isMyTurn) return
        if ((g.visit.isNotEmpty() || g.visitComplete) && !g.players[g.current].isBot) nextPlayer()
    }

    // ---------- Lens (Kamera) ----------

    fun startLens(owner: LifecycleOwner) {
        lens.setSensitivity(settings.value.lensSensitivity)
        lens.training.enabled = settings.value.lensCaptureTraining
        lens.useFrontCamera = settings.value.lensUseFrontCamera
        lens.exposure = settings.value.lensExposure
        lens.onCalibrationChanged = { pts -> updateSettings { it.copy(lensCalibration = pts) }; track("lens_calibrated", "method" to "auto") }
        lens.start(owner, null)
        updateSettings { it.copy(lensEnabled = true) }
    }

    fun stopLens() {
        lens.stop()
        updateSettings { it.copy(lensEnabled = false) }
    }

    fun setLensCalibration(points: List<Float>) {
        lens.setCalibration(points)
        updateSettings { it.copy(lensCalibration = points) }
        track("lens_calibrated", "method" to "manual")
    }

    fun clearHistory() { repo.clearMatches(); online.deleteAllMatches(); track("history_cleared") }

    override fun onCleared() {
        caller.shutdown()
        board.disconnect()
        lens.stop()
        remote.stop()
        online.shutdown()
    }
}
