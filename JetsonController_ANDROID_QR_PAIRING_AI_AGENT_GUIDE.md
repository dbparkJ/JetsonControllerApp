# JetsonController Android — QR Pairing & BLE Authentication AI Agent Guide

> **Scope**
>
> Android Studio project only.
>
> DO NOT implement or modify Jetson/Linux/BlueZ code in this task.
>
> The existing Android project has already been refactored into:
>
> - Jetpack Compose + Material 3
> - `BleScanner`
> - `BleGattClient`
> - `JetsonRepository`
> - ViewModels + `StateFlow`
> - Device list / Dashboard
> - Jetson GATT service UUID + command protocol
>
> Preserve the current working BLE implementation and extend it with:
>
> 1. QR device registration
> 2. Jetson-only BLE Service UUID filtering
> 3. Device ID verification
> 4. HMAC challenge-response authentication
> 5. Authenticated navigation to Dashboard
> 6. Status notification decoding
>
> Package:
>
> ```text
> com.example.jetsoncontroller
> ```
>
> Current Android target is Android 12+ / API 31+.
>
> Build after every major phase. Do not continue on compilation failure.

---

# 1. Required user experience

The production onboarding flow must be:

```text
App
 ↓
장비 추가
 ↓
QR 코드 스캔
 ↓
QR parser
 ↓
PairingInfo(deviceId, bootstrapSecret)
 ↓
Jetson BLE scan
 ↓
SERVICE_UUID candidate
 ↓
connect
 ↓
discover services
 ↓
read DEVICE_ID
 ↓
QR deviceId == GATT deviceId ?
 ├─ NO → disconnect + error
 └─ YES
      ↓
read AUTH_CHALLENGE
      ↓
HMAC-SHA256
      ↓
write AUTH_RESPONSE
      ↓
read AUTH_STATE
      ↓
authenticated
      ↓
enable STATUS notify
      ↓
Dashboard
```

Do NOT navigate to Dashboard merely because GATT service discovery succeeds.

Dashboard access requires successful authentication.

---

# 2. QR data format

The Jetson QR will contain a custom URI:

```text
jetsonctl://pair?v=1&id=<UUID>&key=<64_HEX_CHARS>
```

Example shape only:

```text
jetsonctl://pair?v=1&id=8f84cba8-5a64-4e16-9c1d-d2f14478a82f&key=<64 hex chars>
```

Do NOT hardcode a real secret in source control.

Fields:

```text
v
protocol version

id
physical Jetson device UUID

key
32-byte bootstrap secret encoded as 64 hexadecimal characters
```

The QR secret is sensitive.

Never:

- print it to Logcat
- include it in analytics
- include it in exceptions shown to user
- persist it as plaintext SharedPreferences
- show it in UI

---

# 3. GATT UUID extension

Extend the existing `JetsonGattSpec.kt`.

Keep existing values:

```text
SERVICE_UUID
a1000000-0000-0000-0000-000000000001

COMMAND_UUID
a1000000-0000-0000-0000-000000000002

STATUS_UUID
a1000000-0000-0000-0000-000000000003

SYSTEM_INFO_UUID
a1000000-0000-0000-0000-000000000004

WIFI_CONFIG_UUID
a1000000-0000-0000-0000-000000000005
```

Add:

```kotlin
val DEVICE_ID_UUID: UUID =
    UUID.fromString(
        "a1000000-0000-0000-0000-000000000006"
    )

val AUTH_CHALLENGE_UUID: UUID =
    UUID.fromString(
        "a1000000-0000-0000-0000-000000000007"
    )

val AUTH_RESPONSE_UUID: UUID =
    UUID.fromString(
        "a1000000-0000-0000-0000-000000000008"
    )

val AUTH_STATE_UUID: UUID =
    UUID.fromString(
        "a1000000-0000-0000-0000-000000000009"
    )
```

---

# 4. Dependencies

Inspect the current Gradle structure first.

If the project uses `libs.versions.toml`, integrate there.

Do not rewrite Gradle structure unnecessarily.

Use the bundled ML Kit barcode model so QR recognition is immediately available offline after app installation.

Current known compatible choices:

