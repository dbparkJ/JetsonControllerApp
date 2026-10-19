# GEO-X drawing kit (R3)

The kit every artboard agent copies from. It implements `R3-final.md` (the spec) inside the format of `ARTBOARD_RULES.md`. When the kit and the spec disagree, the spec wins; tell the lead. Copy snippets as they are and change only the text, the data and the state. Do not invent colours, sizes, radii, shadows, icons or components.

---

## 0. Rules of use

1. **Units.** 1 CSS px = 1 dp. Phone root 412 × 882. Status inset 36 at the top (painted `topBar`), navigation inset 48 at the bottom (painted in the colour of whatever sits directly above it: the bottom bar, the action area, a sheet, the page or the black media stage).
2. **Inline styles only**, flex or grid with `gap` for layout. `position:absolute` only for: the bottom bar, a sticky action area, scrims, sheets, dialogs, menus, snackbars, map overlays and floating map controls. The `<helmet><style>` holds only the font face, `body{margin:0}`, keyframes and `url()` mask rules (§6).
3. **Real elements:** `<h1>`/`<h2>` for titles, `<button>` for buttons, `<a href="#">` for tabs and navigation rows, `<label>` + `<input>` for fields, `aria-label` on icon-only buttons, `aria-hidden="true"` on decorative SVG.
4. **Text styles** (paste these strings; numbers that change or compare add `font-variant-numeric:tabular-nums`):

| Role | Inline style |
|---|---|
| heading | `font-size:24px;line-height:32px;font-weight:700;letter-spacing:-0.6px` |
| board-title | `font-size:22px;line-height:30px;font-weight:700;letter-spacing:-0.55px` |
| title | `font-size:18px;line-height:26px;font-weight:600;letter-spacing:-0.27px` |
| title-sm (row) | `font-size:16px;line-height:24px;font-weight:500;letter-spacing:-0.05px` |
| title-sm (section, names) | `font-size:16px;line-height:24px;font-weight:600;letter-spacing:-0.05px` |
| body | `font-size:15px;line-height:24px;font-weight:400;letter-spacing:-0.05px` |
| label | `font-size:14px;line-height:20px;font-weight:500` |
| caption | `font-size:13px;line-height:20px;font-weight:400;letter-spacing:-0.04px` |
| badge text | `font-size:13px;line-height:20px;font-weight:600` |
| tab label | `font-size:13px;line-height:16px;font-weight:500` (selected 700) |
| count | `font-size:12px;line-height:16px;font-weight:600` |
| button | `font-size:16px;line-height:20px;font-weight:600` |
| input | `font-size:16px;line-height:24px;font-weight:400` |
| mono | `font-family:ui-monospace,Menlo,Consolas,monospace;font-size:13px;line-height:20px` |

   **고대비** raises caption, label and body by one weight step (400 → 500, 500 → 600) and row titles to 600.
