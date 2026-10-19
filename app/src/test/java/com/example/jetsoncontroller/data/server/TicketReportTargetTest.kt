package com.example.jetsoncontroller.data.server

import com.example.jetsoncontroller.model.UploadContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TicketReportTargetTest {
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
    private val ticket = ServerTicket("ticket-row", "Ticket", revision = "ticket-r4")
    private val receipt = receipt(context)

    @Test
    fun `matching profile ticket revision run and verified receipt remain reportable`() {
        assertTrue(matches(target, ticket, receipt))
    }

    @Test
    fun `changed report tuple components are rejected`() {
        assertFalse(matches(target.copy(profileId = "other-profile"), ticket, receipt))
        assertFalse(matches(target.copy(rowId = "other-row"), ticket, receipt))
        assertFalse(matches(target.copy(ticketRevision = "ticket-r5"), ticket, receipt))
        assertFalse(matches(target.copy(runId = "other-run"), ticket, receipt))
        assertFalse(matches(target.copy(receiptId = "other-receipt"), ticket, receipt))
        assertFalse(matches(target, ticket.copy(revision = "ticket-r5"), receipt))
    }

    @Test
    fun `receipt must be independently verified and belong to the same run and device`() {
        assertFalse(matches(target, ticket, receipt.copy(matched = false)))
        assertFalse(matches(target, ticket, receipt.copy(state = "PENDING")))
        assertFalse(matches(target, ticket, receipt.copy(sessionId = "other-receipt")))
        assertFalse(matches(target, ticket, receipt.copy(surveyContext = context.copy(runId = "other-run"))))
        assertFalse(matches(target, ticket, receipt.copy(surveyContext = context.copy(deviceId = "other-device"))))
    }

    @Test
    fun `report without a revision or receipt cannot pass the completion target check`() {
        assertFalse(matches(target.copy(ticketRevision = null), ticket.copy(revision = null), receipt))
        assertFalse(matches(target.copy(receiptId = null), ticket, null))
    }

    @Test
    fun `start report target may omit a receipt but still requires the exact ticket revision`() {
        assertTrue(ticketReportTargetMatches(
            target = target.copy(receiptId = null),
            profileId = "profile",
            ticket = ticket,
            runId = "run-7",
            deviceId = "device-1",
            receiptId = null,
            receipt = null
        ))
        assertFalse(ticketReportTargetMatches(
            target = target.copy(receiptId = null),
            profileId = "profile",
            ticket = ticket.copy(revision = "changed"),
            runId = "run-7",
            deviceId = "device-1",
            receiptId = null,
            receipt = null
        ))
    }

    @Test
    fun `ticket revision and receipt target survive outbox and run link recreation`() {
        val file = temporaryFolder.newFile("ticket-reports.json")
        val store = TicketReportStore(file)
        store.savePendingSelection(PendingTicketSelection(
            profileId = target.profileId,
            rowId = target.rowId,
            title = "Ticket",
            project = "Project",
            unit = "Unit",
            deviceId = "device-1",
            pipelineId = "pipeline",
            ticketRevision = target.ticketRevision
        ))
        store.armPendingSelection("device-1", "pipeline", "request")
        assertTrue(store.bindPendingSelection("run-7", "device-1", "pipeline", "request")
            ?.ticketRevision == target.ticketRevision)
        store.enqueue(PendingTicketReport(
            key = ticketReportKey(target.runId, completed = true),
            rowId = target.rowId,
            runId = target.runId,
            deviceId = "device-1",
            profileId = target.profileId,
            createdAt = "2026-10-19T07:37:00Z",
            ticketRevision = target.ticketRevision,
            receiptId = target.receiptId
        ))

        val restored = TicketReportStore(file)
        assertEquals(target.ticketRevision, restored.link(target.runId)?.ticketRevision)
        assertEquals(target, restored.pendingReports().single().target())
    }

    private fun matches(
        target: TicketReportTarget,
        ticket: ServerTicket?,
        receipt: ServerReceipt?
    ): Boolean = ticketReportTargetMatches(
        target = target,
        profileId = "profile",
        ticket = ticket,
        runId = "run-7",
        deviceId = "device-1",
        receiptId = target.receiptId,
        receipt = receipt,
        requireReceipt = true
    )

    private fun receipt(context: UploadContext) = ServerReceipt(
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
