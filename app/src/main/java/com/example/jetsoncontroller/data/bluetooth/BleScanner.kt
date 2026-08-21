package com.example.jetsoncontroller.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.jetsoncontroller.model.JetsonDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface BleScanFailure {

    val userMessage: String

    data object BluetoothUnavailable : BleScanFailure {
        override val userMessage =
            "이 기기에서 Bluetooth를 사용할 수 없습니다."
    }

    data object BluetoothDisabled : BleScanFailure {
        override val userMessage =
            "Bluetooth가 꺼져 있습니다. Bluetooth를 켠 뒤 다시 시도해 주세요."
    }

    data object LocationDisabled : BleScanFailure {
        override val userMessage =
            "위치 서비스가 꺼져 있습니다. 위치를 켠 뒤 다시 시도해 주세요."
    }

    data object ScannerUnavailable : BleScanFailure {
        override val userMessage =
            "BLE 검색을 시작할 수 없습니다. Bluetooth를 껐다 켠 뒤 다시 시도해 주세요."
    }

    data object PermissionDenied : BleScanFailure {
        override val userMessage =
            "주변 기기 검색 권한을 확인해 주세요."
    }

    data class StartRejected(
        val detail: String?
    ) : BleScanFailure {
        override val userMessage =
            "BLE 검색을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요."
    }

    data class PlatformFailure(
        val errorCode: Int
    ) : BleScanFailure {
        override val userMessage: String
            get() = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                    "BLE 검색이 이미 진행 중입니다. 잠시 후 다시 시도해 주세요."

                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                    "이 기기에서는 BLE 검색을 지원하지 않습니다."

                ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                    "BLE 검색 요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."

                else ->
                    "BLE 검색 중 오류가 발생했습니다. Bluetooth를 껐다 켠 뒤 다시 시도해 주세요."
            }
    }
}

sealed interface BleScanState {

    data object Idle : BleScanState

    data object Scanning : BleScanState

    data object Stopped : BleScanState

    data object TimedOut : BleScanState

    data class Failed(
        val failure: BleScanFailure
    ) : BleScanState
}

internal data class BleAdvertisementMetadata(
    val name: String? = null,
    val advertisedServiceUuids: Set<String> = emptySet()
)

internal object BleAdvertisementMerger {

    fun normalizeName(
        value: String?
    ): String? =
        value
            ?.trim()
            ?.takeIf {
                it.isNotEmpty() &&
                    !it.equals("unknown", ignoreCase = true)
            }

    fun merge(
        previous: BleAdvertisementMetadata?,
        observedName: String?,
        observedServiceUuids: Collection<String>
    ): BleAdvertisementMetadata {
        val current =
            previous ?: BleAdvertisementMetadata()

        val mergedUuids =
            LinkedHashSet(current.advertisedServiceUuids)

        observedServiceUuids
            .mapTo(mergedUuids) {
                it.trim().lowercase(Locale.ROOT)
            }

        return current.copy(
            name = normalizeName(observedName) ?: current.name,
            advertisedServiceUuids = mergedUuids
        )
    }

    fun isJetsonDevice(
        metadata: BleAdvertisementMetadata
    ): Boolean =
        metadata.name
            ?.startsWith("MMS-", ignoreCase = true) == true ||
            metadata.advertisedServiceUuids.any {
                it.equals(
                    JetsonGattSpec.SERVICE_UUID.toString(),
                    ignoreCase = true
                )
            }

    fun displayName(
        metadata: BleAdvertisementMetadata,
        address: String
    ): String? {
        metadata.name?.let {
            return it
        }

        if (!isJetsonDevice(metadata)) {
            return null
        }

        val addressSuffix =
            address
                .filter(Char::isLetterOrDigit)
                .takeLast(4)
                .uppercase(Locale.ROOT)

        return if (addressSuffix.isEmpty()) {
            "MMS 장비"
        } else {
            "MMS 장비 ($addressSuffix)"
        }
    }
}

