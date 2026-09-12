# 외부 수집·실시간 검지 파이프라인 연동 계약

이 문서는 다른 저장소를 다루는 AI 에이전트와 개발자가 GEO& 앱의 작업 등록·실행 규칙을 이해하기 위한 인터페이스 설명이다. 앱에 검지 모델이나 외부 저장소를 내장하지 않는다. 장치의 외부 Git 작업 트리를 등록하면 백엔드가 실행 스냅샷을 만들고 기존 systemd 실행기를 사용한다. 실시간 검지 구현 자체는 이번 버전에 포함되지 않는다.

## 저장소 구조

```text
/home/<pipeline-user>/road-detection/   # 소문자 영문·숫자·._- 이름
├── .git/                             # 외부 원본과 revision 추적
├── .venv/bin/python                  # 이 Jetson에서 만든 실행 가능한 Python
├── main.py                           # 하나의 실행 진입점
├── config.yaml                       # config.yml과 둘 중 하나만 존재
├── requirements.lock                 # 프로젝트의 고정 의존성 (이름은 자유)
├── models/                           # 모델 파일 또는 읽기 가능한 외부 경로
├── src/                              # 검지 및 수집 구현
└── results/                          # 쓰기 가능 결과 폴더, 등록 시 생성 가능
```

폴더 자동 검색은 `backend/jetson_control/pipeline_layout.py`의 계약을 따른다. 저장소·`.venv`·`main.py`·YAML·결과 폴더 자체의 심볼릭 링크는 허용하지 않는다. `.venv/bin/python`이 시스템 Python을 가리키는 일반적인 venv 구성은 허용한다. 폴더 이름이 파이프라인 ID이며 표시 이름은 앱에서 지정한다. 같은 ID를 재등록하면 새 스냅샷으로 갱신되므로 다른 작업을 유지하려면 별도 폴더 ID가 필요하다.

## Python·CUDA 환경

실행 가상환경은 Controller API의 `/opt/jetson-control/venv`와 별개다. JetPack/L4T, CUDA, TensorRT, Python minor ABI, aarch64 및 카메라 SDK 버전에 맞는 패키지가 외부 `.venv`에 설치되어 있어야 한다. 다른 PC의 venv 디렉터리를 복사하는 방식은 호환되지 않는다. Jetson 시스템 제공 패키지가 필요한 경우 해당 프로젝트가 `--system-site-packages` 사용 여부를 명시한다.

배포 설명에 기록되는 환경 정보는 Python 버전, JetPack/L4T, CUDA/TensorRT 버전, 패키지 lock, 모델 체크섬·라이선스·입출력 형상이다. 앱이 pip 설치나 모델 다운로드를 수행하지 않는다. 모델·비밀키·대용량 결과는 소스 스냅샷과 분리하여 관리하는 구성이 적합하다. 외부 모델 경로는 systemd의 파일 접근 정책과 일치해야 한다.

## 프로세스와 설정

실행기는 스냅샷의 Python 진입점에 `--config <실제 YAML 경로>`를 전달한다. 독립 실행 시에도 저장소의 YAML을 기본값으로 읽는 진입점이 호환된다. stdout/stderr는 실행별 로그와 journald에 남는다. 종료 코드 0은 완료, 다른 코드는 실패 또는 signal에 의한 중지다. SIGTERM/SIGINT에서 장치와 파일을 정리하고 종료하는 전경 프로세스여야 하며 daemon으로 분리하면 실행 기록과 센서 반환이 어긋난다.

| 환경 변수 | 의미 |
|---|---|
| `JETSON_PIPELINE_ID` | 등록된 작업 ID |
| `JETSON_PIPELINE_RELEASE` | 실행 중인 불변 소스 스냅샷 경로 |
| `JETSON_PIPELINE_CONFIG` | 실행 설정 YAML 경로 |
| `JETSON_PIPELINE_RESULTS_DIR` | 등록기가 지정한 지속 결과 경로 |
| `JETSON_PIPELINE_LOGS_DIR` | 실행 로그 디렉터리 |
| `JETSON_PIPELINE_SENSOR_BRIDGE_DIR` | 센서 상태·프리뷰 게시 경로. 센서 모니터 연동 시 제공 |
| `JETSON_PIPELINE_MOBILE_RTK_RELAY` | 모바일 RTK 중계 경로가 활성화되었을 때 `1` |

