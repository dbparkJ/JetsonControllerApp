package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketReportState
import com.example.jetsoncontroller.data.server.ServerTicketState
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.model.CompletionReportState
import com.example.jetsoncontroller.ui.model.TicketSegmentState
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.model.PreparationDraft
import com.example.jetsoncontroller.model.PreparationSelection
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.ui.sensors.sensorPresentations
import com.example.jetsoncontroller.ui.sensors.GnssReceptionState
import com.example.jetsoncontroller.ui.sensors.gnssReceptionState
import com.example.jetsoncontroller.ui.dashboard.metricIsValid
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState

internal data class TicketActionAvailability(
    val enabled: Boolean,
    val reason: String? = null
)

internal data class TicketCompletionPresentation(
    val showButton: Boolean,
    val enabled: Boolean,
    val message: String? = null
)

internal fun defaultTicketUnit(deviceUnit: String?, @Suppress("UNUSED_PARAMETER") units: List<String>): String? =
    deviceUnit?.takeIf(String::isNotBlank)

/** A unit filter keeps that unit's tickets and the ones nobody has assigned to a unit yet. */
internal fun filterTicketsByUnit(tickets: List<ServerTicket>, selectedUnit: String?): List<ServerTicket> =
    if (selectedUnit == null) tickets else tickets.filter { it.unit == selectedUnit || it.unit.isNullOrBlank() }

/** Unit label for a ticket row or detail; an empty unit reads as unassigned, not undecided. */
internal fun ticketUnitLabel(ticket: ServerTicket): String =
    ticket.unit?.takeIf(String::isNotBlank) ?: "호기 미지정"

internal fun ticketStartAvailability(role: String?, ticket: ServerTicket): TicketActionAvailability = when {
    role.equals("VIEWER", ignoreCase = true) -> TicketActionAvailability(
        false, "수집 시작은 작업자 권한이 필요합니다."
    )
    (!role.equals("OPERATOR", ignoreCase = true) && !role.equals("ADMIN", ignoreCase = true)) -> TicketActionAvailability(
        false, "작업자 권한을 확인할 수 없습니다."
    )
    ticketConflictCopy(ticket) != null -> TicketActionAvailability(false, ticketConflictCopy(ticket))
    ticket.status?.isBlocked == true -> TicketActionAvailability(false, TICKET_BLOCKED_COPY)
    ticket.status?.normalizedCategory != ServerTicketState.OPEN -> TicketActionAvailability(
        false, "열린 티켓에서만 수집을 시작할 수 있습니다."
    )
    else -> TicketActionAvailability(true)
}

internal fun ticketCompletionPresentation(
    attached: Boolean,
    serverReceiptConfirmed: Boolean,
    role: String?,
    conflicted: Boolean = false,
    startRejected: Boolean = false,
    startRejectedMessage: String? = null
): TicketCompletionPresentation {
    if (!attached) return TicketCompletionPresentation(showButton = false, enabled = false)
    if (!serverReceiptConfirmed) return TicketCompletionPresentation(
        showButton = false,
        enabled = false,
        message = "서버 수신 확인 뒤에 완료를 보고할 수 있습니다. 티켓은 '진행 중'으로 둡니다."
    )
    if (conflicted) return TicketCompletionPresentation(
        showButton = false,
        enabled = false,
        message = "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요."
    )
    if (startRejected) return TicketCompletionPresentation(
        showButton = false,
        enabled = false,
        message = startRejectedMessage ?: "티켓에서 수집을 시작하지 못했습니다. GEO& Work에서 확인해 주세요."
    )
    if (role.equals("VIEWER", ignoreCase = true)) return TicketCompletionPresentation(
        showButton = true,
        enabled = false,
        message = "완료 보고는 작업자 권한이 필요합니다."
    )
    if (!role.equals("OPERATOR", ignoreCase = true) && !role.equals("ADMIN", ignoreCase = true)) return TicketCompletionPresentation(
        showButton = true,
        enabled = false,
        message = "작업자 권한을 확인할 수 없습니다."
    )
    return TicketCompletionPresentation(showButton = true, enabled = true)
}

internal fun ticketConflictCopy(ticket: ServerTicket): String? =
    "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요.".takeIf {
        ticket.report?.normalizedState == ServerTicketReportState.CONFLICT &&
            ticket.report.code == "HUMAN_CHANGED"
    }

internal fun ticketReportStateLabel(ticket: ServerTicket): String {
    ticketConflictCopy(ticket)?.let { return it }
    return ticketReportWord(ticket).word
}

internal fun ticketStatusLabel(ticket: ServerTicket): String =
    ticketSegmentState(ticket).word

/** A person marked the ticket 막힘; the app never starts it or moves it on their behalf. */
internal const val TICKET_BLOCKED_COPY = "막힘 상태입니다. GEO& Work에서 상태를 바꾼 뒤 시작하세요."

internal fun ticketTone(ticket: ServerTicket): Tone = ticketSegmentState(ticket).tone

