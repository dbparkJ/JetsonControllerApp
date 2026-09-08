# 연결 안정성 v3 진단 — 2026-09-08

> 최신 추가분: 상시 로그 소스 구현은 완료했으나 사용자 지시로 앱 빌드·Android 테스트는 **NOT RUN**입니다. 아래 기존 PASS/APK 기록은 이전 소스의 결과입니다. 문서 끝의 상시 진단 추가분과 [사용 안내](CONTINUOUS_LOGGING.md)를 함께 확인하세요.

## G0 초기 기록 (2026-09-08 01:55 UTC, 제품 동작 수정 전)

- 기준: 사용자 제공 `../JetsonControllerApp_CONNECTION_STABILITY_AI_AGENT_v3.md` §0–§2 및 §9. 가설을 해결책으로 간주하지 않는다.
- 시작 HEAD `d9764e756ae64161acd12c9e7bf93ca18ea98909`, `main...origin/main`. 미커밋 변경은 사용자가 제공한 v3 문서 1개(untracked)뿐. 기존 tracked 수정·stash 없음. 상위 디렉터리 및 저장소 AGENTS.md 없음. 루트 README 없음; docs/README.md, 빌드·CI·기존 진단 기록 확인.
- 작업 브랜치 `codex/connection-stability-v3-20260908`. 원격 fetch/push/PR/merge 없음. 사용자 문서와 이전 진단 기록 보존.
- **G0 PARTIAL:** 개발 호스트 자체가 Linux aarch64 Jetson (kernel 5.10.216-tegra, Ubuntu 20.04.6, L4T R35.6.0). 로컬에서 서비스 조회 가능. SSH localhost는 엄격한 host-key 검증에서 차단; 검증을 우회하지 않음. 별도 독립 관리 경로 미확보.
- 초기 `adb devices -l`: 장비 0대. `/usr/bin/adb` 39가 기존 server 41과 충돌하여 자동 서버 재시작 발생. 의도한 종료는 아니지만 수집 경로 교란이며 이전 무선 ADB 세션 상태는 확인 불가. 제품 장애로 세지 않는다. 이후 별도 서버/버전 호환 경로 사용.
- **앱만 갱신되고 백엔드는 이전 상태인가: 판단 불가.** 현재 설치 앱 식별 불가. HISTORY에는 앱만 갱신한 기록이 있으나 LIVE 증명으로 사용하지 않음. 이후 사용자가 지정한 현재 무선 ADB 대상으로 읽기 재조회 예정.
- API: active, PID 1398, NRestarts=0, WorkingDirectory `/opt/jetson-control`, unit `/etc/systemd/system/jetson-control-api.service`; ExecStart `/opt/jetson-control/venv/bin/uvicorn`, module `jetson_control.asgi:app`.
- P2P: active, PID 1399, NRestarts=0, 같은 WorkingDirectory, unit `/etc/systemd/system/jetson-wifi-direct.service`; `/usr/bin/python3 -m jetson_control.wifi_direct`.
- 두 서비스 시작시각은 systemd가 `1970-01-01 09:00:38 KST`로 반환: 부팅 시 wall clock 부정확. 현재 2026 시각과 직접 뺀 가동시간은 무효. `/proc/PID/{cwd,exe}` 읽기는 PermissionError. executable/module은 읽힌 cmdline과 unit에서 한정 추출, 환경/비밀은 출력하지 않음.
- 배포 디스크에는 `.git` 없음. API/status/P2P 파일은 저장소와 다르고 mtime은 9월 1일/8월 21일; auth.py는 동일. 런타임 build identifier 미확보: 디스크 해시만으로 실행 Python이 해당 코드를 로드했다고 확정하지 않음. 기존 상태 baseline 보존, 자동 배포/재시작 없음.
- 프로토콜 호환성: **미확인**. 버전 차이와 TLS/HMAC/hello/status 실제 호환성은 별개.
- 로컬 기존 APK SHA-256 `49afd594a355d8c1a290b1f341c5ea079796a278fe1f391cbf834e16fef46334`, 소스 applicationId `com.example.jetsoncontroller`, versionCode 22, versionName 1.15.3, target/compile SDK 37. 설치 앱과 동일하다는 증거 아직 없음. 서명·build ID 대조 추가 필요.
- JDK 기본 21.0.12, Gradle daemon 요구/설치 25, wrapper 9.5.0, SDK `.mobile-build/android-sdk`, Python 기본 3.8.10. 빌드/테스트는 프로젝트 환경으로 실행 예정.
- 무선 capabilities: managed≤1, AP/P2P-client/P2P-GO≤1, P2P-device≤1, total≤3, channels≤2. 이것만으로 현행 링크/동시 사용 성공을 단정하지 않음. 앱 endpoint/GO/client/실제 요청 경로/운영 작업은 현재 미확인.
- 승인: 저장소 읽기·작업 브랜치 로컬 테스트/확인 결함 최소 수정. 설치·재시작·장애 주입·운영 중단·네트워크 설정 변경 미승인. USB ADB/독립 관리 경로/롤백/구체 승인 없는 T6/T9는 BLOCKED.

