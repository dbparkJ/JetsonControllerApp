package com.example.jetsoncontroller.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.EndpointTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

class LanDiscoveryManager(private val context: Context) {

    private val SERVICE_TYPE = "_jetsonctl._tcp."
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredEndpoints = MutableStateFlow<List<DeviceEndpoint>>(emptyList())
    val discoveredEndpoints: StateFlow<List<DeviceEndpoint>> = _discoveredEndpoints.asStateFlow()

    private val _lastSeenAtEpochMillis = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastSeenAtEpochMillis: StateFlow<Map<String, Long>> =
        _lastSeenAtEpochMillis.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var discoveryActive = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            discoveryActive = true
            _isDiscovering.value = true
            _error.value = null
        }
        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType.trimEnd('.') == SERVICE_TYPE.trimEnd('.')) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(
                            "JetsonLAN",
                            "NSD resolve failed for ${serviceInfo.serviceName}: $errorCode"
                        )
                        _error.value = "LAN 장비 주소 확인 실패: $errorCode"
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val attributes = serviceInfo.attributes.mapValues { (_, value) ->
                            String(value, StandardCharsets.UTF_8)
                        }
                        if (attributes["api"] != "1" || attributes["tls"] != "1") {
                            return
                        }
                        val deviceId = runCatching {
                            UUID.fromString(attributes["id"]).toString().lowercase()
                        }.getOrNull() ?: return
                        val host = serviceInfo.host?.hostAddress.orEmpty()
                        if (host.isBlank() || serviceInfo.port <= 0) {
                            return
                        }
                        Log.d(
                            "JetsonLAN",
                            "Resolved ${serviceInfo.serviceName} at $host:${serviceInfo.port} " +
                                "for $deviceId"
                        )
                        val endpoint = DeviceEndpoint(
                            deviceId = deviceId,
                            displayName = serviceInfo.serviceName,
                            host = host,
                            port = serviceInfo.port,
                            transport = EndpointTransport.LAN
                        )
                        updateEndpoints(endpoint)
                    }
                })
            }
        }
        override fun onServiceLost(service: NsdServiceInfo) {
            _discoveredEndpoints.value = _discoveredEndpoints.value
                .filterNot { it.displayName == service.serviceName }
        }
        override fun onDiscoveryStopped(regType: String) {
            discoveryActive = false
            _isDiscovering.value = false
        }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryActive = false
            _isDiscovering.value = false
            _error.value = "같은 네트워크 장비 검색 시작 실패: $errorCode"
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryActive = false
            _isDiscovering.value = false
            _error.value = "같은 네트워크 장비 검색 중지 실패: $errorCode"
        }
    }

    fun startDiscovery() {
        if (discoveryActive) {
            return
        }

        _discoveredEndpoints.value = emptyList()
        _error.value = null
        discoveryActive = true
        _isDiscovering.value = true
        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (error: SecurityException) {
            discoveryActive = false
            _isDiscovering.value = false
            _error.value = "로컬 네트워크 권한을 허용해 주세요."
        } catch (error: Exception) {
            discoveryActive = false
            Log.e("JetsonLAN", "NSD discovery failed", error)
            _isDiscovering.value = false
            _error.value = "같은 네트워크 장비 검색을 시작하지 못했습니다."
        }
    }

    fun stopDiscovery() {
        if (!discoveryActive) {
            return
        }

        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (_: Exception) {
            discoveryActive = false
            _isDiscovering.value = false
        }
    }

    private fun updateEndpoints(endpoint: DeviceEndpoint) {
        val current = _discoveredEndpoints.value.toMutableList()
        val index = current.indexOfFirst { it.deviceId == endpoint.deviceId }
        if (index >= 0) {
            current[index] = endpoint
        } else {
            current.add(endpoint)
        }
        _discoveredEndpoints.value = current
        _lastSeenAtEpochMillis.value = _lastSeenAtEpochMillis.value +
            (endpoint.deviceId.lowercase() to System.currentTimeMillis())
    }
}
