# GEO-X R8 · B안 스타일 가이드

기준: 2026-10-27, `rebuild-r8`의 R7 공통 부품을 B안으로 전환하는 1단계. 화면별 연결은 [APPLY-R8.md](APPLY-R8.md)의 2단계에서 한다. 이 문서가 R7·R7T와 충돌하면 R8을 따른다. 길이는 dp, 글자·줄높이는 sp, 시간은 ms다.

## 1. 원본과 보존 계약

- B 원본 7개: `B-phone-work.dc.html`, `B-phone-run.dc.html`, `B-phone-device.dc.html`, `B-phone-transfer.dc.html`, `B-pad-work.dc.html`, `B-pad-device.dc.html`, `B-pad-camera.dc.html`. 원본은 사용자 지정 `scratchpad/design-r8/canvas/project/`에 있다. A·C·D·E·F는 적용하지 않는다.
- `ref-mobile-native/SKILL.md`의 즉시 눌림, 안전 영역, 내용 선택, 입력 목적, 내부 스크롤 격리를 Compose의 interaction source·WindowInsets·SelectionContainer·KeyboardOptions·nestedScroll로 옮긴다. 웹 CSS나 기기 종류 추측을 넣지 않는다.
- 색의 유일한 원본은 [colors.json](../colors.json)의 6팔레트와 `fixed`다. 새로운 hex·팔레트·의미색을 만들지 않는다. 앱은 Pretendard를 유지한다.
- [R7](../r7/STYLE-R7.md), [공통 부품](../r7/COMPONENTS-R7.md), [패드](../r7/tablet/STYLE-R7T.md), [가로](../r7/tablet/LANDSCAPE-R7T.md)의 상태 증거·거래·권한·수신 검증·확인 대상 고정·초안 보존·장치 전환·탭 스택·로고 자산 규칙을 이어받는다.
- 종료–저장–수신–보고는 독립된 4단계다. 100% 전송, 성공 색, 과거 성공을 근거로 현재 수신이나 보고를 완료 처리하지 않는다. 확인하지 못했으면 미확인·보고 전으로 표시한다.
- 수집 종료·직접 연결 변경·원본 정리·등록 해제·영구 삭제·개발자 명령은 기존 확인 흐름과 실제 대상 ID를 유지한다. 디자인 변경으로 새 동작을 만들지 않는다.

## 2. 토큰

### 간격·모양·높이

| 역할 / 구현 | 수치 | 소유권 |
|---|---|---|
| 간격 척도 `GeoSpace` | 4·8·12·16·20·24·28·32·40·48 | 기존 세부 별칭 유지 |
| 화면 gutter `AppSpacing.screenGutter` | 24 | 본문 / 가로 목록 contentPadding 중 하나만 소유 |
| 패드 내용 여백 | 위8 · 좌우24 · 아래24 | 두 칸은 `GeoPaneLayout`이 한 번 소유 |
| 행 좌우 | 16 | `GeoRow` 내부. 묶음 밖에 다시 넣지 않음 |
| 묶음 사이 / 머리말 아래 | 24 / 8 | `GeoGroupedSection` |
| 섹션 제목 아래 | 12 | `GeoSection` |
| 오늘 카드 안쪽 | 폰20 / 패드24 | `GeoTodayCard` |
| 고정 행동 | 폰 좌우24 · 위아래12, 두 칸은 외곽 여백 재사용 | `GeoFixedActionBar`, canvas 면 |
| 묶음·카드·일반 대화상자 | 반경20 | `GeoShape.card`, `dialog` |
| 버튼·입력 | 반경16 | `GeoShape.control` |
| 시트 | 위쪽 반경24, 아래0 | `GeoShape.sheetTop` |
| 필터 | 반경999 · 높이48 | 일반 행동·상태에는 알약 금지 |
| 탐색 선택 표시 | 하단64×32 / 레일56×32 | 탐색의 명시적 알약 예외 |
| 주 행동 / 보조 행동 | 최소56 / 48 | 큰 글자·여러 줄은 높이 증가 |
| 한 줄 / 두 줄 행 | 최소56 / 72 | 이름·원인 길이에 따라 증가 |
| 줄어든 막대 / 상세 도구 줄 | 최소64 / 48 | 시스템 상태 inset 별도 |
| 하단 탐색 / 레일 | 최소72 / 폭80 | OS 탐색 inset은 고정 수치 아님 |
| 아이콘 / 사진·아바타 | 24 / 40 | 선행 아이콘에 별도 타일 면 없음 |
| 빠른 기능 | 폰104×96, 반경20, gap8 | 확대 시 높이만 증가, 항목 단위 스냅 |
| 시트 끌개 / 상태 점 | 32×4 / 지름6 | borderStrong / 의미색 |
| 미디어 조작 / 캡처 | 지름48 / 80 | 원형 기능 모양 |
| 선 | 행·지표1 물리 px, 막대 아래1dp | density로 나눈 dp가 필요한 곳은 Canvas 1f |

화면 바탕·카드·목록·시트·대화상자·스낵바에는 그림자가 없다. 미디어 위 떠 있는 조작만 y4 / blur16 / 현재 text alpha6%를 쓴다. 기존 `geoCardShadow()`는 호환을 위해 남으며 그림자를 그리지 않는다. 떠 있는 미디어에는 `geoMediaShadow()`를 명시한다.

### 글자 `LocalGeoType`

| 역할 | 크기 / 줄높이 / 굵기 | 이름 |
|---|---|---|
| 큰 화면 제목 | 32 / 40 / 700 | `largeTitle`, `heading`, `boardTitle` |
| 줄어든 막대 제목 | 20 / 28 / 600 | `compactTitle` |
| 묶음 머리말 | 14 / 20 / 600 | `groupHeading` |
| 섹션 제목 | 18 / 28 / 700 | `sectionTitle` |
| 오늘 카드 제목 | 폰26/34/700 · 패드28/36/700 | `workTitle`, `tabletWorkTitle` |
| 행 제목 / 본문 | 16 / 24 / 600·400 | `titleSm`, `body` |
| 버튼 / 입력 | 16 / 24 / 600·400 | `button`, `input` |
| 보조 / 후행 상태 | 14 / 20 / 400·500 | `supporting`, `inlineStatus` |
| 캡션·단위 | 13 / 20 / 500 | `caption`, `unit` |
| 필터 | 14 / 20 / 600 | `filter`, 선택 전후 동일 |
| 지표 숫자 | 폰22/28/700 · 패드28/36 또는26/32/700 | `phoneFigure`, `tabletFigure` |
| 경과 시간 | 40 / 48 / 700 | `clock` |

