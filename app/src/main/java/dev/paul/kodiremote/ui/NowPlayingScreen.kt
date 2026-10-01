package dev.paul.kodiremote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dev.paul.kodiremote.kodi.NowPlaying

@Composable
fun NowPlayingScreen(vm: RemoteViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val np = state.nowPlaying
    Box(
        modifier = Modifier
            .fillMaxSize()
            .crownSteps { vm.changeVolumeBy(it * 2) },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { np?.progress ?: 0f },
            modifier = Modifier.fillMaxSize().padding(2.dp),
            strokeWidth = 4.dp,
        )
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                state.status != Status.Connected -> CenterText(state.status.label)
                np == null -> CenterText("Nothing playing")
                else -> ItemInfo(np, state.logo)
            }
            Spacer(Modifier.height(6.dp))
            val active = np != null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteButton(KodiIcons.FastRewind, "Seek back", { vm.seek(forward = false) }, size = 44.dp, enabled = active)
                RemoteButton(
                    icon = if (np?.playing == true) KodiIcons.Pause else KodiIcons.Play,
                    description = "Play/Pause",
                    onClick = vm::playPause,
                    size = 60.dp,
                    primary = true,
                    enabled = active,
                )
                RemoteButton(KodiIcons.FastForward, "Seek forward", { vm.seek(forward = true) }, size = 44.dp, enabled = active)
            }
        }
    }
}

/** Logo (when the library has one) over a centred title, falling back to show/artist as text. */
@Composable
private fun ItemInfo(np: NowPlaying, logo: ImageBitmap?) {
    val time = "${np.time} / ${np.totalTime}"
    if (logo != null) {
        Image(
            bitmap = logo,
            contentDescription = if (np.logoIsTitle) np.title else np.subtitle,
            modifier = Modifier.size(width = 130.dp, height = 44.dp),
        )
        if (!np.logoIsTitle) Title(np.title)
        Caption(listOf(np.episode, time).filter { it.isNotEmpty() }.joinToString(" · "))
    } else {
        Title(np.title)
        if (np.subtitle.isNotEmpty()) {
            Text(
                text = np.subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Caption(time)
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CenterText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
