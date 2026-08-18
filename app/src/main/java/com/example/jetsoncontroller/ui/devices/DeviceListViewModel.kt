package com.example.jetsoncontroller.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.bluetooth.BleScanState
import com.example.jetsoncontroller.data.bluetooth.JetsonGattSpec
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.model.canonicalBleNameForDeviceId
import com.example.jetsoncontroller.model.legacyBleNameForDeviceId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface ReconnectCandidateDecision<out T> {

    data class Connect<T>(
        val candidate: T
    ) : ReconnectCandidateDecision<T>

    data object ContinueScanning :
        ReconnectCandidateDecision<Nothing>

    data object NotFound :
        ReconnectCandidateDecision<Nothing>

    data object Ambiguous :
        ReconnectCandidateDecision<Nothing>

    data object ScanStopped :
        ReconnectCandidateDecision<Nothing>

    data class ScanFailed(
        val userMessage: String
    ) : ReconnectCandidateDecision<Nothing>
}

internal fun <T> chooseReconnectCandidate(
    candidates: List<T>,
    expectedBleName: String,
    legacyExpectedBleName: String,
    isScanning: Boolean,
    nameOf: (T) -> String?,
    advertisesJetsonService: (T) -> Boolean
): ReconnectCandidateDecision<T> {
    val exactMatches =
        candidates.filter { candidate ->
            nameOf(candidate).equals(
                expectedBleName,
                ignoreCase = true
            )
        }

    if (exactMatches.size == 1) {
        return ReconnectCandidateDecision.Connect(
            exactMatches.single()
        )
    }
    if (exactMatches.size > 1) {
        return if (isScanning) {
            ReconnectCandidateDecision.ContinueScanning
        } else {
            ReconnectCandidateDecision.Ambiguous
        }
    }

    val legacyMatches =
        candidates.filter { candidate ->
            nameOf(candidate).equals(
                legacyExpectedBleName,
                ignoreCase = true
            )
        }

    if (legacyMatches.size == 1) {
        return ReconnectCandidateDecision.Connect(
            legacyMatches.single()
        )
    }
    if (legacyMatches.size > 1) {
        return if (isScanning) {
            ReconnectCandidateDecision.ContinueScanning
        } else {
            ReconnectCandidateDecision.Ambiguous
        }
    }

    if (isScanning) {
        return ReconnectCandidateDecision.ContinueScanning
    }

    val serviceMatches =
        candidates.filter(advertisesJetsonService)

    return when (serviceMatches.size) {
        1 -> ReconnectCandidateDecision.Connect(
            serviceMatches.single()
        )

        0 -> ReconnectCandidateDecision.NotFound
        else -> ReconnectCandidateDecision.Ambiguous
    }
}

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
            repository.scanState,
            repository.connectionState,
            permissionGranted,
            savedDevicesState
        ) {
                devices,
                scanState,
                connection,
                permission,
                savedDevices ->

            DeviceListUiState(
                devices = devices,
                registeredDevices = savedDevices.first,
                isScanning = scanState is BleScanState.Scanning,
                permissionGranted = permission,
                connectionState = connection,
                reconnectingDeviceId = savedDevices.second.deviceId,
                reconnectError = savedDevices.second.error,
                scanError =
                    (scanState as? BleScanState.Failed)
                        ?.failure
                        ?.userMessage
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
            val jetsonUuid =
                JetsonGattSpec.SERVICE_UUID.toString()
            val canonicalBleName =
                canonicalBleNameForDeviceId(device.deviceId)
            val legacyBleName =
                legacyBleNameForDeviceId(device.deviceId)

            fun decide(
                candidates: List<JetsonDevice>,
                isScanning: Boolean
            ): ReconnectCandidateDecision<JetsonDevice> =
                chooseReconnectCandidate(
                    candidates = candidates,
                    expectedBleName = canonicalBleName,
                    legacyExpectedBleName = legacyBleName,
                    isScanning = isScanning,
                    nameOf = { it.name },
                    advertisesJetsonService = { candidate ->
                        candidate.advertisedServiceUuids.any {
                            it.equals(
                                jetsonUuid,
                                ignoreCase = true
                            )
                        }
                    }
                )

            val visibleDecision =
                decide(
                    candidates = repository.devices.value,
                    isScanning = true
                )

            val decision =
                if (
                    visibleDecision is
                        ReconnectCandidateDecision.Connect
                ) {
                    visibleDecision
                } else {
                    repository.startScan(jetsonOnly = true)

                    withTimeoutOrNull(20_000L) {
                        combine(
                            repository.devices,
                            repository.scanState
                        ) { candidates, scanState ->
                            when (scanState) {
                                BleScanState.Scanning ->
                                    decide(
                                        candidates = candidates,
                                        isScanning = true
                                    )

                                BleScanState.TimedOut ->
                                    decide(
                                        candidates = candidates,
                                        isScanning = false
                                    )

                                is BleScanState.Failed ->
                                    ReconnectCandidateDecision.ScanFailed(
                                        scanState.failure.userMessage
                                    )

                                BleScanState.Idle,
                                BleScanState.Stopped ->
                                    ReconnectCandidateDecision.ScanStopped
                            }
                        }
                            .filter {
                                it !is
                                    ReconnectCandidateDecision.ContinueScanning
                            }
                            .first()
                    } ?: ReconnectCandidateDecision.NotFound
                }

            when (decision) {
                is ReconnectCandidateDecision.Connect -> {
                    reconnectState.value = ReconnectState()
                    repository.reconnectRegistered(
                        decision.candidate,
                        device.deviceId
                    )
                }

                ReconnectCandidateDecision.Ambiguous -> {
                    repository.stopScan()
                    reconnectState.value = ReconnectState(
                        error =
                            "Jetson 장비가 여러 대 검색되었습니다. " +
                                "${device.deviceName} 장비만 켠 뒤 다시 시도해 주세요."
                    )
                }

                is ReconnectCandidateDecision.ScanFailed -> {
                    repository.stopScan()
                    reconnectState.value = ReconnectState(
                        error = decision.userMessage
                    )
                }

                ReconnectCandidateDecision.ContinueScanning,
                ReconnectCandidateDecision.NotFound,
                ReconnectCandidateDecision.ScanStopped -> {
                    repository.stopScan()
                    reconnectState.value = ReconnectState(
                        error =
                            "${device.deviceName} 장비를 찾지 못했습니다. " +
                                "장비가 켜져 있는지 확인하세요."
                    )
                }
            }
        }
    }

    fun forget(device: RegisteredDevice) {
        viewModelScope.launch {
            repository.forgetRegisteredDevice(device.deviceId)
            reconnectState.value = ReconnectState()
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
