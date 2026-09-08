# 연결 끊김 사후 분석용 기록

2026-09-08 추가. **소스 구현이며 현재 스마트폰/운영 Jetson에 설치·배포하지 않았다.** 사용자의 후속 지시에 따라 앱 빌드·Android 테스트는 실행하지 않았다. 예전 APK와 예전 자동 테스트 결과에는 이 기능이 들어 있지 않다.

## 설치 후 사용 방법

앱은 시작할 때부터 내부 저장소에 자동 기록한다. 연결 실패·인증 실패·상태 오류·P2P 채널/연결 실패·RTK 오류를 관측하면 직전 기록과 이후 최대 60초를 별도 장애 파일에 보존한다. 사용자 해제는 의도 이벤트로 구분한다. 키·인증 헤더 원문·요청/응답 본문·좌표·IP/MAC 원문·장비 이름은 새 진단 로그에 넣지 않는다.

끊김이 발생하면:

1. 앱 **설정 → 연결 진단 기록 → 지금 끊김 시점 표시**를 누른다. 자동 보존도 수행되므로 즉시 누르지 못했다고 기록 전체가 사라지는 것은 아니다.
2. 가능하면 약 60초 뒤 **앱 진단 ZIP 저장**을 누른다. 앱이 종료될 것 같으면 먼저 저장한다. 제어 연결이 없어도 이 화면을 열 수 있다. 직접 표시한 시각과 쓰기 오류·누락 수를 확인한다.
3. Jetson의 아래 내보내기 명령으로 장치 ZIP을 회수한다. 두 ZIP과 사람이 본 끊김 시각·LAN/Direct 여부·화면 잠금/백그라운드 여부·사용자 해제 여부를 함께 전달한다.

앱만 갱신하면 Jetson 측 새 로그가 생기지 않는다. **앱과 API/P2P 모두 해당 소스가 설치되고 새 프로세스로 실행되어야 양쪽 상시 수집이 시작된다.** 설치·재시작은 기존 v3 §9의 별도 승인·백업·원복 경계를 따른다.

## 저장량과 수명

| 대상 | 위치 | 한도 |
|---|---|---|
| 앱 | `Context.noBackupFilesDir/connection-diagnostics` | 기본 4 × 1 MiB + 최근 장애 3 × 1 MiB = 최대 7 MiB |
| Jetson API | 실제 `RuntimePaths.state_dir/diagnostics/api` | 기본 4 × 1 MiB + 장애 3 × 1 MiB = 최대 7 MiB |
| Jetson P2P | 실제 `RuntimePaths.state_dir/diagnostics/p2p` | 동일; API/P2P 합계 최대 14 MiB |

7일 지난 세그먼트는 기록/내보내기 시 정리한다. 용량 제한에 먼저 도달하면 더 일찍 순환한다. **7일 전체 또는 60초 전체가 항상 남는다는 보장은 없다.** 프리뷰 등 요청이 많으면 기본 기록 보존 시간도 짧아진다. 앱의 장애 직전 기록은 최대 512 KiB, Jetson은 최근 60개 이벤트다. 이후 최대 60초를 파일 한도 안에서 추가하며 자동 장애 보존은 최소 120초 간격으로 합친다. 반복 장애·잘린 마지막 행·저장 실패·큐 누락을 성공적인 완전 수집으로 해석하지 않는다.

기록은 각 프로세스의 용량 256 비동기 큐를 사용한다. 새 wake lock/FGS/자동 업로드/무선 폴링/복구 타이머를 추가하지 않는다. 앱 OS 회수·사용자 강제 종료·전원 꺼짐 때 수집도 멈추며 큐 끝부분이 유실될 수 있다. 프로세스 시작마다 새 run ID가 생긴다. OS 회수와 강제 종료의 원인 구분은 이 기록만으로 확정할 수 없다. Android/커널/NetworkManager 전체 journal을 대신하는 수집기는 아니다.

## Jetson ZIP 내보내기

서비스가 실제 사용한 state root와 해당 코드의 Python 환경을 먼저 확인한다. 기본값 `/var/lib/jetson-control`을 현재 실행값으로 가정하지 않는다. P2P의 `/run` 상태 JSON 위치와 영속 진단 로그 위치는 다르다. 설치 후 일반적인 사용 형태는 다음과 같다. 경로는 실제 값으로 바꾼다.

```bash
<backend-python> -m jetson_control.diagnostics \
  --state-dir <actual-runtime-state-root> \
  --output <new-private-output-path>.zip
```

API/P2P의 state root가 다르면 `--state-dir`을 반복한다. 이 명령은 새 ZIP만 만든다. 기존 파일을 덮어쓰거나 서비스·Wi-Fi 설정을 변경하지 않는다. 실행 권한으로 읽을 수 있는 진단 파일만 포함하고 인증 키·설정·일반 journal은 수집하지 않는다. ZIP은 0600으로 생성하며 manifest의 `missingSources`, `skippedFiles`, `skippedLines`, `droppedEvents`, `storageFailures`를 확인한다. 실행 중 순환 파일의 스냅샷이므로 정확한 동시 정지를 주장하지 않는다.

내보낸 ZIP은 순환/보존 한도 밖의 사용자 파일이다. 분석 후 직접 보관·삭제한다. 자격 증명은 제외하지만 시각·사용 패턴·장비 빌드 지문은 들어 있으므로 공개 저장소에 올리지 않는다.

## 나중에 분석할 때의 기준

