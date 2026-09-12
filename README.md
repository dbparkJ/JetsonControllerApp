# GEO& 도로관리장치 제어

Android에서 NVIDIA Jetson을 등록하고 연결해 센서, 데이터 수집 작업, 저장소와 업로드를 관리하는 프로젝트입니다. Kotlin·Jetpack Compose 앱, Jetson 제어 백엔드, 외부 업로드 수신 서버를 함께 관리합니다.

## 주요 기능

- **장비 등록과 연결**: QR 기반 등록, BLE 인증·Wi-Fi 설정, LAN 자동 탐색, Wi-Fi Direct 연결과 복구 상태 표시
- **장비 상태**: CPU·GPU·메모리·온도·저장공간, 센서 상태, 카메라 프리뷰, GNSS 지도와 모바일 RTK 보정 중계
- **수집 작업**: Git 소스 스냅샷 등록, Python 가상환경 실행, 시작·중지·자동 실행 설정, YAML 편집과 실행 로그 조회
- **데이터 관리**: 장치 파일 탐색·미리보기·삭제, 외부 HTTPS 서버 업로드·재개·취소, 완료된 서버 데이터 조회
- **현장 화면**: GEO& 시작 화면, 실행별 작업 기록·GPS 경로, 날짜별 미디어 탐색, 장치·모바일 카메라 캡처, 개발자 터미널
- **운영 도구**: 시간 동기화, FAN 제어, 허용된 전원 명령, 알림 이력, 연결 진단 ZIP 내보내기

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
3. 저장소 루트에서 빌드·검증합니다.

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest \
  --max-workers=1 --console=plain
```

Windows에서는 `gradlew.bat`를 사용합니다. APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 연결된 테스트 기기의 UI·네트워크 시험은 `./gradlew :app:connectedDebugAndroidTest`로 별도 실행합니다.

### Jetson / Linux ARM64에서 빌드

[setup-mobile-build-env.sh](scripts/setup-mobile-build-env.sh)는 이미 준비된 SDK·Gradle·QEMU·AAPT2 실행 환경을 저장소의 `.mobile-build/`로 복사해 재사용합니다. 필요한 원본 도구가 설치된 환경에서 실행합니다.

```bash
./scripts/setup-mobile-build-env.sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest \
  --max-workers=1 --console=plain
```

원본 위치는 `ANDROID_SDK_SOURCE`, `GRADLE_HOME_SOURCE`, `QEMU_X86_64_SOURCE`, `AAPT2_SYSROOT_SOURCE`로 지정할 수 있습니다. `scripts/aapt2`는 이 환경의 QEMU를 사용하는 실행 도구입니다. Unix `gradlew`는 기본 Gradle 캐시를 `.mobile-build/gradle-home`에 보관하며, `GRADLE_USER_HOME`을 지정하면 해당 경로를 사용합니다.

### VWorld 지도 키

```bash
./scripts/configure-vworld-key.sh
```

도우미가 입력값을 화면에 표시하지 않고 Git에서 제외된 `local.properties`의 `vworld.apiKey`에 저장합니다. 읽기 우선순위는 Gradle 속성 `VWORLD_API_KEY` → 같은 이름의 환경변수 → `local.properties`입니다. 키는 APK에 포함되는 클라이언트 키이므로 배포 범위에 맞게 관리합니다.

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
| [Wi-Fi Direct](docs/WIFI_DIRECT.md) | P2P 설치, 연결 상태, 점검과 장애 대응 |
| [업로드 서버](docs/UPLOAD_SERVER.md) | 수신·재개·라이브러리 API, HDD 저장, HTTPS 배포·복구 |
| [연결 진단](docs/DIAGNOSTICS.md) | 로그 수집·내보내기, 배포·원복 도구, 남은 검증 항목 |

연결 안정성의 장시간·절전·실제 링크 장애 시험은 아직 남아 있습니다. 자동 테스트 통과와 실제 장비 배포·현장 검증 상태는 [연결 진단](docs/DIAGNOSTICS.md)에서 구분해 확인합니다.

문서는 현재 사용법과 유지할 계약을 중심으로 갱신합니다. 완료된 작업 지시서·날짜별 실행 기록은 Git 이력으로 확인하고, APK·진단 ZIP·로그·캐시·로컬 설정은 커밋하지 않습니다.

신규 화면·저장 경로·호환성은 [GEO& 사용 설명](docs/GEO_FIELD_FEATURES.md), 외부 실시간 검지 프로그램의 연결 계약은 [외부 파이프라인 연동](docs/EXTERNAL_PIPELINE_CONTRACT.md)에 설명되어 있습니다.
