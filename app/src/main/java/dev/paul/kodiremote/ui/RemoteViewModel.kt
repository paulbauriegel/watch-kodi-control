package dev.paul.kodiremote.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.paul.kodiremote.data.KodiConfig
import dev.paul.kodiremote.data.SettingsRepository
import dev.paul.kodiremote.data.WifiNetworkProvider
import dev.paul.kodiremote.kodi.InputKey
import dev.paul.kodiremote.kodi.KodiApi
import dev.paul.kodiremote.kodi.KodiClient
import dev.paul.kodiremote.kodi.KodiException
import dev.paul.kodiremote.kodi.NowPlaying
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class Status(val label: String) {
    NotConfigured("Not configured"),
    WaitingForWifi("Waiting for Wi-Fi…"),
    Connecting("Connecting…"),
    Connected("Connected"),
    Unreachable("Kodi unreachable"),
    AuthFailed("Login failed"),
    Error("Error"),
}

data class RemoteUiState(
    val status: Status = Status.Connecting,
    val nowPlaying: NowPlaying? = null,
    /** Clear logo of what's playing, once loaded. */
    val logo: ImageBitmap? = null,
    val volume: Int = 0,
    val muted: Boolean = false,
)

private const val POLL_INTERVAL_MS = 1500L

/** Give Wi-Fi this long to come up before falling back to the default network. */
private const val WIFI_GRACE_MS = 6000L

/** Ignore polled volume for a moment after a local change so the crown doesn't jump back. */
private const val VOLUME_HOLD_MS = 1500L

/** Logos are shown about 130dp wide; decoding bigger only costs memory. */
private const val LOGO_MAX_WIDTH_PX = 320

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsRepository(app)
    private val wifi = WifiNetworkProvider(app)
    private val api = KodiApi(KodiClient(config = { settings.config.value }, network = { wifi.network.value }))

    val config: StateFlow<KodiConfig> = settings.config

    private val _state = MutableStateFlow(RemoteUiState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()

    private val volumeTarget = MutableStateFlow<Int?>(null)
    private var lastLocalVolumeChange = 0L
    private var logoPath: String? = null

    init {
        viewModelScope.launch {
            volumeTarget.filterNotNull().collectLatest { volume ->
                delay(80) // coalesce fast crown turns
                runAction { api.setVolume(volume) }
            }
        }
    }

    /** Keeps Wi-Fi requested and polls Kodi until cancelled. Run while the UI is visible. */
    suspend fun pollWhileVisible() {
        val started = SystemClock.elapsedRealtime()
        wifi.acquire()
        try {
            while (true) {
                val waitingForWifi = wifi.network.value == null &&
                    SystemClock.elapsedRealtime() - started < WIFI_GRACE_MS
                if (waitingForWifi && settings.config.value.isConfigured) {
                    _state.update { it.copy(status = Status.WaitingForWifi) }
                    delay(250)
                    continue
                }
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        } finally {
            wifi.release()
        }
    }

    private suspend fun refresh() {
        try {
            val nowPlaying = api.nowPlaying()
            val volume = api.volume()
            val holdVolume = SystemClock.elapsedRealtime() - lastLocalVolumeChange < VOLUME_HOLD_MS
            _state.update {
                it.copy(
                    status = Status.Connected,
                    nowPlaying = nowPlaying,
                    volume = if (holdVolume) it.volume else volume.volume,
                    muted = volume.muted,
                )
            }
            updateLogo(nowPlaying?.logo)
        } catch (e: KodiException) {
            _state.update { it.copy(status = e.toStatus(), nowPlaying = null) }
        }
    }

    /** Fetches the logo when the playing item changes. Without one the screen just shows text. */
    private fun updateLogo(path: String?) {
        if (path == logoPath) return
        logoPath = path
        _state.update { it.copy(logo = null) }
        if (path == null) return
        viewModelScope.launch {
            val logo = try {
                api.image(path, LOGO_MAX_WIDTH_PX)?.asImageBitmap()
            } catch (e: KodiException) {
                null
            }
            if (logoPath == path) _state.update { it.copy(logo = logo) }
        }
    }

    fun playPause() = playerAction { api.playPause(it) }
    fun stop() = playerAction { api.stop(it) }
    fun next() = playerAction { api.next(it) }
    fun previous() = playerAction { api.previous(it) }
    fun seek(forward: Boolean) = playerAction { api.seek(it, forward) }

    fun input(key: InputKey) = launchAction { api.input(key) }

    fun toggleMute() = launchAction { api.toggleMute() }

    fun changeVolumeBy(delta: Int) {
        val volume = (_state.value.volume + delta).coerceIn(0, 100)
        lastLocalVolumeChange = SystemClock.elapsedRealtime()
        _state.update { it.copy(volume = volume) }
        volumeTarget.value = volume
    }

    fun saveConfig(config: KodiConfig) {
        settings.save(config)
        _state.update { it.copy(status = Status.Connecting, nowPlaying = null) }
    }

    /** Pings Kodi with the given settings without saving them. Returns a user-facing result. */
    suspend fun testConnection(config: KodiConfig): String {
        wifi.acquire()
        withTimeoutOrNull(WIFI_GRACE_MS) { wifi.network.filterNotNull().first() }
        val api = KodiApi(KodiClient(config = { config }, network = { wifi.network.value }))
        return try {
            api.ping()
            "Connected"
        } catch (e: KodiException) {
            e.toStatus().label
        }
    }

    private fun playerAction(block: suspend (playerId: Int) -> Unit) {
        val playerId = _state.value.nowPlaying?.playerId ?: return
        launchAction { block(playerId) }
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (runAction(block)) refresh()
        }
    }

    private suspend fun runAction(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: KodiException) {
        _state.update { it.copy(status = e.toStatus()) }
        false
    }

    private fun KodiException.toStatus() = when (kind) {
        KodiException.Kind.NotConfigured -> Status.NotConfigured
        KodiException.Kind.Unreachable -> Status.Unreachable
        KodiException.Kind.AuthFailed -> Status.AuthFailed
        KodiException.Kind.Rpc -> Status.Error
    }

    override fun onCleared() {
        wifi.release()
    }
}
