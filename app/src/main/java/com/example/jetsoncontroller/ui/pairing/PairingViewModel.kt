package com.example.jetsoncontroller.ui.pairing

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
    val serviceMatches = candidates.filter(advertisesJetsonService)
    // The advertised service is a candidate, not proof of identity. GATT still
    // verifies DEVICE_ID against the QR before sending any authentication data.
    if (serviceMatches.size == 1) {
        return PairingCandidateDecision.Connect(serviceMatches.single())
    }
    if (isScanning) {
        return PairingCandidateDecision.ContinueScanning
    }
    return when (serviceMatches.size) {
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
        is BlePairingState.Retrying -> PairingPhase.CONNECTING
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
        pairingFailureText(repositoryState.userMessage, deviceFound = true)
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

internal fun BlePairingState.isActivePairing(): Boolean = when (this) {
    BlePairingState.Connecting, BlePairingState.DiscoveringServices,
    BlePairingState.VerifyingIdentity, BlePairingState.Authenticating,
    BlePairingState.EnablingNotifications, is BlePairingState.Retrying -> true
    else -> false
}

internal fun reconcileRestoredPairingState(
    state: PairingRecreationState,
    repositoryState: BlePairingState
): PairingRecreationState = if (
    state.phase.isPairingInProgress() && repositoryState == BlePairingState.Idle
) {
    state.copy(
        phase = PairingPhase.ERROR,
        errorMessage = "장치 등록이 중단되었습니다. 다시 시도하세요.",
        observeRepositoryState = false
    )
} else {
    state
}

class PairingViewModel(
    private val repository:
        JetsonRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val restoredState = reconcileRestoredPairingState(
        restorePairingRecreationState(savedStateHandle), repository.pairingState.value
    )

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
                connectionAttempt = (repoPairingState as? BlePairingState.Retrying)?.attempt ?: 1,
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
            initialValue = PairingUiState(
                phase = resolvePairingPhase(restoredState.phase, restoredState.observeRepositoryState, repository.pairingState.value),
                pairingInfo = restoredState.pairingInfo,
                displayDeviceName = restoredState.pairingInfo?.expectedBleName,
                connectionAttempt = (repository.pairingState.value as? BlePairingState.Retrying)?.attempt ?: 1,
                errorMessage = resolvePairingError(restoredState.errorMessage, restoredState.observeRepositoryState, repository.pairingState.value)
            )
        )

    init {
        saveRecreationState()
        viewModelScope.launch {
            repository.pairingState.collect { repositoryState ->
                if (_observeRepositoryState.value) {
                    _qrPhase.value = resolvePairingPhase(_qrPhase.value, true, repositoryState)
                    if (repositoryState is BlePairingState.Error) {
                        _errorMessage.value = pairingFailureText(repositoryState.userMessage, deviceFound = true)
                    }
                    if (repositoryState is BlePairingState.Ready) PairingScannerFrame.image = null
                    saveRecreationState()
                }
            }
        }
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
        PairingScannerFrame.image = null
        userStartedAttempt = false
        _observeRepositoryState.value = false
        _qrPhase.value = PairingPhase.IDLE
        _pairingInfo.value = null
        _errorMessage.value = null
        saveRecreationState()
        repository.prepareForQrPairing()
    }

    fun onQrScanned(rawValue: String): Boolean {
        if (_qrPhase.value != PairingPhase.IDLE || repository.pairingState.value.isActivePairing()) return false
        val info = parsePairingCode(rawValue)
        if (info == null) {
            _pairingInfo.value = null
            _errorMessage.value = INVALID_PAIRING_CODE
            saveRecreationState()
            return false
        }
        userStartedAttempt = false
        _observeRepositoryState.value = false
        repository.prepareForQrPairing()
        _pairingInfo.value = info
        _errorMessage.value = null
        _qrPhase.value = PairingPhase.QR_SCANNED
        saveRecreationState()
        return true
    }

    fun startPairing() {
        if (_qrPhase.value !in setOf(PairingPhase.QR_SCANNED, PairingPhase.ERROR)) return
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
        PairingScannerFrame.image = null
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
        _errorMessage.value = null
        startPairing()
    }

    private fun failPairing(userMessage: String) {
        userStartedAttempt = false
        _observeRepositoryState.value = false
        _errorMessage.value = pairingFailureText(userMessage)
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
