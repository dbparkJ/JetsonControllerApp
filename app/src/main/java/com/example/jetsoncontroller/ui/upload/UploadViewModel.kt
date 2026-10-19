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
import com.example.jetsoncontroller.ui.transfers.*
import com.google.gson.Gson
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
    val transferRun: PipelineRun? = null,
    val otherDeviceQueues: Map<String, List<UploadJob>> = emptyMap(),
    val isCalculatingSource: Boolean = false,
    val isLoading: Boolean = false,
    val isSavingTarget: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Recent progress observations of the active job, used for speed and remaining time. */
    val transferSamples: List<TransferSample> = emptyList()
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
    private val cachedQueues = mutableMapOf<String, List<UploadJob>>()
    private val transferRuns = mutableMapOf<String, PipelineRun>()
    private val gson = Gson()

    init {
        viewModelScope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { deviceId, transport ->
                deviceId to transport
            }.collectLatest { (deviceId, transport) ->
                val previous = _uiState.value
                previous.deviceId?.let { cachedQueues[it.lowercase()] =
                    (listOfNotNull(previous.currentJob) + previous.queue).distinctBy { job -> job.id } }
                _uiState.value = workspace.select(deviceId, previous).copy(deviceId = deviceId,
                    otherDeviceQueues = cachedQueues.filterKeys { !it.equals(deviceId, true) })
                if (_uiState.value.currentJob == null) {
                    val restored = savedStateHandle.get<String>("transferJob:${deviceId?.lowercase()}")
                        ?.let { runCatching { gson.fromJson(it, UploadJob::class.java) }.getOrNull() }
                    if (restored != null) applyCurrentJob(restored)
                }
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

    /**
     * Every second: one read of the active job (not the whole active list). Every
     * [FULL_HISTORY_POLL_INTERVAL_MILLIS]: the full history, which is the only read that returns
     * every job.
     */
    private fun startQueuePolling(generation: Long) {
        queuePollingJob?.cancel()
        queuePollingJob = viewModelScope.launch {
            var elapsedSinceFullHistoryRefresh = 0L
            while (generation == connectionGeneration) {
                delay(ACTIVE_JOB_POLL_INTERVAL_MILLIS)
                elapsedSinceFullHistoryRefresh += ACTIVE_JOB_POLL_INTERVAL_MILLIS
                val state = _uiState.value
                activeJobToPoll(
                    currentJob = state.currentJob,
                    queue = state.queue,
                    currentJobPollRunning = currentPollingJob?.isActive == true
                )?.let { jobId -> pollSingleJob(jobId, generation) }
                if (historyRefreshDue(elapsedSinceFullHistoryRefresh)) {
                    refreshQueue(generation, reportFailure = false, activeOnly = false)
                    elapsedSinceFullHistoryRefresh = 0L
                }
            }
        }
    }

    /** Reads one job and keeps it current in the queue. Failures stay silent on this path. */
    private suspend fun pollSingleJob(jobId: String, generation: Long) {
        repository.getUploadJob(jobId)
            .onSuccess { job ->
                if (generation != connectionGeneration) return@onSuccess
                val state = _uiState.value
                if (state.currentJob?.id == job.id) {
                    applyCurrentJob(job)
                } else {
                    _uiState.value = state.copy(queue = replaceQueuedJob(state.queue, job))
                }
            }
    }

    /** Applies a poll of the current job: queue entry, verification and one progress sample. */
    private fun applyCurrentJob(job: UploadJob) {
        savedStateHandle["transferJob:${_uiState.value.deviceId?.lowercase()}"] = gson.toJson(job)
        val state = _uiState.value
        val observed = if (job.bytesTransferred != null && isActiveUploadState(job.state)) {
            TransferSample(
                jobId = job.id,
                bytesTransferred = job.bytesTransferred,
                observedAtMillis = monotonicMillis()
            )
        } else {
            null
        }
        _uiState.value = state.copy(
            currentJob = job,
            queue = upsertJob(state.queue, job),
            verification = job.verification,
            transferSamples = observed?.let { appendTransferSample(state.transferSamples, it) }
                ?: state.transferSamples
        )
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
            _uiState.value.sourceSummaryKey == key && _uiState.value.linkedRunId == linkedRunId &&
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
                        error = "선택한 실행의 장치·결과 폴더·업로드 컨텍스트가 현재 전송 위치와 일치하지 않습니다."
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

    fun startUpload(rootId: String, path: String, targetId: String,
        expectedDeviceId: String? = _uiState.value.deviceId, onStarted: (UploadJob) -> Unit = {}) {
        val transport = repository.transportState.value as? TransportState.Connected
        if (!expectedDeviceId.equals(repository.selectedDeviceId.value, true) ||
            !_uiState.value.controlAvailable || transport == null || transport.type != TransportType.LAN ||
            !_uiState.value.deviceId.equals(repository.selectedDeviceId.value, true) ||
            !transport.deviceId.equals(_uiState.value.deviceId, true) ||
            _uiState.value.targets.none { it.id == targetId }) return
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
        if (linkedRun != null && (!linkedRunMatchesSource(linkedRun, expectedDeviceId, rootId, path) ||
                linkedRun.output.manifestState != "FINAL")) {
            _uiState.value = _uiState.value.copy(
                error = "수집 실행 결과 manifest가 FINAL로 확인된 뒤 전송할 수 있습니다."
            )
            return
        }
        startNewUpload(rootId, path, targetId, _uiState.value.linkedRun?.uploadContext, onStarted)
    }

    private fun startNewUpload(rootId: String, path: String, targetId: String, context: UploadContext? = null,
        onStarted: (UploadJob) -> Unit = {}) {
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
                        if (job.id.isBlank() || job.rootId != rootId || job.relativePath != path || job.targetId != targetId ||
                            (context != null && job.context != context)) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                currentJob = null,
                                error = "업로드 작업이 장치 실행 컨텍스트를 그대로 확인하지 못했습니다. 작업 목록에서 서버 상태를 확인하세요."
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
                        openJob(job)
                        onStarted(job)
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
                        applyCurrentJob(job)
                        if (job.state !in activeUploadStates) return@launch
                    }
                    .onFailure { error ->
                        if (generation == connectionGeneration) {
                            _uiState.value = _uiState.value.copy(
                                error = userFacingFailure(error, "업로드 상태를 확인하지 못했습니다. 다시 시도하세요.").message
                            )
                        }
                    }
                delay(ACTIVE_JOB_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun currentTarget(): TransferActionTarget? {
        val state = _uiState.value
        val deviceId = state.deviceId ?: return null
        val job = state.currentJob ?: return null
        return transferActionTarget(deviceId, job)
    }

    fun retryJob(job: UploadJob) {
        val device = _uiState.value.deviceId ?: return
        mutateTransfer(transferActionTarget(device, job), TransferMutation.RETRY)
    }

    fun cancelCurrentUpload() { currentTarget()?.let { mutateTransfer(it, TransferMutation.CANCEL) } }
    fun retryCurrentUpload() { currentTarget()?.let { mutateTransfer(it, TransferMutation.RETRY) } }
    fun verifyCurrentUpload() { currentTarget()?.let { mutateTransfer(it, TransferMutation.VERIFY) } }
    fun deleteCurrentSource() { currentTarget()?.let { mutateTransfer(it, TransferMutation.CLEAN_UP) } }
    fun restoreCurrentSource() { currentTarget()?.let { mutateTransfer(it, TransferMutation.RESTORE) } }

    fun deleteJobFromQueue(job: UploadJob) {
        val device = _uiState.value.deviceId ?: return
        mutateTransfer(transferActionTarget(device, job), TransferMutation.DELETE_RECORD)
    }

    fun openJob(job: UploadJob) {
        currentPollingJob?.cancel()
        rememberCurrentJobId(job.id)
        applyCurrentJob(job)
        _uiState.value = _uiState.value.copy(message = null, error = null, transferRun = null)
        val runId = job.context?.runId
        if (runId != null) {
            val cacheKey = "${_uiState.value.deviceId}:$runId"
            val cached = transferRuns[cacheKey] ?: savedStateHandle.get<String>("transferRun:$cacheKey")
                ?.let { runCatching { gson.fromJson(it, PipelineRun::class.java) }.getOrNull() }
            _uiState.value = _uiState.value.copy(transferRun = cached)
            if (_uiState.value.controlAvailable) {
                val generation = connectionGeneration
                viewModelScope.launch {
                    repository.pipelineRun(runId).onSuccess { run ->
                        if (generation == connectionGeneration && run.uploadContext == job.context &&
                            linkedRunMatchesSource(run, _uiState.value.deviceId, job.rootId, job.relativePath)) {
                            transferRuns[cacheKey] = run
                            savedStateHandle["transferRun:$cacheKey"] = gson.toJson(run)
                            if (_uiState.value.currentJob?.id == job.id) _uiState.value = _uiState.value.copy(transferRun = run)
                        }
                    }
                }
            }
        }
        if (job.state in activeUploadStates && _uiState.value.controlAvailable) startCurrentPolling(job.id)
    }

    /** Fetch and revalidate the immutable tuple immediately before each mutation. */
    fun mutateTransfer(target: TransferActionTarget, mutation: TransferMutation, onUpdated: (UploadJob) -> Unit = {}) {
        if (actionJob?.isActive == true || !_uiState.value.controlAvailable ||
            !target.deviceId.equals(repository.selectedDeviceId.value, true)) return
        val generation = connectionGeneration
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null, error = null)
            try {
                val job = repository.getUploadJob(target.jobId).getOrThrow()
                check(generation == connectionGeneration && target.matches(repository.selectedDeviceId.value, job)) {
                    "전송 대상이 바뀌었습니다. 다시 확인하세요."
                }
                val transport = repository.transportState.value as? TransportState.Connected
                check(transport?.deviceId.equals(target.deviceId, true) && transport?.type != TransportType.BLE)
                if (mutation in setOf(TransferMutation.RETRY, TransferMutation.VERIFY, TransferMutation.CLEAN_UP)) {
                    check(transport?.type == TransportType.LAN) { "같은 네트워크에서 다시 확인하세요." }
                }
                check(transferMutationAllowed(target, repository.selectedDeviceId.value, job, mutation,
                    transport?.type != TransportType.BLE && transport?.deviceId.equals(target.deviceId, true),
                    transport?.type == TransportType.LAN))
                val actions = transferDetailActions(job)
                val updated = when (mutation) {
                    TransferMutation.CANCEL -> {
                        check(transferActionAllowed(job, TransferDetailAction.CANCEL))
                        repository.cancelUpload(target.jobId).getOrThrow()
                    }
                    TransferMutation.RETRY -> {
                        check(transferActionAllowed(job, TransferDetailAction.RETRY))
                        if (job.state == UploadJobState.FAILED) repository.retryUpload(target.jobId).getOrThrow()
                        else repository.startUpload(target.rootId, target.path, target.targetId, job.context).getOrThrow()
                    }
                    TransferMutation.VERIFY -> {
                        check(job.state == UploadJobState.COMPLETED)
                        val receipt = repository.verifyUploadSource(target.jobId).getOrThrow()
                        check(matchingTransferVerification(job, receipt) != null)
                        job.copy(verification = receipt)
                    }
                    TransferMutation.CLEAN_UP -> {
                        check(actions.canCleanUp)
                        val receipt = repository.verifyUploadSource(target.jobId).getOrThrow()
                        check(matchingTransferVerification(job, receipt)?.let { it.matched && it.deletionAllowed } == true)
                        check(generation == connectionGeneration && target.deviceId.equals(repository.selectedDeviceId.value, true))
                        repository.deleteUploadSource(target.jobId).getOrThrow()
                    }
                    TransferMutation.RESTORE -> {
                        check(actions.canRestore)
                        val restored = repository.restoreTrash(requireNotNull(target.trashId)).getOrThrow()
                        check(restored.state == "RESTORED")
                        repository.getUploadJob(target.jobId).getOrThrow()
                    }
                    TransferMutation.DELETE_RECORD -> {
                        check(isDeletableUploadJob(job))
                        repository.deleteUploadJob(target.jobId).getOrThrow()
                        if (generation == connectionGeneration) {
                            deletedQueueJobIds += job.id
                            savedStateHandle.remove<String>("transferJob:${target.deviceId.lowercase()}")
                            val current = _uiState.value.currentJob
                            if (current?.id == job.id) { currentPollingJob?.cancel(); rememberCurrentJobId(null) }
                            _uiState.value = _uiState.value.copy(queue = _uiState.value.queue.filterNot { it.id == job.id },
                                currentJob = current?.takeUnless { it.id == job.id }, isLoading = false,
                                verification = _uiState.value.verification.takeUnless { current?.id == job.id },
                                message = "전송 기록을 목록에서 삭제했습니다.")
                        }
                        return@launch
                    }
                }
                if (generation != connectionGeneration) return@launch
                check(updated.rootId == target.rootId && updated.relativePath == target.path &&
                    updated.targetId == target.targetId && updated.context == target.context)
                if (_uiState.value.currentJob?.id == target.jobId) {
                    rememberCurrentJobId(updated.id)
                    applyCurrentJob(updated)
                    if (updated.state in activeUploadStates) startCurrentPolling(updated.id, generation)
                } else _uiState.value = _uiState.value.copy(queue = upsertJob(_uiState.value.queue, updated))
                _uiState.value = _uiState.value.copy(isLoading = false)
                onUpdated(updated)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation == connectionGeneration) _uiState.value = _uiState.value.copy(isLoading = false,
                    error = userFacingFailure(error, "전송 대상을 다시 확인하고 시도하세요.").message)
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

    fun confirmationClosedForDeviceChange() {
        _uiState.value = _uiState.value.copy(message = "장치를 바꿔서 열려 있던 확인 창을 닫았습니다.")
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

/** Monotonic clock for progress samples, so wall-clock changes never produce a negative rate. */
private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

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
): Boolean = job.state == UploadJobState.COMPLETED && matchingTransferVerification(job, verification)?.matched == false

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
