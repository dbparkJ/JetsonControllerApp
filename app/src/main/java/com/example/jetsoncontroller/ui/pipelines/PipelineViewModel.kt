package com.example.jetsoncontroller.ui.pipelines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.MobileRtkRelayState
import com.example.jetsoncontroller.model.PipelineConfigField
import com.example.jetsoncontroller.model.PipelineConfigValueType
import com.example.jetsoncontroller.model.PipelineLogFile
import com.example.jetsoncontroller.model.PipelineFolderDiscovery
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
        get() = label.isNotBlank() && repositoryRoot != null
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
    val discoveredFolder: PipelineFolderDiscovery? = null,
    val isDiscoveringFolder: Boolean = false,
    val picker: PipelinePickerState = PipelinePickerState(),
    val isLoading: Boolean = false,
    val busyPipelineId: String? = null,
    val registrationComplete: Boolean = false,
    val detailPipelineId: String? = null,
    val logFiles: List<PipelineLogFile> = emptyList(),
    val selectedLogId: String? = null,
    val logContent: String = "",
    val logNextOffset: Long = 0,
    val logFollowingLatest: Boolean = true,
    val logLive: Boolean = false,
    val configPath: String = "",
    val configRevision: String = "",
    val configFields: List<PipelineConfigField> = emptyList(),
    val originalConfigValues: Map<String, String> = emptyMap(),
    val detailLoading: Boolean = false,
    val configSaving: Boolean = false,
    val mobileRtkRelay: MobileRtkRelayState = MobileRtkRelayState(),
    val message: String? = null,
    val error: String? = null
) {
    val configHasChanges: Boolean
        get() = configFields.any { originalConfigValues[it.path] != it.value }

    val configValuesValid: Boolean
        get() = configFields.all { configFieldValueValid(it.type, it.value) }
}

