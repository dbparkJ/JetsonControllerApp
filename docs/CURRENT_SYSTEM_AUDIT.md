# Current Jetson System Audit

점검일: 2026-08-12, upload receiver 추가 점검: 2026-08-13 (Asia/Seoul)

이 문서는 secret, token, QR URI를 기록하지 않는다. 배포 전 실제 장비 상태와 새 backend 설치 후 확인할 상태를 구분한다.

## 1. 설치 전 확인 결과

| 항목 | 확인 결과 |
|---|---|
| 저장소 branch / 기준 commit | `main` / `9a2931a`에서 작업 시작; 최종 변경은 ControllerApp 작업 branch의 commit 참조 |
| 장비 이름 | `MMS-4DE0` |
| 장비 ID | `d606c26d-98d6-4b09-99d7-c3da7dda4de0` |
| 기존 BLE service | `jetson-control.service` active, root로 `/opt/jetson-control/jetson_control.py` 실행 |
| Bluetooth daemon | systemd override가 `/usr/local/libexec/bluetooth/bluetoothd-5.55` 실행, `-v` 결과 `5.55` |
| 기존 API | systemd unit 없음, root 수동 `uvicorn app:app`가 `0.0.0.0:8765`에서 평문 HTTP 실행 |
| 기존 API 범위 | `/v1/hello`는 있으나 새 저장소/명령/업로드 API와 요청·응답 인증은 미배포 |
| Avahi | active, `_jetsonctl._tcp`, port 8765, `id`/`api=1` 광고 |
| Wi-Fi | `wlan0` up |
| Wi-Fi Direct | `p2p-wlan0-3` 존재하지만 down |
| 데이터 root 후보 | `/home/jm/26_camera_record`, 점검 시 약 212 GiB 사용 |
| 디스크 | root ext4 약 914 GiB, 약 411 GiB available |
| 서비스 제어 대상 | 카메라/LiDAR/GNSS/MMS systemd unit 미확정 |
| 외부 업로드 target | URL/token 미제공, 아직 구성하지 않음 |
| DepthAI source | Git root `/home/jm/26_camera_record`의 `depthai_refactored_ver2` 하위 프로젝트, branch `feature/yolo-seg-shp-debug-ui`, remote 없음, dirty |
| DepthAI main/config | `synced_image_recorder.py`, `configs/capture.yaml` |
| DepthAI venv | `/home/jm/26_camera_record/.venv`, Python 3.8.10, entrypoint `--help` 확인 |

기존 `/etc/jetson-control/device.json`은 root 전용이며 설치 script는 이 identity를 보존한다. secret 값은 확인 결과나 로그에 출력하지 않는다.

## 2. 발견된 문제와 저장소 내 수정

- 기존 Android/서버 HTTP 인증 전달 방식이 일치하지 않던 부분을 단일 canonical HMAC 계약으로 통합했다.
- 요청 시각과 nonce replay 방지, 응답 body 서명을 추가했다.
- 평문 로컬 API를 QR secret으로 증명하는 장비별 인증서 고정 HTTPS로 전환했다.
- BLE `SET_WIFI`, 상태 조회, 재부팅/종료 command를 동일 backend 구현에 연결했다.
- BLE Wi-Fi credential을 challenge 파생 AES-256-GCM session key로 암호화하고 `nmcli` 표준입력으로 전달한다.
- storage root containment와 traversal/symlink 차단을 적용했다.
- 외부 HTTPS receiver로 보내는 영속 청크 업로드, offset 재개, hash, retry, cancel을 구현했다.
- 업로드 완료와 취소가 겹칠 때 완료 상태가 취소를 덮는 경합을 수정했다.
- Wi-Fi SSID 앞뒤 공백을 보존하고 비밀번호를 로그에 남기지 않는다.
- LAN/Wi-Fi Direct 연결마다 독립 API client와 연결 세대 번호를 사용한다.
- Android credential은 Keystore AES-GCM으로 암호화하고 backup/device transfer에서 제외했다.
- Wi-Fi AP를 선택하면 해당 행 바로 아래에서 비밀번호를 입력하고 IME에 맞춰 목록이 스크롤되도록 UI를 재구성했다.
- 최초 QR/BLE 인증 성공 시 dashboard를 건너뛰고 바로 Wi-Fi 설정 화면을 열어 초기 네트워크를 구성하도록 연결했다.
- 저장소 탐색/용량, 파일·폴더 업로드, 업로드 기록·진행률·취소, 재부팅/종료 확인 UI를 구현했다.
- Git working tree를 system release로 복사하고 선택한 virtualenv/main Python/config로 실행하는 pipeline 등록기를 구현했다.
- 등록 pipeline의 실행/중지/재시작/부팅 toggle/등록 해제를 backend API와 Android UI에 연결했다.
- 새 Jetson에서 package, BlueZ 5.55, backend, QR identity, DepthAI pipeline을 설치하는 bootstrap script를 추가했다.
- Wi-Fi Direct를 discovery 대기 상태로 유지하고 Android PBC 요청 시 NetworkManager가 임시 GO/DHCP profile을 소유하도록 변경했다.

