# JetsonController ↔ GEO& Work 연동 계약

v0.3 · 2026-10-18 · v0.1 제안(2026-10-17)에 GEO& Work 세션의 회신(GEON Work main 10d6f1af 기준)과 티켓 계약(6e5dc0d6)을 반영했습니다. GEON Work는 GEO& Work로 이름을 바꾸는 중이며, 이 문서는 새 이름을 씁니다.

현장 장치(Jetson, MMS 호기)의 상태, 수집 실행, 서버 전송 결과를 GEO& Work 채널로 보내 사람이 판단하게 합니다. 장치 원격 제어는 범위 밖입니다.

## 1. 결정 사항

| 주제 | 결정 |
|---|---|
| 알림 경로 | GEO& Work Incoming Webhook(F20)은 운영 API로 유지합니다. 설치·연장·회수는 GEO& Work 관리자 API가 맡습니다. 비밀값을 바꿀 때는 새 연동을 만들고 수신 서버를 전환한 뒤 옛 연동을 회수합니다. |
| 운영 값 | 채널 하나('장치 알림')로 시작하고, 프로젝트는 `project` 필드로 구분합니다. integrationId·channelId·webhook 주소·signing secret은 수신 서버 비밀 설정에만 두고 저장소·앱에는 넣지 않습니다. 소유자가 GEO& Work 서버의 비밀 파일에서 수신 서버로 직접 옮깁니다. |
| 토큰 만료 | 만료되면 GEO& Work가 `404 INTEGRATION_NOT_FOUND`를 돌려줍니다. 수신 서버는 이것을 '연동 만료'로 표시합니다. |
| 페이로드 | `deviceId`·`runId`·`link` 필드는 추가하지 않습니다. 식별자와 측정값은 `details`에 `key=value` 줄로 넣습니다. 스키마에 없는 필드는 보내지 않습니다(GEO& Work가 버린 뒤 서명을 검사하므로 서명이 틀어짐). |
| 링크 | GEO& Work 웹은 http/https만 링크로 만들고 앱 셸은 다른 scheme을 막습니다. `geoand://` 딥링크(J4)는 보류하고 양방향 단계에서 https App Links로 다시 설계합니다. |
| 보드·티켓 | 소유자가 '데이터 수집 구간' 보드 티켓 ↔ 현장 앱 양방향 연동을 승인했습니다(J5~J7). GEO& Work 쪽 계약(boards scope, 행 조회·PATCH)이 오면 시작합니다. 원격 제어는 여전히 없습니다. |
| 디자인 토큰 | 원본은 GEO& Work `apps/web/src/shared/ui/tokens.css`입니다. JSON 내보내기는 없으므로 main 커밋 SHA를 적고 복사합니다. 단추 체계가 바뀌는 중이라 GEO& Work의 다음 배포 뒤에 맞춥니다. |

## 2. 장치 이벤트: Jetson → 수신 서버

`POST /v1/device-events`, 장치 토큰(`Authorization: Bearer`)으로 인증합니다. 수신 서버는 `GET /v1/capabilities`에 아래 값을 광고하고, Jetson은 이 광고가 있을 때만 보냅니다.

```json
"deviceEvents": {"version": 1, "maxEvents": 50, "maxBytes": 65536, "heartbeatSeconds": 300}
```

요청 본문(스키마 `jetson.event.v1`, 필드 추가는 하위 호환으로만):

```json
{
  "schema": "jetson.event.v1",
  "device": {"name": "MMS-JETSON-01"},
  "events": [
    {"eventId": "<uuid4>", "type": "device.temperature", "occurredAt": "2026-10-19T09:12:03+09:00",
     "level": "WARNING", "temperatureC": 82.5, "thresholdC": 80.0, "lastNormalAt": "2026-10-19T09:00:00+09:00"}
  ]
}
```

