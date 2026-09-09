# wlan0 일반 인터넷 실기 조사 — 2026-09-08

현재 결론은 **지속적인 인터넷 단절의 단일 원인 미확정**이다. 실제 장치에서 OFF,
DISCOVERABLE, Android READY, 서비스 재시작, 종료 후 정리까지 수행했다.
`shared` 제거나 route metric 강제 변경을 정당화하는 증거는 확보되지 않았다.
확인된 주파수 상태 정보의 오해를 막는 진단 수정만 저장소에 적용했다.

## 1. 실제 원인: 확정 범위와 한계

* **주파수 정보 문제는 확정했다.** 기존 `frequencyMhz`는 실제 RF 주파수가 아니라
  `settings.frequency`였다. 당시 문제의 `p2p-wlan0-1`은 wpa_supplicant 로그에서
  **5240 MHz**로 생성됐다. 따라서 그 시점의 `2412` 상태 값으로 서로 다른 채널
  동시 운용을 전제하면 안 된다. 이번 태블릿 연결에서도 `iw dev`로 양쪽 5240 MHz를 확인했다.
* **metric penalty의 구현은 확인했다.** 장치와 같은 NetworkManager 1.22.10의
  `default_route_metric_penalty_detect/get`은 해당 IP family의 connectivity가
  FULL이 아니고 검사가 활성화돼 있으면 20000을 더한다. 기존 600→20600은 이 동작과
  일치하지만, 원래 검사 실패의 구체적인 이유를 소급해서 입증하지는 못했다.
  [NetworkManager 1.22.10 소스](https://github.com/NetworkManager/NetworkManager/blob/1.22.10/src/devices/nm-device.c#L2163)
* **P2P 생성에 따른 DNS 서비스 재시작 경로를 확정했다.** Tailscale 1.102.2는 이 장치에서
  `direct` DNS manager를 사용한다. P2P 생성의 major link change 뒤 DNS를 재설정하고
  `systemd-resolved`를 재시작한다. 실제 로그와 해당 버전의 `direct.go`가 일치한다.
  과거에는 Tailscale→`127.0.0.53` DNS 전달 timeout과 NetworkManager→resolved
  D-Bus `NoReply`가 반복됐다. 그러나 현재 재현에서는 이 재시작 후에도 DNS가 성공하며,
  이 경로만으로 과거 metric penalty나 IP ping 손실 전체의 원인을 확정할 수 없다.
  [Tailscale 1.102.2 소스](https://github.com/tailscale/tailscale/blob/v1.102.2/net/dns/direct.go#L413)
* **짧은 IP 응답 실패도 관측했다.** 재부팅 전 DISCOVERABLE과 READY에서 association과
  metric 600을 유지하면서 인터넷 IP 및 공유기 ping이 함께 실패했다. OFF 첫 측정에도
  최대 4.4초 지연이 있었다. 무선/AP 구간 후보를 남기지만, 드라이버의 특정 결함이나
  커널 드롭 위치는 확인되지 않았다. 짧은 probe deadline 내 미응답을 영구 패킷 유실과
  동일시하지 않는다.

## 2. 근거 로그와 보존 위치

원본은 저장소 밖 `/home/jm/jetson-internet-evidence-20260908`에 보존했다.
상위 폴더 권한은 0700이며 공개 진단 ZIP에 원본 IP/MAC/SSID를 추가하지 않았다.
각 snapshot의 `*.meta.json`에는 명령, 시작/종료 시각, 종료 코드, stderr가 있다.
`nft`는 설치되지 않아 조회하지 못했으며 실제 조회 가능한 iptables를 비교했다.
Wi-Fi 비밀번호 출력 옵션, QR secret, 인증서 재생성은 사용하지 않았다.

| 자료 | 의미 |
|---|---|
| `group-frequencies.txt` | 과거 GO 그룹 주파수만 추출한 로그; 그룹 1은 5240 MHz |
| `nm-journal-full.txt` | 재부팅 전 부팅부터의 NM 로그; 첫 그룹보다 앞서 CONNECTED_SITE와 resolved NoReply 존재 |
| `tailscale-dns-events.txt`, `resolved-full.txt` | 재부팅 전 DNS 재설정·재시작·timeout |
| `nm-source/`, `tailscale-source/` | 실제 설치 버전에 대응하는 upstream 소스 |
| `off-01`, `off-02`, `discoverable-01`, `ready-172354` | 최초 OFF/ON 상세 비교 |
| `watch-171649.jsonl`, `radio-watch.jsonl` | 재부팅 전 15분 관측 및 공유기/무선 카운터 |
| `reboot-off-174209`, `reboot-ready-174420` | 재부팅 후 OFF/READY 상세 비교 |
| `service-restart-174538`, `off-after-restart-174621` | 실제 재시작·종료 |
| `reproduction-final-off-*`, `*-reboot-off-final.diff` | 최종 OFF 및 규칙·경로 원복 비교 |
| `watch-174209.jsonl` | 재부팅 후 전체 lifecycle 관측 |
| `tablet-events.jsonl`, `tablet-ui.xml` | 태블릿 API 인증 이벤트 및 UI 관측 |

과거 부팅 로그에는 시계 변경이 있어 해당 시점의 상관관계에는 journal monotonic 시간을
사용했다. 두 번의 부팅 구간은 분리해서 해석한다. 재부팅은 조사 도구가 요청한 동작이 아니다.

## 3. 재현 절차

수정 전 실행 코드와 저장소의 `wifi_direct.py`, `network.py`, `config.py`,
`diagnostics.py` SHA-256 일치를 확인했다. 사용자 요청의 여섯 파일, main 대비 diff와
마지막 16개 commit을 검토했다. main 대비 profile/shared/일반 Wi-Fi 설정 변경은 없었고,
주요 P2P 변경은 관측 실패 시 그룹 보존과 진단 추가였다.

태블릿은 사용자 지정 ADB 주소로 새로 페어링하고 `1.15.3` / versionCode 22,
build ID `f2113af-internet-investigation`을 `install -r`로 설치했다. 기존 QR로 사용자가
등록한 뒤 LAN 제어와 앱의 명시적 Direct 전환을 수행했다.

로컬 `capture.py`는 요청된 네트워크 명령과 wlan0 지정 ping, 전체 routing table,
무선 station 정보, connectivity HTTP 응답, 제한된 ICMP/ARP 헤더를 저장한다.
`watch.py`는 5초 간격의 probe와 상태 전환 snapshot을 수집하고 임시 CONCHECK 로그를 복원한다.
`reproduce.py`는 OFF→DISCOVERABLE→태블릿 READY→재시작→OFF를 실행한다.
이 스크립트들은 해당 장치에 맞춘 로컬 조사 산출물이며 서비스에 설치되지 않았다.

## 4. 수정 파일

* `backend/jetson_control/wifi_direct.py`
* `backend/jetson_control/diagnostics.py`
* `backend/tests/test_wifi_direct.py`
* `docs/WIFI_DIRECT.md`
* 이 조사 기록

기존 미추적 사용자 문서 `docs/JETSONCONTROLLER_DESIGN_REBUILD_AI_AGENT.md`는 보존했다.

## 5. 변경 내용

기존 `frequencyMhz` 설정 필드를 유지하면서 `groupFrequencyMhz`를 추가했다.
이미 수행하는 `iw dev`의 해당 그룹 section에서 실제 채널을 읽으므로 추가 radio 명령이 없다.
그룹 부재·그룹 조회 실패·채널 정보 부재에는 `null`이며, 이전 관측값을 재사용하지 않는다.
진단 allowlist에도 정수 필드만 추가했다.

문서에서는 shared 모드가 NAT/DHCP/DNS forwarding을 구성한다는 사실과
`never-default`의 의미를 바로잡았다. `dhcpActive=false`는 수동 dnsmasq child 부재를 뜻하며
NetworkManager가 제공하는 DHCP 부재를 뜻하지 않는다는 제한도 명시했다.

## 6. 수정 이유와 네트워크 대안 판단

잘못된 실제 주파수 추정이 원인 분석을 왜곡하는 문제를 막는다. 이것은 인터넷 단절을
해결했다고 주장하는 패치가 아니다. 일반 route, DNS, shared, DHCP, radio 제어는 변경하지 않았다.

| 후보 | 이번 판단 |
|---|---|
| shared 유지 + never-default | 이미 적용돼 있고 실제 Android DHCP/API 동작 확인; 유지 |
| route metric 강제 하향/검사 비활성화 | 관측 결과를 가릴 수 있고 실제 L3 실패를 해결하지 못하므로 미적용 |
| manual 주소 + 별도 DHCP | shared의 인과관계 미확정; DHCP·RTK 회귀 위험을 감수할 근거 부족 |
| DNS priority/zone 변경 | P2P profile의 DNS·gateway는 비어 있음; 충돌 증거 없이 변경하지 않음 |
| rp_filter 변경 | 관측값은 loose 2; 실패 구간의 reverse-path-filter 카운터 증가 없음 |
| Tailscale DNS manager 정합성 수정 | 별도 A/B 대상; 공식 문서는 NM+resolved 환경에서 stub symlink 구성을 권장하지만 이 장치의 설정은 아직 변경하지 않음 |
| 인터넷 probe 기반 P2P fail-safe | OFF에도 지연이 있어 P2P 원인 판별 없는 자동 해제는 부적절; 미구현 |

[Tailscale Linux DNS 구성 문서](https://tailscale.com/docs/reference/linux-dns)는 DNS manager
연동을 검증할 다음 실험의 근거이며, 실제 장애가 해결됐다는 증거를 대신하지 않는다.

## 7. 기존 기능 영향

generation guard, cleanup, retry, 기존 진단, NetworkManager shared DHCP와 Android API를 유지했다.
새 API 필드는 선택적 추가다. SSID/profile/credentials/QR secret/인증서/RTK 설정을 변경하지 않았다.
해당 하드웨어의 managed+P2P 및 `#channels <= 2` 경로를 보존했다.

## 8. 자동 테스트 결과

* Android assembleDebug, lintDebug 성공; unit test **191/191 통과**.
* 변경 후 backend 전체 **234/234 통과**. 최초 전체 실행은 venv의 system dbus 미노출로
  두 모듈 import에 실패했으며, 같은 Python 3.8의 시스템 dist-packages를 테스트 경로에
  추가한 실행으로 해결했다. OS 패키지/운영 서비스 설정을 바꾸지 않았다.
* 새 회귀 테스트는 실제 그룹별 주파수, unknown 시 stale 값 제거, `#channels <= 2`,
  READY 반복 monitor의 조회 명령 제한을 검증한다. profile 생성의 shared,
  never-default, 임시 저장, autoconnect 설정도 명시적으로 검사한다.
* 기존 timeout/cleanup/API/진단 privacy 테스트를 보존했다. 모의 테스트의 배포 성공 출력은
  실제 `/opt` 배포 증거가 아니다.
* `git diff --check` 통과.

## 9. 실제 Jetson 검증 결과

| 단계 | 결과 |
|---|---|
| 최초 OFF-01 | IP 10/10, 도메인 9/10; 최대 약 4.4초 지연 |
| OFF-02 | IP/도메인/wlan0 지정 각각 10/10; full, metric 600 |
| DISCOVERABLE-01 | 일반 IP/도메인 각각 10/10, wlan0 지정 9/10; full, metric 600 |
| 첫 READY snapshot | 세 ping 모두 10/10; Android Direct dashboard 및 API 인증 확인 |
| 재부팅 전 연속 DISCOVERABLE | 85개 측정 중 IP 2회 probe의 전부 미응답 구간 3개; full/600 유지 |
| 재부팅 전 연속 READY | 95개 측정 중 같은 미응답 구간 1개; full/600 유지 |
| 재부팅 후 OFF/DISCOVERABLE/READY snapshot | 세 ping 모두 10/10; full/600 유지 |
| 실제 service restart | 세 ping 모두 10/10; 최대 약 1.51초 지연; DISCOVERABLE 복귀 |
| 종료 후 OFF 유지 | 세 ping 모두 10/10; full/600 |
| 최종 정리 | route/ip rule/NAT/정규화한 iptables/resolv.conf/sysctl이 OFF 기준과 동일 |

연속 probe는 5초마다 ping 2개, 응답 대기 1초 및 HTTP 제한 4초를 사용했다.
측정 구간 사이의 장애나 더 늦게 도착한 응답은 포착하지 못한다. 재부팅 후 77개 연속
측정에는 IP/도메인 ping 명령 실패가 없었지만, READY 중 connectivity URL의
4초 connection timeout은 1회 있었다. NM 상태는 모든 측정에서 full이었다.

주파수 수정은 실제 Jetson의 `iw dev`를 새 코드로 읽어
`frequencyMhz=2412`, `groupFrequencyMhz=5240`을 확인했다.
이는 읽기 전용 검증이며, 수정 코드는 `/opt/jetson-control` 서비스에 아직 배포하지 않았다.
조사 종료 시 P2P 서비스는 **inactive**, wlan0 인터넷은 **full/metric 600**,
NetworkManager 로깅은 원래 **INFO**다.

## 10. 아직 확인하지 못한 항목

* 원래의 지속적인 단절/20600 전환을 일으킨 최초 실패 지점과 이를 제거한 인터넷 수정.
* Tailscale DNS manager 구성을 바꾼 전후 비교 및 DNS 전달 순환 여부의 패킷 수준 확정.
* 짧은 L3 미응답의 AP/드라이버/firmware/전력 절감 중 정확한 원인.
* CONNECTING의 독립된 상세 snapshot: 짧은 협상이 5초 poll 사이에 지나감.
* 실제 connection timeout 장애 주입, RTK relay 실제 데이터 전송, 외부 upload/server 전송.
* 수정한 진단 코드의 운영 배포 후 service restart 및 기존 Android 기종에서의 재검증.

CASE A의 짧은 미응답은 관측했지만 CASE B의 DNS 단독 장애나 CASE C의
full 인터넷+limited/20600 조합은 확정하지 못했다. CASE D의 완전한 RX/TX 정지는
관측되지 않았다. CASE E의 shared 규칙 생성·제거는 확인했으며 그것만으로 장애 원인이라고
판정하지 않았다. 이전 휴대전화 대신 사용자가 지정한 태블릿을 사용했다는 조건 차이도 남는다.

## 11. 되돌리는 방법

운영 backend 및 DNS 설정은 교체하지 않았으므로 그 부분의 rollback은 필요 없다.
이번 저장소 수정 네 파일만 되돌릴 때는 후속 수정과 충돌하지 않는지 먼저 확인하고,
보존한 patch에 `git apply -R /home/jm/jetson-internet-evidence-20260908/frequency-diagnostics.patch`를
사용할 수 있다. 이 기록과 사용자 미추적 문서는 삭제하지 않는다.
태블릿 설치는 앱 데이터 유지 방식이었으며, 이전 APK로 돌아갈 필요가 있으면
동일 서명의 해당 APK를 `adb install -r`로 설치한다. 이전 태블릿 APK 자체는 확보하지 않았다.
