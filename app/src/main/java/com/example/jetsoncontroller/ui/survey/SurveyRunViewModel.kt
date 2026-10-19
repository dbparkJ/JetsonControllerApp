package com.example.jetsoncontroller.ui.survey

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.jetsoncontroller.data.network.JetsonApiException
import com.example.jetsoncontroller.data.network.JetsonCommandResultUnknownException
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.data.survey.SurveyRunLocalState
import com.example.jetsoncontroller.data.survey.SurveyRunPersistence
import com.example.jetsoncontroller.data.survey.SurveySelection
import com.example.jetsoncontroller.data.survey.SurveySelectionStore
import com.example.jetsoncontroller.data.survey.chooseProject
import com.example.jetsoncontroller.data.survey.chooseSection
import com.example.jetsoncontroller.data.survey.withCreationRequestIds
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.field.FieldStageInput
import com.example.jetsoncontroller.ui.model.NextActionInput
import com.example.jetsoncontroller.ui.model.nextAction as resolveNextAction
import com.example.jetsoncontroller.ui.userFacingFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONObject

internal enum class SensorRequirementChoice { REQUIRED, OPTIONAL, NOT_USED }

internal data class RunPolicyDraft(
    val sensors: Map<String, SensorRequirementChoice> = SupportedPolicySensors.associateWith {
        SensorRequirementChoice.NOT_USED
    },
    val minFreeBytes: String = "0",
    val outputMinFiles: String = "0",
    val outputMinBytes: String = "0",
    val outputPatterns: String = "",
    val outputRootId: String = "",
    val outputPath: String = ""
) {
    val valid: Boolean
        get() = minFreeBytes.toLongOrNull()?.let { it >= 0 } == true &&
            outputMinFiles.toIntOrNull()?.let { it >= 0 } == true &&
            outputMinBytes.toLongOrNull()?.let { it >= 0 } == true &&
            outputRootId.isNotBlank() && safeOutputPath(outputPath)

    fun request(policy: PipelineRunPolicy?): UpdatePipelineRunPolicyRequest =
        UpdatePipelineRunPolicyRequest(
            requiredSensors = SupportedPolicySensors.filter { sensors[it] == SensorRequirementChoice.REQUIRED },
            optionalSensors = SupportedPolicySensors.filter { sensors[it] == SensorRequirementChoice.OPTIONAL },
            minFreeBytes = minFreeBytes.toLong(),
            expectedOutput = ExpectedOutputPolicy(
                minFiles = outputMinFiles.toInt(),
                minBytes = outputMinBytes.toLong(),
                patterns = outputPatterns.lines().map(String::trim).filter(String::isNotEmpty).distinct()
            ),
            outputRootId = outputRootId,
            outputPath = outputPath.trim().trim('/'),
            expectedRevision = policy?.revision,
            clientRequestId = UUID.randomUUID().toString()
        )
}

internal data class SurveyDeviceConnection(val deviceId: String?, val online: Boolean, val controlAvailable: Boolean = online)

internal data class RunResultTarget(val deviceId: String, val runId: String, val pipelineId: String? = null) {
    fun matches(run: PipelineRun): Boolean = run.runId == runId &&
        run.deviceId.equals(deviceId, true) && (pipelineId == null || run.pipelineId == pipelineId)
}

internal data class RunResultUiState(
    val target: RunResultTarget? = null,
    val run: PipelineRun? = null,
    val loading: Boolean = false,
    val error: String? = null
)

internal data class RunLogTarget(val deviceId: String, val pipelineId: String, val logId: String)
internal data class RunLogUiState(val target: RunLogTarget? = null, val text: String? = null, val error: String? = null)

internal interface SurveyRunDataSource {
    val deviceConnection: Flow<SurveyDeviceConnection>
    suspend fun projects(): Result<List<SurveyProject>>
    suspend fun createProject(request: SurveyLabelMutationRequest): Result<SurveyProject>
    suspend fun sections(projectId: String): Result<List<SurveySection>>
    suspend fun createSection(projectId: String, request: SurveyLabelMutationRequest): Result<SurveySection>
    suspend fun policy(pipelineId: String): Result<PipelineRunPolicy>
    suspend fun savePolicy(pipelineId: String, request: UpdatePipelineRunPolicyRequest): Result<PipelineRunPolicy>
    suspend fun preflight(pipelineId: String, request: PipelinePreflightRequest): Result<PipelinePreflight>
    suspend fun contextualStart(pipelineId: String, request: ContextualStartRequest): Result<ManagedPipeline>
    suspend fun pipelineRun(runId: String): Result<PipelineRun>
    suspend fun runLog(pipelineId: String, logId: String): Result<String>
    suspend fun pipelines(): Result<List<ManagedPipeline>>
    suspend fun roots(): Result<List<RemoteRoot>>
    /** Phase 2b-B/5 supplies the current server row/revision check; tickets fail closed until then. */
    suspend fun revalidateTicket(draft: PreparationDraft): Result<Unit> =
        if (draft.ticketRowId == null) Result.success(Unit) else Result.failure(IllegalStateException(TicketChangedMessage))
    suspend fun stop(target: StopTarget): Result<ManagedPipeline> = Result.failure(UnsupportedOperationException())
}

internal class RepositorySurveyRunDataSource(
    private val repository: JetsonRepository,
    private val ticketValidator: (suspend (PreparationDraft) -> Result<Unit>)? = null
) : SurveyRunDataSource {
    override val deviceConnection: Flow<SurveyDeviceConnection> = combine(
        repository.selectedDeviceId,
        repository.transportState
    ) { deviceId, transport ->
        SurveyDeviceConnection(
            deviceId,
            transport is TransportState.Connected && transport.type != TransportType.BLE &&
                transport.deviceId.equals(deviceId, true)
        )
    }
    override suspend fun projects() = repository.surveyProjects()
    override suspend fun createProject(request: SurveyLabelMutationRequest) = repository.createSurveyProject(request)
    override suspend fun sections(projectId: String) = repository.surveySections(projectId)
    override suspend fun createSection(projectId: String, request: SurveyLabelMutationRequest) =
        repository.createSurveySection(projectId, request)
    override suspend fun policy(pipelineId: String) = repository.pipelineRunPolicy(pipelineId)
    override suspend fun savePolicy(pipelineId: String, request: UpdatePipelineRunPolicyRequest) =
        repository.updatePipelineRunPolicy(pipelineId, request)
    override suspend fun preflight(pipelineId: String, request: PipelinePreflightRequest) =
        repository.pipelinePreflight(pipelineId, request)
    override suspend fun contextualStart(pipelineId: String, request: ContextualStartRequest) =
        repository.contextualStart(pipelineId, request)
    override suspend fun pipelineRun(runId: String) = repository.pipelineRun(runId)
    override suspend fun runLog(pipelineId: String, logId: String) =
        repository.taskRunLog(pipelineId, logId).map { it.content.ifBlank { "저장된 로그가 없습니다." } }
    override suspend fun pipelines() = repository.getPipelines()
    override suspend fun roots() = repository.getRoots()
    override suspend fun stop(target: StopTarget) = repository.controlPipeline(target.pipelineId, "stop", target.runId)
    override suspend fun revalidateTicket(draft: PreparationDraft): Result<Unit> =
        ticketValidator?.invoke(draft) ?: super.revalidateTicket(draft)
}

