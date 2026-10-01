package dev.paul.kodiremote.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.layout.androidImageResource
import androidx.wear.protolayout.layout.basicImage
import androidx.wear.protolayout.layout.box
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.imageResource
import androidx.wear.protolayout.layout.inlineImageResource
import androidx.wear.protolayout.layout.row
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.ButtonDefaults.filledButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.circularProgressIndicator
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.dp
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import dev.paul.kodiremote.MainActivity
import dev.paul.kodiremote.R
import dev.paul.kodiremote.data.SettingsRepository
import dev.paul.kodiremote.data.WifiNetworkProvider
import dev.paul.kodiremote.kodi.KodiApi
import dev.paul.kodiremote.kodi.KodiClient
import dev.paul.kodiremote.kodi.KodiException
import dev.paul.kodiremote.kodi.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

private const val ID_PLAY_PAUSE = "play_pause"
private const val ID_SEEK_BACK = "seek_back"
private const val ID_SEEK_FORWARD = "seek_forward"
private const val ID_OPEN_APP = "open_app"

/** Same grace as the app: give Wi-Fi this long to come up before trying the default network. */
private const val WIFI_GRACE_MS = 6000L

/** Keep Wi-Fi requested a little after a request so a few taps in a row don't each wait for it. */
private const val WIFI_LINGER_MS = 20_000L

/** Re-request the tile this often while something plays, to move the progress ring along. */
private const val PLAYING_FRESHNESS_MS = 60_000L

/** The logo is shown about 110dp wide; a small PNG keeps the tile payload light. */
private const val LOGO_MAX_WIDTH_PX = 240

private val KodiTileColors = ColorScheme(
    primary = 0xFF17B2E7.toInt().argb,
    onPrimary = 0xFF00202C.toInt().argb,
    primaryContainer = 0xFF0E4F68.toInt().argb,
    onPrimaryContainer = 0xFFCDEFFF.toInt().argb,
)

/** What the tile shows: either something playing, or a status line. */
private sealed interface TileContent {
    data class Playing(val np: NowPlaying, val logoPng: ByteArray?, val logoWidth: Int, val logoHeight: Int) : TileContent
    data class Message(val text: String) : TileContent
}

