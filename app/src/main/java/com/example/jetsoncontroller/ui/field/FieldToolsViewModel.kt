package com.example.jetsoncontroller.ui.field

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.network.JetsonCommandResultUnknownException
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.userFacingFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FieldState(
    val deviceId: String? = null, val online: Boolean = false,
    val runs: List<TaskRun> = emptyList(), val nextOffset: Int? = null,
    val selectedRun: TaskRun? = null, val route: List<RoutePoint> = emptyList(),
    val routeQuality: RunQuality? = null,
    val historyCurrent: Boolean = false,
    val loading: Boolean = false, val error: String? = null,
    val technicalError: String? = null,
    val terminalBusy: Boolean = false, val terminalOutput: String = "",
    val captureBusy: Boolean = false, val captureMessage: String? = null,
    val log: String? = null, val deletingRunId: String? = null, val undoTrashId: String? = null,
    val message: String? = null
)

class FieldToolsViewModel(private val repository: JetsonRepository) : ViewModel() {
    private val _state = MutableStateFlow(FieldState())
    val state = _state.asStateFlow()
    private var generation = 0L
    private var routeRequestGeneration = 0L
    private var historyJob: Job? = null
    private var routeJob: Job? = null
    private var logJob: Job? = null
    private var terminalJob: Job? = null
    private var captureJob: Job? = null
    private var deleteJob: Job? = null
    init {
        viewModelScope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { id, transport -> id to transport }
                .distinctUntilChanged().collectLatest { (id, transport) ->
                    generation++
                    routeRequestGeneration++
                    historyJob?.cancel(); routeJob?.cancel(); logJob?.cancel(); terminalJob?.cancel(); captureJob?.cancel(); deleteJob?.cancel()
                    val online = transport is TransportState.Connected && transport.type != TransportType.BLE && transport.deviceId.equals(id, true)
                    _state.value = if (id != _state.value.deviceId) FieldState(deviceId = id, online = online)
                        else _state.value.copy(online = online, historyCurrent = false, loading = false,
                            terminalBusy = false, captureBusy = false, deletingRunId = null,
                            error = null, technicalError = null)
                    if (online) {
                        while (true) { refresh(retainLoaded = true); delay(10_000) }
                    }
                }
        }
    }
    fun refresh(more: Boolean = false, retainLoaded: Boolean = false) {
        if (!_state.value.online || historyJob?.isActive == true || _state.value.deletingRunId != null) return
        val g = generation
        val offset = if (more) _state.value.nextOffset ?: return else 0
        historyJob = viewModelScope.launch {
            val showLoading = historyRefreshShowsLoading(more, retainLoaded, _state.value.runs.isNotEmpty())
            _state.value = _state.value.copy(loading = showLoading, error = null, technicalError = null)
            repository.taskRuns(offset).onSuccess { result ->
                if (g == generation) {
                    val previous = _state.value
                    val merged = if (more) (previous.runs + result.runs).distinctBy { it.id }
                        else if (retainLoaded) (result.runs + previous.runs).distinctBy { it.id }
                        else result.runs
                    val added = result.runs.count { fresh -> previous.runs.none { it.id == fresh.id } }
                    _state.value = previous.copy(runs = merged,
                        nextOffset = if (!more && retainLoaded && previous.runs.size > result.runs.size)
                            previous.nextOffset?.plus(added) else result.nextOffset,
                        historyCurrent = true, loading = false, error = null, technicalError = null)
                }

            }.onFailure { error -> if (g == generation) {
                val failure = userFacingFailure(error, "작업 기록을 불러오지 못했습니다. 다시 시도하세요.")
                _state.value = _state.value.copy(loading = false,
                    historyCurrent = if (more) _state.value.historyCurrent else false,
                    error = failure.message, technicalError = failure.technicalDetail)
            } }
        }
    }
    fun selectRun(run: TaskRun?) {
        routeJob?.cancel()
        val request = ++routeRequestGeneration
        _state.value = _state.value.copy(selectedRun = run, route = emptyList(), routeQuality = run?.quality)
        val g = generation
        if (run != null && _state.value.online) routeJob = viewModelScope.launch {
            do {
                repository.taskRoute(run.pipelineId, run.logId).onSuccess {
                    if (routeResponseIsCurrent(g, generation, request, routeRequestGeneration, _state.value.selectedRun?.id, run.id)) {
                        _state.value = _state.value.copy(route = it.points, routeQuality = it.quality ?: run.quality)
                    }
                }.onFailure {
                    if (routeResponseIsCurrent(g, generation, request, routeRequestGeneration, _state.value.selectedRun?.id, run.id)) {
                        val failure = userFacingFailure(it, "수집 경로를 불러오지 못했습니다. 다시 시도하세요.")
                        _state.value = _state.value.copy(error = failure.message,
                            technicalError = failure.technicalDetail)
                    }
                }
                delay(5_000)
            } while (run.isActiveRun() && g == generation && request == routeRequestGeneration)
        }
    }
    fun openLog(run: TaskRun) {
        logJob?.cancel()
        val g = generation
        logJob = viewModelScope.launch {
            repository.taskRunLog(run.pipelineId, run.logId).onSuccess {
                if (g == generation) _state.value = _state.value.copy(log = it.content.ifBlank { "저장된 로그가 없습니다." })
            }.onFailure { error -> if (g == generation) {
                val failure = userFacingFailure(error, "저장된 기록을 불러오지 못했습니다. 다시 시도하세요.")
                _state.value = _state.value.copy(error = failure.message,
                    technicalError = failure.technicalDetail)
            } }
        }
    }
    fun stopRoutePolling() { routeRequestGeneration++; routeJob?.cancel() }
    fun dismissMessage(shown: String) {
        if (_state.value.message == shown) _state.value = _state.value.copy(message = null, undoTrashId = null)
    }
    fun deleteRun(run: TaskRun) {
        if (!_state.value.online || _state.value.deletingRunId != null || run.isActiveRun()) return
        val g = generation
        historyJob?.cancel()
        routeJob?.cancel()
        routeRequestGeneration++
        logJob?.cancel()
        _state.value = _state.value.copy(deletingRunId = run.id, loading = false,
            error = null, technicalError = null, message = null)
        deleteJob = viewModelScope.launch {
            repository.deleteTaskRun(run.pipelineId, run.logId).onSuccess { trashed ->
                if (g == generation) {
                    val current = _state.value
                    _state.value = current.copy(
                        runs = current.runs.filterNot { it.id == run.id }, deletingRunId = null,
                        selectedRun = current.selectedRun?.takeUnless { it.id == run.id },
                        route = if (current.selectedRun?.id == run.id) emptyList() else current.route,
                        routeQuality = if (current.selectedRun?.id == run.id) null else current.routeQuality,
                        log = null,
                        undoTrashId = trashed.trashId.takeIf { trashed.restoreSupported && trashed.state == "TRASHED" },
                        message = if (trashed.restoreSupported && trashed.state == "TRASHED") {
                            "작업 이력을 휴지통으로 옮겼습니다. 수집 원본 데이터는 유지됩니다."
                        } else "작업 이력을 휴지통으로 옮겼지만 이 항목은 복원할 수 없습니다.")
                    refresh()
                }
            }.onFailure {
                if (g == generation) {
                    val failure = userFacingFailure(
                        it,
                        "작업 기록을 휴지통으로 옮기지 못했습니다. 다시 확인하세요."
                    )
                    _state.value = _state.value.copy(deletingRunId = null,
                        error = failure.message, technicalError = failure.technicalDetail)
                    refresh()
                }
            }
        }
    }
    fun undoDeleteRun() {
        if (!_state.value.online || _state.value.deletingRunId != null) return
        val trashId = _state.value.undoTrashId ?: return
        val g = generation
        _state.value = _state.value.copy(deletingRunId = trashId, message = null,
            error = null, technicalError = null)
        deleteJob?.cancel()
        deleteJob = viewModelScope.launch {
            repository.restoreTrash(trashId).onSuccess { restored ->
                if (g == generation) {
                    _state.value = _state.value.copy(
                        deletingRunId = null,
                        undoTrashId = null,
                        message = if (restored.state == "RESTORED") "${restored.name} 작업 이력을 복원했습니다."
                        else "복원 상태를 다시 확인해 주세요."
                    )
                    refresh()
                }
            }.onFailure { error ->
                if (g == generation) {
                    val failure = userFacingFailure(
                        error,
                        "작업 기록을 복원하지 못했습니다. 휴지통을 다시 확인하세요."
                    )
                    _state.value = _state.value.copy(deletingRunId = null,
                        error = failure.message, technicalError = failure.technicalDetail)
                }
            }
        }
    }
    fun dismissLog() { _state.value = _state.value.copy(log = null) }
    fun dismissCaptureMessage(shown: String) {
        if (_state.value.captureMessage == shown) {
            _state.value = _state.value.copy(captureMessage = null)
        }
    }
    fun execute(command: String) {
        if (!_state.value.online || _state.value.terminalBusy || command.isBlank()) return
        val g = generation
        terminalJob = viewModelScope.launch {
            _state.value = _state.value.copy(terminalBusy = true, terminalOutput = "실행 중…")
            repository.terminal(command).onSuccess {
                if (g == generation) _state.value = _state.value.copy(terminalBusy = false,
                    terminalOutput = it.output + "\n종료 코드: ${it.exitCode}" +
                        (if (it.timedOut) " · 15초 시간 제한" else "") + (if (it.truncated) " · 출력 제한 64KB" else ""))
            }.onFailure { if (g == generation) _state.value = _state.value.copy(terminalBusy = false, terminalOutput = it.message ?: "명령 실패") }
        }
    }
    fun capture(context: Context, device: Boolean, mobile: Boolean) {
        if (!_state.value.online || _state.value.captureBusy || (!device && !mobile)) return
        val g = generation
        _state.value = _state.value.copy(captureBusy = true, captureMessage = null)
        captureJob = viewModelScope.launch {
            var deviceSaved: String? = null
            try {
                val result = if (device) repository.captureFrame().getOrThrow() else null
                deviceSaved = result?.relativePath
                val bytes = if (result != null) Base64.decode(result.jpegBase64, Base64.DEFAULT)
                    else repository.getCameraPreviewFrame().getOrThrow().bytes
                val name = result?.name ?: "GEO_${System.currentTimeMillis()}.jpg"
                if (mobile) withContext(Dispatchers.IO) { saveToGallery(context.applicationContext, name, bytes) }
                if (g == generation) _state.value = _state.value.copy(captureMessage = listOfNotNull(
                    deviceSaved?.let { "장치에 저장했습니다." },
                    if (mobile) "모바일 갤러리에 저장했습니다." else null
                ).joinToString("\n"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (g == generation) {
                    val failure = userFacingFailure(error, "사진을 저장하지 못했습니다. 다시 시도하세요.")
                    _state.value = _state.value.copy(
                        captureMessage = deviceSaved?.let { "장치 저장은 완료했습니다.\n${failure.message}" }
                            ?: failure.message,
                        technicalError = failure.technicalDetail
                    )
                }
            } finally {
                if (g == generation) _state.value = _state.value.copy(captureBusy = false)
            }
        }
    }
    class Factory(private val repository: JetsonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = FieldToolsViewModel(repository) as T
    }
}

internal fun routeResponseIsCurrent(
    expectedDeviceGeneration: Long,
    currentDeviceGeneration: Long,
    expectedRequestGeneration: Long,
    currentRequestGeneration: Long,
    selectedRunId: String?,
    responseRunId: String
): Boolean = expectedDeviceGeneration == currentDeviceGeneration &&
    expectedRequestGeneration == currentRequestGeneration && selectedRunId == responseRunId

internal fun historyRefreshShowsLoading(
    more: Boolean,
    retainLoaded: Boolean,
    hasLoadedRuns: Boolean
): Boolean = more || !retainLoaded || !hasLoadedRuns

internal fun TaskRun.isActiveRun(): Boolean = active || state in setOf("STARTING", "RUNNING", "STOPPING")

internal fun saveToGallery(context: Context, name: String, bytes: ByteArray) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GEO&")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("갤러리 파일 생성 실패")
    try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("갤러리 쓰기 실패")
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        check(resolver.update(uri, values, null, null) > 0)
    } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
}
