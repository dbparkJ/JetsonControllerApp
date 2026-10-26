package com.example.jetsoncontroller.ui.sensors

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.CameraPreviewFrame
import com.example.jetsoncontroller.ui.userFacingFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class CameraPreviewUiState(
    val frame: Bitmap? = null,
    val frameRevision: Long? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val checkedAtElapsedMillis: Long = 0L,
    val frameFreshness: CameraFrameFreshness = CameraFrameFreshness(),
    val updatedAtEpochMillis: Long? = null,
    val deviceId: String? = null,
    val deviceName: String = "선택한 장치",
    val targetConnected: Boolean = false,
    val targetStillCurrent: () -> Boolean = { false }
)

class CameraPreviewViewModel(
    private val repository: JetsonRepository,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val fetchFrame: suspend (Long?) -> Result<CameraPreviewFrame> = repository::getCameraPreviewFrame,
    private val decodeFrame: (suspend (ByteArray) -> Bitmap)? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val environment: () -> CameraPreviewEnvironment = { CameraPreviewEnvironment() }
) : ViewModel() {
    private val visible = MutableStateFlow(false)
    private val sensorActive = MutableStateFlow(false)
    private val previewTargetId = MutableStateFlow(savedStateHandle.get<String>(CAMERA_PREVIEW_TARGET_KEY))
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private var streamGeneration = 0L
    private val _uiState = MutableStateFlow(CameraPreviewUiState())
    val uiState: StateFlow<CameraPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.selectedDeviceId.collect { deviceId ->
                if (visible.value && previewTargetId.value == null && deviceId != null) setTarget(deviceId)
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(visible, sensorActive, repository.selectedDeviceId, repository.transportState, previewTargetId) { isVisible, isActive, deviceId, transport, targetId ->
                Triple(cameraTargetCanStream(isVisible, isActive, targetId, deviceId,
                    (transport as? com.example.jetsoncontroller.data.transport.TransportState.Connected)?.deviceId), targetId, transport)
            }.collectLatest { (shouldStream, deviceId, _) ->
                streamGeneration += 1
                _uiState.value = CameraPreviewUiState(deviceId = deviceId, targetConnected = shouldStream,
                    targetStillCurrent = { currentTargetConnected(deviceId) },
                    deviceName = repository.registeredDevices.value.firstOrNull { it.deviceId.equals(deviceId, true) }?.deviceName ?: "선택한 장치")
                if (!shouldStream) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                    return@collectLatest
                }
                coroutineScope {
                    launch {
                        while (true) {
                            val now = elapsedRealtime()
                            _uiState.value = _uiState.value.let { state -> state.copy(
                                checkedAtElapsedMillis = now,
                                frameFreshness = state.frameFreshness.checked(now)
                            ) }
                            delay(CAMERA_FRAME_CHECK_INTERVAL_MS)
                        }
                    }
                    streamFrames()
                }
            }
        }
    }

    fun setVisible(isVisible: Boolean) {
        if (isVisible && previewTargetId.value == null) setTarget(repository.selectedDeviceId.value)
        visible.value = isVisible
        if (!isVisible) {
            streamGeneration += 1
            // Drop the bitmap synchronously, before the stream collector processes visibility.
            _uiState.value = CameraPreviewUiState()
        }
    }

    fun close() {
        setVisible(false)
        sensorActive.value = false
        setTarget(null)
    }

    private fun setTarget(deviceId: String?) {
        previewTargetId.value = deviceId
        savedStateHandle[CAMERA_PREVIEW_TARGET_KEY] = deviceId
    }

    fun trimMemory() {
        if (!visible.value) return
        streamGeneration += 1
        _uiState.value = _uiState.value.copy(frame = null, frameRevision = null,
            updatedAtEpochMillis = null, frameFreshness = CameraFrameFreshness())
    }

    fun setSensorActive(isActive: Boolean) {
        sensorActive.value = isActive
    }

    fun refresh() {
        if (!visible.value || !sensorActive.value) return
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        refreshRequests.trySend(Unit)
    }

    private data class ReceivedFrame(
        val result: Result<CameraPreviewFrame>,
        val afterRevision: Long?,
        val generation: Long,
        val targetId: String?,
        val receivedAt: Long,
        val receivedAtEpochMillis: Long
    )

    private suspend fun receiveFrame(afterRevision: Long?): ReceivedFrame {
        currentCoroutineContext().ensureActive()
        val generation = streamGeneration
        val target = _uiState.value.deviceId
        refreshRequests.tryReceive()
        _uiState.value = _uiState.value.let { it.copy(isLoading = it.frame == null && it.error == null) }
        val result = try {
            if (currentTargetConnected(target)) fetchFrame(afterRevision)
            else Result.failure(IllegalStateException("카메라 연결이 변경되었습니다."))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
        currentCoroutineContext().ensureActive()
        return ReceivedFrame(result, afterRevision, generation, target, elapsedRealtime(), wallClock())
    }

    /** One lookahead JPEG at most; no second fetch starts until the first has returned. */
    private suspend fun streamFrames(): Unit = coroutineScope {
        var pending: Deferred<ReceivedFrame>? = null
        try {
            while (true) {
                val received = pending?.await() ?: receiveFrame(_uiState.value.frameRevision)
                pending = null
                var outcome = FrameLoadResult.FAILURE
                try {
                    if (received.generation == streamGeneration && currentTargetConnected(received.targetId)) {
                        val preview = received.result.getOrThrow()
                        if (preview.revision != null && preview.revision == received.afterRevision) {
                            _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false,
                                checkedAtElapsedMillis = elapsedRealtime(), error = null)
                            outcome = FrameLoadResult.UNCHANGED
                        } else {
                            if (preview.revision != null &&
                                cameraPreviewCanOverlap(repository.transportState.value, environment())) {
                                // Start on Main before dispatching decode to Default. Revision belongs to
                                // the last received JPEG, which may not have reached the screen yet.
                                pending = async(start = CoroutineStart.UNDISPATCHED) { receiveFrame(preview.revision) }
                            }
                            val bitmap = if (decodeFrame != null) decodeFrame.invoke(preview.bytes)
                                else CameraPreviewBitmapDecoder.decode(preview.bytes)
                            currentCoroutineContext().ensureActive()
                            if (received.generation == streamGeneration && currentTargetConnected(received.targetId)) {
                                val checkedAt = elapsedRealtime()
                                _uiState.value = _uiState.value.copy(
                                    frame = bitmap, frameRevision = preview.revision,
                                    isLoading = false, isRefreshing = false, error = null,
                                    checkedAtElapsedMillis = checkedAt,
                                    frameFreshness = _uiState.value.frameFreshness.received(received.receivedAt).checked(checkedAt),
                                    updatedAtEpochMillis = received.receivedAtEpochMillis
                                )
                                outcome = if (preview.revision == null) FrameLoadResult.LEGACY else FrameLoadResult.REALTIME
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (received.generation == streamGeneration && currentTargetConnected(received.targetId)) {
                        _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false,
                            checkedAtElapsedMillis = elapsedRealtime(),
                            error = userFacingFailure(error, "카메라 프리뷰를 불러오지 못했습니다. 다시 시도하세요.").message)
                    }
                }
                if (outcome != FrameLoadResult.REALTIME) {
                    pending?.cancelAndJoin()
                    pending = null
                    val interval = if (outcome == FrameLoadResult.FAILURE) CAMERA_RETRY_INTERVAL_MS
                        else CAMERA_PREVIEW_LEGACY_INTERVAL_MS
                    withTimeoutOrNull(interval) { refreshRequests.receive() }
                }
            }
        } finally {
            pending?.cancel()
        }
    }

    private fun currentTargetConnected(targetId: String?): Boolean = cameraTargetCanStream(
        visible.value, sensorActive.value, targetId, repository.selectedDeviceId.value,
        (repository.transportState.value as? com.example.jetsoncontroller.data.transport.TransportState.Connected)?.deviceId)

    class Factory(
        private val repository: JetsonRepository,
        context: android.content.Context
    ) : ViewModelProvider.Factory {
        private val context = context.applicationContext
        private fun environment() = cameraPreviewEnvironment(context, repository.transportState.value)
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CameraPreviewViewModel(repository, environment = ::environment) as T

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            CameraPreviewViewModel(repository, savedStateHandle = extras.createSavedStateHandle(), environment = ::environment) as T
    }
}

private enum class FrameLoadResult {
    REALTIME,
    UNCHANGED,
    LEGACY,
    FAILURE
}

internal const val CAMERA_PREVIEW_LEGACY_INTERVAL_MS = 67L
internal const val CAMERA_RETRY_INTERVAL_MS = 1_000L
internal const val CAMERA_PREVIEW_TARGET_KEY = "cameraPreviewTarget"
