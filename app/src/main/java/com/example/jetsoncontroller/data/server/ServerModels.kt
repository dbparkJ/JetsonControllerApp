package com.example.jetsoncontroller.data.server

import com.example.jetsoncontroller.model.UploadContext
import com.google.gson.JsonElement

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
    val pathSummary: ServerPathSummary,
    val surveyContext: UploadContext? = null,
    val accessProjectId: String? = null
)

data class ServerJobsResponse(
    val serverEnvironment: String,
    val employeeId: String,
    val projectId: String,
    val jobs: List<ServerJob>,
    val nextOffset: Int?,
    val refreshedAt: String,
    val accessProjectId: String? = null
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
    val refreshedAt: String,
    val surveyContext: UploadContext? = null,
    val accessProjectId: String? = null
)

enum class ServerDeviceEventIntegrationState {
    ACTIVE,
    NOT_CONFIGURED,
    EXPIRED,
    ERROR,
    UNKNOWN
}

data class ServerDeviceEventIntegration(
    val state: String?,
    val detail: String? = null
) {
    val normalizedState: ServerDeviceEventIntegrationState
        get() = when (state?.uppercase()) {
            "ACTIVE" -> ServerDeviceEventIntegrationState.ACTIVE
            "NOT_CONFIGURED" -> ServerDeviceEventIntegrationState.NOT_CONFIGURED
            "EXPIRED" -> ServerDeviceEventIntegrationState.EXPIRED
            "ERROR" -> ServerDeviceEventIntegrationState.ERROR
            else -> ServerDeviceEventIntegrationState.UNKNOWN
        }
}

data class ServerDeviceEventWindow(
    val hours: Int,
    val pending: Int,
    val sent: Int,
    val dedup: Int,
    val dead: Int
)

data class ServerDeviceEventDelivery(
    val status: String,
    val at: String
)

data class ServerDeviceEventFailure(
    val status: String,
    val code: String,
    val at: String
)

data class ServerDeviceEventStatus(
    val deviceId: String,
    val integration: ServerDeviceEventIntegration,
    val window: ServerDeviceEventWindow,
    val lastDelivered: ServerDeviceEventDelivery?,
    val lastFailure: ServerDeviceEventFailure?,
    val lastSeenAt: String?
)

enum class ServerTicketState { OPEN, IN_PROGRESS, COMPLETED, UNKNOWN }

enum class ServerTicketReportState { PENDING, SENT, DEDUP, CONFLICT, ERROR, UNKNOWN }

enum class ServerTicketIntegrationState { ACTIVE, NOT_CONFIGURED, ERROR, UNKNOWN }

data class ServerTicketStatus(
    val name: String? = null,
    val category: String? = null,
    /** GEO& Work systemKey; '막힘' shares IN_PROGRESS with '진행 중' and is told apart by BLOCKED. */
    val systemKey: String? = null
) {
    val isBlocked: Boolean
        get() = systemKey.equals("BLOCKED", ignoreCase = true) || name == "막힘"

    val normalizedCategory: ServerTicketState
        get() = when (category?.uppercase()) {
            "OPEN", "TODO", "NOT_STARTED" -> ServerTicketState.OPEN
            "IN_PROGRESS", "IN PROGRESS", "WORKING", "STARTED" -> ServerTicketState.IN_PROGRESS
            "COMPLETED", "DONE" -> ServerTicketState.COMPLETED
            else -> ServerTicketState.UNKNOWN
        }
}

data class ServerTicketReport(
    val state: String? = null,
    val code: String? = null
) {
    val normalizedState: ServerTicketReportState
        get() = when (state?.uppercase()) {
            "PENDING" -> ServerTicketReportState.PENDING
            "SENT" -> ServerTicketReportState.SENT
            "DEDUP", "DEDUPLICATED" -> ServerTicketReportState.DEDUP
            "CONFLICT" -> ServerTicketReportState.CONFLICT
            "ERROR", "DEAD", "FAILED" -> ServerTicketReportState.ERROR
            else -> ServerTicketReportState.UNKNOWN
        }
}

data class ServerTicketIntegration(
    val state: String? = null,
    val detail: String? = null
) {
    val normalizedState: ServerTicketIntegrationState
        get() = when (state?.uppercase()) {
            "ACTIVE" -> ServerTicketIntegrationState.ACTIVE
            "NOT_CONFIGURED" -> ServerTicketIntegrationState.NOT_CONFIGURED
            "ERROR" -> ServerTicketIntegrationState.ERROR
            else -> ServerTicketIntegrationState.UNKNOWN
        }
}

data class ServerTicket(
    val rowId: String,
    val title: String,
    val project: String? = null,
    val unit: String? = null,
    val assignees: List<String> = emptyList(),
    val dueDate: String? = null,
    val status: ServerTicketStatus? = null,
    val revision: String? = null,
    val updatedAt: String? = null,
    val runId: String? = null,
    val report: ServerTicketReport? = null,
    val features: JsonElement? = null,
    val bufferM: Double? = null,
    val range: JsonElement? = null,
    val bbox: List<Double>? = null,
    val lengthM: Double? = null,
    val areaM2: Double? = null,
    val geometryRevision: String? = null,
    val geometryHash: String? = null,
    val geometry: JsonElement? = null
) {
    val zoneRevision: String? get() = com.example.jetsoncontroller.data.zone.ZoneContract.fromTicket(this).contract?.revision ?: geometryRevision
    val zoneHash: String? get() = com.example.jetsoncontroller.data.zone.ZoneContract.fromTicket(this).contract?.hash ?: geometryHash
}

data class ServerTicketsResponse(
    val tickets: List<ServerTicket> = emptyList(),
    val units: List<String> = emptyList(),
    val deviceUnit: String? = null,
    val syncedAt: String? = null,
    val integration: ServerTicketIntegration? = null,
    val projectId: String? = null,
    val deviceId: String? = null
)

data class ServerTicketStartRequest(val runId: String, val deviceId: String)

data class ServerTicketCompleteRequest(val runId: String)

/** Echoes are optional because the receiver may answer 202 with an empty body. */
data class ServerTicketMutationResponse(
    val rowId: String? = null,
    val projectId: String? = null,
    val runId: String? = null,
    val deviceId: String? = null
)

data class ServerTicketErrorResponse(
    val code: String? = null,
    /** The receiver answers {"detail": "TICKET_NOT_OPEN"}; prose details are not codes. */
    val detail: String? = null,
    val message: String? = null,
    val rowId: String? = null,
    val projectId: String? = null,
    val runId: String? = null,
    val deviceId: String? = null
)

data class ServerTrashJob(
    val sessionId: String,
    val clientJobId: String,
    val sourceName: String,
    val totalBytes: Long,
    val fileCount: Int,
    val state: String,
    val trashedAt: String,
    val surveyContext: UploadContext? = null,
    val purgeSupported: Boolean? = null,
    val restoreSupported: Boolean? = null
)

data class ServerTrashResponse(
    val serverEnvironment: String,
    val projectId: String,
    val jobs: List<ServerTrashJob>,
    val refreshedAt: String,
    val accessProjectId: String? = null,
    val emptySupported: Boolean = false,
    val total: Int = jobs.size,
    val nextOffset: Int? = null
)

data class ServerEmptyTrashRequest(
    val confirmed: Boolean = true,
    val sessionIds: List<String>
)

data class ServerEmptyTrashItemResult(
    val sessionId: String,
    val state: String,
    val error: String? = null
)

data class ServerEmptyTrashResponse(
    val serverEnvironment: String,
    val projectId: String,
    val accessProjectId: String? = null,
    val results: List<ServerEmptyTrashItemResult>,
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
