# v3 원복 기록 — 2026-09-08

> 2026-09-08 04:46 UTC 최신 상태: 앱 업데이트 설치 및 실제 진단 마커·ZIP 저장/회수 PASS. Android191개·backend232개 자동 시험 PASS. 작업 브랜치 push 확인. Jetson 운영 배포는 BLOCKED이며 사용자 터미널용 명령을 준비했습니다. 실제 사용 중 끊김 원인은 미확정, T1–T9 완료0건입니다. 아래 시각이 앞선 미설치/미푸시 기록은 당시 상태로 보존합니다.

- 기존 사용자 v3 문서 및 이전 기록 보존. 작업 브랜치에서만 변경한다.
- 설치·배포·서비스 재시작·Doze/배터리 override·라우팅/무선 설정·장애 주입 없음. 앱 데이터/키/설정 변경 없음.
- 첫 기본 ADB 조회가 구버전 client/server 충돌로 기존 서버를 자동 재시작함. 이전 연결 목록을 복구했다고 주장하지 않으며, 사용자가 제공한 현재 endpoint로 별도 읽기 접속을 시도한다.
- 소스 revert는 APK/백엔드 런타임을 되돌리지 않는다. 새 산출물 설치는 구체 승인·기존 APK/split 해시/서명·데이터 호환성·검증 가능한 복귀 경로 확보 전 BLOCKED.
- Jetson 배포는 독립 관리 경로와 실제 코드/unit/config/소유권 백업 및 승인 전 BLOCKED. 비밀 설정을 git이나 진단 로그에 넣지 않는다.
- 추가 로컬 변경/테스트/수집 종료 및 원복 결과를 작업 종료 시 기록한다.

## 현재 변경과 보호한 baseline

| 변경 | H/R | 원복 범위 |
|---|---|---|
| `7776d65`: backend/jetson_control/wifi_direct.py, backend/tests/test_wifi_direct.py | H12 / R5,R15 | 소스 commit revert만; 현재 `/opt/jetson-control`에는 적용 안 됨 |
| `a1b8250`: JetsonRepository.kt, JetsonRepositoryStabilityTest.kt, app/build.gradle.kts test dependencies | H1,H2,H5 / R1–R4,R6–R8,R13 | Repository 복구/의도 및 테스트 전용 의존성; 저장된 앱 데이터 형식 변경 없음 |
| `e6cb95d`: LocalApiClient.kt, LocalApiClientReplayTest.kt, test-only PKCS12 | H13,H5 / R4,R8,R12 | 결과불명조회/무재전송/endpoint세대가드 |
| `5dd0735`: Repository/회귀 | H2 / R7 | 화면dispose가 수동 의도를 지우는 경로만 원복 |
| `a44a59c`: Client/회귀 | D1(H5/H13) / R8 | 최초 인증 deviceId 고정 가드, 저장된 인증정보/키 변경 없음 |
| `14e21b3`: Repository/회귀 | H1 / R3,R6,R12 | 자동복구 읽기한정·취소/중재 회귀; 초기 연결time-sync 유지 |
| `b1bdeb2`: Repository/회귀 | H13 / R9,R12 | UNKNOWN+같은pipeline RUNNING/STARTING 관측의 RTK 보존 |
| CI 선택목록, .gitignore, docs/diagnosis 3문서 | 증거·재현성 | 진단 기록은 보존하고 동작 source revert와 구분 |

초기 사용자 untracked v3 문서와 기존 docs 기록은 변경/삭제/커밋에 포함하지 않는다. 원격 branch/PR/merge/release 없음. backend auth/TLS/config/QR/device identity/파이프라인/원본 수집데이터/업로드 설정은 변경하지 않는다.

baseline 앱 백업은 `/home/jm/.local/state/jetson-stability-backups/20260908/baseline-v22.apk` (상위0700/파일0600, git 제외). 설치 base.apk와 정확한 SHA 일치 확인 후 기존 로컬 APK를 복사했다. SHA `49afd594a355d8c1a290b1f341c5ea079796a278fe1f391cbf834e16fef46334`, signing certificate SHA `7e78141e5f83032ca3092c0691df3ca2ef5ce3c3dbba18cabec3d8c7405cb66a`, version22. 관련 split 없음. 이 백업은 비밀이 들어갈 수 있는 앱 산출물로 취급하며 진단 로그에 내용을 넣지 않는다.

