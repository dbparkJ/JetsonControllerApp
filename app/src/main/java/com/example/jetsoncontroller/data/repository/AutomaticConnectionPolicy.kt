package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.ConnectionState
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

/**
 * LAN discovery has priority over the automatic Wi-Fi Direct fallback. Waiting
 * for an in-flight LAN probe prevents both transports from racing to connect to
 * the same device and the eventual LAN success from cancelling P2P negotiation.
 */
internal fun allowsAutomaticDirectAttempt(
    transportState: TransportState,
    lanConnectionPending: Boolean
): Boolean = !lanConnectionPending && allowsAutomaticDirectFallback(transportState)

internal enum class WifiDirectEntryReadiness {
    READY,
    WAITING_FOR_BLE,
    WRONG_BLE_DEVICE,
    BLOCKED_BY_LAN
}

/**
 * P2P discovery is only safe after the intended Jetson has completed its
 * authenticated BLE handshake. Automatic fallback additionally yields to a
 * LAN connection already in flight.
 */
internal fun wifiDirectEntryReadiness(
    automatic: Boolean,
    bleReady: Boolean,
    bleDeviceId: String?,
    targetDeviceId: String,
    transportState: TransportState,
    lanConnectionPending: Boolean
): WifiDirectEntryReadiness {
    if (
        automatic && !allowsAutomaticDirectAttempt(
            transportState,
            lanConnectionPending
        )
    ) {
        return WifiDirectEntryReadiness.BLOCKED_BY_LAN
    }
    if (!bleReady) {
        return WifiDirectEntryReadiness.WAITING_FOR_BLE
    }
    if (!bleDeviceId.equals(targetDeviceId, ignoreCase = true)) {
        return WifiDirectEntryReadiness.WRONG_BLE_DEVICE
    }
    return WifiDirectEntryReadiness.READY
}

internal fun wifiDirectEntryIsCurrent(
    currentGeneration: Long,
    requestGeneration: Long,
    connectivityEnabled: Boolean,
    pairingActive: Boolean
): Boolean = currentGeneration == requestGeneration &&
    connectivityEnabled &&
    !pairingActive

internal fun shouldConnectPreparedWifiDirectPeer(
    preparedTargetDeviceId: String?,
    selectedTargetDeviceId: String,
    discoveryAttempted: Boolean,
    connected: Boolean,
    connectingPeerAddress: String?
): Boolean = preparedTargetDeviceId.equals(selectedTargetDeviceId, ignoreCase = true) &&
    discoveryAttempted &&
    !connected &&
    connectingPeerAddress == null

internal fun wifiDirectCommandRetryDelayMillis(failureCount: Int): Long {
    val exponent = (failureCount - 1).coerceIn(0, 4)
    return (250L shl exponent).coerceAtMost(4_000L)
}

internal data class WifiProvisionFollowUp(
    val pollCurrentEndpoint: Boolean,
    val waitForLanHandoff: Boolean
)

internal fun wifiProvisionFollowUpForTransport(
    transportType: TransportType
): WifiProvisionFollowUp = when (transportType) {
    TransportType.BLE -> WifiProvisionFollowUp(
        pollCurrentEndpoint = false,
        waitForLanHandoff = true
    )
    TransportType.WIFI_DIRECT -> WifiProvisionFollowUp(
        pollCurrentEndpoint = false,
        waitForLanHandoff = true
    )
    TransportType.LAN -> WifiProvisionFollowUp(
        pollCurrentEndpoint = true,
        waitForLanHandoff = false
    )
}

internal fun allowsAutomaticLanUpgrade(transportState: TransportState): Boolean =
    allowsAutomaticDirectFallback(transportState) ||
        (transportState is TransportState.Connected &&
            transportState.type == TransportType.WIFI_DIRECT)

internal fun allowsAutomaticLanRetry(
    lanDiscoveryEnabled: Boolean,
    transportState: TransportState,
    lanConnectionPending: Boolean
): Boolean = lanDiscoveryEnabled &&
    !lanConnectionPending &&
    allowsAutomaticLanUpgrade(transportState)

internal fun allowsAutomaticBleReconnect(
    transportState: TransportState,
    lanConnectionPending: Boolean,
    wifiDirectConnectionInProgress: Boolean
): Boolean = (
    transportState !is TransportState.Connected ||
        transportState.type == TransportType.WIFI_DIRECT
    ) &&
    !lanConnectionPending &&
    !wifiDirectConnectionInProgress

internal fun allowsAutomaticDirectRecovery(
    connectivityEnabled: Boolean,
    pairingActive: Boolean,
    wifiProvisionLanHandoffActive: Boolean
): Boolean = connectivityEnabled &&
    !pairingActive &&
    !wifiProvisionLanHandoffActive

internal fun isMatchingLanHandoffTransport(
    transportState: TransportState,
    targetDeviceId: String
): Boolean = transportState is TransportState.Connected &&
    transportState.type == TransportType.LAN &&
    transportState.deviceId.equals(targetDeviceId, ignoreCase = true)

internal fun isFreshBleReconnectCandidate(
    candidateScanGeneration: Long,
    currentScanGeneration: Long,
    observedAtElapsedRealtimeMillis: Long,
    nowElapsedRealtimeMillis: Long,
    maxAgeMillis: Long
): Boolean = candidateScanGeneration > 0L &&
    candidateScanGeneration == currentScanGeneration &&
    observedAtElapsedRealtimeMillis > 0L &&
    nowElapsedRealtimeMillis >= observedAtElapsedRealtimeMillis &&
    nowElapsedRealtimeMillis - observedAtElapsedRealtimeMillis <= maxAgeMillis

internal fun isCurrentBleFailureState(connectionState: ConnectionState): Boolean =
    connectionState is ConnectionState.Disconnected ||
        connectionState is ConnectionState.Error

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
