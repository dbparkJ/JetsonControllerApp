# PM Cycle 01 통합 평가

> **현재 상태 공지, 2026-09-14:** 이 문서의 §1~§10은 `00cdd99` 시점 Cycle 01 평가 기록으로 보존한다. 당시 P0 미구현·모든 실기 `NOT_RUN` 결론은 후속 P0 구현과 실제 검증으로 일부 해소됐다. 현재 최종 통합 commit, CI, main 병합과 배포 판정은 [`INTEGRATION_ASTRA_REVIEW.md`](INTEGRATION_ASTRA_REVIEW.md)를 우선하고, 요구사항별 현재 증거는 [`ACCEPTANCE_MATRIX_KO.md`](../qa/ACCEPTANCE_MATRIX_KO.md)를 따른다. 아래 역사 문구를 현재 release 판정으로 인용하면 안 된다.

## 0. Cycle 01 후속 P0 구현·검증 갱신

당시 P0였던 Survey Project/Section, versioned required/optional sensor·storage·expected-output policy, contextual preflight/start, root 소유 run→output→upload→receipt identity, recoverable Jetson run/source 삭제가 구현됐다. Receiver access project는 survey context와 별도 namespace로 유지된다. 같은 contextual start `clientRequestId` replay는 실제 Orin에서 동일 `runId`를 반환했고, active survey 변경은 409로 거절됐다.

현재 자동 검증은 Android full JVM 252개와 `assembleDebug`, `lintDebug`, `assembleDebugAndroidTest`, backend full 312개와 마지막 upload 관련 targeted 28개, receiver full 41개, Slate 66 token/94 role pair, QA 42행 coverage가 PASS다. 이는 최신 root 통합 source의 로컬 자동 검증 범위이며 원격 CI 완료나 모든 hardware acceptance를 뜻하지 않는다.

실제 환경의 확인 범위는 다음과 같다.

- Galaxy S22 Ultra SM-S908N, Android API 36에 `1.17.0`/code 25 설치 성공. 실제 LAN stored-credential reconnect는 도달 불가 discovery host를 cached reachable endpoint로 복구하는 흐름을 3회 통과했다. Survey instrumentation은 2200×1600, density 240, font scale 1.5의 넓은 viewport에서 2개 PASS했고 실제 1440×3088, density 560, font scale 1.1 화면과 rotation 복원도 PASS했다. 대표 UI와 실제 LAN reconnect 19개는 실패 테스트 보정 후 모두 통과했다(CoreWorkflow 9개 재실행 포함). Survey 하단 inset 보정은 최종 물리 터치 재확인 중이다.
- Orin NX `jm-desktop`에서 camera·GNSS ACTIVE, external IMU missing/error를 관찰했다. QA용 정책 `required=camera,gnss`, `optional=imu`, 최소 1 GiB, expected 1 file/1 byte로 preflight/start를 통과했고 required IMU 설정에서는 시작이 차단됐다. 이 값은 생산 threshold 승인 기록이 아니다.
- API 재시작 동안 수집은 계속됐고 explicit stop은 `STOPPED`로 종결됐다. workload 387 files/1,083,395,576 bytes가 `FINAL`/`SATISFIED` manifest로 기록됐다.
- metadata 2개를 포함한 389 files/1,083,396,422 bytes를 공개 receiver로 upload해 `COMPLETED` 뒤 모든 object hash `MATCHED`를 확인했다. Jetson run/source trash, 중복 DELETE, restore, fresh verification, exact log hash와 context 보존도 PASS다.
- Jetson API 구 package rollback과 신 package 복귀 뒤 TLS/HMAC, device identity와 run 보존을 확인했다. 공개 receiver `geonwsPrecision`은 기존 6 sessions/196,131 files를 보존한 backup·DB migration·rollback, 중단 13 MiB offset resume, 3-file batch, context/environment/auth/role 거절, token lifecycle, audit와 trash/restore를 실제 server에서 통과했다.