```kotlin
implementation("com.google.mlkit:barcode-scanning:17.3.0")

implementation("androidx.camera:camera-core:1.6.1")
implementation("androidx.camera:camera-camera2:1.6.1")
implementation("androidx.camera:camera-lifecycle:1.6.1")
implementation("androidx.camera:camera-view:1.6.1")
```

Also preserve the existing Compose/Lifecycle/Navigation dependencies.

Do NOT add Firebase.

Do NOT use a cloud QR API.

---

# 5. Manifest additions

Preserve the currently working Bluetooth permissions.

Add camera permission:

```xml
<uses-permission
    android:name="android.permission.CAMERA" />

<uses-feature
    android:name="android.hardware.camera"
    android:required="false" />
```

Existing Bluetooth section should remain equivalent to:

```xml
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="true" />

<uses-permission
    android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />

<uses-permission
    android:name="android.permission.BLUETOOTH_CONNECT" />
```

Do not re-add location permission.

---

# 6. New package structure

Add:

```text
model/
└── PairingInfo.kt

protocol/
├── PairingQrParser.kt
├── PairingAuth.kt
├── StatusCodec.kt
└── UuidCodec.kt

ui/pairing/
├── PairingUiState.kt
├── PairingViewModel.kt
├── PairingScreen.kt
└── QrScannerScreen.kt
```

Modify:

```text
data/bluetooth/BleScanner.kt
data/bluetooth/BleGattClient.kt
data/bluetooth/JetsonGattSpec.kt
data/repository/JetsonRepository.kt
ui/JetsonApp.kt
MainActivity.kt
```

---

# 7. Pairing model

Create:

`model/PairingInfo.kt`

```kotlin
package com.example.jetsoncontroller.model

data class PairingInfo(
    val version: Int,
    val deviceId: String,
    val bootstrapSecretHex: String
) {

    val shortId: String
        get() =
            deviceId
                .replace("-", "")
                .takeLast(4)
                .uppercase()

    val expectedBleName: String
        get() = "MMS-$shortId"
}
```

IMPORTANT:

Do NOT log this data class because generated `toString()` contains the secret.

Never write:

```kotlin
Log.d(TAG, "$pairingInfo")
```

---

# 8. QR parser

Create:

`protocol/PairingQrParser.kt`

```kotlin
package com.example.jetsoncontroller.protocol

import android.net.Uri
import com.example.jetsoncontroller.model.PairingInfo
import java.util.UUID

object PairingQrParser {

    fun parse(
        rawValue: String
    ): PairingInfo {

        val uri =
            Uri.parse(
                rawValue.trim()
            )

        require(
            uri.scheme == "jetsonctl"
        ) {
            "지원하지 않는 QR 코드입니다."
        }

        require(
            uri.host == "pair"
        ) {
            "지원하지 않는 QR 코드입니다."
        }

        val version =
            uri.getQueryParameter("v")
                ?.toIntOrNull()
                ?: throw IllegalArgumentException(
                    "QR 버전 정보가 없습니다."
                )

        require(version == 1) {
            "지원하지 않는 QR 버전입니다."
        }

        val deviceId =
            uri.getQueryParameter("id")
                ?: throw IllegalArgumentException(
                    "장비 ID가 없습니다."
                )

        val normalizedId =
            UUID.fromString(deviceId)
                .toString()
                .lowercase()

        val secret =
            uri.getQueryParameter("key")
                ?: throw IllegalArgumentException(
                    "장비 인증 정보가 없습니다."
                )

        require(
            secret.matches(
                Regex("^[0-9a-fA-F]{64}$")
            )
        ) {
            "장비 인증 정보 형식이 올바르지 않습니다."
        }

        return PairingInfo(
            version = version,
            deviceId = normalizedId,
            bootstrapSecretHex =
                secret.lowercase()
        )
    }
}
```

---

# 9. UUID binary codec

Jetson will expose `DEVICE_ID` as raw 16-byte UUID.

Create:

`protocol/UuidCodec.kt`

