# 요구사항 추적표

기준일: 2026-09-14
기준 commit: `e41b752a72be4e5367c1108ab32a2725c8fb4dec`

이 문서는 제품 요구사항, 현재 구현 근거, 2026-09-14 작업 owner, 합격 증거를 연결한다. 이후 execution report와 AG07 acceptance matrix는 아래 ID를 그대로 사용한다.

## 1. 상태 정의

| 상태 | 뜻 |
|---|---|
| 구현됨 | 기준 commit 코드에 요구 행동이 있으며 파일/API 근거를 확인함 |
| 부분 구현 | 일부 행동은 있으나 합격 기준 전체를 충족하지 않음 |
| 요청됨 | 이번 작업 세트에서 구현해야 하나 기준 commit에는 없음 |
| 계약만 확정 | AG01이 제품 규칙을 정했으며 코드 owner/구현이 아직 필요함 |
| PM 결정 필요 | 임계값·권한·업무 정책을 인간 PM이 결정해야 함 |

`구현됨`은 실제 장비 또는 운영 환경 합격을 뜻하지 않는다. 자동·실장치·운영 증거는 별도로 기록한다.

## 2. 요구사항 추적

| ID | 요구사항 / 합격 기준 | 기준선 상태와 코드 근거 | 2026-09-14 owner | 필요한 합격 증거 / 출시 영향 |
|---|---|---|---|---|
| REQ-CON-001 | 사용자가 여러 등록 장비 중 한 대를 선택하고 모든 제어 요청이 같은 `deviceId`에만 적용된다. | 구현됨: `JetsonRepository.kt`, `DeviceWorkspace.kt`, transport state의 device 검사 | AG03 | 장비 A→B 전환과 늦은 A 응답 격리 unit/instrumented test. 잘못된 장비 제어는 운영 release blocker |
| REQ-CON-002 | LAN과 Wi-Fi Direct의 현재 경로, BLE 제한 연결, offline을 서로 구분한다. | 구현됨: `UserConnectionStage.kt`, `ControlCapabilities.kt`, `TransportPolicy.kt` | AG02, AG03 | 각 연결 상태 화면·capability test |
| REQ-CON-003 | 화면 잠금·일시 단절 후 같은 장비를 재인증하고 실제 pipeline/upload/storage 상태를 다시 조회한다. | 부분 구현: 각 ViewModel의 connection generation과 refresh/polling | AG03 | 잠금/복귀, endpoint 전환, stale response 회귀 test. 현장 운영 필수 |
| REQ-CON-004 | 앱 연결 단절을 수집 중단으로 해석하지 않는다. 마지막 관찰 시각과 확인 필요 상태를 표시한다. | 부분 구현: pipeline은 systemd 독립 실행, ViewModel은 offline 시 상태 보존/재조회. 통합 사용자 표현 미완료 | AG02, AG03, AG04 | 연결 단절 중 Jetson 수집 지속 + 재연결 동일 run 증거 |
| REQ-CON-005 | mutation 응답 유실 시 자동 중복 start/stop을 하지 않고 read로 실제 상태를 조정한다. | 부분 구현: one-shot request, `pendingActions`, `JetsonCommandResultUnknownException` 상태 조회 | AG03, AG04 | start/stop 응답 유실·지연·중복 요청 test. 데이터/제어 blocker |
| REQ-CTX-001 | 실행 전에 `projectId`를 선택하고 실행 동안 고정한다. | 계약만 확정: project entity 없음 | 통합 단계 신규 owner 지정 | entity 저장·선택·실행 연결 test. 현장 운영 release blocker |
| REQ-CTX-002 | 프로젝트 소속 `sectionId`를 선택하고 실행 동안 고정한다. | 계약만 확정: section entity 없음. `RoutePoint.segment`는 업무 구간 entity가 아님 | 통합 단계 신규 owner 지정 | project/section 관계와 변경 방지 test. 현장 운영 release blocker |
| REQ-CTX-003 | 직원 인증과 프로젝트별 역할/접근 권한을 검증한다. | 요청됨: receiver는 장비 bearer token만 지원 | AG05 계약 및 구현, 통합 확인 | 직원 계정 allow/deny와 project scoping test. 인증 문제는 release blocker |
| REQ-TASK-001 | 등록된 작업을 선택하고 `pipelineId`, release/config, output 위치를 확인한다. | 부분 구현: `ManagedPipeline.kt`, `PipelineScreens.kt`, `/v1/pipelines` | AG02, AG04 | 선택 작업 요약과 backend 동일성 test |
| REQ-TASK-002 | 외부 수집 프로그램을 안전한 snapshot/venv/entrypoint로 등록한다. | 구현됨: pipeline registration, folder discovery, `pipeline_layout.py`, `register-pipeline.py` | AG04 | register/discover/runtime unit test와 Jetson install evidence |
| REQ-CHK-001 | 시작 전에 장비 identity, 작업, 시간, 저장, 필수/선택 센서, GNSS/RTK, server 상태를 항목별로 보여 준다. | 부분 구현: 시간 동기화와 개별 화면 존재, 통합 preflight와 snapshot 없음 | AG02, AG04, AG06 | preflight 상태 matrix와 snapshot→run trace test. 승인 blocker 누락 시 운영 blocker |
| REQ-CHK-002 | 저장 root/path의 쓰기 가능성과 작업 정책의 최소 여유량을 시작 전에 점검한다. | 요청됨: output metadata 일부 존재, runtime 저장 사전점검 보강 필요 | AG04; 임계값은 인간 PM | full/read-only/missing path test. 임계값 없으면 관찰값만 표시 |
| REQ-CHK-003 | 필수 센서와 선택 센서를 구분하고 승인된 필수 센서 실패만 차단한다. | 요청됨 | AG06; 목록은 인간 PM | 작업별 sensor policy test. 미결 정책 명시 |
| REQ-CHK-004 | RTK 상태의 근거 없는 합격 임계값을 만들지 않는다. | 계약 확정 | AG06 | 원자료/경고만으로 동작하는 unit test, PM 승인 전 pass/fail 부재 |
| REQ-RUN-001 | start 성공은 선택 pipeline이 실제 `RUNNING`이고 새 실행 identity가 확인된 때다. | 부분 구현: pipeline state polling과 log files 있음. start 결과와 run identity의 원자 연결 부족 | AG04, AG03 | 요청 수락과 관찰 성공 분리 test. 운영 핵심 |
| REQ-RUN-002 | 한 실행에 device/project/section/pipeline/source revision/config/start preflight/output identity를 추적한다. | 요청됨: `TaskRun`은 id/pipeline/label/log/time/state/exit만 보유 | AG04 + 통합 project/section owner | metadata schema와 restart/recovery persistence test. 운영 release blocker |
| REQ-RUN-003 | 휴대전화가 끊겨도 시작한 Jetson 수집은 계속된다. | 구조상 구현: systemd pipeline은 앱 session과 독립 | AG04, AG03 | 실제/모의 연결 단절 중 process·output 지속 증거 |
| REQ-RUN-004 | 재부팅 자동 재시작은 opt-in이며 기본은 실제 상태 확인 후 사람 시작이다. | 부분 구현: pipeline autostart 설정 존재, 기준 문서/일부 설치 default 확인 필요 | AG04 | fresh registration와 reboot policy test |
| REQ-RUN-005 | 수집 중 상태, 로그, 경로, 품질 문제 구간을 같은 run에 연결한다. | 부분 구현: `FieldToolsViewModel.kt`, `TaskRun`, `TaskRoute`, log API | AG04, AG06, AG02 | 동일 run identity의 log/route/quality 조회 test |
| REQ-RUN-006 | stop 성공은 비실행 종결 상태, `finishedAt`, 결과/exit와 로그를 관찰한 때다. | 부분 구현: pipeline state와 log history 있음. 정상 `STOPPED`/`COMPLETED` 정책 미결 | AG04; 정상 결과 정책은 인간 PM | stop, timeout, response loss, failed 종료 test |
| REQ-RUN-007 | RTK 저하 기본 행동은 수집 지속 + 문제 구간 기록이다. | 요청됨 | AG06 | quality degradation 중 pipeline 지속과 interval 기록 test |
| REQ-STO-001 | 종료 run에서 Jetson output root/path/session을 직접 찾을 수 있다. | 부분 구현: `/v1/fs/roots`, list/file와 `ManagedPipeline.outputRootId/outputPath` | AG04, AG02 | run→output identity 탐색 test |
| REQ-STO-002 | Jetson 저장 확인은 경로 존재, 기대 파일 수·총량·종료 시각 증거를 포함한다. | 요청됨/부분 구현: 파일 browser는 있으나 run 기대 합계 계약 없음 | AG04, AG05 경계 협의 | empty/partial/missing output test. 미완료 시 서버 업로드 금지 또는 경고 정책 확인 |
| REQ-UPL-001 | Jetson이 HTTPS receiver로 직접 upload하며 앱은 job을 생성·감시한다. | 구현됨: `uploads.py`, upload API, `UploadViewModel.kt` | AG05/AG04 변경 후 회귀 | receiver integration test, TLS/target test |
| REQ-UPL-002 | upload는 재개 가능하고 동일 job/session identity를 유지하며 실패·취소·재시도를 구분한다. | 구현됨: queue states, offsets/batch, retry, receiver session id | AG05 | interrupted transfer resume/retry/idempotency test |
| REQ-UPL-003 | 서버 수신 완료는 job `COMPLETED` + verification `matched=true` + 동일 `remoteSessionId` 증거다. | 구현됨/표현 보강 필요: `UploadJob`, `UploadVerification`, receiver verification API | AG05, AG02 | totals/hash match와 mismatch test; mismatch는 release blocker |
| REQ-UPL-004 | 검증 전 또는 server 불가 시 Jetson 원본을 유지한다. | 구현됨: source deletion은 `deletionEligible`/verification gate와 별도 API | AG05 | verify failure/timeout에서 delete 거절 test. 데이터 손실은 blocker |
| REQ-SRV-001 | Jetson 연결 상태와 무관하게 휴대전화 인터넷으로 receiver session/file/verification을 조회한다. | 요청됨: 현재 Android 조회는 Jetson local API proxy. receiver library API 자체는 존재 | AG05 | Jetson offline 상태의 Android direct-client test. 운영 필수 |
| REQ-SRV-002 | server 최근 목록 cache에 마지막 성공 갱신 시각과 stale 상태를 표시한다. | 요청됨 | AG05, AG02 | process restart/offline cache test와 UI evidence |
| REQ-SRV-003 | 개발/시험/운영 server를 구분하고 운영 결과를 잘못된 환경에 전송·조회하지 않는다. | 요청됨: target label/url은 있으나 environment 계약 없음 | AG05 | environment binding·wrong-env prevention test |
| REQ-SRV-004 | 이미지·영상 preview와 경로 요약을 권한/크기 제한 안에서 제공한다. | 부분 구현: file preview와 receiver max preview size, Android media presentation 존재 | AG05, AG02 | image/video/oversize/unsupported MIME test |
| REQ-DEL-001 | 일반 제거는 soft-delete + Undo + 휴지통/복원으로 복구 가능하다. | 요청됨. 현재 일부 delete는 즉시 영구 삭제지만 확인 절차 존재 | AG02 UX, AG05 storage API | delete/undo/restore/retention test. 지원 없는 API를 성공으로 표시 금지 |
| REQ-DEL-002 | 영구 삭제는 별도 권한·명시적 확인·감사 증거가 필요하다. | 부분 구현: confirmation headers와 device ownership은 있으나 직원 역할/audit 미완료 | AG05 | authorization/confirmation/audit test. 데이터 손실 blocker |
| REQ-UX-001 | 홈은 연결, 시작 준비, 수집 상태, 다음 행동을 우선하며 연결/수집/인터넷/GNSS·RTK 상태를 분리한다. | 요청됨 | AG02 | phone/tablet screenshot + semantics/navigation test |
| REQ-UX-002 | 일반 사용자와 관리자/개발자 영역을 분리한다. | 부분 구현: 화면은 있으나 권한 기반 정보 구조 부족 | AG02, AG05 | role별 navigation/authorization test |
| REQ-UX-003 | 오류 문구는 확인된 사실, 미확인 범위, 다음 행동을 설명한다. | 부분 구현: 일부 ViewModel의 결과 확인 필요 문구 존재 | AG02, 모든 기능 owner | 오류/부분 성공 상태별 UI evidence |
| REQ-QLT-001 | run별 RTK FIX 비율, 시간, 문제 구간을 계산하되 승인 전 임의 pass/fail을 하지 않는다. | 요청됨 | AG06 | deterministic quality fixtures + no-threshold behavior test |
| REQ-QLT-002 | 지도와 작업 이력에서 sensor/RTK 문제 구간을 같은 run에 표시할 데이터 계약을 제공한다. | 요청됨 | AG06, AG02 | segment serialization/persistence/render evidence |
| REQ-QA-001 | Android phone/tablet/Orin NX 지원 matrix와 핵심 업무 acceptance matrix를 유지한다. | 요청됨 | AG07 | 문서화된 matrix + 실행 증거 링크 |
| REQ-QA-002 | 자동 test, 실장치 test, demo 합격, 현장 운영 합격을 구분한다. | 계약 확정, 현재 통합 evidence 없음 | AG07 | 각 결과에 환경·commit·시각·증거 분류 |
| REQ-QA-003 | 데이터 손실, 잘못된 장비 제어, 인증 문제를 운영 release blocker로 처리한다. | 계약 확정 | AG07, 통합 | blocker query와 release gate 결과 |
| REQ-QA-004 | rollback·진단 절차와 demo freeze/internal release checklist를 제공한다. | 일부 진단 문서 존재, 이번 release 체계 요청됨 | AG07 | checklist dry run와 artifact evidence |

