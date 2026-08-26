# Jetson Controller Backend Setup

이 문서는 이 저장소의 `backend/` 구현, Android 연동 계약, Jetson 배포 방법을 설명한다. 외부 업로드 수신 서버 구현은 [UPLOAD_RECEIVER_AGENT_GUIDE.md](UPLOAD_RECEIVER_AGENT_GUIDE.md)를 따른다.

## 1. 구성

```text
Android app
  |-- BLE GATT: QR 인증, 상태, Wi-Fi 설정, 제한 명령
  |-- Wi-Fi Direct: 검색 대기 + 요청 시 Jetson Group Owner/DHCP
  `-- Pinned HTTPS API :8765: 상태, 저장소, 업로드, 전원, 파이프라인
                              |
                              v
                    Jetson control backend
                      |-- NetworkManager
                      |-- systemd allow-list
                      |-- Git source snapshot + Python virtualenv
                      |-- local storage (read)
                      `-- public HTTPS upload receiver (outbound :443)
```

제어 plane은 BLE/LAN/Wi-Fi Direct를 사용한다. 실제 업로드 파일은 Jetson이 인터넷상의 HTTPS 수신 서버로 직접 보낸다. 업로드 대상과 Android 기기가 같은 LAN일 필요가 없다.

## 2. 구현 파일

| 경로 | 역할 |
|---|---|
| `backend/jetson_control/api.py` | FastAPI endpoint와 인증 적용 |
| `backend/jetson_control/auth.py` | HTTP HMAC 서명, boot nonce, replay 방지 |
| `backend/jetson_control/tls.py` | Jetson TLS 인증서 SHA-256 지문 계산 |
| `backend/jetson_control/ble.py` | BlueZ GATT, QR secret 인증, 상태/명령/Wi-Fi |
| `backend/jetson_control/ble_crypto.py` | BLE challenge 파생키와 Wi-Fi AES-GCM 복호화 |
| `backend/jetson_control/status.py` | CPU, GPU, RAM, 온도, 저장공간, 서비스 상태 |
| `backend/jetson_control/commands.py` | systemd service와 power allow-list |
| `backend/jetson_control/filesystem.py` | 허용 root 내부 탐색과 traversal 차단 |
| `backend/jetson_control/uploads.py` | 영속 작업, 외부 HTTPS 청크 전송, 재시도/취소 |
| `backend/jetson_control/network.py` | BLE/API Wi-Fi payload 검증과 NetworkManager 실행 |
| `backend/jetson_control/wifi_direct.py` | P2P 검색, peer 요청, NetworkManager GO/DHCP와 runtime 상태 |
| `backend/jetson_control/pipelines.py` | 등록된 Python 파이프라인 제어와 실행별 로그 조회 |
| `backend/jetson_control/sensor_handoff.py` | 부팅 센서 모니터와 실제 수집 작업 사이의 장치 소유권 handoff |
| `backend/jetson_control/sensor_monitor.py` | 등록 snapshot을 비기록 센서 모드로 상시 감독 |
| `backend/scripts/install.sh` | 기존 장비 ID/secret을 보존하는 설치/업데이트 |
| `backend/scripts/bootstrap-jetson.sh` | 새 Jetson의 package, BlueZ, backend, pipeline 일괄 설치 |
| `backend/scripts/install-bluez-5.55.sh` | BlueZ 5.55 검증·설치·systemd override |
| `backend/scripts/register-pipeline.py` | Git 작업 트리 스냅샷과 systemd instance 등록 |
| `backend/scripts/install-depthai-pipeline.sh` | 현재 DepthAI 수집 pipeline preset 등록 |
| `backend/scripts/run-pipeline.py` | manifest를 검증하고 venv Python 출력을 journal과 실행별 파일에 동시 기록 |
| `backend/scripts/configure-upload-target.sh` | 외부 HTTPS 대상 설정 |
| `backend/scripts/doctor.sh` | 설치 상태 점검 |
| `backend/systemd/jetson-wifi-direct.service` | Wi-Fi Direct 부팅 자동 시작과 장애 재시작 |
| `backend/systemd/jetson-sensor-monitor.service` | dataset 없이 카메라·GNSS·IMU 상태를 부팅부터 게시 |
| `backend/udev/99-jetson-controller-sensors.rules` | 배포된 EBIMU CP2102를 ModemManager probe에서 제외 |

## 3. 설치

Jetson에서 저장소 root 기준으로 실행한다.

