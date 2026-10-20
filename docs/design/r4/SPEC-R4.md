# R4 — the GEO& Work 업무 문서 v9 document style (delta on R3)

This delta was agreed on 2026-10-20 between Claude and Astra after the owner's phone review. The owner raised two points:
- the 작업 root does not read as separate sections;
- GEO-X should take the feel of GEO& Work's 업무 문서 page v9 (canvas "GEO& Work 업무 문서 페이지 v9").

Everything in R3 (`docs/design/r3/SPEC.md`, `KIT.md`) still holds unless a line below replaces it. No features change, no copy changes, and no new hues.

Notation: `18/26/700` means size sp / line height sp / weight. Spacing is in dp.

## 1. Why R3 did not separate sections

- R3 drew section titles, row titles and empty-state titles at the same size, 16/24 at weight 600, 500 and 600. With only a 24 gap, a heading like "수집 구간" followed by "서버 연결 정보가 없습니다" reads as two titles in a row.
- In 최근 수집, `GeoRow` drew its divider under the title and meta. The receipt line then sat below that divider, so the line cut one record in two.

## 2. Colour roles

- `iconSubtle` is an alias of `textSubtle`.
- `borderControl` is `borderStrong`. Field and secondary-button outlines are not weakened, because #D3D9E3 on white is only 1.42:1.
- `sectionDivider` is `border`; in 고대비 it is `borderStrong`.
- Section background stays `surface`, with no bands. `sunken` is unchanged.

## 3. Type (Type.kt)

| Role | R3 | R4 |
|---|---|---|
| `sectionTitle` (GeoSection heading) | `titleSm` 16/24 at 600 | **18/26/700**, −0.27 sp |
| `boardTitle` (detail H1) | 22/30/700 | **24/32/700**, −0.36 sp |
| root top-bar title | 24/32/700 | unchanged |
| row title (`titleSm`) | 16/24/500 | unchanged (600 in 고대비) |
| `propertyLabel` (new) | — | 15/22/500, `textSubtle` |
| `propertyValue` (new) | — | 16/24/400, `text` |
| meta (`caption`) | 13/20/400 | unchanged |
| `inlineStatus` (new) | — | 14/20/600, in the tone colour |
| `deviceName` (new; device identity on 작업 and 장치) | `sectionTitle` | 18/26/700 |
| `emptyTitle` (new; the fact line of an inline EMPTY state) | 16/24/600 | 15/24/400, `textSecondary` |
| error, permission and offline state titles | 16/24/600 | unchanged |

Split every use that borrowed `sectionTitle` (device name, empty state, Focus title) onto its own role, so the bigger section title does not enlarge them. 고대비 keeps its existing weight adjustments.

## 4. Spacing and gutters

- Gutters are unchanged: 24 dp at ≥390, 20 at 340–389, 16 below.
- Root top padding is 16 and scroll end padding 40.
- Settings screens that hard-code 24 take the shared gutter.

## 5. GeoSection

- The first section has no divider.
- Every later section runs: end of the previous content → 16 → 1 dp `sectionDivider` (content width, not full bleed) → 16 → heading → 8 → content.
- Danger sections (관리, 이 휴대폰) put 24 before the line instead of 16.
- `GeoSection` alone owns this spacing. Parents add nothing between sections.
- A count sits 6 dp after the title in `textSubtle` at 600, and may appear together with a trailing action. Remove the count/action mutual-exclusion `require`.

## 6. Rows

- `GeoRow` keeps its minimum heights of 56 and 72.
- New: a `divider` parameter (default on) and an `extra` slot drawn below the meta. The divider is drawn after the extra content, so one record is one block.
- The divider under the last row of a section is not drawn, because the section line follows. Inside the last section of a page it stays.
- 작업 recent runs use the slot. The order is title, then meta, then the receipt cells, then the divider. The record title is the section (지점) name, as history already does. The meta leads with the project name, or a human folder name such as 20261019_1636. A run-named output folder (run-2026…) is an identifier and never appears; run IDs belong in 기술 정보. Time, duration, size and the report state stay in the meta and the receipt.

## 7. Property list (new `GeoPropertyList` / `GeoPropertyRow`)