| type | 필드 | 보내는 때 |
|---|---|---|
| `device.heartbeat` | `onDuty`(bool), `activeRunId`·`activeUploadJobId`(선택) | `heartbeatSeconds`마다. 큐에 쌓지 않고 실패하면 버립니다. |
| `device.temperature` | `level`(NORMAL·WARNING·CRITICAL), `temperatureC`, `thresholdC`, `peakC`·`lastNormalAt`(선택) | 단계가 바뀔 때만. 오름은 연속 2회 평가(30초 간격), 내림은 문턱−3°C 아래가 10분 지속될 때 |
| `device.storage` | `level`, `freePercent`, `freeBytes`, `totalBytes`, `lastNormalAt`(선택) | 단계가 바뀔 때만. WARNING 여유 <15%, CRITICAL <5%, 복귀는 문턱+3%p 이상이 10분 지속될 때 |
| `run.started` | `runId`, `pipelineId`, `label`·`surveyProjectLabel`·`surveySectionLabel`(선택) | 수집 시작 |
| `run.finished` | `runId`, `pipelineId`, `result`(COMPLETED·FAILED·STOPPED), `durationSeconds`·`bytesWritten`·`preciseFixRatio`(0~1)·라벨(선택) | 수집 종료 |
| `upload.failed` | `jobId`, `runId`(선택), `failureCount`(같은 runId 연속 실패 횟수, 1부터), `errorCode`(`[A-Z0-9_]{1,64}`), 라벨(선택) | 업로드 작업 실패 |

- 응답 200: `{"results": [{"eventId": "…", "state": "ACCEPTED" | "DUPLICATE" | "REJECTED", "reason": "…"}]}`. Jetson은 ACCEPTED·DUPLICATE·REJECTED를 모두 큐에서 지우고(REJECTED는 기록), 요청 전체 오류(5xx·네트워크)만 다시 보냅니다.
- 요청 전체 오류: 400(JSON·스키마), 401, 413(`maxBytes` 초과), 429(장치당 분당 60회 초과, `Retry-After`).
- 이벤트별 거부: 모르는 `type`, 필수 필드 누락·형식 오류, `occurredAt`이 7일보다 오래됨. 모르는 필드는 무시합니다.
- 같은 장치의 같은 `eventId`는 한 번만 처리합니다(DUPLICATE).
- 업로드 완료("서버 수신 확인")는 수신 서버가 직접 압니다. 업로드 세션이 COMPLETED가 되고 manifest `context.runId`가 있으면 수신 서버가 이벤트를 만듭니다. Jetson은 따로 보내지 않습니다.
- 응답 없음 판정(2026-10-18 GEO& Work 확정): 수신 서버가 장치별 마지막 수신 시각을 기록하고, 감시 구간 안에서만 판정합니다.
  - 구간 시작: `run.started`.
  - 구간 유지: 수집 중(`run.started` 뒤 `run.finished` 없음)이거나 업로드 중(수신 서버에 그 장치의 열린 업로드 세션이 있거나 마지막 heartbeat의 `onDuty`가 true).
  - 구간 종료: 수집이 끝난 뒤 업로드가 이어지면 그 run의 "서버 수신 확인"에서, 업로드가 없으면 `run.finished`에서 끝납니다. `onDuty: false` heartbeat도 구간을 닫습니다.
  - 구간 안에서 30분 동안 아무 이벤트도 없으면 "응답 없음"(WARNING, `lastHealthyAt` = 마지막 수신 시각), 다시 이벤트가 오면 "응답 복귀"(RECOVERY)입니다. 운행 중 `run.finished` 없이 꺼진 경우도 30분 뒤 WARNING입니다.
  - 구간 밖(운행을 마치고 전원을 끈 장치)은 판정하지 않습니다.
  - Jetson은 시작한 run을 재시작 뒤에도 기억합니다. 다시 켜졌을 때 끝나지 않은 run이 남아 있으면 그 run의 마지막 상태로 `run.finished`를 한 번 보내 구간을 닫습니다.

## 3. GEO& Work 전송: 수신 서버 → GEO& Work

### 3.1 설정

