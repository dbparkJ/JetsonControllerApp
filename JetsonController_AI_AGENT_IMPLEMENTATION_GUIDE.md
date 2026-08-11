# Jetson Controller Android App — AI Agent Implementation Guide

> **Purpose**
>
> This document is an implementation specification for an AI coding agent.
> The agent should use this file as the primary guide to refactor and extend the existing Android project.
>
> Target project:
>
> - Project name: `JetsonController`
> - Package: `com.example.jetsoncontroller`
> - Platform: Android
> - Language: Kotlin
> - UI: Jetpack Compose + Material 3
> - Transport: Bluetooth Low Energy (BLE)
> - Current minimum SDK: Android 12 / API 31
> - Current app already compiles and BLE scanning has been verified on a real Samsung Galaxy device.
>
> **Do not replace the working BLE behavior with mock data.**
> Preserve actual BLE scanning and actual GATT connection behavior.

---

# 0. Current verified state

The existing project already has the following working:

- Android Studio project created.
- `BLUETOOTH_SCAN` runtime permission works.
- `BLUETOOTH_CONNECT` runtime permission works.
- `BLUETOOTH_SCAN` uses `neverForLocation`.
- BLE scan successfully receives nearby advertisements.
- App runs on a physical Galaxy device.
- Project builds successfully.
- BLE scan currently finds too many devices.
- Devices with no device name must NOT be shown.
- If a device was initially unnamed and a later advertising/scan-response packet contains its name, it should then appear.
- Current UI is only a development prototype and must be replaced with a polished Material 3 UI.

The agent must not regress any of the above.

---

# 1. Product goal

Build a production-quality Android application that controls an NVIDIA Jetson device over BLE.

Initial connection must require:

- no Wi-Fi
- no Ethernet
- no USB connection to Jetson
- no internet

The intended flow is:

```text
Jetson boots
    ↓
Jetson BLE service starts automatically
    ↓
Jetson advertises its Control Service UUID
    ↓
Android app scans
    ↓
Jetson appears in device list
    ↓
User connects
    ↓
Android discovers Jetson GATT service
    ↓
Dashboard opens
    ↓
Android can read status / send commands
```

Later, BLE may also be used for Wi-Fi provisioning.

Large data such as logs, video, LiDAR point clouds, map files, and updates should eventually use Wi-Fi rather than BLE.

---

# 2. Mandatory architecture

Do NOT keep all logic inside `MainActivity.kt`.

Use this architecture:

```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
BLE Scanner / BLE GATT Client
 ↓
Android Bluetooth stack
 ↓
Jetson
```

Recommended project tree:

```text
com.example.jetsoncontroller
│
├── MainActivity.kt
├── JetsonApplication.kt
│
├── data
│   ├── bluetooth
│   │   ├── BleScanner.kt
│   │   ├── BleGattClient.kt
│   │   └── JetsonGattSpec.kt
│   │
│   └── repository
│       └── JetsonRepository.kt
│
├── model
│   ├── JetsonDevice.kt
│   ├── ConnectionState.kt
│   └── JetsonStatus.kt
│
├── protocol
│   ├── JetsonCommand.kt
│   └── CommandCodec.kt
│
├── ui
│   ├── JetsonApp.kt
│   │
│   ├── devices
│   │   ├── DeviceListScreen.kt
│   │   ├── DeviceListViewModel.kt
│   │   └── DeviceListUiState.kt
│   │
│   ├── dashboard
│   │   ├── DashboardScreen.kt
│   │   ├── DashboardViewModel.kt
│   │   └── DashboardUiState.kt
│   │
│   └── components
│       ├── DeviceCard.kt
│       ├── ConnectionStatusCard.kt
│       └── MetricCard.kt
│
└── util
    └── SignalStrength.kt
```

Keep the existing generated Compose theme files under:

```text
ui/theme/
```

unless there is a strong reason to change them.

---

# 3. Architectural rules

The AI agent MUST follow these rules.

## Rule 1 — MainActivity must stay small

`MainActivity.kt` is responsible only for:

- runtime permission request
- setting Compose content
- launching the root application composable

It must NOT contain:

- scan callbacks
- BluetoothGatt callbacks
- device list mutation
- command encoding
- dashboard business logic

---

## Rule 2 — UI never talks directly to Bluetooth APIs

This is NOT allowed:

```kotlin
@Composable
fun Screen() {
    bluetoothAdapter.bluetoothLeScanner.startScan(...)
}
```

Instead:

```text
Composable
 ↓ event
ViewModel
 ↓
Repository
 ↓
BleScanner / BleGattClient
```

---

## Rule 3 — Bluetooth classes do not contain Compose code

`BleScanner.kt` and `BleGattClient.kt` must contain Android Bluetooth logic only.

---

## Rule 4 — ViewModel exposes immutable UI state

Use `StateFlow`.

Example:

```kotlin
data class DeviceListUiState(
    val devices: List<JetsonDevice> = emptyList(),
    val isScanning: Boolean = false,
    val permissionGranted: Boolean = false,
    val message: String = "Bluetooth 준비됨"
)
```

---

## Rule 5 — no unnamed devices in the list

Mandatory filter:

```kotlin
val name =
    result.scanRecord?.deviceName
        ?: result.device.name

if (name.isNullOrBlank()) {
    return
}
```

Do NOT create:

```kotlin
"Unknown"
"Unnamed device"
"이름 없는 BLE 장치"
```

as a display substitute.

If a later BLE packet contains the name, the normal scan callback should add the device at that time.

---

## Rule 6 — deduplicate by device address

Use BLE address as the temporary scan-list identity.

Update RSSI/name if a new result arrives for an existing address.

---

## Rule 7 — sort by RSSI

Nearest / strongest devices should appear first.

```text
-40 dBm
-55 dBm
-67 dBm
-80 dBm
```

Sort descending (`-40` before `-80`).

---

# 4. AndroidManifest.xml

Use the following Bluetooth declarations.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="true" />

    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />

    <uses-permission
        android:name="android.permission.BLUETOOTH_CONNECT" />

    <application
        android:name=".JetsonApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.JetsonController">

        <activity
            android:name=".MainActivity"
            android:exported="true">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

        </activity>

    </application>