```kotlin
package com.example.jetsoncontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object UuidCodec {

    fun fromBytes(
        value: ByteArray
    ): UUID {

        require(value.size == 16) {
            "DEVICE_ID must be exactly 16 bytes."
        }

        val buffer =
            ByteBuffer
                .wrap(value)
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        return UUID(
            buffer.long,
            buffer.long
        )
    }


    fun toBytes(
        uuid: UUID
    ): ByteArray {

        return ByteBuffer
            .allocate(16)
            .order(
                ByteOrder.BIG_ENDIAN
            )
            .putLong(
                uuid.mostSignificantBits
            )
            .putLong(
                uuid.leastSignificantBits
            )
            .array()
    }
}
```

---

# 10. Authentication algorithm

Protocol:

```text
Challenge length:
16 bytes

QR bootstrap secret:
32 bytes

Response:
first 16 bytes of HMAC-SHA256
```

HMAC message must be byte-for-byte identical on Android and Jetson:

```text
UTF-8 bytes of:
JETSONCTRL1|

then:
16 raw DEVICE_ID UUID bytes

then:
ASCII |

then:
16 challenge bytes
```

Create:

`protocol/PairingAuth.kt`

```kotlin
package com.example.jetsoncontroller.protocol

import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PairingAuth {

    private val CONTEXT =
        "JETSONCTRL1|"
            .toByteArray(
                Charsets.UTF_8
            )

    private val SEPARATOR =
        byteArrayOf(
            '|'.code.toByte()
        )


    fun computeResponse(
        deviceId: String,
        bootstrapSecretHex: String,
        challenge: ByteArray
    ): ByteArray {

        require(
            challenge.size == 16
        ) {
            "Invalid authentication challenge."
        }

        val secret =
            hexToBytes(
                bootstrapSecretHex
            )

        require(
            secret.size == 32
        )

        val deviceIdBytes =
            UuidCodec.toBytes(
                UUID.fromString(
                    deviceId
                )
            )

        val message =
            CONTEXT +
            deviceIdBytes +
            SEPARATOR +
            challenge

        val mac =
            Mac.getInstance(
                "HmacSHA256"
            )

        mac.init(
            SecretKeySpec(
                secret,
                "HmacSHA256"
            )
        )

        return mac
            .doFinal(message)
            .copyOfRange(
                0,
                16
            )
    }


    private fun hexToBytes(
        hex: String
    ): ByteArray {

        require(
            hex.length % 2 == 0
        )

        return ByteArray(
            hex.length / 2
        ) {
            index ->

            hex.substring(
                index * 2,
                index * 2 + 2
            )
                .toInt(16)
                .toByte()
        }
    }
}
```

---

# 11. Pairing state machine

Create:

`ui/pairing/PairingUiState.kt`

```kotlin
package com.example.jetsoncontroller.ui.pairing

import com.example.jetsoncontroller.model.PairingInfo

enum class PairingPhase {
    IDLE,
    QR_SCANNED,
    SEARCHING,
    CONNECTING,
    VERIFYING_IDENTITY,
    AUTHENTICATING,
    ENABLING_STATUS,
    READY,
    ERROR
}

data class PairingUiState(

    val phase:
        PairingPhase =
        PairingPhase.IDLE,

    val pairingInfo:
        PairingInfo? =
        null,

    val displayDeviceName:
        String? =
        null,

    val message:
        String =
        "Jetson 본체의 QR 코드를 스캔하세요.",

    val errorMessage:
        String? =
        null
)
```

---

# 12. BLE scan filter

The project previously scanned all named BLE devices during development.

Once the Jetson server advertises the Control Service UUID, change production scanning to service UUID filtering.

`BleScanner.kt` should use:

```kotlin
val filter =
    ScanFilter
        .Builder()
        .setServiceUuid(
            ParcelUuid(
                JetsonGattSpec
                    .SERVICE_UUID
            )
        )
        .build()
```

And:

```kotlin
bleScanner.startScan(
    listOf(filter),
    settings,
    scanCallback
)
```

Still preserve:

```kotlin
if (name.isNullOrBlank()) {
    return
}
```

The service UUID is the machine filter.

The name is only the human-readable label.

---

# 13. QR candidate matching

After QR scanning:

```text
deviceId
8f84...a82f

expectedBleName
MMS-A82F
```

The app may prioritize a BLE candidate with:

```kotlin
device.name.equals(
    pairingInfo.expectedBleName,
    ignoreCase = true
)
```

However:

**DO NOT treat name matching as authentication.**

