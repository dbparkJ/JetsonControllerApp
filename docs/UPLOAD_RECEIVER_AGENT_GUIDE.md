# External Upload Receiver: AI Agent Implementation Guide

이 문서는 Jetson Controller의 파일을 **같은 로컬 네트워크가 아닌 인터넷상의 서버**로 전송하기 위한 수신 서버 구현 계약이다. 구현 Agent는 이 문서의 API와 완료 조건을 변경하지 말고 서버 프레임워크, DB, 오브젝트 스토리지만 배포 환경에 맞게 선택한다.

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
- 파일 전체 SHA-256을 세션 생성 전에 계산
- 청크별 `X-Chunk-SHA256` 전송
- 실패 시 0, 1, 3, 7초 간격으로 재시도
- 실패한 PUT 뒤 서버 오프셋을 다시 조회하여 이어서 전송
- 작업 상태는 `/var/lib/jetson-control/upload-jobs`에 원자적으로 저장
- Jetson 재시작으로 실행 스레드가 사라진 작업은 `FAILED`로 복구
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

권장 초기 제한:

| 항목 | 권장값 |
|---|---:|
| 세션당 파일 | 100,000 |
| 세션 전체 크기 | 장비 정책에 따라 1~5 TiB |
| 개별 청크 | 4 MiB |
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
| `state` | `PENDING`, `UPLOADING`, `VERIFIED` |
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
  "sessionId": "dfe4038e-314c-45e0-b0d5-e8bca82b163c"
}
```

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

### 6.3 청크 업로드

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

### 6.4 세션 완료

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

### 6.5 세션 취소

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
- 요청 body 제한은 manifest 32 MiB, 청크 5 MiB 이상으로 구분한다.
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
