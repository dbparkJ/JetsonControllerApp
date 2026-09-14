package com.example.jetsoncontroller.ui.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.QualityProblemInterval
import com.example.jetsoncontroller.model.RoutePoint
import com.example.jetsoncontroller.model.RunQuality
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class RunQualityPresentation(
    val headline: String,
    val detail: String,
    val problems: List<String>
)

internal data class LocatedQualityProblem(
    val routeIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val title: String
)

internal fun runQualityPresentation(quality: RunQuality?): RunQualityPresentation {
    if (quality == null) return RunQualityPresentation(
        headline = "품질 근거 없음",
        detail = "이 실행에는 저장된 GNSS·센서 품질 관찰이 없습니다.",
        problems = emptyList()
    )
    val headline = when (quality.sampleState.uppercase(Locale.US)) {
        "OBSERVED" -> quality.rtkFixRatio
            ?.takeIf { it.isFinite() && it in 0.0..1.0 && (quality.rtkObservedDurationMillis ?: 0L) > 0L }
            ?.let { "RTK FIX 관찰 비율 ${String.format(Locale.US, "%.1f", it * 100)}%" }
            ?: "RTK 관찰 비율 미제공"
        "UNKNOWN" -> "RTK 상태 분류 미확인"
        "INSUFFICIENT_TIMING" -> "관찰 시간 계산에 표본 부족"
        "NO_SAMPLES" -> "RTK 비율 산정 표본 없음"
        else -> "품질 표본 상태 미확인"
    }
    val durations = listOfNotNull(
        quality.elapsedDurationMillis?.let { "관찰 구간 ${qualityDurationLabel(it)}" },
        quality.rtkObservedDurationMillis?.let { "RTK 관찰 ${qualityDurationLabel(it)}" },
        quality.rtkFixDurationMillis?.let { "FIX 유지 ${qualityDurationLabel(it)}" },
        quality.rtkUnknownDurationMillis?.takeIf { it > 0L }?.let { "RTK 미확인 ${qualityDurationLabel(it)}" },
        quality.unknownDurationMillis?.takeIf { it > 0L }?.let { "기록 미확인 ${qualityDurationLabel(it)}" }
    )
    val detail = buildList {
        add("관찰 ${quality.observationCount}개")
        addAll(durations)
        if (quality.truncated) add("일부 구간만 집계")
        add("판정 기준 없음")
    }.joinToString(" · ")
    return RunQualityPresentation(
        headline = headline,
        detail = detail,
        problems = quality.problemIntervals.map(::qualityProblemLabel)
    )
}

internal fun locatedQualityProblems(
    route: List<RoutePoint>,
    quality: RunQuality?
): List<LocatedQualityProblem> = quality?.problemIntervals.orEmpty().mapNotNull { problem ->
    val index = problem.startRoutePointIndex ?: return@mapNotNull null
    val point = route.getOrNull(index) ?: return@mapNotNull null
    if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
        point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0
    ) return@mapNotNull null
    LocatedQualityProblem(index, point.latitude, point.longitude, qualityProblemLabel(problem))
}.distinctBy { it.routeIndex to it.title }

internal fun qualityProblemLabel(problem: QualityProblemInterval): String {
    val issue = when (problem.kind.uppercase(Locale.US)) {
        "RTK_NOT_FIXED" -> "RTK FIX 아님"
        "RTK_UNKNOWN" -> "RTK 상태 미확인"
        "GNSS_LOST" -> "GNSS 수신 끊김"
        "GNSS_STALE" -> "GNSS 갱신 지연"
        "GNSS_UNKNOWN" -> "GNSS 상태 미확인"
        "GNSS_NOT_CONFIGURED" -> "GNSS 미설정"
        "CLOCK_GAP" -> "시간 관찰 공백"
        "SENSOR_LOST" -> "센서 수신 끊김"
        "SENSOR_STALE" -> "센서 갱신 지연"
        "SENSOR_ERROR" -> "센서 오류 관찰"
        "SENSOR_UNKNOWN" -> "센서 상태 미확인"
        "SENSOR_NOT_CONFIGURED" -> "센서 미설정"
        else -> problem.kind.ifBlank { "종류 미확인" }
    }
    val requirement = when (problem.requirement.uppercase(Locale.US)) {
        "REQUIRED" -> "필수"
        "OPTIONAL" -> "선택"
        else -> "요구 수준 미지정"
    }
    val sensor = problem.sensor?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
    val duration = problem.durationMillis?.let { " · ${qualityDurationLabel(it)}" }.orEmpty()
    return "$issue · $requirement$sensor$duration"
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
    val minutes = (seconds % 3_600L) / 60L
    val remainder = seconds % 60L
    return when {
        hours > 0L -> "${hours}시간 ${minutes}분"
        minutes > 0L -> "${minutes}분 ${remainder}초"
        else -> "${remainder}초"
    }
}

@Composable
internal fun RunQualityEvidence(
    quality: RunQuality?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val presentation = runQualityPresentation(quality)
    val colors = LocalCobaltColors.current
    var showAllProblems by remember(quality) { mutableStateOf(false) }
    if (showAllProblems && quality != null) {
        AlertDialog(
            onDismissRequest = { showAllProblems = false },
            title = { Text("전체 품질 관찰 ${quality.problemIntervals.size}건") },
            text = {
                LazyColumn(
                    Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(quality.problemIntervals) { problem ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(qualityProblemLabel(problem), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                qualityProblemTimeLabel(problem),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                            if (problem.startRoutePointIndex == null) Text(
                                "위치 연결 정보 없음",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAllProblems = false }) { Text("닫기") } }
        )
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.sectionSoft,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            Modifier.padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("수집 품질 관찰", style = MaterialTheme.typography.labelLarge)
                if (quality != null) Text(
                    "판정 없음",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted
                )
            }
            Text(presentation.headline, style = MaterialTheme.typography.titleSmall)
            Text(
                presentation.detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
            presentation.problems.take(if (compact) 2 else 4).forEach { problem ->
                Text("• $problem", style = MaterialTheme.typography.bodySmall)
            }
            if (presentation.problems.isNotEmpty()) {
                TextButton(onClick = { showAllProblems = true }) {
                    Text("전체 관찰 ${presentation.problems.size}건과 시각 보기")
                }
            }
        }
    }
}

private val QualityTimestampFormatter = DateTimeFormatter
    .ofPattern("yyyy.MM.dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