5. **Button reset** (prefix every `<button>` style with it): `font-family:inherit;border:0;margin:0;cursor:pointer;`
6. **Colours** come only from §1. Every snippet below is in 표준 and lists the role of each colour after it ("Roles: …"), so a board in another theme swaps by role, never by hex (the same hex plays different roles: #FFFFFF is `surface`, `topBar`, `nav` and `onPrimary` in 표준).
7. **Copy and data** come from `R3-final.md` §11.0 (sample data) and the board's row in §11.1. Format numbers with spec §3.13 ("896 MB", "16:36", "약 36초 남음").
8. **Never:** emoji, "!", gradients, left-border accents, eyebrow labels, step numbers, decorative illustrations, pill-shaped buttons (pills only for badges and the segmented control), fake status bars, Android permission dialogs.

---

## 1. Role → hex for the six themes

Columns: 표준 · 네이비 (밝게) · 고대비 · 그래파이트 · 네이비 (어둡게) · 흐림. "=" repeats the value to its left. Alphas are written `rgba()` for direct use.

| Role | 표준 | 네이비 (밝게) | 고대비 | 그래파이트 | 네이비 (어둡게) | 흐림 |
|---|---|---|---|---|---|---|
| canvas | #EDF0F5 | = | #FFFFFF | #161719 | #13171F | #26282B |
| surface | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| sunken | #F3F5F8 | = | #F2F4F7 | #191B1D | #171B23 | #2A2B2E |
| field | #F1F3F7 | = | #F2F4F7 | #292A2C | #262A33 | #3A3C3F |
| fieldPressed | #E9ECF2 | = | #E6E9EF | #303234 | #2D323A | #424447 |
| text | #151B31 | = | #0B1022 | #E8E9EC | #E6E9F0 | #EBEDEF |
| textSecondary | #4B5570 | = | #283045 | #BDBFC4 | #BBBFC8 | #CACCD0 |
| textSubtle | #5F6880 | = | #3B4256 | #9EA2A7 | #9DA1AB | #B3B6BB |
| border | #E7EAF0 | = | #C3C9D4 | #323336 | #2F333C | #444649 |
| borderStrong | #838891 | = | #4D5466 | #7E8289 | #7D828D | #90949A |
| primary (= info, focus, switch on, checked) | #1F52B8 | = | #163E8F | #8AADEC | #8BACEF | #9ABBF6 |
| primaryPressed | #1A47A0 | = | #103170 | #A8C3F4 | #A9C3F7 | #B9D2FE |
| onPrimary | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| primaryTint (= infoSoft, selected row) | #E7EEFB | = | #E3EBF9 | #242E40 | #242F46 | #374154 |
| primaryTintText | #1A47A0 | = | #103170 | #B9CFF5 | #BBCEF5 | #C1D7FD |
| focusHalo | rgba(31,82,184,.24) | = | rgba(22,62,143,.32) | rgba(138,173,236,.32) | rgba(139,172,239,.32) | rgba(154,187,246,.32) |
| success | #22704B | = | #0E5634 | #88CCA5 | #88CCA5 | #91D6AE |
| successSoft | #E2F1E9 | = | #DFF0E6 | #203026 | #203026 | #314238 |
| warning | #8A5A06 | = | #5F3D00 | #E3BC7A | #E3BC7A | #EDC684 |
| warningSoft | #FBF0DA | = | #FAEDD3 | #332A1C | #332A1C | #453C2D |
| danger | #B3372C | = | #8B1E15 | #E9938C | #E9938C | #F39D95 |
| dangerPressed | #9A2E25 | = | #6F150E | #F0A9A3 | #F0A9A3 | #F7B2AB |
| onDanger | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| dangerSoft | #FBE8E5 | = | #FBE4E1 | #392626 | #392626 | #4B3837 |
| pending | #3D3A86 | = | #2C2970 | #CCC8F3 | #CCC8F3 | #D5D0FC |
| pendingSoft | #E4E3F4 | = | #E5E4F5 | #2D2B3F | #2D2B3F | #3F3D52 |
| neutral | #5D667B | = | #3D4456 | #ADB1B9 | #ACB1BD | #BBBFC7 |
| neutralSoft | #EEF0F4 | = | #ECEEF2 | #2C2E31 | #292E38 | #3D4044 |
| tone borders (badges in 고대비 only; decorative elsewhere) | success #A9D3BD · warning #ECCA8C · danger #F0B8B1 · info #B9CBEF · pending #C4C1E8 · neutral #D9DEE7 | = | the solid tone colours | #376048 · #67522D · #734844 · #435B85 · #565176 · #46494E | #376048 · #67522D · #734844 · #455A85 · #565176 · #444955 | #477159 · #78623D · #855854 · #546C97 · #676288 · #56595F |
| disabledFill | #E9ECF2 | = | #E6E9EF | #292A2C | #262A33 | #3A3C3F |
| disabledText | #5F6880 | = | #555D70 | #9EA2A7 | #9DA1AB | #B3B6BB |
| switchTrackOff | #7D8598 | = | #5E6577 | #7E8289 | #7D828D | #90949A |
| switchThumbOff | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| switchThumbOn | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| topBar (incl. the 36 px status area) | #FFFFFF | #121A3D | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| onTopBar (title, back, switcher name) | #151B31 | #FFFFFF | #0B1022 | #E8E9EC | #E6E9F0 | #EBEDEF |
| onTopBarIcon (trailing icons, chevron) | #4B5570 | #AEB9D9 | #283045 | #BDBFC4 | #BBBFC8 | #CACCD0 |
| topBarBorder (only when content is scrolled under it) | #E7EAF0 | none | #4D5466 | #323336 | #2F333C | #444649 |
| nav (bottom bar, rail, the 48 px inset under them) | #FFFFFF | #121A3D | #FFFFFF | #0E0F11 | #0C0F17 | #1E1F22 |
| navSelected | #1F52B8 | #FFFFFF | #163E8F | #A8C3F4 | #A9C3F7 | #B9D2FE |
| navUnselected | #5F6880 | #AEB9D9 | #5A6276 | #9EA2A7 | #9DA1AB | #B3B6BB |
| navBorder (hairline above the bar) | #E7EAF0 | none | #4D5466 | none | none | none |
| navFocus | #1F52B8 | #8FB0F0 | #163E8F | #9BBAF0 | #9DB9F0 | #A5C3FA |
| attention (tab dot, bell count) | #D0342C | #F08F86 | #B3261E | #F08F86 | #F08F86 | #FB988F |
| onAttention | #FFFFFF | #121A3D | #FFFFFF | #161719 | #13171F | #26282B |
| chrome tone dots (success · warning · danger · info · pending · neutral) on the top bar | content tones | #88CCA5 · #E3BC7A · #F08F86 · #8FB0F0 · #CCC8F3 · #AEB9D9 | content tones | content tones | content tones | content tones |
| inverse (snackbar) | #151B31 | = | #0B1022 | #3D3E41 | #3A3E48 | #4F5155 |
| onInverse | #FFFFFF | = | #FFFFFF | #E8E9EC | #E6E9F0 | #EBEDEF |
| inverseAction | #8FB0F0 | = | #A9C3F7 | #A8C3F4 | #A9C3F7 | #B9D2FE |
| scrim | rgba(18,26,61,.32) | = | rgba(11,16,34,.48) | rgba(5,6,8,.60) | rgba(4,6,14,.60) | rgba(6,7,9,.60) |
| mapPlate (map badge, attribution, map controls; opaque) | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| shadow-sm | 0 0 0 1px rgba(18,26,61,.06), 0 1px 2px rgba(18,26,61,.06) | = | = | 0 0 0 1px rgba(255,255,255,.07) | = | = |
| shadow-lg | 0 0 0 1px rgba(18,26,61,.06), 0 20px 48px rgba(18,26,61,.18) | = | = | 0 0 0 1px rgba(255,255,255,.10), 0 16px 40px rgba(0,0,0,.45) | = | = |
| shadow-switch (thumb) | 0 1px 2px rgba(18,26,61,.25) | = | = | 0 1px 2px rgba(0,0,0,.5) | = | = |
| loader base filter · opacity | grayscale(1) contrast(0) brightness(1.74) · 1 | = | = | grayscale(1) contrast(0) brightness(.9) · .28 | = | grayscale(1) contrast(0) brightness(.96) · .28 |

**Fixed, never themed:** map overlays (§5), the media stage (#000000; plates rgba(0,0,0,.72); text #FFFFFF; on-dark badge fill #1E1F21 with 실시간 #88CCA5, 지연 #E3BC7A, 대기 중 #BDBFC4, 받지 못함 #E9938C), the loader symbol's own colours, and the board background of multi-frame boards (#DDE1E8).

**Theme swaps that are easy to miss:** 네이비 (밝게) paints the top bar *including the 36 px status area*, the bottom bar and the 48 px inset navy, with white titles, #AEB9D9 icons and unselected tabs, #8FB0F0 focus and #F08F86 dots; its content is 표준. In 고대비 every surface is white, fields and badges carry their solid borders, the hairline above the bottom bar is #4D5466, and weights step up (§0.4). In dark palettes shadows become the white outline variants, the 일반 map base uses the dimmed colours (§5.1), and the action area and sheets use the dark `surface`.

---

## 2. Skeletons

### 2.1 File head (every board)

```html
<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>작업</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<style>
@font-face{font-family:"Pretendard GX";src:url(/_blob/5a79d92e2068adfe419778e914118227) format("truetype");font-weight:100 900;font-display:block}
body{margin:0}
</style>
</helmet>
<!-- the root goes here -->
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":412,"height":882}}'>
class Component extends DCLogic {
  renderVals() { return {}; }
}
</script>
</body>
</html>
```

Change `<title>` to the board's 제목 and `$preview` to the root size. Boards with the loader add the §6 rules inside the same `<style>`.

### 2.2 Tab root (bottom bar)

```html
<div style="width:412px;height:882px;position:relative;overflow:hidden;box-sizing:border-box;font-family:'Pretendard GX',sans-serif;color:#151B31;background:#FFFFFF;display:flex;flex-direction:column">
  <header style="background:#FFFFFF;padding-top:36px"><!-- root top bar row, §3.1 --></header>
  <main style="flex:1;overflow:hidden;padding:16px 24px 128px;display:flex;flex-direction:column">
    <!-- device strip, Focus block, sections -->
  </main>
  <!-- bottom bar, §3.2 (absolute, 112 px incl. the 48 px inset) -->
</div>
```
Roles: root background `surface`; header `topBar`.

### 2.3 Detail screen with the bar and a sticky action area

```html
<div style="width:412px;height:882px;position:relative;overflow:hidden;box-sizing:border-box;font-family:'Pretendard GX',sans-serif;color:#151B31;background:#FFFFFF;display:flex;flex-direction:column">
  <header style="background:#FFFFFF;padding-top:36px"><!-- detail top bar row, §3.1 --></header>
  <!-- optional status strip, §3.3 -->
  <main style="flex:1;overflow:hidden;padding:16px 24px 232px;display:flex;flex-direction:column">
    <!-- H1 block, then sections -->
  </main>
  <!-- action area at bottom:112px, §3.12 -->
  <!-- bottom bar, §3.2 -->
</div>
```
The `main` bottom padding = action area (≈ 108) + bar (112) + 12 so nothing hides behind them. Without an action area use 128.

### 2.4 Focused flow (no bar)

Same as 2.3 without the bottom bar; the action area sits at `bottom:0` and paints the 48 px inset with `surface`: `padding:12px 24px 60px`. Media screens (camera, QR, video, map) paint the inset in the stage's colour.

### 2.5 H1 block (detail screens)

```html
<div style="display:flex;flex-direction:column;gap:8px;padding-bottom:8px">
  <h2 style="margin:0;font-size:22px;line-height:30px;font-weight:700;letter-spacing:-0.55px;color:#151B31">신월4거리 북측 보조 구간</h2>
  <div style="display:flex;flex-wrap:wrap;align-items:center;gap:8px">
    <!-- one badge, §3.8 -->
    <span style="font-size:13px;line-height:20px;letter-spacing:-0.04px;color:#4B5570">수원시 도로대장 · GEO-X-1 · 마감 10월 21일</span>
  </div>
</div>
```
Roles: title `text`; meta `textSecondary`. The first section after it takes `padding-top:32px` instead of 24.

### 2.6 Multi-frame board root

```html
<div style="width:1316px;height:882px;box-sizing:border-box;background:#DDE1E8;display:flex;gap:40px;font-family:'Pretendard GX',sans-serif">
  <div style="width:412px;height:882px;flex:none;position:relative;overflow:hidden;box-sizing:border-box;color:#151B31;background:#FFFFFF;display:flex;flex-direction:column"><!-- frame 1 --></div>
  <div style="width:412px;height:882px;flex:none;position:relative;overflow:hidden;box-sizing:border-box;color:#151B31;background:#FFFFFF;display:flex;flex-direction:column"><!-- frame 2 --></div>
  <div style="width:412px;height:882px;flex:none;position:relative;overflow:hidden;box-sizing:border-box;color:#151B31;background:#FFFFFF;display:flex;flex-direction:column"><!-- frame 3 --></div>
</div>
```
Width = 412 n + 40 (n − 1): 2 frames 864, 3 frames 1316, 4 frames 1768. Set `$preview` to the same size.

### 2.7 Landscape root (882 × 412)

```html
<div style="width:882px;height:412px;position:relative;overflow:hidden;box-sizing:border-box;font-family:'Pretendard GX',sans-serif;color:#151B31;background:#FFFFFF;display:flex;flex-direction:column;padding-right:48px">
  <header style="background:#FFFFFF;padding-top:24px"><!-- detail top bar row --></header>
  <!-- content; the 48 px navigation bar sits on the end side: paint it with a 48 px absolute strip at right:0 in the colour under it -->
</div>
```

### 2.8 Design-system board root

```html
<div style="width:1280px;height:1600px;box-sizing:border-box;padding:56px;background:#EDF0F5;font-family:'Pretendard GX',sans-serif;color:#151B31;display:flex;flex-direction:column;gap:40px">
  <h1 style="margin:0;font-size:24px;line-height:32px;font-weight:700;letter-spacing:-0.6px">글자와 정렬</h1>
  <section style="background:#FFFFFF;border-radius:12px;padding:24px;display:flex;flex-direction:column;gap:16px">
    <h2 style="margin:0;font-size:16px;line-height:24px;font-weight:600">유형</h2>
    <!-- specimens -->
  </section>
</div>
```
Each specimen carries a one-line caption in `caption` `textSecondary` naming the role or the token ("body 15/24 400").

---

## 3. Components (표준) with their states

Icons are written `[icon:name size colour]`; replace each with the §4 SVG at that size and colour (`style="color:…"` on the `<svg>`). A pressed state changes only the fill named in its line; a focused state adds the focus ring `box-shadow:0 0 0 2px #FFFFFF,0 0 0 4px #1F52B8,0 0 0 8px rgba(31,82,184,.24)` (on chrome: `navFocus`).

### 3.1 Top bars

**Root** (작업, 장치, 전송, 설정). The switcher appears on 작업 and 전송; the bell on 작업, 장치 and 전송; "+" on 장치.

```html
<header style="background:#FFFFFF;padding-top:36px">
  <div style="min-height:64px;display:flex;align-items:center;gap:4px;padding:0 4px 0 24px">
    <h1 style="margin:0;flex:1;font-size:24px;line-height:32px;font-weight:700;letter-spacing:-0.6px;color:#151B31">작업</h1>
    <button aria-label="현재 장치 GEO-X-1, 장치 바꾸기" style="font-family:inherit;border:0;margin:0;cursor:pointer;display:flex;align-items:center;gap:6px;min-height:48px;padding:0 8px;border-radius:10px;background:transparent;color:#151B31">
      <span style="width:8px;height:8px;border-radius:999px;background:#22704B"></span>
      <span style="font-size:16px;line-height:24px;font-weight:600;letter-spacing:-0.05px">GEO-X-1</span>
      [icon:chevronDown 20 #4B5570]
    </button>
    <button aria-label="알림 2개" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;border-radius:999px;background:transparent;color:#4B5570;display:flex;align-items:center;justify-content:center">
      <span style="display:flex;align-items:flex-start">[icon:bell 24 #4B5570]<span style="margin:-6px 0 0 -10px;min-width:18px;height:18px;padding:0 5px;box-sizing:border-box;border-radius:999px;background:#D0342C;color:#FFFFFF;font-size:12px;line-height:18px;font-weight:600;text-align:center">2</span></span>
    </button>
  </div>
</header>
```
Roles: header `topBar`; title and switcher name `onTopBar`; dot `chrome*` tone; chevron and bell `onTopBarIcon`; count `attention` / `onAttention`. Scrolled: add `border-bottom:1px solid #E7EAF0` (`topBarBorder`). 네이비 (밝게): #121A3D / #FFFFFF / #AEB9D9, dot #88CCA5, count #F08F86 with #121A3D digits, no hairline.

**Root, switcher on a second row** (when title + trailing do not fit, e.g. 320 dp or font scale 2.0): keep the first row with title and bell, then

```html
  <div style="padding:0 24px 8px">
    <button aria-label="현재 장치 GEO-X-현장지원차량-2, 장치 바꾸기" style="font-family:inherit;border:0;margin:0 0 0 -8px;cursor:pointer;display:flex;align-items:center;gap:6px;min-height:48px;padding:0 8px;border-radius:10px;background:transparent;color:#151B31;text-align:left">
      <span style="width:8px;height:8px;flex:none;border-radius:999px;background:#22704B"></span>
      <span style="font-size:16px;line-height:24px;font-weight:600">GEO-X-현장지원차량-2</span>
      [icon:chevronDown 20 #4B5570]
    </button>
  </div>
```

**Detail** (pushed screens; the title names the place, the object name is the H1):

```html
<header style="background:#FFFFFF;padding-top:36px">
  <div style="min-height:56px;display:flex;align-items:center;gap:4px;padding:0 4px">
    <button aria-label="뒤로" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;flex:none;border-radius:999px;background:transparent;color:#151B31;display:flex;align-items:center;justify-content:center">[icon:back 24 #151B31]</button>
    <h1 style="margin:0 0 0 12px;flex:1;font-size:18px;line-height:26px;font-weight:600;letter-spacing:-0.27px;color:#151B31">수집 구간</h1>
    <button aria-label="더 보기" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;flex:none;border-radius:999px;background:transparent;color:#4B5570;display:flex;align-items:center;justify-content:center">[icon:more 24 #4B5570]</button>
  </div>
</header>
```
Roles: `topBar`, back and title `onTopBar`, ⋮ `onTopBarIcon`. The title starts 64 px from the edge (4 + 48 + 12). States: icon button pressed = a 48 px circle `fieldPressed` (on navy: rgba(255,255,255,.14)).

### 3.2 Bottom bar and rail

```html
<nav aria-label="주 메뉴" style="position:absolute;left:0;right:0;bottom:0;background:#FFFFFF;border-top:1px solid #E7EAF0;padding-bottom:48px">
  <div style="display:grid;grid-template-columns:repeat(4,minmax(0,1fr));min-height:64px">
    <a href="#" aria-current="page" style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;text-decoration:none;color:#1F52B8">[icon:tabWork-filled 24 #1F52B8]<span style="font-size:13px;line-height:16px;font-weight:700">작업</span></a>
    <a href="#" aria-label="장치, 확인할 일 있음" style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;text-decoration:none;color:#5F6880"><span style="display:flex;align-items:flex-start">[icon:tabDevice 24 #5F6880]<span style="margin:-1px 0 0 -5px;width:8px;height:8px;border-radius:999px;background:#D0342C"></span></span><span style="font-size:13px;line-height:16px;font-weight:500">장치</span></a>
    <a href="#" style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;text-decoration:none;color:#5F6880">[icon:tabTransfer 24 #5F6880]<span style="font-size:13px;line-height:16px;font-weight:500">전송</span></a>
    <a href="#" style="display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;text-decoration:none;color:#5F6880">[icon:tabSettings 24 #5F6880]<span style="font-size:13px;line-height:16px;font-weight:500">설정</span></a>
  </div>
</nav>
```
Roles: `nav` (also paints the 48 px inset), hairline `navBorder`, selected `navSelected` with the filled icon (its knock-out strokes use `nav`), unselected `navUnselected`, dot `attention`. On detail screens the owning tab stays lit. Pressed: an 8 % circle of `navSelected` behind the icon (`background:rgba(31,82,184,.08)` on a 40 px circle).

**Rail** (landscape and wide windows): an absolute column at the start, 80 px wide, full height under the status area, `background:#FFFFFF;border-right:1px solid #E7EAF0;display:flex;flex-direction:column;gap:8px;padding-top:8px`, each item a 72 px tall link with the same icon + label stack; content shifts right by 80 px.

### 3.3 Status strip (one per screen, directly under the top bar)

```html
<div role="status" style="display:flex;align-items:center;gap:12px;min-height:48px;padding:12px 24px;box-sizing:border-box;background:#FBF0DA">
  [icon:warning 20 #8A5A06]
  <p style="margin:0;flex:1;font-size:15px;line-height:24px;letter-spacing:-0.05px;color:#151B31">GEO-X-1 연결이 끊겼습니다 · 마지막 응답 5분 전</p>
</div>
<div style="display:flex;gap:8px;padding:0 12px 8px;background:#FBF0DA;justify-content:flex-end">
  <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 12px;border-radius:10px;background:transparent;color:#1F52B8;font-size:16px;line-height:20px;font-weight:600">연결 방법</button>
  <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 12px;border-radius:10px;background:transparent;color:#1F52B8;font-size:16px;line-height:20px;font-weight:600">다시 연결</button>
</div>
```
One action fits on the text row (put the button after the `<p>`); two actions take the second row as shown. Tones and icons: 인증 오류 `dangerSoft` + [icon:danger #B3372C]; 연결 끊김 / 구역 밖 `warningSoft` + [icon:warning #8A5A06]; 수집 중 (ongoing-run entry) `primaryTint` + [icon:info #1F52B8] with "GEO-X-1 · 신월4거리 수집 중 · 00:12:34" and "진행 보기"; 마지막 동기화 `neutralSoft` + [icon:pending #5D667B] with "마지막 동기화 16:36" and "다시 확인". Text is always `text`.

### 3.4 Focus block (tab roots only)

```html
<div style="display:flex;flex-direction:column;gap:12px;padding:16px;border-radius:12px;background:#FBF0DA">
  <div style="display:flex;gap:12px;align-items:flex-start">
    [icon:warning 20 #8A5A06]
    <div style="display:flex;flex-direction:column;gap:4px">
      <h2 style="margin:0;font-size:16px;line-height:24px;font-weight:600;letter-spacing:-0.05px;color:#151B31">GEO-X-1 연결이 끊겼습니다</h2>
      <p style="margin:0;font-size:15px;line-height:24px;letter-spacing:-0.05px;color:#151B31">마지막 응답 5분 전 · 수집은 장치에서 계속될 수 있습니다.</p>
    </div>
  </div>
  <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;border-radius:10px;background:#1F52B8;color:#FFFFFF;font-size:16px;line-height:20px;font-weight:600">다시 연결</button>
  <button style="font-family:inherit;border:0;margin:0;cursor:pointer;align-self:flex-start;min-height:48px;padding:0 12px;margin-left:-12px;border-radius:10px;background:transparent;color:#1F52B8;font-size:16px;line-height:20px;font-weight:600">연결 방법</button>
</div>
```
Roles: fill tone-soft (warning for unreachable / 기본 연결 / 권한; danger for credentials; pending for 시작 결과 / 상태 미확인; info for 수집 중 / 결과 / 완료 보고), icon tone, text `text`, primary `primary`/`onPrimary`, tertiary `primary`.

### 3.5 Device strip (작업 root)

```html
<a href="#" style="display:flex;align-items:center;gap:16px;margin:0 -24px;padding:12px 24px;min-height:72px;box-sizing:border-box;text-decoration:none;color:#151B31">
  <img src="/_blob/462cdfa56113406ef6f6babfcb1d6435" alt="" style="width:40px;height:40px;flex:none;object-fit:contain">
  <div style="flex:1;display:flex;flex-direction:column;gap:4px">
    <div style="display:flex;flex-wrap:wrap;align-items:center;gap:8px">
      <span style="font-size:16px;line-height:24px;font-weight:600;letter-spacing:-0.05px">GEO-X-1</span>
      <!-- badge 연결됨 (success), §3.8 -->
    </div>
    <span style="font-size:13px;line-height:20px;letter-spacing:-0.04px;color:#4B5570">54°C · 저장 512 GB 남음 · 방금 갱신</span>
  </div>
  [icon:chevronRight 20 #5F6880]
</a>
```
Attention variant: replace the meta's last fact with `<span style="display:inline-flex;align-items:center;gap:6px;color:#8A5A06">[icon:warning 16 #8A5A06]카메라 지연</span>`. Pressed: `background:#E9ECF2`.

### 3.6 Section title

```html
<section style="padding-top:24px;display:flex;flex-direction:column">
  <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;min-height:24px;margin-bottom:8px">
    <h2 style="margin:0;font-size:16px;line-height:24px;font-weight:600;letter-spacing:-0.05px;color:#151B31">최근 수집</h2>
    <button style="font-family:inherit;border:0;margin:-12px -12px -12px 0;cursor:pointer;min-height:48px;padding:0 12px;border-radius:10px;background:transparent;color:#1F52B8;font-size:16px;line-height:20px;font-weight:600">전체 기록</button>
  </div>
  <!-- rows -->
</section>
```
Variants: a count instead of the button (`<span style="font-size:13px;line-height:20px;color:#4B5570;font-variant-numeric:tabular-nums">3건</span>`); a one-line note under the title (`caption` `textSecondary`). Danger sections (관리, 이 휴대폰) use `padding-top:28px`; the first section under the H1 block uses 32; the first section on a root uses 0 (the root's 16 px top padding). No icon, no box, no eyebrow.

### 3.7 Rows

**Navigation row, single line (56):**

```html
<a href="#" style="display:flex;align-items:center;gap:16px;margin:0 -24px;padding:0 24px;min-height:56px;text-decoration:none;color:#151B31">
  <div style="flex:1;display:flex;align-items:center;gap:12px;min-height:56px;border-bottom:1px solid #E7EAF0">
    <span style="flex:1;font-size:16px;line-height:24px;font-weight:500;letter-spacing:-0.05px">장치 Wi-Fi</span>
    <span style="font-size:15px;line-height:24px;color:#4B5570">GEON-5G</span>
    [icon:chevronRight 20 #5F6880]
  </div>
</a>
```
Roles: title `text`, value `textSecondary`, chevron `textSubtle`, divider `border`. Pressed: the `<a>` gets `background:#E9ECF2`. Disabled: title `disabledText`, value replaced by the reason "연결되면 쓸 수 있습니다." in `caption` `textSecondary`, no chevron. A leading 24 px icon or a 40 px photo goes before the inner `<div>` (the divider then starts at the text).

**Two-line row (72 = 12 + 24 + 4 + 20 + 12):**

```html
<a href="#" style="display:flex;margin:0 -24px;padding:0 24px;text-decoration:none;color:#151B31">
  <div style="flex:1;display:flex;align-items:flex-start;gap:12px;padding:12px 0;border-bottom:1px solid #E7EAF0">
    <div style="flex:1;display:flex;flex-direction:column;gap:4px">
      <span style="font-size:16px;line-height:24px;font-weight:500;letter-spacing:-0.05px">신월4거리 북측 보조 구간</span>
      <span style="font-size:13px;line-height:20px;letter-spacing:-0.04px;color:#4B5570">마감 10월 21일 · 담당 김현우 · 350 m</span>
    </div>
    <!-- badge 할 일 (neutral), aligned to the first line -->
  </div>
</a>
```
Names wrap without limit; never `text-overflow:ellipsis`.

**Action row (two targets — content opens the detail, the button acts):**

```html
<div style="display:flex;align-items:center;gap:8px;margin:0 -24px;padding:0 24px 0 0">
  <a href="#" aria-label="20261018_0912, 연결이 끊겨 멈췄습니다, 상세 보기" style="flex:1;display:flex;align-items:flex-start;gap:16px;padding:12px 0 12px 24px;text-decoration:none;color:#151B31">
    [icon:warning 24 #8A5A06]
    <div style="display:flex;flex-direction:column;gap:4px">
      <span style="font-size:16px;line-height:24px;font-weight:500;letter-spacing:-0.05px">20261018_0912</span>
      <span style="font-size:13px;line-height:20px;letter-spacing:-0.04px;color:#4B5570">연결이 끊겨 멈췄습니다.</span>
    </div>
  </a>
  <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 16px;border-radius:10px;background:#FFFFFF;box-shadow:inset 0 0 0 1px #838891;color:#151B31;font-size:16px;line-height:20px;font-weight:600;flex:none">다시 보내기</button>
</div>
```
Divider: wrap both in a `<div style="border-bottom:1px solid #E7EAF0;margin-left:24px">` row when it is in a list. Other trailing actions: "복원", "보내기", "등록", "숨기기".

**Fact row (label 40 % · value 60 %):**

```html
<div style="display:flex;gap:16px;min-height:56px;align-items:center;border-bottom:1px solid #E7EAF0">
  <span style="width:40%;font-size:15px;line-height:24px;color:#4B5570">마감</span>
  <span style="flex:1;text-align:right;font-size:15px;line-height:24px;color:#151B31;font-variant-numeric:tabular-nums">10월 21일</span>
</div>
```
Stacked variant (font scale ≥ 1.5 or long values): `flex-direction:column;align-items:flex-start;gap:4px;padding:12px 0`, both start-aligned.

**Check row (준비 상태 and 시작 전 점검):**

```html
<div style="display:flex;align-items:flex-start;gap:12px;padding:16px 0;border-bottom:1px solid #E7EAF0">
  [icon:success 20 #22704B]
  <div style="flex:1;display:flex;flex-direction:column;gap:4px">
    <div style="display:flex;justify-content:space-between;gap:12px">
      <span style="font-size:16px;line-height:24px;font-weight:500">저장 공간</span>
      <span style="font-size:15px;line-height:24px;color:#4B5570;font-variant-numeric:tabular-nums">512 GB 남음</span>
    </div>
  </div>
</div>
```
States: checking — the icon is a 16 px spinner (§4 `spinner`, #3D3A86) inside a 20 px box and the value reads "확인 중"; failed — [icon:warning 20 #8A5A06], value "12 GB 남음", plus a second line `<p style="margin:0;font-size:15px;line-height:24px;color:#151B31">저장 공간이 수집 설정의 최소값보다 적습니다.</p>` and a tertiary remedy button ("장치 파일") under it; not applicable — [icon:unknown 20 #5D667B] and value "꺼짐".

**Switch row (56; whole row toggles):**

```html
<button role="switch" aria-checked="true" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:100%;display:flex;align-items:center;gap:16px;min-height:56px;padding:8px 0;background:transparent;border-bottom:1px solid #E7EAF0;text-align:left;color:#151B31">
  <span style="flex:1;display:flex;flex-direction:column;gap:4px">
    <span style="font-size:16px;line-height:24px;font-weight:500">장치 온도</span>
    <span style="font-size:13px;line-height:20px;color:#4B5570">80°C 이상이면 알립니다.</span>
  </span>
  <span aria-hidden="true" style="width:52px;height:32px;flex:none;box-sizing:border-box;padding:4px;border-radius:999px;background:#1F52B8;display:flex;justify-content:flex-end">
    <span style="width:24px;height:24px;border-radius:999px;background:#FFFFFF;box-shadow:0 1px 2px rgba(18,26,61,.25)"></span>
  </span>
</button>
```
Off: `aria-checked="false"`, track `background:#7D8598` (`switchTrackOff`) with `justify-content:flex-start` (thumb `switchThumbOff`). Disabled: track #E9ECF2, thumb #5F6880, the row meta shows the reason.

**Radio row and checkbox row (start-aligned control):**

```html
<div role="radiogroup" aria-label="밝은 테마" style="display:flex;flex-direction:column">
  <button role="radio" aria-checked="true" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:100%;display:flex;align-items:center;gap:16px;min-height:56px;padding:8px 0;background:transparent;border-bottom:1px solid #E7EAF0;text-align:left;color:#151B31">
    <span aria-hidden="true" style="width:20px;height:20px;flex:none;box-sizing:border-box;border-radius:999px;border:2px solid #1F52B8;display:flex;align-items:center;justify-content:center"><span style="width:10px;height:10px;border-radius:999px;background:#1F52B8"></span></span>
    <span style="flex:1;font-size:16px;line-height:24px;font-weight:500">표준</span>
  </button>
</div>
```
Unselected radio: `aria-checked="false"`, `border:2px solid #838891` and no inner dot. Checkbox rows are the same `<button>` with `role="checkbox"`; the indicator is `width:20px;height:20px;border-radius:6px;border:2px solid #838891` unchecked; checked = `background:#1F52B8;border-color:#1F52B8` with `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>` inside. A selected choice row in a sheet or list–detail adds `background:#E7EEFB` (`primaryTint`).

**Slider row (with −/+ for gloves):**

```html
<div style="display:flex;flex-direction:column;gap:8px;padding:12px 0;border-bottom:1px solid #E7EAF0">
  <div style="display:flex;justify-content:space-between"><span style="font-size:16px;line-height:24px;font-weight:500">저장 공간</span><span style="font-size:15px;line-height:24px;color:#4B5570;font-variant-numeric:tabular-nums">85% 이상</span></div>
  <div style="display:flex;align-items:center;gap:8px">
    <button aria-label="1 낮추기" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;flex:none;border-radius:999px;background:transparent;color:#4B5570;display:flex;align-items:center;justify-content:center">[icon:zoomOut 24 #4B5570]</button>
    <div aria-hidden="true" style="flex:1;height:48px;display:flex;align-items:center">
      <span style="flex:71;height:4px;border-radius:2px 0 0 2px;background:#1F52B8"></span>
      <span style="width:20px;height:20px;flex:none;border-radius:999px;background:#1F52B8;box-shadow:0 0 0 2px #FFFFFF"></span>
      <span style="flex:29;height:4px;border-radius:0 2px 2px 0;background:#838891"></span>
    </div>
    <button aria-label="1 올리기" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;flex:none;border-radius:999px;background:transparent;color:#4B5570;display:flex;align-items:center;justify-content:center">[icon:add 24 #4B5570]</button>
  </div>
</div>
```
The two `flex` numbers are the filled and remaining shares: 저장 공간 50–99 % at 85 → 71 : 29; 장치 온도 40–110 °C at 80 → 57 : 43; 팬 20–100 % at 40 → 25 : 75. The drawn track is decorative (`aria-hidden`); the value is the text above it and the −/+ buttons are the controls.

### 3.8 Badges (status only; never tappable, never a time)

```html
<span style="display:inline-flex;align-items:center;gap:6px;min-height:24px;padding:0 8px;box-sizing:border-box;border-radius:999px;background:#E2F1E9;color:#22704B;font-size:13px;line-height:20px;font-weight:600;white-space:nowrap"><span style="width:6px;height:6px;border-radius:999px;background:#22704B"></span>연결됨</span>
```

| Tone | Fill · dot and text | Words (spec §3.12) |
|---|---|---|
| success | #E2F1E9 · #22704B | 연결됨, 수신 중, 정밀 위치, 구역 안, 저장 확인, 수신 확인, 보고함, 완료, 종료 |
| warning | #FBF0DA · #8A5A06 | 기본 연결, 응답 없음, 지연, 대략 위치, 구역 밖 · 35 m, 막힘, 먼저 바뀜, 권한 필요 |
| danger | #FBE8E5 · #B3372C | 인증 필요, 위치 없음, 내용 불일치, 실패, 보고 실패, 시작하지 못함 |
| info | #E7EEFB · #1F52B8 | 진행 중, 수집 중, 보내는 중, 경계 근처, 연결 가능, 대기 중 (sensor) |
| pending | #E4E3F4 · #3D3A86 | 확인 중, 시작 확인 중, 종료 확인 중, 수신 확인 중, 보고 대기, 위치 확인 중 |
| neutral | #EEF0F4 · #5D667B | 할 일, 미확인, 오프라인, 대기 중 (transfer), 보고 전, 취소됨, 꺼짐 |

Variants: **on-map** `background:#FFFFFF;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 1px 2px rgba(18,26,61,.06)` with the tone colour for dot and text; **on-dark** (camera, QR, video) `background:#1E1F21` with #88CCA5 실시간 / #E3BC7A 지연 / #BDBFC4 대기 중 / #E9938C 받지 못함; **고대비** adds `box-shadow:inset 0 0 0 1px` in the solid tone colour.

### 3.9 Buttons

```html
<!-- primary, 52, full width in action areas -->
<button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;width:100%;padding:0 16px;border-radius:10px;background:#1F52B8;color:#FFFFFF;font-size:16px;line-height:20px;font-weight:600">수집 시작</button>
<!-- secondary, 48 (52 beside a primary) -->
<button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 16px;border-radius:10px;background:#FFFFFF;box-shadow:inset 0 0 0 1px #838891;color:#151B31;font-size:16px;line-height:20px;font-weight:600">다시 스캔</button>
<!-- tertiary, 48 target -->
<button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 12px;border-radius:10px;background:transparent;color:#1F52B8;font-size:16px;line-height:20px;font-weight:600">연결 방법</button>
<!-- danger-outline, 52 in action areas -->
<button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;width:100%;padding:0 16px;border-radius:10px;background:#FFFFFF;box-shadow:inset 0 0 0 1px #B3372C;color:#B3372C;font-size:16px;line-height:20px;font-weight:600">수집 종료</button>
<!-- danger-filled, only as an S1b confirm -->
<button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;padding:0 16px;border-radius:10px;background:#B3372C;color:#FFFFFF;font-size:16px;line-height:20px;font-weight:600">영구 삭제</button>
<!-- icon button, 48 circle -->
<button aria-label="장치 위치로" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;border-radius:999px;background:transparent;color:#4B5570;display:flex;align-items:center;justify-content:center">[icon:myLocation 24 #4B5570]</button>
```

| State | Primary | Secondary / tertiary / icon | Danger-outline | Danger-filled |
|---|---|---|---|---|
| pressed | `background:#1A47A0` | `background:#E9ECF2` | `background:#E9ECF2` | `background:#9A2E25` |
| focused | add the focus ring | add the focus ring | add the focus ring | add the focus ring |
| disabled | `background:#E9ECF2;color:#5F6880` and the reason next to it | `color:#5F6880` (secondary keeps its border) | `box-shadow:inset 0 0 0 1px #838891;color:#5F6880` | — |
| busy | label "보내는 중" with a 16 px spinner in #FFFFFF before it (gap 10) | — | label "종료하는 중" with a #B3372C spinner | — |

**Pairs (the one stacking rule):** side by side when both labels fit on one line in equal halves — `<div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px">` with [secondary][primary] (primary at the end, both 52). Otherwise stack full width with the primary first: `<div style="display:flex;flex-direction:column;gap:8px">`. Dialog actions: natural widths, end-aligned (`display:flex;justify-content:flex-end;gap:8px;flex-wrap:wrap`); when they do not fit, the same stacked column with the confirm first.

### 3.10 Fields

```html
<div style="display:flex;flex-direction:column;gap:8px">
  <label for="pw" style="font-size:14px;line-height:20px;font-weight:500;color:#151B31">비밀번호</label>
  <div style="display:flex;align-items:center;min-height:56px;padding:0 4px 0 16px;box-sizing:border-box;border-radius:10px;background:#F1F3F7;box-shadow:inset 0 0 0 1px #838891">
    <input id="pw" type="password" value="abcdefgh" style="flex:1;min-width:0;border:0;outline:0;background:transparent;font-family:inherit;font-size:16px;line-height:24px;color:#151B31">
    <button aria-label="비밀번호 보기" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;border-radius:999px;background:transparent;display:flex;align-items:center;justify-content:center">[icon:eye 24 #4B5570]</button>
  </div>
  <p style="margin:4px 0 0;font-size:13px;line-height:20px;color:#4B5570">대소문자를 구분합니다.</p>
</div>
```
States: **focused** `box-shadow:inset 0 0 0 2px #1F52B8`; **error** `box-shadow:inset 0 0 0 2px #B3372C` and the helper becomes `<p style="margin:4px 0 0;display:flex;gap:6px;font-size:13px;line-height:20px;color:#B3372C">[icon:danger 16 #B3372C]코드 형식이 맞지 않습니다.</p>`; **disabled** `background:#E9ECF2` with value `color:#5F6880`. Plain text fields drop the trailing button; numeric fields add a unit suffix `<span style="color:#4B5570">GB</span>` before the end padding. Placeholders only as examples ("예: GEON-5G") in `textSubtle`.

**Picker field (opens a choice sheet):**

```html
<button style="font-family:inherit;border:0;margin:0;cursor:pointer;width:100%;min-height:56px;padding:6px 12px 6px 16px;box-sizing:border-box;border-radius:10px;background:#F1F3F7;box-shadow:inset 0 0 0 1px #838891;display:flex;align-items:center;gap:8px;text-align:left;color:#151B31">
  <span style="flex:1;display:flex;flex-direction:column">
    <span style="font-size:13px;line-height:20px;color:#4B5570">프로젝트</span>
    <span style="font-size:16px;line-height:24px">수원시 도로대장</span>
  </span>
  [icon:chevronDown 20 #4B5570]
</button>
```

**Segmented (2–4 equal segments; a pill track is allowed):**

```html
<div role="tablist" aria-label="보기" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0;height:48px;padding:3px;box-sizing:border-box;border-radius:999px;background:#F1F3F7;box-shadow:inset 0 0 0 1px #838891">
  <button role="tab" aria-selected="true" style="font-family:inherit;border:0;margin:0;cursor:pointer;border-radius:999px;background:#FFFFFF;box-shadow:inset 0 0 0 1px #838891;color:#151B31;font-size:14px;line-height:20px;font-weight:600">GEO-X-1</button>
  <button role="tab" aria-selected="false" style="font-family:inherit;border:0;margin:0;cursor:pointer;border-radius:999px;background:transparent;color:#4B5570;font-size:14px;line-height:20px;font-weight:500">전체</button>
</div>
```
Three segments: `repeat(3,minmax(0,1fr))`. When a label would not fit (or font scale ≥ 1.5) draw a picker field instead.

### 3.11 Figure grid (plain, aligned to the gutter)

```html
<div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px">
  <div style="display:flex;flex-direction:column">
    <span style="font-size:13px;line-height:20px;color:#4B5570">온도</span>
    <span style="margin-top:14px;font-size:24px;line-height:32px;font-weight:700;letter-spacing:-0.6px;color:#151B31;font-variant-numeric:tabular-nums">54°C</span>
    <span style="font-size:13px;line-height:20px;color:#4B5570">&#8203;</span>
  </div>
  <div style="display:flex;flex-direction:column">
    <span style="font-size:13px;line-height:20px;color:#4B5570">저장 여유</span>
    <span style="margin-top:14px;font-size:24px;line-height:32px;font-weight:700;letter-spacing:-0.6px;color:#151B31;font-variant-numeric:tabular-nums">512 GB</span>
    <span style="font-size:13px;line-height:20px;color:#4B5570">57% 남음</span>
  </div>
</div>
```
Cells: **미확인** — value `<span style="margin-top:14px;font-size:18px;line-height:32px;font-weight:600;color:#5D667B">미확인</span>`; **stale** — the last value stays `text`, detail "마지막 값 기준"; **over threshold** — the label row gets [icon:warning 16 #8A5A06] before the label (gap 6), the value stays `text`, the detail reads "기준 80°C 초과" in #8A5A06. The detail line is always present (an empty line holds the space). Contained variant (inside a panel or Focus block): the grid gets `padding:16px` once.

### 3.12 Action area (sticky above the bar, or inline)

```html
<div style="position:absolute;left:0;right:0;bottom:112px;background:#FFFFFF;border-top:1px solid #E7EAF0;padding:12px 24px;display:flex;flex-direction:column;gap:8px">
  <p style="margin:0;display:flex;gap:6px;align-items:flex-start;font-size:15px;line-height:24px;letter-spacing:-0.05px;color:#4B5570"><span style="display:flex;margin-top:4px">[icon:success 16 #22704B]</span>GEO-X-1 · 같은 네트워크 · 시작할 수 있습니다.</p>
  <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;width:100%;border-radius:10px;background:#1F52B8;color:#FFFFFF;font-size:16px;line-height:20px;font-weight:600">수집 시작</button>
</div>
```
Without a bar: `bottom:0;padding:12px 24px 60px`. Inline (landscape, compact height): drop `position:absolute…bottom` and the top border; it becomes the last block of `main`. Readiness icons: success, warning, pending, neutral per state.

### 3.13 Receipts

**Receipt list (수집 결과, 전송 상세):** four two-line rows — 수집 종료 · 장치 저장 · 서버 수신 · GEO& Work 보고 — each `title-sm` 500 + badge aligned to the first line + `caption` meta ("1,204개 · 1.2 GB", "이 휴대폰에서 보낸 기록이 없습니다.", "서버 수신을 확인한 뒤 보고할 수 있습니다.").

**Receipt line (inside list rows):**

```html
<div style="display:flex;flex-wrap:wrap;gap:4px 16px;margin-top:4px">
  <span style="display:inline-flex;align-items:center;gap:6px;font-size:13px;line-height:20px;color:#4B5570">수집 종료<span style="width:6px;height:6px;border-radius:999px;background:#22704B"></span><span style="font-weight:600;color:#151B31">종료</span></span>
  <span style="display:inline-flex;align-items:center;gap:6px;font-size:13px;line-height:20px;color:#4B5570">장치 저장<span style="width:6px;height:6px;border-radius:999px;background:#22704B"></span><span style="font-weight:600;color:#151B31">저장 확인</span></span>
  <span style="display:inline-flex;align-items:center;gap:6px;font-size:13px;line-height:20px;color:#4B5570">서버 수신<span style="width:6px;height:6px;border-radius:999px;background:#5D667B"></span><span style="font-weight:600;color:#151B31">미확인</span></span>
</div>
```

### 3.14 Progress and skeleton

```html
<div style="display:flex;align-items:center;gap:12px">
  <div style="flex:1;height:4px;border-radius:2px;background:#F1F3F7;overflow:hidden"><div style="width:64%;height:4px;border-radius:2px;background:#1F52B8"></div></div>
  <span style="font-size:13px;line-height:20px;color:#4B5570;font-variant-numeric:tabular-nums">64%</span>
</div>
```
Indeterminate (drawn): the indicator is a 30 % segment at `margin-left:20%`, no percentage. Skeleton: rows of `height:16px;border-radius:6px;background:#F3F5F8` bars shaped like the rows they replace (no shimmer).

### 3.15 Inline note and state block

```html
<div style="display:flex;gap:12px;padding:16px;border-radius:12px;background:#E7EEFB">
  [icon:info 20 #1F52B8]
  <p style="margin:0;flex:1;font-size:15px;line-height:24px;color:#151B31">공유기 없이 휴대폰과 장치를 바로 연결합니다. 서버로 보내기는 같은 네트워크에서만 됩니다.</p>
</div>
```
Warning note: `background:#FBF0DA` + [icon:warning #8A5A06].

```html
<div style="display:flex;flex-direction:column;align-items:flex-start;gap:4px;padding-top:56px">
  <h2 style="margin:0;font-size:16px;line-height:24px;font-weight:600;color:#151B31">수집 기록이 없습니다</h2>
  <p style="margin:0;font-size:15px;line-height:24px;color:#4B5570">수집을 마치면 여기에 남습니다.</p>
</div>
```
Top-aligned (56 under the top bar for full-screen states, 0 inside a section), never centred. Add one action under it with `margin-top:8px` (tertiary, or the primary when it is the main action: "다시 시도", "권한 허용", "설정에서 허용", "목록으로").

### 3.16 Sheets (S3 choice, S4 form, S5 info, S8 start)

```html
<div style="position:absolute;inset:0;background:rgba(18,26,61,.32)"></div>
<div role="dialog" aria-label="GEO-X-1 연결 방법" style="position:absolute;left:0;right:0;bottom:0;max-height:811px;background:#FFFFFF;border-radius:20px 20px 0 0;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 20px 48px rgba(18,26,61,.18);display:flex;flex-direction:column;padding-bottom:48px">
  <div style="display:flex;justify-content:center;padding-top:8px"><span style="width:32px;height:4px;border-radius:2px;background:#838891"></span></div>
  <div style="padding:16px 24px 8px;display:flex;flex-direction:column;gap:4px">
    <h2 style="margin:0;font-size:18px;line-height:26px;font-weight:600;letter-spacing:-0.27px">GEO-X-1 연결 방법</h2>
    <p style="margin:0;font-size:15px;line-height:24px;color:#4B5570">연결됨 · 마지막 응답 방금</p>
  </div>
  <div style="padding:0 24px;display:flex;flex-direction:column">
    <a href="#" style="display:flex;gap:16px;align-items:flex-start;padding:12px 0;border-bottom:1px solid #E7EAF0;text-decoration:none;color:#151B31">
      [icon:wifi 24 #4B5570]
      <span style="display:flex;flex-direction:column;gap:4px"><span style="font-size:16px;line-height:24px;font-weight:500">같은 네트워크에서 다시 찾기</span><span style="font-size:13px;line-height:20px;color:#4B5570">휴대폰과 장치가 같은 Wi-Fi에 있을 때 씁니다. 모든 기능을 쓸 수 있습니다.</span></span>
    </a>
    <!-- more rows -->
  </div>
</div>
```
Roles: scrim `scrim`, sheet `surface` + `shadow-lg`, handle `borderStrong`, rows as §3.7. The 48 px inset is the sheet's own bottom padding. **Form sheet (S4):** fields (§3.10) in the body and a pinned footer `<div style="padding:12px 24px;border-top:1px solid #E7EAF0">` with the primary (and secondary per the stacking rule). **Info sheet (S5):** read-only sections, no footer. **Full-screen fallback:** when header + footer would leave < 160 px of body, draw it as a focused screen with a detail top bar whose leading button is [icon:close] "닫기".

**S8 수집 시작 확인** (always a sheet):

```html
<div role="dialog" aria-label="수집 시작 확인" style="position:absolute;left:0;right:0;bottom:0;background:#FFFFFF;border-radius:20px 20px 0 0;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 20px 48px rgba(18,26,61,.18);display:flex;flex-direction:column">
  <div style="display:flex;justify-content:center;padding-top:8px"><span style="width:32px;height:4px;border-radius:2px;background:#838891"></span></div>
  <div style="padding:16px 24px 0;display:flex;flex-direction:column;gap:8px">
    <h2 style="margin:0;font-size:18px;line-height:26px;font-weight:600;letter-spacing:-0.27px">신월4거리 북측 보조 구간 수집을 시작할까요?</h2>
    <p style="margin:0;font-size:15px;line-height:24px;color:#4B5570">수원시 도로대장 · GEO-X-1</p>
    <div style="display:flex;align-items:center;gap:8px"><!-- badge 구역 안 or the distance sentence in body --></div>
  </div>
  <div style="padding:8px 24px 0">
    <!-- 시작 전 점검 check rows, §3.7 (연결, 장치 시간, 저장 공간, 카메라, 위치(GNSS)) -->
    <p style="margin:12px 0 0;font-size:15px;line-height:24px;color:#151B31">GEO& Work 상태가 '진행 중'으로 바뀝니다.</p>
    <p style="margin:4px 0 0;font-size:15px;line-height:24px;color:#151B31">시작하면 이 수집의 프로젝트와 구간을 바꿀 수 없습니다.</p>
  </div>
  <div style="margin-top:16px;padding:12px 24px 60px;border-top:1px solid #E7EAF0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px">
    <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;border-radius:10px;background:#FFFFFF;box-shadow:inset 0 0 0 1px #838891;color:#151B31;font-size:16px;line-height:20px;font-weight:600">취소</button>
    <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;border-radius:10px;background:#1F52B8;color:#FFFFFF;font-size:16px;line-height:20px;font-weight:600">수집 시작</button>
  </div>
</div>
```
Checking: rows in the checking state and the primary busy "점검하는 중". Failed: failed rows with reason and remedy, the primary disabled, a readiness-style line above the footer "점검을 통과해야 시작할 수 있습니다." and a tertiary "다시 점검".

### 3.17 Dialogs (S1a confirm, S1b danger confirm, S2 info)

```html
<div style="position:absolute;inset:0;background:rgba(18,26,61,.32)"></div>
<div role="alertdialog" aria-label="GEO-X-1을 재부팅할까요?" style="position:absolute;left:24px;right:24px;top:50%;transform:translateY(-50%);background:#FFFFFF;border-radius:14px;padding:24px;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 20px 48px rgba(18,26,61,.18);display:flex;flex-direction:column;gap:8px">
  <h2 style="margin:0;font-size:18px;line-height:26px;font-weight:600;letter-spacing:-0.27px;color:#151B31">GEO-X-1을 재부팅할까요?</h2>
  <p style="margin:0;font-size:15px;line-height:24px;color:#151B31">재부팅하는 동안 연결이 끊깁니다. 진행 중인 전송은 멈춥니다.</p>
  <div style="display:flex;justify-content:flex-end;flex-wrap:wrap;gap:8px;margin-top:16px">
    <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;padding:0 12px;border-radius:10px;background:transparent;color:#1F52B8;font-size:16px;line-height:20px;font-weight:600">취소</button>
    <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:52px;padding:0 16px;border-radius:10px;background:#B3372C;color:#FFFFFF;font-size:16px;line-height:20px;font-weight:600">재부팅</button>
  </div>
</div>
```
S1a: the confirm is the primary (`background:#1F52B8`). Disabled confirm: `background:#E9ECF2;color:#5F6880` and the reason as a second body line ("수집 중에는 전원을 끌 수 없습니다."). Fact rows (§3.7) may sit between body and actions. S2 info: read-only fact rows and a single tertiary "닫기". Copy: spec §8 only. No icons, no centred titles.

### 3.18 Menu

```html
<div role="menu" style="position:absolute;top:84px;right:8px;min-width:200px;max-width:280px;background:#FFFFFF;border-radius:12px;padding:8px 0;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 20px 48px rgba(18,26,61,.18);display:flex;flex-direction:column">
  <button role="menuitem" style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 16px;background:transparent;text-align:left;font-size:15px;line-height:24px;color:#151B31">선택</button>
  <button role="menuitem" style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 16px;background:transparent;text-align:left;font-size:15px;line-height:24px;color:#151B31">휴지통</button>
  <div style="height:1px;background:#E7EAF0;margin:8px 0"></div>
  <button role="menuitem" style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 16px;background:transparent;text-align:left;font-size:15px;line-height:24px;color:#B3372C">휴지통으로 이동</button>
</div>
```
`top:84px` puts it just under a detail top bar's ⋮ (36 + 48). Destructive items last, after the divider, in `danger`. No scrim.

### 3.19 Snackbar

```html
<div role="status" style="position:absolute;left:16px;right:16px;bottom:120px;display:flex;align-items:center;gap:8px;padding:4px 4px 4px 16px;min-height:48px;box-sizing:border-box;border-radius:12px;background:#151B31;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 20px 48px rgba(18,26,61,.18)">
  <p style="margin:0;flex:1;padding:8px 0;font-size:15px;line-height:24px;color:#FFFFFF">수집 기록을 휴지통으로 옮겼습니다.</p>
  <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 12px;border-radius:10px;background:transparent;color:#8FB0F0;font-size:16px;line-height:20px;font-weight:600">실행 취소</button>
</div>
```
`bottom:120px` sits 8 px above the bottom bar; above an action area use its top + 8. Roles: `inverse`, `onInverse`, `inverseAction`.

### 3.20 기술 정보 and log view (developer opt-in)

```html
<section style="padding-top:28px">
  <button aria-expanded="true" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:100%;min-height:56px;display:flex;align-items:center;gap:8px;background:transparent;color:#151B31;text-align:left;border-bottom:1px solid #E7EAF0">
    <span style="flex:1;font-size:16px;line-height:24px;font-weight:600">기술 정보</span>[icon:chevronUp 20 #5F6880]
  </button>
  <div style="display:flex;align-items:center;gap:8px;min-height:56px;border-bottom:1px solid #E7EAF0">
    <span style="width:32%;font-size:15px;line-height:24px;color:#4B5570">실행 ID</span>
    <span style="flex:1;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:13px;line-height:20px;color:#151B31;word-break:break-all">[실행 ID]</span>
    <button aria-label="실행 ID 복사" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;border-radius:999px;background:transparent;display:flex;align-items:center;justify-content:center">[icon:copy 20 #4B5570]</button>
  </div>
</section>
```
Collapsed: only the header row with [icon:chevronDown]. Log view (P9): lines in `mono` on `surface`, a live line at the top (`<span style="display:inline-flex;align-items:center;gap:6px;font-size:13px;color:#4B5570"><span style="width:6px;height:6px;border-radius:999px;background:#22704B"></span>실시간</span>`), and when scrolled away a floating secondary button "최신 로그" at `position:absolute;right:24px;bottom:136px` with `shadow-sm`.

### 3.21 Media stage (camera, QR, video, image preview)

```html
<main style="flex:1;position:relative;background:#000000;overflow:hidden">
  <div style="position:absolute;top:12px;left:12px;display:flex;gap:8px;align-items:center">
    <span style="display:inline-flex;align-items:center;gap:6px;min-height:24px;padding:0 8px;border-radius:999px;background:#1E1F21;color:#88CCA5;font-size:13px;line-height:20px;font-weight:600"><span style="width:6px;height:6px;border-radius:999px;background:#88CCA5"></span>실시간</span>
    <span style="padding:2px 8px;border-radius:12px;background:rgba(0,0,0,.72);color:#FFFFFF;font-size:13px;line-height:20px;font-variant-numeric:tabular-nums">1920 × 1080</span>
  </div>
  <!-- the picture area: a neutral #2B2D30 rectangle filling the stage; never a fake photo -->
</main>
<div style="background:#000000;padding:16px 24px 64px;display:flex;align-items:center;justify-content:space-between">
  <button style="font-family:inherit;border:0;margin:0;cursor:pointer;min-height:48px;padding:0 12px;border-radius:10px;background:transparent;color:#FFFFFF;font-size:16px;line-height:20px;font-weight:600">저장 위치</button>
  <button aria-label="캡처" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:64px;height:64px;border-radius:999px;background:transparent;box-shadow:inset 0 0 0 4px #FFFFFF;display:flex;align-items:center;justify-content:center"><span style="width:48px;height:48px;border-radius:999px;background:#FFFFFF"></span></button>
  <span style="width:96px"></span>
</div>
```
The 48 px inset under the capture bar is black (`padding-bottom:64px` = 16 + 48). **QR:** a 240 × 240 frame centred in the stage drawn as four white L-shaped corners (`width:40px;height:40px;border:4px solid #FFFFFF` with two sides removed per corner), the instruction plate under it `background:rgba(0,0,0,.72);color:#FFFFFF;padding:8px 12px;border-radius:12px;font-size:15px;line-height:24px;text-align:center` "장치 본체의 QR 코드를 사각형 안에 맞추세요.", torch icon button top-end in white, and the outlined white secondary "코드 직접 입력" (`box-shadow:inset 0 0 0 1px #FFFFFF;color:#FFFFFF;background:transparent`) near the bottom. **Video:** the same stage with a control row on a 72 % plate: [icon:pause 24 #FFFFFF], a 4 px position bar (track rgba(255,255,255,.4), progress #FFFFFF), time "00:12 / 01:05" in `mono` #FFFFFF.

### 3.22 Map card, full map, panel and run panel

**Map card (364 × 200; 240 on 수집 중):**

```html
<a href="#" aria-label="수집 구역 지도, 경로 1.2 km, 장치가 구역에서 350 m 떨어져 있습니다. 크게 보기" style="display:block;position:relative;height:200px;border-radius:12px;overflow:hidden;box-shadow:inset 0 0 0 1px #E7EAF0;text-decoration:none">
  <!-- base map + overlays SVG, §5.3 (width 364, height 200) -->
  <span style="position:absolute;top:12px;left:12px;display:inline-flex;align-items:center;gap:6px;min-height:24px;padding:0 8px;border-radius:999px;background:#FFFFFF;color:#22704B;font-size:13px;line-height:20px;font-weight:600;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 1px 2px rgba(18,26,61,.06)"><span style="width:6px;height:6px;border-radius:999px;background:#22704B"></span>구역 안</span>
  <span style="position:absolute;left:8px;bottom:8px;padding:0 6px;border-radius:6px;background:#FFFFFF;color:#4B5570;font-size:13px;line-height:20px">© VWorld</span>
</a>
<div style="margin-top:8px;display:flex;flex-direction:column">
  <span style="font-size:13px;line-height:20px;color:#4B5570">경로 1.2 km · 폭 20 m · 지점 4곳</span>
  <span style="font-size:13px;line-height:20px;color:#4B5570">장치가 구역에서 350 m 떨어져 있습니다.</span>
</div>
```
The card's border is drawn as an inset shadow so the SVG does not cover it; 고대비 uses #4D5466. States: loading — the SVG replaced by `background:#F3F5F8` and the centred-left caption "지도를 불러오는 중입니다"; tiles failed — overlays on the plain `sunken` background and the caption line "배경 지도를 불러오지 못했습니다. 구역은 그대로 표시합니다."; range not computed — features only and "구역 범위가 아직 계산되지 않았습니다."

**Full-screen map (focused, no bar):** the stage fills from the top bar to the screen bottom; floating controls on `mapPlate` circles:

```html
<div style="position:absolute;top:104px;right:12px;display:flex;flex-direction:column;align-items:flex-end;gap:8px">
  <div role="tablist" aria-label="지도 종류" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));width:136px;height:48px;padding:3px;box-sizing:border-box;border-radius:999px;background:#FFFFFF;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 1px 2px rgba(18,26,61,.06)">
    <button role="tab" aria-selected="true" style="font-family:inherit;border:0;margin:0;cursor:pointer;border-radius:999px;background:#F1F3F7;box-shadow:inset 0 0 0 1px #838891;color:#151B31;font-size:14px;line-height:20px;font-weight:600">일반</button>
    <button role="tab" aria-selected="false" style="font-family:inherit;border:0;margin:0;cursor:pointer;border-radius:999px;background:transparent;color:#4B5570;font-size:14px;line-height:20px;font-weight:500">위성</button>
  </div>
  <button aria-label="장치 위치로" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;border-radius:999px;background:#FFFFFF;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 1px 2px rgba(18,26,61,.06);display:flex;align-items:center;justify-content:center">[icon:myLocation 24 #4B5570]</button>
  <button aria-label="구역 전체 보기" style="font-family:inherit;border:0;margin:0;cursor:pointer;width:48px;height:48px;border-radius:999px;background:#FFFFFF;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 1px 2px rgba(18,26,61,.06);display:flex;align-items:center;justify-content:center">[icon:fitZone 24 #4B5570]</button>
</div>
<div style="position:absolute;right:12px;bottom:200px;display:flex;flex-direction:column;gap:8px">
  <!-- 확대 [icon:zoomIn] and 축소 [icon:zoomOut] as the same 48 px plates -->
</div>
```
"장치 위치로" selected (following on): `background:#E7EEFB` with the icon in #1F52B8. Focus on a map control: `box-shadow:0 0 0 2px #1F52B8,0 0 0 4px #FFFFFF`.

**Panel (peek 128 + 48 inset):**

```html
<div style="position:absolute;left:0;right:0;bottom:0;background:#FFFFFF;border-radius:20px 20px 0 0;box-shadow:0 0 0 1px rgba(18,26,61,.06),0 20px 48px rgba(18,26,61,.18);padding:8px 24px 60px;display:flex;flex-direction:column;gap:8px">
  <div style="display:flex;align-items:center;justify-content:space-between">
    <span style="width:32px;height:4px;border-radius:2px;background:#838891;margin-left:166px"></span>
    <button aria-label="패널 펼치기" style="font-family:inherit;border:0;margin:0 -12px 0 0;cursor:pointer;width:48px;height:48px;border-radius:999px;background:transparent;display:flex;align-items:center;justify-content:center">[icon:chevronUp 24 #4B5570]</button>
  </div>
  <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap"><!-- zone badge --><span style="font-size:15px;line-height:24px;color:#151B31">정밀 위치 98%</span></div>
  <span style="font-size:13px;line-height:20px;color:#4B5570">경로 1.2 km · 폭 20 m</span>
</div>
```
Expanded: the same container grown upward with sections 범례 (swatch + word rows), 장치 위치 (fact rows with the coordinate in `mono`), the switch row "휴대폰 위치 표시", and the row "위치 품질 자세히 ›"; the button reads "패널 접기" with [icon:chevronDown]. **Run panel** (mode 수집 중): add the line "수집 중 · 00:12:34" (body, tnum) and a full-width danger-outline 52 "수집 종료" above the bottom padding. **Landscape:** the panel is a 320 px column at the end side, full height under the top bar, collapsible to 56 px.

### 3.23 Device photo

`<img src="/_blob/462cdfa56113406ef6f6babfcb1d6435" alt="" style="width:56px;height:56px;object-fit:contain">` at 40, 56 or 120 px; no frame, no radius; decorative (the name is text next to it).

---

## 4. Icons (the complete `GeoIcons` set)

Wrapper (24 px; for 20 or 16 change only `width` and `height`; the colour comes from `style="color:…"`):

```html
<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="color:#4B5570;flex:none">…</svg>
```

Put the inner markup of the row inside it. Dots are filled circles (`fill="currentColor" stroke="none"`). Filled tab variants knock out their inner lines with the bar colour (`stroke="#FFFFFF"` in 표준; use the theme's `nav`).

| Name | Inner markup |
|---|---|
| tabWork | `<rect x="4" y="5" width="16" height="16" rx="2.5"/><path d="M9 3.5h6v3H9z"/><path d="M8 12h8M8 16h5"/>` |
| tabWork-filled | `<rect x="4" y="5" width="16" height="16" rx="2.5" fill="currentColor"/><path d="M9 3.5h6v3H9z" fill="currentColor"/><path d="M8 12h8M8 16h5" stroke="#FFFFFF"/>` |
| tabDevice | `<rect x="3" y="6" width="18" height="12" rx="2.5"/><circle cx="15.5" cy="12" r="2.5"/><path d="M7 10h3M7 14h3"/>` |
| tabDevice-filled | `<rect x="3" y="6" width="18" height="12" rx="2.5" fill="currentColor"/><circle cx="15.5" cy="12" r="2.5" stroke="#FFFFFF"/><path d="M7 10h3M7 14h3" stroke="#FFFFFF"/>` |
| tabTransfer | `<path d="M4 14v3.5A2.5 2.5 0 0 0 6.5 20h11a2.5 2.5 0 0 0 2.5-2.5V14"/><path d="M12 15V4"/><path d="M7.5 8.5L12 4l4.5 4.5"/>` |
| tabTransfer-filled | `<rect x="3" y="3" width="18" height="18" rx="4" fill="currentColor"/><path d="M12 16V8M8.5 11.5L12 8l3.5 3.5" stroke="#FFFFFF"/>` |
| tabSettings (also the gear on the 장치 등록 scene) | `<path d="M9.92 5.21L10.61 2.70L13.39 2.70L14.08 5.21L15.33 5.73L17.59 4.44L19.56 6.41L18.27 8.67L18.79 9.92L21.30 10.61L21.30 13.39L18.79 14.08L18.27 15.33L19.56 17.59L17.59 19.56L15.33 18.27L14.08 18.79L13.39 21.30L10.61 21.30L9.92 18.79L8.67 18.27L6.41 19.56L4.44 17.59L5.73 15.33L5.21 14.08L2.70 13.39L2.70 10.61L5.21 9.92L5.73 8.67L4.44 6.41L6.41 4.44L8.67 5.73Z"/><circle cx="12" cy="12" r="3"/>` |
| tabSettings-filled | the same gear path with `fill="currentColor"`, then `<circle cx="12" cy="12" r="3" fill="#FFFFFF" stroke="#FFFFFF"/>` |
| back | `<path d="M19 12H5"/><path d="M11 6l-6 6 6 6"/>` |
| close | `<path d="M6 6l12 12M18 6L6 18"/>` |
| more | `<circle cx="12" cy="5" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="19" r="1.5" fill="currentColor" stroke="none"/>` |
| bell | `<path d="M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>` |
| add (also zoomIn) | `<path d="M12 5v14M5 12h14"/>` |
| zoomOut | `<path d="M5 12h14"/>` |
| search | `<circle cx="11" cy="11" r="6"/><path d="M20 20l-4.5-4.5"/>` |
| chevronRight | `<path d="M9 6l6 6-6 6"/>` |
| chevronDown (also collapsePanel) | `<path d="M6 9l6 6 6-6"/>` |
| chevronUp (expandPanel) | `<path d="M6 15l6-6 6 6"/>` |
| copy | `<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15H4.5A1.5 1.5 0 0 1 3 13.5v-9A1.5 1.5 0 0 1 4.5 3h9A1.5 1.5 0 0 1 15 4.5V5"/>` |
| refresh (only inside "다시 시도") | `<path d="M20 11a8 8 0 1 0-2.34 5.66"/><path d="M20 5v6h-6"/>` |
| send | `<path d="M12 20V8"/><path d="M7 13l5-5 5 5"/><path d="M5 4h14"/>` |
| trash | `<path d="M4 7h16"/><path d="M10 11v6M14 11v6"/><path d="M6 7l1 13h10l1-13"/><path d="M9 7V4h6v3"/>` |
| restore | `<path d="M9 14L4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/>` |
| play | `<path d="M8 5v14l11-7z"/>` |
| pause | `<path d="M9 5v14M15 5v14"/>` |
| stop | `<rect x="6" y="6" width="12" height="12" rx="2"/>` |
| torch | `<path d="M8 3h8v4l-2 3v11h-4V10L8 7z"/><path d="M12 13v3"/>` |
| torchOff | `<path d="M8 3h8v4l-2 3v11h-4V10L8 7z"/><path d="M4 4l16 16"/>` |
| camera | `<path d="M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/><circle cx="12" cy="13.5" r="3.5"/>` |
| qr | `<path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4z"/><path d="M14 14h2v2h-2zM18 18h2v2h-2zM18 14h2M14 18v2"/>` |
| myLocation | `<circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2.5" fill="currentColor" stroke="none"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>` |
| fitZone | `<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>` |
| layers | `<path d="M12 4L3 9l9 5 9-5z"/><path d="M3 14l9 5 9-5"/>` |
| eye | `<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>` |
| eyeOff | `<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/><path d="M4 4l16 16"/>` |
| wifi | `<path d="M2.5 9.5a14 14 0 0 1 19 0"/><path d="M5.5 12.8a9.5 9.5 0 0 1 13 0"/><path d="M8.6 16a5 5 0 0 1 6.8 0"/><circle cx="12" cy="19.2" r="1.1" fill="currentColor" stroke="none"/>` |
| bluetooth | `<path d="M7 7l10 10-5 4V3l5 4L7 17"/>` |
| link (직접 연결) | `<path d="M4 9h15l-3.5-3.5"/><path d="M20 15H5l3.5 3.5"/>` |
| power | `<path d="M12 3v8"/><path d="M7.4 6.4a7.5 7.5 0 1 0 9.2 0"/>` |
| reboot | `<path d="M4 12a8 8 0 1 0 2.34-5.66"/><path d="M4 4v5h5"/>` |
| fan | `<path d="M12 12C12 7.2 14.6 4.4 17.1 5.6C18.6 6.4 18.1 9.6 12 12Z M12 12C16.16 14.4 17.28 18.05 14.99 19.62C13.55 20.52 11.03 18.48 12 12Z M12 12C7.84 14.4 4.12 13.55 3.91 10.78C3.85 9.08 6.87 7.92 12 12Z"/><circle cx="12" cy="12" r="1.6"/>` |
| terminal | `<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 10l3 2-3 2"/><path d="M12.5 15h4.5"/>` |
| folder | `<path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H9l2 2h7.5A2.5 2.5 0 0 1 21 9.5v8a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z"/>` |
| file | `<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/>` |
| image | `<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="1.5"/><path d="M21 16l-5-5-8 8"/>` |
| video | `<rect x="3" y="6" width="13" height="12" rx="2"/><path d="M16 10.5l5-3v9l-5-3"/>` |
| log | `<path d="M6 3h12v18H6z"/><path d="M9 8h6M9 12h6M9 16h4"/>` |
| success | `<circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.5 2.5L16 9.5"/>` |
| warning | `<path d="M12 4L2.8 19.5h18.4z"/><path d="M12 10v4.5"/><circle cx="12" cy="17" r="1" fill="currentColor" stroke="none"/>` |
| danger | `<circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5"/><circle cx="12" cy="16.3" r="1" fill="currentColor" stroke="none"/>` |
| info | `<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5"/><circle cx="12" cy="7.8" r="1" fill="currentColor" stroke="none"/>` |
| pending | `<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>` |
| unknown | `<circle cx="12" cy="12" r="9"/><path d="M9.6 9.6a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .8-1 1.5v.6"/><circle cx="12" cy="17" r="1" fill="currentColor" stroke="none"/>` |
| spinner (busy, draw at 16) | `<circle cx="12" cy="12" r="8" stroke-opacity=".25"/><path d="M20 12a8 8 0 0 0-8-8"/>` |

Sizes: 24 in bars, tabs and leading row icons; 20 in buttons, strips, notes and chevrons; 16 in readiness lines, captions and figure warnings. No icon on section titles, settings rows or state blocks.

---

## 5. Maps: the abstract base and the overlays

Maps are drawn as one inline SVG: the base layer (muted blocks and roads, no labels), then the overlays in the order of spec §5.1. Never write place names on the map. The overlay colours are fixed in every theme; only the base layer changes.

### 5.1 Base colours

| Element | 일반 (light palettes) | 일반 dimmed 0.75 (그래파이트, 네이비 어둡게) | 일반 dimmed 0.85 (흐림) | 위성 (any theme) |
|---|---|---|---|---|
| land | #EEF0EA | #B3B4AF | #CACCC7 | #4B5346 |
| block | #E3E6DE | #AAADA6 | #C1C3BD | #3F4739 |
| park | #DCE8D6 | #A7ACA3 | #BBC2B6 | #46553D |
| water | #D4E2EE | #9FA9B2 | #B4BFC8 | #2E3D48 |
| minor road casing · road | #D3D7CF · #FFFFFF (6 · 4) | #9EA19B · #BFBFBF | #B3B7B0 · #D9D9D9 | none · #7E8178 (3) |
| major road casing · road | #C9CEC5 · #FFFFFF (12 · 9) | #979A94 · #BFBFBF | #ABAEA7 · #D9D9D9 | none · #9C9E95 (6) |

### 5.2 Overlay styles (fixed)

| Feature | SVG |
|---|---|
| range fill + casing | `fill="#1F52B8" fill-opacity=".16"` (".22" over a dimmed base) `stroke="#FFFFFF" stroke-width="5" stroke-linejoin="round"` |
| range edge | the same path again: `fill="none" stroke="#1F52B8" stroke-width="2" stroke-dasharray="6 4" stroke-linejoin="round"` |
| 영역 edge | casing as above, then `fill="none" stroke="#1F52B8" stroke-width="2"` (solid) |
| 경로 centreline | `stroke="#FFFFFF" stroke-width="5"` casing, then `stroke="#1F52B8" stroke-width="2"`, both `fill="none" stroke-linecap="round" stroke-linejoin="round"` |
| 지점 | `<circle r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>` |
| trail casing | `stroke="#FFFFFF" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" fill="none"` under all trail segments |
| trail 정밀 위치 | `stroke="#22704B" stroke-width="4" stroke-linecap="round"` (solid) |
| trail 대략 위치 | `stroke="#8A5A06" stroke-width="4" stroke-dasharray="8 4"` |
| trail 위치 미확인 | `stroke="#5D667B" stroke-width="4" stroke-dasharray="2 4"` |
| observation gap | no line; at each end `<circle r="4" fill="none" stroke="#FFFFFF" stroke-width="4"/>` then `<circle r="4" fill="none" stroke="#B3372C" stroke-width="2"/>` |
| device | `<circle r="11" fill="none" stroke="#121A3D" stroke-width="1"/>` + `<circle r="9" fill="#121A3D" stroke="#FFFFFF" stroke-width="3"/>` |
| device, stale | `<circle r="9" fill="#FFFFFF" stroke="#121A3D" stroke-width="3"/>` |
| device uncertainty (only when ≥ 3 m) | `<circle r="(u in px)" fill="#121A3D" fill-opacity=".08" stroke="#121A3D" stroke-opacity=".4" stroke-width="1"/>` under the marker |
| phone (only when 휴대폰 위치 표시 is on) | `<circle r="18" fill="#1F52B8" fill-opacity=".12"/>` + `<circle r="7" fill="#1F52B8" stroke="#FFFFFF" stroke-width="3"/>` |

### 5.3 Map card SVG (364 × 200) — base 일반 + 수집 구간 overlays

```html
<svg width="364" height="200" viewBox="0 0 364 200" aria-hidden="true" style="display:block">
  <rect width="364" height="200" fill="#EEF0EA"/>
  <rect x="8" y="10" width="96" height="58" rx="3" fill="#E3E6DE"/>
  <rect x="116" y="10" width="64" height="46" rx="3" fill="#E3E6DE"/>
  <rect x="124" y="66" width="56" height="40" rx="3" fill="#E3E6DE"/>
  <rect x="226" y="48" width="44" height="34" rx="3" fill="#E3E6DE"/>
  <rect x="10" y="182" width="120" height="14" rx="3" fill="#E3E6DE"/>
  <path d="M304 118L364 108V200H318Z" fill="#DCE8D6"/>
  <path d="M232 0C246 18 262 28 298 28L364 24V0Z" fill="#D4E2EE"/>
  <path d="M62 0L98 200M0 92L364 106M196 0L176 200" fill="none" stroke="#D3D7CF" stroke-width="6"/>
  <path d="M62 0L98 200M0 92L364 106M196 0L176 200" fill="none" stroke="#FFFFFF" stroke-width="4"/>
  <path d="M0 166L120 126L214 132L300 78L364 26" fill="none" stroke="#C9CEC5" stroke-width="12" stroke-linejoin="round"/>
  <path d="M0 166L120 126L214 132L300 78L364 26" fill="none" stroke="#FFFFFF" stroke-width="9" stroke-linejoin="round"/>
  <!-- range (route band) -->
  <path d="M21.5 170.4L121.4 137.1L216.8 143.2L306.5 86.9L353 48.5L339 31.5L293.5 69.1L211.2 120.8L118.6 114.9L14.5 149.6Z" fill="#1F52B8" fill-opacity=".16" stroke="#FFFFFF" stroke-width="5" stroke-linejoin="round"/>
  <path d="M21.5 170.4L121.4 137.1L216.8 143.2L306.5 86.9L353 48.5L339 31.5L293.5 69.1L211.2 120.8L118.6 114.9L14.5 149.6Z" fill="none" stroke="#1F52B8" stroke-width="2" stroke-dasharray="6 4" stroke-linejoin="round"/>
  <!-- 영역 -->
  <path d="M214 150L268 118L300 150L254 182Z" fill="#1F52B8" fill-opacity=".16" stroke="#FFFFFF" stroke-width="5" stroke-linejoin="round"/>
  <path d="M214 150L268 118L300 150L254 182Z" fill="none" stroke="#1F52B8" stroke-width="2" stroke-linejoin="round"/>
  <!-- 경로 -->
  <path d="M18 160L120 126L214 132L300 78L346 40" fill="none" stroke="#FFFFFF" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M18 160L120 126L214 132L300 78L346 40" fill="none" stroke="#1F52B8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- 지점 ×4 -->
  <circle cx="18" cy="160" r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>
  <circle cx="120" cy="126" r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>
  <circle cx="214" cy="132" r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>
  <circle cx="346" cy="40" r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>
  <!-- device 350 m away -->
  <circle cx="74" cy="44" r="11" fill="none" stroke="#121A3D" stroke-width="1"/>
  <circle cx="74" cy="44" r="9" fill="#121A3D" stroke="#FFFFFF" stroke-width="3"/>
</svg>
```

**수집 중, inside** (card 240 tall: set `height="240" viewBox="0 -20 364 240"` and make the first land `<rect>` `y="-20" height="240"`; same base and zone): replace the device with the trail and the device on the route:

```html
  <path d="M18 160L120 126L190 130.5" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M18 160L96 134" fill="none" stroke="#22704B" stroke-width="4" stroke-linecap="round"/>
  <path d="M96 134L120 126L150 128" fill="none" stroke="#8A5A06" stroke-width="4" stroke-dasharray="8 4" stroke-linejoin="round"/>
  <path d="M150 128L190 130.5" fill="none" stroke="#22704B" stroke-width="4" stroke-linecap="round"/>
  <circle cx="190" cy="130.5" r="11" fill="none" stroke="#121A3D" stroke-width="1"/>
  <circle cx="190" cy="130.5" r="9" fill="#121A3D" stroke="#FFFFFF" stroke-width="3"/>
```

**수집 중, outside** (badge "구역 밖 · 35 m"): extend the trail `M190 130.5L214 132L250 126L284 118` (casing + 정밀 solid) and put the device at (284, 118).

**수집 결과** (trail over the bound zone, no device, one observation gap):

```html
  <path d="M18 160L120 126L214 132L250 109.4M262 101.9L300 78L346 40" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M18 160L96 134" fill="none" stroke="#22704B" stroke-width="4" stroke-linecap="round"/>
  <path d="M96 134L120 126L150 128" fill="none" stroke="#8A5A06" stroke-width="4" stroke-dasharray="8 4" stroke-linejoin="round"/>
  <path d="M150 128L214 132L250 109.4" fill="none" stroke="#22704B" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M300 78L346 40" fill="none" stroke="#22704B" stroke-width="4" stroke-linecap="round"/>
  <path d="M262 101.9L300 78" fill="none" stroke="#5D667B" stroke-width="4" stroke-dasharray="2 4"/>
  <circle cx="250" cy="109.4" r="4" fill="none" stroke="#FFFFFF" stroke-width="4"/><circle cx="250" cy="109.4" r="4" fill="none" stroke="#B3372C" stroke-width="2"/>
  <circle cx="262" cy="101.9" r="4" fill="none" stroke="#FFFFFF" stroke-width="4"/><circle cx="262" cy="101.9" r="4" fill="none" stroke="#B3372C" stroke-width="2"/>
```
(The gap ring pair marks where the phone could not observe; the dotted segment after it is 위치 미확인.)

### 5.4 Full-screen map SVG (stage 412 × 790 under the top bar) — 수집 중

```html
<svg width="412" height="790" viewBox="0 0 412 790" aria-hidden="true" style="display:block">
  <rect width="412" height="790" fill="#EEF0EA"/>
  <rect x="12" y="20" width="120" height="90" rx="3" fill="#E3E6DE"/>
  <rect x="150" y="20" width="110" height="130" rx="3" fill="#E3E6DE"/>
  <rect x="16" y="360" width="150" height="120" rx="3" fill="#E3E6DE"/>
  <rect x="180" y="250" width="100" height="140" rx="3" fill="#E3E6DE"/>
  <rect x="20" y="700" width="200" height="80" rx="3" fill="#E3E6DE"/>
  <path d="M0 150L100 180L90 330L0 320Z" fill="#DCE8D6"/>
  <path d="M300 0C320 60 340 90 412 96V0Z" fill="#D4E2EE"/>
  <path d="M0 300L412 340M80 0L120 790M0 720L412 680M300 0L250 790" fill="none" stroke="#D3D7CF" stroke-width="6"/>
  <path d="M0 300L412 340M80 0L120 790M0 720L412 680M300 0L250 790" fill="none" stroke="#FFFFFF" stroke-width="4"/>
  <path d="M0 662L140 560L250 548L330 420L372 250L392 150L404 0" fill="none" stroke="#C9CEC5" stroke-width="12" stroke-linejoin="round"/>
  <path d="M0 662L140 560L250 548L330 420L372 250L392 150L404 0" fill="none" stroke="#FFFFFF" stroke-width="9" stroke-linejoin="round"/>
  <path d="M40.6 654.6L146.7 577.4L260.6 564.9L346.8 427.1L389.6 253.9L409.7 153.5L374.3 146.5L354.4 246.1L313.2 412.9L239.4 531.1L133.3 542.6L19.4 625.4Z" fill="#1F52B8" fill-opacity=".16" stroke="#FFFFFF" stroke-width="5" stroke-linejoin="round"/>
  <path d="M40.6 654.6L146.7 577.4L260.6 564.9L346.8 427.1L389.6 253.9L409.7 153.5L374.3 146.5L354.4 246.1L313.2 412.9L239.4 531.1L133.3 542.6L19.4 625.4Z" fill="none" stroke="#1F52B8" stroke-width="2" stroke-dasharray="6 4" stroke-linejoin="round"/>
  <path d="M250 600L330 560L368 640L290 690Z" fill="#1F52B8" fill-opacity=".16" stroke="#FFFFFF" stroke-width="5" stroke-linejoin="round"/>
  <path d="M250 600L330 560L368 640L290 690Z" fill="none" stroke="#1F52B8" stroke-width="2" stroke-linejoin="round"/>
  <path d="M30 640L140 560L250 548L330 420L372 250L392 150" fill="none" stroke="#FFFFFF" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M30 640L140 560L250 548L330 420L372 250L392 150" fill="none" stroke="#1F52B8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="30" cy="640" r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>
  <circle cx="140" cy="560" r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>
  <circle cx="250" cy="548" r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>
  <circle cx="392" cy="150" r="5" fill="#1F52B8" stroke="#FFFFFF" stroke-width="2"/>
  <path d="M30 640L140 560L250 548L300 468" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M30 640L140 560" fill="none" stroke="#22704B" stroke-width="4" stroke-linecap="round"/>
  <path d="M140 560L200 553.5" fill="none" stroke="#8A5A06" stroke-width="4" stroke-dasharray="8 4"/>
  <path d="M200 553.5L250 548L300 468" fill="none" stroke="#22704B" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="300" cy="468" r="11" fill="none" stroke="#121A3D" stroke-width="1"/>
  <circle cx="300" cy="468" r="9" fill="#121A3D" stroke="#FFFFFF" stroke-width="3"/>
</svg>
```
Mode 수집 구역 (from the ticket): drop the trail and put the device at (96, 420) (350 m away). Mode 장치 위치: no zone layers, device centred. Landscape (882 × 388 stage): draw the same base at `viewBox="0 120 882 388"` scaled to fill, the panel on the end side.

### 5.5 Base map in dark palettes and 위성

Swap only the base fills and strokes by §5.1 (dimmed for 그래파이트 / 네이비 (어둡게) at 0.75, 흐림 at 0.85; 위성 drops road casings and narrows roads to 3 / 6). The overlays, the white casings and the marker colours never change. The card border uses the palette `border` (고대비 `borderStrong`), the on-map badge and attribution plates use the palette `mapPlate`.

---

## 6. Loader, loading scene, error scene and splash

The geon-loader per LOADER_SOURCE at S = 88: P = 70, H = 106, top = −12. Add these rules inside the board's `<helmet><style>` (keyframes and the `url()` mask only):

```css
@keyframes gxKf{0%{-webkit-mask-position:0 88px;mask-position:0 88px;opacity:1}72%{-webkit-mask-position:-210px -12px;mask-position:-210px -12px;opacity:1}88%{-webkit-mask-position:-210px -12px;mask-position:-210px -12px;opacity:1}100%{-webkit-mask-position:-210px -12px;mask-position:-210px -12px;opacity:0}}
@keyframes gxKg{0%{-webkit-mask-position:35px 81px;mask-position:35px 81px;opacity:.32}66%{-webkit-mask-position:175px -24px;mask-position:175px -24px;opacity:.32}100%{-webkit-mask-position:175px -24px;mask-position:175px -24px;opacity:0}}
@keyframes gxKs{0%,74%{-webkit-mask-position:100% 0;mask-position:100% 0}96%,100%{-webkit-mask-position:0 0;mask-position:0 0}}
.gx-wave{-webkit-mask-image:url(/_blob/6895d0adcc2dccb74c781c4d839c0cee);mask-image:url(/_blob/6895d0adcc2dccb74c781c4d839c0cee);-webkit-mask-repeat:repeat-x;mask-repeat:repeat-x;-webkit-mask-size:70px 106px;mask-size:70px 106px}
```

Loader markup (back → front: base, second wave, main wave, shine):

```html
<div role="img" aria-label="불러오는 중" style="position:relative;width:88px;height:88px;flex:none">
  <img src="/_blob/c822272385435698f933577c972e1e1d" alt="" style="position:absolute;inset:0;width:88px;height:88px;object-fit:contain;filter:grayscale(1) contrast(0) brightness(1.74);opacity:1">
  <img class="gx-wave" src="/_blob/c822272385435698f933577c972e1e1d" alt="" style="position:absolute;inset:0;width:88px;height:88px;object-fit:contain;opacity:.32;animation:gxKg 2.4s cubic-bezier(.45,.05,.35,1) infinite">
  <img class="gx-wave" src="/_blob/c822272385435698f933577c972e1e1d" alt="" style="position:absolute;inset:0;width:88px;height:88px;object-fit:contain;animation:gxKf 2.4s cubic-bezier(.45,.05,.35,1) infinite">
  <img src="/_blob/c822272385435698f933577c972e1e1d" alt="" style="position:absolute;inset:0;width:88px;height:88px;object-fit:contain;filter:brightness(1.9) saturate(.6);opacity:.7;-webkit-mask-image:linear-gradient(62deg,transparent 42%,#000 50%,transparent 58%);mask-image:linear-gradient(62deg,transparent 42%,#000 50%,transparent 58%);-webkit-mask-size:300% 100%;mask-size:300% 100%;-webkit-mask-repeat:no-repeat;mask-repeat:no-repeat;animation:gxKs 2.4s ease-in-out infinite">
</div>
```
Dark palettes: the base `<img>` uses `filter:grayscale(1) contrast(0) brightness(.9);opacity:.28` (흐림 `brightness(.96)`). Reduced motion: a single `<img src="/_blob/c822272385435698f933577c972e1e1d" alt="" style="width:88px;height:88px;object-fit:contain">` (full colour, no layers, no pulse).

**Loading scene (P10):**

```html
<div style="width:412px;height:882px;position:relative;overflow:hidden;box-sizing:border-box;font-family:'Pretendard GX',sans-serif;background:#FFFFFF;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:36px 24px 48px">
  <!-- loader 88 -->
  <p style="margin:20px 0 0;font-size:18px;line-height:26px;font-weight:600;letter-spacing:-0.27px;color:#151B31">GEO-X</p>
  <p style="margin:8px 0 0;font-size:15px;line-height:24px;color:#4B5570">장치 정보를 불러오는 중입니다.</p>
</div>
```

**Error scene (P10):**

```html
<div style="width:412px;height:882px;position:relative;overflow:hidden;box-sizing:border-box;font-family:'Pretendard GX',sans-serif;background:#FFFFFF;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:36px 24px 48px">
  <img src="/_blob/c822272385435698f933577c972e1e1d" alt="" style="width:56px;height:56px;object-fit:contain;filter:grayscale(1);opacity:.45">
  <h1 style="margin:20px 0 0;font-size:18px;line-height:26px;font-weight:600;letter-spacing:-0.27px;color:#151B31">장치 정보를 불러오지 못했습니다</h1>
  <p style="margin:8px 0 0;max-width:360px;font-size:15px;line-height:24px;color:#4B5570">저장된 장치 정보를 읽지 못했습니다. 다시 시도하세요.</p>
  <button style="font-family:inherit;border:0;margin:24px 0 0;cursor:pointer;width:240px;min-height:52px;border-radius:10px;background:#1F52B8;color:#FFFFFF;font-size:16px;line-height:20px;font-weight:600">다시 시도</button>
</div>
```

**Splash frames** (design-system board only): a frame filled with the palette `surface` (#FFFFFF for the three light palettes, #1E1F21, #1B1F28, #2F3033) and the symbol `<img src="/_blob/c822272385435698f933577c972e1e1d">` at 96 px centred; nothing else. The GEO& wordmark (`/_blob/5c79e230e87a803fee58158ad76436e5`) is not used in the app.

---

## 7. Before handing in a board

- Root size and `$preview` match the board type (§2); `<title>` is the board's 제목; `lang="ko"`.
- The 36 px status area is the `topBar` colour; the 48 px navigation inset is the colour of what sits above it; no fake status icons, no Android dialogs.
- Bar visibility follows spec §1.2 (focused flows have none); the owning tab is lit.
- Exactly one primary (filled) action per screen, or none; dialogs use the copy deck (spec §8) word for word.
- Every colour is a §1 role of the board's theme; every number goes through spec §3.13; names wrap and never end in "…".
- Icons are from §4 at 24 / 20 / 16; buttons are rectangles with radius 10; pills appear only as badges, the segmented control and counts.
- Text contrast holds (the §1 pairs are pre-checked; do not put `textSubtle` on a tone-soft fill or any text on a map tile without its plate).
- No emoji, no "!", no gradients except the loader's shine mask, no left-border accents, no eyebrow labels, no step numbers, no decorative illustration, no invented data beyond spec §11.0.