숫자는 `fontFeatureSettings = "tnum"`. 단위 앞2dp, 가능한 경우 같은 기준선. 지표가 좁으면 단위를 다음 줄로 옮기고 격자를 재배치한다. 폰 숫자를 22sp보다 작게 줄이지 않는다. 이름·파일명·원인·근거는 말줄임하지 않는다. 큰 제목만 최대 두 줄이며 2.0배에서도 높이를 실제 글자에 맞춘다.

### 동작 `GeoMotion`

| 역할 | 수치 / 처리 |
|---|---|
| `fast` / `normal` / `slow` | 120 / 180 / 240 |
| 눌림 | 포인터를 내리는 즉시 면 변경, 0.97배, 120ms ease-out `CubicBezier(0.2,0,0,1)` |
| 눌림 색 | 중립 `fieldPressed`+text, 주 버튼 `primaryPressed`+onPrimary. 선택 필터·미디어도 눌림 면에 맞춰 글자/아이콘 변경 |
| 동작 줄이기 | 크기1.0 고정, 색 반응 유지, 애니메이션 시간0 |
| hover | 공통 부품은 hover 효과를 만들지 않음. 추가할 경우 Mouse 정밀 포인터 이벤트에만 허용하며 Touch·Stylus에 남기지 않음 |
| 로더 | 기존2400ms 리빌. 동작 줄이기는 완성 원본 정지 |
| 제목 접힘 | 실제 측정한 큰 머리 높이−작은 막대 높이. 음의 스크롤을 먼저 소비하고, 본문 최상단에서 돌아오는 스크롤로 펼침 |

모든 누르는 부품은 `geoPressFeedback`을 같은 interaction source에 연결한다. 일반 사용자 정의 줄은 `geoClickable`, 탭은 `geoSelectable`을 쓴다. 클릭 완료를 기다려 눌림 상태를 바꾸지 않는다. 기본 ripple과 별도 크기 효과를 중복하지 않는다.

## 3. 여섯 팔레트의 역할 사용표

아래 역할은 각 열의 해당 팔레트에서 읽는다. 밝은 면이라는 표현은 흰색 상수를 뜻하지 않는다.

| 용도 | STANDARD | NAVY_LIGHT | HIGH_CONTRAST | GRAPHITE | NAVY_DARK | MIST |
|---|---|---|---|---|---|---|
| 화면·상태바·머리·레일 | canvas | canvas | canvas | canvas | canvas | canvas |
| 묶음·시트·입력 외 카드·하단 탐색 | surface | surface | surface | surface | surface | surface |
| 낮은 보조 면 | sunken | sunken | sunken | sunken | sunken | sunken |
| 오늘 카드 면 / 글자 | inverse/onInverse | inverse/onInverse | inverse/onInverse | sunken/text | sunken/text | sunken/text |
| 본문 / 보조 / 캡션 | text/textSecondary/textSubtle | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 |
| 주 행동 / 글자 | primary/onPrimary | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 |
| 선택 행·탐색 표시 | primaryTint/primaryTintText | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 |
| 선택 필터 | inverse/onInverse | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 |
| 입력 / 눌림 | field/fieldPressed | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 |
| 선 / 끌개·입력 경계 | border/borderStrong | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 | 동일 역할 |
| 상태바·시스템 탐색 아이콘 | 어두움 | 어두움 | 어두움 | 밝음 | 밝음 | 밝음 |

- `colors.json` 원본 값은 변경하지 않는다. `JetsonControllerTheme`의 `geoR8Colors`는 기존 `topBar`를 canvas, `nav`를 surface, 해당 글자를 text/primary/textSubtle로 연결한다. NAVY_LIGHT도 흰색·네이비 상단바를 따로 만들지 않는다. 기존 chrome 상태색도 success·warning·danger·primary·pending·neutral 역할로 연결한다.
- 성공은 확인된 연결·수신·응답, warning은 지연·미연결·기준 초과, danger는 실패·불일치·위험 행동, pending은 아직 확인하지 못한 수신·보고, neutral은 대기·오프라인이다. 점+문구를 함께 표시한다. 정상 지표는 text이며 장식용 상태색을 쓰지 않는다.
- 의미색의 `*Soft`·`*Border`는 실제 경고·실패 묶음과 확인 동작에만 사용한다. 선택을 성공색으로 표현하지 않는다.
- 지도는 `fixed.map*`와 `mapPlate`, 미디어는 `fixed.mediaStage`, `mediaPlate`, `mediaText`, `mediaBadge`, `mediaLive`, `mediaDelayed`, `mediaPending`, `mediaFailed`를 사용한다. 사진·로고의 내장 픽셀은 보존한다.
- 창은 투명 시스템 바를 사용하고 그 아래 canvas/surface/mediaStage를 실제로 그린다. 하단 탐색은 navigationBars 영역까지 surface로 칠하고 내용만 그 위로 띄운다. 탐색 없는 화면·레일 아래는 canvas다. inset은 셸과 오버레이별로 한 번만 소유한다.

## 4. 공통 부품 해부도

### 큰 제목·도구 줄·줄어든 막대

`GeoLargeTitleScaffold` → `GeoLargeTitleHeader` → 본문 스크롤 → 선택적 `GeoFixedActionBar` 순서다. 루트는 위8·좌우24·아래20 안에 32/40 제목, 보조 설명14/20, 오른쪽48dp 행동을 둔다. 상세는 먼저48dp `GeoDetailToolbar`(뒤로·더 보기), 그 아래 큰 제목을 둔다. 뒤로와 더 보기는 각각48×48, 아이콘24다. 글자 확대 시 오른쪽 행동이 제목 폭을 줄이므로 제목은 두 줄까지 허용한다.