</manifest>
```

Do not re-add `ACCESS_FINE_LOCATION` unless a future product requirement genuinely uses BLE scan results to derive location.

---

# 5. Gradle dependencies

Do NOT arbitrarily downgrade Kotlin, AGP, Compose, Gradle, or SDK versions already generated by Android Studio.

Preserve the project versions.

Make sure the app module contains equivalent dependencies for:

```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-compose:<compatible-version>")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<compatible-version>")
implementation("androidx.navigation:navigation-compose:<compatible-version>")
implementation("androidx.compose.material:material-icons-extended")
```

If the project uses `libs.versions.toml`, add aliases there instead of hardcoding dependency versions if practical.

The AI agent should inspect the existing project first and integrate with the existing dependency style.

After dependency edits:

```text
Gradle Sync
→ assembleDebug
```

must succeed.

---

# 6. GATT protocol constants

Create:

`data/bluetooth/JetsonGattSpec.kt`

```kotlin
package com.example.jetsoncontroller.data.bluetooth

import java.util.UUID

object JetsonGattSpec {

    val SERVICE_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000001")

    val COMMAND_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000002")

    val STATUS_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000003")

    val SYSTEM_INFO_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000004")

    val WIFI_CONFIG_UUID: UUID =
        UUID.fromString("a1000000-0000-0000-0000-000000000005")
}
```

These UUIDs are the Android-side protocol definition.

The Jetson BLE GATT server must later expose the same UUIDs.

---

# 7. Model classes

## `model/JetsonDevice.kt`

```kotlin
package com.example.jetsoncontroller.model

import android.bluetooth.BluetoothDevice

data class JetsonDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int
)
```

---

## `model/ConnectionState.kt`

```kotlin
package com.example.jetsoncontroller.model

sealed interface ConnectionState {

    data object Disconnected : ConnectionState

    data class Connecting(
        val deviceName: String
    ) : ConnectionState

    data class Connected(
        val deviceName: String
    ) : ConnectionState

    data class Ready(
        val deviceName: String
    ) : ConnectionState

    data class Error(
        val message: String
    ) : ConnectionState
}
```

Meaning:

```text
Disconnected
    ↓
Connecting
    ↓
Connected
    ↓ service discovery
Ready
```

`Ready` means the expected Jetson GATT service was discovered and commands may be sent.

---

## `model/JetsonStatus.kt`

```kotlin
package com.example.jetsoncontroller.model

data class JetsonStatus(
    val cpuPercent: Int = 0,
    val gpuPercent: Int = 0,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val temperatureC: Float = 0f,
    val storagePercent: Int = 0,
    val cameraRunning: Boolean = false,
    val lidarRunning: Boolean = false,
    val gnssRunning: Boolean = false,
    val mmsRunning: Boolean = false
)
```

This is an Android-side domain model.

The exact Jetson status packet format can be expanded later.

---

# 8. BLE Scanner

Create:

`data/bluetooth/BleScanner.kt`

```kotlin
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
```

---

# 9. Future Jetson-only BLE filtering

Do NOT enable this until Jetson advertising is implemented and verified.

Once Jetson advertises `JetsonGattSpec.SERVICE_UUID`, change the scan to:

```kotlin
import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
```

Then:

```kotlin
val filter =
    ScanFilter.Builder()
        .setServiceUuid(
            ParcelUuid(
                JetsonGattSpec.SERVICE_UUID
            )
        )
        .build()

bleScanner.startScan(
    listOf(filter),
    settings,
    scanCallback
)
```

Final product behavior should therefore become:

```text
Nearby BLE devices
 ├─ watch
 ├─ earphones
 ├─ TV
 ├─ sensor
 └─ Jetson Control UUID

Android filter
        ↓

Jetson only
```

Do not rely only on a device name prefix such as `JETSON-`.

UUID filtering is the primary identity filter.

The device name is for humans.

---

# 10. BLE GATT Client

Create:

`data/bluetooth/BleGattClient.kt`

```kotlin
package com.example.jetsoncontroller.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.example.jetsoncontroller.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleGattClient(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private var bluetoothGatt:
        BluetoothGatt? = null

    private var currentDeviceName:
        String = "Jetson"

    private val _connectionState =
        MutableStateFlow<ConnectionState>(
            ConnectionState.Disconnected
        )

    val connectionState:
        StateFlow<ConnectionState> =
        _connectionState.asStateFlow()


    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothDevice,
        displayName: String
    ) {

        disconnect()

        currentDeviceName =
            displayName

        _connectionState.value =
            ConnectionState.Connecting(
                displayName
            )

        bluetoothGatt =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.M
            ) {

                device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )

            } else {

                device.connectGatt(
                    appContext,
                    false,
                    gattCallback
                )
            }
    }


    @SuppressLint("MissingPermission")
    fun disconnect() {

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        bluetoothGatt = null

        _connectionState.value =
            ConnectionState.Disconnected
    }


    @SuppressLint("MissingPermission")
    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    _connectionState.value =
                        ConnectionState.Error(
                            "GATT connection error: $status"
                        )

                    gatt.close()

                    return
                }

                when (newState) {

                    BluetoothProfile.STATE_CONNECTED -> {

                        _connectionState.value =
                            ConnectionState.Connected(
                                currentDeviceName
                            )

                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {

                        _connectionState.value =
                            ConnectionState.Disconnected

                        gatt.close()
                    }
                }
            }


            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    _connectionState.value =
                        ConnectionState.Error(
                            "Service discovery failed: $status"
                        )

                    return
                }

                val jetsonService =
                    gatt.getService(
                        JetsonGattSpec.SERVICE_UUID
                    )

                if (jetsonService == null) {

                    _connectionState.value =
                        ConnectionState.Error(
                            "Jetson Control Service not found"
                        )

                    return
                }

                _connectionState.value =
                    ConnectionState.Ready(
                        currentDeviceName
                    )
            }


            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                    BluetoothGattCharacteristic,
                value: ByteArray
            ) {

                when (characteristic.uuid) {

                    JetsonGattSpec.STATUS_UUID -> {
                        /*
                         * TODO:
                         * decode Jetson status packet
                         * and expose through repository.
                         */
                    }
                }
            }

            @Deprecated(
                "Legacy Android callback"
            )
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                    BluetoothGattCharacteristic
            ) {

                onCharacteristicChanged(
                    gatt,
                    characteristic,
                    characteristic.value ?: byteArrayOf()
                )
            }
        }


    fun isReady(): Boolean =
        connectionState.value
            is ConnectionState.Ready


    @SuppressLint("MissingPermission")
    fun writeCommand(
        payload: ByteArray
    ): Boolean {

        val gatt =
            bluetoothGatt
                ?: return false

        val service =
            gatt.getService(
                JetsonGattSpec.SERVICE_UUID
            ) ?: return false

        val characteristic =
            service.getCharacteristic(
                JetsonGattSpec.COMMAND_UUID
            ) ?: return false

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic
                    .WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS

        } else {

            @Suppress("DEPRECATION")
            characteristic.value =
                payload

            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(
                characteristic
            )
        }
    }


    @SuppressLint("MissingPermission")
    fun enableStatusNotifications(): Boolean {

        val gatt =
            bluetoothGatt
                ?: return false

        val service =
            gatt.getService(
                JetsonGattSpec.SERVICE_UUID
            ) ?: return false

        val characteristic =
            service.getCharacteristic(
                JetsonGattSpec.STATUS_UUID
            ) ?: return false

        val notificationEnabled =
            gatt.setCharacteristicNotification(
                characteristic,
                true
            )

        if (!notificationEnabled) {
            return false
        }

        val cccdUuid =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )

        val descriptor =
            characteristic.getDescriptor(
                cccdUuid
            ) ?: return false

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE
            ) == BluetoothGatt.GATT_SUCCESS

        } else {

            @Suppress("DEPRECATION")
            descriptor.value =
                BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE

            @Suppress("DEPRECATION")
            gatt.writeDescriptor(
                descriptor
            )
        }
    }
}
```

---

# 11. Command protocol

Create:

`protocol/JetsonCommand.kt`

```kotlin
package com.example.jetsoncontroller.protocol