현재 release 판정은 범위별로 유지한다. P0 핵심 수집·저장·upload·검증·복구 흐름은 자동 및 대표 실제 환경에서 확인됐다. 넓은 viewport와 S22 rotation은 검증했지만 physical tablet 증거를 대신하지 않는다. Outdoor RTK FIX, 장시간 연속 수집과 모든 BLE·Wi-Fi Direct·LTE 전환은 아직 미검증이다. 자동 purge는 의도적으로 제공하지 않으며 조직 SSO 연동은 이번 범위가 아니다. 문서 작성 시점에는 Survey 하단 inset의 최종 물리 터치, main 병합과 진행 중인 PR 7 CI가 남아 있다. 따라서 새 증거를 전체 42개 요구사항의 글로벌 PASS 또는 생산 정책·최종 device 승인으로 확대하지 않는다.

> 역사 판정: Cycle 01 `00cdd99` 통합 코드·로컬 검증 **PASS**, demo **NOT_RUN**, 내부 운영 release **BLOCKED**. 아래 내용은 그 시점의 후속 작업 결정을 설명한다.

## 1. 평가 기준과 현재 결론

- 평가 기준일: 2026-09-14
- 통합 검수 기준: `work/merge-stage-20260914@00cdd99`
- 기능·CI 검증 기준: `3d716c64184d36955e38fa1006b23d45002b39a8`
- 확인 완료: AG01~AG07 실행 보고서와 각 Astra PASS. AG02 `0b8662c`와 AG07 `6a02bbc`는 권장 순서로 충돌 없이 통합됐다.
- 통합 Astra: [`00cdd99`](INTEGRATION_ASTRA_REVIEW.md), Cycle 01 통합 코드·로컬 검증 PASS, demo NOT_RUN, 내부 운영 BLOCKED
- 최종 Android: assemble/lint/full JVM PASS, 51 suites/242 tests, failure/error/skip 0. Lint error 0, warning 76
- Backend/receiver: 275/37 tests, failure/error/skip 0. Python 검증 뒤 최종 기능 commit까지 두 source directory가 바뀌지 않았음을 확인
- 정적 계약: slate 66 token/94 role pair, QA 42개 요구사항 coverage, workflow 4 jobs와 전체 diff whitespace 검사 PASS
- 로컬 debug APK: `app/build/outputs/apk/debug/app-debug.apk`, version `1.16.1`/code `24`, SHA-256 `9b45ca9a8afca300c3514770714918e53aada062fc520a6ed4fb92dc02f21ff0`. `diagnosticsBuildId=local-unidentified`이므로 production freeze artifact가 아니다.

Cycle 01은 연결 복구, Jetson runtime, receiver 직접 조회·복구, RTK/센서 품질 기록과 운영 UI의 로컬 구현을 크게 보강했다. 그러나 **cycle 구현 결과**와 **release acceptance**는 다르다. 프로젝트·조사 구간 context와 전체 실행 증거 chain이 없고, 필수 센서 정책이 runtime에 연결되지 않았으며, 실제 Android/Orin NX/receiver 환경 검증이 없다. 따라서 현장 운영 release를 승인할 수 없다. 최종 승인자는 인간 PM이다.

## 2. 42개 요구사항 상태

상태 의미:

- `구현(로컬)`: 통합 source와 자동 test 근거가 있다. 실장치·운영 합격을 뜻하지 않는다.
- `부분`: 일부 계층 또는 흐름만 구현되었거나 인간 정책·통합 evidence가 남았다.
- `미구현/결정 대기`: 핵심 domain 또는 승인 정책이 없다.

