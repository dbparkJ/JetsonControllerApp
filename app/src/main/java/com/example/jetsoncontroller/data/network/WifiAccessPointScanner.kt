package com.example.jetsoncontroller.data.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

data class WifiAccessPoint(
    val ssid: String,
    val rssi: Int,
    val security: WifiSecurity
) {
    val secured: Boolean
        get() = security != WifiSecurity.OPEN && security != WifiSecurity.ENHANCED_OPEN

    val requiresPassword: Boolean
        get() = security == WifiSecurity.PERSONAL

    val provisionable: Boolean
        get() = security in setOf(
            WifiSecurity.OPEN,
            WifiSecurity.ENHANCED_OPEN,
            WifiSecurity.PERSONAL
        )
}

enum class WifiSecurity {
    OPEN,
    ENHANCED_OPEN,
    PERSONAL,
    ENTERPRISE,
    LEGACY_WEP
}

data class WifiAccessPointState(
    val accessPoints: List<WifiAccessPoint> = emptyList(),
    val currentSsid: String? = null,
    val infrastructureWifiConnected: Boolean = false,
    val scanning: Boolean = false,
    val error: String? = null
)

class WifiAccessPointScanner(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val infrastructureNetworks = ConcurrentHashMap.newKeySet<Network>()

    private val _state = MutableStateFlow(WifiAccessPointState())
    val state: StateFlow<WifiAccessPointState> = _state.asStateFlow()

    private var registered = false

    // SSID access requires location permission/services and can be redacted even while
    // Wi-Fi is connected. Track the transport independently so automatic Direct cannot
    // replace the phone's infrastructure network. INTERNET is the network's configured
    // capability; VALIDATED is intentionally not required for a local-only router.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            infrastructureNetworks.add(network)
            publishCurrentConnection()
        }

        override fun onLost(network: Network) {
            infrastructureNetworks.remove(network)
            publishCurrentConnection()
        }
    }

    init {
        refreshCurrentConnection()
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            networkCallback
        )
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                publishResults()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(
                scanning = false,
                error = "주변 공유기 검색을 위해 위치 권한을 허용해 주세요."
            )
            return
        }

        if (!locationManager.isLocationEnabled) {
            _state.value = _state.value.copy(
                scanning = false,
                error = "주변 공유기 검색을 위해 위치 서비스를 켜 주세요."
            )
            return
        }

        if (!wifiManager.isWifiEnabled) {
            _state.value = _state.value.copy(
                scanning = false,
                error = "Wi-Fi를 켠 뒤 다시 검색해 주세요."
            )
            return
        }

        register()
        publishResults(scanning = true)

        if (!wifiManager.startScan()) {
            // Android can throttle active scans. Cached results are still valid and
            // should remain usable instead of turning a successful lookup into an error.
            publishResults()
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshCurrentConnection() {
        val connectedNetworks = connectivityManager.allNetworks.filter { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
        infrastructureNetworks.retainAll(connectedNetworks.toSet())
        infrastructureNetworks.addAll(connectedNetworks)
        publishCurrentConnection()
    }

    private fun publishCurrentConnection() {
        _state.update {
            it.copy(
                currentSsid = readCurrentSsid(),
                infrastructureWifiConnected = infrastructureNetworks.isNotEmpty()
            )
        }
    }

    private fun register() {
        if (registered) {
            return
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        registered = true
    }

    @SuppressLint("MissingPermission")
    private fun publishResults(scanning: Boolean = false) {
        val accessPoints = try {
            @Suppress("DEPRECATION")
            wifiManager.scanResults
                .mapNotNull { result ->
                    @Suppress("DEPRECATION")
                    val ssid = result.SSID.orEmpty()
                    if (ssid.isEmpty()) {
                        return@mapNotNull null
                    }

                    WifiAccessPoint(
                        ssid = ssid,
                        rssi = result.level,
                        security = securityType(result.capabilities.orEmpty())
                    )
                }
                .groupBy { it.ssid }
                .mapNotNull { (_, entries) -> entries.maxByOrNull { it.rssi } }
                .sortedByDescending { it.rssi }
        } catch (_: SecurityException) {
            emptyList()
        }

        _state.value = WifiAccessPointState(
            accessPoints = accessPoints,
            currentSsid = readCurrentSsid(),
            infrastructureWifiConnected = infrastructureNetworks.isNotEmpty(),
            scanning = scanning,
            error = null
        )
    }

    @SuppressLint("MissingPermission")
    private fun readCurrentSsid(): String? {
        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || !wifiManager.isWifiEnabled
        ) return null
        return try {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo?.ssid
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
        } catch (_: SecurityException) {
            null
        }
    }

    fun stop() {
        if (registered) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered by the system.
            }
            registered = false
        }
        _state.value = _state.value.copy(scanning = false)
    }

    private fun securityType(capabilities: String): WifiSecurity {
        val upper = capabilities.uppercase()
        return when {
            "EAP" in upper -> WifiSecurity.ENTERPRISE
            "WEP" in upper -> WifiSecurity.LEGACY_WEP
            "PSK" in upper || "SAE" in upper -> WifiSecurity.PERSONAL
            "OWE" in upper -> WifiSecurity.ENHANCED_OPEN
            else -> WifiSecurity.OPEN
        }
    }
}
