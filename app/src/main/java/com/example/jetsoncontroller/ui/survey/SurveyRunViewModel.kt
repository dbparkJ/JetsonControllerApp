package com.example.jetsoncontroller.ui.survey

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.network.JetsonApiException
import com.example.jetsoncontroller.data.network.JetsonCommandResultUnknownException
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.data.survey.SurveyRunLocalState
import com.example.jetsoncontroller.data.survey.SurveyRunPersistence
import com.example.jetsoncontroller.data.survey.SurveySelection
import com.example.jetsoncontroller.data.survey.SurveySelectionStore
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
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

internal data class SurveyDeviceConnection(val deviceId: String?, val online: Boolean)

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
    suspend fun pipelines(): Result<List<ManagedPipeline>>
    suspend fun roots(): Result<List<RemoteRoot>>
}

internal class RepositorySurveyRunDataSource(private val repository: JetsonRepository) : SurveyRunDataSource {
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
    override suspend fun pipelines() = repository.getPipelines()
    override suspend fun roots() = repository.getRoots()
}

internal data class SurveyRunUiState(
    val deviceId: String? = null,
    val online: Boolean = false,
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
    val acceptedStart: ContextualStartReceipt? = null,
    /** Last run observed for this device and pipeline, including terminal evidence. */
    val latestRun: PipelineRun? = null,
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
        get() = online && !contextLocked && operation == null && selectionComplete && policy != null
    val canStart: Boolean
        get() = canPreflight && preflight?.ready == true &&
            preflightMatches(preflight, deviceId, pipelineId, selectedProject, selectedSection, policy)
}

