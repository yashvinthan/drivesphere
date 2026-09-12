package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

object NetworkBinder {
    private var connectivityManager: ConnectivityManager? = null
    private var explicitHubNetwork: Network? = null
    @Volatile
    private var activeWifiNetwork: Network? = null
    private var isCallbackRegistered = false

    fun init(context: Context) {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager = cm

        if (cm != null && !isCallbackRegistered) {
            try {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()

                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        activeWifiNetwork = network
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            activeWifiNetwork = network
                        }
                    }

                    override fun onLost(network: Network) {
                        if (activeWifiNetwork == network) {
                            activeWifiNetwork = null
                        }
                    }
                })
                isCallbackRegistered = true
            } catch (_: Exception) {}
        }
    }

    fun setHubNetwork(network: Network?) {
        explicitHubNetwork = network
    }

    fun getWifiNetwork(): Network? {
        val explicit = explicitHubNetwork
        val cm = connectivityManager
        if (explicit != null && cm != null) {
            try {
                val caps = cm.getNetworkCapabilities(explicit)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return explicit
                }
            } catch (_: Exception) {}
        }

        activeWifiNetwork?.let { return it }

        // Fallback: scan allNetworks if callback has not fired yet
        return try {
            cm?.allNetworks?.firstOrNull { net ->
                val caps = cm?.getNetworkCapabilities(net)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getWifiSocketFactory(): SocketFactory? {
        return getWifiNetwork()?.socketFactory
    }

    /**
     * Direct TCP socket probe to verify connectivity and measure latency to the ESP32 Hub or Cam.
     * Binds the socket to Wi-Fi if available, while seamlessly supporting local mobile hotspot routing.
     */
    fun pingHost(host: String, port: Int = 80, timeoutMs: Int = 1200): Pair<Boolean, Long> {
        val start = System.currentTimeMillis()
        val wifiNet = getWifiNetwork()
        val isHotspotHost = host.startsWith("192.168.43.")

        // 1. First attempt: Wi-Fi bound socket (for 192.168.4.x / 192.168.5.x on vehicle Wi-Fi AP)
        if (wifiNet != null && !isHotspotHost) {
            try {
                val socket = Socket()
                wifiNet.bindSocket(socket)
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val latency = System.currentTimeMillis() - start
                socket.close()
                return Pair(true, latency)
            } catch (_: Exception) {}
        }

        // 2. Second attempt: Direct kernel socket (for hotspot clients, local tethering, or direct routing)
        val socket2 = Socket()
        return try {
            socket2.connect(InetSocketAddress(host, port), timeoutMs)
            val latency = System.currentTimeMillis() - start
            socket2.close()
            Pair(true, latency)
        } catch (_: Exception) {
            try { socket2.close() } catch (_: Exception) {}
            Pair(false, -1L)
        }
    }

    fun bindToWifi(): Boolean {
        // Individual OkHttpClient calls use getWifiNetwork()?.socketFactory
        // We preserve default cellular internet routing for Maps, OSRM, and eChallan scraping
        return getWifiNetwork() != null
    }

    fun unbind() {
        try {
            connectivityManager?.bindProcessToNetwork(null)
        } catch (_: Exception) {}
    }

    fun isWifiConnected(): Boolean {
        return getWifiNetwork() != null
    }
}
