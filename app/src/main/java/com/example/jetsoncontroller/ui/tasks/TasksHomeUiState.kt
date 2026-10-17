package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.ui.field.RunReceiptCell
import com.example.jetsoncontroller.ui.field.isActiveRun
import com.example.jetsoncontroller.ui.field.runBytesLabel
import com.example.jetsoncontroller.ui.field.taskRunReceiptCells
import com.example.jetsoncontroller.ui.pipelines.taskStateLabel
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val RECENT_TASK_LIMIT = 3

/** Everything the 작업 root reads, gathered by the caller from the existing view models. */
internal data class TasksHomeInput(
    /** Null when no device is selected. */
    val deviceName: String?,
    /** True only when the selected device is reachable over a control transport. */
    val connected: Boolean,
    val survey: SurveyRunUiState,
    val runs: List<TaskRun>,
    val uploadJobs: List<UploadJob>,
    val pipelines: List<ManagedPipeline>,
    val developerModeEnabled: Boolean,
    val zone: ZoneId = ZoneId.systemDefault()
)

/** The pinned 현장 수집 card. [startBlockedReason] is one line, shown only while the start is off. */
internal data class PinnedSurveyState(
    val scopeLabel: String?,
    val scopeCaption: String,
    val startEnabled: Boolean,
    val startBlockedReason: String?
)

internal data class ActiveRunState(
    val title: String,
    val caption: String
)

internal data class RecentRunState(
    val runId: String,
    val title: String,
    val meta: String,
    val cells: List<RunReceiptCell>
)

internal data class OtherTaskState(
    val pipelineId: String,
    val title: String,
    val caption: String,
    val stateLabel: String
)

internal data class TasksHomeUiState(
    val deviceName: String,
    val hasDevice: Boolean,
    val pinned: PinnedSurveyState,
    val activeRun: ActiveRunState?,
    val recent: List<RecentRunState>,
    val recentEmptyText: String,
    val otherTasks: List<OtherTaskState>,
    val otherTasksNote: String,
    val otherTasksEmptyText: String
)

internal fun buildTasksHomeUiState(input: TasksHomeInput): TasksHomeUiState {
    val survey = input.survey
    val deviceName = input.deviceName?.takeIf(String::isNotBlank) ?: UnnamedDeviceLabel
    val activeRun = survey.activeRun?.takeIf { it.active }
    val scope = survey.selectedProject?.let { project ->
        survey.selectedSection?.let { section -> "${project.label} · ${section.label}" }
    }
    val blockedReason = startBlockedReason(input, activeRun != null, scope != null)
    val recent = input.runs
        .filterNot { it.isActiveRun() }
        .take(RECENT_TASK_LIMIT)
        .map { run -> recentRunState(run, input) }
    return TasksHomeUiState(
        deviceName = deviceName,
        hasDevice = input.deviceName != null,
        pinned = PinnedSurveyState(
            scopeLabel = scope,
            scopeCaption = if (scope != null) "마지막으로 고른 구간" else "구간을 골라 주세요",
            startEnabled = blockedReason == null,
            startBlockedReason = blockedReason
        ),
        activeRun = activeRun?.let { run ->
            ActiveRunState(
                title = run.contextSnapshot.surveyProjectLabel + " · " + run.contextSnapshot.surveySectionLabel,
                caption = "수집 중 · $deviceName"
            )
        },
        recent = recent,
        recentEmptyText = "완료한 수집이 없습니다. 수집을 마치면 여기에 영수증이 남습니다.",
        otherTasks = input.pipelines
            .sortedBy { it.label.lowercase(Locale.ROOT) }
            .map { pipeline ->
                OtherTaskState(
                    pipelineId = pipeline.id,
                    title = pipeline.label,
                    caption = formatTaskTime(pipeline.execution?.startedAt, input.zone)
                        ?.let { "마지막 실행 $it" } ?: "실행 기록 없음",
                    stateLabel = taskStateLabel(pipeline, confirmed = input.connected)
                )
            },
        otherTasksNote = if (input.developerModeEnabled) {
            "등록과 편집은 개발자 모드에서 합니다."
        } else {
            "작업 등록과 편집은 관리자에게 요청하세요."
        },
        otherTasksEmptyText = "등록된 다른 작업이 없습니다. 작업 등록은 관리자에게 요청하세요."
    )
}

/** One labelled line on the read-only task detail. */
internal data class TaskDetailRow(val label: String, val value: String)