**설치 원복은 NOT RUN.** 이전 APK 보유만으로 설치가능을 주장하지 않는다. 변경된 시험 APK version22/동일 서명은 build 후 대조 완료했다(SHA `ae8f4ebc532364f47deac40e45dc1bca863322091f9c9824ea5f04b0948df3ed`). `adb install -r` 실제 설치·복귀는 승인/안전경로가 확보된 뒤 별도 검증해야 한다. 서명 불일치나 downgrade 제한을 uninstall/데이터삭제/키재등록으로 우회하지 않는다. 필요시 이전코드를 같은서명의 더높은 versionCode로 빌드하는 경로도 승인 후 검증한다.

Jetson은 **배포변경 없음**이므로 운영 백업/복원은 수행하지 않았다. 추후 배포할 경우 현재 `/opt/jetson-control` 파일별 baseline(혼합버전), 실제 unit/config/permission/소유권 및 현장 dirty 설정을 별도 보호위치에 백업해야 한다. 이 repository HEAD checkout만으로 현재 운영환경을 복원할 수 있다고 가정하지 않는다. NetworkManager profile export/변경, power_save/라우팅/서비스 재시작 없음.

## 승인 후 적용·원복 순서 (현재 NOT RUN)

1. 실제 시험 대상·운영 작업·USB ADB·독립 Jetson 관리경로 확인, 영향/시점/범위 구체 승인 기록.
2. 동일 서명 baseline/시험 APK/split 및 데이터 호환성을 대조하고, 검증 가능한 복귀 설치 경로 확보. backend 배포를 포함한다면 별도 backup/rollback 계획과 승인.
3. 승인된 범위만 적용하고 설치 APK hash·서명·실행 PID/build ID를 다시 확인. backend는 디스크·프로세스 로딩을 별도 대조.
4. 문제가 생기면 승인된 범위의 설치/배포를 원복하고 앱 데이터/키/설정·TLS/HMAC·동일장비 인증·실제 제어경로를 확인. 소스 revert만으로 런타임 복귀 완료라 쓰지 않음.

## 실제 정리 결과

- 운영 무선/라우팅/API/DHCP/RTK/프로세스에 장애 주입하지 않음. Doze force/배터리 override/권한 변경/서비스 restart 없음: unforce/battery reset 같은 불필요한 원복 명령도 실행 안 함.
- backend FakeRunner/임시디렉터리·loopback HTTPS fixture는 각 테스트 종료 때 종료/정리. 추가 패키지 설치나 venv/system 설정 수정 없음. 시스템 Python 모듈 경로 보정은 테스트 프로세스 종료와 함께 사라짐.
- PHONE_A END 마커와 Jetson END logger 요청 확인. Jetson START 누락/ADB stdout 보존불완전 제한은 DIAGNOSIS에 남김.
- **02:23:09 UTC 작업 전용 ADB server(port5040) 종료 exit0**, 소유 exec session1654 exit0. 5040 listener 없음 확인. 별도 진행 중 수집/예약 자동작업 없음.
- 초기 기본 ADB39 호출로 원래 server41이 자동 교체된 사실은 되돌렸다고 쓰지 않는다. 이전 세션을 소급 복원할 수 없어 최초 교란을 보존했다. 이후 기본5037은 다시 변경하지 않았고, 다른 세션의 서버를 임의로 종료하지 않았다.
- 원본 로그/수집데이터 삭제 없음. 보호 baseline APK는 남김(보관 목표7일, 자동 삭제 예약 없음). artifact 크기/비밀·git 제외/최종 소유 프로세스 확인은 최종 점검에 기록.

최종 점검 **02:37:02 UTC**: artifact 약0.9MiB로100MiB한도 이내, 검사한 실제 폰/Jetson주소·MAC의 저장 증거 노출0. 실제 비밀을 읽어 대조한 검사는 아니므로 모든 비밀의 절대 부재를 보증하지 않는다. parent/case 디렉터리0700, git제외 확인. 운영 배포 파일4개 hash는 baseline과 동일, API1398/P2P1399 active/NRestarts0. `final-manifest.json`에 source/APK/배포 hash·실행상태를 기록했다. 작업용 temporary staging patch도 제거했고 원격/설치 작업은 없었다.

