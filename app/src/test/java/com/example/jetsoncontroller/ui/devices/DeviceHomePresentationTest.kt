package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.model.JetsonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHomePresentationTest {
    @Test
    fun `an offline device is reconnected first, a running collection is watched, and an idle one can start`() {
        assertEquals(DeviceNextAction.RECONNECT, deviceNextAction(hasDevice = true, online = false, runActive = true))
        assertEquals(DeviceNextAction.VIEW_RUN, deviceNextAction(hasDevice = true, online = true, runActive = true))
        assertEquals(DeviceNextAction.START_COLLECTION, deviceNextAction(hasDevice = true, online = true, runActive = false))
        assertEquals(DeviceNextAction.NONE, deviceNextAction(hasDevice = false, online = true, runActive = false))
    }

    @Test
    fun `power actions need the feature, a live link, no other operation and no running collection`() {
        assertTrue(devicePowerActionEnabled(powerCommandsEnabled = true, online = true, operationInProgress = false, runActive = false))
        assertFalse(devicePowerActionEnabled(powerCommandsEnabled = true, online = true, operationInProgress = false, runActive = true))
        assertFalse(devicePowerActionEnabled(powerCommandsEnabled = true, online = true, operationInProgress = true, runActive = false))
        assertFalse(devicePowerActionEnabled(powerCommandsEnabled = true, online = false, operationInProgress = false, runActive = false))
        assertFalse(devicePowerActionEnabled(powerCommandsEnabled = false, online = true, operationInProgress = false, runActive = false))
    }

    @Test
    fun `an unsent collection gives one attention item and nothing otherwise`() {
        assertEquals("서버로 보내지 않은 수집 1건", unsentCollectionAttention("MMS-4DE0", pendingTransfer = true)?.title)
        assertNull(unsentCollectionAttention("MMS-4DE0", pendingTransfer = false))
    }

    @Test
    fun `last response reads as minutes and hours while it is recent`() {
        val now = 10_000_000_000L
        assertEquals("방금", formatSeenAt(now - 30_000L, now))
        assertEquals("3분 전", formatSeenAt(now - 181_000L, now))
        assertEquals("2시간 전", formatSeenAt(now - 7_201_000L, now))
    }

    @Test
    fun `freshness reads seconds, then minutes, and is absent when the age is unknown`() {
        assertEquals("5초 전 갱신", formatFreshness(5L))
        assertEquals("2분 전 갱신", formatFreshness(120L))
        assertNull(formatFreshness(null))
    }

    @Test
    fun `a snapshot is recorded only from a fresh reading of an online device`() {
        assertTrue(shouldRecordSnapshot(online = true, statusIsCurrent = true, observedAt = 1L))
        assertFalse(shouldRecordSnapshot(online = false, statusIsCurrent = true, observedAt = 1L))
        assertFalse(shouldRecordSnapshot(online = true, statusIsCurrent = false, observedAt = 1L))
        assertFalse(shouldRecordSnapshot(online = true, statusIsCurrent = true, observedAt = null))
    }

    @Test
    fun `zero readings stay unknown instead of being saved as measurements`() {
        val snapshot = snapshotFromStatus(
            deviceId = "dev-a",
            deviceName = "MMS-4DE0",
            status = JetsonStatus(temperatureC = 0f, storageTotalBytes = 0L, ramTotalMb = 0, cpuPercent = 12),
            observedAtEpochMillis = 5L,
            pendingTransferCount = null,
            runningLabel = null
        )
        assertNull(snapshot.temperatureC)
        assertNull(snapshot.storageAvailableBytes)
        assertNull(snapshot.memoryTotalMb)
        assertEquals(12, snapshot.cpuPercent)
    }

    @Test
    fun `live figures win, and stored figures are labelled as the last known values`() {
        val live = deviceFigures(
            live = JetsonStatus(
                temperatureC = 62.4f,
                storagePercent = 38,
                storageTotalBytes = 1_000_000_000_000L,
                storageAvailableBytes = 412_000_000_000L,
                cpuPercent = 23,
                gpuPercent = 41,
                ramUsedMb = 3174,
                ramTotalMb = 7577
            ),
            snapshot = null
        )
        assertEquals("62°C", live.temperature)
        assertEquals("62%", live.storageFree)
        assertEquals("412 GB", live.storageFreeDetail)
        assertEquals("23% · 41%", live.cpuGpu)
        assertNull(live.sourceNote)

        val stored = deviceFigures(
            live = null,
            snapshot = DeviceSnapshot(
                deviceId = "dev-a",
                deviceName = "MMS-4DE0",
                lastSeenAtEpochMillis = 1L,
                temperatureC = 81f,
                storagePercent = 38
            )
        )
        assertEquals("81°C", stored.temperature)
        assertEquals("마지막 값 기준", stored.sourceNote)
        assertEquals("확인 안 됨", stored.cpuGpu)
    }

    @Test
    fun `the chip names the state and the one fact that matters for it`() {
        val online = deviceConnectionUiForTest(connected = true)
        assertEquals("연결됨 · 5초 전 갱신", statusChipText(online, lastSeenText = null, online = true, freshness = "5초 전 갱신"))
        val offline = deviceConnectionUiForTest(connected = false)
        assertEquals("오프라인 · 마지막 응답 10월 15일 10:39", statusChipText(offline, "10월 15일 10:39", online = false, freshness = null))
    }

    private fun deviceConnectionUiForTest(connected: Boolean) =
        com.example.jetsoncontroller.ui.connection.deviceConnectionUi(
            isOnline = connected,
            transportType = if (connected) com.example.jetsoncontroller.data.transport.TransportType.LAN else null
        )
}
