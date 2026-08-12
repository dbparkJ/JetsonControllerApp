package com.example.jetsoncontroller.ui.pipelines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.RegisterPipelineRequest
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteRoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class PipelinePickerTarget {
    REPOSITORY,
    VIRTUALENV,
    ENTRYPOINT,
    CONFIG
}

private fun isSafeRelativePath(value: String, allowEmpty: Boolean): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return allowEmpty
    if (trimmed.startsWith('/') || '\u0000' in trimmed) return false
    return trimmed.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

data class PipelineDraft(
    val id: String = "",
    val label: String = "",
    val repositoryRoot: RemoteRoot? = null,
    val repositoryPath: String = "",
    val virtualenvRoot: RemoteRoot? = null,
    val virtualenvPath: String = "",
    val entrypoint: String = "",
    val config: String = "config.yaml",
    val writableDirectory: String = "image_records",
    val autostart: Boolean = true
) {
    val canSubmit: Boolean
        get() = id.matches(Regex("[a-z0-9][a-z0-9.-]{0,63}")) &&
            label.isNotBlank() &&
            repositoryRoot != null &&
            virtualenvRoot != null &&
            entrypoint.endsWith(".py", ignoreCase = true) &&
            (config.endsWith(".yaml", ignoreCase = true) ||
                config.endsWith(".yml", ignoreCase = true)) &&
            isSafeRelativePath(writableDirectory, allowEmpty = true)
}

