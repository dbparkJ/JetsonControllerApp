# AG03 Android 연결·복구 실행 보고서

## 구현 결과

- 등록 장비 자동 대상 정책은 등록 장비가 한 대일 때만 암시적으로 선택하고, 여러 대이면 저장된 명시 선택과 정확히 일치하는 장비만 사용한다. 장비 전환 시 이전 transport 요청과 Wi-Fi Direct 복구를 폐기하고 이전 장비 소유 RTK relay만 정리한다.
- 기존 LAN 우선, 인프라 Wi-Fi가 없을 때 Wi-Fi Direct fallback, 사용자가 직접 선택한 Wi-Fi Direct 유지 정책을 보존했다. 화면 종료로 discovery만 중지할 때 연결 의도를 지우지 않으며, 일시 API 단절은 물리 Wi-Fi Direct group 손실로 처리하지 않는다.
- 늦은 이전 session 응답은 endpoint/session generation 검증으로 폐기한다. start/stop 응답이 유실되면 mutation을 재전송하지 않고 GET pipeline 상태만 조회하며, 원래 결과는 `RESULT_UNKNOWN`으로 유지한다. 중복 start/stop 응답의 `ALREADY_SATISFIED`와 `commandIssued=false`도 Android 모델에서 수용한다.
- start/restart 결과 미확인 뒤 RTK relay는 동일 pipeline이 `STOPPED` 또는 `FAILED`로 확인된 경우에만 종료한다. 상태 재조회 실패, 다른 pipeline 관찰, 진행 중 상태는 수집 종료 근거로 사용하지 않는다. 명시적인 명령 실패와 확인된 terminal 상태의 기존 cleanup은 유지했다.
- RTK relay 소유권을 `LocalApiClient` session에 묶었다. 이전 client의 늦은 cleanup이나 heartbeat가 새 client relay를 변경할 수 없고, 같은 pipeline ID라도 client가 다르면 기존 server/heartbeat/upstream 구성을 재사용하지 않는다. relay resource와 foreground service 시작/종료는 같은 ownership mutex 구간에서 처리한다.
- AG04 runtime 응답을 위한 optional Android adapter를 추가했다. `ManagedPipeline`은 nullable `observedAt`, `activeRunId`, `execution`, `failureKind`, `control`을 수용하고, execution/storage/control evidence 필드를 모두 additive default로 제공한다. `PipelineFolderDiscovery.autostartDefault`는 `false`다.

## 계약 전달

AG02에 다음 모델 계약을 선행 전달했고 adapter commit `e1d9a8e6a46a63ddaaeba2728bd4eaf17369a8fd`를 제공했다.

- `ManagedPipeline.observedAt: String?`
- `ManagedPipeline.activeRunId: String?`
- `ManagedPipeline.execution: PipelineExecution?`
- `ManagedPipeline.failureKind: String?`
- `ManagedPipeline.control: PipelineControl?`
- `PipelineExecution`: run/log identity, active/started/finished/exit, source/release/config, results/storage evidence, failure kind
- `PipelineControl`: action, commandIssued, outcome

AG02 확인 규칙은 fresh Jetson 조회에서 `state == RUNNING`과 non-null `activeRunId`가 함께 관찰될 때만 현재 실행 시작을 확정하는 것이다. 연결 단절 중 cached pipeline 상태는 현재 상태 근거가 아니다.

## 검증

다음 focused JVM suite를 실행했다. production Kotlin compile과 unit-test compile이 함께 수행됐다.

```text
./gradlew :app:testDebugUnitTest \
  --tests '*JetsonRepositoryStabilityTest' \
  --tests '*LocalApiClientReplayTest' \
  --tests '*AutomaticConnectionPolicyTest' \
  --tests '*TransportCoordinatorTest' \
  --tests '*MobileRtkRelayManagerTest' \
  --tests '*ApiCompatibilityTest' \
  --max-workers=1 --console=plain

70 tests completed
BUILD SUCCESSFUL in 24s
```

이 suite는 다음을 직접 검증한다.

- 복수 등록 장비의 암시 선택 금지와 명시 선택, target switch의 stale response 폐기
- LAN/Wi-Fi Direct 우선순위, 수동 Direct 의도 유지, 일시 API 실패와 물리 group loss 구분
- 동일 group API 복구, 유한 retry budget, 명시 disconnect/cancel
- 실제 Retrofit/OkHttp/TLS/HMAC test server에서 start/stop 유실 후 GET 재조회, mutation 1회, late response 폐기, 중복 명령의 backend mutation 1회
- 결과 미확인 start의 relay 보존과 verified terminal/definite failure cleanup
- old/new client relay ownership, 늦은 cleanup 격리, 다른 client의 같은 pipeline 재사용 금지
- 신규 runtime JSON과 legacy pipeline JSON 역직렬화, autostart 기본값 false

`git diff --check`도 통과했다. 최종 통합 assemble/lint/full JVM 검증은 root merge-stage에서 수행한다.

## 실제 한계

- Android 화면 잠금/백그라운드, 실제 Wi-Fi Direct group, 인프라 LAN 전환, BLE fallback은 JVM의 repository/manager callback seam에서 검증했다. 실제 휴대전화 OS의 radio/lifecycle 동작은 연결 장치 시험이 필요하다.
- Jetson 배포, service restart, 실제 pipeline 시작/종료, 카메라·GNSS 수집, 셀룰러 NTRIP relay는 수행하지 않았다. 승인된 장치 target이 없으므로 device operation은 하지 않았다.
- 연결 단절은 pipeline 종료로 기록하지 않는다. 앱은 재연결 후 Jetson의 pipeline/execution evidence를 다시 조회해야 하며, 조회 전 상태는 미확인이다.
- client 교체 시 이전 Jetson의 relay registration 해제 요청이 실패할 수 있다. 휴대전화 server/heartbeat는 즉시 소유권에서 제거되고 backend registration은 기존 lease 만료 안전장치에 의존할 수 있다.
