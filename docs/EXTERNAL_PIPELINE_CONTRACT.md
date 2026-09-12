# 외부 수집·실시간 검지 파이프라인 연동 계약

이 문서는 다른 저장소를 다루는 AI 에이전트와 개발자가 GEO& 앱의 작업 등록·실행 규칙을 이해하기 위한 인터페이스 설명이다. 앱에 검지 모델이나 외부 저장소를 내장하지 않는다. 장치의 외부 Git 작업 트리를 등록하면 백엔드가 실행 스냅샷을 만들고 기존 systemd 실행기를 사용한다. 실시간 검지 구현 자체는 이번 버전에 포함되지 않는다.

## 저장소 구조

AI 에이전트는 이 문서를 외부 검지 저장소의 연동 명세로 사용할 수 있다. 현재 Controller에 맞추는 필수 규칙과 외부 프로젝트에서 구현할 예시를 구분한다. 여기의 예시 설정 키·Python 함수·검지 JSON은 Controller가 자동 생성하거나 해석하는 기능이 아니다.

| 담당 | 책임 |
|---|---|
| 외부 검지 저장소 | 모델·추론·센서 입력·결과 저장 구현, `main.py` 어댑터와 환경 준비 |
| Jetson Controller 백엔드 | 소스 스냅샷, 프로세스 실행·중지, 로그, 센서 인계, 파일 조회·전송 |
| GEO& 앱 | 장치 폴더 선택·작업 등록, 설정 편집, 실행·기록·데이터 화면 |

앱에는 Git URL을 받아 clone하거나 장치의 의존성을 설치하는 기능이 없다. clone 또는 기존 코드의 폴더 정리는 장치에서 먼저 수행하고, 준비된 폴더를 앱으로 등록한다.

```text
/home/<pipeline-user>/road-detection/   # 소문자 영문·숫자·._- 이름
├── .git/                             # Git 작업 트리: 최소 1개 commit 필요
├── .venv/bin/python                  # 이 Jetson에서 만든 실행 가능한 Python
├── main.py                           # 하나의 실행 진입점
├── config.yaml                       # config.yml과 둘 중 하나만 존재
├── requirements.lock                 # 프로젝트의 고정 의존성 (이름은 자유)
├── models/                           # 모델 파일 또는 읽기 가능한 외부 경로
├── src/                              # 검지 및 수집 구현
└── results/                          # 쓰기 가능 결과 폴더, 등록 시 생성 가능
```

폴더 자동 검색은 `backend/jetson_control/pipeline_layout.py`의 계약을 따른다. 저장소·`.venv`·`main.py`·YAML·결과 폴더 자체의 심볼릭 링크는 허용하지 않는다. `.venv/bin/python`이 시스템 Python을 가리키는 일반적인 venv 구성은 허용한다. 폴더 이름이 파이프라인 ID이며 표시 이름은 앱에서 지정한다. 같은 ID를 재등록하면 새 스냅샷으로 갱신되므로 다른 작업을 유지하려면 별도 폴더 ID가 필요하다.

`.git`은 worktree 사용 시 파일일 수도 있다. 필수 조건은 선택한 폴더에서 `git rev-parse --show-toplevel`과 `git rev-parse HEAD`가 성공하는 것이다. 하위 프로젝트를 등록하면 그 폴더 밖의 형제 소스는 스냅샷에 포함되지 않는다. 외부 폴더를 가리키는 import, `pip install -e`로 연결한 개발 원본, 아직 내려받지 않은 모델/LFS 파일에 의존하지 않도록 준비한다. Git submodule을 사용하면 필요한 파일이 실제 등록 스냅샷에 포함되는지 별도로 확인한다.

## 외부 코드 준비: clone 또는 장치의 기존 코드

다음 명령은 외부 프로젝트를 준비하는 예시이며 이 문서를 작성하면서 실행하지 않는다. `<...>`는 실제 값으로 바꾼다. 앱에서 작업공간으로 탐색할 수 있는 pipeline 사용자의 홈 아래에 고유한 폴더를 두는 구성이 간단하다.

새 저장소를 가져오는 경우:

```bash
git clone <검지-저장소-URL> /home/<pipeline-user>/road-detector
cd /home/<pipeline-user>/road-detector
git checkout <검증한-tag-또는-commit>
```

