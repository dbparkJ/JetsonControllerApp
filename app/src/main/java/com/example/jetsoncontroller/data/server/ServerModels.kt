package com.example.jetsoncontroller.data.server

data class ServerEmployee(
    val employeeId: String,
    val displayName: String,
    val role: String
)

data class ServerProject(
    val projectId: String,
    val displayName: String
)

data class ServerCapabilities(
    val version: Int,
    val serverEnvironment: String,
    val employee: ServerEmployee,
    val projects: List<ServerProject>,
    val maxPreviewBytes: Long,
    val refreshedAt: String
)

data class ServerPathSummary(
    val rootEntries: List<String>,
    val rootEntriesTruncated: Boolean,
    val imageCount: Int,
    val videoCount: Int
)

data class ServerJob(
    val sessionId: String,
    val clientJobId: String,
    val projectId: String,
    val deviceId: String,
    val sourceName: String,
    val state: String,
    val failureCode: String?,
    val totalBytes: Long,
    val receivedBytes: Long,
    val fileCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String?,
    val pathSummary: ServerPathSummary
)

data class ServerJobsResponse(
    val serverEnvironment: String,
    val employeeId: String,
    val projectId: String,
    val jobs: List<ServerJob>,
    val nextOffset: Int?,
    val refreshedAt: String
)

data class ServerFileEntry(
    val name: String,
    val relativePath: String,
    val type: String,
    val sizeBytes: Long?,
    val modifiedAt: String?
)

data class ServerFilesResponse(
    val serverEnvironment: String,
    val projectId: String,
    val sessionId: String,
    val path: String,
    val entries: List<ServerFileEntry>,
    val truncated: Boolean,
    val refreshedAt: String
)

data class ServerReceipt(
    val serverEnvironment: String,
    val projectId: String,
    val sessionId: String,
    val clientJobId: String,
    val sourceName: String,
    val folderName: String,
    val state: String,
    val totalBytes: Long,
    val fileCount: Int,
    val contentSha256: String,
    val completedAt: String,
    val matched: Boolean,
    val verifiedAt: String,
    val refreshedAt: String
)

data class ServerTrashJob(
    val sessionId: String,
    val clientJobId: String,
    val sourceName: String,
    val totalBytes: Long,
    val fileCount: Int,
    val state: String,
    val trashedAt: String
)

data class ServerTrashResponse(
    val serverEnvironment: String,
    val projectId: String,
    val jobs: List<ServerTrashJob>,
    val refreshedAt: String
)

data class ServerLifecycleResponse(
    val serverEnvironment: String,
    val projectId: String,
    val sessionId: String,
    val state: String,
    val trashedAt: String? = null,
    val restoredAt: String? = null
)