```bash
sudo backend/scripts/install.sh \
  --device-name MMS-JETSON-01 \
  --pipeline-user jm \
  --enable-power
```

- `--enable-power`: 앱의 재부팅/종료를 활성화한다.
- `--device-name`: 새 장비의 광고 이름이다. 생략하면 UUID 기반 이름을 만든다.
- `--pipeline-user`: 카메라·serial 장치를 사용하는 Python 작업의 Linux 계정이다.
- `--storage-root`: 앱에 노출할 데이터 디렉터리다. 생략하면 `/data/collections`를 생성해 pipeline 사용자 소유로 설정한다. 이전 기본값인 `~/26_camera_record` 또는 `/var/lib/jetson-control/data`는 다음 설치 때 이 경로로 자동 전환된다. 기존 `~/26_camera_record` 데이터는 `Previous collected data` root로 함께 남고, 관리자가 직접 지정한 다른 경로는 유지된다.
- 설치기는 `storage_roots.json`의 경로만 API service의 `ReadWritePaths`로 생성한다. 서비스의 나머지 파일시스템은 계속 읽기 전용이며, storage root를 수동 변경한 뒤에는 설치기를 다시 실행해야 앱의 파일·폴더 삭제가 허용된다.
- Wi-Fi Direct는 기본 활성화된다. 무선 칩이 P2P GO를 지원하지 않는 장비만 `--disable-wifi-direct`를 사용한다.
- 기본 P2P 주파수는 2.4 GHz 채널 1인 `2412` MHz다. 현장 규격에 맞춰 `--wifi-direct-frequency <MHz>`로 바꿀 수 있다.
- 기존 `/etc/jetson-control/device.json`은 덮어쓰지 않는다. QR에 사용한 장비 UUID와 secret이 유지된다.
- 명시한 storage root를 추가하면서, 과거 설정이 사용자 홈 전체를 노출했다면 해당 항목을 제거한다.
- 외부 수신 URL과 token은 설치 과정에서 임의 값으로 만들지 않는다. 설정 전 앱에는 업로드 대상이 표시되지 않는다.

설치 결과:

```text
/opt/jetson-control/jetson_control/
/opt/jetson-control/venv/
/etc/jetson-control/device.json
/etc/jetson-control/storage_roots.json
/etc/jetson-control/upload_targets.json
/etc/jetson-control/tls.crt
/etc/jetson-control/tls.key
/var/lib/jetson-control/managed-upload-targets.json
/var/lib/jetson-control/upload-target-tokens/
/var/lib/jetson-control/upload-jobs/
/opt/jetson-pipelines/
/var/log/jetson-pipelines/<pipeline-id>/run-*.log
/etc/systemd/system/jetson-control.service
/etc/systemd/system/jetson-control-api.service
/etc/systemd/system/jetson-wifi-direct.service
/etc/systemd/system/jetson-pipeline@.service
/etc/systemd/system/jetson-sensor-monitor.service
/etc/udev/rules.d/99-jetson-controller-sensors.rules
/etc/jetson-sensor-monitor.json
```

새 Jetson에서 package와 BlueZ 5.55까지 자동 설치하는 절차는 [MULTI_JETSON_PIPELINE_DEPLOYMENT.md](MULTI_JETSON_PIPELINE_DEPLOYMENT.md)를 따른다.

## 4. 외부 업로드 설정

수신 서버가 준비된 뒤 발급받은 token 파일을 설정 script에 전달한다. script가 token을 root 전용 경로로 복사하고 원본 경로를 설정에 남기지 않는다.

2026-08-13에 이 저장소와 함께 구축한 수신기의 base URL은 `https://125-142-22-24.sslip.io`다. Token은 장비 ID마다 별도로 발급하며 다른 Jetson의 token을 재사용하지 않는다. 현재 등록된 token 파일은 다음과 같고, 원문은 문서나 명령행에 복사하지 않는다.

```text
/data/server_storage/jetson-upload-receiver/secrets/device-tokens/d606c26d-98d6-4b09-99d7-c3da7dda4de0.token
/data/server_storage/jetson-upload-receiver/secrets/device-tokens/9e7b51ac-1ca3-4f61-86c4-849704c9d137.token
```

대상 Jetson의 장비 ID와 일치하는 파일을 신뢰할 수 있는 방법으로 임시 `./receiver.token`에 전달한 뒤 다음처럼 설정한다.

