# Wi-Fi Direct Setup and Operation

이 문서는 Android 앱이 공유기 없이 Jetson Local Control API에 연결하도록 Wi-Fi Direct backend를 설치하고 운영하는 절차다.

## 1. 동작 구조

```text
Android WifiP2pManager
  |  discoverPeers + WPS PBC + groupOwnerIntent=0
  v
Jetson wpa_supplicant
  |  GO negotiation request signal
  v
jetson-wifi-direct.service
  |  요청한 Android peer로 NetworkManager wifi-p2p 연결 활성화
  v
p2p-wlan0-* (Jetson Group Owner, 192.168.49.1/24)
  |  NetworkManager shared IPv4/DHCP
  `-- pinned HTTPS + HMAC API :8765
```

Jetson이 부팅되자마자 영구 P2P group을 만들지는 않는다. 서비스는 discovery를 켜고 Android 요청을 기다린다. 요청을 받으면 그 peer를 지정한 임시 NetworkManager profile을 만들고 PBC negotiation을 시작한다. Android의 GO intent는 0이고 NetworkManager 1.22의 Jetson intent는 7이므로 Jetson이 Group Owner가 된다.

이 방식은 NetworkManager가 관리 중인 `wlan0`에서 외부 `p2p_group_add`로 만든 group을 즉시 제거하는 문제를 피한다. 일반 Wi-Fi `wlan0` 연결은 유지하며, P2P profile에는 `ipv4.never-default=yes`를 적용해 인터넷 기본 경로를 바꾸지 않는다.

## 2. 사전 조건

동시 interface 지원을 확인한다.

```bash
iw phy phy0 info | sed -n '/valid interface combinations:/,/HT Capability/p'
nmcli --version
```

필요한 조합은 `managed` 1개, `P2P-GO` 1개, `P2P-device` 1개다. 현재 확인한 Intel AC 9260은 총 3 interface와 최대 2 channel 조합을 지원한다.

## 3. 자동 설치

새 Jetson 전체 설치:

```bash
sudo backend/scripts/bootstrap-jetson.sh \
  --device-name MMS-JETSON-01 \
  --pipeline-user <user> \
  --enable-power \
  --depthai-repo /home/<user>/geo_multifusion_sensors \
  --depthai-venv /home/<user>/geo_multifusion_sensors/.venv
```

기존 장비 backend 업데이트:

```bash
sudo backend/scripts/install.sh \
  --pipeline-user <user> \
  --enable-power \
  --storage-root /home/<user>/26_camera_record \
  --enable-wifi-direct
```

두 script는 `jetson-wifi-direct.service`를 설치하고 부팅 시 자동 시작한다. P2P를 지원하지 않는 adapter에서만 `--disable-wifi-direct`를 사용한다. `bootstrap-jetson.sh`는 `wpasupplicant`, `NetworkManager`, `python3-dbus`, `python3-gi`, `iw`, `iproute2`, `dnsmasq-base`를 준비한다. `dnsmasq-base`는 NetworkManager의 shared IPv4 DHCP에 사용된다.

## 4. 장비 설정

`/etc/jetson-control/device.json`의 관련 값:

```json
{
  "wifi_interface": "wlan0",
  "wifi_direct_enabled": true,
  "wifi_direct_frequency": 2412,
  "wifi_direct_address": "192.168.49.1/24"
}
```

- P2P device name은 `device_name`을 UTF-8 32 bytes 이내로 줄여 사용한다.
- 기본 channel은 2.4 GHz channel 1인 `2412` MHz다.
- Android 앱은 주소를 고정 추측하지 않고 `WifiP2pInfo.groupOwnerAddress`를 사용한다.
- profile은 연결 중에만 존재하며 디스크에 영구 저장하지 않는다.

## 5. 상태 의미

상태 파일은 `/run/jetson-control/wifi-direct.json`이다.

| 상태 | 의미 |
|---|---|
| `STARTING` | wpa_supplicant와 NetworkManager 준비 중 |
| `DISCOVERABLE` | 정상 대기, Android 연결 요청을 받을 수 있음 |
| `CONNECTING` | 요청한 Android peer와 PBC negotiation 중 |
| `READY` | Jetson GO interface, IPv4, DHCP가 준비됨 |
| `ERROR` | 명령 또는 D-Bus 처리 실패; journal 확인 필요 |
| `DISABLED` | 장비 설정에서 비활성 |
| `STOPPED` | 서비스가 정상 종료됨 |

연결 전 정상 예시:

```json
{"state":"DISCOVERABLE","managementInterface":"p2p-dev-wlan0","address":"192.168.49.1"}
```

연결 후 정상 예시:

```json
{"state":"READY","groupInterface":"p2p-wlan0-0","address":"192.168.49.1"}
```

## 6. Android 연결 순서

1. 앱에서 QR을 스캔하고 BLE challenge-response 인증을 완료한다.
2. 최초 등록이면 앱이 바로 Wi-Fi 설정 화면을 열며, 필요한 경우 일반 공유기 Wi-Fi를 BLE로 설정한다.
3. 공유기 없이 제어할 때 연결 허브의 `Wi-Fi Direct`를 연다.
4. 주변 기기 권한을 허용하고 Android 위치 서비스를 켠다.
5. Jetson 장비를 선택한다. 앱은 WPS PBC와 `groupOwnerIntent=0`으로 연결한다.
6. 앱은 Group Owner 주소의 `https://<address>:8765/v1/hello`를 검사한다.
7. QR secret 기반 인증서 proof와 요청·응답 HMAC 검증 후 Dashboard를 연다.

