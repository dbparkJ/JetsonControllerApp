# Demo Freeze·내부 운영 Release Checklist

이 문서는 실행 template과 gate 정의다. 체크박스가 비어 있는 상태는 통과가 아니다. 실제 결과는 commit별 evidence record와 [ACCEPTANCE_MATRIX_KO.md](ACCEPTANCE_MATRIX_KO.md)에 연결한다.

## 1. 공통 Freeze

- [ ] release commit이 정해졌고 worktree가 clean이다.
- [ ] Android APK의 versionName, versionCode, SHA-256과 서명 주체를 기록했다.
- [ ] backend, pipeline source/config, receiver revision과 environment를 기록했다.
- [ ] phone, tablet, Orin NX, receiver host의 실제 환경을 support matrix에 기록했다.
- [ ] full Android build/lint/JVM, backend discovery, receiver discovery CI가 같은 commit에서 통과했다.
- [ ] 실패·skip·disabled lint/test를 검토했고 이유와 owner가 있다.
- [ ] secret, token, QR URI, NTRIP credential, private key가 artifact/log/screenshot에 없다.
- [ ] rollback artifact와 복구 책임자가 정해졌고 rehearsal evidence가 있다.
- [ ] 변경 freeze 뒤에는 blocker 수정만 허용하고 수정 후 영향 suite와 gate를 다시 실행한다.

## 2. Demo Gate

Demo는 제한된 대표 흐름을 보여 주는 합격이며 내부 운영 승인이 아니다.

- [ ] 선택한 한 장비의 identity와 transport를 참가자에게 보여 준다.
- [ ] 시작 전 작업과 관찰 가능한 preflight 근거를 보여 준다.
- [ ] start 요청 수락과 `RUNNING + activeRunId` 관찰을 구분한다.
- [ ] 연결을 끊어도 수집 중단으로 표시하지 않고 재연결 뒤 같은 run을 조회한다.
- [ ] stop 뒤 terminal state와 실행 footer/exit를 조회한다.
- [ ] Jetson output 위치와 현재 확인 가능한 file/bytes evidence를 보여 준다.
- [ ] upload job, remote session, receiver `matched=true` receipt를 구분한다.
- [ ] Jetson이 없어도 직원 credential과 올바른 environment/project로 server를 조회한다.
- [ ] 품질 화면은 관찰값과 문제 구간만 보여 주고 승인되지 않은 합격 임계값을 표시하지 않는다.
- [ ] 응답 유실이나 offline 시 자동 mutation 재전송이 없음을 보여 준다.
- [ ] `projectId`/`sectionId`, run-output identity 등 미구현 제한을 demo 시작 전에 명시한다.
- [ ] 모든 step에 log와 대표 screenshot artifact가 있다.

현재 P0 공백 때문에 demo를 현장 운영 준비 완료로 소개할 수 없다.

## 3. 내부 운영 Gate

다음은 모두 필수다.

- [ ] `REQ-CTX-001`: survey project를 선택·저장하고 run 동안 고정한다.
- [ ] `REQ-CTX-002`: project 소속 survey section을 선택·고정한다.
- [ ] `REQ-RUN-002`: device/project/section/pipeline/source/config/preflight/output identity가 같은 run에 영속 연결된다.
- [ ] `REQ-STO-002`: 기대 output file count/bytes/finish 근거와 partial/missing 정책이 있다.
- [ ] 조직이 승인한 직원 credential lifecycle, project grant, 퇴사/회전 절차가 있다.
- [ ] pipeline별 required/optional sensor 정책이 인간 PM에게 승인되고 versioned source로 runtime/preflight에 연결됐다. 필수 센서가 없는 정책도 명시적으로 승인·versioning하며 `UNSPECIFIED`를 운영 합격으로 사용하지 않는다.
- [ ] storage 최소량, 정상 operator stop 결과, RTK 임계값의 PM 결정이 기록됐다. 미결 값을 임의 기본값으로 대체하지 않았다.
- [ ] support matrix의 필수 phone/tablet/Orin/receiver 조합이 current commit으로 `QUALIFIED`다.
- [ ] 연결 단절 장시간, upload 중단/재개, receiver 장애, storage 장애, credential 거절을 실환경에서 검증했다.
- [ ] backup/restore 및 Android/backend/receiver rollback rehearsal를 완료했다.
- [ ] monitoring, log 보존, incident owner, 연락 경로, 데이터 보존 기간이 승인됐다.

## 4. Hard Blocker Query

아래 중 하나라도 `OPEN`, `UNKNOWN`, 또는 증거 없음이면 내부 운영 release는 `BLOCKED`다. 데이터 손실, 잘못된 장비 제어, 인증 blocker는 원인을 해결하고 영향 시험을 다시 통과해야 하며 waiver로 닫을 수 없다. Astra `FAIL`도 수정·재검수 전에는 인간 승인으로 우회하지 않는다.

| Blocker | 확인 질문 | 필수 증거 |
|---|---|---|
| 데이터 손실 | 미검증/부분 upload, delete, rollback, storage 장애에서 원본이 보존되는가? | failure injection + Jetson/receiver object inventory |
| 잘못된 장비 제어 | 장비 전환과 늦은 응답에서 mutation이 선택 deviceId에만 적용되는가? | 두 Jetson correlation log와 mutation count |
| 인증·권한 | stale/다른 environment/다른 project/disabled 직원이 cache나 device token으로 우회하지 못하는가? | allow/deny 실환경 log와 감사 record |
| 결과 오인 | offline/timeout/accepted를 completed로 표시하지 않는가? | 상태별 screenshot + target GET evidence |
| 추적 단절 | run에서 project/section/output/upload/receipt로 추적 가능한가? | immutable identity chain |

## 5. 현재 Release 판정

2026-09-14 작업 세트는 자동 검증과 문서 준비 단계다. current commit의 phone/tablet/Orin NX 실장치, 공개 receiver, demo, field evidence가 없고 핵심 project/section/run/output 계약이 미완료다.

- Demo: `NOT_RUN`
- 내부 운영 release: `BLOCKED`
- main merge 승인: Astra 통합 검수, PM 평가, 인간 PM 실기와 명시 승인이 필요

## 6. 승인 Record

```text
Release commit:
CI run URL / immutable run ID:
Acceptance matrix revision:
Open blocker count:
Demo result / evidence ID:
Internal release result / evidence ID:
Engineering reviewer / time:
Operations reviewer / time:
Human PM decision / time / reference:
```
