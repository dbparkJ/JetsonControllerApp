# GEO& 도로관리장치 제어

Android에서 NVIDIA Jetson을 등록하고 연결해 센서, 데이터 수집 작업, 저장소와 업로드를 관리하는 프로젝트입니다. Kotlin·Jetpack Compose 앱, Jetson 제어 백엔드, 외부 업로드 수신 서버를 함께 관리합니다.

## 주요 기능

앱은 디스플레이 없는 Jetson의 화면 역할을 하며, 하단 탭 **장치 · 작업 · 전송 · 설정**으로 나뉩니다. 구조와 화면 규칙은 [앱 구조 재설계 계획](docs/design/APP_REBUILD_PLAN.md)에 있습니다.

- **장치**: 등록 장치별 연결 상태와 마지막 응답, 온도·저장 여유·실행 중 작업(오프라인이면 마지막 값), 장치 상세의 CPU·GPU·메모리·센서·카메라 미리보기·위치 지도, 장치 Wi-Fi, 재부팅·전원 종료. 등록은 QR, 연결은 같은 네트워크(LAN)·장치 직접 연결(Wi-Fi Direct)·블루투스
- **작업**: 현장 수집(구간 선택 → 장치 점검 → 시작 → 진행 → 결과 영수증), 다른 작업의 상태, 작업 기록. 작업 등록·편집·로그는 개발자 모드
- **전송**: Jetson에서 수신 서버로 보내는 업로드의 진행·대기·실패·서버 수신 확인, 보낼 파일 고르기, 서버 파일 조회
- **설정**: 알림 기준, 화면 테마, 서버 연결, 도움말, 앱 정보. 개발자 모드(앱 정보 7회)에서 고급 도구(진단, 업로드 서버 대상, 파이프라인 편집, 팬 제어)

## 구성

```text
Android 앱
  ├─ BLE ─────────────── 장비 인증, Wi-Fi 설정, 제한된 제어
  └─ LAN / Wi-Fi Direct
       └─ HTTPS :8765 ── Jetson 제어 API
                           ├─ 센서·systemd·Python 수집 작업
                           ├─ 장치 저장소
                           └─ HTTPS :443 ── 외부 업로드 수신 서버
                                               └─ SQLite + 파일 저장소
```

업로드 파일은 Jetson에서 수신 서버로 직접 전송합니다. Android는 Jetson API로 작업과 진행률을 관리합니다. 장비 제어는 QR secret으로 검증한 TLS 인증서와 요청·응답 HMAC을 사용합니다.

| 경로 | 내용 |
|---|---|
| [app/](app/) | Android 앱과 JVM·기기 테스트 |
| [backend/](backend/) | Jetson API, BLE·P2P, 센서·파이프라인 관리, 설치 스크립트 |
| [upload_receiver/](upload_receiver/) | 업로드 수신 API, 저장소, Caddy·systemd 배포 도구 |
| [scripts/](scripts/) | ARM64 Android 빌드 환경, 지도 키 설정, 진단 백엔드 배포 도구 |
| [docs/](docs/) | 주제별 설치·운영 문서 |

## Android 개발

저장소의 현재 빌드 설정은 **Android 12 이상(API 31), compile/target SDK 37, Gradle JVM 25**입니다. 도구 버전은 [Gradle 설정](app/build.gradle.kts), [버전 카탈로그](gradle/libs.versions.toml), [JVM 설정](gradle/gradle-daemon-jvm.properties)을 기준으로 합니다.

### 일반 개발 PC

1. JDK 25와 Android SDK의 `platform-tools`, `platforms;android-37.0`, `build-tools;37.0.0`을 준비합니다.
2. Android Studio에서 프로젝트를 열거나, 로컬 `local.properties`에 `sdk.dir=/실제/Android/SDK/경로`를 설정합니다.
3. 저장소 루트에서 빌드·검증합니다. 아래는 전체 조합 예시이며, 부분 변경은 영향을 받는 Gradle 작업과 `--tests '*테스트클래스명'`으로 범위를 선택합니다.

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest \
  --max-workers=1 --console=plain
