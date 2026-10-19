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
import com.example.jetsoncontroller.data.server.ServerJob
import com.example.jetsoncontroller.data.server.ServerReceipt
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketIntegration
import com.example.jetsoncontroller.data.server.ServerTicketIntegrationState
import com.example.jetsoncontroller.data.server.ServerTicketRequestException
import com.example.jetsoncontroller.data.server.ServerProfileStore
import com.example.jetsoncontroller.data.server.TicketCacheSnapshot
import com.example.jetsoncontroller.data.server.TicketCacheStore
import com.example.jetsoncontroller.data.server.TicketReportDeliveryState
import com.example.jetsoncontroller.data.server.TicketReportStore
import com.example.jetsoncontroller.data.server.TicketReportTarget
import com.example.jetsoncontroller.data.server.TicketRunLink
import com.example.jetsoncontroller.data.server.ticketReportKey
import com.example.jetsoncontroller.data.server.ticketReportResponseIsTerminal
import com.example.jetsoncontroller.data.server.ticketReportTargetMatches
import com.example.jetsoncontroller.data.storage.RecentServerJobsCache
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveySection
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    val reportMessages: Map<String, String> = emptyMap(),
    val reportOutboxRevision: Long = 0L,
    val lastSyncedAt: String? = null,
    val isCached: Boolean = false
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
    val integration: ServerTicketIntegration?,
    val cachedAt: String
)

internal data class TicketPreparation(
    val profileId: String,
    val credentialRevision: String,
    val deviceId: String,
    val ticket: ServerTicket,
    val pipelineId: String? = null,
    val surveyProjectId: String? = null,
    val surveySectionId: String? = null
) {
    fun pendingSelection(): PendingTicketSelection? = pipelineId?.let {
        PendingTicketSelection(profileId, ticket.rowId, ticket.title, ticket.project, ticket.unit,
            deviceId, it, ticketRevision = ticket.revision)
    }
}

/** The ticket snapshot includes its row revision, even before a pipeline has been chosen. */
internal class TicketPreparationStore(private val file: File, private val gson: Gson = Gson()) {
    fun read(): TicketPreparation? = runCatching {
        if (file.isFile) gson.fromJson(file.readText(Charsets.UTF_8), TicketPreparation::class.java) else null
    }.getOrNull()

    fun save(preparation: TicketPreparation?) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(gson.toJson(preparation).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
    }
}

internal fun ticketPreparation(
    profileId: String,
    credentialRevision: String,
    deviceId: String,
    ticket: ServerTicket,
    pipelineId: String?,
    runs: List<TaskRun>
): TicketPreparation {
    val context = ticket.runId?.takeIf(String::isNotBlank)?.let { runId ->
        runs.singleOrNull { run ->
            run.runId == runId && run.contextSnapshot?.deviceId.equals(deviceId, true) &&
                (run.deviceId == null || run.deviceId.equals(deviceId, true))
        }?.contextSnapshot
    }
    return TicketPreparation(profileId, credentialRevision, deviceId, ticket, pipelineId,
        context?.surveyProjectId, context?.surveySectionId)
}

internal fun ticketPreparationMatches(
    preparation: TicketPreparation,
    profileId: String?,
    deviceId: String?,
    credentialRevision: String,
    ticket: ServerTicket?
): Boolean = preparation.profileId == profileId && preparation.deviceId.equals(deviceId, true) &&
    preparation.credentialRevision == credentialRevision && ticket != null && ticket.rowId == preparation.ticket.rowId &&
    ticket.revision == preparation.ticket.revision

internal fun ticketPreparationProject(preparation: TicketPreparation, projects: List<SurveyProject>): SurveyProject? =
    if (preparation.surveyProjectId != null) projects.singleOrNull { it.surveyProjectId == preparation.surveyProjectId }
    else projects.singleOrNull { it.label == preparation.ticket.project }

internal fun ticketPreparationSection(preparation: TicketPreparation, sections: List<SurveySection>): SurveySection? =
    if (preparation.surveySectionId != null) sections.singleOrNull {
        it.surveySectionId == preparation.surveySectionId && it.surveyProjectId == preparation.surveyProjectId
    } else sections.singleOrNull { it.label == preparation.ticket.title }

