package dev.paul.kodiremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.paul.kodiremote.tile.NowPlayingTileService
import dev.paul.kodiremote.ui.PagerScreen
import dev.paul.kodiremote.ui.RemoteViewModel
import dev.paul.kodiremote.ui.SettingsScreen

private val KodiColors = ColorScheme(
    primary = Color(0xFF17B2E7),
    onPrimary = Color(0xFF00202C),
    primaryContainer = Color(0xFF0E4F68),
    onPrimaryContainer = Color(0xFFCDEFFF),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = KodiColors) {
                KodiRemoteApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Whatever was changed in the app should show on the tile right away
        NowPlayingTileService.requestUpdate(this)
    }
}

@Composable
fun KodiRemoteApp(vm: RemoteViewModel = viewModel()) {
    // Poll Kodi (and hold the Wi-Fi request) only while the app is on screen
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollWhileVisible() }
    }

    AppScaffold {
        val navController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(navController = navController, startDestination = "remote") {
            composable("remote") {
                PagerScreen(vm, onOpenSettings = { navController.navigate("settings") })
            }
            composable("settings") {
                SettingsScreen(vm, onDone = { navController.popBackStack() })
            }
        }
        LaunchedEffect(Unit) {
            if (!vm.config.value.isConfigured) navController.navigate("settings")
        }
    }
}
