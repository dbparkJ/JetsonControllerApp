package com.example.jetsoncontroller.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileContent
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.UploadLibrarySession
import com.example.jetsoncontroller.model.UploadTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ServerStorageUiState(
    val targets: List<UploadTarget> = emptyList(),
    val selectedTarget: UploadTarget? = null,
    val sessions: List<UploadLibrarySession> = emptyList(),
    val nextOffset: Int? = null,
    val selectedSession: UploadLibrarySession? = null,
    val currentPath: String = "",
    val entries: List<RemoteFileEntry> = emptyList(),
    val preview: RemoteFileContent? = null,
    val listingTruncated: Boolean = false,
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class ServerStorageViewModel(
    private val repository: JetsonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServerStorageUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var actionJob: Job? = null
    private var connectionGeneration = 0L

    init {
        viewModelScope.launch {
            repository.transportState.collectLatest { transport ->
                connectionGeneration += 1
                loadJob?.cancel()
                actionJob?.cancel()
                if (transport is TransportState.Connected && transport.type != TransportType.BLE) {
                    loadTargets(connectionGeneration)
                } else {
                    _uiState.value = ServerStorageUiState()
                }
            }
        }
    }

    fun refresh() {
        val state = _uiState.value
        when {
            state.selectedSession != null -> loadDirectory(
                state.selectedSession.sessionId,
                state.currentPath,
                connectionGeneration
            )
            state.selectedTarget != null -> loadSessions(state.selectedTarget.id, false)
            else -> loadTargets(connectionGeneration)
        }
    }

    fun selectTarget(target: UploadTarget) {
        _uiState.value = _uiState.value.copy(
            selectedTarget = target,
            sessions = emptyList(),
            nextOffset = null,
            selectedSession = null,
            currentPath = "",
            entries = emptyList(),
            preview = null,
            error = null
        )
        loadSessions(target.id, false)
    }

    fun loadMoreSessions() {
        val target = _uiState.value.selectedTarget ?: return
        if (_uiState.value.nextOffset == null || _uiState.value.isLoading) return
        loadSessions(target.id, true)
    }

    fun openSession(session: UploadLibrarySession) {
        _uiState.value = _uiState.value.copy(
            selectedSession = session,
            currentPath = "",
            entries = emptyList(),
            preview = null,
            error = null
        )
        loadDirectory(session.sessionId, "", connectionGeneration)
    }

    fun openDirectory(entry: RemoteFileEntry) {
        if (entry.type != RemoteEntryType.DIRECTORY) return
        val session = _uiState.value.selectedSession ?: return
        _uiState.value = _uiState.value.copy(currentPath = entry.relativePath)
        loadDirectory(session.sessionId, entry.relativePath, connectionGeneration)
    }

    fun openFile(entry: RemoteFileEntry) {
        if (entry.type != RemoteEntryType.FILE) return
        val target = _uiState.value.selectedTarget ?: return
        val session = _uiState.value.selectedSession ?: return
        val generation = connectionGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getUploadLibraryFile(target.id, session.sessionId, entry.relativePath)
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
                            error = error.message ?: "서버 파일을 열지 못했습니다."
                        )
                    }
                }
        }
    }

    fun deleteSession(session: UploadLibrarySession) {
        val target = _uiState.value.selectedTarget ?: return
        val generation = connectionGeneration
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDeleting = true,
                message = null,
                error = null
            )
            repository.deleteUploadLibrarySession(target.id, session.sessionId)
                .onSuccess {
                    if (generation == connectionGeneration) {
                        val selectedWasDeleted =
                            _uiState.value.selectedSession?.sessionId == session.sessionId
                        _uiState.value = _uiState.value.copy(
                            sessions = _uiState.value.sessions.filterNot {
                                it.sessionId == session.sessionId
                            },
                            selectedSession = if (selectedWasDeleted) null
                            else _uiState.value.selectedSession,
                            currentPath = if (selectedWasDeleted) "" else _uiState.value.currentPath,
                            entries = if (selectedWasDeleted) emptyList() else _uiState.value.entries,
                            preview = if (selectedWasDeleted) null else _uiState.value.preview,
                            isDeleting = false,
                            message = "서버의 업로드 데이터를 삭제했습니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isDeleting = false,
                            error = error.message ?: "서버 데이터를 삭제하지 못했습니다."
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
        val session = _uiState.value.selectedSession ?: return false
        val currentPath = _uiState.value.currentPath
        if (currentPath.isNotEmpty()) {
            val parent = currentPath.substringBeforeLast('/', "")
            _uiState.value = _uiState.value.copy(currentPath = parent)
            loadDirectory(session.sessionId, parent, connectionGeneration)
            return true
        }
        _uiState.value = _uiState.value.copy(
            selectedSession = null,
            currentPath = "",
            entries = emptyList(),
            listingTruncated = false,
            error = null
        )
        return true
    }

    private fun loadTargets(generation: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getUploadTargets()
                .onSuccess { targets ->
                    if (generation != connectionGeneration) return@onSuccess
                    val httpTargets = targets.filter { it.type.equals("http", true) }
                    val selected = httpTargets.firstOrNull {
                        it.id == _uiState.value.selectedTarget?.id
                    } ?: httpTargets.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        targets = httpTargets,
                        selectedTarget = selected,
                        isLoading = selected != null,
                        error = if (selected == null) "등록된 업로드 서버가 없습니다." else null
                    )
                    if (selected != null) loadSessionsNow(selected.id, false, generation)
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "업로드 서버를 불러오지 못했습니다."
                        )
                    }
                }
        }
    }

    private fun loadSessions(targetId: String, append: Boolean) {
        val generation = connectionGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadSessionsNow(targetId, append, generation)
        }
    }

    private suspend fun loadSessionsNow(targetId: String, append: Boolean, generation: Long) {
        val offset = if (append) _uiState.value.nextOffset ?: return else 0
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        repository.getUploadLibrarySessions(targetId, offset)
            .onSuccess { response ->
                if (
                    generation == connectionGeneration &&
                    _uiState.value.selectedTarget?.id == targetId
                ) {
                    val sessions = if (append) {
                        (_uiState.value.sessions + response.sessions)
                            .distinctBy { it.sessionId }
                    } else response.sessions
                    _uiState.value = _uiState.value.copy(
                        sessions = sessions,
                        nextOffset = response.nextOffset,
                        isLoading = false
                    )
                }
            }
            .onFailure { error ->
                if (generation == connectionGeneration) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "서버 데이터를 불러오지 못했습니다."
                    )
                }
            }
    }

    private fun loadDirectory(sessionId: String, path: String, generation: Long) {
        val target = _uiState.value.selectedTarget ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, preview = null)
            repository.getUploadLibraryFiles(target.id, sessionId, path)
                .onSuccess { response ->
                    if (
                        generation == connectionGeneration &&
                        _uiState.value.selectedSession?.sessionId == sessionId &&
                        _uiState.value.currentPath == path
                    ) {
                        _uiState.value = _uiState.value.copy(
                            entries = response.entries,
                            listingTruncated = response.truncated,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "서버 파일 목록을 불러오지 못했습니다."
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ServerStorageViewModel(repository) as T
    }
}