장치에 이미 있는 코드는 해당 Git 작업 트리에 `main.py` 어댑터와 YAML을 추가한다. Git 이력이 없는 독립 폴더라면 `.gitignore`를 먼저 작성하고 `git init` 후 실행 소스와 설정만 명시적으로 추가하여 최초 commit을 만든다. 기존 Git 저장소 안에 중첩 저장소를 만들거나 데이터·모델·인증 파일 전체를 무조건 `git add .` 하지 않는다.

권장 `.gitignore` 예시:

```gitignore
.venv/
results/
logs/
__pycache__/
*.pyc
.env
models/*.engine
models/*.onnx
models/*.pt
```

ignore한 모델은 release에 복사되지 않으므로, YAML에 장치에서 읽을 수 있는 모델의 절대경로를 설정한다. `.venv`, `logs`, `results`는 폴더 등록 시 별도로 항상 제외된다. `main.py`, YAML, import 대상 소스는 Git 추적 파일 또는 ignore되지 않은 파일이어야 한다. 재현 가능한 배포를 위해 소스 변경은 commit한 뒤 등록한다.

## Python·CUDA 환경

실행 가상환경은 Controller API의 `/opt/jetson-control/venv`와 별개다. JetPack/L4T, CUDA, TensorRT, Python minor ABI, aarch64 및 카메라 SDK 버전에 맞는 패키지가 외부 `.venv`에 설치되어 있어야 한다. 다른 PC의 venv 디렉터리를 복사하는 방식은 호환되지 않는다. Jetson 시스템 제공 패키지가 필요한 경우 해당 프로젝트가 `--system-site-packages` 사용 여부를 명시한다.

배포 설명에 기록되는 환경 정보는 Python 버전, JetPack/L4T, CUDA/TensorRT 버전, 패키지 lock, 모델 체크섬·라이선스·입출력 형상이다. 앱이 pip 설치나 모델 다운로드를 수행하지 않는다. 모델·비밀키·대용량 결과는 소스 스냅샷과 분리하여 관리하는 구성이 적합하다. 외부 모델 경로는 systemd의 파일 접근 정책과 일치해야 한다.

```bash
# 선택한 Python이 해당 프로젝트의 Jetson SDK와 호환되는지 먼저 확인한다.
<호환-Python-실행파일> -m venv .venv
.venv/bin/python -m pip install -r requirements.lock
.venv/bin/python -m pip check
.venv/bin/python --version
```

`requirements.lock`은 외부 프로젝트에서 제공해야 한다. TensorRT·PyTorch·카메라 SDK는 대상 Jetson에서 검증한 설치 경로와 wheel/시스템 패키지 조합을 별도 환경 설명에 기록한다. systemd는 `.bashrc`, `conda activate`, 셸의 `source .venv/bin/activate`를 실행하지 않으며 등록된 `.venv/bin/python`을 직접 실행한다.

소스만 스냅샷으로 보존되고 `.venv`와 외부 모델은 공유된 원본 경로를 사용한다. 따라서 같은 venv를 업그레이드하면 이전 소스 release에도 영향을 준다. 재현성이 필요한 버전은 별도의 작업 폴더·venv·모델 경로로 준비한다. 실행 중인 작업의 환경을 덮어쓰지 않는다.

## 프로세스와 설정

실행기는 스냅샷의 Python 진입점에 `--config <실제 YAML 경로>`를 전달한다. 독립 실행 시에도 저장소의 YAML을 기본값으로 읽는 진입점이 호환된다. stdout/stderr는 실행별 로그와 journald에 남는다. 종료 코드 0은 완료, 다른 코드는 실패 또는 signal에 의한 중지다. SIGTERM/SIGINT에서 장치와 파일을 정리하고 종료하는 전경 프로세스여야 하며 daemon으로 분리하면 실행 기록과 센서 반환이 어긋난다.

폴더 등록 시 working directory는 원본 작업 폴더이고 실행 파일은 release 안에 있다. 소스·리소스 경로는 `Path(__file__).resolve().parent` 기준으로 계산한다. `Path.cwd()`에서 원본 코드를 다시 읽으면 스냅샷의 버전 고정 효과가 사라진다. C++ 실행 파일·다른 CLI가 실제 추론을 담당해도 root `main.py`가 설정 전달, 하위 프로세스 대기·signal 전달, 종료 코드를 책임지는 어댑터로 남아야 한다.

서비스는 `Restart=on-failure`, `RestartSec=15`, `TimeoutStopSec=30`으로 동작한다. 장시간 추론도 종료 요청을 주기적으로 확인하고 30초 안에 리소스를 정리한다. 정상 완료는 0, 사용자 중지는 130(SIGINT) 또는 143(SIGTERM), 오류는 다른 비영(非零) 코드를 사용하면 실행 기록이 구분된다. 오류로 끝난 작업은 서비스가 재시도할 수 있다. 앱의 중지 요청으로 서비스가 멈춘 경우에는 이 재시도가 발생하지 않는다.

