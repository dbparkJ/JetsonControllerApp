package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadPollingTest {

    @Test
    fun `the current active job is the one read when no read loop is running`() {
        val current = job("job-1", UploadJobState.UPLOADING)

        assertEquals("job-1", activeJobToPoll(current, listOf(current), currentJobPollRunning = false))
    }

    @Test
    fun `a job with its own read loop is not read a second time`() {
        val current = job("job-1", UploadJobState.UPLOADING)

        assertNull(activeJobToPoll(current, listOf(current), currentJobPollRunning = true))
    }

    @Test
    fun `a transfer started elsewhere is read when there is no current job`() {
        val finished = job("job-0", UploadJobState.COMPLETED)
        val elsewhere = job("job-2", UploadJobState.SCANNING)

        assertEquals("job-2", activeJobToPoll(null, listOf(finished, elsewhere), currentJobPollRunning = false))
    }

    @Test
    fun `finished or queued-only queues are not read every second`() {
        val finished = job("job-0", UploadJobState.COMPLETED)
        val failed = job("job-3", UploadJobState.FAILED)

        assertNull(activeJobToPoll(finished, listOf(finished, failed), currentJobPollRunning = false))
        assertEquals(
            "job-4",
            activeJobToPoll(null, listOf(job("job-4", UploadJobState.QUEUED)), currentJobPollRunning = false)
        )
    }

    @Test
    fun `the full history is read at fifteen seconds and not on the one-second tick`() {
        assertFalse(historyRefreshDue(elapsedSinceHistoryMillis = 1_000L))
        assertFalse(historyRefreshDue(elapsedSinceHistoryMillis = 14_000L))
        assertTrue(historyRefreshDue(elapsedSinceHistoryMillis = FULL_HISTORY_POLL_INTERVAL_MILLIS))
        assertEquals(1_000L, ACTIVE_JOB_POLL_INTERVAL_MILLIS)
    }

    @Test
    fun `a polled job replaces its listed entry in place`() {
        val first = job("job-1", UploadJobState.QUEUED)
        val second = job("job-2", UploadJobState.UPLOADING)
        val updated = second.copy(bytesTransferred = 42L)

        val queue = replaceQueuedJob(listOf(first, second), updated)

        assertEquals(listOf("job-1", "job-2"), queue.map { it.id })
        assertEquals(42L, queue[1].bytesTransferred)
    }

    @Test
    fun `a polled job that is not listed is added at the top`() {
        val listed = job("job-1", UploadJobState.QUEUED)

        val queue = replaceQueuedJob(listOf(listed), job("job-9", UploadJobState.UPLOADING))

        assertEquals(listOf("job-9", "job-1"), queue.map { it.id })
    }

    private fun job(id: String, state: UploadJobState) = UploadJob(
        id = id,
        rootId = "data",
        relativePath = "runs/$id",
        targetId = "target",
        state = state,
        bytesTotal = 100L,
        bytesTransferred = 0L,
        filesTotal = null,
        filesTransferred = null,
        currentFile = null,
        errorMessage = null
    )
}
