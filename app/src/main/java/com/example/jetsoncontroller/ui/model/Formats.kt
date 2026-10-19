package com.example.jetsoncontroller.ui.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Deterministic, locale-independent display formats used throughout the app. */
object Formats {
    private const val UNKNOWN = "미확인"
    private const val NBSP = '\u00A0'
    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val THOUSAND = BigDecimal.valueOf(1_000L)
    private val MEBIBYTE = BigDecimal.valueOf(1_048_576L)
    private val SIZE_UNITS = listOf("B", "KB", "MB", "GB", "TB")
    private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)
    private val SAME_YEAR_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)
    private val OTHER_YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREA)

    /** Decimal SI byte size. */
    fun size(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return UNKNOWN
        if (bytes < 1_000L) return "$bytes$NBSP${SIZE_UNITS[0]}"

        var unitIndex = 0
        var value = BigDecimal.valueOf(bytes)
        while (value >= THOUSAND && unitIndex < SIZE_UNITS.lastIndex) {
            value = value.divide(THOUSAND)
            unitIndex++
        }

        while (true) {
            val integerValue = value.setScale(0, RoundingMode.HALF_UP)
            if (integerValue >= THOUSAND && unitIndex < SIZE_UNITS.lastIndex) {
                value = value.divide(THOUSAND)
                unitIndex++
                continue
            }

            val oneDecimal = value.setScale(1, RoundingMode.HALF_UP)
            val rendered = when {
                value < BigDecimal.valueOf(100L) && oneDecimal < BigDecimal.valueOf(100L) ->
                    oneDecimal.toPlainString()
                else -> integerValue.toPlainString()
            }
            return "$rendered$NBSP${SIZE_UNITS[unitIndex]}"
        }
    }

    fun free(bytes: Long?): String =
        if (bytes == null || bytes < 0L) UNKNOWN else "${size(bytes)} 남음"

    /** Android Settings style binary memory display; inputs and divisor are in KiB. */
    fun memory(usedKiB: Long?, totalKiB: Long?): String {
        if (usedKiB == null || totalKiB == null || usedKiB < 0L || totalKiB < 0L) return UNKNOWN
        val used = BigDecimal.valueOf(usedKiB).divide(MEBIBYTE, 1, RoundingMode.HALF_UP)
        val total = BigDecimal.valueOf(totalKiB).divide(MEBIBYTE, 1, RoundingMode.HALF_UP)
        return "${used.toPlainString()}${NBSP}GB / ${total.toPlainString()}${NBSP}GB"
    }

    fun speed(bytesPerSecond: Long?): String =
        if (bytesPerSecond == null || bytesPerSecond < 0L) UNKNOWN
        else "${size(bytesPerSecond)}/초"

    /** Percent metrics are rounded HALF_UP after clamping to [0, 100]. */
    fun percent(value: Double?): String {
        if (value == null || !value.isFinite()) return UNKNOWN
        val rounded = BigDecimal.valueOf(value.coerceIn(0.0, 100.0))
            .setScale(0, RoundingMode.HALF_UP)
        return "${rounded.toPlainString()}%"
    }

    /** Floors partial progress; values beyond the total are invalid rather than 100%. */
    fun progress(done: Long?, total: Long?): String {
        if (done == null || total == null || done < 0L || total <= 0L || done > total) return UNKNOWN
        val value = BigDecimal.valueOf(done)
            .multiply(BigDecimal.valueOf(100L))
            .divide(BigDecimal.valueOf(total), 0, RoundingMode.FLOOR)
        return "${value.toPlainString()}%"
    }

    fun temperature(celsius: Double?): String {
        val value = nonNegativeOrSigned(celsius) ?: return UNKNOWN
        return "${value.setScale(0, RoundingMode.HALF_UP).toPlainString()}°C"
    }

    fun clock(elapsed: Duration?): String {
        if (elapsed == null || elapsed.isNegative) return UNKNOWN
        val totalSeconds = elapsed.seconds
        val hours = totalSeconds / 3_600L
        val minutes = totalSeconds / 60L % 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun duration(value: Duration?): String {
        if (value == null || value.isNegative) return UNKNOWN
        val totalSeconds = value.seconds
        if (totalSeconds < 60L) return "1분 미만"
        val totalMinutes = totalSeconds / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours == 0L -> "${totalMinutes}분"
            minutes == 0L -> "${hours}시간"
            else -> "${hours}시간 ${minutes}분"
        }
    }

    /** Returns null when ETA is unknown so callers can omit the entire line. */
    fun eta(value: Duration?): String? {
        if (value == null) return null
        if (value.isNegative) return UNKNOWN
        return if (value < Duration.ofSeconds(60L)) "1분 미만 남음" else "약 ${duration(value)} 남음"
    }

    fun time(instant: Instant?, now: Instant? = Instant.now()): String {
        if (instant == null || now == null) return UNKNOWN
        val moment = instant.atZone(SEOUL)
        val current = now.atZone(SEOUL)
        return when {
            moment.toLocalDate() == current.toLocalDate() -> CLOCK_FORMAT.format(moment)
            moment.toLocalDate() == current.toLocalDate().minusDays(1) -> "어제 ${CLOCK_FORMAT.format(moment)}"
            moment.year == current.year -> SAME_YEAR_FORMAT.format(moment)
            else -> OTHER_YEAR_FORMAT.format(moment)
        }
    }

    /** [updatedAt] is needed only for ages of an hour or more. */
    fun freshness(
        age: Duration?,
        updatedAt: Instant? = null,
        now: Instant? = Instant.now()
    ): String {
        if (age == null || age.isNegative) return UNKNOWN
        return when {
            age <= Duration.ofSeconds(15L) -> "방금 갱신"
            age < Duration.ofMinutes(60L) -> "${age.toMinutes().coerceAtLeast(1L)}분 전 갱신"
            updatedAt == null -> UNKNOWN
            else -> "${time(updatedAt, now)} 갱신"
        }
    }

    fun distance(meters: Double?): String {
        val value = nonNegativeOrSigned(meters)?.takeIf { it.signum() >= 0 } ?: return UNKNOWN
        val step = when {
            value < BigDecimal.TEN -> 1L
            value < BigDecimal.valueOf(100L) -> 5L
            value < BigDecimal.valueOf(1_000L) -> 10L
            else -> return "${value.divide(THOUSAND, 1, RoundingMode.HALF_UP).toPlainString()}${NBSP}km"
        }
        return "${roundToStep(value, step)}${NBSP}m"
    }

    fun length(meters: Double?): String {
        val value = nonNegativeOrSigned(meters)?.takeIf { it.signum() >= 0 } ?: return UNKNOWN
        return if (value < THOUSAND) {
            "${roundToStep(value, 10L)}${NBSP}m"
        } else {
            "${value.divide(THOUSAND, 1, RoundingMode.HALF_UP).toPlainString()}${NBSP}km"
        }
    }

    fun width(bufferMeters: Double?): String {
        val buffer = nonNegativeOrSigned(bufferMeters)?.takeIf { it.signum() >= 0 } ?: return UNKNOWN
        val width = buffer.multiply(BigDecimal.valueOf(2L)).setScale(0, RoundingMode.HALF_UP)
        return "폭 ${width.toPlainString()}${NBSP}m"
    }

    fun area(squareMeters: Double?): String {
        val value = nonNegativeOrSigned(squareMeters)?.takeIf { it.signum() >= 0 } ?: return UNKNOWN
        return when {
            value < THOUSAND -> "${grouped(roundToStep(value, 10L))}${NBSP}m²"
            value < BigDecimal.valueOf(1_000_000L) -> "${grouped(roundToStep(value, 100L))}${NBSP}m²"
            else -> "${value.divide(BigDecimal.valueOf(1_000_000L), 1, RoundingMode.HALF_UP).toPlainString()}${NBSP}km²"
        }
    }

    fun itemCount(value: Long?): String = count(value, "개")
    fun caseCount(value: Long?): String = count(value, "건")
    fun deviceCount(value: Long?): String = count(value, "대")
    fun occurrenceCount(value: Long?): String = count(value, "회")

    fun satelliteCount(value: Long?): String =
        if (value == null || value < 0L) UNKNOWN else "위성 ${grouped(value)}개"

    fun coordinate(latitude: Double?, longitude: Double?): String {
        if (latitude == null || longitude == null ||
            !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return UNKNOWN
        val lat = if (latitude == 0.0) 0.0 else latitude
        val lon = if (longitude == 0.0) 0.0 else longitude
        return String.format(Locale.US, "%.7f, %.7f", lat, lon)
    }

    /** Legacy card date format retained for [com.example.jetsoncontroller.ui.components.formatGeoDateTime]. */
    fun dateTime(
        instant: Instant,
        now: Instant = Instant.now(),
        zone: ZoneId = SEOUL
    ): String {
        val moment = instant.atZone(zone)
        return if (moment.year == now.atZone(zone).year) {
            SAME_YEAR_FORMAT.format(moment)
        } else {
            OTHER_YEAR_FORMAT.format(moment)
        }
    }

    private fun count(value: Long?, unit: String): String =
        if (value == null || value < 0L) UNKNOWN else "${grouped(value)}$unit"

    private fun nonNegativeOrSigned(value: Double?): BigDecimal? =
        value?.takeIf { it.isFinite() }?.let { BigDecimal.valueOf(it) }

    private fun roundToStep(value: BigDecimal, step: Long): String =
        value.divide(BigDecimal.valueOf(step), 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(step))
            .toPlainString()

    private fun grouped(value: String): String =
        DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(BigDecimal(value))

    private fun grouped(value: Long): String =
        DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(value)
}
