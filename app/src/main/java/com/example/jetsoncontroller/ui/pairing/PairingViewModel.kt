package com.example.jetsoncontroller.ui.pairing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.bluetooth.BleScanState
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

internal sealed interface PairingCandidateDecision<out T> {
    data class Connect<T>(val candidate: T) : PairingCandidateDecision<T>
    data object ContinueScanning : PairingCandidateDecision<Nothing>
    data class Fail(val userMessage: String) : PairingCandidateDecision<Nothing>
}

internal fun <T> choosePairingCandidate(
    candidates: List<T>,
    expectedBleName: String,
    legacyExpectedBleName: String,
    isScanning: Boolean,
    nameOf: (T) -> String?,
    advertisesJetsonService: (T) -> Boolean
): PairingCandidateDecision<T> {
    val exactMatches = candidates.filter { candidate ->
        nameOf(candidate).equals(expectedBleName, ignoreCase = true)
    }

    if (exactMatches.size == 1) {
        return PairingCandidateDecision.Connect(exactMatches.single())
    }
    if (exactMatches.size > 1) {
        return if (isScanning) {
            PairingCandidateDecision.ContinueScanning
        } else {
            ambiguousPairingName(expectedBleName)
        }
    }

    val legacyMatches = candidates.filter { candidate ->
        nameOf(candidate).equals(legacyExpectedBleName, ignoreCase = true)
    }
    if (legacyMatches.size == 1) {
        return PairingCandidateDecision.Connect(legacyMatches.single())
    }
    if (legacyMatches.size > 1) {
        return if (isScanning) {
            PairingCandidateDecision.ContinueScanning
        } else {
            ambiguousPairingName(legacyExpectedBleName)
        }
    }
    if (isScanning) {
        return PairingCandidateDecision.ContinueScanning
    }

    val serviceMatches = candidates.filter(advertisesJetsonService)
    return when (serviceMatches.size) {
        1 -> PairingCandidateDecision.Connect(serviceMatches.single())
        0 -> PairingCandidateDecision.Fail(
            "$expectedBleName 장비를 찾지 못했습니다. Jetson의 Bluetooth 상태를 확인해 주세요."
        )
        else -> PairingCandidateDecision.Fail(
            "Jetson 장비가 여러 대 검색되었지만 $expectedBleName 이름을 확인할 수 없습니다. " +
                "대상 장비만 켠 뒤 다시 시도해 주세요."
        )
    }
}

private fun ambiguousPairingName(
    bleName: String
): PairingCandidateDecision.Fail =
    PairingCandidateDecision.Fail(
        "$bleName 이름을 사용하는 장비가 여러 대 검색되었습니다. " +
            "대상 장비만 켠 뒤 다시 시도해 주세요."
    )

internal fun resolvePairingPhase(
    requestedPhase: PairingPhase,
    observeRepositoryState: Boolean,
    repositoryState: BlePairingState
): PairingPhase {
    if (!observeRepositoryState) {
        return requestedPhase
    }

    return when (repositoryState) {
        is BlePairingState.Connecting -> PairingPhase.CONNECTING
        is BlePairingState.DiscoveringServices -> PairingPhase.VERIFYING_IDENTITY
        is BlePairingState.VerifyingIdentity -> PairingPhase.VERIFYING_IDENTITY
        is BlePairingState.Authenticating -> PairingPhase.AUTHENTICATING
        is BlePairingState.EnablingNotifications -> PairingPhase.ENABLING_STATUS
        is BlePairingState.Ready -> PairingPhase.READY
        is BlePairingState.Error -> PairingPhase.ERROR
        else -> requestedPhase
    }
}