/** The app's Now Playing page as a tile: progress ring, title, seek back / play-pause / seek forward. */
class NowPlayingTileService : Material3TileService(allowDynamicTheme = false, defaultColorScheme = KodiTileColors) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wifi by lazy { WifiNetworkProvider(this) }
    private var wifiRelease: Job? = null

    /** The last decoded logo, so refreshes of the same item don't download it again. */
    private var logoCache: Pair<String, Bitmap?>? = null

    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val content = withTimeoutOrNull(12_000) { load(requestParams.currentState.lastClickableId) }
            ?: TileContent.Message("Kodi unreachable")
        val playing = (content as? TileContent.Playing)?.np?.playing == true
        return TileBuilders.Tile.Builder()
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(content)))
            .setFreshnessIntervalMillis(if (playing) PLAYING_FRESHNESS_MS else 0)
            .build()
    }

    /** Runs the tapped action, if any, then reads what's playing now. */
    private suspend fun load(clickedId: String): TileContent {
        val settings = SettingsRepository(this)
        if (!settings.config.value.isConfigured) return TileContent.Message("Set up Kodi in the app")

        acquireWifi()
        val api = KodiApi(KodiClient(config = { settings.config.value }, network = { wifi.network.value }))
        return try {
            var np = api.nowPlaying()
            if (np != null && runAction(api, np.playerId, clickedId)) np = api.nowPlaying()
            if (np == null) TileContent.Message("Nothing playing") else playing(api, np)
        } catch (e: KodiException) {
            TileContent.Message(
                when (e.kind) {
                    KodiException.Kind.NotConfigured -> "Set up Kodi in the app"
                    KodiException.Kind.Unreachable -> "Kodi unreachable"
                    KodiException.Kind.AuthFailed -> "Login failed"
                    KodiException.Kind.Rpc -> "Error"
                },
            )
        }
    }

    private suspend fun runAction(api: KodiApi, playerId: Int, clickedId: String): Boolean {
        when (clickedId) {
            ID_PLAY_PAUSE -> api.playPause(playerId)
            ID_SEEK_BACK -> api.seek(playerId, forward = false)
            ID_SEEK_FORWARD -> api.seek(playerId, forward = true)
            else -> return false
        }
        return true
    }

    private suspend fun playing(api: KodiApi, np: NowPlaying): TileContent.Playing {
        val logo = np.logo?.let { path ->
            logoCache?.takeIf { it.first == path }?.second ?: try {
                api.image(path, LOGO_MAX_WIDTH_PX)
            } catch (e: KodiException) {
                null
            }.also { logoCache = path to it }
        }
        val png = logo?.let { bitmap ->
            withContext(Dispatchers.Default) {
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            }
        }
        return TileContent.Playing(np, png, logo?.width ?: 0, logo?.height ?: 0)
    }

    /** Requests Wi-Fi (Bluetooth via the phone can't reach the LAN) and waits briefly for it. */
    private suspend fun acquireWifi() {
        wifiRelease?.cancel()
        wifi.acquire()
        withTimeoutOrNull(WIFI_GRACE_MS) { wifi.network.filterNotNull().first() }
        wifiRelease = scope.launch {
            delay(WIFI_LINGER_MS)
            wifi.release()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wifi.release()
        super.onDestroy()
    }

    private fun MaterialScope.layout(content: TileContent): LayoutElement {
        val np = (content as? TileContent.Playing)?.np
        val openApp = clickable(
            ActionBuilders.launchAction(ComponentName(context, MainActivity::class.java)),
            ID_OPEN_APP,
        )
        return box(
            circularProgressIndicator(staticProgress = np?.progress ?: 0f),
            column(
                when (content) {
                    is TileContent.Message -> statusText(content.text)
                    is TileContent.Playing -> itemInfo(content)
                },
                spacer(height = 6f.dp),
                row(
                    mediaButton(ID_SEEK_BACK, R.drawable.ic_fast_rewind, size = 44f),
                    spacer(width = 6f.dp),
                    mediaButton(
                        ID_PLAY_PAUSE,
                        if (np?.playing == true) R.drawable.ic_pause else R.drawable.ic_play,
                        size = 56f,
                        primary = true,
                    ),
                    spacer(width = 6f.dp),
                    mediaButton(ID_SEEK_FORWARD, R.drawable.ic_fast_forward, size = 44f),
                    verticalAlignment = LayoutElementBuilders.VERTICAL_ALIGN_CENTER,
                ),
                modifier = LayoutModifier.clickable(openApp),
            ),
        )
    }

    private fun MaterialScope.itemInfo(content: TileContent.Playing): LayoutElement {
        val np = content.np
        val time = "${np.time} / ${np.totalTime}"
        val logo = content.logoPng
        if (logo == null) {
            return column(
                title(np.title),
                *listOfNotNull(np.subtitle.ifEmpty { null }?.let { body(it) }).toTypedArray(),
                caption(time),
            )
        }
        val logoHeight = 36f
        val logoWidth = (logoHeight * content.logoWidth / content.logoHeight).coerceAtMost(110f)
        return column(
            protoLayoutScope.basicImage(
                imageResource(inlineImage = inlineImageResource(logo, content.logoWidth, content.logoHeight)),
                width = logoWidth.dp,
                height = logoHeight.dp,
                modifier = LayoutModifier.contentDescription(if (np.logoIsTitle) np.title else np.subtitle),
            ),
            *listOfNotNull(if (np.logoIsTitle) null else title(np.title)).toTypedArray(),
            caption(listOf(np.episode, time).filter { it.isNotEmpty() }.joinToString(" · ")),
        )
    }

    private fun MaterialScope.mediaButton(
        id: String,
        iconRes: Int,
        size: Float,
        primary: Boolean = false,
    ): LayoutElement = iconButton(
        // A load action brings the tap back to tileResponse, which runs it and redraws the tile
        onClick = clickable(loadAction(), id),
        iconContent = { icon(drawable(iconRes)) },
        width = size.dp,
        height = size.dp,
        colors = if (primary) filledButtonColors() else filledTonalButtonColors(),
    )

    private fun drawable(res: Int): ImageResource = imageResource(androidImageResource(res))

    private fun MaterialScope.title(text: String) = text(
        text.layoutString,
        typography = Typography.TITLE_SMALL,
        maxLines = 2,
    )

    private fun MaterialScope.body(text: String) = text(
        text.layoutString,
        typography = Typography.BODY_SMALL,
        maxLines = 1,
    )

    private fun MaterialScope.caption(text: String) = text(
        text.layoutString,
        typography = Typography.LABEL_SMALL,
        color = colorScheme.onSurfaceVariant,
    )

    private fun MaterialScope.statusText(text: String) = text(
        text.layoutString,
        typography = Typography.TITLE_SMALL,
        maxLines = 2,
    )

    companion object {
        /** Asks the system to re-request the tile, e.g. after the app changed what's playing. */
        fun requestUpdate(context: Context) {
            TileService.getUpdater(context).requestUpdate(NowPlayingTileService::class.java)
        }
    }
}