internal fun ticketPreparationContextMatches(preparation: TicketPreparation, project: SurveyProject?, section: SurveySection?): Boolean =
    project != null && section != null && section.surveyProjectId == project.surveyProjectId &&
        (if (preparation.surveyProjectId != null) preparation.surveyProjectId == project.surveyProjectId
         else preparation.ticket.project.isNullOrBlank() || preparation.ticket.project == project.label) &&
        (if (preparation.surveySectionId != null) preparation.surveySectionId == section.surveySectionId
         else preparation.ticket.title == section.label)

internal class ServerTicketViewModel(context: Context) : ViewModel() {
    private val cache = RecentServerJobsCache(context.noBackupFilesDir.resolve("server-job-cache"))
    private val profiles = ServerProfileStore(context, cache)
    private val ticketCache = TicketCacheStore(context.noBackupFilesDir.resolve("ticket-cache.json"))
    private val reportStore = TicketReportStore(context.filesDir.resolve("ticket-reports.json"), Gson())
    private val preparationStore = TicketPreparationStore(context.filesDir.resolve("ticket-preparation.json"))
    private val _preparation = MutableStateFlow(preparationStore.read())
    val preparation = _preparation.asStateFlow()
    private val reportMutex = Mutex()
    private val _uiState = MutableStateFlow(ServerTicketUiState())
    val uiState = _uiState.asStateFlow()
    private val snapshotsByScope = mutableMapOf<String, CachedTicketSnapshot>()
    private var refreshJob: Job? = null
    private var preparationJob: Job? = null

    fun selectUnit(unit: String?) {
        val current = _uiState.value.copy(selectedUnit = unit)
        _uiState.value = current
        current.lastSyncedAt?.let { persistTicketSnapshot(current, it) }
    }

    private fun persistTicketSnapshot(state: ServerTicketUiState, cachedAt: String) {
        val profileId = state.profileId ?: return
        val deviceId = state.deviceId ?: return
        runCatching {
            ticketCache.save(TicketCacheSnapshot(
                profileId = profileId,
                deviceId = deviceId,
                tickets = state.tickets,
                units = state.units,
                deviceUnit = state.deviceUnit,
                selectedUnit = state.selectedUnit,
                capabilities = state.capabilities,
                integration = state.integration,
                cachedAt = cachedAt
            ))
        }
    }

    private fun ticketCacheState(snapshot: TicketCacheSnapshot): ServerTicketUiState = ServerTicketUiState(
        profileId = snapshot.profileId,
        deviceId = snapshot.deviceId,
        tickets = snapshot.tickets,
        units = snapshot.units,
        deviceUnit = snapshot.deviceUnit,
        selectedUnit = snapshot.selectedUnit,
        integration = snapshot.integration,
        capabilities = snapshot.capabilities,
        lastSyncedAt = snapshot.cachedAt,
        isCached = true
    )

    private fun ticketCacheStatusCopy(timestamp: String): String? =
        formatTaskTime(timestamp)?.let { "마지막 동기화 $it" }

