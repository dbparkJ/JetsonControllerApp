package com.example.jetsoncontroller.ui.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatsTest {
    private val nbsp = "\u00A0"

    @Test
    fun `size matches all specified decimal examples`() {
        listOf(
            0L to "0${nbsp}B",
            999L to "999${nbsp}B",
            1_000L to "1.0${nbsp}KB",
            1_234L to "1.2${nbsp}KB",
            99_949L to "99.9${nbsp}KB",
            99_950L to "100${nbsp}KB",
            896_000_000L to "896${nbsp}MB",
            999_600_000L to "1.0${nbsp}GB",
            14_700_000_000L to "14.7${nbsp}GB",
            512_110_000_000L to "512${nbsp}GB",
            1_800_000_000_000L to "1.8${nbsp}TB"
        ).forEach { (bytes, expected) -> assertEquals(expected, Formats.size(bytes)) }
    }

    @Test
    fun `size uses half up and promotes rounded unit boundaries`() {
        assertEquals("999${nbsp}KB", Formats.size(999_499L))
        assertEquals("1.0${nbsp}MB", Formats.size(999_500L))
        assertEquals("100${nbsp}KB", Formats.size(99_950L))
        assertEquals("1.0${nbsp}GB", Formats.size(999_500_000L))
    }

    @Test
    fun `free memory and speed match specified examples`() {
        assertEquals("512${nbsp}GB 남음", Formats.free(512_110_000_000L))
        assertEquals(
            "2.0${nbsp}GB / 15.1${nbsp}GB",
            Formats.memory(2_097_152L, 15_833_498L)
        )
        assertEquals("12.3${nbsp}MB/초", Formats.speed(12_300_000L))
    }

    @Test
    fun `percent and progress preserve zero floor and boundaries`() {
        assertEquals("57%", Formats.percent(57.0))
        assertEquals("0%", Formats.percent(0.0))
        assertEquals("58%", Formats.percent(57.5))
        assertEquals("0%", Formats.percent(-4.0))
        assertEquals("100%", Formats.percent(120.0))
        assertEquals("미확인", Formats.percent(Double.NaN))
        assertEquals("64%", Formats.progress(64L, 100L))
        assertEquals("33%", Formats.progress(1L, 3L))
        assertEquals("0%", Formats.progress(0L, 100L))
        assertEquals("100%", Formats.progress(64L, 64L))
        assertEquals("미확인", Formats.progress(65L, 64L))
    }

    @Test
    fun `temperature and clock match specified examples`() {
        assertEquals("54°C", Formats.temperature(54.0))
        assertEquals("00:12:34", Formats.clock(Duration.ofMinutes(12).plusSeconds(34)))
        assertEquals(
            "101:02:03",
            Formats.clock(Duration.ofHours(101).plusMinutes(2).plusSeconds(3))
        )
        assertEquals("55°C", Formats.temperature(54.5))
    }

    @Test
    fun `duration and eta match specified examples`() {
        assertEquals("12분", Formats.duration(Duration.ofMinutes(12)))
        assertEquals("1시간 5분", Formats.duration(Duration.ofHours(1).plusMinutes(5)))
        assertEquals("2시간", Formats.duration(Duration.ofHours(2)))
        assertEquals("1분 미만", Formats.duration(Duration.ofSeconds(59)))
        assertEquals("약 4분 남음", Formats.eta(Duration.ofMinutes(4)))
        assertEquals("1분 미만 남음", Formats.eta(Duration.ofSeconds(59)))
        assertNull(Formats.eta(null))
    }

    @Test
    fun `time uses Seoul date and freshness wording`() {
        val now = Instant.parse("2026-10-19T08:00:00Z")
        assertEquals("16:36", Formats.time(Instant.parse("2026-10-19T07:36:00Z"), now))
        assertEquals("어제 16:36", Formats.time(Instant.parse("2026-10-18T07:36:00Z"), now))
        assertEquals(
            "10월 19일 16:36",
            Formats.time(
                Instant.parse("2026-10-19T07:36:00Z"),
                Instant.parse("2026-10-25T08:00:00Z")
            )
        )
        assertEquals("2025년 9월 16일 16:36", Formats.time(Instant.parse("2025-09-16T07:36:00Z"), now))
        assertEquals("방금 갱신", Formats.freshness(Duration.ofSeconds(15)))
        // A device clock slightly ahead of the phone is still "now"; far in the future is unknown.
        assertEquals("방금 갱신", Formats.freshness(Duration.ofMillis(-1_300)))
        assertEquals("미확인", Formats.freshness(Duration.ofMinutes(-5)))
        assertEquals("3분 전 갱신", Formats.freshness(Duration.ofMinutes(3)))
        assertEquals(
            "16:36 갱신",
            Formats.freshness(
                Duration.ofHours(2),
                Instant.parse("2026-10-19T07:36:00Z"),
                Instant.parse("2026-10-19T09:36:00Z")
            )
        )
    }

    @Test
    fun `distance length width and area match specified examples`() {
        assertEquals("7${nbsp}m", Formats.distance(7.0))
        assertEquals("35${nbsp}m", Formats.distance(35.0))
        assertEquals("350${nbsp}m", Formats.distance(350.0))
        assertEquals("1.2${nbsp}km", Formats.distance(1_200.0))
        assertEquals("1.2${nbsp}km", Formats.length(1_200.0))
        assertEquals("폭 20${nbsp}m", Formats.width(10.0))
        assertEquals("3,400${nbsp}m²", Formats.area(3_400.0))
        assertEquals("100${nbsp}m", Formats.distance(99.6))
    }

    @Test
    fun `counts and coordinate match specified examples`() {
        assertEquals("1,204개", Formats.itemCount(1_204L))
        assertEquals("3건", Formats.caseCount(3L))
        assertEquals("2대", Formats.deviceCount(2L))
        assertEquals("2회", Formats.occurrenceCount(2L))
        assertEquals("위성 12개", Formats.satelliteCount(12L))
        assertEquals("37.5665000, 126.9780000", Formats.coordinate(37.5665, 126.978))
        assertEquals("미확인", Formats.coordinate(null, 126.978))
        assertEquals("미확인", Formats.coordinate(91.0, 0.0))
    }

    @Test
    fun `nullable formatter inputs map to unknown except omitted eta`() {
        assertEquals("미확인", Formats.size(null))
        assertEquals("미확인", Formats.free(null))
        assertEquals("미확인", Formats.memory(null, 1L))
        assertEquals("미확인", Formats.speed(null))
        assertEquals("미확인", Formats.percent(null))
        assertEquals("미확인", Formats.progress(null, 1L))
        assertEquals("미확인", Formats.temperature(null))
        assertEquals("미확인", Formats.clock(null))
        assertEquals("미확인", Formats.duration(null))
        assertEquals("미확인", Formats.time(null))
        assertEquals("미확인", Formats.freshness(null))
        assertEquals("미확인", Formats.distance(null))
        assertEquals("미확인", Formats.length(null))
        assertEquals("미확인", Formats.width(null))
        assertEquals("미확인", Formats.area(null))
        assertEquals("미확인", Formats.itemCount(null))
        assertEquals("미확인", Formats.caseCount(null))
        assertEquals("미확인", Formats.deviceCount(null))
        assertEquals("미확인", Formats.occurrenceCount(null))
        assertEquals("미확인", Formats.satelliteCount(null))
        assertNull(Formats.eta(null))
    }
}
