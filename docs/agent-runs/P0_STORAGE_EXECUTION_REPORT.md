# P0 storage·receiver 실행 보고서

작성일: 2026-09-14  
작업 브랜치: `work/p0-storage-20260914`

## 구현 결과

- Jetson의 파일·디렉터리와 실행 log/route/quality sidecar를 같은 filesystem의 예약 휴지통으로 원자 이동한다. 전이 journal을 fsync하고 시작 시 미완료 이동·복원을 실제 경로 상태로 조정한다. 목록과 명시적 복원을 제공하며 자동 purge와 영구 삭제 API는 없다.
- 휴지통 parent와 payload directory는 소유권·mode·symlink를 검사하고 fd-relative 경로 처리와 `RENAME_NOREPLACE`를 사용한다. 복원은 동시에 생성된 목적지를 덮어쓰지 않는다. 손상된 journal 하나는 다른 entry를 변경하지 않는다.
- contextual run은 고유 출력 디렉터리와 root 소유 run record를 기준으로 terminal 상태와 `FINAL` manifest가 확인된 뒤에만 linked upload를 시작할 수 있다. pipeline 사용자가 바꿀 수 있는 `.jetson-output-context.json`은 단독 권한 근거로 사용하지 않는다.
- upload job은 10개 필드의 immutable survey/run context와 source inventory identity를 보존한다. 요청 context, root 소유 record, sidecar가 다르거나 전송 중 source가 바뀌면 거부한다. 업로드 source를 휴지통에서 복원한 뒤 검증하면 trash 상태를 조정하고 현재 local/remote content를 다시 비교한다.
- active contextual output과 그 상위 경로의 upload·휴지통 이동을 같은 run-context lock에서 차단한다. pipeline 등록·설정·삭제, 정책, contextual start, legacy control도 같은 lock에서 조정하며 stop intent를 command 전에 기록한다.
- survey project/section CRUD, run policy, preflight, contextual start와 canonical run 조회를 HMAC API에 연결했다. canonical 경로는 `GET /v1/pipeline-runs/{run_id:path}`이며 contextual start는 기존 ManagedPipeline 필드와 top-level `contextualStart`를 함께 반환한다.
- receiver는 survey context를 manifest hash, session, 직원 job, receipt, trash에 별도로 보존한다. `accessProjectId`는 접근 권한 경계이고 survey project ID를 대신하지 않는다. 직원 token의 expiry, rotation, disable, role/project grant 변경을 매 요청 확인하고 ADMIN audit 조회를 제공한다. 장비 token의 기존 영구 완료-data 삭제는 `409`로 거부한다.

## 검증

- backend 전체: `309 tests`, `OK`, 12.528초. 기존 backend venv와 `PYTHONPATH=/usr/lib/python3/dist-packages`를 사용해 native `dbus`/`gi` binding을 포함했다.
- 최종 adapter·runtime·storage 집중 회귀: `104 tests`, `OK`, 11.038초. 별도 end-to-end contextual API fixture 3건을 포함한다.
- upload receiver 전체: `41 tests`, `OK`, 3.947초.
- 추가 회귀는 malformed journal 격리, symlink trash parent, 복원 destination 경합, active output/ancestor lock, terminal+FINAL upload gate, 실행 이력 reconciliation-before-trash, source trash→restore→변조→fresh mismatch, survey context 불변성, 직원 token rotation/expiry/disable을 포함한다.
- Python compile과 `git diff --check`를 통과했다.

## 공개 계약

- upload 시작 body: `{rootId, relativePath, targetId, context?}`. contextual output에서는 Android가 `PipelineRun.uploadContext`를 그대로 `context`에 넣어야 한다. legacy source만 context 생략이 가능하다.
- local trash: `GET /v1/trash?includeRestored=false`, `POST /v1/trash/{trashId}/restore` body `{confirmed:true}`.
- recoverable mutations: `DELETE /v1/fs/entry?root=&path=`, `DELETE /v1/task-runs/{pipelineId}/{logId}`, `DELETE /v1/uploads/{jobId}/source`; 모두 body `{confirmed:true}`가 필요하다.
- survey/run: `/v1/survey/projects`, 하위 sections, `/v1/pipelines/{pipelineId}/run-policy`, `/preflight`, `/contextual-start`, `/v1/pipeline-runs/{run_id:path}`.

## 제한과 운영 주의

- 이 작업자는 실제 Jetson/Android 설치, 서비스 restart, sensor operation, receiver 배포나 운영 데이터 mutation을 수행하지 않았다. 공개 장비·receiver 검증과 배포 증거는 root 검수자가 별도로 기록한다.
- 운영 receiver에는 objects manifest를 읽는 별도 original-name hardlink view가 있다. 향후 승인된 purge가 추가되더라도 view inode lifecycle까지 처리하고 검증하지 않으면 disk byte 회수나 완전 삭제를 보장할 수 없다.
- 조직 IdP 자격 증명과 provisioning 절차는 확정되지 않았다. 구현은 기존 employee token의 구체적인 expiry/revocation/role/project lifecycle을 강화했으며 조직 SSO 자격 증명을 만들지 않는다.
- 보존 기간과 purge 정책은 운영자 결정이 남아 있다. 현재 동작은 명시적 복원까지 보존이며 기존 데이터를 삭제하지 않는다.
