package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FileBrowserStateTest {
    @Test fun `five picker choices include logs and filtering keeps folders navigable`() {
        assertEquals(listOf("전체", "이미지", "영상", "센서", "로그"), mediaCategories)
        val folder = entry("run", RemoteEntryType.DIRECTORY)
        val log = entry("CAPTURE.LOG")
        val image = entry("road.JPG")
        val sensor = entry("gps.NMEA")
        assertEquals(listOf(log, folder), browserEntries(listOf(folder, image, log, sensor), "", "로그"))
        assertEquals(listOf(sensor, folder), browserEntries(listOf(folder, image, log, sensor), "", "센서"))
        assertTrue(browserEntries(listOf(log), "missing", "로그").isEmpty())
        assertEquals(listOf(log), browserEntries(listOf(log), "capture", "로그"))
    }

    @Test fun `entry points and devices retain independent locations`() {
        val locations = FileBrowserLocations()
        val initial = locations.select("browse", "device-a", DeviceStorageUiState())
        val browse = initial.copy(currentRoot = RemoteRoot("recordings", "수집 자료", null), currentPath = "browse/run")
        val send = locations.select("send", "device-a", browse)
        assertEquals("", send.currentPath)
        val otherDevice = locations.select("send", "device-b", send.copy(currentPath = "send/run"))
        assertEquals("", otherDevice.currentPath)
        assertEquals("browse/run", locations.select("browse", "device-a", otherDevice).currentPath)
        assertEquals("send/run", locations.select("send", "DEVICE-A", browse).currentPath)
    }

    @Test fun `location restore clears transient loading deletion and preview`() {
        val locations = FileBrowserLocations()
        val initial = locations.select("browse", "device-a", DeviceStorageUiState())
        val old = initial.copy(currentPath = "capture", isLoading = true, isDeleting = true,
            preview = RemoteFileContent("image.jpg", "image/jpeg", byteArrayOf(1)))
        val send = locations.select("send", "device-a", old)
        val restored = locations.select("browse", "device-a", send)
        assertEquals("capture", restored.currentPath)
        assertFalse(restored.isLoading)
        assertFalse(restored.isDeleting)
        assertNull(restored.preview)
    }

    @Test fun `day headings use sample dates in the local zone`() {
        val today = LocalDate.of(2026, 10, 20)
        val seoul = ZoneId.of("Asia/Seoul")
        assertEquals("오늘", browserDay("2026-10-19T16:00:00Z", today, seoul))
        assertEquals("어제", browserDay("2026-10-18T16:00:00Z", today, seoul))
        assertEquals("9월 16일", browserDay("2026-09-16T00:00:00Z", today, seoul))
        assertEquals("날짜 미확인", browserDay("invalid", today, seoul))
    }

    private fun entry(name: String, type: RemoteEntryType = RemoteEntryType.FILE) =
        RemoteFileEntry(name, name, type, 12, "2026-10-19T00:00:00Z")
}
