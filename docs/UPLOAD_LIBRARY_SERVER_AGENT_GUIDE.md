# Upload Library Server Agent Guide

이 문서는 업로드 완료 파일을 Android 앱의 `데이터 > 서버` 탭에서 탐색하고 미리보기 위해 public upload receiver에 배포할 변경과 검증 절차를 정의한다.

## 1. 보안 경계

- Android 앱에는 public receiver bearer token을 절대 전달하지 않는다.
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

## 2. Receiver API 계약

모든 `/v1/library/*` 요청은 `Authorization: Bearer <device-token>`이 필요하다.

### Capability

`GET /v1/capabilities`

```json
{
  "library": {
    "version": 1,
    "maxPreviewBytes": 12582912
  }
}
```

### 완료 세션 목록

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

### 가상 폴더 목록

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

### 파일 미리보기

`GET /v1/library/sessions/{sessionId}/file?path=camera/front/frame-0001.jpg`

- MIME type은 확장자로 결정한다.
- `UPLOAD_RECEIVER_MAX_PREVIEW_BYTES` 기본값은 `12582912`이다.
- 제한 초과는 `413`, 다른 장비 session은 `403`, 없는 파일은 `404`다.
- `Range`와 무제한 다운로드는 version 1 범위에 포함하지 않는다.

## 3. 배포 절차

서버의 기존 data root, DB, pepper, token을 보존한다. application code만 원자적으로 재설치한다.

```bash
cd /home/geonws/JetsonControllerServer/JetsonControllerApp
git fetch origin
git switch main
git pull --ff-only origin main

PYTHONPATH=upload_receiver \
  backend/.venv/bin/python -m unittest discover \
  -s upload_receiver/tests -p 'test_*.py'

./upload_receiver/scripts/install-user-service.sh \
  /data/server_storage/jetson-upload-receiver

./upload_receiver/scripts/configure-public-https.sh \
  125-142-22-24.sslip.io
```

`install-user-service.sh`가 사용하는 Python 환경에는 업데이트된 `upload_receiver/requirements.txt`를 설치해야 한다. 이번 library 기능은 DB schema migration이 없으며 기존 완료 object를 즉시 탐색할 수 있다.

선택 설정:

```ini
UPLOAD_RECEIVER_MAX_PREVIEW_BYTES=12582912
```

이 값은 receiver systemd environment에 추가한 뒤 daemon reload와 restart를 수행한다. 앱과 Jetson proxy도 12 MiB 상한을 가지므로 더 큰 값은 version 1 앱 동작을 바꾸지 않는다.

## 4. 배포 검증

토큰은 shell history, process list, 로그에 남기지 않는다. 서버의 보호된 token file을 읽을 수 있는 관리 shell에서만 요청을 실행한다.

1. `GET /health/ready`가 `200 READY`인지 확인한다.
2. 인증 없는 `GET /v1/library/sessions`가 `401`인지 확인한다.
3. 장비 token으로 `GET /v1/capabilities`의 `library.version == 1`을 확인한다.
4. 장비 token으로 sessions 목록을 조회하고 최근 완료 upload가 보이는지 확인한다.
5. root와 하위 폴더를 조회해 `relativePath`가 정상인지 확인한다.
6. 12 MiB 이하 JPEG/PNG와 UTF-8 text 파일을 열어 MIME type과 byte가 일치하는지 확인한다.
7. 다른 장비 token으로 같은 session을 조회했을 때 `403`인지 확인한다.
8. 12 MiB 초과 파일이 `413`인지 확인한다.
9. 기존 resumable upload, batch upload, deferred hash upload를 각각 한 번 완료한다.

자동 회귀 테스트:

```bash
PYTHONPATH=upload_receiver \
  backend/.venv/bin/python -m unittest discover \
  -s upload_receiver/tests -p 'test_*.py'
```

기대 결과는 library 소유권/크기 제한 테스트를 포함한 `30 tests OK` 이상이다. 이후 테스트가 추가되면 개수보다 전체 통과 여부를 기준으로 한다.

## 5. Jetson 및 앱 연동 확인

Jetson backend가 업데이트된 뒤 앱의 `데이터 > 서버`에서 다음을 확인한다.

- 등록된 HTTP upload target을 선택할 수 있다.
- 완료 upload session이 최신 순으로 보인다.
- 폴더 이동과 뒤로가기가 동작한다.
- 사진이 화면에 맞춰 열리고 pinch/pan 및 1–6배 확대가 동작한다.
- UTF-8 text는 선택 가능한 텍스트로 열린다.
- receiver가 구버전이면 앱에 “Upload server library is not installed” 오류가 표시되고 기존 업로드 기능은 계속 동작한다.

Jetson local proxy API:

| Method | Path | 역할 |
|---|---|---|
| `GET` | `/v1/upload/library/sessions?target=...&offset=...` | receiver session 목록 프록시 |
| `GET` | `/v1/upload/library/files?target=...&session=...&path=...` | 가상 폴더 목록 프록시 |
| `GET` | `/v1/upload/library/file?target=...&session=...&path=...` | 제한된 미리보기 byte 프록시 |

## 6. 롤백

1. 이전 정상 commit으로 application code만 되돌린다.
2. `install-user-service.sh`를 다시 실행한다.
3. `/health/ready`와 기존 upload create/chunk/batch/complete를 검증한다.
4. data root의 `db/`, `secrets/`, `storage/objects/`, `storage/staging/`은 삭제하거나 복원하지 않는다.

구버전 receiver로 롤백하면 앱의 서버 열람 탭만 사용할 수 없고, 기존 업로드 데이터와 업로드 프로토콜은 유지된다.
