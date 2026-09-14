# 요구사항 Acceptance Matrix

기준 요구사항: `docs/product/REQUIREMENTS_TRACEABILITY_KO.md`의 42개 ID. 이 표는 코드 존재, 자동 검증, 실장치 검증, demo, 내부 운영 합격을 서로 독립적으로 기록한다.

> 2026-09-14 P0 후속 검증 반영본이다. 최종 통합 commit, CI, main 병합과 배포 판정은 [`INTEGRATION_ASTRA_REVIEW.md`](../agent-runs/INTEGRATION_ASTRA_REVIEW.md)를 우선한다. 이 표의 `PASS`는 해당 행과 해당 열의 증거 범위만 뜻하며 전체 42개 요구사항 또는 생산 release 일괄 승인이 아니다.

결과 값은 `PASS`, `PARTIAL`, `MISSING`, `NOT_RUN`, `PM_REQUIRED`만 사용한다. 자동 검증 `PASS`가 실장치나 운영 합격을 뜻하지 않는다. QA에서 사용한 required sensor, 최소 저장량과 expected output 값은 검증 입력이며 생산 threshold 승인 기록이 아니다.

현재 증거 색인:

- 최신 root Android: full JVM **252 tests**, `assembleDebug`, `lintDebug`, `assembleDebugAndroidTest` PASS, 총 2분 17초. Slate Harmony 66 token/94 role pair와 QA 42행 checker PASS.
- 최신 root backend: full discovery **312 tests** PASS, 마지막 upload/runtime targeted **28 tests** PASS. Receiver full discovery **41 tests** PASS.
- Galaxy S22 Ultra SM-S908N, Android API 36: `1.17.0`/code 25 설치 성공. 실제 LAN stored-credential reconnect는 도달 불가 discovery host를 cached reachable endpoint로 복구하는 흐름을 3회 통과했다. Survey instrumentation은 넓은 viewport 2200×1600, density 240, font scale 1.5에서 2개 PASS했고 실제 화면 1440×3088, density 560, font scale 1.1과 rotation 복원도 PASS했다. 최종 UI 19개 중 CoreWorkflow LazyColumn 3개는 실패해 수정 중이므로 전체 UI PASS가 아니다.
- Orin NX `jm-desktop`: camera와 GNSS ACTIVE, external IMU는 missing/error. QA 정책 `required=camera,gnss`, `optional=imu`, `minFreeBytes=1 GiB`, `expectedOutput=1 file/1 byte`로 preflight/start, 같은 request ID의 동일 run ID replay, active survey 변경 409, API 재시작 중 수집 지속, 명시 stop의 `STOPPED`를 확인했다. 이 QA 정책은 생산 정책 승인이 아니다.
- 같은 Orin run: workload 387 files/1,083,395,576 bytes, output manifest `FINAL`/`SATISFIED`. metadata 2개를 포함한 upload 389 files/1,083,396,422 bytes가 공개 receiver에서 `COMPLETED` 뒤 전체 hash `MATCHED`로 검증됐다.
- Orin run/source trash, 중복 DELETE, restore, fresh verification, log exact hash와 context 보존 PASS. API 구 package rollback과 신 package 복귀 뒤 TLS/HMAC, identity와 run 보존 PASS.
- 공개 receiver `geonwsPrecision`: 기존 6 sessions/196,131 files 보존 backup·DB migration·rollback PASS. 중단된 13 MiB upload offset resume, 3-file batch, context mismatch 409, scope 403, oversize 413, wrong environment 409, role downgrade 403, project revoke 403, account disable 401, token rotation/expiry, audit, trash/restore를 실제 server에서 확인했다.
- 미검증 범위: outdoor RTK FIX 신호, physical tablet, 장시간 연속 수집, 모든 BLE·Wi-Fi Direct·LTE 전환. 넓은 viewport instrumentation은 통과했지만 physical tablet 증거를 대신하지 않는다. 자동 purge는 의도적으로 제공하지 않으며 조직 SSO 연동은 현재 범위가 아니다.
- 문서 작성 시점에 main 병합은 완료되지 않았고 PR 7 CI가 진행 중이다. 최종 commit과 배포 결과는 통합 Astra 보고서에서 갱신한다.

