package com.example.jetsoncontroller.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.network.LocalControlApi
import com.example.jetsoncontroller.data.network.JetsonCommandResultUnknownException
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteFileContent
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.connection.DeviceWorkspace
import com.example.jetsoncontroller.ui.userFacingFailure
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

data class DeviceStorageUiState(
    val deviceId: String? = null,
    val controlAvailable: Boolean = false,
    val roots: List<RemoteRoot> = emptyList(),
    val currentRoot: RemoteRoot? = null,
    val currentPath: String = "",
    val entries: List<RemoteFileEntry> = emptyList(),
    val preview: RemoteFileContent? = null,
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val undoTrashId: String? = null,
    val message: String? = null,
    val error: String? = null
)

class DeviceStorageViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceStorageUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var deleteJob: Job? = null
    private val workspace = DeviceWorkspace { DeviceStorageUiState() }
    private var connectionGeneration = 0L

    init {
        viewModelScope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { deviceId, transport ->
                deviceId to transport
            }.collectLatest { (deviceId, transport) ->
                _uiState.value = workspace.select(deviceId, _uiState.value).copy(deviceId = deviceId)
                connectionGeneration += 1
                loadJob?.cancel()
                deleteJob?.cancel()
                if (
                    transport is TransportState.Connected &&
                    transport.type != TransportType.BLE &&
                    transport.deviceId.equals(deviceId, ignoreCase = true)
                ) {
                    _uiState.value = _uiState.value.copy(controlAvailable = true, isLoading = false, isDeleting = false)
                    refresh()
                } else {
                    val deletionPending = _uiState.value.isDeleting
                    _uiState.value = _uiState.value.copy(
                        controlAvailable = false, isLoading = false, isDeleting = false,
                        message = if (deletionPending) "삭제 결과를 확인하지 못했습니다. 재연결 후 목록에서 확인해 주세요." else _uiState.value.message
                    )
                }
            }
        }
    }

    fun dismissMessage(shown: String) {
        if (_uiState.value.message == shown) _uiState.value = _uiState.value.copy(message = null, undoTrashId = null)
    }

    fun refresh() {
        if (!_uiState.value.controlAvailable) return
        if (_uiState.value.currentRoot == null) {
            loadRoots(connectionGeneration)
        } else {
            val root = _uiState.value.currentRoot ?: return
            loadDirectory(root.id, _uiState.value.currentPath, connectionGeneration)
        }
    }

    fun openCollection() {
        refresh()
    }

    private fun loadRoots(generation: Long) {
        if (!_uiState.value.controlAvailable) return
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
                            error = userFacingFailure(error, "수집 데이터 저장소를 불러오지 못했습니다. 다시 시도하세요.").message,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun selectRoot(root: RemoteRoot) {
        if (!_uiState.value.controlAvailable) return
        _uiState.value = _uiState.value.copy(currentRoot = root, currentPath = "")
        loadDirectory(root.id, "", connectionGeneration)
    }

    fun selectDirectory(entry: RemoteFileEntry) {
        if (!_uiState.value.controlAvailable) return
        val root = _uiState.value.currentRoot ?: return
        _uiState.value = _uiState.value.copy(currentPath = entry.relativePath)
        loadDirectory(root.id, entry.relativePath, connectionGeneration)
    }

    fun openLocation(rootId: String, path: String) {
        if (!_uiState.value.controlAvailable) return
        val generation = connectionGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, preview = null)
            val storageRoots = repository.getRoots()
            val workspaceRoots = repository.getWorkspaceRoots()
            if (generation != connectionGeneration) return@launch
            val roots = storageRoots.getOrDefault(emptyList()) +
                workspaceRoots.getOrDefault(emptyList())
            val root = roots.firstOrNull { it.id == rootId }
            if (root == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = (storageRoots.exceptionOrNull() ?: workspaceRoots.exceptionOrNull())
                        ?.let { userFacingFailure(it, "결과 저장소를 찾지 못했습니다. 다시 시도하세요.").message }
                        ?: "결과 저장소를 찾지 못했습니다."
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
    }

    fun openFile(entry: RemoteFileEntry) {
        if (!_uiState.value.controlAvailable) return
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
                            error = userFacingFailure(error, "파일을 열지 못했습니다. 다시 시도하세요.").message
                        )
                    }
                }
        }
    }

    fun deleteEntry(entry: RemoteFileEntry) {
        if (!_uiState.value.controlAvailable) return
        val root = _uiState.value.currentRoot ?: return
        val expectedDeviceId = _uiState.value.deviceId ?: return
        val generation = connectionGeneration
        deleteJob?.cancel()
        deleteJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDeleting = true,
                message = null,
                error = null
            )
            repository.deleteStorageEntry(root.id, entry.relativePath, expectedDeviceId)
                .onSuccess { trashed ->
                    if (
                        generation == connectionGeneration &&
                        _uiState.value.currentRoot?.id == root.id
                    ) {
                        _uiState.value = _uiState.value.copy(
                            entries = _uiState.value.entries.filterNot {
                                it.relativePath == entry.relativePath
                            },
                            preview = _uiState.value.preview?.takeUnless {
                                it.name == entry.name
                            },
                            isDeleting = false,
                            undoTrashId = trashed.trashId.takeIf { trashed.restoreSupported && trashed.state == "TRASHED" },
                            message = if (trashed.restoreSupported && trashed.state == "TRASHED") {
                                "${entry.name} 데이터를 휴지통으로 옮겼습니다."
                            } else "${entry.name} 데이터가 휴지통으로 이동했지만 이 항목은 복원할 수 없습니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isDeleting = false,
                            error = if (error is JetsonCommandResultUnknownException) {
                                "휴지통 이동 결과를 확인하지 못했습니다. 자동 재시도하지 않고 현재 폴더를 다시 조회합니다."
                            } else userFacingFailure(error, "장치 데이터를 휴지통으로 옮기지 못했습니다. 다시 시도하세요.").message
                        )
                        loadDirectory(root.id, _uiState.value.currentPath, generation)
                    }
                }
        }
    }

    fun undoDelete() {
        if (!_uiState.value.controlAvailable || _uiState.value.isDeleting) return
        val trashId = _uiState.value.undoTrashId ?: return
        val root = _uiState.value.currentRoot ?: return
        val path = _uiState.value.currentPath
        val generation = connectionGeneration
        deleteJob?.cancel()
        deleteJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, message = null, error = null)
            repository.restoreTrash(trashId).onSuccess { restored ->
                if (generation == connectionGeneration && _uiState.value.currentRoot?.id == root.id) {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        undoTrashId = null,
                        message = if (restored.state == "RESTORED") "${restored.name} 데이터를 복원했습니다."
                        else "복원 상태를 다시 확인해 주세요."
                    )
                    loadDirectory(root.id, path, generation)
                }
            }.onFailure { error ->
                if (generation == connectionGeneration) _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = userFacingFailure(error, "휴지통 데이터를 복원하지 못했습니다. 목록을 새로고침해 주세요.").message
                )
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
        if (!_uiState.value.controlAvailable) return
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
                            error = userFacingFailure(error, "폴더를 불러오지 못했습니다. 다시 시도하세요.").message,
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
