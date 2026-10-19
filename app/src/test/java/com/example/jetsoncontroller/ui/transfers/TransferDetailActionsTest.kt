package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.model.UploadContext
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadVerification
import org.junit.Assert.*
import org.junit.Test

class TransferDetailActionsTest {
    @Test fun `every transport state has one primary action and Korean state word`() {
        val expected = mapOf(
            UploadJobState.QUEUED to (TransferDetailAction.CANCEL to "대기 중"),
            UploadJobState.SCANNING to (TransferDetailAction.CANCEL to "파일 확인 중"),
            UploadJobState.UPLOADING to (TransferDetailAction.CANCEL to "보내는 중"),
            UploadJobState.FAILED to (TransferDetailAction.RETRY to "실패"),
            UploadJobState.COMPLETED to (TransferDetailAction.VERIFY to "수신 확인 중"),
            UploadJobState.CANCELLED to (TransferDetailAction.NONE to "취소됨")
        )
        expected.forEach { (state, pair) ->
            val result = transferDetailActions(job(state))
            assertEquals(pair.first, result.primary)
            assertEquals(pair.second, result.word)
            assertFalse(result.canCleanUp)
        }
    }

    @Test fun `only an identity matched receipt enables report or clean up`() {
        val verified = verifiedJob()
        val receipt = requireNotNull(verified.verification)
        val wrongReceipts = listOf(
            receipt.copy(jobId = "other"), receipt.copy(targetId = "other"),
            receipt.copy(remoteSessionId = "other"), receipt.copy(sourceName = "other")
        )
        wrongReceipts.forEach { receipt ->
            assertNull(matchingTransferVerification(verified, receipt))
            val actions = transferDetailActions(verified, receipt, linkedToTicket = true)
            assertEquals(TransferDetailAction.VERIFY, actions.primary)
            assertFalse(actions.canCleanUp)
        }
        assertTrue(transferDetailActions(verified).canCleanUp)
        assertEquals(TransferDetailAction.REPORT,
            transferDetailActions(verified, linkedToTicket = true).primary)
    }

    @Test fun `matched legacy receipt still accepts omitted optional identity fields`() {
        val job = verifiedJob()
        val receipt = job.verification!!.copy(targetId = null, remoteSessionId = null, sourceName = null)
        assertNotNull(matchingTransferVerification(job, receipt))
    }

    @Test fun `a mismatched receipt retries and never cleans up or reports`() {
        val job = verifiedJob().let { it.copy(verification = it.verification!!.copy(matched = false)) }
        val actions = transferDetailActions(job, linkedToTicket = true)
        assertEquals("내용 불일치", actions.word)
        assertEquals(TransferDetailAction.RETRY, actions.primary)
        assertFalse(actions.canCleanUp)
    }

    @Test fun `reporting is independent of device connectivity and finished reports open the result or server`() {
        val arbitrary = verifiedJob().copy(context = null)
        assertEquals(TransferDetailAction.REPORT,
            transferDetailActions(arbitrary, linkedToTicket = true).primary)
        assertEquals(TransferDetailAction.SERVER,
            transferDetailActions(arbitrary, linkedToTicket = true, reported = true).primary)
        assertEquals(TransferDetailAction.RESULT,
            transferDetailActions(verifiedJob(), linkedToTicket = true, reported = true).primary)
        assertFalse(transferMutationAllowed(transferActionTarget("device-a", arbitrary), "device-a", arbitrary,
            TransferMutation.CLEAN_UP, controlAvailable = false, sameNetwork = false))
    }

