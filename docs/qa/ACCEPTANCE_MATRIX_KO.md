# 요구사항 Acceptance Matrix

기준 요구사항: `docs/product/REQUIREMENTS_TRACEABILITY_KO.md`의 42개 ID. 이 표는 코드 존재, 자동 검증, 실장치 검증, demo, 내부 운영 합격을 서로 독립적으로 기록한다.

결과 값은 `PASS`, `PARTIAL`, `MISSING`, `NOT_RUN`, `PM_REQUIRED`만 사용한다. `PASS`는 해당 열의 증거만 충족한다. 자동 검증 `PASS`가 실장치나 운영 합격을 뜻하지 않는다. 실행 보고서의 로컬 결과는 해당 worker commit에 한정되고, root가 제공한 통합 결과는 `ROOT_REPORTED_LOCAL`로 구분한다.

현재 자동 증거 색인:

- AG03 `3945b5b`: Android 연결/복구 focused JVM 70개 통과.
- AG04 `bf48810`: backend runtime targeted 86개 통과.
- AG05 `00bfa15` + `9409328`: receiver 37개와 Android direct-server 4개 통과, Astra PASS.
- AG06 `6827e1c`: backend quality targeted 64개와 Android quality/API compatibility 통과.
- 통합 `03b2e93`: root가 2026-09-14에 backend discovery 275개 및 receiver discovery 37개, skip 0을 로컬 실행해 통과했다고 보고했다. 원격 CI 실행 증거가 아니다.
- AG02 UI 결과는 아직 독립 검수 전이므로 현재 표에서 운영 합격 근거로 사용하지 않는다.