`<data_root>/secrets/geon-work.json`(권한 0600, 경로는 `UPLOAD_RECEIVER_GEON_WORK_CONFIG`로 바꿀 수 있음):

```json
{"version": 1, "webhookUrl": "https://<GEO& Work API>/v1/webhooks/incoming/<integrationId>",
 "channelId": "<uuid>", "signingSecret": "<secret>"}
```

- 파일이 없으면 연동 꺼짐(NOT_CONFIGURED)입니다. 이벤트는 받아 저장하지만 보낼 행은 만들지 않습니다.
- 그룹·다른 사용자 권한이 있거나 https가 아니면 불러오지 않고 오류 상태로 둡니다.
- 파일이 바뀌면 재시작 없이 다시 읽습니다. 나가는 주소는 `webhookUrl`의 호스트 하나입니다.
- 관리 명령: `python -m upload_receiver.admin configure-geon-work --webhook-url <url> --channel-id <uuid>`는 secret을 표준 입력 첫 줄에서 읽어 0600 파일로 원자적으로 씁니다(명령줄 인자·로그에 secret 없음). `geon-work-status`는 상태와 건수만 출력합니다.
- J5~J7을 위해 같은 파일에 `apiBaseUrl`·`apiToken`(`geon_…`)·`boardIds`를 나중에 더합니다.

### 3.2 outbox

알림과 티켓 보고(J7)가 함께 쓰는 SQLite 표입니다.

- 열: `message_id`, `target`(`geon-work`), `kind`(`webhook`, 나중에 `board-patch`), `device_id`, `incident_key`, `status_text`, `idempotency_key`(유일, 선택), `payload_json`, `state`(PENDING·SENDING·SENT·DEDUP·DEAD), `attempts`, `next_attempt_at`, `last_http_status`, `last_error_code`, 생성·갱신·완료 시각.
- 전송 worker는 앱 수명주기 안에서 스레드 하나로 돌고 동시 전송은 1개, 전송 간격 0.5초 이상(분당 120 이하)입니다. 요청 시간 제한은 15초입니다.
- 재시도: 15초에서 시작해 두 배씩, 최대 15분, ±10% 흔들림, 최대 10회 뒤 DEAD(`RETRY_EXHAUSTED`). 429는 `Retry-After`를 따릅니다. 처음 만든 지 24시간이 지난 PENDING 행은 보내지 않고 DEAD(`STALE`)로 둡니다.
- 같은 `incident_key`의 마지막 행과 `status_text`가 같으면 새 행을 만들지 않습니다(GEO& Work도 같은 기준으로 거릅니다).

### 3.3 서명

- `x-geon-timestamp`: 10자리 유닉스 초(NTP 필수, 서버와 ±300초).
- `x-geon-nonce`: `[A-Za-z0-9_-]{8,120}`. 시도마다 새로 만들고 outbox 행 ID 앞부분을 접두어로 씁니다.
- `x-geon-signature`: `"v1=" + hex(HMAC-SHA256(signingSecret, "<timestamp>.<nonce>.<canonical>"))`, UTF-8.
- canonical: 스키마 필드만 담은 객체를 키 재귀 정렬, 공백 없이 직렬화합니다(Python `json.dumps(v, ensure_ascii=False, separators=(",", ":"), sort_keys=True)`). 문자열 앞뒤 공백은 미리 지우고 `severity`·`retryCount`·`actions`는 항상 넣습니다. 보내는 본문은 서명한 canonical 바이트 그대로입니다.
- 시험 벡터(GEO& Work 제공): secret `test-signing-secret-0123456789abcdef`, timestamp `1792240000`, nonce `evt-0001-a1b2c3d4`, canonical `{"actions":["장치 환기 확인"],"channelId":"11111111-2222-4333-8444-555555555555","details":"deviceId=jetson-01\ntemperatureC=82.5\nthresholdC=80","impact":"수집 속도 저하 가능","incidentKey":"jetson:jetson-01:temperature","lastHealthyAt":"2026-10-19T09:00:00+09:00","retryCount":0,"severity":"WARNING","source":"jetson-01 (MMS 3호기)","status":"과열 경고","template":"JETSON_DEVICE"}` → `v1=fd84e54658f0112d8fce6f62559aabde9d2ded72b99510fa8509a559493b744f`.

