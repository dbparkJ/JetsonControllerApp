# Wi-Fi Direct 설치·운영

이 문서는 Android 앱이 공유기 없이 Jetson Local Control API에 연결하도록 Wi-Fi Direct backend를 설치하고 운영하는 절차다.

## 동작 구조

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

이 방식은 NetworkManager가 관리 중인 `wlan0`에서 외부 `p2p_group_add`로 만든 group을 즉시 제거하는 문제를 피한다. P2P profile에는 `ipv4.never-default=yes`를 적용한다. 일반 Wi-Fi 유지 여부는 어댑터의 동시 인터페이스 지원과 드라이버에 달려 있으며, 단일 인터페이스 방식에서는 Jetson의 공유기 연결과 서버 업로드가 중단될 수 있다. 앱은 휴대전화가 일반 Wi-Fi에 연결된 동안 자동 Direct 전환을 하지 않는다.

## 사전 조건

동시 interface 지원을 확인한다.

```bash
iw phy phy0 info | sed -n '/valid interface combinations:/,/HT Capability/p'
nmcli --version
```

필요한 조합은 `managed` 1개, `P2P-GO` 1개, `P2P-device` 1개다. 실제 지원 여부와 동시 채널 수는 대상 장비의 조회 결과로 확인한다.

## 자동 설치

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
  --enable-wifi-direct
```

두 script는 `jetson-wifi-direct.service`를 설치하고 부팅 시 자동 시작한다. P2P를 지원하지 않는 adapter에서만 `--disable-wifi-direct`를 사용한다. `bootstrap-jetson.sh`는 `wpasupplicant`, `NetworkManager`, `python3-dbus`, `python3-gi`, `iw`, `iproute2`, `dnsmasq-base`를 준비한다. `dnsmasq-base`는 NetworkManager의 shared IPv4 DHCP에 사용된다.

## 장비 설정

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
- 기본 선호 channel은 2.4 GHz channel 1인 `2412` MHz다. NetworkManager가 협상한 실제 그룹 channel은 다를 수 있으며, `iw dev`로 확인한다.
- Android 앱은 주소를 고정 추측하지 않고 `WifiP2pInfo.groupOwnerAddress`를 사용한다.
- profile은 연결 중에만 존재하며 디스크에 영구 저장하지 않는다.

## 상태 의미

상태 파일은 `/run/jetson-control/wifi-direct.json`이다.

`frequencyMhz`는 기존 API와의 호환성을 유지한 **설정상 선호 주파수**다.
`groupFrequencyMhz`는 마지막 성공한 `iw dev` 그룹 조회에서 관측한 실제 주파수이며,
그룹이 없거나 조회에 실패하거나 channel 정보가 없으면 `null`이다.
상태 파일의 갱신 시각과 함께 해석한다. `dhcpActive`는 서비스가 직접 실행한
수동 모드 dnsmasq 프로세스만 나타내므로, NetworkManager 모드의 `false`를
DHCP 부재로 해석하지 않는다. 해당 모드의 DHCP 할당은 NetworkManager journal에서 확인한다.

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

## Android 연결 순서

1. 앱에서 QR을 스캔하고 BLE challenge-response 인증을 완료한다.
2. 최초 등록이면 앱이 바로 Wi-Fi 설정 화면을 열며, 필요한 경우 일반 공유기 Wi-Fi를 BLE로 설정한다.
3. 연결 허브에서 등록된 장비의 `장비에 직접 연결`을 누른다. 공유기 연결·서버 업로드 중단 가능성 안내를 확인하고 직접 연결을 선택한다. 명시적으로 선택한 Direct 연결은 자동 LAN 탐색이 즉시 되돌리지 않는다.
4. 주변 기기 권한을 허용하고 Android 위치 서비스를 켠다.
5. Jetson 장비를 선택한다. 앱은 WPS PBC와 `groupOwnerIntent=0`으로 연결한다.
6. 앱은 Group Owner 주소의 `https://<address>:8765/v1/hello`를 검사한다.
7. QR secret 기반 인증서 proof와 요청·응답 HMAC 검증 후 Dashboard를 연다.

Wi-Fi Direct association만으로 장비 제어 권한을 부여하지 않는다. QR로 등록된 장비 ID와 secret이 없으면 API 연결은 거부된다.

연결 시간 초과·취소 시에는 `이전 연결 정리 중` 단계를 거친다. Android 협상 취소와 대상 그룹 제거가 끝나거나 정리 제한 시간이 지나기 전까지 다음 연결을 중첩하지 않는다. 다른 장비의 그룹을 임의로 삭제하지 않는다. 정리에 실패했다면 표시된 안내에 따라 Wi-Fi Direct 상태를 확인한 후 재시도한다.

상태 API는 별도 수집 루프의 마지막 스냅샷을 반환한다. `collectedAtEpochMillis`, `statusFresh`, `metricValidity`로 응답 수신 시각과 실제 측정 시각을 구분한다. 측정 실패의 기존 숫자 필드는 호환 목적으로 남으며, 새 앱은 해당 지표를 `확인 불가`로 표시한다.

## 점검

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

위 curl은 도달성 점검용이며 인증서·장비 인증을 검증하지 않습니다. 연결 정상성은 앱의 TLS proof·HMAC 성공과 함께 확인합니다.

상세 로그:

```bash
journalctl -u jetson-wifi-direct.service -n 150 --no-pager
journalctl -u NetworkManager.service -n 150 --no-pager
```

## 장애 대응

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

## 보안 경계

- NetworkManager 모드의 `ipv4.method=shared`는 DHCP·DNS forwarding·NAT를 구성한다.
  `ipv4.never-default=yes`는 P2P를 Jetson의 기본 경로로 지정하지 않는 설정이며,
  NAT나 Android로 제공되는 DHCP를 끄는 설정이 아니다.
- P2P 연결은 기존 LAN/모바일 인터넷 기본 경로를 대체하지 않는다.
- Local API는 pinned TLS proof와 양방향 HMAC 없이는 사용할 수 없다.
- 저장소 경로, upload token, pipeline 명령은 인증되지 않은 endpoint에 노출하지 않는다.

참고:

- [Android Wi-Fi Direct 공식 개요](https://developer.android.com/develop/connectivity/wifi/wifip2p)
- [Android WifiP2pManager API](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager)
- [NetworkManager wifi-p2p 설정](https://networkmanager.dev/docs/api/latest/settings-wifi-p2p.html)
- [wpa_supplicant P2P control interface](https://w1.fi/wpa_supplicant/devel/p2p.html)

연결 진단 ZIP 수집과 현재 남은 검증 항목은 [연결 진단](DIAGNOSTICS.md)을 참고합니다.
