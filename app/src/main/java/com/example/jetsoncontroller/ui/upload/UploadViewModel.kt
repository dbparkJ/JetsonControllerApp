package com.example.jetsoncontroller.ui.upload

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadSourceSummary
import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.model.UploadVerification
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.UploadContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.jetsoncontroller.ui.connection.DeviceWorkspace
import com.example.jetsoncontroller.ui.userFacingFailure
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val activeUploadStates = setOf(
    UploadJobState.QUEUED,
    UploadJobState.SCANNING,
    UploadJobState.UPLOADING
)

private const val ACTIVE_QUEUE_POLL_INTERVAL_MILLIS = 1_000L
private const val FULL_HISTORY_POLL_INTERVAL_MILLIS = 15_000L
private const val CURRENT_UPLOAD_JOB_ID_KEY = "currentUploadJobId"

data class UploadUiState(
    val deviceId: String? = null,
    val controlAvailable: Boolean = false,
    val targets: List<UploadTarget> = emptyList(),
    val queue: List<UploadJob> = emptyList(),
    val currentJob: UploadJob? = null,
    val sourceSummary: UploadSourceSummary? = null,
    val sourceSummaryKey: String? = null,
    val linkedRunId: String? = null,
    val linkedRun: PipelineRun? = null,
    val verification: UploadVerification? = null,
    val isCalculatingSource: Boolean = false,
    val isLoading: Boolean = false,
    val isSavingTarget: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class UploadViewModel(
    private val repository: JetsonRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState = _uiState.asStateFlow()

    private var targetsJob: Job? = null
    private var queueRefreshJob: Job? = null
    private var queuePollingJob: Job? = null
    private var currentPollingJob: Job? = null
    private var sourceSummaryJob: Job? = null
    private var actionJob: Job? = null
    private var queueActionJob: Job? = null
    private var targetActionJob: Job? = null
    private val workspace = DeviceWorkspace { UploadUiState() }
    private var connectionGeneration = 0L
    private val deletedQueueJobIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { deviceId, transport ->
                deviceId to transport
            }.collectLatest { (deviceId, transport) ->
                _uiState.value = workspace.select(deviceId, _uiState.value).copy(deviceId = deviceId)
                connectionGeneration += 1
                cancelConnectionJobs()
                deletedQueueJobIds.clear()
                if (
                    transport is TransportState.Connected &&
                    transport.type != TransportType.BLE &&
                    transport.deviceId.equals(deviceId, ignoreCase = true)
                ) {
                    _uiState.value = _uiState.value.copy(controlAvailable = true, isLoading = false, isCalculatingSource = false, isSavingTarget = false)
                    refresh(connectionGeneration)
                    startQueuePolling(connectionGeneration)
                    _uiState.value.currentJob?.takeIf { it.state in activeUploadStates }?.let {
                        startCurrentPolling(it.id, connectionGeneration)
                    }
                    _uiState.value.sourceSummaryKey?.split('\u0000', limit = 2)?.let { source ->
                        if (source.size == 2) loadSourceSummary(
                            source[0], source[1], force = true, linkedRunId = _uiState.value.linkedRunId
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        controlAvailable = false, isLoading = false, isCalculatingSource = false,
                        isSavingTarget = false
                    )
                }
            }
        }
    }

    fun refresh() = refresh(connectionGeneration)

    private fun refresh(generation: Long) {
        loadTargets(generation)
        loadQueue(generation)
    }

    fun refreshTargets() = loadTargets(connectionGeneration)

    private fun loadTargets(generation: Long) {
        if (!_uiState.value.controlAvailable) return
        targetsJob?.cancel()
        targetsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getUploadTargets()
                .onSuccess { targets ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            targets = targets,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = userFacingFailure(
                                error,
                                "업로드 서버 목록을 불러오지 못했습니다. 다시 시도하세요."
                            ).message
                        )
                        loadQueue(generation)
                    }
                }
        }
    }

    fun loadQueue() = loadQueue(connectionGeneration)

    private fun loadQueue(generation: Long) {
        if (!_uiState.value.controlAvailable) return
        queueRefreshJob?.cancel()
        queueRefreshJob = viewModelScope.launch {
            refreshQueue(generation, reportFailure = true, activeOnly = false)
        }
    }

    private fun startQueuePolling(generation: Long) {
        queuePollingJob?.cancel()
        queuePollingJob = viewModelScope.launch {
            var elapsedSinceFullHistoryRefresh = 0L
            while (generation == connectionGeneration) {
                delay(ACTIVE_QUEUE_POLL_INTERVAL_MILLIS)
                refreshQueue(generation, reportFailure = false, activeOnly = true)
                elapsedSinceFullHistoryRefresh += ACTIVE_QUEUE_POLL_INTERVAL_MILLIS
                if (elapsedSinceFullHistoryRefresh >= FULL_HISTORY_POLL_INTERVAL_MILLIS) {
                    refreshQueue(generation, reportFailure = false, activeOnly = false)
                    elapsedSinceFullHistoryRefresh = 0L
                }
            }
        }
    }

    private suspend fun refreshQueue(
        generation: Long,
        reportFailure: Boolean,
        activeOnly: Boolean
    ) {
        repository.getUploadJobs(activeOnly = activeOnly)
            .onSuccess { jobs ->
                if (generation != connectionGeneration) return@onSuccess
                val visibleJobs = filterDeletedUploadJobs(jobs, deletedQueueJobIds)
                val state = _uiState.value
                val queue = if (activeOnly) {
                    mergeActiveUploadJobs(state.queue, visibleJobs)
                } else {
                    visibleJobs
                }
                val current = state.currentJob
                val rememberedJobId = current?.id
                    ?: savedStateHandle.get<String>(currentUploadJobIdKey())
                val matchingJob = queue.firstOrNull { it.id == rememberedJobId }
                val refreshedCurrent = when {
                    matchingJob != null -> matchingJob
                    activeOnly -> current
                    rememberedJobId != null -> null
                    else -> current
                }
                val restoredJob = current == null && matchingJob != null
                val restorationMissing = !activeOnly && current == null &&
                    rememberedJobId != null && matchingJob == null
                when {
                    refreshedCurrent != null -> rememberCurrentJobId(refreshedCurrent.id)
                    !activeOnly && rememberedJobId != null -> rememberCurrentJobId(null)
                }
                _uiState.value = _uiState.value.copy(
                    queue = queue,
                    currentJob = refreshedCurrent,
                    verification = when {
                        refreshedCurrent != null ->
                            refreshedCurrent.verification ?: state.verification
                        restorationMissing -> null
                        else -> state.verification
                    },
                    error = if (restorationMissing) {
                        "저장된 업로드 작업을 찾을 수 없습니다."
                    } else {
                        state.error
                    }
                )
                if (
                    restoredJob && refreshedCurrent != null &&
                    refreshedCurrent.state in activeUploadStates
                ) {
                    startCurrentPolling(refreshedCurrent.id, generation)
                }
            }
            .onFailure { error ->
                if (reportFailure && generation == connectionGeneration) {
                    _uiState.value = _uiState.value.copy(
                        error = userFacingFailure(error, "업로드 목록을 불러오지 못했습니다. 다시 시도하세요.").message
                    )
                }
            }
    }

    fun loadSourceSummary(
        rootId: String,
        path: String,
        force: Boolean = false,
        linkedRunId: String? = null
    ) {
        val generation = connectionGeneration
        val key = "$rootId\u0000$path"
        if (!_uiState.value.controlAvailable) {
            _uiState.value = _uiState.value.copy(
                sourceSummaryKey = key,
                sourceSummary = _uiState.value.sourceSummary?.takeIf {
                    it.matchesUploadSource(rootId, path)
                },
                linkedRunId = linkedRunId,
                linkedRun = _uiState.value.linkedRun?.takeIf { it.runId == linkedRunId },
                isCalculatingSource = false
            )
            return
        }
        if (!force &&
            _uiState.value.sourceSummaryKey == key &&
            (_uiState.value.sourceSummary != null || _uiState.value.isCalculatingSource)
        ) return
        sourceSummaryJob?.cancel()
        sourceSummaryJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                sourceSummary = null,
                sourceSummaryKey = key,
                linkedRunId = linkedRunId,
                linkedRun = null,
                isCalculatingSource = true,
                error = null
            )
            val linkedRun = linkedRunId?.let { runId ->
                repository.pipelineRun(runId).getOrElse { error ->
                    if (generation == connectionGeneration && _uiState.value.sourceSummaryKey == key &&
                        _uiState.value.linkedRunId == linkedRunId
                    ) _uiState.value = _uiState.value.copy(
                        isCalculatingSource = false,
                        error = userFacingFailure(error, "수집 실행의 결과 정보를 불러오지 못했습니다. 다시 시도하세요.").message
                    )
                    return@launch
                }
            }
            if (linkedRun != null && !linkedRunMatchesSource(
                    linkedRun, _uiState.value.deviceId, rootId, path
                )
            ) {
                if (generation == connectionGeneration && _uiState.value.linkedRunId == linkedRunId) {
                    _uiState.value = _uiState.value.copy(
                        isCalculatingSource = false,
                        error = "선택한 실행의 장비·결과 폴더·업로드 컨텍스트가 현재 전송 위치와 일치하지 않습니다."
                    )
                }
                return@launch
            }
            repository.getUploadSourceSummary(rootId, path)
                .onSuccess { summary ->
                    if (generation == connectionGeneration && _uiState.value.sourceSummaryKey == key &&
                        _uiState.value.linkedRunId == linkedRunId
                    ) {
                        _uiState.value = if (summary.matchesUploadSource(rootId, path)) {
                            _uiState.value.copy(
                                sourceSummary = summary,
                                linkedRun = linkedRun,
                                isCalculatingSource = false
                            )
                        } else {
                            _uiState.value.copy(
                                sourceSummary = null,
                                isCalculatingSource = false,
                                error = "업로드할 폴더 정보가 요청한 위치와 일치하지 않습니다."
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration && _uiState.value.sourceSummaryKey == key &&
                        _uiState.value.linkedRunId == linkedRunId
                    ) {
                        _uiState.value = _uiState.value.copy(
                            isCalculatingSource = false,
                            error = userFacingFailure(error, "업로드할 폴더 정보를 불러오지 못했습니다. 다시 시도하세요.").message
                        )
                    }
                }
        }
    }

    fun startUpload(rootId: String, path: String, targetId: String) {
        if (!_uiState.value.controlAvailable ||
            !_uiState.value.deviceId.equals(repository.selectedDeviceId.value, true)) return
        if (!_uiState.value.sourceSummary.matchesUploadSource(rootId, path)) {
            _uiState.value = _uiState.value.copy(
                message = null,
                verification = null,
                error = "업로드할 폴더의 용량을 다시 계산해 주세요."
            )
            return
        }
        val linkedRun = _uiState.value.linkedRun
        if (_uiState.value.linkedRunId != null && linkedRun == null) {
            _uiState.value = _uiState.value.copy(error = "수집 실행의 업로드 컨텍스트를 다시 확인해 주세요.")
            return
        }
        if (linkedRun != null && (linkedRun.active || linkedRun.output.manifestState != "FINAL")) {
            _uiState.value = _uiState.value.copy(
                error = "수집 실행 결과 manifest가 FINAL로 확인된 뒤 전송할 수 있습니다."
            )
            return
        }
        startNewUpload(rootId, path, targetId, _uiState.value.linkedRun?.uploadContext)
    }

    private fun startNewUpload(rootId: String, path: String, targetId: String, context: UploadContext? = null) {
        if (actionJob?.isActive == true || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true)
        val generation = connectionGeneration
        currentPollingJob?.cancel()
        rememberCurrentJobId(null)
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentJob = null,
                verification = null,
                isLoading = true,
                message = null,
                error = null
            )
            repository.startUpload(rootId, path, targetId, context)
                .onSuccess { job ->
                    if (generation == connectionGeneration) {
                        if (context != null && job.context != context) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                currentJob = null,
                                error = "업로드 작업이 장비 실행 컨텍스트를 그대로 확인하지 못했습니다. 작업 목록에서 서버 상태를 확인하세요."
                            )
                            loadQueue(generation)
                            return@onSuccess
                        }
                        rememberCurrentJobId(job.id)
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertJob(_uiState.value.queue, job),
                            verification = null,
                            isLoading = false
                        )
                        startCurrentPolling(job.id, generation)
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            error = userFacingFailure(error, "업로드를 시작하지 못했습니다. 다시 시도하세요.").message,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun startCurrentPolling(
        jobId: String,
        generation: Long = connectionGeneration
    ) {
        if (!_uiState.value.controlAvailable) return
        currentPollingJob?.cancel()
        currentPollingJob = viewModelScope.launch {
            while (generation == connectionGeneration) {
                repository.getUploadJob(jobId)
                    .onSuccess { job ->
                        if (generation != connectionGeneration) return@onSuccess
                        rememberCurrentJobId(job.id)
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertJob(_uiState.value.queue, job),
                            verification = job.verification
                        )
                        if (job.state !in activeUploadStates) return@launch
                    }
                    .onFailure { error ->
                        if (generation == connectionGeneration) {
                            _uiState.value = _uiState.value.copy(
                                error = userFacingFailure(error, "업로드 상태를 확인하지 못했습니다. 다시 시도하세요.").message
                            )
                        }
                    }
                delay(1_000)
            }
        }
    }

    fun cancelCurrentUpload() {
        if (!_uiState.value.controlAvailable) return
        val jobId = _uiState.value.currentJob?.id ?: return
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = null,
                error = null
            )
            repository.cancelUpload(jobId)
                .onSuccess { job ->
                    if (generation == connectionGeneration) {
                        currentPollingJob?.cancel()
                        rememberCurrentJobId(job.id)
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertJob(_uiState.value.queue, job),
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            error = userFacingFailure(error, "업로드를 취소하지 못했습니다. 다시 시도하세요.").message,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun retryCurrentUpload() {
        if (!_uiState.value.controlAvailable) return
        val current = _uiState.value.currentJob ?: return
        if (canStartFreshReupload(current, _uiState.value.verification)) {
            startNewUpload(current.rootId, current.relativePath, current.targetId, current.context)
            return
        }
        if (current.state != UploadJobState.FAILED) return
        val jobId = current.id
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                verification = null,
                isLoading = true,
                message = null,
                error = null
            )
            repository.retryUpload(jobId)
                .onSuccess { job ->
                    if (generation == connectionGeneration) {
                        rememberCurrentJobId(job.id)
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertJob(_uiState.value.queue, job),
                            verification = null,
                            isLoading = false
                        )
                        startCurrentPolling(job.id, generation)
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            error = userFacingFailure(error, "업로드를 다시 시작하지 못했습니다. 다시 시도하세요.").message,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun openJob(job: UploadJob) {
        currentPollingJob?.cancel()
        rememberCurrentJobId(job.id)
        _uiState.value = _uiState.value.copy(
            currentJob = job,
            verification = job.verification,
            message = null,
            error = null
        )
        if (job.state in activeUploadStates) startCurrentPolling(job.id)
    }

    fun deleteJobFromQueue(job: UploadJob) {
        if (!_uiState.value.controlAvailable) return
        if (!isDeletableUploadJob(job)) {
            _uiState.value = _uiState.value.copy(
                message = null,
                error = "진행 중인 업로드 기록은 삭제할 수 없습니다."
            )
            return
        }
        val generation = connectionGeneration
        queueRefreshJob?.cancel()
        queueActionJob?.cancel()
        queueActionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = null,
                error = null
            )
            repository.deleteUploadJob(job.id)
                .onSuccess {
                    if (generation == connectionGeneration) {
                        deletedQueueJobIds += job.id
                        val deletingCurrent = _uiState.value.currentJob?.id == job.id
                        if (deletingCurrent) {
                            currentPollingJob?.cancel()
                            rememberCurrentJobId(null)
                        }
                        _uiState.value = _uiState.value.copy(
                            queue = _uiState.value.queue.filterNot { it.id == job.id },
                            currentJob = _uiState.value.currentJob?.takeUnless {
                                it.id == job.id
                            },
                            verification = _uiState.value.verification?.takeUnless {
                                deletingCurrent
                            },
                            isLoading = false,
                            message = "업로드 기록을 목록에서 삭제했습니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = userFacingFailure(error, "업로드 기록을 삭제하지 못했습니다. 다시 시도하세요.").message
                        )
                    }
                }
        }
    }

    fun verifyCurrentUpload() {
        if (!_uiState.value.controlAvailable) return
        val jobId = _uiState.value.currentJob?.id ?: return
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null, error = null)
            repository.verifyUploadSource(jobId)
                .onSuccess { verification ->
                    if (generation == connectionGeneration) {
                        rememberCurrentJobId(jobId)
                        _uiState.value = _uiState.value.copy(
                            verification = verification,
                            isLoading = false,
                            message = if (verification.matched) {
                                "서버 데이터와 원본이 일치합니다."
                            } else {
                                "서버 데이터와 원본이 일치하지 않습니다."
                            }
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = userFacingFailure(error, "서버 데이터를 확인하지 못했습니다. 다시 시도하세요.").message
                        )
                    }
                }
        }
    }

    fun deleteCurrentSource() {
        if (!_uiState.value.controlAvailable) return
        val jobId = _uiState.value.currentJob?.id ?: return
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null, error = null)
            repository.deleteUploadSource(jobId)
                .onSuccess { job ->
                    if (generation == connectionGeneration) {
                        rememberCurrentJobId(job.id)
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertJob(_uiState.value.queue, job),
                            verification = job.verification,
                            isLoading = false,
                            message = if (job.sourceRecoverable && job.sourceTrashId != null) {
                                "확인된 업로드 원본을 장치 휴지통으로 옮겼습니다."
                            } else "원본 처리 결과를 다시 확인해 주세요."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = userFacingFailure(error, "업로드 원본을 휴지통으로 옮기지 못했습니다. 다시 시도하세요.").message
                        )
                    }
                }
        }
    }

    fun restoreCurrentSource() {
        if (!_uiState.value.controlAvailable) return
        val current = _uiState.value.currentJob ?: return
        val trashId = current.sourceTrashId?.takeIf { current.sourceRecoverable } ?: return
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null, error = null)
            repository.restoreTrash(trashId).onSuccess { restored ->
                if (generation != connectionGeneration) return@onSuccess
                repository.getUploadJob(current.id).onSuccess { refreshed ->
                    if (generation == connectionGeneration) _uiState.value = _uiState.value.copy(
                        currentJob = refreshed,
                        queue = upsertJob(_uiState.value.queue, refreshed),
                        isLoading = false,
                        message = if (restored.state == "RESTORED") "업로드 원본을 복원했습니다."
                        else "복원 상태를 다시 확인해 주세요."
                    )
                }.onFailure { error ->
                    if (generation == connectionGeneration) _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = userFacingFailure(error, "복원 후 업로드 상태를 확인하지 못했습니다. 다시 시도하세요.").message
                    )
                }
            }.onFailure { error ->
                if (generation == connectionGeneration) _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = userFacingFailure(error, "업로드 원본을 복원하지 못했습니다. 휴지통을 다시 확인하세요.").message
                )
            }
        }
    }

    fun saveTarget(
        targetId: String,
        label: String,
        baseUrl: String,
        token: String?
    ) {
        if (!_uiState.value.controlAvailable) return
        val generation = connectionGeneration
        targetActionJob?.cancel()
        targetActionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingTarget = true,
                message = null,
                error = null
            )
            repository.saveUploadTarget(targetId, label, baseUrl, token)
                .onSuccess { target ->
                    if (generation == connectionGeneration) {
                        val targets = _uiState.value.targets
                            .filterNot { it.id == target.id }
                            .plus(target)
                            .sortedBy { it.label.lowercase() }
                        _uiState.value = _uiState.value.copy(
                            targets = targets,
                            isSavingTarget = false,
                            message = "업로드 서버를 저장했습니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isSavingTarget = false,
                            error = userFacingFailure(error, "업로드 서버를 저장하지 못했습니다. 다시 시도하세요.").message
                        )
                    }
                }
        }
    }

    fun deleteTarget(targetId: String) {
        if (!_uiState.value.controlAvailable) return
        val generation = connectionGeneration
        targetActionJob?.cancel()
        targetActionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingTarget = true,
                message = null,
                error = null
            )
            repository.deleteUploadTarget(targetId)
                .onSuccess {
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            targets = _uiState.value.targets.filterNot { it.id == targetId },
                            isSavingTarget = false,
                            message = "업로드 서버를 삭제했습니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isSavingTarget = false,
                            error = userFacingFailure(error, "업로드 서버를 삭제하지 못했습니다. 다시 시도하세요.").message
                        )
                    }
                }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    fun dismissMessage(shown: String) {
        if (_uiState.value.message == shown) {
            _uiState.value = _uiState.value.copy(message = null)
        }
    }

    private fun rememberCurrentJobId(jobId: String?) {
        if (jobId == null) {
            savedStateHandle.remove<String>(currentUploadJobIdKey())
        } else {
            savedStateHandle[currentUploadJobIdKey()] = jobId
        }
    }

    private fun currentUploadJobIdKey(): String =
        "$CURRENT_UPLOAD_JOB_ID_KEY:${_uiState.value.deviceId?.lowercase().orEmpty()}"

    private fun cancelConnectionJobs() {
        targetsJob?.cancel()
        queueRefreshJob?.cancel()
        queuePollingJob?.cancel()
        currentPollingJob?.cancel()
        sourceSummaryJob?.cancel()
        actionJob?.cancel()
        queueActionJob?.cancel()
        targetActionJob?.cancel()
    }

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UploadViewModel(repository, SavedStateHandle()) as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras
        ): T {
            return UploadViewModel(repository, extras.createSavedStateHandle()) as T
        }
    }
}

