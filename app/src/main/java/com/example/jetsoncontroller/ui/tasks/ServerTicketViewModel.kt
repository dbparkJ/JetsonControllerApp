package com.example.jetsoncontroller.ui.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.server.DirectServerApiFactory
import com.example.jetsoncontroller.data.server.DirectServerRepository
import com.example.jetsoncontroller.data.server.PendingTicketReport
import com.example.jetsoncontroller.data.server.PendingTicketSelection
import com.example.jetsoncontroller.data.server.ServerCapabilities
import com.example.jetsoncontroller.data.server.ServerConnection
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketIntegration
import com.example.jetsoncontroller.data.server.ServerTicketIntegrationState
import com.example.jetsoncontroller.data.server.ServerTicketRequestException
import com.example.jetsoncontroller.data.server.ServerProfileStore
import com.example.jetsoncontroller.data.server.TicketReportStore
import com.example.jetsoncontroller.data.server.TicketRunLink
import com.example.jetsoncontroller.data.server.ticketReportKey
import com.example.jetsoncontroller.data.server.ticketReportResponseIsTerminal
import com.example.jetsoncontroller.data.storage.RecentServerJobsCache
import com.example.jetsoncontroller.model.PipelineRun
import com.google.gson.Gson
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ServerTicketUiState(
    val profileId: String? = null,
    val deviceId: String? = null,
    val credentialRevision: String? = null,
    val tickets: List<ServerTicket> = emptyList(),
    val units: List<String> = emptyList(),
    val deviceUnit: String? = null,
    val selectedUnit: String? = null,
    val integration: ServerTicketIntegration? = null,
    val capabilities: ServerCapabilities? = null,
    val employeeRoles: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val quietError: String? = null,
    val reportMessages: Map<String, String> = emptyMap()
) {
    val role: String? get() = capabilities?.employee?.role
    val displayName: String? get() = capabilities?.employee?.displayName
    val visibleTickets: List<ServerTicket> get() = filterTicketsByUnit(tickets, selectedUnit)
}

private data class CachedTicketSnapshot(
    val credentialRevision: String,
    val tickets: List<ServerTicket>,
    val units: List<String>,
    val deviceUnit: String?,
    val selectedUnit: String?,
    val capabilities: ServerCapabilities?,
    val integration: ServerTicketIntegration?
)

internal class ServerTicketViewModel(context: Context) : ViewModel() {
    private val cache = RecentServerJobsCache(context.noBackupFilesDir.resolve("server-job-cache"))
    private val profiles = ServerProfileStore(context, cache)
    private val reportStore = TicketReportStore(context.filesDir.resolve("ticket-reports.json"), Gson())
    private val reportMutex = Mutex()
    private val _uiState = MutableStateFlow(ServerTicketUiState())
    val uiState = _uiState.asStateFlow()
    private val snapshotsByScope = mutableMapOf<String, CachedTicketSnapshot>()
    private var refreshJob: Job? = null

    fun selectUnit(unit: String?) {
        val current = _uiState.value.copy(selectedUnit = unit)
        _uiState.value = current
        val profileId = current.profileId ?: return
        val deviceId = current.deviceId ?: return
        val credentialRevision = current.credentialRevision ?: return
        snapshotsByScope[ticketScopeKey(profileId, deviceId)] = CachedTicketSnapshot(
            credentialRevision,
            current.tickets,
            current.units,
            current.deviceUnit,
            unit,
            current.capabilities,
            current.integration
        )
    }

