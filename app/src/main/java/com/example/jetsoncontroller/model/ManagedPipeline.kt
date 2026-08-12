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
    val snapshotCreatedAt: String = ""
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