## 기존 보호 로직 (CODE)

- `AutomaticConnectionPolicy.IP_STATUS_FAILURE_LIMIT=3`; `refreshStatus`는 current session/request 검사 후 연속 실패 집계. 1회 즉시 해제가 아님.
- `TransportCoordinator`: attempt/session 수명 분리, 장비·세대·요청 순서 보호.
- Repository 기존 자동 연결 owner/Job, LAN/BLE/Direct 중재와 명시적 Direct 우선·일반 Wi-Fi 보호·backoff 존재.
- `WifiDirectConnectionSession` 및 Manager: 세대/대상 그룹 보호, 8초 cleanup, 채널 복구·인터페이스 기반 주소 대체 탐색.
- `LocalApiClient`: 8초 status/hello/capabilities 제한, 인증 갱신 Mutex/revision, TLS pin/hello proof/응답 HMAC·취소 전파.
- 기존 MobileRtkRelayService/Manager 및 셀룰러 경로 owner 있음. 서비스 추가 안 함.
- 백엔드 저장소: 상태 snapshot/cache, P2P worker/attempt guard·120초 deadline·DHCP/peer/driver 처리. 실제 배포 반영은 별도 검증 대상.

## 증거와 제한

초기 현장 끊김을 관측하지 않았음. FIELD_CAUSE 미확정, 최초 계층 UNKNOWN_FIRST_LAYER. ADB 없음/서버 재시작은 제품 L1 소실 증거가 아님. 이후 CODE/LAB/LIVE와 원인 판정/실행 상태를 분리해 추가한다. 원시 증거는 git 제외 `artifacts/`에 마스킹 후 저장하고 공유 문서에는 별칭 사용.

## G0 추가 실측: 사용자가 지정한 무선 ADB (01:58–02:07 UTC)

- 사용자 지정 endpoint를 `PHONE_A`로 표기. 기존 5037 서버를 추가 변경하지 않고 SDK 37.0.1/ADB 1.0.41 + 기존 QEMU로 작업 전용 5040 server 사용. 첫 자동 fork는 Exec format error로 실패했고, 명시적 `nodaemon server`로 접속 성공. 해당 세션만 종료 시 정리한다.
- LIVE: SM-S908N, Android 16/API 36. 앱 v1.15.3(22), targetSdk 37, PID 23290, stopped=false. 마지막 갱신 `2026-09-08 09:35:32`(device package 출력, timezone 확인 한계). base.apk 1개, split 추가 없음. 근처 Wi-Fi/위치/Bluetooth/알림 등 조회된 runtime 권한 granted=true. 화면 Awake, 배터리 구동, 충전 아님, 강제 idle 아님, ACTIVE.
- 설치 APK SHA-256가 로컬 APK와 정확히 같음: `49afd594a355d8c1a290b1f341c5ea079796a278fe1f391cbf834e16fef46334`. 같은 바이너리를 apksigner로 검증(exit0), 인증서 SHA-256 `7e78141e5f83032ca3092c0691df3ca2ef5ce3c3dbba18cabec3d8c7405cb66a`. 소스 commit 내장 build ID 없음. SHA 일치는 바이너리 동일성의 증거이며 소스 provenance의 암호학적 증명은 아님. HISTORY의 f68673c 산출물과 hash 일치.
- 배포 디스크 API/status/P2P는 각각 과거 e15309c/1b015c1/fc579e8의 파일 blob과 일치. 592d192의 snapshot/worker 안정화 미반영 확인. `/proc/stat` start tick으로 약 8,225초 가동 확인, systemd 1970 wall timestamp 대체의 상대 시간만 사용. 모듈 런타임 hash 미확보.
- **정합성 판정 유지: 판단 불가(실행 코드 확정 범위). 설치 앱 갱신 + 백엔드 디스크 구버전은 확인.** 프로토콜 비호환은 미확인. auth/asgi 파일 일치만으로 handshake 성공을 대신하지 않음. 구버전 baseline은 유효하며 폐기/자동 갱신하지 않는다.
- 현재 UI 관측: `전체 제어 연결이 필요합니다. 작성한 내용은 유지됩니다.`; 연결 허브에는 `기본 연결` 및 별도 `등록됨` 카드. 선택 대상 추가 확인 요청. 앱을 종료하지 않고 기존 activity만 전경으로 가져옴(HOT 107ms). 데이터·설정·명령 변경 없음.
- 02:06:43 Jetson `iw dev`: managed + P2P-device, P2P client/GO 없음. NM wifi connected, wifi-p2p disconnected. 이는 이 순간의 그룹 부재이며 과거 끊김 시각/원인의 증거는 아님. API journal 해당 시각 창에 visible record 0; 요청 실패나 성공 어느 쪽도 단정하지 않음.
- 무선 ADB 접속은 USB·독립 관리 경로를 대체하지 않는다. G0는 여전히 PARTIAL. 설치/재시작/T6/T9 미승인·안전 경로 미확보.

