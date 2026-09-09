# 파이프라인 배포·운영

Controller 백엔드와 Python 수집 작업을 여러 Jetson에 배포하는 절차입니다. 작업의 실행 규칙, 소스 스냅샷, 센서 모니터, 시간 동기화와 FAN 제어를 함께 다룹니다.

## 새 Jetson 자동 설치

ControllerApp 저장소, DepthAI Git 작업 트리, 해당 장비에서 생성한 virtualenv를 새
장비에 준비한 뒤 한 번 실행한다. `<user>`는 카메라, GPS, IMU 장치에 접근할 Linux
사용자다. DepthAI 저장소는 사용하는 원격 저장소에서 `/home/<user>/geo_multifusion_sensors`로
clone한다. virtualenv는 장비와 Python ABI에 종속되므로 다른 Jetson에서 그대로
복사하지 않고 각 장비의 repository root에 `.venv`로 생성한다.

```bash
sudo backend/scripts/bootstrap-jetson.sh \
  --device-name MMS-JETSON-02 \
  --pipeline-user <user> \
  --enable-power \
  --depthai-repo /home/<user>/geo_multifusion_sensors \
  --depthai-venv /home/<user>/geo_multifusion_sensors/.venv
```

이 명령은 다음 작업을 idempotent하게 수행한다.

1. backend와 BlueZ build/runtime package 설치
2. BlueZ 5.55 binary 검증 또는 공식 source build
3. 새 장비 UUID/secret과 QR 생성; 기존 identity는 보존
4. BLE GATT와 pinned HTTPS API 설치 및 부팅 자동 시작
5. Wi-Fi Direct 검색 service와 요청 시 NetworkManager Group Owner/DHCP 자동 구성
6. storage root와 전원 명령 구성
7. DepthAI working tree의 실행 snapshot 생성
8. `jetson-sensor-monitor.service` 부팅 자동 시작 활성화와 실제 수집 pipeline 수동 시작 등록

등록 직후 전용 센서 모니터가 저대역폭으로 카메라·GNSS·IMU를 열어 앱 상태와
프리뷰를 게시한다. 모니터는 dataset을 만들거나 수집 파일을 쓰지 않는다. 실제 수집
pipeline은 앱이나 운영 명령으로 시작한 뒤 인증된 모바일 시간 동기화를 기다린다. 설치
session에서 바로 수집을 시작하려면 마지막에 `--start-depthai-now`를 추가한다. 수집이
시작되면 모니터가 장치를 정상 종료하고 넘겨주며, 수집이 끝나면 모니터가 자동으로 다시
장치를 연다. 수집 서비스는 부팅 자동 실행하지 않으므로 장치 오류가 반복되어도 상시
센서 상태가 주기적으로 끊기지 않는다.

BlueZ 5.55 binary가 이미 있으면 `/usr/local/libexec/bluetooth/bluetoothd-5.55`를 검증해 재사용한다. 별도 빌드 binary를 배포할 때는 다음 옵션을 쓴다.

```bash
sudo backend/scripts/bootstrap-jetson.sh \
  --bluez-binary /path/to/bluez-5.55/src/bluetoothd \
  ...
```

package와 BlueZ를 장비 이미지에서 이미 관리한다면 각각 `--skip-packages`, `--skip-bluez`를 사용할 수 있다. 건너뛰기 전 `bluetoothd-5.55 -v`, systemd ExecStart, BLE 광고를 별도로 확인한다. Wi-Fi Direct는 기본 활성화되며 adapter가 P2P-GO를 지원하지 않는 장비만 `--disable-wifi-direct`를 사용한다. 자세한 검증은 [WIFI_DIRECT.md](WIFI_DIRECT.md)에 있다.

## QR와 BLE 재등록

신규 장비의 QR은 다음 root 전용 경로에 생성된다.

```text
/var/lib/jetson-control/jetson-pairing-uri.txt
/var/lib/jetson-control/jetson-pairing-qr.png
```

