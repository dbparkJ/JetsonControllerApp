# Python 작업 폴더·시간 동기화·FAN 제어 규칙

이 문서는 Jetson Controller에 Python 작업을 폴더 하나로 등록할 때 지켜야 할
규칙과, 작업 실행 전에 필요한 모바일 시간 동기화 및 FAN 제어 동작을 설명한다.

## 1. 작업 폴더 규칙

작업 폴더 이름은 내부 작업 ID가 된다. 영문 소문자, 숫자, 점(`.`),
하이픈(`-`)만 사용하고 64자 이내로 만든다. 공백, 한글, 밑줄, 대문자는 폴더
이름에 사용할 수 없다. 앱에 표시할 **작업 이름**에는 한글을 사용할 수 있다.
장치 안에서 작업 폴더 이름은 서로 달라야 한다. 같은 이름의 다른 폴더를 등록하면
동일한 내부 작업의 새 스냅샷으로 취급된다.

```text
camera-capture/             # 내부 작업 ID: camera-capture
├── .venv/
│   └── bin/python          # 실행 가능한 Python 인터프리터
├── main.py                 # 고정 실행 진입점
├── config.yaml             # config.yml도 가능하지만 둘 중 하나만 존재해야 함
└── results/                # 결과 폴더; 없으면 등록할 때 자동 생성
```

추가 요구사항은 다음과 같다.

- 작업 폴더는 Git 작업 트리 안에 있어야 한다.
- `main.py`, YAML 설정, 작업에서 import하는 소스는 Git에 추적되거나
  `.gitignore`에 의해 제외되지 않은 파일이어야 한다.
- `.venv/`와 `results/`는 보통 `.gitignore`에 넣는다. 가상환경은 스냅샷에
  복사하지 않고 그 위치의 인터프리터를 그대로 사용한다. 폴더 등록기는 안전을
  위해 `.venv/`, `logs/`, `results/`를 소스 스냅샷에서 항상 제외한다.
- `main.py`는 `--config <절대경로>` 인자를 받아야 한다.
- YAML 파일과 `main.py`는 심볼릭 링크가 아닌 일반 파일이어야 한다.
- 결과는 `results/`에 저장한다. 실행 시
  `JETSON_PIPELINE_RESULTS_DIR` 환경변수에도 같은 절대경로가 제공된다.
- 로그 파일을 작업 코드에서 따로 만들 필요가 없다. 표준 출력과 표준 오류가
  `/var/log/jetson-pipelines/<작업-ID>/run-*.log`와 journald에 함께 기록된다.

실행 환경에는 다음 값도 제공된다.

| 환경변수 | 의미 |
|---|---|
| `JETSON_PIPELINE_ID` | 내부 작업 ID |
| `JETSON_PIPELINE_RELEASE` | 실행 중인 읽기 전용 소스 스냅샷 |
| `JETSON_PIPELINE_CONFIG` | 실행 중인 YAML 설정 절대경로 |
| `JETSON_PIPELINE_RESULTS_DIR` | 결과 폴더 절대경로 |
| `JETSON_PIPELINE_LOGS_DIR` | 해당 실행의 관리 로그 폴더 |

## 2. 폴더만으로 등록하기

다음 명령은 `.venv`, `main.py`, YAML, `results/`를 자동으로 찾아 등록한다.
`--name`만 사용자가 보는 이름이며 내부 ID는 폴더 이름에서 결정된다.

```bash
sudo /opt/jetson-control/register-pipeline.py \
  --folder /home/jm/jobs/camera-capture \
  --name "카메라 수집" \
  --user jm \
  --autostart
```

`--folder` 방식은 `--autostart`와 `--no-autostart`를 모두 생략해도 자동 실행이
기본값이다. 등록기는 기존과 동일하게 Git 파일만 `/opt/jetson-pipelines` 아래에
스냅샷으로 복사하고, `jetson-pipeline@camera-capture.service`를 부팅 자동 실행에
등록한다. 기존의 `--repo`, `--venv`, `--entry` 등 상세 옵션 방식도 계속 사용할
수 있다.

## 3. 모바일 시스템 시간 동기화

부팅할 때마다 `/run`이 초기화되므로 모든 Python 작업은 인증된 모바일 시간
동기화가 성공할 때까지 대기한다. 시간 설정이 성공하고 실제 장치 시간을 다시
검증한 뒤에만 root 소유 마커
`/run/jetson-control/time-synchronized.json`이 생성된다.

- 모바일은 Unix epoch 밀리초를 보낸다. 허용 범위는 2020-01-01 이상,
  2100-01-01 미만이다.
