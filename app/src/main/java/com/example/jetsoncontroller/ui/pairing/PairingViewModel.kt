package com.example.jetsoncontroller.ui.pairing

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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
            "$expectedBleName 장치를 찾지 못했습니다. 장치의 Bluetooth 상태를 확인해 주세요."
        )
        else -> PairingCandidateDecision.Fail(
            "장치가 여러 대 검색되었지만 $expectedBleName 이름을 확인할 수 없습니다. " +
                "대상 장치만 켠 뒤 다시 시도해 주세요."
        )
    }
}

private fun ambiguousPairingName(
    bleName: String
): PairingCandidateDecision.Fail =
    PairingCandidateDecision.Fail(
        "$bleName 이름을 사용하는 장치가 여러 대 검색되었습니다. " +
            "대상 장치만 켠 뒤 다시 시도해 주세요."
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

internal data class PairingRecreationState(
    val phase: PairingPhase = PairingPhase.IDLE,
    val pairingInfo: com.example.jetsoncontroller.model.PairingInfo? = null,
    val errorMessage: String? = null,
    val observeRepositoryState: Boolean = false
)

private const val PAIRING_PHASE_KEY = "pairing.phase"
private const val PAIRING_INFO_VERSION_KEY = "pairing.info.version"
private const val PAIRING_INFO_DEVICE_ID_KEY = "pairing.info.deviceId"
private const val PAIRING_INFO_SECRET_KEY = "pairing.info.bootstrapSecretHex"
private const val PAIRING_ERROR_KEY = "pairing.error"
private const val PAIRING_OBSERVE_REPOSITORY_KEY = "pairing.observeRepository"

internal fun restorePairingRecreationState(handle: SavedStateHandle): PairingRecreationState {
    val phase = handle.get<String>(PAIRING_PHASE_KEY)?.let {
        runCatching { PairingPhase.valueOf(it) }.getOrNull()
    } ?: PairingPhase.IDLE
    val pairingInfo = runCatching {
        val version = handle.get<Int>(PAIRING_INFO_VERSION_KEY) ?: return@runCatching null
        val deviceId = handle.get<String>(PAIRING_INFO_DEVICE_ID_KEY) ?: return@runCatching null
        val secret = handle.get<String>(PAIRING_INFO_SECRET_KEY) ?: return@runCatching null
        com.example.jetsoncontroller.model.PairingInfo(version, deviceId, secret)
    }.getOrNull()
    return PairingRecreationState(
        phase = phase,
        pairingInfo = pairingInfo,
        errorMessage = handle.get(PAIRING_ERROR_KEY),
        observeRepositoryState = handle[PAIRING_OBSERVE_REPOSITORY_KEY] ?: false
    )
}

internal fun savePairingRecreationState(handle: SavedStateHandle, state: PairingRecreationState) {
    handle[PAIRING_PHASE_KEY] = state.phase.name
    handle[PAIRING_INFO_VERSION_KEY] = state.pairingInfo?.version
    handle[PAIRING_INFO_DEVICE_ID_KEY] = state.pairingInfo?.deviceId
    handle[PAIRING_INFO_SECRET_KEY] = state.pairingInfo?.bootstrapSecretHex
    handle[PAIRING_ERROR_KEY] = state.errorMessage
    handle[PAIRING_OBSERVE_REPOSITORY_KEY] = state.observeRepositoryState
}

internal fun shouldConnectPairingCandidate(phase: PairingPhase, userStartedAttempt: Boolean): Boolean =
    phase == PairingPhase.SEARCHING && userStartedAttempt

class PairingViewModel(
    private val repository:
        JetsonRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val restoredState = restorePairingRecreationState(savedStateHandle)

    private val _qrPhase =
        MutableStateFlow(
            restoredState.phase
        )

    private val _pairingInfo =
        MutableStateFlow<com.example.jetsoncontroller.model.PairingInfo?>(
            restoredState.pairingInfo
        )

    private val _errorMessage =
        MutableStateFlow<String?>(
            restoredState.errorMessage
        )

    private val _observeRepositoryState =
        MutableStateFlow(restoredState.observeRepositoryState)

    // Restoring a SEARCHING presentation must not initiate another BLE connection.
    // A fresh user tap on Start or Retry opens this gate for the current ViewModel only.
    private var userStartedAttempt = false

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

                if (!shouldConnectPairingCandidate(snapshot.phase, userStartedAttempt)) {
                    return@collect
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
                        saveRecreationState()
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
        userStartedAttempt = false
        _observeRepositoryState.value = false
        _qrPhase.value = PairingPhase.IDLE
        _pairingInfo.value = null
        _errorMessage.value = null
        saveRecreationState()
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
            userStartedAttempt = false
            _observeRepositoryState.value = false
            repository.prepareForQrPairing()
            _pairingInfo.value = info
            _errorMessage.value = null
            _qrPhase.value =
                PairingPhase.QR_SCANNED
            saveRecreationState()

            true

        } catch (e: Exception) {
            Log.e("JetsonBLE", "QR Parse failed", e)
            _pairingInfo.value = null
            _errorMessage.value =
                e.message ?: "QR 코드 형식을 확인할 수 없습니다."
            _qrPhase.value = PairingPhase.IDLE
            saveRecreationState()
            false
        }
    }


    fun startPairing() {
        val info =
            _pairingInfo.value
                ?: return

        userStartedAttempt = true
        _observeRepositoryState.value = true
        _errorMessage.value = null
        val connectedDevice = repository.startPairing(info)
        _qrPhase.value = if (connectedDevice) {
            PairingPhase.AUTHENTICATING
        } else {
            PairingPhase.SEARCHING
        }
        saveRecreationState()
    }


    fun cancelPairing() {
        Log.d("JetsonBLE", "Pairing cancelled by user")
        userStartedAttempt = false
        _observeRepositoryState.value = false
        repository.cancelPairing()
        _qrPhase.value =
            PairingPhase.IDLE
        _pairingInfo.value = null
        _errorMessage.value = null
        saveRecreationState()
    }


    fun retry() {
        Log.d("JetsonBLE", "Retrying pairing")
        _errorMessage.value = null
        startPairing()
    }

    private fun failPairing(userMessage: String) {
        userStartedAttempt = false
        _observeRepositoryState.value = false
        _errorMessage.value = userMessage
        _qrPhase.value = PairingPhase.ERROR
        saveRecreationState()
    }

    private fun saveRecreationState() {
        savePairingRecreationState(
            savedStateHandle,
            PairingRecreationState(
                phase = _qrPhase.value,
                pairingInfo = _pairingInfo.value,
                errorMessage = _errorMessage.value,
                observeRepositoryState = _observeRepositoryState.value
            )
        )
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

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            PairingViewModel(repository, extras.createSavedStateHandle()) as T
    }
}
