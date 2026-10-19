package com.example.jetsoncontroller.ui.components

import com.example.jetsoncontroller.ui.model.Formats
import java.time.Instant
import java.time.ZoneId

/**
 * The one date-and-time format for the app: "10월 15일 10:39".
 *
 * The year is added only when the moment is not in the current year, so "2025년 12월 31일
 * 23:10" appears only for an older record. [nowEpochMillis] and [zone] are parameters so
 * the rule can be tested without the clock.
 */
fun formatGeoDateTime(
    epochMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.of("Asia/Seoul")
): String {
    return Formats.dateTime(
        Instant.ofEpochMilli(epochMillis),
        Instant.ofEpochMilli(nowEpochMillis),
        zone
    )
}
