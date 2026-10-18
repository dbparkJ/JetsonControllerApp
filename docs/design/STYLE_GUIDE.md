# GEO& 장치 콘솔 스타일 가이드

색상 기준은 [colors.json](colors.json), 앱 `ui/theme/GeoPalette.kt`, `ui/theme/Color.kt`다. `GeoPalette`는 원시 색상이고 `GeoLight`/`GeoDark`는 화면에서 쓰는 의미 역할이다. 화면은 `LocalGeoColors.current`로 역할을 읽는다. 역할 이름은 유지하고 값만 회사 앱 GEO& Work(옛 GEON Work)를 따른다.

- 원본: GEO& Work `apps/web/src/shared/ui/tokens.css`, main **10d6f1af**(2026-10-16). Light는 `:root`, Dark는 기본 다크 팔레트(graphite)다. JSON 내보내기가 없으므로 새 커밋에서 다시 옮기고 이 SHA와 `GeoPalette.kt` 머리말을 함께 고친다.
- GEO& Work에 없는 상태는 그쪽 팔레트에서 가져온다: 처리 중 `pending`은 보라 선택지(option-6), 미확인 `unknown`은 할 일(todo) 색이다. 안내 `info`는 GEO& Work처럼 주요 파랑을 쓴다.
- 단추 체계(주 단추 알약, 보조 10, 필터 6)는 GEO& Work가 바꾸는 중이라, 그쪽 다음 배포 뒤에 맞춘다. 반경은 아래 값을 유지한다.

| 역할 | Light | Dark | GEO& Work 토큰 |
|---|---|---|---|
| 바탕 `canvas` | `#EDF0F5` | `#161719` | `--color-bg` |
| 표면 `surface` | `#FFFFFF` | `#1E1F21` | `--color-surface` |
| 낮은 표면 `surfaceSunken` | `#F3F5F8` | `#191B1D` | `--color-surface-sunken` |
| 글자 `ink` | `#151B31` | `#E8E9EC` | `--color-text` |
| 보조 글자 `muted` | `#4B5570` | `#BDBFC4` | `--color-text-secondary` |
| 약한 글자 `inkSubtle` | `#5F6880` | `#9EA2A7` | `--color-text-subtle` |
| 장식 선 `border` | `#E7EAF0` | `#323336` | `--color-border` |
| 입력·조작 경계 `controlBorder` | `#838891` | `#7E8289` | `--color-border-strong` |
| 주요 행동 `primary` | `#1F52B8` | `#8AADEC` | `--color-primary` |
| 브랜드 네이비 `hero` (다음 행동 띠에만) | `#121A3D` | `#0E0F11` | `--color-rail` |
| 확인 `success` | `#22704B` | `#88CCA5` | `--color-success` |
| 주의 `warning` | `#8A5A06` | `#E3BC7A` | `--color-warning` |
| 실패 `danger` | `#B3372C` | `#E9938C` | `--color-danger` |
| 안내 `info` | `#1F52B8` | `#8AADEC` | `--color-info` |
| 처리 중 `pending` | `#3D3A86` | `#CCC8F3` | `--option-6-fg` |
| 미확인 `unknown` | `#5D667B` | `#ADB1B9` | `--color-todo` |

상태는 이름과 아이콘, 색을 함께 쓴다. 미확인과 오래된 값은 확인도 실패도 아닌 `unknown`으로 표시한다.

## 모양과 간격

- 반경: 표면(카드·섹션·상태 타일) 12dp, 컨트롤(버튼·입력) 8dp, 시트와 대화상자 위쪽 모서리 16dp. 알약 모양은 상태 칩에만 쓴다 (`GeoRadius`).
- 버튼은 한 모양(8dp)이다. 주요 버튼 최소 높이는 52dp이고, 화면당 채워진 주요 버튼은 하나다.
- 카드에는 1dp 테두리와 그림자를 쓰지 않는다. 바탕과 표면의 명도 차로 구분하고, 목록 안 구분선만 쓴다. 그림자는 시트와 메뉴에만 쓴다.
- 제목 위 아이브로 라벨, 단계 번호 장식, 같은 모양 타일을 줄지어 놓는 배치는 쓰지 않는다.

## 글꼴

- Pretendard 가변 글꼴(`res/font/pretendard_variable.ttf`, SIL OFL)을 400·500·600·700으로 쓴다. 굵기는 `FontVariation`으로 명시한다.
- 크기와 줄높이는 `Type.kt` 값을 따른다. 갱신되는 숫자는 tabular(`tnum`), 식별자(Run ID, 경로)는 monospace다.

## 문구

- 존댓말을 쓴다. 상태는 "~습니다", 안내는 "~세요", 버튼은 명사형이다. 느낌표와 이모지는 쓰지 않는다.
- 장치는 "장치"로 쓰고 "장비"는 쓰지 않는다. 단독 "확인 필요"와 UUID는 쓰지 않는다. 알림과 상태에는 장치 이름을 넣는다.
- 온도는 °C로 쓴다. 날짜와 시각은 "10월 15일 10:39"이며 연도는 올해가 아닐 때만 붙인다 (`formatGeoDateTime`).
- 상태 어휘: 연결됨 · 연결 가능 · 오프라인(마지막 응답 시각) · 응답 없음 · 확인 중.

## 알림

- 알림 제목은 장치 이름으로 시작한다 (예: "MMS-4DE0 온도 높음"). 이름이 없으면 "장치"를 쓴다.
- 벨 숫자는 24시간 안의 읽지 않은 알림만 센다. 알림 화면을 열면 모두 읽음 처리한다.

## 시작과 로딩

- 시스템 스플래시는 바탕색과 GEO& 심볼만 쓴다 (`values/themes.xml`, 다크는 `values-night`).
- 인앱 로딩 `GeoLoadingScene`은 장치 목록 복원이 300ms를 넘을 때만 보인다.

## 검사

`scripts/check_slate_harmony.py`는 `GeoPalette` 참조와 `GeoLight`/`GeoDark` 리터럴을 해석해 `colors.json`과 일치하는지 검사하고, 본문 글자는 4.5:1, 입력 경계와 포커스 링은 3:1을 확인한다. 한글 Windows에서는 `PYTHONUTF8=1`을 설정해 실행한다. 이 검사는 렌더링된 화면의 접근성 인증이 아니다.