    fun refresh(profileId: String, deviceId: String) {
        if (profileId.isBlank() || deviceId.isBlank()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val previous = _uiState.value
            val scopeKey = ticketScopeKey(profileId, deviceId)
            val sameScope = previous.profileId == profileId && previous.deviceId.equals(deviceId, ignoreCase = true)
            val cached = snapshotsByScope[scopeKey]
            val scoped = when {
                sameScope -> previous
                cached != null -> previous.copy(
                    profileId = profileId,
                    deviceId = deviceId,
                    credentialRevision = cached.credentialRevision,
                    tickets = cached.tickets,
                    units = cached.units,
                    deviceUnit = cached.deviceUnit,
                    selectedUnit = cached.selectedUnit,
                    capabilities = cached.capabilities,
                    integration = cached.integration,
                    reportMessages = emptyMap(),
                    quietError = null
                )
                else -> ServerTicketUiState(
                    profileId = profileId,
                    deviceId = deviceId,
                    employeeRoles = previous.employeeRoles,
                    isLoading = true
                )
            }
            _uiState.value = scoped.copy(isLoading = true, quietError = null)
            try {
                val connection = profiles.connection(profileId)
                    ?: throw IllegalStateException("Saved server profile is unavailable")
                val repo = repository(connection)
                val capabilities = repo.connect().getOrThrow()
                val response = repo.tickets(deviceId).getOrThrow()
                val responseTickets = response.tickets.orEmpty()
                val responseUnits = response.units.orEmpty()
                val current = _uiState.value
                val preserveSelection = (sameScope || cached != null) &&
                    current.credentialRevision == connection.credentialRevision
                val selected = if (preserveSelection &&
                    (current.selectedUnit == null || current.selectedUnit in responseUnits)
                ) current.selectedUnit else defaultTicketUnit(response.deviceUnit, responseUnits)
                responseTickets.forEach { reportStore.saveServerRunLink(profileId, deviceId, it) }
                val conflictMessages = responseTickets.mapNotNull { ticket ->
                    ticketConflictCopy(ticket)?.let { ticket.rowId to it }
                }.toMap()
                conflictMessages.keys.forEach(reportStore::markConflict)
                val employeeRoles = current.employeeRoles + (profileId to capabilities.employee.role)
                snapshotsByScope[scopeKey] = CachedTicketSnapshot(
                    credentialRevision = connection.credentialRevision,
                    tickets = responseTickets,
                    units = responseUnits.distinct(),
                    deviceUnit = response.deviceUnit,
                    selectedUnit = selected,
                    capabilities = capabilities,
                    integration = response.integration
                )
                _uiState.value = current.copy(
                    profileId = profileId,
                    deviceId = deviceId,
                    credentialRevision = connection.credentialRevision,
                    tickets = responseTickets,
                    units = responseUnits.distinct(),
                    deviceUnit = response.deviceUnit,
                    selectedUnit = selected,
                    integration = response.integration,
                    capabilities = capabilities,
                    employeeRoles = employeeRoles,
                    isLoading = false,
                    quietError = when (response.integration?.normalizedState) {
                        null, ServerTicketIntegrationState.ACTIVE -> null
                        ServerTicketIntegrationState.NOT_CONFIGURED -> "GEO& Work 연동이 설정되지 않았습니다."
                        ServerTicketIntegrationState.ERROR -> response.integration?.detail
                            ?.takeIf(String::isNotBlank)
                            ?.replace(Regex("\\s+"), " ")
                            ?.take(160) ?: "GEO& Work 연동 상태를 확인해 주세요."
                        ServerTicketIntegrationState.UNKNOWN -> "GEO& Work 연동 상태를 확인할 수 없습니다."
                    },
                    reportMessages = current.reportMessages + conflictMessages
                )
                if (conflictMessages.isNotEmpty()) {
                    reportStore.pendingReports()
                        .filter { it.rowId in conflictMessages.keys }
                        .forEach { reportStore.applyResponse(it.key, 409, "HUMAN_CHANGED") }
                }
                retryPendingReports()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                val current = _uiState.value
                val same = current.profileId == profileId && current.deviceId.equals(deviceId, true)
                _uiState.value = (if (same) current else ServerTicketUiState(
                    profileId = profileId, deviceId = deviceId,
                    employeeRoles = current.employeeRoles
                )).copy(
                    isLoading = false,
                    quietError = "티켓 목록을 새로고침하지 못했습니다. 저장된 목록을 표시합니다."
                )
                retryPendingReports()
            }
        }
    }

