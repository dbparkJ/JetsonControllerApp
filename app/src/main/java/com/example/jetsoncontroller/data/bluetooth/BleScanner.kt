package com.example.jetsoncontroller.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
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


    @SuppressLint("MissingPermission")
    private val scanCallback =
        object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                val device =
                    result.device

                /*
                 * Prefer the latest advertised name.
                 *
                 * IMPORTANT:
                 * Do not display unnamed devices.
                 *
                 * If the device initially sends an advertisement
                 * without a name, it will be ignored.
                 *
                 * If a later advertisement / scan response includes
                 * a name, this callback runs again and the device
                 * will then be inserted.
                 */
                val name =
                    result.scanRecord?.deviceName
                        ?: device.name

                if (name.isNullOrBlank()) {
                    return
                }

                val address =
                    device.address

                deviceMap[address] =
                    JetsonDevice(
                        device = device,
                        name = name,
                        address = address,
                        rssi = result.rssi
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

        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .setReportDelay(0)
                .build()

        _isScanning.value = true

        /*
         * During early development we scan all BLE devices
         * and hide unnamed ones.
         *
         * When the Jetson advertiser is complete,
         * replace this with a SERVICE_UUID ScanFilter.
         */
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
