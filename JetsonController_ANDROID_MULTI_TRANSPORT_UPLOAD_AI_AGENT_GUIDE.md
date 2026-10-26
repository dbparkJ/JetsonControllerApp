# JetsonController Android — Multi-Transport Control, Wi-Fi Direct, LAN, File Browser & Upload
## AI Coding Agent Implementation Specification

> Scope: Android Studio project ONLY.
>
> Do NOT implement Jetson/Linux/server code in this task.
>
> The existing project already has:
>
> - Kotlin
> - Jetpack Compose + Material 3
> - BLE scanning
> - BLE GATT client
> - Repository/ViewModel architecture
> - QR pairing
> - HMAC device authentication
> - Dashboard
> - command/status protocol
>
> Preserve all existing working functionality.
>
> This milestone adds:
>
> 1. Manual BLE connection entry
> 2. QR connection entry
> 3. Wi-Fi Direct connection/control
> 4. Same-router LAN discovery/control
> 5. Wi-Fi provisioning UI
> 6. Jetson directory browser
> 7. Folder selection
> 8. Jetson → server upload job control
> 9. Upload progress/history UI
> 10. Transport abstraction so BLE/IP transports do not leak into Compose

---

# 1. Conceptual distinction

The app has **three user-visible ways to enter/connect**:

```text
BLE 직접 연결
QR로 연결
Wi-Fi Direct 연결
```

But QR is NOT itself a network transport.

QR is:

```text
identity
+
bootstrap authentication
+
automatic device selection
```

Actual control transport can be:

```text
BLE
Wi-Fi Direct IP
same-router LAN IP
```

The architecture must reflect that distinction.

---

# 2. Target user experience

Main connection screen:

```text
Jetson Control

장비에 연결

┌──────────────────────────────┐
│ Bluetooth로 연결             │
│ 주변 Jetson을 직접 검색합니다 │
│                       [연결] │
└──────────────────────────────┘

┌──────────────────────────────┐
│ QR 코드로 연결               │
│ 본체 QR로 안전하게 등록합니다 │
│                       [스캔] │
└──────────────────────────────┘

┌──────────────────────────────┐
│ Wi-Fi Direct로 연결          │
│ 공유기 없이 고속으로 연결합니다│
│                       [연결] │
└──────────────────────────────┘

같은 네트워크의 등록된 장비
MMS-A82F             ● LAN 연결 가능
```

---

# 3. Important pairing behavior

## QR route

First-time secure registration should normally use QR.

```text
QR
 ↓
deviceId + bootstrap secret
 ↓
BLE identity/authentication
 ↓
credential saved securely
```

## Manual BLE route

Manual BLE connection is always available.

For a device already registered:

```text
BLE scan
 ↓
device selected
 ↓
stored credential
 ↓
authenticate
 ↓
Dashboard
```

For a device NOT registered:

```text
BLE scan
 ↓
device selected
 ↓
DEVICE_ID readable
 ↓
credential missing
 ↓
show:
"이 장비는 아직 등록되지 않았습니다."
 ↓
[QR로 인증]
```

Do NOT silently bypass authentication for manual BLE.

---

# 4. Transport architecture

Create a transport abstraction.

Recommended:

```text
                           ┌──────────────┐
                           │ Compose UI   │
                           └──────┬───────┘
                                  │
                           ViewModel/Repo
                                  │
                       TransportCoordinator
                           /       |       \
                          /        |        \
                         ▼         ▼         ▼
                 BleTransport  P2pTransport LanTransport
                      │             │          │
                      BLE           HTTP       HTTP
                      GATT          API        API
```

File browsing and upload job management require an IP transport:

```text
Wi-Fi Direct
or
LAN
```

BLE should remain for:

- discovery
- pairing
- authentication
- status fallback
- small control commands
- network bootstrap

Do NOT attempt directory browsing or data transfer over BLE.

---

# 5. New package structure

Add:

