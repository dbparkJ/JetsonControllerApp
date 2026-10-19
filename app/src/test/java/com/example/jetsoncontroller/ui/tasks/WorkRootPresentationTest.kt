package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketStatus
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.model.CameraSensorStatus
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MetricValidity
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.ui.model.NextActionCommand
import com.example.jetsoncontroller.ui.model.OperationState
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WorkRootPresentationTest {
    private val now = Instant.parse("2026-10-20T07:36:00Z")
    private fun input(survey: SurveyRunUiState = SurveyRunUiState(runStateConfirmed = true)) = TasksHomeInput(
        "GEO-X-1", true, survey, emptyList(), emptyList(), emptyList(), false, now = now
    )

    @Test fun `pending start keeps operation while offline and offers both remedies immediately`() {
        val pending = PendingContextualStart("pipe", "project", 1, "section", 1, "policy", "check", "request")
        val offline = input(SurveyRunUiState(pendingStart = pending)).copy(connected = false)
        val focus = workNextAction(offline)
        assertEquals(OperationState.START_PENDING, focus.operation)
        assertEquals("GEO-X-1 수집 시작 결과를 확인해야 합니다", focus.title)
        assertEquals(NextActionCommand.RECONNECT, focus.primary)
        assertEquals(NextActionCommand.CONNECTION_HELP, focus.secondary)
        assertEquals(OperationState.START_PENDING, workNextAction(offline.copy(connected = true)).operation)
        assertEquals(NextActionCommand.CHECK_START, workNextAction(offline.copy(connected = true)).primary)
    }

    @Test fun `unconfirmed run and multiple runs block idle presentation`() {
        assertEquals(OperationState.RUN_UNCONFIRMED,
            workNextAction(input(SurveyRunUiState(unconfirmedRunId = "run")).copy(connected = false)).operation)
        assertEquals(OperationState.MULTIPLE_RUNS,
            workNextAction(input(SurveyRunUiState(runStateConfirmed = true, observedActiveRunCount = 2))).operation)
        assertEquals(OperationState.IDLE, workNextAction(input()).operation)
    }

    @Test fun `transport remedies preserve pending operation for permission and rejected credentials`() {
        val pending = PendingContextualStart("pipe", "project", 1, "section", 1, "policy", "check", "request")
        val input = input(SurveyRunUiState(pendingStart = pending)).copy(connected = false)
        val permission = workNextAction(input.copy(permissionMissing = true))
        assertEquals(OperationState.START_PENDING, permission.operation)
        assertEquals(NextActionCommand.ALLOW_PERMISSION, permission.primary)
        val credentials = workNextAction(input.copy(credentialsRejected = true))
        assertEquals(OperationState.START_PENDING, credentials.operation)
        assertEquals(NextActionCommand.REGISTER_AGAIN, credentials.primary)
        assertTrue(credentials.transportRemedy!!.detail!!.startsWith("GEO-X-1"))
    }

    @Test fun `foreign receipt never promotes the root to completion report`() {
        val receipt = UploadVerification("job", "target", "session", "source", "VERIFIED", true, true)
        val job = UploadJob("job", "data", "source", "target", UploadJobState.COMPLETED,
            100, 100, 1, 1, null, null, sourceName = "source", remoteSessionId = "session", verification = receipt)
        assertNotNull(workUploadWithMatchingReceipt(job).verification)
        listOf(receipt.copy(jobId = "other"), receipt.copy(targetId = "other"),
            receipt.copy(remoteSessionId = "other"), receipt.copy(sourceName = "other")).forEach {
            assertNull(workUploadWithMatchingReceipt(job.copy(verification = it)).verification)
        }
        assertNull(workUploadWithMatchingReceipt(job.copy(remoteSessionId = null)).verification)
    }

    @Test fun `sensor exception remains visible at zero taps despite fresh status`() {
        val status = JetsonStatus(collectedAtEpochMillis = now.toEpochMilli(), sensorTelemetryAvailable = true,
            sensorTelemetryFresh = true, cameraSensor = CameraSensorStatus(configured = true,
                connected = true, active = true, lastFrameAtEpochMillis = now.minusSeconds(60).toEpochMilli()))
        val strip = workDeviceStrip(input(SurveyRunUiState(runStateConfirmed = false)).copy(status = status))
        assertEquals("카메라 지연", strip.firstAttention)
        assertFalse(strip.hiddenByFocus)
    }

    @Test fun `new device checks never reuse the previous devices facts`() {
        val state = buildTasksHomeUiState(input().copy(checkingDevice = true, status = null))
        val strip = requireNotNull(state.deviceStrip)
        assertNull(state.focus)
        assertEquals("확인 중", strip.connectionWord)
        assertTrue(strip.facts.isEmpty())
        assertFalse(strip.hiddenByFocus)
    }

    @Test fun `valid zero free storage is preserved and invalid metrics stay unknown`() {
        val status = JetsonStatus(storageTotalBytes = 1000, storageAvailableBytes = 0,
            metricValidity = mapOf("storageAvailableBytes" to MetricValidity("valid")))
        assertTrue(workDeviceStrip(input().copy(status = status)).facts.any { it.contains("0") })
        val invalid = status.copy(metricValidity = mapOf("storageAvailableBytes" to MetricValidity("unavailable")))
        assertTrue(workDeviceStrip(input().copy(status = invalid)).facts.isEmpty())
    }

    @Test fun `queue filters keep unassigned and order in progress blocked todo with completed separate`() {
        fun ticket(id: String, unit: String?, category: String, blocked: Boolean = false) = ServerTicket(id, id,
            unit = unit, status = ServerTicketStatus(if (blocked) "막힘" else "할 일", category,
                systemKey = if (blocked) "BLOCKED" else null))
        val tickets = listOf(ticket("done", "1호기", "COMPLETED"), ticket("todo", "1호기", "OPEN"),
            ticket("unassigned", " ", "OPEN"), ticket("blocked", "1호기", "IN_PROGRESS", true),
            ticket("other", "2호기", "IN_PROGRESS"), ticket("active", "1호기", "IN_PROGRESS"))
        val queue = workTicketQueue(tickets, "1호기")
        assertEquals(listOf("active", "blocked", "todo", "unassigned"), queue.open.map { it.rowId })
        assertEquals(listOf("done"), queue.completed.map { it.rowId })
        assertEquals(5, workTicketQueue(tickets, null).open.size)
    }

    @Test fun `offline cached list keeps rows and announces last sync with refresh alternative`() {
        val tickets = listOf(ServerTicket("row", "구간", unit = null))
        val state = ServerTicketUiState(tickets = tickets, isCached = true,
            lastSyncedAt = "2026-10-19T07:36:00Z", selectedUnit = "1호기")
        assertEquals(tickets, workTicketQueue(state.tickets, state.selectedUnit).open)
        assertEquals("마지막 동기화 10월 19일 16:36", ticketSyncLabel(state, now))
        assertTrue(ticketRefreshVisible(state, now))
        assertFalse(ticketRefreshVisible(state.copy(isCached = false, lastSyncedAt = now.toString()), now))
    }
}