화면이 연결된 장비에서는 승인된 운영 절차로 QR 이미지를 열고 앱의 `QR 스캔`을 사용한다. QR에는 장비 secret이 포함되므로 메신저나 일반 파일 서버에 올리지 않는다. 설치를 다시 실행해도 `/etc/jetson-control/device.json`을 보존하므로 기존 앱 등록은 유지된다.

QR 등록 뒤 앱은 BLE challenge-response로 장비를 인증한다. Wi-Fi 비밀번호는 challenge에서 파생한 AES-256-GCM key로 암호화되어 GATT로 전달된다. LAN API 연결에서는 같은 QR secret으로 TLS 인증서 proof와 HTTP HMAC을 검증한다.

## Pipeline 작성 계약

자동 관리 대상은 pipeline 안에서 실제 수집을 시작하고 종료 signal을 처리하는 하나의
메인 Python 파일이다. 보조 script와 library를 각각 서비스로 등록할 필요는 없다.
현재 DepthAI pipeline의 표준 메인은 repository root의 `main.py`다.

새 pipeline은 레포 root에 `config.yaml`을 두고, 메인 Python을 인자 없이 실행해도 같은 레포의 `config.yaml`을 기본으로 읽도록 구성하는 것을 표준으로 한다.

```python
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent
parser.add_argument(
    "--config",
    type=Path,
    default=REPO_ROOT / "config.yaml",
)
```

상대 current working directory에 의존해 `Path("config.yaml")`만 사용하지 않는다. systemd, test, 개발 shell의 working directory가 달라도 같은 설정을 읽어야 하기 때문이다. 메인 파일은 `SIGINT`와 `SIGTERM`에서 장치와 파일 writer를 정상 종료해야 한다.

관리자는 실행 시 항상 snapshot 안의 config 절대 경로를 다음처럼 넘긴다.

```text
<venv>/bin/python -u <release>/<main.py> --config <release>/<config.yaml>
```

현재 DepthAI 저장소는 root `.venv`, `main.py`, `config.yaml` 계약을 따른다. preset은
YAML의 상대 출력 경로에 의존하지 않고 `--output-dir /data/collections`와
`--controller-bridge-dir /var/lib/jetson-sensors`를 `main.py`의 명시적 CLI 인수로
넘긴다. systemd 작업 디렉터리는 source repository root이며 위 두 절대경로만
`ReadWritePaths`에 추가된다. 따라서 YAML에 남아 있는 상대경로가 sandbox 밖의 홈
디렉터리로 쓰기 경로를 바꾸지 못한다.

DepthAI preset은 같은 등록 snapshot을 `--monitor-only`로 실행하는 부팅 센서 모니터도
설정한다. 이 모드는 센서에 맞는 최대 RGB 캡처 크기(최대 1920 px 폭),
최대 5 FPS, depth 비활성을 사용한다. USB2 급 연결은 MJPEG 장치 전송으로
대역폭을 제한하며, 실제 수집은 기존 USB3 검사를 유지한다. GNSS와 외부 IMU의
기본 장치는 번호가 바뀌는 `/dev/ttyACM*` 또는 `/dev/ttyUSB*`가 아니라
`/dev/serial/by-id`의 장치 식별자로 선택한다. Android 휴대폰은 GNSS 후보에서
제외한다.

## 작업 폴더 규칙

작업 폴더 이름은 내부 작업 ID가 된다. 영문 소문자, 숫자, 점(`.`),
밑줄(`_`), 하이픈(`-`)만 사용하고 64자 이내로 만든다. 첫 글자는 영문 소문자나
숫자여야 하며 공백, 한글, 대문자는 폴더 이름에 사용할 수 없다. 앱에 표시할
**작업 이름**에는 한글을 사용할 수 있다.
장치 안에서 작업 폴더 이름은 서로 달라야 한다. 같은 이름의 다른 폴더를 등록하면
동일한 내부 작업의 새 스냅샷으로 취급된다.

