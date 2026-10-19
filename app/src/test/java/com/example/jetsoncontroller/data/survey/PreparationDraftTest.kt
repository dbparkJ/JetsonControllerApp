package com.example.jetsoncontroller.data.survey

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.model.PreparationDraft
import com.example.jetsoncontroller.model.PreparationSelection
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.ui.survey.toPreparationDraft
import com.example.jetsoncontroller.ui.tasks.TicketPreparation
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreparationDraftTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val draft = PreparationDraft(
        deviceId = "GEO-X-1", serverProfileId = "profile", ticketRowId = "row", ticketRevision = "r4",
        project = PreparationSelection.CreateFromTicket("수원시 도로대장"),
        section = PreparationSelection.CreateFromTicket("신월4거리"),
        geometryRevision = "zone-r2", geometryHash = "zone-hash", updatedAt = 42, credentialRevision = "credentials"
    ).withCreationRequestIds()

    @Test
    fun `every choice and pending command identity round trips without losing uncertainty`() {
        val choices = listOf(null, PreparationSelection.Existing("id", 3),
            PreparationSelection.CreateFromTicket("구간"), PreparationSelection.CreateNew("직접 만든 구간"))
        for (project in choices) for (section in choices) {
            val prepared = draft.copy(project = project, section = section, pipelineId = "pipe")
            val pending = PendingContextualStart("pipe", "project", 2, "section", 3, "policy", "preflight", "start-id",
                deviceId = prepared.deviceId, serverProfileId = "profile", ticketRowId = "row", ticketRevision = "r4",
                geometryRevision = "zone-r2", geometryHash = "zone-hash",
                projectRequestId = prepared.projectRequestId, sectionRequestId = prepared.sectionRequestId)
            val state = SurveyRunLocalState(selection = SurveySelection("project", 2, "section", 3),
                pendingStart = pending, lastPipelineId = "pipe", lastRunId = "run", acknowledgedRunId = "older",
                preparationDraft = prepared, unconfirmedRunId = "uncertain-run", acceptedStarts = mapOf("older-run" to pending))
            assertEquals(state, decode(encode(state)))
        }
        val old = """{"version":1,"pendingStart":{"pipelineId":"pipe","surveyProjectId":"project","surveyProjectRevision":1,"surveySectionId":"section","surveySectionRevision":2,"policyRevision":"policy","preflightId":"preflight","clientRequestId":"same-request"}}"""
        assertEquals("same-request", decode(old).pendingStart?.clientRequestId)
        assertNull(decode(old).preparationDraft)
    }

    @Test
    fun `disk restore isolates devices and accepted start clears only its draft`() = runTest {
        val file = File(temporaryFolder.root, "survey.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var store = SurveySelectionStore(PreferenceDataStoreFactory.create(scope = scope) { file })
            store.savePreparationDraft("GEO-X-1", draft)
            val other = draft.copy(deviceId = "GEO-X-2", ticketRowId = "other", pipelineId = "other-pipe")
            store.savePreparationDraft("GEO-X-2", other)
            val pending = PendingContextualStart("pipe", "project", 1, "section", 1, "policy", "preflight", "request")
            store.savePendingStart("geo-x-1", pending)
            store.saveUnconfirmedRun("geo-x-1", "uncertain")
            scope.coroutineContext[Job]!!.cancelAndJoin()

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = SurveySelectionStore(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(draft, store.state("geo-x-1").first().preparationDraft)
            assertEquals(other, store.state("GEO-X-2").first().preparationDraft)
            assertEquals(pending, store.state("GEO-X-1").first().pendingStart)
            assertEquals("uncertain", store.state("GEO-X-1").first().unconfirmedRunId)
            store.saveAcceptedRun("GEO-X-1", "pipe", "accepted-run")
            val accepted = store.state("GEO-X-1").first()
            assertNull(accepted.preparationDraft)
            assertNull(accepted.pendingStart)
            assertNull(accepted.unconfirmedRunId)
            assertEquals(pending, accepted.acceptedStarts["accepted-run"])
            assertEquals("accepted-run", accepted.lastRunId)
            assertEquals(other, store.state("GEO-X-2").first().preparationDraft)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test
    fun `request ids survive task choice retry and process death but change with the creation decision`() {
        var calls = 0
        val first = draft.copy(projectRequestId = null, sectionRequestId = null).withCreationRequestIds { "request-${++calls}" }
        assertEquals(2, calls)
        val restored = requireNotNull(decode(encode(SurveyRunLocalState(preparationDraft = first.copy(pipelineId = "pipe"))))
            .preparationDraft).withCreationRequestIds { error("must reuse persisted ids") }
        assertEquals(first.projectRequestId, restored.projectRequestId)
        assertEquals(first.sectionRequestId, restored.sectionRequestId)
        assertEquals(restored, restored.chooseSection(restored.section, 43))
        val renamed = restored.chooseSection(PreparationSelection.CreateNew("다른 구간"), 44)
        assertEquals(restored.projectRequestId, renamed.projectRequestId)
        assertNotEquals(restored.sectionRequestId, renamed.sectionRequestId)
        val changedProject = restored.chooseProject(PreparationSelection.CreateNew("다른 조사"), 45)
        assertNotEquals(restored.projectRequestId, changedProject.projectRequestId)
        assertNull(changedProject.section)
        assertNull(changedProject.sectionRequestId)
    }

    @Test
    fun `Phase 1 import preserves ticket through task choice and replaces it for a different ticket`() {
        val ticket = ServerTicket("row", "신월4거리", project = "수원시 도로대장", revision = "r4")
        val preparation = TicketPreparation("profile", "credentials", "GEO-X-1", ticket)
        val imported = preparation.toPreparationDraft(now = 1)
        val chosen = preparation.copy(pipelineId = "pipe").toPreparationDraft(previous = imported, now = 2)
        assertEquals(imported.ticketRowId, chosen.ticketRowId)
        assertEquals(imported.ticketRevision, chosen.ticketRevision)
        assertEquals(imported.projectRequestId, chosen.projectRequestId)
        assertEquals(imported.sectionRequestId, chosen.sectionRequestId)
        assertEquals("pipe", chosen.pipelineId)
        val replacement = preparation.copy(ticket = ticket.copy(rowId = "another-row"))
            .toPreparationDraft(previous = chosen, now = 3)
        assertEquals("another-row", replacement.ticketRowId)
        assertNotEquals(chosen.projectRequestId, replacement.projectRequestId)
        val missingLinked = preparation.copy(surveyProjectId = "missing-project", surveySectionId = "missing-section")
            .toPreparationDraft(now = 4)
        assertNull(missingLinked.project)
        assertNull(missingLinked.section)
        val project = SurveyProject("project", "수원시 도로대장", 3, "created", "updated")
        val section = SurveySection("section", "project", "신월4거리", 4, "created", "updated")
        val existing = preparation.toPreparationDraft(project = project, section = section, now = 5)
        assertEquals(PreparationSelection.Existing("project", 3), existing.project)
        assertEquals(PreparationSelection.Existing("section", 4), existing.section)
        assertNull(existing.projectRequestId)
    }

    @Test
    fun `device keys are locale independent and damaged draft cannot clear pending start`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(deviceScope(" DEVICE-I "), deviceScope("device-i"))
            assertNotEquals(deviceScope("GEO-X-1"), deviceScope("GEO-X-2"))
        } finally { Locale.setDefault(previous) }
        val state = SurveyRunLocalState(preparationDraft = draft,
            pendingStart = PendingContextualStart("pipe", "p", 1, "s", 1, "policy", "check", "request"))
        val damaged = encode(state).replace("\"kind\":\"ticket\"", "\"kind\":\"unsupported\"")
        assertNull(decode(damaged).preparationDraft)
        assertEquals(state.pendingStart, decode(damaged).pendingStart)
        assertTrue(draft.projectRequestId != null)
        assertFalse(draft.sectionRequestId.isNullOrBlank())
    }
}
