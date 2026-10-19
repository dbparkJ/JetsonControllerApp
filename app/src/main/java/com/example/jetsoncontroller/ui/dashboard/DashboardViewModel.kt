package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.ui.dev.DevFanSnapshot
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

class DashboardViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    private val visible = MutableStateFlow(false)
    private val nowEpochMillis = MutableStateFlow(System.currentTimeMillis())
    private val fanController = com.example.jetsoncontroller.ui.dev.DevFanController(repository, viewModelScope)

    init {
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

    val uiState = combine(baseUiState, fanController.state) { state, fan ->
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

    fun refreshFan() = fanController.refresh()

    fun setFanAuto() = fanController.auto()

    fun setFanManual(percent: Int) = fanController.manual(percent)

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


internal fun DevFanSnapshot.finishedLoading(): DevFanSnapshot =
    if (loading) copy(loading = false) else this
