# Jetson Controller · Cobalt Field V5

최신 기준은 `jetson_cobalt_v5/COBALT_FIELD_STYLE_GUIDE.md`, `design/tokens.json`,
`screens/`의 로컬 시안이다. 제공 PNG는 Chromium 시안이며 Figma export나 앱 캡처가 아니다.
제품 구현은 Kotlin / Jetpack Compose다. 시안 PNG·HTML을 제품에 포함하지 않는다.

## 색과 크기

| 역할 | Light | Dark |
|---|---|---|
| 배경 | #F7F5F2 | #101827 |
| 표면 | #FFFFFF | #182338 |
| 주 행동 | #2456D8 / #FFFFFF | #9CBBFF / #10265A |
| 선택 | #DCE7FF / #173B82 | #DCE7FF / #173B82 |
| 현재 작업 | #172B4D / #F6F8FF | #203553 / #F6F8FF |
| 확인된 성공 | #384B92 / #EEF0FF | #C2CBFF / #303954 |
| 주의 | #8A4E19 / #FFF0DD | #FFD39A / #473620 |
| 위험 | #AE382C / #FCECE8 | #FFB8AC / #4A2B2A |

모든 의미 색은 `ui/theme/Color.kt`의 CobaltLight/CobaltDark에 모은다.
Material 3의 container, on-container, surfaceContainer, error 역할도 명시한다.
성공·실행·로그·영상에 초록/청록/라임을 사용하지 않는다. 상태는 문구를 함께 표시한다.

화면 제목 26/36sp, 핵심 제목 24/34sp, 그룹 제목 20/28sp, 본문 16/24sp,
보조 본문 14/21sp. 시스템 SansSerif를 사용하며 폰트를 추가 번들하지 않는다.
화면 여백 20dp, 카드 16~20dp, 일반 radius 20dp, 핵심 radius 24dp.
주요 행동은 최소 52dp. 아이콘 터치 영역은 Material 기본 48dp.
헤더는 고정 높이 TopAppBar 대신 내용에 맞게 늘어난다. 기본 글꼴에서는 준비 상태를 2열로, 큰 글꼴에서는 타일·행동을
세로로 쌓아 핵심 정보와 접근 가능한 스크롤을 우선한다.

## 구조와 재사용

- 홈: 선택 장비 → 현재 작업 → 준비 상태 → 센서/카메라/GNSS → 전송 이력.
- 작업: 간결한 상태 카드 → 시작 전 확인 또는 상세. 로그·부팅 시 실행·등록 해제는 상세.
- 데이터: 전송 대기 폴더 선택 → 용량·대상 확인 → 기존 업로드 진행/검증/이력.
  장비 파일과 서버 보관함은 기존 탐색 기능으로 연결한다.
- 설정: 네트워크·센서·팬·서버·진단·시스템 지표·알림·테마·위험 동작.

`DeviceContextHeader`, `ControlNavigationBar`, `StatusBadge`, `AppBanner`,
`TaskStateCard`, `ReadinessTile`, 기존 FanControlCard/MetricsGrid를 사용한다.
센서는 하단 탭에서 제거했으며 홈과 설정에서 접근한다. 알림은 루트 공통 헤더에서 연다.
테마는 앱 로컬 preference에 저장하고 네트워크 설정을 변경하지 않는다.
상태 보존은 옵션으로 노출하지 않는다.

## 실제 코드에 맞춘 조정

| 관찰 | 채택한 표현/동작 | 사용자 영향 |
|---|---|---|
| 업로드는 rootId/path/targetId 단일 원본 경로 | 새 선택 UI는 폴더 한 개; 파일 임의 묶음 없음 | 선택한 폴더 전체만 전송 |
| 백엔드는 FILE/DIRECTORY 원본을 처리하지만 기존 UI 진입은 폴더 | 폴더 선택을 유지; 임의 파일 다중 선택 API를 만들지 않음 | 전송 단위가 명확함 |
| 서버 사전 접근 확인 API 없음 | 접근·인증은 전송 시 확인이라고 안내 | LAN을 서버 인증 성공으로 오인하지 않음 |
| LAN만 서버 업로드 시작 가능 | 기존 TransportPolicy와 Repository를 보존 | Direct/BLE에서 제한 이유 표시 |
| 모바일 RTK는 RTCM 중계 | 파일 업로드·GNSS FIX와 별도 표시 | RTCM 바이트를 파일 전송량으로 보이지 않음 |
| pipeline 상태 폴링 5초 | 세 번의 폴링 간격(15초)을 넘으면 현재 상태 미확인 | 끊김 후 작업 없음/실행 중 단정 방지 |
| 제어 응답은 접수와 실제 실행을 구분해야 함 | pending 문구 유지, 다음 상태 조회로 조정 | 중복 클릭·새로고침 재명령 방지 |
| 카메라 센서 active만으로 실제 프레임을 보장하지 않음 | 받은 프레임·오류·4초 최신성 검사; 미수신 LIVE 금지 | 마지막 영상과 현재 수신 구분 |
| RTK FIX 미확인은 전원 꺼짐을 뜻하지 않음 | FIX 미확인/FLOAT/FIX 확인 문구 | 장치 전원·보정·측위 품질 혼동 방지 |
| API에 작업 경과 시간·저장 파일 수 보장 없음 | 홈에서 예시 시간/개수 생략, 실제 상태 확인 시각 표시 | 가짜 진행률·ETA·정상값 없음 |

위 최신성 임계값은 기존 폴링/long-poll 주기에 근거한 UI 정책이며 실제 장비 성능 측정값이 아니다.
위험 확인창은 대상 장비와 중단 영향을 표시하고 장비 변경·취소 시 명령을 보내지 않는다.

## 디버그 디자인 검수

`src/debug/.../CobaltGalleryActivity.kt`는 Repository가 없는 Compose 시안 갤러리다.
`DEBUG 시안 · 장비 통신 없음`을 표시하며 release 소스 세트에 존재하지 않는다.
화면: home, tasks, detail, data, settings, offline. dark / fontScale 인자로 검수한다.
`src/androidTest/.../CobaltFieldScreenTest.kt`의 캡처는 실제 Android Compose 렌더링이지만
그 안의 정상 상태·장비명·수치는 테스트 fixture다. 실제 Jetson 관측값과 구분한다.