| ID | Cycle 01 상태 | 근거와 남은 조건 |
|---|---|---|
| REQ-CON-001 | 구현(로컬) | 명시 장비 선택, 복수 장비 암시 선택 금지, generation/identity 격리 test. 실제 A→B 장치 전환은 미실행 |
| REQ-CON-002 | 구현(로컬) | LAN·Direct·BLE·offline 정책, 모델과 구분 화면이 JVM/source 검수를 통과. 실제 radio 경로 evidence 대기 |
| REQ-CON-003 | 부분 | stale response와 reconnect callback test 통과. Android 잠금/복귀·실제 endpoint 전환 미실행 |
| REQ-CON-004 | 부분 | pipeline은 phone session과 독립이고 offline을 stop으로 바꾸지 않으며 UI는 마지막 관찰과 현재 미확인을 구분. 실제 단절 중 동일 run 지속 evidence 대기 |
| REQ-CON-005 | 구현(로컬) | start/stop mutation 1회, 응답 유실 뒤 GET 조정과 relay 보존 test. 실장치 timeout은 미실행 |
| REQ-CTX-001 | 미구현 | survey `Project` entity, 선택 저장, 실행 중 고정 없음. receiver access project로 대체 불가 |
| REQ-CTX-002 | 미구현 | survey `Section` entity와 project 관계 없음. `RoutePoint.segment`는 수신 gap 구간일 뿐 조사 구간이 아님 |
| REQ-CTX-003 | 부분 | receiver 직원 token/role/access-project grant 구현. 조직 IdP와 survey project 권한 mapping 없음 |
| REQ-TASK-001 | 구현(로컬) | pipeline/release/config/output metadata와 작업 화면 구현. Jetson 응답 동일성 실장치 evidence 대기 |
| REQ-TASK-002 | 구현(로컬) | snapshot/venv/entrypoint 등록과 runtime test 통과. Orin NX 설치·실행 미검증 |
| REQ-CHK-001 | 부분 | 홈 관찰 항목과 runtime storage evidence 일부 존재. 단일 preflight snapshot과 run 연결 없음 |
| REQ-CHK-002 | 부분 | runner가 writable path create/write/fsync/unlink와 가용량을 확인. 승인된 최소 용량이 없고 `0`/`not_configured`는 합격 정책이 아님 |
| REQ-CHK-003 | 미구현/결정 대기 | quality model은 explicit REQUIRED/OPTIONAL을 수용하지만 runner 입력 source가 없어 runtime은 모두 `UNSPECIFIED`; 시작 차단 없음 |
| REQ-CHK-004 | 구현(로컬) | RTK 원자료·FIX 시간/비율·unknown을 제공하고 임의 pass/fail 없음 |
| REQ-RUN-001 | 구현(로컬) | backend `RUNNING` + InvocationID 일치 `activeRunId`, Android lost-response 조정과 fresh state 표현 구현. 실제 start evidence 대기 |
| REQ-RUN-002 | 미구현 | device/survey project/section/pipeline/revision/preflight/output/upload identity를 한 run에 영속 연결하지 않음 |
| REQ-RUN-003 | 구현(구조/로컬) | systemd 실행과 phone session을 분리. 실제 phone 단절 중 process/output 지속 시험 미실행 |
| REQ-RUN-004 | 구현(로컬) | 신규 등록 autostart 기본 false, 기존 enable 상태 보존과 test 존재. Orin reboot 시험 미실행 |
| REQ-RUN-005 | 구현(로컬) | 같은 `pipelineId/logId`의 log/route/quality API·sidecar와 nullable history/map 표현 구현 |
| REQ-RUN-006 | 부분/결정 대기 | footer finishedAt/exitCode와 terminal state 존재. operator stop의 정상 `STOPPED`/`COMPLETED` 정책 미결 |
| REQ-RUN-007 | 구현(로컬) | 품질 저하를 stop 조건으로 사용하지 않고 bounded problem interval을 기록. 실제 장시간 수집 미실행 |
| REQ-STO-001 | 부분 | pipeline output root/path와 resultsDirectory를 열 수 있으나 TaskRun이 output session identity를 직접 보유하지 않음 |
| REQ-STO-002 | 미구현/부분 | runtime path 쓰기 증거와 일반 파일 browser는 있으나 run별 기대 파일 수·총량·종료 시각 비교 계약 없음 |
| REQ-UPL-001 | 구현(로컬) | Jetson upload job과 receiver 직접 전송 흐름 존재. 운영 HTTPS 전송 미검증 |
| REQ-UPL-002 | 구현(로컬) | queue state, offset/batch, retry/cancel, 동일 job/session persistence test 존재 |
| REQ-UPL-003 | 구현(로컬) | `COMPLETED`와 receiver object 재검증 receipt `matched=true` 계약 및 별도 UI 근거 구현. 전체 run/output 동일성 연결은 미구현 |
| REQ-UPL-004 | 구현(로컬) | fresh remote verification 전 source delete 거절. 실제 장애/재개 환경 미검증 |
| REQ-SRV-001 | 구현(로컬) | Android direct client와 화면은 Jetson local API 없이 동작. Jetson OFF + phone LTE 실기 대기 |
| REQ-SRV-002 | 구현(로컬) | environment+URL+employee+project+credential revision cache, stale timestamp/source 표현 구현 |
| REQ-SRV-003 | 구현(로컬) | development/test/production expected header와 response 검증, mismatch 차단 구현. 운영 endpoint 구성 미실행 |
| REQ-SRV-004 | 구현(로컬) | receiver image/video bounded preview와 Android presentation 구현. phone/tablet 실기 대기 |
| REQ-DEL-001 | 부분 | direct receiver session은 trash/restore/Undo가 가능. Jetson run/local file과 legacy library 경로는 영구 삭제이며 일반 제거 계약이 일관되지 않음 |
| REQ-DEL-002 | 부분/결정 대기 | receiver 역할·명시 요청은 있으나 조직 권한, 감사 정책, retention/purge와 legacy device-token delete 정책 미결 |
| REQ-UX-001 | 구현(로컬) | 연결/수집/인터넷·server/GNSS·RTK를 분리한 operational home과 반응형 카드가 JVM/source 검수를 통과. phone/tablet evidence 대기 |
| REQ-UX-002 | 부분 | 일반/관리자 정보 구조와 receiver role gating 구현. 화면 구분은 조직 역할 부여가 아니며 관리자 권한 체계 미완성 |
| REQ-UX-003 | 구현(로컬) | 확인 사실/미확인/다음 행동 문구와 partial/unknown 상태 처리가 JVM/source 검수를 통과. 실제 화면 evidence 대기 |
| REQ-QLT-001 | 구현(로컬) | timing-weighted FIX ratio/time, clock high-watermark, top-level `NO_SAMPLES`/`INSUFFICIENT_TIMING`/`OBSERVED`, RTK `UNKNOWN`, no-threshold tests 통과 |
| REQ-QLT-002 | 구현(로컬) | run/history/route quality 계약과 nullable index, 전체 문제 시각, 최대 50개 map marker 표현이 JVM/source 검수를 통과 |
| REQ-QA-001 | 구현(문서/로컬) | phone/tablet/Orin NX/receiver 지원·acceptance matrix와 42개 coverage checker 구현. 모든 실장치 조합은 `NOT_RUN` |
| REQ-QA-002 | 구현(문서/로컬) | 자동/실장치/demo/내부 운영 evidence 열과 template을 분리. 실제 demo/field record 없음 |
| REQ-QA-003 | 구현(문서/로컬) | 데이터 손실·잘못된 장비 제어·인증 및 Astra FAIL을 waiver 불가 hard blocker로 정의 |
| REQ-QA-004 | 부분 | rollback·diagnostics·demo freeze/internal release checklist 구현. dry-run과 rollback rehearsal 미실행 |

