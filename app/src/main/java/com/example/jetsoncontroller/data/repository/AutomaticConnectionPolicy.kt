package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.canonicalBleNameForDeviceId
import com.example.jetsoncontroller.model.legacyBleNameForDeviceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

/**
 * A normal LAN connection always takes precedence over the Wi-Fi Direct fallback.
 * Android can deliver a stale P2P connection callback while an NSD/LAN connection
 * is being established, so the callback must not be allowed to replace that LAN
 * attempt (or an already active LAN transport).
 */
internal fun allowsWifiDirectApiProbe(
    transportState: TransportState,
    lanConnectionPending: Boolean
): Boolean = !lanConnectionPending &&
    !(transportState is TransportState.Connected && transportState.type == TransportType.LAN)

internal data class WifiDirectProbeSignal(
    val connected: Boolean,
    val host: String?,
    val lanConnectionPending: Boolean
)

internal fun wifiDirectProbeSignals(
    directStates: Flow<WifiDirectState>,
    connectingLanDeviceIds: Flow<String?>
): Flow<WifiDirectProbeSignal> = combine(
    directStates
        .map { state -> state.connected to state.groupOwnerAddress }
        .distinctUntilChanged(),
    connectingLanDeviceIds
        .map { it != null }
        .distinctUntilChanged()
) { direct, lanConnectionPending ->
    WifiDirectProbeSignal(
        connected = direct.first,
        host = direct.second,
        lanConnectionPending = lanConnectionPending
    )
}.distinctUntilChanged()

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
