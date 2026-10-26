package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.canonicalBleNameForDeviceId
import com.example.jetsoncontroller.model.legacyBleNameForDeviceId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Discovery identifies the link; its name must never replace the selected UUID. */
internal fun wifiDirectPeerMatchesTarget(
    peer: WifiDirectPeer,
    expectedDeviceId: String,
    registeredDevices: List<RegisteredDevice>
): Boolean {
    val matchedDevice = registeredDevices.filter { device ->
        peer.name.equals(device.deviceName, ignoreCase = true) ||
            peer.name.equals(canonicalBleNameForDeviceId(device.deviceId), ignoreCase = true) ||
            peer.name.equals(legacyBleNameForDeviceId(device.deviceId), ignoreCase = true)
    }.singleOrNull()
    return expectedDeviceId.equals(matchedDevice?.deviceId, ignoreCase = true)
}

/** Ready includes the boot time command ACK on capable backends. */
internal suspend fun awaitWifiDirectBootstrap(
    connection: StateFlow<ConnectionState>,
    expectedDeviceId: String,
    currentDeviceId: () -> String?,
    timeoutMillis: Long = 30_000L
): Boolean = withTimeoutOrNull(timeoutMillis) {
    val state = connection.first {
        (it is ConnectionState.Ready && expectedDeviceId.equals(currentDeviceId(), ignoreCase = true)) ||
            it is ConnectionState.Error || it is ConnectionState.RegistrationRequired
    }
    state is ConnectionState.Ready
} ?: false