```text
data/
├── network/
│   ├── WifiDirectManager.kt
│   ├── LanDiscoveryManager.kt
│   ├── LocalControlApi.kt
│   ├── LocalApiClient.kt
│   ├── HttpAuthSigner.kt
│   └── NetworkModels.kt
│
├── transport/
│   ├── ControlTransport.kt
│   ├── BleControlTransport.kt
│   ├── IpControlTransport.kt
│   ├── TransportCoordinator.kt
│   └── TransportState.kt
│
└── credentials/
    └── DeviceCredentialStore.kt

model/
├── DeviceEndpoint.kt
├── RemoteFileEntry.kt
├── RemoteRoot.kt
├── UploadJob.kt
├── UploadTarget.kt
└── WifiProvisionRequest.kt

ui/
├── connection/
│   ├── ConnectionHubScreen.kt
│   └── ConnectionHubViewModel.kt
│
├── wifi/
│   ├── WifiDirectScreen.kt
│   ├── WifiDirectViewModel.kt
│   ├── WifiProvisionScreen.kt
│   └── WifiProvisionViewModel.kt
│
├── storage/
│   ├── DeviceStorageScreen.kt
│   ├── DeviceStorageViewModel.kt
│   └── FolderPickerScreen.kt
│
└── upload/
    ├── UploadConfirmScreen.kt
    ├── UploadProgressScreen.kt
    ├── UploadHistoryScreen.kt
    └── UploadViewModel.kt
```

Modify:

```text
MainActivity.kt
AndroidManifest.xml
JetsonApp.kt
JetsonRepository.kt
BleScanner.kt
BleGattClient.kt
DashboardScreen.kt
```

---

# 6. Android permissions

Preserve existing BLE and CAMERA permissions.

Add Wi-Fi Direct / local network permissions.

Manifest should contain equivalent entries:

```xml
<uses-permission
    android:name="android.permission.INTERNET" />

<uses-permission
    android:name="android.permission.ACCESS_NETWORK_STATE" />

<uses-permission
    android:name="android.permission.CHANGE_NETWORK_STATE" />

<uses-permission
    android:name="android.permission.ACCESS_WIFI_STATE" />

<uses-permission
    android:name="android.permission.CHANGE_WIFI_STATE" />

<uses-permission
    android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" />

<uses-permission
    android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="32" />
```

If project target SDK is 37 or higher, also declare/request:

```xml
<uses-permission
    android:name="android.permission.ACCESS_LOCAL_NETWORK" />
```

The agent MUST inspect current compileSdk/targetSdk before editing.

Do not remove:

```xml
BLUETOOTH_SCAN
BLUETOOTH_CONNECT
CAMERA
```

---

# 7. Runtime permission grouping

Create a clear permission policy.

Bluetooth:

```text
BLUETOOTH_SCAN
BLUETOOTH_CONNECT
```

Wi-Fi Direct, Android 13+:

```text
NEARBY_WIFI_DEVICES
```

Android 12 / 12L compatibility:

```text
ACCESS_FINE_LOCATION
```

Local TCP/mDNS if targetSdk >= 37:

```text
ACCESS_LOCAL_NETWORK
```

Camera:

```text
CAMERA
```

Do not request every permission at app launch.

Request just-in-time:

```text
BLE button
→ Bluetooth permissions

QR button
→ Camera + Bluetooth permissions

Wi-Fi Direct button
→ Nearby Wi-Fi permission

LAN/file browser
→ Local-network permission if required
```

---

# 8. Device credentials must now persist securely

The prior QR milestone may have kept the bootstrap secret only in memory.

This milestone requires reconnect through BLE/LAN/Wi-Fi Direct after app restart.

Therefore implement:

```text
DeviceCredentialStore
```

Store:

```text
deviceId
deviceName
encrypted bootstrap secret
date paired
optional last endpoint
```

Do NOT store secret as plaintext SharedPreferences.

Recommended implementation:

```text
Android Keystore AES/GCM key
+
DataStore stores:
IV + ciphertext
```

Do not introduce a deprecated crypto abstraction merely to avoid using Android Keystore.

Suggested domain model:

```kotlin
data class StoredDeviceCredential(
    val deviceId: String,
    val deviceName: String,
    val secretCiphertextBase64: String,
    val secretIvBase64: String
)
```

Only decrypted secret should exist temporarily in memory when authenticating/signing.

Never log it.

---

# 9. Transport state

Create:

`data/transport/TransportState.kt`

```kotlin
package com.example.jetsoncontroller.data.transport

enum class TransportType {
    BLE,
    WIFI_DIRECT,
    LAN
}

sealed interface TransportState {

    data object Disconnected :
        TransportState

    data class Connecting(
        val type: TransportType
    ) : TransportState

    data class Connected(
        val type: TransportType,
        val endpoint: String? = null
    ) : TransportState

    data class Error(
        val type: TransportType?,
        val message: String
    ) : TransportState
}
```

---

# 10. Transport capabilities

Not every transport supports the same operations.

Create:

```kotlin
data class TransportCapabilities(
    val control: Boolean,
    val status: Boolean,
    val networkProvisioning: Boolean,
    val fileBrowsing: Boolean,
    val uploadControl: Boolean
)
```

BLE:

```text
control              YES
status               YES
networkProvisioning  optional
fileBrowsing         NO
uploadControl        NO
```

Wi-Fi Direct:

```text
control              YES
status               YES
networkProvisioning  YES
fileBrowsing         YES
uploadControl        YES
```

LAN:

```text
control              YES
status               YES
networkProvisioning  YES
fileBrowsing         YES
uploadControl        YES
```

---

# 11. ControlTransport interface

Create:

`data/transport/ControlTransport.kt`

```kotlin
package com.example.jetsoncontroller.data.transport

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.protocol.JetsonCommand

interface ControlTransport {

    val type: TransportType

    val capabilities:
        TransportCapabilities

    suspend fun ping(): Boolean

    suspend fun getStatus():
        Result<JetsonStatus>

    suspend fun sendCommand(
        command: JetsonCommand,
        payload: ByteArray = byteArrayOf()
    ): Result<Unit>

    suspend fun disconnect()
}
```

Do not put Compose dependencies here.

---

# 12. TransportCoordinator

Create:

`data/transport/TransportCoordinator.kt`

Responsibilities:

```text
track active transport
switch transport
prefer fastest available transport
fallback to BLE if IP transport disappears
```

Recommended priority:

```text
LAN
>
Wi-Fi Direct
>
BLE
```

But do NOT automatically switch while a destructive command is being sent.

Provide:

```kotlin
suspend fun activate(
    type: TransportType
)

fun currentTransport():
    ControlTransport?

val state:
    StateFlow<TransportState>
```

When Wi-Fi Direct or LAN is connected:

```text
Dashboard uses IP transport
```

If IP disappears:

```text
try BLE fallback
```

Display transport badge:

```text
● LAN
● Wi-Fi Direct
● Bluetooth
```

---

# 13. Wi-Fi Direct manager

Use Android platform:

```text
WifiP2pManager
```

Create:

`data/network/WifiDirectManager.kt`

Responsibilities:

```text
initialize WifiP2pManager
register P2P broadcast receiver
discover peers
request peers
connect
request connection info
disconnect/remove group
```

State:

```kotlin
data class WifiDirectState(
    val enabled: Boolean = false,
    val discovering: Boolean = false,
    val peers: List<WifiDirectPeer> = emptyList(),
    val connected: Boolean = false,
    val groupOwnerAddress: String? = null,
    val error: String? = null
)
```

Peer model:

```kotlin
data class WifiDirectPeer(
    val name: String,
    val deviceAddress: String,
    val status: Int
)
```

---

# 14. Wi-Fi Direct connection policy

The Jetson side will be configured to strongly prefer being Group Owner.

Android should prefer being client.

When building configuration:

```kotlin
val config =
    WifiP2pConfig().apply {
        deviceAddress =
            peer.deviceAddress

        groupOwnerIntent =
            WifiP2pConfig
                .GROUP_OWNER_INTENT_MIN

        wps.setup =
            WpsInfo.PBC
    }
```

After connection:

