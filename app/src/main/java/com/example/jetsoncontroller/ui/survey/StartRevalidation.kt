package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.model.PreparationDraft
import com.example.jetsoncontroller.model.PreparationSelection
import com.example.jetsoncontroller.model.StartTarget
import com.example.jetsoncontroller.model.StopTarget

internal const val PreflightMaxAgeMillis = 120_000L
internal const val TicketChangedMessage = "GEO& Work에서 이 구간이 바뀌었습니다. 다시 확인하세요."

internal fun startRevalidationError(
    target: StartTarget,
    state: SurveyRunUiState,
    nowEpochMillis: Long,
    nowElapsedMillis: Long
): String? {
    val draft = target.draft
    if (!draft.deviceId.equals(state.deviceId, true) || draft.pipelineId != state.pipelineId ||
        !state.online || !state.controlAvailable || state.restoringLocalState || !state.runStateConfirmed
    ) return "장치와 수집 작업을 다시 확인하세요."
    if (state.contextLocked || state.observedActiveRunCount > 0) return "진행 중인 수집과 시작 요청을 먼저 확인하세요."
    val current = state.preparationDraft ?: state.manualDraft()
    if (current != draft) return if (!sameTicketIdentity(current, draft)) TicketChangedMessage
        else "프로젝트와 구간을 다시 확인하세요."
    val project = draft.project as? PreparationSelection.Existing
    val section = draft.section as? PreparationSelection.Existing
    if (project?.id != state.selectedProject?.surveyProjectId || project?.revision != state.selectedProject?.revision ||
        section?.id != state.selectedSection?.surveySectionId || section?.revision != state.selectedSection?.revision ||
        project == null || section == null
    ) return "프로젝트와 구간을 다시 확인하세요."
    val preflight = state.preflight ?: return "점검을 통과해야 시작할 수 있습니다."
    if (!preflight.ready || target.preflightId != preflight.preflightId ||
        target.policyRevision != state.policy?.revision ||
        !preflightMatches(preflight, state.deviceId, state.pipelineId, state.selectedProject, state.selectedSection, state.policy)
    ) return "점검을 통과해야 시작할 수 있습니다."
    val age = nowEpochMillis - preflight.checkedAtEpochMillis
    val receiptAge = state.preflightReceivedAtElapsedRealtime?.let { nowElapsedMillis - it }
    if (age !in 0L..PreflightMaxAgeMillis || receiptAge == null || receiptAge !in 0L..PreflightMaxAgeMillis)
        return "시작 전 점검이 만료되었습니다. 다시 점검하세요."
    return null
}

internal fun sameTicketIdentity(first: PreparationDraft?, second: PreparationDraft): Boolean = first != null &&
    first.serverProfileId == second.serverProfileId && first.ticketRowId == second.ticketRowId &&
    first.ticketRevision == second.ticketRevision && first.credentialRevision == second.credentialRevision &&
    first.geometryRevision == second.geometryRevision && first.geometryHash == second.geometryHash

internal fun SurveyRunUiState.manualDraft(): PreparationDraft = PreparationDraft(
    deviceId = deviceId.orEmpty(), pipelineId = pipelineId,
    project = selectedProject?.let { PreparationSelection.Existing(it.surveyProjectId, it.revision) },
    section = selectedSection?.let { PreparationSelection.Existing(it.surveySectionId, it.revision) }
)

internal fun stopTargetMatches(target: StopTarget, state: SurveyRunUiState): Boolean =
    state.deviceId.equals(target.deviceId, true) && state.pipelineId == target.pipelineId &&
        state.online && state.controlAvailable && !state.restoringLocalState && state.runStateConfirmed &&
        state.pendingStart == null && state.unconfirmedRunId == null &&
        state.activeRun?.let { it.active && it.runId == target.runId && it.pipelineId == target.pipelineId &&
            it.deviceId.equals(target.deviceId, true) } == true
