package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.model.GnssSensorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssDiagnosticsPresentationTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `ticker stays off without elapsed time rows even when other diagnostics are visible`() {
        for (gnss in listOf(GnssSensorStatus(), GnssSensorStatus(ntripSelection = "caster",
            rtcmBaseStationId = 123, rtcmMessageTypes = listOf(1005), rtcmSignals = listOf("GPS 5Q"),
            ntripError = "GGA 위치를 확인하세요."))) {
            assertFalse(gnssDiagnosticsNeedsTicker(gnss, now))
        }
    }

    @Test
    fun `ticker runs for either displayed correction or GGA age including stale diagnostics`() {
        for (gnss in listOf(
            GnssSensorStatus(rtcmLastAtEpochMillis = now - 3_000),
            GnssSensorStatus(ntripLastGgaSentAtEpochMillis = now - 1_000),
            GnssSensorStatus(rtcmLastAtEpochMillis = now + 60_000),
            GnssSensorStatus(rtcmLastAtEpochMillis = -1, ntripLastGgaSentAtEpochMillis = now)
        )) {
            assertTrue(gnssDiagnosticsNeedsTicker(gnss, now))
            for (fresh in listOf(true, false)) {
                assertTrue(gnssDiagnosticsPresentation(gnss, now, fresh).rows.any {
                    it.label == "마지막 보정" || it.label == "마지막 GGA 송신"
                })
            }
        }
    }

    @Test
    fun `ticker stays off for invalid or hidden future timestamps`() {
        for (timestamp in listOf(null, 0L, -1L, now + 60_001, Long.MAX_VALUE)) {
            val gnss = GnssSensorStatus(rtcmLastAtEpochMillis = timestamp,
                ntripLastGgaSentAtEpochMillis = timestamp)
            assertFalse(gnssDiagnosticsNeedsTicker(gnss, now))
            assertFalse(gnssDiagnosticsNeedsTicker(gnss, 0))
            assertTrue(gnssDiagnosticsPresentation(gnss, now, true).rows.isEmpty())
        }
    }

    @Test
    fun `caster diagnostics show padded base ID distance message types and actual ages`() {
        val result = gnssDiagnosticsPresentation(GnssSensorStatus(
            ntripSelection = "caster", rtcmBaseStationId = 123, rtcmBaseDistanceM = 7400.0,
            rtcmMessageTypes = listOf(1077, 1005, 1004, 1005),
            rtcmLastAtEpochMillis = now - 3_000, ntripLastGgaSentAtEpochMillis = now - 1_000
        ), now, fresh = true)
        assertEquals(listOf(
            GnssDiagnosticRow("기준국 선택", "캐스터 자동 선택"),
            GnssDiagnosticRow("보정 기준국", "ID 0123 · 7.4\u00a0km"),
            GnssDiagnosticRow("받은 RTCM 메시지", "1004, 1005, 1077"),
            GnssDiagnosticRow("마지막 보정", "3초 전"),
            GnssDiagnosticRow("마지막 GGA 송신", "1초 전")
        ), result.rows)
        assertNull(result.error)
        assertFalse(result.lastKnown)
    }

    @Test
    fun `legacy missing fields hide rows and invalid direct model values stay hidden`() {
        for (gnss in listOf(GnssSensorStatus(), GnssSensorStatus(
            ntripSelection = "auto", rtcmBaseStationId = 4096, rtcmBaseDistanceM = Double.NaN,
            rtcmMessageTypes = listOf(0, 4096), rtcmLastAtEpochMillis = 0,
            ntripLastGgaSentAtEpochMillis = now + 60_001, ntripError = " "
        ))) {
            val result = gnssDiagnosticsPresentation(gnss, now, fresh = false)
            assertTrue(result.rows.isEmpty())
            assertNull(result.error)
            assertFalse(result.lastKnown)
        }
    }

    @Test
    fun `client selection partial base data zero values and stale errors remain visible`() {
        val gnss = GnssSensorStatus(ntripSelection = "client", rtcmBaseStationId = 0,
            rtcmLastAtEpochMillis = now - 120_000, ntripError = "GGA 위치를 확인하세요.")
        val result = gnssDiagnosticsPresentation(gnss, now, fresh = false)
        assertEquals(listOf(
            GnssDiagnosticRow("기준국 선택", "가까운 기준국 직접 선택"),
            GnssDiagnosticRow("보정 기준국", "ID 0000"),
            GnssDiagnosticRow("마지막 보정", "2분 전")
        ), result.rows)
        assertEquals("NTRIP 오류: GGA 위치를 확인하세요.", result.error)
        assertTrue(result.lastKnown)
        val distanceOnly = gnssDiagnosticsPresentation(GnssSensorStatus(rtcmBaseDistanceM = 0.0), now, true)
        assertEquals(listOf(GnssDiagnosticRow("보정 기준국", "0\u00a0m")), distanceOnly.rows)
    }

    @Test
    fun `received signals group sorted distinct codes under all constellation display names`() {
        val signals = listOf("GPS 5Q", "GAL 7Q", "BDS 5P", "GPS 1C", "GAL 1C", "GAL 5Q",
            "BDS 2I", "GLO 2P", "QZS 5X", "SBS ?12", "IRN 5A", "GPS 5Q")
        assertEquals(listOf(
            GnssDiagnosticRow("받은 신호", "GPS 1C·5Q · GLONASS 2P · Galileo 1C·5Q·7Q · BeiDou 2I·5P · QZSS 5X · SBAS ?12 · NavIC 5A"),
            GnssDiagnosticRow("L5 대역 보정", "있음")
        ), gnssSignalRows(signals, emptyList()))
    }

    @Test
    fun `each supported constellation band 5 independently establishes L5 corrections`() {
        for (prefix in listOf("GPS", "GAL", "QZS", "BDS")) {
            for (code in listOf("5A", "5P", "5Q", "5X")) {
                assertEquals(GnssDiagnosticRow("L5 대역 보정", "있음"),
                    gnssSignalRows(listOf("$prefix $code"), emptyList()).last())
            }
        }
    }

    @Test
    fun `other bands constellations and unknown IDs do not establish L5 corrections`() {
        for (signal in listOf("GPS 1C", "GAL 7Q", "QZS 2L", "BDS 2I", "GLO 5P", "SBS 5Q", "IRN 5A",
            "GPS ?5", "GAL ?15", "BDS ?05")) {
            assertEquals(GnssDiagnosticRow("L5 대역 보정", "없음"),
                gnssSignalRows(listOf(signal), emptyList()).last())
        }
    }

    @Test
    fun `empty or invalid signals hide both signal rows without legacy observations`() {
        for (signals in listOf(emptyList(), listOf("gps 5Q", "GPS 5q", "GPS 5Q\n", "GPS ?100", "GPS  1C"))) {
            for (types in listOf(emptyList(), listOf(1005, 1006, 1230), listOf(1077, 1097, 1127))) {
                assertTrue(gnssSignalRows(signals, types).isEmpty())
            }
        }
    }

    @Test
    fun `legacy observations show sorted message IDs without an L5 verdict`() {
        for ((types, expected) in listOf(
            listOf(1012, 1004, 1004) to "1004·1012",
            listOf(1005, 1004, 1006, 1230) to "1004",
            listOf(1003, 1002, 1001) to "1001·1002·1003",
            listOf(1011, 1010, 1009) to "1009·1010·1011"
        )) {
            assertEquals(listOf(GnssDiagnosticRow("받은 신호", "MSM 없음 ($expected 레거시 관측)")),
                gnssSignalRows(emptyList(), types))
        }
    }

    @Test
    fun `MSM messages suppress the legacy only explanation even when signal details are missing`() {
        for (constellation in 107..113) {
            for (subtype in 1..7) {
                assertTrue(gnssSignalRows(emptyList(), listOf(1004, 1012, constellation * 10 + subtype)).isEmpty())
            }
        }
    }

    @Test
    fun `signal rows integrate into diagnostics with precedence over legacy hints and stale marking`() {
        val result = gnssDiagnosticsPresentation(GnssSensorStatus(
            rtcmSignals = listOf("GPS 5Q", "GPS 1C"), rtcmMessageTypes = listOf(1012, 1004)
        ), now, fresh = false)
        assertEquals(listOf(
            GnssDiagnosticRow("받은 RTCM 메시지", "1004, 1012"),
            GnssDiagnosticRow("받은 신호", "GPS 1C·5Q"),
            GnssDiagnosticRow("L5 대역 보정", "있음")
        ), result.rows)
        assertTrue(result.lastKnown)
        val legacy = gnssDiagnosticsPresentation(GnssSensorStatus(rtcmMessageTypes = listOf(1004, 1012)), now, true)
        assertEquals(GnssDiagnosticRow("받은 신호", "MSM 없음 (1004·1012 레거시 관측)"), legacy.rows.last())
        assertFalse(legacy.rows.any { it.label == "L5 대역 보정" })
    }

    @Test
    fun `age handles unit boundaries clock skew and invalid timestamps`() {
        for ((elapsed, expected) in listOf(
            0L to "0초 전", 3_999L to "3초 전", 59_999L to "59초 전", 60_000L to "1분 전",
            3_599_999L to "59분 전", 3_600_000L to "1시간 전", 86_400_000L to "1일 전",
            -60_000L to "0초 전"
        )) assertEquals(expected, gnssDiagnosticAge(now - elapsed, now))
        for (timestamp in listOf(null, 0L, -1L, now + 60_001, Long.MAX_VALUE)) {
            assertNull(gnssDiagnosticAge(timestamp, now))
        }
    }
}
