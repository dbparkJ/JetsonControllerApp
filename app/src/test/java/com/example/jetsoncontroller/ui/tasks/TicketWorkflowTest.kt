package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.PendingTicketReport
import com.example.jetsoncontroller.data.server.PendingTicketSelection
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketIntegrationState
import com.example.jetsoncontroller.data.server.ServerTicketReportState
import com.example.jetsoncontroller.data.server.ServerTicketState
import com.example.jetsoncontroller.data.server.ServerTicketsResponse
import com.example.jetsoncontroller.data.server.TicketReportStore
import com.example.jetsoncontroller.data.server.ticketReportKey
import com.example.jetsoncontroller.data.server.ticketReportResponseIsTerminal
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TicketWorkflowTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun ticketDtosParseKnownAndUnknownStates() {
        val parsed = Gson().fromJson(
            """{
              "tickets":[{
                "rowId":"row-1","title":"북측","project":"농장","unit":"2호기",
                "assignees":["홍길동"],"dueDate":"2026-10-31",
                "status":{"name":"진행 중","category":"IN_PROGRESS"},
                "revision":"r4","updatedAt":"2026-10-18T00:00:00Z","runId":"run-1",
                "report":{"state":"CONFLICT","code":"HUMAN_CHANGED"}
              }],"units":["1호기","2호기"],"deviceUnit":"2호기",
              "syncedAt":"2026-10-18T00:00:00Z","integration":{"state":"ACTIVE"}
            }""", ServerTicketsResponse::class.java
        )
        assertEquals("row-1", parsed.tickets.single().rowId)
        assertEquals(ServerTicketState.IN_PROGRESS, parsed.tickets.single().status?.normalizedCategory)
        assertEquals(ServerTicketReportState.CONFLICT, parsed.tickets.single().report?.normalizedState)
        assertEquals(ServerTicketIntegrationState.ACTIVE, parsed.integration?.normalizedState)

        val unknown = Gson().fromJson(
            """{"tickets":[{"rowId":"r","title":"t","status":{"category":"FUTURE"},"report":{"state":"FUTURE"}}],"integration":{"state":"FUTURE"}}""",
            ServerTicketsResponse::class.java
        )
        assertEquals(ServerTicketState.UNKNOWN, unknown.tickets.single().status?.normalizedCategory)
        assertEquals(ServerTicketReportState.UNKNOWN, unknown.tickets.single().report?.normalizedState)
        assertEquals(ServerTicketIntegrationState.UNKNOWN, unknown.integration?.normalizedState)
    }

    @Test
    fun defaultUnitAndUnitFilterFollowDeviceAssignment() {
        val tickets = listOf(ticket("a", "1호기"), ticket("b", "2호기"))
        assertEquals("2호기", defaultTicketUnit("2호기", listOf("1호기", "2호기")))
        assertNull(defaultTicketUnit(null, listOf("1호기")))
        assertEquals(listOf("b"), filterTicketsByUnit(tickets, "2호기").map { it.rowId })
        assertEquals(tickets, filterTicketsByUnit(tickets, null))
    }

    @Test
    fun startAndCompletionActionsRespectRoleTicketAndReceipt() {
        val open = ticket("open", "1호기")
        assertTrue(ticketStartAvailability("OPERATOR", open).enabled)
        assertFalse(ticketStartAvailability("VIEWER", open).enabled)
        assertEquals("수집 시작은 작업자 권한이 필요합니다.", ticketStartAvailability("VIEWER", open).reason)
        assertFalse(ticketStartAvailability("OPERATOR", ticket("done", "1호기", "COMPLETED")).enabled)

        assertFalse(ticketCompletionPresentation(false, true, "OPERATOR").showButton)
        val awaitingReceipt = ticketCompletionPresentation(true, false, "OPERATOR")
        assertFalse(awaitingReceipt.showButton)
        assertEquals("서버 수신 확인 뒤에 완료를 보고할 수 있습니다. 티켓은 '진행 중'으로 둡니다.", awaitingReceipt.message)
        assertTrue(ticketCompletionPresentation(true, true, "OPERATOR").enabled)
        assertTrue(ticketCompletionPresentation(true, true, "VIEWER").showButton)
        assertFalse(ticketCompletionPresentation(true, true, "VIEWER").enabled)
        assertFalse(ticketCompletionPresentation(true, true, "OPERATOR", conflicted = true).showButton)
        assertFalse(ticketCompletionPresentation(true, true, "OPERATOR", startRejected = true).showButton)
    }

    @Test
    fun humanChangedConflictUsesRequiredCopy() {
        val conflicted = ticket("row", "1호기").copy(
            report = com.example.jetsoncontroller.data.server.ServerTicketReport("CONFLICT", "HUMAN_CHANGED")
        )
        assertEquals("다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요.", ticketConflictCopy(conflicted))
        assertEquals("다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요.", ticketReportStateLabel(conflicted))
    }

    @Test
    fun pendingReportsPersistDedupeAndApplyResponseRemovalRules() {
        val file = temporaryFolder.newFile("ticket-reports.json")
        val store = TicketReportStore(file)
        val report = pending("run-1", false, "first")
        store.enqueue(report)
        store.enqueue(report.copy(createdAt = "second"))
        assertEquals("first", TicketReportStore(file).pendingReports().single().createdAt)

        store.applyResponse(report.key, 500)
        store.applyResponse(report.key, null)
        assertEquals(1, store.pendingReports().size)
        store.applyResponse(report.key, 409, "HUMAN_CHANGED")
        assertTrue(TicketReportStore(file).pendingReports().isEmpty())
        assertTrue(store.isSettled(report.key))
        assertEquals("HUMAN_CHANGED", store.errorCode(report.key))
        store.enqueue(report)
        assertTrue(store.pendingReports().isEmpty())

        val completed = pending("run-2", true, "now")
        store.enqueue(completed)
        store.applyResponse(completed.key, 202)
        assertTrue(store.pendingReports().isEmpty())
        assertEquals(202, store.responseCode(completed.key) ?: 0)
        store.markConflict("row-conflict")
        assertTrue(TicketReportStore(file).isConflict("row-conflict"))
        assertTrue(ticketReportResponseIsTerminal(202))
        assertTrue(ticketReportResponseIsTerminal(409))
        assertFalse(ticketReportResponseIsTerminal(500))
        assertFalse(ticketReportResponseIsTerminal(null))
    }

    @Test
    fun runLinkBindsOnlyToTheArmedStartRequestAndPersists() {
        val file = temporaryFolder.newFile("ticket-links.json")
        val store = TicketReportStore(file)
        store.savePendingSelection(
            PendingTicketSelection("profile", "row", "ticket", "project", "2호기", "device", "pipeline")
        )
        assertNull(store.bindPendingSelection("run", "device", "pipeline", null))
        store.armPendingSelection("device", "pipeline", "client-1")
        assertNull(store.bindPendingSelection("run", "device", "pipeline", "client-other"))
        val link = store.bindPendingSelection("run", "device", "pipeline", "client-1")
        assertEquals("row", link?.rowId)
        assertEquals("row", TicketReportStore(file).link("run")?.rowId)
        assertNull(store.pendingSelection("device", "pipeline"))
    }

    private fun ticket(rowId: String, unit: String, category: String = "OPEN") = ServerTicket(
        rowId = rowId,
        title = rowId,
        unit = unit,
        status = com.example.jetsoncontroller.data.server.ServerTicketStatus("할 일", category)
    )

    private fun pending(runId: String, completed: Boolean, createdAt: String) = PendingTicketReport(
        key = ticketReportKey(runId, completed), rowId = "row-$runId", runId = runId,
        deviceId = "device-1", profileId = "profile-1", createdAt = createdAt
    )
}
