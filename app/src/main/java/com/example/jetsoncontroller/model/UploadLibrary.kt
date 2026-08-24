package com.example.jetsoncontroller.model

data class UploadLibrarySession(
    val sessionId: String,
    val sourceName: String,
    val totalBytes: Long,
    val fileCount: Int,
    val createdAt: String? = null,
    val completedAt: String? = null,
    val folderName: String? = null
) {
    val displayFolderName: String
        get() = folderName?.takeIf { it.isNotBlank() } ?: sourceName
}

data class UploadLibrarySessionsResponse(
    val sessions: List<UploadLibrarySession> = emptyList(),
    val nextOffset: Int? = null
)

data class UploadLibraryFilesResponse(
    val sessionId: String,
    val path: String,
    val entries: List<RemoteFileEntry> = emptyList(),
    val truncated: Boolean = false
)
