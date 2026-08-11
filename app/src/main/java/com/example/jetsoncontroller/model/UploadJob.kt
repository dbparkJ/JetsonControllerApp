package com.example.jetsoncontroller.model

enum class UploadJobState {
    QUEUED,
    SCANNING,
    UPLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class UploadJob(
    val id: String,
    val rootId: String,
    val relativePath: String,
    val targetId: String,
    val state: UploadJobState,
    val bytesTotal: Long?,
    val bytesTransferred: Long?,
    val filesTotal: Int?,
    val filesTransferred: Int?,
    val currentFile: String?,
    val errorMessage: String?
)
