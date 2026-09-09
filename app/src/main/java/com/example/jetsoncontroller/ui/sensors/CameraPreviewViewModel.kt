package com.example.jetsoncontroller.ui.sensors

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val checkedAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long? = null
)

class CameraPreviewViewModel(
    private val repository: JetsonRepository
) : ViewModel() {
    private val visible = MutableStateFlow(false)
    private val sensorActive = MutableStateFlow(false)
    private val loadMutex = Mutex()
    private var streamGeneration = 0L
    private val _uiState = MutableStateFlow(CameraPreviewUiState())
    val uiState: StateFlow<CameraPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(visible, sensorActive, repository.selectedDeviceId, repository.transportState) { isVisible, isActive, deviceId, transport ->
                Triple(isVisible && isActive && transport is com.example.jetsoncontroller.data.transport.TransportState.Connected &&
                    transport.deviceId.equals(deviceId, true), deviceId, transport)
            }.collectLatest { (shouldStream, _, _) ->
                streamGeneration += 1
                _uiState.value = CameraPreviewUiState()
                if (!shouldStream) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                    return@collectLatest
                }
                while (true) {
                    when (loadFrame()) {
                        FrameLoadResult.REALTIME -> Unit
                        FrameLoadResult.LEGACY -> delay(CAMERA_PREVIEW_LEGACY_INTERVAL_MS)
                        FrameLoadResult.FAILURE -> delay(CAMERA_RETRY_INTERVAL_MS)
                    }
                }
            }
        }
    }

    fun setVisible(isVisible: Boolean) {
        visible.value = isVisible
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
        val generation = streamGeneration
        val current = _uiState.value
        _uiState.value = current.copy(
            isLoading = current.frame == null,
            isRefreshing = forceRefresh,
            error = null
        )
        try {
            val preview = repository.getCameraPreviewFrame(current.frameRevision).getOrThrow()
            if (generation != streamGeneration || !visible.value) return@withLock FrameLoadResult.FAILURE
            if (preview.revision != null && preview.revision == current.frameRevision) {
                _uiState.value = current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    checkedAtEpochMillis = System.currentTimeMillis(),
                    error = null
                )
                FrameLoadResult.REALTIME
            } else {
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(preview.bytes, 0, preview.bytes.size)
                        ?: error("카메라 프레임 형식을 읽을 수 없습니다.")
                }
                if (generation != streamGeneration || !visible.value) return@withLock FrameLoadResult.FAILURE
                _uiState.value = CameraPreviewUiState(
                    checkedAtEpochMillis = System.currentTimeMillis(),
                    frame = bitmap,
                    frameRevision = preview.revision,
                    updatedAtEpochMillis = System.currentTimeMillis()
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
                checkedAtEpochMillis = System.currentTimeMillis(),
                error = error.message ?: "카메라 프리뷰를 불러오지 못했습니다."
            )
            FrameLoadResult.FAILURE
        }
    }

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CameraPreviewViewModel(repository) as T
    }
}

private enum class FrameLoadResult {
    REALTIME,
    LEGACY,
    FAILURE
}

internal const val CAMERA_PREVIEW_LEGACY_INTERVAL_MS = 67L
internal const val CAMERA_RETRY_INTERVAL_MS = 1_000L
