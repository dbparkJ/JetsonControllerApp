package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.TicketReportStore
import com.example.jetsoncontroller.model.SurveyContextSnapshot
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.model.TaskRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TicketPreparationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val ticket = ServerTicket("row", "구간", project = "조사", revision = "r4", runId = "previous-run")
    private val project = SurveyProject("project", "새 조사 이름", 3, "created", "updated")
    private val section = SurveySection("section", "project", "새 구간 이름", 5, "created", "updated")
    private val context = SurveyContextSnapshot(deviceId = "device", surveyProjectId = "project",
        surveyProjectLabel = "이전 조사 이름", surveyProjectRevision = 2, surveySectionId = "section",
        surveySectionLabel = "이전 구간 이름", surveySectionRevision = 4, capturedAt = "captured")

    @Test
    fun `ambiguous choice survives recreation with row and revision then binds exact start`() {
        val file = temporaryFolder.newFile("preparation.json")
        val prepared = ticketPreparation("profile", "credentials", "device", ticket, null, emptyList())
        TicketPreparationStore(file).save(prepared)
        val restored = requireNotNull(TicketPreparationStore(file).read())
        assertEquals("row", restored.ticket.rowId)
        assertEquals("r4", restored.ticket.revision)
        assertNull(restored.pipelineId)
        assertNull(restored.pendingSelection())

        val chosen = restored.copy(pipelineId = "pipe-two")
        TicketPreparationStore(file).save(chosen)
        val afterChoice = requireNotNull(TicketPreparationStore(file).read())
        val reportsFile = temporaryFolder.newFile("reports.json")
        val reports = TicketReportStore(reportsFile)
        reports.savePendingSelection(requireNotNull(afterChoice.pendingSelection()))
        reports.armPendingSelection("device", "pipe-two", "request")
        val recreatedReports = TicketReportStore(reportsFile)
        assertNull(recreatedReports.bindPendingSelection("run", "device", "other-pipe", "request"))
        assertNull(recreatedReports.bindPendingSelection("run", "device", "pipe-two", "other-request"))
        val link = recreatedReports.bindPendingSelection("run", "device", "pipe-two", "request")
        assertEquals("row", link?.rowId)
        assertEquals("profile", link?.profileId)
        assertEquals("pipe-two", link?.pipelineId)
        assertEquals("r4", afterChoice.ticket.revision)
        TicketPreparationStore(file).save(null)
        assertNull(TicketPreparationStore(file).read())
    }

    @Test
    fun `preparation rejects changed ticket profile device and credential revision`() {
        val prepared = ticketPreparation("profile", "credentials", "device", ticket, null, emptyList())
        assertTrue(ticketPreparationMatches(prepared, "profile", "DEVICE", "credentials", ticket))
        assertFalse(ticketPreparationMatches(prepared, "profile", "device", "credentials", ticket.copy(revision = "r5")))
        assertFalse(ticketPreparationMatches(prepared, "profile", "device", "credentials", ticket.copy(rowId = "other")))
        assertFalse(ticketPreparationMatches(prepared, "profile", "device", "credentials", null))
        assertFalse(ticketPreparationMatches(prepared, "other", "device", "credentials", ticket))
        assertFalse(ticketPreparationMatches(prepared, "profile", "other-device", "credentials", ticket))
        assertFalse(ticketPreparationMatches(prepared, "profile", "device", "new-credentials", ticket))
    }

    @Test
    fun `linked run resolves survey identities despite renamed and duplicate titles`() {
        val history = TaskRun("history", "pipe", "이전 수집", "log", "started",
            runId = "previous-run", deviceId = "device", contextSnapshot = context)
        val prepared = ticketPreparation("profile", "credentials", "device", ticket, "pipe", listOf(history))
        assertEquals("project", prepared.surveyProjectId)
        assertEquals("section", prepared.surveySectionId)
        assertEquals(project, ticketPreparationProject(prepared, listOf(project, project.copy(surveyProjectId = "other"))))
        assertEquals(section, ticketPreparationSection(prepared, listOf(section, section.copy(surveySectionId = "other"))))
        assertTrue(ticketPreparationContextMatches(prepared, project, section))
        assertFalse(ticketPreparationContextMatches(prepared, project, section.copy(surveySectionId = "other")))
        assertNull(ticketPreparationSection(prepared, listOf(section.copy(surveySectionId = "other", label = ticket.title))))
    }

    @Test
    fun `missing identity uses unique title fallback and never borrows another device context`() {
        val wrongDevice = TaskRun("history", "pipe", "이전 수집", "log", "started",
            runId = "previous-run", deviceId = "other-device", contextSnapshot = context)
        val prepared = ticketPreparation("profile", "credentials", "device", ticket, null, listOf(wrongDevice))
        assertNull(prepared.surveyProjectId)
        assertNull(prepared.surveySectionId)
        val titledProject = project.copy(label = "조사")
        val titledSection = section.copy(label = "구간")
        assertEquals(titledProject, ticketPreparationProject(prepared, listOf(titledProject)))
        assertEquals(titledSection, ticketPreparationSection(prepared, listOf(titledSection)))
        assertTrue(ticketPreparationContextMatches(prepared, titledProject, titledSection))
        assertFalse(ticketPreparationContextMatches(prepared, project, section))
        assertFalse(ticketPreparationContextMatches(prepared, titledProject, titledSection.copy(label = "다른 구간")))
        assertNull(ticketPreparationSection(prepared, listOf(titledSection, titledSection.copy(surveySectionId = "duplicate"))))
        assertNull(ticketPreparationProject(prepared, listOf(titledProject, titledProject.copy(surveyProjectId = "duplicate"))))
    }
}
