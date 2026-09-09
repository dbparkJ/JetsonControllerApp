# 연결 진단·배포

연결이 끊긴 시점의 앱·Jetson 로그를 수집하고, 실제 배포와 검증 범위를 확인하는 문서입니다. 앱의 로그 기능과 백엔드 배포 도구는 구현되어 있으며 현장 연결 안정성 검증은 남아 있습니다.

## 마지막 확인 상태

다음은 **2026-09-08 04:46 UTC에 기록한 상태**이며, 이후 장비의 현재 실행 상태를 대신하지 않습니다.

| 항목 | 확인 범위 |
|---|---|
| Android | `1.15.3` / versionCode `22`, build ID `4ba808a-diagfix1` 설치·실행 |
| 앱 진단 기능 | 수동 마커, ZIP 저장·회수 성공; 회수 기록 75개, 누락·쓰기 오류 0 |
| 자동 검증 | Android 191개, backend 232개 통과; Android 빌드·lint 성공 |
| Jetson 진단 백엔드 | 운영 배포·재시작 미실행; 수동 배포 도구 준비 |
| 당시 연결 관측 | hello 요청 20회 timeout, 인증된 IP 응답·동일 요청 소켓 증거 미확보 |
| 현장 원인 | 실제 끊김 원인 미확정; 아래 장시간·장애 주입 시험 미완료 |

당시 백엔드 배포는 운영 업로드 상태 조회 권한과 USB·독립 관리 경로가 확보되지 않아 진행하지 못했습니다. 재개할 때는 실제 권한·런타임·작업 상태를 다시 확인합니다. 과거 실행 기록과 원복 APK 정보는 정리 전 커밋 `e24904b`의 `docs/diagnosis/`에서 확인할 수 있습니다. 미추적 초안과 로컬 실행 산출물은 문서 정리 시 저장소 밖의 로컬 백업에 보존했습니다.

## 끊김 시 로그 수집

앱은 시작할 때 내부 저장소에 자동 기록합니다. 연결·인증·상태·P2P·RTK 오류를 관측하면 직전 기록과 이후 최대 60초를 장애 기록으로 보존합니다.

1. 앱 **알림 설정 → 연결 진단 기록 → 지금 끊김 시점 표시**를 누릅니다.
2. 가능하면 약 60초 뒤 **앱 진단 ZIP 저장**을 누릅니다. 앱이 종료될 상황이면 먼저 저장합니다. 제어 연결이 없어도 진단 화면을 열 수 있습니다.
3. Jetson의 진단 ZIP도 내보내고, 사람이 본 끊김 시각·LAN/Direct 여부·화면 잠금·백그라운드·사용자 해제 여부를 함께 기록합니다.

앱만 갱신하면 Jetson 측 로그는 생기지 않습니다. API와 P2P도 진단 소스를 배포하고 새 프로세스로 실행해야 양쪽 기록을 수집할 수 있습니다.

## 보존 범위

| 대상 | 위치 | 한도 |
|---|---|---|
| 앱 | `Context.noBackupFilesDir/connection-diagnostics` | 순환 4 × 1 MiB + 장애 3 × 1 MiB |
| Jetson API | 실제 `RuntimePaths.state_dir/diagnostics/api` | 같은 한도, 최대 7 MiB |
| Jetson P2P | 실제 `RuntimePaths.state_dir/diagnostics/p2p` | 같은 한도, API와 합계 최대 14 MiB |

7일 지난 세그먼트는 기록·내보내기 시 정리하고, 용량 제한에 먼저 도달하면 더 일찍 순환합니다. 앱의 장애 직전 기록은 최대 512 KiB, Jetson은 최근 60개 이벤트입니다. 이후 최대 60초를 파일 한도 안에서 추가하며 자동 장애 보존은 최소 120초 간격으로 합칩니다.

프로세스별 비동기 큐는 256개이며 프로세스 종료·OS 회수·전원 꺼짐에는 수집이 멈추고 큐 끝부분이 유실될 수 있습니다. 7일 또는 60초 전체가 항상 남는 것은 아닙니다. ZIP manifest의 누락·저장 오류와 실제 기록 시각을 함께 확인합니다.

