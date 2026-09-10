package com.freedarts.scorer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.screens.BoardScreen
import com.freedarts.scorer.ui.screens.HomeScreen
import com.freedarts.scorer.ui.screens.LensScreen
import com.freedarts.scorer.ui.screens.LobbyScreen
import com.freedarts.scorer.ui.screens.MatchScreen
import com.freedarts.scorer.ui.screens.ModeSelectScreen
import com.freedarts.scorer.ui.screens.PlayersScreen
import com.freedarts.scorer.ui.screens.ResultScreen
import com.freedarts.scorer.ui.screens.SettingsScreen
import com.freedarts.scorer.ui.screens.StatsScreen
import com.freedarts.scorer.ui.theme.FreeDartsTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeDartsTheme {
                Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    FreeDartsApp(vm, onKeepScreenOn = { on ->
                        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    })
                }
            }
        }
    }
}

@Composable
fun FreeDartsApp(vm: AppViewModel, onKeepScreenOn: (Boolean) -> Unit) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    LaunchedEffect(screen, settings.keepScreenOn) { onKeepScreenOn((screen == Screen.Match || screen == Screen.Lens) && settings.keepScreenOn) }

    BackHandler(enabled = screen != Screen.Home) {
        if (screen == Screen.Match) return@BackHandler // Abbruch nur über den Button im Match
        vm.back()
    }

    when (screen) {
        Screen.Home -> HomeScreen(vm)
        Screen.Lobby -> LobbyScreen(vm)
        Screen.Players -> PlayersScreen(vm)
        Screen.Match -> MatchScreen(vm)
        Screen.Result -> ResultScreen(vm)
        Screen.Stats -> StatsScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.Board -> BoardScreen(vm)
        Screen.Lens -> LensScreen(vm)
        Screen.ModeSelect -> ModeSelectScreen(vm)
        Screen.History -> StatsScreen(vm, startTab = 1)
    }
}