Final identity check is the binary `DEVICE_ID` characteristic.

---

# 14. BLE GATT operations must be serialized

Android BLE GATT operations are asynchronous.

Do NOT issue:

```text
read DEVICE_ID
read AUTH_CHALLENGE
write AUTH_RESPONSE
read AUTH_STATE
enable notify
```

at the same time.

Use a state machine / queued operation sequence.

Required order:

```text
service discovered
 ↓
read DEVICE_ID
 ↓ callback
validate
 ↓
read AUTH_CHALLENGE
 ↓ callback
compute HMAC
 ↓
write AUTH_RESPONSE
 ↓ callback
read AUTH_STATE
 ↓ callback
enable STATUS notification
 ↓ callback
READY
```

---

# 15. GATT client pairing session

Extend `BleGattClient`.

Create a private session model:

```kotlin
private data class PairingSession(
    val info: PairingInfo,
    val displayName: String
)
```

Keep:

```kotlin
private var pairingSession:
    PairingSession? = null
```

Add pairing callbacks as `StateFlow` or one immutable state flow.

Recommended state:

```kotlin
sealed interface BlePairingState {

    data object Idle :
        BlePairingState

    data object Connecting :
        BlePairingState

    data object DiscoveringServices :
        BlePairingState

    data object VerifyingIdentity :
        BlePairingState

    data object Authenticating :
        BlePairingState

    data object EnablingNotifications :
        BlePairingState

    data class Ready(
        val deviceName: String
    ) : BlePairingState

    data class Error(
        val userMessage: String,
        val technicalCode: String? = null
    ) : BlePairingState
}
```

Do not expose QR secret from any state.

---

# 16. Pairing connect API

Add:

```kotlin
fun connectForPairing(
    device: BluetoothDevice,
    displayName: String,
    pairingInfo: PairingInfo
)
```

It must:

```text
disconnect old connection
store PairingSession
set Connecting
connectGatt(... TRANSPORT_LE)
```

When GATT connects:

```text
discoverServices()
```

When services discovered:

```text
SERVICE_UUID exists?
 ├─ NO → fail
 └─ YES
      ↓
DEVICE_ID characteristic exists?
AUTH_CHALLENGE exists?
AUTH_RESPONSE exists?
AUTH_STATE exists?
STATUS exists?
      ↓
read DEVICE_ID
```

---

# 17. DEVICE_ID callback

When `DEVICE_ID_UUID` is read:

```kotlin
val actualDeviceId =
    UuidCodec
        .fromBytes(value)
        .toString()
        .lowercase()
```

Compare with QR:

```kotlin
val expected =
    pairingSession
        ?.info
        ?.deviceId
        ?: return fail(...)
```

If mismatch:

```text
disconnect immediately
do not send HMAC
show:
"QR 코드와 연결된 Jetson이 일치하지 않습니다."
```

If match:

```text
read AUTH_CHALLENGE
```

---

# 18. AUTH_CHALLENGE callback

Validate:

```kotlin
require(value.size == 16)
```

Calculate:

```kotlin
val response =
    PairingAuth.computeResponse(
        deviceId =
            session.info.deviceId,
        bootstrapSecretHex =
            session.info.bootstrapSecretHex,
        challenge =
            value
    )
```

Then write exactly 16 bytes to:

```text
AUTH_RESPONSE_UUID
```

Do not log `response`.

---

# 19. AUTH_RESPONSE write callback

When characteristic write succeeds:

```text
read AUTH_STATE
```

If write fails:

```text
pairing error
disconnect
```

---

# 20. AUTH_STATE callback

Expected:

```text
1 byte
```

Meaning:

```text
0x00
authentication failed

0x01
authenticated
```

If:

```kotlin
value.contentEquals(
    byteArrayOf(1)
)
```

then enable status notifications.

Otherwise:

```text
disconnect
show:
"장비 인증에 실패했습니다."
```

---

# 21. Status notification enabling

After authentication only:

```kotlin
gatt.setCharacteristicNotification(
    statusCharacteristic,
    true
)
```

Then write CCCD:

```text
00002902-0000-1000-8000-00805f9b34fb
```

value:

```kotlin
BluetoothGattDescriptor
    .ENABLE_NOTIFICATION_VALUE
```

