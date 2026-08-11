package com.example.jetsoncontroller.ui.pairing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.bluetooth.JetsonGattSpec
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.BlePairingState
import com.example.jetsoncontroller.protocol.PairingQrParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PairingViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    private val _qrPhase =
        MutableStateFlow(
            PairingPhase.IDLE
        )

    private val _pairingInfo =
        MutableStateFlow<com.example.jetsoncontroller.model.PairingInfo?>(
            null
        )

    private val _errorMessage =
        MutableStateFlow<String?>(
            null
        )

    val uiState: StateFlow<PairingUiState> =
        combine(
            _qrPhase,
            _pairingInfo,
            _errorMessage,
            repository.pairingState
        ) {
                qrPhase,
                info,
                error,
                repoPairingState ->

            // Logic to advance phase based on repository pairing state
            val finalPhase = when (repoPairingState) {
                is BlePairingState.Connecting -> PairingPhase.CONNECTING
                is BlePairingState.DiscoveringServices -> PairingPhase.VERIFYING_IDENTITY
                is BlePairingState.VerifyingIdentity -> PairingPhase.VERIFYING_IDENTITY
                is BlePairingState.Authenticating -> PairingPhase.AUTHENTICATING
                is BlePairingState.EnablingNotifications -> PairingPhase.ENABLING_STATUS
                is BlePairingState.Ready -> PairingPhase.READY
                is BlePairingState.Error -> PairingPhase.ERROR
                else -> qrPhase
            }

            PairingUiState(
                phase = finalPhase,
                pairingInfo = info,
                displayDeviceName = info?.expectedBleName,
                errorMessage = error ?: if (repoPairingState is BlePairingState.Error) repoPairingState.userMessage else null
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted
                .WhileSubscribed(5_000),
            initialValue =
                PairingUiState()
        )

    init {
        // Separate collector for auto-connect side effect
        viewModelScope.launch {
            repository.devices.collect { devices ->
                val currentQrPhase = _qrPhase.value
                val info = _pairingInfo.value
                
                if (currentQrPhase == PairingPhase.SEARCHING && info != null) {
                    val jetsonUuid = JetsonGattSpec.SERVICE_UUID.toString().lowercase()
                    val candidate = devices.find { device ->
                        device.name.equals(info.expectedBleName, ignoreCase = true) ||
                        device.advertisedServiceUuids.any { it.lowercase() == jetsonUuid }
                    }
                    
                    if (candidate != null) {
                        Log.d("JetsonBLE", "FOUND candidate: ${candidate.name} (${candidate.address}). Connecting...")
                        // Move phase to CONNECTING immediately to prevent re-triggering while gattClient updates
                        _qrPhase.value = PairingPhase.CONNECTING
                        repository.connectForPairing(candidate, info)
                    }
                }
            }
        }
    }

    fun onQrScanned(
        rawValue: String
    ): Boolean {
        if (_qrPhase.value != PairingPhase.IDLE) {
            return false
        }

        return try {
            val info =
                PairingQrParser.parse(
                    rawValue
                )
            Log.d("JetsonBLE", "QR Parsed: deviceId=${info.deviceId} expectedBleName=${info.expectedBleName}")

            _pairingInfo.value = info
            _errorMessage.value = null
            _qrPhase.value =
                PairingPhase.QR_SCANNED

            true

        } catch (e: Exception) {
            Log.e("JetsonBLE", "QR Parse failed", e)
            _pairingInfo.value = null
            _errorMessage.value =
                e.message ?: "QR 코드 형식을 확인할 수 없습니다."
            _qrPhase.value = PairingPhase.IDLE
            false
        }
    }


    fun startPairing() {
        val info =
            _pairingInfo.value
                ?: return

        Log.d("JetsonBLE", "Starting pairing scan for ${info.expectedBleName}")
        _qrPhase.value =
            PairingPhase.SEARCHING

        repository.startPairing(info)
    }


    fun cancelPairing() {
        Log.d("JetsonBLE", "Pairing cancelled by user")
        repository.cancelPairing()
        _qrPhase.value =
            PairingPhase.IDLE
        _pairingInfo.value = null
        _errorMessage.value = null
    }


    fun retry() {
        Log.d("JetsonBLE", "Retrying pairing")
        _errorMessage.value = null
        startPairing()
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

            return PairingViewModel(
                repository
            ) as T
        }
    }
}
