package com.example.jetsoncontroller.ui.alerts

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPresentationTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 20)
    private val todayStart = alertDayBoundaryEpochMillis(today, zone)

    @Test
    fun alertsGroupAsTodayYesterdayAndCalendarDate() {
        assertEquals("오늘", alertDayLabel(todayStart + 12 * 60 * 60 * 1000, todayStart, zone))
        assertEquals("어제", alertDayLabel(alertDayBoundaryEpochMillis(today.minusDays(1), zone), todayStart, zone))
        assertEquals("10월 18일", alertDayLabel(alertDayBoundaryEpochMillis(today.minusDays(2), zone), todayStart, zone))
    }

    @Test
    fun alertTimeUsesLocalTwentyFourHourClock() {
        assertEquals("16:36", formatAlertTime(todayStart + 16 * 60 * 60 * 1000 + 36 * 60 * 1000, zone))
    }

    @Test
    fun timeAndUnreadStatusShareOneSupportingLine() {
        assertEquals("12:30 · 읽지 않음", alertSupportingText("", "12:30", false))
        assertEquals("전송이 끝났습니다.\n12:30 · 읽지 않음", alertSupportingText("전송이 끝났습니다.", "12:30", false))
        assertEquals("12:30", alertSupportingText("  ", "12:30", true))
    }

    @Test
    fun onlyIdsArrivingAfterTheScreenOpensGetTheVisitDot() {
        val initial = setOf("old-a", "old-b")
        assertEquals(setOf("new-c"), newlyArrivedAlertIds(initial, initial + "new-c"))
        assertTrue(newlyArrivedAlertIds(initial, initial).isEmpty())
    }
}