## 3. PM 결정 누락과 의미 충돌

| 결정 ID | 인간 PM이 결정할 내용 | 결정 전 규칙 |
|---|---|---|
| PM-C1-001 | survey project/section source of truth, ID lifecycle, Android/Jetson/receiver 중 authority | 수기 context는 demo limitation일 뿐 운영 합격으로 인정하지 않는다 |
| PM-C1-002 | receiver access project와 survey project의 mapping 및 wire naming | AG05 `projectId`를 survey run context로 해석하지 않는다. 필요하면 `accessProjectId`로 namespace한다 |
| PM-C1-003 | pipeline별 REQUIRED/OPTIONAL 센서와 정책 version | runtime은 `UNSPECIFIED`; sensor 문제로 시작을 차단하지 않는다 |
| PM-C1-004 | output별 최소 저장 여유량과 예상 file/count/bytes 규칙 | 0 byte default나 path probe를 업무 저장 합격으로 확대하지 않는다 |
| PM-C1-005 | 정상 operator stop의 state/exit code 정책 | `STOPPED`, `COMPLETED`, exit 143을 임의로 같은 성공으로 합치지 않는다 |
| PM-C1-006 | RTK 운영 합격 기준이 필요한지, 필요하다면 dataset 근거와 threshold/version | 현재는 비율·시간·문제 구간만 표시한다 |
| PM-C1-007 | 조직 IdP/SSO 연계, receiver token 발급·회전·퇴사 처리 owner | receiver-issued token의 로컬 구현을 조직 인증 완료로 승인하지 않는다 |
| PM-C1-008 | server trash 보존 기간, purge 권한/audit, legacy device-token 영구 삭제 존치 여부 | 자동 purge를 추가하지 않고 원본 보존을 우선한다 |
| PM-C1-009 | 지원 Android phone/tablet OS·모델과 Orin NX/JetPack 기준, 실제 release target | 자동 test 결과로 hardware matrix를 채우지 않는다 |

