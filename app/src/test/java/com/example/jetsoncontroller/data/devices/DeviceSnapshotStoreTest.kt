package com.example.jetsoncontroller.data.devices

import com.example.jetsoncontroller.model.MetricValidity
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
                pendingTransferCount = 2,
                metricValidity = mapOf("cpuPercent" to MetricValidity("valid", 1_234L))
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

    @Test
    fun `invalid fallback values are absent when recorded serialized and restored`() {
        listOf("unavailable", "stale").forEach { validity ->
            val incoming = DeviceSnapshot(
                deviceId = "dev-a",
                deviceName = "MMS",
                lastSeenAtEpochMillis = 5L,
                temperatureC = 0f,
                cpuPercent = 0,
                gpuPercent = 34,
                storagePercent = 0,
                storageAvailableBytes = 0,
                storageTotalBytes = 100,
                memoryUsedMb = 0,
                memoryTotalMb = 100,
                metricValidity = listOf(
                    "temperatureC", "cpuPercent", "gpuPercent", "storagePercent",
                    "storageAvailableBytes", "storageTotalBytes", "ramUsedMb", "ramTotalMb"
                ).associateWith { MetricValidity(validity) }
            )
            val merged = mergeDeviceSnapshot(emptyMap(), incoming).getValue("dev-a")
            val encoded = encodeDeviceSnapshots(mapOf("dev-a" to incoming))
            val raw = JsonParser.parseString(encoded).asJsonArray[0].asJsonObject
            listOf(
                "temperatureC", "cpuPercent", "gpuPercent", "storagePercent", "storageAvailableBytes",
                "storageTotalBytes", "memoryUsedMb", "memoryTotalMb"
            ).forEach { assertFalse(raw.has(it)) }
            assertEquals(merged, decodeDeviceSnapshots(encoded).getValue("dev-a"))
            assertNull(merged.cpuPercent)
            assertNull(merged.gpuPercent)
            assertNull(merged.temperatureC)
        }
    }

    @Test
    fun `loading a snapshot drops numeric fields whose validity is known to be invalid`() {
        val raw = """[{
            "deviceId":"dev-a", "deviceName":"MMS", "lastSeenAtEpochMillis":5,
            "cpuPercent":0, "gpuPercent":0, "storagePercent":0,
            "metricValidity":{
                "cpuPercent":{"validity":"unavailable"},
                "gpuPercent":{"validity":"valid"},
                "storagePercent":{"validity":"stale"}
            }
        }]"""
        val restored = decodeDeviceSnapshots(raw).getValue("dev-a")
        assertNull(restored.cpuPercent)
        assertNull(restored.storagePercent)
        assertEquals(0, restored.gpuPercent)
    }

    @Test
    fun `a new invalid reading replaces previously valid metrics with missing values`() {
        val old = snapshot("dev-a", 1L).copy(cpuPercent = 25)
        val invalid = snapshot("dev-a", 2L).copy(
            cpuPercent = 0,
            metricValidity = mapOf("cpuPercent" to MetricValidity("unavailable"))
        )
        assertNull(mergeDeviceSnapshot(mapOf("dev-a" to old), invalid).getValue("dev-a").cpuPercent)
    }

    @Test
    fun `valid zeros survive a snapshot round trip`() {
        val zero = snapshot("dev-a", 1L, temperature = 0f).copy(
            cpuPercent = 0,
            gpuPercent = 0,
            storagePercent = 0,
            memoryUsedMb = 0,
            metricValidity = listOf("temperatureC", "cpuPercent", "gpuPercent", "storagePercent", "ramUsedMb")
                .associateWith { MetricValidity("valid") }
        )
        assertEquals(zero, decodeDeviceSnapshots(encodeDeviceSnapshots(mapOf("dev-a" to zero))).getValue("dev-a"))
    }

    @Test
    fun `legacy snapshots without validity cannot distinguish invalid zeros from real zeros`() {
        val raw = """[{
            "deviceId":"dev-a", "deviceName":"MMS", "lastSeenAtEpochMillis":5,
            "cpuPercent":0, "gpuPercent":0, "storagePercent":0
        }]"""
        val restored = decodeDeviceSnapshots(raw).getValue("dev-a")
        assertNull(restored.metricValidity)
        assertEquals(0, restored.cpuPercent)
        assertEquals(0, restored.gpuPercent)
        assertEquals(0, restored.storagePercent)
        assertNull(restored.temperatureC)
    }

    private fun snapshot(id: String, seenAt: Long, temperature: Float? = null) = DeviceSnapshot(
        deviceId = id,
        deviceName = "MMS",
        lastSeenAtEpochMillis = seenAt,
        temperatureC = temperature
    )
}