키·인증 헤더 원문·본문·좌표·IP/MAC 원문·장비 이름은 진단 로그에 넣지 않습니다. 내보낸 ZIP은 순환 보존 대상 밖의 파일이므로 별도로 관리합니다.

## Jetson ZIP 내보내기

서비스가 사용하는 Python 환경과 실제 state root를 확인합니다. P2P의 `/run` 상태 JSON과 영속 진단 로그는 위치가 다릅니다. 설치된 코드가 `/opt/jetson-control`, state root가 `/var/lib/jetson-control`인 경우:

```bash
sudo /opt/jetson-control/venv/bin/python -c \
  'import sys; sys.path.insert(0,"/opt/jetson-control"); from jetson_control.diagnostics import main; raise SystemExit(main())' \
  --state-dir /var/lib/jetson-control \
  --output /tmp/jetson-connection-evidence.zip
```

API/P2P의 state root가 다르면 `--state-dir`을 반복합니다. ZIP은 `0600`으로 새로 생성하고 기존 파일을 덮지 않습니다. 이미 같은 이름이 있으면 새 이름을 사용합니다. 읽을 수 있는 진단 파일만 포함하며 서비스·Wi-Fi 설정은 변경하지 않습니다.

manifest의 `missingSources`, `skippedFiles`, `skippedLines`, `droppedEvents`, `storageFailures`를 확인합니다. 순환 중인 파일의 스냅샷이므로 모든 소스가 같은 순간에 정지된 기록은 아닙니다.

## 분석 기준

- **시각·실행 식별**: 앱은 `utcMs`, `elapsedMs`, Jetson은 `utcEpochMillis`, `elapsedMillis`를 사용합니다. `runId`와 단조 시각은 서로 다른 프로세스·장치 사이에서 직접 비교하지 않습니다. `api_hello_clock`의 서버−클라이언트 시차·왕복 시간과 시계 변경을 함께 확인합니다.
- **같은 요청 연결**: 양쪽의 `requestRef`는 기존 nonce의 `SHA256(UTF-8("STAB1:" + nonce))[:16 hex]`입니다. 앱 내부의 `requestId`, `clientId`, `endpointGeneration`, `sessionId`, `requestSequence`, `attemptId`와 함께 사용합니다. public hello에는 `requestRef`가 없을 수 있습니다.
- **인증 성공**: 앱 `api_authenticated`가 응답 HMAC 검증 성공입니다. `api_response`의 200이나 `api_call_ended`만으로 인증 성공을 판단하지 않습니다. Jetson의 `api_reply_sent`도 휴대전화 수신을 증명하지 않습니다.
- **전송과 상태 반영**: `api_connection`의 소켓 관측과 같은 요청의 서버 수신·인증 응답을 연결합니다. `status_refresh`의 `APPLIED/DISCARDED`, `API_THRESHOLD/LINK_LOST`, 사용자 의도, 센서 freshness와 RTK heartbeat를 구분합니다. RTK heartbeat 실패만으로 보정 데이터의 물리 손실을 확정하지 않습니다.
- **P2P 정리**: 요청, OS 수락, 실제 그룹 관측, 대상 확인, `CONFIRMED_ABSENT`는 별도 단계입니다. `cleanupConfirmed=false`나 조회 실패를 그룹 부재로 해석하지 않습니다.

순환·장애 파일의 중복 행은 `(source, runId, seq)`로 제거합니다. 최초 실패 계층을 입증하지 못하면 원인은 미확정으로 남깁니다. 무선 ADB 단절도 제품 제어 요청의 실패와 구분합니다. 앱의 version/build ID·APK/서명 hash와 Jetson 시작 기록의 PID/build 지문을 대조해 실행 버전을 확인합니다.

## 진단 백엔드의 제한 배포 도구

[deploy_backend.py](../scripts/diagnosis/deploy_backend.py)와 [배포 manifest](../scripts/diagnosis/backend-deployment-manifest.json)는 확인된 기존 배포에 진단 관련 **6개 파일**을 갱신하는 도구입니다. 일반적인 전체 설치는 [백엔드 설치·운영](BACKEND.md)을 따릅니다. manifest의 기존·신규·의존 파일 hash가 현재 환경과 다르면 중단하므로, 차이를 검토하지 않고 hash나 검사를 수정하지 않습니다.

