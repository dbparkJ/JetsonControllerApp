package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.data.server.PendingTicketReport
import com.example.jetsoncontroller.data.server.ServerReceipt
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.TicketReportDeliveryState
import com.example.jetsoncontroller.data.server.TicketReportStore
import com.example.jetsoncontroller.data.server.TicketReportTarget
import com.example.jetsoncontroller.data.server.ticketReportKey
import com.example.jetsoncontroller.data.server.ticketReportTargetMatches
import com.example.jetsoncontroller.model.UploadContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportFlowTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val context = UploadContext(
        surveyProjectId = "survey-project",
        surveySectionId = "section",
        runId = "run-7",
        deviceId = "device-1",
        pipelineId = "pipeline",
        sourceRevision = "source-r2",
        configSha256 = "config-hash",
        outputId = "output-7",
        createdAt = "2026-10-19T07:30:00Z"
    )
    private val target = TicketReportTarget(
        profileId = "profile",
        rowId = "ticket-row",
        ticketRevision = "ticket-r4",
        runId = "run-7",
        receiptId = "receipt-session"
    )
    private val report = PendingTicketReport(
        key = ticketReportKey(target.runId, completed = true),
        rowId = target.rowId,
        runId = target.runId,
        deviceId = "device-1",
        profileId = target.profileId,
        createdAt = "2026-10-19T07:37:00Z",
        ticketRevision = target.ticketRevision,
        receiptId = target.receiptId
    )

    @Test
    fun `failed outbox report survives restart and retry keeps its target tuple`() {
        val file = temporaryFolder.newFile("ticket-reports.json")
        val store = TicketReportStore(file)
        store.enqueue(report)
        assertEquals(TicketReportDeliveryState.PENDING, store.deliveryState(report.key))

        assertTrue(store.markFailed(report.key))
        val restored = TicketReportStore(file)
        assertEquals(TicketReportDeliveryState.FAILED, restored.deliveryState(report.key))
        assertEquals(target, restored.pendingReport(report.key)?.target())
        assertTrue(restored.markRetrying(report.key))
        assertEquals(TicketReportDeliveryState.PENDING, restored.deliveryState(report.key))

        restored.applyResponse(report.key, 202)
        assertEquals(TicketReportDeliveryState.SENT, restored.deliveryState(report.key))
        assertNull(restored.pendingReport(report.key))
    }

    @Test
    fun `changed ticket revision rejects queued report instead of retargeting it`() {
        val ticket = ServerTicket(target.rowId, "Ticket", revision = "ticket-r5")
        val receipt = receipt()
        assertFalse(ticketReportTargetMatches(
            target = target,
            profileId = target.profileId,
            ticket = ticket,
            runId = target.runId,
            deviceId = "device-1",
            receiptId = target.receiptId,
            receipt = receipt,
            requireReceipt = true
        ))

        val store = TicketReportStore(temporaryFolder.newFile("changed-target.json"))
        store.enqueue(report)
        store.applyResponse(report.key, 409, "REPORT_TARGET_CHANGED")
        assertEquals(TicketReportDeliveryState.REJECTED, store.deliveryState(report.key))
        assertTrue(store.pendingReports().isEmpty())
    }

    private fun receipt() = ServerReceipt(
        serverEnvironment = "production",
        projectId = "server-project",
        sessionId = "receipt-session",
        clientJobId = "client-job",
        sourceName = "run-output",
        folderName = "run-output",
        state = "COMPLETED",
        totalBytes = 25,
        fileCount = 2,
        contentSha256 = "contents-hash",
        completedAt = "2026-10-19T07:35:00Z",
        matched = true,
        verifiedAt = "2026-10-19T07:36:00Z",
        refreshedAt = "2026-10-19T07:37:00Z",
        surveyContext = context
    )
}