출력 경로는 `JETSON_PIPELINE_RESULTS_DIR`를 우선 사용한다. 하드코딩한 개발 PC 경로 또는 읽기 전용 release 내부에 결과를 쓰지 않는다. 예를 들어 `results/<UTC-run-id>/images`, `video`, `sensors`, `logs`, `detections.jsonl`로 구분하면 앱의 폴더·파일 전송 및 확장자 필터를 그대로 사용할 수 있다. 날짜 그룹은 파일 수정 시간을 휴대전화의 시간대로 표시한다.

## 센서 소유권과 실시간 데이터

부팅 센서 모니터와 작업 프로세스가 같은 카메라·GNSS 포트를 동시에 열지 않는다. 기존 실행기는 등록된 센서 인계 설정에 따라 모니터를 멈추고 센서 lease를 확보한 뒤 외부 프로세스를 실행한다. 이 동작은 `sensor_handoff.py`, `sensor_monitor.py`와 [파이프라인 운영](PIPELINES.md)에 정의되어 있다.

외부 파이프라인이 현재 센서 상태 형식을 게시하면 앱이 카메라 프리뷰와 GNSS를 표시할 수 있다. 정확한 필드와 원자적 파일 작성 방식은 `backend/jetson_control/sensors.py`의 `SensorBridgeStore` 및 `sensor_monitor.py`의 게시 코드를 기준으로 한다. GNSS에는 유효한 위·경도, `active`, `lastSampleAtEpochMillis`와 최신 상태 시간이 필요하다. 과거 샘플을 현재 시간처럼 재게시하면 경로 신뢰성을 잃는다. API의 `RouteRecorder`는 실행기에서 2초마다 새 유효 GPS 샘플만 기록하며 수신 중단 구간을 나눈다.

기존 카메라 캡처는 게시된 JPEG 프리뷰의 해상도로 저장된다. 고해상도 원본 촬영이나 검지 결과 오버레이가 필요하면 외부 파이프라인에서 해당 프레임을 게시하거나 버전이 명시된 별도 API를 정의하는 확장이다.

## 앱 연결 흐름

작업 → `+ 새 작업 시작하기` → 실행 프로그램 선택 → `+ 작업 추가` → 장치 폴더 선택·등록 → 작업 설정 확인 → 시작 전 확인 → 실행 요청 순서다. 새 등록은 부팅 자동 실행을 기본으로 켜지 않는다. 기존 등록의 자동 실행 설정은 유지된다. 시작 직전 시간 동기화·인증·센서 인계는 Controller의 기존 실행 경로를 사용한다.

새로운 검지 작업도 같은 등록·시작·중지·로그·결과 인터페이스를 사용한다. 등록했다고 즉시 실행되는 것은 아니다. Git 소스 변경은 등록 스냅샷에 자동 반영되지 않으며 재등록 시 새 revision이 적용된다. 실행 중 스냅샷을 직접 수정하는 방식은 지원하지 않는다.

## 향후 검지 결과 API

현재 앱은 검지 confidence나 bounding box 등의 실시간 결과를 해석하지 않는다. 외부 구현은 파일 결과를 지금의 데이터 탭에서 다룰 수 있다. 후속 실시간 UI 연동이 생기는 경우 명시적인 capability와 schema version, run ID, frame timestamp, 좌표계, 단위, 모델 revision, confidence 정의가 포함되는 계약이 필요하다. 기존 `/v1/status` 센서 상태 필드를 검지 의미로 재사용하지 않는 구조가 호환성을 유지한다.

현재 제공된 실행 기록 API는 `GET /v1/task-runs`, 실행 로그는 `/v1/task-runs/{pipelineId}/{logId}/log`, 경로는 같은 prefix의 `/route`다. 모두 기존 장치 인증과 응답 서명을 사용한다. 등록·명령 API와 이 API의 가용성은 장치 백엔드 버전에 달려 있다.