## 시계·마커·자료 관리 (§3)

PC와 Jetson은 같은 호스트이므로 호스트 간 offset 0(별도 장비가 아님). 폰 epoch 조회 전후 host wall clock midpoint를 5회 측정. 02:00:03 UTC 최소 RTT 105.8ms 표본에서 phone-host offset 약 +47.5ms, 최소 불확실성 ±52.9ms. 전체 offset +21.0~+51.8ms, RTT 105.8~157.7ms; 통신 비대칭 미측정이므로 정밀한 인과 순서는 보장하지 않는다. 서로 다른 monotonic clock을 빼지 않았다. 장비 시각 강제 변경 없음.

폰 `case=G0-WIRELESS event=START` 마커 수신 epoch 1788832804.937. 이는 baseline 수집 마커이며 T1 시험 시작이 아니다. 앱 per-request/session 로그가 없고 연속 인증 응답 증거가 없으므로 끊김 계층을 강제로 확정하지 않는다. 로그 buffer 삭제/pcap/전체 payload 수집 없음. UI는 저장소의 정적 문구 allowlist만 저장해 장비명/파일명/좌표를 제외. 원시 설정/키는 읽어 출력하지 않음.

자료: `artifacts/20260908-015500_G0/`의 adb-access/adb-wireless/phone-baseline/phone-session/ui-allowlist/ui-connection-help/live-api-wireless/apk-identity 및 backend-* JSON·로그. 디렉터리 0700, git 제외, raw 로그 보존 목표 7일·총 100MiB 상한(자동 삭제 예약 없음; 종료 시 크기 확인). APK 백업은 별도 보호 저장소에 분리.

## G1: 수정 전 결정적 LAB 재현

2026-09-08 02:02:16.225–02:02:40.356 UTC, 24.131초, Gradle exit1, 6 tests 중 4 assertion FAIL. 테스트 하네스/의존성만 추가한 d9764e7 Android 제품 코드에서 실행했다. `android-baseline.json/.log` 및 `android-baseline-xml/*.xml` 보존.

- H1: 실제 Repository/Coordinator의 status 요청 3회 실패, fake L1 present 유지, Direct cleanup 요청 1회·RTK stop 요청 1회. N−1회 보호는 PASS. 확정 범위는 CODE_DEFECT, 실제 OS group removal은 관측하지 않음.
- H2: 실제 Repository의 사용자 Direct 선택→비사용자 링크 소실 collector→manualDirect=false. CODE_DEFECT 확정. 일반 Wi-Fi에서 LAN 업그레이드 정책이 허용됨; 실물 LAN 전환 발생은 미확인.
- H13: 실제 public LocalApiClient→Retrofit→OkHttp→loopback TLS/hello proof/HMAC 경로에서 pipeline restart를 처리한 뒤 응답 서명 손상→서버 mutation **2회**(기대 1회). 별도 upload 응답 유실은 mutation 1회, 결과 조회 **0회**(기대 1회). 테스트 전용 키만 사용. CODE_DEFECT 확정, FIELD_CAUSE 미확정.
- H12: 실제 `WifiDirectController.monitor()` + 기존 FakeRunner의 READY/그룹/DHCP 유효 상태에서 `iw dev` 또는 address query의 exit1→기존 코드가 cleanup으로 확대. 별도 timeout subcase는 `WifiDirectError`가 monitor 밖으로 전파되어 ERROR가 났으며 cleanup 사건과 구분한다. `backend-unknown-group-reproduction.json`, `backend-r15-before.*`의 기대 안전동작 assertion 실패로 CODE_DEFECT 확정. 운영 명령 주입 아님.

## 다중 행 타임라인 (§3.4)

표의 LAB elapsed는 논리 순서이며 가짜 스케줄러/동기 호출 순서를 물리 시간처럼 표기하지 않는다. 원본 wall 시각은 test XML/JSON의 실행 구간이다. attempt/session/request는 fixture의 현재 세션 또는 HMAC 검증된 요청을 뜻한다; 원문 nonce/signature 출력 없음.

