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
    val errorMessage: String?,
    val sourceName: String? = null,
    val folderName: String? = null,
    val remoteSessionId: String? = null,
    val bytesPrepared: Long? = null,
    val filesPrepared: Int? = null,
    val throughputBytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
    val verification: UploadVerification? = null,
    val verifiedAt: String? = null,
    val deletionEligible: Boolean = false,
    val sourceDeleted: Boolean = false,
    val sourceDeletedAt: String? = null
)

data class UploadSourceSummary(
    val rootId: String,
    val relativePath: String,
    val sourceName: String,
    val folderName: String,
    val sourceType: String,
    val bytesTotal: Long,
    val filesTotal: Int,
    val calculatedAt: String? = null
)

data class UploadVerification(
    val jobId: String,
    val targetId: String? = null,
    val remoteSessionId: String? = null,
    val sourceName: String? = null,
    val state: String,
    val matched: Boolean,
    val deletionAllowed: Boolean,
    val bytesTotal: Long? = null,
    val filesTotal: Int? = null,
    val contentSha256: String? = null,
    val verifiedAt: String? = null
)

data class UploadDeletionResponse(
    val sessionId: String,
    val state: String
)