internal data class SurveyRunUiState(
    val deviceId: String? = null,
    val online: Boolean = false,
    val controlAvailable: Boolean = online,
    val runStateConfirmed: Boolean = false,
    val observedActiveRunCount: Int = 0,
    val preparationDraft: PreparationDraft? = null,
    val acceptedStarts: Map<String, PendingContextualStart> = emptyMap(),
    val pipelineId: String? = null,
    val pipelineLabel: String = "",
    val projects: List<SurveyProject> = emptyList(),
    val sections: List<SurveySection> = emptyList(),
    val selectedProject: SurveyProject? = null,
    val selectedSection: SurveySection? = null,
    val roots: List<RemoteRoot> = emptyList(),
    val policy: PipelineRunPolicy? = null,
    val policyDraft: RunPolicyDraft = RunPolicyDraft(),
    val preflight: PipelinePreflight? = null,
    val preflightReceivedAtElapsedRealtime: Long? = null,
    val acceptedStart: ContextualStartReceipt? = null,
    /** Client request identity retained so a ticket link can recover even if the run finishes quickly. */
    val ticketStartRequestId: String? = null,
    /** Last run observed for this device and pipeline, including terminal evidence. */
    val latestRun: PipelineRun? = null,
    /** Monotonic receipt time for [latestRun]'s identity-verified telemetry packet. */
    val telemetryReceivedAtElapsedRealtime: Long? = null,
    val resultAcknowledged: Boolean = false,
    val activeRun: PipelineRun? = null,
    val unconfirmedRunId: String? = null,
    val pendingStart: PendingContextualStart? = null,
    val restoringLocalState: Boolean = false,
    val isLoading: Boolean = false,
    val operation: String? = null,
    val message: String? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val technicalError: String? = null
) {
    val contextLocked: Boolean
        get() = activeRun?.active == true || acceptedStart != null || pendingStart != null ||
            unconfirmedRunId != null || (latestRun?.active == false && !resultAcknowledged)
    val selectionComplete: Boolean
        get() = selectedProject != null && selectedSection != null
    val canPreflight: Boolean
        get() = online && controlAvailable && !restoringLocalState && !pipelineId.isNullOrBlank() &&
            runStateConfirmed && observedActiveRunCount == 0 && !contextLocked && operation == null && selectionComplete && policy != null &&
            (preparationDraft == null || preparationDraft.pipelineId == pipelineId)
    val canStart: Boolean
        get() = canPreflight && preflight?.ready == true &&
            preflightMatches(preflight, deviceId, pipelineId, selectedProject, selectedSection, policy)

    fun nextAction(deviceName: String, registeredDeviceCount: Int) = resolveNextAction(NextActionInput(
        deviceName = deviceName,
        field = FieldStageInput(
            connected = online, runStateConfirmed = runStateConfirmed && !restoringLocalState,
            activeRunId = activeRun?.runId, pendingStartRequestId = pendingStart?.clientRequestId,
            unconfirmedRunId = unconfirmedRunId,
            runAwaitingResultId = latestRun?.takeIf { !it.active && !resultAcknowledged }?.runId,
            surveySelected = selectionComplete, preflightReady = canStart,
            surveyLabel = latestRun?.contextSnapshot?.surveySectionLabel ?: selectedSection?.label,
            registeredDeviceCount = registeredDeviceCount
        ),
        activeRunCount = maxOf(observedActiveRunCount, if (activeRun == null) 0 else 1)
    ))
}

internal data class SurveyRecreationState(
    val deviceId: String? = null,
    val pipelineId: String? = null,
    val pipelineLabel: String = "",
    val pendingStartRequestId: String? = null
)

private const val SURVEY_RESTORE_DEVICE_KEY = "survey.restore.deviceId"
private const val SURVEY_RESTORE_PIPELINE_KEY = "survey.restore.pipelineId"
private const val SURVEY_RESTORE_LABEL_KEY = "survey.restore.pipelineLabel"
private const val SURVEY_PENDING_START_ID_KEY = "survey.pendingStart.clientRequestId"

internal fun restoreSurveyRecreationState(handle: SavedStateHandle): SurveyRecreationState =
    SurveyRecreationState(
        deviceId = handle[SURVEY_RESTORE_DEVICE_KEY],
        pipelineId = handle[SURVEY_RESTORE_PIPELINE_KEY],
        pipelineLabel = handle[SURVEY_RESTORE_LABEL_KEY] ?: "",
        pendingStartRequestId = handle[SURVEY_PENDING_START_ID_KEY]
    )

internal fun saveSurveyRecreationState(handle: SavedStateHandle, state: SurveyRecreationState) {
    handle[SURVEY_RESTORE_DEVICE_KEY] = state.deviceId
    handle[SURVEY_RESTORE_PIPELINE_KEY] = state.pipelineId
    handle[SURVEY_RESTORE_LABEL_KEY] = state.pipelineLabel
    handle[SURVEY_PENDING_START_ID_KEY] = state.pendingStartRequestId
}