internal data class BleScanDebugSummary(
    val totalResults: Int,
    val uniqueDevices: Int,
    val namedDevices: Int,
    val serviceAdvertisingDevices: Int,
    val jetsonCandidates: Int
)

internal object BleScanDebugInfo {

    fun summarize(
        totalResults: Int,
        observations: Collection<BleAdvertisementMetadata>
    ): BleScanDebugSummary =
        BleScanDebugSummary(
            totalResults = totalResults,
            uniqueDevices = observations.size,
            namedDevices = observations.count {
                it.name != null
            },
            serviceAdvertisingDevices = observations.count {
                it.advertisedServiceUuids.isNotEmpty()
            },
            jetsonCandidates = observations.count(
                BleAdvertisementMerger::isJetsonDevice
            )
        )

    fun safeCandidateName(
        name: String?
    ): String {
        val normalized =
            BleAdvertisementMerger.normalizeName(name)
                ?: return "<none>"

        if (!normalized.startsWith("MMS-", ignoreCase = true)) {
            return "<present>"
        }

        val suffix = normalized.drop(4)
        val isSafeStructuredName =
            suffix.length in 1..MAX_DEVICE_NAME_SUFFIX_LENGTH &&
            suffix.all { character ->
                character in 'A'..'Z' ||
                    character in 'a'..'z' ||
                    character in '0'..'9'
            }

        return if (isSafeStructuredName) {
            normalized
        } else {
            "MMS-<redacted>"
        }
    }

    private const val MAX_DEVICE_NAME_SUFFIX_LENGTH = 12
}