### 3.4 응답 처리

| 응답 | 행 상태 | 연동 상태 |
|---|---|---|
| 201 | SENT | 정상 |
| 200(중복 제거) | DEDUP | 정상 |
| 409 `WEBHOOK_REPLAY` | SENT | 정상 |
| 429, 5xx, 네트워크 오류 | 재시도 | 그대로 |
| 400 | DEAD | 그대로(그 행만의 문제) |
| 401 | DEAD | 오류 `SIGNATURE_REJECTED`, 설정이 바뀔 때까지 전송 멈춤 |
| 403 | DEAD | 오류 `FORBIDDEN`, 전송 멈춤 |
| 404 `INTEGRATION_NOT_FOUND` | DEAD | 만료 `EXPIRED`, 전송 멈춤 |
| 409 `INTEGRATION_CHANNEL_UNAVAILABLE` | DEAD | 오류 `CHANNEL_UNAVAILABLE`, 전송 멈춤 |

전송이 멈춘 동안 새 행은 PENDING으로 쌓이고 24시간 규칙을 따릅니다. 로그에는 행 ID·HTTP 상태·오류 코드만 남기고 secret·서명·본문은 남기지 않습니다.

### 3.5 이벤트 → 템플릿

모든 페이로드에 `template`, `channelId`, `source`(장치 표시 이름, 없으면 "장치 " + deviceId 앞 8자, ≤100), `severity`, `status`, `incidentKey`(3~160자, 넘으면 `run:`·`upload:` 뒤를 SHA-256 앞 32자로 줄임), `details`(≤1500), `actions`(≤10개, 각 ≤200), `retryCount`를 넣고, `project`(≤120)·`impact`(≤500)·`lastHealthyAt`(ISO 8601 + 오프셋)는 값이 있을 때만 넣습니다.

| 이벤트 | template | severity | status | incidentKey | impact · actions |
|---|---|---|---|---|---|
| 온도 WARNING / CRITICAL / NORMAL | `JETSON_DEVICE` | WARNING / CRITICAL / RECOVERY | 과열 경고 / 과열 위험 / 온도 정상 | `jetson:{deviceId}:temperature` | 수집 속도 저하 가능 · [장치 환기 확인] / 장치 보호를 위해 꺼질 수 있음 · [장치 환기 확인, 수집 중지 검토] / – · [] |
| 저장 WARNING / CRITICAL / NORMAL | `JETSON_DEVICE` | WARNING / CRITICAL / RECOVERY | 저장 공간 부족 / 저장 공간 위험 / 저장 공간 정상 | `jetson:{deviceId}:storage` | 새 수집을 저장하지 못할 수 있음 · [전송 끝난 수집 정리] / 같음 / – · [] |
| 응답 없음 / 복귀 | `JETSON_DEVICE` | WARNING / RECOVERY | 응답 없음 / 응답 복귀 | `jetson:{deviceId}:heartbeat` | 수집·전송이 멈췄을 수 있음 · [장치 전원·네트워크 확인] / – · [] |
| 수집 시작 / 종료 | `ROADINVENTORY_MMS` | INFO | 수집 시작 / 수집 종료 | `run:{runId}` | – · [] |
| 전송 실패 | `ROADINVENTORY_MMS` | retryCount <3 WARNING, ≥3 CRITICAL | 전송 실패 / 전송 실패 반복 | `upload:{runId 또는 jobId}` | 서버에 결과가 없음 · [앱에서 다시 시도, 수신 서버 확인] |
| 서버 수신 확인 | `ROADINVENTORY_MMS` | RECOVERY | 서버 수신 확인 | `upload:{runId}` | – · [] |

