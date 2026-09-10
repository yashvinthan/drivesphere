package com.example.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiConnectHelper(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _connectionStatus = MutableStateFlow("Wi-Fi Standby")
    val connectionStatus = _connectionStatus.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun connectToHubWifi(
        ssid: String = "DriveSphere-Hub",
        password: String = "drivesphere123",
        onConnected: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Cancel existing request if any
                disconnect()

                _connectionStatus.value = "Requesting link to '$ssid'..."

                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        connectivityManager.bindProcessToNetwork(network)
                        _connectionStatus.value = "Connected to $ssid (In-App Linked)"
                        onConnected()
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        _connectionStatus.value = "Wi-Fi link declined or unavailable"
                        onError("Wi-Fi network request unavailable")
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        _connectionStatus.value = "Wi-Fi link disconnected"
                    }
                }

                networkCallback = callback
                connectivityManager.requestNetwork(request, callback)
            } catch (e: Exception) {
                _connectionStatus.value = "Failed: ${e.localizedMessage}"
                onError(e.localizedMessage ?: "Unknown error")
                // Fallback to in-app settings panel
                launchWifiSettingsPanel()
            }
        } else {
            // Android 9 and lower fallback
            launchWifiSettingsPanel()
        }
    }

    fun launchWifiSettingsPanel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(panelIntent)
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun disconnect() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                connectivityManager.bindProcessToNetwork(null)
            } catch (_: Exception) {}
        }
        networkCallback = null
        _connectionStatus.value = "Wi-Fi Disconnected"
    }
}
