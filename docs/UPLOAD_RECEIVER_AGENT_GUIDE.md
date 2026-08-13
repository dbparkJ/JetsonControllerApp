# External Upload Receiver: AI Agent Implementation Guide

이 문서는 Jetson Controller의 파일을 **같은 로컬 네트워크가 아닌 인터넷상의 서버**로 전송하기 위한 수신 서버 구현 계약과 현재 서버의 실제 배포 절차다. 다른 구현은 이 문서의 API와 완료 조건을 변경하지 말고 서버 프레임워크, DB, 오브젝트 스토리지만 배포 환경에 맞게 선택한다.

## 1. 확정 아키텍처

```text
Android app
  |  BLE / pinned HTTPS over LAN or Wi-Fi Direct (제어와 진행률만)
  v
Jetson control API
  |  outbound HTTPS :443 (실제 파일 데이터)
  v
Public upload receiver
  |-- PostgreSQL or equivalent transactional DB
  `-- S3-compatible object storage or durable local volume
```

- 앱과 수신 서버가 같은 LAN에 있을 필요가 없다.
- 파일 바이트는 Android 앱을 경유하지 않는다.
- Android 앱은 Jetson의 로컬 API에서 업로드 시작, 조회, 취소만 수행한다.
- 수신 서버는 공인 DNS와 신뢰 가능한 TLS 인증서를 사용한다.
- Jetson에서 외부로 나가는 TCP 443만 허용해도 동작해야 한다. 수신 서버가 Jetson으로 역접속하면 안 된다.

## 2. Jetson 송신 구현

송신 코드는 `backend/jetson_control/uploads.py`에 있다.

- 청크 크기: 최대 4 MiB
- 작은 파일이 많은 디렉터리는 수신기가 `fileBatch` capability를 광고할 때 최대 32 MiB, 256개 파일 단위로 묶어 전송
- 배치 전송 전 여러 파일의 오프셋을 한 요청으로 조회하고, 일부만 올라간 파일은 기존 4 MiB 재개 청크로 자동 전환
- 파일 전체 SHA-256을 세션 생성 전에 계산
- 청크별 `X-Chunk-SHA256` 전송
- 실패 시 0, 1, 3, 7초 간격으로 재시도
- 실패한 PUT 뒤 서버 오프셋을 다시 조회하여 이어서 전송
- 작업 상태는 `/var/lib/jetson-control/upload-jobs`에 원자적으로 저장
- Jetson/API 재시작 뒤 진행 중 작업은 같은 `clientJobId`로 자동 재개
- 완료 요청은 수 TiB 파일의 전체 해시 검증을 기다릴 수 있도록 응답 read timeout을 24시간으로 확장
- 운영 모드에서는 `http://`와 로컬 복사 대상을 사용하지 않는다.

## 3. 인증과 TLS

모든 `/v1/upload-sessions` 요청은 다음 헤더가 필요하다.

```http
Authorization: Bearer <device-token>
```

수신 서버 요구사항:

1. TLS 1.2 이상과 유효한 공인 인증서를 사용한다.
2. 토큰을 로그, 오류 응답, 추적 데이터에 기록하지 않는다.
3. 토큰 원문을 DB에 저장하지 않는다. Argon2id 또는 HMAC 기반 서버 측 해시로 검증한다.
4. 토큰은 정확히 하나의 `deviceId`에 연결한다.
5. 비활성화, 교체, 만료를 지원한다.
6. 인증 실패는 정보 차이를 드러내지 않고 `401`을 반환한다.
7. Jetson의 토큰 파일은 root 전용 `0600` 권한으로 저장한다.

CORS는 필요하지 않다. 호출자는 브라우저가 아닌 Jetson 데몬이다.

## 4. 식별자와 경로 규칙

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

## 5. 데이터 모델

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
| `sha256` | 선언된 전체 해시 |
| `next_offset` | 서버가 영속화한 다음 바이트 위치 |
| `state` | `PENDING`, `UPLOADING`, `RECEIVED`, `VERIFIED`, `FAILED` |
| `staging_key`, `final_key` | 저장소 객체 키 |

오프셋 변경은 해당 파일 행 잠금 또는 비교 후 갱신으로 직렬화한다. 메모리 변수만으로 진행률을 관리하면 안 된다.

## 6. API 계약

Base URL 예: `https://uploads.example.com`. 모든 응답은 JSON이다. 성공 응답의 필드명과 대소문자를 그대로 지킨다.

