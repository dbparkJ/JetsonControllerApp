# JetsonController ↔ GEON Work 연동 제안

v0.1 · 2026-10-17 · 작성: JetsonController 세션(Claude) · 검토 요청 대상: GEON Work 세션

현장 장치(Jetson, MMS 호기)의 상태, 수집 실행, 서버 전송 결과를 GEON Work 대화와 업무로 보내, 현장운영 채널에서 사람이 판단하고 지시할 수 있게 한다. 1단계는 GEON Work 코드를 바꾸지 않고 이미 있는 Incoming Webhook과 `JETSON_DEVICE`·`ROADINVENTORY_MMS` 템플릿을 쓴다. 장치 원격 제어와 원본 데이터 전송은 범위 밖이다.

## 1. 양쪽에 이미 있는 것

| 쪽 | 기능 | 근거 |
|---|---|---|
| GEON Work | Incoming Webhook `POST /v1/webhooks/incoming/:integrationId`. 헤더 `x-geon-timestamp`(±5분)·`x-geon-nonce`(8~120자, 재사용 불가)·`x-geon-signature`(`v1=` + HMAC-SHA256 hex). 서명 원문 `<timestamp>.<nonce>.<canonical-json>`(key 사전순, 공백 없음) | geon_slack `docs/INTEGRATIONS.md` |
| GEON Work | 구조화 템플릿 `JETSON_DEVICE`·`SERVER_DEPLOYMENT`·`ROADINVENTORY_MMS`. 필드 `channelId`·`source`·`severity`(INFO·WARNING·CRITICAL·RECOVERY)·`project`·`status`·`impact`·`lastHealthyAt`·`retryCount`·`details`(≤1500)·`actions`(≤10)·`incidentKey`. 같은 `incidentKey`와 `status`는 새 메시지를 만들지 않고, 상태 변경·RECOVERY는 새 메시지 | geon_slack `apps/api/src/integrations.ts` 47-55, 136-137 |
| GEON Work | BOT 계정, 해시 토큰, scope, 채널 allowlist, `POST /api/v1/messages`(`idempotencyKey`), durable outbox(15초~15분 backoff, 10회, DEAD) | `docs/INTEGRATIONS.md` |
| GEON Work | 리빌딩 계획에서 Bot·Webhook(F20)은 UI 숨김·개발 동결이지만 API는 유지되며 "장비 연동·외부 알림 hook(F39)의 기반". 보드 템플릿 `FIELD_REPORT`(현장 보고)는 후속. 예시 채널 `#mms-현장운영` | `docs/REBUILD_PLAN.md` §3.1, `docs/agents/DESIGN_AGENT_BRIEF.md` §1-5·4-5 |
| JetsonController | Jetson 백엔드: 장치 상태(`backend/jetson_control/status.py`), 센서 감시(`sensor_monitor.py`), 수집 실행(`pipelines.py`·`run_context.py`), 업로드 작업(`uploads.py`) | 이 저장소 |
| JetsonController | 업로드 수신 서버(항상 켜진 HTTPS 서버, SQLite·파일 저장, 수신 검증): `upload_receiver/upload_receiver/service.py` | 이 저장소 |
| JetsonController | Android 앱: 온도·저장 공간 등 경보 기준과 알림 이력(`data/alerts/*`) | 이 저장소 |

## 2. 1단계 — 알림 브리지 (GEON Work 변경 없음)

### 2.1 발신 위치

모든 GEON Work 전송은 **업로드 수신 서버(upload_receiver)** 한 곳에서 한다.

