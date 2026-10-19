package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MetricValidity
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
    fun `legacy zero temperature and missing totals stay unknown`() {
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
        assertEquals("미확인", stored.cpuGpu)
    }

    @Test
    fun `invalid live readings do not show numbers or fall back to cached values`() {
        listOf("unavailable", "stale").forEach { validity ->
            val status = measuredStatus().copy(metricValidity = metricNames.associateWith { MetricValidity(validity) })
            val figures = deviceFigures(status, snapshotOf(measuredStatus()))
            assertEquals("미확인", figures.temperature)
            assertEquals("미확인", figures.storageFree)
            assertEquals("미확인", figures.cpuGpu)
            assertEquals("미확인", figures.memory)
            assertNull(figures.storageFreeDetail)
            assertNull(figures.memoryDetail)
            assertNull(figures.sourceNote)

            val saved = snapshotOf(status)
            assertNull(saved.temperatureC)
            assertNull(saved.storagePercent)
            assertNull(saved.storageAvailableBytes)
            assertNull(saved.storageTotalBytes)
            assertNull(saved.cpuPercent)
            assertNull(saved.gpuPercent)
            assertNull(saved.memoryUsedMb)
            assertNull(saved.memoryTotalMb)
            assertEquals("마지막 값 기준", deviceFigures(null, saved).sourceNote)
        }
    }

    @Test
    fun `valid zero readings survive live presentation and snapshot persistence`() {
        val status = measuredStatus().copy(
            temperatureC = 0f,
            cpuPercent = 0,
            gpuPercent = 0,
            ramUsedMb = 0,
            storagePercent = 100,
            storageAvailableBytes = 0,
            metricValidity = metricNames.associateWith { MetricValidity("valid") }
        )
        val saved = snapshotOf(status)
        assertEquals(0f, saved.temperatureC)
        assertEquals(0, saved.cpuPercent)
        assertEquals(0, saved.gpuPercent)
        assertEquals(0, saved.memoryUsedMb)
        assertEquals(0L, saved.storageAvailableBytes)
        listOf(deviceFigures(status, null), deviceFigures(null, saved)).forEach { figures ->
            assertEquals("0°C", figures.temperature)
            assertEquals("0% · 0%", figures.cpuGpu)
            assertEquals("0%", figures.storageFree)
            assertEquals("0.0 GB", figures.memory)
        }
        val emptyStorage = status.copy(storagePercent = 0)
        assertEquals(0, snapshotOf(emptyStorage).storagePercent)
        assertEquals("100%", deviceFigures(emptyStorage, null).storageFree)
    }

    @Test
    fun `each metric uses its own validity including dependent totals`() {
        metricNames.forEach { name ->
            val status = measuredStatus().copy(metricValidity = mapOf(name to MetricValidity("unavailable")))
            val saved = snapshotOf(status)
            val live = deviceFigures(status, null)
            val offline = deviceFigures(null, saved)
            assertEquals(live.copy(sourceNote = "마지막 값 기준"), offline)
            when (name) {
                "temperatureC" -> assertEquals("미확인", live.temperature)
                "cpuPercent" -> assertEquals("미확인 · 41%", live.cpuGpu)
                "gpuPercent" -> assertEquals("23% · 미확인", live.cpuGpu)
                "storagePercent" -> assertEquals("미확인", live.storageFree)
                "storageAvailableBytes" -> assertNull(live.storageFreeDetail)
                "storageTotalBytes" -> {
                    assertEquals("미확인", live.storageFree)
                    assertNull(live.storageFreeDetail)
                }
                "ramUsedMb" -> assertEquals("미확인", live.memory)
                "ramTotalMb" -> {
                    assertEquals("미확인", live.memory)
                    assertNull(live.memoryDetail)
                }
            }
        }
    }

    private val metricNames = listOf(
        "temperatureC", "cpuPercent", "gpuPercent", "storagePercent", "storageAvailableBytes",
        "storageTotalBytes", "ramUsedMb", "ramTotalMb"
    )

    private fun measuredStatus() = JetsonStatus(
        temperatureC = 62.4f,
        storagePercent = 38,
        storageTotalBytes = 1_000_000_000_000L,
        storageAvailableBytes = 412_000_000_000L,
        cpuPercent = 23,
        gpuPercent = 41,
        ramUsedMb = 3174,
        ramTotalMb = 7577
    )

    private fun snapshotOf(status: JetsonStatus) = snapshotFromStatus(
        deviceId = "dev-a",
        deviceName = "MMS-4DE0",
        status = status,
        observedAtEpochMillis = 5L,
        pendingTransferCount = null,
        runningLabel = null
    )

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