```bash
sudo /opt/jetson-control/configure-upload-target.sh \
  https://125-142-22-24.sslip.io \
  ./receiver.token \
  "Operations upload server"

rm -f ./receiver.token
```

위 관리자 script 방식을 사용하면 Android 앱에 URL이나 token을 다시 입력할 필요 없이 Jetson의 target 목록에서 `Operations upload server`를 선택한다. 최신 앱의 업로드 서버 관리 화면에서 같은 URL과 token을 입력해 앱 관리 target으로 등록하는 방법도 있다. 이 경우 token은 QR secret으로 인증되고 인증서가 고정된 Local Control API 요청으로 Jetson에 전달되며, Jetson은 root 전용 파일로 저장하고 이후 API 응답으로 되돌려주지 않는다. 공인 IP가 바뀌면 `sslip.io` hostname도 바뀌므로 수신기 HTTPS와 해당 target을 함께 갱신한다. 서버의 전체 설치·점검·복구 절차는 [UPLOAD_RECEIVER_AGENT_GUIDE.md](UPLOAD_RECEIVER_AGENT_GUIDE.md)의 실제 배포 절을 따른다. 2026-08-18에는 `MMS-D137`을 이 절차로 등록하고 Jetson 자체 네트워크에서 deferred upload, complete, library preview와 서버 HDD 객체 일치까지 검증했다.

일반적인 별도 수신 서버의 예시는 다음과 같다.

```bash
sudo /opt/jetson-control/configure-upload-target.sh \
  https://uploads.example.com \
  ./receiver.token \
  "Operations cloud"
```

생성되는 `/etc/jetson-control/upload_targets.json`:

```json
{
  "external": {
    "label": "Operations cloud",
    "type": "http",
    "base_url": "https://uploads.example.com",
    "token_file": "/etc/jetson-control/upload-receiver.token",
    "verify_tls": true
  }
}
```

운영 backend는 로컬 복사 target을 앱에 노출하지 않는다. `verify_tls: false`와 `http://`는 backend 단위 테스트용 loopback receiver에서만 사용한다.

## 5. 장비 설정

`/etc/jetson-control/device.json`의 필드:

```json
{
  "device_id": "canonical-uuid",
  "device_name": "MMS-0000",
  "bootstrap_secret_hex": "64-hex-characters",
  "controlled_services": [],
  "service_flags": {
    "camera": "",
    "lidar": "",
    "gnss": "",
    "imu": "",
    "mms": ""
  },
  "allow_power_commands": true,
  "wifi_interface": "wlan0",
  "pipeline_user": "jm",
  "wifi_direct_enabled": true,
  "wifi_direct_frequency": 2412,
  "wifi_direct_address": "192.168.49.1/24"
}
```

- `bootstrap_secret_hex`는 32 bytes이며 QR, BLE 인증, HTTP HMAC에서 같은 값을 사용한다.
- secret을 로그, 문서, Git에 넣지 않는다.
- 센서 탭은 `camera`, `gnss`, `imu` unit의 활성 상태를 표시한다. 실제 unit이 정해지기 전에는 해당 `service_flags` 값을 비워 둔다.
- backend는 문자열 shell command를 받지 않고 설정에 있는 정확한 systemd unit만 실행한다.

Storage root 예시:

```json
{
  "recordings": {
    "label": "Collected data",
    "path": "/data/collections",
    "path_hint": "/data/collections"
  }
}
```

## 6. Local Control API

API는 `https://0.0.0.0:8765`에서 LAN과 Wi-Fi Direct 요청을 받는다. 설치 script가 장비별 self-signed 인증서를 만들며 Android는 평문 HTTP를 거부한다.

