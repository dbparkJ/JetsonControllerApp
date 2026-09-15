package com.example.jetsoncontroller.model

data class SurveyProject(
    val surveyProjectId: String,
    val label: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String
)

data class SurveyProjectsResponse(val projects: List<SurveyProject> = emptyList())

data class SurveySection(
    val surveySectionId: String,
    val surveyProjectId: String,
    val label: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String
)

data class SurveySectionsResponse(val sections: List<SurveySection> = emptyList())

data class SurveyProjectSummary(
    val surveyProjectId: String,
    val label: String,
    val revision: Int
)

data class SurveySectionSummary(
    val surveySectionId: String,
    val surveyProjectId: String,
    val label: String,
    val revision: Int
)

data class SurveyLabelMutationRequest(
    val label: String,
    val expectedRevision: Int? = null,
    val clientRequestId: String? = null
)

data class ExpectedOutputPolicy(
    val minFiles: Int = 0,
    val minBytes: Long = 0,
    val patterns: List<String> = emptyList()
)

data class PipelineRunPolicy(
    val schemaVersion: Int = 1,
    val pipelineId: String,
    val policyVersion: Int = 1,
    val revision: String,
    val requiredSensors: List<String> = emptyList(),
    val optionalSensors: List<String> = emptyList(),
    val minFreeBytes: Long = 0,
    val expectedOutput: ExpectedOutputPolicy = ExpectedOutputPolicy(),
    val outputRootId: String,
    val outputPath: String = "",
    val configured: Boolean = true,
    val updatedAt: String
)

data class UpdatePipelineRunPolicyRequest(
    val requiredSensors: List<String>,
    val optionalSensors: List<String>,
    val minFreeBytes: Long,
    val expectedOutput: ExpectedOutputPolicy,
    val outputRootId: String,
    val outputPath: String,
    val expectedRevision: String? = null,
    val clientRequestId: String
)

data class SurveyContextSnapshot(
    val schemaVersion: Int = 1,
    val deviceId: String,
    val surveyProjectId: String,
    val surveyProjectLabel: String,
    val surveyProjectRevision: Int,
    val surveySectionId: String,
    val surveySectionLabel: String,
    val surveySectionRevision: Int,
    val capturedAt: String
)

data class PipelinePreflightRequest(
    val surveyProjectId: String,
    val surveySectionId: String,
    val surveyProjectRevision: Int,
    val surveySectionRevision: Int,
    val policyRevision: String
)

data class PreflightCheck(
    val state: String = "UNKNOWN",
    val observedAt: String? = null,
    val source: String? = null,
    val synchronizedAtEpochMillis: Long? = null,
)

data class PreflightStorageCheck(
    val state: String = "UNKNOWN",
    val rootId: String,
    val path: String,
    val availableBytes: Long? = null,
    val requiredBytes: Long = 0,
    val detail: String? = null
)

data class PreflightSensorCheck(
    val sensor: String,
    val requirement: String = "UNSPECIFIED",
    val state: String = "UNKNOWN",
    val sampleAtEpochMillis: Long? = null,
    val ageMillis: Long? = null,
    val detail: String? = null
)

data class PipelinePreflightChecks(
    val time: PreflightCheck = PreflightCheck(),
    val storage: PreflightStorageCheck,
    val sensors: List<PreflightSensorCheck> = emptyList()
)

data class PipelinePreflightProblem(
    val code: String,
    val sensor: String? = null,
    val requirement: String? = null,
    val detail: String? = null
)

data class PipelinePreflight(
    val schemaVersion: Int = 1,
    val preflightId: String,
    val pipelineId: String,
    val deviceId: String,
    val surveyProject: SurveyProjectSummary,
    val surveySection: SurveySectionSummary,
    val contextSnapshot: SurveyContextSnapshot,
    val policy: PipelineRunPolicy,
    val sourceRevision: String,
    val configRevision: String,
    val checkedAt: String,
    val checkedAtEpochMillis: Long,
    val checks: PipelinePreflightChecks,
    val ready: Boolean = false,
    val problems: List<PipelinePreflightProblem> = emptyList(),
    val bootId: String? = null,
    val sourceDirty: Boolean? = null,
    val release: String? = null
)

data class ContextualStartRequest(
    val surveyProjectId: String,
    val surveySectionId: String,
    val surveyProjectRevision: Int,
    val surveySectionRevision: Int,
    val policyRevision: String,
    val preflightId: String,
    val clientRequestId: String
)

data class OutputPatternMatch(
    val pattern: String,
    val fileCount: Int = 0,
    val bytesTotal: Long = 0
)

data class PipelineOutputManifest(
    val schemaVersion: Int = 1,
    val runId: String,
    val generatedAt: String,
    val finishedAt: String? = null,
    val fileCount: Int = 0,
    val bytesTotal: Long = 0,
    val truncated: Boolean = false,
    val matchedPatterns: List<OutputPatternMatch> = emptyList(),
    val expected: ExpectedOutputPolicy = ExpectedOutputPolicy(),
    val expectationState: String = "PENDING"
)

data class PipelineRunOutput(
    val rootId: String,
    val path: String,
    val outputId: String,
    val manifestState: String = "PENDING",
    val manifest: PipelineOutputManifest? = null
)

data class UploadContext(
    val schemaVersion: Int = 1,
    val surveyProjectId: String,
    val surveySectionId: String,
    val runId: String,
    val deviceId: String,
    val pipelineId: String,
    val sourceRevision: String,
    val configSha256: String,
    val outputId: String,
    val createdAt: String
)

data class ContextualStartReceipt(
    val runId: String,
    val clientRequestId: String,
    val outcome: String,
    val contextSnapshot: SurveyContextSnapshot,
    val preflightSnapshot: PipelinePreflight,
    val output: PipelineRunOutput,
    val uploadContext: UploadContext,
    val statusUrl: String
)

data class RunTelemetry(
    val schemaVersion: Int = 1,
    val runId: String,
    val outputId: String,
    val sourceRevision: String,
    val observedAtEpochMillis: Long,
    val bytesObservedAtEpochMillis: Long,
    val durationMillis: Long? = null,
    val collectedBytes: Long? = null,
    val collectedFileCount: Int? = null,
    val collectionBytesState: String = "UNAVAILABLE"
)

data class PipelineRun(
    val schemaVersion: Int = 1,
    val runId: String,
    val logId: String,
    val pipelineId: String,
    val deviceId: String,
    val state: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val exitCode: Int? = null,
    val stopReason: String? = null,
    val contextSnapshot: SurveyContextSnapshot,
    val policySnapshot: PipelineRunPolicy,
    val preflightSnapshot: PipelinePreflight,
    val sourceRevision: String,
    val configRevision: String,
    val output: PipelineRunOutput,
    val uploadContext: UploadContext,
    val active: Boolean = false,
    val sourceDirty: Boolean? = null,
    val release: String? = null,
    val telemetry: RunTelemetry? = null,
    val quality: RunQuality? = null
)
