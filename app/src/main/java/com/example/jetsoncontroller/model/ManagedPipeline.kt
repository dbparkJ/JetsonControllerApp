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
    val outputPath: String? = null
)

enum class PipelineState {
    RUNNING,
    STARTING,
    STOPPING,
    STOPPED,
    FAILED,
    RETRYING,
    UNKNOWN
}

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
    val autostart: Boolean = true
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
