package com.freedarts.scorer

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.screens.BoardScreen
import com.freedarts.scorer.ui.screens.DevicesScreen
import com.freedarts.scorer.ui.screens.FriendsScreen
import com.freedarts.scorer.ui.screens.HelpScreen
import com.freedarts.scorer.ui.screens.OnboardingScreen
import com.freedarts.scorer.ui.screens.HomeScreen
import com.freedarts.scorer.ui.screens.LensScreen
import com.freedarts.scorer.ui.screens.LobbyScreen
import com.freedarts.scorer.ui.screens.MatchScreen
import com.freedarts.scorer.ui.screens.ModeSelectScreen
import com.freedarts.scorer.ui.screens.OnlineLobbyScreen
import com.freedarts.scorer.ui.screens.OnlineScreen
import com.freedarts.scorer.ui.screens.PlayersScreen
import com.freedarts.scorer.ui.screens.ResultScreen
import com.freedarts.scorer.ui.screens.SettingsScreen
import com.freedarts.scorer.ui.screens.StatsScreen
import com.freedarts.scorer.ui.screens.RemoteViewScreen
import com.freedarts.scorer.ui.screens.TournamentScreen
import com.freedarts.scorer.ui.theme.DartColors
import com.freedarts.scorer.ui.theme.FreeDartsTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.online.handleRedirect(intent?.data)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.online.handleRedirect(intent.data)
    }
}

@Composable
fun FreeDartsApp(vm: AppViewModel, onKeepScreenOn: (Boolean) -> Unit) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    LaunchedEffect(screen, settings.keepScreenOn) { onKeepScreenOn((screen == Screen.Match || screen == Screen.Lens) && settings.keepScreenOn) }

    BackHandler(enabled = screen != Screen.Home && screen != Screen.Onboarding) {
        if (screen == Screen.Match) return@BackHandler // Abbruch nur über den Button im Match
        vm.back()
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { ScreenContent(screen, vm) }
        // Untere Navigation wie in einer normalen Android-App: nur auf den vier Hauptseiten
        if (screen in tabs.values) NavigationBar(containerColor = DartColors.BottomBar) {
            tabs.forEach { (label, target) ->
                NavigationBarItem(selected = screen == target, onClick = { vm.switchTab(target) }, label = { Text(label) },
                    icon = { Icon(when (target) { Screen.Home -> Icons.Default.Home; Screen.Lens -> Icons.Default.CameraAlt; Screen.Stats -> Icons.Default.BarChart; else -> Icons.Default.Settings }, label) })
            }
        }
    }
}

private val tabs = linkedMapOf("Home" to Screen.Home, "Lens" to Screen.Lens, "Statistik" to Screen.Stats, "Einstellungen" to Screen.Settings)

@Composable
private fun ScreenContent(screen: Screen, vm: AppViewModel) {
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
        Screen.Devices -> DevicesScreen(vm)
        Screen.Onboarding -> OnboardingScreen(vm)
        Screen.Help -> HelpScreen(vm)
        Screen.Online -> OnlineScreen(vm)
        Screen.OnlineLobby -> OnlineLobbyScreen(vm)
        Screen.Friends -> FriendsScreen(vm)
        Screen.Tournament -> TournamentScreen(vm)
        Screen.RemoteView -> RemoteViewScreen(vm)
    }
}