| case/event | 원본 시각·호스트 / 보정 UTC·오차 | elapsed | 채널·증거 | 관측 사실 | 계층 | attempt/session/request | 해석·제한 |
|---|---|---|---|---|---|---|---|
| LIVE-BASE/1 | 02:00:02–04 host, phone 보정 ±최소53ms | 수집 시작 | phone-baseline.json | 앱 PID 존재, Awake/ACTIVE | UNKNOWN | 앱 세션 불명 | 최초 정상 요청 미확보 |
| LIVE-BASE/2 | 02:02:39 host | 약 2분 | ui-allowlist.json | 전체 제어 필요 안내 | L4 표시 | 불명 | 사용자가 해제했는지/장애인지 불명 |
| LIVE-BASE/3 | 02:05:12 host | 약 5분 | ui-connection-help.json | 기본 연결만 표시 | L4 표시 | 불명 | BLE/전체제어 구분, FIELD_CAUSE 미확정 |
| LIVE-BASE/4 | 02:06:43 host | 약 6분 | live-api-wireless.json | P2P GO/client 없음, visible API logs 0 | L1 snapshot / UNKNOWN | 불명 | 최초 계층 UNKNOWN_FIRST_LAYER, 끊김 횟수로 세지 않음 |
| LAB-H1/1 | 02:02:39 XML host, 같은 호스트 순서 | setup | JetsonRepositoryStabilityTest XML | manual Direct, active current transport, fake link present | L4→fixture L1 | current session | 인증 transport 경계 fake, 프레임워크 실기 아님 |
| LAB-H1/2 | 같은 test 구간 | status1,2 | 같은 XML/R1 | 실패2회, cleanup0 | L3 fault/L4 | current status requests | 기존 임계 보호 PASS |
| LAB-H1/3 | 같은 test 구간 | status3 | 같은 XML/R2 | 실패3회, cleanup1, RTKstop1 | L4 | current session | cleanup 요청과 실제 그룹 제거는 별개 |
| LAB-H1/4 | 같은 test 구간 | assertion 종료 | android-baseline.log | expected cleanup0 FAIL | L4 | 해당 test 종료 | 최초 주입 API 장애, 증폭 코드만 확정 |
| LAB-H2/1 | 02:02:39 XML host | setup | Repository XML/R7 | 수동 Direct, 일반 Wi-Fi 존재, LAN upgrade 억제 | L4 | target=current | 사용자 의도 설정 |
| LAB-H2/2 | 같은 test 구간 | flow=false | 같은 XML | 비사용자 링크 소실 전달 | fixture L1 | current | 실제 RF 장애 아님 |
| LAB-H2/3 | 같은 test 구간 | collector 후 | 같은 XML/stdout | manualDirect=false, transport Disconnected | L4 | current | 잘못된 의도 초기화 확정 |
| LAB-H13/1 | 02:02:36.410 XML host | handshake | LocalApiClientReplayTest XML | TLS pin/hello proof 확인 후 첫 mutation 처리 | L3/L4 | signed request1 | loopback fixture |
| LAB-H13/2 | 같은 test 구간 | response1 | 같은 test fixture | 첫 응답 HMAC 손상 | L4 | request1 | 명령 미실행 증거가 아님 |
| LAB-H13/3 | 같은 test 구간 | retry | 같은 XML | 같은 restart 재실행, server count2 | L4 | request2 | mutation 기대1 FAIL |
| LAB-H13-UP/1 | 같은 suite 구간 | mutation1 | 같은 XML/upload test | upload 수락 뒤 응답 연결 종료 | L3 | signed request1 | 서버 처리 후 응답 유실 |
| LAB-H13-UP/2 | 같은 suite 구간 | return | 같은 XML | 결과 조회0, expected1 FAIL | L4 | 결과 불명 | 원래 명령 성공 여부 단정 금지 |
| LAB-H12/1 | backend reproduction JSON UTC | setup | backend-unknown-group-reproduction.json | READY, group/DHCP present | fixture L1/L4 | existing owner | 정상 fixture |
| LAB-H12/2 | 같은 동기 실행 구간 | monitor tick | 같은 JSON/재현 스크립트 | iw dev exit1, 출력없음 | L4 observation failure | current owner | peer 부재 아님 |
| LAB-H12/3 | 같은 동기 실행 구간 | cleanup | 같은 JSON | group_remove1/DHCP 종료/상태 DISCOVERABLE | L4 증폭 | current owner | 실제 Jetson 무선 변경 아님 |

