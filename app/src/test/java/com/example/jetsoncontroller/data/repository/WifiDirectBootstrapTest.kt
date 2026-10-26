package com.example.jetsoncontroller.data.repository

import android.content.Context
import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.data.bluetooth.BleScanState
import com.example.jetsoncontroller.data.bluetooth.BleScanner
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.data.network.LanDiscoveryManager
import com.example.jetsoncontroller.data.network.WifiAccessPointScanner
import com.example.jetsoncontroller.data.network.WifiAccessPointState
import com.example.jetsoncontroller.data.network.WifiDirectManager
import com.example.jetsoncontroller.data.network.WifiDirectApiStatus
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.data.rtk.MobileRtkRelayManager
import com.example.jetsoncontroller.model.BlePairingState
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MobileRtkRelayState
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.canonicalBleNameForDeviceId
import com.example.jetsoncontroller.model.legacyBleNameForDeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.mockito.Answers
import org.mockito.MockedConstruction
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiDirectBootstrapTest {
    @Test
    fun `only an unambiguous peer for the authenticated target can connect`() {
        val devices = listOf(RegisteredDevice(TARGET, "Target"), RegisteredDevice(OTHER, "Other"))
        for (name in listOf("TARGET", canonicalBleNameForDeviceId(TARGET), legacyBleNameForDeviceId(TARGET))) {
            assertTrue(wifiDirectPeerMatchesTarget(WifiDirectPeer(name, "00:11:22:33:44:55", 3), TARGET, devices))
        }
        for (name in listOf("Other", canonicalBleNameForDeviceId(OTHER), "Unknown")) {
            assertFalse(wifiDirectPeerMatchesTarget(WifiDirectPeer(name, "00:11:22:33:44:55", 3), TARGET, devices))
        }
        val ambiguous = devices.map { it.copy(deviceName = "Shared name") }
        assertFalse(wifiDirectPeerMatchesTarget(WifiDirectPeer("Shared name", "00:11:22:33:44:55", 3), TARGET, ambiguous))
        assertFalse(wifiDirectPeerMatchesTarget(WifiDirectPeer("Target", "00:11:22:33:44:55", 3), TARGET, emptyList()))
    }

    @Test
    fun `P2P waits for authenticated clock ACK from the selected device`() = runTest {
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected("phone"))
        var deviceId = "other"
        var connects = 0
        val task = async {
            if (awaitWifiDirectBootstrap(connection, "target", { deviceId })) connects++
        }
        runCurrent()
        assertEquals(0, connects)
        connection.value = ConnectionState.Ready("other")
        runCurrent()
        assertEquals(0, connects)
        connection.value = ConnectionState.Connected("target")
        deviceId = "TARGET"
        runCurrent()
        assertEquals(0, connects)
        connection.value = ConnectionState.Ready("target")
        task.await()
        assertEquals(1, connects)
    }

    @Test
    fun `failed or unavailable BLE never silently starts P2P`() = runTest {
        for (state in listOf(ConnectionState.Disconnected, ConnectionState.Error("sync failed"))) {
            val connection = MutableStateFlow<ConnectionState>(state)
            val task = async { awaitWifiDirectBootstrap(connection, "target", { "target" }, 100) }
            advanceTimeBy(101)
            assertFalse(task.await())
        }
    }

    @Test
    fun `cancelling pending preparation prevents late P2P connection`() = runTest {
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected("target"))
        var connects = 0
        val task = async {
            if (awaitWifiDirectBootstrap(connection, "target", { "target" })) connects++
        }
        runCurrent()
        task.cancelAndJoin()
        connection.value = ConnectionState.Ready("target")
        runCurrent()
        assertEquals(0, connects)
    }

    @Test
    fun `authentication error and registration request end preparation immediately`() = runTest {
        for (state in listOf(ConnectionState.Error("sync rejected"), ConnectionState.RegistrationRequired("peer", "target"))) {
            val before = testScheduler.currentTime
            assertFalse(awaitWifiDirectBootstrap(MutableStateFlow(state), "target", { "target" }))
            assertEquals(before, testScheduler.currentTime)
        }
    }

    @Test
    fun `Ready for another UUID cannot authorize target even when peer names match`() = runTest {
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Ready("same advertised name"))
        val before = testScheduler.currentTime
        assertFalse(awaitWifiDirectBootstrap(connection, "target", { "another UUID" }, 50))
        assertEquals(50, testScheduler.currentTime - before)
    }

    @Test
    fun `another peer cannot use the selected target clock ACK or replace its authentication UUID`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            f.repository.prepareManualWifiDirect(TARGET)
            val peer = WifiDirectPeer(canonicalBleNameForDeviceId(OTHER), "00:11:22:33:44:55", 3)
            f.repository.connectWifiDirect(peer)
            runCurrent()
            assertEquals(TARGET, f.repository.selectedDeviceId.value)
            assertTrue(f.connects.isEmpty())
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertTrue(f.connects.isEmpty())
            Mockito.verify(f.direct).markApiError(Mockito.contains("일치하지 않습니다"))
        }
    }

    @Test
    fun `replacement peer retires pending request and only latest peer connects after Ready`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            f.repository.prepareManualWifiDirect(TARGET)
            val first = WifiDirectPeer(canonicalBleNameForDeviceId(TARGET), "00:11:22:33:44:55", 3)
            val latest = first.copy(deviceAddress = "00:11:22:33:44:66")
            f.repository.connectWifiDirect(first)
            runCurrent()
            f.repository.connectWifiDirect(latest)
            runCurrent()
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertEquals(listOf(latest), f.connects)
        }
    }

    @Test
    fun `cancelled repository preparation never connects after late Ready`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            f.repository.prepareManualWifiDirect(TARGET)
            f.repository.connectWifiDirect(WifiDirectPeer(canonicalBleNameForDeviceId(TARGET), "00:11:22:33:44:55", 3))
            runCurrent()
            f.repository.cancelWifiDirectConnection()
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertTrue(f.connects.isEmpty())
        }
    }

    @Test
    fun `repository timeout reports error and never connects on later Ready`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            f.repository.prepareManualWifiDirect(TARGET)
            f.repository.connectWifiDirect(WifiDirectPeer(canonicalBleNameForDeviceId(TARGET), "00:11:22:33:44:55", 3))
            runCurrent()
            advanceTimeBy(30_001)
            runCurrent()
            Mockito.verify(f.direct).markApiError(Mockito.contains("완료하지 못했습니다"))
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertTrue(f.connects.isEmpty())
        }
    }

    @Test
    fun `removed credential cannot authorize P2P even with prior BLE Ready`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            f.secretAvailable = false
            f.repository.prepareManualWifiDirect(TARGET)
            f.ble.value = ConnectionState.Ready("target")
            f.repository.connectWifiDirect(WifiDirectPeer(canonicalBleNameForDeviceId(TARGET), "00:11:22:33:44:55", 3))
            runCurrent()
            assertTrue(f.connects.isEmpty())
            Mockito.verify(f.direct).markApiError(Mockito.contains("QR"))
        }
    }

    @Test
    fun `automatic preparation survives its own state and fallback timer reset`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            val peer = f.startAutomatic()
            runCurrent()
            assertEquals(peer, f.directState.value.bootstrapPeer)
            assertEquals(WifiDirectApiStatus.CHECKING, f.directState.value.apiStatus)
            f.flag("automaticDirectFallbackReady", false)
            runCurrent()
            assertEquals(peer, f.directState.value.bootstrapPeer)
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertEquals(listOf(peer), f.connects)
            assertNull(f.directState.value.bootstrapPeer)
        }
    }

    @Test
    fun `revoked automatic policy cancels pending preparation before late Ready`() = runTest {
        for (condition in listOf("infrastructure", "automaticConnectivityEnabled", "nearbyWifiPermissionGranted",
            "bluetoothPermissionGranted", "qrPairingActive", "wifiProvisioningHandoff")) {
            RepositoryFixture(testScheduler).use { f ->
                f.startAutomatic()
                runCurrent()
                assertNotNull(f.directState.value.bootstrapPeer)
                if (condition == "infrastructure") {
                    f.wifiState.value = WifiAccessPointState(infrastructureWifiConnected = true)
                } else f.flag(condition, condition in setOf("qrPairingActive", "wifiProvisioningHandoff"))
                runCurrent()
                assertNull("condition=$condition", f.directState.value.bootstrapPeer)
                f.ble.value = ConnectionState.Ready("target")
                runCurrent()
                assertTrue("condition=$condition", f.connects.isEmpty())
            }
        }
    }

    @Test
    fun `automatic connect rechecks policy even before observer handles its change`() = runTest {
        for (condition in listOf("infrastructure", "automaticConnectivityEnabled")) {
            RepositoryFixture(testScheduler).use { f ->
                f.beforeSecret = {
                    f.beforeSecret = {}
                    f.ble.value = ConnectionState.Ready("target")
                    if (condition == "infrastructure") {
                        f.wifiState.value = WifiAccessPointState(infrastructureWifiConnected = true)
                    } else f.flag(condition, false)
                }
                f.startAutomatic()
                runCurrent()
                assertTrue("condition=$condition", f.connects.isEmpty())
                assertNull(f.directState.value.bootstrapPeer)
            }
        }
    }

    @Test
    fun `explicit manual preparation keeps policy on infrastructure Wifi and auto off`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            f.repository.prepareManualWifiDirect(TARGET)
            val peer = WifiDirectPeer(canonicalBleNameForDeviceId(TARGET), "00:11:22:33:44:55", 3)
            f.repository.connectWifiDirect(peer)
            runCurrent()
            f.wifiState.value = WifiAccessPointState(infrastructureWifiConnected = true)
            f.flag("automaticConnectivityEnabled", false)
            runCurrent()
            assertEquals(peer, f.directState.value.bootstrapPeer)
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertEquals(listOf(peer), f.connects)
        }
    }

    @Test
    fun `pre-group timeout is published and retry uses the same peer without discovery`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            val peer = f.startAutomatic()
            runCurrent()
            advanceTimeBy(30_001)
            runCurrent()
            assertFalse(f.directState.value.connected)
            assertEquals(peer, f.directState.value.bootstrapPeer)
            assertEquals(WifiDirectApiStatus.ERROR, f.directState.value.apiStatus)
            assertTrue(f.directState.value.apiError!!.contains("완료하지 못했습니다"))
            f.directState.value = f.directState.value.copy(peers = emptyList())
            f.wifiState.value = WifiAccessPointState(infrastructureWifiConnected = true)
            f.repository.retryWifiDirectApi()
            runCurrent()
            assertEquals(WifiDirectApiStatus.CHECKING, f.directState.value.apiStatus)
            assertNull(f.directState.value.apiError)
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertEquals(listOf(peer), f.connects)
        }
    }

    @Test
    fun `Bluetooth permission failure before group is visible and cancel clears it`() = runTest {
        RepositoryFixture(testScheduler).use { f ->
            f.repository.prepareManualWifiDirect(TARGET)
            f.flag("bluetoothPermissionGranted", false)
            f.repository.connectWifiDirect(WifiDirectPeer(canonicalBleNameForDeviceId(TARGET), "00:11:22:33:44:55", 3))
            runCurrent()
            assertEquals(WifiDirectApiStatus.ERROR, f.directState.value.apiStatus)
            assertTrue(f.directState.value.apiError!!.contains("권한"))
            assertFalse(f.directState.value.connected)
            f.repository.cancelWifiDirectConnection()
            runCurrent()
            assertNull(f.directState.value.bootstrapPeer)
            assertNull(f.directState.value.apiError)
            f.flag("bluetoothPermissionGranted", true)
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertTrue(f.connects.isEmpty())
        }
    }

    @Test
    fun `automatic custom name remains only a candidate until selected UUID authenticates`() = runTest {
        RepositoryFixture(testScheduler, targetName = "GEO-X-1").use { f ->
            val peer = f.startAutomatic("GEO-X-1")
            runCurrent()
            assertEquals(peer, f.directState.value.bootstrapPeer)
            assertEquals(TARGET, f.repository.selectedDeviceId.value)
            f.authenticatedDeviceId = OTHER
            f.ble.value = ConnectionState.Ready("other")
            runCurrent()
            assertTrue(f.connects.isEmpty())
            assertEquals(TARGET, f.repository.selectedDeviceId.value)
            f.ble.value = ConnectionState.Connected("target")
            runCurrent()
            f.authenticatedDeviceId = TARGET
            f.ble.value = ConnectionState.Ready("target")
            runCurrent()
            assertEquals(listOf(peer), f.connects)
            assertEquals(TARGET, f.repository.selectedDeviceId.value)
        }
    }

    private class RepositoryFixture(
        private val scheduler: TestCoroutineScheduler,
        targetName: String = canonicalBleNameForDeviceId(TARGET)
    ) : AutoCloseable {
        val ble = MutableStateFlow<ConnectionState>(ConnectionState.Connected("target"))
        val directState = MutableStateFlow(WifiDirectState(enabled = true))
        val wifiState = MutableStateFlow(WifiAccessPointState())
        val connects = mutableListOf<WifiDirectPeer>()
        lateinit var direct: WifiDirectManager
        var secretAvailable = true
        var authenticatedDeviceId = TARGET
        var beforeSecret: () -> Unit = {}
        private val constructions = mutableListOf<MockedConstruction<*>>()
        val repository: JetsonRepository

        init {
            Dispatchers.setMain(StandardTestDispatcher(scheduler))
            try {
                construct(BleScanner::class.java) { mock ->
                    Mockito.`when`(mock.devices).thenReturn(MutableStateFlow<List<JetsonDevice>>(emptyList()))
                    Mockito.`when`(mock.isScanning).thenReturn(MutableStateFlow(false))
                    Mockito.`when`(mock.scanState).thenReturn(MutableStateFlow<BleScanState>(BleScanState.Idle))
                }
                construct(BleGattClient::class.java) { mock ->
                    Mockito.`when`(mock.connectionState).thenReturn(ble)
                    Mockito.`when`(mock.currentDeviceId()).thenAnswer { authenticatedDeviceId }
                    Mockito.`when`(mock.pairingState).thenReturn(MutableStateFlow<BlePairingState>(BlePairingState.Idle))
                    Mockito.`when`(mock.status).thenReturn(MutableStateFlow(JetsonStatus()))
                }
                construct(WifiDirectManager::class.java) { mock ->
                    direct = mock
                    WifiDirectManager::class.java.getDeclaredField("_state").apply {
                        isAccessible = true
                    }.set(mock, directState)
                    Mockito.`when`(mock.state).thenReturn(directState)
                }
                construct(MobileRtkRelayManager::class.java) { mock ->
                    Mockito.`when`(mock.state).thenReturn(MutableStateFlow(MobileRtkRelayState()))
                }
                construct(WifiAccessPointScanner::class.java) { mock ->
                    Mockito.`when`(mock.state).thenReturn(wifiState)
                }
                construct(LanDiscoveryManager::class.java) { mock ->
                    Mockito.`when`(mock.discoveredEndpoints).thenReturn(MutableStateFlow<List<DeviceEndpoint>>(emptyList()))
                    Mockito.`when`(mock.lastSeenAtEpochMillis).thenReturn(MutableStateFlow<Map<String, Long>>(emptyMap()))
                    Mockito.`when`(mock.isDiscovering).thenReturn(MutableStateFlow(false))
                    Mockito.`when`(mock.error).thenReturn(MutableStateFlow<String?>(null))
                }
                val credentials = Mockito.mock(DeviceCredentialStore::class.java,
                    Mockito.withSettings().defaultAnswer { call ->
                        if (call.method.name == "getSecret") {
                            beforeSecret()
                            if (secretAvailable) "00".repeat(32) else null
                        }
                        else Answers.RETURNS_DEFAULTS.answer(call)
                    })
                Mockito.`when`(credentials.registeredDevices).thenReturn(flowOf(listOf(TARGET, OTHER).map {
                    DeviceCredentialStore.StoredCredential(it, if (it == TARGET) targetName else canonicalBleNameForDeviceId(it), "unused", "unused")
                }))
                repository = JetsonRepository(Mockito.mock(Context::class.java), credentials)
                scheduler.runCurrent()
                @Suppress("UNCHECKED_CAST")
                val permission = field("bluetoothPermissionGranted") as MutableStateFlow<Boolean>
                permission.value = true
            } catch (error: Throwable) {
                constructions.asReversed().forEach { it.close() }
                Dispatchers.resetMain()
                throw error
            }
        }

        private fun <T> construct(type: Class<T>, initialize: (T) -> Unit) {
            constructions += Mockito.mockConstruction(type,
                Mockito.withSettings().defaultAnswer { call ->
                    if (type == WifiDirectManager::class.java && call.method.name == "connect") {
                        connects += call.arguments[0] as WifiDirectPeer
                        direct.clearBootstrap()
                        directState.value = directState.value.copy(connectingPeerAddress = connects.last().deviceAddress)
                        null
                    } else if (type == WifiDirectManager::class.java && call.method.name in
                        setOf("markBootstrapPreparing", "clearBootstrap", "markApiError")) {
                        call.callRealMethod()
                    } else Answers.RETURNS_DEFAULTS.answer(call)
                }) { mock, _ -> initialize(mock) }
        }

        private fun field(name: String): Any = JetsonRepository::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.get(repository)!!

        @Suppress("UNCHECKED_CAST")
        fun flag(name: String, value: Boolean) {
            (field(name) as MutableStateFlow<Boolean>).value = value
        }

        fun startAutomatic(name: String = canonicalBleNameForDeviceId(TARGET)): WifiDirectPeer {
            repository.prepareManualWifiDirect(TARGET)
            (field("explicitWifiDirectRequested") as AtomicBoolean).set(false)
            flag("nearbyWifiPermissionGranted", true)
            flag("automaticDirectFallbackReady", true)
            val peer = WifiDirectPeer(name, "00:11:22:33:44:55", 3)
            directState.value = directState.value.copy(peers = listOf(peer))
            return peer
        }

        override fun close() {
            (field("scope") as CoroutineScope).cancel()
            scheduler.runCurrent()
            constructions.asReversed().forEach { it.close() }
            Dispatchers.resetMain()
        }
    }

    companion object {
        private const val TARGET = "00000000-0000-4000-8000-000000000001"
        private const val OTHER = "00000000-0000-4000-8000-000000000002"
    }
}