```text
26_camera_record/           # 내부 작업 ID: 26_camera_record
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
  앱 API로 등록하면 수집 storage root의 `<pipeline-id>/`를 결과 경로로 사용하므로
  작업 코드는 이 환경변수를 우선 사용한다.
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

## 폴더만으로 등록하기

다음 명령은 `.venv`, `main.py`, YAML, `results/`를 자동으로 찾아 등록한다.
`--name`만 사용자가 보는 이름이며 내부 ID는 폴더 이름에서 결정된다.

```bash
sudo /opt/jetson-control/register-pipeline.py \
  --folder /home/<user>/26_camera_record \
  --name "카메라 수집" \
  --user <user> \
  --autostart
```

`--folder` 방식은 `--autostart`와 `--no-autostart`를 모두 생략해도 자동 실행이
기본값이다. 등록기는 기존과 동일하게 Git 파일만 `/opt/jetson-pipelines` 아래에
스냅샷으로 복사하고, `jetson-pipeline@26_camera_record.service`를 부팅 자동 실행에
등록한다. 기존의 `--repo`, `--venv`, `--entry` 등 상세 옵션 방식도 계속 사용할
수 있다.

## DepthAI 작업 등록

backend 설치 후 preset script를 실행한다.

```bash
sudo /opt/jetson-control/install-depthai-pipeline.sh
```

다른 경로라면 명시한다.

```bash
sudo /opt/jetson-control/install-depthai-pipeline.sh \
  --repo /home/<user>/geo_multifusion_sensors \
  --venv /home/<user>/geo_multifusion_sensors/.venv
```

이 preset은 같은 `source_repo`로 등록된 작업이 하나 있으면 그 ID를 재사용하고,
없으면 `geo_multifusion_sensors`를 사용한다. 두 ID가 같은 저장소를 가리키면 잘못된
작업을 선택하지 않도록 설치를 중단한다. 선택된 수집 unit은 disable 상태로
등록되고 부팅 센서 모니터만 enable된다. 수집 중이면 먼저 정상 종료한 뒤
preset을 다시 실행한다. 녹화는 앱의 시작 버튼 또는 `systemctl start`로 명시적으로
시작한다.

generic pipeline은 다음처럼 등록한다.

```bash
sudo /opt/jetson-control/register-pipeline.sh \
  --id capture-main \
  --label "Capture Main" \
  --repo /home/<user>/path/to/repo \
  --venv /home/<user>/path/to/.venv \
  --entry main.py \
  --config config.yaml \
  --working-dir /home/<user>/path/to/repo \
  --write-path /home/<user>/path/to/repo/records \
  --user <user> \
  --autostart
```

`--autostart`는 다음 부팅을 활성화하고 현재 process는 시작하지 않는다. 즉시 실행은 `--start-now`, 실행 중인 작업을 새 snapshot으로 바꿀 때는 `--restart-running`을 추가한다.

## Source snapshot과 버전 이력

등록기는 선택한 source 디렉터리에서 `git ls-files --cached --others --exclude-standard -- .` 결과만 복사한다. source가 Git worktree의 하위 프로젝트여도 선택 디렉터리 밖의 파일은 포함하지 않는다. 따라서 현재 commit의 tracked 파일뿐 아니라 ignore되지 않은 미커밋 새 파일도 정확히 snapshot에 포함되며, 원본 `.git`, ignored image dataset, cache, 기존 venv는 복사하지 않는다.

```text
/opt/jetson-pipelines/depthai-capture/
  pipeline.json
  current -> releases/<timestamp>-<commit>[-dirty]
  releases/
    <timestamp>-<commit>[-dirty]/
```

manifest에는 source repo, branch, commit, dirty 여부, Python/venv, entrypoint, config, 생성 시각을 기록한다. 앱의 작업 목록에서도 branch, dirty 여부, Python 버전을 확인할 수 있다.

source 변경을 배포할 때는 pipeline을 중지한 뒤 같은 ID로 다시 등록하거나 CLI에서 `--restart-running`을 쓴다. 이전 release는 그대로 남는다. 등록 해제는 실행 release를 삭제하지 않고 `/opt/jetson-pipelines/.archive/`로 이동한다.

## 변경분 시스템 반영

Controller backend 코드, systemd unit, helper script가 바뀌면 Git 작업 트리를 최신 commit으로 맞춘 뒤 설치 script를 다시 실행한다. `install.sh`는 `/opt/jetson-control/jetson_control`을 새 복사본으로 교체하고, systemd unit과 실행 script를 덮어쓰며, 기존 `/etc/jetson-control/device.json`, QR identity, storage/upload 설정은 보존한다.

```bash
git pull --ff-only
sudo backend/scripts/install.sh \
  --pipeline-user <user> \
  --enable-power
