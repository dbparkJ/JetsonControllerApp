package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
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

/**
 * Wi-Fi Direct is an offline fallback, not an automatic replacement for an
 * infrastructure Wi-Fi connection. Some Jetson adapters cannot keep managed
 * Wi-Fi and a P2P group active at the same time, so starting P2P while the phone
 * is already on Wi-Fi can tear down the very LAN connection being discovered.
 */
internal fun allowsAutomaticDirectConnection(
    transportState: TransportState,
    infrastructureWifiConnected: Boolean
): Boolean = !infrastructureWifiConnected && allowsAutomaticDirectFallback(transportState)

internal fun shouldDisconnectAutomaticDirect(
    infrastructureWifiConnected: Boolean,
    explicitlyRequested: Boolean
): Boolean = infrastructureWifiConnected && !explicitlyRequested

internal fun allowsAutomaticLanUpgrade(
    transportState: TransportState,
    explicitlyRequestedDirect: Boolean = false
): Boolean = !explicitlyRequestedDirect && (
    allowsAutomaticDirectFallback(transportState) ||
        (transportState is TransportState.Connected &&
            transportState.type == TransportType.WIFI_DIRECT))

/**
 * A normal LAN connection always takes precedence over the Wi-Fi Direct fallback.
 * Android can deliver a stale P2P connection callback while an NSD/LAN connection
 * is being established, so the callback must not be allowed to replace that LAN
 * attempt (or an already active LAN transport).
 */
internal fun allowsWifiDirectApiProbe(
    transportState: TransportState,
    lanConnectionPending: Boolean,
    wifiProvisioningHandoffPending: Boolean = false
): Boolean = !lanConnectionPending && !wifiProvisioningHandoffPending &&
    !(transportState is TransportState.Connected && transportState.type == TransportType.LAN)

internal data class WifiDirectProbeSignal(
    val connected: Boolean,
    val host: String?,
    val lanConnectionPending: Boolean,
    val lanTransportActive: Boolean
)

internal fun wifiDirectProbeSignals(
    directStates: Flow<WifiDirectState>,
    connectingLanDeviceIds: Flow<String?>,
    transportStates: Flow<TransportState>
): Flow<WifiDirectProbeSignal> = combine(
    directStates
        .map { state -> state.connected to state.groupOwnerAddress }
        .distinctUntilChanged(),
    connectingLanDeviceIds
        .map { it != null }
        .distinctUntilChanged(),
    // Only LAN ownership affects probe eligibility. Direct success/errors must
    // not restart the probe itself or reset its finite recovery budget.
    transportStates
        .map { it is TransportState.Connected && it.type == TransportType.LAN }
        .distinctUntilChanged()
) { direct, lanConnectionPending, lanTransportActive ->
    WifiDirectProbeSignal(
        connected = direct.first,
        host = direct.second,
        lanConnectionPending = lanConnectionPending,
        lanTransportActive = lanTransportActive
    )
}.distinctUntilChanged()

internal fun chooseAutomaticWifiDirectPeer(
    peers: List<WifiDirectPeer>,
    deviceId: String,
    registeredDevices: List<RegisteredDevice>
): WifiDirectPeer? {
    return peers
        .filter { it.name.isNotBlank() && wifiDirectPeerMatchesTarget(it, deviceId, registeredDevices) }
        .singleOrNull()
}

internal fun ipConnectionIsOffline(consecutiveFailures: Int): Boolean =
    consecutiveFailures >= IP_STATUS_FAILURE_LIMIT