앱 schema 1 행은 `utcMs`, `elapsedMs`, `runId`, `seq`, `event`, `fields`를 가진다. Jetson schema 1 행은 `utcEpochMillis`, `elapsedMillis`, `clock`, `runId`, `seq`, `event`와 허용 필드를 최상위에 둔다. 두 형식의 필드 위치/시간 이름을 정규화해서 비교한다. 앱은 elapsedRealtime, Jetson은 CLOCK_BOOTTIME(불가 시 monotonic)을 사용한다. run ID/단조 시각은 서로 다른 프로세스·장치 사이에서 직접 비교하지 않는다.

- 같은 요청: 기존 요청 nonce로부터 `SHA256(UTF-8("STAB1:" + nonce))[:16 hex]`인 `requestRef`를 양쪽에 남긴다. nonce 원문이나 새로운 인증 헤더는 기록/추가하지 않는다. public hello에는 이 연결 키가 없을 수 있다.
- 앱 내부: Call별 `requestId`와 `clientId`, `endpointGeneration`, 연결 `sessionId`, 상태 요청 `requestSequence`, 복구 `attemptId`를 사용한다. 상태/복구 요청은 coroutine context → Retrofit Request tag로 전달되므로 전역 “마지막 요청”을 사용하지 않는다. 모든 UI 명령에 Repository session ID가 붙는 것은 아니다.
- 실제 경로: `api_connection`의 소켓 양끝 익명 주소·포트·connection ID와 같은 요청의 서버 수신/서명 검증 응답을 함께 본다. 익명 주소는 앱 실행마다 바뀌므로 실행 간 동일 주소 추정을 하지 않는다. `reused`는 이 수집기가 그 Connection 객체를 전에 관측했는지 뜻한다. 서버 `socketPath=unknown` 또는 앱 P2P Network 객체 부재만으로 실패를 확정하지 않는다.
- 시간 맞춤: 인증된 hello의 `api_hello_clock`은 **server minus client** offset 추정치와 왕복 시간/서버 초 단위 정밀도/앱 wall-clock 변화 오차를 남긴다. 이를 해당 구간의 참고로 사용한다. 나중 시계 조정·서버 응답 지연은 별도로 확인한다. 현재 시각 맞춤 동작을 변경하지 않는다.
- 인증: `api_call_ended`는 본문 전송 종료이며 HMAC 인증 성공이 아니다. `api_response`의 200도 인증 성공이 아니다. `api_authenticated`가 실제 응답 HMAC 검증 성공이고, `api_hello_clock authenticated=true`는 별도의 hello proof 확인 성공이다. Jetson `api_reply_sent`는 ASGI send 반환 관측이며 휴대폰 수신 증명이 아니다.
- 앱 반영: `status_refresh outcome=APPLIED/DISCARDED`로 현재 세션 반영/이전 세대 폐기를 구분한다. `transport_state reasonCode=API_THRESHOLD`와 `LINK_LOST`, `connection_intent`, 센서 freshness/age, RTK 상태·heartbeat·바이트 수를 각각 본다. RTK 제어 heartbeat 실패가 보정 데이터의 물리 손실을 곧바로 증명하지 않는다.
- P2P: Android `*_REQUESTED`, `*_OS_ACCEPTED`, 그룹 관측, target 승인, cleanup `CONFIRMED_ABSENT`를 구분한다. cleanup timeout/권한 없음/다른 그룹 보존은 `cleanupConfirmed=false`이다. Jetson unknown 조회와 command OS acceptance도 그룹 부재/해제 완료와 별개다.

기본 파일과 장애 파일에 같은 행이 중복될 수 있다. `(source, runId, seq)`로 중복을 제거하고, 사건마다 v3 §3.4의 여러 행 타임라인을 만든다. 기록 순서와 시계 오차로 L1–L4 최초 계층을 판단할 수 없으면 **UNKNOWN**이다. 무선 ADB의 단절은 제품 요청과 분리한다.

앱 시작/ZIP manifest에는 versionCode/versionName/build ID, 설치 base/split APK SHA-256, 서명 인증서 SHA-256가 있다. Jetson 시작 행에는 PID/시작 시각/run ID/code version/소스 파일 묶음 SHA-256가 있다. Jetson SHA는 시작 시 디스크 지문이며 임의의 hot edit와 실행 메모리의 일치를 증명하지 않는다. 설치 여부는 이 식별과 실제 런타임을 대조한다.

## 사용자 재개용 빌드 명령 — 이번 작업에서는 실행하지 않음

작업 브랜치에서 다음처럼 전체 검증과 APK 빌드를 실행할 수 있다. build ID는 아래 명령을 실행하는 실제 HEAD이다. 미커밋 수정이 있으면 별도로 기록한다.

```bash
GRADLE_USER_HOME="$PWD/.mobile-build/gradle-home" nice -n 10 ./gradlew \
  :app:assembleDebug :app:lintDebug :app:testDebugUnitTest \
  -PdiagnosticsBuildId="$(git rev-parse --short=12 HEAD)" \
  --max-workers=1 --console=plain
```

이 명령은 설치를 포함하지 않는다. 신규 Android 진단/회귀 테스트는 작성만 했으므로 **NOT RUN**이다. 현재 `app/build/outputs/apk/debug/app-debug.apk`가 존재하더라도 이전 작업의 산출물이다. 이 로그 기능이 반영된 신규 APK라고 배포하지 않는다.
