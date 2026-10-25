# R7 공통 기반 사용 안내

현재 수치·색·문구 기준은 [STYLE-R7.md](STYLE-R7.md)다. 이 문서는 다음 단계 화면 구현자를 위한 API 안내다. 모든 부품은 `com.example.jetsoncontroller.ui.components.geo`에 있으며 별도 표시한 브랜드와 토큰만 다른 패키지다.

## 1. 토큰과 카드 면

| 파일 | 공개 API | 적용 |
|---|---|---|
| `ui/theme/GeoShape.kt` | `GeoShape.card`, `control`, `iconTile`, `badge`, `pill`, `sheetTop` | 16 / 12 / 12 / 8 / 999 / 상단 24dp |
| `ui/theme/AppSpacing.kt` | `AppSpacing.screenGutter`, `cardInset`, `workCardInset`, `sectionGap`, `titleGap`, `relatedCardGap`, `actionHorizontal`, `actionVertical` | 24 / 16 / 20 / 24 / 12 / 12 / 24 / 12dp |
| 같은 파일 | 기존 `GeoSpace` 의미별 이름, `gutterForPane(widthDp)` | gutter는 모든 pane에서 24dp. `space1..space12`는 4dp 배수. shell의 `paneGutter`도 같은 토큰 사용 |
| `ui/theme/Type.kt` | `LocalGeoType.current` | `brandTitle` 32/40, `heading`·`boardTitle` 24/32, `workTitle`·`focusTitle` 22/32, `sectionTitle`·`deviceName` 18/28, `titleSm` 16/24, `body` 16/24, `supporting` 14/20, `caption`·`filter`·`badge`·`unit` 13/20, `figure` 28/36, `clock` 40/48 |
| `ui/theme/GeoCardStyle.kt` | `Modifier.geoCardShadow(shape: Shape = GeoShape.card)` | y4, blur16, 현재 text의 6%. 추가 elevation·테두리를 겹치지 않는다 |
| 같은 파일 | `geoWorkCardColors(palette: GeoPalette): GeoWorkCardColors` | 기존 팔레트 역할만 선택. 밝은 면: inverse/onInverse/inverseAction/inverse, 어두운 면: sunken/text/primary/onPrimary |

`Color.kt`와 `colors.json` 값은 그대로다. 숫자에는 `figure`·`clock`의 `tnum`을 사용한다. 직접 만든 숫자 스타일에도 `fontFeatureSettings = "tnum"`을 적용한다. `GeoShape.pill`은 필터에만 사용한다.

## 2. 목록과 상태 배지

파일: `GeoRows.kt`, `GeoBadge.kt`. `GeoRow`·`GeoActionRow`의 기존 인자를 유지했다.

```kotlin
@Composable fun GeoRow(
    title: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    meta: String? = null, value: String? = null,
    badge: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true, disabledReason: String? = null,
    divider: Boolean = true, extra: (@Composable ColumnScope.() -> Unit)? = null
)
@Composable fun GeoRowList(
    trailingColumn: GeoRowTrailing, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
)
// GeoRowTrailing.None / Status
@Composable fun GeoActionRow(
    title: String, actionLabel: String, onOpen: () -> Unit, onAction: () -> Unit,
    modifier: Modifier = Modifier, meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actionEnabled: Boolean = true, disabledReason: String? = null, divider: Boolean = true
)
@Composable fun GeoBadge(
    word: String, tone: Tone, modifier: Modifier = Modifier,
    variant: GeoBadgeVariant = GeoBadgeVariant.SOFT,
    highContrast: Boolean = LocalGeoPalette.current.highContrast,
    minWidth: Dp = 64.dp
)
```

- 목록 카드의 16dp 안쪽에 행을 놓는다. 행 자체는 가로 패딩을 더하지 않는다.
- 상태 목록은 `GeoRowList(Status)`로 묶는다. 값이 아직 없는 행도 72dp를 예약한다. LazyColumn에서는 각 item을 같은 모드의 `GeoRowList`로 감싸도 된다.
- 상태 없는 목록은 `None`을 쓴다. 단독 행은 `badge` 또는 `value`가 있으면 상태 열을 자동 선택한다.
- 선행 40, 후행 상태 72, 쉐브론 24dp. 상태 목록 간격은 8, 상태 없는 목록 간격은 12dp. 최소 높이 80, 세로 패딩 16dp.
- `badge` 슬롯 안의 `GeoBadge`는 자동으로 채움·알약 없는 점·글자가 되어 오른쪽 정렬된다. `value`는 우측 보조 값이다. 한 행에는 짧은 상태 하나를 전달한다.
- 1.5배 글자 또는 좁은 pane에서는 상태를 다음 줄의 같은 오른쪽 열로 내린다. 제목·원인을 말줄임하지 않는다.
- 숨기기·다시 보내기·등록·보내기 같은 독립 행동은 `GeoActionRow`. 88×56dp 글자 버튼 열이며 상태 열과 섞지 않는다. 큰 글자에서는 같은 오른쪽 열의 다음 줄로 이동한다.
- 카드 머리 배지는 24dp, 반경 8, 기본 최소 폭 64dp다. 종류별 고정 폭은 `Modifier.width(폭)`과 `minWidth`를 함께 지정한다. 제목 첫 줄 28dp 영역 중앙에 놓으며 카드당 한 개만 사용한다. 글자 확대 시 배지·행 행동 높이는 글자가 잘리지 않도록 늘어날 수 있다.