```kotlin
manager.requestConnectionInfo(
    channel
) { info ->

    if (
        info.groupFormed &&
        info.groupOwnerAddress != null
    ) {

        val jetsonIp =
            info.groupOwnerAddress
                .hostAddress
    }
}
```

The Jetson API endpoint is then:

```text
http://<groupOwnerAddress>:8765
```

for the initial development implementation.

Do not hardcode `192.168.49.1`.

Use the address returned by the framework.

---

# 15. Wi-Fi Direct receiver

Handle at minimum:

```text
WIFI_P2P_STATE_CHANGED_ACTION
WIFI_P2P_PEERS_CHANGED_ACTION
WIFI_P2P_CONNECTION_CHANGED_ACTION
WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
```

Register/unregister safely with app lifecycle.

Do not leak an Activity reference.

Use application context where practical.

---

# 16. Wi-Fi Direct filtering

During development, list all peers.

Production UI should prioritize:

```text
MMS-XXXX
```

but peer name is not security identity.

After IP API is reachable:

```text
GET /v1/hello
```

must return `device_id`.

Compare that with stored/QR device ID.

If different:

```text
disconnect P2P
reject endpoint
```

---

# 17. LAN discovery

Jetson will advertise mDNS/DNS-SD:

```text
service type:
_jetsonctl._tcp.

instance:
MMS-A82F

TXT:
id=<device UUID>
api=1
```

Create:

`data/network/LanDiscoveryManager.kt`

Use:

```text
NsdManager
```

Discovery must filter:

```text
_jetsonctl._tcp.
```

When service resolves, obtain:

```text
host address
port
TXT device id
```

Match device ID against a registered device.

Do not trust service name only.

---

# 18. LAN endpoint model

Create:

`model/DeviceEndpoint.kt`

```kotlin
package com.example.jetsoncontroller.model

enum class EndpointTransport {
    WIFI_DIRECT,
    LAN
}

data class DeviceEndpoint(
    val deviceId: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val transport:
        EndpointTransport
)
```

---

# 19. Local Jetson API contract

The Android app expects this local API.

Base:

```text
http://JETSON_IP:8765
```

API version:

```text
/v1
```

Public identity endpoint:

```text
GET /v1/hello
```

Response:

```json
{
  "apiVersion": 1,
  "deviceId": "uuid",
  "deviceName": "MMS-A82F",
  "bootNonce": "32-hex"
}
```

Authenticated endpoints:

```text
GET  /v1/status

POST /v1/commands/start
POST /v1/commands/stop
POST /v1/commands/restart
POST /v1/commands/reboot
POST /v1/commands/shutdown

POST /v1/network/wifi

GET  /v1/fs/roots
GET  /v1/fs/list

GET  /v1/upload/targets
POST /v1/uploads
GET  /v1/uploads
GET  /v1/uploads/{jobId}
POST /v1/uploads/{jobId}/cancel
```

---

# 20. Local API request authentication

For the initial private-network implementation, use request HMAC.

Every authenticated request includes:

```text
X-Device-Id
X-Request-Nonce
X-Signature
```

First call:

```text
GET /v1/hello
```

obtain:

```text
bootNonce
```

Canonical string:

```text
JETSONHTTP1
<deviceId>
<bootNonce>
<requestNonce>
<METHOD>
<encoded path + query>
<SHA256 body hex>
```

Signature:

```text
HMAC-SHA256(
    bootstrapSecret,
    canonicalUtf8
)
```

encoded as lowercase hex.

Generate new random 16-byte request nonce per request.

Do NOT send the bootstrap secret.

Create:

`data/network/HttpAuthSigner.kt`

Core API:

```kotlin
data class SignedHeaders(
    val deviceId: String,
    val requestNonce: String,
    val signature: String
)

fun sign(
    secret: ByteArray,
    deviceId: String,
    bootNonce: String,
    method: String,
    canonicalPath: String,
    body: ByteArray
): SignedHeaders
```

Do not log signature/secret.

NOTE:

This provides request authentication/integrity but not payload confidentiality.

Production hardening should move the local API to TLS.

