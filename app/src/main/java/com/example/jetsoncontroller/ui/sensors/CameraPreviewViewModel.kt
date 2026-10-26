package com.example.jetsoncontroller.ui.sensors

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val decodeFrame: suspend (ByteArray) -> Bitmap = ::decodeCameraPreviewBitmap,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val visible = MutableStateFlow(false)
    private val sensorActive = MutableStateFlow(false)
    private val previewTargetId = MutableStateFlow(savedStateHandle.get<String>(CAMERA_PREVIEW_TARGET_KEY))
    private val loadMutex = Mutex()
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
                    while (true) {
                        when (loadFrame()) {
                            FrameLoadResult.REALTIME -> Unit
                            FrameLoadResult.UNCHANGED -> delay(CAMERA_PREVIEW_LEGACY_INTERVAL_MS)
                            FrameLoadResult.LEGACY -> delay(CAMERA_PREVIEW_LEGACY_INTERVAL_MS)
                            FrameLoadResult.FAILURE -> delay(CAMERA_RETRY_INTERVAL_MS)
                        }
                    }
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
        viewModelScope.launch { loadFrame(forceRefresh = true) }
    }

    private suspend fun loadFrame(
        forceRefresh: Boolean = false
    ): FrameLoadResult = loadMutex.withLock {
        val transport = repository.transportState.value as? com.example.jetsoncontroller.data.transport.TransportState.Connected
        if (!visible.value || !sensorActive.value || transport == null ||
            !transport.deviceId.equals(_uiState.value.deviceId, true) ||
            !_uiState.value.deviceId.equals(repository.selectedDeviceId.value, true)) return@withLock FrameLoadResult.FAILURE
        val generation = streamGeneration
        val current = _uiState.value
        _uiState.value = current.copy(
            isLoading = current.frame == null && current.error == null,
            isRefreshing = forceRefresh,
            error = current.error
        )
        try {
            val preview = fetchFrame(current.frameRevision).getOrThrow()
            val receivedAt = elapsedRealtime()
            val receivedAtEpochMillis = wallClock()
            if (generation != streamGeneration || !currentTargetConnected(current.deviceId)) return@withLock FrameLoadResult.FAILURE
            if (preview.revision != null && preview.revision == current.frameRevision) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    checkedAtElapsedMillis = elapsedRealtime(),
                    error = null
                )
                FrameLoadResult.UNCHANGED
            } else {
                val bitmap = decodeFrame(preview.bytes)
                if (generation != streamGeneration || !currentTargetConnected(current.deviceId)) return@withLock FrameLoadResult.FAILURE
                val checkedAt = elapsedRealtime()
                _uiState.value = CameraPreviewUiState(
                    deviceId = current.deviceId,
                    deviceName = current.deviceName,
                    targetConnected = current.targetConnected,
                    targetStillCurrent = current.targetStillCurrent,
                    checkedAtElapsedMillis = checkedAt,
                    frameFreshness = _uiState.value.frameFreshness.received(receivedAt).checked(checkedAt),
                    frame = bitmap,
                    frameRevision = preview.revision,
                    updatedAtEpochMillis = receivedAtEpochMillis
                )
                if (preview.revision == null) {
                    FrameLoadResult.LEGACY
                } else {
                    FrameLoadResult.REALTIME
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation != streamGeneration) return@withLock FrameLoadResult.FAILURE
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                checkedAtElapsedMillis = elapsedRealtime(),
                error = userFacingFailure(error, "카메라 프리뷰를 불러오지 못했습니다. 다시 시도하세요.").message
            )
            FrameLoadResult.FAILURE
        }
    }

    private fun currentTargetConnected(targetId: String?): Boolean = cameraTargetCanStream(
        visible.value, sensorActive.value, targetId, repository.selectedDeviceId.value,
        (repository.transportState.value as? com.example.jetsoncontroller.data.transport.TransportState.Connected)?.deviceId)

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CameraPreviewViewModel(repository) as T

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            CameraPreviewViewModel(repository, savedStateHandle = extras.createSavedStateHandle()) as T
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

private suspend fun decodeCameraPreviewBitmap(bytes: ByteArray): Bitmap = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "카메라 프레임 형식을 읽을 수 없습니다." }
    val options = BitmapFactory.Options().apply {
        inSampleSize = cameraPreviewSampleSize(bounds.outWidth, bounds.outHeight)
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: error("카메라 프레임 형식을 읽을 수 없습니다.")
}
