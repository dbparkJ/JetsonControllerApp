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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.transportState.collectLatest { transport ->
                if (transport is TransportState.Connected) {
                    while (true) {
                        if (transport.type != com.example.jetsoncontroller.data.transport.TransportType.BLE) {
                            repository.refreshStatus()
                        }
                        delay(2_000L)
                    }
                }
            }
        }
    }

    val uiState =
        combine(
            repository.connectionState,
            repository.status,
            repository.transportState,
            repository.capabilities,
            repository.controlOperation
        ) {
                connection,
                status,
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
                    status,
                transportType = connectedTransport?.type,
                deviceName = connectedTransport?.deviceName ?: bleName ?: "Jetson",
                endpoint = connectedTransport?.endpoint,
                capabilities = capabilities,
                operationInProgress = operation.inProgress,
                operationMessage = operation.message,
                operationIsError = operation.isError
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
