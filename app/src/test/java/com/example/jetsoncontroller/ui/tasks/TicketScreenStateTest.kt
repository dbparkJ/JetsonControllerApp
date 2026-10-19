package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketReport
import com.example.jetsoncontroller.model.PreparationSelection
import com.example.jetsoncontroller.ui.components.geo.GeoStartPhase
import com.example.jetsoncontroller.ui.components.geo.startSheetRules
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TicketScreenStateTest {
    private val ticket = ServerTicket("row", "신월4거리 북측 보조 구간", project = "수원시 도로대장",
        revision = "r1", geometryRevision = "g1", geometryHash = "hash")

    @Test fun `start confirmation rejects any change to its immutable target`() {
        val target = WorkTicketTarget("device", "profile", "row", "r1", "pipe", "g1", "hash")
        assertTrue(target.matches("DEVICE", "profile", ticket, "pipe"))
        assertFalse(target.matches("other", "profile", ticket, "pipe"))
        assertFalse(target.matches("device", "other", ticket, "pipe"))
        assertFalse(target.matches("device", "profile", ticket.copy(rowId = "other"), "pipe"))
        assertFalse(target.matches("device", "profile", ticket.copy(revision = "r2"), "pipe"))
        assertFalse(target.matches("device", "profile", ticket, "other"))
        assertFalse(target.matches("device", "profile", ticket.copy(geometryRevision = "g2"), "pipe"))
        assertFalse(target.matches("device", "profile", ticket.copy(geometryHash = "other"), "pipe"))
        assertFalse(target.matches("device", "profile", null, "pipe"))
    }

    @Test fun `task choice and recreation preserve ticket geometry and creation request identities`() {
        val initial = ticketDraft(ticket, "device", "profile", "credentials", null, null, 10)
        assertEquals(PreparationSelection.CreateFromTicket(ticket.title), initial.section)
        assertEquals("g1", initial.geometryRevision)
        assertEquals("hash", initial.geometryHash)
        val persisted = initial.copy(projectRequestId = "project-request", sectionRequestId = "section-request")
        val chosen = ticketDraft(ticket, "device", "profile", "credentials", "pipe", persisted, 20)
        assertEquals("row", chosen.ticketRowId)
        assertEquals("project-request", chosen.projectRequestId)
        assertEquals("section-request", chosen.sectionRequestId)
        assertEquals(chosen, ticketDraft(ticket, "device", "profile", "credentials", "pipe", chosen, 30))
        val other = ticketDraft(ticket, "other-device", "profile", "credentials", "pipe", chosen, 30)
        assertNull(other.projectRequestId)
        assertNull(other.sectionRequestId)
        assertEquals("device", chosen.deviceId)
    }

    @Test fun `ticket or credential revision change invalidates the old draft`() {
        val old = ticketDraft(ticket, "device", "profile", "credentials", "pipe", null, 10)
            .copy(projectRequestId = "project", sectionRequestId = "section")
        assertNull(ticketDraft(ticket.copy(revision = "r2"), "device", "profile", "credentials", "pipe", old, 20).projectRequestId)
        assertNull(ticketDraft(ticket, "device", "profile", "new-credentials", "pipe", old, 20).sectionRequestId)
    }

    @Test fun `passive offline readiness makes no claims from cached sensor values`() {
        val state = ticketPassiveReadiness("GEO-X-1", null, false, "오프라인", Instant.EPOCH)
        assertEquals("미확인", state.storage)
        assertEquals("위치 미확인", state.position)
        assertEquals("미확인", state.camera)
        assertFalse(state.controlAvailable)
        assertEquals("연결 방법", state.remedyLabel)
    }

    @Test fun `unknown reports and backend codes never leak into operator words`() {
        val report = ticket.copy(report = ServerTicketReport("FUTURE", "INTERNAL_ERROR_123"))
        assertEquals("보고 실패", ticketReportStateLabel(report))
        assertFalse(ticketReportStateLabel(report).contains("INTERNAL"))
        assertEquals("보고 전", ticketReportStateLabel(ticket))
    }

    @Test fun `start sheet checks block confirmation until a fresh successful response`() {
        val checking = ticketStartSheet(ticket, "GEO-X-1", SurveyRunUiState(), true, false, null, {}, {})
        assertEquals(GeoStartPhase.CHECKING, checking.phase)
        assertTrue(startSheetRules(checking).busy)
        assertFalse(startSheetRules(checking).canStart)
        val failed = ticketStartSheet(ticket, "GEO-X-1", SurveyRunUiState(), false, false,
            "수집 구간을 다시 확인하세요.", {}, {})
        assertFalse(startSheetRules(failed).canStart)
        assertTrue(startSheetRules(failed).showRecheck)
        assertNotNull(failed.checks.single().remedy)
        val changed = ticketStartSheet(ticket, "GEO-X-1", SurveyRunUiState(), false, true, null, {}, {})
        assertEquals("GEO& Work에서 이 구간이 바뀌었습니다. 다시 확인하세요.", startSheetRules(changed).blockingReason)
    }
}
