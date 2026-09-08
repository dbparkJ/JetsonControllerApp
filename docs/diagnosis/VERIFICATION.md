# v3 검증 기록 — 2026-09-08

G0 PARTIAL 기록 후 로컬 검증 진행. 실제 실행 결과는 아래에 추가한다. 기존 보고 PASS는 이번 PASS로 승격하지 않는다.

실기 목표 유지: T1 LAN 30분, T2 Direct 30분, T3 각 10회, T4 잠금 10분, T5 백그라운드 30분, T7 해제 후 10분. T6A API와 T6B 링크 각각 3/10/60초. 무선 ADB만으로 T6/T9 안전 조건을 충족하지 못한다. 아직 시작하지 않은 시간은 0이며 짧은 smoke로 대체하지 않는다.

## 실행 ledger (UTC)

초기 baseline 제품은 d9764e7이며 테스트 fixture만 추가해 실행했다. 이후 경계 재현은 표에 명시된 중간 수정 상태다. 각 명령·exit·monotonic 경과·UTC·XML은 git 제외 `artifacts/20260908-015500_G0/`에 보존한다. 아래 PASS는 해당 실행한 자동 범위만 뜻한다.

| 실행 | 실제 시작–종료 UTC | wall 시간·실제 횟수 | 상태/exit | 증거 |
|---|---|---|---|---|
| backend 기존 CI 3 suites | 01:59:17.889–22.354 | 4.464s, 63 tests | PASS / 0 | backend-existing.json/log |
| Android 수정 전 | 02:02:16.225–40.356 | 24.131s, 6 tests, 4 assertion 실패 | FAIL / 1 (의도한 결함 재현) | android-baseline.json/log, android-baseline-xml |
| H12 추가 안전 회귀 수정 전 | 02:02:22.890–23.097 | 0.205s, 3 tests(조회 3 subcases 포함), failures4/errors1 | FAIL / 1 | backend-r15-before.json/log |
| H12 수정 후 | 02:02:42.683–42.867 | 0.183s, 3 tests | PASS / 0 | backend-r15-after.json/log |
| H12 관련 전체 CI | 02:02:51.625–56.082 | 4.455s, 66 tests | PASS / 0 | backend-r15-regression.json/log |
| backend 전체 1차 | 02:05:27.536–36.139 | 8.601s, 207 entries: pass203/import error2/skip2 | FAIL / 1; dbus 환경 BLOCKED, crypto skip | backend-full.json/log |
| backend 전체 환경 보정 | 02:07:11.005–20.012 | 9.004s, 211 tests, skip0 | PASS / 0 | backend-full-system-deps.json/log |
| H13 1차 수정 + 인증 회귀 | 02:10:16.585–40.158 | 23.573s, replay12/compatibility6/HMAC3=21 tests | PASS / 0 | android-h13-after.json/log/xml |
| H1/H2 1차 수정 + 기존 정책 | 02:13:05.881–13.460 | 7.579s, Repository13/policy9/coordinator3=25 tests | PASS / 0 | android-repository-after.json/log/xml |
| H13 추가 경계 수정 전 | 02:16:48.680–02:17:12.642 | 23.961s, Client17/Repository16=33 tests, 5 failures | FAIL / 1 (새 결함 재현) | android-h13-boundary-before.json/log/xml |
| H13 경계 1차 + H2 화면dispose 수정 전 | 02:20:05.695–29.873 | 24.178s, Client15/Repository19=34 tests, 2 failures | FAIL / 1 (조회중epoch변경 retry2회, H2dispose) | android-h13-after-h2-dispose-before.json/log/xml |
| H13 경계 최종 + 자동복구 time-sync 예방 수정 전 | 02:27:35.917–02:28:00.136 | 24.219s, Client16 PASS/Repository20 중1 failure | FAIL / 1 (time-sync호출1, 기대0) | android-h13-final-recovery-readonly-before.json/log/xml |

Android 명령 공통: `GRADLE_USER_HOME=$PWD/.mobile-build/gradle-home nice -n 10 ./gradlew :app:testDebugUnitTest --tests '<해당 클래스>' --max-workers=1 --console=plain`. 전체/assemble/lint 최종 실행 결과는 아래 최종 G2 기록 참조. 의존성은 테스트 전용 Mockito5.20.0, coroutines-test1.9.0; runtime coroutines1.9.0과 정합. 테스트 서버는 loopback 임의 포트·공개 테스트 인증서만 사용한다.