| Method | Path | 인증 | 역할 |
|---|---|---|---|
| `GET` | `/v1/hello` | TLS proof | API 버전, ID, 이름, boot nonce, 인증서 증명 |
| `GET` | `/v1/capabilities` | HMAC | 활성 기능 |
| `GET` | `/v1/status` | HMAC | 장비 상태 |
| `POST` | `/v1/commands/{action}` | HMAC | allow-list 명령 |
| `GET` | `/v1/fs/roots` | HMAC | 노출 storage root |
| `GET` | `/v1/fs/list?root=&path=` | HMAC | 디렉터리 목록 |
| `GET` | `/v1/fs/file?root=&path=` | HMAC | 12 MiB 이하 수집 파일 미리보기 |
| `GET` | `/v1/fs/workspaces` | HMAC | pipeline 사용자의 `~/` 작업공간 root |
| `GET` | `/v1/fs/workspace/list?root=&path=` | HMAC | 작업공간 내부 소스 선택 |
| `GET` | `/v1/upload/library/sessions?target=&offset=` | HMAC | 외부 서버 완료 upload 목록 프록시 |
| `GET` | `/v1/upload/library/files?target=&session=&path=` | HMAC | 외부 서버 upload의 가상 폴더 목록 프록시 |
| `GET` | `/v1/upload/library/file?target=&session=&path=` | HMAC | 외부 서버 파일의 12 MiB 제한 미리보기 프록시 |
| `DELETE` | `/v1/upload/library/sessions/{sessionId}?target=` | HMAC + 확인 | 외부 서버의 완료 업로드 삭제 |
| `DELETE` | `/v1/fs/entry?root=&path=` | HMAC + 확인 | 장치 저장소의 선택 파일·폴더 삭제 |
| `GET` | `/v1/upload/targets` | HMAC | 외부 업로드 대상 |
| `PUT` | `/v1/upload/targets/{id}` | HMAC | 앱 관리 HTTPS 업로드 서버 추가·수정 |
| `DELETE` | `/v1/upload/targets/{id}` | HMAC | 앱 관리 업로드 서버 삭제 |
| `POST` | `/v1/uploads` | HMAC | 업로드 작업 시작 |
| `GET` | `/v1/uploads?active=true` | HMAC | 대기·실행 중인 전송 큐 |
| `GET` | `/v1/uploads/{jobId}` | HMAC | 작업 상태 |
| `POST` | `/v1/uploads/{jobId}/cancel` | HMAC | 작업 취소 |
| `POST` | `/v1/uploads/{jobId}/retry` | HMAC | 실패 작업을 같은 ID로 재개 |
| `POST` | `/v1/network/wifi` | HMAC | Wi-Fi 연결 요청 |
| `GET` | `/v1/network/wifi/status` | HMAC | Wi-Fi 요청 상태 |
| `GET` | `/v1/network/wifi-direct/status` | HMAC | P2P 검색, 연결, Group Owner와 주소 상태 |
| `GET` | `/v1/pipelines` | HMAC | 등록 작업과 systemd 상태 |
| `POST` | `/v1/pipelines` | HMAC | storage root에서 pipeline 등록 |
| `POST` | `/v1/pipelines/{id}/{action}` | HMAC | start/stop/restart/enable/disable |
| `DELETE` | `/v1/pipelines/{id}` | HMAC | 작업 등록 해제 |
| `GET` | `/v1/pipelines/{id}/logs` | HMAC | 최근 systemd journal 로그 |
| `GET` | `/v1/pipelines/{id}/log-files` | HMAC | 실행별 로그 파일 목록과 현재 실행 여부 |
| `GET` | `/v1/pipelines/{id}/log-files/{logId}?offset=&limit=` | HMAC | 최대 128 KiB 로그 구간 증분 읽기 |
| `GET`, `PUT` | `/v1/pipelines/{id}/config` | HMAC | 현재 release의 YAML 읽기와 원자 저장 |
| `GET` | `/v1/pipelines/{id}/config/fields` | HMAC | 편집 가능한 scalar key/value와 revision 조회 |
| `PATCH` | `/v1/pipelines/{id}/config/fields` | HMAC | revision 확인 후 선택한 value만 원자 저장 |

### TLS bootstrap

앱은 첫 `/v1/hello` 요청에서 아직 인증서를 신뢰하지 않지만 민감한 body를 보내지 않는다. 응답에는 TLS 연결에서 실제 제시된 인증서의 SHA-256 지문과 다음 값이 들어간다.

```text
apiVersion, deviceId, deviceName, bootNonce, serverTimeEpochSeconds,
authScheme, tlsCertificateSha256, helloProof
```

`helloProof`는 위 필드를 `JETSONHELLO1` canonical message로 묶어 QR의 32-byte secret으로 HMAC-SHA256한 값이다. 앱은 다음 순서가 모두 성공해야만 상태나 명령 요청을 보낸다.

1. TLS peer 인증서 지문과 응답의 `tlsCertificateSha256` 일치
2. 저장된 QR secret으로 `helloProof` 검증
3. 이후 OkHttp 연결을 해당 인증서 지문에 고정
4. HMAC 인증 `/v1/status` 성공

인증서가 교체되어도 secret을 보존했다면 다음 연결의 hello proof로 새 인증서를 검증할 수 있어 QR 재발급이 필요 없다. Bootstrap trust manager는 `/v1/hello`에만 사용되고 인증 session이 없는 다른 endpoint 호출은 앱에서 차단한다.