Do not enable cleartext for unrelated internet hosts.

---

# 21. Local API client

Create:

`data/network/LocalApiClient.kt`

Use the project's existing HTTP client if one exists.

Otherwise use a mainstream Android HTTP client and isolate it behind this class.

Do not let ViewModels construct HTTP requests.

Responsibilities:

```text
hello
status
commands
wifi provisioning
directory roots/list
upload targets
upload start/status/cancel
```

Always:

1. call/cached `hello`
2. validate deviceId
3. sign authenticated requests
4. deserialize response
5. map network errors to domain errors

---

# 22. Wi-Fi provisioning

User should be able to connect Jetson to an infrastructure router.

UI:

```text
공유기 연결

SSID
[ office_wifi ]

비밀번호
[ ••••••••••• ]

숨김 네트워크
[ ]

[ Jetson을 이 Wi-Fi에 연결 ]
```

App sends:

```text
POST /v1/network/wifi
```

Body:

```json
{
  "ssid": "office_wifi",
  "password": "secret",
  "hidden": false
}
```

Never log password.

Never persist password unless explicitly required.

After request succeeds:

```text
Wi-Fi Direct may disconnect
 ↓
show "공유기 연결 중"
 ↓
start LAN mDNS discovery
 ↓
find matching deviceId
 ↓
switch transport to LAN
```

---

# 23. Wi-Fi provisioning status

Expected response:

```json
{
  "accepted": true,
  "message": "connecting"
}
```

LAN discovery timeout:

```text
60 seconds
```

If not found:

```text
"공유기 연결 명령은 전달됐지만
같은 네트워크에서 Jetson을 찾지 못했습니다."
```

Provide:

```text
[ 다시 찾기 ]
[ Bluetooth로 확인 ]
```

---

# 24. Directory browser security model

The Android app must NOT request arbitrary Linux paths like:

```text
/etc
/root
/home
```

Jetson exposes allowlisted logical roots.

Example:

```json
[
  {
    "id": "collections",
    "label": "수집 데이터",
    "pathHint": "/data/collections"
  },
  {
    "id": "archive",
    "label": "보관 데이터",
    "pathHint": "/data/archive"
  }
]
```

App only uses:

```text
rootId
+
relative path
```

Never sends raw absolute paths for upload.

---

# 25. File-browser models

Create:

`model/RemoteRoot.kt`

```kotlin
data class RemoteRoot(
    val id: String,
    val label: String,
    val pathHint: String?
)
```

Create:

`model/RemoteFileEntry.kt`

```kotlin
enum class RemoteEntryType {
    DIRECTORY,
    FILE
}

data class RemoteFileEntry(
    val name: String,
    val relativePath: String,
    val type: RemoteEntryType,
    val sizeBytes: Long?,
    val modifiedAt: String?
)
```

Do not calculate recursive directory size in every list call.

Directory size may be null.

---

# 26. File API

Roots:

```text
GET /v1/fs/roots
```

List:

```text
GET /v1/fs/list?root=collections&path=Job_20260811
```

Response:

```json
{
  "root": "collections",
  "path": "Job_20260811",
  "entries": [
    {
      "name": "Track01",
      "relativePath": "Job_20260811/Track01",
      "type": "DIRECTORY",
      "sizeBytes": null,
      "modifiedAt": "2026-08-11T12:00:00Z"
    }
  ]
}
```

---

# 27. DeviceStorageScreen

UI:

```text
← 수집 데이터

수집 데이터 / Job_20260811

[ .. ]

📁 Track01
📁 Track02
📁 Track03
📄 metadata.json       18 KB

────────────────────────

현재 폴더
Job_20260811

[ 이 폴더를 서버로 업로드 ]
```

Requirements:

- breadcrumb navigation
- folder-first sorting
- loading state
- empty state
- refresh
- no delete operation in this milestone
- no rename operation
- upload current folder
- optional folder-selection mode

---

# 28. Upload destination profiles

Credentials and server addresses should live on Jetson, not Android.

App fetches:

```text
GET /v1/upload/targets
```

Example:

```json
[
  {
    "id": "primary",
    "label": "메인 수집 서버"
  },
  {
    "id": "backup",
    "label": "백업 서버"
  }
]
```

Android sends only target ID.

Do NOT send server SSH password/key from the app.

---

# 29. Start upload

Request:

```text
POST /v1/uploads
```

Body:

```json
{
  "rootId": "collections",
  "relativePath": "Job_20260811",
  "targetId": "primary"
}
```

Response:

```json
{
  "jobId": "up_01J...",
  "state": "QUEUED"
}
```

---

# 30. Upload job model

Create:

`model/UploadJob.kt`

```kotlin
enum class UploadJobState {
    QUEUED,
    SCANNING,
    UPLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class UploadJob(
    val id: String,
    val rootId: String,
    val relativePath: String,
    val targetId: String,
    val state: UploadJobState,
    val bytesTotal: Long?,
    val bytesTransferred: Long?,
    val filesTotal: Int?,
    val filesTransferred: Int?,
    val currentFile: String?,
    val errorMessage: String?
)
```

---

# 31. Upload progress

Polling interval:

```text
2 seconds while screen visible
```

Endpoint:

```text
GET /v1/uploads/{jobId}
```

Display:

```text
서버 업로드 중

Job_20260811

██████████░░░░  67%

43.2 GB / 64.1 GB
238 / 351 files

현재:
Track03/lidar/000238.las

[ 업로드 취소 ]
```

Do NOT keep aggressive polling in background.

---

# 32. Upload history

Endpoint:

```text
GET /v1/uploads
```

UI:

```text
업로드 기록

✓ Job_20260810
  완료 · 128.4 GB

↑ Job_20260811
  진행 중 · 67%

! Job_20260809
  실패 · 연결 끊김
  [재시도]
```

A retry should start a NEW job referencing same logical folder.

Jetson uploader handles actual resume semantics.

---

# 33. Upload requires server connectivity

Before enabling upload button:

```text
GET /v1/upload/targets
```

and/or Jetson upload status endpoint should report availability.

If Jetson only has Wi-Fi Direct connection to phone and no route to upload server:

```text
"Jetson이 업로드 서버에 연결되어 있지 않습니다.
공유기 연결을 먼저 설정하세요."
```

Do not route multi-gigabyte data through Android phone.

---

# 34. Dashboard changes

Add sections:

```text
연결
● LAN / Wi-Fi Direct / Bluetooth

네트워크
공유기 연결됨
192.168.x.x
[ 네트워크 설정 ]

데이터
수집 데이터
[ 장치 파일 보기 ]

업로드
진행 중 1건
[ 업로드 관리 ]
```

If transport is BLE only:

```text
장치 파일 보기
```

should be disabled with explanation:

```text
"파일 탐색은 Wi-Fi Direct 또는 같은 네트워크 연결이 필요합니다."
```

---

# 35. ConnectionHubScreen

The old device list should become part of a connection hub.

Routes:

```text
connection
connection/ble
connection/qr
connection/p2p
pair/confirm
pair/progress
dashboard
wifi/provision
storage
storage/folder
upload/confirm
upload/progress/{jobId}
upload/history
```

---

# 36. Manual BLE screen

Keep existing BLE scan.

Production filter:

```text
Jetson SERVICE_UUID
```

Display only Jetson devices.

If credential exists:

```text
[ 연결 ]
```

If credential missing:

```text
[ 장비 등록 ]
```

then route to QR scanner.

---

# 37. QR screen

Keep current QR implementation.

After successful QR registration:

```text
store encrypted credential
 ↓
try BLE pairing
 ↓
Dashboard
```

Offer optional:

```text
[ Wi-Fi Direct로 고속 연결 ]
```

after authentication.

---

# 38. Wi-Fi Direct screen

UI:

```text
Wi-Fi Direct

공유기 없이 Jetson과 직접 연결합니다.

검색된 장비

MMS-A82F
신호 강함
[ 연결 ]

MMS-B923
[ 연결 ]
```

After group formed:

```text
MMS-A82F
● Wi-Fi Direct 연결됨

IP: 192.168.x.x

[ Dashboard 열기 ]
```

Validate `/v1/hello` device ID before showing connected state.

---

# 39. LAN discovery screen/state

Registered devices on same router should appear automatically:

```text
같은 네트워크

MMS-A82F
● 연결 가능

192.168.0.44

[ 연결 ]
```

Only list:

```text
registered device IDs
```

Unknown `_jetsonctl._tcp` services may be ignored or shown under an "unregistered" debug section.

---

# 40. Repository API expansion

`JetsonRepository` should expose domain-level APIs.

Example:

```kotlin
val transportState:
    StateFlow<TransportState>

val uploadJobs:
    StateFlow<List<UploadJob>>

suspend fun connectBle(
    deviceId: String
)

suspend fun connectWifiDirect(
    peer: WifiDirectPeer
)

suspend fun connectLan(
    endpoint: DeviceEndpoint
)

suspend fun provisionWifi(
    request: WifiProvisionRequest
): Result<Unit>

suspend fun getRoots():
    Result<List<RemoteRoot>>

suspend fun listDirectory(
    rootId: String,
    relativePath: String
): Result<List<RemoteFileEntry>>

suspend fun getUploadTargets():
    Result<List<UploadTarget>>

suspend fun startUpload(
    rootId: String,
    relativePath: String,
    targetId: String
): Result<UploadJob>

suspend fun getUploadJob(
    jobId: String
): Result<UploadJob>

suspend fun cancelUpload(
    jobId: String
): Result<Unit>
```

Compose must not know HTTP paths.

---

# 41. Error mapping

User-facing errors:

Wi-Fi Direct unsupported:

```text
"이 휴대폰에서는 Wi-Fi Direct를 사용할 수 없습니다."
```

Jetson not found:

```text
"Jetson을 찾지 못했습니다."
```

P2P formed but API unavailable:

```text
"Wi-Fi Direct 연결은 되었지만 Jetson 제어 서비스에 연결하지 못했습니다."
```

LAN permission denied:

```text
"같은 네트워크의 Jetson을 찾으려면 로컬 네트워크 권한이 필요합니다."
```

Upload server unavailable:

```text
"Jetson에서 업로드 서버에 연결할 수 없습니다."
```

Directory access denied:

```text
"이 경로는 장치에서 공유하도록 설정되지 않았습니다."
```

Do not expose Python exception stack traces.

---

# 42. Network lifecycle rules

Wi-Fi Direct:

- stop discovery when screen leaves foreground
- unregister broadcast receiver
- keep formed group only while actively needed
- allow explicit disconnect

NSD:

- stop discovery when no longer needed
- avoid duplicate listeners
- do not permanently cache IP addresses

LAN IP can change.

Use mDNS/device ID to rediscover.

---

# 43. File browser lifecycle

Do not preload entire directory tree.

Use lazy, one-level directory listing.

Do not recursively request thousands of files unless user enters that directory.

List calls should be cancellable when navigation changes.

---

# 44. Data transfer principle

Very important:

```text
BAD:

Jetson
 ↓ tens/hundreds of GB
Android phone
 ↓
Server
```

Do NOT implement this.

Correct:

```text
Android
  │
  │ start upload job
  ▼
Jetson ─────────────────────▶ Server
       direct data transfer
```

Android only receives:

```text
job metadata
progress
status
errors
```

---

# 45. Transport use cases

## Small control

Preferred:

```text
BLE / LAN / P2P
```

## Directory browse

Required:

```text
LAN or P2P
```

## Upload trigger/progress

Required:

```text
LAN or P2P local API
```

Actual server data path:

```text
Jetson → server network
```

## Video / logs / large previews

Future:

```text
LAN or P2P
```

Never BLE.

---

# 46. Same-router expected flow

```text
Phone
   │
   │ Wi-Fi Direct local API
   ▼
Jetson

User enters router credentials

Jetson
   │
   ├─ connect infrastructure Wi-Fi
   │
   └─ advertise _jetsonctl._tcp via mDNS

Phone
   │
   ├─ already on same router
   │
   └─ NsdManager discovers Jetson
        ↓
   validate device ID
        ↓
   TransportCoordinator switches:
   WIFI_DIRECT → LAN
```