```kotlin
GeoRowList(GeoRowTrailing.Status) {
    GeoRow(deviceName, onOpen, meta = freshness,
        leading = { GeoIconTile(GeoIcons.tabDevice) },
        badge = { GeoBadge(connectionWord, connectionTone) })
}
```

## 3. 필터

파일: `GeoSelection.kt`. 기존 선택·스위치 API와 별도로 추가했다.

```kotlin
@Composable fun GeoFilterChip(
    label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true
)
@Composable fun GeoFilters(
    options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
)
```

높이 48, 좌우 12, gap8. 선택 전후 같은 13/20·600 글꼴이고 체크 아이콘이 없다. 선택 시 primary/onPrimary다. `GeoFilters`가 가로 스크롤을 소유하므로 FlowRow로 감싸지 않는다. 첫 칩 왼쪽을 본문 시작선에 맞춘다.

```kotlin
GeoFilters(filterWords, selectedFilter, onSelectFilter)
```

## 4. 4단계 영수증

파일: `GeoReceipt.kt`.

```kotlin
data class GeoReceiptItem(
    val word: String = "미확인", val tone: Tone = Tone.UNKNOWN,
    val meta: String? = null, val done: Boolean = false
)
data class GeoReceiptState(
    val collection: GeoReceiptItem = GeoReceiptItem(),
    val storage: GeoReceiptItem = GeoReceiptItem(),
    val server: GeoReceiptItem = GeoReceiptItem(),
    val report: GeoReceiptItem = GeoReceiptItem(word = "보고 전")
)
@Composable fun GeoReceipt(state: GeoReceiptState, modifier: Modifier = Modifier)
@Composable fun GeoReceiptLine(state: GeoReceiptState, modifier: Modifier = Modifier)
```

둘 다 종료·저장·수신·보고의 같은 4열을 쓴다. 점 12dp, 선 1dp, 이름·상태 13/20. `GeoReceipt`는 아래에 기존 2열 속성 목록으로 원문 상태·근거를 모두 보존한다. `GeoReceiptLine`은 최근 수집용 격자만 표시한다.

`done`은 실제 증거가 있는 단계에만 true로 전달한다. tone이 SUCCESS라는 이유로 완료를 추정하지 않는다. 완료는 success 점, 미확인은 빈 점과 pending 문구, 보고 전은 빈 점과 neutral 문구다. 기존 `rows()`·`firstIncomplete()`와 긴 단계명은 유지한다.

```kotlin
GeoReceipt(receiptState)
// 최근 수집 카드
GeoReceiptLine(receiptState)
```

## 5. 지표와 아이콘

파일: `GeoFigureGrid.kt`, `GeoCards.kt`.

```kotlin
data class GeoFigure(
    val label: String, val value: String?, val valid: Boolean = true,
    val stale: Boolean = false, val detail: String? = null,
    val exceededThreshold: String? = null, val unit: String = ""
)
@Composable fun GeoFigureGrid(
    figures: List<GeoFigure>, modifier: Modifier = Modifier, contained: Boolean = false
)
@Composable fun GeoIconTile(
    icon: ImageVector, modifier: Modifier = Modifier,
    selected: Boolean = false, tone: Tone? = null
)
```

- 기본 4열, gap8, sunken 면, 높이 최소 80, 패딩8. 숫자 28/36, 단위 13/20, 간격2dp. 큰 글자에서 내용 높이는 늘어날 수 있다.
- 1.5배부터 2열, 2배부터 1열. 실제 콘텐츠 폭이 312dp 미만이면 2열, 200dp 미만이면 1열이다. `contained=true`는 전체 grid에 16dp 패딩을 한 번 더 넣으므로 이미 패딩한 카드 안에서는 false다.
- `value="512", unit="GB"`처럼 분리하는 방식을 권장한다. 기존 `"512 GB"` 값도 숫자·단위로 분리해 그린다. 긴 정상 설명·오래된 값 근거·초과 원인은 타일 아래에 표시한다.
- 정상 숫자는 text. `exceededThreshold`가 있을 때만 warning 숫자와 원인을 함께 쓴다. null·무효 값은 미확인이다.
- 아이콘 타일은 장식 40dp. 기본 sunken/textSecondary, 선택 primaryTint/primaryTintText, 경고·실패만 해당 soft다. 누르는 부모는 최소 48dp를 확보한다.