sudo systemctl restart jetson-control.service jetson-control-api.service
sudo systemctl restart jetson-wifi-direct.service
sudo /opt/jetson-control/doctor.sh
```

Python pipeline source가 바뀐 경우에는 backend 설치만으로 실행 snapshot이 바뀌지 않는다. 앱의 `자동 실행 작업`에서 해당 작업을 다시 등록하거나 CLI에서 같은 ID로 `--restart-running`을 사용해 새 release를 만든다.

```bash
sudo /opt/jetson-control/register-pipeline.sh \
  --id depthai-capture \
  --label "DepthAI Capture" \
  --repo /home/<user>/26_camera_record \
  --venv /home/<user>/26_camera_record/.venv \
  --entry main.py \
  --config config.yaml \
  --working-dir /home/<user>/26_camera_record \
  --write-path /data/collections \
  --write-path /var/lib/jetson-sensors \
  --argument=--output-dir \
  --argument /data/collections \
  --argument=--controller-bridge-dir \
  --argument /var/lib/jetson-sensors \
  --user <user> \
  --no-autostart \
  --restart-running
```

명시적 `--output-dir`이 YAML 기본값보다 우선하므로 수집물은
`/data/collections` 아래에 기록된다. `--argument=--output-dir`처럼 option 이름은
등호 형식으로 등록해야 registrar가 다음 registrar option으로 오해하지 않는다.
기존 `~/26_camera_record`의 수집물은 backend 재설치 후 앱에
`Previous collected data` root로 계속 노출된다.

운영 순서는 `Git 최신화 -> backend install 재실행 -> 필요한 pipeline 재등록 -> doctor와 앱 연결 확인`으로 고정한다. 설정 파일을 직접 복사해 덮어쓰지 말고, 설치 script와 등록기를 통해 원자적으로 반영한다.

## 앱에서 작업 추가

Jetson에 LAN 또는 Wi-Fi Direct로 연결한 뒤 `대시보드 > 자동 실행 작업 > 작업 추가`로 이동한다. 앱에서 다음 순서로 선택한다.

1. 표시 이름 입력
2. 표준 작업 폴더 선택
3. 부팅 자동 실행 toggle 확인

앱의 실행 소스 선택기는 `pipeline_user`의 `~/` 아래만 탐색한다. 폴더 이름은 소문자나
숫자로 시작하고 소문자, 숫자, 점, 밑줄, 하이픈만 사용할 수 있으므로
`26_camera_record`도 유효한 작업 ID다. 선택한 폴더 root에는 `.venv/bin/python`,
`main.py`, `config.yaml` 또는 `config.yml` 중 하나가 있어야 한다. backend는 Git root,
venv Python, Python syntax, config 파일 유형을 다시 검사한다. API로 등록한 작업의
출력은 수집 storage root의 `<pipeline-id>/`에 저장되며 `JETSON_PIPELINE_RESULTS_DIR`로 전달된다. 임의 shell command는 앱에서 등록할 수 없다. `/data/collections`와
Controller sensor bridge가 필요한 현재 DepthAI 운영 preset은 앞 절의
`install-depthai-pipeline.sh`로 명시적 등록한다.

등록된 작업 카드에서는 실시간 실행 로그, 현재 release의 YAML 설정, 첫 출력 폴더를 각각 별도 화면으로 연다. 로그는 자동 재시작마다 `/var/log/jetson-pipelines/<id>/run-*.log`로 분리되어 앱에서 이전 실행까지 선택할 수 있다. YAML 저장은 실행 snapshot에 원자 반영되며 작업 재시작 후 적용된다. 소스 레포를 다시 등록하면 새 snapshot의 YAML이 기준이 된다.

## 모바일 시스템 시간 동기화

부팅할 때마다 `/run`이 초기화되므로 실제 수집을 실행하는 모든 Python 작업은 인증된
모바일 시간 동기화가 성공할 때까지 대기한다. DepthAI 부팅 센서 모니터는 dataset을
만들지 않는 별도 서비스이므로 시간 동기화 전에도 장치 상태를 게시한다. 시간 설정이
성공하고 실제 장치 시간을 다시 검증한 뒤에만 root 소유 마커
`/run/jetson-control/time-synchronized.json`이 생성된다.

- 모바일은 Unix epoch 밀리초를 보낸다. 허용 범위는 2020-01-01 이상,
  2100-01-01 미만이다.
- 같은 부팅에서 첫 동기화가 끝나면 5분 이내의 반복 요청은 성공한 idempotent 요청으로
  처리하되 장치 시계를 다시 설정하지 않는다. 5분을 넘는 재보정은 거부한다. 모바일
  재연결 때문에 실행 중인 데이터의 시각이 점프하거나 역행하지 않게 하기 위한 제한이다.
- `date` 명령은 셸 없이 고정 인자 배열로 실행한다.
- 시간 설정 또는 사후 검증에 실패하면 마커를 만들지 않으므로 작업은 시작되지 않는다.
- 대기 중인 작업 상태는 `WAITING_FOR_TIME_SYNC`로 노출된다.

## FAN 제어

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

## API 연결 계약

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

앱 API로 등록한 폴더 방식의 실행 결과는 수집 storage root의 `<pipeline-id>/`에 저장한다. API는
등록 snapshot 경로와 storage root에만 쓰며, 홈 작업공간은 읽기 전용으로 유지한다.

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

## 운영 명령

```bash
systemctl status jetson-sensor-monitor.service
journalctl -u jetson-sensor-monitor.service -f

