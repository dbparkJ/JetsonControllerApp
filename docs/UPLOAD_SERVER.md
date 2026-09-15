# 업로드 서버 설치·API

외부 HTTPS 수신기의 업로드·재개·완료 데이터 탐색 계약과 배포 절차입니다.

## 확정 아키텍처

```text
Android app
  |  BLE / pinned HTTPS over LAN or Wi-Fi Direct (제어와 진행률만)
  v
Jetson control API
  |  outbound HTTPS :443 (실제 파일 데이터)
  v
Public upload receiver
  |-- SQLite (WAL, synchronous=FULL)
  `-- HDD 파일 저장소 (staging / objects)
```

- 앱과 수신 서버가 같은 LAN에 있을 필요가 없다.
- 파일 바이트는 Android 앱을 경유하지 않는다.
- Android 앱은 Jetson의 로컬 API에서 업로드 시작, 조회, 취소만 수행한다.
- 수신 서버는 공인 DNS와 신뢰 가능한 TLS 인증서를 사용한다.
- Jetson에서 외부로 나가는 TCP 443만 허용해도 동작해야 한다. 수신 서버가 Jetson으로 역접속하면 안 된다.

## Jetson 송신 구현

송신 코드는 `backend/jetson_control/uploads.py`에 있다.

- 청크 크기: 최대 4 MiB
- 작은 파일이 많은 디렉터리는 수신기가 `fileBatch` capability를 광고할 때 최대 32 MiB, 256개 파일 단위로 묶어 전송
- 배치 전송 전 여러 파일의 오프셋을 한 요청으로 조회하고, 일부만 올라간 파일은 기존 4 MiB 재개 청크로 자동 전환
- 수신기가 `deferredFileHashes`를 광고하면 세션에는 경로와 크기만 먼저 보내고, 각 batch·파일이 도착할 때 전체 SHA-256을 확정
- 구형 수신기에서는 파일 전체 SHA-256을 세션 생성 전에 계산하되 Android에 `SCANNING` byte·파일 진행률을 표시
- 청크별 `X-Chunk-SHA256` 전송
- 실패 시 0, 1, 3, 7초 간격으로 재시도
- 실패한 PUT 뒤 서버 오프셋을 다시 조회하여 이어서 전송
- 작업 상태는 `/var/lib/jetson-control/upload-jobs`에 원자적으로 저장
- Jetson/API 재시작 뒤 진행 중 작업은 같은 `clientJobId`로 자동 재개
- 완료 요청은 수 TiB 파일의 전체 해시 검증을 기다릴 수 있도록 응답 read timeout을 24시간으로 확장
- 운영 모드에서는 `http://`와 로컬 복사 대상을 사용하지 않는다.

`JETSONBATCH1`은 길이 기반 바이너리 배치 형식입니다. 부분 전송 파일은 기존 청크 API로 재개하며, 전송 전에 모든 파일의 해시를 계산할지는 수신 서버 capability로 결정합니다.

## 인증과 TLS

모든 `/v1/*` 요청은 다음 헤더가 필요하다.

```http
Authorization: Bearer <device-token>
```

수신 서버 요구사항:

1. TLS 1.2 이상과 유효한 공인 인증서를 사용한다.
2. 토큰을 로그, 오류 응답, 추적 데이터에 기록하지 않는다.
3. 토큰 원문을 DB에 저장하지 않는다. 서버 pepper를 사용하는 HMAC-SHA256으로 검증한다.
4. 토큰은 정확히 하나의 `deviceId`에 연결한다.
5. 비활성화, 교체, 만료를 지원한다.
6. 인증 실패는 정보 차이를 드러내지 않고 `401`을 반환한다.
7. Jetson의 토큰 파일은 root 전용 `0600` 권한으로 저장한다.

CORS는 필요하지 않다. 호출자는 브라우저가 아닌 Jetson 데몬이다.

## 식별자와 경로 규칙

- `sessionId`: 정규식 `^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$`를 만족해야 한다. UUID 문자열이 권장된다.
- `clientJobId`: Jetson이 생성한 32자리 lowercase hex 문자열이다.
- `deviceId`: canonical lowercase UUID 문자열이다.
- 파일 경로: UTF-8 POSIX 상대 경로다.
- 절대 경로, 빈 경로, `.`/`..` 세그먼트, NUL, 역슬래시, 중복 경로를 거부한다.
- 정규화 전후 경로가 달라지는 입력을 거부한다.
- 하나의 세션에서 파일 수, 전체 바이트, 개별 파일 크기의 상한을 설정한다.
- 장비별 열린 세션 수, 누적 세션/파일 metadata와 세션 생성 속도의 상한도 설정한다. 전체 바이트가 0인 manifest도 무제한 허용하면 안 된다.

권장 초기 제한:

| 항목 | 권장값 |
|---|---:|
| 세션당 파일 | 100,000 |
| 세션 전체 크기 | 장비 정책에 따라 1~5 TiB |
| 개별 청크 | 4 MiB |
| 파일 배치 | 32 MiB, 최대 256개 |
| manifest JSON | 32 MiB |
| 동시 파일 PUT | 세션당 1개, 장비당 2개 이하 |

