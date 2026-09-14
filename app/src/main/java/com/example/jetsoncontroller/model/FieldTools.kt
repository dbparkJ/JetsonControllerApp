package com.example.jetsoncontroller.model

data class TaskRun(val id: String, val pipelineId: String, val label: String, val logId: String,
    val startedAt: String, val finishedAt: String? = null, val state: String = "UNKNOWN",
    val exitCode: Int? = null, val quality: RunQuality? = null,
    val runId: String? = null, val deviceId: String? = null,
    val contextSnapshot: SurveyContextSnapshot? = null,
    val policySnapshot: PipelineRunPolicy? = null,
    val preflightSnapshot: PipelinePreflight? = null,
    val output: PipelineRunOutput? = null,
    val active: Boolean = false)
data class TaskRunsResponse(val runs: List<TaskRun> = emptyList(), val nextOffset: Int? = null)
data class RoutePoint(val latitude: Double, val longitude: Double, val timestamp: Long,
    val segment: Int = 0, val fixState: String? = null, val sensorState: String? = null)
data class TaskRoute(val points: List<RoutePoint> = emptyList(), val quality: RunQuality? = null)
data class CaptureResult(val name: String, val rootId: String, val relativePath: String, val jpegBase64: String)
data class TerminalRequest(val command: String)
data class TerminalResult(val output: String, val exitCode: Int, val timedOut: Boolean = false, val truncated: Boolean = false)
