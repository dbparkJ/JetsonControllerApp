package com.example.jetsoncontroller.data.repository

import android.content.Context
import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.data.bluetooth.BleScanState
import com.example.jetsoncontroller.data.bluetooth.BleScanner
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.data.network.LanDiscoveryManager
import com.example.jetsoncontroller.data.network.LocalApiClient
import com.example.jetsoncontroller.data.network.LocalControlApi
import com.example.jetsoncontroller.data.network.WifiAccessPointScanner
import com.example.jetsoncontroller.data.network.WifiAccessPointState
import com.example.jetsoncontroller.data.network.WifiDirectManager
import com.example.jetsoncontroller.data.network.WifiDirectPeer
import com.example.jetsoncontroller.data.network.WifiDirectState
import com.example.jetsoncontroller.data.rtk.MobileRtkRelayManager
import com.example.jetsoncontroller.data.transport.ControlTransport
import com.example.jetsoncontroller.data.transport.TransportCapabilities
import com.example.jetsoncontroller.data.transport.TransportCoordinator
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.BlePairingState
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.DeviceEndpoint
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MobileRtkRelayState
import com.example.jetsoncontroller.model.canonicalBleNameForDeviceId
import com.example.jetsoncontroller.protocol.JetsonCommand
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.Answers
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume

/**
 * Runs the production Repository and TransportCoordinator. Only Android hardware
 * managers are replaced; no copy of the failure/intent policy exists in this test.
 * LAB coverage stops at manager calls and does not claim a real OS group removal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JetsonRepositoryStabilityTest {
    @Test
    fun `R1 two API failures on a present Direct link do not request cleanup`() = runTest {
        Harness(testScheduler).use { h ->
            val transport = h.activateDirect()
            repeat(IP_STATUS_FAILURE_LIMIT - 1) { assertFalse(h.repository.refreshStatus()) }
            testScheduler.runCurrent()

            assertEquals(2, transport.statusRequests)
            assertEquals(0, h.cleanupCalls())
            assertEquals(0, h.rtkStopCalls())
            assertTrue(h.directState.value.connected)
        }
    }

    @Test
    fun `R2 API failure threshold alone must not request Direct group cleanup`() = runTest {
        Harness(testScheduler).use { h ->
            val transport = h.activateDirect()
            repeat(IP_STATUS_FAILURE_LIMIT) { assertFalse(h.repository.refreshStatus()) }
            testScheduler.runCurrent()

            assertEquals(3, transport.statusRequests)
            assertTrue("No link-loss callback was injected", h.directState.value.connected)
            println("LAB H1 failures=3 link=present cleanupCalls=${h.cleanupCalls()} rtkStopCalls=${h.rtkStopCalls()}")
            assertEquals("API failures must not authorize physical group cleanup", 0, h.cleanupCalls())
            assertTrue(h.repository.transportState.value is TransportState.Error)
            assertFalse("Unavailable control must reject mutations", h.repository.sendCommand(JetsonCommand.REBOOT))
            assertEquals("RTK retains its existing conservative shutdown", 1, h.rtkStopCalls())
        }
    }

    @Test
    fun `R2 same-link API recovery restores authenticated transport without reconnecting P2P`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.runCurrent()
            assertEquals(1, h.probeCalls)
            h.probeSucceeds = true
            testScheduler.advanceTimeBy(750)
            testScheduler.runCurrent()
            val restored = h.repository.transportState.value as TransportState.Connected
            assertEquals(DEVICE_ID, restored.deviceId)
            assertEquals(TransportType.WIFI_DIRECT, restored.type)
            assertEquals(2, h.probeCalls)
            assertEquals(0, h.cleanupCalls())
            assertEquals(0, h.directConnectCalls())
        }
    }

    @Test
    fun `R13 exhausted API recovery stays unavailable until explicit retry starts a new episode`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.advanceTimeBy(120_000)
            testScheduler.runCurrent()
            assertEquals(3, h.probeCalls)
            assertEquals(listOf(0L, 750L, 2250L), h.probeTimes)
            assertTrue(h.repository.transportState.value is TransportState.Error)
            repeat(10) { assertFalse(h.repository.refreshStatus()) }
            assertEquals(3, h.probeCalls)
            assertEquals(0, h.cleanupCalls())

            h.probeSucceeds = true
            h.repository.retryWifiDirectApi()
            testScheduler.runCurrent()
            assertEquals(4, h.probeCalls)
            assertTrue(h.repository.transportState.value is TransportState.Connected)
            assertEquals(0, h.cleanupCalls())
        }
    }

    @Test
    fun `R13 hung probes consume at most the existing 62250 millisecond episode budget`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.holdProbe = true
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.advanceTimeBy(62_250)
            testScheduler.runCurrent()
            assertEquals(3, h.probeCalls)
            assertEquals(listOf(0L, 20_750L, 42_250L), h.probeTimes)
            assertEquals(3, h.probeCancellations)
            assertFalse(h.probeJobActive())
            assertTrue(h.repository.transportState.value is TransportState.Error)
            assertEquals(0, h.cleanupCalls())
        }
    }

    @Test
    fun `R8 a different authenticated device stops recovery without accepting control`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.probeSucceeds = true
            h.helloDeviceId = OTHER_DEVICE_ID
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.advanceTimeBy(120_000)
            testScheduler.runCurrent()
            assertEquals(1, h.probeCalls)
            assertTrue(h.repository.transportState.value is TransportState.Error)
            assertFalse(h.repository.sendCommand(JetsonCommand.REBOOT))
            assertEquals(0, h.cleanupCalls())
        }
    }

    @Test
    fun `R3 disconnect cancels a queued recovery before any API probe starts`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            h.repository.disconnect()
            testScheduler.advanceTimeBy(120_000)
            testScheduler.runCurrent()
            assertEquals(0, h.probeCalls)
            assertFalse(h.probeJobActive())
            assertEquals(TransportState.Disconnected, h.repository.transportState.value)
        }
    }

    @Test
    fun `R4 target switch cancels old recovery IO and rejects its late result`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.holdProbe = true
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.runCurrent()
            val pending = requireNotNull(h.pendingProbe)
            h.repository.prepareManualWifiDirect(OTHER_DEVICE_ID)
            testScheduler.runCurrent()
            assertEquals(1, h.probeCancellations)
            pending.resume(h.helloResponse())
            testScheduler.runCurrent()
            assertEquals(OTHER_DEVICE_ID, h.repository.selectedDeviceId.value)
            assertEquals(TransportState.Disconnected, h.repository.transportState.value)
        }
    }

    @Test
    fun `R4 physical Direct loss cancels API recovery and keeps control unavailable`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.holdProbe = true
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.runCurrent()
            h.directState.value = WifiDirectState(connected = false)
            testScheduler.runCurrent()
            assertEquals(1, h.probeCancellations)
            assertFalse(h.probeJobActive())
            assertEquals(TransportState.Disconnected, h.repository.transportState.value)
            assertTrue(h.cleanupCalls() > 0)
            assertTrue(h.rtkStopCalls() > 0)
            assertTrue(h.manualDirectRequested())
        }
    }

    @Test
    fun `R7 non-user Direct loss preserves the manual transport choice`() = runTest {
        Harness(testScheduler).use { h ->
            h.repository.prepareManualWifiDirect(DEVICE_ID)
            h.activateDirect()
            h.wifiState.value = WifiAccessPointState(infrastructureWifiConnected = true)
            testScheduler.runCurrent()
            assertTrue(h.manualDirectRequested())
            assertFalse(allowsAutomaticLanUpgrade(h.repository.transportState.value, h.manualDirectRequested()))

            h.directState.value = WifiDirectState(connected = false)
            testScheduler.runCurrent()

            println("LAB H2 event=link-loss manualDirect=${h.manualDirectRequested()} actual=${h.repository.transportState.value::class.simpleName}")
            assertTrue("Only user transport changes may clear manual Direct intent", h.manualDirectRequested())
            assertFalse(allowsAutomaticLanUpgrade(h.repository.transportState.value, h.manualDirectRequested()))
        }
    }

    @Test
    fun `R4 late previous-session failure does not increment the current counter`() = runTest {
        Harness(testScheduler).use { h ->
            val transport = h.activateDirect()
            transport.beforeResponse = {
                h.coordinator.setActiveTransport(FakeTransport(), deviceId = "replacement-device")
            }
            assertFalse(h.repository.refreshStatus())
            testScheduler.runCurrent()
            assertEquals(0, h.failureCount())
            assertEquals(0, h.cleanupCalls())
        }
    }

    @Test
    fun `R7 manual Direct reconnects through the existing fallback when wifi is absent`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.repository.configureAutomaticConnectivity(true, false, true, false)
            testScheduler.runCurrent()
            h.directState.value = WifiDirectState(peers = listOf(
                WifiDirectPeer(canonicalBleNameForDeviceId(DEVICE_ID), "02:00:00:00:00:01", 3)
            ))
            testScheduler.runCurrent()
            assertTrue(h.manualDirectRequested())
            testScheduler.advanceTimeBy(750)
            testScheduler.runCurrent()
            assertEquals(1, h.directConnectCalls())
        }
    }

    @Test
    fun `R3 explicit disconnect cancels fallback despite subsequent peer callbacks`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.repository.configureAutomaticConnectivity(true, false, true, false)
            h.repository.disconnect()
            h.directState.value = WifiDirectState(peers = listOf(
                WifiDirectPeer(canonicalBleNameForDeviceId(DEVICE_ID), "02:00:00:00:00:01", 3)
            ))
            h.repository.configureAutomaticConnectivity(true, false, true, false)
            testScheduler.advanceTimeBy(600_000)
            testScheduler.runCurrent()
            assertFalse(h.manualDirectRequested())
            assertEquals(0, h.directConnectCalls())
        }
    }

    private class FakeTransport : ControlTransport {
        override val type = TransportType.WIFI_DIRECT
        override val capabilities = TransportCapabilities(true, true, true, true, true)
        var statusRequests = 0
        var beforeResponse: () -> Unit = {}
        override suspend fun ping() = true
        override suspend fun getStatus(): Result<JetsonStatus> {
            statusRequests += 1
            beforeResponse()
            return Result.failure(IOException("Test transport: transient API timeout"))
        }
        override suspend fun sendCommand(command: JetsonCommand, payload: ByteArray) = Result.success(Unit)
        override suspend fun disconnect() = Unit
    }

    private class Harness(private val scheduler: TestCoroutineScheduler) : AutoCloseable {
        private val constructions = mutableListOf<MockedConstruction<*>>()
        val directState = MutableStateFlow(
            WifiDirectState(connected = true, groupOwnerAddress = "192.0.2.1")
        )
        val wifiState = MutableStateFlow(WifiAccessPointState())
        private lateinit var directManager: WifiDirectManager
        private lateinit var relayManager: MobileRtkRelayManager
        var probeSucceeds = false
        var holdProbe = false
        var helloDeviceId = DEVICE_ID
        var probeCalls = 0
        var probeCancellations = 0
        val probeTimes = mutableListOf<Long>()
        var pendingProbe: CancellableContinuation<Any>? = null
        val repository: JetsonRepository
        val coordinator: TransportCoordinator

        init {
            Dispatchers.setMain(StandardTestDispatcher(scheduler))
            try {
                construction(BleScanner::class.java) { mock ->
                    Mockito.`when`(mock.devices).thenReturn(MutableStateFlow<List<JetsonDevice>>(emptyList()))
                    Mockito.`when`(mock.isScanning).thenReturn(MutableStateFlow(false))
                    Mockito.`when`(mock.scanState).thenReturn(MutableStateFlow<BleScanState>(BleScanState.Idle))
                }
                construction(BleGattClient::class.java) { mock ->
                    Mockito.`when`(mock.connectionState).thenReturn(MutableStateFlow<ConnectionState>(ConnectionState.Disconnected))
                    Mockito.`when`(mock.pairingState).thenReturn(MutableStateFlow<BlePairingState>(BlePairingState.Idle))
                    Mockito.`when`(mock.status).thenReturn(MutableStateFlow(JetsonStatus()))
                }
                construction(WifiDirectManager::class.java) { mock ->
                    directManager = mock
                    Mockito.`when`(mock.state).thenReturn(directState)
                }
                construction(MobileRtkRelayManager::class.java) { mock ->
                    relayManager = mock
                    Mockito.`when`(mock.state).thenReturn(MutableStateFlow(MobileRtkRelayState()))
                }
                construction(WifiAccessPointScanner::class.java) { mock ->
                    Mockito.`when`(mock.state).thenReturn(wifiState)
                }
                construction(LanDiscoveryManager::class.java) { mock ->
                    Mockito.`when`(mock.discoveredEndpoints).thenReturn(MutableStateFlow<List<DeviceEndpoint>>(emptyList()))
                    Mockito.`when`(mock.lastSeenAtEpochMillis).thenReturn(MutableStateFlow<Map<String, Long>>(emptyMap()))
                    Mockito.`when`(mock.isDiscovering).thenReturn(MutableStateFlow(false))
                    Mockito.`when`(mock.error).thenReturn(MutableStateFlow<String?>(null))
                }
                constructions += Mockito.mockConstruction(
                    LocalApiClient::class.java,
                    Mockito.withSettings().defaultAnswer { invocation ->
                        when (invocation.method.name.substringBefore('-')) {
                            "hello" -> {
                                probeCalls += 1
                                probeTimes += scheduler.currentTime
                                if (holdProbe) {
                                    val suspended: suspend () -> Any = {
                                        suspendCancellableCoroutine { continuation ->
                                            pendingProbe = continuation
                                            continuation.invokeOnCancellation { probeCancellations += 1 }
                                        }
                                    }
                                    @Suppress("UNCHECKED_CAST")
                                    suspended.startCoroutineUninterceptedOrReturn(
                                        invocation.rawArguments.last() as Continuation<Any>
                                    )
                                } else {
                                    if (!probeSucceeds) throw IOException("LAB API unavailable")
                                    helloResponse()
                                }
                            }
                            "getStatus" -> JetsonStatus()
                            "getCapabilities" -> LocalControlApi.CapabilitiesResponse()
                            else -> Answers.RETURNS_DEFAULTS.answer(invocation)
                        }
                    }
                )
                val credentials = Mockito.mock(DeviceCredentialStore::class.java,
                    Mockito.withSettings().defaultAnswer { invocation ->
                        if (invocation.method.name == "getSecret") "00".repeat(32)
                        else Answers.RETURNS_DEFAULTS.answer(invocation)
                    }
                )
                Mockito.`when`(credentials.registeredDevices).thenReturn(flowOf(listOf(
                    DeviceCredentialStore.StoredCredential(DEVICE_ID, "LAB device", "unused", "unused"),
                    DeviceCredentialStore.StoredCredential(OTHER_DEVICE_ID, "Other LAB device", "unused", "unused")
                )))
                repository = JetsonRepository(Mockito.mock(Context::class.java), credentials)
                coordinator = field("transportCoordinator")
                scheduler.runCurrent()
            } catch (error: Throwable) {
                constructions.asReversed().forEach { it.close() }
                Dispatchers.resetMain()
                throw error
            }
        }

        fun activateDirect(): FakeTransport {
            // Hardware reports the same group throughout this fixture. Initial
            // constructor cleanup calls are outside the established-session case.
            // The real coordinator is the seam for an already verified transport;
            // these tests do not bypass or exercise LocalApiClient authentication.
            repository.prepareManualWifiDirect(DEVICE_ID)
            scheduler.runCurrent()
            val transport = FakeTransport()
            coordinator.setActiveTransport(transport, deviceId = DEVICE_ID)
            scheduler.runCurrent()
            Mockito.clearInvocations(directManager, relayManager)
            return transport
        }

        fun cleanupCalls(): Int = Mockito.mockingDetails(directManager).invocations.count {
            it.method.name == "cancelConnect" || it.method.name == "disconnect"
        }

        fun rtkStopCalls(): Int = Mockito.mockingDetails(relayManager).invocations.count {
            it.method.name == "stop"
        }

        fun directConnectCalls(): Int = Mockito.mockingDetails(directManager).invocations.count {
            it.method.name == "connect"
        }

        fun helloResponse() = LocalControlApi.HelloResponse(
            1, helloDeviceId, "LAB device", "unused", 0, "JETSONHTTP2", "unused", "unused"
        )

        fun probeJobActive(): Boolean = field<kotlinx.coroutines.Job?>("wifiDirectApiProbeJob")?.isActive == true

        fun manualDirectRequested(): Boolean = field<AtomicBoolean>("explicitWifiDirectRequested").get()
        fun failureCount(): Int = field<AtomicInteger>("consecutiveIpStatusFailures").get()

        private fun <T> construction(type: Class<T>, initialize: (T) -> Unit) {
            constructions += Mockito.mockConstruction(type) { mock, _ -> initialize(mock) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> field(name: String): T = JetsonRepository::class.java.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(repository) as T
        }

        override fun close() {
            field<CoroutineScope>("scope").cancel()
            scheduler.runCurrent()
            constructions.asReversed().forEach { it.close() }
            Dispatchers.resetMain()
        }
    }

    private companion object {
        const val DEVICE_ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER_DEVICE_ID = "00000000-0000-4000-8000-000000000002"
    }
}