### HTTP HMAC

Android의 `HttpAuthInterceptor`와 backend는 실제 encoded path, query, body bytes를 다음 canonical message로 서명한다.

```text
JETSONHTTP2
<lowercase-device-id>
<boot-nonce>
<request-nonce>
<request-unix-time-seconds>
<UPPERCASE-METHOD>
<raw-encoded-path-and-query>
<lowercase-sha256-of-body>
```

`HMAC-SHA256(bootstrap_secret, canonical_message)`의 lowercase hex를 `X-Signature`로 보낸다.

```text
X-Device-Id: <uuid>
X-Request-Nonce: <8..128 safe characters>
X-Request-Timestamp: <unix seconds>
X-Signature: <64 hex>
```

backend는 서버 시각 기준 120초 밖의 요청, nonce 재사용, nonce 기억 용량을 초과한 요청을 거부한다. 앱은 서명된 hello의 서버 시각으로 시계 차이를 보정한다. `/v1/hello`를 다시 호출하면 앱 interceptor가 새 boot nonce로 session을 갱신한다.

인증 endpoint의 모든 응답은 상태 코드와 정확한 body hash를 `JETSONHTTPRESP1`로 HMAC 서명하고 `X-Response-Signature`에 넣는다. 앱은 Retrofit에 body를 넘기기 전에 서명을 검증한다. TLS가 기밀성을 제공하고 양방향 HMAC이 장비 인증과 메시지 무결성을 보강한다.

## 7. 명령과 전원 제어

허용 action:

```text
start-system
stop-system
restart-services
reboot
shutdown
```

- 서비스 action은 `controlled_services`가 비어 있으면 `409`로 거부한다.
- power action은 `allow_power_commands`가 false면 `409`로 거부한다.
- 재부팅/종료는 HTTP/BLE 응답이 반환될 시간을 주기 위해 1초 뒤 `systemctl reboot|poweroff`를 실행한다.
- API와 BLE systemd service가 root로 동작하므로 별도 광범위 sudoers 규칙을 만들지 않는다.
- 운영 검증 중 실제 재부팅/종료 endpoint를 자동 호출하지 않는다. mock unit test와 앱 확인 dialog로 검증한다.

## 8. BLE GATT

### QR와 BLE 인증 흐름

선행 구현한 QR와 BLE 연결도 이 backend의 장비 identity에 통합되어 있다.

1. 최초 provision에서 UUID와 32-byte secret을 생성한다.
2. `jetsonctl://pair?v=1&id=<uuid>&key=<secret>` URI와 QR 이미지를 root 전용 상태 경로에 만든다.
3. 앱은 QR을 스캔한 뒤 secret을 Android Keystore AES-GCM으로 암호화해 저장한다.
4. BLE 연결 후 앱이 GATT challenge를 읽고 QR secret 기반 HMAC response를 쓴다.
5. 인증된 BLE session에서만 명령과 상태를 사용하고, Wi-Fi credential은 별도 AES-256-GCM envelope로 보낸다.
6. LAN 연결에서는 같은 secret으로 TLS hello proof와 요청·응답 HMAC을 검증한다.

QR URI, secret, pairing QR 파일은 인증 자격증명이다. 로그, 문서, Git, Android backup에 포함하지 않는다.

Service UUID: `a1000000-0000-0000-0000-000000000001`

| 끝자리 | Characteristic | 접근 |
|---|---|---|
| `0002` | command | 인증 후 write |
| `0003` | status | 인증 후 read/notify |
| `0004` | system info | read |
| `0005` | Wi-Fi config | 인증 후 write |
| `0006` | device UUID | read |
| `0007` | auth challenge | read |
| `0008` | auth response | write |
| `0009` | auth state | read |

Command frame:

```text
magic 0x5A | version 0x01 | command | payloadLength | payload | checksum
```

Command ID:

| ID | 명령 |
|---:|---|
| `0x01` | start system |
| `0x02` | stop system |
| `0x03` | restart services |
| `0x04` | reboot |
| `0x05` | shutdown |
| `0x06` | get status |
| `0x07` | set Wi-Fi |

Wi-Fi payload:

```text
version(1) | flags(1) | ssidLength(1) | passwordLength(1)
| ssid UTF-8 | password UTF-8
```

