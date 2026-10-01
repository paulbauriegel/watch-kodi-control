package dev.paul.kodiremote.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class KodiConfig(
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
) {
    val isConfigured get() = host.isNotBlank()
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("kodi", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<KodiConfig> = _config.asStateFlow()

    fun save(config: KodiConfig) {
        val cleaned = config.copy(host = config.host.trim())
        prefs.edit()
            .putString("host", cleaned.host)
            .putInt("port", cleaned.port)
            .putString("username", cleaned.username)
            .putString("password", cleaned.password)
            .apply()
        _config.value = cleaned
    }

    private fun load() = KodiConfig(
        host = prefs.getString("host", "").orEmpty(),
        port = prefs.getInt("port", 8080),
        username = prefs.getString("username", "").orEmpty(),
        password = prefs.getString("password", "").orEmpty(),
    )
}