internal fun ticketSegmentState(ticket: ServerTicket): TicketSegmentState = when {
    ticket.status?.isBlocked == true -> TicketSegmentState.BLOCKED
    ticket.status?.normalizedCategory == ServerTicketState.OPEN -> TicketSegmentState.TODO
    ticket.status?.normalizedCategory == ServerTicketState.IN_PROGRESS -> TicketSegmentState.IN_PROGRESS
    ticket.status?.normalizedCategory == ServerTicketState.COMPLETED -> TicketSegmentState.COMPLETED
    else -> TicketSegmentState.UNKNOWN
}

internal fun ticketReportWord(ticket: ServerTicket): CompletionReportState = when (ticket.report?.normalizedState) {
    ServerTicketReportState.PENDING -> CompletionReportState.WAITING
    ServerTicketReportState.SENT, ServerTicketReportState.DEDUP -> CompletionReportState.REPORTED
    ServerTicketReportState.CONFLICT -> CompletionReportState.ALREADY_CHANGED
    ServerTicketReportState.ERROR, ServerTicketReportState.UNKNOWN -> CompletionReportState.FAILED
    null -> CompletionReportState.NOT_REPORTED
}

internal data class WorkTicketQueue(val open: List<ServerTicket>, val completed: List<ServerTicket>)

internal fun workTicketQueue(tickets: List<ServerTicket>, unit: String?): WorkTicketQueue {
    val filtered = filterTicketsByUnit(tickets, unit)
    val completed = filtered.filter { ticketSegmentState(it) == TicketSegmentState.COMPLETED }
    val order = listOf(TicketSegmentState.IN_PROGRESS, TicketSegmentState.BLOCKED, TicketSegmentState.TODO, TicketSegmentState.UNKNOWN)
    return WorkTicketQueue(filtered.filterNot { it in completed }.sortedBy { order.indexOf(ticketSegmentState(it)) }, completed)
}

internal fun ticketDueLabel(date: String?): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M월 d일"))
}.getOrDefault("미정")

internal fun ticketQueueMeta(ticket: ServerTicket): String =
    "마감 ${ticketDueLabel(ticket.dueDate)} · 담당 ${ticket.assignees.orEmpty().filter(String::isNotBlank).joinToString().ifBlank { "미지정" }}"

internal fun ticketSyncLabel(state: ServerTicketUiState, now: Instant): String? {
    val synced = parseInstantOrNull(state.lastSyncedAt)
    return when {
        state.isCached || state.quietError != null -> "마지막 동기화 ${formatTaskTime(state.lastSyncedAt, java.time.ZoneId.of("Asia/Seoul")) ?: "미확인"}"
        synced != null -> Formats.freshness(Duration.between(synced, now), synced, now)
        else -> null
    }
}

internal fun ticketRefreshVisible(state: ServerTicketUiState, now: Instant): Boolean =
    state.isCached || state.quietError != null || parseInstantOrNull(state.lastSyncedAt)?.let {
        Duration.between(it, now) > Duration.ofSeconds(60)
    } == true

internal data class TicketPassiveReadiness(
    val deviceName: String = UnnamedDeviceLabel,
    val connection: String = "미확인",
    val storage: String = "미확인",
    val position: String = "위치 미확인",
    val camera: String = "미확인",
    val relay: String? = null,
    val controlAvailable: Boolean = false,
    val blockedReason: String? = "장치 상태를 확인한 뒤 시작할 수 있습니다.",
    val remedyLabel: String? = null
)

internal fun ticketPassiveReadiness(
    deviceName: String,
    status: JetsonStatus?,
    connected: Boolean,
    transportLabel: String,
    now: Instant,
    operationBlockedReason: String? = null
): TicketPassiveReadiness {
    val sensors = status?.let { sensorPresentations(it, connected, now.toEpochMilli()) }
    val gnss = status?.gnssSensor
    val position = when {
        sensors?.gnss?.badge != "수신 중" || gnss == null -> "위치 미확인"
        else -> when (gnssReceptionState(true, gnss.fixType, gnss.rtkStatus)) {
            GnssReceptionState.RTK_FIXED -> "정밀 위치"
            GnssReceptionState.RTK_FLOAT -> "대략 위치"
            GnssReceptionState.GNSS_OFF, GnssReceptionState.RTK_OFF ->
                if (gnss.latitude != null && gnss.longitude != null) "대략 위치" else "위치 없음"
        }
    }
    return TicketPassiveReadiness(
        deviceName, transportLabel,
        status?.takeIf { connected && it.storageTotalBytes > 0 && it.metricIsValid("storageAvailableBytes") }
            ?.let { Formats.free(it.storageAvailableBytes) } ?: "미확인",
        position, sensors?.camera?.badge ?: "미확인",
        controlAvailable = connected,
        blockedReason = operationBlockedReason ?: if (!connected) "연결되면 시작할 수 있습니다." else null,
        remedyLabel = if (!connected) "연결 방법" else null
    )
}

