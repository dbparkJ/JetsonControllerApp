package com.example.jetsoncontroller.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.WifiProvisionRequest
import com.example.jetsoncontroller.model.WifiProvisionPhase
import com.example.jetsoncontroller.model.phase
import com.example.jetsoncontroller.data.transport.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NetworkSettingsViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkSettingsUiState())
    val uiState = _uiState.asStateFlow()

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
            repository.transportState.collect { transport ->
                _uiState.update {
                    it.copy(
                        transportType = (transport as? TransportState.Connected)?.type
                    )
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
        val current = _uiState.value
        if (current.sending) {
            return
        }
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
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sending = true,
                    message = "Wi-Fi 연결 요청을 Jetson에 전송하고 있습니다.",
                    isError = false
                )
            }

            val result = repository.provisionWifi(
                WifiProvisionRequest(
                    ssid = current.ssid,
                    password = current.password,
                    hidden = current.hidden
                )
            )

            val receipt = result.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        sending = false,
                        message = error.message ?: "Wi-Fi 설정 전송에 실패했습니다.",
                        isError = true
                    )
                }
                return@launch
            }

            if (receipt.lanHandoffRequired) {
                _uiState.update {
                    it.copy(
                        message =
                            "Jetson이 ${receipt.ssid} Wi-Fi로 전환 중입니다. " +
                                "새 LAN에서 장비를 다시 찾습니다.",
                        isError = false
                    )
                }
                val handoff = repository.awaitWifiProvisionLanHandoff(receipt.deviceId)
                _uiState.update {
                    if (handoff.isSuccess) {
                        it.copy(
                            sending = false,
                            password = "",
                            message =
                                "${receipt.ssid} Wi-Fi 연결과 LAN 재연결에 성공했습니다.",
                            isError = false
                        )
                    } else {
                        it.copy(
                            sending = false,
                            message = handoff.exceptionOrNull()?.message
                                ?: "새 LAN에서 Jetson을 다시 찾지 못했습니다.",
                            isError = true
                        )
                    }
                }
                return@launch
            }

            if (!receipt.statusPollingAvailable) {
                _uiState.update {
                    it.copy(
                        sending = false,
                        password = "",
                        message = "Wi-Fi 연결 요청을 Jetson에 전송했습니다.",
                        isError = false
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    message = "Jetson이 ${receipt.ssid} Wi-Fi에 연결 중입니다. 결과를 확인하고 있습니다.",
                    isError = false
                )
            }
            val completion = awaitWifiProvisionCompletion(
                expectedSsid = receipt.ssid,
                fetchStatus = repository::getWifiProvisionStatus
            )
            val status = completion.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        sending = false,
                        message = error.message
                            ?: "Wi-Fi 요청은 접수됐지만 최종 결과를 확인하지 못했습니다.",
                        isError = true
                    )
                }
                return@launch
            }

            _uiState.update {
                when (status.phase()) {
                    WifiProvisionPhase.CONNECTED -> it.copy(
                        sending = false,
                        password = "",
                        message = wifiProvisionConnectedMessage(status, receipt.ssid),
                        isError = false
                    )
                    WifiProvisionPhase.FAILED -> it.copy(
                        sending = false,
                        message = wifiProvisionFailedMessage(status, receipt.ssid),
                        isError = true
                    )
                    else -> it.copy(
                        sending = false,
                        message = "Jetson의 Wi-Fi 최종 상태를 확인하지 못했습니다.",
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