## 3. 실제 배포 결과

배포 완료: 2026-08-12 13:24 KST

| 항목 | 확인값 |
|---|---|
| BlueZ | `bluetooth.service` active, `/usr/local/libexec/bluetooth/bluetoothd-5.55`, 버전 5.55 |
| `jetson-control.service` | enabled/active, 새 `jetson_control.ble`; GATT application과 광고 등록 완료 |
| `jetson-control-api.service` | enabled/active, pinned HTTPS `:8765`, `/v1/hello` 정상 |
| 기존 수동 uvicorn | 종료; API는 systemd unit이 소유 |
| storage root | 정확히 `/home/jm/26_camera_record` |
| power commands | enabled |
| controlled services | 빈 목록 유지 |
| TLS | `/etc/jetson-control/tls.crt`, `tls.key`; private key `0600` |
| Avahi TXT | `id`, `api=1`, `tls=1` |
| upload targets | receiver URL/token을 받기 전 빈 객체 |
| `jetson-wifi-direct.service` | enabled/active, 상태 `DISCOVERABLE`; `p2p-dev-wlan0`는 NetworkManager `disconnected` 상태에서 요청 대기 |
| Wi-Fi Direct route | 일반 Wi-Fi `wlan0` 연결과 기존 default route 유지; 연결 시 임시 P2P profile은 `never-default` |
| `jetson-pipeline@depthai-capture.service` | loaded/enabled; 카메라 미연결 시 앱 상태 `RETRYING`, 15초 후 자동 재시도 |
| pipeline source | `feature/yolo-seg-shp-debug-ui` commit `41d7e27fd6fa`, dirty working tree snapshot |
| pipeline runtime | `/opt/jetson-pipelines/depthai-capture/current`, root 소유·`jm` 읽기 권한 |
| pipeline command | Python 3.8.10 venv + `synced_image_recorder.py` + `configs/capture.yaml` |

전체 `/opt/jetson-control/doctor.sh` 항목이 통과했다. systemd 245용 path escape를 적용한 뒤 instance unit 정적 검사에서도 `WorkingDirectory`와 `ReadWritePaths` 오류가 없었다. pipeline unit은 실제로 자동 시작되며, 현재는 DepthAI 장치가 없어 `No available devices`로 종료한 뒤 15초마다 재시도한다. release의 main/config를 선택 venv에서 로드하는 경로와 읽기 권한은 정상이다. 실제 reboot와 Android 단말의 최종 Wi-Fi Direct association은 자동 검증에서 수행하지 않았다.

저장소 검증 결과는 backend unit test 42개 통과(개발 venv의 BLE 암호화 2개 skip), 시스템 Python BLE AES-GCM test 2개 통과, Android unit test/lint/debug APK/debug androidTest APK build 통과다.

## 4. 배포 후 확인 명령

```bash
sudo /opt/jetson-control/doctor.sh
systemctl --no-pager --full status jetson-control.service jetson-control-api.service
curl --fail --insecure https://127.0.0.1:8765/v1/hello
```

