package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.GeoShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.model.Formats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class LocationQualityBand { PRECISE, APPROXIMATE, LEGACY_OTHER, UNKNOWN, NONE }

internal data class PrecisionSlice(
    val band: LocationQualityBand,
    val label: String,
    val durationMillis: Long,
    val ratio: Double
)

internal data class RunQualityPresentation(
    val headline: String,
    val detail: String,
    val slices: List<PrecisionSlice>,
    val problems: List<String>
)

internal data class LocatedQualityProblem(
    val routeIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val title: String
)

internal fun locationQualityBand(fixState: String?, sensorState: String? = null): LocationQualityBand {
    val sensor = sensorState?.uppercase(Locale.US)
    if (sensor in setOf("LOST", "NOT_CONFIGURED", "DISCONNECTED")) return LocationQualityBand.NONE
    if (sensor in setOf("UNKNOWN", "NO_SAMPLE", "STALE", "ERROR")) return LocationQualityBand.UNKNOWN
    return when (fixState?.uppercase(Locale.US)) {
        "FIXED", "RTK_FIXED" -> LocationQualityBand.PRECISE
        "FLOAT", "RTK_FLOAT", "DIFFERENTIAL", "STANDALONE", "SINGLE" -> LocationQualityBand.APPROXIMATE
        "NO_FIX", "NONE" -> LocationQualityBand.NONE
        else -> LocationQualityBand.UNKNOWN
    }
}

internal fun locationQualityLabel(band: LocationQualityBand): String = when (band) {
    LocationQualityBand.PRECISE -> "정밀 위치"
    LocationQualityBand.APPROXIMATE -> "대략 위치"
    LocationQualityBand.LEGACY_OTHER -> "정밀 위치 외"
    LocationQualityBand.UNKNOWN -> "위치 미확인"
    LocationQualityBand.NONE -> "위치 없음"
}

internal fun runQualityPresentation(quality: RunQuality?): RunQualityPresentation {
    if (quality == null) return RunQualityPresentation(
        "위치 품질 미확인", "저장된 위치 관찰이 없습니다.", emptyList(), emptyList()
    )
    val classifiedSlices = quality.locationPrecision.mapNotNull(::precisionSlice)
        .filter { it.durationMillis > 0L }
        .groupBy { it.band }
        .map { (band, values) ->
            PrecisionSlice(
                band = band,
                label = locationQualityLabel(band),
                durationMillis = values.sumOf { it.durationMillis },
                ratio = values.sumOf { it.ratio }.coerceIn(0.0, 1.0)
            )
        }
        .sortedBy { PrecisionBandOrder.indexOf(it.band) }
    val exactSlices = if (classifiedSlices.isEmpty()) emptyList() else buildList {
        addAll(classifiedSlices)
        quality.rtkUnknownDurationMillis?.takeIf { it > 0L }?.let { unknown ->
            val elapsed = (quality.rtkObservedDurationMillis ?: classifiedSlices.sumOf { it.durationMillis }) + unknown
            add(PrecisionSlice(LocationQualityBand.UNKNOWN, "위치 미확인", unknown, unknown.toDouble() / elapsed))
        }
    }
    val slices = exactSlices.ifEmpty { legacyPrecisionSlices(quality) }
    val preciseRatio = quality.rtkFixRatio?.takeIf {
        it.isFinite() && it in 0.0..1.0 && (quality.rtkObservedDurationMillis ?: 0L) > 0L
    } ?: slices.firstOrNull { it.band == LocationQualityBand.PRECISE }?.ratio
    val headline = when {
        preciseRatio != null -> "정밀 위치 ${formatPercent(preciseRatio)}"
        quality.sampleState.equals("NO_SAMPLES", true) -> "위치 품질 미확인"
        slices.any { it.band == LocationQualityBand.NONE } -> "위치 없음"
        else -> "위치 품질 미확인"
    }
    val detail = buildList {
        add("관찰 ${quality.observationCount}개")
        quality.rtkObservedDurationMillis?.let {
            add("분류 ${qualityDurationLabel(it)}")
            if (it > 0L) add("비율은 확인된 관찰 시간 기준")
        }
        quality.rtkUnknownDurationMillis?.takeIf { it > 0L }?.let { add("미확인 ${qualityDurationLabel(it)}") }
        if (quality.truncated) add("일부 구간 집계")
    }.joinToString(" · ")
    return RunQualityPresentation(headline, detail, slices, quality.problemIntervals.map(::qualityProblemLabel))
}

private fun precisionSlice(value: LocationPrecisionDuration): PrecisionSlice? {
    if (value.durationMillis < 0L || !value.ratio.isFinite() || value.ratio !in 0.0..1.0) return null
    val band = locationQualityBand(value.fixState)
    return PrecisionSlice(band, locationQualityLabel(band), value.durationMillis, value.ratio)
}