systemctl status jetson-pipeline@depthai-capture.service
journalctl -u jetson-pipeline@depthai-capture.service -f
ls -lh /var/log/jetson-pipelines/depthai-capture/

sudo systemctl start jetson-pipeline@depthai-capture.service
sudo systemctl stop jetson-pipeline@depthai-capture.service
sudo systemctl restart jetson-pipeline@depthai-capture.service

sudo systemctl enable jetson-pipeline@depthai-capture.service
sudo systemctl disable jetson-pipeline@depthai-capture.service
```

파일 로그는 pipeline별 최근 20개, 합계 1 GiB, 실행당 128 MiB까지 자동 보관한다. 실행 파일 한도를 넘긴 출력은 journald의 기존 회전 정책으로 계속 확인할 수 있다.

pipeline 사용자에게 필요한 장치 group을 장비 정책에 맞게 부여한다. 일반적인 후보는 `video`, `dialout`, `plugdev`지만 실제 `/dev` node의 owner/group을 먼저 확인한다. group 변경 후에는 사용자 session 재로그인 또는 재부팅이 필요하다.

## 검증

```bash
/usr/local/libexec/bluetooth/bluetoothd-5.55 -v
systemctl show bluetooth.service --property=ExecStart
systemctl is-enabled jetson-control.service jetson-control-api.service jetson-wifi-direct.service
systemctl is-enabled jetson-sensor-monitor.service
systemctl is-enabled jetson-pipeline@depthai-capture.service  # disabled가 정상
sudo /opt/jetson-control/doctor.sh
```

재부팅 검증:

```bash
sudo reboot
```

부팅 후 API, BLE 광고, 센서 모니터 상태·프리뷰, GPS/IMU의 stable serial 식별자,
모니터 중 dataset 미생성, 수집 시작/종료 시 장치 handoff, 새 dataset 생성 위치와 종료 시
파일 무결성을 확인한다. 운영 데이터가 연결된 상태에서는 앱의 reboot/shutdown 버튼을
누르기 전에 pipeline을 정상 중지한다.