- 같은 부팅에서 이미 동기화한 뒤에는 5분을 넘는 재보정을 거부한다. 큰 오입력이나
  시간 롤백으로 이미 실행 중인 데이터의 시각 순서가 깨지는 것을 막기 위한 제한이다.
- `date` 명령은 셸 없이 고정 인자 배열로 실행한다.
- 시간 설정 또는 사후 검증에 실패하면 마커를 만들지 않으므로 작업은 시작되지 않는다.
- 대기 중인 작업 상태는 `WAITING_FOR_TIME_SYNC`로 노출된다.

## 4. FAN 제어

Jetson의 NVIDIA `nvfancontrol.service`와 알려진 PWM sysfs 경로를 함께 사용한다.

- `AUTO`: `nvfancontrol.service`를 다시 시작해 NVIDIA 온도 정책에 제어를 돌려준다.
- `MANUAL`: 자동 데몬을 멈춘 뒤 20~100% 범위의 PWM만 허용한다. 20% 미만은
  과열 위험 때문에 거부한다.
- PWM 쓰기 또는 확인에 실패하면 기존에 실행 중이던 자동 데몬을 즉시 다시 시작한다.
- API 서비스는 알려진 PWM FAN sysfs 경로만 쓰기 가능하며, 임의 경로나 셸 명령을
  받지 않는다.
- 재부팅 후에는 NVIDIA 자동 제어가 기본이며 수동 설정은 영구 저장하지 않는다.

응답에는 사용 가능 여부, `AUTO`/`MANUAL` 모드, 현재 비율, PWM 원시값,
최대 PWM, 지원 장치의 RPM, 자동 제어 가능 여부가 포함된다.

구형 Xavier 계열의 `target_pwm`(0~255)과 별도 tachometer `rpm` 경로는
[NVIDIA Jetson Linux 전원 관리 문서](https://docs.nvidia.com/jetson/l4t/Tegra%20Linux%20Driver%20Package%20Development%20Guide/power_management_jetson_xavier.html)의
FAN 제어 규칙을 따른다. JetPack 6 계열에서는 `nvfancontrol`과 PWM hwmon 경로를
우선 사용한다.

## 5. API 연결 계약

백엔드 API 라우터에서 다음 계약으로 연결한다. 모든 경로는 기존 `/v1` 인증과
응답 서명을 그대로 적용해야 한다.

### 폴더 검사

`POST /v1/pipelines/discover-folder`

```json
{"rootId":"workspace","path":"jobs/camera-capture"}
```

선택한 루트와 상대경로를 기존 안전한 파일시스템 resolver로 해석한 뒤
`PipelineManager.discover_folder(resolved_path)`를 호출한다.

### 폴더 등록

`POST /v1/pipelines/register-folder`

```json
{
  "rootId":"workspace",
  "path":"jobs/camera-capture",
  "name":"카메라 수집",
  "autostart":true
}
```

`PipelineManager.register_folder(label=name, repository=resolved_path,
autostart=autostart)`를 호출한다. 폴더 오류는 400, 실행 중 재등록 충돌은 409,
등록기/systemd 실패는 502로 응답한다.

### 시간 상태·동기화

- `GET /v1/system/time` → `SystemTimeSynchronizer.status()`
- `PUT /v1/system/time` + `{"mobileTimeEpochMillis":1777000123456}` →
  `SystemTimeSynchronizer.synchronize(...)`

입력 오류는 400, 같은 부팅의 큰 재보정 충돌은 409, 장치 시간 설정 실패는 502로
응답한다. `SystemTimeSynchronizer`를 만들 때는 `on_clock_changed`에 인증기의
thread-safe nonce 기록 시각 재기준화 함수를 전달해야 한다. 특히 시간을 뒤로
보정하면 기존 nonce의 기록 시각이 미래가 될 수 있기 때문이다. 이때 nonce를
삭제하면 현재 요청을 재전송할 수 있게 되므로, nonce 키는 모두 보존하고 저장된
시각만 새 장치 시각으로 바꾼다. 동기화 응답을 보낸 직후에는 서버 시간이 바뀌므로
앱은 `/v1/hello`를 다시 읽어 인증용 서버 시각 오프셋을 갱신한다.

### FAN 상태·제어

- `GET /v1/system/fan` → `FanController.status()`
- `PUT /v1/system/fan` + `{"mode":"AUTO"}`
- `PUT /v1/system/fan` + `{"mode":"MANUAL","percent":40}`

`FanController.set(mode, percent)`를 호출한다. 입력 오류는 400, 지원 장치나 자동
제어기가 없으면 409, sysfs/systemd 제어 실패는 502로 응답한다.
