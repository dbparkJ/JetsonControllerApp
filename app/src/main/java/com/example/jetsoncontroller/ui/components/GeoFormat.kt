package com.example.jetsoncontroller.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SAME_YEAR_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)
private val OTHER_YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREA)

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
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
    val formatter = if (moment.year == now.year) SAME_YEAR_FORMATTER else OTHER_YEAR_FORMATTER
    return formatter.format(moment)
}