class PipelineViewModel(
    private val repository: JetsonRepository
) : ViewModel() {
    companion object {
        private const val LOG_POLL_INTERVAL_MS = 1_000L
        private const val LOG_CHUNK_BYTES = 128 * 1024
        private const val LOG_CONTENT_CHARS = 512 * 1024
    }

    private val _uiState = MutableStateFlow(PipelineUiState())
    val uiState = _uiState.asStateFlow()

    private var operationJob: Job? = null
    private var pollingJob: Job? = null
    private var pickerJob: Job? = null
    private var logPollingJob: Job? = null
    private var activeLogPipelineId: String? = null
    private var connectionGeneration = 0L

    init {
        viewModelScope.launch {
            repository.transportState.collectLatest { transport ->
                connectionGeneration += 1
                operationJob?.cancel()
                pollingJob?.cancel()
                pickerJob?.cancel()
                logPollingJob?.cancel()
                activeLogPipelineId = null
                if (transport is TransportState.Connected && transport.type != TransportType.BLE) {
                    refresh(connectionGeneration)
                    startPolling(connectionGeneration)
                } else {
                    _uiState.value = PipelineUiState()
                }
            }
        }
        viewModelScope.launch {
            repository.mobileRtkRelayState.collect { relay ->
                _uiState.value = _uiState.value.copy(mobileRtkRelay = relay)
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
            discoveredFolder = null,
            isDiscoveringFolder = false,
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
        _uiState.value = _uiState.value.copy(
            draft = updated,
            discoveredFolder = null,
            error = null
        )
        if (picker.target == PipelinePickerTarget.REPOSITORY) {
            discoverFolder(root.id, picker.currentPath)
        }
        return true
    }

    private fun discoverFolder(rootId: String, path: String) {
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                discoveredFolder = null,
                isDiscoveringFolder = true,
                error = null
            )
            repository.discoverPipelineFolder(rootId, path)
                .onSuccess { discovered ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            discoveredFolder = discovered,
                            isDiscoveringFolder = false,
                            draft = _uiState.value.draft.copy(id = discovered.pipelineId)
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isDiscoveringFolder = false,
                            error = error.message ?: "작업 폴더 규칙을 확인하지 못했습니다."
                        )
                    }
                }
        }
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
        if (
            !draft.canSubmit || _uiState.value.discoveredFolder == null ||
            _uiState.value.isLoading
        ) return
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val timeSync = repository.synchronizeSystemTime(System.currentTimeMillis())
            if (generation != connectionGeneration) return@launch
            if (timeSync.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "모바일 시간 동기화 실패: " +
                        (timeSync.exceptionOrNull()?.message ?: "알 수 없는 오류")
                )
                return@launch
            }
            repository.registerPipelineFolder(
                rootId = repositoryRoot.id,
                path = draft.repositoryPath,
                name = draft.label.trim(),
                autostart = draft.autostart
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
            if (action in setOf("start", "restart")) {
                val timeSync = repository.synchronizeSystemTime(System.currentTimeMillis())
                if (generation != connectionGeneration) return@launch
                if (timeSync.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        busyPipelineId = null,
                        error = "모바일 시간 동기화 실패: " +
                            (timeSync.exceptionOrNull()?.message ?: "알 수 없는 오류")
                    )
                    return@launch
                }
            }
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

    fun startLogStreaming(pipelineId: String) {
        activeLogPipelineId = pipelineId
        _uiState.value = _uiState.value.copy(
            detailPipelineId = pipelineId,
            logFiles = emptyList(),
            selectedLogId = null,
            logContent = "",
            logNextOffset = 0,
            logFollowingLatest = true,
            logLive = false,
            detailLoading = true,
            error = null
        )
        launchLogPolling(pipelineId)
    }

    fun stopLogStreaming() {
        activeLogPipelineId = null
        logPollingJob?.cancel()
        logPollingJob = null
        _uiState.value = _uiState.value.copy(logLive = false, detailLoading = false)
    }

    fun selectLogFile(logId: String) {
        val pipelineId = activeLogPipelineId ?: return
        val current = _uiState.value
        val selected = current.logFiles.firstOrNull { it.id == logId } ?: return
        _uiState.value = current.copy(
            selectedLogId = selected.id,
            logContent = "",
            logNextOffset = (selected.sizeBytes - LOG_CHUNK_BYTES).coerceAtLeast(0),
            logFollowingLatest = selected.id == current.logFiles.firstOrNull()?.id,
            detailLoading = true,
            error = null
        )
        launchLogPolling(pipelineId)
    }

    fun refreshLogs() {
        val pipelineId = activeLogPipelineId ?: return
        val current = _uiState.value
        val selected = current.logFiles.firstOrNull { it.id == current.selectedLogId }
        _uiState.value = current.copy(
            logContent = "",
            logNextOffset = selected
                ?.let { (it.sizeBytes - LOG_CHUNK_BYTES).coerceAtLeast(0) }
                ?: 0,
            detailLoading = true,
            error = null
        )
        launchLogPolling(pipelineId)
    }

    private fun launchLogPolling(pipelineId: String) {
        val generation = connectionGeneration
        logPollingJob?.cancel()
        logPollingJob = viewModelScope.launch {
            while (
                generation == connectionGeneration &&
                activeLogPipelineId == pipelineId
            ) {
                pollPipelineLogs(pipelineId, generation)
                delay(LOG_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollPipelineLogs(pipelineId: String, generation: Long) {
        val response = repository.getPipelineLogFiles(pipelineId).getOrElse { error ->
            if (generation == connectionGeneration && activeLogPipelineId == pipelineId) {
                _uiState.value = _uiState.value.copy(
                    detailLoading = false,
                    logLive = false,
                    error = error.message ?: "실행 로그를 불러오지 못했습니다."
                )
            }
            return
        }
        if (generation != connectionGeneration || activeLogPipelineId != pipelineId) return

        val files = response.files
        if (files.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                logFiles = emptyList(),
                selectedLogId = null,
                logContent = "",
                logNextOffset = 0,
                detailLoading = false,
                logLive = true,
                error = null
            )
            return
        }

        val current = _uiState.value
        val newest = files.first()
        var selectedId = current.selectedLogId
        var followingLatest = current.logFollowingLatest
        var content = current.logContent
        var offset = current.logNextOffset
        if (
            selectedId == null ||
            files.none { it.id == selectedId } ||
            (followingLatest && selectedId != newest.id)
        ) {
            selectedId = newest.id
            followingLatest = true
            content = ""
            offset = (newest.sizeBytes - LOG_CHUNK_BYTES).coerceAtLeast(0)
        }
        var selected = files.first { it.id == selectedId }
        if (offset > selected.sizeBytes) {
            content = ""
            offset = (selected.sizeBytes - LOG_CHUNK_BYTES).coerceAtLeast(0)
        }

        var reads = 0
        while (offset < selected.sizeBytes && reads < 4) {
            val chunk = repository.getPipelineLogChunk(
                pipelineId = pipelineId,
                logId = selected.id,
                offset = offset,
                limit = LOG_CHUNK_BYTES
            ).getOrElse { error ->
                if (generation == connectionGeneration && activeLogPipelineId == pipelineId) {
                    _uiState.value = _uiState.value.copy(
                        logFiles = files,
                        detailLoading = false,
                        logLive = false,
                        error = error.message ?: "실행 로그 내용을 불러오지 못했습니다."
                    )
                }
                return
            }
            if (generation != connectionGeneration || activeLogPipelineId != pipelineId) return
            val previousOffset = offset
            content = trimPipelineLogContent(
                content + chunk.content,
                LOG_CONTENT_CHARS
            )
            offset = chunk.nextOffset
            selected = selected.copy(
                modifiedAt = chunk.modifiedAt,
                sizeBytes = chunk.sizeBytes
            )
            reads += 1
            if (chunk.eof || chunk.nextOffset == previousOffset) break
        }
        val updatedFiles = files.map { if (it.id == selected.id) selected else it }
        _uiState.value = _uiState.value.copy(
            logFiles = updatedFiles,
            selectedLogId = selected.id,
            logContent = content,
            logNextOffset = offset,
            logFollowingLatest = followingLatest,
            detailLoading = false,
            logLive = true,
            error = null
        )
    }

    fun loadConfig(pipelineId: String) {
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                detailPipelineId = pipelineId,
                detailLoading = true,
                configPath = "",
                configRevision = "",
                configFields = emptyList(),
                originalConfigValues = emptyMap(),
                message = null,
                error = null
            )
            repository.getPipelineConfigFields(pipelineId)
                .onSuccess { document ->
                    if (generation == connectionGeneration) {
                        val values = document.fields.associate { it.path to it.value }
                        _uiState.value = _uiState.value.copy(
                            configPath = document.path,
                            configRevision = document.revision,
                            configFields = document.fields,
                            originalConfigValues = values,
                            detailLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            detailLoading = false,
                            error = error.message ?: "작업 설정을 불러오지 못했습니다."
                        )
                    }
                }
        }
    }

    fun setConfigValue(path: String, value: String) {
        _uiState.value = _uiState.value.copy(
            configFields = _uiState.value.configFields.map { field ->
                if (field.path == path) field.copy(value = value) else field
            },
            message = null,
            error = null
        )
    }

    fun saveConfig() {
        val pipelineId = _uiState.value.detailPipelineId ?: return
        val current = _uiState.value
        if (
            current.configSaving || !current.configHasChanges ||
            !current.configValuesValid || current.configRevision.isBlank()
        ) return
        val changedValues = current.configFields
            .filter { current.originalConfigValues[it.path] != it.value }
            .associate { it.path to it.value }
        val generation = connectionGeneration
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(configSaving = true, error = null)
            repository.updatePipelineConfigFields(
                pipelineId,
                current.configRevision,
                changedValues
            )
                .onSuccess { document ->
                    if (generation == connectionGeneration) {
                        val values = document.fields.associate { it.path to it.value }
                        _uiState.value = _uiState.value.copy(
                            configPath = document.path,
                            configRevision = document.revision,
                            configFields = document.fields,
                            originalConfigValues = values,
                            configSaving = false,
                            message = "${document.path} 설정을 저장했습니다. 재시작하면 적용됩니다."
                        )
                    }
                }
                .onFailure { error ->
                    if (generation == connectionGeneration) {
                        _uiState.value = _uiState.value.copy(
                            configSaving = false,
                            error = error.message ?: "작업 설정을 저장하지 못했습니다."
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

internal fun configFieldValueValid(type: PipelineConfigValueType, value: String): Boolean =
    when (type) {
        PipelineConfigValueType.BOOLEAN -> value == "true" || value == "false"
        PipelineConfigValueType.INTEGER -> value.toLongOrNull() != null
        PipelineConfigValueType.DECIMAL -> value.toDoubleOrNull()?.isFinite() == true
        PipelineConfigValueType.STRING,
        PipelineConfigValueType.NULL -> '\u0000' !in value
    }

internal fun trimPipelineLogContent(value: String, maxChars: Int): String {
    if (value.length <= maxChars) return value
    val tail = value.takeLast(maxChars)
    val firstLineEnd = tail.indexOf('\n')
    return if (firstLineEnd >= 0 && firstLineEnd + 1 < tail.length) {
        tail.substring(firstLineEnd + 1)
    } else {
        tail
    }
}
