package com.example.jetsoncontroller.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.network.LocalControlApi
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceStorageUiState(
    val roots: List<RemoteRoot> = emptyList(),
    val currentRoot: RemoteRoot? = null,
    val currentPath: String = "",
    val entries: List<RemoteFileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DeviceStorageViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceStorageUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadRoots()
    }

    private fun loadRoots() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getRoots()
                .onSuccess { roots ->
                    _uiState.value = _uiState.value.copy(roots = roots, isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message, isLoading = false)
                }
        }
    }

    fun selectRoot(root: RemoteRoot) {
        _uiState.value = _uiState.value.copy(currentRoot = root, currentPath = "")
        loadDirectory(root.id, "")
    }

    fun selectDirectory(entry: RemoteFileEntry) {
        val root = _uiState.value.currentRoot ?: return
        _uiState.value = _uiState.value.copy(currentPath = entry.relativePath)
        loadDirectory(root.id, entry.relativePath)
    }

    fun navigateBack() {
        val root = _uiState.value.currentRoot ?: return
        val currentPath = _uiState.value.currentPath
        if (currentPath.isEmpty()) {
            _uiState.value = _uiState.value.copy(currentRoot = null)
            return
        }
        
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        val newPath = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/")
        _uiState.value = _uiState.value.copy(currentPath = newPath)
        loadDirectory(root.id, newPath)
    }

    private fun loadDirectory(rootId: String, path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.listDirectory(rootId, path)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(entries = response.entries, isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message, isLoading = false)
                }
        }
    }

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceStorageViewModel(repository) as T
        }
    }
}
