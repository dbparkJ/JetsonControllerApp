package com.example.jetsoncontroller.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.EndpointTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LanDiscoveryManager(private val context: Context) {

    private val SERVICE_TYPE = "_jetsonctl._tcp."
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredEndpoints = MutableStateFlow<List<DeviceEndpoint>>(emptyList())
    val discoveredEndpoints: StateFlow<List<DeviceEndpoint>> = _discoveredEndpoints.asStateFlow()

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}
        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType == SERVICE_TYPE) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val deviceId = serviceInfo.attributes["id"]?.let { String(it) } ?: "unknown"
                        val endpoint = DeviceEndpoint(
                            deviceId = deviceId,
                            displayName = serviceInfo.serviceName,
                            host = serviceInfo.host.hostAddress ?: "",
                            port = serviceInfo.port,
                            transport = EndpointTransport.LAN
                        )
                        updateEndpoints(endpoint)
                    }
                })
            }
        }
        override fun onServiceLost(service: NsdServiceInfo) {
            // Remove endpoint logic
        }
        override fun onDiscoveryStopped(regType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    fun startDiscovery() {
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {}
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
    }
}