class BleScanner(
    context: Context
) {

    private companion object {
        const val LOG_TAG = "JetsonBLE"
        const val MAX_CANDIDATE_LOGS_PER_SCAN = 5
    }

    private val appContext =
        context.applicationContext

    private val bluetoothManager =
        appContext.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val bluetoothAdapter
        get() = bluetoothManager.adapter

    private val locationManager =
        appContext.getSystemService(
            LocationManager::class.java
        )

    private val scanner
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val handler =
        Handler(Looper.getMainLooper())

    private val deviceMap =
        LinkedHashMap<String, JetsonDevice>()

    private val observationMap =
        LinkedHashMap<String, BleAdvertisementMetadata>()

    private val _devices =
        MutableStateFlow<List<JetsonDevice>>(
            emptyList()
        )

    val devices: StateFlow<List<JetsonDevice>> =
        _devices.asStateFlow()

    private val _isScanning =
        MutableStateFlow(false)

    val isScanning: StateFlow<Boolean> =
        _isScanning.asStateFlow()

    private val _scanState =
        MutableStateFlow<BleScanState>(
            BleScanState.Idle
        )

    val scanState: StateFlow<BleScanState> =
        _scanState.asStateFlow()

    private val _scanError =
        MutableStateFlow<String?>(null)

    val scanError: StateFlow<String?> =
        _scanError.asStateFlow()

    private var stopRunnable: Runnable? = null

    private var jetsonOnlyScan = false

    private var scanResultCount = 0

    private val loggedCandidateAddresses =
        LinkedHashSet<String>()


    @SuppressLint("MissingPermission")
    private val scanCallback =
        object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                if (!_isScanning.value) {
                    return
                }

                val record = result.scanRecord

                val device = result.device

                val address = try {
                    device.address
                } catch (error: SecurityException) {
                    failScan(
                        failure = BleScanFailure.PermissionDenied,
                        error = error
                    )
                    return
                }

                val recordName =
                    BleAdvertisementMerger.normalizeName(
                        record?.deviceName
                    )

                val bondedName =
                    try {
                        BleAdvertisementMerger.normalizeName(
                            device.name
                        )
                    } catch (_: SecurityException) {
                        null
                    }

                val uuids = record?.serviceUuids
                    ?.map { it.uuid.toString() }
                    .orEmpty()

                val previousMetadata =
                    observationMap[address]

                val metadata =
                    BleAdvertisementMerger.merge(
                        previous = previousMetadata,
                        observedName =
                            recordName ?: bondedName.takeIf {
                                previousMetadata?.name == null
                            },
                        observedServiceUuids = uuids
                    )

                val isJetsonDevice =
                    BleAdvertisementMerger.isJetsonDevice(
                        metadata
                    )

                scanResultCount += 1

                if (
                    isJetsonDevice &&
                    loggedCandidateAddresses.size <
                    MAX_CANDIDATE_LOGS_PER_SCAN &&
                    loggedCandidateAddresses.add(address)
                ) {
                    Log.d(
                        LOG_TAG,
                        "BLE candidate observed: " +
                            "name=${BleScanDebugInfo.safeCandidateName(metadata.name)}, " +
                            "serviceUuidPresent=" +
                            metadata.advertisedServiceUuids.isNotEmpty() +
                            ", targetServicePresent=" +
                            metadata.advertisedServiceUuids.any {
                                it.equals(
                                    JetsonGattSpec.SERVICE_UUID.toString(),
                                    ignoreCase = true
                                )
                            } +
                            ", rssi=${result.rssi}"
                    )
                }

                val displayName =
                    BleAdvertisementMerger.displayName(
                        metadata = metadata,
                        address = address
                    )

                observationMap[address] = metadata

                if (
                    displayName == null ||
                    (jetsonOnlyScan && !isJetsonDevice)
                ) {
                    deviceMap.remove(address)
                    publishDevices()
                    return
                }

                deviceMap[address] =
                    JetsonDevice(
                        device = device,
                        name = displayName,
                        address = address,
                        rssi = result.rssi,
                        advertisedServiceUuids =
                            metadata.advertisedServiceUuids.toList()
                    )

                publishDevices()
            }

            override fun onBatchScanResults(
                results: MutableList<ScanResult>
            ) {
                results.forEach {
                    onScanResult(
                        ScanSettings.CALLBACK_TYPE_ALL_MATCHES,
                        it
                    )
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {
                if (!_isScanning.value) {
                    return
                }

                Log.e("JetsonBLE", "Scan failed with error: $errorCode")
                finishScan(
                    finalState =
                        BleScanState.Failed(
                            BleScanFailure.PlatformFailure(errorCode)
                        ),
                    stopPlatformScanner = false
                )
            }
        }


    private fun publishDevices() {

        _devices.value =
            deviceMap.values
                .sortedByDescending {
                    it.rssi
                }
    }


    @SuppressLint("MissingPermission")
    fun startScan(
        durationMillis: Long = 15_000L,
        jetsonOnly: Boolean = false
    ) {

        if (_isScanning.value) {
            return
        }

        cancelStopRunnable()
        deviceMap.clear()
        observationMap.clear()
        scanResultCount = 0
        loggedCandidateAddresses.clear()
        _devices.value = emptyList()
        jetsonOnlyScan = jetsonOnly

        val adapter =
            bluetoothAdapter
                ?: run {
                    failScan(BleScanFailure.BluetoothUnavailable)
                    return
                }

        val adapterEnabled = try {
            adapter.isEnabled
        } catch (error: SecurityException) {
            failScan(
                failure = BleScanFailure.PermissionDenied,
                error = error
            )
            return
        }

        if (!adapterEnabled) {
            failScan(BleScanFailure.BluetoothDisabled)
            return
        }

        val locationEnabled =
            try {
                locationManager?.isLocationEnabled != false
            } catch (error: RuntimeException) {
                Log.w(
                    LOG_TAG,
                    "Unable to verify location service state",
                    error
                )
                true
            }

        if (!locationEnabled) {
            failScan(BleScanFailure.LocationDisabled)
            return
        }

        val bleScanner =
            try {
                adapter.bluetoothLeScanner
            } catch (error: SecurityException) {
                failScan(
                    failure = BleScanFailure.PermissionDenied,
                    error = error
                )
                return
            } ?: run {
                failScan(BleScanFailure.ScannerUnavailable)
                return
            }

        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .setReportDelay(0)
                .build()

        _isScanning.value = true
        updateScanState(BleScanState.Scanning)

        Log.d(
            LOG_TAG,
            "BLE scan started: jetsonOnly=$jetsonOnlyScan, " +
                "durationMs=$durationMillis"
        )

        val timeoutRunnable =
            Runnable {
                finishScan(
                    finalState = BleScanState.TimedOut,
                    stopPlatformScanner = true
                )
            }

        stopRunnable = timeoutRunnable

        try {
            bleScanner.startScan(
                null,
                settings,
                scanCallback
            )
        } catch (error: SecurityException) {
            failScan(
                failure = BleScanFailure.PermissionDenied,
                error = error
            )
            return
        } catch (error: RuntimeException) {
            failScan(
                failure =
                    BleScanFailure.StartRejected(
                        error.message
                    ),
                error = error
            )
            return
        }

        if (
            _isScanning.value &&
            stopRunnable === timeoutRunnable
        ) {
            handler.postDelayed(
                timeoutRunnable,
                durationMillis
            )
        }
    }


    @SuppressLint("MissingPermission")
    fun stopScan() {

        if (!_isScanning.value) {
            return
        }

        finishScan(
            finalState = BleScanState.Stopped,
            stopPlatformScanner = true
        )
    }


    @SuppressLint("MissingPermission")
    private fun finishScan(
        finalState: BleScanState,
        stopPlatformScanner: Boolean
    ) {
        if (!_isScanning.value) {
            return
        }

        var resolvedState = finalState

        if (stopPlatformScanner && _isScanning.value) {
            try {
                scanner?.stopScan(
                    scanCallback
                )
            } catch (error: SecurityException) {
                Log.e(
                    "JetsonBLE",
                    "Missing permission while stopping scan",
                    error
                )
                resolvedState =
                    BleScanState.Failed(
                        BleScanFailure.PermissionDenied
                    )
            } catch (error: RuntimeException) {
                Log.e("JetsonBLE", "Failed to stop BLE scan", error)
            }
        }

        _isScanning.value = false
        jetsonOnlyScan = false

        logScanSummary(resolvedState)

        cancelStopRunnable()
        updateScanState(resolvedState)
    }


    private fun failScan(
        failure: BleScanFailure,
        error: Throwable? = null
    ) {
        if (error == null) {
            Log.e("JetsonBLE", "BLE scan failed: $failure")
        } else {
            Log.e("JetsonBLE", "BLE scan failed: $failure", error)
        }

        val failedState =
            BleScanState.Failed(failure)

        if (_isScanning.value) {
            finishScan(
                finalState = failedState,
                stopPlatformScanner = true
            )
        } else {
            jetsonOnlyScan = false
            cancelStopRunnable()
            updateScanState(failedState)
        }
    }


    private fun updateScanState(
        state: BleScanState
    ) {
        _scanState.value = state
        _scanError.value =
            (state as? BleScanState.Failed)
                ?.failure
                ?.userMessage
    }


    private fun logScanSummary(
        finalState: BleScanState
    ) {
        val summary =
            BleScanDebugInfo.summarize(
                totalResults = scanResultCount,
                observations = observationMap.values
            )

        Log.d(
            LOG_TAG,
            "BLE scan finished: state=${finalState.javaClass.simpleName}, " +
                "results=${summary.totalResults}, " +
                "unique=${summary.uniqueDevices}, " +
                "named=${summary.namedDevices}, " +
                "withServiceUuid=${summary.serviceAdvertisingDevices}, " +
                "jetsonCandidates=${summary.jetsonCandidates}"
        )
    }


    private fun cancelStopRunnable() {

        stopRunnable?.let {
            handler.removeCallbacks(it)
        }

        stopRunnable = null
    }


    fun clear() {

        deviceMap.clear()
        observationMap.clear()
        scanResultCount = 0
        loggedCandidateAddresses.clear()

        _devices.value =
            emptyList()
    }
}