| ID | 현재 코드·계약 판단 | 자동 증거 | 실장치·운영환경 증거 | Demo gate | 내부 운영 gate | Blocker / 다음 증거 |
|---|---|---|---|---|---|---|
| REQ-CON-001 | 명시 장비 선택과 session 격리 구현 | PASS: AG03 target-switch/stale-response JVM | NOT_RUN | NOT_RUN | MISSING | Android 두 대와 Jetson 두 대에서 A→B 오제어 없음 확인 |
| REQ-CON-002 | transport/capability 모델 구현, UI 검수 대기 | PARTIAL: AG03 policy와 AG02 branch test | NOT_RUN | NOT_RUN | MISSING | phone/tablet에서 LAN, Direct, BLE, offline 대표 화면 |
| REQ-CON-003 | generation 폐기와 재조회 구현, 잠금 lifecycle 미실행 | PARTIAL: AG03 endpoint/recovery JVM | NOT_RUN | NOT_RUN | MISSING | 화면 잠금·복귀 후 같은 deviceId와 pipeline/upload/storage 재조회 로그 |
| REQ-CON-004 | pipeline은 transport와 독립, offline UI 검수 대기 | PARTIAL: AG04 runtime + AG03 recovery | NOT_RUN | NOT_RUN | MISSING | 연결 단절 중 동일 runId/process/output 지속 실장치 증거 |
| REQ-CON-005 | one-shot mutation과 GET reconciliation 구현 | PASS: 실제 TLS/HMAC start/stop 유실·지연·중복 test | NOT_RUN | NOT_RUN | MISSING | 실제 Jetson proxy fault 시험과 mutation count |
| REQ-CTX-001 | survey project entity와 실행 고정 없음 | MISSING | NOT_RUN | NOT_RUN | MISSING | P0: projectId source, 저장, run 연결 구현 |
| REQ-CTX-002 | survey section entity와 실행 고정 없음 | MISSING | NOT_RUN | NOT_RUN | MISSING | P0: project-section 관계와 run 중 변경 방지 구현 |
| REQ-CTX-003 | receiver access project/role 구현, survey context와 조직 IdP 없음 | PARTIAL: AG05 allow/deny·scope test | NOT_RUN | NOT_RUN | MISSING | 운영 직원 lifecycle, IdP 또는 승인된 token 운영 정책 |
| REQ-TASK-001 | pipeline/source/config/results metadata 구현, 최종 UI 검수 대기 | PARTIAL: AG04 API test | NOT_RUN | NOT_RUN | MISSING | 선택 요약과 Jetson 응답 identity 동일성 device evidence |
| REQ-TASK-002 | snapshot/venv/entrypoint 등록·검증 구현 | PASS: AG04 registration/layout/runtime test | NOT_RUN | NOT_RUN | MISSING | Orin NX 설치와 실제 외부 pipeline 실행 |
| REQ-CHK-001 | 개별 근거는 있으나 통합 preflight snapshot/run 연결 없음 | PARTIAL | NOT_RUN | NOT_RUN | MISSING | P0: device/task/time/storage/sensor/RTK/server snapshot을 run에 저장 |
| REQ-CHK-002 | writable probe와 bytes evidence 구현, 정책/UI 미완료 | PARTIAL: AG04 full/read-only/path test | NOT_RUN | NOT_RUN | PM_REQUIRED | pipeline별 최소 여유량은 승인 전 0/관찰만 사용 |
| REQ-CHK-003 | REQUIRED/OPTIONAL 모델은 있으나 runtime 기본 UNSPECIFIED | PARTIAL: AG06 explicit-policy test | NOT_RUN | NOT_RUN | PM_REQUIRED | 필수 센서 목록과 policy source 승인·runner 연결 |
| REQ-CHK-004 | 임의 RTK pass/fail 임계값 없음 | PASS: AG06 threshold-free interpreter test | NOT_RUN | NOT_RUN | PM_REQUIRED | 현장 임계값은 PM 승인 전 추가 금지 |
| REQ-RUN-001 | RUNNING과 현재 InvocationID 기반 activeRunId 제공 | PASS: AG04 runtime + AG03 adapter/reconciliation | NOT_RUN | NOT_RUN | MISSING | 실제 start에서 새 runId와 output 생성 확인 |
| REQ-RUN-002 | source/config/run 일부만 존재, device/project/section/output 연결 불완전 | MISSING | NOT_RUN | NOT_RUN | MISSING | P0 end-to-end identity schema와 persistence |
| REQ-RUN-003 | systemd 실행과 휴대전화 session 비연동 | PARTIAL: 구조/unit evidence | NOT_RUN | NOT_RUN | MISSING | phone radio/앱 종료 중 process와 output 지속 |
| REQ-RUN-004 | autostart opt-in 기본 false | PASS: AG04 registrar/runtime + AG03 legacy adapter | NOT_RUN | NOT_RUN | MISSING | Orin NX reboot 뒤 등록별 enable 상태 확인 |
| REQ-RUN-005 | run log/route/quality 계약 구현, 통합 UI 검수 대기 | PARTIAL: AG04 + AG06 API/persistence test | NOT_RUN | NOT_RUN | MISSING | 하나의 runId로 log, route, quality, output 조회 |
| REQ-RUN-006 | terminal execution evidence 제공, 정상 종료 정책 미결 | PARTIAL: AG04 stop/runtime + AG03 lost-stop test | NOT_RUN | NOT_RUN | PM_REQUIRED | 정상 operator stop result 정책과 실제 footer/exit 확인 |
| REQ-RUN-007 | 품질 저하가 수집을 중단하지 않고 interval 기록 | PASS: AG06 invalid/stale/non-FIX fixtures | NOT_RUN | NOT_RUN | MISSING | 실제 RTK 저하 중 pipeline/output 지속 |
| REQ-STO-001 | resultsDirectory는 제공, run→root/session identity 불완전 | PARTIAL | NOT_RUN | NOT_RUN | MISSING | 종료 run에서 정확한 output root/path를 직접 탐색 |
| REQ-STO-002 | runtime bytes/preflight는 있으나 기대 파일 수·총량 계약 없음 | MISSING | NOT_RUN | NOT_RUN | MISSING | P0 empty/partial/missing output 판정과 upload gate |
| REQ-UPL-001 | Jetson direct HTTPS upload와 app job API 구현 | PASS: 통합 backend/receiver discovery | NOT_RUN | NOT_RUN | MISSING | 실제 Orin→공개 HTTPS receiver transfer |
| REQ-UPL-002 | resume/retry/cancel/idempotent offset 구현 | PASS: receiver/backend interruption fixtures | NOT_RUN | NOT_RUN | MISSING | 장시간 전송 network interruption 실환경 시험 |
| REQ-UPL-003 | COMPLETED + matched receipt + session identity 구현 | PASS: AG05 totals/hash/mismatch test | NOT_RUN | NOT_RUN | MISSING | 실제 업로드 객체를 receiver에서 독립 재검증 |
| REQ-UPL-004 | verification 전 source delete 차단 구현 | PASS: backend verification/delete test | NOT_RUN | NOT_RUN | MISSING | receiver timeout/불일치에서 Jetson 원본 보존 확인 |
| REQ-SRV-001 | Jetson 독립 Android direct client/data layer 구현, UI 검수 대기 | PARTIAL: AG05 direct repository test | NOT_RUN | NOT_RUN | MISSING | Jetson 전원 OFF에서 phone LTE receiver 조회 |
| REQ-SRV-002 | scope별 stale cache와 refreshedAt 구현, UI 검수 대기 | PARTIAL: AG05 cache test | NOT_RUN | NOT_RUN | MISSING | process restart/offline cache timestamp 화면 |
| REQ-SRV-003 | environment header/binding과 mismatch 거절 구현 | PASS: AG05 server/client environment test | NOT_RUN | NOT_RUN | MISSING | 운영 profile로 test server 접근 거절 실환경 증거 |
| REQ-SRV-004 | bounded image/video preview와 MIME 거절 구현 | PASS: AG05 receiver + Android media test | NOT_RUN | NOT_RUN | MISSING | phone/tablet에서 실제 image/video/oversize 표시 |
| REQ-DEL-001 | receiver trash/restore 구현, 모든 일반 제거와 UI는 미완료 | PARTIAL: AG05 crash-recovery/trash test | NOT_RUN | NOT_RUN | MISSING | device/server 각 제거·Undo·restore·retention 계약 |
| REQ-DEL-002 | 역할·확인은 일부 구현, audit/IdP/purge 정책 미완료 | PARTIAL: AG05 authorization test | NOT_RUN | NOT_RUN | MISSING | 영구 삭제 권한, 이중 확인, 감사 기록과 보존 정책 |
| REQ-UX-001 | operator home 재구성 branch 존재, 독립 검수·화면 증거 없음 | PARTIAL: AG02 local semantics test만 | NOT_RUN | NOT_RUN | MISSING | current commit phone/tablet 대표 화면과 접근성 확인 |
| REQ-UX-002 | 일반/관리자 정보 구조 branch 존재, 권한 기반 완성 아님 | PARTIAL | NOT_RUN | NOT_RUN | MISSING | VIEWER/OPERATOR/ADMIN navigation과 backend deny 증거 |
| REQ-UX-003 | 확인 필요/부분 성공 문구 branch 존재, 전체 상태 matrix 미검수 | PARTIAL: 일부 ViewModel JVM | NOT_RUN | NOT_RUN | MISSING | 오류별 사실·미확인 범위·다음 행동 screenshot |
| REQ-QLT-001 | timing-weighted FIX 근거와 무임계값 해석 구현 | PASS: AG06 deterministic quality fixtures | NOT_RUN | NOT_RUN | PM_REQUIRED | 현장 GNSS 로그 교차검증, 승인 전 pass/fail 금지 |
| REQ-QLT-002 | run quality/interval/route index 계약 구현, UI 증거 없음 | PARTIAL: AG06 serialization/index test | NOT_RUN | NOT_RUN | MISSING | 지도와 이력에서 같은 run 문제 구간 표시 |
| REQ-QA-001 | 지원·acceptance matrix와 consistency checker 추가 | PASS: `scripts/check_qa_acceptance.py` | NOT_RUN | NOT_RUN | MISSING | 각 지원 조합 device evidence 행 채우기 |
| REQ-QA-002 | 증거 등급과 서로 독립인 gate 정의 | PASS: QA 문서와 template | NOT_RUN | NOT_RUN | MISSING | demo/field 실행 record 생성 |
| REQ-QA-003 | 데이터 손실·오제어·인증 hard blocker 정의 | PASS: release checklist blocker query | NOT_RUN | NOT_RUN | MISSING | blocker owner/결과/waiver 없음 확인과 승인 서명 |
| REQ-QA-004 | freeze, internal release, rollback, diagnosis 절차 추가 | PASS: QA 문서 정적 검사 | NOT_RUN | NOT_RUN | MISSING | checklist dry run 및 rollback rehearsal |

## 현재 판정

- 자동 검증은 다수 기능 계약을 확인했지만 실장치·demo·운영환경 증거는 모두 `NOT_RUN`이다.
- `REQ-CTX-001`, `REQ-CTX-002`, `REQ-RUN-002`, `REQ-STO-002`는 P0 구현 공백이다.
- 조직 IdP/직원 lifecycle, 필수 센서, 저장 최소량, 정상 stop 결과, RTK 정책은 승인 또는 구현이 남았다.
- 따라서 이 문서는 demo 또는 내부 운영 release 합격을 선언하지 않는다.
