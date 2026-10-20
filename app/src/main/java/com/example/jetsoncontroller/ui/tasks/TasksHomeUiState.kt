package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
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
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.GeoDeviceStripState
import com.example.jetsoncontroller.ui.field.FieldStageInput
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.model.NextAction
import com.example.jetsoncontroller.ui.model.NextActionInput
import com.example.jetsoncontroller.ui.model.OperationState
import com.example.jetsoncontroller.ui.model.TaskState
import com.example.jetsoncontroller.ui.model.CompletionReportState
import com.example.jetsoncontroller.ui.model.nextAction
import com.example.jetsoncontroller.ui.dashboard.metricIsValid
import com.example.jetsoncontroller.ui.sensors.SensorActivity
import com.example.jetsoncontroller.ui.sensors.sensorPresentations

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
    val zone: ZoneId = ZoneId.systemDefault(),
    val status: JetsonStatus? = null,
    val connectionWord: String = if (connected) "연결됨" else "오프라인",
    val connectionTone: Tone = if (connected) Tone.SUCCESS else Tone.UNKNOWN,
    val basicConnection: Boolean = false,
    val checkingDevice: Boolean = false,
    val permissionMissing: Boolean = false,
    val credentialsRejected: Boolean = false,
    val lastResponseLabel: String? = null,
    val receiptVerified: Boolean = false,
    val ticketLinked: Boolean = false,
    val completionReported: Boolean = false,
    val now: Instant = Instant.now(),
    val reportStates: Map<String, CompletionReportState> = emptyMap(),
    val lastObservedAt: Instant? = null,
    val storageWarningPercent: Int = 85,
    val temperatureWarningC: Int = 80,
    /** The device's task list was read within the freshness window (tasksAreFresh). */
    val tasksFresh: Boolean = false
)

private val BusyPipelineStates = setOf(PipelineState.RUNNING, PipelineState.STARTING, PipelineState.STOPPING,
    PipelineState.RETRYING, PipelineState.WAITING_FOR_TIME_SYNC)

/**
 * Before any task is opened, the survey run state has nothing to confirm. A fresh task list in which
 * nothing runs is then the evidence that the device is idle; anything running stays unconfirmed.
 */
internal fun idleConfirmedFromTaskList(input: TasksHomeInput): Boolean {
    val survey = input.survey
    return survey.pipelineId == null && !survey.restoringLocalState && input.connected && input.tasksFresh &&
        input.pipelines.none { it.execution?.active == true || it.state in BusyPipelineStates }
}

/** The pinned 현장 수집 card. [startBlockedReason] is one line, shown only while the start is off. */
internal data class PinnedSurveyState(
    val scopeLabel: String?,
    val scopeCaption: String,
    val startEnabled: Boolean,
    val startBlockedReason: String?,
    /** Choosing a section reads the device's survey context, so it needs a connection. */
    val changeEnabled: Boolean = true
)

internal data class ActiveRunState(
    val title: String,
    val caption: String
)

internal data class RecentRunState(
    val runId: String,
    val title: String,
    val meta: String,
    val cells: List<RunReceiptCell>,
    val report: CompletionReportState? = null
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
    val otherTasksEmptyText: String,
    val ticketSection: ServerTicketUiState? = null,
    val focus: NextAction? = null,
    val deviceStrip: GeoDeviceStripState? = null
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
            scopeCaption = if (scope != null) "마지막으로 고른 구간" else "아직 고른 구간이 없습니다",
            startEnabled = blockedReason == null,
            startBlockedReason = blockedReason,
            changeEnabled = input.connected && activeRun == null
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
                    stateLabel = workTaskState(pipeline, input.connected).word
                )
            },
        otherTasksNote = "작업 등록은 관리자에게 요청하세요.",
        otherTasksEmptyText = "이 장치에 등록된 작업이 없습니다",
        focus = workNextAction(input).takeUnless { input.checkingDevice && !input.permissionMissing && !input.credentialsRejected &&
            survey.pendingStart == null && survey.unconfirmedRunId == null },
        deviceStrip = workDeviceStrip(input)
    )
}

internal fun workNextAction(input: TasksHomeInput): NextAction {
    val s = input.survey
    return nextAction(NextActionInput(
        deviceName = input.deviceName ?: UnnamedDeviceLabel,
        field = FieldStageInput(
            connected = input.connected || input.basicConnection,
            runStateConfirmed = (s.runStateConfirmed && !s.restoringLocalState) || idleConfirmedFromTaskList(input),
            activeRunId = s.activeRun?.takeIf { it.active }?.runId,
            pendingStartRequestId = s.pendingStart?.clientRequestId,
            unconfirmedRunId = s.unconfirmedRunId,
            runAwaitingResultId = s.latestRun?.takeIf { !it.active && !s.resultAcknowledged }?.runId,
            surveyLabel = s.latestRun?.contextSnapshot?.surveySectionLabel ?: s.selectedSection?.label,
            lastObservedLabel = (input.lastObservedAt ?: input.status?.collectedAtEpochMillis?.let(Instant::ofEpochMilli))
                ?.let { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(input.zone).format(it) },
            elapsedLabel = s.activeRun?.startedAt?.let(::parseInstantOrNull)?.let {
                Formats.clock(Duration.between(it, input.now).coerceAtLeast(Duration.ZERO))
            },
            registeredDeviceCount = if (input.deviceName == null) 0 else 1,
            activeRunCount = s.observedActiveRunCount
        ),
        basicConnection = input.basicConnection, credentialsRejected = input.credentialsRejected,
        permissionMissing = input.permissionMissing, lastResponseLabel = input.lastResponseLabel,
        receiptVerified = input.receiptVerified, ticketLinked = input.ticketLinked,
        completionReported = input.completionReported
    ))
}

