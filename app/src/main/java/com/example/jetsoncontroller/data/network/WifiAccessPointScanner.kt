package com.example.jetsoncontroller.data.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val scanning: Boolean = false,
    val error: String? = null
)

class WifiAccessPointScanner(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _state = MutableStateFlow(WifiAccessPointState())
    val state: StateFlow<WifiAccessPointState> = _state.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val updated = intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED,
                    false
                )
                publishResults(
                    error = if (updated) null else {
                        "새 검색이 제한되어 최근 Wi-Fi 목록을 표시합니다."
                    }
                )
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
        _state.value = _state.value.copy(scanning = true, error = null)

        if (!wifiManager.startScan()) {
            publishResults(
                error = "Android Wi-Fi 검색 제한으로 최근 목록을 표시합니다. 잠시 후 다시 시도하세요."
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
    private fun publishResults(error: String?) {
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
            scanning = false,
            error = error
        )
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