When descriptor write succeeds:

```text
BlePairingState.Ready
```

Only at this point:

```text
navigate Dashboard
```

---

# 22. Status binary packet

Jetson sends 14 bytes:

```text
Offset   Type         Meaning

0        uint8        protocol version
1        uint8        CPU percent
2        uint8        GPU percent
3        int8         temperature °C
4        uint8        storage percent
5        uint8        service flags
6..9     uint32 LE    RAM used MB
10..13   uint32 LE    RAM total MB
```

Flags:

```text
bit 0
Camera running

bit 1
LiDAR running

bit 2
GNSS running

bit 3
MMS running
```

---

# 23. Status decoder

Create:

`protocol/StatusCodec.kt`

```kotlin
package com.example.jetsoncontroller.protocol

import com.example.jetsoncontroller.model.JetsonStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder

object StatusCodec {

    fun decode(
        data: ByteArray
    ): JetsonStatus {

        require(
            data.size >= 14
        ) {
            "Invalid Jetson status packet."
        }

        val buffer =
            ByteBuffer
                .wrap(data)
                .order(
                    ByteOrder
                        .LITTLE_ENDIAN
                )

        val version =
            buffer.get()
                .toInt() and 0xFF

        require(version == 1) {
            "Unsupported status packet version."
        }

        val cpu =
            buffer.get()
                .toInt() and 0xFF

        val gpu =
            buffer.get()
                .toInt() and 0xFF

        val temperature =
            buffer.get()
                .toInt()

        val storage =
            buffer.get()
                .toInt() and 0xFF

        val flags =
            buffer.get()
                .toInt() and 0xFF

        val ramUsed =
            buffer.int

        val ramTotal =
            buffer.int

        return JetsonStatus(
            cpuPercent =
                cpu.coerceIn(0, 100),

            gpuPercent =
                gpu.coerceIn(0, 100),

            ramUsedMb =
                ramUsed.coerceAtLeast(0),

            ramTotalMb =
                ramTotal.coerceAtLeast(0),

            temperatureC =
                temperature.toFloat(),

            storagePercent =
                storage.coerceIn(0, 100),

            cameraRunning =
                flags and 0x01 != 0,

            lidarRunning =
                flags and 0x02 != 0,

            gnssRunning =
                flags and 0x04 != 0,

            mmsRunning =
                flags and 0x08 != 0
        )
    }
}
```

When `STATUS_UUID` notification arrives:

```text
decode
 ↓
Repository status StateFlow
 ↓
DashboardViewModel
 ↓
Compose
```

Do not fake metrics.

---

# 24. QR Scanner implementation

Use:

```text
CameraX PreviewView
+
CameraX ImageAnalysis
+
bundled ML Kit Barcode Scanning
```

Only scan:

```text
Barcode.FORMAT_QR_CODE
```

Scanner:

```kotlin
val barcodeOptions =
    BarcodeScannerOptions
        .Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE
        )
        .build()

val barcodeScanner =
    BarcodeScanning
        .getClient(
            barcodeOptions
        )
```

---

# 25. QR analyzer concurrency

Do not process unlimited frames simultaneously.

Use an atomic guard:

```kotlin
private val analyzing =
    AtomicBoolean(false)
```

Pseudo-flow:

```kotlin
if (
    !analyzing.compareAndSet(
        false,
        true
    )
) {
    imageProxy.close()
    return
}
```

After ML Kit processing:

```kotlin
.addOnCompleteListener {
    analyzing.set(false)
    imageProxy.close()
}
```

Once one valid Jetson QR has been accepted, use another guard:

```kotlin
AtomicBoolean(false)
```

to prevent multiple navigation callbacks from consecutive frames.

---

# 26. QrScannerScreen requirements

Screen should look like a real onboarding flow.

Example:

```text
← 장비 추가

Jetson QR 코드를 스캔하세요

┌────────────────────────────┐
│                            │
│      ┌──────────────┐      │
│      │              │      │
│      │     QR       │      │
│      │              │      │
│      └──────────────┘      │
│                            │
└────────────────────────────┘

Jetson 본체의 QR 코드를
사각형 안에 맞춰주세요.
```

Requirements:

- camera preview full enough for comfortable scanning
- overlay / framing UI
- back button
- permission denied state
- invalid QR should not crash
- non-Jetson QR should be ignored or show subtle feedback
- after valid QR, stop accepting new QR results

---

# 27. Pairing confirmation UI

After valid QR:

```text
장비를 찾을 준비가 됐습니다

MMS-A82F

장비 ID
•••• A82F

[ 연결 및 인증 ]
[ 취소 ]
```

Never display the bootstrap secret.

When connecting:

```text
Jetson 찾는 중...
 ↓
연결 중...
 ↓
장비 확인 중...
 ↓
인증 중...
 ↓
상태 동기화 중...
 ↓
연결 완료
```

Use a single progress screen/card.

Do not expose:

```text
GATT
HMAC
UUID characteristic
```

to normal user-facing text.

---

# 28. Pairing ViewModel

The ViewModel owns:

```text
PairingInfo
PairingPhase
user-facing message
pairing actions
```

Repository owns Bluetooth / GATT.

Recommended actions:

```kotlin
fun onQrScanned(
    rawValue: String
)

fun startPairing()

fun cancelPairing()

fun retry()
```

`onQrScanned()`:

```text
PairingQrParser.parse
 ↓
PairingInfo stored in memory
 ↓
QR_SCANNED
```

`startPairing()`:

```text
repository.startPairing(pairingInfo)
```

---

# 29. Repository API

Add APIs similar to:

```kotlin
fun startPairing(
    pairingInfo: PairingInfo
)

fun cancelPairing()

val pairingState:
    StateFlow<BlePairingState>
```

`startPairing()` should:

1. stop previous scan
2. start SERVICE_UUID-filtered scan
3. prioritize expected BLE name
4. connect candidate
5. let GATT state machine verify `DEVICE_ID`
6. authenticate
7. emit Ready

Do NOT put HMAC code in a Composable.

---

# 30. Candidate-selection timeout

Pairing scan must not run forever.

Recommended:

```text
15 seconds
```

If no Jetson candidate:

```text
"Jetson을 찾지 못했습니다.
장비 전원이 켜져 있는지 확인하세요."
```

Provide:

```text
[ 다시 시도 ]
```

---

# 31. Navigation changes

Recommended root flow:

```text
devices
pair/qr
pair/confirm
pair/progress
dashboard
```

The existing manual device-list screen may remain as a development / fallback path.

Production main CTA:

```text
QR 코드로 장비 추가
```

Dashboard navigation condition must become:

```text
authenticated Ready
```

not merely:

```text
GATT service discovered
```

---

# 32. MainActivity responsibility

Keep `MainActivity` small.

It may own runtime permission launchers for:

```text
BLUETOOTH_SCAN
BLUETOOTH_CONNECT
CAMERA
```

Alternatively camera permission may be scoped to QR route.

Do NOT move:

- QR parsing
- HMAC
- GATT callbacks
- scan matching

into MainActivity.

---

# 33. Bluetooth state restoration

If Bluetooth turns off during pairing:

```text
cancel scan
disconnect GATT
PairingState.Error
```

User message:

```text
Bluetooth가 꺼졌습니다.
Bluetooth를 켠 뒤 다시 시도하세요.
```

---

# 34. Connection-loss behavior

If Jetson disconnects on Dashboard:

```text
Repository:
Disconnected

Dashboard:
연결 끊김

Actions disabled
```

Provide:

```text
[ 다시 연결 ]
[ 장비 목록으로 ]
```

Do not continue sending commands to stale GATT.

---

# 35. Sensitive operations

For:

```text
REBOOT
SHUTDOWN
```

add confirmation dialogs.

Example:

```text
Jetson을 재부팅할까요?

현재 실행 중인 작업이 중단될 수 있습니다.

[ 취소 ] [ 재부팅 ]
```

Do not add confirmation to ordinary status refresh.

---

# 36. Secret persistence — current milestone

For the first integration milestone:

```text
Keep PairingInfo only in memory.
```

This is intentional.

After app process death, user scans QR again.

Do NOT rush plaintext persistence.

Future milestone:

```text
Android Keystore
+
encrypted DataStore
```

for paired-device credentials.

---