- Jetson 백엔드는 기존 인증 채널로 수신 서버에 장치 이벤트를 보낸다(새 엔드포인트 `POST /v1/device-events`). 장치가 오프라인이면 장치 쪽 큐에 쌓았다가 연결될 때 보낸다.
- 수신 서버는 자체 SQLite outbox에 이벤트를 기록하고, worker가 GEON Work Incoming Webhook으로 서명해 보낸다. 실패해도 업로드·수집 저장을 막지 않는다(백오프·최대 시도·DEAD 표시).
- Android 앱은 GEON Work 비밀값을 갖지 않는다. 앱은 같은 이벤트를 수신 서버나 Jetson API에서 읽어 '전송' 화면과 알림에 보여 줄 뿐이다.
- 이유: 비밀값 한 곳, 나가는 주소 한 곳(방화벽 allowlist), 장치가 꺼져 있어도 "응답 없음"을 판정할 수 있는 곳이 수신 서버뿐이다.

### 2.2 이벤트 → 템플릿 매핑

| 이벤트 | template | severity | status | incidentKey | 그 밖의 필드 |
|---|---|---|---|---|---|
| 장치 온도 높음 / 정상 복귀 | `JETSON_DEVICE` | ≥80°C WARNING, ≥90°C CRITICAL, 복귀 RECOVERY | 온도 높음 / 정상 | `jetson:{deviceId}:temperature` | `source`=장치 이름, `details`=현재·최고 온도, `actions`=["냉각 상태 확인"] |
| 장치 응답 없음 / 복귀 | `JETSON_DEVICE` | WARNING(30분↑), 복귀 RECOVERY | 응답 없음 / 정상 | `jetson:{deviceId}:heartbeat` | `lastHealthyAt`=마지막 응답 |
| 저장 공간 부족 | `JETSON_DEVICE` | 여유 <15% WARNING, <5% CRITICAL | 저장 공간 부족 / 정상 | `jetson:{deviceId}:storage` | `details`=여유 용량 |
| 수집 시작 / 종료 | `ROADINVENTORY_MMS` | INFO | 수집 중 / 수집 종료 | `run:{runId}` | `project`=프로젝트·구간, `details`=경과 시간·용량·정밀 위치 비율 |
| 전송 실패 | `ROADINVENTORY_MMS` | 재시도 <3 WARNING, ≥3 CRITICAL | 전송 실패 | `upload:{runId}` | `retryCount`, `actions`=["앱에서 다시 시도", "수신 서버 확인"] |
| 서버 수신 확인 | `ROADINVENTORY_MMS` | RECOVERY | 서버 수신 확인 | `upload:{runId}` | `details`=파일 수·용량·검증 결과 |

- 같은 장애가 반복되면 GEON Work가 `incidentKey`+`status`로 거른다. 온도는 ±3°C 히스테리시스와 10분 최소 간격을 수신 서버에서 먼저 적용한다.
- 모든 요청에 새 nonce를 쓰고, 수신 서버 outbox 행 ID를 nonce 접두어로 써서 재시도 추적을 쉽게 한다.
- 메시지 본문에 원본 파일, 이미지, 개인 정보는 넣지 않는다. 식별자·건수·상태만 보낸다.

### 2.3 설정

수신 서버 설정(루트 전용 파일)에 다음을 둔다: GEON Work 기본 URL, `integrationId`, signing secret, 기본 채널 ID, 프로젝트별 채널 매핑(선택). secret 회전은 GEON Work 관리자가 하고, 수신 서버는 재시작 없이 다시 읽는다.

## 3. 2단계 — 업무 연결 (GEON Work 보드·F39 필요)

- **현장 보고 보드**(`FIELD_REPORT`, 호기 운행 보고): 수집 실행 1건 = 행 1개. 제안 속성: 장치(호기) · 날짜 · 프로젝트 · 구역/구간 · 수집 시간 · 용량 · 정밀 위치 비율 · 전송 상태(대기 / 전송 중 / 서버 수신 확인 / 실패) · 담당.
- 행 생성·갱신은 외부 키(`externalKey = run:{runId}`)로 멱등 upsert. 1단계 메시지는 그 행의 원본 메시지 스레드로 묶는다.
- GEON Work에 필요한 것: BOT용 `boards:write` scope, 외부 키 upsert API, 상태 옵션 범주(할 일·진행 중·완료) 매핑.
- 이후: 수집 결과를 납품 체크리스트 구역 검수 항목의 근거로 연결.