- Each row is at least 48 tall with 12 vertical padding.
- The label column is 104 wide: an optional 18 dp `iconSubtle` icon, an 8 gap, then `propertyLabel`.
- An 8 gap separates the columns. The value is start-aligned in `propertyValue`.
- No per-row dividers.
- A value can be plain text, plain text with a second line (13/20 `textSubtle`), an inline status (icon plus `inlineStatus`), or a tag.
- Stacked fallback: at font scale ≥ 1.5, or when the value width would fall below 160, or when a label needs more than two lines, the whole list switches to label above value with a 4 gap.
- `GeoFactRow` delegates to `GeoPropertyRow`. The numeric tables keep their end alignment: `GeoNumericRows` and figure grids are unchanged.
- Use it for:
  - ticket facts;
  - task detail (상태 · 마지막 실행 · 마지막 결과 · 결과 저장 폴더);
  - run-result facts;
  - transfer detail facts;
  - 앱 정보;
  - the device detail's connection facts.
- Do not use it for the 작업 root device summary.

## 8. Empty states

- Inline `GeoStateKind.EMPTY`:
  - the fact line is `emptyTitle` (15/24/400, textSecondary);
  - the explanation keeps its own line after a 4 gap;
  - the action follows after an 8 gap.
- ERROR, PERMISSION, OFFLINE and full-screen states keep their emphasis and their cause-plus-recovery text.

## 9. Corners

| Element | R4 radius |
|---|---|
| `GeoControlShape` (buttons, fields) | 8 (was 10) |
| segmented track | 8 |
| segmented thumb | 4 |
| cards, menus, notes, Focus block, snackbar | 10 (was 12) |
| tags | 4 |
| dialogs | 14 |
| sheet top corners | 14 (was 20) |

## 10. Badges, tags and buttons

- `GeoBadge` gains a variant.
  - **INLINE** is the default in content: no fill, an 8 dp tone dot (or a 16 dp tone icon where the row has one), a 6 gap, then `inlineStatus` text. A 16 dp solid dot read as too heavy on the phone.
  - **TAG** is for identifiers such as the unit name: tint fill, primary text, radius 4, height at least 24, padding 8, 14/20/500.
  - **ON_MAP** and **ON_DARK** keep their opaque bases.
  - Warning lines, `GeoInlineNote` and the Focus block keep their fills.
- Buttons stay 16/20/600 and radius 8.
  - Primary has a minimum height of 52 in action areas; the rest have 48.
  - Secondary uses `surface` with a 1 dp `borderControl` outline.
  - Tertiary keeps its 48 target and `GeoButtonEdge`.

## 11. Tab bar (`ShellTabItem`)

- Icon 24 dp.
- The selected tab has a 48×28, radius-8 shape behind the icon, filled with `primaryTint`.
  - In 네이비 (밝게) the shape is `navSelected` composited at 14 %.
  - In 고대비 it adds a 1 dp outline in `navSelected`.
- Labels are 13/20/600, and 700 when selected. The icon-to-label gap is 2.
- The bar is at least 64 tall plus the system inset. The R3 top hairline (`navBorder`) stays.

## 12. Per-root notes

- **작업:**
  - The first block is the device: photo 40, name in `deviceName`, then the connection status as an inline status. Temperature and storage share one line; a sensor warning gets its own line at 15/24 with the warning icon.
  - The Focus block keeps its precedence.
  - Section lines appear only between 수집 구간, 최근 수집 and 장치 작업.
  - Keep the 구간 직접 고르기 conditions, the report state and every receipt cell.
- **장치:**
  - The current device's two figures keep 24/32/700.
  - A section line separates that block from 확인할 일, whose count sits beside its title.
  - Each warning keeps its own detail target and 숨기기.
- **전송:**
  - Groups keep their order.
  - Progress, speed and cancel stay with their transfer, with the divider after them.
  - The readiness line and 파일 보내기 stay.
- **설정:** the section rule applies to 화면, 알림, 서버, 정보 and 고급. Values stay on the right and move to the next line at large font scale. 권한 허용 keeps its size.
- **Details:** top bar title, then the H1 block, then 24, then the first property list, then sections.

## 13. Acceptance

- Font scale 1.0, 1.1, 1.3, 1.5 and 2.0: nothing is ellipsised away; titles and actions stack when they do not fit.
- All six palettes: 표준, 네이비, 고대비, 그래파이트, 네이비 (어둡게) and 흐림.
- `GeoActionArea` rules are unchanged.
- Sunlight and gloves are a separate field check.
