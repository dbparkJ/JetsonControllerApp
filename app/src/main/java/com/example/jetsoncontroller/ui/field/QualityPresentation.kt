package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors
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

private fun formatPercent(ratio: Double): String = String.format(Locale.US, "%.0f%%", ratio * 100.0)

internal fun precisionMetricLabel(quality: RunQuality?): String = quality?.rtkFixRatio
    ?.takeIf { it.isFinite() && it in 0.0..1.0 && (quality.rtkObservedDurationMillis ?: 0L) > 0L }
    ?.let(::formatPercent)
    ?: quality?.locationPrecision?.firstOrNull {
        locationQualityBand(it.fixState) == LocationQualityBand.PRECISE && it.durationMillis > 0L &&
            it.ratio.isFinite() && it.ratio in 0.0..1.0
    }?.ratio?.let(::formatPercent)
    ?: "미확인"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunQualityEvidence(
    quality: RunQuality?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    title: String = "위치 품질"
) {
    val presentation = runQualityPresentation(quality)
    val colors = LocalCobaltColors.current
    var showDetails by remember { mutableStateOf(false) }
    val inheritedDensity = LocalDensity.current
    if (showDetails) {
        ModalBottomSheet(onDismissRequest = { showDetails = false }) {
            CompositionLocalProvider(LocalDensity provides inheritedDensity) {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Text("$title 상세", style = MaterialTheme.typography.titleLarge) }
                    items(presentation.slices) { slice ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(slice.label)
                            Text(qualityDurationLabel(slice.durationMillis), color = colors.muted)
                        }
                    }
                    if (presentation.problems.isNotEmpty()) {
                        item { HorizontalDivider(color = colors.border) }
                        items(quality?.problemIntervals.orEmpty()) { problem ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(qualityProblemLabel(problem))
                                Text(qualityProblemTimeLabel(problem), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            }
                        }
                    }
                    item { TextButton(onClick = { showDetails = false }) { Text("닫기") } }
                }
            }
        }
    }
    Surface(modifier.fillMaxWidth(), color = colors.surface, shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.padding(if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.muted)
            Text(presentation.headline, style = MaterialTheme.typography.titleMedium)
            PrecisionBar(presentation.slices)
            if (presentation.slices.isNotEmpty()) {
                Text(
                    presentation.slices.joinToString(" · ") {
                        "${it.label} ${qualityDurationLabel(it.durationMillis)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
            Text(presentation.detail, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            if (presentation.slices.isNotEmpty() || presentation.problems.isNotEmpty()) {
                TextButton(onClick = { showDetails = true }) { Text("품질 상세 보기") }
            }
        }
    }
}

@Composable
internal fun PrecisionBar(slices: List<PrecisionSlice>) {
    if (slices.isEmpty()) return
    val colors = LocalCobaltColors.current
    val denominator = slices.sumOf { it.durationMillis }.takeIf { it > 0L } ?: return
    Row(Modifier.fillMaxWidth().height(8.dp)) {
        slices.filter { it.durationMillis > 0L }.forEach { slice ->
            Box(
                Modifier.weight(slice.durationMillis.toFloat() / denominator.toFloat()).height(8.dp)
                    .background(qualityBandColor(slice.band, colors.primary, colors.warning, colors.unknown, colors.danger))
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

private val QualityTimestampFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private val PrecisionBandOrder = listOf(
    LocationQualityBand.PRECISE,
    LocationQualityBand.APPROXIMATE,
    LocationQualityBand.LEGACY_OTHER,
    LocationQualityBand.NONE,
    LocationQualityBand.UNKNOWN
)
