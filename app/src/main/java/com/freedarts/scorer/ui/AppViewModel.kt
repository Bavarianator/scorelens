package com.freedarts.scorer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freedarts.scorer.audio.Caller
import com.freedarts.scorer.board.BoardManagerClient
import com.freedarts.scorer.data.Repository
import com.freedarts.scorer.engine.Bot
import com.freedarts.scorer.engine.Checkout
import com.freedarts.scorer.engine.DartGame
import com.freedarts.scorer.engine.GameFactory
import com.freedarts.scorer.engine.GameState
import com.freedarts.scorer.lens.LensController
import androidx.lifecycle.LifecycleOwner
import com.freedarts.scorer.model.AppSettings
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.InputMethod
import com.freedarts.scorer.model.MatchRecord
import com.freedarts.scorer.model.OutMode
import com.freedarts.scorer.model.Player
import com.freedarts.scorer.model.Segment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repository.get(app)
    val caller = Caller(app)
    val board = BoardManagerClient(viewModelScope)
    val lens = LensController(app)

    val players: StateFlow<List<Player>> = repo.players
    val settings: StateFlow<AppSettings> = repo.settings
    val matches: StateFlow<List<MatchRecord>> = repo.matches

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
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
    private var botJob: Job? = null
    private var recorded = false
    private val _inputMethod = MutableStateFlow(settings.value.inputMethod)
    val inputMethod: StateFlow<InputMethod> = _inputMethod

    init {
        applyAudioSettings()
        viewModelScope.launch { board.throws.collect { seg -> onBoardThrow(seg) } }
        viewModelScope.launch { board.takeout.collect { onBoardTakeout() } }
        viewModelScope.launch { lens.throws.collect { seg -> onBoardThrow(seg) } }
        viewModelScope.launch { lens.takeout.collect { onBoardTakeout() } }
        lens.setSensitivity(settings.value.lensSensitivity)
        val s = settings.value
        if (s.boardManagerEnabled) board.connect(s.boardManagerHost, s.boardManagerPort)
    }

    // ---------- Navigation ----------

    fun navigate(target: Screen) {
        if (_screen.value != target) backStack.addLast(_screen.value)
        _screen.value = target
    }

    /** true = konsumiert, false = App darf beendet werden. */
    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        // Nach dem Ergebnis nicht wieder ins Match springen
        _screen.value = if (prev == Screen.Match && game?.finished != false) Screen.Home else prev
        return true
    }

    fun goHome() { backStack.clear(); _screen.value = Screen.Home }

    // ---------- Einstellungen ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        repo.updateSettings(transform)
        applyAudioSettings()
    }

    private fun applyAudioSettings() {
        caller.enabled = settings.value.callerEnabled
        caller.soundEffects = settings.value.soundEffects
    }

    fun setInputMethod(m: InputMethod) {
        _inputMethod.value = m
        repo.updateSettings { it.copy(inputMethod = m) }
    }

    // ---------- Spieler ----------

    fun addPlayer(name: String, color: Long) = repo.addPlayer(Player(name = name.trim(), color = color))
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
    fun addBot(level: Int) { _lobbyPlayers.update { list -> list.filter { !it.isBot } + Player.bot(level) } }
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

    fun rematch() {
        val g = game ?: return
        launchGame(g.players, g.settings)
        backStack.clear()
        _screen.value = Screen.Match
    }

    private fun launchGame(ps: List<Player>, gs: GameSettings) {
        botJob?.cancel()
        recorded = false
        _lastRecord.value = null
        game = GameFactory.create(ps, gs)
        refresh()
        caller.callPlayer(ps.first().name)
        if (ps.first().isBot) scheduleBot()
    }

    fun abortGame() {
        botJob?.cancel()
        game = null
        _gameState.value = null
        goHome()
    }

    // ---------- Match ----------

    fun throwDart(segment: Segment) {
        val g = game ?: return
        if (g.finished) return
        val before = g.snapshot()
        g.throwDart(segment)
        afterEvent(before)
    }

    /** Gesamtscore einer Aufnahme (Total-Score-Eingabe). Zerlegt in bis zu 3 Darts. */
    fun enterVisitTotal(total: Int) {
        val g = game ?: return
        if (g.finished || g.visit.isNotEmpty()) return
        val darts = decompose(total, g)
        val before = g.snapshot()
        for (d in darts) {
            if (g.finished) break
            val cur = g.current
            g.throwDart(d)
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
        val result = ArrayList<Segment>()
        var rest = total.coerceIn(0, 180)
        while (rest > 0 && result.size < 3) {
            val seg = Segment.ALL.filter { it.score <= rest && !it.isBull }.maxByOrNull { it.score } ?: break
            result.add(seg); rest -= seg.score
        }
        while (result.size < 3) result.add(Segment.MISS)
        return result
    }

    fun undo() {
        val g = game ?: return
        botJob?.cancel()
        g.undo()
        refresh()
        caller.beep()
        if (g.players[g.current].isBot && !g.finished) scheduleBot()
    }

    fun nextPlayer() {
        val g = game ?: return
        if (g.finished) return
        val before = g.snapshot()
        g.next()
        afterEvent(before)
    }

    private fun afterEvent(before: GameState) {
        val g = game ?: return
        refresh()
        val after = _gameState.value ?: return
        val s = settings.value
        val playerName = before.players[before.currentPlayer].player.name

        if (after.finished) {
            caller.ding()
            caller.callGameShot(after.winnerIndex?.let { g.players[it].name } ?: "Niemand")
            recordMatch()
            return
        }
        val visitEnded = after.currentPlayer != before.currentPlayer || after.currentVisit.isEmpty()
        when {
            after.banner == "Bust" -> { caller.error(); caller.callBust() }
            after.banner == "Leg gewonnen" || after.banner == "Set gewonnen" -> { caller.ding(); caller.callLeg(playerName) }
            visitEnded && g.settings.mode == GameMode.X01 -> {
                val lastVisit = before.players[before.currentPlayer].let { p -> after.players[before.currentPlayer].history.lastOrNull() }
                val score = lastVisit?.label?.toIntOrNull()
                if (score != null && s.callerCallsEveryVisit) caller.callScore(score)
                val rem = after.players[before.currentPlayer].score.toIntOrNull()
                if (rem != null && rem in 2..170 && s.callerEnabled) caller.callRemaining(playerName, rem)
            }
            !visitEnded && s.countEachThrow -> caller.say(after.currentVisit.lastOrNull()?.name ?: "")
            visitEnded && after.banner != null -> caller.say(after.banner)
        }
        if (visitEnded) caller.beep()
        if (g.players[g.current].isBot) scheduleBot()
    }

    private fun refresh() { _gameState.value = game?.snapshot() }

    private fun scheduleBot() {
        botJob?.cancel()
        val g = game ?: return
        botJob = viewModelScope.launch {
            val delayMs = settings.value.botDelayMillis
            delay(delayMs)
            while (!g.finished && g.players[g.current].isBot) {
                val bot = g.players[g.current]
                val seg = Bot.throwAt(g.botTarget(), bot.botLevel)
                val before = g.snapshot()
                g.throwDart(seg)
                afterEventQuiet(before)
                if (g.finished) break
                delay(delayMs)
            }
        }
    }

    private fun afterEventQuiet(before: GameState) {
        // Wie afterEvent, aber ohne erneutes Bot-Scheduling (läuft bereits in der Schleife)
        val g = game ?: return
        refresh()
        val after = _gameState.value ?: return
        val playerName = before.players[before.currentPlayer].player.name
        if (after.finished) {
            caller.ding(); caller.callGameShot(after.winnerIndex?.let { g.players[it].name } ?: "Niemand")
            recordMatch(); return
        }
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
        if (recorded) return
        recorded = true
        val record = MatchRecord(
            id = UUID.randomUUID().toString(),
            mode = g.settings.mode,
            settings = g.settings,
            startedAt = g.startedAt,
            finishedAt = System.currentTimeMillis(),
            winnerId = g.winner?.let { g.players[it].id },
            players = g.players.indices.map { g.playerStats(it) },
        )
        repo.addMatch(record)
        _lastRecord.value = record
    }

    fun finishToResult() {
        if (_lastRecord.value == null) recordMatch()
        navigate(Screen.Result)
    }

    // ---------- Board Manager ----------

    fun connectBoard(host: String, port: Int) {
        updateSettings { it.copy(boardManagerEnabled = true, boardManagerHost = host, boardManagerPort = port) }
        board.connect(host, port)
    }

    fun disconnectBoard() {
        updateSettings { it.copy(boardManagerEnabled = false) }
        board.disconnect()
    }

    private fun onBoardThrow(seg: Segment) {
        val g = game ?: return
        if (_screen.value != Screen.Match || g.finished) return
        if (g.players[g.current].isBot) return
        throwDart(seg)
    }

    private fun onBoardTakeout() {
        val g = game ?: return
        if (_screen.value != Screen.Match || g.finished) return
        if (g.visit.isNotEmpty() && !g.players[g.current].isBot) nextPlayer()
    }

    // ---------- Lens (Kamera) ----------

    fun startLens(owner: LifecycleOwner) {
        lens.setSensitivity(settings.value.lensSensitivity)
        lens.onCalibrationChanged = { pts -> updateSettings { it.copy(lensCalibration = pts) } }
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
    }

    fun clearHistory() = repo.clearMatches()

    override fun onCleared() {
        caller.shutdown()
        board.disconnect()
        lens.stop()
    }
}
