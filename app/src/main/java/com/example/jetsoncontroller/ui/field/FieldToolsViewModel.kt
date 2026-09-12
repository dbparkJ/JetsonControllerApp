package com.example.jetsoncontroller.ui.field

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FieldState(
    val deviceId: String? = null, val online: Boolean = false,
    val runs: List<TaskRun> = emptyList(), val nextOffset: Int? = null,
    val selectedRun: TaskRun? = null, val route: List<RoutePoint> = emptyList(),
    val loading: Boolean = false, val error: String? = null,
    val terminalBusy: Boolean = false, val terminalOutput: String = "",
    val captureBusy: Boolean = false, val captureMessage: String? = null,
    val log: String? = null
)

class FieldToolsViewModel(private val repository: JetsonRepository) : ViewModel() {
    private val _state = MutableStateFlow(FieldState())
    val state = _state.asStateFlow()
    private var generation = 0L
    private var historyJob: Job? = null
    private var routeJob: Job? = null
    private var logJob: Job? = null
    private var terminalJob: Job? = null
    private var captureJob: Job? = null
    init {
        viewModelScope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { id, transport -> id to transport }
                .distinctUntilChanged().collectLatest { (id, transport) ->
                    generation++
                    historyJob?.cancel(); routeJob?.cancel(); logJob?.cancel(); terminalJob?.cancel(); captureJob?.cancel()
                    val online = transport is TransportState.Connected && transport.type != TransportType.BLE && transport.deviceId.equals(id, true)
                    _state.value = if (id != _state.value.deviceId) FieldState(deviceId = id, online = online)
                        else _state.value.copy(online = online, loading = false, terminalBusy = false, captureBusy = false)
                    if (online) {
                        while (true) { refresh(retainLoaded = true); delay(10_000) }
                    }
                }
        }
    }
    fun refresh(more: Boolean = false, retainLoaded: Boolean = false) {
        if (!_state.value.online || historyJob?.isActive == true) return
        val g = generation
        val offset = if (more) _state.value.nextOffset ?: return else 0
        historyJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
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
                        loading = false, error = null)
                }

            }.onFailure { if (g == generation) _state.value = _state.value.copy(loading = false,
                error = it.message ?: "기록을 불러오지 못했습니다. 장치 API 업데이트를 확인하세요.") }
        }
    }
    fun selectRun(run: TaskRun?) {
        routeJob?.cancel()
        _state.value = _state.value.copy(selectedRun = run, route = emptyList())
        val g = generation
        if (run != null && _state.value.online) routeJob = viewModelScope.launch {
            do {
                repository.taskRoute(run.pipelineId, run.logId).onSuccess {
                    if (g == generation) _state.value = _state.value.copy(route = it.points)
                }.onFailure { if (g == generation) _state.value = _state.value.copy(error = it.message) }
                delay(5_000)
            } while (run.state == "RUNNING" && g == generation)
        }
    }
    fun openLog(run: TaskRun) {
        logJob?.cancel()
        val g = generation
        logJob = viewModelScope.launch {
            repository.taskRunLog(run.pipelineId, run.logId).onSuccess {
                if (g == generation) _state.value = _state.value.copy(log = it.content.ifBlank { "저장된 로그가 없습니다." })
            }.onFailure { if (g == generation) _state.value = _state.value.copy(error = it.message) }
        }
    }
    fun stopRoutePolling() { routeJob?.cancel() }
    fun dismissLog() { _state.value = _state.value.copy(log = null) }
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
                    deviceSaved?.let { "장치: $it" }, if (mobile) "모바일: Pictures/GEO&/$name" else null).joinToString("\n"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (g == generation) _state.value = _state.value.copy(captureMessage =
                    (deviceSaved?.let { "장치 저장 완료: $it\n모바일 저장 실패: " } ?: "캡처 실패: ") + error.message)
            } finally {
                if (g == generation) _state.value = _state.value.copy(captureBusy = false)
            }
        }
    }
    class Factory(private val repository: JetsonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = FieldToolsViewModel(repository) as T
    }
}

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