스크롤하면 큰 머리가 사라지고 canvas의 최소64dp 막대, 20/28/600 제목과 아래1dp 선이 남는다. 접힘 길이는 실제 글꼴로 측정한다. 두 칸 상세 루트는 뒤로 없이 각 칸 위 큰 제목이다. `actions`에는 순수한 버튼만 넣고 시트·다이얼로그 상태는 화면 바깥에서 한 번 구성한다.

### 묶음 목록

`GeoGroupedSection`의 머리말은 묶음 시작보다16dp 안쪽, 묶음 위8dp. 묶음은 surface·반경20·패딩0·그림자0이다. `GeoRowList`가 후행 열과 면을 소유한다.

| 행 종류 | 가로 해부도 / 높이 |
|---|---|
| 한 줄 | x16 아이콘24 → gap16 → x56 제목16/24 → 가변 본문 → gap8 → 후행 → gap8 → 화살표24 → 오른쪽16. 최소56 |
| 두 줄 | 같은 x. 제목16/24/600, 아래 gap4·보조14/20. 최소72 |
| 사진·아바타 | x16 사진40 → gap12 → x68 본문. `leadingIsPhoto=true`. 사진도 원본 비율 유지 |
| 후행 상태 | 점6+gap4+14/20/500. 같은 묶음에서 가장 긴 상태에 맞춘72~120dp 공통 열, 오른쪽 정렬. 제목 첫 줄 중앙 기준 |
| 상태 비어 있음 | Status 묶음이면 공통 후행 폭 예약. 없는 상태를 만들어 표시하지 않음 |
| 독립 행동 | `GeoActionRow`: 행 상세 진입과 분리한88×56 행동. 13/20, 좌우8. 같은 목록은 같은 행동 열 폭 |
| 읽기 전용 | `navigable=false`, 화살표·행 클릭 제거. 내용 복사는 별도 지원 |
| 비활성 | 제목 disabledText·눈에 보이는 이유. 누를 수 있는 것처럼 화살표를 만들지 않음 |
| 선택 | `GeoPaneListRow`: primaryTint가 묶음 가장자리까지 채움. 첫·마지막만 반경20, 선택 경계 양쪽 구분선 숨김 |

구분선은 x56(사진 x68)에서 오른쪽 끝까지1px다. 상태와 화살표는 첫 제목 줄의 실제 글자 중앙에 맞춘다. 1.3배 이상 또는 좁은 열·긴 상태에서는 상태를 제목 아래의 같은 오른쪽 열로 내린다. 독립 행동도 같은 방식이다. 내용은 줄바꿈하며 상태 열이 이름을 잘라내지 않게 한다. 선택 전후 x·y는 바뀌지 않는다.

### 지표 띠·오늘 카드·행동 막대

- `GeoMetricStrip`: 정확히4개 `GeoFigure`. surface 묶음 안에서4등분, 각 칸 사이1px, 라벨13/20 textSecondary, 숫자 폰22/28·패드28/36 또는26/32·700·tnum, 단위13/20. 장치 행 아래는 좌우16의 가는 선과12dp 세로 여백. 별도의 sunken 지표 타일을 반복하지 않는다. 1.3배는2열, 2.0배나 폭200 미만은1열. 근거·오래된 값·초과 원인은 같은 칸 아래 보존한다.
- `GeoTodayCard`: inverse 또는 어두운 팔레트 sunken·반경20. 폰 패딩20·제목26/34, 패드24·28/36. 화면당1개. 모든 보조 문구는 카드 기본 글자색으로 읽히게 한다. 카드 안에 버튼을 넣지 않는다.
- `GeoFixedActionBar`: canvas·폰 좌우24·위아래12, 주 행동56·반경16. 두 칸은 pane의 기존 외곽24/아래24를 재사용하여 추가 padding0이고 버튼이 소속 카드와 같은 폭이다. 본문을 덮지 않고 Column에서 실제 공간을 차지한다. 폰은 하단 탐색 바로 위, 탐색 없는 흐름은 화면 아래, 패드 작업은 왼쪽 열 아래다. 시스템 inset은 셸이 처리하고 단독 사용만 `systemInset=true`로 한다. 버튼의 별도560dp 상한은 제거하며, 대화상자만 자체 최대560을 가진다. 버튼 두 개는 gap8·같은 폭이며 들어가지 않으면 세로로 놓는다.
- `GeoActionArea`는 기존 작은 높이/IME에서 본문 끝으로 옮기는 호환 정책을 유지한다. 새 화면의 주 행동은 `GeoLargeTitleScaffold(primaryAction=...)` 또는 명시적 고정 막대를 사용한다. `GeoWorkCard(action=...)`는 기존 호출을 위해 버튼을 진한 카드 바깥에 두는 호환 래퍼다. 2단계에서는 `GeoTodayCard`+고정 행동으로 교체한다.

### 가로 목록·필터·탭

- `GeoHorizontalTiles`: 폰의 전체 pane 폭에 둔다. 좌우 contentPadding24, 타일104×96, 반경20, gap8. 다음 항목이 보이도록 바깥 세로 목록이 좌우24로 다시 자르지 않는다. LazyRow의 항목 스냅을 쓴다. `GeoQuickActions`는 폰에서 이 부품, 패드에서는3열×2행이다. 1.3배는2열, 2.0배는1열이며 타일 높이는 늘어난다.
- `GeoFilterChipRow`: 높이48·좌우16·gap8인 알약, 한 줄 가로 스크롤. 선택 inverse/onInverse, 미선택 surface/textSecondary. 글꼴·패딩·체크 유무가 변하지 않아 선택 전후 폭이 같다. 기존 `GeoFilters`는 바깥 gutter 없는 호환 API다.
- `GeoTabs`: 최소48dp 직사각 터치 영역·좌우16, 선택 primary 밑줄2dp, 본문 보조 탐색용. 필터와 달리 알약을 쓰지 않는다. `GeoSegmented`는2~4개 상호 배타 설정용이며 공간 부족 시 picker 시트로 바뀌는 기존 정책을 유지한다.

