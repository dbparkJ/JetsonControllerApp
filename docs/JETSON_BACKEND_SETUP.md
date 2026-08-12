# Jetson Controller backend contract

Android 앱의 BLE, 같은 LAN, Wi-Fi Direct, 재부팅 기능이 실제로 동작하려면 Jetson에 아래 기능을 제공하는 제어 데몬이 필요하다.

## 1. 권장 구성

```text
Android app
  ├─ BLE GATT (등록, 상태, Wi-Fi 설정, 제한 명령)
  └─ HTTPS/HTTP local API :8765 (상태, 파일, 업로드, 제한 명령)
                         ↓
                 jetson-control daemon
                  ├─ command allow-list
                  ├─ NetworkManager adapter
                  ├─ systemd adapter
                  └─ status collector
```

제어 데몬은 임의 셸 문자열을 실행하면 안 된다. 앱이 보낸 명령을 고정된 allow-list에 매핑한다.

## 2. 같은 LAN에서 장비 검색

Jetson은 mDNS/DNS-SD로 `_jetsonctl._tcp` 서비스를 광고해야 한다. Ubuntu/Jetson Linux에서는 Avahi를 사용할 수 있다.

`/etc/avahi/services/jetson-control.service`:

```xml
<?xml version="1.0" standalone="no"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name replace-wildcards="yes">MMS-JETSON-%h</name>
  <service>
    <type>_jetsonctl._tcp</type>
    <port>8765</port>
    <txt-record>id=00000000-0000-0000-0000-000000000000</txt-record>
    <txt-record>api=1</txt-record>
  </service>
</service-group>
```

`id`에는 BLE `DEVICE_ID` 특성과 QR에 넣은 것과 동일한 UUID를 사용한다. 적용:

```bash
sudo apt install avahi-daemon
sudo systemctl enable --now avahi-daemon
sudo systemctl restart avahi-daemon
avahi-browse -rt _jetsonctl._tcp
```

공유기의 AP isolation/client isolation이 켜져 있거나 휴대기기와 Jetson이 서로 다른 VLAN이면 mDNS와 API 연결이 차단될 수 있다.

## 3. Local Control API

데몬은 LAN 및 Wi-Fi Direct 인터페이스에서 TCP 8765를 수신한다.

필수 엔드포인트:

| Method | Path | 설명 |
|---|---|---|
| GET | `/v1/hello` | API 버전, 장비 ID/이름, 부팅 nonce 반환 |
| GET | `/v1/status` | CPU/GPU/온도/저장공간/서비스 상태 |
| POST | `/v1/commands/start-system` | MMS 서비스 시작 |
| POST | `/v1/commands/stop-system` | MMS 서비스 중지 |
| POST | `/v1/commands/restart-services` | 관리 대상 서비스 재시작 |
| POST | `/v1/commands/reboot` | OS 재부팅 |
| POST | `/v1/commands/shutdown` | OS 종료 |

`/v1/hello` 예시:

```json
{
  "apiVersion": 1,
  "deviceId": "00000000-0000-0000-0000-000000000000",
  "deviceName": "MMS-JETSON-0000",
  "bootNonce": "random-per-boot-value"
}
```

`/v1/hello`를 제외한 요청은 앱의 `HttpAuthSigner`와 동일한 HMAC-SHA256 검증을 수행한다. `X-Device-Id`, `X-Request-Nonce`, `X-Signature`를 검사하고 request nonce 재사용을 거부한다. 부팅 nonce는 재부팅할 때마다 바꾼다.

## 4. 재부팅 구현

HTTP handler 안에서 직접 임의 명령을 조립하지 않는다. 인증과 권한을 확인한 뒤 고정 배열을 `shell=False`로 실행하고, HTTP 응답이 전송된 다음 재부팅한다.

```python
ALLOWED_ACTIONS = {
    "start-system": ["sudo", "/usr/bin/systemctl", "start", "mms.target"],
    "stop-system": ["sudo", "/usr/bin/systemctl", "stop", "mms.target"],
    "restart-services": ["sudo", "/usr/bin/systemctl", "restart", "mms.target"],
    "reboot": ["sudo", "/usr/bin/systemctl", "reboot"],
    "shutdown": ["sudo", "/usr/bin/systemctl", "poweroff"],
}
```

`/etc/sudoers.d/jetson-control`에는 데몬 사용자에게 위 명령만 허용한다. 경로와 인자를 정확히 제한하고 `ALL` 또는 임의 셸 실행 권한을 주지 않는다.

```text
jetsonctl ALL=(root) NOPASSWD: /usr/bin/systemctl start mms.target
jetsonctl ALL=(root) NOPASSWD: /usr/bin/systemctl stop mms.target
jetsonctl ALL=(root) NOPASSWD: /usr/bin/systemctl restart mms.target
jetsonctl ALL=(root) NOPASSWD: /usr/bin/systemctl reboot
jetsonctl ALL=(root) NOPASSWD: /usr/bin/systemctl poweroff
```

BLE에서는 command frame의 command ID `0x04`를 같은 `reboot` action에 매핑한다. 인증이 완료되지 않은 BLE 세션의 재부팅 요청은 거부한다.

## 5. Wi-Fi 프로비저닝

Android 앱은 인증된 BLE command `SET_WIFI (0x07)`의 payload로 다음 바이트를 보낸다.

```text
version(1) | flags(1) | ssidLength(1) | passwordLength(1)
| ssid(UTF-8) | password(UTF-8)
```

- version: `1`
- flags bit 0: hidden SSID
- SSID: 1~32 bytes
- password: open network이면 0 bytes, 아니면 8~63 bytes

Jetson은 길이를 먼저 검증하고 비밀번호를 로그에 남기지 않는다. NetworkManager D-Bus API 사용을 권장하며, `nmcli`를 사용해야 한다면 문자열 셸 조합 없이 인자 배열로 실행한다.

```python
args = ["nmcli", "device", "wifi", "connect", ssid]
if password:
    args += ["password", password]
if hidden:
    args += ["hidden", "yes"]
subprocess.run(args, shell=False, check=True, timeout=30)
```

연결 결과는 BLE status notification 또는 별도 provisioning result characteristic으로 앱에 돌려주는 것이 좋다.

## 6. Wi-Fi Direct

Jetson 무선 인터페이스는 `wpa_supplicant` P2P 설정으로 peer/group owner 대기 상태여야 한다. Android가 peer를 찾은 뒤 group owner IP의 `:8765/v1/hello`에 접근할 수 있어야 한다.

확인 항목:

```bash
iw list | sed -n '/Supported interface modes:/,/Band/p'
wpa_cli -i wlan0 p2p_find
wpa_cli -i wlan0 p2p_peers
ss -lntp | grep 8765
```

## 7. 배포 체크

- `jetson-control.service`를 systemd로 자동 시작
- API는 로컬 LAN/P2P 인터페이스에만 노출
- 방화벽에서 TCP 8765와 mDNS UDP 5353 허용
- QR secret과 Wi-Fi 비밀번호를 로그에 기록하지 않음
- API request nonce 재사용 방지 및 요청 시간 제한
- 재부팅/종료 전 응답 flush와 짧은 지연 적용
- BLE/HTTP 모두 동일한 command allow-list 사용
