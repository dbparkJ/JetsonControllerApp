package com.example.jetsoncontroller.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.WifiProvisionRequest
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.ui.connection.DeviceWorkspace
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NetworkSettingsViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkSettingsUiState())
    val uiState = _uiState.asStateFlow()
    private val workspace = DeviceWorkspace { NetworkSettingsUiState() }
    private var connectionGeneration = 0L
    private var submitJob: Job? = null

    init {
        viewModelScope.launch {
            repository.wifiAccessPointState.collect { scanState ->
                _uiState.update {
                    it.copy(
                        accessPoints = scanState.accessPoints,
                        mobileWifiSsid = scanState.currentSsid,
                        scanningAccessPoints = scanState.scanning,
                        accessPointError = scanState.error
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(repository.selectedDeviceId, repository.transportState) { deviceId, transport ->
                deviceId to transport
            }.collect { (deviceId, transport) ->
                connectionGeneration += 1
                submitJob?.cancel()
                val connected = (transport as? TransportState.Connected)?.takeIf {
                    it.deviceId.equals(deviceId, ignoreCase = true)
                }
                _uiState.update { previous ->
                    workspace.select(deviceId, previous).let { current ->
                        current.copy(
                            transportType = connected?.type,
                            sending = false,
                            message = if (current.sending) "전송 결과를 확인하지 못했습니다. 재연결 후 Wi-Fi 연결 상태를 확인해 주세요." else current.message
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.status.collect { status ->
                _uiState.update {
                    it.copy(
                        wifiConnected = status.wifiConnected,
                        currentWifiSsid = status.wifiSsid
                    )
                }
            }
        }
    }

    fun onSsidChange(value: String) {
        _uiState.update {
            it.copy(
                ssid = value,
                selectedAccessPointSsid = null,
                message = null,
                isError = false
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(password = value, message = null, isError = false)
        }
    }

    fun onHiddenChange(value: Boolean) {
        _uiState.update { it.copy(hidden = value) }
    }

    fun selectAccessPoint(accessPoint: WifiAccessPoint) {
        if (_uiState.value.isCurrentJetsonWifi(accessPoint.ssid)) {
            _uiState.update {
                it.copy(
                    selectedAccessPointSsid = null,
                    password = "",
                    message = "Jetson이 이미 이 네트워크에 연결되어 있습니다.",
                    isError = false
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                ssid = accessPoint.ssid,
                selectedAccessPointSsid = accessPoint.ssid,
                password = "",
                hidden = false,
                message = null,
                isError = false
            )
        }
    }

    fun scanAccessPoints() {
        viewModelScope.launch {
            repository.refreshStatus()
        }
        repository.startWifiAccessPointScan()
    }

    fun stopAccessPointScan() {
        repository.stopWifiAccessPointScan()
    }

    fun submit() {
        if (_uiState.value.transportType == null || _uiState.value.sending) return
        val generation = connectionGeneration
        val current = _uiState.value
        if (current.isCurrentJetsonWifi(current.ssid)) {
            _uiState.update {
                it.copy(
                    sending = false,
                    password = "",
                    selectedAccessPointSsid = null,
                    message = "Jetson이 이미 이 네트워크에 연결되어 있습니다.",
                    isError = false
                )
            }
            return
        }
        submitJob = viewModelScope.launch {
            _uiState.update { it.copy(sending = true, message = null) }

            val result = repository.provisionWifi(
                WifiProvisionRequest(
                    ssid = current.ssid,
                    password = current.password,
                    hidden = current.hidden
                )
            )

            if (generation != connectionGeneration) return@launch
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        sending = false,
                        password = "",
                        message = "Wi-Fi 연결 요청을 Jetson에 전송했습니다.",
                        isError = false
                    )
                } else {
                    it.copy(
                        sending = false,
                        message = result.exceptionOrNull()?.message
                            ?: "Wi-Fi 설정 전송에 실패했습니다.",
                        isError = true
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: JetsonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NetworkSettingsViewModel(repository) as T
        }
    }
}
