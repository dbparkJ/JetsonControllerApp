package com.example.jetsoncontroller.model

data class ManagedPipeline(
    val id: String,
    val label: String,
    val description: String = "",
    val state: PipelineState = PipelineState.UNKNOWN,
    val activeState: String = "unknown",
    val subState: String = "unknown",
    val enabled: Boolean = false,
    val lastExitCode: Int = 0,
    val result: String = "unknown",
    val restartCount: Int = 0,
    val entrypoint: String,
    val config: String,
    val virtualenv: String,
    val pythonVersion: String = "",
    val sourceBranch: String = "",
    val sourceRevision: String = "",
    val sourceDirty: Boolean = false,
    val snapshotCreatedAt: String = "",
    val outputRootId: String? = null,
    val outputPath: String? = null,
    val resultsDirectory: String? = null,
    val folderConvention: Boolean = false,
    val timeSynchronized: Boolean = false,
    val observedAt: String? = null,
    val activeRunId: String? = null,
    val execution: PipelineExecution? = null,
    val failureKind: String? = null,
    val control: PipelineControl? = null
)

data class PipelineExecution(
    val runId: String = "",
    val logId: String = "",
    val active: Boolean = false,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val exitCode: Int? = null,
    val sourceRevision: String? = null,
    val sourceDirty: Boolean? = null,
    val release: String? = null,
    val configSha256: String? = null,
    val resultsDirectory: String? = null,
    val storageAvailableBytes: Long? = null,
    val storageRequiredBytes: Long? = null,
    val storagePreflight: String? = null,
    val failureKind: String? = null
)

data class PipelineControl(
    val action: String = "",
    val commandIssued: Boolean = false,
    val outcome: String = ""
)

enum class PipelineState {
    RUNNING,
    STARTING,
    STOPPING,
    STOPPED,
    FAILED,
    RETRYING,
    WAITING_FOR_TIME_SYNC,
    UNKNOWN
}

data class PipelineFolderDiscovery(
    val pipelineId: String,
    val repository: String,
    val virtualenv: String,
    val entrypoint: String,
    val config: String,
    val workingDirectory: String,
    val resultsDirectory: String,
    val resultsExists: Boolean,
    val logDirectory: String,
    val autostartDefault: Boolean = false
)

data class DiscoverPipelineFolderRequest(
    val rootId: String,
    val path: String
)

data class RegisterPipelineFolderRequest(
    val rootId: String,
    val path: String,
    val name: String,
    val autostart: Boolean = false
)

data class RegisterPipelineRequest(
    val id: String,
    val label: String,
    val repositoryRootId: String,
    val repositoryPath: String,
    val virtualenvRootId: String,
    val virtualenvPath: String,
    val entrypoint: String,
    val config: String,
    val workingDirectory: String = ".",
    val writableDirectories: List<String> = emptyList(),
    val autostart: Boolean = false
)

data class PipelineLog(
    val pipelineId: String,
    val lines: List<String> = emptyList()
)

data class PipelineLogFile(
    val id: String,
    val startedAt: String,
    val modifiedAt: String,
    val sizeBytes: Long,
    val active: Boolean = false
)

data class PipelineLogFilesResponse(
    val pipelineId: String,
    val files: List<PipelineLogFile> = emptyList()
)

data class PipelineLogChunk(
    val pipelineId: String,
    val logId: String,
    val content: String,
    val offset: Long,
    val nextOffset: Long,
    val sizeBytes: Long,
    val modifiedAt: String,
    val eof: Boolean
)

data class PipelineConfigDocument(
    val pipelineId: String,
    val path: String,
    val content: String
)

data class UpdatePipelineConfigRequest(
    val content: String
)

enum class PipelineConfigValueType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    NULL
}

data class PipelineConfigField(
    val path: String,
    val label: String,
    val type: PipelineConfigValueType,
    val value: String
)

data class PipelineConfigFieldsDocument(
    val pipelineId: String,
    val path: String,
    val revision: String,
    val fields: List<PipelineConfigField> = emptyList()
)

data class UpdatePipelineConfigFieldsRequest(
    val revision: String,
    val values: Map<String, String>
)
