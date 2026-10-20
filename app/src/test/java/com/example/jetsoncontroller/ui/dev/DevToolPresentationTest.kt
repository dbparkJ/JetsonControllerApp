package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.model.DeviceNetworkAddress
import com.example.jetsoncontroller.model.SensorTestResult
import com.example.jetsoncontroller.ui.model.ConnectionState
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DevToolPresentationTest {
    @Test fun basicConnectionDoesNotGetAConnectedSuccessBadge() {
        assertEquals(ConnectionState.CONNECTED, devConnectionState(true, true))
        assertEquals(ConnectionState.DEFAULT_CONNECTION, devConnectionState(true, false))
        assertEquals(Tone.WARNING, devConnectionState(true, false).tone)
        assertEquals(ConnectionState.OFFLINE, devConnectionState(false, false))
    }

    @Test fun addressesKeepTheirKnownNetworkNames() {
        assertEquals("Wi-Fi", networkAddressLabel(DeviceNetworkAddress(kind = "wlan")))
        assertEquals("유선", networkAddressLabel(DeviceNetworkAddress(kind = "wired")))
        assertEquals("직접 연결", networkAddressLabel(DeviceNetworkAddress(kind = "p2p")))
        assertEquals("Tailscale", networkAddressLabel(DeviceNetworkAddress(kind = "tailscale")))
        assertEquals("usb0", networkAddressLabel(DeviceNetworkAddress(kind = "other", interfaceName = "usb0")))
    }

    @Test fun sensorBatchWaitsForANewResultAndForTheRunningTestToFinish() {
        val previous = SensorTestResult(sensor = "camera", state = "PASSED", checkedAtEpochMillis = 1000)
        val batch = SensorTestBatch("camera", previous)
        assertFalse(batch.finished(null, previous))
        assertFalse(batch.finished(null, null))
        assertFalse(batch.finished("camera", previous.copy(checkedAtEpochMillis = 2000)))
        assertTrue(batch.finished(null, previous.copy(checkedAtEpochMillis = 2000)))
        assertTrue(SensorTestBatch("gps", null).finished(null, SensorTestResult(sensor = "gps", state = "NO_DATA")))
    }

    @Test fun missingProfileWinsOverStaleTrashOrOtherStates() {
        assertEquals(ServerTrashContent.NO_PROFILE, serverTrashContent(false, false, false, false))
        assertEquals(ServerTrashContent.NO_PROFILE, serverTrashContent(false, true, true, true))
        assertEquals(ServerTrashContent.LOADING, serverTrashContent(true, true, false, false))
        assertEquals(ServerTrashContent.ERROR, serverTrashContent(true, false, true, false))
        assertEquals(ServerTrashContent.EMPTY, serverTrashContent(true, false, false, false))
        assertEquals(ServerTrashContent.CONTENT, serverTrashContent(true, false, true, true))
    }

    @Test fun oneSensorBatchRunsCameraThenGpsAndStopsOnDisconnectOrError() {
        val camera = SensorTestResult(sensor = "camera", state = "PASSED", checkedAtEpochMillis = 2000)
        val gps = SensorTestResult(sensor = "gps", state = "NO_DATA", checkedAtEpochMillis = 3000)
        val batch = SensorTestBatch("camera", null)
        assertEquals(batch, batch.advance(true, "camera", emptyMap(), null))
        val next = batch.advance(true, null, mapOf("camera" to camera), null)
        assertEquals(SensorTestBatch("gps", null), next)
        assertNull(next!!.advance(true, null, mapOf("camera" to camera, "gps" to gps), null))
        assertNull(batch.advance(false, null, mapOf("camera" to camera), null))
        assertNull(batch.advance(true, null, emptyMap(), "failed"))
    }

    @Test fun generatedRunIdsUseAReadableReceiptTitleButNamedFoldersRemainIntact() {
        val now = Instant.parse("2026-10-20T03:00:00Z")
        assertEquals("수신 결과 · 11:15", proxySessionTitle("run-20261020T021506.000Z-12345", "2026-10-20T02:15:06Z", now))
        assertEquals("20260915_data", proxySessionTitle("20260915_data", null, now))
    }
}