backend는 기존 `backend/.venv` Python3.8.10 및 `PYTHONDONTWRITEBYTECODE=1`. 전체 1차는 dbus 미설치로 import error가 났으며 새 패키지 설치 대신 **해당 테스트 프로세스에만** 기존 `/usr/lib/python3/dist-packages`를 sys.path 끝에 추가해 OS dbus/gi/cryptography를 사용했다. 환경 실패 기록을 삭제하지 않았다. 최종 실행은 211개 모두 실제 실행, skip 없음. 운영 subprocess는 mock 또는 임시 디렉터리/dry-run이며 안전 검토는 backend-full-safety-review.txt.

## R1–R16 연결과 검증 범위

| ID | H | 실제 제품/기존 테스트 연결 | 현재 검증 상태 | 남은 범위 |
|---|---|---|---|---|
| R1 | H1 | JetsonRepositoryStabilityTest N−1 실패, AutomaticConnectionPolicyTest | PASS(LAB; fake link present, cleanup0) | 실기 그룹/제어 표시 T6A BLOCKED |
| R2 | H1/H4 | Repository N회, 복구 성공/소진/명시 재시도·62.25초 fake-time budget | PASS(LAB; 최종174 포함) | 실제 복구 시간 미측정 |
| R3 | H2/H14 | Repository 명시 해제 후 fake-time10분, 예약 probe 취소 | PASS(LAB 하위조건) | 모든 OS callback/FGS/재생성은 NOT RUN |
| R4 | H5 | TransportCoordinator 이전 세션/장비/요청순서 + Repository late failure/cancellation | PASS(LAB 하위조건) | 실제 OS callback 미검증 |
| R5 | H3/H12 | WifiDirectConnectionPolicyTest cleanup중 begin차단; backend cleanup조회unknown 새 테스트 | backend PASS, Android 미확정 timeout 경계 NOT RUN | Android timeout→IDLE 후보는 미수정·실물 manager 하네스 없음 |
| R6 | H4 | Repository 단일 probe owner/queued취소, 기존 Coordinator/Direct phase, backend worker 보호 | 부분 PASS(LAB), 전체 trigger 충돌 NOT RUN | 실제 OS connect/cancel/remove 중첩 미관측 |
| R7 | H2 | Repository manualDirect+일반Wi-Fi+비사용자소실, 기존 AutomaticConnectionPolicy | PASS(LAB) | 실기 T3 BLOCKED/T7 NOT RUN |
| R8 | H5/H13 | HttpAuthSignerTest/ApiCompatibilityTest, HTTPS pin/proof fixture, Repository target mismatch, backend auth | PASS(최종174의 인증/세대 하위조건) | 앱↔실제 백엔드 handshake 미확인 |
| R9 | H1/H13 | StatusFreshnessTest/MetricPresentationTest/backend status; RTK UNKNOWN 통합 회귀 | 센서 stale/UNKNOWN 조건부 RTK 보존 PASS | H1 status실패시 RTK stop은 유지: lease/유효경로 독립 판단 미구현. 전체 R9 PASS 아님 |
| R10 | H3/H5 | Direct phase 늦은callback/다른peer 보호; backend cleanupunknown | PASS(실행한 모델/백엔드 하위조건) | Android 실제 채널 재생성/다른그룹 정리0은 NOT RUN |
| R11 | H7/H8/H11 | LanAddressSelectionTest/WifiNetworkSelectionTest/Direct fallback | PASS(최종174의 주소선택 단위 범위) | 같은 앱 UID 소켓/서버/authenticated 반영 증거 NOT RUN; Network null만으로 장애 판정 안 함 |
| R12 | H13 | LocalApiClientReplayTest 실제 HTTPS pipeline/upload/signature/drop/503/307/401/readbudget/cancel | PASS(최종18개 HTTPS 회귀 + Repository 통합) | RTK lease 전용 조회 endpoint 없음; status조회로 등록 성공 단정 금지 |
| R13 | H1/H4/H9 | Repository probe budget소진/수동재개/취소, backend discovery retry/deadworker | Direct API episode LAB PASS | 기존 LAN 재시도 episode상한/jitter·재구성 초기화는 미검증, 전체 PASS 아님 |
| R14 | H6/H14 | BluetoothPermissionPolicyTest 등 기존 권한 단위; Repository 해제 취소 | PASS(최종174의 권한 단위 하위조건) | 실제 FGS거부/권한회수/키복원/force-stop/OS회수 NOT RUN |
| R15 | H12 | backend test_wifi_direct 신규3+worker/DHCP/peer/deadline/cleanup 기존 | PASS(실행한 LAB 하위조건, backend 전체211 포함) | station dump peer조회실패 직접주입 NOT RUN. 실제 무선/driver/DHCP 장애 T6B BLOCKED |
| R16 | H10/H12 | backend status slow collector/timestamp + API signed error/preview | PASS(기존 해당 tests) | cold-cache503/동시 file-preview-status 부하 전체는 NOT RUN |