## 데이터 모델

최소한 다음 상태를 영속화한다.

### devices

| 필드 | 설명 |
|---|---|
| `device_id` | UUID, PK |
| `token_hash` | 토큰 검증값 |
| `enabled` | 차단 여부 |
| `quota_bytes` | 장비별 허용량 |
| `created_at`, `updated_at` | 감사 시각 |

### upload_sessions

| 필드 | 설명 |
|---|---|
| `session_id` | 64자 이하 식별자, PK |
| `device_id` | 인증 장비 FK |
| `client_job_id` | Jetson 작업 ID |
| `source_name` | 표시용 원본 이름 |
| `state` | `OPEN`, `FINALIZING`, `COMPLETED`, `CANCELLED`, `FAILED` |
| `total_bytes`, `file_count` | manifest 집계값 |
| `created_at`, `updated_at`, `completed_at` | 시각 |

`(device_id, client_job_id)`에 UNIQUE 제약을 둔다. 세션 생성 재시도는 기존 세션을 반환해야 한다.

### upload_files

| 필드 | 설명 |
|---|---|
| `session_id`, `relative_path` | 복합 PK |
| `size_bytes` | 선언된 전체 크기 |
| `sha256` | 사전 선언된 전체 해시 또는 deferred upload에서 수신 후 확정한 전체 해시 |
| `next_offset` | 서버가 영속화한 다음 바이트 위치 |
| `state` | `PENDING`, `UPLOADING`, `RECEIVED`, `VERIFIED`, `FAILED` |
| `staging_key`, `final_key` | 저장소 객체 키 |

오프셋 변경은 해당 파일 행 잠금 또는 비교 후 갱신으로 직렬화한다. 메모리 변수만으로 진행률을 관리하면 안 된다.

## API 계약

Base URL 예: `https://uploads.example.com`. 업로드 제어 응답은 JSON이며, 라이브러리 파일 미리보기는 원본 바이트를 반환한다. 성공 응답의 필드명과 대소문자를 그대로 지킨다.

### Capability 조회

```http
GET /v1/capabilities
Authorization: Bearer <token>
```

```json
{
  "deferredFileHashes": {
    "version": 1,
    "manifestHashMode": "deferred-v1"
  },
  "fileBatch": {
    "version": 1,
    "maxBytes": 33554432,
    "maxFiles": 256
  }
}
```

Jetson은 작업 시작 때 한 번 조회한다. endpoint가 없거나 `deferredFileHashes` 값이 정확히 일치하지 않으면 전체 파일 SHA-256을 먼저 계산하는 기존 계약으로 자동 전환한다. 인증 실패나 receiver 장애를 capability 부재로 오인해 보안을 낮추지 않으며, 실제 session·upload 요청은 기존 오류 처리에 따라 실패한다.

### 세션 생성

```http
POST /v1/upload-sessions
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "deviceId": "00000000-0000-0000-0000-000000000001",
  "clientJobId": "6fd7a68a0a734c01a83bb6445e5f6c58",
  "sourceName": "capture-20260812",
  "context": {
    "schemaVersion": 1,
    "surveyProjectId": "survey-alpha",
    "surveySectionId": "section-01",
    "runId": "capture/run-20260914T010203.000004Z-123.log",
    "deviceId": "00000000-0000-0000-0000-000000000001",
    "pipelineId": "capture",
    "sourceRevision": "git-revision",
    "configSha256": "64-lowercase-hex",
    "outputId": "stable-output-id",
    "createdAt": "2026-09-14T01:02:03Z"
  },
  "files": [
    {
      "path": "camera/front/000001.jpg",
      "sizeBytes": 1843200,
      "sha256": "64-lowercase-hex"
    }
  ]
}
```

`context`는 legacy upload에서만 생략할 수 있다. 새 실행 결과에서는 Jetson의 root 소유 runtime record, 결과 폴더의 `.jetson-output-context.json`, Android가 echo한 `PipelineRun.uploadContext`가 정확히 같아야 한다. receiver는 context를 canonical manifest hash에 포함하므로 같은 `(deviceId, clientJobId)`를 다른 survey section, run, source revision 또는 config로 재사용하면 `409`다. 응답의 `surveyContext`는 조사 문맥이고 `accessProjectId`/호환 `projectId`는 서버 접근 권한 경계이므로 서로 대체하지 않는다.

`deferredFileHashes`를 지원하는 receiver에는 다음처럼 전체 파일 SHA-256을 생략한다.

```json
{
  "deviceId": "00000000-0000-0000-0000-000000000001",
  "clientJobId": "6fd7a68a0a734c01a83bb6445e5f6c58",
  "sourceName": "capture-20260812",
  "hashMode": "deferred-v1",
  "files": [
    {
      "path": "camera/front/000001.jpg",
      "sizeBytes": 1843200
    }
  ]
}
```