| 환경 변수 | 의미 |
|---|---|
| `JETSON_PIPELINE_ID` | 등록된 작업 ID |
| `JETSON_PIPELINE_RELEASE` | 실행 중인 소스 스냅샷 경로. YAML은 앱에서 별도 편집 가능 |
| `JETSON_PIPELINE_CONFIG` | 실행 설정 YAML 경로 |
| `JETSON_PIPELINE_RESULTS_DIR` | 등록기가 지정한 지속 결과 경로 |
| `JETSON_PIPELINE_LOGS_DIR` | 실행 로그 디렉터리 |
| `JETSON_PIPELINE_SENSOR_BRIDGE_DIR` | 센서 상태·프리뷰 게시 경로. 센서 모니터 연동 시 제공 |
| `JETSON_PIPELINE_MOBILE_RTK_RELAY` | 모바일 RTK 중계 경로가 활성화되었을 때 `1` |

출력 경로는 `JETSON_PIPELINE_RESULTS_DIR`를 우선 사용한다. 하드코딩한 개발 PC 경로 또는 읽기 전용 release 내부에 결과를 쓰지 않는다. 예를 들어 `results/<UTC-run-id>/images`, `video`, `sensors`, `logs`, `detections.jsonl`로 구분하면 앱의 폴더·파일 전송 및 확장자 필터를 그대로 사용할 수 있다. 날짜 그룹은 파일 수정 시간을 휴대전화의 시간대로 표시한다.

앱의 폴더 등록은 보통 `/data/collections/<pipeline-id>/`를 결과 root로 설정한다. 실제 기준은 환경 변수이며 CLI의 폴더 등록 기본값은 원본 폴더의 `results/`일 수 있다. 각 실행은 이 root 안에 고유한 하위 폴더를 만들어 이전 결과를 보존한다. 모델 캐시·임시 인코딩 파일 등 지속 쓰기가 필요한 경로도 결과 root 아래에 둔다. 일반 홈·venv·release는 systemd에서 쓰기가 제한된다. `/tmp`는 서비스별 임시 공간이므로 앱 데이터 저장소로 사용하지 않는다.

YAML 편집은 현재 release의 설정 파일에 반영된다. 소스 코드 스냅샷과 달리 설정은 앱에서 변경될 수 있으므로 실행 시작 시 실제 설정의 비밀정보를 제거한 사본/해시, 소스 revision, 모델 해시를 결과 메타데이터에 기록한다. 앱의 설정 변경을 실행 중 자동 적용하는 기능은 없다. 외부 코드가 시작 시 읽는 방식이면 다음 실행에서 적용된다.

## 외부 저장소용 진입점 예시

아래 코드는 `main.py`의 호환 어댑터 예시다. `ruamel.yaml`이 외부 venv에 필요하다. `src/pipeline.py`의 `run(config, source_root, output_dir, stop_event)`는 외부 AI 에이전트가 실제 검지 코드에 맞춰 구현하는 함수이며 Controller API가 아니다. `run`은 종료 요청을 확인하고 `finally`에서 센서·writer·하위 프로세스를 정리한다. 모듈 import 시 센서를 여는 부작용은 두지 않는다.

```python
import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import signal
import threading
import uuid

from ruamel.yaml import YAML


def main():
    source_root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=Path, default=source_root / 'config.yaml')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    with args.config.open(encoding='utf-8') as stream:
        config = YAML(typ='safe').load(stream)
    if not isinstance(config, dict):
        raise ValueError('config must be a YAML mapping')
    model = Path(config['model']['path'])
    if not model.is_absolute() or not model.is_file():
        raise ValueError('model.path must be an existing absolute file path')
    threshold = config['detection']['confidence_threshold']
    if isinstance(threshold, bool) or not isinstance(threshold, (int, float)) or not 0 <= threshold <= 1:
        raise ValueError('confidence_threshold must be between 0 and 1')
    if args.check:
        print('Configuration check passed; no sensor or inference was started.')
        return 0

    results = Path(os.environ.get('JETSON_PIPELINE_RESULTS_DIR', source_root / 'results'))
    run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S_%fZ') + '-' + uuid.uuid4().hex[:8]
    output_dir = results / run_id
    output_dir.mkdir(parents=True, exist_ok=False)
    os.environ.setdefault('XDG_CACHE_HOME', str(output_dir / 'cache'))
    stop = threading.Event()
    received_signal = []

    def request_stop(signum, _frame):
        received_signal[:] = [signum]
        stop.set()

    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)
    from src.pipeline import run
    run(config=config, source_root=source_root, output_dir=output_dir, stop_event=stop)
    return 128 + received_signal[0] if received_signal else 0


if __name__ == '__main__':
    raise SystemExit(main())
```

