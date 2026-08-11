package com.example.jetsoncontroller.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.protocol.JetsonCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    val uiState =
        combine(
            repository.connectionState,
            repository.status
        ) {
                connection,
                status ->

            DashboardUiState(
                connectionState =
                    connection,
                status =
                    status
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