## 4. API·schema·state 통합 위험

1. `projectId` 의미가 충돌한다. receiver 직접 API의 값은 access-control project다. 제품의 survey project/section은 아직 없으므로 같은 이름을 공유하기 전에 동일성·mapping·authority를 결정해야 한다.
2. 실행 정보가 `ManagedPipeline.execution`, `TaskRun`, route/quality sidecar, storage path, `UploadJob`, receiver session에 흩어져 있다. `runId/logId`에서 output session과 upload/remote session으로 이어지는 영속 foreign key가 없다.
3. `resultsDirectory`는 Jetson 절대 경로이고 `outputRootId/outputPath`는 앱 file API identity다. 둘의 canonical mapping과 실행별 snapshot이 없다.
4. preflight는 현재 실행 header의 storage probe 근거 일부만 남긴다. device identity, time, sensor requirement/version, GNSS 원자료, server target을 시작 직전 snapshot으로 같은 run에 저장하지 않는다.
5. pipeline state와 history state가 별도다. `RUNNING + activeRunId` 확인은 보강되었지만 정상 stop 결과 정책은 미결이다. UI는 request success를 run success로 바꾸거나 `STOPPED`를 임의 성공 처리하면 안 된다.
6. upload `COMPLETED`, receiver `matched=true`, direct employee 조회 성공은 서로 다른 증거다. 현재 모델을 하나의 초록 상태로 축약하면 안 된다.
7. quality의 `RoutePoint.segment`는 GNSS 수신 단절로 나눈 polyline segment이며 survey `sectionId`가 아니다. problem interval의 nullable route index도 위치 근거가 없으면 채우지 않는다.
8. quality requirement 기본은 `UNSPECIFIED`다. backend API가 내보내는 top-level `sampleState`는 `NO_SAMPLES`, `INSUFFICIENT_TIMING`, `OBSERVED`이고 `UNKNOWN`은 `rtkFixState` 범주다. Android는 null quality를 `NOT_RECORDED`로 해석하고 방어적으로 unknown wire 값을 수용한다. 이 상태들을 fail/pass로 변환하면 안 된다.
9. direct receiver mutation의 `UNKNOWN`은 재조회가 필요한 상태다. 자동 재시도나 성공 toast로 바꾸지 않는다.
10. server trash/restore와 Jetson/local/legacy permanent deletion은 capability가 다르다. UI 문구와 action을 endpoint capability별로 제한해야 한다.

## 5. UI와 backend 연결 상태

