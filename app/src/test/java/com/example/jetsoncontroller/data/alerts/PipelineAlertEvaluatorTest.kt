package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineAlertEvaluatorTest {
    @Test
    fun `first snapshot establishes baseline without notifications`() {
        val running = pipeline("capture", PipelineState.RUNNING)
        val failed = pipeline("upload", PipelineState.FAILED)

        val decision = PipelineAlertEvaluator.evaluate(null, listOf(running, failed))

        assertTrue(decision.started.isEmpty())
        assertTrue(decision.failed.isEmpty())
        assertEquals(PipelineState.RUNNING, decision.currentStates["capture"])
    }

    @Test
    fun `notifies only when pipeline enters running or failed state`() {
        val previous = mapOf(
            "capture" to PipelineState.STARTING,
            "upload" to PipelineState.RUNNING
        )

        val decision = PipelineAlertEvaluator.evaluate(
            previous,
            listOf(
                pipeline("capture", PipelineState.RUNNING),
                pipeline("upload", PipelineState.FAILED)
            )
        )

        assertEquals(listOf("capture"), decision.started.map { it.id })
        assertEquals(listOf("upload"), decision.failed.map { it.id })

        val repeated = PipelineAlertEvaluator.evaluate(
            decision.currentStates,
            listOf(
                pipeline("capture", PipelineState.RUNNING),
                pipeline("upload", PipelineState.FAILED)
            )
        )
        assertTrue(repeated.started.isEmpty())
        assertTrue(repeated.failed.isEmpty())
    }

    private fun pipeline(id: String, state: PipelineState) = ManagedPipeline(
        id = id,
        label = id,
        state = state,
        entrypoint = "main.py",
        config = "config.yaml",
        virtualenv = ".venv"
    )
}
