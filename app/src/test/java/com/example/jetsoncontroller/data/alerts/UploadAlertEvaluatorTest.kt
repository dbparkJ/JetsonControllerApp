package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadAlertEvaluatorTest {
    @Test
    fun `first snapshot establishes baseline`() {
        val decision = UploadAlertEvaluator.evaluate(
            null,
            listOf(job("old", UploadJobState.COMPLETED))
        )
        assertTrue(decision.started.isEmpty())
        assertTrue(decision.ended.isEmpty())
    }

    @Test
    fun `notifies once when upload starts and reaches a terminal state`() {
        val started = UploadAlertEvaluator.evaluate(
            emptyMap(),
            listOf(job("new", UploadJobState.QUEUED))
        )
        assertEquals(listOf("new"), started.started.map { it.id })

        val ended = UploadAlertEvaluator.evaluate(
            started.currentStates,
            listOf(job("new", UploadJobState.COMPLETED))
        )
        assertEquals(listOf("new"), ended.ended.map { it.id })

        val repeated = UploadAlertEvaluator.evaluate(
            ended.currentStates,
            listOf(job("new", UploadJobState.COMPLETED))
        )
        assertTrue(repeated.ended.isEmpty())
    }

    private fun job(id: String, state: UploadJobState) = UploadJob(
        id = id,
        rootId = "recordings",
        relativePath = "capture",
        targetId = "server",
        state = state,
        bytesTotal = 10,
        bytesTransferred = 10,
        filesTotal = 1,
        filesTransferred = 1,
        currentFile = null,
        errorMessage = null
    )
}