internal fun linkedRunMatchesSource(
    run: PipelineRun,
    selectedDeviceId: String?,
    rootId: String,
    path: String
): Boolean {
    val context = run.uploadContext
    return !run.active && run.deviceId.equals(selectedDeviceId, true) &&
        run.output.rootId == rootId && run.output.path.trim('/') == path.trim('/') &&
        context.schemaVersion == 1 && context.runId == run.runId &&
        context.deviceId.equals(run.deviceId, true) && context.pipelineId == run.pipelineId &&
        context.surveyProjectId == run.contextSnapshot.surveyProjectId &&
        context.surveySectionId == run.contextSnapshot.surveySectionId &&
        context.sourceRevision == run.sourceRevision && context.configSha256 == run.configRevision &&
        context.outputId == run.output.outputId && context.createdAt.isNotBlank()
}

private fun upsertJob(queue: List<UploadJob>, job: UploadJob): List<UploadJob> {
    val remaining = queue.filterNot { it.id == job.id }
    return listOf(job) + remaining
}

internal fun filterActiveUploadJobs(jobs: List<UploadJob>): List<UploadJob> =
    jobs.filter { it.state in activeUploadStates }

internal fun isActiveUploadState(state: UploadJobState): Boolean =
    state in activeUploadStates

internal fun isDeletableUploadJob(job: UploadJob): Boolean =
    job.state !in activeUploadStates

internal fun filterDeletedUploadJobs(
    jobs: List<UploadJob>,
    deletedJobIds: Set<String>
): List<UploadJob> = jobs.filterNot { it.id in deletedJobIds }

internal fun UploadSourceSummary?.matchesUploadSource(rootId: String, path: String): Boolean =
    this != null && this.rootId == rootId && this.relativePath == path

internal fun canStartFreshReupload(
    job: UploadJob,
    verification: UploadVerification?
): Boolean = job.state == UploadJobState.COMPLETED && verification?.matched == false

internal fun mergeActiveUploadJobs(
    history: List<UploadJob>,
    activeJobs: List<UploadJob>
): List<UploadJob> {
    if (activeJobs.isEmpty()) return history
    val activeById = activeJobs.associateBy { it.id }
    val knownIds = history.asSequence().map { it.id }.toHashSet()
    val newlyObserved = activeJobs.filterNot { it.id in knownIds }
    return newlyObserved + history.map { activeById[it.id] ?: it }
}