On hardware that cannot keep P2P and infrastructure Wi-Fi concurrently, P2P may drop during this process.

The UI must handle this as an expected transition, not a fatal error.

---

# 47. Test matrix

## BLE direct

- registered Jetson connects
- unregistered Jetson requests QR
- unrelated BLE device not shown

## QR

- QR pairs device
- credential persists securely
- app restart → device remains registered

## Wi-Fi Direct

- Jetson peer discovered
- Android joins as client
- Jetson is group owner
- groupOwnerAddress obtained
- `/v1/hello` works
- wrong device ID rejected

## LAN

- Jetson joins same router
- mDNS service discovered
- correct registered device matched
- local API works

## Storage

- roots listed
- folders listed
- traversal impossible from UI
- empty folder handled
- large directory does not freeze UI

## Upload

- folder selected
- target selected
- upload job created
- progress updates
- cancel works
- server unavailable handled
- Android never transfers data payload itself

---

# 48. Build/test implementation order

Do not implement all changes in one pass.

## Phase 1 — models/interfaces

Add:

```text
TransportState
ControlTransport
DeviceEndpoint
RemoteRoot
RemoteFileEntry
UploadJob
UploadTarget
WifiProvisionRequest
```

Build.

---

## Phase 2 — secure credential persistence

Implement:

```text
DeviceCredentialStore
Android Keystore encryption
DataStore persistence
```

Migrate current QR PairingInfo into store after successful pairing.

Build + test app restart.

---

## Phase 3 — connection hub UI

Implement:

```text
BLE
QR
Wi-Fi Direct
LAN registered devices
```

No P2P functionality yet.

Build.

---

## Phase 4 — Wi-Fi Direct manager

Implement platform P2P discovery/connect/disconnect.

Build + physical test.

---

## Phase 5 — LocalControlApi

Implement:

```text
/v1/hello
HMAC signer
status
commands
```

Test against mock/local endpoint if Jetson API unavailable.

Do NOT fake successful device authentication.

---

## Phase 6 — IP transport

Implement:

```text
IpControlTransport
TransportCoordinator
```

Dashboard can switch BLE ↔ P2P.

Build.

---

## Phase 7 — LAN mDNS

Implement:

```text
NsdManager
_jetsonctl._tcp.
registered device matching
```

Build + test.

---

## Phase 8 — Wi-Fi provisioning

Implement UI + API.

Test P2P → router → LAN transition.

---

## Phase 9 — file browser

Implement roots/list/breadcrumb/folder selection.

Build + test.

---

## Phase 10 — upload jobs

Implement:

```text
target list
confirm
start
progress
cancel
history
```

Build + test.

---

# 49. Security rules

Never:

- store Wi-Fi passwords
- log Wi-Fi passwords
- log bootstrap secret
- expose arbitrary filesystem path input
- expose arbitrary shell commands
- upload via Android as proxy
- trust BLE/P2P/mDNS name as identity
- trust arbitrary local HTTP endpoint
- allow unregistered device to issue destructive commands

Always:

```text
device ID validation
+
registered secret authentication
```

before sensitive local API operations.

---

# 50. Production hardening TODO

The first implementation may use authenticated HTTP on private P2P/LAN.

Before external production deployment, migrate local API to TLS.

Preferred long-term options:

```text
product/device CA
or
mutual TLS
or
device certificate provisioned at manufacturing
```

Keep request authentication even after TLS if practical.

---

# 51. AI Agent final report

At completion report:

```text
Files created
Files modified
Manifest changes
Gradle changes
Permissions implemented
BLE direct result
QR result
Wi-Fi Direct result
LAN/mDNS result
file browser result
upload job result
assembleDebug result
physical Galaxy test result
remaining Jetson-dependent TODOs
```

Do not claim Jetson-dependent functionality succeeded if no Jetson endpoint was available.