## T1–T9: 실제 실기 실행 상태

현재 확인한 설치 baseline은 APK SHA `49afd594…f46334`, API PID1398/P2P PID1399(실행 모듈 식별 제한). 새 코드 설치 없음. 안전한 G0 조회/화면 확인은 실기 재검증 T의 대체가 아니다. 새 APK 설치도 사용자 지시의 영향 있는 변경/§9 승인 경계를 적용한다.

| ID/subcase | H | 사전 목표 | 실제 시작/종료·시간·횟수 | 상태 | 제한/증거 |
|---|---|---|---|---|---|
| T1 LAN | H1/H7/H9/H10 | 인증된 LAN30분 | 미시작, 0/30분 | BLOCKED | 전체 제어 연결 없음·시험 대상 카드 확인 대기·수정 APK 미설치; ui-allowlist/ui-connection-help |
| T2 Direct | H1/H6/H9/H12 | 인증된 Direct30분 | 미시작, 0/30분 | BLOCKED | active P2P group 없음; 일반 Wi-Fi 변경 영향/관리경로·승인 미확보 |
| T3 LAN/Direct | H2/H3/H4/H5/H11 | 각각 연결/해제/재연결10회 | 0/10회씩 | BLOCKED | 대상·active baseline/설치 조건 미확보; 운영 세션 해제 없음 |
| T4-USB 잠금 | H6/H14 | 정상 세션잠금10분 | 0/10분 | BLOCKED | USB 미확보, 정상 인증 세션 미확보 |
| T4-자연 배터리 잠금 | H6/H14 | 정상 세션10분, 자연절전 별도 관측 | 0/10분 | NOT RUN | 배터리/ACTIVE snapshot만 조회; 자연Doze 관측 아님 |
| T4-강제Doze | H6 | 별도 조건/원복 고정 | 0회 | BLOCKED | 구체 승인/원복경로 없음; force-idle/unforce 실행 안 함 |
| T5-일반/RTK | H6/H14 | 다른앱 포함30분씩 | 0/30분 | NOT RUN | 앱 foreground HOT 이동은 background 유지시험 아님 |
| T5-OS회수 | H6/H14 | 별도 원인/복원 기준 | 0회 | BLOCKED | 종료 조작 승인 없음 |
| T5-사용자force-stop | H6/H14 | 사용자 의도 존중 | 0회 | BLOCKED | 강제종료 승인 없음, OS회수와 구분 |
| T6A-3s/10s/60s | H1/H10/H13 | API만 장애, 물리그룹 유지 | 각0회, 주입0초 | BLOCKED | USB·Jetson 독립 관리경로·구체승인·원복 조건 미확보 |
| T6B-3s/10s/60s | H2/H12 | 실제링크 소실 표시/복구 | 각0회, 주입0초 | BLOCKED | T6A와 동일; 실제 링크 손실을 정상표시하는 기준 사용 안 함 |
| T7 사용자해제 | H2/H14 | 해제후10분, connect/FGS0 | 0/10분 | NOT RUN | LAB fake-time10분과 실기10분을 구분 |
| T8 cellular+Direct | H7/H8/H11 | 같은 앱 요청 경로/auth 응답 | 0회 | BLOCKED | Direct 없음·per-request 로그/RTK 승인 없음 |
| T9 API재시작 | H13/H14 | 새runtime+재인증+중복명령0 | 0회 | BLOCKED | USB·독립경로·승인·롤백 미확보 |
| T9 P2P재시작 | H12 | API-only와 별도subcase | 0회 | BLOCKED | 동일, 서비스 재시작 없음 |

실기 복구 예산은 **목표 미정·미측정**이다. LAB의62.25초를 뒤늦게 현장 합격 기준으로 사용하지 않는다. 실제 T를 시작한 뒤 시간이 부족한 case는 현재 없으므로 `INCOMPLETE(짧은 smoke)`로 꾸미지도 않는다.

## 추가 회귀의 의미