검토/소스 원복 시 product commit을 의존관계 역순 `b1bdeb2 → 14e21b3 → a44a59c → 5dd0735 → e6cb95d → a1b8250 → 7776d65`로 revert하는 경로를 검토할 수 있다. 이 원복 자체는 실행하지 않았다. 기록/CI commit은 별도로 취급하고 baseline·실패 증거를 삭제하지 않는다. `a1b8250`에서 H1/H2를 묶은 이유는 같은 기존 복구owner가 사용자의 Direct 의도를 보존한 상태로 회복해야 하기 때문이다. 후속 독립 경계는 별도 commit으로 분리했다.


## 상시 진단 기록 추가분 원복 (2026-09-08)

이번 단계는 사용자 요청대로 앱 빌드 전까지만 수행했다. 신규 APK/설치/운영 배포/서비스 재시작/무선 설정 변경/장애 주입은 0회다. 새 ADB/SSH 수집 작업이나 예약 작업도 시작하지 않았다. 따라서 이번 단계의 운영 원복 대상은 없다. 백엔드 로컬 테스트 프로세스는 종료되었고 임시 fixture를 사용했다. 기존 baseline 앱 데이터·TLS/HMAC 키·설정·배포 파일을 보존했다.

소스 원복은 이번 진단 기능의 Android/Jetson 커밋만 작업 브랜치에서 각각 revert하여 검토한다. 이전 안정성 결함 수정이나 사용자의 첨부 v3 문서를 삭제하지 않는다. 로컬 진단 문서와 이미 회수한 증거는 소스 원복과 구분해 보존한다. git revert는 설치 앱/운영 프로세스를 되돌리는 절차가 아니다.

추후 설치/배포 뒤 이 기능을 되돌릴 필요가 있으면, 실제 배포 식별·앱 서명·기존 데이터·Jetson 독립 경로를 먼저 대조하고 승인받은 기존 APK/백엔드 파일로 원복한다. 앱 uninstall/데이터 초기화/키 재발급이나 운영 중 수집·RTK 중단을 자동 실행하지 않는다. 로그 파일만 정리할 경우에도 연결/장비 설정 전체 디렉터리를 삭제하지 않는다. 내보낸 ZIP은 사용자 소유 산출물이므로 별도 보존한다. 세부 저장 위치와 설치 경계는 [CONTINUOUS_LOGGING.md](CONTINUOUS_LOGGING.md)를 따른다.


## 빌드·설치 재개 단계 정리 (2026-09-08 04:04:49 UTC)

사용자의 무선 ADB 기반 앱 설치 승인은 확보했으나 접속이 거부되어 **설치0회**다. 원본 앱 데이터·Keystore 키·설정·baseline APK 및 운영 Jetson 배포는 변경하지 않았다. 예전 baseline 백업은 보존하지만 현재 단말과 재대조하지 못했으므로 이번 설치의 검증된 baseline으로 주장하지 않는다.

작업 소유 ADB5040 서버는 종료 exit0, 실행 session 종료 exit0 확인. 기본5037 서버에는 read-only device-list만 사용했으며 종료/재시작/연결 변경하지 않았다. 장애 주입·Wi-Fi/라우팅·Doze/배터리 override·서비스 재시작·앱 launch/force-stop·별도 장기 수집·예약 작업 모두0회다. [cleanup.json](../../artifacts/20260908-install-logs/cleanup.json)에 기록했다. 로컬 HTTPS/JVM 테스트는 종료되었고 합성 fixture만 사용했다.

현재 새 APK와 실패/통과 증거는 보존한다. 제품 진단 컴파일 보정 및 시험 수정은 `d15726d` 한 커밋으로 검토 가능하다. 단말 재접속 시 실제 설치 앱/활성 세션/동일 서명/현재 baseline 백업을 다시 확인한 후 이미 승인된 `install -r` 범위로 진행한다. uninstall·데이터 초기화·키 재발급으로 문제를 우회하지 않는다. 운영 원복을 수행했다고 주장하지 않는다.


