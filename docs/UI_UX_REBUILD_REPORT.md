# Cobalt Field V5 구현·빌드 보고서

## 범위와 기준

2026-09-08 사용자는 후속 메시지에서 **Jetson 장비가 함께 없으므로 앱 디자인과 빌드 목적까지**로
범위를 조정했다. 따라서 실제 Jetson 작업 시작·중지, 테스트 업로드, RTK 중계,
전원·네트워크 변경 실험은 수행하지 않았다. 기능 연결과 모의 검증을 실장비 통과로 간주하지 않는다.

- 지시 파일: `/home/jm/ControllerApp/JetsonControllerApp/jetson_cobalt_v5/JETSONCONTROLLER_COBALT_FIELD_V5.md`
- 브랜치: `codex/connection-stability-v3-20260908`
- 시작 HEAD: `f2113afd1300a1f8b1f5a00eabee7a569c2d727c`
- 저장소 및 상위 디렉터리에서 적용되는 AGENTS.md 없음.
- 시작 상태: `evidence/cobalt-v5/baseline.json`. 리셋·stash·commit·push·배포 없음.
- 기존 변경: backend diagnostics.py, wifi_direct.py, test_wifi_direct.py, docs/WIFI_DIRECT.md 모두
  시작 SHA-256과 동일. 사용자 미추적 문서와 V5 패키지도 보존.

## 구현

청록 5탭 UI를 네이비·코발트·아이스 블루·오프화이트의 **홈 / 작업 / 데이터 / 설정**으로 변경했다.
기존 Repository·인증·연결 복구 엔진을 재사용했다. 네트워크 백엔드는 수정하지 않았다.

홈은 현재 작업 카드를 먼저 보여주고 준비 상태, 카메라/GNSS, 데이터 진입을 제공한다.
예시 실행 시간·파일 개수·남은 GB를 제품에 넣지 않았다. 마지막 작업 상태와 확인 시각을 표시하고
오래된 상태를 현재 실행으로 단정하지 않는다. 팬·전원·지표는 설정으로 이동했다.

작업은 목록과 상세를 분리하고 시작 전 capability/시간 동기화 안내를 추가했다.
목록에서 부팅 토글·등록 해제·로그를 제거한 대신 상세에서 기존 기능으로 연결한다.
요청 pending과 원격 상태를 분리하고 폴링 결과로 조정한다. 연속 클릭을 ViewModel에서도 차단한다.
진행 중 명령을 새로고침으로 취소하지 않도록 했으며 편집 재진입 시 작성 중 등록 초안을 유지한다.

데이터는 실제 API에 맞춰 **폴더 한 개 선택**을 제공한다. 신규 자동 선택은 없고 검색에 가려진
선택을 표시한다. 장비별 선택·검색은 SaveableStateHolder로 분리한다. 확인 화면은 장비·경로·용량·
대상 서버를 표시하고 장비 변경 시 닫는다. 용량·출처 일치 검사와 LAN 제한을 유지한다.

설정은 네트워크·센서·팬·서버·보관함·진단·알림·테마·위험 동작을 연결하는 실제 허브다.
위험 확인창은 대상·영향·구체적인 확정 동작을 표시하고 장비 변경 시 초기화한다.
테마 선택은 앱 로컬 preference이며 Repository 연결을 재구성하는 효과를 추가하지 않았다.

영상 LIVE는 실제 프레임·오류·최신성으로 판정한다. 화면 이탈/장비 전환 시 앱 미리보기
상태와 스트림을 정리하며 Jetson 수집 중지 명령을 보내지 않는다. GNSS FIX/FLOAT/미확인과
RTCM 중계를 구분하고 측정하지 않은 1 Hz 표현을 제거했다.

최종 색·타이포·컴포넌트·조정 근거는 [UI_UX_DESIGN_SYSTEM.md](UI_UX_DESIGN_SYSTEM.md)에 있다.

## 기능 연결표