/** Opening preparation only writes the draft; device mutations belong to the start transaction. */
internal fun ticketDraft(
    ticket: ServerTicket, deviceId: String, profileId: String, credentialRevision: String?,
    pipelineId: String?, existing: PreparationDraft?, now: Long
): PreparationDraft {
    if (existing != null && existing.deviceId.equals(deviceId, true) && existing.serverProfileId == profileId &&
        existing.ticketRowId == ticket.rowId && existing.ticketRevision == ticket.revision &&
        existing.credentialRevision == credentialRevision && existing.geometryRevision == ticket.zoneRevision &&
        existing.geometryHash == ticket.zoneHash) return existing.copy(pipelineId = pipelineId ?: existing.pipelineId)
    return PreparationDraft(
        deviceId = deviceId, serverProfileId = profileId, ticketRowId = ticket.rowId,
        ticketRevision = ticket.revision, pipelineId = pipelineId,
        project = ticket.project?.takeIf(String::isNotBlank)?.let { PreparationSelection.CreateFromTicket(it) },
        section = PreparationSelection.CreateFromTicket(ticket.title),
        geometryRevision = ticket.zoneRevision, geometryHash = ticket.zoneHash,
        updatedAt = now, credentialRevision = credentialRevision
    )
}

internal data class WorkTicketTarget(
    val deviceId: String, val profileId: String, val rowId: String, val revision: String?, val pipelineId: String,
    val geometryRevision: String? = null, val geometryHash: String? = null
)

internal fun WorkTicketTarget.matches(deviceId: String?, profileId: String?, ticket: ServerTicket?, pipelineId: String?): Boolean =
    ticket != null && this.deviceId.equals(deviceId, true) && this.profileId == profileId && rowId == ticket.rowId &&
        revision == ticket.revision && this.pipelineId == pipelineId &&
        geometryRevision == ticket.zoneRevision && geometryHash == ticket.zoneHash

internal fun ticketStartSheet(
    ticket: ServerTicket, deviceName: String, survey: SurveyRunUiState, checking: Boolean,
    changed: Boolean, preparationFailure: String?, onStorage: () -> Unit, onConnection: () -> Unit,
    onSensors: () -> Unit = onConnection
): GeoStartSheetState {
    val preflight = survey.preflight
    val checks = when {
        changed -> emptyList()
        checking -> listOf("연결", "저장 공간", "위치 품질", "카메라").map { GeoStartCheck(it, GeoStartPhase.CHECKING) }
        preparationFailure != null -> listOf(GeoStartCheck("수집 구간", GeoStartPhase.FAILED, reason = preparationFailure,
            remedy = GeoOverlayAction("연결 방법", onConnection)))
        preflight == null -> listOf(GeoStartCheck("시작 전 점검", GeoStartPhase.FAILED,
            reason = "장치에서 점검 결과를 받지 못했습니다. 다시 점검하세요.", remedy = GeoOverlayAction("연결 방법", onConnection)))
        else -> {
            fun phase(state: String) = if (state.uppercase() in setOf("READY", "PASS", "PASSED", "OK", "ACTIVE", "AVAILABLE", "CURRENT", "SKIPPED", "NOT_REQUIRED"))
                GeoStartPhase.PASSED else GeoStartPhase.FAILED
            listOf(
                GeoStartCheck("시간", phase(preflight.checks.time.state),
                    reason = "장치 시간을 확인하지 못했습니다.", remedy = GeoOverlayAction("연결 방법", onConnection)),
                GeoStartCheck("저장 공간", phase(preflight.checks.storage.state),
                    value = "남은 공간 ${Formats.size(preflight.checks.storage.availableBytes)}",
                    reason = "저장 공간이 수집 설정의 최소값보다 적습니다.", remedy = GeoOverlayAction("장치 파일", onStorage))
            ) + preflight.checks.sensors.filter { it.requirement == "REQUIRED" || phase(it.state) == GeoStartPhase.PASSED }.map { check ->
                GeoStartCheck(when (check.sensor.lowercase()) { "camera" -> "카메라"; "gnss" -> "위치 품질"; "imu" -> "관성 센서"; else -> "센서" },
                    phase(check.state), reason = "센서 상태를 확인해야 합니다.", remedy = GeoOverlayAction("장치 상세", onSensors))
            } + if (!preflight.ready) listOf(GeoStartCheck("시작 전 점검", GeoStartPhase.FAILED,
                reason = "점검을 통과해야 시작할 수 있습니다.")) else emptyList()
        }
    }
    return GeoStartSheetState(ticket.title, ticket.project ?: "미확인", deviceName, "위치 미확인",
        when { checking -> GeoStartPhase.CHECKING; checks.isNotEmpty() && checks.all { it.phase == GeoStartPhase.PASSED } && survey.canStart -> GeoStartPhase.PASSED; else -> GeoStartPhase.FAILED },
        checks, ticketLinked = true, ticketChanged = changed)
}
