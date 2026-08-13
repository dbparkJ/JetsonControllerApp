package com.example.jetsoncontroller.ui.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import com.example.jetsoncontroller.model.UploadTarget
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

data class UploadUiState(
    val targets: List<UploadTarget> = emptyList(),
    val queue: List<UploadJob> = emptyList(),
    val currentJob: UploadJob? = null,
    val isLoading: Boolean = false,
    val isSavingTarget: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class UploadViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState = _uiState.asStateFlow()

    private var targetsJob: Job? = null
    private var queueRefreshJob: Job? = null
    private var queuePollingJob: Job? = null
    private var currentPollingJob: Job? = null
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
            refreshQueue(generation, reportFailure = true)
        }
    }

    private fun startQueuePolling(generation: Long) {
        queuePollingJob?.cancel()
        queuePollingJob = viewModelScope.launch {
            while (generation == connectionGeneration) {
                delay(2_000)
                refreshQueue(generation, reportFailure = false)
            }
        }
    }

    private suspend fun refreshQueue(generation: Long, reportFailure: Boolean) {
        repository.getUploadJobs(activeOnly = true)
            .onSuccess { jobs ->
                if (generation != connectionGeneration) return@onSuccess
                val queue = filterActiveUploadJobs(jobs)
                val current = _uiState.value.currentJob
                _uiState.value = _uiState.value.copy(
                    queue = queue,
                    currentJob = queue.firstOrNull { it.id == current?.id } ?: current
                )
            }
            .onFailure { error ->
                if (reportFailure && generation == connectionGeneration) {
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
            }
    }

    fun startUpload(rootId: String, path: String, targetId: String) {
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentJob = null,
                isLoading = true,
                error = null
            )
            repository.startUpload(rootId, path, targetId)
                .onSuccess { job ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertActiveJob(_uiState.value.queue, job),
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
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertActiveJob(_uiState.value.queue, job)
                        )
                        if (job.state !in activeUploadStates) return@launch
                    }
                    .onFailure { error ->
                        if (generation == connectionGeneration) {
                            _uiState.value = _uiState.value.copy(error = error.message)
                        }
                    }
                delay(2_000)
            }
        }
    }

    fun cancelCurrentUpload() {
        val jobId = _uiState.value.currentJob?.id ?: return
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.cancelUpload(jobId)
                .onSuccess { job ->
                    if (generation == connectionGeneration) {
                        currentPollingJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertActiveJob(_uiState.value.queue, job),
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
        val jobId = _uiState.value.currentJob?.id ?: return
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.retryUpload(jobId)
                .onSuccess { job ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            queue = upsertActiveJob(_uiState.value.queue, job),
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
        _uiState.value = _uiState.value.copy(currentJob = job, error = null)
        if (job.state in activeUploadStates) startCurrentPolling(job.id)
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

    private fun cancelConnectionJobs() {
        targetsJob?.cancel()
        queueRefreshJob?.cancel()
        queuePollingJob?.cancel()
        currentPollingJob?.cancel()
        actionJob?.cancel()
        targetActionJob?.cancel()
    }

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UploadViewModel(repository) as T
        }
    }
}

private fun upsertActiveJob(queue: List<UploadJob>, job: UploadJob): List<UploadJob> {
    val remaining = queue.filterNot { it.id == job.id }
    return if (job.state in activeUploadStates) listOf(job) + remaining else remaining
}

internal fun filterActiveUploadJobs(jobs: List<UploadJob>): List<UploadJob> =
    jobs.filter { it.state in activeUploadStates }