기본 실행은 읽기 전용 사전 검사입니다. 저장소 루트에서 실행합니다.

```bash
sudo /usr/bin/python3 scripts/diagnosis/deploy_backend.py
```

업로드/P2P 상태 저장소 누락·읽기 실패, 알 수 없는 업로드 상태, 유효 RTK lease, 활성·전이 중 수집 작업, 실제 P2P 그룹·연결 중 상태가 있으면 중단합니다. 취소 업로드 기록만으로 worker 종료를 판단하지 않습니다. 별도의 수동 수집 프로세스도 확인합니다.

실제 적용 전에는 작업이 없는 중단 가능 시간, USB ADB, 무선 P2P와 독립된 관리 경로를 확보합니다. 다음 플래그는 그 조건의 확인이며 조건을 만들어 주지 않습니다.

```bash
sudo /usr/bin/python3 scripts/diagnosis/deploy_backend.py \
  --apply \
  --confirm-maintenance-idle \
  --confirm-usb-adb \
  --confirm-independent-management
```

도구는 소스·의존성·unit·drop-in·운영 상태를 검사하고 `/opt/jetson-control/backups/connection-evidence-<UTC>-<random>`에 원본과 메타데이터를 보관합니다. 설치 조합 import와 운영 상태를 다시 확인한 뒤 `api.py`, `diagnostics.py`, `mobile_rtk.py`, `pipelines.py`, `status.py`, `wifi_direct.py`를 교체하고 기존 활성 API/P2P를 재시작합니다. BLE·NetworkManager·센서·수집 서비스는 배포 대상이 아닙니다.

새 PID·build ID·원래 인증서를 신뢰하는 TLS hello·새 진단 시작 기록을 45초 한도로 확인합니다. 교체 이후 실패하면 백업을 검증하고 파일·원래 부재 상태·서비스 상태를 복원합니다. 결과는 `DEPLOYED`, `FAILED_ROLLED_BACK`, `FAILED_ROLLBACK_INCOMPLETE`를 구분합니다. 저장소나 서비스 명령 자체의 실패까지 자동 복구를 보장하지는 않습니다.

수동 원복은 결과에 출력된 실제 백업 디렉터리를 지정합니다.

```bash
sudo /usr/bin/python3 scripts/diagnosis/deploy_backend.py \
  --confirm-maintenance-idle \
  --confirm-usb-adb \
  --confirm-independent-management \
  --rollback /opt/jetson-control/backups/connection-evidence-ACTUAL_BACKUP_NAME
```

이 도구의 단위 테스트는 임시 파일과 모의 서비스 명령으로 실행합니다. 통과해도 실제 운영 배포·재시작 검증을 의미하지 않습니다.

## 남은 확인 항목

현재 코드는 API 일시 실패 때 기존 Direct 그룹을 보존하지만 RTK 중지는 보수적으로 유지합니다. API 상태 실패와 유효한 RTK lease의 독립 유지, Android P2P 정리 미확정·늦은 콜백 경계, 실제 요청의 소켓 경로·인증 응답 연결은 추가 검증이 필요합니다.

| 실기 시험 | 목표 | 마지막 기록 |
|---|---|---|
| LAN / Direct 유지 | 각각 30분 | 미실행 |
| LAN / Direct 재연결 | 각각 10회 | 미실행 |
| USB 잠금 / 자연 절전 | 각각 10분; 강제 Doze 별도 | 미실행 |
| 일반 / RTK 백그라운드 | 각각 30분; OS 회수·force-stop 별도 | 미실행 |
| API 장애 / 실제 링크 장애 | 각각 3·10·60초 | 미실행 |
| 명시적 해제 후 유지 | 10분 | 미실행 |
| Cellular + Direct | 동일 요청 소켓·인증 응답 확인 | 미실행 |
| API / P2P 서비스 재시작 | 각각 복구·인증 확인 | 미실행 |

시험 전에 정상 인증 세션, 독립 관리 경로, 백업과 복구 기준을 확보하고 실제 실행 시간·결과를 기록합니다. 마커·ZIP 저장 성공이나 자동 회귀 테스트로 이 시험을 대체하지 않습니다.