internal class SurveyRunViewModel(
    private val source: SurveyRunDataSource,
    private val persistence: SurveyRunPersistence,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val restoredRecreationState = restoreSurveyRecreationState(savedStateHandle)
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        SurveyRunUiState(
            deviceId = restoredRecreationState.deviceId,
            pipelineId = restoredRecreationState.pipelineId,
            pipelineLabel = restoredRecreationState.pipelineLabel
        )
    )
    val uiState = _uiState
    private var generation = 0L
    private var operationJob: Job? = null
    private var deviceLoadJob: Job? = null
    private var telemetryJob: Job? = null
    private var localState = SurveyRunLocalState()
    private var draftSaveJob: Job? = null
    private val _resultState = kotlinx.coroutines.flow.MutableStateFlow(RunResultUiState())
    val resultState: kotlinx.coroutines.flow.StateFlow<RunResultUiState> = _resultState
    private var resultJob: Job? = null
    private var resultRequest = 0L
    private val _resultLog = kotlinx.coroutines.flow.MutableStateFlow(RunLogUiState())
    val resultLog: kotlinx.coroutines.flow.StateFlow<RunLogUiState> = _resultLog
    private var resultLogJob: Job? = null
    private var resultLogRequest = 0L

    init {
        viewModelScope.launch {
            source.deviceConnection.collect { connection ->
                val expected = ++generation
                operationJob?.cancel()
                deviceLoadJob?.cancel()
                telemetryJob?.cancel()
                resultJob?.cancel()
                resultRequest++
                _resultState.value = _resultState.value.copy(loading = false)
                dismissResultLog()
                val previous = _uiState.value
                val sameDevice = previous.deviceId.equals(connection.deviceId, ignoreCase = true)
                val pipelineId = previous.pipelineId.takeIf { sameDevice }
                if (sameDevice) {
                    _uiState.value = previous.copy(
                        online = connection.online,
                        controlAvailable = connection.controlAvailable,
                        runStateConfirmed = false,
                        preflight = null,
                        preflightReceivedAtElapsedRealtime = null,
                        isLoading = false,
                        operation = null,
                        restoringLocalState = true,
                        activeRun = previous.latestRun?.takeIf { it.active },
                        unconfirmedRunId = if (!connection.online) {
                            previous.activeRun?.runId ?: previous.unconfirmedRunId
                        } else previous.unconfirmedRunId,
                        error = if (connection.online) null else
                            "장치에 다시 연결하면 현재 실행 상태를 확인합니다."
                    )
                } else {
                    localState = SurveyRunLocalState()
                    _uiState.value = SurveyRunUiState(
                        deviceId = connection.deviceId,
                        online = connection.online,
                        controlAvailable = connection.controlAvailable,
                        pipelineId = pipelineId,
                        pipelineLabel = previous.pipelineLabel.takeIf { sameDevice }.orEmpty(),
                        restoringLocalState = true,
                        error = if (connection.online) null else
                            "장치에 연결한 뒤 조사 정보를 확인하세요."
                    )
                }
                loadDeviceState(expected, connection.deviceId, connection.online, pipelineId)
            }
        }
    }

    fun open(pipelineId: String, label: String) {
        if (pipelineId.isBlank()) return
        if (_uiState.value.pipelineId == pipelineId &&
            (_uiState.value.preparationDraft == null || _uiState.value.preparationDraft?.pipelineId == pipelineId)) return
        if (_uiState.value.restoringLocalState || _uiState.value.contextLocked) return
        generation += 1
        operationJob?.cancel()
        deviceLoadJob?.cancel()
        telemetryJob?.cancel()
        _uiState.value = _uiState.value.copy(
            pipelineId = pipelineId,
            pipelineLabel = label,
            projects = emptyList(),
            sections = emptyList(),
            selectedProject = null,
            selectedSection = null,
            policy = null,
            policyDraft = RunPolicyDraft(),
            preflight = null,
            preflightReceivedAtElapsedRealtime = null,
            acceptedStart = null,
            ticketStartRequestId = null,
            latestRun = null,
            telemetryReceivedAtElapsedRealtime = null,
            resultAcknowledged = false,
            activeRun = null,
            unconfirmedRunId = null,
            pendingStart = null,
            restoringLocalState = false,
            message = null,
            error = null
        )
        saveRecreationState()
        loadDeviceState(generation, _uiState.value.deviceId, _uiState.value.online, pipelineId)
    }

    private fun loadDeviceState(expected: Long, deviceId: String?, online: Boolean, pipelineId: String?) {
        deviceLoadJob = viewModelScope.launch {
            draftSaveJob?.join()
            val loaded = deviceId?.let {
                try {
                    persistence.state(it).first()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (expected == generation) {
                        _uiState.value = _uiState.value.copy(restoringLocalState = false, runStateConfirmed = false,
                            error = "저장된 수집 상태를 불러오지 못했습니다. 다시 확인하세요.")
                    }
                    return@launch
                }
            } ?: SurveyRunLocalState()
            if (expected != generation || _uiState.value.deviceId != deviceId ||
                _uiState.value.pipelineId != pipelineId) return@launch
            val restoredPipelineId = pipelineId ?: loaded.pendingStart?.pipelineId ?: loaded.preparationDraft?.pipelineId ?: loaded.lastPipelineId
            localState = loaded
            _uiState.value = _uiState.value.copy(
                pipelineId = restoredPipelineId,
                pendingStart = loaded.pendingStart?.takeIf { it.pipelineId == restoredPipelineId },
                preparationDraft = loaded.preparationDraft,
                acceptedStarts = loaded.acceptedStarts,
                unconfirmedRunId = loaded.unconfirmedRunId ?: loaded.lastRunId?.takeIf {
                    loaded.acknowledgedRunId != it && !online
                } ?: _uiState.value.unconfirmedRunId,
                restoringLocalState = false
            )
            saveRecreationState()
            if (pipelineId != null && loaded.preparationDraft != null && loaded.preparationDraft.pipelineId != pipelineId) {
                val chosen = loaded.preparationDraft.copy(pipelineId = pipelineId)
                persistence.savePreparationDraft(deviceId ?: return@launch, chosen)
                if (!current(expected, pipelineId)) return@launch
                localState = localState.copy(preparationDraft = chosen)
                _uiState.value = _uiState.value.copy(preparationDraft = chosen)
            }
            if (online && restoredPipelineId != null) refreshInternal(expected)
        }
    }

    fun refresh() = refreshInternal(generation)

    /** Browsing a result never replaces the live collection or its pending start. */
    fun openResult(target: RunResultTarget, seed: PipelineRun? = null) {
        resultJob?.cancel()
        val request = ++resultRequest
        val cached = _resultState.value.takeIf { it.target == target }?.run
            ?: seed?.takeIf(target::matches)
        _resultState.value = RunResultUiState(target, cached)
        val connection = _uiState.value
        if (!connection.online || !connection.deviceId.equals(target.deviceId, true)) return
        val expectedGeneration = generation
        _resultState.value = _resultState.value.copy(loading = true)
        resultJob = viewModelScope.launch {
            val result = source.pipelineRun(target.runId)
            if (request != resultRequest || expectedGeneration != generation) return@launch
            val observed = result.getOrNull()?.takeIf(target::matches)
            _resultState.value = RunResultUiState(
                target = target,
                run = observed ?: cached,
                error = if (observed == null) "선택한 수집 결과를 확인하지 못했습니다." else null
            )
        }
    }

    fun openResultLog(target: RunLogTarget) {
        dismissResultLog()
        val state = _uiState.value
        if (!state.online || !state.deviceId.equals(target.deviceId, true)) return
        val request = resultLogRequest
        val expectedGeneration = generation
        _resultLog.value = RunLogUiState(target)
        resultLogJob = viewModelScope.launch {
            val result = source.runLog(target.pipelineId, target.logId)
            if (request != resultLogRequest || expectedGeneration != generation) return@launch
            _resultLog.value = RunLogUiState(target, result.getOrNull(),
                if (result.isFailure) "저장된 기록을 불러오지 못했습니다. 다시 시도하세요." else null)
        }
    }

    fun dismissResultLog() {
        resultLogJob?.cancel()
        resultLogRequest++
        _resultLog.value = RunLogUiState()
    }

    /** Refreshes only the exact accepted run used by the active screen's lightweight poll. */
    fun refreshRunTelemetry() {
        val state = _uiState.value
        val run = state.activeRun ?: state.latestRun?.takeIf { it.active } ?: return
        val deviceId = state.deviceId ?: return
        val pipelineId = state.pipelineId ?: return
        val expectedGeneration = generation
        if (!state.online || telemetryJob?.isActive == true) return
        telemetryJob = viewModelScope.launch {
            source.pipelineRun(run.runId).onSuccess { observed ->
                val current = _uiState.value
                if (!telemetryPollMatchesScope(
                        current, run.runId, deviceId, pipelineId, observed,
                        expectedGeneration, generation
                    )) return@onSuccess
                val telemetry = observed.telemetry
                if (telemetry != null && (telemetry.runId != observed.runId ||
                        telemetry.outputId != observed.output.outputId ||
                        telemetry.sourceRevision != observed.sourceRevision)
                ) return@onSuccess
                _uiState.value = current.copy(
                    latestRun = observed,
                    telemetryReceivedAtElapsedRealtime = telemetryReceiptTime(
                        observed, elapsedRealtimeMillis()
                    ),
                    activeRun = observed.takeIf { it.active },
                    acceptedStart = current.acceptedStart?.takeIf { observed.active }
                )
            }
        }
    }

    private fun refreshInternal(expectedGeneration: Long) {
        val pipelineId = _uiState.value.pipelineId ?: return
        val expectedDeviceId = _uiState.value.deviceId ?: return
        if (!_uiState.value.online || operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, operation = "refresh", error = null, errorCode = null,
                technicalError = null
            )
            val projects = source.projects()
            val policy = source.policy(pipelineId)
            val roots = source.roots()
            val pipelines = source.pipelines()
            if (!current(expectedGeneration, pipelineId)) return@launch
            if (projects.isFailure || roots.isFailure || pipelines.isFailure) {
                showError(projects.exceptionOrNull() ?: roots.exceptionOrNull() ?: pipelines.exceptionOrNull())
                return@launch
            }
            val projectList = projects.getOrThrow()
            val draftProject = localState.preparationDraft?.project as? PreparationSelection.Existing
            val draftSection = localState.preparationDraft?.section as? PreparationSelection.Existing
            val storedSelection = if (draftProject != null && draftSection != null) SurveySelection(
                draftProject.id, draftProject.revision, draftSection.id, draftSection.revision
            ) else localState.selection.takeIf { localState.preparationDraft == null }
            val selectedProject = projectList.firstOrNull {
                it.surveyProjectId == storedSelection?.surveyProjectId && it.revision == storedSelection.surveyProjectRevision
            }
            val sections = selectedProject?.let { source.sections(it.surveyProjectId) }
            if (!current(expectedGeneration, pipelineId)) return@launch
            if (sections?.isFailure == true) {
                showErrorIfCurrent(sections.exceptionOrNull(), expectedGeneration, pipelineId)
                return@launch
            }
            val sectionList = sections?.getOrNull().orEmpty()
            val selectedSection = sectionList.firstOrNull {
                it.surveySectionId == storedSelection?.surveySectionId && it.revision == storedSelection.surveySectionRevision
            }
            if (storedSelection != null && (selectedProject == null || selectedSection == null) &&
                localState.pendingStart == null
            ) {
                try {
                    persistence.saveSelection(_uiState.value.deviceId.orEmpty(), null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    showErrorIfCurrent(error, expectedGeneration, pipelineId)
                    return@launch
                }
                if (current(expectedGeneration, pipelineId)) localState = localState.copy(selection = null)
            }
            val loadedPolicy = policy.getOrNull()
            val currentPipeline = pipelines.getOrThrow().firstOrNull { it.id == pipelineId }
            var pendingStart = localState.pendingStart?.takeIf { it.pipelineId == pipelineId }
            val receipt = currentPipeline?.contextualStart?.takeIf { candidate ->
                candidate.contextSnapshot.deviceId.equals(expectedDeviceId, ignoreCase = true) &&
                    candidate.preflightSnapshot.pipelineId == pipelineId &&
                    (pendingStart == null || acceptedStartMatches(
                        candidate,
                        pendingStart!!,
                        expectedDeviceId,
                        pipelineId
                    ))
            }
            if (pendingStart != null && receipt != null) {
                persistence.saveAcceptedRun(expectedDeviceId, pipelineId, receipt.runId)
                if (!current(expectedGeneration, pipelineId) ||
                    !_uiState.value.deviceId.equals(expectedDeviceId, ignoreCase = true)
                ) return@launch
                localState = localState.copy(
                    pendingStart = null,
                    preparationDraft = null,
                    lastPipelineId = pipelineId,
                    lastRunId = receipt.runId,
                    acceptedStarts = localState.acceptedStarts + (receipt.runId to requireNotNull(pendingStart))
                )
                pendingStart = null
            }
            val lastRunId = if (pendingStart != null) null else {
                receipt?.runId ?: localState.unconfirmedRunId ?:
                    localState.lastRunId.takeIf { localState.lastPipelineId == pipelineId }
            }
            val runResult = lastRunId?.let { source.pipelineRun(it) }
            val observedRun = runResult?.getOrNull()
                ?.takeIf {
                    it.runId == lastRunId &&
                        it.deviceId.equals(_uiState.value.deviceId, true) && it.pipelineId == pipelineId
                }
            val activeRun = observedRun?.takeIf { it.active }
            val unconfirmedRunId = lastRunId?.takeIf {
                runResult?.isFailure == true || (runResult?.isSuccess == true && observedRun == null)
            }
            if (!current(expectedGeneration, pipelineId)) return@launch
            if (localState.unconfirmedRunId != unconfirmedRunId) {
                persistence.saveUnconfirmedRun(expectedDeviceId, unconfirmedRunId)
                if (!current(expectedGeneration, pipelineId)) return@launch
                localState = localState.copy(unconfirmedRunId = unconfirmedRunId,
                    lastPipelineId = if (unconfirmedRunId != null) pipelineId else localState.lastPipelineId,
                    lastRunId = unconfirmedRunId ?: localState.lastRunId)
            }
            _uiState.value = _uiState.value.copy(
                projects = projectList,
                sections = sectionList,
                selectedProject = selectedProject,
                selectedSection = selectedSection,
                pipelineLabel = currentPipeline?.label ?: _uiState.value.pipelineLabel,
                roots = roots.getOrThrow(),
                policy = loadedPolicy,
                policyDraft = loadedPolicy?.toDraft() ?: RunPolicyDraft(
                    outputRootId = roots.getOrThrow().singleOrNull()?.id.orEmpty()
                ),
                acceptedStart = receipt?.takeIf { observedRun?.active == true || unconfirmedRunId != null },
                ticketStartRequestId = receipt?.clientRequestId,
                latestRun = observedRun ?: _uiState.value.latestRun,
                telemetryReceivedAtElapsedRealtime = if (observedRun != null) {
                    telemetryReceiptTime(observedRun, elapsedRealtimeMillis())
                } else _uiState.value.telemetryReceivedAtElapsedRealtime,
                resultAcknowledged = observedRun?.let {
                    !it.active && localState.acknowledgedRunId == it.runId
                } ?: _uiState.value.resultAcknowledged,
                activeRun = activeRun,
                unconfirmedRunId = unconfirmedRunId,
                pendingStart = pendingStart,
                preparationDraft = localState.preparationDraft,
                acceptedStarts = localState.acceptedStarts,
                runStateConfirmed = true,
                observedActiveRunCount = pipelines.getOrThrow().count { it.hasActiveExecution() },
                isLoading = false,
                operation = null,
                error = when {
                    policy.isFailure && (policy.exceptionOrNull() as? JetsonApiException)?.statusCode != 404 ->
                        policy.exceptionOrNull()?.let {
                            userFacingFailure(
                                it,
                                "수집 설정을 불러오지 못했습니다. 연결을 확인하고 다시 시도하세요."
                            ).message
                        } ?: "수집 설정을 불러오지 못했습니다. 다시 시도하세요."
                    storedSelection != null && (selectedProject == null || selectedSection == null) &&
                        localState.pendingStart != null ->
                        "이전에 접수한 시작 결과를 먼저 확인하세요."
                    storedSelection != null && (selectedProject == null || selectedSection == null) ->
                        "저장된 프로젝트·구간 버전이 변경되었습니다. 현재 항목을 다시 선택하세요."
                    unconfirmedRunId != null ->
                        "최근 시작한 실행의 현재 상태를 확인하지 못했습니다. 같은 장치에서 상태를 다시 불러오세요."
                    else -> null
                },
                technicalError = policy.exceptionOrNull()?.takeIf {
                    (it as? JetsonApiException)?.statusCode != 404
                }?.let { userFacingFailure(it).technicalDetail }
            )
            saveRecreationState()
        }
    }

    fun selectProject(project: SurveyProject) {
        if (_uiState.value.contextLocked || _uiState.value.operation != null) return
        invalidatePreflight()
        persistDraft((_uiState.value.preparationDraft ?: _uiState.value.manualDraft())
            .chooseProject(PreparationSelection.Existing(project.surveyProjectId, project.revision), System.currentTimeMillis()))
        _uiState.value = _uiState.value.copy(
            selectedProject = project,
            selectedSection = null,
            sections = emptyList(),
            error = null
        )
        loadSections(project)
    }

    private fun loadSections(project: SurveyProject) = launch("sections") { expected, pipelineId ->
        source.sections(project.surveyProjectId).onSuccess { sections ->
            if (current(expected, pipelineId) && _uiState.value.selectedProject?.surveyProjectId == project.surveyProjectId) {
                _uiState.value = _uiState.value.copy(sections = sections, operation = null)
            }
        }.onFailure { showErrorIfCurrent(it, expected, pipelineId) }
    }

    fun selectSection(section: SurveySection) {
        val state = _uiState.value
        val project = state.selectedProject ?: return
        if (state.contextLocked || state.operation != null || section.surveyProjectId != project.surveyProjectId) return
        invalidatePreflight()
        persistDraft((state.preparationDraft ?: state.manualDraft())
            .chooseSection(PreparationSelection.Existing(section.surveySectionId, section.revision), System.currentTimeMillis()))
        _uiState.value = _uiState.value.copy(selectedSection = section, error = null)
        val deviceId = state.deviceId ?: return
        val expected = generation
        val pipelineId = state.pipelineId ?: return
        viewModelScope.launch {
            val selection = SurveySelection(project.surveyProjectId, project.revision, section.surveySectionId, section.revision)
            persistence.saveSelection(deviceId, selection)
            if (current(expected, pipelineId) && _uiState.value.deviceId == deviceId &&
                _uiState.value.selectedSection?.surveySectionId == section.surveySectionId
            ) localState = localState.copy(selection = selection, pendingStart = null)
        }
    }

    fun createProject(label: String) {
        val state = _uiState.value
        if (label.isBlank() || state.contextLocked || state.operation != null || state.restoringLocalState) return
        persistDraft((state.preparationDraft ?: state.manualDraft())
            .chooseProject(PreparationSelection.CreateNew(label.trim()), System.currentTimeMillis()))
        _uiState.value = _uiState.value.copy(selectedProject = null, selectedSection = null, sections = emptyList())
        invalidatePreflight()
    }

    fun createSection(label: String) {
        val state = _uiState.value
        val draft = state.preparationDraft ?: state.manualDraft()
        if (draft.project == null || label.isBlank() || state.contextLocked || state.operation != null || state.restoringLocalState) return
        persistDraft(draft.chooseSection(PreparationSelection.CreateNew(label.trim()), System.currentTimeMillis()))
        _uiState.value = _uiState.value.copy(selectedSection = null)
        invalidatePreflight()
    }

    /** Import once from TicketPreparation; no second ticket snapshot is kept here. */
    fun setPreparationDraft(draft: PreparationDraft) {
        val state = _uiState.value
        if (state.contextLocked || state.operation != null || state.restoringLocalState ||
            !draft.deviceId.equals(state.deviceId, true)) return
        persistDraft(draft.withCreationRequestIds())
        invalidatePreflight()
        _uiState.value = _uiState.value.copy(selectedProject = null, selectedSection = null, sections = emptyList())
        if (draft.pipelineId != null) {
            if (draft.pipelineId != state.pipelineId) open(draft.pipelineId, state.pipelineLabel)
            else loadDeviceState(++generation, state.deviceId, state.online, draft.pipelineId)
        }
    }

    private fun persistDraft(draft: PreparationDraft) {
        localState = localState.copy(preparationDraft = draft)
        _uiState.value = _uiState.value.copy(preparationDraft = draft)
        saveRecreationState()
        val previous = draftSaveJob
        draftSaveJob = viewModelScope.launch {
            previous?.join()
            try {
                persistence.savePreparationDraft(draft.deviceId, draft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_uiState.value.deviceId.equals(draft.deviceId, true)) showError(error)
            }
        }
    }

    /** Called only by the explicit start transaction, never by opening preparation or choosing a task. */
    fun createStartContext(target: PreparationDraft) {
        val state = _uiState.value
        if (target != state.preparationDraft || !state.online || !state.controlAvailable ||
            state.contextLocked || !state.runStateConfirmed || state.observedActiveRunCount > 0 ||
            target.pipelineId != state.pipelineId || !target.deviceId.equals(state.deviceId, true)) return
        launch("create_context") { expected, pipelineId ->
            draftSaveJob?.join()
            source.revalidateTicket(target).getOrElse { throw StartTargetChangedException(TicketChangedMessage) }
            checkCreationScope(expected, target)
            require(source.pipelines().getOrThrow().none { it.hasActiveExecution() })
            checkCreationScope(expected, target)
            var draft = target.withCreationRequestIds()
            persistence.savePreparationDraft(target.deviceId, draft)
            checkCreationScope(expected, target)
            val project = when (val choice = draft.project) {
                is PreparationSelection.Existing -> source.projects().getOrThrow().single {
                    it.surveyProjectId == choice.id && it.revision == choice.revision
                }
                null -> error("프로젝트를 다시 선택하세요.")
                else -> source.createProject(SurveyLabelMutationRequest(choice.creationLabel(),
                    clientRequestId = requireNotNull(draft.projectRequestId))).getOrThrow()
            }
            checkCreationScope(expected, target)
            draft = draft.copy(project = PreparationSelection.Existing(project.surveyProjectId, project.revision))
            persistence.savePreparationDraft(target.deviceId, draft)
            checkCreationScope(expected, target)
            localState = localState.copy(preparationDraft = draft)
            _uiState.value = _uiState.value.copy(preparationDraft = draft, selectedProject = project,
                projects = (_uiState.value.projects + project).distinctBy { it.surveyProjectId })
            val section = when (val choice = draft.section) {
                is PreparationSelection.Existing -> source.sections(project.surveyProjectId).getOrThrow().single {
                    it.surveySectionId == choice.id && it.revision == choice.revision && it.surveyProjectId == project.surveyProjectId
                }
                null -> error("구간을 다시 선택하세요.")
                else -> source.createSection(project.surveyProjectId, SurveyLabelMutationRequest(choice.creationLabel(),
                    clientRequestId = requireNotNull(draft.sectionRequestId))).getOrThrow()
            }
            checkCreationScope(expected, target)
            require(section.surveyProjectId == project.surveyProjectId)
            draft = draft.copy(section = PreparationSelection.Existing(section.surveySectionId, section.revision))
            persistence.savePreparationDraft(target.deviceId, draft)
            persistence.saveSelection(target.deviceId, SurveySelection(project.surveyProjectId, project.revision,
                section.surveySectionId, section.revision))
            checkCreationScope(expected, target)
            localState = localState.copy(preparationDraft = draft)
            _uiState.value = _uiState.value.copy(preparationDraft = draft, selectedSection = section,
                sections = (_uiState.value.sections + section).distinctBy { it.surveySectionId }, operation = null,
                preflight = null, preflightReceivedAtElapsedRealtime = null)
        }
    }

    private suspend fun checkCreationScope(expected: Long, target: PreparationDraft) {
        ensureDevice(expected, target.pipelineId.orEmpty(), target.deviceId)
        requireTarget(!_uiState.value.contextLocked, "진행 중인 수집과 시작 요청을 먼저 확인하세요.")
    }

    fun setSensorRequirement(sensor: String, choice: SensorRequirementChoice) = editPolicy {
        copy(sensors = sensors + (sensor to choice))
    }
    fun setMinFreeBytes(value: String) = editPolicy { copy(minFreeBytes = numeric(value, 18)) }
    fun setOutputMinFiles(value: String) = editPolicy { copy(outputMinFiles = numeric(value, 9)) }
    fun setOutputMinBytes(value: String) = editPolicy { copy(outputMinBytes = numeric(value, 18)) }
    fun setOutputPatterns(value: String) = editPolicy { copy(outputPatterns = value.take(2_000)) }
    fun setOutputRoot(rootId: String) = editPolicy { copy(outputRootId = rootId) }
    fun setOutputPath(value: String) = editPolicy { copy(outputPath = value.take(512)) }

    private fun editPolicy(update: RunPolicyDraft.() -> RunPolicyDraft) {
        if (_uiState.value.contextLocked || _uiState.value.operation != null) return
        _uiState.value = _uiState.value.copy(policyDraft = _uiState.value.policyDraft.update(), preflight = null,
            message = null, error = null)
    }

    fun savePolicy() {
        val state = _uiState.value
        val pipelineId = state.pipelineId ?: return
        if (!state.policyDraft.valid || state.contextLocked) return
        val request = state.policyDraft.request(state.policy)
        launch("save_policy") { expected, expectedPipeline ->
            source.savePolicy(pipelineId, request).onSuccess { saved ->
                if (current(expected, expectedPipeline)) _uiState.value = _uiState.value.copy(
                    policy = saved,
                    policyDraft = saved.toDraft(),
                    preflight = null,
                    operation = null,
                    message = "수집 정책 v${saved.policyVersion}을 저장했습니다.",
                    error = null
                )
            }.onFailure { showErrorIfCurrent(it, expected, expectedPipeline) }
        }
    }

    fun runPreflight() {
        val state = _uiState.value
        val pipelineId = state.pipelineId ?: return
        val project = state.selectedProject ?: return
        val section = state.selectedSection ?: return
        val policy = state.policy ?: return
        if (!state.canPreflight) return
        val request = PipelinePreflightRequest(
            project.surveyProjectId, section.surveySectionId, project.revision, section.revision, policy.revision
        )
        launch("preflight") { expected, expectedPipeline ->
            source.preflight(pipelineId, request).onSuccess { checked ->
                if (!current(expected, expectedPipeline)) return@onSuccess
                if (!preflightMatches(checked, state.deviceId, pipelineId, project, section, policy)) {
                    _uiState.value = _uiState.value.copy(operation = null, preflight = null,
                        error = "점검 결과의 장치·프로젝트·구간·정책이 현재 선택과 다릅니다. 다시 확인하세요.")
                } else _uiState.value = _uiState.value.copy(
                    preflight = checked,
                    preflightReceivedAtElapsedRealtime = elapsedRealtimeMillis(),
                    operation = null,
                    message = if (checked.ready) "시작 전 점검이 준비됨으로 확인되었습니다." else null,
                    error = if (checked.ready) null else "시작 전 점검에서 확인할 항목이 있습니다."
                )
            }.onFailure { showErrorIfCurrent(it, expected, expectedPipeline) }
        }
    }

    fun startTarget(): StartTarget? {
        val state = _uiState.value
        if (!state.canStart) return null
        return StartTarget(state.preparationDraft ?: state.manualDraft(),
            requireNotNull(state.preflight).preflightId, requireNotNull(state.policy).revision)
    }

    fun start(): PendingContextualStart? = startTarget()?.let(::start)

    fun start(target: StartTarget): PendingContextualStart? {
        val state = _uiState.value
        if (state.operation != null) return null
        val failure = startRevalidationError(target, state, System.currentTimeMillis(), elapsedRealtimeMillis())
        if (failure != null) {
            _uiState.value = state.copy(error = failure, preflight = null, preflightReceivedAtElapsedRealtime = null)
            return null
        }
        val draft = target.draft
        val project = draft.project as PreparationSelection.Existing
        val section = draft.section as PreparationSelection.Existing
        val pending = PendingContextualStart(
            requireNotNull(draft.pipelineId), project.id, project.revision, section.id,
            section.revision, target.policyRevision, target.preflightId, UUID.randomUUID().toString(),
            deviceId = draft.deviceId, serverProfileId = draft.serverProfileId, ticketRowId = draft.ticketRowId,
            ticketRevision = draft.ticketRevision, geometryRevision = draft.geometryRevision, geometryHash = draft.geometryHash,
            projectRequestId = draft.projectRequestId, sectionRequestId = draft.sectionRequestId
        )
        submitStart(draft.deviceId, pending, target)
        return pending
    }

    fun retryPendingStart() {
        val deviceId = _uiState.value.deviceId ?: return
        val pending = localState.pendingStart ?: return
        if (pending.pipelineId != _uiState.value.pipelineId || !_uiState.value.online || !_uiState.value.controlAvailable ||
            (pending.deviceId != null && !pending.deviceId.equals(deviceId, true))) return
        submitStart(deviceId, pending)
    }

    private fun submitStart(deviceId: String, pending: PendingContextualStart, target: StartTarget? = null) {
        launch("start") { expected, pipelineId ->
            draftSaveJob?.join()
            if (target != null) {
                revalidateStart(target, expected)
                persistence.savePreparationDraft(deviceId, target.draft)
            }
            ensureDevice(expected, pipelineId, deviceId)
            persistence.savePendingStart(deviceId, pending)
            if (!current(expected, pipelineId) || _uiState.value.deviceId != deviceId) return@launch
            localState = localState.copy(pendingStart = pending)
            _uiState.value = _uiState.value.copy(pendingStart = pending)
            saveRecreationState()
            // Persistence can suspend long enough for a device switch or a preflight to expire.
            try {
                ensureDevice(expected, pipelineId, deviceId)
                if (target != null) {
                    val failure = startRevalidationError(target, _uiState.value.copy(pendingStart = null),
                        System.currentTimeMillis(), elapsedRealtimeMillis())
                    requireTarget(failure == null, failure.orEmpty())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                persistence.savePendingStart(deviceId, null)
                if (current(expected, pipelineId)) {
                    localState = localState.copy(pendingStart = null)
                    _uiState.value = _uiState.value.copy(pendingStart = null, preflight = null)
                    saveRecreationState()
                }
                throw error
            }
            val request = ContextualStartRequest(
                pending.surveyProjectId, pending.surveySectionId, pending.surveyProjectRevision,
                pending.surveySectionRevision, pending.policyRevision, pending.preflightId, pending.clientRequestId
            )
            source.contextualStart(pipelineId, request).onSuccess { updated ->
                if (!current(expected, pipelineId) || _uiState.value.deviceId != deviceId) return@onSuccess
                val receipt = updated.contextualStart
                if (!acceptedStartMatches(receipt, pending, deviceId, pipelineId)) {
                    _uiState.value = _uiState.value.copy(operation = null,
                        error = "시작 응답이 현재 준비 정보와 일치하지 않습니다. 실행 상태를 새로고침하세요.")
                    return@onSuccess
                }
                persistence.saveAcceptedRun(deviceId, pipelineId, receipt!!.runId)
                if (!current(expected, pipelineId) || _uiState.value.deviceId != deviceId) return@onSuccess
                localState = localState.copy(pendingStart = null, preparationDraft = null, unconfirmedRunId = null,
                    lastPipelineId = pipelineId, lastRunId = receipt.runId,
                    acceptedStarts = localState.acceptedStarts + (receipt.runId to pending))
                val run = source.pipelineRun(receipt.runId).getOrNull()
                    ?.takeIf {
                        it.runId == receipt.runId &&
                            it.deviceId.equals(deviceId, true) && it.pipelineId == pipelineId
                    }
                if (current(expected, pipelineId)) _uiState.value = _uiState.value.copy(
                    acceptedStart = receipt.takeIf { run?.active != false },
                    ticketStartRequestId = receipt.clientRequestId,
                    latestRun = run ?: _uiState.value.latestRun,
                    telemetryReceivedAtElapsedRealtime = if (run != null) {
                        telemetryReceiptTime(run, elapsedRealtimeMillis())
                    } else _uiState.value.telemetryReceivedAtElapsedRealtime,
                    resultAcknowledged = false,
                    activeRun = run?.takeIf { it.active },
                    unconfirmedRunId = receipt.runId.takeIf { run == null },
                    pendingStart = null,
                    preparationDraft = null,
                    acceptedStarts = localState.acceptedStarts,
                    operation = null,
                    message = "조사 범위를 고정하고 수집 시작 요청을 접수했습니다.",
                    error = null,
                    errorCode = null,
                    technicalError = null
                )
                saveRecreationState()
            }.onFailure { error ->
                if (error is JetsonCommandResultUnknownException) {
                    if (current(expected, pipelineId)) _uiState.value = _uiState.value.copy(
                        operation = null,
                        pendingStart = pending,
                        error = "시작 결과를 아직 확인하지 못했습니다. 시작 상태 확인을 눌러 확인하세요.",
                        errorCode = "RESULT_UNKNOWN"
                    )
                    saveRecreationState()
                } else {
                    if (error is JetsonApiException && error.statusCode in 400..499) {
                        persistence.savePendingStart(deviceId, null)
                        if (current(expected, pipelineId) && _uiState.value.deviceId == deviceId) {
                            val conflictingRunId = if (error.errorCode in setOf(
                                "ACTIVE_RUN_CONTEXT_MISMATCH", "PIPELINE_ALREADY_ACTIVE", "CONTEXT_LOCKED"
                            )) currentRunId(error.currentJson) else null
                            localState = localState.copy(pendingStart = null, unconfirmedRunId = conflictingRunId,
                                lastPipelineId = if (conflictingRunId != null) pipelineId else localState.lastPipelineId,
                                lastRunId = conflictingRunId ?: localState.lastRunId)
                            _uiState.value = _uiState.value.copy(
                                pendingStart = null,
                                preflight = null,
                                unconfirmedRunId = conflictingRunId
                            )
                            saveRecreationState()
                            persistence.saveUnconfirmedRun(deviceId, conflictingRunId)
                        }
                    }
                    showErrorIfCurrent(error, expected, pipelineId)
                }
            }
        }
    }

    private suspend fun revalidateStart(target: StartTarget, expected: Long) {
        val draft = target.draft
        val pipelineId = requireNotNull(draft.pipelineId)
        ensureDevice(expected, pipelineId, draft.deviceId)
        source.revalidateTicket(draft).getOrElse { throw StartTargetChangedException(TicketChangedMessage) }
        ensureDevice(expected, pipelineId, draft.deviceId)
        val project = draft.project as PreparationSelection.Existing
        val section = draft.section as PreparationSelection.Existing
        requireTarget(source.projects().getOrThrow().any { it.surveyProjectId == project.id && it.revision == project.revision },
            "프로젝트와 구간을 다시 확인하세요.")
        ensureDevice(expected, pipelineId, draft.deviceId)
        requireTarget(source.sections(project.id).getOrThrow().any {
            it.surveySectionId == section.id && it.revision == section.revision && it.surveyProjectId == project.id
        }, "프로젝트와 구간을 다시 확인하세요.")
        ensureDevice(expected, pipelineId, draft.deviceId)
        requireTarget(source.policy(pipelineId).getOrThrow().revision == target.policyRevision,
            "점검을 통과해야 시작할 수 있습니다.")
        ensureDevice(expected, pipelineId, draft.deviceId)
        requireTarget(source.pipelines().getOrThrow().none { it.hasActiveExecution() },
            "진행 중인 수집과 시작 요청을 먼저 확인하세요.")
        ensureDevice(expected, pipelineId, draft.deviceId)
        val failure = startRevalidationError(target, _uiState.value, System.currentTimeMillis(), elapsedRealtimeMillis())
        requireTarget(failure == null, failure.orEmpty())
    }

    private suspend fun ensureDevice(expected: Long, pipelineId: String, deviceId: String) {
        val connection = source.deviceConnection.first()
        requireTarget(current(expected, pipelineId) && _uiState.value.deviceId.equals(deviceId, true) &&
            connection.deviceId.equals(deviceId, true) && connection.online && connection.controlAvailable,
            "장치와 수집 작업을 다시 확인하세요.")
    }

    fun stopTarget(): StopTarget? {
        val state = _uiState.value
        val run = state.activeRun ?: return null
        return StopTarget(run.deviceId, run.pipelineId, run.runId).takeIf { stopTargetMatches(it, state) }
    }

    fun stop(target: StopTarget) {
        if (!stopTargetMatches(target, _uiState.value)) {
            _uiState.value = _uiState.value.copy(error = "종료할 수집이 바뀌었습니다. 현재 수집을 다시 확인하세요.")
            return
        }
        launch("stop") { expected, pipelineId ->
            ensureDevice(expected, pipelineId, target.deviceId)
            val run = source.pipelineRun(target.runId).getOrThrow()
            val changedMessage = "종료할 수집이 바뀌었습니다. 현재 수집을 다시 확인하세요."
            requireTarget(run.active && run.runId == target.runId && run.deviceId.equals(target.deviceId, true) &&
                run.pipelineId == target.pipelineId, changedMessage)
            ensureDevice(expected, pipelineId, target.deviceId)
            val pipeline = source.pipelines().getOrThrow().singleOrNull { it.id == target.pipelineId }
            requireTarget(pipeline != null && (pipeline.activeRunId ?: pipeline.execution?.runId) == target.runId &&
                pipeline.hasActiveExecution(), changedMessage)
            ensureDevice(expected, pipelineId, target.deviceId)
            requireTarget(stopTargetMatches(target, _uiState.value), changedMessage)
            source.stop(target).getOrThrow()
            if (current(expected, pipelineId)) {
                _uiState.value = _uiState.value.copy(operation = null)
                operationJob = null
                refresh()
            }
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(
        message = null, error = null, errorCode = null, technicalError = null
    ) }

    fun acknowledgeResult(runId: String) {
        val state = _uiState.value
        val deviceId = state.deviceId ?: return
        if (state.latestRun?.runId != runId || state.latestRun.active) return
        _uiState.value = state.copy(resultAcknowledged = true)
        localState = localState.copy(acknowledgedRunId = runId)
        viewModelScope.launch { persistence.saveAcknowledgedRun(deviceId, runId) }
    }

    private fun invalidatePreflight() {
        _uiState.value = _uiState.value.copy(preflight = null, preflightReceivedAtElapsedRealtime = null,
            acceptedStart = null, error = null, message = null)
    }

    private fun launch(name: String, block: suspend (Long, String) -> Unit) {
        val pipelineId = _uiState.value.pipelineId ?: return
        if (!_uiState.value.online || !_uiState.value.controlAvailable || _uiState.value.operation != null) return
        operationJob?.cancel()
        val expected = ++generation
        _uiState.value = _uiState.value.copy(operation = name, error = null, errorCode = null)
        operationJob = viewModelScope.launch {
            try {
                block(expected, pipelineId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showErrorIfCurrent(error, expected, pipelineId)
            }
        }
    }

    private fun current(expected: Long, pipelineId: String): Boolean =
        expected == generation && _uiState.value.pipelineId == pipelineId

    private fun showError(error: Throwable?) {
        val api = error as? JetsonApiException
        val fallback = "조사 수집 요청을 처리하지 못했습니다. 다시 시도하세요."
        val failure = error?.let { userFacingFailure(it, fallback) }
            ?: com.example.jetsoncontroller.ui.UserFacingFailure(fallback, "")
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            operation = null,
            error = if (error is StartTargetChangedException) error.message else surveyErrorMessage(api?.errorCode, failure.message),
            errorCode = api?.errorCode,
            technicalError = failure.technicalDetail
        )
    }

    private fun showErrorIfCurrent(error: Throwable?, expected: Long, pipelineId: String) {
        if (current(expected, pipelineId)) showError(error)
    }

    private fun saveRecreationState() {
        val state = _uiState.value
        saveSurveyRecreationState(
            savedStateHandle,
            SurveyRecreationState(
                deviceId = state.deviceId,
                pipelineId = state.pipelineId,
                pipelineLabel = state.pipelineLabel,
                pendingStartRequestId = state.pendingStart?.clientRequestId ?: localState.pendingStart?.clientRequestId
            )
        )
    }

    class Factory(
        private val repository: JetsonRepository,
        context: Context,
        private val ticketValidator: (suspend (PreparationDraft) -> Result<Unit>)? = null
    ) : ViewModelProvider.Factory {
        private val persistence = SurveySelectionStore(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SurveyRunViewModel(
            RepositorySurveyRunDataSource(repository, ticketValidator), persistence
        ) as T

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = SurveyRunViewModel(
            RepositorySurveyRunDataSource(repository, ticketValidator), persistence,
            savedStateHandle = extras.createSavedStateHandle()
        ) as T
    }
}

private class StartTargetChangedException(message: String) : IllegalStateException(message)

private fun requireTarget(condition: Boolean, message: String) {
    if (!condition) throw StartTargetChangedException(message)
}

private fun PreparationSelection.creationLabel(): String = when (this) {
    is PreparationSelection.CreateNew -> label
    is PreparationSelection.CreateFromTicket -> label
    is PreparationSelection.Existing -> error("Existing context has no creation label")
}

private fun ManagedPipeline.hasActiveExecution(): Boolean = execution?.active == true ||
    state in setOf(PipelineState.RUNNING, PipelineState.STARTING, PipelineState.STOPPING,
        PipelineState.RETRYING, PipelineState.WAITING_FOR_TIME_SYNC)

internal fun telemetryPollMatchesScope(
    current: SurveyRunUiState,
    requestedRunId: String,
    requestedDeviceId: String,
    requestedPipelineId: String,
    observed: PipelineRun,
    expectedGeneration: Long,
    currentGeneration: Long
): Boolean = expectedGeneration == currentGeneration &&
    current.deviceId?.equals(requestedDeviceId, true) == true &&
    current.pipelineId == requestedPipelineId && observed.runId == requestedRunId &&
    observed.deviceId.equals(requestedDeviceId, true) && observed.pipelineId == requestedPipelineId &&
    current.pendingStart == null && current.unconfirmedRunId == null &&
    (current.activeRun ?: current.latestRun?.takeIf { it.active })?.runId == requestedRunId &&
    current.acceptedStart?.runId?.let { it == requestedRunId } != false

internal fun telemetryReceiptTime(run: PipelineRun, receivedAtElapsedRealtime: Long): Long? {
    val telemetry = run.telemetry ?: return null
    return receivedAtElapsedRealtime.takeIf {
        it >= 0L && telemetry.schemaVersion == 1 &&
            telemetry.runId == run.runId && telemetry.outputId == run.output.outputId &&
            telemetry.sourceRevision == run.sourceRevision &&
            telemetry.observedAtEpochMillis > 0L && telemetry.bytesObservedAtEpochMillis > 0L
    }
}

internal val SupportedPolicySensors = listOf("camera", "gnss", "imu")

internal fun PipelineRunPolicy.toDraft() = RunPolicyDraft(
    sensors = SupportedPolicySensors.associateWith { sensor -> when (sensor) {
        in requiredSensors -> SensorRequirementChoice.REQUIRED
        in optionalSensors -> SensorRequirementChoice.OPTIONAL
        else -> SensorRequirementChoice.NOT_USED
    } },
    minFreeBytes = minFreeBytes.toString(),
    outputMinFiles = expectedOutput.minFiles.toString(),
    outputMinBytes = expectedOutput.minBytes.toString(),
    outputPatterns = expectedOutput.patterns.joinToString("\n"),
    outputRootId = outputRootId,
    outputPath = outputPath
)

internal fun preflightMatches(
    preflight: PipelinePreflight?,
    deviceId: String?,
    pipelineId: String?,
    project: SurveyProject?,
    section: SurveySection?,
    policy: PipelineRunPolicy?
): Boolean = preflight != null && deviceId != null && pipelineId != null && project != null && section != null &&
    policy != null && preflight.deviceId.equals(deviceId, true) && preflight.pipelineId == pipelineId &&
    preflight.surveyProject.surveyProjectId == project.surveyProjectId &&
    preflight.surveyProject.revision == project.revision &&
    preflight.surveySection.surveySectionId == section.surveySectionId &&
    preflight.surveySection.revision == section.revision && preflight.policy.revision == policy.revision &&
    preflight.policy.pipelineId == pipelineId && preflight.surveySection.surveyProjectId == project.surveyProjectId &&
    section.surveyProjectId == project.surveyProjectId && preflight.contextSnapshot.deviceId.equals(deviceId, true) &&
    preflight.contextSnapshot.surveyProjectId == project.surveyProjectId &&
    preflight.contextSnapshot.surveyProjectRevision == project.revision &&
    preflight.contextSnapshot.surveySectionId == section.surveySectionId &&
    preflight.contextSnapshot.surveySectionRevision == section.revision

internal fun acceptedStartMatches(
    receipt: ContextualStartReceipt?,
    pending: PendingContextualStart,
    deviceId: String,
    pipelineId: String
): Boolean = receipt != null && receipt.clientRequestId == pending.clientRequestId &&
    receipt.contextSnapshot.deviceId.equals(deviceId, true) && receipt.preflightSnapshot.pipelineId == pipelineId &&
    receipt.contextSnapshot.surveyProjectId == pending.surveyProjectId &&
    receipt.contextSnapshot.surveyProjectRevision == pending.surveyProjectRevision &&
    receipt.contextSnapshot.surveySectionId == pending.surveySectionId &&
    receipt.contextSnapshot.surveySectionRevision == pending.surveySectionRevision &&
    receipt.preflightSnapshot.preflightId == pending.preflightId && receipt.preflightSnapshot.policy.revision == pending.policyRevision
    && receipt.uploadContext.schemaVersion == 1 && receipt.uploadContext.runId == receipt.runId &&
    receipt.uploadContext.deviceId.equals(deviceId, true) && receipt.uploadContext.pipelineId == pipelineId &&
    receipt.uploadContext.surveyProjectId == pending.surveyProjectId &&
    receipt.uploadContext.surveySectionId == pending.surveySectionId &&
    receipt.uploadContext.sourceRevision == receipt.preflightSnapshot.sourceRevision &&
    receipt.uploadContext.configSha256 == receipt.preflightSnapshot.configRevision &&
    receipt.uploadContext.outputId == receipt.output.outputId && receipt.uploadContext.createdAt.isNotBlank()

internal fun surveyErrorMessage(code: String?, fallback: String?): String = when (code) {
    "CONTEXT_LOCKED" -> "수집 실행이 끝날 때까지 프로젝트·구간·정책을 변경할 수 없습니다."
    "ACTIVE_RUN_CONTEXT_MISMATCH" -> "장치에서 다른 프로젝트·구간의 수집이 실행 중입니다. 현재 실행을 확인하세요."
    "REVISION_REQUIRED", "REVISION_MISMATCH" -> "서버에서 항목이 변경되었습니다. 최신 버전을 다시 불러와 확인하세요."
    "PREFLIGHT_STALE", "PREFLIGHT_MISMATCH" -> "시작 전 점검 근거가 현재 선택과 다릅니다. 다시 점검하세요."
    "PREFLIGHT_NOT_READY" -> "시작 전 점검이 만료되었거나 준비되지 않았습니다. 다시 점검하세요."
    "PIPELINE_CHANGED" -> "점검 뒤 작업 소스 또는 설정이 변경되었습니다. 다시 점검하세요."
    "PIPELINE_ALREADY_ACTIVE" -> "같은 작업의 수집이 이미 실행 중입니다. 현재 실행 상태를 확인하세요."
    "LEGACY_RUN_ACTIVE" -> "조사 컨텍스트 없이 시작된 기존 작업이 실행 중입니다. 현재 실행을 먼저 종료하세요."
    "IDEMPOTENCY_CONFLICT" -> "이전 시작 요청과 현재 준비 정보가 다릅니다. 실행 상태를 새로고침하세요."
    else -> fallback ?: "조사 수집 요청을 처리하지 못했습니다."
}

private fun numeric(value: String, maxLength: Int): String = value.filter(Char::isDigit).take(maxLength)

internal fun currentRunId(currentJson: String?): String? = runCatching {
    val current = currentJson?.let(::JSONObject) ?: return@runCatching null
    current.optString("runId").takeIf(String::isNotBlank)
        ?: current.optJSONObject("run")?.optString("runId")?.takeIf(String::isNotBlank)
}.getOrNull()

internal fun safeOutputPath(value: String): Boolean {
    val path = value.trim().trim('/')
    if ('\u0000' in path) return false
    return path.isEmpty() || path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
