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
