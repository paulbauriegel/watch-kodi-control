package dev.paul.kodiremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
fun VolumeScreen(vm: RemoteViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .crownSteps { vm.changeVolumeBy(it * 2) },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { state.volume / 100f },
            modifier = Modifier.fillMaxSize().padding(2.dp),
            strokeWidth = 6.dp,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (state.muted) "Muted" else "Volume",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "${state.volume}", style = MaterialTheme.typography.displayMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteButton(KodiIcons.Remove, "Volume down", { vm.changeVolumeBy(-5) }, size = 42.dp)
                RemoteButton(
                    icon = if (state.muted) KodiIcons.VolumeOff else KodiIcons.VolumeUp,
                    description = "Mute",
                    onClick = vm::toggleMute,
                    size = 50.dp,
                    primary = true,
                )
                RemoteButton(KodiIcons.Add, "Volume up", { vm.changeVolumeBy(5) }, size = 42.dp)
            }
            Text(
                text = "Turn crown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