deferred manifest도 전체 경로·크기와 집계 quota를 세션 생성 시 검증하고 예약한다. 0-byte 파일은 즉시 빈 SHA-256으로 확정하며, 나머지 파일의 hash DB 값은 batch 또는 완성된 chunk 파일을 검증할 때 원자적으로 채운다.

처리 순서:

1. Bearer 토큰으로 장비를 식별한다.
2. body의 `deviceId`가 토큰 장비와 같은지 확인한다.
3. manifest 전체를 검증하고 quota를 예약한다.
4. 세션과 파일 행을 하나의 트랜잭션으로 만든다.
5. 동일 `(deviceId, clientJobId)`가 있으면 manifest가 완전히 같은 경우 기존 세션을 반환한다. 다르면 `409`를 반환한다.

신규 응답은 `201`, 멱등 재호출은 `200`을 권장한다.

```json
{
  "sessionId": "dfe4038e-314c-45e0-b0d5-e8bca82b163c",
  "fileBatch": {
    "version": 1,
    "maxBytes": 33554432,
    "maxFiles": 256
  }
}
```

`fileBatch`는 선택 capability다. 새 Jetson backend는 이 값이 있으면 다수 파일 배치 전송을 사용하고, 필드가 없는 기존 수신기에는 파일별 offset/청크 계약을 그대로 사용한다.

### 파일 오프셋 조회

```http
GET /v1/upload-sessions/{sessionId}/files/offset?path=camera%2Ffront%2F000001.jpg
Authorization: Bearer <token>
```

```json
{
  "nextOffset": 4194304
}
```

- 인증 장비가 소유한 세션만 조회할 수 있다.
- `0 <= nextOffset <= sizeBytes`를 항상 보장한다.
- 취소되거나 실패한 세션은 `409`, 없는 경로는 `404`를 반환한다.

### 배치 파일 오프셋 조회

```http
POST /v1/upload-sessions/{sessionId}/files/offsets
Authorization: Bearer <token>
Content-Type: application/json

{"paths":["camera/front/000001.jpg","camera/front/000002.jpg"]}
```

```json
{
  "files": [
    {"path": "camera/front/000001.jpg", "nextOffset": 0},
    {"path": "camera/front/000002.jpg", "nextOffset": 1843200}
  ]
}
```

한 요청의 경로 수는 세션 생성 응답의 `fileBatch.maxFiles` 이하로 제한한다. 요청 순서대로 모든 경로와 오프셋을 반환하며, 중복·누락·타 장비 세션은 거부한다.

### 다수 파일 배치 업로드

```http
PUT /v1/upload-sessions/{sessionId}/files/batch
Authorization: Bearer <token>
Content-Type: application/vnd.jetson.upload-batch-v1
Content-Length: <bytes>
X-Batch-SHA256: <64-lowercase-hex>

<JETSONBATCH1 binary body>
```

`JETSONBATCH1` body는 네트워크 byte order로 다음 필드를 연속 배치한다.

1. ASCII magic `JETSONBATCH1\n`
2. unsigned 32-bit 파일 수
3. 파일마다 unsigned 32-bit UTF-8 경로 길이, unsigned 64-bit 내용 길이, 경로 bytes, 파일 bytes

