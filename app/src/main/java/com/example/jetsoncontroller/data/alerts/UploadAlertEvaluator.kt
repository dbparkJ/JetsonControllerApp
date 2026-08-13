package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState

data class UploadAlertDecision(
    val currentStates: Map<String, UploadJobState>,
    val started: List<UploadJob> = emptyList(),
    val ended: List<UploadJob> = emptyList()
)

object UploadAlertEvaluator {
    private val activeStates = setOf(
        UploadJobState.QUEUED,
        UploadJobState.SCANNING,
        UploadJobState.UPLOADING
    )
    private val terminalStates = setOf(
        UploadJobState.COMPLETED,
        UploadJobState.FAILED,
        UploadJobState.CANCELLED
    )

    fun evaluate(
        previousStates: Map<String, UploadJobState>?,
        jobs: List<UploadJob>
    ): UploadAlertDecision {
        val currentStates = jobs.associate { it.id to it.state }
        if (previousStates == null) {
            return UploadAlertDecision(currentStates)
        }
        return UploadAlertDecision(
            currentStates = currentStates,
            started = jobs.filter { job ->
                job.state in activeStates && previousStates[job.id] !in activeStates
            },
            ended = jobs.filter { job ->
                job.state in terminalStates && previousStates[job.id] !in terminalStates
            }
        )
    }
}
