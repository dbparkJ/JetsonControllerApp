package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FileBrowserStateTest {
    @Test fun `file selection requires an available nonempty listing`() {
        val state = DeviceStorageUiState(controlAvailable = true,
            currentRoot = RemoteRoot("data", "수집 자료", null), entries = listOf(entry("capture.jpg")))
        assertTrue(browserCanSelectFiles(state))
        assertFalse(browserCanSelectFiles(state.copy(entries = emptyList())))
        assertFalse(browserCanSelectFiles(state.copy(currentRoot = null)))
        assertFalse(browserCanSelectFiles(state.copy(isLoading = true)))
        assertFalse(browserCanSelectFiles(state.copy(isDeleting = true)))
        assertFalse(browserCanSelectFiles(state.copy(controlAvailable = false)))
        assertFalse(browserCanSelectFiles(state.copy(error = "조회 실패")))
        assertFalse(browserCanSelectFiles(state, "전송 준비 실패"))
    }

    @Test fun `error detail preserves the cause without repeating its title`() {
        val title = "파일 목록을 불러오지 못했습니다"
        assertEquals("연결 상태를 확인한 뒤 다시 확인하세요.", storageFailureDetail("$title.", title))
        assertEquals("장치 연결이 끊겼습니다.", storageFailureDetail("$title. 장치 연결이 끊겼습니다.", title))
        assertEquals("권한이 없습니다.", storageFailureDetail("권한이 없습니다.", title))
    }

    @Test fun `file metadata contains type size and time without repeating its name`() {
        val file = entry("capture.jpg").copy(sizeBytes = 1024L)
        val meta = fileEntryMeta(file)
        assertTrue(meta.contains("이미지"))
        assertTrue(meta.contains("KB"))
        assertFalse(meta.contains(file.name))
    }

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