/**
 * Status and metadata a normal operator may read. Run logs, configuration and editing stay in
 * developer mode, so this list never includes them.
 */
internal fun taskDetailRows(
    pipeline: ManagedPipeline,
    connected: Boolean,
    zone: ZoneId = ZoneId.systemDefault()
): List<TaskDetailRow> {
    val execution = pipeline.execution
    val lastRun = formatTaskTime(execution?.startedAt, zone) ?: "기록 없음"
    val lastResult = when {
        execution == null -> "기록 없음"
        execution.active -> "진행 중"
        execution.exitCode == 0 -> "정상 종료"
        execution.exitCode != null -> "비정상 종료"
        else -> "결과 미확인"
    }
    return listOf(
        TaskDetailRow("상태", taskStateLabel(pipeline, confirmed = connected)),
        TaskDetailRow("마지막 실행", lastRun),
        TaskDetailRow("마지막 결과", lastResult),
        TaskDetailRow("결과 저장 폴더", pipeline.outputPath?.takeIf(String::isNotBlank) ?: "설정되지 않음")
    )
}

/** Developer-only identifiers for the same task. Shown only when developer mode is on. */
internal fun taskDeveloperRows(pipeline: ManagedPipeline): List<TaskDetailRow> = listOf(
    TaskDetailRow("작업 ID", pipeline.id),
    TaskDetailRow("실행 파일", pipeline.entrypoint),
    TaskDetailRow("설정 파일", pipeline.config),
    TaskDetailRow("가상 환경", pipeline.virtualenv),
    TaskDetailRow("소스 버전", pipeline.sourceRevision.ifBlank { "미확인" })
)

/** The first reason the start is off, in the order the operator can fix them. Null means it can start. */
private fun startBlockedReason(input: TasksHomeInput, runActive: Boolean, hasScope: Boolean): String? {
    val survey = input.survey
    return when {
        input.deviceName == null -> "장치를 고르면 점검을 시작할 수 있습니다."
        !input.connected -> "장치와 연결되면 점검을 시작할 수 있습니다."
        runActive -> "수집이 끝난 뒤 새 점검을 시작할 수 있습니다."
        survey.pendingStart != null || survey.unconfirmedRunId != null ->
            "이전 수집의 시작 결과를 확인하고 있습니다."
        survey.contextLocked -> "지난 수집 결과를 확인한 뒤 새 점검을 시작할 수 있습니다."
        !hasScope -> "구간을 고르면 점검을 시작할 수 있습니다."
        survey.policy == null -> "수집 설정이 준비되지 않았습니다. 설정 담당자에게 확인하세요."
        survey.operation != null -> "장치 요청을 처리하고 있습니다."
        else -> null
    }
}

private fun recentRunState(run: TaskRun, input: TasksHomeInput): RecentRunState {
    val title = run.contextSnapshot?.let { "${it.surveyProjectLabel} · ${it.surveySectionLabel}" } ?: run.label
    val meta = listOfNotNull(
        formatTaskTime(run.startedAt, input.zone),
        durationLabel(run.startedAt, run.finishedAt),
        run.output?.manifest?.bytesTotal?.let { runBytesLabel(it) }
    ).joinToString(" · ")
    return RecentRunState(
        runId = run.id,
        title = title,
        meta = meta,
        cells = taskRunReceiptCells(run, input.uploadJobs)
    )
}

/** Shown when the phone has no device name to offer. Never the device UUID. */
internal const val UnnamedDeviceLabel = "선택한 장치"

/** "10월 15일 09:12" in [zone]. Null when the value is missing or not an ISO-8601 instant. */
internal fun formatTaskTime(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
    val instant = parseInstantOrNull(iso) ?: return null
    return TaskTimeFormatter.withZone(zone).format(instant)
}

/** "42분", "1시간 5분", or "1분 미만". Null when either end is missing or the order is invalid. */
internal fun durationLabel(startedIso: String?, finishedIso: String?): String? {
    val start = parseInstantOrNull(startedIso) ?: return null
    val end = parseInstantOrNull(finishedIso) ?: return null
    if (end.isBefore(start)) return null
    val totalMinutes = Duration.between(start, end).toMinutes()
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}시간 ${minutes}분"
        hours > 0L -> "${hours}시간"
        minutes > 0L -> "${minutes}분"
        else -> "1분 미만"
    }
}

internal fun parseInstantOrNull(text: String?): Instant? {
    val value = text?.takeIf(String::isNotBlank) ?: return null
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
}

private val TaskTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN)
