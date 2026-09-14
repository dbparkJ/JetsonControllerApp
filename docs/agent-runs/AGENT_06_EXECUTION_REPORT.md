# AG06 GNSS/RTK·현장 품질 실행 보고서

## 구현 결과

- Jetson 실행기 내부의 `RouteRecorder`가 휴대전화 연결과 독립적으로 실행 로그 옆에 품질 관찰 근거 `.quality.jsonl`과 compact summary `.quality.json`을 기록한다. 원시 근거와 summary는 각각 8 MiB와 256 KiB로 제한하며, 저장·센서 값 오류는 pipeline 수집을 중단하지 않는다.
- 각 관찰은 recorder의 2초 cadence를 coverage window로 명시한다. 다음 관찰까지 더 긴 구간은 이전 FIX 상태를 연장하지 않고 `CLOCK_GAP`과 unknown duration으로 기록한다. 시계 역행 뒤에는 high-watermark 이전 시간을 다시 합산하지 않는다.
- RTK FIX 비율은 전체 recorder 가동 시간이 아니라 실제로 active GNSS sample과 판독 가능한 RTK category가 있었던 `rtkObservedDurationMillis`를 분모로 사용한다. GNSS가 없거나 stale/lost이면 `NO_SAMPLES`, GNSS sample은 있으나 RTK category가 해석되지 않으면 `UNKNOWN`이며 ratio와 FIX duration은 `null`이다. 관찰된 non-FIX는 0 비율로 계산할 수 있지만 합격/실패 판정과 임계값은 없다.
- GNSS 및 camera/IMU 상태를 `ACTIVE`, `STALE`, `LOST`, `ERROR`, `NOT_CONFIGURED`, `UNKNOWN`으로 보존한다. 필수/선택 여부는 명시 입력의 `REQUIRED`, `OPTIONAL`만 사용하며 기본값은 `UNSPECIFIED`다. `configured=true`를 필수로 추론하지 않는다.
- stale, loss, sensor error, unknown, required-but-not-configured, RTK non-FIX, source/recorder clock gap을 같은 run의 problem interval로 직렬화한다. route 시간 범위와 겹치지 않는 interval에는 지도 index를 만들지 않는다.
- 기존 `/v1/task-runs` item과 `/v1/task-runs/{pipelineId}/{logId}/route`에 nullable `quality`를 additive로 제공한다. route point에는 nullable `fixState`, `sensorState`를 추가했다. 과거 log/route는 그대로 파싱되고 quality sidecar가 없으면 `quality=null`이다.
- 작업 이력 삭제는 해당 run의 route와 두 quality sidecar도 함께 제거하지만 수집 원본은 유지한다. runner log retention이 log를 먼저 제거한 orphan quality sidecar는 다음 recorder 시작 시 정규 파일만 정리한다.
- Android에는 nullable wire model과 threshold-free interpreter를 추가했다. `NOT_RECORDED`, `NO_SAMPLES`, `INSUFFICIENT_TIMING`, `OBSERVED`를 구분하며 ratio 유효성 및 route interval index만 방어적으로 검사한다.

## API 추가 계약

`TaskRun.quality`와 `TaskRoute.quality`는 nullable `RunQuality`다. 핵심 필드는 다음과 같다.

- 시간 근거: `firstObservedAtEpochMillis`, `lastObservedAtEpochMillis`, `elapsedDurationMillis`, `observedDurationMillis`, `unknownDurationMillis`
- RTK 근거: `rtkObservedDurationMillis`, `rtkUnknownDurationMillis`, `rtkFixDurationMillis`, `rtkFixRatio`
- 상태: `sampleState`, `observationCount`, `truncated`
- 구간: `problemIntervals[]`의 `kind`, `sensor`, `requirement`, 시작/종료/지속 시간과 nullable route point index
- 센서: `sensors[]`의 sensor/requirement/sampleState와 active/stale/lost/unknown/not-configured duration 및 problem count

`rtkFixRatio`의 분모는 `rtkObservedDurationMillis`다. `observedDurationMillis`는 recorder coverage이므로 FIX 분모로 사용하지 않는다. 합격/실패 boolean이나 점수는 제공하지 않는다.

## 검증

작업 worktree의 `backend/`에서 다음 targeted suite를 실행했다.

```text
/home/jm/ControllerApp/JetsonControllerApp/backend/.venv/bin/python -m unittest tests.test_field_quality tests.test_field_tools tests.test_mobile_rtk tests.test_api
Ran 64 tests
OK
```

검증 범위는 NMEA category 정규화, fractional category 거절, 명시 센서 정책, missing/stale/future/backward sample, `0→10→5→8→12` clock reversal, timing-weighted FIX/unknown 시간, problem interval과 route index, invalid sensor data 이후 수집 지속, Jetson sidecar persistence, history/route API serialization, legacy null parsing과 삭제 안전성이다.

Android targeted JVM 검증:

```text
./gradlew :app:testDebugUnitTest --tests '*FieldQualityInterpreterTest' --tests '*ApiCompatibilityTest' --max-workers=1 --console=plain
BUILD SUCCESSFUL
```

`git diff --check`와 변경 Python module `py_compile`도 통과했다.

## 통합 및 장치 검증 공백

AG02에 nullable quality wire 계약과 RTK 분모 정정을 전달했다. 인간 PM이 pipeline별 필수 센서 또는 RTK 합격 임계값을 승인하기 전에는 `UNSPECIFIED`와 관찰 경고만 사용해야 한다.

`FieldQualitySampler`와 `RouteRecorder` 생성자는 명시적인 sensor requirement 입력을 지원하지만 현재 pipeline runner에는 승인된 required-sensor policy source가 없다. 따라서 실제 runtime 호출은 모든 센서를 `UNSPECIFIED`로 기록하며 시작 preflight를 차단하지 않는다. 향후 인간 PM이 정책 source를 승인하면 runner가 그 명시 값을 전달하는 별도 통합이 필요하다.

Jetson 배포, service restart, 실제 GNSS/RTK·camera·IMU 수집, 장시간 8 MiB 도달, 휴대전화 연결 해제 상태의 실장치 실행은 수행하지 않았다. 자동 test는 실제 장치/현장 합격을 대체하지 않는다. main 또는 통합 branch에는 병합하지 않았다.