### 시트·대화상자·확인

- `GeoSheet`: surface, 위24 반경, 끌개32×4 borderStrong, 위8~12 여백. 제목18/28/700, 닫기48, 좌우24. 머리·스크롤 본문·고정 footer를 따로 측정한다. 아래 navigationBars·IME inset은 Material/viewport가 한 번 적용한다. `geoContainScroll`이 본문 끝의 남은 세로 스크롤·fling을 소비하므로 뒤 화면이나 시트 부모로 넘기지 않는다. 끌개 영역에서 시트를 움직인다.
- `CHOICE`는 radio/checkbox 줄의 선택색이 시트 가장자리까지 이어진다. `FORM`의 저장·시작은 footer에 둔다. `INFO`는 조작 footer를 만들지 않는다. 남은 본문160dp 미만이면 전체 화면 형태로 전환한다.
- `GeoInfoDialog`·`GeoConfirmDialog`: 반경20, 좌우 외곽24(작은 창은 기존16/20 안전 여백), 최대560, 안쪽24. 제목18/28, 본문16/24, 제목 아래8, 행동 위16, 행동 최소48·주56·gap8. 초점은 연 버튼으로 복원한다. 내용이 들어가지 않으면 전체 화면/스크롤로 바꾼다.
- 위험 확인은 대상 이름·장치·영향·복구 가능 여부와 구체적 동사 버튼을 둔다. `확인`·`예` 같은 불명확한 주 버튼을 쓰지 않는다. `GeoConfirmKind.DANGER`와 기존 dangerSoft/danger/dangerBorder를 사용한다. 취소·시스템 뒤로를 유지한다.
- `GeoMapSheetScaffold`: 지도는 전체 pane 바탕, 시트 위반경24·끌개32×4, 기본 접힌 높이452(882dp 원본의 y430부터 아래, 짧은 창에서는 제한), 최대 창 높이85%. 시간40/48·용량·상태는 시트 본문에, `수집 종료`는 접힘 여부에 관계없이 화면 하단의 같은 surface footer에 남는다. footer는 좌우24·위12, 아래는 시스템 inset만 둔다. 지도 조작은 safeDrawing 안쪽12의48dp 버튼이다. 패드는 기존 왼쪽 제어/오른쪽 지도를 선택할 수 있으며 종료는 왼쪽 아래다.

### 하단 탐색·레일

`GeoBottomBar`는 surface·최소72dp+navigationBars, 네 탭 같은 폭. 아이콘24, 선택 면64×32, 아래gap4와13/20 라벨이다. 선택은 primary/primaryTint, 미선택 textSubtle, 아이콘은 같은 선 아이콘이다. 탭 전체가 터치 영역이다. 주의 표시는6dp attention 점+접근성 상태이며 별도 버튼이 아니다.

`GeoRail`은 canvas 위 폭80, 테두리·그림자 없음. 항목 최소72, gap8, 선택 면56×32. 큰 글자는 높이를 늘리고 레일 안에서 세로 스크롤한다. 패드 카메라만 `GeoFloatingMediaNavigation`의64dp 높이 mediaPlate 알약을 왼쪽 아래24+safe inset에 둔다. 미디어 주 탐색은 같은 네 목적지·선택 탭을 유지한다.

### 미디어·복사·입력

- `GeoImmersiveMediaFrame`: 영상 영역을 화면 끝까지 사용하고 조작만 safeDrawing 안쪽24에 둔다. 원본 비율·기존 수신/지연 판정은 유지한다. `GeoMediaIconButton`은48dp 원·mediaPlate, `GeoCaptureButton`은80dp 원이다. 폰 아래 가운데, 패드 오른쪽 가운데. 조작 글자는 선택되지 않는다. 장치별 영상 표시·저장·재시도 조건은 화면 모델이 결정한다.
- `GeoCopyableValue`: 장치 ID·파일명·서버 주소·오류 코드는 SelectionContainer로 길게 선택 가능하며 별도48dp 복사 버튼을 제공한다. `GeoPropertyRow`의 일반 값도 선택된다. 버튼·칩·탭의 조작 라벨을 SelectionContainer로 둘러싸지 않는다. 복사에는 원래 값을 전달한다.
- `GeoTextField`: field 면·반경16·최소56, 좌우16, 본문16/24, 라벨14/20·아래8, 도움말/오류는 아래12. focus/error 경계2, 기본 borderStrong 1. 오류는 색과 원인 문구를 함께 표시한다. 비활성은 이유를 표시한다.
- `inputKind`: 주소 `SERVER_ADDRESS`는 Uri, 포트 `PORT`와 인증 `CODE`는 Number, ID `IDENTIFIER`는 Ascii·자동 고침/대문자 변환 끔. `SEARCH`는 Search, 폼 중간은 `imeAction=Next`, 마지막은 Done이며 `KeyboardActions`도 연결한다. 기존 명시적 keyboardOptions가 있으면 그 설정을 우선한다.

### 스낵바·빈 상태·불러오는 중·오류

| 상태 | 해부도 / 규칙 |
|---|---|
| 스낵바 `GeoSnackbar` | inverse/onInverse, 반경20, 좌우24·아래8, 안쪽16, 본문16/24. 고정 행동/탐색 위에 놓고 가리지 않음. action·닫기는 최소48. 긴 문구는 행동을 다음 줄로 옮김. 기존 접근성 timeout 유지 |
| 빈 상태 `GeoStateBlock` | 제목18/28, gap4, 설명14/20. 실제 항목0개·상세 선택 전을 로딩과 구분. 다음 행동이 있을 때만48 이상 버튼 |
| 인라인 로딩 `GeoSkeleton` | 상단40 심볼+실제 불러오는 문구, 본문은 sunken/field 정지 골격. shimmer 없음. 제목·탐색·현재 선택 유지 |
| 시작 L1 `StartupLoadingScene` | 기존 surface 전체 화면·원본128 심볼·gap32·GEO-X32/40·gap12·안내16/24, 워드마크88·시스템 하단 위32. 레일 없음 |
| 시작 실패 L3 `StartupErrorScene` | 원본72·alpha0.28, 실패 제목·원래 원인·56dp 다시 시도. StartupTiming 유지 |
| 화면 오류 `GeoStateBlock(ERROR)` | 이름·원인 생략 없음, 재시도/설정 이동이 실제 가능한 경우만 표시 |
| 부분 오류 `GeoInlineNote` | 기존 LIVE_ERROR·원인·복구 행동 유지. 성공 추정이나 항상 켜진 경고 추가 금지 |

