package com.example.jetsoncontroller.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.JetsonDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DeviceListViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    private val permissionGranted =
        MutableStateFlow(false)

    val uiState =
        combine(
            repository.devices,
            repository.isScanning,
            repository.connectionState,
            permissionGranted
        ) {
                devices,
                scanning,
                connection,
                permission ->

            DeviceListUiState(
                devices = devices,
                isScanning = scanning,
                permissionGranted = permission,
                connectionState = connection
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted
                .WhileSubscribed(5_000),
            initialValue =
                DeviceListUiState()
        )


    fun onPermissionResult(
        granted: Boolean
    ) {

        permissionGranted.value =
            granted

        if (!granted) {
            repository.stopScan()
        }
    }


    fun toggleScan() {

        if (!permissionGranted.value) {
            return
        }

        if (
            repository.isScanning.value
        ) {

            repository.stopScan()

        } else {

            repository.startScan()
        }
    }


    fun connect(
        device: JetsonDevice
    ) {

        if (!permissionGranted.value) {
            return
        }

        repository.connect(
            device
        )
    }


    override fun onCleared() {

        repository.stopScan()

        super.onCleared()
    }


    class Factory(
        private val repository:
            JetsonRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel>
            create(
                modelClass: Class<T>
            ): T {

            return DeviceListViewModel(
                repository
            ) as T
        }
    }
}