수신기는 전체 body hash와 각 파일의 manifest 크기를 확인한 뒤 파일 SHA-256과 staging 객체를 함께 확정한다. 기존 manifest의 SHA-256이 있으면 대조하고, deferred manifest이면 계산값을 DB에 기록한다. 오프셋이 `0`인 완전한 파일만 배치에 넣으며 부분 전송 파일은 [청크 API](#청크-업로드)로 재개한다. 동일 배치의 응답이 유실되어 다시 도착하면 저장된 hash와 body를 대조하고 이미 완료된 파일을 덧붙이지 않은 채 같은 오프셋을 반환한다.

```json
{
  "files": [
    {"path": "camera/front/000001.jpg", "nextOffset": 1843200},
    {"path": "camera/front/000002.jpg", "nextOffset": 1843200}
  ]
}
```

### 청크 업로드

```http
PUT /v1/upload-sessions/{sessionId}/files?path=...&offset=4194304
Authorization: Bearer <token>
Content-Type: application/octet-stream
Content-Length: 4194304
Content-Range: bytes 4194304-8388607/12582912
X-Chunk-SHA256: <64-lowercase-hex>

<raw bytes>
```

서버는 다음을 원자적으로 처리한다.

1. query `offset`, `Content-Range` 시작값, DB `next_offset`가 모두 같은지 확인한다.
2. `Content-Length`, range 길이, 실제 수신 길이가 같은지 확인한다.
3. 청크 SHA-256을 계산해 헤더와 비교한다.
4. staging storage에 해당 오프셋으로 내구성 있게 기록한다.
5. 기록 성공 뒤에만 DB `next_offset`을 증가시킨다.
6. 새 오프셋을 반환한다.

deferred manifest의 파일은 첫 offset부터 끊김 없이 완료되면 수신 중 누적한 전체 SHA-256을 즉시 기록한다. 프로세스 재시작 뒤 이어받아 누적 hasher가 없으면 파일 상태를 `RECEIVED`로 두고 세션 완료 검증에서 staging 객체를 한 번 읽어 hash를 확정한다.

```json
{
  "nextOffset": 8388608
}
```

정상 응답은 `200`이다. 이전 오프셋의 청크가 중복 도착하면 데이터를 다시 붙이지 말고 `409`를 반환한다. Jetson은 오프셋 조회 후 계속한다. 응답이 유실되더라도 저장된 오프셋이 유지되어야 한다.

### 세션 완료

```http
POST /v1/upload-sessions/{sessionId}/complete
Authorization: Bearer <token>
Content-Type: application/json

{}
```

완료 처리:

1. 모든 파일의 `next_offset == size_bytes`를 확인한다.
2. 각 staging 파일의 전체 SHA-256을 검증한다. 기존 manifest 값과 대조하거나 deferred 파일의 최종 값을 확정한다.
3. 최종 object key로 원자적 promote 또는 multipart complete를 수행한다.
4. 모든 파일이 성공한 뒤 세션을 `COMPLETED`로 바꾼다.
5. 같은 요청의 재호출은 성공 응답을 반환한다.

```json
{
  "state": "COMPLETED"
}
```

파일이 덜 전송됐으면 `409`, 해시가 다르면 `422`를 반환하고 세션을 `FAILED`로 표시한다.

### 세션 취소

```http
DELETE /v1/upload-sessions/{sessionId}
Authorization: Bearer <token>
```

- 열린 세션을 `CANCELLED`로 변경하고 quota 예약을 해제한다.
- staging 데이터는 즉시 또는 비동기 정리한다.
- 반복 호출은 성공해야 한다.
- 이미 완료된 세션은 삭제하지 말고 `409`를 반환한다.

성공 응답:

```json
{
  "state": "CANCELLED"
}
```

## 오류 응답

```json
{
  "detail": "machine-readable-or-safe-message"
}
```

| 상태 | 용도 |
|---|---|
| `400` | JSON, path, range 형식 오류 |
| `401` | 토큰 누락 또는 실패 |
| `403` | 다른 장비의 세션 접근 |
| `404` | 세션 또는 파일 없음 |
| `409` | 오프셋 불일치, 상태 충돌, idempotency 충돌 |
| `413` | manifest, 청크, quota 초과 |
| `422` | 청크 또는 파일 해시 불일치 |
| `429` | 장비별 동시성 제한 |
| `500` | 내부 오류 |
| `503` | 저장소 일시 장애, `Retry-After` 권장 |

오류에 토큰, 내부 경로, SQL, object storage credential을 넣지 않는다.

## 저장소 구현

구현은 [upload_receiver/](../upload_receiver/)에 있습니다. 앱의 서버 데이터 탐색 요청도 Jetson API가 프록시합니다.

| 경로 | 역할 |
|---|---|
| `upload_receiver/upload_receiver/app.py` | FastAPI route, body 제한, JSON 오류 응답 |
| `upload_receiver/upload_receiver/service.py` | 인증, 세션, quota, 배치·청크 내구성 기록, 전체 해시, 재시작 복구 |
| `upload_receiver/upload_receiver/database.py` | SQLite schema, WAL, `synchronous=FULL`, 트랜잭션 |
| `upload_receiver/upload_receiver/admin.py` | 초기화, token 발급/교체, 장비 차단, staging 정리 |
| `upload_receiver/Caddyfile` | 공인 HTTPS reverse proxy와 endpoint별 body 제한 |
| `upload_receiver/systemd/` | HDD mount guard, receiver, HTTPS, cleanup, port-forward unit |
| `upload_receiver/tests/test_receiver.py` | API·복구·경합·경로·hash·quota·token 자동화 시험 |

구현의 저장 규칙은 다음과 같다.

- FastAPI는 `127.0.0.1:8877`, 단일 worker로 실행하며 Caddy의 TCP 443만 공개한다.
- token 원문 대신 서버 pepper를 이용한 HMAC-SHA256을 DB에 저장한다. token 파일은 원자적으로 쓰고 `0600`으로 제한한다.
- 사용자 상대 경로는 파일시스템 경로로 사용하지 않는다. 각 파일은 불투명한 `file_id.blob` 이름으로 저장하고 최종 `manifest.json`에서 원래 경로와 대응시킨다.
- 청크 파일 `fsync`가 성공한 뒤에만 SQLite offset을 올린다. 재시작 시 DB offset보다 긴 미승인 tail은 잘라낸다.
- 완료 시 모든 staging 객체의 크기와 SHA-256을 다시 확인하고 같은 ext4 파일시스템 안에서 디렉터리를 원자적으로 이동한다. 이동 직후 재시작한 경우에도 final 객체와 manifest 전체를 검증한 뒤 `COMPLETED`로 복구한다.
- 동일 `clientJobId`의 `FAILED` 세션은 같은 manifest일 때 안전하게 초기화하여 Jetson retry를 허용한다. `CANCELLED`는 명시적인 새 Jetson 작업 ID가 필요하다.
- 기본 제한은 32 MiB/256개 파일 배치, 장비당 동시 PUT 2개, 열린 세션 8개, 분당 manifest 30개, 누적 세션 10,000개, 누적 파일 metadata 1,000,000개다.
- `OPEN`/`FAILED`/`CANCELLED` staging 세션은 72시간 뒤 매일 정리한다. `COMPLETED` 객체는 자동 삭제하지 않는다.

## 완료 데이터 라이브러리

- 데이터 탐색 응답은 public receiver bearer token을 Android에 노출하지 않는다. 앱에서 token을 입력해 target을 등록하는 흐름과 구분한다.
- Android는 기존 `JETSONHTTP2` HMAC/TLS 연결로 Jetson local API만 호출한다.
- Jetson backend가 root 전용 token file을 읽어 receiver의 `/v1/library/*`를 호출한다.
- receiver는 token으로 식별한 `device_id`의 `COMPLETED` session만 반환한다. 다른 장비 session ID를 알아도 `403`이다.
- 원본 저장 경로와 `storedObject` 이름은 API 응답에 노출하지 않는다.
- 파일 미리보기는 기본 12 MiB로 제한한다. 전체 대용량 파일 다운로드 API로 사용하지 않는다.
- symlink를 따라가지 않으며 DB의 final object와 크기가 일치하는 regular file만 읽는다.

요청 흐름:

```text
Android app
  -> Jetson HTTPS /v1/upload/library/* (JETSONHTTP2)
  -> public receiver HTTPS /v1/library/* (Bearer token)
  -> device-owned COMPLETED objects only
```

### 라이브러리 API

모든 `/v1/library/*` 요청은 `Authorization: Bearer <device-token>`이 필요하다.

#### Capability

`GET /v1/capabilities`

```json
{
  "library": {
    "version": 1,
    "maxPreviewBytes": 12582912
  }
}
```

#### 완료 세션 목록

`GET /v1/library/sessions?limit=100&offset=0`

- `limit`: `1..200`
- `offset`: `0..10000`
- 최신 완료 순으로 반환한다.
- `nextOffset`이 `null`이면 마지막 페이지다.

```json
{
  "sessions": [
    {
      "sessionId": "2fbf3cf1-8d5c-4b95-9222-5d657d8f8fde",
      "sourceName": "capture-20260813",
      "totalBytes": 188900000000,
      "fileCount": 69373,
      "createdAt": "2026-08-13T06:00:00Z",
      "completedAt": "2026-08-13T08:03:12Z"
    }
  ],
  "nextOffset": null
}
```

#### 가상 폴더 목록

`GET /v1/library/sessions/{sessionId}/files?path=camera/front`

- `path`는 빈 문자열 또는 정규화된 relative path다.
- DB의 `relative_path`를 기준으로 즉석에서 폴더 계층을 만든다.
- 한 폴더에서 최대 500개 항목을 반환한다. 초과 시 `truncated: true`다.
- 파일 크기와 완료 시각만 노출한다.

```json
{
  "sessionId": "2fbf3cf1-8d5c-4b95-9222-5d657d8f8fde",
  "path": "camera/front",
  "entries": [
    {
      "name": "frame-0001.jpg",
      "relativePath": "camera/front/frame-0001.jpg",
      "type": "FILE",
      "sizeBytes": 284112,
      "modifiedAt": "2026-08-13T08:03:12Z"
    }
  ],
  "truncated": false
}
```

#### 파일 미리보기

`GET /v1/library/sessions/{sessionId}/file?path=camera/front/frame-0001.jpg`

- MIME type은 확장자로 결정한다.
- `UPLOAD_RECEIVER_MAX_PREVIEW_BYTES` 기본값은 `12582912`이다.
- 제한 초과는 `413`, 다른 장비 session은 `403`, 없는 파일은 `404`다.
- `Range`와 무제한 다운로드는 version 1 범위에 포함하지 않는다.

### 저장 결과 검증과 삭제

| Method | Path | 역할 |
|---|---|---|
| `GET` | `/v1/library/sessions/{sessionId}/verification` | 완료 객체의 크기·해시를 검증하고 `contentSha256` 영수증 반환 |
| `DELETE` | `/v1/library/sessions/{sessionId}` | legacy 영구 삭제를 `409`로 거부하고 직원 범위 휴지통 사용 안내 |

두 API도 장비 token이 필요합니다. 검증 시 저장소 불일치는 `503`입니다. 장비 token은 역할·access project 감사 주체가 아니므로 완료 객체의 영구 삭제 권한을 갖지 않는다. 완료 데이터 lifecycle mutation은 직원 token의 `/v1/server/*` 휴지통 API만 사용한다.

## 휴대전화의 서버 직접 조회

`/v1/library/*`는 Jetson 장비 token용 호환 API입니다. Jetson이 꺼져 있어도 휴대전화가 인터넷을 통해 조회하는 화면은 별도 `/v1/server/*` API와 직원 token을 사용합니다. 장비 token은 직원 권한으로 취급하지 않으며 Android에 전달하지 않습니다.

직원 token은 `emp_` 접두어를 사용하고 DB에는 pepper HMAC만 저장합니다. 역할은 `VIEWER`, `OPERATOR`, `ADMIN`이며 모든 요청은 역할과 별개로 명시적인 access project grant가 있어야 합니다. 이 access project는 서버 접근 경계를 위한 것이고 현장 조사·파이프라인 project metadata를 대신하지 않습니다. 장비는 하나의 access project에만 배정되며 과거 session 노출이 바뀌지 않도록 다른 project로의 재배정은 거부합니다.

각 요청은 선택한 프로필의 환경을 다음 헤더에 보냅니다.

```http
Authorization: Bearer <employee-token>
X-Expected-Server-Environment: production
```

`UPLOAD_RECEIVER_ENVIRONMENT`은 `development`, `test`, `production` 중 하나입니다. 서버와 헤더가 다르면 `409`이며 모든 JSON 응답과 preview 응답 헤더에도 실제 환경이 포함됩니다. Android 프로필은 HTTPS root URL만 허용하고 redirect를 따르지 않습니다.

| Method | Path | 최소 역할 | 설명 |
|---|---|---|---|
| `GET` | `/v1/server/capabilities` | `VIEWER` | 직원 identity, 역할, 허용 project, 환경 확인 |
| `GET` | `/v1/server/jobs?projectId=...&limit=...&offset=...` | `VIEWER` | project 장비의 upload job 상태와 경로 요약 |
| `GET` | `/v1/server/jobs/{sessionId}/files?projectId=...&path=...` | `VIEWER` | 가상 폴더 조회 |
| `GET` | `/v1/server/jobs/{sessionId}/preview?projectId=...&path=...` | `VIEWER` | 크기 제한 이미지·영상 preview |
| `GET` | `/v1/server/jobs/{sessionId}/receipt?projectId=...` | `VIEWER` | 완료 객체를 다시 검증한 receipt |
| `GET` | `/v1/server/trash?projectId=...&limit=...&offset=...` | `VIEWER` | 휴지통과 purge 진행 상태의 페이지 조회 |
| `DELETE` | `/v1/server/jobs/{sessionId}?projectId=...` | `OPERATOR` | 완료 session을 휴지통으로 이동 |
| `POST` | `/v1/server/trash/{sessionId}/restore?projectId=...` | `OPERATOR` | session 복원 |
| `POST` | `/v1/server/trash/empty?projectId=...` | `OPERATOR` | 화면에서 확인한 휴지통 snapshot을 영구 삭제 |
| `GET` | `/v1/server/audit?projectId=...&limit=...&offset=...` | `ADMIN` | project 범위 mutation·권한 lifecycle 감사 조회 |

job 응답은 `OPEN`, `FINALIZING`, `COMPLETED`, `CANCELLED`, `FAILED` 상태와 `receivedBytes`, `updatedAt`을 포함합니다. `pathSummary`에는 최대 5개의 `rootEntries`, 생략 여부, image/video 개수가 포함됩니다. 목록 응답의 `refreshedAt`은 서버가 실제 조회한 시각입니다. Android의 최근 목록 cache는 환경+base URL+직원 identity+project+credential revision으로 격리하며 cache 응답은 항상 stale로 표시하고 저장된 `refreshedAt`을 함께 보여야 합니다. `401`, `403`, 환경/identity/project 불일치에서는 cache를 폐기하고 표시하지 않습니다.

receipt가 성공을 증명하려면 `state=COMPLETED`, `matched=true`, 화면이 요청한 같은 `sessionId`여야 합니다. `matched`는 receiver가 최종 객체의 크기와 SHA-256을 독립적으로 다시 읽어 검증했다는 뜻입니다. 업로드 접수만 된 상태는 성공 receipt가 아닙니다.

휴지통 이동과 복원은 같은 filesystem 안의 directory rename과 DB transition record를 사용합니다. rename 뒤 `fsync`나 DB commit 결과가 불명확하면 active 목록에서 숨긴 transition을 남기고 시작 시 실제 두 directory 위치를 확인해 완료합니다. 요청과 완료는 actor, access project, session, outcome과 함께 `audit_events`에 남는다. 클라이언트는 network timeout이나 `5xx`를 확정 실패로 표시하거나 자동 재시도하지 않고 상태를 새로 조회해야 합니다.

휴지통 목록은 한 페이지에 최대 200개이며 `total`, `nextOffset`, `emptySupported=true`를 반환합니다. 각 항목은 `purgeSupported`, `restoreSupported`를 포함합니다. `PURGING`은 삭제가 끝나거나 재시작 복구가 완료될 때까지 목록에 남고 복원할 수 없습니다. 화면은 현재 페이지의 명시적인 session ID만 확인 대상으로 사용해야 하며 다음 페이지까지 자동으로 반복 삭제하면 안 됩니다.

영구 삭제 요청 body는 다른 필드 없이 다음 형식이어야 합니다. `sessionIds`는 중복 없는 1~200개 ID이며, 요청이 시작된 뒤 새로 휴지통에 들어온 항목은 삭제 대상이 아닙니다.

```json
{
  "confirmed": true,
  "sessionIds": ["confirmed-session-id"]
}
```

서버는 파일을 지우기 전에 모든 ID가 선택한 access project에 속하는지 검증합니다. 알 수 없거나 다른 project의 ID가 하나라도 있으면 파일을 하나도 지우지 않고 요청 전체를 거부합니다. 유효한 project 항목의 상태가 그 사이 바뀐 경우에는 해당 항목만 `FAILED`가 될 수 있으며 응답의 `results`는 각 ID에 `PURGED`, `PURGING`, `FAILED` 중 하나를 반환합니다. 이미 `PURGED`인 같은 snapshot 재요청은 멱등하게 `PURGED`입니다.

삭제 의도와 결과는 별도 `library_purges` tombstone과 감사 이벤트로 유지합니다. 파일 payload가 삭제된 뒤에도 upload receipt metadata와 `(deviceId, clientJobId)` 소유권을 보존하므로 같은 작업 ID가 새 완료 데이터로 나타나지 않습니다. 장비 token의 legacy `DELETE /v1/library/sessions/{sessionId}`는 계속 비활성화되어 있으며, 영구 삭제는 직원 범위 endpoint만 허용합니다. quota는 `PURGING` 동안 유지되고 payload 삭제와 `PURGED` 기록이 끝난 뒤 반환됩니다. 삭제는 휴지통 root에 고정한 directory descriptor 아래에서만 수행하며 symlink 대상은 따라가지 않습니다.

접근 project와 직원 token 구성 예시입니다. 저장소 루트에서 실행하되, 기본 data root를 사용하지 않도록 먼저 배포된 receiver의 기존 environment file을 불러옵니다. token 원문은 지정한 `0600` 파일에만 기록합니다.

```bash
receiver_environment_file="${XDG_CONFIG_HOME:-${HOME}/.config}/jetson-upload-receiver/environment"
set -a
. "${receiver_environment_file}"
set +a

PYTHONPATH=upload_receiver upload_receiver/.venv/bin/python -m upload_receiver.admin upsert-project \
  --project-id road-alpha --display-name "Road Alpha"
PYTHONPATH=upload_receiver upload_receiver/.venv/bin/python -m upload_receiver.admin assign-device-project \
  --device-id <canonical-device-uuid> --project-id road-alpha
PYTHONPATH=upload_receiver upload_receiver/.venv/bin/python -m upload_receiver.admin issue-employee-token \
  --employee-id employee.one --display-name "Employee One" --role OPERATOR \
  --project-id road-alpha --output /secure/path/employee.one.token
```

즉시 권한 변경은 `set-employee-role`, `revoke-employee-project`, `disable-employee`, `disable-project`를 사용한다. 각 변경은 다음 인증 요청부터 DB의 현재 enabled/expiry/role/grant를 다시 확인하며 audit event를 남긴다. 조직 IdP 자격 증명은 이 도구가 발급하지 않으며 조직이 확정한 provisioning 절차에서 employee token을 전달·회수해야 한다.

## HTTPS 프록시

- 공개 포트는 443만 연다.
- 요청 body 제한은 manifest 32 MiB, 파일 배치 32 MiB, 청크 5 MiB 이상으로 구분한다.
- PUT request buffering을 꺼서 디스크 이중 사용을 피한다.
- upstream read/write timeout은 90초 이상으로 둔다.
- access log에서 `Authorization`을 제외한다.
- rate limit key는 토큰 원문이 아니라 인증된 `deviceId`를 사용한다.
- `/health/live`와 `/health/ready`를 제공하되 인증 정보나 저장소 경로를 노출하지 않는다.

## 서버 설치·업데이트

제공된 설치기는 `/data/server_storage`가 실제 mount point인지 검사하고, 그 하위 디렉터리만 데이터 루트로 허용합니다. 운영 서버에서 HDD를 이 위치에 마운트한 뒤 실행합니다. `/data` 디렉터리가 존재하는 것만으로 저장 장치가 준비된 것은 아닙니다.

```text
/data/server_storage/jetson-upload-receiver/
|-- db/receiver.sqlite3
|-- secrets/token-pepper
|-- secrets/device-tokens/<deviceId>.token
|-- storage/staging/<deviceId>/<sessionId>/*.blob
|-- storage/objects/<deviceId>/<sessionId>/{manifest.json,*.blob}
|-- storage/trash/<deviceId>/<sessionId>/{manifest.json,*.blob}
|-- storage/locks/
|-- runtime/
`-- caddy/{data,config}/
```

`ConditionPathIsMountPoint=/data/server_storage`, `RequiresMountsFor=/data/server_storage`와 시작 전 `mountpoint` 검사를 함께 사용한다. HDD mount가 빠진 부팅에서 같은 경로명의 NVMe 디렉터리에 조용히 기록하는 fallback을 허용하지 않는다.

설치 또는 코드 업데이트:

```bash
# 저장소 루트에서 실행
./upload_receiver/scripts/install-user-service.sh \
  /data/server_storage/jetson-upload-receiver production
```

설치기는 `upload_receiver/.venv`에 requirements를 설치하고 user service와 cleanup timer를 갱신·재시작합니다. 기존 DB, 객체, pepper와 token은 유지합니다. Caddy 설정 변경은 아래 HTTPS 구성 명령도 다시 실행해야 반영됩니다.

공인 HTTPS를 처음 구성하거나 다시 적용:

```bash
./upload_receiver/scripts/configure-public-https.sh \
  uploads.example.com
```

위 도메인은 실제 서버를 가리키는 도메인으로 바꿉니다. 저장소의 Android 기본 운영 URL은 `https://125-142-22-24.sslip.io`입니다. 공인 IP 기반 hostname이므로 IP가 바뀌면 HTTPS 설정과 Jetson target URL도 갱신해야 합니다. 기존 장비의 실제 설정은 `/etc/jetson-control/upload_targets.json` 또는 앱 관리 target에서 확인합니다.

공유기의 UPnP TCP 443 mapping은 이 서버의 TCP 443으로 연결되고 15분마다 확인됩니다. Caddy 인증서·설정은 HDD의 `caddy/`에 보존합니다.

상태 확인:

```bash
findmnt /data/server_storage
systemctl --user --no-pager --full status \
  jetson-upload-receiver.service \
  jetson-upload-receiver-cleanup.timer \
  jetson-upload-caddy.service \
  jetson-upload-port-forward.timer
curl --fail https://uploads.example.com/health/ready
journalctl --user -u jetson-upload-receiver.service -n 100 --no-pager
journalctl --user -u jetson-upload-caddy.service -n 100 --no-pager
```

장비 token 신규 발급/교체 예시는 다음과 같다. 원문을 터미널이나 Git에 출력하지 않고 `--output` 파일만 안전하게 Jetson으로 전달한다. `--force`는 기존 Jetson의 인증을 즉시 교체하므로 새 파일을 장치에 배포할 준비가 된 경우에만 사용한다.

```bash
export UPLOAD_RECEIVER_DATA_ROOT=/data/server_storage/jetson-upload-receiver
export UPLOAD_RECEIVER_EXPECTED_MOUNT=/data/server_storage
export UPLOAD_RECEIVER_REQUIRE_MOUNT=true
export PYTHONPATH=upload_receiver

upload_receiver/.venv/bin/python -m upload_receiver.admin issue-token \
  --device-id <canonical-device-uuid> \
  --quota-bytes <quota> \
  --output /data/server_storage/jetson-upload-receiver/secrets/device-tokens/<deviceId>.token
```

DB, `storage/objects`, `token-pepper`, Caddy data를 함께 백업한다. pepper를 잃으면 DB의 token digest를 검증할 수 없고, DB와 objects를 서로 다른 시점으로 복원하면 metadata와 객체가 어긋난다. 백업에는 token 원문이 포함될 수 있으므로 저장 시 암호화하고 접근을 제한한다.

## Jetson 연동과 검증

서버에서 해당 Jetson UUID용으로 발급한 token 파일을 장비에 전달하고 [백엔드의 외부 업로드 설정](BACKEND.md#외부-업로드-설정)을 적용합니다. 앱의 `데이터 > 서버`에서 완료 세션·폴더·미리보기를 확인합니다.

단위 테스트 명령은 [README](../README.md#python-개발과-테스트)를 따릅니다. 배포 후에는 readiness, 무인증 `401`, 타 장비 접근 `403`, 12 MiB 초과 미리보기 `413`, 작은 파일의 청크·배치·deferred 업로드와 완료 객체 일치를 확인합니다. 이후 실제 외부망에서 중단·재개와 대용량 전송을 검증합니다.

2026-09-08 문서 정리 기준, 기존 기록은 작은 파일의 공인 HTTPS 업로드·서버 탐색까지 확인한 상태입니다. 실제 외부망 중단·재개, 장시간 대용량 전송과 장기 운영 지표 검증은 남아 있습니다. `/metrics`는 로컬에서 세션 상태·전체 byte의 기본 지표를 제공하며, 처리량·checksum·offset·latency 대시보드가 구현된 것으로 간주하지 않습니다.

## 복구

애플리케이션 문제는 이전 정상 코드로 되돌린 뒤 설치기를 재실행하고 readiness와 기존 업로드 계약을 검증합니다. 코드 원복을 위해 데이터 루트의 `db/`, `secrets/`, `storage/objects/`, `storage/staging/`을 삭제하지 않습니다. 저장 데이터 복구는 DB·객체·pepper의 일관된 백업을 기준으로 별도 수행합니다.
