package dev.paul.kodiremote.kodi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class NowPlaying(
    val playerId: Int,
    val title: String,
    /** Show and episode, artist or album. */
    val subtitle: String,
    /** "S03E05" for TV episodes, otherwise empty. */
    val episode: String,
    /** Kodi image path of the show's, movie's or artist's clear logo, if the library has one. */
    val logo: String?,
    /** The logo spells out the title itself (movies), so the title text can be left out next to it. */
    val logoIsTitle: Boolean,
    val playing: Boolean,
    /** 0..1 */
    val progress: Float,
    val time: String,
    val totalTime: String,
)

data class VolumeState(val volume: Int, val muted: Boolean)

enum class InputKey(val method: String) {
    Up("Input.Up"), Down("Input.Down"), Left("Input.Left"), Right("Input.Right"),
    Select("Input.Select"), Back("Input.Back"), Home("Input.Home"),
    ContextMenu("Input.ContextMenu"), Info("Input.Info"),
}

/** Art types that hold a transparent logo: the show's for episodes, the movie's, the artist's for songs. */
private val LOGO_ART = listOf("tvshow.clearlogo", "clearlogo", "artist.clearlogo")

/** Typed wrappers around the Kodi JSON-RPC methods the remote uses. */
class KodiApi(private val client: KodiClient) {

    suspend fun ping() {
        client.call("JSONRPC.Ping")
    }

    suspend fun nowPlaying(): NowPlaying? {
        val players = client.call("Player.GetActivePlayers") as? JSONArray ?: return null
        val playerId = players.optJSONObject(0)?.optInt("playerid") ?: return null

        val item = (client.call(
            "Player.GetItem",
            JSONObject()
                .put("playerid", playerId)
                .put("properties", JSONArray(listOf("title", "showtitle", "season", "episode", "artist", "album", "art"))),
        ) as JSONObject).getJSONObject("item")
        val props = client.call(
            "Player.GetProperties",
            JSONObject()
                .put("playerid", playerId)
                .put("properties", JSONArray(listOf("speed", "percentage", "time", "totaltime"))),
        ) as JSONObject

        val episode = episodeOf(item)
        val logo = item.optJSONObject("art")?.let { art ->
            LOGO_ART.firstNotNullOfOrNull { key -> art.optString(key).ifEmpty { null } }
        }
        return NowPlaying(
            playerId = playerId,
            title = item.optString("title").ifEmpty { item.optString("label") },
            subtitle = subtitleOf(item, episode),
            episode = episode,
            logo = logo,
            logoIsTitle = item.optString("type") == "movie",
            playing = props.optInt("speed") != 0,
            progress = (props.optDouble("percentage", 0.0) / 100).toFloat().coerceIn(0f, 1f),
            time = formatTime(props.optJSONObject("time")),
            totalTime = formatTime(props.optJSONObject("totaltime")),
        )
    }

    /** Downloads and decodes an artwork image, scaled down to roughly [maxWidth] pixels. */
    suspend fun image(path: String, maxWidth: Int): Bitmap? {
        val bytes = client.image(path)
        return withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= maxWidth) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    suspend fun playPause(playerId: Int) = playerCall("Player.PlayPause", playerId)

    suspend fun stop(playerId: Int) = playerCall("Player.Stop", playerId)

    suspend fun next(playerId: Int) = playerCall("Player.GoTo", playerId) { put("to", "next") }

    suspend fun previous(playerId: Int) = playerCall("Player.GoTo", playerId) { put("to", "previous") }

    suspend fun seek(playerId: Int, forward: Boolean) = playerCall("Player.Seek", playerId) {
        put("value", JSONObject().put("step", if (forward) "smallforward" else "smallbackward"))
    }

    suspend fun volume(): VolumeState {
        val result = client.call(
            "Application.GetProperties",
            JSONObject().put("properties", JSONArray(listOf("volume", "muted"))),
        ) as JSONObject
        return VolumeState(result.optInt("volume"), result.optBoolean("muted"))
    }

    suspend fun setVolume(volume: Int) {
        client.call("Application.SetVolume", JSONObject().put("volume", volume.coerceIn(0, 100)))
    }

    suspend fun toggleMute() {
        client.call("Application.SetMute", JSONObject().put("mute", "toggle"))
    }

    suspend fun input(key: InputKey) {
        client.call(key.method)
    }

    private suspend fun playerCall(method: String, playerId: Int, extra: JSONObject.() -> Unit = {}) {
        client.call(method, JSONObject().put("playerid", playerId).apply(extra))
    }

    private fun episodeOf(item: JSONObject): String {
        if (item.optString("showtitle").isEmpty()) return ""
        val season = item.optInt("season", -1)
        val episode = item.optInt("episode", -1)
        return if (season >= 0 && episode >= 0) "S%02dE%02d".format(season, episode) else ""
    }

    private fun subtitleOf(item: JSONObject, episode: String): String {
        val show = item.optString("showtitle")
        if (show.isNotEmpty()) return if (episode.isNotEmpty()) "$show $episode" else show
        val artists = item.optJSONArray("artist")
        if (artists != null && artists.length() > 0) {
            return (0 until artists.length()).joinToString(", ") { artists.optString(it) }
        }
        return item.optString("album")
    }

    private fun formatTime(time: JSONObject?): String {
        if (time == null) return "0:00"
        val h = time.optInt("hours")
        val m = time.optInt("minutes")
        val s = time.optInt("seconds")
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
