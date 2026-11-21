package com.shavlegi.brawlarena

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.shavlegi.brawlarena.game.BrawlerDef
import com.shavlegi.brawlarena.game.GameEngine
import com.shavlegi.brawlarena.game.GameScreen
import com.shavlegi.brawlarena.game.MatchResult
import com.shavlegi.brawlarena.game.MenuScreen
import com.shavlegi.brawlarena.game.ResultScreen
import com.shavlegi.brawlarena.game.trophiesForPlacement
import com.shavlegi.brawlarena.ui.theme.BrawlArenaTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrawlArenaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrawlArenaApp()
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Menu : Screen
    data object Game : Screen
    data object Result : Screen
}

@Composable
private fun BrawlArenaApp() {
    val context = LocalContext.current
    val store = remember { TrophyStore(context) }
    val scope = rememberCoroutineScope()
    val trophies by store.trophies.collectAsState(initial = 0)
    val selectedBrawlerId by store.brawlerId.collectAsState(initial = "grom")
    val language by store.language.collectAsState(initial = "ka")

    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    var engine by remember { mutableStateOf<GameEngine?>(null) }
    var result by remember { mutableStateOf<MatchResult?>(null) }

    when (screen) {
        Screen.Menu -> MenuScreen(
            trophies = trophies,
            selectedBrawlerId = selectedBrawlerId,
            language = language,
            onSelectBrawler = { id -> scope.launch { store.setBrawler(id) } },
            onSelectLanguage = { code ->
                scope.launch { store.setLanguage(code) }
                LocaleHelper.applyLanguage(code)
            },
            onPlay = {
                engine = GameEngine(BrawlerDef.byId(selectedBrawlerId))
                screen = Screen.Game
            }
        )
        Screen.Game -> {
            val game = engine
            if (game != null) {
                GameScreen(
                    engine = game,
                    onEnd = { finished ->
                        val placement = finished.playerPlacement
                        val delta = trophiesForPlacement(placement)
                        scope.launch { store.addTrophies(delta) }
                        result = MatchResult(
                            placement = placement,
                            kills = finished.player.kills,
                            trophyDelta = delta,
                            totalTrophies = (trophies + delta).coerceAtLeast(0),
                            victory = placement == 1
                        )
                        screen = Screen.Result
                    }
                )
            }
        }
        Screen.Result -> {
            val r = result
            if (r != null) {
                ResultScreen(
                    result = r,
                    onPlayAgain = {
                        engine = GameEngine(BrawlerDef.byId(selectedBrawlerId))
                        screen = Screen.Game
                    },
                    onMenu = { screen = Screen.Menu }
                )
            }
        }
    }
}