### 6.1 세션 생성

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
  "files": [
    {
      "path": "camera/front/000001.jpg",
      "sizeBytes": 1843200,
      "sha256": "64-lowercase-hex"
    }
  ]
}
```

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

### 6.2 파일 오프셋 조회

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

### 6.3 배치 파일 오프셋 조회

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

### 6.4 다수 파일 배치 업로드

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

수신기는 전체 body hash와 각 파일의 manifest 크기·SHA-256을 먼저 확인한 뒤 staging 객체를 기록한다. 오프셋이 `0`인 완전한 파일만 배치에 넣으며 부분 전송 파일은 6.5의 청크 API로 재개한다. 동일 배치의 응답이 유실되어 다시 도착하면 이미 완료된 파일을 덧붙이지 않고 같은 오프셋을 반환한다.

```json
{
  "files": [
    {"path": "camera/front/000001.jpg", "nextOffset": 1843200},
    {"path": "camera/front/000002.jpg", "nextOffset": 1843200}
  ]
}
```

### 6.5 청크 업로드

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

```json
{
  "nextOffset": 8388608
}
```

정상 응답은 `200`이다. 이전 오프셋의 청크가 중복 도착하면 데이터를 다시 붙이지 말고 `409`를 반환한다. Jetson은 오프셋 조회 후 계속한다. 응답이 유실되더라도 저장된 오프셋이 유지되어야 한다.

### 6.6 세션 완료

```http
POST /v1/upload-sessions/{sessionId}/complete
Authorization: Bearer <token>
Content-Type: application/json

{}
```

완료 처리:

1. 모든 파일의 `next_offset == size_bytes`를 확인한다.
2. 각 staging 파일의 전체 SHA-256을 manifest와 대조한다.
3. 최종 object key로 원자적 promote 또는 multipart complete를 수행한다.
4. 모든 파일이 성공한 뒤 세션을 `COMPLETED`로 바꾼다.
5. 같은 요청의 재호출은 성공 응답을 반환한다.

```json
{
  "state": "COMPLETED"
}
```

파일이 덜 전송됐으면 `409`, 해시가 다르면 `422`를 반환하고 세션을 `FAILED`로 표시한다.

### 6.7 세션 취소

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

## 7. 오류 응답

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

## 8. 저장소 설계

권장 object key:

```text
devices/{deviceId}/uploads/{sessionId}/{percent-encoded-relative-path}
```

- 사용자 경로를 OS 경로에 그대로 join하지 않는다.
- 로컬 볼륨을 쓰면 `openat` 계열 안전한 경로 처리 또는 검증된 storage abstraction을 사용한다.
- staging과 final namespace를 분리한다.
- 완료되지 않은 staging 데이터에는 24~72시간 lifecycle을 둔다.
- 완료 객체는 DB metadata와 함께 보존 정책, 암호화, 백업 정책을 적용한다.
- S3를 사용할 때 SSE-S3 또는 KMS 암호화를 활성화한다.

## 9. Reverse Proxy 설정 원칙

- 공개 포트는 443만 연다.
- 요청 body 제한은 manifest 32 MiB, 파일 배치 32 MiB, 청크 5 MiB 이상으로 구분한다.
- PUT request buffering을 꺼서 디스크 이중 사용을 피한다.
- upstream read/write timeout은 90초 이상으로 둔다.
- access log에서 `Authorization`을 제외한다.
- rate limit key는 토큰 원문이 아니라 인증된 `deviceId`를 사용한다.
- `/health/live`와 `/health/ready`를 제공하되 인증 정보나 저장소 경로를 노출하지 않는다.

## 10. 관측성과 운영

필수 metric:

- 세션 생성/완료/실패/취소 수
- 장비별 전송 바이트와 처리량
- checksum 실패 수
- offset 충돌과 재시도 수
- 열린 세션 수와 가장 오래된 세션 나이
- DB 및 object storage latency/error
- quota 사용량

로그 correlation key는 `sessionId`, `deviceId`, `clientJobId`다. 파일 경로는 개인정보 가능성이 있으므로 기본 로그에서 제외하거나 안전하게 축약한다.

## 11. 필수 테스트

구현 Agent는 최소 다음 자동화 테스트를 작성한다.

1. 정상 단일 파일과 다중 파일 업로드
2. 빈 파일 업로드
3. 한글, 공백, `%`, `+`가 포함된 UTF-8 경로
4. 절대 경로와 `../` traversal 거부
5. 잘못된 token 및 다른 장비 세션 접근 거부
6. 잘못된 chunk hash, 전체 file hash 거부
7. 잘못된 `Content-Range`와 4 MiB 초과 청크 거부
8. 같은 청크 중복 전송 시 데이터가 중복되지 않음
9. 청크 저장 후 HTTP 응답 유실을 가정한 offset 재조회/재개
10. 동일 `clientJobId` 세션 생성 재호출의 멱등성
11. 같은 세션 파일에 대한 동시 PUT 직렬화
12. 미완료 세션 complete 거부
13. cancel 재호출의 멱등성 및 staging 정리
14. 프로세스 재시작 뒤 DB offset과 저장 데이터 일치
15. quota, rate limit, 만료 세션 정리
16. 배치 body hash·파일별 hash·중복 경로·trailing bytes 거부
17. 배치 응답 유실 재시도와 부분 파일의 청크 전환

저장소 장애 주입 테스트에서 DB offset이 실제 내구성 저장보다 앞서가면 안 된다.

## 12. 배포 순서

1. 공인 DNS와 TLS 인증서를 준비한다.
2. DB schema migration을 적용한다.
3. object storage bucket, 암호화, lifecycle, service credential을 만든다.
4. 장비 UUID에 묶인 upload token을 발급한다.
5. 수신 서버를 배포하고 readiness와 외부 HTTPS를 검증한다.
6. 수신 서버에서 token 파일을 Jetson으로 안전하게 전달한다.
7. Jetson upload target을 설정한다. 설정 script는 token을 `/etc/jetson-control/upload-receiver.token`에 `0600`으로 복사하고 localhost, `.local`, 사설 literal IP를 거부한다.

```bash
sudo /opt/jetson-control/configure-upload-target.sh \
  https://uploads.example.com \
  ./receiver.token \
  "Operations cloud"
