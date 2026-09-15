# QA Evidence Template

이 파일은 실행 결과가 아니다. 시험마다 이 template을 복사해 별도 evidence record를 만들고 실제 생성된 log와 screenshot만 연결한다. 실행하지 않은 항목은 빈 이미지나 예시 성공값으로 채우지 않고 `NOT_RUN`으로 남긴다.

## 1. 실행 Metadata

```text
Evidence ID:
Classification: AUTOMATED_LOCAL | AUTOMATED_CI | DEVICE | DEMO | FIELD
Requirement IDs:
Support matrix IDs:
Result: PASS | FAIL | BLOCKED | NOT_RUN
Repository commit:
Dirty worktree: true | false
Started at (ISO 8601 + timezone):
Finished at (ISO 8601 + timezone):
Operator:
Environment owner:
```

`AUTOMATED_LOCAL`과 worker 실행 보고서는 `AUTOMATED_CI`가 아니다. GitHub Actions URL과 immutable run ID가 있을 때만 `AUTOMATED_CI`로 기록한다.

## 2. 환경

```text
Android model / form factor / OS / API:
APK versionName / versionCode / SHA-256:
Jetson model / JetPack-L4T / Ubuntu / kernel:
Backend revision / Python / BlueZ / NetworkManager:
Wi-Fi chipset / driver / LAN or P2P context:
Pipeline ID / source revision / config SHA-256:
Receiver environment / build revision / TLS hostname:
Employee/project references (non-secret):
```

## 3. 시험표

| Step | 사전 조건 | 수행 | 기대 관찰 | 실제 관찰 | Result | Evidence |
|---|---|---|---|---|---|---|
| 1 |  |  |  |  | NOT_RUN |  |

mutation의 HTTP 수락과 실제 완료를 한 행으로 합치지 않는다. start는 `RUNNING`과 새 `activeRunId`, stop은 non-running 상태와 가능한 `finishedAt`/`exitCode`, upload는 `COMPLETED`와 `matched=true` receipt를 별도 관찰로 기록한다.

## 4. Log Evidence

```text
Command or collection method:
Exit code:
Full log artifact path:
Log SHA-256:
Relevant bounded excerpt:
Redactions performed:
```

전체 log를 보존하고 문서에는 판단에 필요한 짧은 구간만 인용한다. secret, bearer token, QR URI, NTRIP password, private key, raw device UUID는 저장 전에 제거한다. 삭제한 값을 임의 문자열로 바꿔 실제 값처럼 보이게 하지 말고 `<REDACTED:TYPE>`으로 표시한다.

## 5. 대표 화면 Evidence

현재 release에서 필요한 대표 화면은 다음과 같다. 실제 장치에서 current commit을 실행해 캡처하기 전에는 모두 `NOT_RUN`이다.

| Shot ID | 화면 / 상태 | 필수로 보여야 할 사실 | 파일 | SHA-256 | Result |
|---|---|---|---|---|---|
| SHOT-01 | 홈 / 연결됨 | 선택 장비, 실제 transport, 연결과 수집 상태 분리 |  |  | NOT_RUN |
| SHOT-02 | 시작 전 점검 | 작업, 시간, storage evidence, sensor policy, GNSS/RTK, server |  |  | NOT_RUN |
| SHOT-03 | 수집 중 | pipeline RUNNING, activeRunId, 관찰 시각 |  |  | NOT_RUN |
| SHOT-04 | 연결 단절 | 수집 중단 추정 없음, 마지막 관찰, 확인 필요, 다음 행동 |  |  | NOT_RUN |
| SHOT-05 | 재연결 | 같은 deviceId와 실제 run 재조회 |  |  | NOT_RUN |
| SHOT-06 | 종료·Jetson 저장 | terminal evidence와 output path/file/bytes |  |  | NOT_RUN |
| SHOT-07 | Upload 완료 | job/session identity, receipt matched, source 보존/삭제 가능 여부 |  |  | NOT_RUN |
| SHOT-08 | Jetson 없는 서버 조회 | 인터넷 경로, environment/project, stale/current 시각 |  |  | NOT_RUN |
| SHOT-09 | 품질 | 같은 run의 raw summary와 문제 구간, 임의 pass/fail 없음 |  |  | NOT_RUN |
| SHOT-10 | Phone/Tablet layout | 주요 action과 상태가 잘림·겹침 없이 표시 |  |  | NOT_RUN |

Screenshot에는 notification, status bar, keyboard clipboard suggestion 등에서 개인정보나 secret이 노출되지 않았는지 확인한다. crop은 가능하지만 상태 판단에 필요한 장비·run·시각 context를 제거하지 않는다.

## 6. 장애 주입과 복구

```text
Injected condition:
Injection start/end:
Was a mutation already dispatched?: yes | no | unknown
Observed target state after recovery:
Duplicate mutation count:
Data retained at Jetson:
Receiver receipt state:
Rollback/recovery action:
```

권장 조건은 start/stop 응답 유실, 화면 잠금, LAN 단절, Wi-Fi Direct group 손실, receiver timeout, upload 중단/재개, storage read-only/full, 잘못된 environment/project, stale credential이다. 운영 data나 승인되지 않은 장치에는 주입하지 않는다.

## 7. 판정과 Sign-off

```text
Confirmed facts:
Unconfirmed scope:
Release blockers opened/closed:
Artifact reviewer:
Reviewer decision and time:
PM approval reference (when required):
```

실장치 미실행, 정책 미결, artifact 누락은 자동 test 성공으로 덮지 않는다. 데이터 손실, 잘못된 장비 제어, 인증 hard blocker에는 waiver를 적용하지 않는다. 원인을 해결하고 영향 시험을 다시 통과한 뒤에만 gate를 닫는다. 인간 PM은 승인된 운영 범위와 미결 정책을 결정하고 모든 gate가 충족된 뒤 최종 release를 승인한다.