대응하는 `config.yaml` 예시다. 모든 키는 외부 프로그램이 소비한다. 앱은 YAML의 scalar 값을 편집할 수 있지만 카메라나 모델의 의미를 자동 해석하지 않는다. 비밀번호·접속 token은 넣지 않고 필요한 경우 기존 `/etc/jetson-control/pipelines/<pipeline-id>.env` 환경 파일을 운영자가 설정한다.

```yaml
model:
  path: /home/<pipeline-user>/models/road-detector/model.engine
detection:
  confidence_threshold: 0.5
  save_annotated_images: true
input:
  source: camera
```

`--check`는 이 예시에서 외부 프로그램에 추가한 기능이다. Controller 등록기는 이를 자동 호출하지 않는다. 예시는 파일 존재·설정만 검사하며 모델 로딩, SDK import, GPU 호환성 검증은 외부 프로젝트의 별도 검사가 필요하다.

## 센서 소유권과 실시간 데이터

부팅 센서 모니터와 작업 프로세스가 같은 카메라·GNSS 포트를 동시에 열지 않는다. 기존 실행기는 등록된 센서 인계 설정에 따라 모니터를 멈추고 센서 lease를 확보한 뒤 외부 프로세스를 실행한다. 이 동작은 `sensor_handoff.py`, `sensor_monitor.py`와 [파이프라인 운영](PIPELINES.md)에 정의되어 있다.

인계 대상은 `/etc/jetson-sensor-monitor.json`의 `capture_pipeline_ids`에 포함된 ID이며, 필드가 없으면 `pipeline_id` 하나만 대상이다. 새 작업을 앱에 등록하는 것만으로 이 목록에 자동 추가되지는 않는다. 기존 모니터와 같은 장치를 직접 여는 검지 작업은 운영자가 기존 목록을 보존하면서 새 ID를 등록해야 한다. `pipeline_id`는 부팅 모니터용 기존 작업을 계속 가리키도록 유지한다. 파일 입력만 쓰는 작업이나 이미 게시된 프리뷰를 읽기만 하는 검지 작업은 센서 직접 인계와 구분한다. 프리뷰만 읽는 작업이 자신을 인계 목록에 넣으면 프리뷰 생산자인 모니터를 중단하게 된다.

센서를 직접 인계받은 외부 프로그램은 `/var/lib/jetson-sensors/status.json`과 `camera-preview.jpg`를 게시한다. 인계 환경 변수로 지정된 경로가 우선이다. 동일 디렉터리에 임시 파일을 쓰고 닫은 뒤 `os.replace`로 게시하며, 프리뷰를 교체한 다음 상태를 교체한다. 상태는 UTF-8 JSON, `schemaVersion: 1`, `updatedAtEpochMillis`가 필요하고 현재 reader의 신선도 기준은 기본 5초다. 상태 최대 크기는 128 KiB, JPEG 최대 크기는 12 MiB다. 센서 상태를 게시하지 않으면 추론 프로세스가 실행 중이어도 앱 프리뷰·경로·캡처가 제공되지 않을 수 있다.

```json
{
  "schemaVersion": 1,
  "updatedAtEpochMillis": 1000000000000,
  "pipeline": {"active": true},
  "camera": {
    "configured": true, "connected": true, "active": true,
    "lastFrameAtEpochMillis": 1000000000000,
    "frameWidth": 1280, "frameHeight": 720,
    "previewAvailable": true, "previewUpdatedAtEpochMillis": 1000000000000
  },
  "gnss": {
    "configured": true, "connected": true, "active": true,
    "lastSampleAtEpochMillis": 1000000000000,
    "latitude": 37.0, "longitude": 127.0, "altitudeM": 20.0
  },
  "imu": {"configured": false, "connected": false, "active": false}
}
```

위 timestamp·좌표는 형식 설명용이며 실제 값으로 교체한다. 수신이 끊긴 센서는 `active: false`로 게시한다. GPS 좌표는 WGS84 도 단위, 고도는 미터, timestamp는 Unix epoch 밀리초다. 여러 프로그램이 공유 상태/JPEG를 동시에 쓰는 방식은 지원하지 않는다.

