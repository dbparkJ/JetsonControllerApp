package com.example.jetsoncontroller.ui.survey

import com.example.jetsoncontroller.data.survey.withCreationRequestIds
import com.example.jetsoncontroller.model.PreparationDraft
import com.example.jetsoncontroller.model.PreparationSelection
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.ui.tasks.TicketPreparation

/** Cut-over seam: import the Phase 1 snapshot, then let the per-device store own the draft. */
internal fun TicketPreparation.toPreparationDraft(
    previous: PreparationDraft? = null,
    project: SurveyProject? = null,
    section: SurveySection? = null,
    geometryRevision: String? = null,
    geometryHash: String? = null,
    now: Long
): PreparationDraft {
    val sameTicket = previous?.takeIf {
        it.deviceId.equals(deviceId, true) && it.serverProfileId == profileId &&
            it.credentialRevision == credentialRevision && it.ticketRowId == ticket.rowId &&
            it.ticketRevision == ticket.revision && it.geometryRevision == geometryRevision && it.geometryHash == geometryHash
    }
    if (sameTicket != null) return sameTicket.copy(pipelineId = pipelineId ?: sameTicket.pipelineId, updatedAt = now)
    val resolvedProject = project?.takeIf {
        if (surveyProjectId != null) it.surveyProjectId == surveyProjectId
        else ticket.project.isNullOrBlank() || ticket.project == it.label
    }
    val resolvedSection = section?.takeIf {
        resolvedProject != null && it.surveyProjectId == resolvedProject.surveyProjectId &&
            (if (surveySectionId != null) it.surveySectionId == surveySectionId else it.label == ticket.title)
    }
    return PreparationDraft(
        deviceId = deviceId, serverProfileId = profileId, credentialRevision = credentialRevision,
        ticketRowId = ticket.rowId, ticketRevision = ticket.revision, pipelineId = pipelineId,
        project = when {
            resolvedProject != null -> PreparationSelection.Existing(resolvedProject.surveyProjectId, resolvedProject.revision)
            surveyProjectId != null -> null // A missing linked identity must be refreshed, never created by label.
            else -> ticket.project?.takeIf(String::isNotBlank)?.let { PreparationSelection.CreateFromTicket(it) }
        },
        section = when {
            resolvedSection != null -> PreparationSelection.Existing(resolvedSection.surveySectionId, resolvedSection.revision)
            surveySectionId != null -> null
            else -> PreparationSelection.CreateFromTicket(ticket.title)
        },
        geometryRevision = geometryRevision, geometryHash = geometryHash, updatedAt = now
    ).withCreationRequestIds()
}