| 기능 | 코드 연결/이동 | 실제 Jetson 검증 |
|---|---|---|
| 장비 등록·QR·수동 코드·인증 | 기존 onboarding/pairing/credential 경로 보존 | 이번 범위 제외 |
| 연결 복구·LAN/Direct/BLE | 기존 Repository/TransportCoordinator/정책 보존 | 이번 범위 제외 |
| 홈·작업 상태 | Repository pipeline 목록, 관측 시각, pending mapper | 이번 범위 제외 |
| 작업 시작·중지·재시작 | 기존 time sync / controlPipeline, 시작 전 확인 | 이번 범위 제외 |
| 작업 등록·설정 초안 | 기존 ViewModel workspace, 재진입 초안 초기화 방지 | 이번 범위 제외 |
| 작업 로그·부팅 설정·등록 해제 | INTENTIONALLY_RELOCATED: 작업 상세 | 이번 범위 제외 |
| 장비 파일·서버 파일 | 기존 탐색·미리보기·안전 삭제 경로 | 이번 범위 제외 |
| 폴더 선택·전송 확인 | 기존 source summary / startUpload | 이번 범위 제외 |
| 전송 진행·검증·재시도·이력 | 기존 UploadViewModel/화면 | 이번 범위 제외 |
| 센서·카메라·GNSS·RTK | INTENTIONALLY_RELOCATED: 홈/설정, 기존 기능 보존 | 이번 범위 제외 |
| 팬·재부팅·종료 | INTENTIONALLY_RELOCATED: 설정, 기존 capability/Repository | 이번 범위 제외 |
| 알림 이력·임계값·권한 | 헤더 이력 + 설정의 기존 알림 화면 | 해당 없음 |
| 밝게·어둡게·시스템 | Compose 전체 의미 토큰 + preference | Android 화면 검수 별도 |
| 정상 상태 시안 갤러리 | PREVIEW_ONLY: debug source set, Repository 없음 | 실측값 아님 |

## 자동 검사

최종 결과: Gradle 종료 코드 **0** (`evidence/cobalt-v5/completion-build.log`).
Debug APK / lintDebug / JVM 테스트 / Android 테스트 APK 빌드 성공.
JVM 테스트 **196개, 실패 0, 오류 0, skip 0**.
Lint 오류 0개, 경고 73개. 기존 detector 제외 목록과 SDK/의존성 버전은 변경하지 않았다.
이전 실패/중간 로그는 최종 통과 근거로 사용하지 않는다.
`verification-summary.json`, `unit-results.json`에 구조화한 결과와 APK SHA-256을 남겼다.

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest \
  -PtestApplicationId=com.example.jetsoncontroller.cobalt.test --console=plain