외부 파이프라인이 현재 센서 상태 형식을 게시하면 앱이 카메라 프리뷰와 GNSS를 표시할 수 있다. 정확한 필드와 원자적 파일 작성 방식은 `backend/jetson_control/sensors.py`의 `SensorBridgeStore` 및 `sensor_monitor.py`의 게시 코드를 기준으로 한다. GNSS에는 유효한 위·경도, `active`, `lastSampleAtEpochMillis`와 최신 상태 시간이 필요하다. 과거 샘플을 현재 시간처럼 재게시하면 경로 신뢰성을 잃는다. 실행기의 `RouteRecorder`는 2초마다 새 유효 GPS 샘플만 기록하며 수신 중단 구간을 나눈다.

기존 카메라 캡처는 게시된 JPEG 프리뷰의 해상도로 저장된다. 고해상도 원본 촬영이나 검지 결과 오버레이가 필요하면 외부 파이프라인에서 해당 프레임을 게시하거나 버전이 명시된 별도 API를 정의하는 확장이다.

## 앱 연결 흐름

작업 → `+ 새 작업 시작하기` → 실행 프로그램 선택 → `+ 작업 추가` → 장치 폴더 선택·등록 → 작업 설정 확인 → 시작 전 확인 → 실행 요청 순서다. 새 등록은 부팅 자동 실행을 기본으로 켜지 않는다. 기존 등록의 자동 실행 설정은 유지된다. 시작 직전 시간 동기화·인증·센서 인계는 Controller의 기존 실행 경로를 사용한다.

새로운 검지 작업도 같은 등록·시작·중지·로그·결과 인터페이스를 사용한다. 등록했다고 즉시 실행되는 것은 아니다. Git 소스 변경은 등록 스냅샷에 자동 반영되지 않으며 재등록 시 새 revision이 적용된다. 실행 중 스냅샷을 직접 수정하는 방식은 지원하지 않는다.

CLI로 준비 상태를 확인한 후 앱에서 실행할 수도 있다. 다음 예시는 **등록만** 하며 즉시 시작과 부팅 자동 실행을 모두 사용하지 않는다. CLI 폴더 등록은 옵션 생략 시 자동 실행을 기본으로 하므로 `--no-autostart`를 명시한다. 이는 새 앱 등록 화면의 기본값 `false`와 다르다.

```bash
sudo /opt/jetson-control/register-pipeline.py \
  --folder /home/<pipeline-user>/road-detector \
  --name "실시간 도로 검지" \
  --user <pipeline-user> \
  --results-dir /data/collections/road-detector \
  --no-autostart
```

`/data/collections`가 실제 장치의 수집 root인지 먼저 확인한다. 시스템 시간 동기화 대기로 프로세스가 아직 시작되지 않을 수 있으므로 CLI에서 서비스를 직접 시작해 검지 오류로 단정하지 말고 앱의 인증·시간 동기화 흐름을 사용한다.

## 등록 전 검증과 AI 에이전트의 결과물

외부 저장소에 다음 내용을 남기면 다음 에이전트가 환경을 재구성하고 앱에 등록할 수 있다.

| 결과물 | 필수 내용 |
|---|---|
| `main.py`, 단일 YAML | `--config` 인자, 경로 처리, 종료 signal, 검지 코드 어댑터 |
| 환경 설명·의존성 lock | 대상 JetPack/Python/SDK, venv 생성·설치 순서, import 검사 결과 |
| 모델 설명 | 절대경로, 해시, 모델 revision, 입력 형상·전처리·출력 의미 |
| 결과·센서 계약 | 파일 구조, 쓰기 경로, 센서 직접 인계 여부, bridge 게시 여부 |
| 등록 설명 | 고유 폴더 ID, 표시 이름, 앱 등록 순서, 재등록·환경 갱신 방법 |
| 검증 기록 | 모의 검사와 실제 장치 검사를 구분한 결과, 미검증 항목 |

센서를 열거나 실제 작업을 시작하지 않고 가능한 검사는 다음과 같다.