private fun legacyPrecisionSlices(quality: RunQuality): List<PrecisionSlice> {
    val observed = quality.rtkObservedDurationMillis?.takeIf { it > 0L } ?: return emptyList()
    val fixed = quality.rtkFixDurationMillis?.coerceIn(0L, observed)
        ?: quality.rtkFixRatio?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?.let { (observed * it).toLong().coerceIn(0L, observed) }
        ?: return emptyList()
    val unknown = quality.rtkUnknownDurationMillis?.coerceAtLeast(0L) ?: 0L
    return buildList {
        if (fixed > 0L) add(PrecisionSlice(LocationQualityBand.PRECISE, "정밀 위치", fixed, fixed.toDouble() / observed))
        val other = (observed - fixed).coerceAtLeast(0L)
        if (other > 0L) add(PrecisionSlice(LocationQualityBand.LEGACY_OTHER, "정밀 위치 외", other, other.toDouble() / observed))
        if (unknown > 0L) add(PrecisionSlice(LocationQualityBand.UNKNOWN, "위치 미확인", unknown, 0.0))
    }
}

internal fun locatedQualityProblems(route: List<RoutePoint>, quality: RunQuality?): List<LocatedQualityProblem> =
    quality?.problemIntervals.orEmpty().mapNotNull { problem ->
        val index = problem.startRoutePointIndex ?: return@mapNotNull null
        val point = route.getOrNull(index)?.takeIf(::validRoutePoint) ?: return@mapNotNull null
        LocatedQualityProblem(index, point.latitude, point.longitude, qualityProblemLabel(problem))
    }.distinctBy { it.routeIndex to it.title }

internal fun validRoutePoint(point: RoutePoint): Boolean = point.latitude.isFinite() && point.longitude.isFinite() &&
    point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

internal fun qualityProblemLabel(problem: QualityProblemInterval): String {
    val issue = when (problem.kind.uppercase(Locale.US)) {
        "RTK_NOT_FIXED" -> "정밀 위치가 아니었던 구간"
        "RTK_UNKNOWN", "GNSS_UNKNOWN", "SENSOR_UNKNOWN" -> "위치 상태 미확인"
        "GNSS_LOST" -> "위치 수신 끊김"
        "GNSS_STALE" -> "위치 갱신 지연"
        "GNSS_NOT_CONFIGURED" -> "위치 수신 미설정"
        "CLOCK_GAP" -> "관찰 시간 공백"
        "SENSOR_LOST" -> "센서 수신 끊김"
        "SENSOR_STALE" -> "센서 갱신 지연"
        "SENSOR_ERROR" -> "센서 상태 확인 필요"
        "SENSOR_NOT_CONFIGURED" -> "센서 미설정"
        else -> "품질 확인 필요"
    }
    val sensor = when (problem.sensor?.lowercase(Locale.US)) {
        "camera" -> "카메라"
        "imu" -> "움직임 센서"
        "gnss", "gps" -> "위치"
        else -> null
    }
    val duration = problem.durationMillis?.let { " · ${qualityDurationLabel(it)}" }.orEmpty()
    return listOfNotNull(issue, sensor).joinToString(" · ") + duration
}

internal fun qualityProblemTimeLabel(problem: QualityProblemInterval): String {
    val start = qualityTimestampLabel(problem.startedAtEpochMillis)
    val end = problem.endedAtEpochMillis?.let(::qualityTimestampLabel) ?: "종료 시각 미확인"
    return "$start ~ $end"
}

private fun qualityTimestampLabel(epochMillis: Long): String = runCatching {
    QualityTimestampFormatter.format(Instant.ofEpochMilli(epochMillis))
}.getOrDefault("시각 미확인")

internal fun qualityDurationLabel(durationMillis: Long): String {
    val seconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = seconds % 3_600L / 60L
    val remainder = seconds % 60L
    return when {
        hours > 0L -> "${hours}시간 ${minutes}분"
        minutes > 0L -> "${minutes}분 ${remainder}초"
        else -> "${remainder}초"
    }
}

private fun formatPercent(ratio: Double): String = Formats.percent(ratio * 100.0)

internal fun precisionMetricLabel(quality: RunQuality?): String = quality?.rtkFixRatio
    ?.takeIf { it.isFinite() && it in 0.0..1.0 && (quality.rtkObservedDurationMillis ?: 0L) > 0L }
    ?.let(::formatPercent)
    ?: quality?.locationPrecision?.firstOrNull {
        locationQualityBand(it.fixState) == LocationQualityBand.PRECISE && it.durationMillis > 0L &&
            it.ratio.isFinite() && it.ratio in 0.0..1.0
    }?.ratio?.let(::formatPercent)
    ?: "미확인"

internal fun qualityDetailSlices(presentation: RunQualityPresentation): List<PrecisionSlice> =
    presentation.slices.filter { it.durationMillis >= 1_000L }

internal fun qualityDetailProblems(quality: RunQuality?): List<QualityProblemInterval> =
    quality?.problemIntervals.orEmpty().filter { problem ->
        val duration = problem.durationMillis ?: problem.endedAtEpochMillis?.let { it - problem.startedAtEpochMillis }
        duration == null || duration >= 1_000L
    }

internal fun qualitySheetHeadline(precision: String, headline: String): String =
    if (precision != "미확인") precision else headline.removePrefix("위치 품질 ")

