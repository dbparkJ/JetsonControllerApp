package com.example.jetsoncontroller.ui.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DiagnosticsPresentationTest {
    private val now = Instant.parse("2026-10-20T03:00:00Z")

    @Test fun usesTheSharedKoreanTimeFormatWithoutRawDatesOrSeconds() {
        assertEquals("11:15", diagnosticTime(Instant.parse("2026-10-20T02:15:06Z").toEpochMilli(), now))
        assertEquals("어제 11:15", diagnosticTime(Instant.parse("2026-10-19T02:15:06Z").toEpochMilli(), now))
        assertEquals("아직 없음", diagnosticTime(null, now))
    }

    @Test fun usesSiSizesWhileKeepingTheRealBinaryStorageLimit() {
        assertEquals("1.0\u00a0KB / 최대 7.3\u00a0MB", diagnosticSize(1024L, 7L * 1024 * 1024))
        assertEquals("0\u00a0B / 최대 7.3\u00a0MB", diagnosticSize(0L, 7L * 1024 * 1024))
    }
}