## H1 사전 수정·회귀 예산 (02:09 UTC, H1 수정 후 결과 확인 전)

H1 Direct API 복구는 기존 owner와 3회×20초 + 750/1500ms 지연=62.25초의 기존 probe 한도를 재사용하는 설계를 검증한다. 추가 무한 episode나 새 FGS를 만들지 않는다. 예산 소진 후 unavailable 상태에서 명시적 재시도/새 유효 링크 사건의 안전한 재개를 검증한다. RTK 유효 lease 증거가 없는 경우 기존 보수적 stop을 유지하며 R9 전체 통과로 쓰지 않는다. 이 수치는 LAB 코드 예산이며 실기 복구 성능의 사후 합격 기준이 아니다.

H12의 다음 수치는 이미 구현된 정책을 확인한 것이며 02:09에 새로 고정한 사전 실기 기준이 아니다. 기존 2초 monitor owner, 외부 조회 명령당 기존 15초 한도를 유지한다. unknown 관측은 ERROR로 표시하고 group/DHCP를 파괴하지 않으며, 이후 유효 관측에서 READY 또는 확인된 소실의 기존 복구로 재개한다. UNKNOWN 상태를 정상으로 위장하지 않는다.

## H1–H14 원인 판정과 실행 상태

모든 FIELD_CAUSE는 **미검증**이다. 확인된 코드 결함과 현장 원인을 합치지 않는다. `PASS`는 아래에 지정한 LAB 범위만 뜻한다. 실제 자동 실행 수치는 VERIFICATION의 ledger/최종 결과를 따른다.

| H | 원인 판정·확정 범위 | 증거 | 구현/실행 상태·반증·제한 |
|---|---|---|---|
| H1 | 확정 CODE_DEFECT | Repository 실제 refreshStatus 3회실패→group cleanup 요청1 | 기존 group 보존+제어 ERROR+기존 probe 복구로 수정. 최종 Repository21/전체174 LAB PASS. 초기 그룹 최초검증 실패의 기존 cleanup은 범위 밖. RTK stop 유지, 유효 lease 유지 부분 미충족 |
| H2 | 확정 CODE_DEFECT | 소실 collector→manualDirect false; 추가 화면dispose→stopDiscovery에서도 flag 해제 재현 | 두 비사용자 의도 초기화 경로를 최소 수정. 명시 사용자 cancel/disconnect/LAN선택은 보존. disk persistence 추가 없음 |
| H3 | 미검증 CODE 후보 | cleanup timeout도 IDLE이나 다음 connect는 group 재조회 보호 있음 | Android Manager 협상 미확정/nullgroup/latecallback 결정적 하네스 NOT RUN. 수정 없음. 후속 BUSY 추측만으로 처방 안 함 |
| H4 | 미검증(현장 충돌) | 기존 owner/phase/Jobs, 새 Direct API owner취소 테스트 | R6 LAB 일부 PASS, 모든 실제 trigger 상충은 NOT RUN. 새 연결관리자 추가 안 함 |
| H5 | 기존 가드 일부 PASS; H13 조회세대 누락은 CODE_DEFECT | Coordinator/Repository late result 회귀 + Client same-URL reset/조회도중reset 실패 | 기존 가드 보존, Client endpoint 세대를 보강. 모든 framework callback 검증으로 확장 해석 안 함 |
| H6 | 미검증 | LIVE Awake/ACTIVE·배터리 snapshot, 기존 RTK FGS CODE | 자연절전/Doze/OS회수/force-stop NOT RUN 또는 BLOCKED. 새 FGS/wakelock/예외 설정 없음 |
| H7 | 미검증 | API 기본client, RTK cellular socket factory CODE | 같은 앱 request의 실제 route/auth response LIVE 미확보. default cellular 여부로 오라우팅 단정 안 함 |
| H8 | 미검증; 제시된 단순판정 근거 없음 | Network미제공시 인터페이스/경로 fallback CODE·기존단위테스트 | 미노출 자체를 오류/DEGRADED로 바꾸지 않음. 인증된 실기경로 T8 BLOCKED |
| H9 | 미검증 | idle/RF/pool 원인 상관증거 없음 | power_save A/B/유휴요청 시험 NOT RUN, 무선 설정 변경 없음 |
| H10 | 미검증(경쟁/풀 원인) | 기존 status8초/일반45초 및 공유 OkHttp 자원 CODE | 부작용 재전송 차단은 H13으로 검증. 파일/프리뷰/status 경쟁 부하 NOT RUN |
| H11 | 미검증(현장 주소변경); Client 세대누락은 CODE_DEFECT | 주소선택 기존 tests, 새 endpoint reset 경계 tests | fallback/NSD 기존 개선 보존. 실제 endpoint변경 T3/T8 NOT RUN |
| H12 | 확정 CODE_DEFECT | iw/ip 조회오류→정상그룹/DHCP cleanup 실제 monitor LAB | 7776d65 최소 수정; 66 관련/211 전체 PASS. 배포 디스크 구버전 별도 확인, 현장 원인은 미검증 |
| H13 | 확정 CODE_DEFECT | 실제 TLS/HMAC 경로의 mutation2회/조회0회, nullbody/세대/RTK 증폭 추가 재현 | 결과UNKNOWN+bounded조회/재전송0/세대가드로 수정. 실제 백엔드 호환성/인증 재시작 T9 BLOCKED |
| H14 | 미검증 | 기존 explicitDisconnect/credentials flow/RTK lifecycle CODE | 세션 내 intent 보존, R3 fake10분 PASS. 디스크복원·키로딩·OS재생성/사용자force-stop 실기는 NOT RUN |