- `details` 줄: 항상 `deviceId=`, 이벤트에 따라 `runId=`·`jobId=`·`temperatureC=`·`thresholdC=`·`freePercent=`·`result=`·`durationSeconds=`·`files=`·`bytes=`·`errorCode=`·`occurredAt=`. status에는 측정값을 넣지 않습니다(중복 제거 기준이 `incidentKey`+`status`).
- `project`: 라벨이 있으면 "프로젝트 · 구간", 없으면 생략. `retryCount`는 `failureCount − 1`, 그 밖은 0.
- 원본 파일, 이미지, IP, 원시 예외 문구, 개인 정보는 넣지 않습니다.

## 4. 전달 상태 조회: 앱 → 수신 서버

`GET /v1/server/device-events/status?projectId=<p>&deviceId=<d>`, 직원 토큰 인증(다른 `/v1/server/*`와 같음). 직원에게 그 프로젝트 권한이 있고 장치가 그 프로젝트에 속할 때만 200, 아니면 404입니다.

```json
{"deviceId": "…",
 "integration": {"state": "ACTIVE" | "NOT_CONFIGURED" | "EXPIRED" | "ERROR", "detail": "INTEGRATION_NOT_FOUND" | null},
 "window": {"hours": 24, "pending": 0, "sent": 3, "dedup": 1, "dead": 0},
 "lastDelivered": {"status": "과열 경고", "at": "…"} | null,
 "lastFailure": {"status": "…", "code": "…", "at": "…"} | null,
 "lastSeenAt": "…" | null}
```

앱 '전송' 화면은 이 값으로 "GEO& Work 알림" 한 줄을 읽기 전용으로 보입니다. 서버 연결 프로필이 없거나 NOT_CONFIGURED·404이면 줄을 숨깁니다. 앱은 GEO& Work 비밀값을 갖지 않습니다.

## 5. 수집 구간 티켓 (J5~J7)

GEO& Work 계약(2026-10-18, GEON Work 커밋 6e5dc0d6, 운영 배포 전)과 이 저장소의 설계입니다. 운영 배포 전에는 이 계약을 흉내 낸 가짜 서버로 시험합니다.

### 5.1 GEO& Work 쪽 계약

- 알림과 별도 연동 'JetsonController 수집 티켓', scope `boards:read`·`boards:write`, 대상 보드는 '데이터 수집 구간' 하나입니다. bot은 그 보드의 업무 행만 고칠 수 있습니다.
- 헤더 `Authorization: Bearer geon_…`(그 연동의 apiToken). token별 분당 120회(세 경로 합산), 429 `RATE_LIMITED`는 `Retry-After` 뒤 같은 키·같은 본문으로 재시도합니다.
- `GET /api/v1/boards/:boardId` → `{id, name, properties[], statusOptions[]}`. 속성 id는 하드코딩하지 않고 이름으로 찾습니다.
- `GET /api/v1/boards/:boardId/rows?status=open|all&updatedSince=<RFC3339>&cursor=&limit=1~100` → `{items[], nextCursor}`. 행: `id, title, values{속성UUID:{type,value}}, status{id,name,category}, assignees[], dueDate, revision, updatedAt, archivedAt`.
- `PATCH /api/v1/boards/:boardId/rows/:rowId` `{expectedRevision, idempotencyKey(8~120자), status?, values?, comment?(1~2000자)}` → `{row, event, deduplicated}`.
- 같은 idempotencyKey는 본문·보드·행이 완전히 같을 때만 재전송으로 처리됩니다. 본문이 다르면 409 `IDEMPOTENCY_KEY_CONFLICT`.
- 409 `ROW_REVISION_CONFLICT`는 응답의 `current`를 봅니다. 사람이 상태를 바꿨으면 덮어쓰지 않고 알립니다. 진행해도 되면 `current.revision`과 새 키(`…:attempt2`)로 다시 보냅니다.
- 그 밖의 400대(`VALIDATION_ERROR`, `INTEGRATION_STATUS_INVALID`, `PROPERTY_VALUE_INVALID`, `INTEGRATION_BOARD_FORBIDDEN`, `BOARD_ARCHIVED`, `ROW_ARCHIVED` 등)는 재시도하지 않고 DEAD + 경고입니다. 429·5xx·네트워크 오류만 백오프합니다.
- 보드 구성: 행 제목 = 구간 이름. 속성 프로젝트(TEXT), 호기(SELECT), 담당(PERSON), 마감(DATE), 상태(STATUS: 할 일·진행 중·완료). 결과 속성 수집 작업자(TEXT), runId(TEXT), 수집 시간(NUMBER, 형식 HOURS: 시간, 소수 둘째 자리), 용량(GB)(NUMBER, 형식 PLAIN: 10^9 바이트 기준 GB, 소수 둘째 자리), 정밀 위치 비율(NUMBER, 형식 PERCENT: 0~100, 소수 첫째 자리). 호기 선택지 이름·id도 보드 응답에서 읽습니다.

