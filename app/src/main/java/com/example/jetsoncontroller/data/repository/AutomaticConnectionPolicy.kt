package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.canonicalBleNameForDeviceId
import com.example.jetsoncontroller.model.legacyBleNameForDeviceId

internal const val IP_STATUS_FAILURE_LIMIT = 3

internal fun automaticTargetDeviceId(
    preferredDeviceId: String?,
    registeredDevices: List<RegisteredDevice>
): String? {
    if (preferredDeviceId != null) {
        return registeredDevices.firstOrNull {
            it.deviceId.equals(preferredDeviceId, ignoreCase = true)
        }?.deviceId
    }
    return registeredDevices.singleOrNull()?.deviceId
}

internal fun allowsAutomaticDirectFallback(transportState: TransportState): Boolean =
    transportState is TransportState.Disconnected ||
        transportState is TransportState.Error ||
        (transportState is TransportState.Connected && transportState.type == TransportType.BLE)

internal fun allowsAutomaticLanUpgrade(transportState: TransportState): Boolean =
    allowsAutomaticDirectFallback(transportState) ||
        (transportState is TransportState.Connected &&
            transportState.type == TransportType.WIFI_DIRECT)

internal fun chooseAutomaticWifiDirectPeer(
    peers: List<WifiDirectPeer>,
    deviceId: String
): WifiDirectPeer? {
    val expectedNames = setOf(
        canonicalBleNameForDeviceId(deviceId).lowercase(),
        legacyBleNameForDeviceId(deviceId).lowercase()
    )
    return peers
        .filter { it.name.lowercase() in expectedNames }
        .singleOrNull()
}

internal fun ipConnectionIsOffline(consecutiveFailures: Int): Boolean =
    consecutiveFailures >= IP_STATUS_FAILURE_LIMIT
