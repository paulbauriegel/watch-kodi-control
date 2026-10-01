package dev.paul.kodiremote.kodi

import android.net.Network
import android.util.Base64
import dev.paul.kodiremote.data.KodiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

class KodiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NotConfigured, Unreachable, AuthFailed, Rpc }
}

/** Minimal JSON-RPC 2.0 client for Kodi's HTTP endpoint. */
class KodiClient(
    private val config: () -> KodiConfig,
    private val network: () -> Network?,
) {
    private val ids = AtomicInteger()

    suspend fun call(method: String, params: JSONObject? = null): Any? = request("jsonrpc") { conn ->
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")

        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", ids.incrementAndGet())
            .put("method", method)
        if (params != null) body.put("params", params)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val response = JSONObject(conn.readBody().decodeToString())
        response.optJSONObject("error")?.let {
            throw KodiException(KodiException.Kind.Rpc, it.optString("message", "RPC error"))
        }
        response.opt("result")
    }

    /** Downloads an image through Kodi, given an `image://` path from an "art" property. */
    suspend fun image(path: String): ByteArray =
        request("image/" + URLEncoder.encode(path, "UTF-8").replace("+", "%20")) { it.readBody() }

    private suspend fun <T> request(path: String, block: (HttpURLConnection) -> T): T = withContext(Dispatchers.IO) {
        val cfg = config()
        if (!cfg.isConfigured) throw KodiException(KodiException.Kind.NotConfigured, "Not configured")

        val url = URL("http://${cfg.host}:${cfg.port}/$path")
        val conn = (network()?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        try {
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            if (cfg.username.isNotEmpty()) {
                val credentials = "${cfg.username}:${cfg.password}".toByteArray()
                conn.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(credentials, Base64.NO_WRAP))
            }
            block(conn)
        } catch (e: IOException) {
            throw KodiException(KodiException.Kind.Unreachable, e.message ?: "Unreachable")
        } catch (e: JSONException) {
            throw KodiException(KodiException.Kind.Rpc, "Invalid response")
        } finally {
            conn.disconnect()
        }
    }

    private fun HttpURLConnection.readBody(): ByteArray {
        when (val code = responseCode) {
            401 -> throw KodiException(KodiException.Kind.AuthFailed, "Wrong username or password")
            !in 200..299 -> throw KodiException(KodiException.Kind.Rpc, "HTTP $code")
        }
        return inputStream.use { it.readBytes() }
    }
}