위 plaintext는 GATT로 직접 보내지 않는다. 인증 challenge와 QR secret으로 다음 32-byte session key를 양쪽에서 파생한다.

```text
HMAC-SHA256(secret, "JETSONBLEENC1|" || deviceUuidBytes || "|" || challenge)
```

Wi-Fi wire envelope:

```text
encryptedVersion 0x02 | randomNonce(12) | AES-256-GCM(ciphertext || tag)
AAD = "JETSONWIFI2|" || deviceUuidBytes
```

- SSID: 1~32 UTF-8 bytes
- 비밀번호: 빈 값 또는 8~63 UTF-8 bytes
- flags bit 0: hidden network
- SSID의 앞뒤 공백은 실제 이름의 일부로 보존한다.
- backend는 비밀번호를 로그나 process argument에 남기지 않고 `nmcli --ask` 표준입력으로 전달한다.
- 설치 전에 system Python의 `python3-cryptography` AESGCM 지원을 검사한다.

## 9. 저장소와 업로드 상태

- 모든 API path는 설정된 root 아래로 `resolve`한 뒤 containment를 다시 검사한다.
- symlink는 업로드 대상에서 제외한다.
- 앱에는 절대 실제 경로 대신 root ID와 상대 경로를 전달한다.
- 업로드 상태: `QUEUED`, `SCANNING`, `UPLOADING`, `COMPLETED`, `FAILED`, `CANCELLED`.
- 수신기가 `deferredFileHashes` capability를 제공하면 경로·크기 manifest로 세션을 즉시 만들고 파일 batch를 준비·전송하면서 전체 SHA-256을 확정한다.
- 구형 수신기에서는 전체 SHA-256 계산 중 `SCANNING` 상태에 준비한 byte·파일 수와 현재 파일을 갱신하고, 수신 세션이 열린 뒤 `UPLOADING` 진행률을 0부터 표시한다.
- 수신기가 `fileBatch` capability를 제공하면 최대 32 MiB/256개 파일의 오프셋을 묶어 조회·전송하고, capability가 없거나 파일이 부분 전송된 상태면 기존 파일별 offset과 4 MiB PUT으로 자동 전환한다.
- 관리자 target의 token은 Android 앱에 전달하지 않는다. 앱 관리 target은 등록/교체 요청 때만 입력받고 Local Control API 응답에는 token을 절대 포함하지 않는다.
- 앱이 등록한 서버와 token은 `/var/lib/jetson-control` 아래 root 전용 파일로 원자 저장한다. `/etc`의 관리자 대상은 앱에서 수정할 수 없다.
- 재부팅 또는 API 재시작 후 진행 중 작업은 영속 상태에서 자동 재개한다.
- 실패 작업의 retry는 같은 job ID와 receiver session을 재사용해 offset부터 이어간다.
- receiver 완료 처리에서 수 TiB 전체 SHA-256 검증을 기다릴 수 있도록 완료 응답 read timeout만 24시간이며, 세션/offset/청크 요청은 기존 60초 timeout을 유지한다.

## 10. Python 파이프라인 자동 실행

앱은 인증 후 `pipeline_user`의 홈 작업공간(`~/`) 아래에서 표준 작업 폴더 하나와
부팅 시 자동 실행 여부를 선택해 등록한다. 폴더 이름은 소문자나 숫자로 시작하고
소문자, 숫자, 점, 밑줄, 하이픈만 사용한다. 폴더 root에는 실행 가능한
`.venv/bin/python`, 일반 파일 `main.py`, `config.yaml` 또는 `config.yml` 중 정확히
하나가 있어야 한다. 앱에서 폴더를 등록하면 등록기는 수집 storage root의
`<pipeline-id>/`(기본값 `/data/collections/<pipeline-id>/`)를 출력용 쓰기
디렉터리로 만들고 `JETSON_PIPELINE_RESULTS_DIR`로 전달한다. 따라서 API와 실행
서비스는 홈 작업공간에 쓸 필요가 없고, 수집 데이터 화면에서 같은 결과 폴더를 연다.

backend는 임의 shell 문자열을 저장하지 않는다. 등록기는 Git tracked 파일과 ignore되지 않은 untracked 파일만 `/opt/jetson-pipelines/<id>/releases/`에 복사하고, commit·branch·dirty 상태를 manifest에 남긴다. `.git`, ignored dataset, cache는 실행 사본에 들어가지 않는다. `current` symlink가 활성 release를 가리키며 `jetson-pipeline@<id>.service`가 선택한 virtualenv Python으로 실행한다.