```

Windows에서는 `gradlew.bat`를 사용합니다. APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 연결된 테스트 기기의 UI·네트워크 시험은 `./gradlew :app:connectedDebugAndroidTest`로 별도 실행합니다.

기기에 이미 다른 서명 키로 만든 앱이 설치되어 있으면 덮어쓰기 설치가 거부됩니다. 기존 앱의 데이터(등록 장치·서버 프로필)를 지우지 않고 시험하려면 `-PdebugApplicationIdSuffix=.redesign`처럼 접미사를 주어 별도 패키지("GEO-X 시험판")로 설치합니다.

### Jetson / Linux ARM64에서 빌드

[setup-mobile-build-env.sh](scripts/setup-mobile-build-env.sh)는 이미 준비된 SDK·Gradle·QEMU·AAPT2 실행 환경을 저장소의 `.mobile-build/`로 복사해 재사용합니다. 필요한 원본 도구가 설치된 환경에서 실행합니다.

```bash
./scripts/setup-mobile-build-env.sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest \
  --max-workers=1 --console=plain
```

원본 위치는 `ANDROID_SDK_SOURCE`, `GRADLE_HOME_SOURCE`, `QEMU_X86_64_SOURCE`, `AAPT2_SYSROOT_SOURCE`로 지정할 수 있습니다. `scripts/aapt2`는 이 환경의 QEMU를 사용하는 실행 도구입니다. Unix `gradlew`는 기본 Gradle 캐시를 `.mobile-build/gradle-home`에 보관하며, `GRADLE_USER_HOME`을 지정하면 해당 경로를 사용합니다.

### 네이버 지도 키

```bash
./scripts/configure-naver-map-key.sh
```

도우미가 입력값을 화면에 표시하지 않고 Git에서 제외된 `local.properties`의 `naver.mapClientId`에 저장합니다. 값은 NCP 콘솔 Maps Application의 Client ID(`ncpKeyId`)입니다. Client Secret은 앱에 넣지 않습니다. 읽기 우선순위는 Gradle 속성 `NAVER_MAP_CLIENT_ID` → 같은 이름의 환경변수 → `local.properties`입니다.

Client ID는 APK에 포함됩니다. 네이버는 그 Application의 서비스 환경 등록에 올라간 Android 패키지에서만 지도를 허용합니다. 정식 `com.example.jetsoncontroller`와 시험판 `com.example.jetsoncontroller.redesign`을 모두 등록합니다. 인증 결과는 지도를 열 때 `GeoMap` 로그에 코드로 남습니다(401: 패키지 미등록 또는 잘못된 키, 429: Maps 미선택 또는 한도 초과).

기기에 배포하는 APK는 `./gradlew :app:assembleDebug -PrequireMapKey=true`로 빌드합니다. 이 옵션은 지도 키가 누락되면 빌드를 중단합니다. 연결된 기기에서 `NaverMapConfigurationTest`를 실행하면 설치된 앱에 지도 키가 들어 있는지 확인합니다.

## Jetson 설치와 첫 연결

Jetson에서 저장소를 준비한 뒤 아래 명령의 `<user>`를 센서 장치에 접근할 Linux 계정으로 바꿉니다. 최초 설치는 시스템 패키지·BlueZ·백엔드 서비스를 함께 구성합니다.

```bash
sudo backend/scripts/bootstrap-jetson.sh \
  --device-name MMS-JETSON-01 \
  --pipeline-user <user>

