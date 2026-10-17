package com.example.jetsoncontroller.data.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSnapshotStoreTest {
    @Test
    fun `a newer reading replaces the stored one and a late older reading never overwrites it`() {
        val first = mergeDeviceSnapshot(emptyMap(), snapshot("Dev-A", 100L, temperature = 60f))
        val newer = mergeDeviceSnapshot(first, snapshot("dev-a", 200L, temperature = 70f))
        assertEquals(70f, newer.getValue("dev-a").temperatureC)

        val late = mergeDeviceSnapshot(newer, snapshot("DEV-A", 150L, temperature = 40f))
        assertEquals(70f, late.getValue("dev-a").temperatureC)
        assertEquals(200L, late.getValue("dev-a").lastSeenAtEpochMillis)
    }

    @Test
    fun `device ids are matched without regard to case or surrounding spaces`() {
        val merged = mergeDeviceSnapshot(emptyMap(), snapshot("  Dev-B ", 1L))
        assertEquals(setOf("dev-b"), merged.keys)
        assertEquals("dev-b", deviceSnapshotKey("  DEV-B\t"))
    }

    @Test
    fun `encoding and decoding keeps every field`() {
        val original = mapOf(
            "dev-a" to DeviceSnapshot(
                deviceId = "dev-a",
                deviceName = "MMS-4DE0",
                lastSeenAtEpochMillis = 1_234L,
                temperatureC = 62.5f,
                storagePercent = 38,
                storageAvailableBytes = 412_000_000_000L,
                storageTotalBytes = 1_000_000_000_000L,
                cpuPercent = 23,
                gpuPercent = 41,
                memoryUsedMb = 3174,
                memoryTotalMb = 7577,
                runningLabel = "수집 진행 중",
                pendingTransferCount = 2
            )
        )
        assertEquals(original, decodeDeviceSnapshots(encodeDeviceSnapshots(original)))
    }

    @Test
    fun `unreadable or blank stored data gives no devices instead of failing`() {
        assertTrue(decodeDeviceSnapshots(null).isEmpty())
        assertTrue(decodeDeviceSnapshots("   ").isEmpty())
        assertTrue(decodeDeviceSnapshots("{not json").isEmpty())
    }

    @Test
    fun `a snapshot without a device id is dropped when the data is read back`() {
        val raw = encodeDeviceSnapshots(mapOf("blank" to snapshot("   ", 1L)))
        assertTrue(decodeDeviceSnapshots(raw).isEmpty())
    }

    private fun snapshot(id: String, seenAt: Long, temperature: Float? = null) = DeviceSnapshot(
        deviceId = id,
        deviceName = "MMS",
        lastSeenAtEpochMillis = seenAt,
        temperatureC = temperature
    )
}
