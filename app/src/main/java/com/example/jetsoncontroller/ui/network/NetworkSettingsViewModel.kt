package com.example.jetsoncontroller.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.WifiProvisionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NetworkSettingsViewModel(
    private val repository: JetsonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkSettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun onSsidChange(value: String) {
        _uiState.update {
            it.copy(ssid = value, message = null, isError = false)
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

    fun submit() {
        val current = _uiState.value
        _uiState.update { it.copy(sending = true, message = null) }

        val result = repository.provisionWifi(
            WifiProvisionRequest(
                ssid = current.ssid,
                password = current.password,
                hidden = current.hidden
            )
        )

        _uiState.update {
            if (result.isSuccess) {
                it.copy(
                    sending = false,
                    password = "",
                    message = "공유기 설정을 Jetson에 전송했습니다.",
                    isError = false
                )
            } else {
                it.copy(
                    sending = false,
                    message = result.exceptionOrNull()?.message
                        ?: "공유기 설정 전송에 실패했습니다.",
                    isError = true
                )
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
