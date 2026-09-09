# Jetson Controller · Slate Harmony V7

최신 색상 기준은 [V7 토큰](../jetson_slate_harmony_v7/design/tokens.json)과
[V7 스타일 가이드](../jetson_slate_harmony_v7/SLATE_HARMONY_STYLE_GUIDE.md)다.
V5 문서는 이전 정보 구조의 이력이며 활성 색상 기준이 아니다.
제품 구현은 기존 Kotlin / Jetpack Compose이고 PNG·HTML을 화면으로 붙이지 않는다.
연구는 설계 참고이며 이 HEX의 최적성·피로 감소·사용자 선호를 입증하지 않는다.

## 색과 크기

| 역할 | Light | Dark |
|---|---|---|
| 바탕 | #F4F5F7 | #1C1E22 |
| 기본 표면·확인창 | #FBFCFD | #25282D |
| 준비·장비·목적지 | #EBEEF1 | #303339 |
| 결과·최근 이력·앱 설정 | #E1E5EA | #393D44 |
| 현재 작업 / 본문 | #E0E7F1 / #272C32 | #333B48 / #E4E6EA |
| 주요 버튼 / 전경 | #526584 / #FFFFFF | #9EACC1 / #1B222E |
| 선택 / 전경 | #D3DEF1 / #38465B | #3E4859 / #DDE2E9 |
| 입력·아웃라인 경계 | #717884 | #868D97 |
| 포커스 | #556887 | #AAB8D0 |
| 성공 / 배경 | #525862 / #EBEEF1 | #B5BBC5 / #303339 |
| 주의 / 배경 | #7A5729 / #F5EDDF | #D6B88A / #3A3126 |
| 위험 / 배경 | #9B4B46 / #F8ECEA | #E0AAA4 / #3C2C2D |

정확한 66개 역할은 V7 JSON과 `ui/theme/Color.kt`에 있다. 기존 CobaltLight/CobaltDark,
LocalCobaltColors 이름을 유지해 공급자를 중복하지 않았다. 앱 테마 preference와 상태 소유자는 유지한다.
일반 tertiary는 중립 raised 역할이고 실제 경고는 warning/warningBg를 직접 사용한다.
기본 모달의 surfaceContainerHigh는 sectionBase에 매핑한다. 명시적 섹션 색을 유지하도록
Material3 tonal elevation을 끄며 동적 색상은 사용하지 않는다.

`Controls.kt`는 기존 Material 버튼의 callback·semantics·enabled를 전달하고 색상과 내부 포커스
표시만 적용한다. 불투명 disabled/onDisabled를 사용한다. 입력은 surface 바탕과 controlBorder,
focusRing을 쓴다. 선택 배경에서는 onAccent를 사용한다. 대비 검사는
`scripts/check_slate_harmony.py`로 수행하되 실제 화면 검증으로 간주하지 않는다.

카메라/QR 영상의 검정·흰색 오버레이는 영상 위 가독성 때문에 유지한다. 지도 J/M 마커는
고정된 V7 Light primary/warning과 흰 테두리·문자 쌍을 사용한다. 외부 지도 픽셀 대비는
이번 토큰 검사에 포함되지 않으며 위치 추적·RTK 동작은 변경하지 않는다.
짧은 그룹에 SectionSurface를 사용하고 기존 LazyColumn 항목·키·장비별 저장 상태를 유지한다.

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
