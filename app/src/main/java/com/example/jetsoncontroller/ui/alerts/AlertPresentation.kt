package com.example.jetsoncontroller.ui.alerts

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun alertDayLabel(
    epochMillis: Long,
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    return when (day) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> day.monthValue.toString() + "월 " + day.dayOfMonth + "일"
    }
}

internal fun formatAlertTime(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): String = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(zone)
    .format(Instant.ofEpochMilli(epochMillis))

internal fun newlyArrivedAlertIds(
    previousIds: Set<String>,
    currentIds: Set<String>
): Set<String> = currentIds - previousIds

internal fun alertDayBoundaryEpochMillis(
    date: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
