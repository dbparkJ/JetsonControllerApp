package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
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
    fun `automatic direct never replaces infrastructure wifi`() {
        assertFalse(
            allowsAutomaticDirectConnection(
                TransportState.Disconnected,
                infrastructureWifiConnected = true
            )
        )
        assertFalse(
            allowsAutomaticDirectConnection(
                TransportState.Connected(TransportType.BLE),
                infrastructureWifiConnected = true
            )
        )
        assertTrue(
            allowsAutomaticDirectConnection(
                TransportState.Connected(TransportType.BLE),
                infrastructureWifiConnected = false
            )
        )
    }

    @Test
    fun `infrastructure wifi removes only automatic direct groups`() {
        assertTrue(
            shouldDisconnectAutomaticDirect(
                infrastructureWifiConnected = true,
                explicitlyRequested = false
            )
        )
        assertFalse(
            shouldDisconnectAutomaticDirect(
                infrastructureWifiConnected = true,
                explicitlyRequested = true
            )
        )
        assertFalse(
            shouldDisconnectAutomaticDirect(
                infrastructureWifiConnected = false,
                explicitlyRequested = false
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
        assertFalse(
            allowsWifiDirectApiProbe(
                TransportState.Disconnected,
                lanConnectionPending = false,
                wifiProvisioningHandoffPending = true
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
    fun `three consecutive IP failures mark the device offline`() {
        assertFalse(ipConnectionIsOffline(2))
        assertTrue(ipConnectionIsOffline(3))
    }
}