AG02 최종 `0b8662c`와 통합 `5b2f393`을 source에서 확인했고 Astra PASS를 받았다. direct receiver profile/employee token/environment 연결, current/cache 표현, receipt, preview, trash/restore가 Jetson navigation과 독립된 화면에 실제 연결된다. 홈은 연결·수집·인터넷/server·GNSS/RTK를 별도 상태로 표시하고, pipeline은 fresh `RUNNING`과 non-blank `activeRunId`가 함께 있어야 실행 중으로 확정한다. history/map은 nullable run quality와 문제 시각을 표시하고 위치 근거가 유효한 문제만 최대 50개 marker로 만든다.

이 연결은 UI와 로컬 상태 계약의 구현 근거다. 실제 Android lifecycle, Keystore restart, radio 전환, receiver 통신, 영상 재생과 phone/tablet layout은 실행하지 않았으므로 device 또는 release 합격으로 승격하지 않는다.

실기에서 다시 확인할 연결은 다음과 같다.

- 홈이 연결/Jetson 수집/인터넷·server/GNSS·RTK를 별도 관찰값으로 표시하는가.
- pipeline start 완료가 fresh `RUNNING`과 non-null `activeRunId`를 함께 요구하는가.
- offline cached pipeline state를 현재 수집 상태처럼 표시하지 않는가.
- preflight가 없는 항목을 완료로 표시하지 않고 sensor 정책을 `UNSPECIFIED`로 유지하는가.
- history와 map이 동일 run의 nullable quality를 표시하며 FIX ratio denominator를 `rtkObservedDurationMillis`로 사용하는가.
- direct server screen이 Jetson 연결 없이 동작하고 cache timestamp/source, expected environment, employee/project scope를 표시하는가.
- receipt `matched=true`를 upload 완료와 별도 증거로 보여 주는가.
- trash/restore만 복구 가능하다고 표현하고 local permanent delete에 Undo를 약속하지 않는가.

## 6. Demo 필수와 운영 release 차단 분리

### Cycle 01 demo 전에 필요한 것

- 확보한 `3d716c6` 로컬 Android assemble/lint/JVM과 통합 Astra PASS를 demo evidence record에 연결한다. 이는 실기 합격을 대신하지 않는다.
- demo build/commit, APK hash, backend/receiver commit, 환경을 고정한다.
- 한 Android target과 test receiver/Jetson target이 승인된 경우 대표 흐름 smoke evidence를 남긴다. target이 없으면 화면 fixture demo와 미실행 항목을 명시한다.
- 앱의 survey project/section context가 미구현이며 외부 수기 기록은 demo limitation일 뿐이라는 점, required sensor blocker가 없다는 제한, 실장치/운영 승인이 아니라는 문구를 demo checklist에 둔다. 수기 입력 UI가 구현됐다는 의미가 아니다.
- wrong-device, auth mismatch, verification mismatch, mutation result unknown을 성공으로 표시하지 않는 흐름을 시연한다.
- geofence와 실시간 검지 사진/metadata를 demo 범위에 넣지 않는다.

### 현장 운영 release 전 반드시 해결할 P0

- survey Project/Section domain과 선택 고정, 권한, run persistence를 구현한다.
- device→survey context→pipeline/revision→preflight→output→upload→receiver receipt의 실행 증거 chain을 영속화한다.
- 승인된 required sensor/storage/stop 정책을 versioned input으로 runtime과 UI에 연결한다.
- 조직 인증과 receiver access project↔survey project mapping을 확정하고 운영 provisioning/revocation을 검증한다.
- local/server 삭제 capability, audit, retention/purge 정책을 일관되게 정한다.
- Android phone/tablet, Orin NX, public receiver에서 핵심 흐름과 장애 복구를 실행한다.

### 운영 안정화 후 후속 가능 항목

- 현장 dataset 기반 RTK threshold 고도화
- 조사 구간 geofence 기반 시작 보조
- 실시간 검지 사진+metadata 전송
- Xavier NX 확대와 원본 자동 upload

