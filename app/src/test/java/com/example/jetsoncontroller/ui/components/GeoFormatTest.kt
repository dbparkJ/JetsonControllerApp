package com.example.jetsoncontroller.ui.components

import java.time.Instant
import com.example.jetsoncontroller.ui.model.Formats
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoFormatTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `same-year moment omits the year`() {
        val now = millis(2026, 10, 17, 9, 0)
        val moment = millis(2026, 10, 15, 10, 39)

        assertEquals("10월 15일 10:39", Formats.dateTime(Instant.ofEpochMilli(moment), Instant.ofEpochMilli(now), seoul))
    }

    @Test
    fun `single-digit month and day are not zero-padded`() {
        val now = millis(2026, 10, 17, 9, 0)
        val moment = millis(2026, 3, 5, 9, 5)

        assertEquals("3월 5일 09:05", Formats.dateTime(Instant.ofEpochMilli(moment), Instant.ofEpochMilli(now), seoul))
    }

    @Test
    fun `moment from another year includes the year`() {
        val now = millis(2026, 10, 17, 9, 0)
        val moment = millis(2025, 12, 31, 23, 10)

        assertEquals("2025년 12월 31일 23:10", Formats.dateTime(Instant.ofEpochMilli(moment), Instant.ofEpochMilli(now), seoul))
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, seoul).toInstant().toEpochMilli()
}
