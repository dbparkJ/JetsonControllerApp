package com.example.jetsoncontroller.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.RegisteredDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DeviceListViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    private val permissionGranted =
        MutableStateFlow(false)

    private data class ReconnectState(
        val deviceId: String? = null,
        val error: String? = null
    )

    private val reconnectState =
        MutableStateFlow(ReconnectState())

    private var reconnectJob: Job? = null

    private val savedDevicesState =
        combine(
            repository.registeredDevices,
            reconnectState
        ) { devices, reconnect ->
            devices to reconnect
        }

    val uiState =
        combine(
            repository.devices,
            repository.isScanning,
            repository.connectionState,
            permissionGranted,
            savedDevicesState
        ) {
                devices,
                scanning,
                connection,
                permission,
                savedDevices ->

            DeviceListUiState(
                devices = devices,
                registeredDevices = savedDevices.first,
                isScanning = scanning,
                permissionGranted = permission,
                connectionState = connection,
                reconnectingDeviceId = savedDevices.second.deviceId,
                reconnectError = savedDevices.second.error
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


    fun reconnect(
        device: RegisteredDevice
    ) {

        if (!permissionGranted.value) {
            reconnectState.value = ReconnectState(
                error = "주변 기기 권한을 허용해 주세요."
            )
            return
        }

        reconnectJob?.cancel()
        reconnectState.value = ReconnectState(
            deviceId = device.deviceId
        )

        reconnectJob = viewModelScope.launch {
            val alreadyVisible = repository.devices.value
                .firstOrNull {
                    it.name.equals(
                        device.deviceName,
                        ignoreCase = true
                    )
                }

            val found = alreadyVisible ?: run {
                repository.startScan(jetsonOnly = true)

                withTimeoutOrNull(20_000L) {
                    repository.devices
                        .map { devices ->
                            devices.firstOrNull {
                                it.name.equals(
                                    device.deviceName,
                                    ignoreCase = true
                                )
                            }
                        }
                        .filterNotNull()
                        .first()
                }
            }

            if (found == null) {
                repository.stopScan()
                reconnectState.value = ReconnectState(
                    error = "${device.deviceName} 장비를 찾지 못했습니다. 장비가 켜져 있는지 확인하세요."
                )
            } else {
                reconnectState.value = ReconnectState()
                repository.connect(found)
            }
        }
    }


    override fun onCleared() {

        reconnectJob?.cancel()
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
