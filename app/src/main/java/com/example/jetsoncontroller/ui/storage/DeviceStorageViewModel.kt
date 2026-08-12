package com.example.jetsoncontroller.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.network.LocalControlApi
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteFileContent
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

data class DeviceStorageUiState(
    val roots: List<RemoteRoot> = emptyList(),
    val currentRoot: RemoteRoot? = null,
    val currentPath: String = "",
    val entries: List<RemoteFileEntry> = emptyList(),
    val preview: RemoteFileContent? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class DeviceStorageViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceStorageUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var connectionGeneration = 0L

    init {
        viewModelScope.launch {
            repository.transportState.collectLatest { transport ->
                connectionGeneration += 1
                loadJob?.cancel()
                if (
                    transport is TransportState.Connected &&
                    transport.type != TransportType.BLE
                ) {
                    loadRoots(connectionGeneration)
                } else {
                    _uiState.value = DeviceStorageUiState()
                }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.currentRoot == null) {
            loadRoots(connectionGeneration)
        } else {
            val root = _uiState.value.currentRoot ?: return
            loadDirectory(root.id, _uiState.value.currentPath, connectionGeneration)
        }
    }

    fun openCollection() {
        loadRoots(connectionGeneration)
    }

    private fun loadRoots(generation: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getRoots()
                .onSuccess { roots ->
                    if (generation == connectionGeneration) {
                        val collectionRoot = selectCollectionRoot(roots)
                        _uiState.value = _uiState.value.copy(
                            roots = listOfNotNull(collectionRoot),
                            currentRoot = collectionRoot,
                            currentPath = "",
                            isLoading = collectionRoot != null,
                            error = if (collectionRoot == null) {
                                "수집 데이터 저장소를 찾지 못했습니다."
                            } else null
                        )
                        if (collectionRoot != null) {
                            loadDirectory(collectionRoot.id, "", generation)
                        }
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

    fun selectRoot(root: RemoteRoot) {
        _uiState.value = _uiState.value.copy(currentRoot = root, currentPath = "")
        loadDirectory(root.id, "", connectionGeneration)
    }

    fun selectDirectory(entry: RemoteFileEntry) {
        val root = _uiState.value.currentRoot ?: return
        _uiState.value = _uiState.value.copy(currentPath = entry.relativePath)
        loadDirectory(root.id, entry.relativePath, connectionGeneration)
    }

    fun openLocation(rootId: String, path: String) {
        val generation = connectionGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, preview = null)
            repository.getRoots()
                .onSuccess { roots ->
                    if (generation != connectionGeneration) return@onSuccess
                    val root = roots.firstOrNull { it.id == rootId }
                    if (root == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "출력 저장소를 찾지 못했습니다."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            roots = listOf(root),
                            currentRoot = root,
                            currentPath = path
                        )
                        loadDirectory(root.id, path, generation)
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

    fun openFile(entry: RemoteFileEntry) {
        val root = _uiState.value.currentRoot ?: return
        if (entry.type != com.example.jetsoncontroller.model.RemoteEntryType.FILE) return
        val generation = connectionGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getFile(root.id, entry.relativePath)
                .onSuccess { content ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            preview = content,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "파일을 열지 못했습니다."
                        )
                    }
                }
        }
    }

    fun navigateBack(): Boolean {
        if (_uiState.value.preview != null) {
            _uiState.value = _uiState.value.copy(preview = null, error = null)
            return true
        }
        val root = _uiState.value.currentRoot ?: return false
        val currentPath = _uiState.value.currentPath
        if (currentPath.isEmpty()) {
            return false
        }
        
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        val newPath = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/")
        _uiState.value = _uiState.value.copy(currentPath = newPath)
        loadDirectory(root.id, newPath, connectionGeneration)
        return true
    }

    private fun selectCollectionRoot(roots: List<RemoteRoot>): RemoteRoot? {
        if (roots.isEmpty()) return null
        val preferredIds = listOf("recordings", "collection", "data", "captures")
        return roots.minWithOrNull(
            compareBy<RemoteRoot> { root ->
                preferredIds.indexOf(root.id.lowercase()).let { if (it < 0) Int.MAX_VALUE else it }
            }.thenBy { root -> root.label.lowercase() }
        )
    }

    private fun loadDirectory(rootId: String, path: String, generation: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.listDirectory(rootId, path)
                .onSuccess { response ->
                    if (
                        generation == connectionGeneration &&
                        _uiState.value.currentRoot?.id == rootId &&
                        _uiState.value.currentPath == path
                    ) {
                        _uiState.value = _uiState.value.copy(
                            entries = response.entries,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (
                        generation == connectionGeneration &&
                        _uiState.value.currentRoot?.id == rootId &&
                        _uiState.value.currentPath == path
                    ) {
                        _uiState.value = _uiState.value.copy(
                            error = error.message,
                            isLoading = false
                        )
                    }
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