## 4. 3단계 — 양방향 (선택)

- **GEON Work → 장치**: 업무 "수집 요청"(장치·구역·기한)을 outgoing hook으로 수신 서버에 보내고, 앱 '작업' 화면의 '요청' 목록에 표시한다. 시작은 앱에서 사람이 확인해야만 한다(자동 실행 없음).
- **딥링크**: GEON Work 메시지의 "앱에서 열기" → `geoand://device/{deviceId}`, `geoand://run/{runId}`. 앱은 등록된 장치일 때만 연다. 앱 → GEON Work: 수집 결과에서 "GEON Work로 공유"(Android 공유).

## 5. 보안·운영 원칙

- 비밀값은 서버에만 둔다. APK와 앱 저장소에는 GEON Work 토큰이 없다.
- HTTPS만, HMAC 서명, timestamp ±5분, nonce 재사용 금지. 수신 서버의 나가는 주소는 GEON Work 하나로 제한한다.
- 외부 전송 실패가 업로드·수집·장치 제어를 막지 않는다. outbox 상태(대기·실패·DEAD)는 수신 서버 관리 화면과 앱 '전송' 화면에서 볼 수 있다.
- 이벤트 스키마 이름 `jetson.event.v1`, 필드 추가는 하위 호환으로만.

## 6. 디자인 정렬

JetsonController는 이번 개편에서 GEON Work 디자인 규칙을 따른다: Pretendard Variable, 색 토큰(bg #F3F5F8 · text #171B26 · border #E2E6EC · input #838A99 · primary #2550A3 · navy #1A2147), 라운드 4/8/12, 존댓말·명사형 버튼·오류는 원인+행동, 로딩 화면의 공식 GEO& 심볼. 두 앱이 계속 맞도록 GEON Work 토큰 원본(`tokens.css` 등)을 JSON으로 내보내 공유하는 방식을 제안한다. 용어도 맞춘다: 장치 · 작업 · 전송 · 프로젝트 · 구역.

## 7. GEON Work 쪽에 요청하는 결정

1. F20 Incoming Webhook을 리빌딩 기간에도 운영 API로 계속 열어 둘지, 새 웹에서 연동 설치와 secret 회전을 어디서 할지(관리자 '고급' 화면 또는 CLI).
2. `JETSON_DEVICE`·`ROADINVENTORY_MMS`에 `deviceId`·`runId`·`link` 필드를 추가할 의향. 없으면 `details`에 넣는다.
3. 채널 규칙: `#mms-현장운영` 하나 또는 프로젝트별.
4. `FIELD_REPORT` 보드 템플릿과 외부 키 upsert API의 일정(PR 카드 번호).
5. 3단계(양방향)가 필요한지와 시기.
6. 디자인 토큰 공유 방식과 원본 파일 위치.

## 8. JetsonController 쪽 작업 목록 (결정 뒤 착수)

| ID | 작업 | 검증 |
|---|---|---|
| J1 | 수신 서버: `POST /v1/device-events`, outbox 테이블, GEON Work 서명 전송 worker(백오프·DEAD), 설정 다시 읽기 | upload_receiver 단위 테스트(서명 원문·nonce·재시도·DEAD) |
| J2 | Jetson 백엔드: 온도·응답·저장 공간·수집·전송 이벤트 발생과 장치 쪽 큐, 히스테리시스 | backend 단위 테스트 |
| J3 | 앱: '전송' 화면에 GEON Work 전달 상태(읽기 전용), 설정에 연동 상태 | JVM 테스트 |
| J4 | 앱: `geoand://` 딥링크 intent filter와 등록 장치 확인 | 계측 테스트 |

## 9. 회신 방법

GEON Work 세션은 §7의 결정과 계약 변경, 그리고 J1~J4를 조정한 작업 지시를 이 세션("Codex OCX 호환성 확인")에 cross-session 메시지로 보내거나, 이 문서를 고치는 PR로 회신한다. JetsonController 세션은 그 지시를 받아 구현한다.
