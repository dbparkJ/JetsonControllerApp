package com.example.jetsoncontroller.data.server

import com.example.jetsoncontroller.data.storage.CachedServerJobs
import com.example.jetsoncontroller.data.storage.RecentServerJobsCache
import com.example.jetsoncontroller.data.storage.ServerJobsCacheKey
import com.google.gson.Gson
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

enum class ServerJobsSource { NETWORK, CACHE }

data class ServerJobsSnapshot(
    val response: ServerJobsResponse,
    val source: ServerJobsSource,
    val cachedAtEpochMillis: Long,
    val refreshError: String? = null
) {
    val isCurrent: Boolean get() = source == ServerJobsSource.NETWORK
}

enum class ServerMutationStatus { CONFIRMED, UNKNOWN }

data class ServerMutationResult(
    val status: ServerMutationStatus,
    val response: ServerLifecycleResponse? = null,
    val detail: String? = null
)

data class ServerEmptyTrashMutationResult(
    val status: ServerMutationStatus,
    val response: ServerEmptyTrashResponse? = null,
    val detail: String? = null
)

class DirectServerRepository(
    private val api: DirectServerApi,
    profile: ServerEndpointProfile,
    private val cache: RecentServerJobsCache,
    private val credentialRevision: String,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    private val profile = profile.validated()
    private val cacheKey = ServerJobsCacheKey.from(this.profile)

    suspend fun connect(): Result<ServerCapabilities> = request {
        api.capabilities().requiredBody().also { capabilities ->
            requireEnvironment(capabilities.serverEnvironment)
            check(capabilities.employee.employeeId == profile.employeeId) {
                "Server employee identity does not match the selected profile"
            }
            check(capabilities.projects.any { it.projectId == profile.projectId }) {
                "Selected project is not granted to this employee"
            }
        }
    }

    suspend fun jobs(offset: Int = 0, limit: Int = 100): Result<ServerJobsSnapshot> {
        val network = request {
            api.jobs(profile.projectId, limit, offset).requiredBody().also(::validateJobs)
        }
        return network.fold(
            onSuccess = { response ->
                val cachedAt = nowEpochMillis()
                if (offset == 0) {
                    runCatching {
                        cache.write(
                            CachedServerJobs(cacheKey, credentialRevision, cachedAt, response)
                        )
                    }
                }
                Result.success(
                    ServerJobsSnapshot(response, ServerJobsSource.NETWORK, cachedAt)
                )
            },
            onFailure = { error ->
                if (!isAvailabilityFailure(error)) {
                    cache.invalidate(cacheKey)
                    Result.failure(error)
                } else {
                    val cached = if (offset == 0) {
                        cache.read(cacheKey, credentialRevision)?.takeIf {
                            runCatching { validateJobs(it.response) }.isSuccess
                        }
                    } else null
                    if (cached == null) Result.failure(error) else Result.success(
                        ServerJobsSnapshot(
                            response = cached.response,
                            source = ServerJobsSource.CACHE,
                            cachedAtEpochMillis = cached.cachedAtEpochMillis,
                            refreshError = error.message ?: "Server refresh failed"
                        )
                    )
                }
            }
        )
    }

    suspend fun files(sessionId: String, path: String = ""): Result<ServerFilesResponse> =
        request {
            api.files(sessionId, profile.projectId, path).requiredBody().also {
                requireEnvironment(it.serverEnvironment)
                check(it.projectId == profile.projectId) { "Server project mismatch" }
                check(it.sessionId == sessionId) { "Server job mismatch" }
            }
        }

    suspend fun preview(sessionId: String, path: String): Result<ResponseBody> = request {
        val response = api.preview(sessionId, profile.projectId, path)
        val environment = response.headers()["X-Server-Environment"]
        requireEnvironment(environment)
        response.requiredBody()
    }

    suspend fun receipt(sessionId: String): Result<ServerReceipt> = request {
        api.receipt(sessionId, profile.projectId).requiredBody().also {
            requireEnvironment(it.serverEnvironment)
            check(it.projectId == profile.projectId && it.sessionId == sessionId) {
                "Server receipt scope mismatch"
            }
            check(it.accessProjectId == null || it.accessProjectId == profile.projectId) {
                "Server receipt access project mismatch"
            }
            check(it.state == "COMPLETED" && it.matched) {
                "Server receipt has not independently verified the completed content"
            }
        }
    }

    suspend fun deviceEventStatus(deviceId: String): Result<ServerDeviceEventStatus> = request {
        require(deviceId.isNotBlank()) { "Device ID is required" }
        api.deviceEventStatus(profile.projectId, deviceId).requiredBody().also { response ->
            check(response.deviceId.equals(deviceId, ignoreCase = true)) {
                "Server device event status device mismatch"
            }
            check(response.window.hours >= 0 && response.window.pending >= 0 &&
                response.window.sent >= 0 && response.window.dedup >= 0 && response.window.dead >= 0
            ) { "Server device event status counts are invalid" }
            response.lastDelivered?.let {
                check(it.status.isNotBlank() && it.at.isNotBlank()) {
                    "Server device event delivery status is incomplete"
                }
            }
        }
    }

    suspend fun tickets(
        deviceId: String,
        unit: String? = null,
        status: String = "open"
    ): Result<ServerTicketsResponse> = request {
        require(deviceId.isNotBlank()) { "Device ID is required" }
        require(status in setOf("open", "all")) { "Ticket status must be open or all" }
        api.tickets(profile.projectId, deviceId, unit?.takeIf(String::isNotBlank), status)
            .requiredBody().also { response ->
                check(response.projectId == null || response.projectId == profile.projectId) {
                    "Server ticket project mismatch"
                }
                check(response.deviceId == null || response.deviceId.equals(deviceId, ignoreCase = true)) {
                    "Server ticket device mismatch"
                }
                check(response.tickets.all { it.rowId.isNotBlank() && it.title.isNotBlank() }) {
                    "Server ticket identifiers are incomplete"
                }
                check(response.tickets.map(ServerTicket::rowId).distinct().size == response.tickets.size) {
                    "Server returned duplicate ticket rows"
                }
            }
    }

    suspend fun startTicket(
        rowId: String,
        runId: String,
        deviceId: String
    ): Result<ServerTicketMutationResponse> = request {
        require(rowId.isNotBlank() && runId.isNotBlank() && deviceId.isNotBlank()) {
            "Ticket start scope is required"
        }
        ticketMutation(
            api.startTicket(rowId, profile.projectId, ServerTicketStartRequest(runId, deviceId)),
            rowId, runId, deviceId
        )
    }

    suspend fun completeTicket(
        rowId: String,
        runId: String
    ): Result<ServerTicketMutationResponse> = request {
        require(rowId.isNotBlank() && runId.isNotBlank()) { "Ticket completion scope is required" }
        ticketMutation(
            api.completeTicket(rowId, profile.projectId, ServerTicketCompleteRequest(runId)),
            rowId, runId, null
        )
    }

    suspend fun trash(offset: Int = 0, limit: Int = 200): Result<ServerTrashResponse> = request {
        api.trash(profile.projectId, limit, offset).requiredBody().let { response ->
            requireEnvironment(response.serverEnvironment)
            check(response.projectId == profile.projectId) { "Server project mismatch" }
            check(response.accessProjectId == null || response.accessProjectId == profile.projectId) {
                "Server trash access project mismatch"
            }
            check(response.total >= 0) { "Server trash total is invalid" }
            check(response.jobs.map(ServerTrashJob::sessionId).distinct().size == response.jobs.size) {
                "Server trash contains duplicate sessions"
            }
            check(response.jobs.all { job -> job.state in setOf("TRASHED", "PURGING") }) {
                "Server trash contains an invalid state"
            }
            response.copy(total = response.total.coerceAtLeast(response.jobs.size))
        }
    }

    suspend fun emptyTrash(sessionIds: List<String>): Result<ServerEmptyTrashMutationResult> {
        val requested = sessionIds.toList()
        require(requested.isNotEmpty() && requested.size <= 200 && requested.distinct().size == requested.size) {
            "Server trash empty requires 1..200 unique session IDs"
        }
        cache.invalidate(cacheKey)
        return try {
            val response = api.emptyTrash(
                profile.projectId,
                ServerEmptyTrashRequest(sessionIds = requested)
            ).requiredBody()
            validateEmptyTrash(response, requested)
            Result.success(ServerEmptyTrashMutationResult(ServerMutationStatus.CONFIRMED, response))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (isAvailabilityFailure(error)) Result.success(
                ServerEmptyTrashMutationResult(
                    ServerMutationStatus.UNKNOWN,
                    detail = error.message ?: "Server trash empty result is unknown"
                )
            ) else Result.failure(error)
        }
    }

    suspend fun moveToTrash(sessionId: String): Result<ServerMutationResult> = mutation {
        api.moveToTrash(sessionId, profile.projectId).requiredBody().also {
            validateLifecycle(it, sessionId, "TRASHED")
        }
    }

    suspend fun restore(sessionId: String): Result<ServerMutationResult> = mutation {
        api.restore(sessionId, profile.projectId).requiredBody().also {
            validateLifecycle(it, sessionId, "COMPLETED")
        }
    }

    fun logout() = cache.invalidate(cacheKey)

    private fun validateJobs(response: ServerJobsResponse) {
        requireEnvironment(response.serverEnvironment)
        check(response.employeeId == profile.employeeId) { "Server employee identity mismatch" }
        check(response.projectId == profile.projectId) { "Server project mismatch" }
        check(response.accessProjectId == null || response.accessProjectId == profile.projectId) {
            "Server access project mismatch"
        }
        check(response.jobs.all { it.projectId == profile.projectId &&
            (it.accessProjectId == null || it.accessProjectId == profile.projectId)
        }) {
            "Server returned a job outside the selected project"
        }
    }

    private fun validateLifecycle(
        response: ServerLifecycleResponse,
        sessionId: String,
        state: String
    ) {
        requireEnvironment(response.serverEnvironment)
        check(response.projectId == profile.projectId && response.sessionId == sessionId) {
            "Server lifecycle response scope mismatch"
        }
        check(response.state == state) { "Server lifecycle state mismatch" }
    }

    private fun ticketMutation(
        response: Response<ServerTicketMutationResponse>,
        rowId: String,
        runId: String,
        deviceId: String?
    ): ServerTicketMutationResponse {
        if (!response.isSuccessful) {
            val error = runCatching {
                response.errorBody()?.string()?.let {
                    Gson().fromJson(it, ServerTicketErrorResponse::class.java)
                }
            }.getOrNull()
            if (error?.rowId != null && error.rowId != rowId) ticketScopeMismatch(response.code())
            if (error?.projectId != null && error.projectId != profile.projectId) ticketScopeMismatch(response.code())
            if (error?.runId != null && error.runId != runId) ticketScopeMismatch(response.code())
            if (deviceId != null && error?.deviceId != null &&
                !error.deviceId.equals(deviceId, ignoreCase = true)
            ) ticketScopeMismatch(response.code())
            val code = error?.code ?: error?.detail?.takeIf { it.matches(TICKET_ERROR_CODE) }
            throw ServerTicketRequestException(response.code(), code, error?.message)
        }
        check(response.code() == 202) { "Server ticket report was not accepted with 202" }
        val body = response.body() ?: return ServerTicketMutationResponse()
        if (body.rowId != null && body.rowId != rowId) ticketScopeMismatch(response.code())
        if (body.projectId != null && body.projectId != profile.projectId) ticketScopeMismatch(response.code())
        if (body.runId != null && body.runId != runId) ticketScopeMismatch(response.code())
        if (deviceId != null && body.deviceId != null && !body.deviceId.equals(deviceId, true)) {
            ticketScopeMismatch(response.code())
        }
        return body
    }

    private fun ticketScopeMismatch(statusCode: Int): Nothing = throw ServerTicketRequestException(
        statusCode,
        "RESPONSE_SCOPE_MISMATCH",
        "Server ticket response scope mismatch"
    )

    private fun validateEmptyTrash(response: ServerEmptyTrashResponse, requested: List<String>) {
        requireEnvironment(response.serverEnvironment)
        check(response.projectId == profile.projectId) { "Server trash empty project mismatch" }
        check(response.accessProjectId == profile.projectId) {
            "Server trash empty access project mismatch"
        }
        check(response.results.size == requested.size) { "Server trash empty result count mismatch" }
        check(response.results.map(ServerEmptyTrashItemResult::sessionId).toSet() == requested.toSet()) {
            "Server trash empty result IDs mismatch"
        }
        check(response.results.map(ServerEmptyTrashItemResult::sessionId).distinct().size == response.results.size) {
            "Server trash empty contains duplicate results"
        }
        check(response.results.all { it.state in setOf("PURGED", "PURGING", "FAILED") }) {
            "Server trash empty result state mismatch"
        }
    }

    private fun requireEnvironment(actual: String?) {
        check(actual == profile.environment.wireValue) {
            "Server environment does not match the selected profile"
        }
    }

    private suspend inline fun <T> request(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private suspend inline fun mutation(
        crossinline block: suspend () -> ServerLifecycleResponse
    ): Result<ServerMutationResult> {
        cache.invalidate(cacheKey)
        return try {
            Result.success(
                ServerMutationResult(ServerMutationStatus.CONFIRMED, response = block())
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (isAvailabilityFailure(error)) {
                Result.success(
                    ServerMutationResult(
                        ServerMutationStatus.UNKNOWN,
                        detail = error.message ?: "Server action result is unknown"
                    )
                )
            } else {
                Result.failure(error)
            }
        }
    }

    private fun isAvailabilityFailure(error: Throwable): Boolean =
        (error is IOException && error !is SSLException) ||
            (error is ServerRequestException && error.statusCode in 500..599)
}

private fun <T> Response<T>.requiredBody(): T {
    if (!isSuccessful) throw ServerRequestException(code())
    return body() ?: throw ServerRequestException(code(), "Server returned an empty response")
}

class ServerRequestException(
    val statusCode: Int,
    message: String = "Server request failed ($statusCode)"
) : Exception(message)

private val TICKET_ERROR_CODE = Regex("[A-Z0-9_]{1,64}")

class ServerTicketRequestException(
    val statusCode: Int,
    val errorCode: String?,
    message: String?
) : Exception(message ?: errorCode ?: "Server ticket request failed ($statusCode)")