internal class SurveyRunViewModel(
    private val source: SurveyRunDataSource,
    private val persistence: SurveyRunPersistence
) : ViewModel() {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(SurveyRunUiState())
    val uiState = _uiState
    private var generation = 0L
    private var operationJob: Job? = null
    private var deviceLoadJob: Job? = null
    private var localState = SurveyRunLocalState()

    init {
        viewModelScope.launch {
            source.deviceConnection.collect { connection ->
                val expected = ++generation
                operationJob?.cancel()
                deviceLoadJob?.cancel()
                val previous = _uiState.value
                val sameDevice = previous.deviceId.equals(connection.deviceId, ignoreCase = true)
                val pipelineId = previous.pipelineId.takeIf { sameDevice }
                if (sameDevice) {
                    _uiState.value = previous.copy(
                        online = connection.online,
                        isLoading = false,
                        operation = null,
                        restoringLocalState = true,
                        activeRun = previous.latestRun?.takeIf { it.active },
                        unconfirmedRunId = if (!connection.online) {
                            previous.activeRun?.runId ?: previous.unconfirmedRunId
                        } else previous.unconfirmedRunId,
                        error = if (connection.online) null else
                            "장비에 다시 연결하면 현재 실행 상태를 확인합니다."
                    )
                } else {
                    localState = SurveyRunLocalState()
                    _uiState.value = SurveyRunUiState(
                        deviceId = connection.deviceId,
                        online = connection.online,
                        pipelineId = pipelineId,
                        pipelineLabel = previous.pipelineLabel.takeIf { sameDevice }.orEmpty(),
                        restoringLocalState = true,
                        error = if (connection.online) null else
                            "장비에 연결한 뒤 조사 정보를 확인하세요."
                    )
                }
                loadDeviceState(expected, connection.deviceId, connection.online, pipelineId)
            }
        }
    }

    fun open(pipelineId: String, label: String) {
        if (_uiState.value.pipelineId == pipelineId) return
        if (_uiState.value.restoringLocalState || _uiState.value.contextLocked) return
        generation += 1
        operationJob?.cancel()
        deviceLoadJob?.cancel()
        localState = SurveyRunLocalState()
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
            acceptedStart = null,
            latestRun = null,
            resultAcknowledged = false,
            activeRun = null,
            unconfirmedRunId = null,
            pendingStart = null,
            restoringLocalState = false,
            message = null,
            error = null
        )
        loadDeviceState(generation, _uiState.value.deviceId, _uiState.value.online, pipelineId)
    }

    private fun loadDeviceState(expected: Long, deviceId: String?, online: Boolean, pipelineId: String?) {
        deviceLoadJob = viewModelScope.launch {
            val loaded = deviceId?.let {
                try {
                    persistence.state(it).first()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    SurveyRunLocalState()
                }
            } ?: SurveyRunLocalState()
            if (expected != generation || _uiState.value.deviceId != deviceId ||
                _uiState.value.pipelineId != pipelineId) return@launch
            val restoredPipelineId = pipelineId ?: loaded.pendingStart?.pipelineId ?: loaded.lastPipelineId
            localState = loaded
            _uiState.value = _uiState.value.copy(
                pipelineId = restoredPipelineId,
                pendingStart = loaded.pendingStart?.takeIf { it.pipelineId == restoredPipelineId },
                restoringLocalState = false
            )
            if (online && restoredPipelineId != null) refreshInternal(expected)
        }
    }

    fun refresh() = refreshInternal(generation)

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
            val storedSelection = localState.selection
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
                    lastPipelineId = pipelineId,
                    lastRunId = receipt.runId
                )
                pendingStart = null
            }
            val lastRunId = if (pendingStart != null) null else {
                receipt?.runId ?: localState.lastRunId.takeIf { localState.lastPipelineId == pipelineId }
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
                latestRun = observedRun ?: _uiState.value.latestRun,
                resultAcknowledged = observedRun?.let {
                    !it.active && localState.acknowledgedRunId == it.runId
                } ?: _uiState.value.resultAcknowledged,
                activeRun = activeRun,
                unconfirmedRunId = unconfirmedRunId,
                pendingStart = pendingStart,
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
                        "최근 시작한 실행의 현재 상태를 확인하지 못했습니다. 같은 장비에서 상태를 다시 불러오세요."
                    else -> null
                },
                technicalError = policy.exceptionOrNull()?.takeIf {
                    (it as? JetsonApiException)?.statusCode != 404
                }?.let { userFacingFailure(it).technicalDetail }
            )
        }
    }

    fun selectProject(project: SurveyProject) {
        if (_uiState.value.contextLocked || _uiState.value.operation != null) return
        invalidatePreflight()
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
        if (label.isBlank() || _uiState.value.contextLocked) return
        launch("create_project") { expected, pipelineId ->
            source.createProject(SurveyLabelMutationRequest(label.trim(), clientRequestId = UUID.randomUUID().toString()))
                .onSuccess { project ->
                    if (current(expected, pipelineId)) {
                        _uiState.value = _uiState.value.copy(
                            projects = (_uiState.value.projects + project).distinctBy { it.surveyProjectId },
                            selectedProject = project,
                            selectedSection = null,
                            sections = emptyList(),
                            preflight = null,
                            operation = null,
                            message = "${project.label} 프로젝트를 만들었습니다."
                        )
                        loadSections(project)
                    }
                }.onFailure { showErrorIfCurrent(it, expected, pipelineId) }
        }
    }

    fun createSection(label: String) {
        val project = _uiState.value.selectedProject ?: return
        if (label.isBlank() || _uiState.value.contextLocked) return
        launch("create_section") { expected, pipelineId ->
            source.createSection(project.surveyProjectId,
                SurveyLabelMutationRequest(label.trim(), clientRequestId = UUID.randomUUID().toString()))
                .onSuccess { section ->
                    if (current(expected, pipelineId)) {
                        _uiState.value = _uiState.value.copy(
                            sections = (_uiState.value.sections + section).distinctBy { it.surveySectionId },
                            operation = null,
                            message = "${section.label} 구간을 만들었습니다."
                        )
                        selectSection(section)
                    }
                }.onFailure { showErrorIfCurrent(it, expected, pipelineId) }
        }
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
                        error = "점검 결과의 장비·프로젝트·구간·정책이 현재 선택과 다릅니다. 다시 확인하세요.")
                } else _uiState.value = _uiState.value.copy(
                    preflight = checked,
                    operation = null,
                    message = if (checked.ready) "시작 전 점검이 준비됨으로 확인되었습니다." else null,
                    error = if (checked.ready) null else "시작 전 점검에서 확인할 항목이 있습니다."
                )
            }.onFailure { showErrorIfCurrent(it, expected, expectedPipeline) }
        }
    }

    fun start() {
        val state = _uiState.value
        val pipelineId = state.pipelineId ?: return
        val project = state.selectedProject ?: return
        val section = state.selectedSection ?: return
        val policy = state.policy ?: return
        val preflight = state.preflight ?: return
        val deviceId = state.deviceId ?: return
        if (!state.canStart) return
        val existing = localState.pendingStart?.takeIf {
            it.pipelineId == pipelineId && it.surveyProjectId == project.surveyProjectId &&
                it.surveyProjectRevision == project.revision && it.surveySectionId == section.surveySectionId &&
                it.surveySectionRevision == section.revision && it.policyRevision == policy.revision &&
                it.preflightId == preflight.preflightId
        }
        val pending = existing ?: PendingContextualStart(
            pipelineId, project.surveyProjectId, project.revision, section.surveySectionId,
            section.revision, policy.revision, preflight.preflightId, UUID.randomUUID().toString()
        )
        submitStart(deviceId, pending)
    }

    fun retryPendingStart() {
        val deviceId = _uiState.value.deviceId ?: return
        val pending = localState.pendingStart ?: return
        if (pending.pipelineId != _uiState.value.pipelineId || !_uiState.value.online) return
        submitStart(deviceId, pending)
    }

    private fun submitStart(deviceId: String, pending: PendingContextualStart) {
        launch("start") { expected, pipelineId ->
            persistence.savePendingStart(deviceId, pending)
            if (!current(expected, pipelineId) || _uiState.value.deviceId != deviceId) return@launch
            localState = localState.copy(pendingStart = pending)
            _uiState.value = _uiState.value.copy(pendingStart = pending)
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
                localState = localState.copy(pendingStart = null, lastPipelineId = pipelineId, lastRunId = receipt.runId)
                val run = source.pipelineRun(receipt.runId).getOrNull()
                    ?.takeIf {
                        it.runId == receipt.runId &&
                            it.deviceId.equals(deviceId, true) && it.pipelineId == pipelineId
                    }
                if (current(expected, pipelineId)) _uiState.value = _uiState.value.copy(
                    acceptedStart = receipt.takeIf { run?.active != false },
                    latestRun = run ?: _uiState.value.latestRun,
                    resultAcknowledged = false,
                    activeRun = run?.takeIf { it.active },
                    unconfirmedRunId = receipt.runId.takeIf { run == null },
                    pendingStart = null,
                    operation = null,
                    message = "조사 범위를 고정하고 수집 시작 요청을 접수했습니다.",
                    error = null,
                    errorCode = null,
                    technicalError = null
                )
            }.onFailure { error ->
                if (error is JetsonCommandResultUnknownException) {
                    if (current(expected, pipelineId)) _uiState.value = _uiState.value.copy(
                        operation = null,
                        pendingStart = pending,
                        error = "시작 결과를 아직 확인하지 못했습니다. 시작 상태 확인을 눌러 확인하세요.",
                        errorCode = "RESULT_UNKNOWN"
                    )
                } else {
                    if (error is JetsonApiException && error.statusCode in 400..499) {
                        persistence.savePendingStart(deviceId, null)
                        if (current(expected, pipelineId) && _uiState.value.deviceId == deviceId) {
                            localState = localState.copy(pendingStart = null)
                            val conflictingRunId = if (error.errorCode in setOf(
                                "ACTIVE_RUN_CONTEXT_MISMATCH", "PIPELINE_ALREADY_ACTIVE", "CONTEXT_LOCKED"
                            )) currentRunId(error.currentJson) else null
                            _uiState.value = _uiState.value.copy(
                                pendingStart = null,
                                preflight = null,
                                unconfirmedRunId = conflictingRunId
                            )
                        }
                    }
                    showErrorIfCurrent(error, expected, pipelineId)
                }
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
        _uiState.value = _uiState.value.copy(preflight = null, acceptedStart = null, error = null, message = null)
    }

    private fun launch(name: String, block: suspend (Long, String) -> Unit) {
        val pipelineId = _uiState.value.pipelineId ?: return
        if (!_uiState.value.online || _uiState.value.operation != null) return
        operationJob?.cancel()
        val expected = ++generation
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(operation = name, error = null, errorCode = null)
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
            error = surveyErrorMessage(api?.errorCode, failure.message),
            errorCode = api?.errorCode,
            technicalError = failure.technicalDetail
        )
    }

    private fun showErrorIfCurrent(error: Throwable?, expected: Long, pipelineId: String) {
        if (current(expected, pipelineId)) showError(error)
    }

    class Factory(private val repository: JetsonRepository, context: Context) : ViewModelProvider.Factory {
        private val persistence = SurveySelectionStore(context.applicationContext)
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SurveyRunViewModel(
            RepositorySurveyRunDataSource(repository), persistence
        ) as T
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
    preflight.surveySection.revision == section.revision && preflight.policy.revision == policy.revision

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
    "ACTIVE_RUN_CONTEXT_MISMATCH" -> "장비에서 다른 프로젝트·구간의 수집이 실행 중입니다. 현재 실행을 확인하세요."
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
