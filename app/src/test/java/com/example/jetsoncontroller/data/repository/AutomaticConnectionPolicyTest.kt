package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticConnectionPolicyTest {
    private val first = RegisteredDevice(
        deviceId = "9b58f0b4-70bd-4ddb-a9a8-d3e879d9d137",
        deviceName = "MMS-D137"
    )
    private val second = RegisteredDevice(
        deviceId = "9b58f0b4-70bd-4ddb-a9a8-d3e879d91234",
        deviceName = "MMS-1234"
    )

    @Test
    fun `single registered device is the safe automatic target`() {
        assertEquals(first.deviceId, automaticTargetDeviceId(null, listOf(first)))
        assertNull(automaticTargetDeviceId(null, listOf(first, second)))
        assertEquals(second.deviceId, automaticTargetDeviceId(second.deviceId, listOf(first, second)))
        assertNull(automaticTargetDeviceId("not-registered-yet", listOf(first)))
    }

    @Test
    fun `direct fallback only starts while disconnected or on BLE`() {
        assertTrue(allowsAutomaticDirectFallback(TransportState.Disconnected))
        assertTrue(allowsAutomaticDirectFallback(TransportState.Connected(TransportType.BLE)))
        assertFalse(allowsAutomaticDirectFallback(TransportState.Connected(TransportType.LAN)))
        assertFalse(allowsAutomaticDirectFallback(TransportState.Connected(TransportType.WIFI_DIRECT)))
    }

    @Test
    fun `offline endpoints prefer wifi direct before LAN`() {
        assertTrue(
            shouldPreferWifiDirectBeforeLan(
                mobileSsid = null,
                jetsonWifiConnected = false
            )
        )
        assertTrue(
            shouldPreferWifiDirectBeforeLan(
                mobileSsid = "field-router",
                jetsonWifiConnected = false
            )
        )
        assertTrue(
            shouldPreferWifiDirectBeforeLan(
                mobileSsid = null,
                jetsonWifiConnected = true
            )
        )
        assertFalse(
            shouldPreferWifiDirectBeforeLan(
                mobileSsid = "field-router",
                jetsonWifiConnected = true
            )
        )
    }

    @Test
    fun `automatic direct waits for pending LAN attempt`() {
        assertFalse(
            allowsAutomaticDirectAttempt(
                TransportState.Disconnected,
                lanConnectionPending = true
            )
        )
        assertFalse(
            allowsAutomaticDirectAttempt(
                TransportState.Connected(TransportType.BLE),
                lanConnectionPending = true
            )
        )
        assertTrue(
            allowsAutomaticDirectAttempt(
                TransportState.Disconnected,
                lanConnectionPending = false
            )
        )
        assertTrue(
            allowsAutomaticDirectAttempt(
                TransportState.Connected(TransportType.BLE),
                lanConnectionPending = false
            )
        )
        assertFalse(
            allowsAutomaticDirectAttempt(
                TransportState.Connected(TransportType.LAN),
                lanConnectionPending = false
            )
        )
    }

    @Test
    fun `direct entry requires authenticated BLE for the selected device`() {
        assertEquals(
            WifiDirectEntryReadiness.WAITING_FOR_BLE,
            wifiDirectEntryReadiness(
                automatic = false,
                bleReady = false,
                bleDeviceId = null,
                targetDeviceId = first.deviceId,
                transportState = TransportState.Disconnected,
                lanConnectionPending = false
            )
        )
        assertEquals(
            WifiDirectEntryReadiness.WRONG_BLE_DEVICE,
            wifiDirectEntryReadiness(
                automatic = false,
                bleReady = true,
                bleDeviceId = second.deviceId,
                targetDeviceId = first.deviceId,
                transportState = TransportState.Disconnected,
                lanConnectionPending = false
            )
        )
        assertEquals(
            WifiDirectEntryReadiness.READY,
            wifiDirectEntryReadiness(
                automatic = false,
                bleReady = true,
                bleDeviceId = first.deviceId.uppercase(),
                targetDeviceId = first.deviceId,
                transportState = TransportState.Disconnected,
                lanConnectionPending = false
            )
        )
    }

    @Test
    fun `automatic direct entry stays blocked while LAN is pending`() {
        assertEquals(
            WifiDirectEntryReadiness.BLOCKED_BY_LAN,
            wifiDirectEntryReadiness(
                automatic = true,
                bleReady = true,
                bleDeviceId = first.deviceId,
                targetDeviceId = first.deviceId,
                transportState = TransportState.Connected(TransportType.BLE),
                lanConnectionPending = true
            )
        )
    }

    @Test
    fun `cancelled direct entry generation cannot start discovery`() {
        assertFalse(
            wifiDirectEntryIsCurrent(
                currentGeneration = 8,
                requestGeneration = 7,
                connectivityEnabled = true,
                pairingActive = false
            )
        )
        assertTrue(
            wifiDirectEntryIsCurrent(
                currentGeneration = 8,
                requestGeneration = 8,
                connectivityEnabled = true,
                pairingActive = false
            )
        )
        assertEquals(250L, wifiDirectCommandRetryDelayMillis(1))
        assertEquals(4_000L, wifiDirectCommandRetryDelayMillis(99))
    }

    @Test
    fun `wifi provisioning follow up matches the active transport`() {
        assertEquals(
            WifiProvisionFollowUp(false, true),
            wifiProvisionFollowUpForTransport(TransportType.BLE)
        )
        assertEquals(
            WifiProvisionFollowUp(false, true),
            wifiProvisionFollowUpForTransport(TransportType.WIFI_DIRECT)
        )
        assertEquals(
            WifiProvisionFollowUp(true, false),
            wifiProvisionFollowUpForTransport(TransportType.LAN)
        )
        assertEquals(330_000L, WIFI_PROVISION_LAN_HANDOFF_TIMEOUT_MILLIS)
    }

    @Test
    fun `provisioning handoff suppresses direct recovery until matching LAN arrives`() {
        assertFalse(
            allowsAutomaticDirectRecovery(
                connectivityEnabled = true,
                pairingActive = false,
                wifiProvisionLanHandoffActive = true
            )
        )
        assertTrue(
            isMatchingLanHandoffTransport(
                TransportState.Connected(
                    type = TransportType.LAN,
                    deviceId = first.deviceId.uppercase()
                ),
                first.deviceId
            )
        )
        assertFalse(
            isMatchingLanHandoffTransport(
                TransportState.Connected(
                    type = TransportType.WIFI_DIRECT,
                    deviceId = first.deviceId
                ),
                first.deviceId
            )
        )
    }

    @Test
    fun `BLE reconnect only uses a recently observed candidate from current scan`() {
        assertTrue(
            isFreshBleReconnectCandidate(
                candidateScanGeneration = 4,
                currentScanGeneration = 4,
                observedAtElapsedRealtimeMillis = 10_000,
                nowElapsedRealtimeMillis = 20_000,
                maxAgeMillis = 20_000
            )
        )
        assertFalse(
            isFreshBleReconnectCandidate(
                candidateScanGeneration = 3,
                currentScanGeneration = 4,
                observedAtElapsedRealtimeMillis = 19_000,
                nowElapsedRealtimeMillis = 20_000,
                maxAgeMillis = 20_000
            )
        )
        assertFalse(
            isFreshBleReconnectCandidate(
                candidateScanGeneration = 4,
                currentScanGeneration = 4,
                observedAtElapsedRealtimeMillis = 10_000,
                nowElapsedRealtimeMillis = 31_000,
                maxAgeMillis = 20_000
            )
        )
    }

    @Test
    fun `stale disconnected emission cannot restart scan beside an active GATT attempt`() {
        assertTrue(isCurrentBleFailureState(ConnectionState.Disconnected))
        assertTrue(isCurrentBleFailureState(ConnectionState.Error("GATT 133")))
        assertFalse(isCurrentBleFailureState(ConnectionState.Connecting("MMS-D137")))
        assertFalse(isCurrentBleFailureState(ConnectionState.Ready("MMS-D137")))
    }

    @Test
    fun `stopping LAN discovery prevents a scheduled retry`() {
        assertFalse(
            allowsAutomaticLanRetry(
                lanDiscoveryEnabled = false,
                transportState = TransportState.Disconnected,
                lanConnectionPending = false
            )
        )
        assertTrue(
            allowsAutomaticLanRetry(
                lanDiscoveryEnabled = true,
                transportState = TransportState.Disconnected,
                lanConnectionPending = false
            )
        )
    }

    @Test
    fun `automatic BLE reconnect waits for direct negotiation`() {
        assertFalse(
            allowsAutomaticBleReconnect(
                transportState = TransportState.Disconnected,
                lanConnectionPending = false,
                wifiDirectConnectionInProgress = true
            )
        )
        assertTrue(
            allowsAutomaticBleReconnect(
                transportState = TransportState.Disconnected,
                lanConnectionPending = false,
                wifiDirectConnectionInProgress = false
            )
        )
        assertTrue(
            allowsAutomaticBleReconnect(
                transportState = TransportState.Connected(
                    type = TransportType.WIFI_DIRECT,
                    deviceId = first.deviceId
                ),
                lanConnectionPending = false,
                wifiDirectConnectionInProgress = false
            )
        )
    }

    @Test
    fun `same target LAN can replace wifi direct`() {
        assertTrue(allowsAutomaticLanUpgrade(TransportState.Disconnected))
        assertTrue(allowsAutomaticLanUpgrade(TransportState.Connected(TransportType.BLE)))
        assertTrue(allowsAutomaticLanUpgrade(TransportState.Connected(TransportType.WIFI_DIRECT)))
        assertFalse(allowsAutomaticLanUpgrade(TransportState.Connected(TransportType.LAN)))
    }

    @Test
    fun `wifi direct callback cannot replace pending or connected LAN`() {
        val directTransport = TransportState.Connected(TransportType.WIFI_DIRECT)
        assertFalse(
            allowsWifiDirectApiProbe(
                directTransport,
                lanConnectionPending = true
            )
        )
        // The repository combines the pending-LAN flow with the P2P callback.
        // When a LAN attempt fails and pending changes to false, the unchanged
        // connected P2P group becomes eligible for probing again.
        assertTrue(
            allowsWifiDirectApiProbe(
                directTransport,
                lanConnectionPending = false
            )
        )
        assertFalse(
            allowsWifiDirectApiProbe(
                TransportState.Connected(TransportType.LAN),
                lanConnectionPending = false
            )
        )
        assertTrue(
            allowsWifiDirectApiProbe(
                TransportState.Connected(TransportType.BLE),
                lanConnectionPending = false
            )
        )
        assertTrue(
            allowsWifiDirectApiProbe(
                TransportState.Disconnected,
                lanConnectionPending = false
            )
        )
    }

    @Test
    fun `finishing LAN attempt reevaluates an unchanged wifi direct group`() = runBlocking {
        val directStates = MutableStateFlow(
            WifiDirectState(connected = true, groupOwnerAddress = "192.168.49.1")
        )
        val connectingLanDeviceIds = MutableStateFlow<String?>(first.deviceId)
        val observed = mutableListOf<WifiDirectProbeSignal>()
        val initialObserved = CompletableDeferred<Unit>()
        val collection = launch {
            wifiDirectProbeSignals(directStates, connectingLanDeviceIds)
                .take(2)
                .collect { signal ->
                    observed += signal
                    if (observed.size == 1) initialObserved.complete(Unit)
                }
        }

        initialObserved.await()
        connectingLanDeviceIds.value = null
        collection.join()

        assertEquals(listOf(true, false), observed.map { it.lanConnectionPending })
        assertEquals(listOf("192.168.49.1", "192.168.49.1"), observed.map { it.host })
    }

    @Test
    fun `wifi direct fallback requires one exact device name`() {
        val exact = WifiDirectPeer("MMS-D137", "aa:bb:cc:dd:ee:ff", 3)
        val unrelated = WifiDirectPeer("MMS-1234", "00:11:22:33:44:55", 3)
        assertEquals(exact, chooseAutomaticWifiDirectPeer(listOf(unrelated, exact), first.deviceId))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(exact, exact.copy(deviceAddress = "11:22:33:44:55:66")), first.deviceId))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(unrelated), first.deviceId))
    }

    @Test
    fun `selecting a freshly discovered peer does not resend direct mode command`() {
        assertTrue(
            shouldConnectPreparedWifiDirectPeer(
                preparedTargetDeviceId = first.deviceId.uppercase(),
                selectedTargetDeviceId = first.deviceId,
                discoveryAttempted = true,
                connected = false,
                connectingPeerAddress = null
            )
        )
        assertFalse(
            shouldConnectPreparedWifiDirectPeer(
                preparedTargetDeviceId = first.deviceId,
                selectedTargetDeviceId = second.deviceId,
                discoveryAttempted = true,
                connected = false,
                connectingPeerAddress = null
            )
        )
        assertFalse(
            shouldConnectPreparedWifiDirectPeer(
                preparedTargetDeviceId = first.deviceId,
                selectedTargetDeviceId = first.deviceId,
                discoveryAttempted = false,
                connected = false,
                connectingPeerAddress = null
            )
        )
    }

    @Test
    fun `three consecutive IP failures mark the device offline`() {
        assertFalse(ipConnectionIsOffline(2))
        assertTrue(ipConnectionIsOffline(3))
    }
}