enum class JetsonCommand(
    val id: Byte
) {

    START_SYSTEM(0x01),

    STOP_SYSTEM(0x02),

    RESTART_SERVICES(0x03),

    REBOOT(0x04),

    SHUTDOWN(0x05),

    GET_STATUS(0x06),

    SET_WIFI(0x07)
}
```

---

Create:

`protocol/CommandCodec.kt`

```kotlin
package com.example.jetsoncontroller.protocol

object CommandCodec {

    private const val MAGIC: Byte =
        0x5A

    private const val VERSION: Byte =
        0x01


    fun encode(
        command: JetsonCommand,
        payload: ByteArray = byteArrayOf()
    ): ByteArray {

        require(
            payload.size <= 255
        ) {
            "Payload too large for BLE command frame"
        }

        val length =
            payload.size.toByte()

        val body =
            byteArrayOf(
                MAGIC,
                VERSION,
                command.id,
                length
            ) + payload

        val checksum =
            body.fold(0) { acc, byte ->
                (acc + (byte.toInt() and 0xFF)) and 0xFF
            }.toByte()

        return body +
            byteArrayOf(checksum)
    }
}
```

Frame:

```text
MAGIC
VERSION
COMMAND
LENGTH
PAYLOAD
CHECKSUM
```

Example:

```text
5A 01 01 00 5C
```

This is enough for the first production prototype.

A future revision can replace it with CBOR/Protobuf if necessary.

---

# 12. Repository

Create:

`data/repository/JetsonRepository.kt`

```kotlin
package com.example.jetsoncontroller.data.repository

import android.content.Context
import com.example.jetsoncontroller.data.bluetooth.BleGattClient
import com.example.jetsoncontroller.data.bluetooth.BleScanner
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.CommandCodec
import com.example.jetsoncontroller.protocol.JetsonCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JetsonRepository(
    context: Context
) {

    private val scanner =
        BleScanner(context)

    private val gattClient =
        BleGattClient(context)

    val devices:
        StateFlow<List<JetsonDevice>> =
        scanner.devices

    val isScanning:
        StateFlow<Boolean> =
        scanner.isScanning

    val connectionState:
        StateFlow<ConnectionState> =
        gattClient.connectionState

    private val _status =
        MutableStateFlow(
            JetsonStatus()
        )

    val status:
        StateFlow<JetsonStatus> =
        _status.asStateFlow()


    fun startScan() {

        scanner.startScan(
            durationMillis = 15_000L
        )
    }


    fun stopScan() {

        scanner.stopScan()
    }


    fun connect(
        device: JetsonDevice
    ) {

        scanner.stopScan()

        gattClient.connect(
            device = device.device,
            displayName = device.name
        )
    }


    fun disconnect() {

        gattClient.disconnect()
    }


    fun sendCommand(
        command: JetsonCommand,
        payload: ByteArray = byteArrayOf()
    ): Boolean {

        val frame =
            CommandCodec.encode(
                command,
                payload
            )

        return gattClient.writeCommand(
            frame
        )
    }


    fun requestStatus(): Boolean {

        return sendCommand(
            JetsonCommand.GET_STATUS
        )
    }


    fun startSystem(): Boolean {

        return sendCommand(
            JetsonCommand.START_SYSTEM
        )
    }


    fun stopSystem(): Boolean {

        return sendCommand(
            JetsonCommand.STOP_SYSTEM
        )
    }


    fun restartServices(): Boolean {

        return sendCommand(
            JetsonCommand.RESTART_SERVICES
        )
    }


    fun reboot(): Boolean {

        return sendCommand(
            JetsonCommand.REBOOT
        )
    }


    fun shutdown(): Boolean {

        return sendCommand(
            JetsonCommand.SHUTDOWN
        )
    }
}
```

---

# 13. Application-level service locator

Create:

`JetsonApplication.kt`

```kotlin
package com.example.jetsoncontroller

import android.app.Application
import com.example.jetsoncontroller.data.repository.JetsonRepository

class JetsonApplication :
    Application() {

    lateinit var repository:
        JetsonRepository
        private set

    override fun onCreate() {

        super.onCreate()

        repository =
            JetsonRepository(this)
    }
}
```

This keeps the first implementation simple.

Do NOT add Hilt/Koin merely for fashion.

Dependency injection can be introduced later if the app grows significantly.

---

# 14. Device list UI state

Create:

`ui/devices/DeviceListUiState.kt`

```kotlin
package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonDevice

