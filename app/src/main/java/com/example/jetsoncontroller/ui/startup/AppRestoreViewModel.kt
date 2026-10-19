package com.example.jetsoncontroller.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.credentials.DeviceCredentialStore
import com.example.jetsoncontroller.model.RegisteredDevice
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppRestoreState {
    data object Loading : AppRestoreState
    data class Restored(val devices: List<RegisteredDevice>) : AppRestoreState
    data class Failed(val cause: Throwable) : AppRestoreState
}

class AppRestoreViewModel(
    private val restoreDevices: suspend () -> List<RegisteredDevice>
) : ViewModel() {
    private val _state = MutableStateFlow<AppRestoreState>(AppRestoreState.Loading)
    val state = _state.asStateFlow()
    private var restoreJob: Job? = null

    init { retry() }

    fun retry() {
        if (restoreJob?.isActive == true) return
        _state.value = AppRestoreState.Loading
        restoreJob = viewModelScope.launch {
            _state.value = try {
                AppRestoreState.Restored(restoreDevices().toList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Exception) {
                AppRestoreState.Failed(cause)
            }
        }
    }

    class Factory(private val credentials: DeviceCredentialStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppRestoreViewModel::class.java))
            return AppRestoreViewModel {
                withContext(Dispatchers.IO) {
                    restoreRegisteredDevices(credentials.registeredDevices, credentials::getSecret)
                }
            } as T
        }
    }
}

internal suspend fun restoreRegisteredDevices(
    credentials: Flow<List<DeviceCredentialStore.StoredCredential>>,
    readSecret: suspend (String) -> String?
): List<RegisteredDevice> = credentials.first().map { stored ->
    // Metadata alone does not establish that a restored keystore is readable.
    if (readSecret(stored.deviceId).isNullOrBlank()) throw CredentialRestoreException()
    RegisteredDevice(stored.deviceId, stored.deviceName)
}.sortedBy { it.deviceName.lowercase() }

internal class CredentialRestoreException : IOException()

/** Never expose storage paths, credential values or raw exception messages to operators. */
internal fun restoreFailureWords(cause: Throwable): String = when (cause) {
    is CredentialRestoreException, is java.security.GeneralSecurityException, is SecurityException ->
        "저장된 장치 인증 정보를 읽지 못했습니다. 다시 시도해 주세요."
    is IOException -> "저장된 장치 정보를 읽지 못했습니다. 다시 시도해 주세요."
    else -> "장치 정보를 복원하지 못했습니다. 다시 시도해 주세요."
}

/** Monotonic times; the minimum starts at the first loader frame, not at process launch. */
internal class StartupTiming(private val startedAtMillis: Long, private var splashVisible: Boolean = false) {
    var loadingVisibleAtMillis: Long? = null
        private set
    private var loaderDrawn = false

    fun canDraw(state: AppRestoreState, nowMillis: Long): Boolean =
        state !is AppRestoreState.Loading || nowMillis - startedAtMillis >= FIRST_FRAME_GATE_MS

    fun loadingFrame(nowMillis: Long) {
        loaderDrawn = true
        if (!splashVisible && loadingVisibleAtMillis == null) loadingVisibleAtMillis = nowMillis
    }

    fun splashRemoved(state: AppRestoreState, nowMillis: Long) {
        splashVisible = false
        if (state is AppRestoreState.Loading && loaderDrawn) loadingFrame(nowMillis)
    }

    fun remainingLoadingMillis(nowMillis: Long): Long = loadingVisibleAtMillis?.let {
        (MINIMUM_LOADING_MS - (nowMillis - it)).coerceAtLeast(0L)
    } ?: 0L

    fun retry() {
        loadingVisibleAtMillis = null
        loaderDrawn = false
    }

    fun crossfadeMillis(reducedMotion: Boolean): Int =
        if (loadingVisibleAtMillis == null || reducedMotion) 0 else CROSSFADE_MS

    companion object {
        const val FIRST_FRAME_GATE_MS = 300L
        const val MINIMUM_LOADING_MS = 400L
        const val CROSSFADE_MS = 240
    }
}