    fun refresh(profileId: String, deviceId: String) {
        if (profileId.isBlank() || deviceId.isBlank()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val previous = _uiState.value
            val scopeKey = ticketScopeKey(profileId, deviceId)
            val sameScope = previous.profileId == profileId && previous.deviceId.equals(deviceId, ignoreCase = true)
            val memoryCached = snapshotsByScope[scopeKey]
            val diskCached = ticketCache.read(profileId, deviceId)
            val cached = memoryCached ?: diskCached?.let {
                CachedTicketSnapshot(
                    credentialRevision = "",
                    tickets = it.tickets,
                    units = it.units,
                    deviceUnit = it.deviceUnit,
                    selectedUnit = it.selectedUnit,
                    capabilities = it.capabilities,
                    integration = it.integration,
                    cachedAt = it.cachedAt
                ).also { snapshot -> snapshotsByScope[scopeKey] = snapshot }
            }
            val scoped = when {
                sameScope && previous.lastSyncedAt != null -> previous
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
                    lastSyncedAt = cached.cachedAt,
                    isCached = true,
                    reportMessages = emptyMap(),
                    quietError = null
                )
                sameScope -> previous
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
                    (current.credentialRevision.isNullOrBlank() ||
                        current.credentialRevision == connection.credentialRevision)
                val selected = if (preserveSelection &&
                    (current.selectedUnit == null || current.selectedUnit in responseUnits)
                ) current.selectedUnit else defaultTicketUnit(response.deviceUnit, responseUnits)
                responseTickets.forEach { reportStore.saveServerRunLink(profileId, deviceId, it) }
                val conflictMessages = responseTickets.mapNotNull { ticket ->
                    ticketConflictCopy(ticket)?.let { ticket.rowId to it }
                }.toMap()
                conflictMessages.keys.forEach(reportStore::markConflict)
                val employeeRoles = current.employeeRoles + (profileId to capabilities.employee.role)
                val syncedAt = Instant.now().toString()
                snapshotsByScope[scopeKey] = CachedTicketSnapshot(
                    credentialRevision = connection.credentialRevision,
                    tickets = responseTickets,
                    units = responseUnits.distinct(),
                    deviceUnit = response.deviceUnit,
                    selectedUnit = selected,
                    capabilities = capabilities,
                    integration = response.integration,
                    cachedAt = syncedAt
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
                    reportMessages = current.reportMessages + conflictMessages,
                    lastSyncedAt = syncedAt,
                    isCached = false
                )
                persistTicketSnapshot(_uiState.value, syncedAt)
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
                val fallback = (if (same) current else ServerTicketUiState(
                    profileId = profileId, deviceId = deviceId,
                    employeeRoles = current.employeeRoles
                )).copy(
                    isLoading = false,
                    isCached = same && current.lastSyncedAt != null,
                    quietError = if (same) current.lastSyncedAt?.let(::ticketCacheStatusCopy)
                        ?: "티켓 목록을 새로고침하지 못했습니다. 저장된 목록을 표시합니다."
                    else "티켓 목록을 새로고침하지 못했습니다. 저장된 목록을 표시합니다."
                )
                _uiState.value = fallback
                retryPendingReports()
            }
        }
    }

    fun prepareTicket(
        profileId: String,
        deviceId: String,
        pipelineId: String?,
        ticket: ServerTicket,
        runs: List<TaskRun> = emptyList(),
        onReady: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val previousDevice = _preparation.value?.deviceId ?: deviceId
                check(reportStore.pendingSelectionForDevice(previousDevice)?.clientRequestId == null)
                val connection = profiles.connection(profileId) ?: error("Missing profile")
                val prepared = ticketPreparation(profileId, connection.credentialRevision, deviceId,
                    ticket, pipelineId, runs)
                preparationStore.save(prepared)
                _preparation.value = prepared
                prepared.pendingSelection()?.let(reportStore::savePendingSelection)
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
        reportStore.pendingSelection(deviceId, pipelineId) ?: _preparation.value?.takeIf {
            it.deviceId.equals(deviceId, true) && it.pipelineId == pipelineId
        }?.pendingSelection()

    /** Rechecks the saved row revision and server credentials before choosing or starting. */
    fun usePreparedTicket(profileId: String?, deviceId: String?, pipelineId: String, onReady: () -> Unit) {
        val prepared = _preparation.value ?: return
        if (prepared.profileId != profileId || !prepared.deviceId.equals(deviceId, true)) {
            setReportMessage(prepared.ticket.rowId, "선택한 장치와 서버에서 티켓을 다시 선택하세요.")
            return
        }
        if (preparationJob?.isActive == true ||
            reportStore.pendingSelectionForDevice(prepared.deviceId)?.clientRequestId != null) return
        preparationJob = viewModelScope.launch {
            try {
                val connection = profileId?.let { profiles.connection(it) } ?: error("Missing profile")
                check(connection.credentialRevision == prepared.credentialRevision)
                val repo = repository(connection)
                val capabilities = repo.connect().getOrThrow()
                val ticket = repo.tickets(prepared.deviceId).getOrThrow().tickets.orEmpty()
                    .singleOrNull { it.rowId == prepared.ticket.rowId }
                check(ticketPreparationMatches(prepared, profileId, deviceId, connection.credentialRevision, ticket))
                check(ticket != null && ticketStartAvailability(capabilities.employee.role, ticket).enabled)
                if (_preparation.value != prepared ||
                    reportStore.pendingSelectionForDevice(prepared.deviceId)?.clientRequestId != null) return@launch
                val chosen = prepared.copy(pipelineId = pipelineId)
                preparationStore.save(chosen)
                reportStore.savePendingSelection(requireNotNull(chosen.pendingSelection()))
                _preparation.value = chosen
                onReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                setReportMessage(prepared.ticket.rowId, "티켓 정보가 바뀌었거나 확인할 수 없습니다. 작업에서 티켓을 다시 선택하세요.")
            }
        }
    }

    fun clearPendingSelection(deviceId: String, pipelineId: String) {
        reportStore.clearPendingSelection(deviceId, pipelineId)
        if (_preparation.value?.let { it.deviceId.equals(deviceId, true) && it.pipelineId == pipelineId } == true) {
            preparationStore.save(null)
            _preparation.value = null
        }
    }

    fun clearPendingSelectionIfUnarmed(deviceId: String, pipelineId: String? = null) {
        val pending = reportStore.pendingSelectionForDevice(deviceId)
        if (pending == null) {
            if (_preparation.value?.let { it.deviceId.equals(deviceId, true) &&
                    (pipelineId == null || it.pipelineId == pipelineId) } == true) {
                preparationStore.save(null)
                _preparation.value = null
            }
            return
        }
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
                    val selectionToBind = reportStore.pendingSelection(deviceId, pipelineId)?.takeIf {
                        it.clientRequestId != null && it.clientRequestId == clientRequestId
                    }
                    val link = reportStore.bindPendingSelection(runId, deviceId, pipelineId, clientRequestId)
                        ?: reportStore.link(runId)
                        ?: return@withLock
                    if (selectionToBind != null && _preparation.value?.let { it.ticket.rowId == link.rowId &&
                            it.deviceId.equals(deviceId, true) && it.pipelineId == pipelineId } == true) {
                        runCatching { preparationStore.save(null) }.onSuccess { _preparation.value = null }
                    }
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
                        createdAt = Instant.now().toString(),
                        ticketRevision = link.ticketRevision
                    )
                    reportStore.enqueue(report)
                    notifyReportOutboxChanged()
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
            val existing = reportStore.link(runId)
            if (existing != null && !existing.deviceId.equals(deviceId, ignoreCase = true)) return null
            if (existing?.ticketRevision != null) return existing
            if (!_uiState.value.deviceId.equals(deviceId, ignoreCase = true)) return existing
            val ticket = _uiState.value.tickets.firstOrNull {
                it.runId == runId && (existing == null || it.rowId == existing.rowId)
            } ?: return existing
            reportStore.saveServerRunLink(
                existing?.profileId ?: _uiState.value.profileId ?: return null,
                deviceId,
                ticket
            ) ?: existing
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
                    if (link.ticketRevision.isNullOrBlank()) {
                        setReportMessage(link.rowId, ticketReportErrorCopy("REPORT_TARGET_CHANGED"))
                        return@withLock
                    }
                    val connection = profiles.connection(link.profileId) ?: run {
                        setReportMessage(link.rowId, "서버에 연결되면 보고할 수 있습니다.")
                        return@withLock
                    }
                    val repo = repository(connection)
                    val currentTicket = repo.tickets(link.deviceId, status = "all").getOrThrow()
                        .tickets.orEmpty().singleOrNull { it.rowId == link.rowId }
                    if (currentTicket == null || link.ticketRevision.isNullOrBlank() ||
                        currentTicket.revision != link.ticketRevision ||
                        ticketConflictCopy(currentTicket) != null
                    ) {
                        setReportMessage(link.rowId, ticketReportErrorCopy("REPORT_TARGET_CHANGED"))
                        return@withLock
                    }
                    val receipt = findVerifiedReceipt(repo, run)
                    if (receipt == null) {
                        setReportMessage(link.rowId, ticketReportErrorCopy("REPORT_TARGET_CHANGED"))
                        return@withLock
                    }
                    val target = TicketReportTarget(
                        profileId = link.profileId,
                        rowId = link.rowId,
                        ticketRevision = link.ticketRevision,
                        runId = run.runId,
                        receiptId = receipt.sessionId
                    )
                    if (!ticketReportTargetMatches(
                            target = target,
                            profileId = connection.profile.profileId,
                            ticket = currentTicket,
                            runId = run.runId,
                            deviceId = link.deviceId,
                            receiptId = receipt.sessionId,
                            receipt = receipt,
                            requireReceipt = true
                        )
                    ) {
                        setReportMessage(link.rowId, ticketReportErrorCopy("REPORT_TARGET_CHANGED"))
                        return@withLock
                    }
                    val report = PendingTicketReport(
                        key = key,
                        rowId = link.rowId,
                        runId = run.runId,
                        deviceId = link.deviceId,
                        profileId = link.profileId,
                        createdAt = Instant.now().toString(),
                        ticketRevision = target.ticketRevision,
                        receiptId = target.receiptId
                    )
                    reportStore.enqueue(report)
                    notifyReportOutboxChanged()
                    sendPending(report)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                setReportMessage(link.rowId, "서버에 연결되면 보고할 수 있습니다.")
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

    fun reportDeliveryState(runId: String, completed: Boolean): TicketReportDeliveryState =
        reportStore.deliveryState(ticketReportKey(runId, completed))

    fun ticketReportDeliveryState(rowId: String): TicketReportDeliveryState {
        for (link in reportStore.linksForRow(rowId).asReversed()) {
            for (completed in listOf(true, false)) {
                val state = reportStore.deliveryState(ticketReportKey(link.runId, completed))
                if (state != TicketReportDeliveryState.NONE) return state
            }
        }
        return reportStore.pendingReportsForRow(rowId).lastOrNull()?.let {
            reportStore.deliveryState(it.key)
        } ?: TicketReportDeliveryState.NONE
    }

    /** Retries one failed outbox entry without changing its saved ticket/run/receipt target. */
    fun retryReport(runId: String, completed: Boolean) {
        val key = ticketReportKey(runId, completed)
        if (reportStore.deliveryState(key) != TicketReportDeliveryState.FAILED) return
        viewModelScope.launch {
            reportMutex.withLock {
                val report = reportStore.pendingReport(key)
                    ?.takeIf { it.lastAttemptFailed } ?: return@withLock
                sendPending(report)
            }
        }
    }

    /** Lets a ticket row retry the newest transiently failed report attached to it. */
    fun retryTicketReport(rowId: String) {
        val report = reportStore.pendingReportsForRow(rowId).lastOrNull {
            reportStore.deliveryState(it.key) == TicketReportDeliveryState.FAILED
        } ?: return
        retryReport(report.runId, report.key.endsWith(":completed"))
    }

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
        ?: when (ticketReportDeliveryState(rowId)) {
            TicketReportDeliveryState.PENDING -> "보고 대기"
            TicketReportDeliveryState.FAILED -> "보고 실패"
            else -> null
        }
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
            if (reportStore.markRetrying(report.key)) notifyReportOutboxChanged()
            if (reportStore.isConflict(report.rowId)) {
                reportStore.applyResponse(report.key, 409, "HUMAN_CHANGED")
                setReportMessage(report.rowId, ticketReportErrorCopy("HUMAN_CHANGED"))
                return
            }
            val connection = profiles.connection(report.profileId) ?: run {
                if (reportStore.markFailed(report.key)) notifyReportOutboxChanged()
                return
            }
            val repo = repository(connection)
            val isCompletion = report.key.endsWith(":completed")
            val currentTicket = repo.tickets(report.deviceId, status = "all").getOrThrow()
                .tickets.orEmpty().singleOrNull { it.rowId == report.rowId }
            val receipt = if (isCompletion && !report.receiptId.isNullOrBlank()) {
                repo.receipt(report.receiptId).getOrThrow()
            } else null
            val targetMatches = (!isCompletion || !report.receiptId.isNullOrBlank()) &&
                ticketReportTargetMatches(
                    target = report.target(),
                    profileId = connection.profile.profileId,
                    ticket = currentTicket,
                    runId = report.runId,
                    deviceId = report.deviceId,
                    receiptId = report.receiptId,
                    receipt = receipt,
                    requireReceipt = isCompletion
                ) && currentTicket?.let { ticketConflictCopy(it) } == null
            if (!targetMatches) {
                reportStore.applyResponse(report.key, 409, "REPORT_TARGET_CHANGED")
                setReportMessage(report.rowId, ticketReportErrorCopy("REPORT_TARGET_CHANGED"))
                return
            }
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
                } else if (reportStore.markFailed(report.key)) {
                    notifyReportOutboxChanged()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            if (reportStore.markFailed(report.key)) notifyReportOutboxChanged()
        }
    }

    private suspend fun findVerifiedReceipt(
        repo: DirectServerRepository,
        run: PipelineRun
    ): ServerReceipt? {
        var offset = 0
        while (true) {
            val page = repo.jobs(offset = offset, limit = 100).getOrThrow().response
            val candidates = page.jobs.filter { serverJobMatchesRun(it, run) }
                .sortedByDescending { it.completedAt ?: it.updatedAt }
            for (job in candidates) {
                val receiptResult = repo.receipt(job.sessionId)
                receiptResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                val receipt = receiptResult.getOrNull() ?: continue
                if (serverReceiptMatchesRun(job, receipt, run)) return receipt
            }
            val nextOffset = page.nextOffset ?: return null
            if (nextOffset <= offset) return null
            offset = nextOffset
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
        val updated = current.copy(tickets = current.tickets.map { ticket ->
            if (ticket.rowId == rowId) ticket.copy(
                status = ticket.status?.copy(name = "진행 중", category = "IN_PROGRESS")
            ) else ticket
        })
        _uiState.value = updated
        updated.lastSyncedAt?.let { persistTicketSnapshot(updated, it) }
    }

    private fun setReportMessage(rowId: String, message: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            reportMessages = current.reportMessages + (rowId to message),
            reportOutboxRevision = current.reportOutboxRevision + 1
        )
    }

    private fun clearReportMessage(rowId: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            reportMessages = current.reportMessages - rowId,
            reportOutboxRevision = current.reportOutboxRevision + 1
        )
    }

    private fun notifyReportOutboxChanged() {
        val current = _uiState.value
        _uiState.value = current.copy(reportOutboxRevision = current.reportOutboxRevision + 1)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ServerTicketViewModel(context.applicationContext) as T
    }
}