data class DeviceListUiState(

    val devices:
        List<JetsonDevice> =
        emptyList(),

    val isScanning:
        Boolean = false,

    val permissionGranted:
        Boolean = false,

    val connectionState:
        ConnectionState =
        ConnectionState.Disconnected
)
```

---

# 15. Device list ViewModel

Create:

`ui/devices/DeviceListViewModel.kt`

```kotlin
package com.example.jetsoncontroller.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.JetsonDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DeviceListViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    private val permissionGranted =
        MutableStateFlow(false)

    val uiState =
        combine(
            repository.devices,
            repository.isScanning,
            repository.connectionState,
            permissionGranted
        ) {
                devices,
                scanning,
                connection,
                permission ->

            DeviceListUiState(
                devices = devices,
                isScanning = scanning,
                permissionGranted = permission,
                connectionState = connection
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


    override fun onCleared() {

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
```

---

# 16. Signal-strength helper

Create:

`util/SignalStrength.kt`

```kotlin
package com.example.jetsoncontroller.util

enum class SignalStrength(
    val label: String
) {

    EXCELLENT("매우 강함"),
    GOOD("강함"),
    FAIR("보통"),
    WEAK("약함")
}


fun signalStrengthFromRssi(
    rssi: Int
): SignalStrength {

    return when {

        rssi >= -55 ->
            SignalStrength.EXCELLENT

        rssi >= -67 ->
            SignalStrength.GOOD

        rssi >= -75 ->
            SignalStrength.FAIR

        else ->
            SignalStrength.WEAK
    }
}
```

---

# 17. Reusable device card

Create:

`ui/components/DeviceCard.kt`

```kotlin
package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.util.signalStrengthFromRssi

@Composable
fun DeviceCard(
    device: JetsonDevice,
    onConnect: () -> Unit
) {

    val strength =
        signalStrengthFromRssi(
            device.rssi
        )

    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(24.dp)
    ) {

        Column(
            modifier =
                Modifier.padding(20.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Surface(
                    modifier =
                        Modifier.size(52.dp),
                    shape =
                        RoundedCornerShape(18.dp),
                    color =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                ) {

                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                device.name
                                    .take(1)
                                    .uppercase(),
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimaryContainer
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.size(14.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = device.name,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text = device.address,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Column {

                    Text(
                        text = "신호 세기",
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Spacer(
                        modifier =
                            Modifier.height(2.dp)
                    )

                    Text(
                        text =
                            "${strength.label} · ${device.rssi} dBm",
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        fontWeight =
                            FontWeight.Medium
                    )
                }

                Button(
                    onClick = onConnect,
                    shape =
                        RoundedCornerShape(
                            14.dp
                        )
                ) {

                    Text("연결")
                }
            }
        }
    }
}
```

---

# 18. Connection-status card

Create:

`ui/components/ConnectionStatusCard.kt`

```kotlin
package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ConnectionState

@Composable
fun ConnectionStatusCard(
    isScanning: Boolean,
    connectionState:
        ConnectionState
) {

    val title =
        when {

            isScanning ->
                "주변 장비 검색 중"

            connectionState
                is ConnectionState.Ready ->
                "Jetson 연결됨"

            connectionState
                is ConnectionState.Connecting ->
                "Jetson 연결 중"

            else ->
                "Bluetooth 준비됨"
        }

    val detail =
        when (
            connectionState
        ) {

            ConnectionState.Disconnected ->
                if (isScanning)
                    "BLE 광고 패킷을 검색하고 있습니다."
                else
                    "주변 Jetson 장비를 검색할 수 있습니다."

            is ConnectionState.Connecting ->
                connectionState.deviceName

            is ConnectionState.Connected ->
                "${connectionState.deviceName} · 서비스 검색 중"

            is ConnectionState.Ready ->
                "${connectionState.deviceName} · 제어 준비 완료"

            is ConnectionState.Error ->
                connectionState.message
        }

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(22.dp),
        color =
            MaterialTheme
                .colorScheme
                .surfaceContainer
    ) {

        Row(
            modifier =
                Modifier.padding(18.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(
                            color =
                                when {
                                    connectionState
                                        is ConnectionState.Ready ->
                                        MaterialTheme
                                            .colorScheme
                                            .primary

                                    isScanning ->
                                        MaterialTheme
                                            .colorScheme
                                            .tertiary

                                    else ->
                                        MaterialTheme
                                            .colorScheme
                                            .outline
                                },
                            shape =
                                CircleShape
                        )
            )

            Spacer(
                modifier =
                    Modifier.size(12.dp)
            )

            Column {

                Text(
                    text = title,
                    style =
                        MaterialTheme
                            .typography
                            .labelLarge,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    text = detail,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}
```

---

# 19. Device list screen

Create:

`ui/devices/DeviceListScreen.kt`

```kotlin
package com.example.jetsoncontroller.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.JetsonDevice
import com.example.jetsoncontroller.ui.components.ConnectionStatusCard
import com.example.jetsoncontroller.ui.components.DeviceCard

@Composable
fun DeviceListScreen(
    state: DeviceListUiState,
    onScanClick: () -> Unit,
    onConnect: (JetsonDevice) -> Unit
) {

    Scaffold(
        containerColor =
            MaterialTheme
                .colorScheme
                .background
    ) { paddingValues ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .padding(
                        horizontal = 22.dp
                    )
        ) {

            Spacer(
                modifier =
                    Modifier.height(28.dp)
            )

            Text(
                text = "Jetson Control",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    "Bluetooth를 통해 주변 Jetson 장비에 연결하세요.",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                modifier =
                    Modifier.height(22.dp)
            )

            ConnectionStatusCard(
                isScanning =
                    state.isScanning,
                connectionState =
                    state.connectionState
            )

            Spacer(
                modifier =
                    Modifier.height(28.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = "주변 장비",
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Text(
                        text =
                            "${state.devices.size}개의 이름 있는 BLE 장비",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick =
                        onScanClick,
                    enabled =
                        state.permissionGranted
                ) {

                    if (
                        state.isScanning
                    ) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(
                                    18.dp
                                ),
                            strokeWidth =
                                2.dp
                        )

                        Spacer(
                            modifier =
                                Modifier.size(
                                    8.dp
                                )
                        )

                        Text("중지")

                    } else {

                        Text("검색")
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            if (
                !state.permissionGranted
            ) {

                PermissionMessage(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                )

            } else if (
                state.devices.isEmpty()
            ) {

                EmptyDeviceView(
                    scanning =
                        state.isScanning,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                )

            } else {

                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        ),
                    contentPadding =
                        PaddingValues(
                            bottom = 32.dp
                        )
                ) {

                    items(
                        items =
                            state.devices,
                        key = {
                            it.address
                        }
                    ) {
                            device ->

                        DeviceCard(
                            device =
                                device,
                            onConnect = {
                                onConnect(
                                    device
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun EmptyDeviceView(
    scanning: Boolean,
    modifier: Modifier = Modifier
) {

    Box(
        modifier =
            modifier,
        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Surface(
                modifier =
                    Modifier.size(72.dp),
                shape =
                    CircleShape,
                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceContainerHighest
            ) {

                Box(
                    contentAlignment =
                        Alignment.Center
                ) {

                    if (scanning) {

                        CircularProgressIndicator(
                            modifier =
                                Modifier.size(
                                    28.dp
                                ),
                            strokeWidth =
                                3.dp
                        )

                    } else {

                        Text(
                            text = "BLE",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelLarge,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Text(
                text =
                    if (scanning)
                        "주변 장비를 찾고 있습니다"
                    else
                        "검색된 장비가 없습니다",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    if (scanning)
                        "이름이 확인된 BLE 장비만 표시합니다."
                    else
                        "장비가 켜져 있고 BLE 광고 중인지 확인하세요.",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }
    }
}


@Composable
private fun PermissionMessage(
    modifier: Modifier = Modifier
) {

    Box(
        modifier =
            modifier,
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                "주변 기기 권한을 허용해야 Jetson을 검색할 수 있습니다.",
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}
```

---

# 20. Dashboard state

Create:

`ui/dashboard/DashboardUiState.kt`

```kotlin
package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.model.JetsonStatus

data class DashboardUiState(

    val connectionState:
        ConnectionState =
        ConnectionState.Disconnected,

    val status:
        JetsonStatus =
        JetsonStatus()
)
```

---

# 21. Dashboard ViewModel

Create:

`ui/dashboard/DashboardViewModel.kt`

```kotlin
package com.example.jetsoncontroller.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.protocol.JetsonCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val repository:
        JetsonRepository
) : ViewModel() {

    val uiState =
        combine(
            repository.connectionState,
            repository.status
        ) {
                connection,
                status ->

            DashboardUiState(
                connectionState =
                    connection,
                status =
                    status
            )
        }.stateIn(
            scope = viewModelScope,
            started =
                SharingStarted
                    .WhileSubscribed(
                        5_000
                    ),
            initialValue =
                DashboardUiState()
        )


    fun requestStatus() =
        repository.requestStatus()


    fun startSystem() =
        repository.startSystem()


    fun stopSystem() =
        repository.stopSystem()


    fun restartServices() =
        repository.restartServices()


    fun reboot() =
        repository.reboot()


    fun shutdown() =
        repository.shutdown()


    fun disconnect() =
        repository.disconnect()


    class Factory(
        private val repository:
            JetsonRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel>
            create(
                modelClass: Class<T>
            ): T {

            return DashboardViewModel(
                repository
            ) as T
        }
    }
}
```

---

# 22. Metric card

Create:

`ui/components/MetricCard.kt`

```kotlin
package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier =
            modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                20.dp
            ),
        color =
            MaterialTheme
                .colorScheme
                .surfaceContainer
    ) {

        Column(
            modifier =
                Modifier.padding(
                    18.dp
                )
        ) {

            Text(
                text = title,
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Text(
                text = value,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}
```

---

# 23. Dashboard screen

Create:

`ui/dashboard/DashboardScreen.kt`

```kotlin
package com.example.jetsoncontroller.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.ui.components.MetricCard

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onDisconnect: () -> Unit,
    onStartSystem: () -> Unit,
    onStopSystem: () -> Unit,
    onRestartServices: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit
) {

    val deviceName =
        when (
            val connection =
                state.connectionState
        ) {

            is ConnectionState.Ready ->
                connection.deviceName

            is ConnectionState.Connected ->
                connection.deviceName

            is ConnectionState.Connecting ->
                connection.deviceName

            else ->
                "Jetson"
        }

    Scaffold(
        containerColor =
            MaterialTheme
                .colorScheme
                .background
    ) {
            paddingValues ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .padding(
                        horizontal = 22.dp
                    )
                    .verticalScroll(
                        rememberScrollState()
                    )
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = deviceName,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    if (
                        state.connectionState
                        is ConnectionState.Ready
                    )
                        "● 연결됨"
                    else
                        "연결 상태 확인 중",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = "시스템 상태",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                MetricCard(
                    title = "CPU",
                    value =
                        "${state.status.cpuPercent}%",
                    modifier =
                        Modifier.weight(1f)
                )

                MetricCard(
                    title = "GPU",
                    value =
                        "${state.status.gpuPercent}%",
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                MetricCard(
                    title = "TEMP",
                    value =
                        "${state.status.temperatureC}°C",
                    modifier =
                        Modifier.weight(1f)
                )

                MetricCard(
                    title = "STORAGE",
                    value =
                        "${state.status.storagePercent}%",
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Text(
                text = "서비스",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            ServiceRow(
                name = "Camera",
                running =
                    state.status
                        .cameraRunning
            )

            ServiceRow(
                name = "LiDAR",
                running =
                    state.status
                        .lidarRunning
            )

            ServiceRow(
                name = "GNSS",
                running =
                    state.status
                        .gnssRunning
            )

            ServiceRow(
                name = "MMS",
                running =
                    state.status
                        .mmsRunning
            )

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp
                    )
            )

            Button(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onStartSystem
            ) {

                Text("전체 시스템 시작")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onStopSystem
            ) {

                Text("전체 시스템 중지")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        16.dp
                    ),
                onClick =
                    onRestartServices
            ) {

                Text("서비스 재시작")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        30.dp
                    )
            )

            HorizontalDivider()

            Spacer(
                modifier =
                    Modifier.height(
                        22.dp
                    )
            )

            Text(
                text = "장비 관리",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onReboot
            ) {
                Text("Jetson 재부팅")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onShutdown
            ) {
                Text("Jetson 종료")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            OutlinedButton(
                modifier =
                    Modifier.fillMaxWidth(),
                onClick =
                    onDisconnect
            ) {
                Text("연결 해제")
            }

            Spacer(
                modifier =
                    Modifier.height(
                        40.dp
                    )
            )
        }
    }
}


@Composable
private fun ServiceRow(
    name: String,
    running: Boolean
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 10.dp
                ),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            text = name,
            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )

        Text(
            text =
                if (running)
                    "● Running"
                else
                    "○ Stopped",
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}
```

Do NOT use bright red warning UI for ordinary stop/disconnect actions.

Use confirmation dialogs later for:

- reboot
- shutdown
- destructive reset

---

# 24. Root Compose app

Create:

`ui/JetsonApp.kt`

```kotlin
package com.example.jetsoncontroller.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jetsoncontroller.data.repository.JetsonRepository
import com.example.jetsoncontroller.model.ConnectionState
import com.example.jetsoncontroller.ui.dashboard.DashboardScreen
import com.example.jetsoncontroller.ui.dashboard.DashboardViewModel
import com.example.jetsoncontroller.ui.devices.DeviceListScreen
import com.example.jetsoncontroller.ui.devices.DeviceListViewModel

private object Routes {

    const val DEVICES =
        "devices"

    const val DASHBOARD =
        "dashboard"
}


@Composable
fun JetsonApp(
    repository:
        JetsonRepository,
    bluetoothPermissionGranted:
        Boolean
) {

    val navController =
        rememberNavController()

    val deviceViewModel:
        DeviceListViewModel =
        viewModel(
            factory =
                DeviceListViewModel.Factory(
                    repository
                )
        )

    val dashboardViewModel:
        DashboardViewModel =
        viewModel(
            factory =
                DashboardViewModel.Factory(
                    repository
                )
        )

    val deviceState by
        deviceViewModel
            .uiState
            .collectAsStateWithLifecycle()

    val dashboardState by
        dashboardViewModel
            .uiState
            .collectAsStateWithLifecycle()


    LaunchedEffect(
        bluetoothPermissionGranted
    ) {

        deviceViewModel
            .onPermissionResult(
                bluetoothPermissionGranted
            )
    }


    LaunchedEffect(
        deviceState.connectionState
    ) {

        if (
            deviceState
                .connectionState
                is ConnectionState.Ready
        ) {

            navController.navigate(
                Routes.DASHBOARD
            ) {

                launchSingleTop =
                    true
            }
        }
    }


    NavHost(
        navController =
            navController,
        startDestination =
            Routes.DEVICES
    ) {

        composable(
            Routes.DEVICES
        ) {

            DeviceListScreen(
                state =
                    deviceState,
                onScanClick = {
                    deviceViewModel
                        .toggleScan()
                },
                onConnect = {
                    device ->
                    deviceViewModel
                        .connect(
                            device
                        )
                }
            )
        }


        composable(
            Routes.DASHBOARD
        ) {

            DashboardScreen(
                state =
                    dashboardState,

                onDisconnect = {

                    dashboardViewModel
                        .disconnect()

                    navController
                        .popBackStack(
                            Routes.DEVICES,
                            inclusive = false
                        )
                },

                onStartSystem = {
                    dashboardViewModel
                        .startSystem()
                },

                onStopSystem = {
                    dashboardViewModel
                        .stopSystem()
                },

                onRestartServices = {
                    dashboardViewModel
                        .restartServices()
                },

                onReboot = {
                    dashboardViewModel
                        .reboot()
                },

                onShutdown = {
                    dashboardViewModel
                        .shutdown()
                }
            )
        }
    }
}
```

---

# 25. MainActivity

Replace the old large `MainActivity.kt` with this small activity.

```kotlin
package com.example.jetsoncontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.jetsoncontroller.ui.JetsonApp
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme

class MainActivity :
    ComponentActivity() {

    private var permissionGranted
        by mutableStateOf(false)


    private fun hasBluetoothPermissions():
        Boolean {

        val scan =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission
                        .BLUETOOTH_SCAN
                ) ==
                PackageManager
                    .PERMISSION_GRANTED

        val connect =
            ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission
                        .BLUETOOTH_CONNECT
                ) ==
                PackageManager
                    .PERMISSION_GRANTED

        return scan &&
            connect
    }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        permissionGranted =
            hasBluetoothPermissions()

        val app =
            application
                as JetsonApplication

        setContent {

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    contract =
                        ActivityResultContracts
                            .RequestMultiplePermissions()
                ) {

                    permissionGranted =
                        hasBluetoothPermissions()
                }


            LaunchedEffect(Unit) {

                if (
                    !hasBluetoothPermissions()
                ) {

                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission
                                .BLUETOOTH_SCAN,

                            Manifest.permission
                                .BLUETOOTH_CONNECT
                        )
                    )
                }
            }


            JetsonControllerTheme {

                JetsonApp(
                    repository =
                        app.repository,
                    bluetoothPermissionGranted =
                        permissionGranted
                )
            }
        }
    }
}
```

If the generated theme function has a different exact name, use the project’s actual theme function instead of inventing one.

---

# 26. UX / visual design requirements

The app must look like a real controller product, not a developer demo.

Use:

- Material 3
- generous whitespace
- 20–24dp rounded cards
- simple hierarchy
- minimal borders
- restrained colors
- system light/dark theme support
- clear status text
- no giant debug labels
- no raw GATT terminology in normal user-facing UI
- no MAC address as the main title
- no rainbow colors
- no excessive shadows
- no permanent spinners when idle

Device list layout:

```text
Jetson Control

Bluetooth를 통해 주변 Jetson 장비에 연결하세요.

┌─────────────────────────────────┐
│ ● Bluetooth 준비됨              │
│   주변 Jetson 장비를 검색할 수 있음 │
└─────────────────────────────────┘

주변 장비                         검색

┌─────────────────────────────────┐
│ [J]  MMS-JETSON-01              │
│      AA:BB:CC:DD:EE:FF           │
│                                 │
│ 신호 세기                        │
│ 매우 강함 · -43 dBm       [연결] │
└─────────────────────────────────┘
```

Dashboard:

```text
MMS-JETSON-01
● 연결됨

시스템 상태

CPU                 GPU
27%                 42%

TEMP                STORAGE
47°C                71%

서비스
Camera            ● Running
LiDAR             ● Running
GNSS              ● Running
MMS               ● Running

[ 전체 시스템 시작 ]
[ 전체 시스템 중지 ]
[ 서비스 재시작 ]

장비 관리
[ Jetson 재부팅 ]
[ Jetson 종료 ]
[ 연결 해제 ]
```

---

# 27. Bluetooth behavior requirements

## Scan

When user taps scan:

```text
devices cleared
 ↓
low-latency BLE scan
 ↓
unnamed result ignored
 ↓
named result inserted
 ↓
same-address result updates existing item
 ↓
list sorted by RSSI
 ↓
scan automatically stops
```

Recommended scan duration:

```text
15 seconds
```

Do not scan indefinitely.

---

## Connection

When user presses Connect:

```text
stop scan
 ↓
disconnect old GATT if present
 ↓
connect LE
 ↓
Connected state
 ↓
discover services
 ↓
verify JetsonGattSpec.SERVICE_UUID
 ↓
Ready state
 ↓
navigate Dashboard
```

If service UUID is not found:

```text
show ConnectionState.Error
```

Do NOT treat an arbitrary BLE peripheral as a valid Jetson.

---

# 28. Jetson command semantics

The Android app will eventually send:

| Command | ID | Meaning |
|---|---:|---|
| START_SYSTEM | `0x01` | Start the Jetson application/service stack |
| STOP_SYSTEM | `0x02` | Gracefully stop application/service stack |
| RESTART_SERVICES | `0x03` | Restart controlled services |
| REBOOT | `0x04` | Reboot Jetson Linux |
| SHUTDOWN | `0x05` | Power down OS |
| GET_STATUS | `0x06` | Request device status |
| SET_WIFI | `0x07` | Provision Wi-Fi credentials |

Do not implement arbitrary shell execution over BLE.

This is a security requirement.

The Jetson daemon must map a limited, explicit command set to allowed actions.

---

# 29. Jetson-side target architecture

The future Jetson implementation should look like:

```text
systemd
  ↓
jetson-control.service
  ↓
Jetson Control Daemon
  ├─ BLE GATT Server
  ├─ Command Router
  ├─ Status Collector
  ├─ Network Manager adapter
  └─ Service Manager adapter
        ├─ Docker
        ├─ ROS
        ├─ Camera
        ├─ LiDAR
        └─ GNSS
```

The Android app should NEVER directly expose arbitrary Linux shell commands.

Bad:

```text
BLE payload:
"sudo rm -rf ..."
```

Good:

```text
BLE command:
REBOOT
```

Jetson side:

```text
REBOOT
 ↓
validated command router
 ↓
allowed privileged operation
```

---

# 30. Jetson BLE advertisement

Final Jetson advertising packet should include:

```text
Device Name:
MMS-JETSON-XXXX

Service UUID:
a1000000-0000-0000-0000-000000000001
```

Recommended name generation:

```text
MMS-JETSON-{last4DeviceId}
```

Example:

```text
MMS-JETSON-A82F
```

Do not use the BLE name as authentication.

---

# 31. Security requirements

Before production deployment, add device authentication.

Recommended future flow:

```text
QR on Jetson enclosure
 ↓
Device ID + provision secret
 ↓
Android scans BLE
 ↓
challenge
 ↓
signed response / HMAC
 ↓
authenticated session
```

At minimum:

- no arbitrary remote shell
- no unauthenticated reboot in production
- no plaintext Wi-Fi password stored permanently in app logs
- do not print secrets to Logcat
- do not put secrets in BLE device name
- do not trust name alone as identity

For the current development prototype, GATT connection may remain unauthenticated until the Jetson daemon is available.

---

# 32. Wi-Fi provisioning — future feature

Do not implement until basic GATT commands work.

Suggested UI:

```text
Wi-Fi 설정

SSID
[ office_wifi            ]

Password
[ •••••••••••••••        ]

[ Jetson에 전달 ]
```

Flow:

```text
Android
 ↓ BLE encrypted/authenticated command
Jetson
 ↓
NetworkManager
 ↓
Wi-Fi connect
 ↓
BLE status response
 ↓
Android
```

Do not place Wi-Fi passwords in Logcat.

---

# 33. Status protocol — initial recommendation

A later status notification may use compact JSON during prototyping:

```json
{
  "cpu": 27,
  "gpu": 42,
  "ramUsedMb": 8120,
  "ramTotalMb": 16000,
  "tempC": 47.2,
  "storage": 71,
  "camera": true,
  "lidar": true,
  "gnss": true,
  "mms": true
}
```

But BLE payload fragmentation must be considered.

For the first version, either:

1. keep status packets small, or
2. define a compact binary status structure.

Do not assume a large JSON object fits in a single default BLE characteristic packet.

---

# 34. Error handling requirements

User-facing errors should be understandable.

Bad:

```text
GATT_ERROR 133
```

Better:

```text
Jetson에 연결하지 못했습니다.
장비가 켜져 있는지 확인한 뒤 다시 시도하세요.
```

Developer Logcat may include:

```text
status=133
```

UI should separate technical details from normal user text.

Handle:

- Bluetooth off
- permission denied
- scan failure
- connection timeout
- GATT disconnect
- service UUID missing
- command write failure
- Jetson disconnect while Dashboard open

---

# 35. Lifecycle requirements

The agent must prevent BLE resource leaks.

On appropriate lifecycle/destruction:

- stop scan
- close old BluetoothGatt
- do not hold destroyed Activity context
- use `applicationContext` in BLE infrastructure
- repository should survive Activity recreation through Application service locator
- Compose recomposition must not restart scanning unintentionally

---

# 36. Logging rules

Use a consistent tag style:

```text
JetsonBleScanner
JetsonGatt
JetsonRepository
```

Debug logs may contain:

- address
- RSSI
- connection state
- GATT status
- service UUIDs

Never log:

- Wi-Fi passwords
- device provisioning secrets
- auth tokens

---

# 37. Testing strategy

## Phase A — Android scanner regression

Use a second Android device with nRF Connect advertiser.

Verify:

- advertisement ON
- named device appears
- unnamed packet does not create list entry
- if name later appears, device is displayed
- duplicate address does not create duplicate cards
- RSSI updates
- strongest devices sort first

---

## Phase B — arbitrary BLE GATT device

Connect to a non-Jetson BLE peripheral.

Expected:

```text
BLE link may connect
 ↓
Jetson service missing
 ↓
ConnectionState.Error
```

The app must NOT navigate to Dashboard.

---

## Phase C — Jetson GATT service

After Jetson server exists:

Verify:

- Android sees Jetson
- connect succeeds
- service discovery succeeds
- `Ready` emitted
- Dashboard opens
- START command packet reaches Jetson
- STOP command packet reaches Jetson
- status notification reaches Android

---

# 38. Build acceptance criteria

After each implementation phase run:

```text
Build
→ Assemble Project
```

or terminal:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

The final requirement:

```text
BUILD SUCCESSFUL
```

There must be no unresolved references.

Warnings about legacy callbacks may remain only where compatibility fallback is intentionally used.

---

# 39. Runtime acceptance criteria

The implementation is acceptable only if all conditions below are true.

## Device screen

- [ ] app launches
- [ ] nearby-device permission prompt works
- [ ] scan button works
- [ ] BLE scan starts
- [ ] scan automatically stops
- [ ] unnamed BLE devices are hidden
- [ ] named BLE devices appear
- [ ] duplicate advertisements do not create duplicate cards
- [ ] RSSI updates
- [ ] device list is sorted strongest-first
- [ ] UI looks like Material 3 application rather than debug UI

## Connection

- [ ] tapping Connect stops scanning
- [ ] GATT connection is attempted
- [ ] service discovery runs
- [ ] arbitrary peripheral without Jetson UUID is rejected
- [ ] Jetson UUID produces `Ready`
- [ ] Ready opens Dashboard

## Architecture

- [ ] MainActivity contains no BLE callbacks
- [ ] BLE scanning isolated in `BleScanner`
- [ ] GATT isolated in `BleGattClient`
- [ ] Repository is the app data access layer
- [ ] ViewModels expose StateFlow state
- [ ] Composables are stateless where practical

---

# 40. AI agent implementation order

The AI coding agent must perform changes in this order.

## Step 1 — inspect current project

Before editing:

- inspect package name
- inspect current `MainActivity.kt`
- inspect current manifest
- inspect Gradle dependencies
- inspect theme function name
- confirm project builds before refactor

Do not blindly overwrite build files.

---

## Step 2 — create package directories

Create:

```text
data/bluetooth
data/repository
model
protocol
ui/devices
ui/dashboard
ui/components
util
```

---

## Step 3 — move BLE scanning out first

Implement:

```text
JetsonDevice
BleScanner
```

Build.

Confirm scan still works on physical phone.

Only then continue.

---

## Step 4 — repository

Add:

```text
JetsonRepository
JetsonApplication
```

Update Manifest.

Build.

---

## Step 5 — ViewModel + modern Device UI

Add:

```text
DeviceListUiState
DeviceListViewModel
ConnectionStatusCard
DeviceCard
DeviceListScreen
SignalStrength
```

Build and run.

Verify unnamed filtering.

---

## Step 6 — GATT

Add:

```text
JetsonGattSpec
ConnectionState
BleGattClient
```

Integrate with repository.

Build.

---

## Step 7 — navigation/dashboard

Add:

```text
JetsonApp
DashboardUiState
DashboardViewModel
DashboardScreen
MetricCard
```

Build.

---

## Step 8 — shrink MainActivity

Replace old implementation with the small Activity in this document.

Build and test permissions.

---

## Step 9 — protocol

Add:

```text
JetsonCommand
CommandCodec
```

Wire dashboard buttons to Repository.

---

## Step 10 — do NOT fake Jetson status

Until Jetson notification data exists:

- default status values may display zero/stopped
- do not randomly animate fake CPU/GPU values
- do not invent a successful command response

Real device state must come from the Jetson GATT server.

---

# 41. Migration rule for current working code

The current prototype is already known to scan successfully.

Therefore:

**Do not delete the old implementation before the replacement layer builds.**

Recommended migration strategy:

```text
working MainActivity
        ↓
extract BleScanner
        ↓ build
extract repository
        ↓ build
extract ViewModel
        ↓ build
replace UI
        ↓ build
add GATT
        ↓ build
finally simplify MainActivity
```

If a regression occurs, compare against the last working scan behavior.

---

# 42. Things the AI agent must NOT do

Do not:

- replace BLE with Bluetooth Classic
- require Wi-Fi for initial discovery
- require internet
- add a cloud backend
- add Firebase
- add account login
- add Hilt unless requested
- add database unless required
- show unnamed BLE devices
- identify Jetson solely by human-readable device name
- expose arbitrary shell command execution
- fake connection success
- fake Jetson telemetry
- disable permission checks
- re-add fine-location permission without a concrete reason
- start indefinite high-latency/low-latency scanning in background
- put all code back into MainActivity

---

# 43. Final architecture diagram

```text
┌───────────────────────────────────────┐
│ Android UI                            │
│                                       │
│ DeviceListScreen     DashboardScreen  │
└───────────────┬───────────────────────┘
                │
                ▼
┌───────────────────────────────────────┐
│ ViewModels                            │
│                                       │
│ DeviceListVM        DashboardVM       │
└───────────────┬───────────────────────┘
                │
                ▼
┌───────────────────────────────────────┐
│ JetsonRepository                      │
└───────────────┬───────────────────────┘
                │
        ┌───────┴────────┐
        ▼                ▼
┌───────────────┐  ┌──────────────────┐
│ BleScanner    │  │ BleGattClient    │
│               │  │                  │
│ scan results  │  │ connect          │
│ RSSI          │  │ discover         │
│ device names  │  │ read/write       │
└───────┬───────┘  │ notify           │
        │          └─────────┬────────┘
        │                    │
        └──────────┬─────────┘
                   │
                   ▼
             Android BLE
                   │
                   ▼
        ┌─────────────────────┐
        │ Jetson BLE Server   │
        │                     │
        │ Control Service     │
        │ Command             │
        │ Status              │
        │ System Info         │
        │ Wi-Fi Provisioning  │
        └──────────┬──────────┘
                   │
                   ▼
        Jetson Control Daemon
                   │
       ┌───────────┼────────────┐
       ▼           ▼            ▼
     Docker       ROS        Hardware
                              Camera
                              LiDAR
                              GNSS
```

---

# 44. Definition of done for the current Android milestone

The current milestone is complete when:

```text
App launch
   ↓
Nearby devices permission
   ↓
Modern DeviceListScreen
   ↓
Scan
   ↓
ONLY named BLE devices displayed
   ↓
RSSI sorted
   ↓
Connect
   ↓
GATT service discovery
   ↓
Jetson service validation
```

The next milestone after this document is:

```text
Jetson Linux
   ↓
BlueZ GATT server
   ↓
Jetson Control Service
   ↓
systemd auto start
   ↓
Android identifies actual Jetson
```

---

# 45. Instruction to the coding agent

When executing this specification:

1. Read the current source before modifying it.
2. Preserve currently working BLE scanning.
3. Do not make unrelated refactors.
4. Apply changes incrementally.
5. Build after every major extraction.
6. Fix compile errors before continuing.
7. Do not silently remove working permissions.
8. Do not use fake data to claim a feature works.
9. Keep user-facing Korean strings readable.
10. When finished, report:
    - files created
    - files modified
    - build result
    - remaining TODOs
    - exact next Jetson-side implementation step

The priority order is:

```text
Correctness
> BLE reliability
> maintainable architecture
> UX polish
> extra features
```
