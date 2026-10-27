# R8 2단계 · 화면별 적용 안내

기준은 [STYLE-R8.md](STYLE-R8.md)의 B안이다. `ui/components`, `ui/theme`, `ui/navigation`, `ui/JetsonApp.kt`는 2단계 수정 대상이 아니다. 아래 이름은 모두 구현된 공통 API다. 기존 상태·명령·권한·확인·ID·탭 스택은 보존한다.

## 공통 연결

```kotlin
GeoLargeTitleScaffold(
    title = "장치",
    primaryAction = GeoButtonAction("장치 등록", onRegister),
    actions = { GeoIconButton(GeoIcons.bell, "알림", onAlerts) }
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            GeoGroupedSection("센서", Modifier.padding(horizontal = 24.dp), first = true,
                trailingColumn = GeoRowTrailing.Status) {
                GeoRow("카메라", onCamera, meta = cameraDetail,
                    leading = { GeoIconTile(GeoIcons.camera) },
                    badge = { GeoBadge(cameraWord, cameraTone) }, divider = false)
            }
        }
    }
}
```

- `GeoIcons`의 실제 아이콘 이름을 사용한다. 알림은 `GeoIcons.bell`이다.
- 루트는 뒤로 인자 없이, 상세·흐름은 `onBack`을 전달한다. 기존 `DeviceTitleBar`·`WorkRootHeader` 같은 화면 전용 상단바는 제거하고 큰 제목 스캐폴드로 한 번만 감싼다. 본문은 LazyColumn/verticalScroll 하나를 소유한다. `GeoLargeTitleHeader`+`rememberGeoLargeTitleState`는 별도 머리 구성이 필요한 경우 사용한다.
- 두 칸은 기존 `GeoPaneLayout`을 유지한다. 바깥24·위8·아래24는 이미 적용된다. `LocalGeoPane.current`가 true면 화면 바깥 gutter를 추가하지 않는다. 각 칸은 자신의 제목·스크롤·행동을 소유한다. 작업의 주 행동은 왼쪽 아래다.
- `GeoRowList`는 반경20·surface·패딩0이다. `GeoRow`가 좌우16을 갖는다. 기존 카드의 안쪽16을 제거한다. 기본 아이콘은24, 사진은40과 `leadingIsPhoto=true`. 선택 목록은 `GeoPaneListRow`를 쓴다. 일반 묶음 머리말은 `GeoGroupedSection`, 큰 섹션 제목은 `GeoSection`이다.
- 오늘 정보는 `GeoTodayCard`, 그 행동은 스캐폴드 `primaryAction`으로 분리한다. 기존 `GeoWorkCard(action)`는 호환용으로 버튼이 카드 바깥 본문에 붙으므로 새 화면에서 사용하지 않는다. 채워진 주 행동은 화면당 하나다.
- 가로 목록은 전체 pane 폭에 `GeoHorizontalTiles`/`GeoFilterChipRow`를 두고 기본 contentPadding24만 쓴다. 바깥에서 다시 좌우24로 자르지 않는다. pane 안에서는 contentPadding을0으로 전달할 수 있다. `GeoQuickActions`는 폰 가로 스냅/패드3열을 자동 선택한다.
- 네 지표는 `GeoMetricStrip`; 일반 여러 지표는 `GeoFigureGrid`. 원인·오래된 값·수신 미확인은 기존 모델에서 전달한다. 상태 문구를 직접 성공으로 바꾸지 않는다.
- ID·파일명·주소·오류 코드는 `GeoCopyableValue`. 입력은 `GeoTextField(inputKind=SERVER_ADDRESS/PORT/CODE/IDENTIFIER/SEARCH, imeAction=Next/Done)`와 해당 KeyboardActions를 연결한다. 명시적 keyboardOptions는 목적과 충돌하지 않게 맞춘다.
- 오버레이는 `GeoSheet`, `GeoStartSheet`, `GeoConfirmDialog`, `GeoInfoDialog`로 유지한다. primary footer를 본문 스크롤 안에 넣지 않는다. 기본 내부 스크롤과 새 verticalScroll/LazyColumn을 같은 세로 제약 없이 중첩하지 않는다. actions 슬롯은 버튼만 구성하고 오버레이 자체는 화면에서 한 번 구성한다.
- 새 클릭은 `Modifier.geoClickable`, 탭은 `geoSelectable`; 기존 selectable/toggleable을 유지해야 하면 같은 interactionSource에 `geoPressFeedback`을 붙인다. 조작 글자는 선택하지 않는다. 추가 hover는 정밀 Mouse 포인터에만 허용한다.

## 1. 작업 담당

대상: `ui/tasks`, `ui/survey`, `ui/field`, `ui/zone`, `ui/map`.

