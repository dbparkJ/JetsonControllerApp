# Astra 통합 검수 — 2026-09-14 P0 후속

- 구현: GPT-5.6 Sol High, 독립 검수·통합·실기·배포: Astra.
- 병합 기록과 필수 CI: [PR #7](https://github.com/dbparkJ/JetsonControllerApp/pull/7). 사용자가 main 병합과 알려진 Jetson·receiver·휴대폰 배포를 명시적으로 승인했다.
- 판정: **PASS — 아래 검증한 구성과 P0 코드 범위**. 42개 요구사항의 모든 하드웨어 조합·생산 정책을 일괄 합격으로 표시하지 않는다. 행별 범위는 [Acceptance Matrix](../qa/ACCEPTANCE_MATRIX_KO.md)를 따른다.
- 배포 대상: `jm-desktop` Orin NX, `geonws@100.99.254.37:22`의 공개 receiver, `100.94.106.23:45249`의 SM-S908N/API 36.

## 해결한 P0와 검수 수정

SurveyProject/Section은 Jetson에서 revision과 요청 ID로 관리한다. 명시적인 필수·선택 센서, 저장 여유량과 기대 결과 정책을 부팅·source/release/config에 연결한 preflight로 확인하고, 시작 전에 root 소유 run record와 고유 출력 위치를 예약한다. 응답 유실 시 같은 clientRequestId를 재사용하며 UNKNOWN은 잠금을 유지한다. 실행 중 context/config/source 변경, legacy start 우회와 활성 출력의 이동·전송을 차단한다.

Android는 장비별 선택·미확인 시작 요청을 보존하고 canonical run, output, uploadContext와 receipt를 연결한다. 확정된 시작 거절 뒤에는 재점검이 가능하며, 실제 실행과 오래된 관찰을 구분한다. receiver access project와 조사 project는 별개다. 파일·실행 이력·검증된 업로드 원본은 영구 삭제 대신 휴지통과 복원으로 처리한다. root record 없이 sidecar만으로 컨텍스트를 신뢰하지 않는다.

검수 중 context snapshot 시각 비교, boot/source 변경, 실행 사용자 쓰기 권한, STOPPING 역행, 모호한 systemd 상태 잠금, 정책 replay 영속화·크기 제한, 복원 뒤 재검증과 반복 DELETE, structured API 오류, Android wire/history routing, 오래된 화면 테스트와 시스템 탐색 영역의 하단 버튼 겹침을 수정했다.

## 검증 증거

| 범위 | 결과 |
|---|---|
| Android 로컬 | 전체 JVM 252개/55 suites, failure/error/skip 0. assembleDebug, lintDebug, assembleDebugAndroidTest 통과. Lint error 0, warning 76 |
| Backend 로컬 | 전체 312개 통과. 마지막 upload 수정 후 관련 28개 추가 통과 |
| Receiver 로컬 | 전체 41개 통과 |
| 정적 계약 | Slate Harmony 66 token/94 role pair, QA 42행 checker 통과 |
| 휴대폰 | 기존 데이터·서명을 유지한 `install -r`, 1.17.0/code 25. Survey/TaskFlow/GeoField/CoreWorkflow와 실제 LAN reconnect 합계 19개 통과(실패 테스트 수정 후 해당 suite 재실행 포함) |
| 넓은 화면 | 같은 휴대폰에서 2200×1600, density 240, font scale 1.5로 Survey 2개 통과. 물리 태블릿 시험은 아니며 원래 size/density 560/font 1.1/회전 설정으로 복원 |
| 실제 receiver | 공개 TLS, 기존 6 sessions/196,131 files 보존·DB migration·backup·구버전 rollback/복귀 통과. 끊긴 13 MiB chunk offset resume, deferred batch, context/scope/environment 거부, preview 한도, 직원 role/revoke/disable/rotate/expiry, audit와 trash/restore 확인 |
| 실제 Jetson 복구 | API 구 package로 복귀 후 TLS/HMAC 확인, 다시 최신 package 복원 후 동일 identity/run/output 확인. 설치된 Python 28개가 통합 source와 일치하고 기존 장비 ID·bootstrap secret·TLS 인증서를 보존 |

실제 수집 `26_camera_record/run-20260914T081300.411925Z-557616545.log`에서 필수 IMU 오류가 시작을 막는 것을 먼저 확인했다. 이후 QA 정책을 required camera/GNSS, optional IMU, 최소 1 GiB, 기대 1 file/1 byte로 명시해 시작했다. 이 값은 생산 정책 승인이 아니다. 같은 요청 ID 재전송은 동일 runId를 반환했고, 활성 survey 변경은 409였다. API를 재시작해도 수집이 유지됐으며 명시적 stop 뒤 `STOPPED`와 `FINAL/SATISFIED`를 확인했다.

workload는 387 files/1,083,395,576 bytes다. 메타데이터 2개를 포함한 389 files/1,083,396,422 bytes를 공개 receiver로 전송해 `COMPLETED`와 전체 hash `MATCHED`를 확인했다. remote session은 `d813c09e-cef6-4ec0-8fde-276d4d872262`, content SHA-256은 `ded1ff3176e2de2495be2c10aea253878712193be903a238d10d5c179632e2e8`이다. 원본과 이력을 각각 휴지통으로 이동·복원했고, 원본 fresh verification 및 동일 log hash/context를 확인했다. 실제 QA 수집 결과는 검토할 수 있도록 보존했다.

합성 receiver QA 업로드 두 개는 복구 가능한 휴지통으로 옮겼고, 임시 QA 직원·관리자·장비 토큰은 비활성화했다. 기존 운영 계정과 데이터는 유지했다. 기존 receiver 저장소의 사용자 변경과 export-view 서비스도 보존했다.

## 배포와 남은 범위

앱은 기존 설치와 같은 서명으로 내부 배포한 debug APK다. receiver는 검수한 `ac570731e39d54f5bf2d04564385a9806a3a2439` package이며 이후 통합 receiver 운영 코드와 차이가 없다. 기존 환경·비밀·mount를 유지한 별도 release 경로와 user-service override로 배포했다. Jetson은 공식 installer와 검수한 후속 package 교체를 적용했다. 보호된 배포 backup과 로컬 검증 자료는 `.mobile-build/deployment-backups/cycle02/`, `.mobile-build/device-validation/`에 있고 비밀 파일은 Git에 넣지 않았다.

GNSS는 실내 no-fix이고 외장 IMU는 미검출 오류다. 내장 OAK IMU sample이 있어도 외장 오류를 성공으로 바꾸지 않았다. 야외 RTK FIX, 물리 태블릿, 장시간 수집과 모든 BLE/Wi-Fi Direct/LTE 전환은 미검증으로 남긴다. 자동 purge·영구 삭제 API와 조직 SSO는 제공 범위가 아니다. 생산 센서·저장량·기대 결과 값은 현장 운영자가 명시적으로 설정해야 한다.

아래 Cycle 01 기록은 당시 판단을 보존한 역사이며 현재의 미구현·배포 보류 상태를 뜻하지 않는다.

---

# Cycle 01 Astra 통합 검수

- 검수일: 2026-09-14
- 검수자: Astra / 구현 Worker: GPT-5.6 Sol High
- 통합 branch: `work/merge-stage-20260914`
- 기능·CI 검증 commit: `3d716c64184d36955e38fa1006b23d45002b39a8`
- 기준 main: `e41b752a72be4e5367c1108ab32a2725c8fb4dec`
- Verdict: **PASS** — 이번 cycle의 통합 코드·로컬 검증 범위
- 제품 운영 acceptance: **BLOCKED**, demo: **NOT_RUN**

## 통합 범위와 판정

AG01 → AG04 → AG05 → AG06 → AG03 → AG02 → AG07 순서로 각 검수 완료 branch를 통합했다. 자동 merge가 모두 성공했고 별도 conflict adapter 수정은 필요하지 않았다. 공유 모델·route API 변경은 담당 Worker 간 사전 계약으로 연결했다. main 병합, 원격 push, 장치 배포·service restart·센서 운전은 수행하지 않았다.

| 작업 | 검수 대상 Worker commit | 최종 독립 검수 |
|---|---|---|
| AG01 제품 계약 | `8633f4b` | [PASS](AGENT_01_ASTRA_REVIEW.md) |
| AG04 Jetson runtime | `bf48810` | [PASS](AGENT_04_ASTRA_REVIEW.md) |
| AG05 직접 서버·보관 | `00bfa15`, `9409328` | [PASS](AGENT_05_ASTRA_REVIEW.md) |
| AG06 품질 기록 | `6827e1c` | [PASS](AGENT_06_ASTRA_REVIEW.md) |
| AG03 연결·복구 | `e1d9a8e`, `3945b5b` | [PASS](AGENT_03_ASTRA_REVIEW.md) |
| AG02 운영 UI | `ecd1e08`, `0b8662c` | [PASS](AGENT_02_ASTRA_REVIEW.md) |
| AG07 QA·CI | `6a02bbc` | [PASS](AGENT_07_ASTRA_REVIEW.md) |

홈과 작업 화면은 연결 여부와 실제 수집 근거를 분리한다. 유실된 start/stop 명령은 재전송하지 않고 같은 target의 상태를 조회하며 UNKNOWN을 성공으로 바꾸지 않는다. RTK 중계 정리와 callback은 소유 client로 제한한다. 신규 등록의 reboot autostart는 기본 false다.

Jetson 없이 접근하는 서버 화면은 직원·환경·프로젝트 scope, current/cache 시각, receipt, preview와 복구 가능한 서버 휴지통을 연결한다. 권한/환경 오류에 cache를 우회 노출하지 않고 이전 profile의 화면·요청·Undo를 폐기한다. 품질은 같은 run의 실제 관찰 시간, RTK FIX 비율·유지 시간과 미확인 구간을 제공하며 판정 임계값을 만들지 않는다.

## 최종 로컬 검증

| 검사 | 결과 | 근거 |
|---|---|---|
| Android assembleDebug + lintDebug + 전체 JVM | PASS, 3분 53초 | 51 suite, **242 test**, failure/error/skip 0 |
| Android Lint | PASS | error 0, warning 76; HTML/XML/SARIF 생성 |
| Backend 전체 discovery | PASS | **275 test**, failure/error/skip 0, 10.274초 |
| Receiver 전체 discovery | PASS | **37 test**, failure/error/skip 0, 3.468초 |
| Slate Harmony | PASS | 66 native token, 94 role pair, failure 0; screenshot 검사가 아님 |
| QA acceptance coverage | PASS | 요구사항 42개를 정확히 한 번씩 포함 |
| Workflow YAML | PASS | YAML 구조와 android/backend/upload-receiver/qa-contract 4개 job 확인 |
| 전체 diff whitespace | PASS | `git diff --check e41b752..3d716c6` |

Android 최종 명령:

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --max-workers=1 --console=plain
```

Backend는 기존 Python 3.8 venv에서 같은 버전의 시스템 DBus/GObject 모듈 경로를 추가해 `unittest discover -s tests`를 실행했다. 패키지를 설치하거나 환경 파일을 변경하지 않았다. Receiver는 같은 기존 venv를 사용하되 `upload_receiver/`에서 별도 discovery를 실행했다. Python 검증 시점은 `03b2e93`이며 최종 `3d716c6`과 `backend/`, `upload_receiver/`의 diff가 없음을 확인했다. 이후 문서 보정은 이 기능 검증 범위를 바꾸지 않는다.

Python test 출력의 DEPLOYED/FAILED_ROLLED_BACK은 `/tmp` mock fixture 결과이며 실제 배포가 아니다. 원격 GitHub Actions는 실행하지 않았다. 76개 Lint warning은 dependency/version, logging, 기존 trust manager 등의 항목으로 남아 있으며 Lint 무경고 판정은 아니다. 실제 Android OS lifecycle·화면·하드웨어의 정상 동작을 이 결과로 단정하지 않는다.

## 로컬 산출물

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- 버전: `1.16.1`, versionCode `24`, minSdk `31`
- SHA-256: `9b45ca9a8afca300c3514770714918e53aada062fc520a6ed4fb92dc02f21ff0`
- JVM report: `app/build/reports/tests/testDebugUnitTest/index.html`
- JVM XML: `app/build/test-results/testDebugUnitTest/`
- Lint report: `app/build/reports/lint-results-debug.html`

APK는 debug/local 검증 산출물이며 기존 diagnosticsBuildId는 `local-unidentified`다. 운영 freeze·서명·배포 artifact로 승인한 것이 아니다. 사용자 제공 prompt folder는 기존 untracked 상태로 보존했다.

## 운영 전에 남은 핵심 항목

1. survey Project/Section domain, 실행 중 선택 고정과 run persistence가 없다. receiver access project를 이 context로 대체할 수 없다.
2. device/context/pipeline/preflight/output/upload/receipt의 영속 identity 연결 및 기대 파일 수·총량·부분 결과 계약이 없다.
3. runtime 필수 센서 정책 source가 없어 모두 UNSPECIFIED다. 최소 저장량, 정상 operator stop 결과, 직원 계정 운영, RTK·보존 정책은 명시 결정과 후속 연결이 필요하다.
4. 복구 가능한 제거는 새 직접 서버 경로에 한정된다. 장비 파일·실행 이력·legacy API의 영구 삭제 정책 통합이 남았다.
5. current commit의 phone/tablet/Orin NX, 실제 GNSS/RTK·셀룰러 중계, 공개 HTTPS/Keystore, 장시간 수집·저장 한도·rollback 및 대표 screenshot은 NOT_RUN이다.

따라서 각 Worker와 통합 코드의 PASS가 전체 요구사항 완료나 main/운영 승인은 아니다. PM은 이 공백을 유지한 채 다음 cycle 지시를 작성해야 하며, 지정 runbook의 인간 PM 실기·최종 승인 단계를 대신할 수 없다.

## PM 평가 확인

Astra는 PM Worker의 `ebc4704` 문서 변경을 검수했다. [PM Cycle 01 평가](PM_CYCLE_01_EVALUATION.md)는 42개 요구사항, 정책 결정과 다음 cycle AG01~07 지시를 포함한다. AG02의 최종 로컬 근거로 QA 표를 갱신했으며 미구현 P0와 실기 미충족을 유지한다. 로컬 통합 PASS / demo NOT_RUN / 내부 운영 BLOCKED라는 판정을 확인했다. 이 후속 변경은 문서에 한정되어 위에서 검증한 기능·CI source와 APK를 바꾸지 않는다.