## 5. 화면 유형과 창별 배치

| 유형 | 폰 세로 (<600) | 패드 세로 (600~839 또는 두 칸 최소 폭 미달) | 패드 가로 (두 칸 가능) |
|---|---|---|---|
| 루트 목록형 | 큰 제목→오늘 카드0~1→묶음/타일→고정 행동→하단 탐색 | 레일80+최대720 한 열, 빠른 기능3×2 | 레일80, 각 칸 큰 제목, 작업520/608(1280 기준). 주 행동 왼쪽 아래 |
| 상세형 | 도구48→큰 제목→본문/속성/묶음→해당 행동 | 같은 한 열, 뒤로 표시 | 목록400/상세728(1280 기준), 각 칸 제목. 상세 루트 뒤로 숨김 |
| 흐름형: 준비·시작 | 큰 제목·대상·점검·지도 미리보기, 준비/시작은 아래 또는 시작 시트 footer | 최대720, 필요한 내용만 세로 스크롤 | 한 열 폼 또는 기존 두 칸. 행동은 해당 제어 칸 아래 |
| 흐름형: 진행 | 지도+시트, 경과·용량·상태와 종료 | 지도+시트 또는 한 열 제어+지도, 최소 폭을 검사 | 왼쪽 제어360/오른쪽 지도 나머지. 종료는 왼쪽 아래 |
| 흐름형: 결과 | 큰 제목·4단계 영수증·근거·보고/보내기 아래 | 한 열 유지 | 목록/선택 결과. 행동은 결과를 소유한 칸 아래 |
| 지도+시트 | 지도 전체 화면, safe 조작+끌어올리는 시트 | 전체 가용 pane 지도 | 지도 pane을 채움, 시트 또는 왼쪽 제어. 임의의 상단 흰 막대 없음 |
| 몰입형 미디어 | 영상 전체·폰 아래80 캡처·48 원형 조작 | 영상 전체·오른쪽80 캡처, 떠 있는 탐색 | 영상 전체·오른쪽 가운데80 캡처·왼쪽 아래 탐색 알약 |
| 설정형 | 큰 제목·14/20 묶음 머리말·56/72 행, 선택 즉시 반영은 저장 버튼 없음 | 레일·한 열 | 왼쪽 설정 목록400/오른쪽 상세728. 상태/초안 보존 |
| 폼형 | 도구/큰 제목·입력·오류·하단 저장. IME 위로 행동 이동 | 최대720 한 열 | 내용 폭에 맞춘 폼. 입력 중심 집중 경로의 기존 탐색 숨김 유지 |

`s=max(1,fontScale)`, `P=창 폭−레일80(없으면0)`. 두 칸은 폭≥840, 안전 높이≥480, 배율<1.5, `P≥320s+400s+24+48`에서만 허용한다. 1.0/레일의 최소 창 폭872, 1.3은1088이다. 단일 열은 최대720, 두 칸은 최대1560·열 사이24. 목록 기준400, 작업 왼쪽520, 진행 제어360을 보존하며 상세 최소400s를 우선한다. 가로 판정은 현재 창의 폭>높이·높이480~840이다. 방향 센서나 제품명으로 판정하지 않는다.

### 글꼴 배율별 재배치

| 배율 | 행·제목 | 지표·타일·창 |
|---|---|---|
| 1.0~1.29 | 상태와 화살표를 첫 제목 줄 중앙. 이름·이유는 여러 줄 | 지표4열(실폭312 미만2열), 패드 타일3열, 폰 타일104 가로 스냅 |
| 1.3~1.49 | 후행 상태·독립 행동을 같은 오른쪽 열 아래로. 큰 제목2줄, 행 높이 증가 | 지표2열, 패드 타일2열. 충분한 폭의 두 칸 유지, 겹치면 한 열 |
| 1.5~1.99 | 같은 읽기 순서·터치 최소 유지. 버튼은 줄바꿈/세로 배치 | 두 칸 해제, 각 pane 상태·스크롤·선택 보존, 지표2열 |
| 2.0 | 이름·원인·값을 자르거나 글자를 축소하지 않음 | 지표·패드 타일1열. 긴 폼·시트는 내부 스크롤/전체 화면 fallback |

회전·분할 창·IME 후 검색어·필터·선택 device/run/server ID·스크롤·확인 대상을 유지한다. 키보드가 열리면 기존 셸 정책대로 탐색을 숨기고 창을 줄인다. 접근성 크기를 강제로1.0으로 바꾸지 않는다.

## 6. 화면 목록 점검표

아래 경로는 `app/src/main/java/com/example/jetsoncontroller/ui/` 기준이다. 체크는 2단계 적용·감독 검수가 끝났을 때 한다. 같은 파일의 함수도 각각 적었다. 개발자 경로도 일반 부품과 동일한 규칙을 따른다.