추가 독립 결함 **D1 (H5/H13, R8)**: 같은 TLS 인증서를 쓰는 다른 등록 deviceId를 기존 hello 재인증이 자동 채택할 수 있음. synthetic 장비 두 대/서로 다른 key/같은 test certificate로 현재 public client에서 02:16:48–02:17:12 실행 FAIL(다른 장비를 거부해야 하나 채택). 최초 인증 장비를 endpoint 세션에 고정하고 명시 updateEndpoint 시에만 새 장비 인증을 허용하는 최소 가드를 별도 검토 묶음으로 적용한다. 현장 장비 spoof/키 유출을 관측했다는 뜻이 아니다.

## 추가 결함의 사건 순서

| case/event | 원본 구간·호스트/보정 | elapsed | 증거 | 사실 | 계층 | 세대/요청 | 해석 |
|---|---|---|---|---|---|---|---|
| LAB-H13-EPOCH/1 | 02:16:48.680–02:17:12.642 UTC host | test 순서1 | android-h13-boundary-before XML | 기존endpoint의 command 처리 뒤 응답불명 | L3/L4 | old endpoint epoch | 서버 mutation1 |
| LAB-H13-EPOCH/2 | 같은 구간 | 순서2 | 같은 XML | 동일URL reset 또는 조회도중reset | L4 | new epoch | URL문자열동일은 같은세션 증거 아님 |
| LAB-H13-EPOCH/3 | 같은 구간 | 순서3 | 같은 XML | 이전명령 조회/결과 귀속이 계속됨, expected cancellation FAIL | L4 | old/new 교차 | 세대 경계 코드 결함 |
| LAB-H13-EPOCH/4 | 02:20:05.695–29.873 UTC host | 후속검증 | android-h13-after-h2-dispose-before XML | 전후guard 보강후 내부read retry가조회2회 실행, 기대1 FAIL | L4 | old query retry | 해당 실패 보존, assertion 완화 없이 내부retry guard 보강 |
| LAB-H13-RTK/1 | 02:16:48.680–02:17:12.642 UTC host | setup | Repository XML | 현재Direct 작업 시작 준비/RTK 준비 | fixture L4 | current session/pipeline | public Repository, hardware mock |
| LAB-H13-RTK/2 | 같은 구간 | query 후 | 같은 XML | command UNKNOWN, 같은pipeline RUNNING 관측 | L4 | current | 명령 receipt 성공 증거와 구분 |
| LAB-H13-RTK/3 | 같은 구간 | 반환전 | 같은 XML | 기존 stop1, 기대0 FAIL | L4 | current | 정상 관측이 있는 RTK의 불필요 중지 |
| LAB-H2-DISPOSE/1 | 02:20:05.695–29.873 UTC host | setup/loss | Repository XML | 수동Direct→비사용자소실 이후 intent 유지 | fixture L1→L4 | current | H2 첫 수정상태 |
| LAB-H2-DISPOSE/2 | 같은 구간 | 화면dispose | 같은 XML | stopWifiDirectDiscovery 호출 | L4 | current | UI정리와 사용자전송취소는 별개 |
| LAB-H2-DISPOSE/3 | 같은 구간 | 반환 | 같은 XML | explicitDirect false, 기대true FAIL | L4 | current | 별도 남은 초기화 경로 확인 |

## 현장 baseline 수집 종료

02:20:45–50 UTC: PHONE_A ADB device, 앱 PID23290; API1398/P2P1399 active/NRestarts0 확인. 폰 END 마커·Jetson END logger 요청 exit0. Jetson START는 발행하지 않았으므로 완전한 양호스트 시험마커 시퀀스라고 주장하지 않는다. 중간 연속 요청/인증응답/전체 가용성 증거가 없어 이 약20분 구간을 T1/T5 PASS나 INCOMPLETE로 사용하지 않는다. 장비 조작으로 장애를 주입하지 않았고 새 APK/백엔드 설치·재시작도 없다.

