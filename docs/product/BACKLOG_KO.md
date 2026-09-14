# 제품 Backlog

기준일: 2026-09-14
요구사항 기준: [REQUIREMENTS_TRACEABILITY_KO.md](REQUIREMENTS_TRACEABILITY_KO.md)

## 1. 운영 규칙

- **P0**: 대표 업무 흐름 또는 안전·데이터·인증에 필수. 미충족 시 현장 운영 release를 차단한다.
- **P1**: 운영 효율·복구·품질을 높이는 이번 작업 세트의 필수 범위. 미완료 시 제한과 후속 owner를 명시한다.
- **P2**: 2026-09-14 작업 세트가 명시한 후순위. 현재 release 완료로 주장하지 않는다.
- 상태는 `기준선 구현`, `진행 대상`, `owner 미지정`, `PM 결정 대기`, `후순위`로 구분한다.

Backlog 우선순위는 핵심 요청을 선택 사항으로 낮추지 않는다. 특히 프로젝트·구간 context와 실행 추적은 현재 코드에 없더라도 P0 제품 요구다.

## 2. P0 — 현장 운영 release 차단 항목

| BL ID | 요구사항 | 현재 상태 | 실행 owner / 의존성 | 완료 정의 |
|---|---|---|---|---|
| BL-P0-001 | Project/Section domain entity, 선택, 실행 context 고정 | owner 미지정 | merge-stage lead가 Android/backend/data owner 지정; 인간 PM이 source-of-truth/권한 결정 | REQ-CTX-001, REQ-CTX-002 자동 test와 실제 흐름 증거. label-only 임시값 금지 |
| BL-P0-002 | 직원 인증과 프로젝트 역할/권한 | 진행 대상 | AG05, 부족하면 통합 신규 owner; IdP는 인간 PM 결정 | REQ-CTX-003 allow/deny/project isolation test. device token을 직원 인증으로 간주하지 않음 |
| BL-P0-003 | 장비 identity 고정과 stale response 격리 | 진행 대상 | AG03 | REQ-CON-001, 003, 005의 전환·지연·응답 유실 test 통과 |
| BL-P0-004 | 연결 단절 중 수집 지속과 동일 run 복구 | 진행 대상 | AG03 + AG04 | REQ-CON-004, REQ-RUN-003 실제 상태 재조회와 중복 run 없음 |
| BL-P0-005 | 시작 preflight와 승인 blocker 적용 | 진행 대상 | AG04 저장, AG06 센서/품질, AG02 UI; 정책은 인간 PM | REQ-CHK-001~004. 항목별 관찰 시각과 snapshot→run 연결 |
| BL-P0-006 | 실행 metadata와 project/section/output trace | 진행 대상 + owner gap | AG04 runtime schema + BL-P0-001 owner | REQ-RUN-001, 002, 005, 006. 단일 run identity로 전체 증거 연결 |
| BL-P0-007 | Jetson output 저장 확인 | 진행 대상 | AG04 + AG02 | REQ-STO-001, 002. expected file count/bytes/path/time 증거 |
| BL-P0-008 | server 완전 수신과 mismatch 차단 | 기준선 구현, 회귀 필요 | AG05 + AG07 | REQ-UPL-001~004 통합 test. `COMPLETED` + `matched=true` |
| BL-P0-009 | Jetson 없는 휴대전화 server 직접 조회 | 진행 대상 | AG05 + AG02 | REQ-SRV-001. 동일 remote session/file/verification을 cellular 또는 독립 인터넷에서 조회 |
| BL-P0-010 | 개발/시험/운영 server 분리 | 진행 대상 | AG05 | REQ-SRV-003. wrong-environment 전송/조회 방지 test |
| BL-P0-011 | data deletion authorization와 복구 | 진행 대상 | AG05 API + AG02 UI | REQ-DEL-001, 002. soft-delete/Undo/restore와 영구 삭제 권한. 원본 손실 test 없음 |
| BL-P0-012 | 핵심 업무 acceptance/release gate | 진행 대상 | AG07 | REQ-QA-001~004. 자동/실장치/demo/field 결과 분리, blocker 0 |

## 3. P1 — 이번 작업 세트 운영 완성도

| BL ID | 요구사항 | 현재 상태 | owner | 완료 정의 |
|---|---|---|---|---|
| BL-P1-001 | 연결/수집/인터넷/GNSS·RTK 상태를 분리한 홈과 다음 행동 | 진행 대상 | AG02 | REQ-UX-001 phone/tablet evidence |
| BL-P1-002 | 일반 사용자와 관리자/개발자 기능 분리 | 진행 대상 | AG02 + AG05 authorization | REQ-UX-002 role별 접근 evidence |
| BL-P1-003 | 정상/오류/부분 성공 공통 문구와 recovery action | 진행 대상 | 모든 기능 owner, AG02 통합 | REQ-UX-003. timeout을 실패로 오표시하지 않음 |
| BL-P1-004 | server 목록 cache, 마지막 갱신 시각, stale 표현 | 진행 대상 | AG05 + AG02 | REQ-SRV-002 offline/restart test |
| BL-P1-005 | 이미지·영상 preview와 경로 요약 | 진행 대상 | AG05 + AG02 | REQ-SRV-004 type/size/permission test |
| BL-P1-006 | RTK FIX 비율·시간·문제 구간 | 진행 대상 | AG06 | REQ-QLT-001, 002 deterministic calculation/persistence evidence |
| BL-P1-007 | 필수/선택 센서 정책과 문제 구간 | 진행 대상/PM 결정 대기 | AG06 + 인간 PM | REQ-CHK-003, REQ-RUN-007. 승인 전 warning only |
| BL-P1-008 | 재부팅 후 사람 확인 기본 정책 | 진행 대상 | AG04 | REQ-RUN-004 default-off/opt-in test |
| BL-P1-009 | phone/tablet/Orin NX 지원 matrix와 rollback/diagnostics | 진행 대상 | AG07 | REQ-QA-001, 004 artifact와 dry run |

