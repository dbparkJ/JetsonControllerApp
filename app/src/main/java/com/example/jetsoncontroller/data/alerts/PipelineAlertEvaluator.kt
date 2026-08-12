package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState

data class PipelineAlertDecision(
    val started: List<ManagedPipeline> = emptyList(),
    val failed: List<ManagedPipeline> = emptyList(),
    val currentStates: Map<String, PipelineState>
)

object PipelineAlertEvaluator {
    fun evaluate(
        previousStates: Map<String, PipelineState>?,
        pipelines: List<ManagedPipeline>
    ): PipelineAlertDecision {
        val currentStates = pipelines.associate { it.id to it.state }
        if (previousStates == null) {
            return PipelineAlertDecision(currentStates = currentStates)
        }

        return PipelineAlertDecision(
            started = pipelines.filter { pipeline ->
                pipeline.state == PipelineState.RUNNING &&
                    previousStates[pipeline.id] != PipelineState.RUNNING
            },
            failed = pipelines.filter { pipeline ->
                pipeline.state == PipelineState.FAILED &&
                    previousStates[pipeline.id] != PipelineState.FAILED
            },
            currentStates = currentStates
        )
    }
}
