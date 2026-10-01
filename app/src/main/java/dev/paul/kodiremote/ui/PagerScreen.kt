package dev.paul.kodiremote.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.ScreenScaffold

/** Swipe between Now Playing, the D-pad remote, volume and the remaining controls. */
@Composable
fun PagerScreen(vm: RemoteViewModel, onOpenSettings: () -> Unit) {
    // Pages collect the state themselves, so a poll only recomposes the pages that show it
    val pagerState = rememberPagerState(pageCount = { 4 })
    HorizontalPagerScaffold(pagerState = pagerState) {
        // Keep the neighbouring pages composed so a swipe doesn't have to build the next page first
        HorizontalPager(state = pagerState, beyondViewportPageCount = 1) { page ->
            ScreenScaffold {
                when (page) {
                    0 -> NowPlayingScreen(vm)
                    1 -> RemoteScreen(vm)
                    2 -> VolumeScreen(vm)
                    else -> MoreScreen(vm, onOpenSettings)
                }
            }
        }
    }
}
