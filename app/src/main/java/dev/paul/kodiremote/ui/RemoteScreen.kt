package dev.paul.kodiremote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.paul.kodiremote.kodi.InputKey

/** Navigation only: D-pad with OK in the middle, a prominent Back, and Home / Info / Menu around it. */
@Composable
fun RemoteScreen(vm: RemoteViewModel) {
    val d = 58.dp
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RemoteButton(KodiIcons.Up, "Up", { vm.input(InputKey.Up) }, Modifier.offset(y = -d), size = 46.dp)
        RemoteButton(KodiIcons.Down, "Down", { vm.input(InputKey.Down) }, Modifier.offset(y = d), size = 46.dp)
        RemoteButton(KodiIcons.Left, "Left", { vm.input(InputKey.Left) }, Modifier.offset(x = -d), size = 46.dp)
        RemoteButton(KodiIcons.Right, "Right", { vm.input(InputKey.Right) }, Modifier.offset(x = d), size = 46.dp)
        RemoteButton(KodiIcons.Check, "OK", { vm.input(InputKey.Select) }, size = 58.dp, primary = true)

        RemoteButton(KodiIcons.Home, "Home", { vm.input(InputKey.Home) }, Modifier.offset(-d, -d), size = 36.dp)
        RemoteButton(KodiIcons.Info, "Info", { vm.input(InputKey.Info) }, Modifier.offset(d, -d), size = 36.dp)
        // Back is the most used key after the arrows, so it gets a bigger, accented button
        RemoteButton(
            KodiIcons.Back, "Back", { vm.input(InputKey.Back) }, Modifier.offset(-d, d),
            size = 50.dp, accent = true,
        )
        RemoteButton(KodiIcons.Menu, "Menu", { vm.input(InputKey.ContextMenu) }, Modifier.offset(d, d), size = 36.dp)
    }
}