위 항목은 이번 P0 gap을 가리거나 Cycle 01 scope로 되돌려 넣지 않는다.

## 7. 실장치·운영 evidence matrix

| 환경 | 필요한 실제 증거 | 현재 상태 |
|---|---|---|
| Android phone | 설치/upgrade, 잠금·복귀, LAN↔Direct, BLE 제한, cell internet direct receiver, Keystore token, mobile RTK relay, 응답 유실 UI | NOT_RUN |
| Android tablet | navigation/layout, landscape, 큰 글꼴, history/quality map, preview, profile/confirm dialog | NOT_RUN |
| Orin NX | 설치/rollback, systemd InvocationID, autostart false/reboot, camera/GNSS/IMU handoff, storage probe/full/read-only, phone 단절 중 수집, quality sidecar, NTRIP | NOT_RUN |
| Receiver host | public HTTPS/certificate, employee/project grant, wrong environment/403, upload/receipt, real mount trash/restore, crash recovery, backup/rollback | NOT_RUN |
| End-to-end | 동일 run/output/upload/remote session 추적, local 보존, 재연결, mismatch/partial success, 장시간·8 MiB truncation | NOT_RUN; 통합 identity schema도 미구현 |

## 8. Merge 순서와 충돌 관리

1. reviewed AG01→AG04→AG05→AG06→AG03 stage 위에 AG02 `0b8662c`를 `5b2f393`으로 병합했고, 이어 AG07 `6a02bbc`를 `3d716c6`으로 병합했다. 두 merge는 conflict 없이 완료됐다.
2. 통합 source는 AG02의 `JetsonApp`/dashboard/navigation/storage/quality UI와 AG03 connection/repository/RTK ownership, AG06 nullable quality schema를 함께 보존한다.
3. AG02 targeted 관련 JVM 28개, slate 66 token/94 pair와 AG07 QA 42개 coverage/YAML 검사가 통과했다. root는 최종 `3d716c6`에서 Android 51 suites/242 tests와 assemble/lint를 통과했고 통합 Astra `00cdd99`가 로컬 통합 범위 PASS를 기록했다.
4. AG07 acceptance matrix의 AG02 검수 대기 문구를 최종 로컬 근거로 갱신했다. device/demo/operational 열은 `NOT_RUN`/미충족 상태를 유지한다.
5. Astra FAIL은 PM agent가 해제하지 않는다. 인간 PM이 demo/내부/운영 release 범위를 명시적으로 승인하기 전 main merge·배포·장치 operation을 하지 않는다.

## 9. Cycle 02 Agent별 후속 지시

아래 순서는 P0 계약을 먼저 고정하고 코드·UI·검증이 같은 schema를 따르게 한다.

### AG01 — PM 결정 반영·survey domain 계약

- PM-C1-001~009의 인간 결정을 기록하고 미결 항목을 자동 default로 만들지 않는다.
- `SurveyProject`, `SurveySection`, access project mapping, versioned policy ID의 canonical schema와 ownership을 정의한다.
- receiver access project를 survey project로 오인할 수 있는 `projectId` naming/mapping을 해소한다.
- 완료 조건: 갱신된 요구사항 추적표, migration/compatibility 규칙, 인간 PM decision record.

### AG04 — Jetson run context·preflight·output chain

- 승인 schema로 project/section/pipeline/source/config/preflight/output identity를 run 시작 전에 원자적으로 snapshot하고 restart 뒤 복구한다.
- storage path probe와 승인된 policy version/minimum, expected output count/bytes/end evidence를 분리한다.
- `TaskRun`과 `ManagedPipeline.execution`의 중복·불일치를 없애는 canonical read API를 제공한다.
- 완료 조건: missing/partial/restart/clock/old-run fixtures와 run→output identity test. sensor policy 자체는 AG06 입력을 사용한다.

### AG03 — Android survey context·identity recovery