1. `git rev-parse HEAD`와 `git ls-files --cached --others --exclude-standard -- .`로 revision과 포함 소스를 확인한다.
2. `.venv/bin/python -m pip check`, 진입점 문법 검사, `.venv/bin/python main.py --config config.yaml --check`를 실행한다. 마지막 옵션은 위 예시처럼 구현한 경우에만 가능하다.
3. 가짜 프레임·센서로 설정 검증, 출력 경로, JSON 형식, SIGINT/SIGTERM 종료 동작을 검사한다. 모의 검사는 실제 SDK/GPU 검사와 구분한다.
4. Controller의 `discover_pipeline_folder`와 등록 스냅샷에서 소스 포함·모델 경로·venv 경로를 확인한다. 등록기는 모델 추론이나 모든 import를 검증하지 않는다.
5. 실제 카메라·GPS·GPU 처리, 서비스 실행 중지, 앱 프리뷰·경로·전송 검증은 장비 사용이 가능한 별도 단계로 기록한다. 실행하지 않은 항목을 통과로 적지 않는다.

### 외부 AI 에이전트에 전달할 요청 예시

```text
이 저장소를 GEO& Jetson Controller의 외부 검지 작업으로 등록할 수 있게 구성한다.
연동 기준: https://github.com/dbparkJ/JetsonControllerApp/blob/main/docs/EXTERNAL_PIPELINE_CONTRACT.md

먼저 기존 코드의 진입점, Python/JetPack/SDK, 모델 위치, 센서 소유권을 조사한다.
Controller 앱 안에 검지 구현을 복사하지 않고 외부 저장소에 main.py 어댑터,
단일 config.yaml 또는 config.yml, 장치용 .venv 준비 절차를 제공한다.
출력은 JETSON_PIPELINE_RESULTS_DIR를 우선 사용하고 stdout/stderr 및 종료 signal을 지원한다.
원본 작업 폴더와 실행 release가 다른 점, venv와 모델은 스냅샷에 포함되지 않는 점을 반영한다.
센서를 직접 여는 경우 기존 capture_pipeline_ids와 bridge 계약의 연동 필요사항을 설명한다.
기존 모델이나 장치 환경을 추측하지 말고 확인한 값과 미확인 값을 구분한다.
실행 코드, 환경/모델 설명, 앱 등록 절차, 검증 결과와 미검증 항목을 결과물로 남긴다.
실제 장치 작업은 요청된 범위에서만 실행하며, 모의 검사를 실제 검지 성공으로 보고하지 않는다.
```

## 향후 검지 결과 API

현재 앱은 검지 confidence나 bounding box 등의 실시간 결과를 해석하지 않는다. 외부 구현은 파일 결과를 지금의 데이터 탭에서 다룰 수 있다. 후속 실시간 UI 연동이 생기는 경우 명시적인 capability와 schema version, run ID, frame timestamp, 좌표계, 단위, 모델 revision, confidence 정의가 포함되는 계약이 필요하다. 기존 `/v1/status` 센서 상태 필드를 검지 의미로 재사용하지 않는 구조가 호환성을 유지한다.

현재 제공된 실행 기록 API는 `GET /v1/task-runs`, 실행 로그는 `/v1/task-runs/{pipelineId}/{logId}/log`, 경로는 같은 prefix의 `/route`다. 모두 기존 장치 인증과 응답 서명을 사용한다. 등록·명령 API와 이 API의 가용성은 장치 백엔드 버전에 달려 있다.

## 구현 근거

이 계약은 현재 저장소 구현을 기준으로 한다. 외부 에이전트는 사용 중인 Controller revision의 다음 파일과 대조하며, 새로운 요구사항을 현재 제공되는 기능으로 가정하지 않는다.

| 계약 | 구현 |
|---|---|
| 폴더 검색 | [pipeline_layout.py](../backend/jetson_control/pipeline_layout.py) |
| Git 스냅샷·venv·systemd 등록 | [register-pipeline.py](../backend/scripts/register-pipeline.py) |
| 실행 환경·인자·시간 동기화·signal | [run-pipeline.py](../backend/scripts/run-pipeline.py) |
| 앱 등록·결과 경로·YAML 편집 | [pipelines.py](../backend/jetson_control/pipelines.py) |
| 센서 인계 대상과 lease | [sensor_handoff.py](../backend/jetson_control/sensor_handoff.py) |
| 센서 상태·JPEG reader | [sensors.py](../backend/jetson_control/sensors.py) |
| 서비스 실행·쓰기 제한 | [jetson-pipeline@.service](../backend/systemd/jetson-pipeline@.service) |
| 실행 기록·캡처 API, GPS 저장 | [field_tools.py](../backend/jetson_control/field_tools.py), [route_recorder.py](../backend/jetson_control/route_recorder.py) |
