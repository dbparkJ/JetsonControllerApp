package com.example.jetsoncontroller.data.repository

import android.content.Context
import com.example.jetsoncontroller.data.diagnostics.ConnectionDiagnostics
import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.data.bluetooth.BleScanState
import com.example.jetsoncontroller.data.bluetooth.BleScanner
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.data.network.LanDiscoveryManager
import com.example.jetsoncontroller.data.network.LocalApiClient
import com.example.jetsoncontroller.data.network.LocalControlApi
import com.example.jetsoncontroller.data.network.JetsonCommandResultUnknownException
import com.example.jetsoncontroller.data.network.JetsonResponseSignatureException
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
import com.example.jetsoncontroller.model.EndpointTransport
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MobileRtkRelayState
import com.example.jetsoncontroller.model.MobileRtkRelayConfig
import com.example.jetsoncontroller.model.ControlCapabilities
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.ContextualStartRequest
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.canonicalBleNameForDeviceId
import com.example.jetsoncontroller.protocol.JetsonCommand
import java.io.IOException
import java.net.ServerSocket
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
    fun `manual P2P waits for selected BLE clock preparation and cancellation blocks late Ready`() = runTest {
        Harness(testScheduler).use { h ->
            h.enableBootstrap()
            val peer = WifiDirectPeer("LAB device", "00:11:22:33:44:55", 3)
            h.repository.prepareManualWifiDirect(DEVICE_ID)
            h.repository.connectWifiDirect(peer)
            testScheduler.runCurrent()
            assertEquals(0, h.directConnectCalls())
            h.bleState.value = ConnectionState.Ready("LAB device")
            testScheduler.runCurrent()
            assertEquals(1, h.directConnectCalls())
        }
        Harness(testScheduler).use { h ->
            h.enableBootstrap()
            h.repository.prepareManualWifiDirect(DEVICE_ID)
            h.repository.connectWifiDirect(WifiDirectPeer("LAB device", "00:11:22:33:44:55", 3))
            testScheduler.runCurrent()
            h.repository.cancelWifiDirectConnection()
            h.bleState.value = ConnectionState.Ready("LAB device")
            testScheduler.runCurrent()
            assertEquals(0, h.directConnectCalls())
        }
    }

    @Test
    fun `automatic P2P also waits for BLE clock preparation`() = runTest {
        Harness(testScheduler).use { h ->
            h.enableBootstrap()
            h.repository.connectRegisteredAutomatically(DEVICE_ID)
            h.repository.startWifiDirectDiscovery()
            h.directState.value = WifiDirectState(peers = listOf(
                WifiDirectPeer(canonicalBleNameForDeviceId(DEVICE_ID), "00:11:22:33:44:55", 3)
            ))
            testScheduler.runCurrent()
            assertEquals(0, h.directConnectCalls())
            h.bleState.value = ConnectionState.Ready("LAB device")
            testScheduler.runCurrent()
            assertEquals(1, h.directConnectCalls())
        }
    }

    @Test
    fun `manual Direct reuses idle group after established LAN and prepares RTK on start`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateLan()
            h.probeSucceeds = true
            h.mobileRtkRelay = true
            h.pipelineResponse = pipeline(PipelineState.RUNNING)
            h.repository.prepareManualWifiDirect(DEVICE_ID.uppercase())
            testScheduler.runCurrent()

            val connected = h.repository.transportState.value as TransportState.Connected
            assertEquals(TransportType.WIFI_DIRECT, connected.type)
            assertEquals(DEVICE_ID, connected.deviceId)
            assertEquals(1, h.probeCalls)
            assertEquals(0, h.directConnectCalls())
            assertEquals(0, h.cleanupCalls())
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isSuccess)
            assertEquals(listOf("prepare", "contextualStart"), h.pipelineEvents)
            assertTrue(h.relayState.value.active)
            // Publishing Direct success must not cause another probe.
            testScheduler.runCurrent()
            assertEquals(1, h.probeCalls)
        }
    }

    @Test
    fun `manual Direct without registered target leaves LAN intact`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateLan()
            h.repository.prepareManualWifiDirect("unregistered")
            testScheduler.runCurrent()
            assertEquals(TransportType.LAN, (h.repository.transportState.value as TransportState.Connected).type)
            assertEquals(0, h.probeCalls)
            assertFalse(h.manualDirectRequested())
        }
    }

    @Test
    fun `manual Direct with missing credential cannot probe or prepare RTK`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateLan()
            h.secretAvailable = false
            h.repository.prepareManualWifiDirect(DEVICE_ID)
            testScheduler.runCurrent()
            assertEquals(0, h.probeCalls)
            assertTrue(h.repository.transportState.value !is TransportState.Connected)
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isFailure)
            assertTrue(h.pipelineEvents.isEmpty())
        }
    }

    @Test
    fun `manual Direct rejects another devices API on the existing group`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateLan()
            h.probeSucceeds = true
            h.helloDeviceId = OTHER_DEVICE_ID
            h.repository.prepareManualWifiDirect(DEVICE_ID)
            testScheduler.advanceTimeBy(2_251)
            testScheduler.runCurrent()
            assertEquals(3, h.probeCalls)
            assertTrue(h.repository.transportState.value !is TransportState.Connected)
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isFailure)
            assertTrue(h.pipelineEvents.isEmpty())
        }
    }

    @Test
    fun `LAN authentication cannot publish owner learned from current P2P group`() = runTest {
        Mockito.mockStatic(android.util.Log::class.java).use {
            Harness(testScheduler).use { h ->
                h.directState.value = WifiDirectState()
                testScheduler.runCurrent()
                val cleanupBefore = h.cleanupCalls()
                h.probeSucceeds = true
                h.holdProbe = true
                h.repository.connectLan(DeviceEndpoint(DEVICE_ID, "LAB", "192.0.2.1", 8000, EndpointTransport.LAN))
                testScheduler.runCurrent()
                val pending = requireNotNull(h.pendingProbe)
                h.directState.value = WifiDirectState(connected = true, groupOwnerAddress = "192.0.2.1")
                testScheduler.runCurrent()
                assertEquals(1, h.probeCalls)
                h.holdProbe = false
                // Suspended Kotlin Result returns are boxed at the continuation boundary.
                pending.resume(Result.success(h.helloResponse()))
                testScheduler.runCurrent()
                assertEquals(TransportType.WIFI_DIRECT, (h.repository.transportState.value as TransportState.Connected).type)
                assertEquals(2, h.probeCalls)
                assertEquals(cleanupBefore, h.cleanupCalls())
            }
        }
    }

    @Test
    fun `diagnostics separate current API threshold from physical group loss`() = runTest {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        ConnectionDiagnostics.setSinkForTests { event, fields, _ -> events += event to fields }
        try {
            Harness(testScheduler).use { h ->
                h.activateDirect()
                repeat(IP_STATUS_FAILURE_LIMIT) { assertFalse(h.repository.refreshStatus()) }
                val failures = events.filter { it.first == "status_refresh" && it.second["outcome"] == "APPLIED" }
                assertEquals(listOf(1, 2, 3), failures.map { it.second["failureCount"] })
                assertEquals(1, failures.map { it.second["sessionId"] }.toSet().size)
                assertEquals(3, failures.map { it.second["requestSequence"] }.toSet().size)
                assertTrue(events.any { it.second["reasonCode"] == "API_THRESHOLD" && it.second["groupPresent"] == true })
                assertTrue(events.none { it.second["reasonCode"] == "LINK_LOST" })
                assertEquals(0, h.cleanupCalls())
            }
        } finally { ConnectionDiagnostics.setSinkForTests(null) }
    }

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
    fun `R12 automatic API recovery verifies reads without synchronizing device time`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.probeSucceeds = true
            h.mobileTimeSync = true
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.runCurrent()
            assertTrue(h.repository.transportState.value is TransportState.Connected)
            assertEquals("Automatic control recovery must only verify reads", 0, h.timeSyncCalls)
        }
    }

    @Test
    fun `R12 initial group verification retains the existing time synchronization capability`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.directState.value = WifiDirectState(connected = false)
            testScheduler.runCurrent()
            h.probeSucceeds = true
            h.mobileTimeSync = true
            h.directState.value = WifiDirectState(connected = true, groupOwnerAddress = "192.0.2.1")
            testScheduler.runCurrent()
            assertTrue(h.repository.transportState.value is TransportState.Connected)
            assertEquals(1, h.timeSyncCalls)
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
            h.repository.configureAutomaticConnectivity(true, false, true, true)
            testScheduler.runCurrent()
            h.directState.value = WifiDirectState(peers = listOf(
                WifiDirectPeer(canonicalBleNameForDeviceId(DEVICE_ID), "02:00:00:00:00:01", 3)
            ))
            testScheduler.runCurrent()
            assertTrue(h.manualDirectRequested())
            testScheduler.advanceTimeBy(750)
            testScheduler.runCurrent()
            assertEquals(0, h.directConnectCalls())
            h.bleState.value = ConnectionState.Ready("LAB device")
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

    @Test
    fun `R7 closing Direct discovery after link loss preserves manual transport intent`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.directState.value = WifiDirectState(connected = false)
            testScheduler.runCurrent()
            assertTrue(h.manualDirectRequested())
            // JetsonApp's DisposableEffect and WifiDirectViewModel.onCleared
            // invoke this same public method when the screen goes away.
            h.repository.stopWifiDirectDiscovery()
            assertTrue("Stopping discovery is not a user transport cancellation", h.manualDirectRequested())
        }
    }

    @Test
    fun `R6 repeated recovery requests cancel the old IO and merge queued work`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.holdProbe = true
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.runCurrent()
            h.repository.retryWifiDirectApi()
            h.repository.retryWifiDirectApi()
            testScheduler.runCurrent()
            assertEquals(1, h.probeCancellations)
            assertEquals(2, h.probeCalls)
            assertTrue(h.probeJobActive())
            assertEquals(0, h.directConnectCalls())
            assertEquals(0, h.cleanupCalls())
        }
    }

    @Test
    fun `R3 explicit disconnect cancels in-flight recovery and rejects late success`() = runTest {
        Harness(testScheduler).use { h ->
            h.activateDirect()
            h.holdProbe = true
            repeat(IP_STATUS_FAILURE_LIMIT) { h.repository.refreshStatus() }
            testScheduler.runCurrent()
            val pending = requireNotNull(h.pendingProbe)
            h.repository.disconnect()
            testScheduler.runCurrent()
            pending.resume(h.helloResponse())
            testScheduler.advanceTimeBy(600_000)
            testScheduler.runCurrent()
            assertEquals(1, h.probeCalls)
            assertEquals(1, h.probeCancellations)
            assertEquals(TransportState.Disconnected, h.repository.transportState.value)
            assertFalse(h.probeJobActive())
            assertEquals(0, h.directConnectCalls())
        }
    }

    @Test
    fun `R12 unknown start with observed running pipeline preserves its prepared relay`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            val unknown = JetsonCommandResultUnknownException(
                "pipeline start", Result.success(pipeline(PipelineState.RUNNING)), IOException("response lost")
            )
            h.pipelineError = unknown
            val result = h.repository.controlPipeline("lab-pipeline", "start")
            assertTrue("Observed running does not prove which request executed", result.exceptionOrNull() === unknown)
            assertEquals("An observed running pipeline still needs its prepared relay", 0, h.rtkStopCalls())
        }
    }

    @Test
    fun `R12 unknown start preserves relay until the same pipeline is verified terminal`() = runTest {
        for (observation in listOf(
            Result.success(pipeline(PipelineState.RUNNING).copy(id = "another-pipeline")),
            Result.failure<ManagedPipeline>(IOException("state unavailable"))
        )) {
            Harness(testScheduler).use { h ->
                h.activatePipelineControl()
                val unknown = JetsonCommandResultUnknownException("pipeline start", observation, IOException("response lost"))
                h.pipelineError = unknown
                val result = h.repository.controlPipeline("lab-pipeline", "start")
                assertTrue(result.exceptionOrNull() === unknown)
                assertEquals("An inconclusive read cannot prove acquisition ended", 0, h.rtkStopCalls())
            }
        }
    }

    @Test
    fun `R12 unknown start stops its prepared relay after verified terminal state`() = runTest {
        for (state in listOf(PipelineState.STOPPED, PipelineState.FAILED)) {
            Harness(testScheduler).use { h ->
                h.activatePipelineControl()
                val unknown = JetsonCommandResultUnknownException(
                    "pipeline start",
                    Result.success(pipeline(state)),
                    IOException("response lost")
                )
                h.pipelineError = unknown

                val result = h.repository.controlPipeline("lab-pipeline", "start")

                assertTrue(result.exceptionOrNull() === unknown)
                assertEquals(1, h.rtkStopCalls())
            }
        }
    }

    @Test
    fun `R4 explicit target switch stops relay before retiring old client`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()

            h.repository.connectRegisteredAutomatically(OTHER_DEVICE_ID)
            testScheduler.runCurrent()

            assertEquals(OTHER_DEVICE_ID, h.repository.selectedDeviceId.value)
            assertEquals(1, h.rtkStopCalls())
            assertEquals(TransportState.Disconnected, h.repository.transportState.value)
        }
    }

    @Test
    fun `R12 definite pipeline failure keeps the existing relay cleanup`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineError = IllegalStateException("pipeline failed")
            assertTrue(h.repository.controlPipeline("lab-pipeline", "start").isFailure)
            assertEquals(1, h.rtkStopCalls())
        }
    }

    @Test
    fun `contextual start prepares relay before dispatch and keeps it for running acquisition`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineResponse = pipeline(PipelineState.RUNNING)
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isSuccess)
            assertEquals(listOf("prepare", "contextualStart"), h.pipelineEvents)
            assertEquals(0, h.rtkStopCalls())
        }
    }

    @Test
    fun `contextual start cannot dispatch when cellular relay preparation fails`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.prepareError = IOException("cellular unavailable")
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isFailure)
            assertEquals(listOf("prepare"), h.pipelineEvents)
        }
    }

    @Test
    fun `contextual start without configured NTRIP still dispatches`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.relayAvailable = false
            h.pipelineResponse = pipeline(PipelineState.RUNNING)
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isSuccess)
            assertEquals(listOf("prepare", "contextualStart"), h.pipelineEvents)
            assertEquals(0, h.rtkStopCalls())
        }
    }

    @Test
    fun `contextual start failure releases its prepared relay`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isFailure)
            assertEquals(1, h.rtkStopCalls())
        }
    }

    @Test
    fun `definite start failure cannot release a reused running relay`() = runTest {
        for (action in listOf("contextual", "start", "restart")) {
            Harness(testScheduler).use { h ->
                h.activatePipelineControl()
                h.relayNewlyCreated = false
                h.relayState.value = MobileRtkRelayState(active = true, pipelineId = "lab-pipeline")
                h.pipelineError = IllegalStateException("409 already running")

                val result = if (action == "contextual") {
                    h.repository.contextualStart("lab-pipeline", startRequest())
                } else {
                    h.repository.controlPipeline("lab-pipeline", action)
                }

                assertTrue(result.isFailure)
                assertEquals(0, h.rtkStopCalls())
                assertTrue(h.relayState.value.active)
            }
        }
    }

    @Test
    fun `failed relay refresh preserves active acquisition and skips contextual dispatch`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.relayNewlyCreated = false
            h.relayState.value = MobileRtkRelayState(active = true, pipelineId = "lab-pipeline")
            h.prepareError = IOException("lease refresh failed")

            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isFailure)

            assertEquals(listOf("prepare"), h.pipelineEvents)
            assertEquals(0, h.rtkStopCalls())
            assertTrue(h.relayState.value.active)
        }
    }

    @Test
    fun `reused relay unknown result is retained until matching terminal observation`() = runTest {
        for (state in listOf(PipelineState.RUNNING, PipelineState.UNKNOWN, PipelineState.STOPPED, PipelineState.FAILED)) {
            Harness(testScheduler).use { h ->
                h.activatePipelineControl()
                h.relayNewlyCreated = false
                val unknown = JetsonCommandResultUnknownException(
                    "contextual start", Result.success(listOf(pipeline(state))), IOException("response lost")
                )
                h.pipelineError = unknown

                assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).exceptionOrNull() === unknown)

                val terminal = state in setOf(PipelineState.STOPPED, PipelineState.FAILED)
                assertEquals(if (terminal) 1 else 0, h.rtkStopCalls())
                assertEquals(!terminal, h.relayState.value.active)
            }
        }
    }

    @Test
    fun `reused relay terminal receipt still releases acquisition resources`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.relayNewlyCreated = false
            h.pipelineResponse = pipeline(PipelineState.STOPPED)

            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isSuccess)

            assertEquals(1, h.rtkStopCalls())
            assertFalse(h.relayState.value.active)
        }
    }

    @Test
    fun `new relay definite failure cleanup uses the exact preparation token`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineError = IllegalStateException("409 rejected")

            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isFailure)

            assertEquals(1, h.rtkStopCalls())
            assertFalse(h.relayState.value.active)
            assertTrue(h.cleanedPreparation === h.lastPreparation)
        }
    }

    @Test
    fun `contextual start receipt already terminal releases relay`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineResponse = pipeline(PipelineState.FAILED)
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isSuccess)
            assertEquals(1, h.rtkStopCalls())
        }
    }

    @Test
    fun `contextual start on transport without relay capability skips cellular preparation`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl(relayCapability = false)
            h.pipelineResponse = pipeline(PipelineState.RUNNING)
            assertTrue(h.repository.contextualStart("lab-pipeline", startRequest()).isSuccess)
            assertEquals(listOf("contextualStart"), h.pipelineEvents)
        }
    }

    @Test
    fun `contextual start unknown list read only releases relay for same terminal pipeline`() = runTest {
        val observations = listOf(
            listOf(pipeline(PipelineState.RUNNING)) to false,
            listOf(pipeline(PipelineState.STOPPED)) to true,
            listOf(pipeline(PipelineState.FAILED)) to true,
            listOf(pipeline(PipelineState.UNKNOWN)) to false,
            listOf(pipeline(PipelineState.STOPPED).copy(id = "other")) to false,
            null to false,
            emptyList<ManagedPipeline>() to false
        )
        for ((observation, shouldStop) in observations) {
            Harness(testScheduler).use { h ->
                h.activatePipelineControl()
                val unknown = JetsonCommandResultUnknownException(
                    "contextual start",
                    observation?.let { Result.success(it) } ?: Result.failure<Any>(IOException("query unavailable")),
                    IOException("response lost")
                )
                h.pipelineError = unknown
                val result = h.repository.contextualStart("lab-pipeline", startRequest())
                assertTrue(result.exceptionOrNull() === unknown)
                assertEquals(if (shouldStop) 1 else 0, h.rtkStopCalls())
            }
        }
    }

    @Test
    fun `contextual stop mismatch preserves relay and accepted stop releases it`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.relayState.value = MobileRtkRelayState(active = true, pipelineId = "lab-pipeline")
            h.pipelineError = IllegalStateException("ACTIVE_RUN_MISMATCH")
            assertTrue(h.repository.controlPipeline("lab-pipeline", "stop", "old-run").isFailure)
            assertEquals(0, h.rtkStopCalls())
            h.pipelineResponse = pipeline(PipelineState.STOPPED)
            assertTrue(h.repository.controlPipeline("lab-pipeline", "stop", "current-run").isSuccess)
            assertEquals(1, h.rtkStopCalls())
        }
    }

    @Test
    fun `terminal polling only requests acquisition cleanup for matching terminal pipeline`() = runTest {
        for (state in listOf(PipelineState.RUNNING, PipelineState.UNKNOWN, PipelineState.STOPPED, PipelineState.FAILED)) {
            Harness(testScheduler).use { h ->
                h.activatePipelineControl()
                h.relayState.value = MobileRtkRelayState(active = true, pipelineId = "lab-pipeline")
                h.pipelineList = listOf(pipeline(PipelineState.STOPPED).copy(id = "other"), pipeline(state))
                assertTrue(h.repository.getPipelines().isSuccess)
                assertEquals(if (state in listOf(PipelineState.STOPPED, PipelineState.FAILED)) 1 else 0, h.rtkStopCalls())
            }
        }
    }

    @Test
    fun `session replacement during preparation discards start and releases only old owner`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.beforePrepareResult = { h.coordinator.disconnect() }
            try {
                h.repository.contextualStart("lab-pipeline", startRequest())
                throw AssertionError("Stale session must cancel")
            } catch (_: kotlinx.coroutines.CancellationException) {
                assertEquals(listOf("prepare"), h.pipelineEvents)
                assertEquals(1, h.rtkStopCalls())
            }
        }
    }

    @Test
    fun `session replacement after contextual dispatch discards response and cleans old owner`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineResponse = pipeline(PipelineState.RUNNING)
            h.beforeCommandResult = { h.coordinator.disconnect() }
            try {
                h.repository.contextualStart("lab-pipeline", startRequest())
                throw AssertionError("Stale session must cancel")
            } catch (_: kotlinx.coroutines.CancellationException) {
                assertEquals(listOf("prepare", "contextualStart"), h.pipelineEvents)
                assertEquals(1, h.rtkStopCalls())
            }
        }
    }

    @Test
    fun `same session cancellation after contextual dispatch preserves uncertain acquisition relay`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.beforeCommandResult = { throw kotlinx.coroutines.CancellationException("response interrupted") }
            try {
                h.repository.contextualStart("lab-pipeline", startRequest())
                throw AssertionError("Command cancellation must propagate")
            } catch (_: kotlinx.coroutines.CancellationException) {
                assertEquals(0, h.rtkStopCalls())
            }
        }
    }

    private fun startRequest() = ContextualStartRequest("project", "section", 1, 1, "policy", "preflight", "request")

    @Test
    fun `monitor prepares before acquisition and ignores duplicate connection signals`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED).copy(id = "unsupported"), pipeline(PipelineState.FAILED))
            h.enableMonitoring()
            testScheduler.runCurrent()
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
            assertTrue(h.relayState.value.active)
            h.directState.value = h.directState.value.copy(error = "temporary discovery error")
            h.enableMonitoring()
            testScheduler.advanceTimeBy(60_000)
            testScheduler.runCurrent()
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
            assertEquals(0, h.rtkStopCalls())
        }
    }

    @Test
    fun `monitor discovery retries with a bound and prepares when supported pipeline appears`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED).copy(id = "unsupported"))
            h.enableMonitoring()
            testScheduler.runCurrent()
            assertTrue(h.pipelineEvents.isEmpty())
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED))
            testScheduler.advanceTimeBy(29_999)
            testScheduler.runCurrent()
            assertTrue(h.pipelineEvents.isEmpty())
            testScheduler.advanceTimeBy(1)
            testScheduler.runCurrent()
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
        }
    }

    @Test
    fun `failed monitor preparation retries once per interval not once per pipeline`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED), pipeline(PipelineState.STOPPED).copy(id = "second"))
            h.prepareError = IOException("사용 가능한 모바일 데이터 연결이 없습니다.")
            h.enableMonitoring()
            testScheduler.runCurrent()
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
            testScheduler.advanceTimeBy(30_000)
            testScheduler.runCurrent()
            assertEquals(2, h.pipelineEvents.size)
        }
    }

    @Test
    fun `monitor requires capability permissions selected device and Direct transport`() = runTest {
        for (reason in listOf("capability", "permission", "target", "LAN", "BLE", "link")) {
            Harness(testScheduler).use { h ->
                h.activatePipelineControl(relayCapability = reason != "capability")
                h.pipelineList = listOf(pipeline(PipelineState.STOPPED))
                if (reason != "permission") h.enableMonitoring()
                when (reason) {
                    "target" -> h.selectOtherTarget()
                    "LAN" -> h.coordinator.setActiveTransport(FakeTransport(TransportType.LAN), deviceId = DEVICE_ID)
                    "BLE" -> h.coordinator.setActiveTransport(FakeTransport(TransportType.BLE), deviceId = DEVICE_ID)
                    "link" -> h.directState.value = h.directState.value.copy(connected = false)
                }
                testScheduler.runCurrent()
                assertTrue("Unexpected preparation for $reason", h.pipelineEvents.isEmpty())
            }
        }
    }

    @Test
    fun `monitor cleans owned relay on actual link loss target transport and permission changes`() = runTest {
        for (reason in listOf("permission", "target", "LAN", "BLE", "link")) {
            Harness(testScheduler).use { h ->
                h.activatePipelineControl()
                h.pipelineList = listOf(pipeline(PipelineState.STOPPED))
                h.enableMonitoring()
                testScheduler.runCurrent()
                assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
                when (reason) {
                    "permission" -> h.enableMonitoring(false)
                    "target" -> h.selectOtherTarget()
                    "LAN" -> h.coordinator.setActiveTransport(FakeTransport(TransportType.LAN), deviceId = DEVICE_ID)
                    "BLE" -> h.coordinator.setActiveTransport(FakeTransport(TransportType.BLE), deviceId = DEVICE_ID)
                    "link" -> h.directState.value = h.directState.value.copy(connected = false)
                }
                testScheduler.runCurrent()
                assertTrue("Missing cleanup for $reason", h.rtkStopCalls() > 0)
            }
        }
    }

    @Test
    fun `late monitor preparation after target change is cleaned before another attempt`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED))
            h.beforePrepareResult = { h.selectOtherTarget() }
            h.enableMonitoring()
            testScheduler.runCurrent()
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
            assertTrue(h.rtkStopCalls() > 0)
        }
    }

    @Test
    fun `explicit relay stop pauses automatic monitoring until another authenticated connection`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED))
            h.enableMonitoring()
            testScheduler.runCurrent()
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
            h.repository.stopMobileRtkRelay()
            h.relayState.value = MobileRtkRelayState()
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(60_000)
            testScheduler.runCurrent()
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
        }
    }

    @Test
    fun `authenticated monitor survives transient API outage and finite recovery until actual disconnect`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED))
            h.enableMonitoring()
            testScheduler.runCurrent()
            h.failStatus()
            repeat(IP_STATUS_FAILURE_LIMIT) { assertFalse(h.repository.refreshStatus()) }
            testScheduler.advanceTimeBy(60_000)
            testScheduler.runCurrent()
            assertTrue(h.repository.transportState.value is TransportState.Error)
            assertTrue(h.relayState.value.active)
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
            assertEquals(0, h.rtkStopCalls())
            h.repository.disconnect()
            testScheduler.runCurrent()
            assertTrue(h.rtkStopCalls() > 0)
        }
    }

    @Test
    fun `monitor is retired when authenticated status fails signature verification`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED))
            h.enableMonitoring()
            testScheduler.runCurrent()
            h.failStatus(JetsonResponseSignatureException())
            repeat(IP_STATUS_FAILURE_LIMIT) { assertFalse(h.repository.refreshStatus()) }
            testScheduler.runCurrent()
            assertTrue(h.rtkStopCalls() > 0)
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
        }
    }

    @Test
    fun `monitor retained through timeout is retired if recovery fails signature verification`() = runTest {
        Harness(testScheduler).use { h ->
            h.activatePipelineControl()
            h.pipelineList = listOf(pipeline(PipelineState.STOPPED))
            h.enableMonitoring()
            testScheduler.runCurrent()
            h.probeFailure = JetsonResponseSignatureException()
            h.failStatus()
            repeat(IP_STATUS_FAILURE_LIMIT) { assertFalse(h.repository.refreshStatus()) }
            testScheduler.runCurrent()
            assertTrue(h.rtkStopCalls() > 0)
            assertEquals(listOf("prepareForMonitoring"), h.pipelineEvents)
        }
    }

    private fun pipeline(state: PipelineState) = ManagedPipeline(
        id = "lab-pipeline", label = "LAB pipeline", state = state,
        entrypoint = "unused", config = "unused", virtualenv = "unused"
    )

    private class FakeTransport(override val type: TransportType = TransportType.WIFI_DIRECT) : ControlTransport {
        override val capabilities = TransportCapabilities(true, true, true, true, true)
        var statusRequests = 0
        var statusSucceeds = false
        var statusFailure: Throwable = IOException("Test transport: transient API timeout")
        var beforeResponse: () -> Unit = {}
        override suspend fun ping() = true
        override suspend fun getStatus(): Result<JetsonStatus> {
            statusRequests += 1
            beforeResponse()
            if (statusSucceeds) return Result.success(JetsonStatus())
            return Result.failure(statusFailure)
        }
        override suspend fun sendCommand(command: JetsonCommand, payload: ByteArray) = Result.success(Unit)
        override suspend fun disconnect() = Unit
    }

    private class Harness(private val scheduler: TestCoroutineScheduler) : AutoCloseable {
        val bleState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        private val constructions = mutableListOf<MockedConstruction<*>>()
        val directState = MutableStateFlow(
            WifiDirectState(connected = true, groupOwnerAddress = "192.0.2.1")
        )
        val wifiState = MutableStateFlow(WifiAccessPointState())
        private lateinit var directManager: WifiDirectManager
        private lateinit var relayManager: MobileRtkRelayManager
        var probeSucceeds = false
        var probeFailure: Throwable = IOException("LAB API unavailable")
        var holdProbe = false
        var helloDeviceId = DEVICE_ID
        var probeCalls = 0
        var probeCancellations = 0
        val probeTimes = mutableListOf<Long>()
        var pendingProbe: CancellableContinuation<Any>? = null
        var pipelineError: Throwable = IOException("LAB command unavailable")
        var pipelineResponse: ManagedPipeline? = null
        var pipelineList = emptyList<ManagedPipeline>()
        var prepareError: Throwable? = null
        var relayAvailable = true
        var relayNewlyCreated = true
        var lastPreparation: MobileRtkRelayManager.Preparation.Ready? = null
        var cleanedPreparation: MobileRtkRelayManager.Preparation.Ready? = null
        var beforePrepareResult: () -> Unit = {}
        var beforeCommandResult: () -> Unit = {}
        val pipelineEvents = mutableListOf<String>()
        val relayState = MutableStateFlow(MobileRtkRelayState())
        var mobileTimeSync = false
        var mobileRtkRelay = false
        var secretAvailable = true
        var timeSyncCalls = 0
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
                    Mockito.`when`(mock.connectionState).thenReturn(bleState)
                    Mockito.`when`(mock.currentDeviceId()).thenReturn(DEVICE_ID)
                    Mockito.`when`(mock.pairingState).thenReturn(MutableStateFlow<BlePairingState>(BlePairingState.Idle))
                    Mockito.`when`(mock.status).thenReturn(MutableStateFlow(JetsonStatus()))
                }
                construction(WifiDirectManager::class.java) { mock ->
                    directManager = mock
                    Mockito.`when`(mock.state).thenReturn(directState)
                }
                construction(MobileRtkRelayManager::class.java, prepareRelay = true) { mock ->
                    relayManager = mock
                    Mockito.`when`(mock.state).thenReturn(relayState)
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
                                    if (!probeSucceeds) throw probeFailure
                                    helloResponse()
                                }
                            }
                            "getStatus" -> JetsonStatus()
                            "getCapabilities" -> LocalControlApi.CapabilitiesResponse(mobileTimeSync = mobileTimeSync, mobileRtkRelay = mobileRtkRelay)
                            "synchronizeSystemTime" -> { timeSyncCalls += 1; null }
                            // Kotlin Result is an inline return type. Return its
                            // JVM failure representation at this client boundary.
                            "controlPipeline", "contextualStart" -> {
                                pipelineEvents += invocation.method.name.substringBefore('-')
                                beforeCommandResult()
                                pipelineResponse ?: resultFailure(pipelineError)
                            }
                            "getPipelines" -> pipelineList
                            "getMobileRtkRelayConfig" -> MobileRtkRelayConfig(
                                invocation.arguments[0] as String,
                                available = invocation.arguments[0] != "unsupported"
                            )
                            else -> Answers.RETURNS_DEFAULTS.answer(invocation)
                        }
                    }
                )
                val credentials = Mockito.mock(DeviceCredentialStore::class.java,
                    Mockito.withSettings().defaultAnswer { invocation ->
                        if (invocation.method.name == "getSecret") if (secretAvailable) "00".repeat(32) else null
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

        fun activateLan() {
            coordinator.setActiveTransport(FakeTransport(TransportType.LAN), deviceId = DEVICE_ID)
            scheduler.runCurrent()
            Mockito.clearInvocations(directManager, relayManager)
        }

        fun cleanupCalls(): Int = Mockito.mockingDetails(directManager).invocations.count {
            it.method.name == "cancelConnect" || it.method.name == "disconnect"
        }

        fun rtkStopCalls(): Int = Mockito.mockingDetails(relayManager).invocations.count {
            it.method.name.substringBefore('-') in setOf("stop", "stopIfOwnedBy", "stopIfPrepared", "stopAfterAcquisition")
        }

        fun directConnectCalls(): Int = Mockito.mockingDetails(directManager).invocations.count {
            it.method.name == "connect"
        }

        fun helloResponse() = LocalControlApi.HelloResponse(
            1, helloDeviceId, "LAB device", "unused", 0, "JETSONHTTP2", "unused", "unused"
        )

        fun activatePipelineControl(relayCapability: Boolean = true) {
            activateDirect()
            val client = LocalApiClient(Mockito.mock(DeviceCredentialStore::class.java))
            JetsonRepository::class.java.getDeclaredField("activeIpClient").apply {
                isAccessible = true
                set(repository, client)
            }
            field<MutableStateFlow<ControlCapabilities>>("_capabilities").value =
                ControlCapabilities(mobileRtkRelay = relayCapability)
        }

        fun enableMonitoring(granted: Boolean = true) {
            (coordinator.currentTransport() as? FakeTransport)?.statusSucceeds = true
            field<MutableStateFlow<Boolean>>("localNetworkPermissionGranted").value = granted
            field<MutableStateFlow<Boolean>>("nearbyWifiPermissionGranted").value = granted
        }

        fun enableBootstrap() {
            directState.value = WifiDirectState()
            field<MutableStateFlow<Boolean>>("bluetoothPermissionGranted").value = true
        }

        fun failStatus(error: Throwable = IOException("Test transport: transient API timeout")) {
            (coordinator.currentTransport() as FakeTransport).apply {
                statusSucceeds = false
                statusFailure = error
            }
        }

        fun selectOtherTarget() {
            field<MutableStateFlow<String?>>("preferredAutomaticDeviceId").value = OTHER_DEVICE_ID
        }

        fun probeJobActive(): Boolean = field<kotlinx.coroutines.Job?>("wifiDirectApiProbeJob")?.isActive == true

        fun manualDirectRequested(): Boolean = field<AtomicBoolean>("explicitWifiDirectRequested").get()
        fun failureCount(): Int = field<AtomicInteger>("consecutiveIpStatusFailures").get()

        private fun <T> construction(type: Class<T>, prepareRelay: Boolean = false, initialize: (T) -> Unit) {
            constructions += Mockito.mockConstruction(type,
                Mockito.withSettings().defaultAnswer { invocation ->
                    if (invocation.method.name == "reconnectEndpoint") invocation.arguments[0]
                    else if (type == WifiDirectManager::class.java && invocation.method.name == "connect") {
                        directState.value = directState.value.copy(
                            connectingPeerAddress = (invocation.arguments[0] as WifiDirectPeer).deviceAddress
                        )
                        null
                    }
                    else if (prepareRelay && invocation.method.name.substringBefore('-') in setOf("prepare", "prepareForMonitoring")) {
                        pipelineEvents += invocation.method.name.substringBefore('-')
                        beforePrepareResult()
                        prepareError?.let { resultFailure(it) } ?: if (relayAvailable) {
                            relayState.value = MobileRtkRelayState(
                                active = true, pipelineId = invocation.arguments[0] as String
                            )
                            MobileRtkRelayManager.Preparation.Ready(
                                relayNewlyCreated, Mockito.mock(ServerSocket::class.java)
                            ).also { lastPreparation = it }
                        } else MobileRtkRelayManager.Preparation.NotRequired
                    } else if (prepareRelay && invocation.method.name == "stopIfPrepared") {
                        cleanedPreparation = invocation.arguments[1] as MobileRtkRelayManager.Preparation.Ready
                        relayState.value = MobileRtkRelayState()
                        true
                    }
                    else Answers.RETURNS_DEFAULTS.answer(invocation)
                }
            ) { mock, _ -> initialize(mock) }
        }

        private fun resultFailure(error: Throwable): Any = Class.forName("kotlin.ResultKt")
            .getMethod("createFailure", Throwable::class.java).invoke(null, error)

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