## 4. P2 — 명시적 후순위

| BL ID | 항목 | 선행 조건 | 현재 결정 |
|---|---|---|---|
| BL-P2-001 | 실시간 검지 시 검지 사진 + metadata 전송 | run/output/server trace schema, bandwidth·privacy 정책 | 후순위. 이번 완료 범위 아님 |
| BL-P2-002 | 조사 구간 geofence 기반 시작 보조 | BL-P0-001 section geometry, 위치 오차 정책 | 후순위. 자동 시작이 아니라 사용자 보조를 기본 가정 |
| BL-P2-003 | Xavier NX 지원 확대 | Orin NX 기준 matrix와 backend hardware abstraction | 후순위. 현재 지원으로 주장하지 않음 |
| BL-P2-004 | RTK 판정 기준 고도화 | AG06 관찰 지표, 현장 dataset, 인간 PM 임계값 승인 | 후순위. 근거 없는 pass/fail 금지 |
| BL-P2-005 | 원본 자동 upload | BL-P0-007~010, 네트워크/비용/보존 정책 | 후순위. 자동 삭제와 결합 금지 |

## 5. 기준선 유지 항목

다음 기능은 기준선에 구현되어 있으므로 새 기능처럼 중복 개발하기보다 이번 변경 뒤 회귀를 확인한다.

| 기능 | 근거 | 관련 요구사항 |
|---|---|---|
| QR/BLE 인증, LAN/Wi-Fi Direct 연결 | Android repository/transport, backend BLE/P2P | REQ-CON-001, 002 |
| pipeline 등록·start/stop/restart·log | Android pipeline UI/ViewModel, backend API/manager | REQ-TASK-001, 002; REQ-RUN-001, 006 |
| 실행 이력·log·route 최소 조회 | `FieldToolsViewModel`, `field_tools.py`, `route_recorder.py` | REQ-RUN-005 |
| Jetson file root/list/preview | storage ViewModel, backend filesystem API | REQ-STO-001 |
| upload queue/resume/cancel/retry | `UploadViewModel`, `uploads.py`, receiver offsets | REQ-UPL-001, 002 |
| receiver completed library와 verification | receiver library/session APIs | REQ-UPL-003 |
| verification gate 뒤 source 삭제 | backend upload source API | REQ-UPL-004 |

## 6. 통합 순서와 exit criteria

권장 통합 순서는 AG01 → AG04 → AG05 → AG06 → AG03 → AG02 → AG07이다. 각 단계는 다음 owner가 사용할 contract 또는 API를 먼저 고정한다.

1. **AG01**: PM 결정과 REQ ID를 고정한다.
2. **AG04**: run metadata, actual-state start/stop, output trace와 preflight backend를 제공한다.
3. **AG05**: direct server client/API, auth/role/project, cache/environment, recoverable storage 계약을 제공한다.
4. **AG06**: quality observation와 problem segment 계약을 run에 연결한다.
5. **AG03**: 위 상태를 transport 전환·응답 유실에서도 안전하게 재조정한다.
6. **AG02**: 공통 상태 의미와 다음 행동을 phone/tablet UI에 통합한다.
7. **AG07**: REQ ID별 자동·실장치·demo·field evidence를 판정한다.

통합 exit criteria:

- P0 owner 미지정 항목이 없다.
- P0 요구사항별 구현 commit과 test 또는 명시된 미실행 gap이 있다.
- 데이터 손실, 잘못된 장비 제어, 인증 blocker가 0이다.
- project/section/run/output/upload/server identity가 한 조사 실행으로 추적된다.
- 시연 합격과 현장 운영 합격이 각각 별도로 판정된다.

## 7. 인간 PM 결정 queue

| PMQ ID | 질문 | 결정 없을 때 처리 |
|---|---|---|
| PMQ-001 | Project/Section source-of-truth, geometry, 생성/편집 owner는? | BL-P0-001 운영 release 차단 |
| PMQ-002 | 직원 IdP와 project role model은? | BL-P0-002, 009 운영 release 차단 |
| PMQ-003 | pipeline별 필수 센서와 시작 blocker는? | 관찰값/경고만 제공, 현장 합격 보류 |
| PMQ-004 | 최소 저장 여유량 또는 예상 수집량 규칙은? | 고정 임계값 금지, storage 상태 표시 |
| PMQ-005 | operator stop의 정상 상태/exit code는? | STOPPED와 COMPLETED를 하나로 성공 처리하지 않음 |
| PMQ-006 | RTK 품질 합격 임계값은? | FIX ratio/time/problem segments만 보고 |
| PMQ-007 | local/server trash 보존 기간과 영구 삭제 role은? | 영구 삭제 기능의 운영 release 보류 |