internal fun resolvePairingError(
    localError: String?,
    observeRepositoryState: Boolean,
    repositoryState: BlePairingState
): String? =
    localError ?: if (
        observeRepositoryState && repositoryState is BlePairingState.Error
    ) {
        repositoryState.userMessage
    } else {
        null
    }

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

    private val _observeRepositoryState =
        MutableStateFlow(false)

    val uiState: StateFlow<PairingUiState> =
        combine(
            _qrPhase,
            _pairingInfo,
            _errorMessage,
            _observeRepositoryState,
            repository.pairingState
        ) {
                qrPhase,
                info,
                error,
                observeRepositoryState,
                repoPairingState ->

            val finalPhase = resolvePairingPhase(
                requestedPhase = qrPhase,
                observeRepositoryState = observeRepositoryState,
                repositoryState = repoPairingState
            )

            PairingUiState(
                phase = finalPhase,
                pairingInfo = info,
                displayDeviceName = info?.expectedBleName,
                errorMessage = resolvePairingError(
                    localError = error,
                    observeRepositoryState = observeRepositoryState,
                    repositoryState = repoPairingState
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted
                .WhileSubscribed(5_000),
            initialValue =
                PairingUiState()
        )

    init {
        viewModelScope.launch {
            combine(
                _qrPhase,
                _pairingInfo,
                repository.devices,
                repository.scanState
            ) { phase, info, devices, scanState ->
                PairingSearchSnapshot(phase, info, devices, scanState)
            }.collect { snapshot ->
                val info = snapshot.info
                if (
                    snapshot.phase != PairingPhase.SEARCHING ||
                    info == null ||
                    _qrPhase.value != PairingPhase.SEARCHING ||
                    _pairingInfo.value != info
                ) {
                    return@collect
                }

                when (val scanState = snapshot.scanState) {
                    is BleScanState.Failed -> {
                        failPairing(scanState.failure.userMessage)
                        return@collect
                    }

                    BleScanState.Idle,
                    BleScanState.Stopped -> return@collect

                    BleScanState.Scanning,
                    BleScanState.TimedOut -> Unit
                }

                val jetsonUuid = JetsonGattSpec.SERVICE_UUID.toString()
                when (
                    val decision = choosePairingCandidate(
                        candidates = snapshot.devices,
                        expectedBleName = info.expectedBleName,
                        legacyExpectedBleName = info.legacyExpectedBleName,
                        isScanning = snapshot.scanState is BleScanState.Scanning,
                        nameOf = { it.name },
                        advertisesJetsonService = { device ->
                            device.advertisedServiceUuids.any {
                                it.equals(jetsonUuid, ignoreCase = true)
                            }
                        }
                    )
                ) {
                    is PairingCandidateDecision.Connect -> {
                        Log.d(
                            "JetsonBLE",
                            "Pairing candidate found: ${decision.candidate.name}"
                        )
                        _qrPhase.value = PairingPhase.CONNECTING
                        repository.connectForPairing(decision.candidate, info)
                    }

                    PairingCandidateDecision.ContinueScanning -> Unit

                    is PairingCandidateDecision.Fail -> {
                        failPairing(decision.userMessage)
                    }
                }
            }
        }
    }

    fun beginQrPairing() {
        Log.d("JetsonBLE", "Starting a new QR pairing session")
        _observeRepositoryState.value = false
        _qrPhase.value = PairingPhase.IDLE
        _pairingInfo.value = null
        _errorMessage.value = null
        repository.prepareForQrPairing()
    }

    fun onQrScanned(
        rawValue: String
    ): Boolean {
        val pairingInProgress = when (repository.pairingState.value) {
            is BlePairingState.Connecting,
            is BlePairingState.DiscoveringServices,
            is BlePairingState.VerifyingIdentity,
            is BlePairingState.Authenticating,
            is BlePairingState.EnablingNotifications -> true
            else -> false
        }

        if (pairingInProgress) {
            return false
        }

        return try {
            val info =
                PairingQrParser.parse(
                    rawValue
                )
            Log.d("JetsonBLE", "QR Parsed: deviceId=${info.deviceId} expectedBleName=${info.expectedBleName}")

            // A valid QR always starts a fresh attempt. RegistrationRequired GATT
            // connections are intentionally retained by the repository.
            _observeRepositoryState.value = false
            repository.prepareForQrPairing()
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

        _observeRepositoryState.value = true
        _errorMessage.value = null
        val connectedDevice = repository.startPairing(info)
        _qrPhase.value = if (connectedDevice) {
            PairingPhase.AUTHENTICATING
        } else {
            PairingPhase.SEARCHING
        }
    }


    fun cancelPairing() {
        Log.d("JetsonBLE", "Pairing cancelled by user")
        _observeRepositoryState.value = false
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

    private fun failPairing(userMessage: String) {
        _observeRepositoryState.value = false
        _errorMessage.value = userMessage
        _qrPhase.value = PairingPhase.ERROR
    }

    private data class PairingSearchSnapshot(
        val phase: PairingPhase,
        val info: com.example.jetsoncontroller.model.PairingInfo?,
        val devices: List<com.example.jetsoncontroller.model.JetsonDevice>,
        val scanState: BleScanState
    )


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
