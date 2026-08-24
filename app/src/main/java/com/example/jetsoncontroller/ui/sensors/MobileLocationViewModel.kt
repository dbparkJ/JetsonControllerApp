package com.example.jetsoncontroller.ui.sensors

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.location.AndroidMobileLocationTracker
import com.example.jetsoncontroller.data.location.MobileLocationCallbacks
import com.example.jetsoncontroller.data.location.MobileLocationEnvironment
import com.example.jetsoncontroller.data.location.MobileLocationFix
import com.example.jetsoncontroller.data.location.MobileLocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MobileLocationUiState(
    val trackingEnabled: Boolean = false,
    val trackerOperational: Boolean = true,
    val permissionGranted: Boolean = false,
    val providerAvailable: Boolean = false,
    val providerEnabled: Boolean = false,
    val fix: MobileLocationFix? = null,
    val nowEpochMillis: Long = 0L,
    val nowElapsedRealtimeNanos: Long = 0L,
    val error: String? = null
)

class MobileLocationViewModel(
    private val tracker: MobileLocationTracker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeClock: () -> Long = android.os.SystemClock::elapsedRealtimeNanos
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MobileLocationUiState(
            nowEpochMillis = clock(),
            nowElapsedRealtimeNanos = elapsedRealtimeClock()
        )
    )
    val uiState: StateFlow<MobileLocationUiState> = _uiState.asStateFlow()

    private var visible = false
    private var freshnessJob: Job? = null

    private val callbacks = MobileLocationCallbacks(
        onEnvironmentChanged = ::onEnvironmentChanged,
        onLocation = { fix ->
            _uiState.update { current ->
                val newestFix = newestMobileLocationFix(current.fix, fix)
                current.copy(
                    trackerOperational = true,
                    fix = newestFix,
                    nowEpochMillis = clock(),
                    nowElapsedRealtimeNanos = elapsedRealtimeClock(),
                    error = null
                )
            }
        },
        onError = { message ->
            _uiState.update {
                it.copy(
                    trackerOperational = false,
                    nowEpochMillis = clock(),
                    nowElapsedRealtimeNanos = elapsedRealtimeClock(),
                    error = message
                )
            }
        }
    )

    fun setVisible(isVisible: Boolean) {
        if (visible == isVisible) return
        visible = isVisible
        if (isVisible) startTracking() else stopTracking()
    }

    fun refresh() {
        if (!visible) return
        runCatching(tracker::stop)
        startTracking()
    }

    private fun startTracking() {
        freshnessJob?.cancel()
        _uiState.update {
            it.copy(
                trackingEnabled = true,
                trackerOperational = true,
                nowEpochMillis = clock(),
                nowElapsedRealtimeNanos = elapsedRealtimeClock(),
                error = null
            )
        }
        runCatching { tracker.start(callbacks) }
            .onFailure { error ->
                callbacks.onError(
                    error.message ?: "모바일 위치 추적을 시작하지 못했습니다."
                )
            }
        freshnessJob = viewModelScope.launch {
            while (isActive) {
                delay(MOBILE_LOCATION_FRESHNESS_TICK_MS)
                _uiState.update {
                    it.copy(
                        nowEpochMillis = clock(),
                        nowElapsedRealtimeNanos = elapsedRealtimeClock()
                    )
                }
            }
        }
    }

    private fun stopTracking() {
        freshnessJob?.cancel()
        freshnessJob = null
        runCatching(tracker::stop)
        _uiState.update {
            it.copy(
                trackingEnabled = false,
                nowEpochMillis = clock(),
                nowElapsedRealtimeNanos = elapsedRealtimeClock(),
                error = null
            )
        }
    }

    private fun onEnvironmentChanged(environment: MobileLocationEnvironment) {
        _uiState.update { current ->
            current.copy(
                trackerOperational = true,
                permissionGranted = environment.permissionGranted,
                providerAvailable = environment.providerAvailable,
                providerEnabled = environment.providerEnabled,
                fix = current.fix.takeIf { environment.permissionGranted },
                nowEpochMillis = clock(),
                nowElapsedRealtimeNanos = elapsedRealtimeClock(),
                error = null
            )
        }
    }

    override fun onCleared() {
        runCatching(tracker::stop)
    }

    class Factory(
        context: Context
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MobileLocationViewModel::class.java))
            return MobileLocationViewModel(
                AndroidMobileLocationTracker(appContext)
            ) as T
        }
    }
}

internal fun newestMobileLocationFix(
    current: MobileLocationFix?,
    candidate: MobileLocationFix
): MobileLocationFix = when {
    current == null -> candidate
    candidate.elapsedRealtimeNanos >= current.elapsedRealtimeNanos -> candidate
    else -> current
}

internal const val MOBILE_LOCATION_FRESHNESS_TICK_MS = 1_000L