### 5.2 수신 서버 (J5·J7)

- 설정: `geon-work.json`에 `"tickets": {"apiBaseUrl": "https://<GEO& Work API>", "apiToken": "geon_…", "boardId": "<uuid>"}`를 더합니다. 운영 값은 소유자가 비밀 파일로 옮깁니다. 나가는 주소는 `webhookUrl`·`apiBaseUrl`의 호스트뿐입니다.
- 보드 구조 확인: 시작할 때와 설정이 바뀔 때 보드를 읽어 위 속성과 상태 선택지를 이름으로 찾습니다. 하나라도 없으면 티켓 상태 `ERROR`(`BOARD_SCHEMA_MISMATCH`)로 두고 PATCH를 보내지 않습니다.
- 동기화: 처음에는 `status=open` 전체, 그 뒤 2분마다 `status=all&updatedSince=<마지막 최대 updatedAt − 5초>`로 `nextCursor`가 없을 때까지 받아 `board_tickets`에 행 id·revision·제목·프로젝트·호기·담당·마감·상태·runId·보관 여부를 저장합니다.
- 장치 ↔ 호기: 관리 명령 `set-device-unit --device-id <uuid> --unit <호기 선택지 이름>`으로 정합니다. 앱의 호기 필터 기본값이 됩니다.
- 앱용 API(직원 토큰, 다른 `/v1/server/*`와 같은 프로젝트 권한 검사):
  - `GET /v1/server/tickets?projectId=&deviceId=&unit=&status=open|all` → `{tickets[], units[], deviceUnit, syncedAt, integration{state, detail}}`. 티켓에는 `rowId, title, project, unit, assignees[], dueDate, status{name, category}, revision, updatedAt, runId, report{state, code}`이 들어갑니다.
  - `POST /v1/server/tickets/{rowId}/start?projectId=` `{runId, deviceId}`: OPERATOR·ADMIN만. 장치가 그 프로젝트에 속해야 하고 티켓이 열려 있어야 합니다(아니면 409 `TICKET_NOT_OPEN`, 다른 runId가 진행 중이면 409 `TICKET_IN_PROGRESS`). runId ↔ rowId를 저장하고 outbox에 키 `run:{runId}:started`, 상태 "진행 중", 값 runId·수집 작업자(직원 표시 이름), 댓글 "수집 시작 · {호기} · {장치 이름}"을 넣습니다. 202를 돌려주고, 같은 요청 재전송은 같은 결과입니다.
  - `POST /v1/server/tickets/{rowId}/complete?projectId=` `{runId}`: 그 runId의 업로드 세션이 COMPLETED("서버 수신 확인")일 때만(아니면 409 `UPLOAD_NOT_CONFIRMED`). 키 `run:{runId}:completed`, 상태 "완료", 값 수집 시간(`run.finished` durationSeconds ÷ 3600, 소수 둘째 자리)·용량(GB)(세션 바이트 ÷ 10^9, 소수 둘째 자리)·정밀 위치 비율(preciseFixRatio × 100, 소수 첫째 자리) 중 아는 값만, 댓글 "수집 완료 · 작업자 {이름} · {수집 시간}시간 · {용량} GB · 파일 {n}개 · run {runId 앞 8자}".