### LIVE-ADB-01: 수집 종료 때 확인한 무선 ADB 단절

작업 전용 ADB server의 보관 stdout을 닫는 과정에서 **시험 인프라의 자연 단절**을 발견했다. 전체 로그는 별도 마스킹 저장하지 못했고 도구 반환도 잘렸으므로 보이는 사건만 `adb-infrastructure-timeline.json`에 마스킹해 전사했다. 앱 로그/Jetson 같은 요청과 상관되지 않아 제품 끊김 횟수로 집계하지 않는다. 초기 `현장 끊김 미관측` 기록은 제품 연결에 한정하며, 인프라 단절 관측은 이 추가 기록으로 보정한다.

| case/event | 원본 시각·호스트 | 보정 UTC·오차 | elapsed | 증거 | 관측 | 계층 | request/session | 해석 |
|---|---|---|---|---|---|---|---|---|
| LIVE-ADB-01/1 | host09-08 10:58:52.878 KST | 01:58:52.878, host clock | 0 | ADB server stdout excerpt | PHONE_A ADB TLS handshake 성공 | 시험 인프라 | ADB transport | 앱 TLS와 별개 |
| LIVE-ADB-01/2 | host11:00:28.615 KST | 02:00:28.615 | +95.737s | 같은 excerpt | SSL_read timeout, transport 종료 | 시험 인프라 | ADB transport | 앱 L1–L4 미확인 |
| LIVE-ADB-01/3 | host11:00:29.638 KST | 02:00:29.638 | +96.760s | 같은 excerpt | ADB 재접속 No route to host | 시험 인프라 | ADB transport | shell/ADB 경로 오류만 확인 |
| LIVE-ADB-01/4 | host02:00:59.426575 UTC | 같은 UTC | +126.549s | phone-session.json | ADB am start HOT 성공 | 시험 인프라 | 새 ADB command | 이 시점 이전 복구, 정확한 복구시각 불명 |
| LIVE-ADB-01/5 | host11:23:09.225 KST | 02:23:09.225 | 수집 종료 | collector-cleanup.json/stdout | 작업 전용 server 종료 후 EOF | 의도한 수집 종료 | task-owned server | 자연 단절/제품 장애에 집계 안 함 |

**UNKNOWN_FIRST_LAYER / FIELD_CAUSE 미확정.** 같은 시간 앱 인증된 요청의 성공·실패를 확인하지 못했다. 최종 앱 PID 동일성도 단절 구간의 정상 동작 증거가 아니다. QEMU 지원 경고는 관측 부하/수집 한계이며 제품 원인으로 승격하지 않는다. 원시 server stdout에는 endpoint와 종료 요청 프로세스 계보가 포함돼 있었으나 공유 문서·저장 증거에서는 제외했다. 초기 콘솔 출력의 식별자는 소급 마스킹할 수 없다는 제한을 남긴다.


## 최종 상태

- G0 **PARTIAL**: PHONE_A 무선ADB 및 Jetson 로컬 읽기 확인. 설치 baseline 식별은 확인했지만 실행 backend 모듈/앱 동일요청 경로·프로토콜은 미확인. USB/독립 관리경로·설치/주입 승인은 없음.
- G1 **CODE_DEFECT 확정**: H1/H2/H12/H13와 D1 및 해당 세대/RTK 증폭 경계. FIELD_CAUSE는 전부 미확정. LIVE-ADB-01은 ADB 인프라 단절이며 제품 최초 계층 UNKNOWN.
- 구현: 확인 결함 범위 로컬 수정 완료. 전체 연결 안정성 요구는 **부분 완료**: 특히 H1에서 API와 RTK lease 건강의 완전 분리는 미구현이며, 상태API 실패시 기존 RTK stop을 보수적으로 유지한다.
- 자동 검증: Android174, backend211 PASS. assemble/lint PASS(0errors/72warnings). 실패했던 baseline 및 중간 보강 실패를 별도 보존했다.
- G3: T별 **BLOCKED/NOT RUN**, 실제 시작한 장시간시험0. 설치·재시작·운영중단·무선설정변경·장애주입·push/PR/merge/배포 없음.
- 범위 조정 이유: 실제 현장에서는 인증된 전체 제어 세션이 없고 대상 카드 확인 응답도 받지 못해 안전한 유지시험의 baseline을 만들지 못했다. 과거 기기·주소·배포상태로 대신하지 않았다. 접근 가능한 로컬 결정적 재현/최소수정/전체자동검증까지 수행했고, 실기시험을 짧은smoke나향후자동작업으로 대체하지 않았다.