| 확인 | 경로 · 함수 | 화면 유형 | 적용할 공통 부품 / 행동 |
|---|---|---|---|
| [ ] | `tasks/TasksHomeScreen.kt` · `TasksHomeScreen` | 루트 목록형 | LargeTitleScaffold, TodayCard, DevicePhotoCard, HorizontalTiles/QuickActions, GroupedSection. 준비는 왼쪽/폰 아래 |
| [ ] | `tasks/TicketDetailScreen.kt` · `TicketDetailScreen` | 상세형 | LargeTitleScaffold, PropertyList, 지도 미리보기, 준비 primaryAction |
| [ ] | 같은 파일 · `WorkTaskDetailScreen` | 상세형 | GroupedSection, PropertyRow, 준비 primaryAction |
| [ ] | 같은 파일 · `WorkMissingScreen` | 오류/빈 상세 | StateBlock(MISSING), 복귀 행동 |
| [ ] | `survey/SurveyRunScreen.kt` · `SurveyRunScreen` | 준비 흐름·폼형 | LargeTitleScaffold, TextField, PreparationStatusRow, 시작 시트 진입을 아래 |
| [ ] | 같은 파일 · `SurveyStartSheet` | 시작 흐름 시트 | GeoStartSheet, GeoSheetFooter |
| [ ] | `survey/TaskChoiceSheet.kt` · `TaskChoiceSheet` 두 오버로드 | 선택 시트 | GeoSheet, RadioRow, 선택면 가장자리 채움 |
| [ ] | `field/ActiveRunScreen.kt` · `ActiveRunScreen` | 진행·지도+시트 | GeoMapSheetScaffold, Clock, MetricStrip, stop confirmation. 패드 왼쪽 제어/지도 |
| [ ] | `field/RunResultScreen.kt` · `RunResultScreen` | 결과 흐름 | LargeTitleScaffold, Receipt, CopyableValue, 보고/보내기 아래 |
| [ ] | `field/HistoryEntryScreen.kt` · `HistoryEntryScreen` | 결과 상세 | Receipt, PropertyList, CopyableValue |
| [ ] | 같은 파일 · `SavedHistoryLogScreen` | 로그 상세 | LargeTitleScaffold, GeoLogView, 복사 |
| [ ] | `field/DeveloperScreen.kt` · `DeveloperScreen` | 개발자 루트 목록형 | GroupedSection, GeoRow, 기존 개발자 진입 제한 |
| [ ] | `map/GeoMapScreen.kt` · `GeoMapScreen` | 지도+시트 | 전체 지도, safe 조작, 품질 정보 시트 |
| [ ] | `devices/DevicesHomeScreen.kt` · `DevicesHomeScreen` | 루트 목록형 | LargeTitleScaffold, GroupedSection, PaneListRow, 사진40, ActionRow |
| [ ] | `devices/DeviceDetailScreen.kt` · `DeviceDetailScreen` | 상세형 | LargeTitleScaffold, MetricStrip, 센서/장치 묶음, CopyableValue |
| [ ] | `devices/ConnectMethodSheet.kt` · `ConnectMethodSheet` | 선택 시트 | GeoSheet, RadioRow/Row, 직접 연결 확인 |
| [ ] | `sensors/SensorScreen.kt` · `SensorScreen` | 상세 목록형 | GroupedSection, Row, 실제 상태·원인 |
| [ ] | `sensors/GnssMapScreen.kt` · `GnssMapScreen` | 지도+시트 | GeoMapScreen 재사용, 품질/장치 대상 유지 |
| [ ] | `sensors/CameraPreviewScreen.kt` · `CameraPreviewScreen` | 몰입형 미디어 | ImmersiveMediaFrame, MediaIconButton, CaptureButton, 저장 위치 시트 |
| [ ] | `pairing/QrScannerScreen.kt` · `QrScannerScreen` | 몰입형 스캐너·폼 | MediaFrame/MediaIconButton, 수동 코드 GeoSheet+TextField(CODE/IDENTIFIER) |
| [ ] | `pairing/PairingScreen.kt` · `PairingScreen` | 등록 흐름 시트 | GeoSheet(FORM), 점검 행, 취소/재시도 footer |
| [ ] | `onboarding/FirstDeviceOnboardingScreen.kt` · `FirstDeviceOnboardingScreen` | 등록 흐름 | 큰 제목·안내·StateBlock, QR 등록 아래 |
| [ ] | `diagnostics/ConnectionDiagnosticsScreen.kt` · `ConnectionDiagnosticsScreen` | 진단 상세 | LargeTitleScaffold, PropertyList, CopyableValue, 로그 공유 기존 동작 |
| [ ] | `network/NetworkSettingsScreen.kt` · `NetworkSettingsScreen` | 폼형 | TextField, RowList, 연결 적용 아래, ConfirmDialog |
| [ ] | `wifi/WifiDirectScreen.kt` · `WifiDirectScreen` | 연결 흐름·목록 | GroupedSection, Row, Progress/StateBlock, 연결/취소 아래 |
| [ ] | `transfers/TransfersHomeScreen.kt` · `TransfersHomeScreen` | 루트 목록형 | LargeTitleScaffold, FilterChipRow, GroupedSection, ActionRow, 파일 보내기 아래 |
| [ ] | `transfers/TransferFilesScreen.kt` · `TransferFilesScreen` | 선택 목록·폼형 | FilterChipRow, CheckboxRow, CopyableValue, 선택 요약·전송 시작 아래 |
| [ ] | `upload/UploadQueueScreen.kt` · `UploadQueueScreen` | 목록형 | GroupedSection, Row/ActionRow, 삭제 확인 |
| [ ] | `upload/UploadConfirmScreen.kt` · `UploadConfirmScreen` | 확인 흐름·폼형 | PropertyList, TextField, 수집/서버 근거, 전송 시작 아래 |
| [ ] | `upload/UploadProgressScreen.kt` · `UploadProgressScreen` | 진행/결과 상세 | Progress, Receipt, StateBlock(선택 없음), 보고/검증 아래 |
| [ ] | `upload/UploadTargetSettingsScreen.kt` · `UploadTargetSettingsScreen` | 개발자 설정 목록 | GroupedSection, Row, 추가/편집 기존 경로 |
| [ ] | 같은 파일 · `UploadTargetEditorScreen` | 개발자 폼형 | TextField(SERVER_ADDRESS/PORT), 저장 아래, 삭제 확인 |
| [ ] | `storage/DataHubScreen.kt` · `DataHubScreen` | 목록/미디어 상세 | FileBrowser 부품·기존 pane 전환, ImmersiveMediaFrame |
| [ ] | `storage/DeviceStorageScreen.kt` · `DeviceStorageScreen` | 파일 목록형 | GroupedSection, 필터·선택, CopyableValue, 보낼 파일 행동 아래 |
| [ ] | `storage/DirectServerScreen.kt` · `DirectServerScreen` | 서버 목록/상세 | GroupedSection, Row, PropertyList, 복사·휴지통 확인 |
| [ ] | 같은 파일 · `ReceiptDialog` | 정보 대화상자 | GeoInfoDialog, Receipt/PropertyList, CopyableValue |
| [ ] | `storage/LocalTrashScreen.kt` · `LocalTrashScreen` | 관리 목록형 | GroupedSection, ActionRow, 비우기 위험 확인 |
| [ ] | `storage/ServerStorageScreen.kt` · `ServerStorageScreen` | 개발자 목록/상세 | GroupedSection, Row, 서버 선택 GeoSheet, 영구 삭제 확인 |
| [ ] | `settings/SettingsHubScreen.kt` · `SettingsHubScreen` | 설정형 | LargeTitleScaffold, GroupedSection, PaneListRow |
| [ ] | `settings/ThemeScreen.kt` · `ThemeScreen` | 설정 상세 | LargeTitleScaffold, Segmented/모드 시트, 6팔레트. 선택 즉시 반영 |
| [ ] | `settings/AlertSettingsScreen.kt` · `AlertSettingsScreen` | 설정형 | GroupedSection, SwitchRow/SliderRow, 기존 즉시 저장 |
| [ ] | `settings/ServerConnectionScreen.kt` · `ServerConnectionScreen` | 설정·폼형 | PropertyList, CopyableValue, 주소 TextField, 편집 GeoSheet footer |
| [ ] | `settings/SettingsInformationScreens.kt` · `SettingsAboutScreen` | 정보 상세 | 큰 제목·로고·PropertyList·버전 복사 |
| [ ] | 같은 파일 · `SettingsHelpScreen` | 정보 상세 | GroupedSection, 설명, 실제 지원 경로 |
| [ ] | `alerts/AlertCenterScreen.kt` · `AlertCenterScreen` | 목록형 | GroupedSection, ActionRow, 삭제 확인·빈 상태 |
| [ ] | `dev/DevDeviceTools.kt` · `DevNetworkScreen` | 개발자 상세 | PropertyList, CopyableValue, 새로고침 |
| [ ] | 같은 파일 · `DevSensorTestScreen` | 개발자 점검 흐름 | Row/ActionRow, 실제 테스트 결과·오류 |
| [ ] | `dev/DevFanScreen.kt` · `DevFanScreen` | 개발자 설정형 | SliderRow/입력, 상태 지표, 기존 제어 조건 |
| [ ] | `dev/DevTerminalScreen.kt` · `DevTerminalScreen` | 개발자 폼형 | TextField(IDENTIFIER), GeoLogView, 실행 확인, 실행 아래 |
| [ ] | `dev/DevServerScreens.kt` · `DevServerProfilesScreen` | 개발자 목록·폼 | GroupedSection, Row, 서버 입력, 삭제 확인 |
| [ ] | 같은 파일 · `DevServerTrashScreen` | 개발자 관리 목록 | ActionRow, 복원·비우기 확인 |
| [ ] | `pipelines/PipelineScreens.kt` · `PipelineListScreen` | 개발자 목록형 | GroupedSection, Row, 등록 해제 확인 |
| [ ] | 같은 파일 · `PipelineEditorScreen` | 개발자 폼형 | TextField, PickerField, 저장 아래 |
| [ ] | 같은 파일 · `PipelinePickerScreen` | 개발자 파일 선택 | GroupedSection, Row, 경로 복사 |
| [ ] | 같은 파일 · `PipelineLogScreen` | 개발자 로그 상세 | GeoLogView, 실행 로그 선택 GeoSheet |
| [ ] | 같은 파일 · `PipelineConfigScreen` | 개발자 폼형 | TextField, PropertyList, 저장 아래·다시 불러오기 확인 |
| [ ] | `pipelines/PipelinePolicyScreen.kt` · `PipelinePolicyScreen` | 개발자 설정·폼 | PickerField·결과 저장소 GeoSheet·입력·저장 아래 |
| [ ] | `components/geo/GeoStartSheet.kt` · `GeoStartSheet` | 시작 시트 부품 | GeoSheet(FORM), PreparationStatusRow, 실패 원인, 시작 footer |
| [ ] | `components/geo/GeoSheets.kt` · `GeoSheet` | 공통 시트 | CHOICE/FORM/INFO 해부도·inset·스크롤 격리 |
| [ ] | 같은 파일 · `OverlayFullScreenDialog` | 시트/대화상자 fallback | 안전 영역·제목·스크롤·footer |
| [ ] | `components/geo/GeoDialogs.kt` · `GeoConfirmDialog` | 공통 확인 대화상자 | 대상·근거·취소·구체적 주 행동 |
| [ ] | 같은 파일 · `GeoInfoDialog` | 공통 정보 대화상자 | 내용·복사·닫기 |
| [ ] | 같은 파일 · `OverlayDialog` | 공통 대화상자 내부 | 측정·초점 복원·fallback |