sudo /opt/jetson-control/doctor.sh
```

앱에서 재부팅·종료를 허용하려면 설치 옵션에 `--enable-power`를 추가합니다. DepthAI 작업의 소스와 가상환경 등록은 [파이프라인 운영](docs/PIPELINES.md)을 따릅니다.

1. `/var/lib/jetson-control/jetson-pairing-qr.png`의 QR을 앱에서 스캔합니다.
2. BLE로 장비를 인증하고 필요한 Wi-Fi 설정을 전달합니다.
3. 같은 LAN에서 장비에 연결하거나 앱의 **장비에 직접 연결**로 Wi-Fi Direct를 사용합니다.
4. 대시보드 상태를 확인한 뒤 수집 작업·데이터·업로드 화면을 사용합니다.

기존 장비 업데이트는 [백엔드 설치·운영](docs/BACKEND.md)을 따릅니다. 설치기는 기존 장비 identity와 설정을 보존합니다. 저장소의 코드 갱신과 실행 중인 서비스의 배포는 별도 작업입니다.

## Python 개발과 테스트

아래는 개발 PC에서 하드웨어 없이 실행하는 단위 테스트입니다. 백엔드 CI는 Python 3.11로 연결 관련 테스트를 실행합니다. BLE를 포함한 전체 백엔드 테스트는 Linux의 `python3-dbus`, `python3-gi`, `python3-cryptography` 패키지가 필요하며, 시스템 Python으로 가상환경을 만들고 시스템 패키지를 함께 읽도록 설정합니다.

```bash
/usr/bin/python3 -m venv --system-site-packages backend/.venv
backend/.venv/bin/pip install -r backend/requirements-dev.txt
PYTHONPATH=backend backend/.venv/bin/python -m unittest discover -s backend/tests -v

python3 -m venv upload_receiver/.venv
upload_receiver/.venv/bin/pip install -r upload_receiver/requirements-dev.txt
PYTHONPATH=upload_receiver upload_receiver/.venv/bin/python -m unittest discover -s upload_receiver/tests -v
```

[Reliability CI](.github/workflows/reliability.yml)는 대상 소스가 변경된 PR과 `main` 푸시, 수동 실행에서 Android 빌드·lint·선별 회귀 테스트와 백엔드 연결 관련 테스트를 수행합니다. 전체 Python·Android 테스트는 위 명령으로 실행합니다.

## 운영 문서

| 문서 | 내용 |
|---|---|
| [백엔드 설치·운영](docs/BACKEND.md) | 설치·설정, QR/BLE, TLS/HMAC, 로컬 API, 업로드 대상 |
| [파이프라인 운영](docs/PIPELINES.md) | 여러 Jetson 배포, 작업 폴더·스냅샷, 센서 모니터, 시간·FAN 제어 |
| [외부 검지 작업 연동·AI 에이전트 가이드](docs/EXTERNAL_PIPELINE_CONTRACT.md) | 외부 코드 준비, venv·진입점 예제, 앱 등록, 센서 인계, 검증 기준 |
| [Wi-Fi Direct](docs/WIFI_DIRECT.md) | P2P 설치, 연결 상태, 점검과 장애 대응 |
| [업로드 서버](docs/UPLOAD_SERVER.md) | 수신·재개·라이브러리 API, HDD 저장, HTTPS 배포·복구 |
| [연결 진단](docs/DIAGNOSTICS.md) | 로그 수집·내보내기, 배포·원복 도구, 남은 검증 항목 |
| [앱 구조 재설계 계획](docs/design/APP_REBUILD_PLAN.md) | 탭 구성, 화면 규칙, 시각 규칙, 성공 기준 |
| [GEON Work 연동 제안](docs/integrations/GEON_WORK_INTEGRATION_PROPOSAL.md) | 장치 사건을 회사 메신저로 보내는 단계별 제안(검토 중) |

연결 안정성의 장시간·절전·실제 링크 장애 시험은 아직 남아 있습니다. 자동 테스트 통과와 실제 장비 배포·현장 검증 상태는 [연결 진단](docs/DIAGNOSTICS.md)에서 구분해 확인합니다.

문서는 현재 사용법과 유지할 계약을 중심으로 갱신합니다. 완료된 작업 지시서·날짜별 실행 기록은 Git 이력으로 확인하고, APK·진단 ZIP·로그·캐시·로컬 설정은 커밋하지 않습니다.

에이전트 작업 지침은 [AGENTS.md](AGENTS.md)에 있습니다. 작업에 필요한 문서·스킬·검증만 선택하며, 문서만 바꿀 때는 빌드 대신 diff와 참조 경로를 확인합니다. 지침 정리 근거는 OpenAI의 [Astra 스킬·프롬프트 개선 안내](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)입니다.

신규 화면·저장 경로·호환성은 [GEO& 사용 설명](docs/GEO_FIELD_FEATURES.md), 외부 실시간 검지 프로그램의 연결 계약은 [외부 파이프라인 연동](docs/EXTERNAL_PIPELINE_CONTRACT.md)에 설명되어 있습니다.