앱의 YAML 편집기는 `current` release 안의 등록된 `.yaml` 또는 `.yml` 파일만 UTF-8 텍스트로 읽고 원자 저장한다. 저장 후 작업을 재시작하면 반영된다. 출력 버튼은 등록 시 지정한 쓰기 경로가 수집 storage root 안에 있을 때 그 폴더를 바로 연다.

pipeline runner는 stdout과 stderr를 journald에 계속 보내면서 `/var/log/jetson-pipelines/<id>/`에도 기록한다. systemd가 자동 재시작할 때마다 `run-<UTC>-<pid>.log`를 새로 만들어 이전 오류 로그를 보존한다. 앱은 로그 파일 목록을 매초 확인하고 선택한 파일의 새 바이트만 최대 128 KiB씩 받아 최신 실행을 실시간으로 따라가며, 이전 실행도 목록에서 다시 열 수 있다. 디스크 보호를 위해 pipeline별 최근 20개, 전체 1 GiB, 실행 파일당 128 MiB로 제한하고 파일 한도 이후 출력은 journald에 계속 남긴다.

DepthAI preset을 등록하면 `jetson-sensor-monitor.service`가 부팅 직후 같은 snapshot을
`--monitor-only`로 실행한다. 모니터는 수집 디렉터리를 만들지 않고
`/var/lib/jetson-sensors`의 상태와 최대 1920 px 폭 JPEG 프리뷰만 갱신한다.
카메라 출력은 센서에 맞는 최대 캡처 크기를 선택하되 최대 5 FPS로 제한하고,
USB2 급 연결에서는 MJPEG으로 전송한다. 실제 수집 runner는 모바일 시간
동기화가 끝난 뒤 handoff 요청을 만들고 장치 lock을 넘겨받은 다음에만 시작한다. 수집이
중지되거나 실패하면 lock과 요청을 정리하고 부팅 모니터가 자동으로 재개된다. 강제 종료
뒤 남은 요청 파일도 advisory lock으로 판별해 자동 정리하므로 PID 재사용에 의존하지
않는다.

관리 action은 정확히 `start`, `stop`, `restart`, `enable`, `disable`만 허용한다. 등록 해제 시 unit은 중지·비활성화하고 release는 `/opt/jetson-pipelines/.archive/`로 이동해 보존한다.

Python pipeline 작성 규칙과 현재 DepthAI 등록값은 [MULTI_JETSON_PIPELINE_DEPLOYMENT.md](MULTI_JETSON_PIPELINE_DEPLOYMENT.md)에 있다.

## 11. BlueZ 5.55

현재 BLE daemon 기준은 BlueZ 5.55다. `install-bluez-5.55.sh`는 실행 파일의 `-v` 결과와 shared library 연결을 검사하고 `/usr/local/libexec/bluetooth/bluetoothd-5.55`에 설치한다. 이미 정상 binary가 있으면 재사용하며, 없으면 공식 source tarball을 고정 SHA-256으로 검증한 뒤 빌드한다. systemd override는 이 exact binary만 실행한다.

BlueZ 5.55는 Bluetooth Core spec의 표현이 아니라 Linux Bluetooth stack daemon 버전이다. 앱의 BLE 동작과 GATT protocol 버전은 별도로 관리한다.

## 12. Wi-Fi Direct

`jetson-wifi-direct.service`는 부팅 시 `wpa_supplicant` discovery를 시작하고 Android의 PBC GO negotiation 요청을 D-Bus로 기다린다. 요청을 받으면 해당 peer만 지정한 임시 NetworkManager `wifi-p2p` profile을 활성화한다. Jetson의 GO intent 7과 Android 앱의 intent 0으로 Jetson이 Group Owner가 되며, 기본값 `192.168.49.1/24`와 DHCP는 NetworkManager shared IPv4가 담당한다. API는 `0.0.0.0:8765`에 이미 bind되어 있으므로 P2P 주소에서도 같은 TLS/HMAC endpoint를 사용한다.

서비스는 `/run/jetson-control/wifi-direct.json`에 `STARTING`, `DISCOVERABLE`, `CONNECTING`, `READY`, `ERROR`, `STOPPED` 상태와 실제 group interface를 기록한다. `DISCOVERABLE`은 연결 전 정상 상태다. client가 끊어지면 임시 profile을 정리하고 discovery 상태로 돌아간다. `wlan0` infrastructure 연결과 `P2P-GO` 동시 지원 여부는 `iw phy`의 `valid interface combinations`에서 확인한다.

