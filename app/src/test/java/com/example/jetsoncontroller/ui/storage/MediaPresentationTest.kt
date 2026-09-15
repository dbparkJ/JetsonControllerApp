package com.example.jetsoncontroller.ui.storage

import com.example.jetsoncontroller.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MediaPresentationTest {
    @Test fun utcMidnightUsesTheLocalCollectionDate() {
        val today = LocalDate.of(2026, 9, 12)
        val seoul = ZoneId.of("Asia/Seoul")
        assertEquals("오늘", dateGroup("2026-09-11T16:00:00Z", today, seoul))
        assertEquals("어제", dateGroup("2026-09-11T14:00:00Z", today, seoul))
        assertEquals("날짜 미확인", dateGroup("broken", today, seoul))
    }
    @Test fun collectionTimeUsesTheSameLocalZoneAsItsDateGroup() {
        val seoul = ZoneId.of("Asia/Seoul")
        assertEquals("01:39", localTimeOrDateLabel("2026-09-14T16:39:00Z", seoul))
        assertEquals("2026-09-15", localTimeOrDateLabel("2026-09-15", seoul))
        assertEquals("시간 미확인", localTimeOrDateLabel("broken", seoul))
    }
    @Test fun legacyServerTargetsRemainVisibleWhileLocalTargetsStayExcluded() {
        assertTrue(supportsServerLibrary(UploadTarget("old", "old")))
        assertTrue(supportsServerLibrary(UploadTarget("http", "server", "http")))
        assertFalse(supportsServerLibrary(UploadTarget("local", "disk", "local")))
    }
    @Test fun uppercaseImageAndSensorFormatsAreRecognized() {
        assertEquals("이미지", mediaCategory(RemoteFileEntry("ROAD.JPG", "ROAD.JPG", RemoteEntryType.FILE, 12, null)))
        assertEquals("센서", mediaCategory(RemoteFileEntry("gps.NMEA", "gps.NMEA", RemoteEntryType.FILE, 12, null)))
    }
}
