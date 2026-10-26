package com.example.jetsoncontroller.ui.sensors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import java.net.InetAddress

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
        val caps = connectivity.getNetworkCapabilities(network)
            ?: return@runCatching CameraPreviewEnvironment()
        val endpoint = (transport as? TransportState.Connected)?.endpoint
        val host = endpoint?.let {
            if (it.startsWith("[")) it.substringAfter('[').substringBefore(']')
            else it.substringBefore(':')
        }
        // Numeric endpoints only: never resolve DNS on the UI thread.
        val address = host?.takeIf {
            it.matches(Regex("[0-9.]+")) || (it.contains(':') && it.matches(Regex("[0-9a-fA-F:]+")))
        }?.let { InetAddress.getByName(it) }
        val localRoute = address != null && !address.isLoopbackAddress &&
            !address.isAnyLocalAddress && !address.isMulticastAddress &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            connectivity.getLinkProperties(network)?.routes?.any {
                !it.isDefaultRoute && !it.hasGateway() && it.matches(address)
            } == true
        CameraPreviewEnvironment(
            unmetered = !connectivity.isActiveNetworkMetered,
            powerSave = power.isPowerSaveMode,
            localLanRoute = localRoute,
            thermalLimited = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        )
    }.getOrDefault(CameraPreviewEnvironment())