함수명 스캔에서 함께 잡히는 `sheetNeedsFullScreen`, `startTargetForSheet`, `ticketStartSheet`는 화면을 그리지 않는 판정/모델 함수다. 첫 함수는 시트 fallback, 뒤 두 함수는 시작 시트 모델에 대응한다. 별도 화면을 추가하지 않는다.

### 이름 없는 시트·대화상자와 부가 장면

| 확인 | 소유 경로·장면 | 유형 / 부품 |
|---|---|---|
| [ ] | `navigation/WorkGraph.kt`, `TransferGraph.kt` · 장치 선택 | GeoSheet(CHOICE)+RadioRow, 기존 그래프 소유·선택 ID 유지 |
| [ ] | `navigation/WorkGraph.kt` · 완료 보고 확인 | GeoConfirmDialog+대상 수집·서버 근거 |
| [ ] | `survey/SurveyRunScreen.kt` · 프로젝트 선택·보정 중계 정보 | GeoSheet(CHOICE) / GeoInfoDialog |
| [ ] | `field/ActiveRunScreen.kt`, `map/GeoMapRoute.kt` · 수집 종료 | GeoConfirmDialog(DANGER), 현재 run 대상 고정 |
| [ ] | `field/ActiveRunScreen.kt` · 보정 중계 정보 | GeoInfoDialog |
| [ ] | `field/QualityPresentation.kt`, `map/GeoMapPanel.kt` · 위치 품질 | GeoSheet(INFO)+선택 가능한 근거 |
| [ ] | `map/GeoMapWindow.kt` · 전체 지도 Dialog | 전체 화면 지도+safe 조작, GeoDialogCoversDisplay |
| [ ] | `devices/DeviceDetailScreen.kt` · 장치 위험 동작 / `ConnectMethodSheet.kt` · 직접 연결 | GeoConfirmDialog(DANGER), 기존 장치 대상 |
| [ ] | `pairing/QrScannerScreen.kt` · 수동 등록 코드 / `PairingScreen.kt` · 등록 진행 | GeoSheet(FORM), 목적별 입력/점검과 footer |
| [ ] | `sensors/CameraPreviewScreen.kt` · 캡처 저장 위치 | GeoSheet(CHOICE)+CheckboxRow, 기존 캡처 조건 |
| [ ] | `network/NetworkSettingsScreen.kt` · Wi-Fi 적용·연결 정보 / `wifi/WifiDirectScreen.kt` · 직접 연결 확인 | GeoConfirmDialog / GeoInfoDialog |
| [ ] | `transfers/TransfersHomeScreen.kt` · 취소, `upload/UploadQueueScreen.kt` · 기록 삭제 | GeoConfirmDialog, 실제 전송/기록 대상 |
| [ ] | `upload/UploadProgressScreen.kt` · 정리/취소·완료 보고 / `field/RunResultScreen.kt` · 결과 확인 | GeoConfirmDialog+Receipt 근거 |
| [ ] | `storage/FileBrowser.kt`, `DirectServerScreen.kt` · 휴지통 이동 | GeoConfirmDialog(DANGER)+원본 파일명 CopyableValue |
| [ ] | `storage/DirectServerScreen.kt` · 작업 정보·수신 영수증 | GeoInfoDialog+PropertyList/Receipt |
| [ ] | `storage/LocalTrashScreen.kt`, `ServerStorageScreen.kt` · 비우기/영구 삭제·서버 선택 | GeoConfirmDialog(DANGER) / GeoSheet(CHOICE) |
| [ ] | `settings/ThemeScreen.kt` · 모드, `ServerConnectionScreen.kt` · 서버 편집 | GeoSheet(CHOICE) / GeoSheet(FORM)+저장 footer |
| [ ] | `alerts/AlertCenterScreen.kt` · 알림 삭제 | GeoConfirmDialog |
| [ ] | `dev/DevUi.kt` · 개발자 선택값 | GeoSheet(CHOICE) |
| [ ] | `dev/DevTerminalScreen.kt`, `DevServerScreens.kt` · 명령·서버 삭제·비우기 | GeoConfirmDialog(DANGER) |
| [ ] | `pipelines/PipelineScreens.kt`, `PipelinePolicyScreen.kt` · 등록 해제·다시 불러오기·로그·결과 저장소 선택 | GeoConfirmDialog / GeoSheet(CHOICE) |
| [ ] | `upload/UploadTargetSettingsScreen.kt` · 서버 삭제 | GeoConfirmDialog(DANGER) |
| [ ] | `startup/StartupScenes.kt` · `StartupLoadingScene`, `StartupErrorScene`, `StartupContent` | 기존 L1/L3·StartupTiming·브랜드 원본 |
| [ ] | `storage/MediaPresentation.kt` 등 파일 미리보기 | 미디어 틀·복사 가능한 파일명·기존 선택/회전 상태 |
| [ ] | `zone`·`connection`·`dashboard` | 독립 Screen/Sheet/Dialog 선언 없음. 호스트·상태 제공 부품은 소유 화면의 유형을 따름 |

