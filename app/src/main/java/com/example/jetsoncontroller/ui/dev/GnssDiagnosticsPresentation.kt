package com.example.jetsoncontroller.ui.dev

import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.ui.model.Formats
import java.util.Locale

internal data class GnssDiagnosticRow(val label: String, val value: String)

internal data class GnssDiagnosticsPresentation(
    val rows: List<GnssDiagnosticRow>,
    val error: String?,
    val lastKnown: Boolean
)

internal fun gnssDiagnosticsNeedsTicker(gnss: GnssSensorStatus, nowEpochMillis: Long): Boolean =
    gnssDiagnosticAge(gnss.rtcmLastAtEpochMillis, nowEpochMillis) != null ||
        gnssDiagnosticAge(gnss.ntripLastGgaSentAtEpochMillis, nowEpochMillis) != null

internal fun gnssDiagnosticsPresentation(
    gnss: GnssSensorStatus,
    nowEpochMillis: Long,
    fresh: Boolean
): GnssDiagnosticsPresentation {
    val rows = buildList {
        val selection = when (gnss.ntripSelection) {
            "caster" -> "캐스터 자동 선택"
            "client" -> "가까운 기준국 직접 선택"
            else -> null
        }
        selection?.let { add(GnssDiagnosticRow("기준국 선택", it)) }
        val base = listOfNotNull(
            gnss.rtcmBaseStationId?.takeIf { it in 0..4095 }?.let { String.format(Locale.ROOT, "ID %04d", it) },
            gnss.rtcmBaseDistanceM?.takeIf { it.isFinite() && it >= 0 }?.let(Formats::distance)
        ).joinToString(" · ")
        if (base.isNotEmpty()) add(GnssDiagnosticRow("보정 기준국", base))
        val types = gnss.rtcmMessageTypes.filter { it in 1..4095 }.distinct().sorted().take(32)
        if (types.isNotEmpty()) add(GnssDiagnosticRow("받은 RTCM 메시지", types.joinToString(", ")))
        addAll(gnssSignalRows(gnss.rtcmSignals, types))
        gnssDiagnosticAge(gnss.rtcmLastAtEpochMillis, nowEpochMillis)?.let {
            add(GnssDiagnosticRow("마지막 보정", it))
        }
        gnssDiagnosticAge(gnss.ntripLastGgaSentAtEpochMillis, nowEpochMillis)?.let {
            add(GnssDiagnosticRow("마지막 GGA 송신", it))
        }
    }
    val error = gnss.ntripError?.takeIf { it.isNotBlank() }?.let { "NTRIP 오류: $it" }
    return GnssDiagnosticsPresentation(rows, error, !fresh && (rows.isNotEmpty() || error != null))
}

private val GNSS_SIGNAL_NAMES = mapOf(
    "GPS" to "GPS", "GLO" to "GLONASS", "GAL" to "Galileo", "BDS" to "BeiDou",
    "QZS" to "QZSS", "SBS" to "SBAS", "IRN" to "NavIC"
)
private val GNSS_SIGNAL = Regex("(GPS|GLO|GAL|SBS|QZS|BDS|IRN) ([1-9][A-Z]|\\?[0-9]{1,2})")

internal fun gnssSignalRows(signals: List<String>, messageTypes: List<Int>): List<GnssDiagnosticRow> {
    val valid = signals.filter(GNSS_SIGNAL::matches).distinct().sorted().take(48)
    if (valid.isNotEmpty()) {
        val groups = valid.groupBy({ it.substringBefore(' ') }, { it.substringAfter(' ') })
        val received = GNSS_SIGNAL_NAMES.mapNotNull { (prefix, name) ->
            groups[prefix]?.let { "$name ${it.joinToString("·")}" }
        }.joinToString(" · ")
        val l5 = valid.any {
            it.substringBefore(' ') in setOf("GPS", "GAL", "QZS", "BDS") && it.substringAfter(' ').startsWith('5')
        }
        return listOf(
            GnssDiagnosticRow("받은 신호", received),
            GnssDiagnosticRow("L5 대역 보정", if (l5) "있음" else "없음")
        )
    }
    val legacy = messageTypes.filter { it in 1001..1004 || it in 1009..1012 }.distinct().sorted()
    val msm = messageTypes.any { it / 10 in 107..113 && it % 10 in 1..7 }
    return if (legacy.isNotEmpty() && !msm) {
        listOf(GnssDiagnosticRow("받은 신호", "MSM 없음 (${legacy.joinToString("·")} 레거시 관측)"))
    } else emptyList()
}

internal fun gnssDiagnosticAge(timestamp: Long?, nowEpochMillis: Long): String? {
    if (timestamp == null || timestamp <= 0 || nowEpochMillis <= 0) return null
    val elapsed = nowEpochMillis - timestamp
    if (elapsed < -60_000L) return null
    val seconds = elapsed.coerceAtLeast(0) / 1_000
    return when {
        seconds < 60 -> "${seconds}초 전"
        seconds < 3_600 -> "${seconds / 60}분 전"
        seconds < 86_400 -> "${seconds / 3_600}시간 전"
        else -> "${seconds / 86_400}일 전"
    }
}