수정된 Direct API recovery는 기존 probe3회/20초 및750/1500ms 지연을 재사용한다. SSLException 전체를 영구 인증불일치의 현장 증거로 보는 것은 아니다. SSL/등록동일성/typed auth failure는 자동episode를 보수적으로 멈추고 명시 재시도를 기다리는 정책이며, 개별 현장 실패 인과는 미검증이다. 초기 신규 그룹 검증의 기존 cleanup/time-sync 및 LAN 전체 재시도 상한/jitter/재구성 초기화 후보는 범위 밖으로 보존했다.


## 추가 작업: 상시 연결 진단 기록 (2026-09-08, 사용자 빌드 보류)

사용자가 향후 끊김 시점을 사후 분석할 수 있도록 앱과 장치 양쪽의 영속 로그를 요청했고, 이어 **앱 빌드 전까지만 진행, 빌드는 사용자가 점심 후 별도 수행**하도록 범위를 조정했다. 앞선 G0–G3 기록과 baseline은 보존한다. 이 단계에서는 ADB/SSH 조회·설치·서비스 재시작·장애 주입을 추가 실행하지 않았다. 앞선 G0 PARTIAL의 접근/배포 식별 제한은 해소되었다고 간주하지 않는다. 앱만 새로 설치되고 백엔드가 이전 상태인지는 이번 단계에서 **판단 불가(새 런타임 조회 없음)**이며, 이번 소스 변경 자체의 양쪽 설치/배포 횟수는 0이다.

**원인 판정:** 기존 H1–H14의 FIELD_CAUSE 판정을 변경하지 않는다. 실제 사용자 끊김 원인은 여전히 미확정이다. 이번 계측은 원인 가설을 해결책으로 구현한 것이 아니라 이전의 증거 부족을 보완하는 기능이다. 새로운 실제 끊김 타임라인은 0건이다. 로컬 테스트의 합성 요청/장애 파일을 실제 사건으로 등록하지 않는다.

**구현:** 앱 내부 순환/장애 로그와 오프라인 설정 화면·수동 시점 표시·SAF ZIP, Jetson API/P2P 각 영속 순환/장애 로그와 ZIP CLI를 추가했다. 실제 API Call별 requestRef/소켓 관측/인증 결과, 현재 세션 반영/폐기, API 임계값/복구 owner 세대, 사용자 의도, P2P 요청/OS 수락/그룹 확인/해제 미확정, 센서 freshness/RTK 상태를 분리한다. APK/서명/build ID와 backend 시작 PID/코드 지문도 남긴다. logger 예외가 제품 요청으로 전파되지 않도록 보호하고 새 연결 정책·재시도·타임아웃·서비스 수명은 추가하지 않았다.

**검토 중 보강한 진단 결함:** 저장/시계 예외가 ASGI 요청/원래 send 예외를 바꾸는 경계, 시계 역행 시 활성 파일을 보호하다 보관 개수 한도가 초과되는 경계, Android SAF 출력이 느릴 때 writer를 점유하는 경계를 보강했다. 이들은 새 진단 기능의 내구성 검증이며 기존 사용자 장애의 원인 확인이 아니다.

H/R 연결: H1/H4/H9 ↔ R1/R2/R6/R13(API 실패·복구), H2/H5/H14 ↔ R3/R4/R7(의도·세대·수명), H3/H12 ↔ R5/R10/R15(P2P 관측/해제), H6/H14 ↔ R14(전경/백그라운드/절전 관측), H7/H8/H11 ↔ R11(실제 소켓/요청 연결), H10 ↔ R9/R16(센서 신선도·API 처리), H13 ↔ R8/R12(서명 검증·UNKNOWN 조회). **모든 H의 원인 판정은 기존 그대로, 계측 구현 완료와 실기 검증 상태는 별개**다.

사용법·형식·용량·수집 한계·사용자 재개 빌드 명령: [CONTINUOUS_LOGGING.md](CONTINUOUS_LOGGING.md). 자동 검증은 아래 VERIFICATION 추가 항목을 따른다. 앞선 Android 174 PASS와 이전 APK는 이 새 계측 소스를 검증하거나 포함하지 않는다.

로컬 구현 커밋: `d3a1a89`(Jetson), `cd2cf4f`(Android/CI 선택 목록). 원격 반영 없음. [소스 지문·실행 여부·이전 APK 구분](../../artifacts/20260908-continuous-logs/implementation-manifest.json).
