package com.example.jetsoncontroller.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.data.transport.TransportState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.jetsoncontroller.model.FanStatus

class DashboardViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    private val visible = MutableStateFlow(false)
    private val nowEpochMillis = MutableStateFlow(System.currentTimeMillis())
    private val fanControlState = MutableStateFlow(FanControlSnapshot())

    init {
        viewModelScope.launch {
            combine(repository.transportState, visible) { transport, isVisible ->
                transport to isVisible
            }.collectLatest { (transport, isVisible) ->
                if (transport is TransportState.Connected && isVisible) {
                    if (transport.type != com.example.jetsoncontroller.data.transport.TransportType.BLE) {
                        refreshFanStatus()
                    }
                }
            }
        }
        viewModelScope.launch {
            visible.collectLatest { isVisible ->
                while (isVisible) {
                    nowEpochMillis.value = System.currentTimeMillis()
                    delay(1_000L)
                }
            }
        }
    }

    private val statusWithFreshness = combine(
        repository.status,
        repository.statusUpdatedAtEpochMillis,
        nowEpochMillis
    ) { status, updatedAt, now ->
        StatusSnapshot(
            status = status,
            freshness = statusFreshness(updatedAt, now),
            ageSeconds = statusAgeSeconds(updatedAt, now)
        )
    }

    private val baseUiState =
        combine(
            repository.connectionState,
            statusWithFreshness,
            repository.transportState,
            repository.capabilities,
            repository.controlOperation
        ) {
                connection,
                statusSnapshot,
                transport,
                capabilities,
                operation ->

            val connectedTransport = transport as? TransportState.Connected
            val bleName = when (connection) {
                is com.example.jetsoncontroller.model.ConnectionState.Ready ->
                    connection.deviceName
                is com.example.jetsoncontroller.model.ConnectionState.Connected ->
                    connection.deviceName
                else -> null
            }

            DashboardUiState(
                connectionState =
                    connection,
                status =
                    statusSnapshot.status,
                transportType = connectedTransport?.type,
                isOnline = connectedTransport != null,
                fullControlAvailable = connectedTransport?.type ==
                    com.example.jetsoncontroller.data.transport.TransportType.LAN ||
                    connectedTransport?.type ==
                    com.example.jetsoncontroller.data.transport.TransportType.WIFI_DIRECT,
                deviceName = connectedTransport?.deviceName ?: bleName ?: "Jetson",
                endpoint = connectedTransport?.endpoint,
                capabilities = capabilities,
                operationInProgress = operation.inProgress,
                operationMessage = operation.message,
                operationIsError = operation.isError,
                statusFreshness = statusSnapshot.freshness,
                statusAgeSeconds = statusSnapshot.ageSeconds
            )
        }

    val uiState = combine(baseUiState, fanControlState) { state, fan ->
        state.copy(
            fanStatus = fan.status,
            fanLoading = fan.loading,
            fanError = fan.error
        )
    }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted
                    .WhileSubscribed(
                        5_000
                    ),
            initialValue =
                DashboardUiState()
        )


    fun requestStatus() =
        repository.requestStatus()

    fun setVisible(isVisible: Boolean) {
        visible.value = isVisible
        if (isVisible) {
            nowEpochMillis.value = System.currentTimeMillis()
        }
    }


    fun reboot() =
        repository.reboot()


    fun shutdown() =
        repository.shutdown()

    fun refreshFan() {
        viewModelScope.launch { refreshFanStatus() }
    }

    fun setFanAuto() {
        updateFan("AUTO", null)
    }

    fun setFanManual(percent: Int) {
        updateFan("MANUAL", percent.coerceIn(20, 100))
    }

    private fun updateFan(mode: String, percent: Int?) {
        if (fanControlState.value.loading) return
        viewModelScope.launch {
            fanControlState.value = fanControlState.value.copy(loading = true, error = null)
            repository.setFan(mode, percent)
                .onSuccess { fanControlState.value = FanControlSnapshot(status = it) }
                .onFailure {
                    fanControlState.value = fanControlState.value.copy(
                        loading = false,
                        error = it.message ?: "FAN을 제어하지 못했습니다."
                    )
                }
        }
    }

    private suspend fun refreshFanStatus() {
        if (fanControlState.value.loading) return
        fanControlState.value = fanControlState.value.copy(loading = true, error = null)
        repository.getFanStatus()
            .onSuccess { fanControlState.value = FanControlSnapshot(status = it) }
            .onFailure {
                fanControlState.value = fanControlState.value.copy(
                    loading = false,
                    error = it.message ?: "FAN 상태를 확인하지 못했습니다."
                )
            }
    }

    fun clearOperationMessage() =
        repository.clearControlMessage()


    fun disconnect() =
        repository.disconnect()


    class Factory(
        private val repository:
            JetsonRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel>
            create(
                modelClass: Class<T>
            ): T {

            return DashboardViewModel(
                repository
            ) as T
        }
    }
}

internal const val STATUS_POLL_INTERVAL_MS = 1_000L

private data class StatusSnapshot(
    val status: com.example.jetsoncontroller.model.JetsonStatus,
    val freshness: StatusFreshness,
    val ageSeconds: Long?
)

private data class FanControlSnapshot(
    val status: FanStatus? = null,
    val loading: Boolean = false,
    val error: String? = null
)