Wi-Fi Direct association만으로 장비 제어 권한을 부여하지 않는다. QR로 등록된 장비 ID와 secret이 없으면 API 연결은 거부된다.

## 7. 점검

연결 전:

```bash
systemctl is-enabled jetson-wifi-direct.service
systemctl is-active jetson-wifi-direct.service
cat /run/jetson-control/wifi-direct.json
nmcli -t -f DEVICE,TYPE,STATE device status
sudo /opt/jetson-control/doctor.sh
```

`DISCOVERABLE`은 정상 상태다. Android 연결 후에는 다음을 추가 확인한다.

```bash
interface="$(python3 -c 'import json; print(json.load(open("/run/jetson-control/wifi-direct.json"))["groupInterface"])')"
address="$(python3 -c 'import json; print(json.load(open("/run/jetson-control/wifi-direct.json"))["address"])')"
iw dev
ip -4 address show dev "${interface}"
curl --fail --insecure --interface "${interface}" "https://${address}:8765/v1/hello"
```

상세 로그:

```bash
journalctl -u jetson-wifi-direct.service -n 150 --no-pager
journalctl -u NetworkManager.service -n 150 --no-pager
```

## 8. 장애 대응

### 앱에 Jetson이 보이지 않음

- 상태가 `DISCOVERABLE`인지 확인한다.
- Android Wi-Fi, 위치 서비스, 주변 기기 권한을 확인한다.
- `p2p-dev-wlan0`가 NetworkManager에서 `wifi-p2p`로 보여야 한다.
- 오래된 Android P2P group을 해제하고 다시 검색한다.

### `CONNECTING` 뒤 다시 `DISCOVERABLE`로 돌아옴

- `journalctl -u jetson-wifi-direct.service`에서 `nmcli connection up` 실패 이유를 본다.
- Android가 PBC가 아닌 다른 WPS 방식을 요청하면 backend가 거부한다.
- 무선 adapter의 동시 interface/channel 제한을 확인한다.

### 연결은 됐지만 API가 실패함

- 상태가 `READY`이고 group interface에 상태 파일의 IPv4가 있는지 확인한다.
- API가 `0.0.0.0:8765`에 listen하는지 `ss -lnt`로 확인한다.
- 앱에 해당 장비의 QR credential이 남아 있는지 확인한다.
- 인증서나 QR secret을 임의로 재생성하지 않는다.

## 9. 보안 경계

- P2P profile은 인터넷 forwarding이나 NAT를 제공하지 않는다.
- P2P 연결은 기존 LAN/모바일 인터넷 기본 경로를 대체하지 않는다.
- Local API는 pinned TLS proof와 양방향 HMAC 없이는 사용할 수 없다.
- 저장소 경로, upload token, pipeline 명령은 인증되지 않은 endpoint에 노출하지 않는다.

참고:

- [Android Wi-Fi Direct 공식 개요](https://developer.android.com/develop/connectivity/wifi/wifip2p)
- [Android WifiP2pManager API](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager)
- [NetworkManager wifi-p2p 설정](https://networkmanager.dev/docs/api/latest/settings-wifi-p2p.html)
- [wpa_supplicant P2P control interface](https://w1.fi/wpa_supplicant/devel/p2p.html)
