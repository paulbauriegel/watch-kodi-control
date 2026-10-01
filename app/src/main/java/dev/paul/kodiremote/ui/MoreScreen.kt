package dev.paul.kodiremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text

/** The less used controls that don't fit on Now Playing: previous / stop / next, and settings. */
@Composable
fun MoreScreen(vm: RemoteViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val active = state.nowPlaying != null
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteButton(KodiIcons.SkipPrevious, "Previous", vm::previous, size = 50.dp, enabled = active)
            RemoteButton(KodiIcons.Stop, "Stop", vm::stop, size = 50.dp, enabled = active)
            RemoteButton(KodiIcons.SkipNext, "Next", vm::next, size = 50.dp, enabled = active)
        }
        FilledTonalButton(
            onClick = onOpenSettings,
            icon = { Icon(KodiIcons.Settings, contentDescription = null) },
            label = { Text("Settings") },
        )
    }
}