## 3. 실행 간 공통 계약

### 상태 계약

- `정상`: 대상에서 최종 상태와 identity를 관찰했다.
- `오류`: 명시적 거절, `FAILED`, identity/verification 불일치를 관찰했다.
- `부분 성공/확인 필요`: 앞 단계 성공 또는 mutation 가능성은 있으나 최종 관찰이 없다.

모든 owner는 request accepted와 operation completed를 분리한다. offline/timeout을 operation failed로 자동 변환하지 않는다.

### 추적 identity

최종 통합 모델은 최소한 다음 연결을 잃지 않아야 한다.

```text
deviceId
  + projectId
  + sectionId
  + pipelineId / sourceRevision / configRevision
  -> runId / logId / startedAt / finishedAt
  -> outputRootId / outputSessionOrPath / fileCount / bytesTotal
  -> uploadJobId / targetEnvironment / remoteSessionId
  -> verification matched / contentSha256 / verifiedAt
```

ID가 없는 중간 구현은 label이나 경로를 영구 identity로 간주하지 않는다.

## 4. 통합 요청

| ID | 요청 | 이유 | 결정 owner |
|---|---|---|---|
| INT-AG01-001 | project/section entity, 선택 저장, run 연결을 구현할 Android/backend owner 지정 | 7개 작업의 명시된 소유 영역에 완전한 domain owner가 없음 | merge-stage lead + 인간 PM |
| INT-AG01-002 | run metadata와 Jetson output session의 공통 schema를 AG04 결과와 통합 | 저장 확인과 upload source를 같은 실행에 연결해야 함 | AG04 + merge-stage lead |
| INT-AG01-003 | AG05 직원/프로젝트 권한 구현 범위를 검토하고 계약만 남은 경우 별도 owner 지정 | 기존 receiver는 device token만 있어 운영 직접 조회 권한을 충족하지 못함 | AG05 + 인간 PM |
| INT-AG01-004 | 시작 blocker의 센서 목록, 저장 여유량, 정상 stop 결과, RTK 임계값을 확정 | 근거 없는 값은 코드에 넣을 수 없음 | 인간 PM, AG04, AG06 |
| INT-AG01-005 | soft-delete UI와 receiver/device storage 복구 API의 지원 범위를 맞춤 | UI만 성공을 표시하면 데이터 복구 계약이 거짓이 됨 | AG02 + AG05 + merge-stage lead |

## 5. 합격 판정 규칙

1. 각 요구사항은 **코드 또는 문서 존재**, **자동 검증**, **필요 시 실장치/운영 검증**을 별도 열로 기록한다.
2. `부분 구현`, `요청됨`, `계약만 확정`인 P0 요구사항은 현장 운영 합격으로 바꾸지 않는다.
3. hardware/network가 없어 실행하지 못한 시험은 실패가 아니라 미실행 gap으로 기록하되 release gate에서는 미충족으로 남긴다.
4. 시연에서는 수기 project/section context 같은 제한을 명시할 수 있지만 현장 운영에서는 REQ-CTX-001~003과 REQ-RUN-002를 충족해야 한다.
5. AG07은 이 표의 ID별 결과와 artifact 경로를 acceptance matrix에 연결한다.