private fun ticketScopeKey(profileId: String, deviceId: String): String =
    "$profileId|${deviceId.lowercase(java.util.Locale.ROOT)}"

private fun serverJobMatchesRun(job: ServerJob, run: PipelineRun): Boolean =
    job.state.equals("COMPLETED", ignoreCase = true) &&
        job.deviceId.equals(run.deviceId, ignoreCase = true) &&
        uploadContextMatches(run.uploadContext, job.surveyContext)

private fun serverReceiptMatchesRun(job: ServerJob, receipt: ServerReceipt, run: PipelineRun): Boolean =
    receipt.sessionId == job.sessionId && receipt.clientJobId == job.clientJobId &&
        receipt.projectId == job.projectId && receipt.sourceName == job.sourceName &&
        receipt.state.equals("COMPLETED", ignoreCase = true) && receipt.matched &&
        uploadContextMatches(run.uploadContext, receipt.surveyContext)

private fun uploadContextMatches(
    expected: com.example.jetsoncontroller.model.UploadContext,
    actual: com.example.jetsoncontroller.model.UploadContext?
): Boolean = actual != null &&
    actual.runId == expected.runId &&
    actual.deviceId.equals(expected.deviceId, ignoreCase = true) &&
    actual.pipelineId == expected.pipelineId &&
    actual.surveyProjectId == expected.surveyProjectId &&
    actual.surveySectionId == expected.surveySectionId &&
    actual.sourceRevision == expected.sourceRevision &&
    actual.configSha256 == expected.configSha256 &&
    actual.outputId == expected.outputId

internal fun ticketReportErrorCopy(code: String?): String = when (code) {
    "TICKET_NOT_OPEN" -> "티켓이 열린 상태가 아닙니다. GEO& Work에서 확인해 주세요."
    "TICKET_IN_PROGRESS" -> "다른 수집이 진행 중입니다. GEO& Work에서 확인해 주세요."
    "TICKET_BLOCKED" -> TICKET_BLOCKED_COPY
    "UPLOAD_NOT_CONFIRMED" -> "서버 수신 확인 뒤에 완료를 보고할 수 있습니다. 티켓은 '진행 중'으로 둡니다."
    "HUMAN_CHANGED" -> "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요."
    "REPORT_TARGET_CHANGED" -> "티켓 또는 서버 수신 정보가 바뀌었습니다. GEO& Work에서 확인해 주세요."
    else -> code?.let { "티켓 보고를 처리하지 못했습니다. ($it)" } ?: "티켓 보고를 처리하지 못했습니다."
}
