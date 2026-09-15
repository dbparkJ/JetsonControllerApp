# GEO& Field 색상·컴포넌트 설명

색상 기준은 [colors.json](colors.json), 앱 `ui/theme/GeoPalette.kt`, `ui/theme/Color.kt`다. `GeoPalette`는 원시 색상이고 `GeoLight`/`GeoDark`는 화면에서 사용하는 의미 역할이다. 화면은 `LocalGeoColors.current`를 통해 역할을 읽는다.

라이트 모드는 차가운 오프화이트 바탕과 짙은 회색 글자, 다크 모드는 차콜 바탕과 밝은 회색 글자를 사용한다. 주요 행동과 선택 상태는 GEO& 브랜드 블루, 상태는 확인·주의·실패·안내·처리 중·미확인의 여섯 의미색을 사용한다.

| 역할 | Light | Dark |
|---|---|---|
| 바탕 `canvas` | `#F1F3F6` | `#11141A` |
| 표면 `surface` | `#FFFFFF` | `#181C23` |
| 본문 `ink` | `#2A303A` | `#E7EAEF` |
| 보조 본문 `muted` | `#515967` | `#A8B1BD` |
| 주요 행동 `primary` | `#2550A3` | `#8FAEE4` |
| 히어로 `hero` | `#1A3A74` | `#262A52` |
| 히어로 본문 `heroText` | `#FFFFFF` | `#E7EAEF` |
| 확인 `success` | `#0F6B4F` | `#6FD3A7` |
| 처리 중 `pending` | `#574AA6` | `#B7ACEF` |
| 미확인 `unknown` | `#515967` | `#AAB4C1` |

상태 이름과 아이콘을 함께 표시하므로 색만으로 상태를 구분하지 않는다. 히어로 위 글자는 `heroText`와 `heroMuted`를 사용한다. 일반 `ink`를 브랜드 히어로의 전경으로 가정하지 않는다.

본문 글꼴과 테마 설정은 기존 Compose 테마를 따른다. 입력 컨트롤은 `slateTextFieldColors()`를 사용해 불투명한 `surface` 위에 `controlBorder`를 그린다. 라이트 `controlBorder` `#88919F`는 흰 표면에서 3.18:1이며 `canvas`와 직접 짝지어 검증하지 않는다.

파일 목록은 탐색·날짜 구분·유형 필터를 사용하고 전송 버튼과 미리보기를 유지한다. 창 너비 600dp 이상이면서 글꼴 배율 130% 이하일 때 두 열을 사용할 수 있다. 200% 글꼴에서는 한 열을 유지한다.

`scripts/check_slate_harmony.py`는 `GeoPalette` 참조와 `GeoLight`/`GeoDark`의 리터럴을 해석해 보관된 모든 불투명 토큰의 일치를 검사한다. 본문·히어로·상태·처리 중·미확인·비활성 글자는 4.5:1, 입력 경계와 포커스 링은 실제 배경에서 3.0:1을 유지한다. 이 검사는 모든 렌더링 상태의 접근성 인증을 의미하지 않는다.