- H13 초기 두 baseline은 실제 HTTPS/HMAC 검증을 통과한 요청으로 mutation2회 및 결과조회0회를 재현했다. signed401/503, unsigned401, HMAC손상, EOF/응답유실, nullbody, Unit204, redirect307을 구분했다. signed401은 별도 분류 fixture이며 현행 backend의 사전 auth거부는 unsigned401 경로다. unsigned401을 실제 미실행으로 확정하지 않는다.
- 02:20의 남은 Client 실패는 endpoint변경 뒤 GET 내부retry가 조회2회를 실행한 사례다. 전후 결과폐기만으로 부족해 endpoint세대 변경시 기존 실제 OkHttp Call도 취소하고, 늦은 enqueue 및 read-auth retry 전후를 검사했다. assertion을1→2로 완화하지 않았다.
- Repository의 H13 UNKNOWN+같은pipeline RUNNING/STARTING은 기존 stop1→보존0으로 보정했다. 조회실패/다른pipeline/stopped/확정실패는 stop을 유지하며 UNKNOWN을 명령 성공으로 변환하지 않는다.
- 자동 same-link API 복구는 인증된 읽기 재검증만 수행하도록 이전 initial-probe의 time-sync 부작용을 제외했다. capability=true에서 기존 sync1회가 재현되었다. 이는 새 복구 경로의 부작용 예방이며 현장 시계장애 원인을 관측했다는 뜻이 아니다. 초기 연결의 기존 time-sync 동작은 별도 회귀로 보존한다.
- D1은 두 synthetic 등록deviceId/서로다른key/같은test TLS cert를 사용해 재인증 장비 변경을 재현했다. 명시 endpoint reset의 새장비연결은 허용하고 기존세션의 자동 장비변경만 거부한다.


## 최종 G2 자동 검증 (2026-09-08)

`android-final.json/.log`, `android-final-xml/`, `android-final-summary.json`:

- 실제 실행 **02:30:42.199445–02:33:01.372309 UTC**, wall **139.173초**, exit **0**.
- 명령: `GRADLE_USER_HOME=$PWD/.mobile-build/gradle-home nice -n 10 ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --max-workers=1 --console=plain`.
- Android **40 suites / 174 tests, failure0/error0/skip0 PASS**. 새 실제 Repository 경로 21개, HTTPS Client 경로 18개 포함. 클래스 선택 필터 없는 전체 실행이다.
- `assembleDebug` PASS, `lintDebug` PASS: **Error0 / Warning72**. 경고 없는 빌드라고 주장하지 않는다. 기존 Log/TrustManager 및 dependency/version/style 경고가 포함되고 새 테스트 의존성 두 줄에는 UseTomlInstead2개 경고가 있다. 이번 작업에서 보안/lint 검증을 새로 끄지 않았다.
- backend 전체 **211 tests PASS, skip0**, 별도 02:07:11–20 실행 증거 유지. Android PASS와 backend PASS를 실제 네트워크 통합 PASS로 합치지 않는다.
- CI reliability.yml 선택목록에 JetsonRepositoryStabilityTest, LocalApiClientReplayTest, ApiCompatibilityTest, HttpAuthSignerTest를 추가했다. 로컬 실행만 검증했고 원격 CI 실행은 **NOT RUN**(push 없음).

빌드 도구: Gradle9.5.0, launcherJDK21.0.12, daemon은 프로젝트 기준 Java25, SDK37.0/build-tools37.0.0. APK `app/build/outputs/apk/debug/app-debug.apk`, 148094608 bytes, versionName1.15.3/versionCode22. **신규 APK SHA-256 `ae8f4ebc532364f47deac40e45dc1bca863322091f9c9824ea5f04b0948df3ed`**, apksigner verify exit0, cert SHA `7e78141e5f83032ca3092c0691df3ca2ef5ce3c3dbba18cabec3d8c7405cb66a`(baseline과 동일). `new-apk-identity.json`에 기록. 내장 source commit build ID는 없으며 최종 product file hashes/commit manifest로 로컬 빌드 대응을 보강한다. **설치 안 함**.

G2는 **수정한 독립 결함의 자동 검증 PASS / v3 전체 요구 범위 PARTIAL**이다. R9(API장애 중 유효 RTK lease의 독립 유지), Android H3 cleanup 미확정 경계, OS/실제 네트워크 조건 등 미실행 항목이 남는다. G3는 BLOCKED/NOT RUN이며 실기 완료로 승격하지 않는다. 추가 자동 실행을 예약하거나 이후 결과 전달을 약속하지 않는다.

로컬 증거 바로가기(git 미포함): [수정 전 Android 실패](../../artifacts/20260908-015500_G0/android-baseline.json), [최종 Android 실행](../../artifacts/20260908-015500_G0/android-final.json), [174개 테스트 집계](../../artifacts/20260908-015500_G0/android-final-summary.json), [백엔드 전후·211개 전체 기록](../../artifacts/20260908-015500_G0/backend-summary.md), [무선 ADB 단절](../../artifacts/20260908-015500_G0/adb-infrastructure-timeline.json), [최종 소스·산출물·배포 대조](../../artifacts/20260908-015500_G0/final-manifest.json).