```

8. 작은 테스트 파일, 네트워크 강제 중단/복구, 대용량 파일 순으로 검증한다.

## 13. 완료 기준

- Jetson과 수신 서버가 서로 다른 공인 네트워크에서도 업로드된다.
- Android 앱을 종료해도 Jetson 업로드는 계속된다.
- 일시적 인터넷 단절 뒤 파일 처음부터가 아니라 서버 offset부터 재개한다.
- 완료된 모든 파일의 전체 SHA-256이 원본 manifest와 일치한다.
- 경로 traversal, 타 장비 접근, token 로그 유출 테스트가 통과한다.
- 중복 요청으로 파일 바이트나 session이 중복 생성되지 않는다.
- 운영 대시보드에서 처리량, 실패, backlog, quota를 확인할 수 있다.

이 조건을 모두 만족하기 전에는 수신 서버를 운영 준비 완료로 표시하지 않는다.

## 14. 이 저장소의 수신기 구현

현재 구현은 `upload_receiver/`에 있다. Android 앱은 수신기에 직접 연결하지 않고 기존 Jetson Local Control API만 사용하므로 앱 코드는 변경하지 않았다.

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

## 15. 이 PC의 실제 배포

배포·검증일은 2026-08-13 KST다. `/data` 디렉터리 자체는 HDD가 아니라 root NVMe에 있다. 실제 3.6 TB ext4 HDD `/dev/sda1`의 mount point가 `/data/server_storage`이므로, 새 저장 루트는 반드시 그 아래를 사용한다.

```text
/data/server_storage/jetson-upload-receiver/
|-- db/receiver.sqlite3
|-- secrets/token-pepper
|-- secrets/device-tokens/<deviceId>.token
|-- storage/staging/<deviceId>/<sessionId>/*.blob
|-- storage/objects/<deviceId>/<sessionId>/{manifest.json,*.blob}
|-- storage/locks/
|-- runtime/
`-- caddy/{data,config}/
```

`ConditionPathIsMountPoint=/data/server_storage`, `RequiresMountsFor=/data/server_storage`와 시작 전 `mountpoint` 검사를 함께 사용한다. HDD mount가 빠진 부팅에서 같은 경로명의 NVMe 디렉터리에 조용히 기록하는 fallback을 허용하지 않는다.

설치 또는 코드 업데이트:

```bash
cd /home/geonws/JetsonControllerServer/JetsonControllerApp
./upload_receiver/scripts/install-user-service.sh \
  /data/server_storage/jetson-upload-receiver
```

이 명령은 user service 환경에 `UPLOAD_RECEIVER_MAX_BATCH_BYTES=33554432`, `UPLOAD_RECEIVER_MAX_BATCH_FILES=256`을 기록한다. 기존 설치도 코드를 갱신한 뒤 같은 명령을 다시 실행해야 receiver의 배치 route와 정확한 HDD 저장 루트가 함께 반영된다. 이 명령은 실행 중인 Caddy 설정까지 갱신하지 않으므로 `Caddyfile`이 바뀐 코드 업데이트에서는 바로 아래의 공인 HTTPS 구성 명령도 다시 실행한다. 다른 경로를 임시로 사용하지 말고 receiver 데이터는 항상 `/data/server_storage/jetson-upload-receiver` 아래에 둔다.

공인 HTTPS를 처음 구성하거나 다시 적용:

```bash
./upload_receiver/scripts/configure-public-https.sh \
  125-142-22-24.sslip.io
```

현재 공개 base URL은 다음 값이다.

```text
https://125-142-22-24.sslip.io
```

공유기의 UPnP TCP 443 mapping은 이 PC의 TCP 443으로 연결되고 15분마다 확인한다. Caddy 인증서와 설정은 HDD의 `caddy/` 아래 보존한다. 현재 주소는 공인 IPv4를 이름에 포함하는 임시 운영 DNS다. 공인 IP가 바뀌면 기존 hostname은 새 서버를 가리키지 않으므로 새 `<공인-IP-with-dashes>.sslip.io` 주소로 HTTPS를 다시 구성하고 모든 Jetson target URL도 바꿔야 한다. 장기 운영에는 소유한 고정 DNS/DDNS 이름과 DHCP 예약 또는 고정 LAN 주소가 더 적합하다.

상태 확인:

```bash
findmnt /data/server_storage
systemctl --user --no-pager --full status \
  jetson-upload-receiver.service \
  jetson-upload-receiver-cleanup.timer \
  jetson-upload-caddy.service \
  jetson-upload-port-forward.timer
curl --fail https://125-142-22-24.sslip.io/health/ready
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

## 16. 현재 장치가 바라볼 주소

현재 장비 ID `d606c26d-98d6-4b09-99d7-c3da7dda4de0` 전용 token은 다음 서버 파일에 발급돼 있다. 파일 내용은 이 문서에 기록하지 않는다.

```text
/data/server_storage/jetson-upload-receiver/secrets/device-tokens/d606c26d-98d6-4b09-99d7-c3da7dda4de0.token
```

이 파일을 USB, `scp` 등 신뢰할 수 있는 경로로 Jetson의 임시 `./receiver.token`에 전달한 뒤 Jetson에서 실행한다.

```bash
sudo /opt/jetson-control/configure-upload-target.sh \
  https://125-142-22-24.sslip.io \
  ./receiver.token \
  "Operations upload server"

rm -f ./receiver.token
```

설정 결과 Jetson은 `/etc/jetson-control/upload_targets.json`의 `external.base_url`로 위 HTTPS 주소를 바라보고, token은 `/etc/jetson-control/upload-receiver.token`에 `0600`으로 복사된다. 이 관리자 script 방식을 사용하면 Android 앱에는 URL이나 token을 다시 넣지 않고 연결한 Jetson에서 표시되는 `Operations upload server` target만 선택한다. 최신 앱의 업로드 서버 관리 화면에서 같은 URL과 token을 입력해 앱 관리 target으로 등록하는 방법도 지원하지만, token 파일을 Jetson에 직접 전달할 수 있는 환경에서는 노출 면이 작은 관리자 script 방식을 권장한다.

2026-08-13에는 공인 URL을 통한 배포 smoke file에 대해 세션 생성, offset 조회, PUT, complete, SQLite `COMPLETED`, HDD final 객체와 manifest의 내용 일치까지 검증했고, 최종 코드 재배포 뒤 31-byte 시험도 다시 통과했다. 이후 최신 `main`으로 receiver와 Caddy를 재설치하고 공인 HTTPS에서 `fileBatch` capability(`32 MiB`, 256개), 일괄 offset 조회, 2개 파일 총 60바이트의 batch PUT, 같은 batch 재전송의 멱등성, complete와 HDD final 객체 일치까지 확인했다. 기존 DB, 완료 객체, 장비 token은 그대로 보존됐다. API/HDD/HTTPS 배포는 완료됐지만 이 문서 10절과 13절의 전체 운영 준비 완료 기준은 아직 아니다. 현재 `/metrics`는 localhost에서 최소 세션 상태와 전체 byte만 제공하며, 별도 dashboard와 처리량/checksum/offset/latency 장기 metric은 남은 운영 과제다. 실제 Jetson에서의 외부망 중단 재개와 대용량 파일 시험도 위 target을 장치에 적용한 뒤 수행한다.