internal fun workDeviceStrip(input: TasksHomeInput): GeoDeviceStripState {
    val status = input.status
    val now = input.now.toEpochMilli()
    val sensors = status?.let { sensorPresentations(it, input.connected || input.basicConnection, now) }
    val sensorException = sensors?.let {
        listOf("카메라" to it.camera, "위치" to it.gnss, "관성 센서" to it.imu)
            .firstOrNull { (_, sensor) -> sensor.activity in setOf(SensorActivity.STALE, SensorActivity.DISCONNECTED) }
            ?.let { (name, sensor) -> "$name ${sensor.badge}" }
    }
    val attention = sensorException ?: status?.takeIf {
        it.metricIsValid("storagePercent") && it.storageTotalBytes > 0 && it.storagePercent >= input.storageWarningPercent
    }?.let { "저장 공간 ${Formats.percent(it.storagePercent.toDouble())}" } ?: status?.takeIf {
        it.metricIsValid("temperatureC") && it.temperatureC >= input.temperatureWarningC
    }?.let { "장치 온도 ${Formats.temperature(it.temperatureC.toDouble())}" }
    val observed = status?.collectedAtEpochMillis?.let(Instant::ofEpochMilli)
    val focus = workNextAction(input)
    return GeoDeviceStripState(
        deviceName = input.deviceName ?: UnnamedDeviceLabel,
        connectionWord = when {
            input.credentialsRejected -> "인증 필요"
            input.permissionMissing -> "권한 필요"
            input.checkingDevice -> "확인 중"
            input.basicConnection -> "기본 연결"
            else -> input.connectionWord
        },
        connectionTone = when {
            input.credentialsRejected -> Tone.ERROR
            input.permissionMissing || input.basicConnection -> Tone.WARNING
            input.checkingDevice -> Tone.PENDING
            else -> input.connectionTone
        },
        facts = if (input.checkingDevice) emptyList() else listOfNotNull(
            status?.temperatureC?.takeIf { status.metricIsValid("temperatureC") &&
                (it > 0f || status.metricValidity["temperatureC"]?.validity == "valid") }
                ?.let { Formats.temperature(it.toDouble()) },
            status?.storageAvailableBytes?.takeIf { status.storageTotalBytes > 0 && status.metricIsValid("storageTotalBytes") &&
                status.metricIsValid("storageAvailableBytes") }?.let { "저장 ${Formats.free(it)}" }
        ),
        freshness = when {
            input.permissionMissing -> "연결 권한이 필요합니다"
            input.checkingDevice -> "장치를 찾고 있습니다"
            !input.connected && !input.basicConnection -> input.lastResponseLabel?.let { "마지막 응답 $it" } ?: "마지막 응답 미확인"
            else -> Formats.freshness(observed?.let { Duration.between(it, input.now) }, observed, input.now)
        },
        firstAttention = attention,
        // A sensor exception remains at zero taps even when Focus already explains the connection.
        hiddenByFocus = !input.checkingDevice && attention == null && focus.operation in setOf(OperationState.DISCONNECTED, OperationState.RUN_UNCONFIRMED)
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
        cells = taskRunReceiptCells(run, input.uploadJobs.filter { job ->
            val context = job.context
            val output = run.output
            context != null && output != null && context.runId == run.runId &&
                context.deviceId.equals(run.deviceId ?: run.contextSnapshot?.deviceId, true) &&
                context.pipelineId == run.pipelineId && context.outputId == output.outputId &&
                context.surveyProjectId == run.contextSnapshot?.surveyProjectId &&
                context.surveySectionId == run.contextSnapshot?.surveySectionId &&
                job.rootId == output.rootId && job.relativePath.trim('/') == output.path.trim('/')
        }.map(::workUploadWithMatchingReceipt)),
        report = input.reportStates[run.runId ?: run.id]
    )
}

internal fun workUploadWithMatchingReceipt(job: UploadJob): UploadJob {
    val source = listOf(job.sourceName, job.folderName, job.relativePath.trimEnd('/').substringAfterLast('/'))
        .firstOrNull { !it.isNullOrBlank() }
    return job.copy(verification = job.verification?.takeIf {
        it.jobId == job.id && it.targetId == job.targetId && !job.remoteSessionId.isNullOrBlank() &&
            it.remoteSessionId == job.remoteSessionId && !source.isNullOrBlank() && it.sourceName == source
    })
}

internal fun workTaskState(pipeline: ManagedPipeline, confirmed: Boolean): TaskState = when {
    !confirmed -> TaskState.UNKNOWN
    else -> when (pipeline.state) {
        com.example.jetsoncontroller.model.PipelineState.RUNNING -> if (pipeline.activeRunId.isNullOrBlank()) TaskState.UNKNOWN else TaskState.COLLECTING
        com.example.jetsoncontroller.model.PipelineState.STARTING -> TaskState.START_REQUESTED
        com.example.jetsoncontroller.model.PipelineState.STOPPING -> TaskState.STOP_REQUESTED
        com.example.jetsoncontroller.model.PipelineState.RETRYING -> TaskState.RETRYING
        com.example.jetsoncontroller.model.PipelineState.WAITING_FOR_TIME_SYNC -> TaskState.ALIGNING_TIME
        com.example.jetsoncontroller.model.PipelineState.FAILED -> TaskState.FAILED
        com.example.jetsoncontroller.model.PipelineState.STOPPED -> TaskState.WAITING
        com.example.jetsoncontroller.model.PipelineState.UNKNOWN -> TaskState.UNKNOWN
    }
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
