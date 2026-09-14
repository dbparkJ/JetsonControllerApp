# AGENT 01 실행 보고서

작업: 제품·업무 계약
브랜치: `work/operator-workflow-20260914`
기준 commit: `e41b752a72be4e5367c1108ab32a2725c8fb4dec`
실행일: 2026-09-14
모델: GPT-5.6 Sol High (사용자 override)

## 1. 결과

제품 코드를 변경하지 않고 자전거 도로 조사 대표 흐름과 요구사항 추적 체계를 문서화했다. 요청 접수와 실제 성공을 분리하고, 정상/오류/부분 성공을 공통 의미로 정의했다. 시연 합격과 현장 운영 합격도 별도로 정의했다.

생성 파일:

- `docs/product/PRODUCT_DEFINITION_KO.md`
- `docs/product/FIELD_WORKFLOW_KO.md`
- `docs/product/REQUIREMENTS_TRACEABILITY_KO.md`
- `docs/product/BACKLOG_KO.md`
- `docs/agent-runs/AGENT_01_EXECUTION_REPORT.md`

## 2. 확정한 제품 계약

1. 대표 흐름은 연결 → 프로젝트/구간 → 작업 선택 → 시작 점검 → 수집 → 연결 복구 → 종료 → Jetson 저장 확인 → 서버 수신 확인 → 서버 직접 조회다.
2. start/stop/upload의 요청 수락만으로 성공을 표시하지 않는다. 대상에서 최종 상태와 identity를 관찰해야 한다.
3. 앱 연결 단절은 Jetson 수집 중단이 아니다. 재연결 후 같은 장비의 실제 상태를 조회하고, 결과 미확인 mutation을 중복 전송하지 않는다.
4. Jetson 저장, server receiver 검증, 휴대전화 직접 server 조회는 각각 다른 완료 증거다.
5. 부분 성공은 앞 단계가 확인됐지만 다음 단계 또는 mutation 결과 관찰이 남은 상태다. 오류나 전체 성공으로 축약하지 않는다.
6. project/section context와 실행 metadata 연결은 현재 구현이 없어도 P0 핵심 요구다.
7. 근거 없는 RTK/센서/storage 임계값을 만들지 않으며 인간 PM의 승인 전에는 관찰값과 경고만 제공한다.
8. 시연 통과는 현장 운영 통과가 아니다. 운영은 project/role 권한, 실제 장치/네트워크, 데이터 보존, 장시간·복구 evidence를 요구한다.

## 3. 현재 코드 근거

다음 기준선 구현을 확인해 요구사항의 `구현됨`, `부분 구현`, `요청됨`을 구분했다.

- 장비/transport: `JetsonRepository.kt`, `TransportCoordinator.kt`, `UserConnectionStage.kt`, `DeviceWorkspace.kt`
- pipeline 상태와 결과 미확인 처리: `PipelineViewModel.kt`, `ManagedPipeline.kt`, `LocalApiClient.kt`, `backend/jetson_control/pipelines.py`
- 실행 이력·log·route: `FieldToolsViewModel.kt`, `FieldTools.kt`, `backend/jetson_control/field_tools.py`, `route_recorder.py`
- Jetson 저장소: Android storage ViewModel, backend `/v1/fs/*`
- upload queue와 verification: `UploadViewModel.kt`, `UploadJob.kt`, `backend/jetson_control/uploads.py`
- receiver 완료 library와 독립 API: `upload_receiver/upload_receiver/app.py`, `service.py`, `database.py`

기준선의 Android server storage 화면은 Jetson local API가 receiver를 proxy하는 경로다. receiver의 `/v1/library/sessions`, files, verification endpoint가 존재하지만 휴대전화 직접 client와 직원/project authorization은 기준선에 없다. 이 차이를 REQ-SRV-001과 REQ-CTX-003에 기록했다.

기준선의 `TaskRun`은 실행 id, pipeline, label, log id, 시작/종료 시각, state, exit code만 가진다. project/section, preflight snapshot, output/upload identity의 영속 연결은 없으므로 REQ-RUN-002를 P0 gap으로 기록했다.

## 4. 다른 작업에 전달한 공통 계약

AG04 runtime과 통합 lead에 다음 계약을 조기에 전달했다.

- start 성공: pipeline `RUNNING` + run/log identity 관찰
- stop 성공: 비실행 종결 상태 + finish/exit evidence 관찰
- Jetson 저장 성공: 기대 output/session이 장치 저장소에서 확인됨
- server 수신 성공: upload `COMPLETED` + verification `matched=true`
- server 직접 조회 성공: receiver library session/files/verification에서 동일 session 확인
- 오류: 명시적 reject/`FAILED`/불일치
- 부분 성공/확인 필요: mutation 가능성은 있으나 결과 미관찰, local data만 확인, receiver 직접 확인 대기

후속 worker는 `docs/product/REQUIREMENTS_TRACEABILITY_KO.md`의 REQ ID를 execution report와 QA evidence에 재사용해야 한다.

## 5. 통합 요청과 release blocker

| ID | 내용 | 상태 |
|---|---|---|
| INT-AG01-001 | Project/Section entity, 선택 저장, run 연결 구현 owner 지정 | 7개 현재 scope에 완전한 owner가 없어 merge-stage lead와 인간 PM 결정 필요. 운영 release blocker |
| INT-AG01-002 | AG04 run metadata와 Jetson output session schema 연결 | AG04/통합 단계 확인 필요 |
| INT-AG01-003 | AG05 직원 인증·role·project 권한이 실제 구현인지 계약만인지 확인 | 미완료 시 운영 release blocker |
| INT-AG01-004 | 필수 센서, 저장 최소량, 정상 stop 결과, RTK 임계값 결정 | 인간 PM 결정 필요. 결정 전 임의 blocker/pass 금지 |
| INT-AG01-005 | AG02 soft-delete UX와 AG05 device/server 복구 API 지원 일치 | API 없는 성공 표현 금지 |

## 6. 검증

문서 전용 변경이므로 `AGENTS.md`에 따라 Android build나 hardware test를 실행하지 않았다. 다음을 확인했다.

- 생성한 다섯 파일의 존재와 상대 링크 대상
- 기준선 코드/API를 근거로 한 capability 상태
- `git diff --check`
- 변경 범위가 `docs/product/**`, `docs/agent-runs/AGENT_01_EXECUTION_REPORT.md`뿐인지 확인

## 7. 실제 한계

- 장치 배포, 서비스 재시작, 센서 작동, 운영 server 접속을 수행하지 않았다.
- project/section source-of-truth, 직원 IdP, 필수 센서 목록, storage 최소량, RTK 합격 임계값, 정상 operator-stop 결과, trash 보존 기간은 인간 PM 결정이 없다.
- 이 보고서는 제품 계약과 추적 기준을 완성한 결과이며 후속 AG02~AG07의 구현·test 통과를 주장하지 않는다.
- 기준선 이후 병렬 branch의 변경은 이 branch에 병합하지 않았으므로 통합 단계에서 상태 표를 후속 execution report와 대조해야 한다.