실제 `reboot`/`shutdown`은 자동 검증에서 호출하지 않는다. 외부 receiver가 준비된 뒤 별도 token으로 작은 파일, 네트워크 중단/재개, 대용량 파일 순서로 검증한다.

## 5. 운영 투입 전 필요한 외부 정보와 실기 확인

- Jetson에 발급 완료된 upload receiver URL/token 적용
- 후순위 서비스 탭에 연결할 실제 camera/LiDAR/GNSS/MMS systemd unit 이름
- 최신 APK를 설치한 Android 단말에서 Wi-Fi Direct `DISCOVERABLE -> CONNECTING -> READY` 전환과 API 연결 확인

## 6. 외부 upload receiver 실제 배포 결과

배포 완료: 2026-08-13 KST

| 항목 | 확인값 |
|---|---|
| 구현 | `upload_receiver/` FastAPI 단일 worker + SQLite WAL/`synchronous=FULL` + Caddy |
| HDD | `/dev/sda1`, ext4 3.6 TB, `/data/server_storage` mount, 점검 시 약 3.4 TB available |
| 데이터 root | `/data/server_storage/jetson-upload-receiver` (`/data` 자체는 NVMe이므로 사용하지 않음) |
| mount 안전장치 | systemd `RequiresMountsFor`, `ConditionPathIsMountPoint`, 시작 전 `mountpoint` 검사 |
| receiver | `jetson-upload-receiver.service` enabled/active, `127.0.0.1:8877`만 listen |
| public HTTPS | `jetson-upload-caddy.service` enabled/active, 공개 TCP 443 |
| base URL | `https://125-142-22-24.sslip.io` |
| TLS | Let's Encrypt 공인 인증서, SAN `125-142-22-24.sslip.io`, 2026-11-11까지 유효한 인증서 확인 |
| router mapping | TCP 443 -> 이 PC TCP 443, `jetson-upload-port-forward.timer`가 15분마다 확인 |
| cleanup | `jetson-upload-receiver-cleanup.timer` enabled/active, 72시간 지난 staging을 매일 정리 |
| 장비 token | 장비 ID `d606c26d-98d6-4b09-99d7-c3da7dda4de0`에 발급, 3 TiB quota, 서버의 mode `0600` 파일에만 저장 |
| 자동 시험 | receiver API·경로·hash·재개·복구·경합·quota·token·runtime mount guard 시험 24개 통과 |
| Jetson backend 회귀 시험 | 최신 `main` 통합 후 56개 통과, 개발 venv에 `python3-cryptography`가 없어 BLE crypto 2개 skip |
| 공인 E2E | 공인 URL로 배포 smoke file의 session/offset/PUT/complete 후 HDD 객체와 manifest 일치 확인; 최종 코드 재배포 뒤 31-byte 시험 재통과 |

token 원문은 이 audit에 기록하지 않는다. 서버 보관 위치는 다음이며, 신뢰할 수 있는 경로로 Jetson에 전달한 뒤 [JETSON_BACKEND_SETUP.md](JETSON_BACKEND_SETUP.md)의 명령으로 설정한다.

```text
/data/server_storage/jetson-upload-receiver/secrets/device-tokens/d606c26d-98d6-4b09-99d7-c3da7dda4de0.token
```

현재 `sslip.io` 이름은 공인 IPv4 `125.142.22.24`를 포함한다. 공인 IP가 바뀌면 새 이름으로 Caddy와 Jetson upload target을 함께 갱신해야 한다. 고정 공인 IP 또는 소유한 DDNS로 이전하기 전까지는 이 변경 가능성을 운영 절차에 포함한다.

API, HDD 저장, 공인 TLS와 장비 token 구축은 완료됐다. 다만 [UPLOAD_RECEIVER_AGENT_GUIDE.md](UPLOAD_RECEIVER_AGENT_GUIDE.md)의 전체 운영 준비 완료 기준 중 별도 관측성 dashboard, 처리량/checksum/offset/latency의 장기 metric, 실제 Jetson의 외부망 중단 재개·대용량 전송 시험은 아직 남아 있다. 현재 `/metrics`는 localhost receiver에만 있고 세션 상태 및 전체 declared/received byte의 최소 metric만 제공한다.