```kotlin
GeoFigureGrid(listOf(
    GeoFigure("저장 여유", storageValue, unit = storageUnit),
    GeoFigure("온도", temperature, unit = "°C"),
    GeoFigure("CPU", cpuValue, unit = "%"),
    GeoFigure("GPU", gpuValue, unit = "%")
))
```

## 6. 진한 작업 카드·장치 사진 카드·빠른 기능

파일: `GeoCards.kt`.

```kotlin
@Composable fun GeoWorkCard(
    title: String, action: GeoButtonAction, modifier: Modifier = Modifier,
    label: String? = null, meta: String? = null, clock: String? = null,
    status: String? = null, statusTone: Tone = Tone.UNKNOWN,
    content: @Composable ColumnScope.() -> Unit = {}
)
@Composable fun GeoDevicePhotoCard(
    name: String, status: String, statusTone: Tone, meta: String,
    figures: List<GeoFigure>, modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, statusWidth: Dp = 64.dp
)
data class GeoQuickAction(
    val label: String, val icon: ImageVector, val onClick: () -> Unit,
    val selected: Boolean = false
)
@Composable fun GeoQuickActions(actions: List<GeoQuickAction>, modifier: Modifier = Modifier)
@Composable fun GeoQuickTile(action: GeoQuickAction, modifier: Modifier = Modifier)
```

- `GeoWorkCard`: 화면당 한 개. 패딩20·반경16·제목22/32·시계40/48. 대기와 수집 중에 같은 틀을 쓴다. 카드 안 보조 문구도 기본 글자색이며 투명도로 낮추지 않는다. 기본 주 행동의 면·글자는 팔레트에 맞춰 선택한다. 위험 행동은 `GeoButtonStyle.DangerFilled` 또는 `DangerOutline`로 지정한다.
- `GeoDevicePhotoCard`: 원본 geo_device 72×64, 이름, 제목 첫 줄 중앙의 배지 하나, 메타, 정확히 네 지표. 사진 카드와 진한 카드 사이는 20~24dp다. 메타·이름은 잘라내지 않으며 큰 글자에서는 줄바꿈한다.
- 빠른 기능: 기본 5열, 1.5배 2열, 2배 1열. surface·공통 그림자·40dp 아이콘·13/20 라벨을 쓴다. R7의 두 줄 라벨은 `"카메라\n영상"`처럼 전달한다. 각 action에 이미 승인된 화면 이동만 연결한다.

```kotlin
GeoDevicePhotoCard(deviceName, connectionWord, connectionTone, freshness, fourFigures, onClick = onDevice)
GeoWorkCard(ticketName, GeoButtonAction("준비하기", onPrepare),
    label = "오늘의 수집 구간", meta = taskMeta, status = "대기")
GeoQuickActions(quickActions)
```

## 7. 버튼과 섹션

파일: `GeoButtons.kt`, `GeoSection.kt`.

`GeoButton`의 기존 인자를 유지하며 마지막에 `containerColor: Color? = null`을 추가했다. 직접 배경색을 지정해야 할 때만 기존 팔레트 역할을 전달한다. 진한 카드의 주 버튼은 카드가 자동으로 처리한다. 반경12, 주 버튼56, 보조48 이상. 위험 버튼 두 종류 모두 dangerSoft/danger/dangerBorder다.

`GeoButtonActions(actions, modifier, layout)`는 두 버튼을 같은 폭·gap8로 놓는다. Dialog 모드도 같은 폭이며, 글자가 들어가지 않으면 세로 전체 폭으로 배치한다. 주요 버튼과 짝인 보조 버튼도 56dp다.

`GeoSection`은 첫 섹션 위 간격 없이, 이후 섹션 앞24dp와 제목 아래12dp를 소유한다. 별도 반복 구분선은 그리지 않는다. 화면에서 동일 간격을 중복 추가하지 않는다.

## 8. 뼈대 로딩·시작 장면

파일: `GeoSkeleton.kt`, `GeonLoader.kt`, `ui/startup/StartupScenes.kt`, `ui/components/GeoBrand.kt`.

