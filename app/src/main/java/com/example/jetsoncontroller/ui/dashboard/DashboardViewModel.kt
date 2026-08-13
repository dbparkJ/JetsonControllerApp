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

class DashboardViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    private val visible = MutableStateFlow(false)
    private val nowEpochMillis = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            combine(repository.transportState, visible) { transport, isVisible ->
                transport to isVisible
            }.collectLatest { (transport, isVisible) ->
                if (transport is TransportState.Connected && isVisible) {
                    while (true) {
                        if (transport.type != com.example.jetsoncontroller.data.transport.TransportType.BLE) {
                            repository.refreshStatus()
                        }
                        nowEpochMillis.value = System.currentTimeMillis()
                        delay(5_000L)
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

    val uiState =
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
                deviceName = connectedTransport?.deviceName ?: bleName ?: "Jetson",
                endpoint = connectedTransport?.endpoint,
                capabilities = capabilities,
                operationInProgress = operation.inProgress,
                operationMessage = operation.message,
                operationIsError = operation.isError,
                statusFreshness = statusSnapshot.freshness,
                statusAgeSeconds = statusSnapshot.ageSeconds
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


    fun startSystem() =
        repository.startSystem()


    fun stopSystem() =
        repository.stopSystem()


    fun restartServices() =
        repository.restartServices()


    fun reboot() =
        repository.reboot()


    fun shutdown() =
        repository.shutdown()

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

private data class StatusSnapshot(
    val status: com.example.jetsoncontroller.model.JetsonStatus,
    val freshness: StatusFreshness,
    val ageSeconds: Long?
)