Android는 `WifiP2pManager.discoverPeers()`로 Jetson을 찾고 WPS PBC로 group client가 된 뒤 `WifiP2pInfo.groupOwnerAddress`의 `:8765`를 검사한다. P2P association 자체는 장비 제어 권한이 아니다. QR secret 기반 TLS proof와 요청·응답 HMAC이 성공해야 저장소, 업로드, 파이프라인, 전원 기능이 열린다.

설치·검증·장애 대응의 전체 절차는 [WIFI_DIRECT_SETUP.md](WIFI_DIRECT_SETUP.md)에 있다. 구현은 [Android Wi-Fi Direct 공식 흐름](https://developer.android.com/develop/connectivity/wifi/wifip2p)과 [wpa_supplicant P2P control interface](https://w1.fi/wpa_supplicant/devel/p2p.html)를 기준으로 한다.

## 13. mDNS

설치 script가 `/etc/avahi/services/jetson-control.service`를 장비 설정에서 생성한다.

```text
service type: _jetsonctl._tcp
port: 8765
TXT: id=<device UUID>
TXT: api=1
TXT: tls=1
```

앱은 발견한 `deviceId`가 QR로 등록된 장비인지 확인하고, TLS hello proof, `/v1/hello` ID 일치, HMAC `/v1/status`가 모두 성공한 뒤에만 연결 완료로 표시한다. 연결 시도마다 독립 API client를 사용하므로 동시에 발견된 다른 장비의 endpoint가 활성 session을 덮지 않는다.

## 14. 점검

```bash
sudo /opt/jetson-control/doctor.sh
systemctl --no-pager --full status jetson-control.service jetson-control-api.service jetson-wifi-direct.service jetson-sensor-monitor.service
systemctl --no-pager --full status 'jetson-pipeline@*.service'
journalctl -u jetson-sensor-monitor.service -n 100 --no-pager
journalctl -u jetson-control-api.service -n 100 --no-pager
curl --fail --insecure https://127.0.0.1:8765/v1/hello
```

Python test:

```bash
python3 -m venv backend/.venv
backend/.venv/bin/pip install -r backend/requirements-dev.txt
PYTHONPATH=backend backend/.venv/bin/python -m unittest discover -s backend/tests -v
```

Android test/build:

```bash
scripts/setup-mobile-build-env.sh  # 최초 1회 또는 빌드 도구 갱신 시
./gradlew testDebugUnitTest assembleDebug
```

Android SDK, Gradle 배포본/의존성, ARM64용 AAPT2 실행 도구는 Git에서 제외된
`.mobile-build/`에 재사용된다. `gradlew`는 이 저장소 내부 Gradle home을 기본으로
사용하므로 매 빌드마다 도구를 다시 설치하지 않는다.

## 15. 운영 체크리스트

- 장비 ID와 QR secret 보존
- `/etc/jetson-control/*.json` 및 token `0600`
- TLS private key `0600`, 앱 cleartext traffic 비활성
- 사용자 홈 전체가 storage root로 노출되지 않음
- TCP 8765 접근 범위를 장비용 LAN/P2P 정책으로 제한
- 연결 전 P2P status가 `DISCOVERABLE`, Android 연결 후 `READY`인지 확인
- `READY`일 때 group interface 주소가 설정값과 일치
- public receiver는 HTTPS만 허용
- receiver token rotation과 장비 폐기 절차 마련
- 업로드 중 인터넷 단절/복구와 checksum 검증
- 재부팅/종료 확인 dialog와 power flag 확인
- pipeline은 root가 아닌 지정 사용자로 실행하고 해당 사용자의 video/dialout/plugdev 권한 확인
- 부팅 센서 모니터가 active이고 `/var/lib/jetson-sensors/status.json`이 계속 갱신되는지 확인
- GNSS/IMU가 `/dev/serial/by-id`로 선택되고 Android USB 장치가 GNSS로 선택되지 않는지 확인
- EBIMU CP2102의 udev 속성에 `ID_MM_DEVICE_IGNORE=1`이 적용되는지 확인
- 센서 모니터 중 dataset이 생성되지 않으며 수집 시작/종료 때 장치 handoff가 되는지 확인
- source 변경 후 새 snapshot을 등록하고 commit/dirty 정보를 확인
- 일반 카메라/LiDAR/GNSS 서비스 탭은 실제 unit 이름이 확정될 때까지 비활성