```kotlin
enum class GeoSkeletonKind { DeviceCard, Figures, Rows }
@Composable fun GeoSkeleton(
    loadingText: String, modifier: Modifier = Modifier,
    kind: GeoSkeletonKind = GeoSkeletonKind.DeviceCard, rowCount: Int = 3
)
// GeoProgress.kt의 기존 Boolean 오버로드도 유지한다.
@Composable fun GeoSkeleton(
    loading: Boolean, modifier: Modifier = Modifier, rows: Int = 3,
    loadingText: String = "불러오는 중"
)
@Composable fun GeonLoader(
    modifier: Modifier = Modifier, size: Dp = 64.dp,
    reducedMotion: Boolean = geoReducedMotion()
)
// ui.components 패키지
@Composable fun GeoWordmark(modifier: Modifier = Modifier)
```

뼈대 블록은 sunken/field 정지 상태이고, 위에 40dp 심볼과 호출자가 전달한 실제 문구를 둔다. 새 String 오버로드를 우선 사용한다. Boolean 호환 오버로드는 기존 300ms 표시 조건을 유지한다. 장면 전체를 갈아끼우지 말고 기존 상단바·탭을 유지한 본문 위치에서 표시한다.

```kotlin
if (loading) {
    GeoSkeleton(loadingText, kind = GeoSkeletonKind.DeviceCard)
} else {
    GeoDevicePhotoCard(deviceName, connectionWord, connectionTone, freshness, fourFigures)
}
```

로더는 원본 심볼 alpha0.16 위에 같은 원본을 아래에서 위로 드러낸다. 주기2400ms, 0~1680 리빌·1680~2160 유지·2160~2400 전경 페이드. 동작 줄이기에서는 transition 없이 완성 원본 하나를 그린다. 비트맵은 remember로 한 번 읽는다.

L1은 surface·128dp·32dp·GEO-X 32/40·12dp·기존 안내16/24다. 하단 워드마크는 원본 x310~1000/y0~260을 무손실 crop한 `drawable-nodpi/geo_wordmark.png`이며 88dp, 시스템 inset 위32dp다. 어두운 팔레트에서 워드마크만 text tint다. L3은 원본72dp alpha0.28·기존 실패 원인·전체 폭56dp 다시 시도다. 접근성 공지와 StartupTiming은 유지했다.

## 9. 화면별 적용 목록

1. 본문 gutter24, 카드 inset16, 독립 섹션24, 제목 아래12, 하단 행동24/12를 적용한다.
2. 현재 작업을 `GeoWorkCard` 하나로 묶는다. 배정 대기·수집 중의 데이터와 행동은 기존 상태 모델에서 전달한다.
3. 장치 요약을 `GeoDevicePhotoCard`로 바꾸고 같은 장치의 네 지표를 전달한다. 다른 장치·갱신 시점을 섞지 않는다.
4. 빠른 기능을 `GeoQuickActions`로 바꾸고 R7 두 줄 문구를 전달한다.
5. 지표를 `GeoFigureGrid`로 바꾸고 실제 초과 원인·오래된 값·미확인을 유지한다.
6. 상세 결과는 `GeoReceipt`, 최근 수집은 `GeoReceiptLine`. 수신·보고 증거를 추정하지 않는다.
7. 필터를 `GeoFilters`로 바꾸고 선택 상태는 기존 화면 모델에 둔다.
8. 로딩은 실제 본문 위치에 맞는 `GeoSkeletonKind`와 기존 불러오기 문구를 전달한다.
9. 목록별 후행 열을 한 번 정하고 모든 행에 동일하게 적용한다. 독립 행동 목록은 `GeoActionRow`만 쓴다.
10. 6팔레트와 1.0/1.5/2.0 글꼴 배율에서 긴 이름·원인·근거·하단 버튼·스크롤을 확인한다. 개별 화면 전환·권한·수집·업로드 동작은 기존 계약을 유지한다.

## 10. 검증 진입점

- JVM: `AdaptiveThemeTest`, `GeoTypeTest`, `FigureGridRulesTest`, `GeoSectionRulesTest`, `GeoStackingRuleTest`, `shell.WindowClassTest`, `ReceiptOrderTest`, `R7ComponentRulesTest`, `LoaderTimingTest`.
- 기존 방식의 Compose 기기 테스트: `R7ComponentLayoutTest`. 후행72dp·제목 기준 정렬·큰 글자 상태 이동·필터 선택 전후 실제 폭·동작 줄이기 픽셀 정지를 검사한다.
- 정식 Gradle 빌드·단위 테스트·Compose 기기 테스트 실행은 감독 환경에서 확인한다. 이 단계는 기기 조작이나 업로드를 포함하지 않는다.
