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
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val updatedAtEpochMillis: Long? = null
)

class CameraPreviewViewModel(
    private val repository: JetsonRepository
) : ViewModel() {
    private val visible = MutableStateFlow(false)
    private val sensorActive = MutableStateFlow(false)
    private val loadMutex = Mutex()
    private val _uiState = MutableStateFlow(CameraPreviewUiState())
    val uiState: StateFlow<CameraPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(visible, sensorActive) { isVisible, isActive ->
                isVisible && isActive
            }.collectLatest { shouldStream ->
                if (!shouldStream) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                    return@collectLatest
                }
                while (true) {
                    val loaded = loadFrame()
                    delay(if (loaded) CAMERA_PREVIEW_INTERVAL_MS else CAMERA_RETRY_INTERVAL_MS)
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

    private suspend fun loadFrame(forceRefresh: Boolean = false): Boolean = loadMutex.withLock {
        val current = _uiState.value
        _uiState.value = current.copy(
            isLoading = current.frame == null,
            isRefreshing = forceRefresh,
            error = null
        )
        return try {
            val bytes = repository.getCameraPreviewFrame().getOrThrow()
            val bitmap = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("카메라 프레임 형식을 읽을 수 없습니다.")
            }
            _uiState.value = CameraPreviewUiState(
                frame = bitmap,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = error.message ?: "카메라 프리뷰를 불러오지 못했습니다."
            )
            false
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

internal const val CAMERA_PREVIEW_INTERVAL_MS = 250L
internal const val CAMERA_RETRY_INTERVAL_MS = 1_000L
