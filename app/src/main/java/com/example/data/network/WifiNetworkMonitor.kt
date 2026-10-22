package com.example.data.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.domain.model.IoTNetworkDefaults
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time Wi-Fi connectivity and network parameters monitor.
 * Detects live Wi-Fi connection state, signal RSSI, and the real gateway IPv4 address
 * assigned by the active Wi-Fi network using modern ConnectivityManager APIs.
 */
class WifiNetworkMonitor(context: Context) {
    private val appContext = context.applicationContext

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val activeWifiNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isWifiConnected = MutableStateFlow(checkIsWifiConnected())
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _detectedGatewayIp = MutableStateFlow(resolveActiveGatewayIp())
    val detectedGatewayIp: StateFlow<String> = _detectedGatewayIp.asStateFlow()

    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var isReceiverRegistered = false

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN
                    )
                    if (state == WifiManager.WIFI_STATE_DISABLED ||
                        state == WifiManager.WIFI_STATE_DISABLING
                    ) {
                        activeWifiNetworks.clear()
                        _isWifiConnected.value = false
                    } else {
                        refreshStatus()
                    }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    refreshStatus()
                }
            }
        }
    }

    init {
        registerCallbacks()
        registerBroadcastReceiver()
        startRealtimeWatcher()
    }

    /**
     * Checks in real time whether the phone has Wi-Fi enabled and is connected to a Wi-Fi network.
     */
    fun checkIsWifiConnected(): Boolean {
        return try {
            if (wifiManager != null && !wifiManager.isWifiEnabled) {
                activeWifiNetworks.clear()
                return false
            }

            val cm = connectivityManager ?: return false

            val activeNet = cm.activeNetwork
            if (activeNet != null) {
                val caps = cm.getNetworkCapabilities(activeNet)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true
                }
            }

            if (activeWifiNetworks.isNotEmpty()) {
                val hasValidWifi = activeWifiNetworks.any { net ->
                    cm.getNetworkCapabilities(net)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
                if (hasValidWifi) return true
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating Wi-Fi connectivity", e)
            false
        }
    }

    /**
     * Dynamically resolves the real IPv4 gateway address of the active Wi-Fi network
     * using ConnectivityManager LinkProperties.
     */
    fun resolveActiveGatewayIp(): String {
        try {
            val cm = connectivityManager
            if (cm != null) {
                val candidateNetworks = buildList {
                    cm.activeNetwork?.let { add(it) }
                    addAll(activeWifiNetworks)
                }
                for (net in candidateNetworks) {
                    val caps = cm.getNetworkCapabilities(net) ?: continue
                    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                    val linkProps = cm.getLinkProperties(net) ?: continue
                    for (route in linkProps.routes) {
                        val gw = route.gateway
                        if (gw is Inet4Address && !gw.isLoopbackAddress) {
                            val host = gw.hostAddress
                            if (!host.isNullOrBlank() && IoTNetworkDefaults.isValidPrivateLanIpv4(host)) {
                                return host
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve dynamic gateway IP", e)
        }
        return IoTNetworkDefaults.DEFAULT_AP_IP
    }

    /**
     * Reads the real Wi-Fi signal strength (RSSI in dBm) from the active Wi-Fi connection.
     */
    fun getCurrentRssiDbm(): Int {
        return try {
            val cm = connectivityManager ?: return 0
            val activeNet = cm.activeNetwork ?: activeWifiNetworks.firstOrNull() ?: return 0
            val caps = cm.getNetworkCapabilities(activeNet)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps != null) {
                val strength = caps.signalStrength
                if (strength != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
                    return strength
                }
            }
            0
        } catch (_: Exception) {
            0
        }
    }

    private fun registerCallbacks() {
        val cm = connectivityManager ?: return

        try {
            val wifiRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val wifiCb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    activeWifiNetworks.add(network)
                    refreshStatus()
                }

                override fun onLost(network: Network) {
                    activeWifiNetworks.remove(network)
                    refreshStatus()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        activeWifiNetworks.add(network)
                    } else {
                        activeWifiNetworks.remove(network)
                    }
                    refreshStatus()
                }
            }
            wifiNetworkCallback = wifiCb
            cm.registerNetworkCallback(wifiRequest, wifiCb)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Wi-Fi callback", e)
        }

        try {
            val defCb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refreshStatus()
                }

                override fun onLost(network: Network) {
                    refreshStatus()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    refreshStatus()
                }
            }
            defaultNetworkCallback = defCb
            cm.registerDefaultNetworkCallback(defCb)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register default network callback", e)
        }
    }

    private fun registerBroadcastReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(
                    wifiStateReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                appContext.registerReceiver(wifiStateReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Wi-Fi BroadcastReceiver", e)
        }
    }

    private fun startRealtimeWatcher() {
        monitorScope.launch {
            while (isActive) {
                refreshStatus()
                delay(WATCHER_INTERVAL_MS)
            }
        }
    }

    fun refreshStatus() {
        val connected = checkIsWifiConnected()
        if (_isWifiConnected.value != connected) {
            _isWifiConnected.value = connected
        }
        val gateway = resolveActiveGatewayIp()
        if (_detectedGatewayIp.value != gateway) {
            _detectedGatewayIp.value = gateway
        }
    }

    fun unregister() {
        monitorScope.cancel()
        if (isReceiverRegistered) {
            try {
                appContext.unregisterReceiver(wifiStateReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        val cm = connectivityManager ?: return
        try {
            wifiNetworkCallback?.let { cm.unregisterNetworkCallback(it) }
            defaultNetworkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering network callbacks", e)
        }
        wifiNetworkCallback = null
        defaultNetworkCallback = null
    }

    companion object {
        private const val TAG = "WifiNetworkMonitor"
        private const val WATCHER_INTERVAL_MS = 750L
    }
}