@Composable
internal fun RunQualityEvidence(
    quality: RunQuality?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    title: String = "위치 품질",
    zoneObservation: ResultZoneObservation? = null,
    subtitle: String? = null
) {
    val presentation = runQualityPresentation(quality)
    val detailSlices = qualityDetailSlices(presentation)
    val detailProblems = qualityDetailProblems(quality)
    val precision = precisionMetricLabel(quality)
    val colors = LocalGeoColors.current
    var showDetails by remember(quality) { mutableStateOf(false) }
    val openerFocus = remember { FocusRequester() }
    if (showDetails) {
        GeoSheet(title, { showDetails = false },
            kind = GeoSheetKind.INFO, subtitle = subtitle, openerFocusRequester = openerFocus) {
            SelectionContainer {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (precision != "미확인") Text("정밀 위치 · 관찰한 시간 기준",
                    style = LocalGeoType.current.supporting, color = colors.textSecondary)
                Text(qualitySheetHeadline(precision, presentation.headline),
                    style = if (precision != "미확인") LocalGeoType.current.phoneFigure else LocalGeoType.current.titleSm,
                    color = colors.text)
                Text(presentation.detail, style = LocalGeoType.current.caption, color = colors.textSecondary)
              }
            }
            PrecisionBar(presentation.slices)
            GeoSection("시간 집계") {
                GeoPropertyList(listOf("관찰한 시간", "관찰하지 못한 시간")) {
                    GeoFactRow("관찰한 시간", quality?.rtkObservedDurationMillis?.let(::qualityDurationLabel) ?: "미확인", numeric = true)
                    GeoFactRow("관찰하지 못한 시간", quality?.rtkUnknownDurationMillis?.let(::qualityDurationLabel) ?: "미확인", numeric = true)
                }
            }
            if (detailSlices.isNotEmpty()) GeoSection("품질별 시간") {
                GeoPropertyList(detailSlices.map { it.label }) {
                    detailSlices.forEach { slice ->
                        GeoPropertyRow(slice.label, qualityDurationLabel(slice.durationMillis), numeric = true)
                    }
                }
            }
            if (detailProblems.isNotEmpty()) GeoSection("문제 구간") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    detailProblems.forEach { problem ->
                        SelectionContainer { Column(Modifier.fillMaxWidth().background(colors.warningSoft, GeoShape.card).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(qualityProblemLabel(problem), style = LocalGeoType.current.titleSm, color = colors.text)
                            Text(qualityProblemTimeLabel(problem), style = LocalGeoType.current.supporting, color = colors.textSecondary)
                        } }
                    }
                }
            }
            zoneObservation?.let {
                GeoSection("수집 구역") {
                  GeoPropertyList(listOf("구역 밖 기록", "관찰하지 못한 시간")) {
                    GeoFactRow("구역 밖 기록", "${it.excursions}회 · ${qualityDurationLabel(it.outsideMillis)}")
                    GeoFactRow("관찰하지 못한 시간", qualityDurationLabel(it.unobservedMillis))
                    Text("구역 밖 기록은 관찰한 시간 기준입니다.", style = LocalGeoType.current.caption,
                        color = colors.textSecondary)
                  }
                }
            }
        }
    }
    if (compact) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(presentation.headline, style = LocalGeoType.current.titleSm, color = colors.text)
            PrecisionBar(presentation.slices)
            SelectionContainer { Text(presentation.detail, style = LocalGeoType.current.caption, color = colors.textSecondary) }
        }
    } else {
        GeoRow("위치 품질 자세히", { showDetails = true }, meta = presentation.headline,
            leading = { GeoIconTile(GeoIcons.myLocation) }, divider = false,
            modifier = modifier.focusRequester(openerFocus))
    }
}
@Composable
internal fun PrecisionBar(slices: List<PrecisionSlice>) {
    if (slices.isEmpty()) return
    val colors = LocalGeoColors.current
    val denominator = slices.sumOf { it.durationMillis }.takeIf { it > 0L } ?: return
    Row(Modifier.fillMaxWidth().height(8.dp)) {
        slices.filter { it.durationMillis > 0L }.forEach { slice ->
            Box(
                Modifier.weight(slice.durationMillis.toFloat() / denominator.toFloat()).height(8.dp)
                    .background(qualityBandColor(slice.band, colors.success, colors.warning, colors.neutral, colors.danger))
            )
        }
    }
}

internal fun qualityBandColor(band: LocationQualityBand, precise: Color, approximate: Color, unknown: Color, none: Color): Color =
    when (band) {
        LocationQualityBand.PRECISE -> precise
        LocationQualityBand.APPROXIMATE -> approximate
        LocationQualityBand.LEGACY_OTHER, LocationQualityBand.UNKNOWN -> unknown
        LocationQualityBand.NONE -> none
    }

private val QualityTimestampFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREA)
    .withZone(ZoneId.of("Asia/Seoul"))

private val PrecisionBandOrder = listOf(
    LocationQualityBand.PRECISE,
    LocationQualityBand.APPROXIMATE,
    LocationQualityBand.LEGACY_OTHER,
    LocationQualityBand.NONE,
    LocationQualityBand.UNKNOWN
)
