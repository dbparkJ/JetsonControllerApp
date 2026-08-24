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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val targets: List<UploadTarget> = emptyList(),
    val queue: List<UploadJob> = emptyList(),
    val currentJob: UploadJob? = null,
    val sourceSummary: UploadSourceSummary? = null,
    val sourceSummaryKey: String? = null,
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
    private var targetActionJob: Job? = null
    private var connectionGeneration = 0L

    init {
        viewModelScope.launch {
            repository.transportState.collectLatest { transport ->
                connectionGeneration += 1
                cancelConnectionJobs()
                if (
                    transport is TransportState.Connected &&
                    transport.type != TransportType.BLE
                ) {
                    refresh(connectionGeneration)
                    startQueuePolling(connectionGeneration)
                } else {
                    _uiState.value = UploadUiState()
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
                            error = error.message
                        )
                    }
                }
        }
    }

    fun loadQueue() = loadQueue(connectionGeneration)

    private fun loadQueue(generation: Long) {
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
                val state = _uiState.value
                val queue = if (activeOnly) {
                    mergeActiveUploadJobs(state.queue, jobs)
                } else {
                    jobs
                }
                val current = state.currentJob
                val rememberedJobId = current?.id
                    ?: savedStateHandle.get<String>(CURRENT_UPLOAD_JOB_ID_KEY)
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
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
            }
    }

    fun loadSourceSummary(rootId: String, path: String, force: Boolean = false) {
        val generation = connectionGeneration
        val key = "$rootId\u0000$path"
        if (!force &&
            _uiState.value.sourceSummaryKey == key &&
            (_uiState.value.sourceSummary != null || _uiState.value.isCalculatingSource)
        ) return
        sourceSummaryJob?.cancel()
        sourceSummaryJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                sourceSummary = null,
                sourceSummaryKey = key,
                isCalculatingSource = true,
                error = null
            )
            repository.getUploadSourceSummary(rootId, path)
                .onSuccess { summary ->
                    if (generation == connectionGeneration && _uiState.value.sourceSummaryKey == key) {
                        _uiState.value = if (summary.matchesUploadSource(rootId, path)) {
                            _uiState.value.copy(
                                sourceSummary = summary,
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
                    if (generation == connectionGeneration && _uiState.value.sourceSummaryKey == key) {
                        _uiState.value = _uiState.value.copy(
                            isCalculatingSource = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun startUpload(rootId: String, path: String, targetId: String) {
        if (!_uiState.value.sourceSummary.matchesUploadSource(rootId, path)) {
            _uiState.value = _uiState.value.copy(
                message = null,
                verification = null,
                error = "업로드할 폴더의 용량을 다시 계산해 주세요."
            )
            return
        }
        startNewUpload(rootId, path, targetId)
    }

    private fun startNewUpload(rootId: String, path: String, targetId: String) {
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
            repository.startUpload(rootId, path, targetId)
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
                            error = error.message,
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
                            _uiState.value = _uiState.value.copy(error = error.message)
                        }
                    }
                delay(1_000)
            }
        }
    }

    fun cancelCurrentUpload() {
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
                            error = error.message,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun retryCurrentUpload() {
        val current = _uiState.value.currentJob ?: return
        if (canStartFreshReupload(current, _uiState.value.verification)) {
            startNewUpload(current.rootId, current.relativePath, current.targetId)
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
                            error = error.message,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun openJob(job: UploadJob) {
        rememberCurrentJobId(job.id)
        _uiState.value = _uiState.value.copy(
            currentJob = job,
            verification = job.verification,
            message = null,
            error = null
        )
        if (job.state in activeUploadStates) startCurrentPolling(job.id)
    }

    fun verifyCurrentUpload() {
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
                            error = error.message
                        )
                    }
                }
        }
    }

    fun deleteCurrentSource() {
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
                            message = "확인된 업로드 원본을 장치에서 삭제했습니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun saveTarget(
        targetId: String,
        label: String,
        baseUrl: String,
        token: String?
    ) {
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
                            error = error.message
                        )
                    }
                }
        }
    }

    fun deleteTarget(targetId: String) {
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
                            error = error.message
                        )
                    }
                }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    private fun rememberCurrentJobId(jobId: String?) {
        if (jobId == null) {
            savedStateHandle.remove<String>(CURRENT_UPLOAD_JOB_ID_KEY)
        } else {
            savedStateHandle[CURRENT_UPLOAD_JOB_ID_KEY] = jobId
        }
    }

    private fun cancelConnectionJobs() {
        targetsJob?.cancel()
        queueRefreshJob?.cancel()
        queuePollingJob?.cancel()
        currentPollingJob?.cancel()
        sourceSummaryJob?.cancel()
        actionJob?.cancel()
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

private fun upsertJob(queue: List<UploadJob>, job: UploadJob): List<UploadJob> {
    val remaining = queue.filterNot { it.id == job.id }
    return listOf(job) + remaining
}

internal fun filterActiveUploadJobs(jobs: List<UploadJob>): List<UploadJob> =
    jobs.filter { it.state in activeUploadStates }

internal fun isActiveUploadState(state: UploadJobState): Boolean =
    state in activeUploadStates

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