- project/section 선택을 장비별 draft가 아닌 실행 context로 고정하고 device/session generation과 함께 stale response에서 보호한다.
- 재연결 후 canonical run context가 선택한 device/project/section과 다르면 mutation을 막고 차이를 표시할 data state를 제공한다.
- 실제 phone의 잠금/복귀, LAN↔Direct, BLE 제한, old-device response/relay cleanup을 승인 target에서 검증한다.
- 완료 조건: persistence/process death/target switch tests와 device evidence. UI 배치는 AG02가 담당한다.

### AG06 — 승인된 sensor/quality policy runtime 연결

- 인간 PM이 승인한 pipeline별 required/optional sensor policy와 version만 runner/preflight/quality에 전달한다.
- `UNSPECIFIED` migration과 legacy run을 보존하고 RTK pass/fail threshold는 별도 승인 전 추가하지 않는다.
- long-run/truncation, clock loss, sensor disconnect/recovery를 Orin NX에서 검증한다.
- 완료 조건: policy snapshot→run linkage, required-only blocker tests, collection-continues behavior와 실장치 evidence.

### AG05 — 조직 auth·삭제 lifecycle·survey mapping

- PM 결정에 따라 IdP/SSO 또는 receiver token의 운영 발급·회전·폐기 절차를 구현하고 access project↔survey project mapping을 검증한다.
- server trash retention/purge/audit와 legacy device-token permanent delete 정책을 확정한다.
- Jetson local/run deletion의 recoverable lifecycle은 API capability와 원본 보존 경계를 AG04/AG02와 합의해 구현한다.
- 완료 조건: cross-project deny, terminated employee, wrong environment, trash expiry/restore/purge/audit, local delete recovery tests.

### AG02 — 대표 흐름 UI와 증거 chain 표현

- 승인된 survey context 선택→preflight→start→monitor→stop→Jetson 저장→upload→receipt→direct 조회를 한 run identity로 탐색하게 한다.
- missing/unknown/stale/partial success를 그대로 표시하고 unsupported deletion recovery를 약속하지 않는다.
- phone/tablet, 큰 글꼴, landscape, offline/cache, history/quality map에 대한 screenshot+semantics evidence를 만든다.
- 완료 조건: 42 REQ 중 UI owner 항목의 final-state matrix와 실제 device artifacts.

### AG07 — 통합 acceptance·release gate

- 최종 merge commit에서 자동/phone/tablet/Orin/receiver/end-to-end evidence를 분리한다.
- wrong-device, auth, data loss, verification mismatch와 미구현 P0를 release blocker로 query한다.
- rollback/diagnostics/demo freeze/internal release checklist를 dry-run하고 artifact hash를 기록한다.
- 완료 조건: Astra PASS가 있는 최종 matrix와 인간 PM 승인 대기 상태. 미실행 hardware 항목을 PASS로 바꾸지 않는다.

## 10. 최종 판정과 다음 gate

- Cycle 01 통합 코드·로컬 검증: **PASS**. 이 판정은 `3d716c6` source와 위 자동·정적 검사 범위에 한정된다.
- Demo: **NOT_RUN**. 승인 target, 실제 흐름 record와 screenshot이 없다.
- 내부 운영 release: **BLOCKED**. `REQ-CTX-001`, `REQ-CTX-002`, `REQ-RUN-002`, `REQ-STO-002`, 인간 정책 결정과 모든 필수 hardware 조합이 미충족이다.
- Main merge·배포·장치 승인: 이 평가가 승인하지 않는다. 인간 PM이 범위와 실제 evidence를 검토해 최종 결정한다.

다음 cycle은 §9의 P0 순서대로 survey context, run/output evidence chain, 승인된 runtime policy, recoverable local deletion을 먼저 해결한다. 이후 AG07이 같은 commit의 phone/tablet/Orin NX/public receiver 증거와 rollback rehearsal을 채우고, Astra FAIL이 있다면 수정·재검수한다. geofence와 실시간 검지 전송은 이 gate를 통과하기 전 범위에 넣지 않는다.
