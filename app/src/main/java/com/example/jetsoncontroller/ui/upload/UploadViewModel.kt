package com.example.jetsoncontroller.ui.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.UploadJobState

data class UploadUiState(
    val targets: List<UploadTarget> = emptyList(),
    val currentJob: UploadJob? = null,
    val history: List<UploadJob> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class UploadViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var targetsJob: Job? = null
    private var historyJob: Job? = null
    private var actionJob: Job? = null
    private var connectionGeneration = 0L

    init {
        viewModelScope.launch {
            repository.transportState.collectLatest { transport ->
                connectionGeneration += 1
                targetsJob?.cancel()
                historyJob?.cancel()
                actionJob?.cancel()
                pollingJob?.cancel()
                if (
                    transport is TransportState.Connected &&
                    transport.type != TransportType.BLE
                ) {
                    refresh(connectionGeneration)
                } else {
                    _uiState.value = UploadUiState()
                }
            }
        }
    }

    fun refresh() {
        refresh(connectionGeneration)
    }

    private fun refresh(generation: Long) {
        loadTargets(generation)
        loadHistory(generation)
    }

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

    fun loadHistory() = loadHistory(connectionGeneration)

    private fun loadHistory(generation: Long) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.getUploadJobs()
                .onSuccess { history ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(history = history)
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(error = error.message)
                    }
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
                            isLoading = false
                        )
                        startPolling(job.id, generation)
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

    private fun startPolling(jobId: String, generation: Long = connectionGeneration) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (generation == connectionGeneration) {
                repository.getUploadJob(jobId)
                    .onSuccess { job ->
                        if (generation != connectionGeneration) return@onSuccess
                        _uiState.value = _uiState.value.copy(currentJob = job)
                        if (job.state in setOf(
                                UploadJobState.COMPLETED,
                                UploadJobState.FAILED,
                                UploadJobState.CANCELLED
                            )
                        ) {
                            loadHistory(generation)
                            return@launch
                        }
                    }
                    .onFailure { error ->
                        if (generation == connectionGeneration) {
                            _uiState.value = _uiState.value.copy(error = error.message)
                        }
                    }
                delay(2000)
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
                        pollingJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            currentJob = job,
                            isLoading = false
                        )
                        loadHistory(generation)
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
                            isLoading = false
                        )
                        startPolling(job.id, generation)
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
        if (job.state in setOf(
                UploadJobState.QUEUED,
                UploadJobState.SCANNING,
                UploadJobState.UPLOADING
            )
        ) {
            startPolling(job.id)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
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