    fun prepareTicket(
        profileId: String,
        deviceId: String,
        pipelineId: String,
        ticket: ServerTicket,
        onReady: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                reportStore.savePendingSelection(
                    PendingTicketSelection(
                        profileId = profileId,
                        rowId = ticket.rowId,
                        title = ticket.title,
                        project = ticket.project,
                        unit = ticket.unit,
                        deviceId = deviceId,
                        pipelineId = pipelineId
                    )
                )
                onReady()
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    reportMessages = _uiState.value.reportMessages +
                        (ticket.rowId to "티켓 선택을 저장하지 못했습니다.")
                )
            }
        }
    }

    fun pendingSelection(deviceId: String, pipelineId: String): PendingTicketSelection? =
        reportStore.pendingSelection(deviceId, pipelineId)

    fun clearPendingSelection(deviceId: String, pipelineId: String) {
        reportStore.clearPendingSelection(deviceId, pipelineId)
    }

    fun clearPendingSelectionIfUnarmed(deviceId: String, pipelineId: String? = null) {
        val pending = reportStore.pendingSelectionForDevice(deviceId) ?: return
        if (pending.clientRequestId == null && (pipelineId == null || pending.pipelineId == pipelineId)) {
            clearPendingSelection(deviceId, pending.pipelineId)
        }
    }

    fun armPendingSelection(deviceId: String, pipelineId: String, clientRequestId: String) {
        runCatching { reportStore.armPendingSelection(deviceId, pipelineId, clientRequestId) }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    reportMessages = _uiState.value.reportMessages +
                        (reportStore.pendingSelection(deviceId, pipelineId)?.rowId.orEmpty() to
                            "티켓 연결 정보를 저장하지 못했습니다.")
                )
            }
    }

    fun observeRun(run: PipelineRun?, clientRequestId: String?) {
        run ?: return
        observeRunIdentity(run.runId, run.deviceId, run.pipelineId, clientRequestId)
    }

    fun observeRunIdentity(
        runId: String,
        deviceId: String,
        pipelineId: String,
        clientRequestId: String?
    ) {
        if (runId.isBlank() || deviceId.isBlank() || pipelineId.isBlank()) return
        viewModelScope.launch {
            try {
                reportMutex.withLock {
                    val link = reportStore.bindPendingSelection(runId, deviceId, pipelineId, clientRequestId)
                        ?: reportStore.link(runId)
                        ?: return@withLock
                    if (_uiState.value.tickets.firstOrNull { it.rowId == link.rowId }
                            ?.let { ticketConflictCopy(it) != null } == true
                        || reportStore.isConflict(link.rowId)
                    ) return@withLock
                    val key = ticketReportKey(runId, completed = false)
                    if (reportStore.isSettled(key)) return@withLock
                    val report = PendingTicketReport(
                        key = key,
                        rowId = link.rowId,
                        runId = runId,
                        deviceId = link.deviceId,
                        profileId = link.profileId,
                        createdAt = Instant.now().toString()
                    )
                    reportStore.enqueue(report)
                    sendPending(report)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                val rowId = reportStore.link(runId)?.rowId ?: return@launch
                setReportMessage(rowId, "티켓 시작 보고를 저장하지 못했습니다.")
            }
        }
    }

    fun linkForRun(runId: String, deviceId: String): TicketRunLink? {
        return try {
            reportStore.link(runId)?.let { return it }
            val ticket = _uiState.value.tickets.firstOrNull { it.runId == runId } ?: return null
            reportStore.saveServerRunLink(
                _uiState.value.profileId ?: return null,
                deviceId,
                ticket
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun reportCompletion(run: PipelineRun) {
        val link = linkForRun(run.runId, run.deviceId) ?: return
        viewModelScope.launch {
            try {
                reportMutex.withLock {
                    val key = ticketReportKey(run.runId, completed = true)
                    if (reportStore.isSettled(key) || ticketConflictForRun(run.runId) ||
                        ticketStartRejected(run.runId) ||
                        reportStore.pendingReports().any { it.key == key }
                    ) return@withLock
                    val report = PendingTicketReport(
                        key = key,
                        rowId = link.rowId,
                        runId = run.runId,
                        deviceId = link.deviceId,
                        profileId = link.profileId,
                        createdAt = Instant.now().toString()
                    )
                    reportStore.enqueue(report)
                    sendPending(report)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                setReportMessage(link.rowId, "티켓 완료 보고를 저장하지 못했습니다.")
            }
        }
    }

    fun retryPendingReports() {
        viewModelScope.launch {
            reportMutex.withLock {
                for (report in reportStore.pendingReports()) sendPending(report)
            }
        }
    }

    fun pendingReport(runId: String, completed: Boolean): Boolean = reportStore.pendingReports()
        .any { it.key == ticketReportKey(runId, completed) }

    fun reportSettled(runId: String, completed: Boolean): Boolean =
        reportStore.isSettled(ticketReportKey(runId, completed))

    fun ticketStartRejected(runId: String): Boolean {
        val key = ticketReportKey(runId, completed = false)
        return reportStore.responseCode(key)?.let { it in 400..499 } == true ||
            reportStore.errorCode(key) != null
    }

    fun ticketStartFailureMessage(runId: String): String? = reportStore.errorCode(
        ticketReportKey(runId, completed = false)
    )?.let(::ticketReportErrorCopy)

    fun ticketMessage(rowId: String): String? = _uiState.value.reportMessages[rowId]
        ?: reportStore.linksForRow(rowId).asReversed().firstNotNullOfOrNull { link ->
            reportStore.errorCode(ticketReportKey(link.runId, completed = true))
                ?: reportStore.errorCode(ticketReportKey(link.runId, completed = false))
        }?.let(::ticketReportErrorCopy)
        ?: "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요.".takeIf {
            reportStore.isConflict(rowId)
        }

    fun ticketConflictForRun(runId: String): Boolean {
        return try {
            val link = reportStore.link(runId) ?: _uiState.value.tickets.firstOrNull { it.runId == runId }
                ?.let { ticket ->
                    _uiState.value.profileId?.let { profileId ->
                        reportStore.saveServerRunLink(profileId, _uiState.value.deviceId.orEmpty(), ticket)
                    }
                } ?: return false
            reportStore.isConflict(link.rowId) ||
                _uiState.value.tickets.firstOrNull { it.rowId == link.rowId }
                    ?.let { ticketConflictCopy(it) != null } == true
        } catch (_: Throwable) {
            false
        }
    }

    fun reportRole(profileId: String): String? = _uiState.value.capabilities
        ?.employee?.role?.takeIf { _uiState.value.profileId == profileId }
        ?: _uiState.value.employeeRoles[profileId]

    fun refreshCapabilities(profileId: String) {
        viewModelScope.launch {
            try {
                val connection = profiles.connection(profileId) ?: return@launch
                val capabilities = repository(connection).connect().getOrThrow()
                val current = _uiState.value
                _uiState.value = current.copy(
                    employeeRoles = current.employeeRoles + (profileId to capabilities.employee.role),
                    capabilities = capabilities.takeIf { current.profileId == profileId } ?: current.capabilities
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                Unit
            }
        }
    }

    private suspend fun sendPending(report: PendingTicketReport) {
        try {
            if (reportStore.isConflict(report.rowId)) {
                reportStore.applyResponse(report.key, 409, "HUMAN_CHANGED")
                setReportMessage(report.rowId, ticketReportErrorCopy("HUMAN_CHANGED"))
                return
            }
            val connection = profiles.connection(report.profileId) ?: return
            val repo = repository(connection)
            val result = if (report.key.endsWith(":completed")) {
                repo.completeTicket(report.rowId, report.runId)
            } else {
                repo.startTicket(report.rowId, report.runId, report.deviceId)
            }
            result.onSuccess {
                reportStore.applyResponse(report.key, 202)
                if (!report.key.endsWith(":completed")) markTicketInProgress(report.rowId)
                clearReportMessage(report.rowId)
            }.onFailure { error ->
                val status = (error as? ServerTicketRequestException)?.statusCode
                if (ticketReportResponseIsTerminal(status)) {
                    val code = (error as? ServerTicketRequestException)?.errorCode
                    reportStore.applyResponse(report.key, status, code)
                    if (code == "HUMAN_CHANGED") reportStore.markConflict(report.rowId)
                    setReportMessage(report.rowId, ticketReportErrorCopy(code))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // A missing profile, network failure, or server failure leaves the durable outbox intact.
        }
    }

    private fun repository(connection: ServerConnection): DirectServerRepository = DirectServerRepository(
        DirectServerApiFactory.create(connection.profile, connection.employeeToken),
        connection.profile,
        cache,
        connection.credentialRevision
    )

    private fun markTicketInProgress(rowId: String) {
        val current = _uiState.value
        _uiState.value = current.copy(tickets = current.tickets.map { ticket ->
            if (ticket.rowId == rowId) ticket.copy(
                status = ticket.status?.copy(name = "진행 중", category = "IN_PROGRESS")
            ) else ticket
        })
    }

    private fun setReportMessage(rowId: String, message: String) {
        _uiState.value = _uiState.value.copy(reportMessages = _uiState.value.reportMessages + (rowId to message))
    }

    private fun clearReportMessage(rowId: String) {
        _uiState.value = _uiState.value.copy(reportMessages = _uiState.value.reportMessages - rowId)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ServerTicketViewModel(context.applicationContext) as T
    }
}

private fun ticketScopeKey(profileId: String, deviceId: String): String =
    "$profileId|${deviceId.lowercase(java.util.Locale.ROOT)}"

internal fun ticketReportErrorCopy(code: String?): String = when (code) {
    "TICKET_NOT_OPEN" -> "티켓이 열린 상태가 아닙니다. GEO& Work에서 확인해 주세요."
    "TICKET_IN_PROGRESS" -> "다른 수집이 진행 중입니다. GEO& Work에서 확인해 주세요."
    "TICKET_BLOCKED" -> TICKET_BLOCKED_COPY
    "UPLOAD_NOT_CONFIRMED" -> "서버 수신 확인 뒤에 완료를 보고할 수 있습니다. 티켓은 '진행 중'으로 둡니다."
    "HUMAN_CHANGED" -> "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요."
    else -> code?.let { "티켓 보고를 처리하지 못했습니다. ($it)" } ?: "티켓 보고를 처리하지 못했습니다."
}