- 전송: J1 outbox의 `board-patch` 종류, Bearer 인증, 보내기 직전의 최신 revision을 씁니다. 성공하면 응답 `row`로 로컬 행을 갱신합니다(`deduplicated: true`면 DEDUP).
- 409 `ROW_REVISION_CONFLICT`: `current`의 상태가 우리가 기대한 이전 상태(시작은 할 일, 또는 같은 runId의 진행 중 / 완료는 진행 중)이고 runId 속성이 비었거나 같으면 `current.revision`과 키 `…:attempt{n}`으로 다시 보냅니다. 아니면 DEAD(`HUMAN_CHANGED`)로 두고 티켓 보고 상태를 충돌로 표시합니다. 자동으로 덮어쓰지 않습니다.
- 업로드가 실패하면 티켓은 그대로 '진행 중'입니다. 경고는 장치 알림 `upload:{runId}`가 맡습니다.

### 5.3 앱 (J6)

- '작업' 루트에 "수집 구간" 목록(보드의 열린 티켓 전부, 담당자 표시)과 호기 필터(기본: 현재 장치의 호기, 모르면 전체)를 둡니다. 서버 연결 프로필이 없으면 목록을 숨깁니다.
- 구간을 고르면 확인 대화상자("{구간} 수집을 시작합니다. 티켓이 '진행 중'으로 바뀝니다.") 뒤 기존 수집 시작 흐름으로 갑니다. Jetson이 run을 시작해 runId가 생기면 시작 보고를 보냅니다. 휴대폰이 오프라인이면 보고를 저장했다가 다시 보냅니다.
- 수집 결과 영수증에서 "서버 수신 확인"이 된 뒤에만 "구간 완료 보고"를 보입니다. 업로드가 실패하면 버튼 대신 '진행 중 유지' 안내를 보입니다.
- VIEWER는 목록만 보고 시작·완료 보고 단추는 이유와 함께 비활성입니다. 충돌(`HUMAN_CHANGED`)은 "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인해 주세요."로 보입니다.

## 6. 작업 목록

| ID | 작업 | 상태 |
|---|---|---|
| J1 | 수신 서버: device-events 엔드포인트, 이벤트·출석 저장, 공용 outbox, 서명 전송 worker, 응답 없음 판정, 업로드 완료 이벤트, 설정·관리 명령, 상태 조회 API | 진행 |
| J2 | Jetson 백엔드: 온도·저장 공간 단계 판정, 수집·업로드 실패 이벤트, 영속 큐, heartbeat, capability 확인 뒤 전송 | 진행 |
| J3 | 앱: '전송' 화면의 GEO& Work 알림 한 줄(읽기 전용) | 진행 |
| J4 | 딥링크 | 보류(양방향 단계에서 https App Links로) |
| J5 | 수신 서버 티켓 동기화(열린 티켓 증분 조회, rowId·revision 저장, 앱용 목록 API) | 계약 받음(§5), J1 병합 뒤 착수 |
| J6 | 앱 '작업' 화면 티켓 목록·호기 필터, 시작 확인 → '진행 중', runId ↔ rowId | 계약 받음(§5), J3 병합 뒤 착수 |
| J7 | 완료 보고(outbox 경유, `run:{runId}:started`·`run:{runId}:completed` 멱등 키, 409 `ROW_REVISION_CONFLICT`는 다시 읽고 사람에게 알림) | 계약 받음(§5), J5와 함께 |

## 7. 회신

GEO& Work 세션("GEON Work 로컬 세션 인수인계")과 이 저장소 세션이 cross-session 메시지로 주고받습니다. 계약을 바꾸면 이 문서를 함께 고칩니다.