- 작업 홈: 큰 제목 → TodayCard → DevicePhotoCard/MetricStrip → 빠른 기능 → 최근 수집/구간 묶음. 패드 가로는 왼쪽520에 오늘·장치·준비, 오른쪽608에 빠른 기능3×2·목록. 같은 구간/주 행동 중복을 제거한다.
- 구간/작업 상세: LargeTitleScaffold+PropertyList+지도 미리보기, 준비 primaryAction. 준비 화면은 점검·입력·프로젝트 선택을 유지하고 시작은 GeoStartSheet footer에 둔다.
- 진행: 폰은 GeoMapSheetScaffold로 지도 전체와 시간·용량·상태·종료를 연결한다. 패드의 왼쪽 제어/오른쪽 지도에서는 종료를 왼쪽 아래에 고정한다. 셸은 run의 폰 배경에 상태 inset을 예약하지 않는다. 지도 조작은 자체 safeDrawingPadding을 적용한다.
- 결과/기록: Receipt와 CopyableValue, 보고/파일 보내기 아래. 품질/중계 정보는 INFO 시트·정보 대화상자, 종료는 기존 대상 확인을 유지한다.
- zone 관찰·지도 provider·상태 계산은 시각 배치에 필요한 부분만 바꾼다. 개발자 루트 `field/DeveloperScreen`도 묶음 목록으로 적용한다.

## 2. 장치 담당

대상: `ui/devices`, `ui/sensors`, `ui/pairing`, `ui/onboarding`, `ui/connection`, `ui/diagnostics`, `ui/network`, `ui/wifi`.

- 장치 홈: 사진40 선택 행+확인할 일+주변 장치 묶음. 독립 등록/재연결은 GeoActionRow. 상세는 큰 이름·MetricStrip·센서/장치 정보 묶음, ID는 CopyableValue. 위험 관리 흐름 유지.
- 카메라: GeoImmersiveMediaFrame에 기존 영상·freshness·권한 상태를 연결한다. `captureAction`은 기존 캡처 실행, `captureEnabled`는 기존 지연 판정. 조작은 GeoMediaIconButton; 저장 위치는 기존 시트. 영상 바깥 상단바/조작 열을 제거한다. 패드 주 탐색은 셸이 왼쪽 아래에 제공하므로 중복 생성하지 않는다.
- QR: 같은 미디어 틀·safe 조작, 수동 입력은 CODE/IDENTIFIER. 등록/연결은 큰 제목·상태 행·하단 행동/시트 footer. 연결방법·직접 연결·Wi-Fi 적용 확인 대상을 유지한다.
- 네트워크·진단: GroupedSection, TextField, PropertyList, CopyableValue. 오류 코드를 선택 가능하게 하고 연결 조작은 입력 오류/가용성 판단을 유지한다.

## 3. 전송 담당

대상: `ui/transfers`, `ui/upload`, `ui/storage`.

- 전송 홈: 큰 제목·FilterChipRow·진행 정보·확인 필요 ActionRow·보낼 수집 묶음. 파일 보내기는 아래 primaryAction. 진한 카드 안 취소 버튼은 카드 밖 독립 행동 또는 확인 경로로 옮긴다.
- 파일 선택: 검색·필터 가로줄·CheckboxRow, 선택 요약/서버/시작은 고정 아래. 패드 목록/미리보기 폭·선택 ID·root/path 보존. 긴 파일명은 말줄임하지 않는다.
- 진행/결과: Progress·Receipt·수신 근거, 보고/검증 행동 아래. 선택 없는 패드 상세는 StateBlock 안내이며 영구 로딩으로 보이지 않게 한다.
- 저장소·휴지통·직접 서버·개발자 서버 편집도 같은 묶음/입력/복사/확인 규칙. 미리보기는 MediaFrame. 원본 정리·휴지통 이동·비우기·영구 삭제의 기존 확인과 복원 범위를 유지한다.

## 4. 설정·기타 담당

대상: `ui/settings`, `ui/startup`, `ui/alerts`, `ui/dev`, `ui/pipelines`, `ui/dashboard`.

- 설정: 큰 제목+GroupedSection, SwitchRow/SliderRow/Segmented, 팔레트 미리보기는 기존6개. 미리보기 상단은 각 팔레트의 canvas, 탐색은 surface로 그려 원본 topBar/nav 값에 남은 R7 크롬과 혼동하지 않게 한다. 테마 선택은 즉시 저장/적용을 유지하고 저장 버튼을 추가하지 않는다. 패드 상세 루트 뒤로는 공통 pane 문맥이 숨긴다.
- 서버 설정/프로필/전송 대상/파이프라인 편집: 주소·포트·ID 목적별 TextField, 선택은 GeoSheet, 저장/실행은 아래, 삭제/재불러오기는 기존 확인. 로그와 오류·버전 값은 복사 가능하게 한다.
- 알림: ActionRow·실제 원인·삭제 확인·빈 상태. 개발자 네트워크/센서/팬/터미널/서버 휴지통도 동일 토큰을 쓴다.
- 시작: StartupTiming·원본 심볼 리빌·L1/L3 계약 유지. 일반 앱 화면은 canvas, 브랜드 로딩은 기존 surface 전체 장면이다. dashboard 상태 제공 코드에 새 화면을 만들지 않는다.

## 인계 확인

모든 화면·이름 없는 시트/대화상자의 체크 목록은 STYLE-R8 §6에 있다. 1.3부터 상태 아래 이동, 큰 이름·원인 생략 금지, 2.0에서 겹침 없는 입력/행동을 확인한다. 작업자는 정적 확인과 연속 물음표 검색만 하고 git·Gradle·adb를 실행하지 않는다. 감독이 빌드·테스트·린트·패드 계측·실제 캡처를 한다.