## 실제 앱 설치 후 보존·정리 및 Jetson 수동 원복 준비 (04:44–04:46 UTC)

사용자 승인 범위의 앱 `install -r` 1회는 완료했다. 기존 versionCode22 APK는 `/home/jm/.local/state/jetson-stability-backups/20260908/baseline-v22.apk`에 전체 파일로 보존하며 현재 설치 전 APK SHA `49afd594a355d8c1a290b1f341c5ea079796a278fe1f391cbf834e16fef46334`와 일치한다. 새 SHA는 `56c0649bbd75f170f7f2e8469bd389616fa6f19717238ef7bcf85092fed91890`, 서명은 동일하다. 부분 재전송 파일은 별도 INCOMPLETE로 기록한 후 제거했다. 새 앱이 정상 실행돼 baseline APK 재설치는 실행하지 않았다.

설치 직후 DataStore3개 해시는 그대로였다. 앱 실행 후 credential 파일은 기존 BLE 재인증 재암호화 경로와 부합하는 변경이 관측됐지만 secret 평문/Keystore 추출 비교는 하지 않았다. 등록2개와 암호화 형식은 확인했다. uninstall/데이터 clear/키 재발급·credential 원시 파일 백업/비밀 로그 저장은 수행하지 않았다. rollback 때도 `install -r`로 데이터 보존 업데이트하고, 최신 endpoint와 동일 서명/현재 운영 상태를 재확인해야 한다. 이전 APK 설치만으로 과거 실행 중 설정값까지 되돌렸다고 주장하지 않는다.

Jetson 코드/설정/unit/키/운영 서비스에는 변경0회, 자동 또는 수동 원복0회다. [수동 배포 README](../../scripts/diagnosis/README.md)에 지금 실행 가능한 read-only preflight와 안전 조건 확보 후 apply/rollback 명령을 구분했다. 스크립트는 정확한6파일의 기존 상태·권한·mtime·서비스 상태를 백업하고 전체 rollback 해시/대상을 중단 전에 검사한다. 실제 운영 백업 디렉터리는 아직 생성하지 않았다. 손상된 baseline·알 수 없는 운영 상태를 무시하는 플래그는 제공하지 않는다. 원래 API `storage-roots.conf` drop-in과 나머지 설정·키를 보존한다. 실제 장애 시 자동 원복 성공을 검증한 것은 아니다.

**04:44:46 UTC 소유 ADB5040 서버 종료exit0, 소유 실행 session 종료exit0, 5040 listener 없음**을 확인했다. 전화기의 작업 전용 UI XML은 없고, incomplete APK 파일도 없다. 실제 내보낸 ZIP은 폰 Downloads와 로컬 증거에 보존하며 앱의 상시 진단 기능은 의도대로 실행 상태에 둔다. 이 제품 기능은 종료해야 할 임시 수집 작업이 아니다. 별도 수집/장애 주입/예약 작업은 없다. 기본ADB5037, Wi-Fi/라우팅, Doze/배터리 override, RTK/pipeline 및 운영 서비스를 변경하지 않았다.

종료 조회에서 잘못된 이름 `jetson-control-p2p.service`의 inactive를 잠시 수집했으나 LoadState=not-found를 확인해 보정했다. 실제 unit `jetson-wifi-direct.service` 재조회는 PID1399 active/running·NRestarts0이며 API1398도 동일하다. 이 오조회는 실제 P2P 중단 사건이 아니다. 수집 보정 이력은 [cleanup.json](../../artifacts/20260908-deploy-logs/cleanup.json)에 남겼다. 현재 증거 디렉터리는0700, 파일0600으로 제한하고 git에서 제외했다. 보호 baseline 및 실패 증거를 삭제하지 않았다.

작업 브랜치 push는 명시 승인 후 수행했다. 운영 코드 배포와 git push는 별개다. 원격 main 변경/PR/merge는0회이며 원격 작업 브랜치를 삭제하거나 force-push하는 원복은 수행하지 않았다. 사용자 첨부 v3 문서는 기존 untracked 상태로 보존했다.
