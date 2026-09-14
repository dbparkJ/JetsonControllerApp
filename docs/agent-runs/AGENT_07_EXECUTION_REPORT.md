# AG07 QA·CI·Release 실행 보고서

## 구현 결과

- `docs/product/REQUIREMENTS_TRACEABILITY_KO.md`의 현재 42개 요구사항을 acceptance matrix에 정확히 한 번씩 연결했다. 자동, 실장치/운영환경, demo, 내부 운영 gate를 별도 열로 유지하며 자동 test 결과를 장치 또는 release 합격으로 승격하지 않는다.
- Android phone/tablet, Jetson Orin NX, upload receiver의 구성 요소와 10개 필수 조합을 support matrix에 정의했다. current release commit의 장치 증거가 없으므로 모든 조합은 `NOT_RUN`/`NOT_QUALIFIED`다. Xavier NX는 이번 지원 대상으로 표시하지 않았다.
- 실행 metadata, 시험표, full log hash, 대표 screenshot 10종, 장애 주입, 판정/sign-off를 기록하는 evidence template을 만들었다. 실제 실행하지 않은 screenshot이나 성공 log는 생성하지 않았다.
- demo freeze와 내부 운영 checklist를 분리했다. 데이터 손실, 잘못된 장비 제어, 인증 문제와 Astra `FAIL`은 waiver 없이 원인 해결·재시험이 필요한 hard blocker로 정의했다.
- Android, Jetson backend, receiver별 진단과 rollback 절차를 작성했다. 장치 target 확인, 증거 보존, data/credential 비삭제, schema 호환 확인을 선행하며 과거 `backend-deployment-manifest.json`을 current 배포 승인이나 전체 package manifest로 재사용하지 않는다.
- acceptance table 누락·중복, 빈 요구사항 입력, 잘못된 evidence result vocabulary를 실패시키는 `scripts/check_qa_acceptance.py`를 추가했다.

## CI 보강

`.github/workflows/reliability.yml`을 다음과 같이 변경했다.

- `upload_receiver/**`, QA 문서/checker 변경도 workflow를 trigger한다.
- Android job은 SDK 37/JVM 25를 유지하고 `assembleDebug`, `lintDebug`, 전체 `testDebugUnitTest`를 실행한다.
- backend job은 `ubuntu-24.04`와 `/usr/bin/python3`를 사용한다. `python3-cryptography`, `python3-dbus`, `python3-gi`, `python3-venv`를 apt로 설치하고 `--system-site-packages` venv에서 전체 unittest discovery를 실행한다.
- upload receiver는 별도 Python 3.11 job에서 전체 unittest discovery를 실행한다.
- QA contract job은 requirement/acceptance matrix 일관성을 확인한다.

원격 GitHub Actions는 이 작업에서 실행하지 않았다. action 설치, apt mirror, SDK download를 포함한 hosted runner 결과는 merge 후 CI run URL과 immutable run ID로 기록해야 한다.

## Acceptance 판정

자동 증거는 worker report와 root 통합 결과를 근거로 분류했다. AG02 UI는 아직 독립 검수 전이므로 관련 행을 `PARTIAL`로 유지했다.

현재 운영 blocker:

- `REQ-CTX-001`, `REQ-CTX-002`: survey project/section entity, 선택 저장, run 중 고정 없음
- `REQ-RUN-002`: device/project/section/pipeline/source/config/preflight/output identity의 영속 연결 미완료
- `REQ-STO-002`: 기대 file count/bytes/finish와 partial/missing output 계약 미완료
- 조직 IdP/직원 lifecycle과 영구 삭제 audit/policy 미완료
- required sensor source, storage 최소량, 정상 stop 결과, RTK threshold의 인간 PM 결정 미완료
- current phone/tablet/Orin NX/public receiver 실장치·운영 증거 없음

판정은 demo `NOT_RUN`, 내부 운영 release `BLOCKED`다.

## 로컬 검증

QA contract와 workflow syntax:

```text
python3 scripts/check_qa_acceptance.py
QA acceptance matrix covers 42 requirements exactly once.

empty requirements negative check: rejected as expected
Ruby Psych YAML parse: passed
git diff --check: passed
```

CI에서 사용할 root-working-directory Python invocation을 AG07 base에서 확인했다. worktree 내부 venv가 없어 저장소 main의 기존 backend venv를 실행 환경으로 재사용했고, 대상 source와 tests는 AG07 worktree의 `PYTHONPATH`/상대 경로를 사용했다.

```text
PYTHONPATH=backend:/usr/lib/python3/dist-packages \
  /home/jm/ControllerApp/JetsonControllerApp/backend/.venv/bin/python \
  -m unittest discover -s backend/tests -p 'test_*.py' -v
Ran 259 tests in 9.915s
OK

PYTHONPATH=upload_receiver \
  /home/jm/ControllerApp/JetsonControllerApp/backend/.venv/bin/python \
  -m unittest discover -s upload_receiver/tests -p 'test_*.py' -v
Ran 37 tests in 3.350s
OK
```

root가 통합 commit `03b2e93`에서 별도로 보고한 로컬 결과는 backend 275개/skip 0, receiver 37개/skip 0 통과다. 이는 `ROOT_REPORTED_LOCAL` evidence이며 remote CI 결과가 아니다. test 중 출력된 deployment JSON은 `/tmp` mock 대상이었고 실제 배포가 아니다.

AG07은 기능 코드나 Android resource를 변경하지 않았고 공유 Gradle lane을 사용하지 않았다. root merge-stage가 모든 branch 통합 후 full Android assemble/lint/JVM을 실행한다.

## 생성·변경 파일

- `.github/workflows/reliability.yml`
- `scripts/check_qa_acceptance.py`
- `docs/qa/README.md`
- `docs/qa/SUPPORT_MATRIX_KO.md`
- `docs/qa/ACCEPTANCE_MATRIX_KO.md`
- `docs/qa/EVIDENCE_TEMPLATE_KO.md`
- `docs/qa/RELEASE_CHECKLIST_KO.md`
- `docs/qa/ROLLBACK_DIAGNOSIS_KO.md`
- `docs/agent-runs/AGENT_07_EXECUTION_REPORT.md`

## 실제 한계

- Android 설치, `connectedDebugAndroidTest`, 화면 잠금, BLE/LAN/Wi-Fi Direct, camera/GNSS/RTK, Orin NX pipeline, 공개 HTTPS receiver, backup/rollback rehearsal을 실행하지 않았다.
- 실제 screenshot, device log, demo/field evidence를 만들지 않았다. template의 `NOT_RUN`은 실패가 아니라 미실행 기록이지만 release gate에서는 미충족이다.
- hosted GitHub Actions의 실제 runtime과 비용/시간은 확인하지 않았다. full suite로 확장했으므로 첫 remote run에서 timeout과 native package 설치를 확인해야 한다.
- AG02의 최종 UI commit/report와 Astra verdict가 나오면 root가 관련 acceptance 행의 code/automated evidence를 보정할 수 있다. device/demo/operational 열은 실제 evidence 없이 변경하면 안 된다.
- 배포, service restart, sensor operation, device install, data deletion은 수행하지 않았다.
