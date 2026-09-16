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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedarts.scorer.ui.AppViewModel
import com.freedarts.scorer.ui.Screen
import com.freedarts.scorer.ui.screens.BoardScreen
import com.freedarts.scorer.ui.screens.AchievementsScreen
import com.freedarts.scorer.ui.screens.CheckoutTableScreen
import com.freedarts.scorer.ui.screens.MatchDetailScreen
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
            val themeMode by vm.settings.collectAsStateWithLifecycle()
            FreeDartsTheme(themeMode.theme) {
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

    // Eine Snackbar für Online-Fehler, Hinweise und Undo („Wiederherstellen“) – statt drei verschiedener Fehlerkarten
    val snackbar = remember { SnackbarHostState() }
    val error by vm.online.error.collectAsStateWithLifecycle()
    val notice by vm.online.notice.collectAsStateWithLifecycle()
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it, actionLabel = "OK", duration = SnackbarDuration.Long); if (vm.online.error.value == it) vm.online.error.value = null } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short); if (vm.online.notice.value == it) vm.online.notice.value = null } }
    LaunchedEffect(Unit) {
        vm.toasts.collect { t ->
            val r = snackbar.showSnackbar(t.text, actionLabel = t.action, duration = SnackbarDuration.Short, withDismissAction = t.action == null)
            if (r == SnackbarResult.ActionPerformed) t.onAction?.invoke()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (settings.animations) androidx.compose.animation.Crossfade(targetState = screen, label = "screen") { ScreenContent(it, vm) }
            else ScreenContent(screen, vm)
        }
        // Untere Navigation wie in einer normalen Android-App: nur auf den vier Hauptseiten
        if (screen in tabs.values) NavigationBar(containerColor = DartColors.BottomBar) {
            tabs.forEach { (label, target) ->
                NavigationBarItem(selected = screen == target, onClick = { vm.switchTab(target) }, label = { Text(label) },
                    icon = { Icon(painterResource(tabIcons.getValue(target)), label, Modifier.size(22.dp)) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = DartColors.Text, indicatorColor = DartColors.Primary,
                        unselectedIconColor = DartColors.TextMuted, unselectedTextColor = DartColors.TextMuted))
            }
        }
    }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (screen in tabs.values) 84.dp else 8.dp))
    }
}

private val tabs = linkedMapOf("Home" to Screen.Home, "Lens" to Screen.Lens, "Statistik" to Screen.Stats, "Einstellungen" to Screen.Settings)
/** Lucide-Strichicons (res/drawable/ic_nav_*): Haus, Blende, Balken, Schieberegler. */
private val tabIcons = mapOf(Screen.Home to R.drawable.ic_nav_home, Screen.Lens to R.drawable.ic_nav_lens, Screen.Stats to R.drawable.ic_nav_stats, Screen.Settings to R.drawable.ic_nav_settings)

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
        Screen.MatchDetail -> MatchDetailScreen(vm)
        Screen.CheckoutTable -> CheckoutTableScreen(vm)
        is Screen.Achievements -> AchievementsScreen(vm, screen.playerId)
    }
}