data class PipelinePickerState(
    val target: PipelinePickerTarget? = null,
    val root: RemoteRoot? = null,
    val basePath: String = "",
    val currentPath: String = "",
    val entries: List<RemoteFileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class PipelineUiState(
    val pipelines: List<ManagedPipeline> = emptyList(),
    val roots: List<RemoteRoot> = emptyList(),
    val draft: PipelineDraft = PipelineDraft(),
    val picker: PipelinePickerState = PipelinePickerState(),
    val isLoading: Boolean = false,
    val busyPipelineId: String? = null,
    val registrationComplete: Boolean = false,
    val detailPipelineId: String? = null,
    val logLines: List<String> = emptyList(),
    val configPath: String = "",
    val configContent: String = "",
    val detailLoading: Boolean = false,
    val configSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class PipelineViewModel(
    private val repository: JetsonRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PipelineUiState())
    val uiState = _uiState.asStateFlow()

    private var operationJob: Job? = null
    private var pollingJob: Job? = null
    private var pickerJob: Job? = null
    private var connectionGeneration = 0L

    init {
        viewModelScope.launch {
            repository.transportState.collectLatest { transport ->
                connectionGeneration += 1
                operationJob?.cancel()
                pollingJob?.cancel()
                pickerJob?.cancel()
                if (transport is TransportState.Connected && transport.type != TransportType.BLE) {
                    refresh(connectionGeneration)
                    startPolling(connectionGeneration)
                } else {
                    _uiState.value = PipelineUiState()
                }
            }
        }
    }

    fun refresh() = refresh(connectionGeneration)

    private fun refresh(generation: Long) {
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val pipelines = repository.getPipelines()
            val roots = repository.getWorkspaceRoots()
            if (generation != connectionGeneration) return@launch
            if (pipelines.isFailure || roots.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = pipelines.exceptionOrNull()?.message
                        ?: roots.exceptionOrNull()?.message
                        ?: "자동 실행 작업을 불러오지 못했습니다."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                pipelines = pipelines.getOrThrow(),
                roots = roots.getOrThrow(),
                isLoading = false,
                error = null
            )
        }
    }

    private fun startPolling(generation: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (generation == connectionGeneration) {
                delay(5_000)
                repository.getPipelines().onSuccess { pipelines ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(pipelines = pipelines)
                    }
                }
            }
        }
    }

    fun beginCreate() {
        _uiState.value = _uiState.value.copy(
            draft = PipelineDraft(),
            picker = PipelinePickerState(),
            registrationComplete = false,
            message = null,
            error = null
        )
    }

    fun setId(value: String) {
        _uiState.value = _uiState.value.copy(
            draft = _uiState.value.draft.copy(
                id = value.lowercase().filter {
                    it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.'
                }.take(64)
            )
        )
    }

    fun setLabel(value: String) {
        _uiState.value = _uiState.value.copy(
            draft = _uiState.value.draft.copy(label = value.take(64))
        )
    }

    fun setWritableDirectory(value: String) {
        _uiState.value = _uiState.value.copy(
            draft = _uiState.value.draft.copy(writableDirectory = value)
        )
    }

    fun setAutostart(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            draft = _uiState.value.draft.copy(autostart = enabled)
        )
    }

    fun beginPick(target: PipelinePickerTarget) {
        val draft = _uiState.value.draft
        val repositoryRoot = draft.repositoryRoot
        val root = when (target) {
            PipelinePickerTarget.REPOSITORY -> _uiState.value.roots.singleOrNull()
            PipelinePickerTarget.ENTRYPOINT,
            PipelinePickerTarget.CONFIG,
            PipelinePickerTarget.VIRTUALENV -> repositoryRoot
        }
        val basePath = when (target) {
            PipelinePickerTarget.ENTRYPOINT,
            PipelinePickerTarget.CONFIG -> draft.repositoryPath
            else -> ""
        }
        val currentPath = when (target) {
            PipelinePickerTarget.VIRTUALENV -> draft.virtualenvPath
                .ifEmpty { draft.repositoryPath }
            else -> basePath
        }
        _uiState.value = _uiState.value.copy(
            picker = PipelinePickerState(
                target = target,
                root = root,
                basePath = basePath,
                currentPath = currentPath
            ),
            error = null
        )
        if (root != null) loadPickerDirectory(root.id, currentPath)
    }

    fun selectPickerRoot(root: RemoteRoot) {
        val target = _uiState.value.picker.target ?: return
        _uiState.value = _uiState.value.copy(
            picker = PipelinePickerState(target = target, root = root)
        )
        loadPickerDirectory(root.id, "")
    }

    fun openPickerDirectory(entry: RemoteFileEntry) {
        if (entry.type != RemoteEntryType.DIRECTORY) return
        val root = _uiState.value.picker.root ?: return
        _uiState.value = _uiState.value.copy(
            picker = _uiState.value.picker.copy(currentPath = entry.relativePath)
        )
        loadPickerDirectory(root.id, entry.relativePath)
    }

    fun refreshPicker() {
        val picker = _uiState.value.picker
        val root = picker.root ?: return
        loadPickerDirectory(root.id, picker.currentPath)
    }

    fun selectCurrentPickerDirectory(): Boolean {
        val picker = _uiState.value.picker
        val root = picker.root ?: return false
        val draft = _uiState.value.draft
        val updated = when (picker.target) {
            PipelinePickerTarget.REPOSITORY -> draft.copy(
                repositoryRoot = root,
                repositoryPath = picker.currentPath,
                virtualenvRoot = root,
                virtualenvPath = joinPath(picker.currentPath, ".venv"),
                entrypoint = "",
                config = "config.yaml"
            )
            PipelinePickerTarget.VIRTUALENV -> draft.copy(
                virtualenvRoot = root,
                virtualenvPath = picker.currentPath
            )
            else -> return false
        }
        _uiState.value = _uiState.value.copy(draft = updated)
        return true
    }

    fun selectPickerFile(entry: RemoteFileEntry): Boolean {
        val picker = _uiState.value.picker
        if (entry.type != RemoteEntryType.FILE) return false
        val relative = relativeToBase(entry.relativePath, picker.basePath) ?: return false
        val draft = _uiState.value.draft
        val updated = when (picker.target) {
            PipelinePickerTarget.ENTRYPOINT -> {
                if (!relative.endsWith(".py", ignoreCase = true)) return false
                draft.copy(entrypoint = relative)
            }
            PipelinePickerTarget.CONFIG -> {
                if (!relative.endsWith(".yaml", ignoreCase = true) &&
                    !relative.endsWith(".yml", ignoreCase = true)
                ) return false
                draft.copy(config = relative)
            }
            else -> return false
        }
        _uiState.value = _uiState.value.copy(draft = updated)
        return true
    }

    fun navigatePickerBack(): Boolean {
        val picker = _uiState.value.picker
        if (picker.root == null) return false
        if (picker.currentPath == picker.basePath) {
            if (picker.basePath.isNotEmpty()) return false
            _uiState.value = _uiState.value.copy(
                picker = picker.copy(root = null, currentPath = "", entries = emptyList())
            )
            return true
        }
        val parent = picker.currentPath.substringBeforeLast('/', "")
        if (picker.basePath.isNotEmpty() && !isInside(parent, picker.basePath)) return false
        _uiState.value = _uiState.value.copy(picker = picker.copy(currentPath = parent))
        loadPickerDirectory(picker.root.id, parent)
        return true
    }

    private fun loadPickerDirectory(rootId: String, path: String) {
        val generation = connectionGeneration
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                picker = _uiState.value.picker.copy(isLoading = true, error = null)
            )
            val listing = if (rootId == "workspace-home") {
                repository.listWorkspaceDirectory(rootId, path)
            } else {
                repository.listDirectory(rootId, path)
            }
            listing
                .onSuccess { response ->
                    val picker = _uiState.value.picker
                    if (generation == connectionGeneration &&
                        picker.root?.id == rootId && picker.currentPath == path
                    ) {
                        _uiState.value = _uiState.value.copy(
                            picker = picker.copy(entries = response.entries, isLoading = false)
                        )
                    }
                }
                .onFailure { error ->
                    val picker = _uiState.value.picker
                    if (generation == connectionGeneration && picker.currentPath == path) {
                        _uiState.value = _uiState.value.copy(
                            picker = picker.copy(isLoading = false, error = error.message)
                        )
                    }
                }
        }
    }

    fun register() {
        val draft = _uiState.value.draft
        val repositoryRoot = draft.repositoryRoot ?: return
        val virtualenvRoot = draft.virtualenvRoot ?: return
        if (!draft.canSubmit || _uiState.value.isLoading) return
        val writableDirectories = draft.writableDirectory.trim()
            .takeIf { it.isNotEmpty() }
            ?.let(::listOf)
            ?: emptyList()
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.registerPipeline(
                RegisterPipelineRequest(
                    id = draft.id,
                    label = draft.label.trim(),
                    repositoryRootId = repositoryRoot.id,
                    repositoryPath = draft.repositoryPath,
                    virtualenvRootId = virtualenvRoot.id,
                    virtualenvPath = draft.virtualenvPath,
                    entrypoint = draft.entrypoint,
                    config = draft.config,
                    writableDirectories = writableDirectories,
                    autostart = draft.autostart
                )
            ).onSuccess { registered ->
                if (generation == connectionGeneration) {
                    val others = _uiState.value.pipelines.filterNot { it.id == registered.id }
                    _uiState.value = _uiState.value.copy(
                        pipelines = (others + registered).sortedBy { it.label.lowercase() },
                        isLoading = false,
                        registrationComplete = true,
                        message = "${registered.label} 작업을 등록했습니다."
                    )
                }
            }.onFailure { error ->
                if (generation == connectionGeneration) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "작업을 등록하지 못했습니다."
                    )
                }
            }
        }
    }

    fun control(pipeline: ManagedPipeline, action: String) {
        if (_uiState.value.busyPipelineId != null) return
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                busyPipelineId = pipeline.id,
                message = null,
                error = null
            )
            repository.controlPipeline(pipeline.id, action)
                .onSuccess { updated ->
                    if (generation == connectionGeneration) {
                        replacePipeline(updated)
                        _uiState.value = _uiState.value.copy(
                            busyPipelineId = null,
                            message = actionMessage(updated.label, action)
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            busyPipelineId = null,
                            error = error.message ?: "작업 제어에 실패했습니다."
                        )
                    }
                }
        }
    }

    fun remove(pipeline: ManagedPipeline) {
        if (_uiState.value.busyPipelineId != null) return
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                busyPipelineId = pipeline.id,
                message = null,
                error = null
            )
            repository.removePipeline(pipeline.id)
                .onSuccess {
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            pipelines = _uiState.value.pipelines.filterNot { it.id == pipeline.id },
                            busyPipelineId = null,
                            message = "${pipeline.label} 작업 등록을 해제했습니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            busyPipelineId = null,
                            error = error.message ?: "작업 등록 해제에 실패했습니다."
                        )
                    }
                }
        }
    }

    fun loadLogs(pipelineId: String) {
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                detailPipelineId = pipelineId,
                detailLoading = true,
                logLines = emptyList(),
                error = null
            )
            repository.getPipelineLogs(pipelineId)
                .onSuccess { log ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            logLines = log.lines,
                            detailLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            detailLoading = false,
                            error = error.message ?: "실행 로그를 불러오지 못했습니다."
                        )
                    }
                }
        }
    }

    fun loadConfig(pipelineId: String) {
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                detailPipelineId = pipelineId,
                detailLoading = true,
                configPath = "",
                configContent = "",
                message = null,
                error = null
            )
            repository.getPipelineConfig(pipelineId)
                .onSuccess { document ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            configPath = document.path,
                            configContent = document.content,
                            detailLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            detailLoading = false,
                            error = error.message ?: "YAML 설정을 불러오지 못했습니다."
                        )
                    }
                }
        }
    }

    fun setConfigContent(value: String) {
        _uiState.value = _uiState.value.copy(configContent = value)
    }

    fun saveConfig() {
        val pipelineId = _uiState.value.detailPipelineId ?: return
        if (_uiState.value.configSaving) return
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(configSaving = true, error = null)
            repository.updatePipelineConfig(pipelineId, _uiState.value.configContent)
                .onSuccess { document ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            configContent = document.content,
                            configSaving = false,
                            message = "${document.path} 설정을 저장했습니다. 재시작하면 적용됩니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            configSaving = false,
                            error = error.message ?: "YAML 설정을 저장하지 못했습니다."
                        )
                    }
                }
        }
    }

    fun consumeRegistrationComplete() {
        _uiState.value = _uiState.value.copy(registrationComplete = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    private fun replacePipeline(updated: ManagedPipeline) {
        _uiState.value = _uiState.value.copy(
            pipelines = _uiState.value.pipelines.map {
                if (it.id == updated.id) updated else it
            }
        )
    }

    private fun relativeToBase(path: String, base: String): String? = when {
        base.isEmpty() -> path
        path.startsWith("$base/") -> path.removePrefix("$base/")
        else -> null
    }

    private fun isInside(path: String, base: String): Boolean =
        path == base || path.startsWith("$base/")

    private fun joinPath(parent: String, child: String): String =
        if (parent.isEmpty()) child else "$parent/$child"

    private fun actionMessage(label: String, action: String): String = when (action) {
        "start" -> "$label 작업을 시작했습니다."
        "stop" -> "$label 작업을 중지했습니다."
        "restart" -> "$label 작업을 다시 시작했습니다."
        "enable" -> "$label 작업이 부팅 시 자동으로 시작됩니다."
        "disable" -> "$label 작업의 자동 시작을 해제했습니다."
        else -> "$label 작업을 변경했습니다."
    }

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PipelineViewModel(repository) as T
        }
    }
}
