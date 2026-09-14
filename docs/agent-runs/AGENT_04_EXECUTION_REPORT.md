# AG04 Jetson 수집 Runtime 실행 보고서

## 구현 결과

- 새 외부 파이프라인 등록의 부팅 자동 시작 기본값을 `false`로 변경했다. 폴더 검색 응답도 `autostartDefault=false`를 반환하며, CLI에서 옵션을 생략하면 기존 unit enable 상태를 임의로 바꾸지 않는다.
- `start`, `stop`, `enable`, `disable`의 이미 충족된 요청은 `systemctl`을 다시 실행하지 않는다. 명령 실패나 timeout 뒤 목표 상태가 실제로 관찰되면 그 상태와 `OBSERVED_AFTER_COMMAND_ERROR`를 반환하며, `restart`는 중복 여부를 추측하지 않는다.
- 모든 파이프라인 상태 응답은 systemd에서 다시 읽은 실제 상태와 관찰 시각을 기준으로 한다. 실행 로그의 bounded header/footer에서 최신 실행 ID, 시작/종료 시각, 종료 코드, source revision, config SHA-256, 결과 경로와 저장 사전점검 근거를 읽는다.
- 실행 중 ID는 로그 header의 `INVOCATION_ID`가 현재 systemd `InvocationID`와 일치할 때만 `activeRunId`로 확정한다. 과거 미완료 로그나 재부팅 전 로그를 현재 실행으로 오인하지 않는다.
- runner는 등록된 writable path마다 실제 create/write/fsync/unlink를 수행하고 `statvfs` 가용량을 기록한 뒤 센서를 인계한다. 기본 최소 여유 공간은 임의 제한 없이 0 byte이며, 보호된 per-pipeline 환경의 `JETSON_PIPELINE_MIN_FREE_BYTES`로 명시 설정할 수 있다. 대상이 없으면 `not_configured`로 기록한다.
- 저장 사전점검 실패는 실행 로그에 근거를 남기고 exit 78로 끝나며 같은 부팅에서 자동 재시작하지 않는다. 외부 애플리케이션 자체의 exit 78은 로그에는 보존하되 runner가 systemd에 1을 반환하여 일반 runtime 복구를 유지한다.
- runner header 뒤에 빈 줄 경계를 두어 외부 프로그램 stdout이 `invocation_id`나 `storage_preflight`를 위조하지 못하게 했다. 로그 본문이 크기 제한에 도달해도 runner footer는 보존한다.
- 휴대전화 연결 수명과 pipeline systemd unit 수명 사이에 stop/restart 연동을 추가하지 않았다. 이미 실행 중인 수집은 전화 연결이 끊겨도 계속되며, 재연결 후 GET/명령 응답의 실제 systemd 상태와 실행 ID로 조정한다.

## API 추가 계약

기존 필드는 유지하고 pipeline item/control response에 다음 optional/additive 필드를 추가했다.

- `observedAt: String`
- `activeRunId: String?` (`<pipelineId>/<logId>`)
- `execution: Object?`: `runId`, `logId`, `active`, `startedAt`, `finishedAt`, `exitCode`, `sourceRevision`, `sourceDirty`, `release`, `configSha256`, `resultsDirectory`, `storageAvailableBytes`, `storageRequiredBytes`, `storagePreflight`, `failureKind`
- `failureKind: String?`; 명시적인 runner 근거가 있는 경우에만 `STORAGE_PREFLIGHT`
- control response의 `control`: `action`, `commandIssued`, `outcome` (`ALREADY_SATISFIED`, `COMMAND_COMPLETED`, `OBSERVED_AFTER_COMMAND_ERROR`)

`INTEGRATION_REQUEST`: AG03 Android network/model adapter는 위 필드를 optional로 수용하고 등록 기본값을 false로 맞춘다. AG02 UI는 `RUNNING`과 non-null `activeRunId`를 함께 관찰한 경우에만 시작을 확정하며, stop은 non-running state와 가능한 `finishedAt`/`exitCode` 근거를 사용한다. timeout/502 후 요청을 자동 재전송하지 않고 GET으로 조정한다. `storagePreflight=not_configured`는 저장 검증 성공으로 표시하지 않는다.

## 검증

작업 worktree의 `backend/`에서 다음 명령을 실행했다.

```text
/home/jm/ControllerApp/JetsonControllerApp/backend/.venv/bin/python -m unittest tests.test_pipelines tests.test_pipeline_runner tests.test_pipeline_registrar tests.test_pipeline_layout tests.test_systemd_runtime tests.test_api
Ran 86 tests in 7.263s
OK
```

전체 backend discovery 결과:

```text
/home/jm/ControllerApp/JetsonControllerApp/backend/.venv/bin/python -m unittest discover -s tests -p 'test_*.py'
Ran 255 tests in 11.432s
FAILED (errors=2, skipped=2)
```

251개 test는 통과했다. 두 collection error는 해당 실행의 venv 기본 `sys.path`에 `dbus` module이 없어 `test_ble_advertising`과 `test_ble_status` import가 실패한 것이며 AG04 변경 영역과 무관하다. 지침에 따라 동일 prerequisite를 재시도하지 않았다. 이후 root가 환경 변경 없이 기존 `/usr/lib/python3/dist-packages`를 `sys.path`에 추가하면 `dbus`와 `gi` import가 성공함을 확인했으며 통합 suite에서 이 bootstrap을 사용할 예정이다. `git diff --check`와 변경 Python 파일 `py_compile`도 통과했다.

## 장치 검증 공백

Jetson 장치 배포, service restart, 실제 카메라/GNSS 수집, 실제 filesystem 용량 부족, 전화 연결 해제/재연결은 수행하지 않았다. 실제 systemd `InvocationID` 연계와 저장 장치 fsync 오류는 대상 Jetson 통합 검증이 필요하다. main 또는 통합 branch에는 병합하지 않았다.