| ID | 현재 코드·계약 판단 | 자동 증거 | 실장치·운영환경 증거 | Demo gate | 내부 운영 gate | Blocker / 다음 증거 |
|---|---|---|---|---|---|---|
| REQ-CON-001 | 명시 deviceId 선택, session generation과 contextual run device 고정 구현 | PASS: identity와 stale-response 회귀 | PARTIAL: S22 한 대와 Orin 한 대 검증 | PARTIAL: 단일 장비 흐름 | PARTIAL: 두 Android·두 Jetson 오제어 시험 없음 | A→B 전환 중 늦은 A 응답 격리 실기 |
| REQ-CON-002 | LAN·Direct·BLE 제한·offline transport와 capability 구분 구현 | PASS: transport policy와 UI 계약 | PARTIAL: TLS LAN 경로 확인, 전체 radio 전환 미실행 | PARTIAL | PARTIAL | BLE·Wi-Fi Direct·LTE 대표 전환과 tablet 화면 |
| REQ-CON-003 | 재인증, generation 폐기와 canonical pipeline/run/upload 재조회 구현 | PASS: lifecycle/recovery tests | PASS: API rollback·restart identity 보존과 S22 stored-credential LAN reconnect 3회 | PASS: 검증한 LAN 복구 범위 | PARTIAL: Wi-Fi Direct·LTE 전환 미검증 | 다른 transport에서 같은 identity 재조회 |
| REQ-CON-004 | phone/API session과 systemd 수집 분리, offline을 stop으로 해석하지 않음 | PASS: 독립 runtime와 stale UI tests | PASS: API 재시작 중 같은 Orin 수집 지속 | PASS: 대표 단절 지속 흐름 | PARTIAL: 장시간·radio 단절 미검증 | 장시간 phone 단절과 재연결 동일 run 확인 |
| REQ-CON-005 | payload-bound mutation replay와 GET reconciliation 구현 | PASS: idempotency·unknown-state tests | PASS: 동일 start clientRequestId가 같은 runId 반환 | PASS | PASS: 검증한 contextual start 범위 | proxy 지연을 포함한 추가 장시간 fault는 후속 |
| REQ-CTX-001 | Jetson-authoritative SurveyProject CRUD와 실행 snapshot 고정 구현 | PASS: backend/API/Android tests | PASS: Orin authenticated API project 생성·active 변경 409, S22 Survey 화면 검증 | PASS | PARTIAL: main·CI와 UI 실패 수정 대기 | 최종 통합 commit에서 재확인 |
| REQ-CTX-002 | SurveySection CRUD, project 관계와 실행 snapshot 고정 구현 | PASS: 관계·revision·lock tests | PASS: Orin authenticated API project/section 생성·active lock, S22 Survey 선택 화면 검증 | PASS | PARTIAL: main·CI와 UI 실패 수정 대기 | physical tablet 선택 흐름 |
| REQ-CTX-003 | receiver 직원 token, access project role과 survey context namespace 분리 | PASS: allow/deny와 mapping 경계 tests | PASS: role downgrade, revoke, disable, token rotate/expire 실제 server | PASS | PASS: 현재 receiver token 운영 계약 | 조직 SSO는 범위 밖이며 별도 연동 시 재검수 |
| REQ-TASK-001 | pipeline/release/config/output와 survey 선택 요약 구현 | PASS: API/model/UI tests | PASS: 실제 Orin run identity와 output 연결 | PASS | PARTIAL: 최종 main/CI 대기 | release artifact 기준 동일성 재확인 |
| REQ-TASK-002 | snapshot/venv/entrypoint 등록과 안전한 release 실행 구현 | PASS: layout/registration/runner tests | PASS: Orin의 실제 external pipeline 실행 | PASS | PASS: 검증한 Orin pipeline 범위 | 다른 외부 pipeline은 별도 호환 검증 |
| REQ-CHK-001 | device/task/time/storage/sensor/GNSS context preflight와 run snapshot 구현 | PASS: API/Android preflight contract | PARTIAL: Orin 핵심 항목 확인, outdoor RTK와 server 상태 snapshot은 제한 | PASS: 수집 blocker 흐름 | PARTIAL | outdoor RTK와 선택 receiver 상태 표현 확인 |
| REQ-CHK-002 | pipeline-user write/traverse, free bytes와 versioned minimum 정책 구현 | PASS: read-only/ancestor/free-space tests | PASS: QA용 1 GiB 정책 preflight | PASS | PM_REQUIRED: 생산 최소량 미승인 | 작업별 생산 minFreeBytes 승인 필요 |
| REQ-CHK-003 | 명시 required/optional sensor 정책과 required-only blocker 구현 | PASS: policy snapshot과 runner recheck tests | PASS: required IMU 차단, optional external IMU error에서 camera/GNSS run 허용 | PASS | PM_REQUIRED: QA 목록은 생산 승인 아님 | pipeline별 생산 sensor policy 승인 |
| REQ-CHK-004 | RTK 근거를 기록하되 임의 pass/fail threshold 없음 | PASS: threshold-free quality tests | PARTIAL: GNSS active, RTK FIX 없음 | PARTIAL | PM_REQUIRED: 현장 threshold 승인 없음 | outdoor RTK dataset; 승인 전 관찰값 유지 |
| REQ-RUN-001 | contextual start가 immutable runId와 실제 RUNNING identity를 연결 | PASS: start/replay/reconciliation tests | PASS: Orin start와 동일 request replay 확인 | PASS | PASS: 검증한 pipeline 범위 | 최종 release commit 재확인 |
| REQ-RUN-002 | device/survey/pipeline/source/config/preflight/output/upload identity를 root record에 영속 연결 | PASS: canonical run과 trusted upload tests | PASS: Orin run에서 receiver receipt까지 동일 context 확인 | PASS | PASS: 검증한 chain 범위 | 장기간 보존 뒤 재조회는 후속 |
| REQ-RUN-003 | 휴대전화/API가 끊겨도 Jetson child와 output 지속 | PASS: process/session 독립 tests | PASS: API 재시작 중 수집 지속 | PASS | PARTIAL: 장시간 phone/radio 단절 미검증 | S22 network loss 장시간 시험 |
| REQ-RUN-004 | autostart opt-in과 contextual one-shot launch, legacy bypass 차단 구현 | PASS: absent/stale/consumed launch와 legacy start 차단 tests | PARTIAL: contextual start 확인, Orin reboot autostart 시험 없음 | PASS | PARTIAL | Orin reboot 뒤 enable 상태와 무재생 확인 |
| REQ-RUN-005 | 같은 runId의 log, route, quality, output과 history/map 연결 구현 | PASS: persistence/API/UI tests | PARTIAL: Orin log/output 확인, 실제 문제구간 map과 tablet 미확인 | PARTIAL | PARTIAL | outdoor route/quality를 S22 history/map에서 대조 |
| REQ-RUN-006 | operator stop과 natural completion을 실제 footer로 구분 | PASS: STOPPING race/footer/recovery tests | PASS: Orin explicit stop이 `STOPPED`, output `FINAL` | PASS | PASS: 검증한 stop 경로 | 장시간 forced-kill 복구는 후속 |
| REQ-RUN-007 | sensor/RTK 저하에서 수집 지속하고 problem interval 기록 | PASS: stale/loss/clock-gap tests | PASS: optional external IMU error 중 Orin 수집과 output 지속 | PASS | PARTIAL: outdoor RTK 저하 미검증 | 실제 RTK loss/recovery 장시간 interval |
| REQ-STO-001 | 종료 run에서 canonical output root/path/outputId 직접 탐색 구현 | PASS: run source lookup tests | PASS: Orin run output과 log/context exact identity | PASS | PASS | retention 기간 뒤 lookup은 후속 |
| REQ-STO-002 | 기대 file/bytes/pattern과 종료 evidence manifest 구현 | PASS: empty/partial/metadata exclusion tests | PASS: 387 files, 1,083,395,576 bytes, FINAL/SATISFIED | PASS | PM_REQUIRED: QA 1 file/1 byte는 생산 승인 아님 | pipeline별 expected output 승인 |
| REQ-UPL-001 | Jetson이 공개 HTTPS receiver로 직접 upload하고 앱이 job 감시 | PASS: backend/receiver/Android tests | PASS: 389 files, 1,083,396,422 bytes 실제 upload COMPLETED | PASS | PASS: 검증한 target/run | 장시간 다른 network 경로는 후속 |
| REQ-UPL-002 | offset resume, batch, retry/cancel과 session identity 유지 구현 | PASS: interruption/idempotency tests | PASS: 중단 13 MiB resume와 3-file batch 실제 server | PASS | PASS | LTE handover 중 resume는 미검증 |
| REQ-UPL-003 | COMPLETED 뒤 remote totals와 모든 object hash MATCHED 검증 | PASS: receipt/hash mismatch tests | PASS: 실제 389 files 전체 hash MATCHED | PASS | PASS | 없음; 새 receiver version마다 회귀 |
| REQ-UPL-004 | fresh verification 전 원본 제거 차단과 mismatch 보존 구현 | PASS: delete gate/trash tests | PASS: fresh verification, source trash/restore와 context 보존 | PASS | PASS | 자동 purge 없음은 의도된 정책 |
| REQ-SRV-001 | Jetson local API와 독립된 Android direct receiver client 구현 | PASS: direct repository/UI tests | PARTIAL: 공개 receiver 실제 동작, phone LTE 단독 경로 미확인 | PARTIAL | PARTIAL | Jetson OFF와 S22 LTE에서 직접 조회 |
| REQ-SRV-002 | cache source, refreshedAt과 stale 상태 표현 구현 | PASS: process restart/offline cache tests | PARTIAL: 대표 S22 UI PASS, offline 장시간 cache 미확인 | PARTIAL | PARTIAL | S22 process death/offline timestamp |
| REQ-SRV-003 | environment binding과 wrong-environment 차단 구현 | PASS: server/client environment tests | PASS: 실제 wrong environment 409 | PASS | PASS | production profile 변경 시 재검수 |
| REQ-SRV-004 | 권한·크기 제한 image/video preview 구현 | PASS: MIME/size/player tests | PARTIAL: oversize 413 실제 server, phone/tablet media preview 미확인 | PARTIAL | PARTIAL | 실제 image/video와 physical tablet |
| REQ-DEL-001 | receiver와 Jetson run/source를 trash/restore로 복구 가능하게 구현 | PASS: local/receiver trash tests | PASS: run/source trash, duplicate DELETE, restore, fresh verification 실제 확인 | PASS | PASS: 자동 purge 없음이 현재 계약 | 장기 retention은 운영 정책으로 별도 결정 |
| REQ-DEL-002 | 역할·확인·audit가 있는 파괴 작업 경계 구현 | PASS: authorization/audit tests | PASS: role downgrade/revoke/disable/audit와 trash restore 실제 server | PASS | PASS: 현재 no-purge 계약 | 영구 purge 도입 시 별도 승인과 시험 |
| REQ-UX-001 | 연결·준비·수집·server·GNSS/RTK를 분리한 operator home 구현 | PASS: Compose/ViewModel tests | PARTIAL: S22 실제 viewport·rotation PASS, 최종 UI 19개 중 CoreWorkflow LazyColumn 3개 실패 수정 중 | PARTIAL | PARTIAL | 3개 UI 재시험과 physical tablet |
| REQ-UX-002 | 일반 사용자와 관리자 영역 및 receiver role action 구분 구현 | PASS: navigation/role tests | PARTIAL: server role deny 실제 확인, 전체 S22 role navigation 미확인 | PARTIAL | PARTIAL | VIEWER/OPERATOR/ADMIN physical navigation |
| REQ-UX-003 | 사실·미확인 범위·다음 행동과 partial/unknown 표현 구현 | PASS: presentation/state tests | PARTIAL: 대표 S22 UI PASS, 오류별 전체 화면 evidence 없음 | PARTIAL | PARTIAL | radio/auth/storage 오류 screenshot과 semantics |
| REQ-QLT-001 | timing-weighted FIX ratio/time/unknown과 no-threshold semantics 구현 | PASS: clock high-watermark와 no-sample tests | PARTIAL: GNSS active지만 outdoor RTK FIX 표본 없음 | PARTIAL | PM_REQUIRED: RTK 생산 판정 미승인 | outdoor fixed/float/loss 로그 교차검증 |
| REQ-QLT-002 | history/map에 같은 run의 sensor/RTK interval과 nullable 위치 표시 | PASS: serialization/index/render tests | PARTIAL: physical route 문제구간과 tablet 지도 미확인 | PARTIAL | PARTIAL | outdoor run을 S22와 tablet에서 대조 |
| REQ-QA-001 | 42행 acceptance와 phone/tablet/Orin/receiver matrix 유지 | PASS: checker가 42 ID를 정확히 한 번 확인 | PARTIAL: S22 실제·wide viewport, Orin, receiver 증거 있음; physical tablet 없음 | PARTIAL | PARTIAL | physical tablet과 남은 UI 3개 실행 |
| REQ-QA-002 | 자동·실장치·demo·운영 evidence gate 분리 | PASS: 문서 checker와 report 구조 | PASS: 실제 환경 증거를 자동 결과와 분리 기록 | PASS | PASS: 범위별 판정 유지 | 전체 42행 일괄 PASS 금지 |
| REQ-QA-003 | 데이터 손실·오제어·인증 오류를 hard blocker로 처리 | PASS: blocker queries와 negative tests | PASS: 401/403/409, mismatch, restore 경로 실제 확인 | PASS | PASS: 검증한 receiver/run 범위 | 새 Astra FAIL은 waiver 금지 |
| REQ-QA-004 | freeze, diagnosis, rollback과 release checklist 제공 | PASS: QA/runbook tests | PASS: API package와 receiver DB/package 실제 rollback·복귀 | PASS | PARTIAL: PR 7 CI, main 병합과 실패한 UI 3개 수정·재시험 대기 | 최종 integration review에 commit·artifact·배포 갱신 |

## 현재 판정

- Survey Project/Section, contextual preflight/start, run→output→upload→receipt chain, recoverable local deletion의 Cycle 01 P0 공백은 구현됐고 S22·Orin NX·공개 receiver의 대표 경로에서 실제 증거를 확보했다.
- QA 정책 `required camera+gnss`, `optional imu`, `minFreeBytes 1 GiB`, `expectedOutput 1 file/1 byte`는 검증 fixture다. 생산 작업별 sensor/storage/output threshold는 `PM_REQUIRED`로 남는다.
- Outdoor RTK FIX, physical tablet, 장시간 수집, 전체 BLE·Wi-Fi Direct·LTE 전환은 미검증이다. 넓은 viewport와 rotation은 S22에서 검증했지만 tablet 증거가 아니다. 조직 SSO와 자동 purge는 현재 범위가 아니다.
- 문서 작성 시점에는 실패한 CoreWorkflow LazyColumn UI 3개 수정·재시험, PR 7 CI, main 병합과 최종 배포 보고가 남았다. 그러므로 모든 42개 요구사항의 글로벌 PASS나 무조건적인 운영 release 승인을 선언하지 않는다.