```

최종 스마트폰 Compose UI 테스트는 **14개, 실패 0개** (`native-verified.log`, 24.543초).
폴더의 기본 미선택, A→B→A 선택 복귀, SavedInstanceState 복원과 360dp/2배에서
화면 밖 폴더를 스크롤해 선택하는 동작도 통과했다. 캡처 24장을 최종 테스트로 갱신했다.
이는 실제 Repository/장비가 없는 UI fixture 검사이며 Jetson 기능 수용 테스트가 아니다.

추가 검사: 작업 관측 시간의 경계, pending 표시/조정, 연속 시작 입력+새로고침의 요청 1회,
A→B→A 초안 분리/복귀, 새 프레임 없는 LIVE 금지, 센서 상태 표현.
기존 Repository 안정성·늦은 응답·재연결·인증·전송 정책 테스트도 실행한다.

색 토큰은 Light/Dark 46개가 제공 JSON과 일치한다. 대표 대비 24쌍 중 23쌍은 4.5:1 이상,
Light의 disabled/onDisabled 쌍은 4.10:1이다. 비활성 역할의 제공 값을 유지했으며 전체 접근성
통과나 모든 텍스트 대비 통과로 보고하지 않는다. `token-check.json`에 계산값을 남겼다.

## Android 및 캡처 증거

사용자 지정 Android 대상만 사용했다. 모델 SM-S908N, Android 16, 1440×3088,
물리 density 600 / 기존 override 560, 기존 앱 1.15.3(versionCode 22).
다른 ADB 기기에 설치하거나 테스트하지 않았다.

- [변경 전 장비 목록](evidence/cobalt-v5/before-devices.png): 실제 기존 앱.
- [변경 전 오프라인 홈](evidence/cobalt-v5/before-home.png): 실제 기존 앱. 장비 연결 성공 캡처가 아님.
- 변경 후 실제 제품 앱(장비 미연결): [홈](evidence/cobalt-v5/after-home.png),
  [작업](evidence/cobalt-v5/after-tasks.png), [데이터](evidence/cobalt-v5/after-data.png),
  [설정](evidence/cobalt-v5/after-settings.png), [장비 목록](evidence/cobalt-v5/after-devices.png).
  이 캡처에는 demo 수치를 사용하지 않았다. 캡처 당시 시스템 테마/상태바를 그대로 포함하며,
  변경 전 캡처와 동일 조건의 성능 비교 자료가 아니다.
- 앱 APK는 `install -r`로 데이터 유지 업데이트했다. 등록/인증 저장 파일 SHA-256 전후 일치
  (`credentials-before.sha256`, `credentials-after.sha256`). 비밀값은 출력/기록하지 않았다.
- 기존 `.test` 패키지는 서명이 달라 업데이트 실패했다. 기존 테스트 설치도 지우지 않고
  `testApplicationId` 속성으로 별도 `.cobalt.test` 패키지를 빌드하도록 했다.
- 새 정상 화면은 `CobaltGalleryActivity`의 debug 전용 Compose fixture다. release에 포함되지 않는다.
  `CobaltFieldScreenTest`는 360dp·1배/2배·밝게/어둡게 24화면 캡처와 시작 확인 취소/장비 변경을 검사한다.
  2배 LocalDensity는 Compose 스트레스 검사이며 Android OS 비선형 최대 글꼴/TalkBack 검증과 다르다.

### 새 캡처와 실행 UI 검사

초기에 잠금 화면과 기존 테스트 패키지 서명 불일치로 실행이 막혔다. 이후 사용자의 스마트폰
잠금 해제와 별도 `.cobalt.test` 패키지 설치로 진행했다. 기존 테스트 앱은 삭제하지 않았다.
초기 서명 실패 로그는 `native-design-tests.log`, 실제 성공한 실행 로그는 별도로 구분한다.

- `native-verified.log`: 최종 Compose/기존 화면/확인 취소/장비 전환/선택 복원 검사.
- `native-regressions.log`: 앞선 CoreWorkflow/NetworkSettings 화면 11개 테스트 성공.
- `native-completion.log`의 LazyColumn 화면 밖 항목 조회 실패는 테스트의 스크롤 검색으로 수정했다. 최종 로그와 구분한다.
- `native-fixtures/`: 실제 Android에서 렌더링한 **24개 debug fixture 캡처**.
  홈·작업·상세·데이터·설정·오프라인 × 밝게/어둡게 × 1배/2배.
- [홈 밝게](evidence/cobalt-v5/native-fixtures/home-light-1.0.png),
  [홈 어둡게](evidence/cobalt-v5/native-fixtures/home-dark-1.0.png),
  [작업](evidence/cobalt-v5/native-fixtures/tasks-light-1.0.png),
  [데이터](evidence/cobalt-v5/native-fixtures/data-light-1.0.png),
  [설정](evidence/cobalt-v5/native-fixtures/settings-light-1.0.png),
  [큰 글꼴](evidence/cobalt-v5/native-fixtures/data-dark-2.0.png).

fixture 캡처는 정상 시안 상태의 **실제 Compose 렌더링**이다. 제품 앱이 Jetson에서 실제
수집 중이라는 증거가 아니다. 360dp·2배 검사는 Compose LocalDensity 스트레스 검사이며
OS 비선형 최대 글꼴·TalkBack·실기기 명령 검증과 구분한다.

### 성능 증거 범위

APK 용량과 SHA-256만 전후 측정했다(`verification-summary.json`). 시작 시간·프레임 속도·메모리·
영상 이탈 후 자원·추가 네트워크 요청 수는 동일 조건 실측하지 않았고 개선율을 주장하지 않는다.
앱 UI 변경에 새 네트워크 polling loop나 map/chart 라이브러리를 추가하지 않았다.

## Figma

알려진 초안 키 `DhyCybqq65CPBwomkElI9r`, 페이지 `0:1`의 실제 metadata 조회를 시도했다.
Starter MCP 호출 한도 오류로 실패했다. 따라서 현재 파일 내용·V5 생성·렌더링·PNG export는
확인하지 못했다. 로컬 figma-plugin과 tokens/PNG는 구현 참고 자료로 사용했으며
Figma에서 생성·내보내기 완료라고 보고하지 않는다. Claude 링크 재접속은 선행 조건으로 삼지 않았다.

## 범위 밖/미검증 사항과 지시 MD

실제 Jetson 수집·전송·RTK·영상·복구·전원·네트워크·장비 두 대 연동 및 성능 비교는
사용자 범위 조정으로 제외했다. 실제 작동 통과라고 보고하지 않는다.
OS 최대 글꼴의 비선형 확대, TalkBack 포커스, 프로세스 종료 후 모든 설정 초안 복원,
실장비 연결 손실 중 명령 확인·영상 자원 실측도 이번 디자인/빌드 결과만으로 인증하지 않는다.
기존 장비별 ViewModel workspace는 세션 내 복원이며 모든 초안의 영구 저장 계약 완성을 뜻하지 않는다.

원래 문서의 전체 완료 조건(실제 장비 수용 검증 포함)은 충족하지 않았으므로 **처음 기록한 V5 지시 MD는 유지**한다.
이번 디자인/빌드 범위의 산출물과 원래 전체 완료 판정을 분리한다.
