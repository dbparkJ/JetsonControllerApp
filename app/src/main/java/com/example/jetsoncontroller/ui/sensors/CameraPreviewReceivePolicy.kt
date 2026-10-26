package com.example.jetsoncontroller.ui.sensors

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import android.os.PowerManager
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType

data class CameraPreviewEnvironment(
    val unmetered: Boolean = false,
    val powerSave: Boolean = true,
    val localLanRoute: Boolean = false,
    val thermalLimited: Boolean = false
)

internal fun cameraPreviewCanOverlap(
    transport: TransportState,
    environment: CameraPreviewEnvironment
): Boolean {
    val connected = transport as? TransportState.Connected ?: return false
    val nearby = connected.type == TransportType.WIFI_DIRECT ||
        (connected.type == TransportType.LAN && environment.localLanRoute)
    return nearby && environment.unmetered && !environment.powerSave && !environment.thermalLimited
}

/** Read at each request boundary; unknown network/service state fails closed. */
internal fun cameraPreviewEnvironment(context: Context, transport: TransportState): CameraPreviewEnvironment =
    runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching CameraPreviewEnvironment()
        val power = context.getSystemService(PowerManager::class.java)
            ?: return@runCatching CameraPreviewEnvironment()
        val network = connectivity.activeNetwork ?: return@runCatching CameraPreviewEnvironment()
        val active = connectivity.previewSnapshot(network)
            ?: return@runCatching CameraPreviewEnvironment()
        // Underlying VPN networks are not exposed by the public Android SDK.
        // Fall back to visible Wi-Fi/Ethernet networks; ambiguous routes fail closed.
        val fallback = if (active.vpn) connectivity.allNetworks.filter { it != network }
            .mapNotNull { candidate -> runCatching { connectivity.previewSnapshot(candidate) }.getOrNull() }
            else emptyList()
        val decision = cameraPreviewNetworkDecision(
            cameraPreviewNumericAddress((transport as? TransportState.Connected)?.endpoint),
            active, fallback
        )
        CameraPreviewEnvironment(
            unmetered = decision.unmetered,
            powerSave = power.isPowerSaveMode,
            localLanRoute = decision.localLanRoute,
            thermalLimited = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        )
    }.getOrDefault(CameraPreviewEnvironment())

private fun ConnectivityManager.previewSnapshot(network: Network): CameraPreviewNetwork? {
    val caps = getNetworkCapabilities(network) ?: return null
    return CameraPreviewNetwork(
        vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        wifiOrEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        routes = getLinkProperties(network)?.routes?.map { route ->
            CameraPreviewRoute(
                destination = route.destination.address.address.toList(),
                prefixLength = route.destination.prefixLength,
                hasGateway = route.hasGateway(),
                unicast = Build.VERSION.SDK_INT < 33 || route.type == RouteInfo.RTN_UNICAST
            )
        }
    )
}