## 7. R7에서 바뀐 점 / 이어받는 점

| 바뀜 | 이어받음 |
|---|---|
| 상단 흰 앱바→canvas 큰 제목·64dp 접힘 | 탭·뒤로·장치 선택·초안/스크롤 소유권 |
| 카드16→20, 버튼12→16, 기본 그림자 제거 | 6팔레트·fixed·상태 의미·실제 대비 |
| 40dp 아이콘 타일→24dp 선행, 행80→56/72 | 이름·원인 생략 금지·실제 첫 줄 정렬 |
| 진한 카드 안 주 버튼→하단 엄지 영역 | 다음 행동 우선순위·실제 대상과 확인 흐름 |
| 5열 빠른 기능→폰 스냅/패드3×2 | 실제 제공 기능·권한·연결 가용성 |
| 지표 sunken 타일→surface 띠+1px 구분 | 미확인·오래된 값·초과 근거·tnum |
| 선택 필터 primary→inverse | 폭 불변·가로 스크롤 |
| 모든 누름 공통0.97+120ms | reduced motion·48dp 접근성·키보드 초점 |
| 패드 카메라 별도 조작 열→전체 영상과 mediaPlate 조작 | 프레임 신선도·캡처 저장 위치·재시도 |
| 1.3배부터 모든 목록 상태 아래로 | 회전/분할 창 fallback·확인 대상 보존 |
| 선택 행 반경20·새 x56/68 | 5c51917의 가장자리 전체 선택 채움·인접 선 숨김 |

## 8. 검증과 인계

1단계 작업자는 Gradle·git·adb를 실행하지 않는다. 변경 소스의 import·API 이름·괄호·호출부·참조 경로·한국어가 연속 물음표로 깨진 곳을 정적으로 검사한다. JVM/계측 테스트를 작성·갱신하며 실행은 감독이 한다.

감독 확인: 여섯 팔레트, 폰412 세로·패드800 세로/1280 가로, 글꼴1.0·1.1·1.3·1.5·2.0, 제스처/3버튼 inset, IME, 화면 회전·분할 창, 실제 머리 접힘·시트 내부 스크롤·가로 스냅·TalkBack·복사·모든 위험 확인. `R7ComponentLayoutTest` 등의 이름은 기존 실행 진입점 호환을 위해 유지하며 R8 기준으로 갱신한다.
