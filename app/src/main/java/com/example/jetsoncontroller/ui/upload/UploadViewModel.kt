package com.example.jetsoncontroller.ui.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    init {
        loadTargets()
        loadHistory()
    }

    private fun loadTargets() {
        viewModelScope.launch {
            repository.getUploadTargets()
                .onSuccess { targets -> _uiState.value = _uiState.value.copy(targets = targets) }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            repository.getUploadJobs()
                .onSuccess { history -> _uiState.value = _uiState.value.copy(history = history) }
        }
    }

    fun startUpload(rootId: String, path: String, targetId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.startUpload(rootId, path, targetId)
                .onSuccess { job ->
                    _uiState.value = _uiState.value.copy(currentJob = job, isLoading = false)
                    startPolling(job.id)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message, isLoading = false)
                }
        }
    }

    private fun startPolling(jobId: String) {
        viewModelScope.launch {
            while (true) {
                repository.getUploadJob(jobId)
                    .onSuccess { job ->
                        _uiState.value = _uiState.value.copy(currentJob = job)
                        if (job.state.name in listOf("COMPLETED", "FAILED", "CANCELLED")) return@launch
                    }
                delay(2000)
            }
        }
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
