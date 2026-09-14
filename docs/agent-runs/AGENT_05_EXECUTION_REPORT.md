# AG05 서버 직접 조회·Storage 실행 보고서

## 구현 결과

- public receiver에 Jetson 장비 token과 분리된 직원 bearer 인증을 추가했다. 직원 token은 `emp_` namespace와 별도 DB table을 사용하며 원문 대신 기존 server pepper HMAC을 저장한다. 역할은 `VIEWER`, `OPERATOR`, `ADMIN`이고 모든 직접 API가 명시적인 access project grant를 다시 확인한다.
- device-to-access-project 배정은 장비당 하나이며 최초 배정 뒤 다른 project로 바꾸지 못한다. 기존 session을 새 project에 조용히 노출하는 재배정을 막는다. 이 access project는 조사·pipeline project metadata와 별개다.
- `/v1/server/*`에서 Jetson 없이 job 상태, 완료 파일 계층, 이미지·영상 preview, 독립 검증 receipt를 조회할 수 있다. job에는 upload state/수신 byte/갱신 시각과 root path/image/video 요약이 포함된다. receipt는 최종 객체를 다시 읽어 검증한 뒤에만 `state=COMPLETED`, `matched=true`를 반환한다.
- server environment를 `development`, `test`, `production`으로 구분했다. 직접 API는 `X-Expected-Server-Environment`가 실제 설정과 일치해야 하며 응답에도 환경을 포함한다. 설치기는 기본 `production`을 기록하고 두 번째 인자로 환경을 명시할 수 있다.
- Android 독립 data layer를 추가했다. HTTPS root URL만 허용하고 redirect와 automatic connection retry를 끄며 직원 token과 expected environment를 request header에 넣는다. 응답의 environment, employee identity, project, session scope를 확인한다.
- 직원 token은 Android Keystore AES-GCM으로 암호화해 DataStore에 저장한다. credential 변경·logout 시 최근 cache를 폐기한다. cache key는 environment+base URL+employee identity+project이고 credential revision도 대조한다. cache fallback은 일반 transport/서버 가용성 실패에만 허용하며 TLS, `401`, `403`, 환경/identity/project 불일치에서는 사용하지 않는다. cache 결과는 `isCurrent=false`, 저장된 receiver `refreshedAt`, refresh 오류를 함께 전달한다.
- 완료 job의 휴지통 이동과 복원을 구현했다. `OPERATOR` 이상만 실행할 수 있으며 같은 filesystem의 directory rename과 영속 transition row를 사용한다. rename 뒤 fsync/DB 결과가 불명확하면 transition을 보존하고 시작 복구가 실제 directory 위치로 정리한다. timeout/`5xx`에 대한 Android 결과는 `UNKNOWN`이며 자동 재시도 계약이 아니다.
- 기존 device-token upload와 `/v1/library/*` 호환 경로는 유지했다. trash 중인 session은 기존 library 조회와 영구 삭제 경로에서도 제외한다.

## 직접 API 계약

- `GET /v1/server/capabilities`
- `GET /v1/server/jobs?projectId=&limit=&offset=`
- `GET /v1/server/jobs/{sessionId}/files?projectId=&path=`
- `GET /v1/server/jobs/{sessionId}/preview?projectId=&path=`
- `GET /v1/server/jobs/{sessionId}/receipt?projectId=`
- `GET /v1/server/trash?projectId=`
- `DELETE /v1/server/jobs/{sessionId}?projectId=`
- `POST /v1/server/trash/{sessionId}/restore?projectId=`

직원/project 구성용 admin command는 `upsert-project`, `assign-device-project`, `issue-employee-token`, `disable-employee`다. 직원 token 파일은 원문을 출력하지 않고 `0600`으로 원자적으로 게시한다.

`INTEGRATION_REQUEST`: AG02는 기존 `ServerStorageViewModel`의 Jetson target/IP 의존을 제거하고 `com.example.jetsoncontroller.data.server.ServerProfileStore`에서 `ServerConnection`을 읽어 `DirectServerApiFactory`와 `DirectServerRepository`를 구성한다. 연결 전 profile에는 `profileId`, `displayName`, `ServerEnvironment`, HTTPS root `baseUrl`, `employeeId`, `projectId`가 필요하다. 저장은 `save(profile, employeeToken)`, 단건 load는 `connection(profileId)`, 목록은 `profiles: Flow<List<StoredServerProfile>>`, logout/remove는 `remove(profileId)`다. `connect()` 성공 뒤 `jobs/files/preview/receipt/trash/moveToTrash/restore`를 호출한다. cache 구현은 `com.example.jetsoncontroller.data.storage.RecentServerJobsCache`다. `ServerJobsSnapshot.source=CACHE`는 현재 상태가 아니므로 `response.refreshedAt`을 마지막 서버 갱신으로 표시한다. lifecycle `UNKNOWN`은 실패 완료로 표시하거나 자동 재시도하지 말고 job/trash를 새로 조회한다. logout은 `ServerProfileStore.remove(profileId)`와 `DirectServerRepository.logout()`으로 persisted credential과 cache를 정리한다.

## 검증

receiver 전체 test:

```text
/home/jm/ControllerApp/JetsonControllerApp/backend/.venv/bin/python -m unittest tests.test_receiver
Ran 37 tests in 3.272s
OK
```

추가 test는 device/employee token 분리, environment mismatch, project grant/session isolation, viewer mutation 거부, path summary, preview type/크기, receipt `matched`, 직원 token 평문 미저장, 장비 project 재배정 거부, trash/restore, rename 뒤 fsync 실패 양방향 복구를 포함한다.

Android direct data/cache test:

```text
./gradlew :app:testDebugUnitTest --tests '*DirectServerRepositoryTest' --max-workers=1 --console=plain
BUILD SUCCESSFUL in 4s
```

HTTPS profile validation, scope·credential별 cache 격리, stale 표시, availability fallback, `403` cache 폐기, coroutine cancellation 전파를 확인했다. `git diff --check`와 receiver Python compile도 통과했다.

## 운영 검증 공백과 제한

서버 배포, service restart, 실제 public HTTPS/인증서, 실제 HDD mount에서 장시간 upload와 trash rename, Android 기기의 Keystore/DataStore 동작은 수행하지 않았다. 실제 운영 전 직원/project/token을 서버에 구성하고 production environment header, 타 project `403`, receipt object 검증, crash recovery를 배포 환경에서 확인해야 한다.

현재 직원 인증은 receiver가 직접 관리하는 만료·회전·비활성화 가능한 token 방식이다. 조직 IdP/SSO, 자동 직원 퇴사 처리, 중앙 project directory 연동은 저장소에 계약이나 provider가 없어 구현하지 않았다. 직접 API의 영구 purge도 제공하지 않았으며 기존 장비 소유 token의 `/v1/library/*` 영구 삭제 호환 계약은 남아 있다. 이 두 운영 정책은 release 전에 보안/운영 담당자가 결정해야 한다.

Jetson, receiver host, Android 장치에 배포하지 않았고 main 또는 통합 branch에 병합하지 않았다.
