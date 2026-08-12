package com.example.jetsoncontroller.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.jetsoncontroller.model.JetsonDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleScanner(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val bluetoothManager =
        appContext.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val bluetoothAdapter
        get() = bluetoothManager.adapter

    private val scanner
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val handler =
        Handler(Looper.getMainLooper())

    private val deviceMap =
        LinkedHashMap<String, JetsonDevice>()

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

    private var stopRunnable: Runnable? = null

    private var jetsonOnlyScan = false


    @SuppressLint("MissingPermission")
    private val scanCallback =
        object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val record = result.scanRecord

                val name = record?.deviceName
                    ?: try {
                        result.device.name
                    } catch (_: SecurityException) {
                        null
                    }

                val displayName = name
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty() &&
                            !it.equals("unknown", ignoreCase = true)
                    }

                if (displayName == null) {
                    return
                }

                val uuids = record?.serviceUuids
                    ?.map { it.uuid.toString() }
                    .orEmpty()

                val isJetsonDevice =
                    displayName.startsWith("MMS-", ignoreCase = true) ||
                        uuids.any {
                            it.equals(
                                JetsonGattSpec.SERVICE_UUID.toString(),
                                ignoreCase = true
                            )
                        }

                if (jetsonOnlyScan && !isJetsonDevice) {
                    return
                }

                val device = result.device
                val address = device.address

                deviceMap[address] =
                    JetsonDevice(
                        device = device,
                        name = displayName,
                        address = address,
                        rssi = result.rssi,
                        advertisedServiceUuids = uuids
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
                _isScanning.value = false
                Log.e("JetsonBLE", "Scan failed with error: $errorCode")
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

        val adapter =
            bluetoothAdapter
                ?: return

        if (!adapter.isEnabled) {
            return
        }

        val bleScanner =
            scanner
                ?: return

        deviceMap.clear()
        _devices.value = emptyList()
        jetsonOnlyScan = jetsonOnly

        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .setReportDelay(0)
                .build()

        _isScanning.value = true

        bleScanner.startScan(
            null,
            settings,
            scanCallback
        )

        stopRunnable?.let {
            handler.removeCallbacks(it)
        }

        stopRunnable =
            Runnable {
                stopScan()
            }

        handler.postDelayed(
            stopRunnable!!,
            durationMillis
        )
    }


    @SuppressLint("MissingPermission")
    fun stopScan() {

        if (!_isScanning.value) {
            return
        }

        scanner?.stopScan(
            scanCallback
        )

        _isScanning.value = false
        jetsonOnlyScan = false

        stopRunnable?.let {
            handler.removeCallbacks(it)
        }

        stopRunnable = null
    }


    fun clear() {

        deviceMap.clear()

        _devices.value =
            emptyList()
    }
}