    @Test fun `clean up requires all safety gates and a recoverable source offers restore`() {
        val verified = verifiedJob()
        listOf(
            verified.copy(deletionEligible = false),
            verified.copy(sourceDeleted = true),
            verified.copy(sourceDeletionState = "PURGING"),
            verified.copy(sourceDeletionState = "PURGED"),
            verified.copy(sourceDeletionState = "UNKNOWN"),
            verified.copy(verification = verified.verification!!.copy(deletionAllowed = false))
        ).forEach { assertFalse(transferDetailActions(it).canCleanUp) }
        val trashed = verified.copy(sourceRecoverable = true, sourceTrashId = "trash-a", sourceDeletionState = "TRASHED")
        assertTrue(transferDetailActions(trashed).canRestore)
        assertFalse(transferDetailActions(trashed).canCleanUp)
        assertFalse(transferDetailActions(trashed.copy(sourceDeletionState = "PURGING")).canRestore)
    }

    @Test fun `confirmation rejects changes to any part of its immutable target`() {
        val original = verifiedJob()
        val target = transferActionTarget("device-a", original)
        assertTrue(target.matches("DEVICE-A", original))
        assertFalse(target.matches("device-b", original))
        listOf(
            original.copy(id = "other"), original.copy(rootId = "other"),
            original.copy(relativePath = "other"), original.copy(targetId = "other"),
            original.copy(remoteSessionId = "other"), original.copy(sourceIdentity = "other"),
            original.copy(sourceTrashId = "other"), original.copy(context = original.context!!.copy(outputId = "other"))
        ).forEach { changed -> assertFalse(target.matches("device-a", changed)) }
    }

    @Test fun `execution rechecks current state and allows cancel over direct connection`() {
        val sending = job(UploadJobState.UPLOADING)
        val target = transferActionTarget("device-a", sending)
        assertTrue(transferMutationAllowed(target, "device-a", sending, TransferMutation.CANCEL, true, false))
        assertFalse(transferMutationAllowed(target, "device-a", sending.copy(state = UploadJobState.COMPLETED),
            TransferMutation.CANCEL, true, true))
        assertFalse(transferMutationAllowed(target, "device-b", sending, TransferMutation.CANCEL, true, true))
        assertFalse(transferMutationAllowed(target, "device-a", sending, TransferMutation.DELETE_RECORD, true, true))
        val failed = sending.copy(state = UploadJobState.FAILED)
        assertFalse(transferMutationAllowed(target, "device-a", failed, TransferMutation.RETRY, true, false))
        assertTrue(transferMutationAllowed(target, "device-a", failed, TransferMutation.RETRY, true, true))
    }

    @Test fun `log filters retain cancelled jobs and do not count unverified transport completion as success`() {
        val cancelled = job(UploadJobState.CANCELLED)
        assertTrue(transferLogMatches(cancelled, 0))
        assertTrue(transferLogMatches(cancelled, 3))
        assertFalse(transferLogMatches(cancelled, 2))
        assertFalse(transferLogMatches(job(UploadJobState.COMPLETED), 2))
        assertTrue(transferLogMatches(verifiedJob(), 2))
        val mismatch = verifiedJob().let { it.copy(verification = it.verification!!.copy(matched = false)) }
        assertTrue(transferLogMatches(mismatch, 1))
        assertFalse(transferLogMatches(mismatch, 2))
    }

    private fun job(state: UploadJobState) = UploadJob(
        id = "job-a", rootId = "data", relativePath = "capture/run-a", targetId = "operations",
        state = state, bytesTotal = 1200, bytesTransferred = 768, filesTotal = 12, filesTransferred = 8,
        currentFile = null, errorMessage = null, sourceName = "run-a", remoteSessionId = "receipt-a",
        sourceIdentity = "manifest-a"
    )

    private fun verifiedJob(): UploadJob = job(UploadJobState.COMPLETED).copy(
        deletionEligible = true,
        context = UploadContext(surveyProjectId = "project", surveySectionId = "section", runId = "run-a",
            deviceId = "device-a", pipelineId = "capture", sourceRevision = "source", configSha256 = "config",
            outputId = "output-a", createdAt = "2026-10-19T07:36:00Z"),
        verification = UploadVerification("job-a", "operations", "receipt-a", "run-a",
            state = "MATCHED", matched = true, deletionAllowed = true)
    )
}