# 37. Logging policy

Allowed:

```text
device display name
RSSI
connection state
GATT error code
service discovery result
device ID suffix
```

Forbidden:

```text
bootstrap secret
full QR URI
HMAC response
Wi-Fi password
authentication key
```

Do not log raw QR value.

---

# 38. Tests

Create unit tests for protocol code.

## PairingQrParser tests

- valid v1 URI
- wrong scheme
- wrong host
- invalid UUID
- missing key
- short key
- non-hex key

## PairingAuth tests

Use fixed test vectors.

Given:

```text
known device UUID
known 32-byte secret
known 16-byte challenge
```

the result must match the Jetson implementation exactly.

IMPORTANT:

Create one shared documented test vector once Jetson code is running.

## StatusCodec tests

- valid packet
- wrong version
- short packet
- service flag mapping
- signed temperature

---

# 39. Acceptance criteria

The Android implementation is complete when:

## QR

- [ ] bundled offline QR scanner works
- [ ] only `jetsonctl://pair` is accepted
- [ ] malformed QR never crashes
- [ ] secret never appears in UI/logcat
- [ ] QR duplicate-frame navigation is prevented

## BLE scan

- [ ] production scan filters by Jetson SERVICE_UUID
- [ ] unnamed devices remain hidden
- [ ] unrelated watches / earbuds / TVs do not appear
- [ ] 15-second timeout works

## Pairing

- [ ] candidate connects
- [ ] DEVICE_ID is read
- [ ] wrong device ID is rejected
- [ ] 16-byte challenge is read
- [ ] HMAC response is generated
- [ ] response is written
- [ ] AUTH_STATE is checked
- [ ] failure disconnects
- [ ] success enables STATUS notifications
- [ ] Dashboard opens only after authenticated Ready

## Status

- [ ] 14-byte status packets decode
- [ ] Repository StateFlow updates
- [ ] Dashboard updates
- [ ] no fake telemetry

## Build

- [ ] `assembleDebug` succeeds
- [ ] app installs on physical Galaxy device

---

# 40. AI Agent implementation order

Do not implement everything in one unverified edit.

## Phase 1
Add:

```text
PairingInfo
PairingQrParser
UuidCodec
PairingAuth
StatusCodec
```

Add unit tests.

Build.

---

## Phase 2
Add camera / ML Kit dependencies and camera permission.

Build.

---

## Phase 3
Implement `QrScannerScreen`.

Test physical QR scanning.

Build.

---

## Phase 4
Add Pairing UI + PairingViewModel.

No GATT auth yet.

Verify QR → confirmation navigation.

Build.

---

## Phase 5
Extend `JetsonGattSpec`.

Extend `BleGattClient` with the serialized pairing state machine.

Build.

---

## Phase 6
Add Repository pairing APIs.

Wire PairingViewModel to Repository.

Build.

---

## Phase 7
Change production BLE scan to SERVICE_UUID filter.

Do NOT remove manual debug scan code until Jetson advertising has been verified.

Build.

---

## Phase 8
Enable Status notification decode.

Wire to Dashboard.

Build.

---

## Phase 9
Change Dashboard navigation condition to authenticated `Ready`.

Add disconnect/reconnect UX.

Build and test on physical device.

---

# 41. Important migration rule

The Jetson server may not yet be running while Android work is implemented.

Therefore:

- compile protocol code first
- test QR parser locally
- test QR camera locally
- keep existing BLE scanner fallback available
- do NOT fake successful GATT authentication

If Jetson characteristics do not exist yet, the expected runtime result is an understandable pairing error.

That is preferable to fake success.

---

# 42. Final instruction to AI coding agent

Before editing:

1. inspect current files
2. inspect existing GATT callback implementation
3. inspect navigation routes
4. inspect version catalog / Gradle style
5. inspect current theme / permissions

Then perform the phases above.

Do not rewrite already-working architecture unnecessarily.

At completion report:

```text
Created files
Modified files
Gradle changes
Manifest changes
Unit-test result
assembleDebug result
Physical-device QR test result
BLE pairing state reached
Remaining Jetson-dependent TODO
```

Priority:

```text
security correctness
>
BLE state-machine correctness
>
no regressions
>
UX
>
extra features
```
