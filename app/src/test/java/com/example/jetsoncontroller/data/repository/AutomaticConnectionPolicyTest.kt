package com.example.jetsoncontroller.data.repository

import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.canonicalBleNameForDeviceId
import com.example.jetsoncontroller.model.legacyBleNameForDeviceId
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
        assertFalse(allowsAutomaticLanUpgrade(TransportState.Disconnected, explicitlyRequestedDirect = true))
        assertFalse(allowsAutomaticLanUpgrade(TransportState.Connected(TransportType.WIFI_DIRECT), explicitlyRequestedDirect = true))
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
            wifiDirectProbeSignals(directStates, connectingLanDeviceIds, MutableStateFlow(TransportState.Disconnected))
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
    fun `leaving established LAN reevaluates the same idle P2P group`() = runBlocking {
        val direct = MutableStateFlow(WifiDirectState(connected = true, groupOwnerAddress = "192.168.49.1"))
        val transport = MutableStateFlow<TransportState>(TransportState.Connected(TransportType.LAN))
        val observed = mutableListOf<WifiDirectProbeSignal>()
        val initial = CompletableDeferred<Unit>()
        val collection = launch {
            wifiDirectProbeSignals(direct, MutableStateFlow(null), transport).take(2).collect {
                observed += it
                if (observed.size == 1) initial.complete(Unit)
            }
        }
        initial.await()
        transport.value = TransportState.Disconnected
        collection.join()
        assertEquals(listOf(true, false), observed.map { it.lanTransportActive })
        assertTrue(observed.all { it.connected && !it.lanConnectionPending })
        assertEquals(1, observed.map { it.host }.distinct().size)
    }

    @Test
    fun `wifi direct fallback requires one exact device name`() {
        val exact = WifiDirectPeer("MMS-D137", "aa:bb:cc:dd:ee:ff", 3)
        val unrelated = WifiDirectPeer("MMS-1234", "00:11:22:33:44:55", 3)
        assertEquals(exact, chooseAutomaticWifiDirectPeer(listOf(unrelated, exact), first.deviceId, listOf(first, second)))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(exact, exact.copy(deviceAddress = "11:22:33:44:55:66")), first.deviceId, listOf(first)))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(unrelated), first.deviceId, listOf(first, second)))
    }

    @Test
    fun `unique custom name and UUID names select only the registered target`() {
        val target = RegisteredDevice("d606c26d-98d6-4b09-99d7-c3da7dda4de0", "GEO-X-1")
        val registered = listOf(target, second)
        for (name in listOf("geo-x-1", canonicalBleNameForDeviceId(target.deviceId), legacyBleNameForDeviceId(target.deviceId))) {
            val peer = WifiDirectPeer(name, "aa:bb:cc:dd:ee:ff", 3)
            assertEquals(peer, chooseAutomaticWifiDirectPeer(listOf(peer), target.deviceId.uppercase(), registered))
            assertTrue(wifiDirectPeerMatchesTarget(peer, target.deviceId, registered))
        }
        for (name in listOf("MMS-1234", "unknown", "")) {
            assertNull(chooseAutomaticWifiDirectPeer(listOf(WifiDirectPeer(name, "other", 3)), target.deviceId, registered))
        }
        val peer = WifiDirectPeer(target.deviceName, "aa:bb:cc:dd:ee:ff", 3)
        assertNull(chooseAutomaticWifiDirectPeer(listOf(peer), target.deviceId, emptyList()))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(peer), "not-registered", registered))
        assertEquals(target.deviceId, automaticTargetDeviceId(target.deviceId, registered))
        assertNull(automaticTargetDeviceId(null, registered))
    }

    @Test
    fun `duplicate custom names and alias collisions cannot choose an automatic peer`() {
        val target = first.copy(deviceName = "GEO-X-1")
        val peer = WifiDirectPeer("geo-x-1", "aa:bb:cc:dd:ee:ff", 3)
        val duplicateNames = listOf(target, second.copy(deviceName = "geo-x-1"))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(peer), target.deviceId, duplicateNames))
        assertFalse(wifiDirectPeerMatchesTarget(peer, target.deviceId, duplicateNames))
        assertNull(chooseAutomaticWifiDirectPeer(
            listOf(peer, peer.copy(deviceAddress = "11:22:33:44:55:66")), target.deviceId, listOf(target)
        ))
        val canonical = peer.copy(name = canonicalBleNameForDeviceId(target.deviceId))
        val collision = listOf(target, second.copy(deviceName = canonical.name))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(canonical), target.deviceId, collision))
        assertFalse(wifiDirectPeerMatchesTarget(canonical, target.deviceId, collision))
        assertNull(chooseAutomaticWifiDirectPeer(listOf(peer, canonical), target.deviceId, listOf(target)))
    }

    @Test
    fun `three consecutive IP failures mark the device offline`() {
        assertFalse(ipConnectionIsOffline(2))
        assertTrue(ipConnectionIsOffline(3))
    }
}
