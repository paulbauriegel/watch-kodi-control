package dev.paul.kodiremote.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wear OS routes traffic through the phone's Bluetooth proxy by default, which can't reach
 * LAN addresses. While acquired, this asks the system to bring up Wi-Fi and exposes it so
 * requests can be bound to it.
 */
class WifiNetworkProvider(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _network = MutableStateFlow<Network?>(null)
    val network: StateFlow<Network?> = _network.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun acquire() {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _network.value = network
            }

            override fun onLost(network: Network) {
                if (_network.value == network) _network.value = null
            }
        }
        callback = cb
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Kodi is on the LAN, so the Wi-Fi doesn't need internet access
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivity.requestNetwork(request, cb)
    }

    fun release() {
        callback?.let { connectivity.unregisterNetworkCallback(it) }
        callback = null
        _network.value = null
    }
}
