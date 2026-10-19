# GEO-X — R3 final specification (buildable)

Round 3 of the R3 debate · 2026-10-19 · lead designer (opus) · supersedes R1-opus.md. Inputs: R1-opus, R2-astra (and `astra-scratch/`), R3-lead-decisions, ARTBOARD_RULES, ASSETS, LOADER_SOURCE, web-tokens.css (GEO& Work 3f85615f), INVENTORY (138 items) and the code under `app/src/main/java/com/example/jetsoncontroller/`. No repository file was edited.

This file is the single source for the drawings (with `KIT.md`) and for the implementation. Where it and an older document disagree, this file wins. Source paths are relative to `app/src/main/java/com/example/jetsoncontroller/`.

**Drawing frame.** 412 × 882 dp portrait (Galaxy S22 Ultra, density 560, 1440 × 3088 px), status bar 36 dp, Samsung three-button navigation 48 dp, font scale 1.0 in drawings (the owner runs 1.1; acceptance runs 1.0 / 1.1 / 1.3 / 1.5 / 2.0).

---

## 0. Changes from R1 (one line each)

**Navigation and IA**
1. The bottom bar (or rail) now stays on tab-owned browsing and detail screens with the owning tab lit; it hides only in focused flows (장치 등록, 수집 중, full-screen map, camera/QR/video, keyboard editors).
2. Re-tapping the current tab pops to its root and switching tabs restores that tab's stack — now consistent with persistent chrome.
3. 작업 stays the start screen, but the rationale is corrected: work-versus-monitoring frequency is an assumption, so the device strip now also surfaces sensor exceptions at zero taps.
4. 전송 shows the current device's transfers only; another device appears only as an attention row that switches device; there is no fleet-wide monitor.
5. Device switching is transactional: drafts are kept per device, open confirmations close, the new device reads "확인 중" until its state arrives, and pushed screens keep their own target.
6. A shared ongoing-run entry (the info status strip "수집 중") appears on every root and detail screen; 작업 uses Focus block 5 instead.
7. Next-action precedence now follows `FieldStage.kt:116` exactly — pending start, unconfirmed run and connected-but-unknown come before offline — and separates the operation state from the transport remedy.
8. "연결 방법" is offered immediately beside "다시 연결" (R1 waited 15 s).
9. The developer-mode promise is "daily screens do not change"; developer-only controls live in developer destinations, and only two daily screens get one link row in developer mode (server trash, no upload target).
10. Diagnosis corrections adopted: the sensor route is reachable through temperature alerts; "모두 읽음" is redundant rather than never shown; the `HUMAN_CHANGED` example was wrong (Korean copy exists, unknown codes still leak); launch asks only for missing permissions; not every start door lands in project picking.

**Preparation and start**
11. A persisted **PreparationDraft** per device holds ticket rowId + revision, server profile, device, pipeline, project/section or "create from ticket", geometry revision/hash and the create request ids; it survives the task choice, device switches and process death.
12. The task is chosen before any check, and the task sheet keeps the ticket (fixes `JetsonApp.kt:1642`).
13. The preparation screen shows passive readiness from telemetry only; R1's automatic preflight is withdrawn because preflight writes device state (`run_context.py:544`).
14. "수집 시작" runs the preflight and opens one merged confirmation sheet (S8) with check results, ticket and zone line; failures are listed with remedies and the confirm stays disabled.
15. Project and section creation happens only inside the start transaction, idempotent by a persisted `clientRequestId` (today a new UUID per tap, `SurveyRunViewModel.kt:469-495`).
16. The manual path restores the last valid project and section per device, so a repeat collection is 3 taps.

**Run, result and transfer**
17. A user-confirmed stop opens the result automatically (stop → confirm = 2 taps); a remote end stays in place.
18. `run_result/{runId}` loads that run by id, not `latestRun`; legacy entries without a canonical run id get `history_result/{historyId}`; logs are reachable from both (both routes landed in 560c6f9).
19. Start, stop, report, retry, cancel, clean-up, undo, power, forget and Wi-Fi apply bind to immutable target tuples and revalidate at execution.
20. "완료 보고" is also offered on the linked, verified transfer detail.
21. A disabled "서버로 보내기" always shows a remedy action ("장치 Wi-Fi 설정" or "연결 방법").
22. A disconnected device no longer blocks server-only work (viewing receipts, reporting verified runs).
23. An arbitrary file upload merges selection and review in the browser's action area: 파일 보내기 → folder → 전송 시작 (3 taps).
24. One file-browser implementation keeps a separate location per entry point (R1 merged the browsing state; `JetsonApp.kt:452` keeps them apart on purpose).
25. Action rows have two separate targets: the content opens the detail, the trailing button acts.
26. Transfer groups match verification, transport state and source identity together; a receipt counts only when job, target, session and source match.

**Map and zone**
27. Zone monitoring is phase-split: Phase 1 is phone-side and foreground-only with recorded observation gaps; Phase 2 is device-side; R1's background notification and "구역 이탈" alert setting are removed from Phase 1.
28. The inside/outside function uses signed distance with holes and MultiPolygons, u = max(category floor, reported accuracy), sample-time freshness, duplicate suppression, a "위치 확인 중" start, a "경계 근처" state and two-sample hysteresis.
29. The stated warning latency is 1–2 s at the 1 s status poll (R1 claimed 3 fixes in ≥ 3 s, which is ≥ 10 s at 5 s polling).
30. `bufferM` is per side; the shown width is 2 × bufferM.
31. The geometry revision and hash are bound to the run at start and cached with the tickets for offline use.
32. A local map style always exists (no key → background + overlays); sources are separate; layer order and 5 dp casings are specified; trail quality also differs by line pattern.
33. The full map gains zoom buttons, a visible panel expand/collapse button, a run action panel with "수집 종료" and a side panel in compact landscape; only one live map runs at a time.

**Layout and rulebook**
34. Window classes come from the current window or pane; `TABLET_UI_SCALE` (`Theme.kt:24-31`) is removed; the rail shows at ≥ 600 dp in any orientation and scrolls.
35. Two panes need measured minimum widths after rail, gutters and cutouts; column spans are stated.
36. The inset contract is rewritten as astra's six rules, including the system-bar icon colour per bar.
37. A two-line row is 12 + 24 + 4 + 20 + 12 = 72 dp; `GeoSection` alone owns the 24 dp section gap.
38. Figures align with the gutter; the contained variant gets 16 dp padding once.
39. Numeric columns reserve a trailing value slot and a separate unit slot.
40. Deterministic formatters are specified with examples (decimal sizes, binary memory, floor progress, freshness once a minute).
41. Identity names wrap without limit; emergency grapheme wrapping applies only to a token wider than the line; `WordBreak.Phrase` is verified per device, not assumed.
42. One measured stacking rule now covers screen buttons, dialog actions and sheet footers (R1 had different font-scale cutoffs).
43. The root top bar moves the device switcher to a second row when it does not fit.
44. Sticky actions are decided from the remaining viewport after IME and measured chrome, and the action area never shrinks while visible.
45. Sheets and dialogs scroll and fall back to full screen when the body would be under 160 dp.
46. The readiness line, status strip and recovery text are `body` size (R1 used caption for readiness).
47. Accessibility gains explicit alternatives (a freshness "다시 확인" and an accessibility action for pull-to-refresh, map zoom buttons, copy buttons), focus return to the opener and recommended snackbar timeouts.
48. The log viewer follows the end only while the user is at the end and offers "최신 로그".
49. Icons are one GeoIcons stroke set built as `ImageVector`s (1.75 dp, round caps), matching the drawings; R1 wrongly said stroke icons needed a new library and called `material-icons-extended` "Material Symbols".

**Colour**
50. Text fields and picker fields carry a 1 dp `borderStrong` in every palette.
51. Switch tracks use astra's corrected values, and the switch thumbs, checkboxes, radios, selected rows and map-control focus are specified.
52. 네이비 (밝게) is a full theme: navy root and pushed top bars including the status-bar area, bottom bar or rail, and the navigation inset, with `navFocus` #8FB0F0, `attention` #F08F86 and light system-bar icons.
53. Warning figures stay `text` with a warning icon and label.
54. The map attribution plate is opaque; the media neutral is #BDBFC4; on-dark badges are opaque #1E1F21.
55. `dangerPressed`, chrome tones and `navFocus` are added for every palette.
56. The contrast script was re-run over the final table: 378 + 13 checks, 0 failures (§4.4).
57. The theme picker gains a live preview, separately labelled radio groups, growing descriptions and a "saved for the other mode" message.

**Startup**
58. `setSplashScreenTheme` exists from API 31 (R1 said 33): three adaptive splash themes keyed by the chosen dark palette, a fixed persistence order, a manifest default matching first-install defaults, and an honest first-upgrade limit.
59. The restore state is explicit (loading / restored / failed); a failure is never "zero devices".
60. Pipeline adoption and device reconciliation move from the 장치 root into an app-scope coordinator.

**Features whose homes were incomplete**
61. 서버 연결 is an operator page (status, environment, employee/project, "연결 확인", "다시 로그인"); URL and profile editing live in 개발자 도구; credentials are never displayed.
62. Server video preview (playback and retry), job paging ("이전 수신 결과 더 불러오기"), the trash's shown-versus-total line and unsupported restore/purge states are kept.
63. Mobile RTK relay gets an operator home ("보정 정보 중계") on preparation and on the run.
64. Fan control keeps 자동, the supported minimum, the manual percentage and the loading, error and unavailable states.
65. Wi-Fi Direct keeps separate discovery, connection, authentication/retry and cancel states.
66. The file-type filter becomes a picker with five types including 로그 (R1's four-segment control dropped 로그).
67. 알림 기준 keeps today's thresholds and toggles (저장 공간 50–99 %, 장치 온도 40–110 °C, 작업 and 전송 start/end) on one page.
68. The drawing frame is 412 × 882 dp with a 36 dp status bar and a 48 dp navigation bar.

---

## 1. IA, shell and navigation

### 1.1 Four destinations

| Tab | Owner's job | Root shows (top → bottom) |
|---|---|---|
| **작업** (start) | give devices work; watch runs | device strip · Focus block (0–1, §1.6) · 수집 구간 for this 호기 (cached, "마지막 동기화 …" when stale) · 최근 수집 · 장치 작업 |
| **장치** | manage devices; monitor hardware | strip (0–1) · 현재 장치 with figures · 확인할 일 · 다른 장치 · 주변에서 찾은 장치 |
| **전송** | watch device → server uploads (current device) | strip (0–1) · 보내는 중 · 확인이 필요한 전송 · 보낼 수집 · 대기 중 · 수신 확인 중 · 서버 수신 확인 · other devices needing attention · 서버 · sticky "파일 보내기" |
| **설정** | preferences, server connection, the developer door | 화면 · 알림 · 서버 · 정보 · 고급 (developer mode) |

Four tabs are a deliberate choice for persistent access to 설정 (six themes, alert thresholds, the server connection, the developer door), at the cost of one quarter of the bar. 작업 is the start screen because a field day is a sequence of 수집 구간 and the device strip keeps health and sensor exceptions at zero taps; this is a product assumption, so the device strip and the 장치 tab dot must make monitoring problems visible without visiting 장치. Five tabs (알림 or 지도) and a device-scoped workspace stay rejected for R1's reasons: alerts are a by-product reached from the bell, and the map always belongs to a ticket, run, result or device.

### 1.2 Shell and navigation rules [`GeoAppShell`, `ui/navigation/*`]

1. **The bar** (bottom bar or rail) is shown on the four roots and on every tab-owned browsing or detail screen, with the owning tab lit.
2. **Focused flows hide the bar:** 장치 등록 (`first_run`, `scan` and its sheets), 수집 중 (`run`), the full-screen map, media (camera, QR, image and video preview) and editors — keyboard-first forms whose main content is text entry (`dev/terminal`, `dev/upload-targets/edit`, `dev/pipeline/new`, `dev/pipeline/{id}/config`, `dev/pipeline/{id}/policy`, `dev/server-profiles/edit`). Back leaves a focused flow to the screen that opened it. The ticket screen keeps the bar.
3. **Stacks:** each tab is a nested graph (`navigation()` with `saveState`/`restoreState`). Re-tapping the current tab pops to its root; tapping another tab restores that tab's stack; system back on the 장치, 전송 or 설정 root goes to the 작업 root; back on the 작업 root leaves the app.
4. **Ownership:** a pushed screen belongs to the tab it was pushed on. Cross-links (수집 결과 → 전송 상세, 장치 상세 → 장치 파일, an alert → its destination) push onto the current tab's stack and keep that tab lit; nothing switches tabs by itself.
5. **One current device.** The switcher is in the top bar of the 작업 and 전송 roots (button "● GEO-X-1 ⌄" → S3 "장치 선택"); the 장치 root lists all devices. A switch is transactional: the PreparationDraft and other drafts are kept per device; open sheets and dialogs that target the previous device close with the snackbar "장치를 바꿔서 열려 있던 확인 창을 닫았습니다."; the new device shows "확인 중" until its state arrives; pushed screens keep their own device target and show it in their meta.
6. **Bell** on the 작업, 장치 and 전송 roots only; 알림 is pushed on the current tab.
7. **Zero devices** → the 장치 등록 scene (no bar; its gear opens 설정). After the first registration the app opens 작업 with the bar.
8. **Tab dots** (8 dp `attention`, no numbers): 장치 when any device has an item in 확인할 일; 전송 when the current or any cached device has 확인이 필요한 전송.
9. **No automatic navigation** on background changes. Allowed: the end of a flow the operator started — pairing done → 작업; start accepted → `run`; a user-confirmed stop → `run_result`; "전송 시작" → `transfer/{jobId}`; 직접 연결 established → back to the opener.
10. **Stack hygiene:** an accepted start replaces `ticket`/`prepare` with `run`; a user-confirmed stop replaces `run` with `run_result`; `launchSingleTop` everywhere; back from `run_result` returns to its opener.
11. **Window classes, panes and insets** per §3.1–§3.2.

### 1.3 Route map (old → new)

| Old route / surface | New route | Tab | Note |
|---|---|---|---|
| `tasks` | `work` | 작업 | start destination |
| `ticket_detail/{rowId}` | `ticket/{rowId}` | 작업 | the preparation screen (P3) |
| `survey_run/{pipelineId}` | `prepare?pipeline={id}` | 작업 | manual path "구간 직접 고르기" |
| `pipelines` (operator use) | sheet "수집 작업 선택" | 작업 | only when the task is ambiguous; keeps the draft |
| `pipelines`, `pipeline_detail/{id}` (developer use) | `dev/pipelines`, `dev/pipeline/{id}` | 설정 | |
| `task_detail/{pipelineId}` | `task/{pipelineId}` | 작업 | |
| `active_run` | `run` | 작업 | focused |
| `run_result` | `run_result/{runId}` (landed in 560c6f9) | 작업 | loads that run; back returns to the opener |
| — (landed in 560c6f9) | `history_result/{historyId}` | 작업 | entries without a canonical run id; `historyId` = `TaskRun.id` (pipelineId/logId) |
| `task_history` | `history` | 작업 | |
| `devices` | `devices` | 장치 | |
| `device_detail` | `device/{deviceId}` | 장치 | also non-selected devices (snapshot) |
| `connection_hub`, `devices_ble` | removed | — | features re-homed (§7.2 #112, #117) |
| `onboarding` | `first_run` | — | zero devices only |
| `qr_scanner`, `pairing` | `scan` + sheets | — | focused |
| `wifi_direct` | `device/{id}/direct` | 장치 | returns to the opener |
| `network_settings` | `device/{id}/wifi` | 장치 | |
| `storage?rootId&path` | `files?mode=browse&root&path` | current | one implementation, location kept per entry |
| `data_hub` | `files?mode=send` | 전송 | selection + review |
| `local_trash` | `device/{id}/trash` | 장치 | |
| `sensors` | redirect → `device/{id}` (센서 section) | 장치 | |
| `camera_preview` | `camera` | current | focused |
| `gnss_map` | `map?mode=zone\|run\|route\|location` | current | focused |
| `transfers` | `transfers` | 전송 | |
| `upload_progress` | `transfer/{jobId}` | current | |
| `upload_queue` | `transfers/log` | 전송 | |
| `upload_confirm/{rootId}?path&runId` | `send?root&path&runId` | current | |
| `server_storage` | `server` | 전송 | profiles → `settings/server` and `dev/server-profiles` |
| `server_proxy_storage` | `dev/server-proxy` | 설정 | |
| `upload_servers` | `dev/upload-targets` | 설정 | |
| `settings` + theme/about/help dialogs | `settings`, `settings/theme`, `settings/about`, `settings/help` | 설정 | pages |
| `alert_settings` | `settings/alerts` | 설정 | |
| — (new) | `settings/server` | 설정 | operator connection page |
| `developer` | `dev` | 설정 | |
| `admin_tools` | removed | — | parts in `dev/*` and 장치 상세 |
| `connection_diagnostics` | `dev/diagnostics` | 설정 | |
| `pipeline_editor`, `pipeline_picker`, `pipeline_logs/{id}`, `pipeline_config/{id}` | `dev/pipeline/new`, `dev/picker`, `dev/pipeline/{id}/logs`, `dev/pipeline/{id}/config` | 설정 | editors focused |
| — (new) | `dev/terminal`, `dev/fan`, `dev/network`, `dev/sensor-test`, `dev/pipeline/{id}/policy`, `dev/server-profiles`, `dev/server-trash` | 설정 | split out of the hub, admin tools, the survey policy pane and the server screen |
| `alerts` | `alerts` | current | |

### 1.4 Where every inventory group lives

| Inventory group | New home |
|---|---|
| 장치 (8) | 장치 tab (`devices`, `device/{id}`), the 장치 등록 scene, S3 연결 방법, S1b power dialogs, S6 menu |
| 작업 (25) | 작업 tab (`work`, `ticket`, `prepare`, `task`, `run`, `run_result`, `history`, `history_result`), S3/S5/S6/S7/S8 |
| 전송 (32) | 전송 tab (`transfers`, `transfer`, `transfers/log`, `send`, `server`, `files?mode=send`); device files and trash on 장치; proxy, profiles and server-trash purge in 개발자 도구 |
| 설정 (21) | app preferences on 설정 (`settings/*`); Wi-Fi, sensors, camera and map on 장치 (`device/{id}/wifi`, `device/{id}`, `camera`, `map`) |
| 개발자 도구 (24) | `dev/*` under 설정 › 고급 › 개발자 도구; the legacy dashboard retires with its features re-homed |
| 등록/연결 (19) | the 장치 등록 scene, `scan` + sheets, 연결 방법, `device/{id}/direct`, just-in-time permission blocks |
| 알림 (3) | `alerts` from the bells |
| 공통 (6) | loading and error scenes, just-in-time permissions, automatic behaviour shown as states, the status strip |

Orphans with a home: device removal → 장치 상세 › 이 휴대폰 › 등록 해제; IMU and the full sensor list → 장치 상세 › 센서; dashboard health (`ui/dashboard/DashboardHealth.kt`: stale status, unavailable metrics, high temperature, storage pressure, failed task, failed upload) → 장치 › 확인할 일 with per-item 숨기기; "같은 장치에서 실행이 N개로 보입니다" → Focus 5b; fan control → `dev/fan`; discovered unregistered units → 장치 › 주변에서 찾은 장치; mobile RTK relay → 준비 상태 and 수집 중 rows; undo → S7 with a visible action.

### 1.5 Identity, persistence and invariants [`ui/model/*`, `data/*`]

**PreparationDraft** (one per device; on disk; extends the `TicketPreparationStore` the working tree is adding):

| Field | Meaning |
|---|---|
| `deviceId`, `serverProfileId` | the target; a different device has its own draft |
| `ticketRowId`, `ticketRevision` | null on the manual path |
| `pipelineId` | chosen or remembered task; null until chosen |
| `project` | existing id + revision, or `CreateFromTicket(label)`, or `CreateNew(label)` |
| `section` | existing id + revision, or `CreateFromTicket(title)`, or `CreateNew(label)` |
| `projectRequestId`, `sectionRequestId` | `clientRequestId`s generated once when a creation is decided and reused on every retry (the backend replays by id, `survey_context.py:189-306`; same label required) |
| `geometryRevision`, `geometryHash` | from the cached ticket |
| `updatedAt` | — |

The draft survives the task choice, device switches and process death; opening another ticket on the same device replaces it; an accepted start clears it. The preparation screen never creates anything on the device.

**Start transaction** (on S8 "수집 시작"): (1) revalidate — same device, online with control, no active or pending run, ticket revision unchanged (else S8 shows "GEO& Work에서 이 구간이 바뀌었습니다. 다시 확인하세요." and refreshes), preflight result ≤ 2 min old and passed for this pipeline and context; (2) create the project/section if the draft says so, with the persisted request ids; (3) send the contextual start with its persisted `clientRequestId` (`PendingContextualStart`, `data/survey/SurveySelectionStore.kt`), binding device, pipeline, project, section, preflight id, ticket row/revision and geometry revision/hash; (4) queue the GEO& Work start report in the existing outbox (`data/server/TicketReportStore.kt`); (5) open `run` in "시작 확인 중".

**Immutable action targets** — every command carries its tuple and revalidates it when it executes; on a mismatch it does nothing and explains:

| Command | Target tuple | Revalidated against |
|---|---|---|
| 수집 시작 | device, pipeline, project/section (+ request ids), preflight id, ticket row/revision, geometry revision | device state, ticket revision, preflight age |
| 수집 종료 | device, runId | the device's active run id |
| 완료 보고 | profile, ticket row/revision, runId, receipt id | receipt verified and matching; ticket not changed by someone else |
| 다시 보내기 / 전송 취소 | device, jobId, source path, target id | job state on the device |
| 장치 원본 정리 / 복원 | device, jobId, source path, receipt id | the server receipt re-checked at execution ("서버와 다시 대조한 뒤") |
| 실행 취소 (undo) | trash item id, original path | item still restorable |
| 재부팅 / 전원 종료 | device | no active run |
| 등록 해제 | device | — |
| 장치 Wi-Fi 적용 | device, SSID | device still connected |

**Pending commands and uncertainty:** a pending start or an unconfirmed run is restored after process death and shown as such; reconnecting never clears it and never makes a new start available until the device answers for that request (`FieldStage.kt:116-126` order, §1.6).

**Run identity:** `run_result/{runId}` loads that run by id, independent of `surveyRunState.latestRun`. History rows without a canonical run id (`canonicalHistoryRunId`, `ui/field/GeoRunDashboard.kt:305-309`: needs a context snapshot, a FINAL manifest and a manifest run id) open `history_result/{historyId}` (the entry id `TaskRun.id`), which shows the entry's facts, its 64 KB log and, when a manifest exists, its folder.

**Offline cache:** tickets (with geometry, revision and hash) are cached on disk per server profile and device scope (today an in-memory map, `ServerTicketViewModel.kt:71`); when the server is unreachable lists show "마지막 동기화 10월 19일 16:36" and stay usable. A disconnected device never blocks server-only work: viewing receipts, opening server results, and reporting an already verified run while the server is reachable.

**State correctness:** sensor words come from sample freshness and device availability, not from a fresh status response alone; invalid metrics are "미확인" in presentation **and** are never written to snapshots (existing snapshots migrate invalid values to null), while a legitimate 0 stays 0; a transfer is "서버 수신 확인" only when the verification matches and the receipt's job, target, remote session and source match the job.

### 1.6 Next-action precedence [`ui/field/FieldStage.kt` extended → `NextAction`]

One pure function computes, per device, an **operation state** (first match wins, in `FieldStage`'s safety order) and a **transport remedy**; the Focus block on 작업 and 장치, the device strip and every readiness line render from it.

| # | Operation state (condition) | Focus title / body | Primary when the device is reachable | When it is not reachable |
|---|---|---|---|---|
| 0 | no registered device | the 장치 등록 scene replaces the tabs | QR 코드 스캔 | — |
| 1 | start requested, result unknown (`pendingStartRequestId`) | "GEO-X-1 수집 시작 결과를 확인해야 합니다" / "같은 요청의 결과를 확인할 때까지 새 수집을 시작할 수 없습니다." | 시작 결과 확인 | transport remedy |
| 2 | run unconfirmed (`unconfirmedRunId`, or connected but run state not confirmed) | "GEO-X-1 수집 상태를 확인하지 못했습니다" / "마지막 확인 16:36:02 · 상태를 확인할 때까지 새 수집을 시작할 수 없습니다." | 상태 다시 확인 | transport remedy |
| 3 | device not reachable, nothing pending | "GEO-X-1 연결이 끊겼습니다" / "마지막 응답 5분 전" (+ "수집은 장치에서 계속될 수 있습니다." when a run was known) | — | transport remedy |
| 4 | one run active (own or adopted) | "신월4거리 수집 중" / "00:12:34 경과 · 구역 안" | 진행 보기 | 진행 보기 (last values) |
| 4b | more than one active run seen | "GEO-X-1에서 수집 2건이 진행 중으로 보입니다" / "새로 시작하기 전에 진행 중인 수집을 확인하세요." | 진행 보기 | transport remedy |
| 5 | ended, result not opened | "신월4거리 수집이 끝났습니다" | 결과 보기 | 결과 보기 |
| 6 | receipt verified, ticket linked, not reported | "신월4거리 서버 수신을 확인했습니다" / "GEO& Work에 완료를 보고하세요." | 완료 보고 | 완료 보고 (server-only work) |
| 7 | reachable only by 기본 연결 | "GEO-X-1 기본 연결입니다" / "같은 네트워크에 연결하면 모든 기능을 쓸 수 있습니다." | 장치 Wi-Fi 설정 | — |
| 8 | idle | no block — the queue is the action | — | — |

**Transport remedies** (when the operation needs the device and it is not reachable): credentials rejected → primary "QR 코드로 다시 등록" ("GEO-X-1 인증 정보를 확인할 수 없습니다."); permission missing → "권한 허용" ("GEO-X-1 · 연결 권한이 필요합니다"); otherwise → primary "다시 연결" + secondary "연결 방법" (both immediately). The remedy changes the button, never the operation text: a pending start stays visible while reconnecting. Unsent runs, failed transfers and failed reports never take the Focus block; they live on their rows, 전송 › 보낼 수집 and the tab dot.

### 1.7 Behaviour rules

- **Refresh cadence (unchanged from today):** device status every 1 s over 같은 네트워크 and 직접 연결; uploads every 1 s for the active job and 15 s for the history; run telemetry every 5 s on `run`; tickets every 60 s while 작업 is visible; alert monitors 5 s (tasks) and 2 s (uploads); bell recount 60 s. Lists offer pull-to-refresh, the freshness line with "다시 확인" when older than 60 s, and an accessibility action "새로 고침". No refresh icons in top bars or menus.
- **Keyboard:** IME open hides the bar; the action area rides above it; the focused field is brought into view.
- **Permissions just in time, never at launch:**

| Permission | Asked when | In-app rationale (shown first) |
|---|---|---|
| 카메라 | "QR 코드 스캔"; "카메라" preview never needs it (device camera) | 장치 등록 "등록에 필요한 권한" rows; scanner permission block |
| 블루투스 · 근처 기기 | "등록" in the pairing sheet; "블루투스로 기본 연결" | the sheet or row text |
| 근처 Wi-Fi 기기 | "장치에 직접 연결" | 직접 연결 permission block |
| 로컬 네트워크 (Android 17+) | same-network search on 장치 is blocked | device strip / 장치 root permission block |
| 위치 (Wi-Fi 검색) | 장치 Wi-Fi › 주변 네트워크 | permission block |
| 위치 (휴대폰 위치 표시) | the map switch "휴대폰 위치 표시" | the switch row |
| 알림 (Android 13+) | turning on an alert in 알림 기준; the 설정 row | row + block |

  When auto-reconnect is blocked by a missing permission, the device strip reads "GEO-X-1 · 연결 권한이 필요합니다" with "권한 허용" (one OS dialog for exactly the missing set).
- **Developer mode:** daily screens do not change. 설정 gains the section 고급 with one row "개발자 도구". The 서버 수신 결과 › 휴지통 segment and the "받을 서버가 없습니다" state each gain one tertiary link row in developer mode ("개발자 도구에서 비우기", "개발자 도구에서 업로드 서버 설정"); everything else developer-only lives under `dev`. With "화면에 기술 정보 표시" on, detail screens append the collapsed 기술 정보 section.

---

## 2. Flows with tap counts

Counts include confirmation taps and exclude scrolling, typing, waiting and OS permission dialogs. "(n)" marks a tap.

| Flow | Taps | Start point |
|---|---:|---|
| F1 first registration | 2 | 장치 등록 scene (later devices: 장치 › "+" = 2) |
| F2 daily check | 0 for the strip; 1 for 장치 상세; 2 for the camera | 작업 root |
| F3 known ticket → collecting | 3 (2 from an open ticket) | 작업 root |
| F3 with an ambiguous task, first time | 4 | 작업 root |
| F3 manual repeat (last project/section restored) | 3 | 작업 root |
| F4 watch a run | 0 after start; 1 from any root or detail via the ongoing-run entry | anywhere |
| F5 stop → result | 2 | `run` or the full map |
| F5 result → sending | 2 | `run_result` |
| F5 completion report | 2 | `run_result`, 작업 Focus 6 or the verified transfer detail |
| F6 arbitrary file upload | 3 (+ folder traversal) | 전송 root |
| F6 saved run upload | 2 | 전송 › 보낼 수집 row |
| F7 reconnect | 1 for the retry; 3–5 for 직접 연결 | strip or Focus block |
| F8 developer tools | 11 while locked; 2 once unlocked | 작업 root |

**F1 · First run and registration — 2 taps**
1. Cold start with no device: splash → 장치 등록 scene (no bar). Heading "GEO-X에 장치를 등록하세요"; body "장치 본체의 QR 코드를 스캔하면 이 휴대폰에서 장치를 관리할 수 있습니다."; device photo 120; section "등록에 필요한 권한" with rows "카메라 · QR 코드를 읽습니다." and "블루투스 · 가까운 장치를 찾아 인증합니다."; primary "QR 코드 스캔"; tertiary "장치 없이 서버 파일 보기"; gear → 설정.
2. "QR 코드 스캔" (1) → camera permission if needed → `scan`. Denied: the stage shows the permission block ("카메라 권한이 꺼져 있습니다" + "권한 허용", or "설정에서 허용" when permanently denied) and "코드 직접 입력" stays available.
3. QR read → haptic, preview freezes, sheet "장치 등록": photo 40, "GEO-X-1", "등록 코드 •••• 4DE0", "블루투스로 장치를 찾아 이 휴대폰에 등록합니다.", secondary "다시 스캔", primary "등록".
4. "등록" (2) → Bluetooth/nearby permission if needed → progress in the same sheet: a 4 dp indeterminate bar and one line changing in place ("GEO-X-1을 찾는 중입니다." → "연결하는 중입니다." → "장치를 확인하는 중입니다." → "인증하는 중입니다." → "상태를 맞추는 중입니다.") and tertiary "취소" (cancel keeps the scanned code so "다시 시도" needs no rescan).
5. Success → 작업 with the bar, snackbar "GEO-X-1을 등록했습니다.", device strip "확인 중" → "연결됨". Bluetooth-only → Focus 7 "장치 Wi-Fi 설정".
6. Failure → "등록하지 못했습니다" + mapped cause ("장치를 찾지 못했습니다. 장치 전원과 거리를 확인하세요." / "인증하지 못했습니다. QR 코드를 다시 스캔하세요." / "응답이 늦습니다. 다시 시도하세요."), primary "다시 시도", tertiary "취소".

**F2 · Daily check — 0 / 1 / 2 taps**
1. Cold start → 작업 (or the loading scene first when restore takes > 300 ms).
2. The device strip changes in place: "● 확인 중" → "● 연결됨 · 54°C · 저장 512 GB 남음 · 방금 갱신". The first attention item replaces the last fact in `warning` ("카메라 지연", "저장 공간 92%"); the 장치 tab dot shows when any device has an item in 확인할 일.
3. Exceptions surface without hunting: unreachable → Focus 3; unverified or failed transfers → 전송 dot; verified but unreported → Focus 6.
4. Detail: strip (1) → 장치 상세 (상태 figures, 센서, 연결). Camera: 장치 상세 › 카메라 (2).

**F3 · Ticket → collecting — 3 taps**
1. 작업 › 수집 구간: segmented "GEO-X-1 | 전체" (default = the device's 호기; unassigned tickets included); rows sorted 진행 중 → 막힘 → 할 일; 완료 collapsed under "완료 N개"; meta "마감 10월 21일 · 담당 김현우 · 350 m" (distance from the fresh device position, "구역 안", or nothing when unknown).
2. Row (1) → `ticket/{rowId}` (bar visible): H1 + badge, meta "수원시 도로대장 · GEO-X-1 · 마감 10월 21일", 지도 카드 + caption, 정보 rows (담당, 마감, GEO& Work 보고, 수집 작업), 준비 상태 rows from telemetry (연결, 저장 공간, 위치 품질, 카메라, 보정 정보 중계 when used). The PreparationDraft is written.
3. "수집 시작" (2) → when the task is ambiguous and not remembered, the S3 "수집 작업 선택" sheet appears first (+1, remembered per device, the draft keeps the ticket) → the preflight runs inside S8 (rows "확인 중", primary busy "점검하는 중").
4. S8 passes → "수집 시작" (3) → start transaction (§1.5) → `run` in "시작 확인 중".
5. Failures: S8 lists each failed check with its reason and remedy ("저장 공간이 수집 설정의 최소값보다 적습니다 · 남은 공간 12 GB" + "장치 파일"), the confirm stays disabled with "점검을 통과해야 시작할 수 있습니다.", "다시 점검" reruns. Role VIEWER → "수집 시작" disabled with "수집 시작은 작업자 권한이 필요합니다."; 막힘 → "막힘 상태입니다. GEO& Work에서 상태를 바꾼 뒤 시작하세요."; conflict → inline note "다른 사람이 먼저 바꿨습니다. GEO& Work에서 확인하세요."; device unreachable → strip + disabled primary with the transport remedy.
6. Manual path: 작업 › "구간 직접 고르기" (1) → `prepare` with the last valid project/section restored → "수집 시작" (2) → S8 "수집 시작" (3). Changing the project: picker (+1) → row (+1); changing the section: row (+1); "새 구간" opens an inline name field (created in the start transaction).

**F4 · Watch a run — 0 / 1 tap**
1. `run` (no bar): strip (0–1); H1 section name; meta "GEO-X-1 · 수원시 도로대장"; 지도 카드 240 with zone, live position, trail and the zone badge; figures 경과 시간 · 수집 용량 · 정밀 위치 · 구역 밖 기록; "마지막 확인 3초 전"; rows "카메라 영상 ›", "지도 크게 보기 ›", "보정 정보 중계" (when used); action area: readiness "GEO-X-1 · 수집 중 · 구역 안." + danger-outline "수집 종료".
2. Elsewhere: every root and detail screen of that device shows the info strip "GEO-X-1 · 신월4거리 수집 중 · 00:12:34" with "진행 보기" (1); 작업 shows Focus 4.
3. Leaving the range (§5.5): warning strip "GEO-X-1이 수집 구역을 벗어났습니다 · 35 m" and one haptic; returning clears it. Nothing starts or stops by itself.
4. Unreachable: strip "GEO-X-1 연결이 끊겼습니다. 수집은 장치에서 계속될 수 있습니다." + "다시 연결" / "연결 방법"; figures keep "마지막 값 기준"; "수집 종료" disabled with "연결되면 종료할 수 있습니다."
5. Start not confirmed: H1 "시작 확인 중", note "같은 수집의 시작 결과를 확인합니다. 확인이 끝날 때까지 새 수집을 시작할 수 없습니다.", primary "시작 결과 확인". Unknown: "수집 상태를 확인할 수 없습니다" + "상태 다시 확인".

**F5 · Finish → device save → server receipt → report**
1. "수집 종료" (1) → S1b "신월4거리 수집을 종료할까요?" → "수집 종료" (2) → "종료 확인 중" in place → once termination is confirmed, `run_result/{runId}` replaces `run`. A remote end stays on `run` with "수집이 끝났습니다" + "결과 보기".
2. `run_result`: receipt rows 수집 종료 · 장치 저장 (1,204개 · 1.2 GB) · 서버 수신 · GEO& Work 보고, figures, map card (trail over the bound zone; "구역 밖 기록 2회 · 1분 10초 · 관찰하지 못한 시간 2분"), 저장 폴더, 위치 품질.
3. The primary follows the chain: storage unconfirmed → disabled "서버로 보내기" with the reason · stored and reachable → "서버로 보내기" (1) → `send` → "전송 시작" (2) → `transfer/{jobId}` · sending → "전송 보기" · sent, unverified → "수신 확인" · verified, linked, unreported → "완료 보고" (1) → S1a (2) · reported or no ticket → "다음 수집" (back to 작업).
4. Not on 같은 네트워크 (typical with 직접 연결): "서버로 보내기" disabled with "같은 네트워크에서만 서버로 보낼 수 있습니다. 지금은 직접 연결입니다." and the remedy "장치 Wi-Fi 설정"; the run waits in 전송 › 보낼 수집.
5. Report later: 작업 Focus 6 → "완료 보고" (1) → S1a (2); or the verified `transfer/{jobId}` → "완료 보고" (1) → S1a (2). Works while the device is offline when the server is reachable.

**F6 · Upload and transfer management**
1. 전송 root (current device): 보내는 중 (progress) · 확인이 필요한 전송 (실패, 내용 불일치) · 보낼 수집 (stored runs without a receipt; trailing "보내기") · 대기 중 · 수신 확인 중 · 서버 수신 확인 (newest 3 + "전체 기록") · other devices needing attention ("GEO-X-2 · 확인이 필요한 전송 1건", tap switches device) · 서버 ("서버 수신 결과 ›", "GEO& Work 알림" status row).
2. Arbitrary files: "파일 보내기" (1) → `files?mode=send` → folder row radio (2) → the action area shows "20261019_1636 · 1,204개 · 1.2 GB", 받는 서버 (fact, or a picker when more than one) and "전송 시작" (3) → `transfer/{jobId}`.
3. Saved run: 보낼 수집 row "보내기" (1) → `send` → "전송 시작" (2).
4. `transfer/{jobId}` by state: sending → danger-outline "전송 취소" (S1b) · failed → "다시 보내기" · sent, unverified → "수신 확인" · mismatch → "다시 보내기" with the reason · verified → "완료 보고" when linked and unreported, else "수집 결과" or "서버에서 보기"; below, the 관리 block: danger-outline "장치 원본 정리" (S1a) or "장치 원본 복원".
5. `transfers/log`: every job including 취소됨; segmented 전체 | 실패 | 완료 | 취소; row ⋮ "기록 삭제" (S1a).
6. `server`: segmented 수신 결과 | 휴지통; paging "이전 수신 결과 더 불러오기"; job → files, "서버 수신 영수증", ⋮ "서버 휴지통으로 이동"; video files play in P8 with "다시 시도"; trash rows "복원" (or "복원할 수 없음"), the line "전체 128개 중 100개를 표시합니다."
7. Device trash: 장치 상세 › 휴지통 (or the browser ⋮ › 휴지통): rows "원래 위치로 복원"; ⋮ "휴지통 비우기" (S1b).

**F7 · Offline recovery**
1. Signal: device strip / Focus block on roots, status strip on detail screens — device name first, last response time.
2. "다시 연결" (1): the existing single connect path; badge "확인 중", strip "GEO-X-1에 연결하는 중입니다."; no response within 15 s → "응답 없음" + "GEO-X-1이 응답하지 않습니다. 같은 네트워크인지 확인하세요."
3. "연결 방법" (1, available from the first moment) → S3 with 같은 네트워크에서 다시 찾기 · 장치에 직접 연결 · 블루투스로 기본 연결 · QR 코드로 다시 등록, each with when to use it.
4. 장치에 직접 연결 (2) → S1a when it would cut a LAN link or a transfer ("GEO-X-1에 직접 연결할까요?") (3) → `device/{id}/direct` → device row (4) → connecting / 장치 확인 중 / connected → back to the opener, never a root.
5. Credentials rejected → danger strip "GEO-X-1 인증 정보를 확인할 수 없습니다." + "QR 코드로 다시 등록" → `scan`.
6. During a run the collection continues on the device; on reconnect the app re-adopts it and `run` resumes; a pending start stays pending until the device answers.
7. Server unreachable: lists show "마지막 동기화 10월 19일 09:11" with "다시 확인"; server-only work stays available.

**F8 · Developer tools**
1. 설정 (1) › 앱 정보 (2) › 버전 ×7 (9): from the fourth tap the snackbar counts ("개발자 모드까지 3번 남았습니다."), at the seventh "개발자 모드를 켰습니다." → back (10) → 고급 › 개발자 도구 (11). Unlocked: 2 taps.
2. `dev` hub: switches 개발자 모드 (off → back to 설정) and 화면에 기술 정보 표시; sections 장치 · 수집 작업 · 서버·전송 · 진단 (§7.2 #92).

---

## 3. The rulebook (executable)

Values come from `web-tokens.css`; where the app needs more (field ergonomics, Android insets) the rule says why. Every rule here has one owner in code (named in brackets) so two implementers cannot produce different screens.

### 3.1 Window classes, panes and grid [`GeoAppShell`, `GeoWindow`]

1. **Classification uses the current window or pane, never orientation or `smallestScreenWidthDp`.** Read `LocalWindowInfo`/`BoxWithConstraints` of the shell content after system bars: width class compact < 600 dp ≤ medium < 840 dp ≤ expanded; height class compact < 480 dp. `TABLET_UI_SCALE` and every density multiplier in `ui/theme/Theme.kt` are removed — 1 dp in this spec is 1 dp on every device.
2. **Navigation chrome:** bottom bar when the available width is < 600 dp; an 80 dp rail at ≥ 600 dp, in any orientation (a phone in landscape is 882 dp wide and gets the rail). The rail's items are top-aligned with 8 dp gaps and the rail scrolls vertically when four items at the current font scale do not fit.
3. **Pane width** = window width − rail (80, when shown) − horizontal safe-drawing insets. All gutters and columns derive from the pane width, not the window.
4. **Gutter** (web `--mobile-page-gutter`): 24 dp when the pane is ≥ 390 dp, 20 dp at 340–389 dp, 16 dp below 340 dp.
5. **Content column:** max 720 dp, centred in the pane on `canvas` when the pane is wider; the body sheet is `surface`.
6. **Two panes (list | detail)** only when all of these hold: width class expanded, height class not compact, font scale < 1.5, and the pane fits `listMin + 1 + detailMin + 2 × gutter`, where `listMin = 320 dp × max(1, fontScale)` and `detailMin = 400 dp × max(1, fontScale)`. Used by 장치 (list | 상세), 전송 (list | 상세), 작업 (queue | 수집 구간) and 설정 (list | page). Otherwise single pane.
7. **Columns and spans.** Compact: 4 columns, 16 dp gaps; medium: 8; expanded single pane: 8 inside the 720 dp column. Spans: sections, rows, notes, strips, the map card and the action area span all columns; figure-grid cells span half (2 of 4, 4 of 8); a button pair splits into two equal halves; the theme swatch, device photo and icons are fixed sizes, not spans. Label/value pairs are not grid items: label 40 %, value 60 % of the row's content width.
8. **Vertical rhythm:** multiples of 4 dp; heights are minimums, never fixed for anything that holds text.

### 3.2 Inset contract (one owner per edge) [`GeoAppShell`, `GeoTopBar`, `GeoBottomBar`, `GeoActionArea`]

1. `enableEdgeToEdge()` in `MainActivity`; `isNavigationBarContrastEnforced = false`; the app paints every system-bar area itself.
2. **Horizontal safe drawing** (display cutout, side navigation bar in landscape) is applied once by the shell to its whole content — top bar, body, action area, bottom bar and rail. Nothing below re-applies it.
3. **Top inset** (status bar + top cutout) is applied once by `GeoTopBar`, which paints `topBar` behind it. Screens without a top bar (scenes, media) pad their content by `statusBars` themselves and paint the scene colour behind it.
4. **Bottom navigation inset** is applied once by exactly one of: the bottom bar (paints `nav` behind it), the sticky action area of a screen without a bar (paints `surface`), a sheet footer, or — when none of those exists — the scroll content's end padding (content colour shows behind the buttons).
5. **IME:** the nearest common ancestor of a form body and its action area applies `imePadding()` once and calls `consumeWindowInsets`; descendants never apply IME padding. While the IME is visible the bottom bar is hidden, so the IME replaces the navigation inset.
6. **A rail does not protect content from the other edges:** content still takes the bottom inset (when the navigation bar is at the bottom) and the end inset (when it is on the far side).
7. **System-bar icons** follow the colour under each bar separately (role table rows "status-bar icons" / "navigation-bar icons"); one controller in `JetsonControllerTheme` sets them on every frame where the colour under the bar changes (navy top bar → light status icons even in a light theme; a sheet or scrim → the colour of the sheet).
8. Acceptance: Samsung three-button and gesture navigation, both landscape directions (navigation bar left and right), split screen resizing, IME-open forms and sheets, display cutout on top and side.

### 3.3 Spacing scale with named uses [`GeoSpace`]

| Token (web) | dp | Named uses |
|---|---|---|
| `space-1` | 2 | focus-ring offset; optical nudges of icons against text |
| `space-2` | 4 | row title ↔ meta line; segmented thumb inset; gap between wrapped badge lines; progress bar height |
| `space-3` | 6 | badge dot ↔ label; caption icon ↔ caption text |
| `space-4` | 8 | section title ↔ first row; field label ↔ field; readiness line ↔ primary; button ↔ button in a pair; badge horizontal padding; rail item gap |
| `space-5` | 10 | icon ↔ label inside buttons and menu items |
| `space-6` | 12 | **two-line row vertical padding** (12 + 24 + 4 + 20 + 12 = 72 dp); action-area vertical padding; overlay inset on map and camera stage; field ↔ helper text |
| `space-7` | 14 | figure label ↔ value |
| `space-8` | 16 | single-line row vertical padding (16 + 24 + 16 = 56 dp); leading icon/photo ↔ text; figure-grid gaps; contained-block padding (Focus block, inline note, map panel, sheet sections); gutter < 340 dp |
| `space-9` | 20 | gutter 340–389 dp; loader ↔ app name |
| `space-10` | 24 | gutter ≥ 390 dp; **gap between sections (owned by `GeoSection`)**; dialog padding; sheet side padding |
| `space-11` | 28 | extra gap before a danger section (관리, 이 휴대폰) — replaces the 24 dp of that section |
| `space-12` | 32 | H1 block ↔ first section on detail screens; gaps inside scenes |
| `space-13` | 40 | scroll-content end padding above the action area or bottom chrome |
| `space-14` | 56 | top padding of scenes (장치 등록, loading error) |

Ownership: `GeoSection` alone puts 24 dp above its title (28 for danger sections; none for the first section directly under a top bar, strip or H1 block, which uses the H1 block's 32 or the root's 16 dp top padding). Lists and parents never add `spacedBy` between sections. Components own their internal paddings; no other spacing value may appear in screen code.

### 3.4 Sticky actions, compact height and keyboard [`GeoActionArea`]

1. A screen with a primary action declares it once; `GeoActionArea` decides where it sits.
2. **Sticky** (pinned above the bottom bar or the navigation inset) when `viewport − measured top chrome − measured bottom chrome − IME − measured action area ≥ 280 dp`; otherwise it is the last item of the scroll content with the same look. On the drawing frame: 882 − 92 − 112 − 108 = 570 → sticky. In landscape (412 high) it is inline.
3. Tab roots have no action area except 전송 ("파일 보내기"); scenes (장치 등록) pin their primary.
4. **The action area never shrinks while the screen is visible**: it keeps the largest height it has measured, so a live readiness line cannot move the primary under the thumb. It may grow.
5. Compact height (< 480 dp): action areas inline, status strip single line with its action, map card 160 dp, two-column content where the template defines it (수집 중: map | figures; 수집 결과: receipt | map).
6. IME open: the bottom bar hides, the action area rides above the IME (rule 2 still applies), the focused field is brought into view; forms that take the keyboard as their main content are focused flows (no bar at all, §1.2).

### 3.5 Text alignment, numbers and wrapping [`GeoText`, `GeoNumber`]

1. **Start alignment** is the default for titles, body, rows, sheets, dialogs, notes, Focus blocks and state blocks (top-aligned, never centred in a void).
2. **Centred** only for: button labels, segmented and tab labels, the loading and error scenes, and instructions anchored to centred media (the QR frame).
3. **End alignment** for trailing values in rows, counts in section headers ("3건"), and the percentage after a progress bar.
4. **Numeric columns:** every list that shows numbers reserves one trailing value slot per list, as wide as its widest value at the current font scale (measured with `SubcomposeLayout`), followed by a separate unit slot. Numbers are end-aligned in their slot with tabular figures (`fontFeatureSettings = "tnum"`); units are start-aligned in theirs, so "896 MB" and "14.7 GB" line up on the number's last digit. Figure-grid cells are start-aligned (each cell is its own column).
5. **Pairs:** label 40 % start, value 60 % end; the value wraps and never ellipsizes; when the label or value would need more than 2 lines, or font scale ≥ 1.5, or the row is < 320 dp, the pair stacks (label above, both start-aligned).
6. **Identity names** (device, ticket, section, project, file and folder names) wrap without limit in every component and are never ellipsized; the full name is also the accessibility label. Only free text may be clamped, with a visible "더 보기" that expands in place.
7. **Korean line breaking:** `LineBreak.Paragraph` + `LineBreak.WordBreak.Phrase` on API 33+, kept only if the measured acceptance strings break between eojeol on the test devices ("GEO-X-현장지원차량-2", "신월4거리 북측 보조 구간 2차 재수집", "같은 네트워크에 연결하면 모든 기능을 쓸 수 있습니다."). Number + unit pairs are joined with a no-break space. Names are never made unbreakable as a whole; a single token wider than the line (a long folder name, a URL in 기술 정보) breaks at grapheme boundaries as the emergency fallback. Copy never contains hard-coded "\n".
8. **Punctuation:** sentences end with a period; titles, labels, buttons, badges and row titles do not. " · " joins facts on one line.

### 3.6 Type roles [`GeoType`]

Pretendard Variable at 400 / 500 / 600 / 700 (explicit `FontVariation`), sp everywhere. Line heights start from the web ratios and are rounded to an even sp; title-sm, title and board-title get 2 sp more because Korean names wrap.

| Role | Size / line | Weight | Tracking | Web token | Use |
|---|---|---|---|---|---|
| count | 12 / 16 | 600 | 0 | `--font-count` | bell count |
| caption | 13 / 20 | 400 (600 in badges) | −0.003 em | `--font-caption` | meta lines, timestamps, helper and error text, badge text, map caption, tab labels (500/700) |
| label | 14 / 20 | 500 | 0 | `--font-label` | field labels, segmented labels, legend words |
| body | 15 / 24 | 400 | −0.003 em | `--font-body` | paragraphs, fact values, dialog and note text, **status strip, readiness line and every recovery explanation** |
| title-sm | 16 / 24 | 500 rows · 600 section titles | −0.003 em | `--font-title-sm` | row titles, section titles, device strip name, Focus block title |
| title | 18 / 26 | 600 | −0.015 em | `--font-title` | pushed top-bar titles, sheet and dialog titles, "미확인" inside a figure cell |
| board-title | 22 / 30 | 700 | −0.025 em | `--font-board-title` | the object H1 on detail screens |
| heading | 24 / 32 | 700 | −0.025 em | `--font-heading` | root top-bar titles, figure values (tnum), the run clock |
| button | 16 / 20 | 600 | 0 | `--font-button` (mobile) | every button label |
| input | 16 / 24 | 400 | 0 | `--font-input` (mobile ≥ 16) | text fields and picker values |
| mono | 13 / 20 | 400 | 0 | `--font-mono` | identifiers in 기술 정보, terminal, logs (system monospace) |

**고대비** raises caption, label and body one weight step (400 → 500, 500 → 600) and row titles to 600. The current `Type.kt` roles at 17, 19, 21, 26, 28 and 32 sp retire.

### 3.7 Shape, elevation, borders and focus [`GeoShape`]

| Radius (web) | dp | Used by |
|---|---|---|
| `radius-sm` | 6 | checkboxes, skeleton bars, small swatches |
| `radius-md` | 10 | all buttons, text fields, picker fields, theme swatches |
| `radius-lg` | 12 | map card, Focus block, inline notes, menus, snackbar, on-map panels |
| `radius-xl` | 14 | dialogs |
| `radius-2xl` | 20 | sheet top corners |
| `radius-pill` | 999 | status badges, the segmented track and thumb — nothing else is a pill |

Icon buttons and map controls are circles (a circle is not a pill button). Progress bars are 4 dp with 2 dp ends. Elevation: body content none; `shadow-sm` for on-map controls, the on-map badge and switch thumbs; `shadow-lg` for menus, dialogs, sheets and the snackbar (dark palettes: the web's white 10 % outline + shadow). The top bar shows `topBarBorder` only while content is scrolled under it. Focus: 2 dp `focus` ring with 2 dp offset and `focusHalo` (4 dp) outside it, on every focusable element; on chrome the ring is `navFocus`; on the map it has a white halo (§4.2 controls).

### 3.8 Component catalogue with states [`ui/components/geo/`]

(Status icons in rows and strips use the `GeoIcons` status set of §3.9: `success`, `warning`, `danger`, `info`, `pending` — a 16 dp circular indicator while something runs — and `unknown`.)

Screen code imports only `Geo*` components (an import lint, applied package by package; `ui/components/geo` itself is exempt). Components render a supplied state and never infer freshness, verification or connection themselves — screen models compute those (`ui/model/*`). States use one vocabulary: enabled · pressed · focused · disabled (with a visible reason) · busy · selected · error. Pressed = `fieldPressed` fill on rows and secondary/tertiary/icon buttons, `primaryPressed` / `dangerPressed` on filled buttons; no ripple. Disabled = `disabledFill` + `disabledText` with the reason visible next to it. Busy = 16 dp circular indicator + present-progressive label ("보내는 중"), same size, input blocked.

| Component | Anatomy and sizes | States and rules |
|---|---|---|
| **GeoTopBar · root** | status inset + min 64 dp row; title `heading` at the gutter; trailing: device switcher (8 dp `chrome*` dot + name `title-sm`/600 `onTopBar` + 20 dp chevron `onTopBarIcon`, min 48 dp) and/or 48 dp icon buttons (bell with `attention` count, add) | `topBar` fill. If title + trailing do not fit on one line (measured), the switcher moves to a second row under the title (min 48 dp, start-aligned) and the bell stays on row 1. Never a logo |
| **GeoTopBar · detail** | status inset + min 56 dp; back 48 dp at 4 dp from the edge; title `title` from 64 dp; ≤ 2 trailing items | title names the place ("수집 구간", "장치", "전송"); the object name is the H1; wraps to 2 lines; navy in 네이비 (밝게) |
| **GeoBottomBar** | 4 equal items, min 64 dp + navigation inset; icon 24 (outline → filled); label `caption` 500 → 700; 8 dp `attention` dot at the icon's top-end | shown on roots and tab-owned detail screens (§1.2); the owning tab is lit; re-tap pops to root; `Role.Tab`; grows for large text |
| **GeoRail** | 80 dp; same items top-aligned, 8 dp gaps; scrolls | at pane width ≥ 600 dp |
| **GeoStatusStrip** | full-bleed under the top bar, min 48 dp, 12 dp vertical padding and the page gutter horizontally; 20 dp tone icon, 12 dp gap, text `body` starting with the device name, trailing tertiary actions (48 dp) | tone-soft fill; one per screen; priority 인증 오류 > 연결 끊김 > 구역 밖 > 수집 중 (the shared ongoing-run entry, info) > 마지막 동기화 (server stale, neutral); expands/collapses in 180 ms; polite live region |
| **GeoFocusBlock** | in content, radius 12, 16 dp padding, tone-soft fill; 20 dp icon + title `title-sm`/600; 1–2 `body` lines; primary (full width, 52) + optional tertiary | tab roots only; one per screen; precedence rows of §1.6; never for success |
| **GeoDeviceStrip** | min 72 dp: photo 40, name `title-sm`/600 + badge (wraps under at large text), meta `caption` "54°C · 저장 512 GB 남음 · 방금 갱신", chevron | the first attention item replaces the meta's last fact in `warning` with a 16 dp icon ("카메라 지연", "저장 공간 92%"); hidden while Focus 2/3 shows |
| **GeoSection** | title `title-sm`/600; 24 dp above (owner), 8 dp below; optional trailing tertiary or end-aligned count; optional one-line `caption` note | heading semantics; no box, no icon, no eyebrow |
| **GeoRow · navigation** | min 56 dp single line / 72 dp two-line (12 + 24 + 4 + 20 + 12); optional leading 24 dp icon or 40 dp photo; title `title-sm`/500 (wraps); meta `caption` `textSecondary`; trailing value slot (§3.5) and/or badge; 20 dp chevron `textSubtle` | one target; pressed fill; disabled shows its reason as meta |
| **GeoRow · action** | content area (leading + title + meta) and a trailing 48 dp secondary button ("다시 보내기", "복원", "보내기", "등록") | **two separate targets**: the content area opens the detail, the button acts; 8 dp between them; each has its own semantics ("…, 상세 보기" / "다시 보내기") |
| **GeoFactRow** | label `body` `textSecondary` 40 % · value `body` `text` 60 % end, wraps | stacks per §3.5.5; tnum for numbers |
| **GeoCheckRow** (readiness and preflight) | min 56 dp; 20 dp tone icon · title `title-sm`/500 · value `body` end; failure adds a second line `body` with the reason and an optional trailing remedy action | status icon per §3.9; rows keep their order while results arrive; only a failure adds its reason line under its own title |
| **GeoSwitchRow / GeoRadioRow / GeoCheckboxRow** | min 56 dp (72 with a description); whole row toggles; switch at the end, radio/checkbox at the start | §4.2 controls; `Role.Switch` / `RadioButton` / `Checkbox`; radio groups carry a group label |
| **GeoSliderRow** | title + current value end ("80°C 이상"); slider full width; 48 dp −/+ buttons at both ends (one step each) | value announced on change; the slider alone is never the only way |
| **GeoFigureGrid** | plain: aligned to the gutter, 2 columns, 16 dp gaps; cell = label `caption` · 14 · value `heading` tnum `text` · detail `caption` (slot always reserved). Contained variant (in a panel): 16 dp padding once | one column when a cell would be < 132 dp or font scale ≥ 1.5; values never ellipsize; invalid or missing → "미확인" (`title` `neutral`); stale → last value + detail "마지막 값 기준"; over threshold → value stays `text`, 16 dp `warning` icon before the label, detail `warning` "기준 80°C 초과"; a legitimate 0 shows "0%" |
| **GeoReceipt · list** | four rows 수집 종료 · 장치 저장 · 서버 수신 · GEO& Work 보고: title `title-sm`/500 + badge; meta `caption` (counts, sizes, reasons) | the screen's primary follows the first row that is not done |
| **GeoReceipt · line** | in rows: `FlowRow` of three pairs "label ● value" (`caption`) | pairs wrap whole; one pair per line at font scale ≥ 1.5 |
| **GeoBadge** | pill, min 24 dp high, 8 dp horizontal padding, 6 dp dot + 6 + one vocabulary word `caption`/600 | tone-soft fill, tone dot and text; 고대비 adds a 1 dp solid tone border; on-map variant `mapPlate` + `shadow-sm`; on-dark variant opaque #1E1F21 (§4.3); never tappable, never carries time; one per row |
| **GeoButton · primary** | min 52 dp, radius 10, `primary`, label `button` `onPrimary`; full width in action areas (max 560 dp); leading 20 dp icon only when it disambiguates | one per screen; disabled keeps its place with the reason in the readiness line or next to it, plus a remedy when one exists |
| **GeoButton · secondary** | min 48 dp (52 beside a primary), 1 dp `borderStrong`, label `text` | pairs per the stacking rule below |
| **GeoButton · tertiary** | min 48 dp target, 12 dp horizontal padding, label `primary` | section actions, strips, Focus secondary, state blocks |
| **GeoButton · danger-outline** | min 48 dp (52 in action areas), 1 dp `danger` border, label `danger` | stop, reboot, power-off, cancel transfer, source clean-up, 등록 해제, 휴지통 비우기; a consequence line is visible above it |
| **GeoButton · danger-filled** | as primary with `danger` / `onDanger` | only the confirm of an S1b dialog |
| **Stacking rule (one rule everywhere)** | buttons that share a row (screen pairs, dialog actions, sheet footers) stay on one row when every label fits on one line in its share (equal halves for pairs; natural width, end-aligned, for dialogs) | otherwise they stack full width, primary/confirm first; labels may wrap to 2 lines and the button grows |
| **GeoIconButton** | 48 × 48, 24 dp icon `textSecondary` (chrome: `onTopBarIcon`), circular pressed fill | content description required |
| **GeoActionArea** | `surface`, 1 dp top `border`, 12 dp vertical padding, gutters; readiness line (16 dp tone icon + `body` `textSecondary`, ≤ 2 lines) → 8 → primary (+ secondary) | §3.4; readiness starts with the device: "GEO-X-1 · 점검을 마쳤습니다." |
| **GeoSegmented** | pill track `field` + 1 dp `borderStrong`, 48 dp, 4 dp inset; thumb `surface` pill + 1 dp `borderStrong`; labels `label` (600 selected / 500 unselected); 2–4 equal segments ≥ 72 dp | thumb slides 180 ms; `selectableGroup` + `Role.Tab`; becomes a picker field when a label does not fit or font scale ≥ 1.5 |
| **GeoPickerField** | radius 10, `field` fill, 1 dp `borderStrong`, min 56 dp; label `caption` + value `input` + 20 dp chevron-down | opens a choice sheet; disabled shows the reason under it |
| **GeoTextField** | label `label` above (8 dp); field radius 10, `field`, 1 dp `borderStrong`, min 56 dp, value `input`; helper/error `caption` under it | focused 2 dp `primary`; error 2 dp `danger` + icon + text; disabled `disabledFill`; password: 48 dp show/hide; numeric: numeric keyboard + unit suffix; multiline ≥ 3 lines |
| **GeoProgress** | linear 4 dp, track `field`, indicator `primary`; determinate shows % (`caption` tnum, floor) | indeterminate under a top bar for loads; circular only inside busy buttons |
| **GeoSkeleton** | `sunken` bars radius 6 in the shape of the rows | first load > 300 ms only; static |
| **GeoSheet · choice / form / info** | `ModalBottomSheet`, top radius 20, 32 × 4 handle, title `title`, optional `body` sub-line; rows ≥ 56 dp; footer pinned above the navigation inset (form, choice with action) | the body always scrolls and a visible dismiss stays ("닫기" or the handle + back); **full-screen fallback** when header + footer leave < 160 dp of body at the current font scale (then a full-screen dialog with a top bar "닫기"); focus returns to the opener on close |
| **GeoStartSheet (S8)** | the merged start confirmation: title "{구간} 수집을 시작할까요?"; ticket line (section · project · device); zone line ("구역 안" badge or "장치가 구역에서 350 m 떨어져 있습니다."); 시작 전 점검 check rows from the preflight; GEO& Work line "GEO& Work 상태가 '진행 중'으로 바뀝니다." (ticket only); footer "취소" + primary "수집 시작" | while the preflight runs: check rows "확인 중" and the primary busy "점검하는 중"; a failure lists each failed row with its reason and remedy, and the primary stays disabled with "점검을 통과해야 시작할 수 있습니다."; "다시 점검" (tertiary) reruns |
| **GeoConfirmDialog (S1a / S1b)** | radius 14, 24 dp padding, 280–560 dp; title `title` "[대상]을 [동작]할까요?"; body `body` = consequence + recoverability; optional fact rows; actions: dismiss tertiary ("취소", or what continues) + filled confirm repeating the verb | body scrolls; stacking rule; S1b uses danger-filled; never "확인/예"; the confirm is disabled while its precondition is false, with the reason in the body; focus returns to the opener |
| **GeoInfoDialog (S2)** | as above, read-only rows; one tertiary "닫기" | receipts, connection status |
| **GeoMenu (S6)** | radius 12, `surface`, `shadow-lg`; items ≥ 48 dp, `body`; destructive items last after a divider in `danger` | secondary actions only |
| **GeoSnackbar (S7)** | radius 12, `inverse`, text `body` `onInverse`, action `button` `inverseAction`; above the bottom chrome | the action is drawn; duration = `calculateRecommendedTimeoutMillis(8 s with action / 4 s without)`; one at a time |
| **GeoInlineNote** | radius 12, tone-soft, 16 dp padding, 20 dp icon, `body`, optional tertiary | help notes dismissible for the session; live errors never |
| **GeoStateBlock (B3)** | top-aligned under its section or the top bar (56 dp for full-screen states); title `title-sm`/600 + 1–2 `body` lines `textSecondary` + one action | variants: empty, error (cause + "다시 시도"), missing (way back), permission ("… 권한이 꺼져 있습니다" + "권한 허용" / "설정에서 허용"), offline (last data + strip), unsupported |
| **GeoTechInfo** | collapsed row "기술 정보" at the end of detail screens; expanded: `mono` fact rows, each with a 48 dp copy button ("복사") | exists only when developer mode **and** "화면에 기술 정보 표시" are on; no long-press-only actions |
| **GeoLogView** | `mono` selectable text in a `LazyColumn`; live line "실시간" / "보관 로그" | follows the end only while the user is at the end; after scrolling away a floating tertiary "최신 로그" appears and returns to the end |
| **GeoMapCard / GeoMapScreen / GeoRunPanel** | §5 | |
| **GeoMediaStage** | black stage; overlays on black 72 % plates; on-dark badges | camera, QR, server video |
| **Device photo** | `geo_device` at 40 / 56 / 120 dp, no frame, decorative | never the only identity cue |

Removed from the vocabulary: `StatusChip` (→ `GeoBadge`), `GeoSection(tone)` left rules, `GeoStatusTile`, `RunReceiptStrip` tiles, `ConnectionStepper` and the survey step tabs, `DeviceContextHeader`, `DataLocationTabs`, `FilterChip` filters, the navy next-action band, `EmptyState`'s refresh icon, `MapQualityBand`'s return button, top-bar refresh icons, home/folder icons in transfer bars. Existing wrappers get adapters first and are deleted after migration (§10).

### 3.9 Icons [`GeoIcons`]

- **One stroke set** drawn on a 24 dp grid with a 1.75 dp stroke, round caps and joins, no fill — the same paths as the drawing kit (`KIT.md` §4), built as `ImageVector`s with `ImageVector.Builder` + `addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(...), strokeLineWidth = 1.75f, strokeLineCap = Round, strokeLineJoin = Round)`. No new library; this also matches the web's stroke icons. The selected tab uses the filled variant of its glyph (four filled paths). `material-icons-extended` is no longer referenced by screen code and is removed after migration.
- **Sizes:** 24 dp in bars, navigation and leading row icons; 20 dp in buttons, strips, notes and chevrons; 16 dp in readiness, caption lines and figure warnings.
- **Colour:** `textSecondary` for neutral icons (`onTopBarIcon` on chrome); the tone colour for status icons; `onPrimary` inside the primary button; `primary` on tertiary actions with an icon.
- **Names (the complete set):** tabs `tabWork`, `tabDevice`, `tabTransfer`, `tabSettings` (+ filled); actions `back`, `close`, `more`, `bell`, `add`, `search`, `chevronRight`, `chevronDown`, `copy`, `refresh` (only inside "다시 시도" buttons), `send` (upload arrow), `trash`, `restore`, `play`, `pause`, `stop`, `torch`, `torchOff`, `camera`, `qr`, `myLocation`, `fitZone`, `zoomIn`, `zoomOut`, `layers`, `expandPanel`, `collapsePanel`, `eye`, `eyeOff`, `wifi`, `bluetooth`, `link` (직접 연결), `power`, `reboot`, `fan`, `terminal`, `folder`, `file`, `image`, `video`, `log`; status `success`, `warning`, `danger`, `info`, `pending`, `unknown`.
- **No decorative icons:** section titles, settings rows and state blocks carry none. A glyph carries meaning alone only for back, close, more, bell, add and the map controls, all with content descriptions.

### 3.10 Motion and haptics [`GeoMotion`]

Durations and easing from the web: 120 / 180 / 240 ms on `CubicBezierEasing(0.2f, 0f, 0f, 1f)`.

| Event | ms |
|---|---|
| press state, top-bar hairline, icon tint, tab switch (crossfade, no slide) | 120 |
| badge tone change, segmented thumb, switch thumb, menu open/close, status strip expand/collapse, primary label crossfade, progress value | 180 |
| sheet and dialog enter/exit, screen push/pop (fade + 24 dp horizontal shift, mirrored on pop), loader → app crossfade, map camera moves, device marker glide | 240 |
| geon-loader | 2400 cycle (§6.3) |

Numbers never animate; no bounce, overshoot, shimmer or pulse; nothing moves by itself except progress, the loader and the device marker; primary controls never move when a live value changes (§3.4.4). **Reduced motion** (animator duration scale 0 or "애니메이션 제거", observed while running): every duration 0, the loader shows the static full-colour symbol, map camera and marker jump, sheets appear in place. **Haptics** (system setting respected): `CONFIRM` when a start or stop is accepted, `REJECT` once per confirmed zone exit, `LONG_PRESS` on a QR read; haptics only supplement visible changes.

### 3.11 Accessibility [`GeoA11y` checks in tests]

- **Contrast:** §4.4 — text ≥ 4.5:1 on its real background, boundaries/focus/meaningful icons/map strokes ≥ 3:1; figures are `text` (≥ 11.2:1).
- **Targets:** ≥ 48 × 48 dp everywhere; field-critical actions (primary, stop, reboot, power-off, send) 52 dp; ≥ 8 dp between independent targets; action rows have two separate targets.
- **Font scale to 2.0, no clipping:** no fixed heights; reflow per the rules in §3.1, §3.5 and the stacking rule. Acceptance matrix: frames 412 × 882, 360 × 780, 320 × 640, 882 × 412 (both directions), split screen 412 × 440; font scales 1.0 / 1.1 / 1.3 / 1.5 / 2.0; screens 작업, 수집 구간, 수집 시작 sheet, 수집 중, 수집 결과, 장치 상세, 전송, 설정; long names "GEO-X-현장지원차량-2" and "신월4거리 북측 보조 구간 2차 재수집".
- **Screen readers:** section titles are headings; a row is one merged node ("신월4거리 북측 보조 구간, 할 일, 마감 10월 21일, 담당 김현우, 장치에서 350미터"); action rows expose two nodes; badges expose `stateDescription`; polite live regions on the status strip, run header (≤ once a minute), upload progress (each 10 %) and loader scene (once); every icon button has a content description; the map card speaks its caption; focus returns to the opener after every sheet, dialog and menu.
- **Alternatives for gestures:** pull-to-refresh lists also show the freshness line with a tertiary "다시 확인" once data is older than 60 s, and expose a custom accessibility action "새로 고침"; maps have 48 dp zoom −/+ buttons, "장치 위치로" and "구역 전체 보기"; the map panel has a visible expand/collapse button; technical values have copy buttons; nothing is swipe-only or long-press-only (swipe-to-trash in 수집 기록 becomes a menu action with undo).
- **Not by colour alone:** status = dot/icon + word; selected tab = filled icon + 700 + colour; segmented = thumb + weight; map = fill vs dashed edge vs solid/dashed/dotted trail + legend words.
- **Gloves and vehicles:** sticky primaries in the bottom 40 % when the viewport allows (§3.4); sliders with −/+; explicit map zoom; run status and recovery text at `body` size; timestamps may stay `caption`.
- **Orientation:** every screen works in both orientations and split screen; no orientation lock.

### 3.12 Copy rules, glossary and state vocabulary [`ui/model/Vocabulary.kt`]

**Rules.**
1. 존댓말. Statements end in "~습니다"; guidance in "~세요"; "~해 주세요" only when asking the operator to contact someone.
2. Buttons are noun phrases ("수집 시작", "다시 연결", "서버로 보내기", "완료 보고"), ideally ≤ 8 characters; a confirm repeats the action; never "확인", "예" or "OK" as a confirm label.
3. No "!", no emoji, no "…" (busy label "보내는 중", not "보내는 중…").
4. "장치" for the unit (never 장비, Jetson, 기기), "휴대폰" for the phone, "서버" for the receiving server, "GEO& Work" spelled exactly.
5. The device name comes first in alerts, confirmations, strips and readiness lines.
6. Errors give a cause category and what to do; never HTTP codes, exception names, raw server codes (e.g. `HUMAN_CHANGED` and unknown report codes), IP addresses, URLs, UUIDs or paths — those live in 기술 정보. Unknown server codes map to "GEO& Work가 보고를 받지 않았습니다. 잠시 후 다시 보고하세요." and keep the code for 기술 정보.
7. Unknown is "미확인"; stale is "마지막 응답 …", "마지막 값 기준" or "마지막 동기화 …"; never a dash or a zero.
8. Particles follow the name (을/를, 이/가, 은/는, 으로/로) with the final-consonant helper.
9. One fact per line; notes ≤ 40 characters per sentence where possible.
10. Patterns — empty: "[무엇]이 없습니다." + "[어떻게 하면] 여기에 표시됩니다." + one action; confirm: "[대상]을 [동작]할까요?" + consequence + recoverability; readiness: "[장치] · [상태] · [문장]."; field labels above the field, placeholders only as examples ("예: GEON-5G").

**Glossary.**

| Use | Never | Meaning |
|---|---|---|
| 수집 구간 | 티켓, 구간 티켓, 조사 구간 | a GEO& Work board row assigned to a 호기 |
| 수집 구역 | 범위, 지오메트리 | the ticket's geometry and its computed range |
| 지점 · 경로 · 영역 | Point · LineString · Polygon | the three feature types |
| 수집 | 조사, 실행, run | one collection run on a device |
| 작업 | 파이프라인, 태스크 | a task registered on a device |
| 장치 · 휴대폰 | 장비, Jetson, 모바일 | the unit · the phone |
| 전송 (place) · 보내기 (action) | 업로드 | moving files to the server |
| 장치 저장 | 매니페스트, FINAL | the device confirmed its output folder |
| 서버 수신 | 업로드 완료 | the server confirmed it holds the same content |
| 완료 보고 | 보고 전송 | marking the ticket complete in GEO& Work |
| 연결 방법 | 연결 문제 해결 | the ways to reach a device |
| 같은 네트워크 · 직접 연결 · 기본 연결 | LAN · Wi-Fi Direct · BLE | the three transports |
| 보정 정보 중계 | NTRIP relay, RTK relay | the phone passing RTK corrections to the device |
| 휴지통 · 영구 삭제 | 삭제 (for a reversible move) | reversible · irreversible removal |
| 등록 해제 | 장치 삭제, 잊기, 작업 삭제 | remove a device from this phone, or a task from a device |
| 정밀 위치 · 대략 위치 · 위치 없음 · 위치 미확인 | RTK FIX, RTK FLOAT, NO_FIX | position quality |
| 관찰하지 못한 시간 | 데이터 누락 | time the phone could not watch the zone |

**State vocabulary (one word, one tone).**

| Domain | Word → tone |
|---|---|
| Connection | 연결됨 → success · 기본 연결 → warning · 연결 가능 → info · 확인 중 → pending · 응답 없음 → warning · 오프라인 → neutral · 인증 필요 → danger · 권한 필요 → warning |
| Freshness (meta text, never a badge) | "방금 갱신" · "3분 전 갱신" · "16:36 갱신"; stale: "마지막 응답 3분 전"; figures add "마지막 값 기준"; server lists "마지막 동기화 16:36" |
| Sensor (from sample freshness and device availability, §1.5) | 수신 중 → success · 대기 중 → info · 지연 → warning · 미연결 → warning · 미설정 → neutral · 미확인 → neutral |
| Position quality | 정밀 위치 → success · 대략 위치 → warning · 위치 없음 → danger · 위치 미확인 → neutral |
| Zone | 위치 확인 중 → pending · 구역 안 → success · 경계 근처 → info · 구역 밖 · 35 m → warning · 위치 미확인 → neutral |
| 수집 구간 (GEO& Work names) | 할 일 → neutral · 진행 중 → info · 막힘 → warning · 완료 → success · 상태 미확인 → neutral |
| 수집 | 시작 확인 중 → pending · 수집 중 → info · 종료 확인 중 → pending · 종료 → success · 비정상 종료 → warning · 시작하지 못함 → danger · 상태 미확인 → neutral |
| 장치 저장 | 확인 중 → pending · 저장 확인 → success · 다시 확인 → warning · 미확인 → neutral |
| 전송 | 대기 중 → neutral · 파일 확인 중 → pending · 보내는 중 → info · 수신 확인 중 → pending · 수신 확인 → success · 내용 불일치 → danger · 실패 → danger · 취소됨 → neutral |
| 완료 보고 | 보고 전 → neutral · 보고 대기 → pending · 보고함 → success · 먼저 바뀜 → warning · 보고 실패 → danger |
| 작업 (task) | 대기 → neutral · 수집 중 → info · 시작 요청 중 / 중지 요청 중 / 시간 맞추는 중 → pending · 재시도 중 → warning · 실패 → danger · 미확인 → neutral |
| 보정 정보 중계 | 중계 중 → success · 연결하는 중 → pending · 꺼짐 → neutral · 오류 → danger |
| Server connection | 연결됨 → success · 다시 로그인 필요 → warning · 연결 실패 → danger · 확인 전 → neutral |
| Server job | 수신 완료 → success · 보내는 중 → info · 실패 → danger · 휴지통 → neutral · 삭제 처리 중 → pending |
| Trash item | 복원 가능 → neutral · 복원 중 → pending · 삭제 처리 중 → pending · 복원할 수 없음 → warning |

### 3.13 Deterministic formatters [`ui/model/Formats.kt`, unit-tested]

All formatters take typed values (`Long` bytes, `Duration`, `Instant` + zone `Asia/Seoul`), return Korean strings with no-break spaces between number and unit, use `RoundingMode.HALF_UP` unless noted, and map `null` to "미확인".

| Formatter | Rule | Examples |
|---|---|---|
| `size(bytes)` — storage, files, transfers (decimal SI, 1 kB = 1000 B) | < 1000 → "{n} B"; else pick the largest unit (KB, MB, GB, TB) with value ≥ 1; value < 100 → one decimal (kept even when .0); value ≥ 100 → integer; if rounding reaches 1000, promote to the next unit as "1.0 …" | 0 → "0 B" · 999 → "999 B" · 1000 → "1.0 KB" · 1,234 → "1.2 KB" · 99,949 → "99.9 KB" · 99,950 → "100 KB" · 896,000,000 → "896 MB" · 999,600,000 → "1.0 GB" · 14,700,000,000 → "14.7 GB" · 512,110,000,000 → "512 GB" · 1,800,000,000,000 → "1.8 TB" |
| `free(bytes)` | `size` + " 남음" | "512 GB 남음" |
| `memory(usedKiB, totalKiB)` (binary, as Android Settings) | each value ÷ 1024² → GB with one decimal; "{used} GB / {total} GB" | "2.0 GB / 15.1 GB" |
| `speed(bytesPerSecond)` | `size` + "/초" | "12.3 MB/초" |
| `percent(value)` (metrics) | integer, clamped 0–100; invalid → "미확인"; legitimate 0 → "0%" | "57%", "0%" |
| `progress(done, total)` | floor, so 100% only when done == total | "64%" |
| `temperature(c)` | integer °C | "54°C" |
| `clock(elapsed)` | "HH:MM:SS", hours unbounded | "00:12:34", "101:02:03" |
| `duration(d)` | < 60 s → "1분 미만"; < 60 min → "{m}분"; else "{h}시간 {m}분" (minutes omitted when 0) | "12분", "1시간 5분", "2시간" |
| `eta(d)` | "약 {duration} 남음"; < 60 s → "1분 미만 남음"; unknown → omitted | "약 4분 남음" |
| `time(instant, now)` | same day → "16:36"; previous day → "어제 16:36"; same year → "10월 19일 16:36"; other year → "2025년 9월 16일 16:36"; seconds only in run intervals ("16:36:02") | — |
| `freshness(age)` (meta only) | ≤ 15 s → "방금 갱신"; < 60 min → "{floor(min), at least 1}분 전 갱신"; else "{time} 갱신"; recomputed at most once a minute | "3분 전 갱신" |
| `distance(m)` | < 10 → 1 m steps; < 100 → 5 m; < 1000 → 10 m; else km with one decimal | "7 m", "35 m", "350 m", "1.2 km" |
| `length(m)` (route) | < 1000 → 10 m steps; else km one decimal | "1.2 km" |
| `width(bufferM)` | 2 × bufferM, integer m | "폭 20 m" |
| `area(m2)` | < 1000 → 10 m² steps; < 1,000,000 → 100 m² steps with thousands separators; else km² one decimal | "3,400 m²" |
| counts | thousands separators + counter | "1,204개", "3건", "2대", "2회", "위성 12개" |
| `coordinate(lat, lon)` | 7 decimals, lat first, tnum; only in the map panel and 기술 정보 | "37.5665000, 126.9780000" |

Sample values in drawings use these formatters (the capture's "896.0 MB" is drawn "896 MB").

---

## 4. Colour — six palettes

### 4.1 Structure

A theme is **mode × palette**. Mode = 시스템 따름 · 밝게 · 어둡게. Palette = one light palette (표준 · 네이비 · 고대비) and one dark palette (그래파이트 · 네이비 · 흐림), both always stored. In 시스템 따름 the app shows the light palette in a light configuration and the dark palette in a dark one. Defaults on first install: 시스템 따름 · 표준 · 그래파이트 (the manifest and splash defaults match these, §6).

- **표준** — the web light palette (`:root`), unchanged.
- **네이비 (밝게)** — a full theme, not a navigation preference: the content is 표준, the chrome is the web rail navy #121A3D — root and pushed top bars including the status-bar area, the bottom bar or rail, and the navigation-inset area under the bar. White/`#AEB9D9` foregrounds, `navFocus` #8FB0F0, `attention` #F08F86, light system-bar icons over navy. Screens without a bottom bar (focused flows) keep a navy top bar; their bottom inset follows the surface under it.
- **고대비** — the outdoor theme. Every surface white, hairlines #C3C9D4 (1.66:1 instead of 1.21:1), every enabled text pair ≥ 6:1 and body text ≥ 9:1, all status borders solid, text weights one step heavier (§3.6). It is the only palette validated by arithmetic for glare; the dark palettes are not described as sunlight palettes.
- **그래파이트 · 네이비 (어둡게) · 흐림** — the web `[data-dark-palette]` values, unchanged except where a control role was missing (switch, chrome tones) or failed (navigation selected state, switch track). 흐림 is the visibly different dark; 그래파이트 and 네이비 are close siblings.

**Role rules.**
1. `primary` is for the one filled button, tertiary actions, focus, progress, checked controls and the selected tab — never decoration.
2. Status hues appear only in badges, dots, status icons, strips, Focus blocks, inline notes and map overlays. **Figures stay `text`**: a value over its threshold keeps `text` and gains a 16 dp warning icon plus a `warning` detail line ("기준 80°C 초과").
3. `danger` only on destructive controls and failures. The danger outline is `danger` itself (6.00:1), never `dangerBorder`.
4. `*Border` tokens are decorative except in 고대비 (where they equal the solid tone). A meaningful status outline uses the solid tone.
5. Text fields and picker fields always carry a 1 dp `borderStrong` boundary (2 dp `primary` when focused, 2 dp `danger` in error).
6. Soft fills only behind their own tone's text or `text`. `textSubtle` only on `surface`/`sunken`/`canvas` and only for secondary information.
7. Chrome roles (`topBar*`, `nav*`, `attention`, `chrome*`) only on the top bar, bottom bar and rail. Navy is never a content surface.
8. No gradients, no tinted rows, no left rules, no translucent text.

### 4.2 Role table (final)

A cell "=" repeats the value to its left. `@ n %` is an alpha over whatever lies beneath. Roles marked *decorative* never form the only boundary of a control, a selected state or a meaningful graphic.

| Role | 표준 | 네이비 (밝게) | 고대비 | 그래파이트 | 네이비 (어둡게) | 흐림 |
|---|---|---|---|---|---|---|
| `canvas` | #EDF0F5 | = | #FFFFFF | #161719 | #13171F | #26282B |
| `surface` | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| `sunken` | #F3F5F8 | = | #F2F4F7 | #191B1D | #171B23 | #2A2B2E |
| `field` | #F1F3F7 | = | #F2F4F7 | #292A2C | #262A33 | #3A3C3F |
| `fieldPressed` | #E9ECF2 | = | #E6E9EF | #303234 | #2D323A | #424447 |
| `text` | #151B31 | = | #0B1022 | #E8E9EC | #E6E9F0 | #EBEDEF |
| `textSecondary` | #4B5570 | = | #283045 | #BDBFC4 | #BBBFC8 | #CACCD0 |
| `textSubtle` | #5F6880 | = | #3B4256 | #9EA2A7 | #9DA1AB | #B3B6BB |
| `border` (decorative) | #E7EAF0 | = | #C3C9D4 | #323336 | #2F333C | #444649 |
| `borderStrong` | #838891 | = | #4D5466 | #7E8289 | #7D828D | #90949A |
| `primary` (= `info`, `focus`, switch on, checked) | #1F52B8 | = | #163E8F | #8AADEC | #8BACEF | #9ABBF6 |
| `primaryPressed` | #1A47A0 | = | #103170 | #A8C3F4 | #A9C3F7 | #B9D2FE |
| `onPrimary` | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| `primaryTint` (= `infoSoft`, selected row) | #E7EEFB | = | #E3EBF9 | #242E40 | #242F46 | #374154 |
| `primaryTintText` | #1A47A0 | = | #103170 | #B9CFF5 | #BBCEF5 | #C1D7FD |
| `focusHalo` | #1F52B8 @ 24 % | = | #163E8F @ 32 % | #8AADEC @ 32 % | #8BACEF @ 32 % | #9ABBF6 @ 32 % |
| `success` | #22704B | = | #0E5634 | #88CCA5 | #88CCA5 | #91D6AE |
| `successSoft` | #E2F1E9 | = | #DFF0E6 | #203026 | #203026 | #314238 |
| `successBorder` (decorative; 고대비 solid) | #A9D3BD | = | #0E5634 | #376048 | #376048 | #477159 |
| `warning` | #8A5A06 | = | #5F3D00 | #E3BC7A | #E3BC7A | #EDC684 |
| `warningSoft` | #FBF0DA | = | #FAEDD3 | #332A1C | #332A1C | #453C2D |
| `warningBorder` (decorative; 고대비 solid) | #ECCA8C | = | #5F3D00 | #67522D | #67522D | #78623D |
| `danger` | #B3372C | = | #8B1E15 | #E9938C | #E9938C | #F39D95 |
| `dangerPressed` | #9A2E25 | = | #6F150E | #F0A9A3 | #F0A9A3 | #F7B2AB |
| `onDanger` | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| `dangerSoft` | #FBE8E5 | = | #FBE4E1 | #392626 | #392626 | #4B3837 |
| `dangerBorder` (decorative; 고대비 solid) | #F0B8B1 | = | #8B1E15 | #734844 | #734844 | #855854 |
| `infoBorder` (decorative; 고대비 solid) | #B9CBEF | = | #163E8F | #435B85 | #455A85 | #546C97 |
| `pending` | #3D3A86 | = | #2C2970 | #CCC8F3 | #CCC8F3 | #D5D0FC |
| `pendingSoft` | #E4E3F4 | = | #E5E4F5 | #2D2B3F | #2D2B3F | #3F3D52 |
| `pendingBorder` (decorative; 고대비 solid) | #C4C1E8 | = | #2C2970 | #565176 | #565176 | #676288 |
| `neutral` (미확인, 할 일) | #5D667B | = | #3D4456 | #ADB1B9 | #ACB1BD | #BBBFC7 |
| `neutralSoft` | #EEF0F4 | = | #ECEEF2 | #2C2E31 | #292E38 | #3D4044 |
| `neutralBorder` (decorative; 고대비 solid) | #D9DEE7 | = | #3D4456 | #46494E | #444955 | #56595F |
| `disabledFill` | #E9ECF2 | = | #E6E9EF | #292A2C | #262A33 | #3A3C3F |
| `disabledText` | #5F6880 | = | #555D70 | #9EA2A7 | #9DA1AB | #B3B6BB |
| `switchTrackOff` | #7D8598 | = | #5E6577 | #7E8289 | #7D828D | #90949A |
| `switchThumbOff` | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| `switchThumbOn` (track = `primary`) | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| `topBar` (root and pushed, incl. status-bar area) | #FFFFFF | #121A3D | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| `onTopBar` (title, back arrow, switcher name) | #151B31 | #FFFFFF | #0B1022 | #E8E9EC | #E6E9F0 | #EBEDEF |
| `onTopBarIcon` (trailing icons, switcher chevron) | #4B5570 | #AEB9D9 | #283045 | #BDBFC4 | #BBBFC8 | #CACCD0 |
| `topBarBorder` (only while content is scrolled under it) | #E7EAF0 | none | #4D5466 | #323336 | #2F333C | #444649 |
| `nav` (bottom bar, rail, and the navigation-inset area under them) | #FFFFFF | #121A3D | #FFFFFF | #0E0F11 | #0C0F17 | #1E1F22 |
| `navSelected` | #1F52B8 | #FFFFFF | #163E8F | #A8C3F4 | #A9C3F7 | #B9D2FE |
| `navUnselected` | #5F6880 | #AEB9D9 | #5A6276 | #9EA2A7 | #9DA1AB | #B3B6BB |
| `navBorder` (hairline above the bar) | #E7EAF0 | none | #4D5466 | none | none | none |
| `navFocus` (focus ring on chrome) | #1F52B8 | #8FB0F0 | #163E8F | #9BBAF0 | #9DB9F0 | #A5C3FA |
| `attention` (tab dot, bell count fill) | #D0342C | #F08F86 | #B3261E | #F08F86 | #F08F86 | #FB988F |
| `onAttention` (count digits) | #FFFFFF | #121A3D | #FFFFFF | #161719 | #13171F | #26282B |
| `chromeSuccess` (status dot on the top bar) | #22704B | #88CCA5 | #0E5634 | #88CCA5 | #88CCA5 | #91D6AE |
| `chromeWarning` | #8A5A06 | #E3BC7A | #5F3D00 | #E3BC7A | #E3BC7A | #EDC684 |
| `chromeDanger` | #B3372C | #F08F86 | #8B1E15 | #E9938C | #E9938C | #F39D95 |
| `chromeInfo` | #1F52B8 | #8FB0F0 | #163E8F | #8AADEC | #8BACEF | #9ABBF6 |
| `chromePending` | #3D3A86 | #CCC8F3 | #2C2970 | #CCC8F3 | #CCC8F3 | #D5D0FC |
| `chromeNeutral` | #5D667B | #AEB9D9 | #3D4456 | #ADB1B9 | #ACB1BD | #BBBFC7 |
| `inverse` (snackbar) | #151B31 | = | #0B1022 | #3D3E41 | #3A3E48 | #4F5155 |
| `onInverse` | #FFFFFF | = | #FFFFFF | #E8E9EC | #E6E9F0 | #EBEDEF |
| `inverseAction` | #8FB0F0 | = | #A9C3F7 | #A8C3F4 | #A9C3F7 | #B9D2FE |
| `scrim` | #121A3D @ 32 % | = | #0B1022 @ 48 % | #050608 @ 60 % | #04060E @ 60 % | #060709 @ 60 % |
| `mapPlate` (attribution and map-panel backing, opaque) | #FFFFFF | = | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |
| status-bar icons | dark | light | dark | light | light | light |
| navigation-bar icons over `nav` | dark | light | dark | light | light | light |
| navigation-bar icons over `surface` (pushed screens without a bar, sheets, scenes) | dark | dark | dark | light | light | light |
| loader base (brightness · opacity) | 1.74 · 1 | = | = | 0.9 · 0.28 | 0.9 · 0.28 | 0.96 · 0.28 |
| map raster dimming (brightness max · saturation) | — | — | — | 0.75 · −0.2 | 0.75 · −0.2 | 0.85 · −0.2 |
| splash / loader background (= `surface`) | #FFFFFF | #FFFFFF | #FFFFFF | #1E1F21 | #1B1F28 | #2F3033 |

**Controls built from the roles.**

| Control | Off / rest | On / selected | Pressed | Focused | Disabled |
|---|---|---|---|---|---|
| Switch (track 52 × 32, thumb 24, 4 dp inset) | track `switchTrackOff`, thumb `switchThumbOff` (light palettes add the web `shadow-switch`) | track `primary`, thumb `switchThumbOn` at the end | row fill `fieldPressed` | 2 dp `focus` ring, 2 dp offset, around the track | track `disabledFill`, thumb `disabledText`, reason in the row meta |
| Checkbox (20 dp box, radius 6, 2 dp stroke) | 2 dp `borderStrong` stroke, `surface` inside | `primary` fill, 2 dp `onPrimary` check | row fill `fieldPressed` | ring around the box | stroke `disabledText` at rest, fill `disabledFill` when checked |
| Radio (20 dp) | 2 dp `borderStrong` ring | 2 dp `primary` ring + 10 dp `primary` dot | row fill `fieldPressed` | ring around the circle | `disabledText` ring |
| Selected row (list–detail, choice sheets) | — | `primaryTint` fill, title stays `text`, trailing radio/check | `fieldPressed` | 2 dp inset ring | — |
| Segmented | track `field` + 1 dp `borderStrong` | thumb `surface` + 1 dp `borderStrong`, label 600 `text` | thumb `fieldPressed` | ring around the segment | labels `disabledText` |
| Map control (48 dp circle on the map) | `mapPlate` fill + `shadow-sm`, icon `textSecondary` | `primaryTint` fill, icon `primary` (e.g. "장치 따라가기" on) | `fieldPressed` | 2 dp #1F52B8 ring inside a 2 dp #FFFFFF halo (fixed, any tile) | — |
| Tab item | icon outline + label 500 `navUnselected` | filled icon + label 700 `navSelected` | 8 % `navSelected` circle behind the icon | 2 dp `navFocus` ring | — |

### 4.3 Fixed colours (not themed)

- **Map overlays** (§5): 수집 구역, 경로 and 지점 #1F52B8; trail 정밀 위치 #22704B (solid), 대략 위치 #8A5A06 (dashed 8/4), 위치 미확인 #5D667B (dotted 2/4); observation gap = no line plus an 8 dp #B3372C ring at each end; device #121A3D; phone #1F52B8. Every stroke sits on a white casing 3 dp wider than the stroke (1.5 dp each side). Raster tiles do not follow the theme, so overlays do not either; the dimming in the role table applies to the raster layer only.
- **Media stage** (camera, QR, server video): stage #000000; instruction and status plates black at 72 %, text #FFFFFF; status badges are the **on-dark badge**: an opaque #1E1F21 pill with the graphite tone text — 실시간 #88CCA5, 지연 #E3BC7A, 대기 중 #BDBFC4, 받지 못함 #E9938C.
- **Loader artwork**: the symbol keeps its own colours; only the base silhouette follows the palette (row "loader base").

### 4.4 Contrast script output over the final table

Produced by `tools/contrast.py` (`cr`, `blend`) driven by the parser in Appendix A, which reads the §4.2 table of this file. WCAG 2.x relative luminance; text ≥ 4.5, non-text ≥ 3.

| Pair | ≥ | 표준 | 네이비(밝게) | 고대비 | 그래파이트 | 네이비(어둡게) | 흐림 |
|---|---:|---:|---:|---:|---:|---:|---:|
| text / surface | 4.5 | 17.03 | 17.03 | 18.89 | 13.59 | 13.57 | 11.24 |
| text / canvas | 4.5 | 14.91 | 14.91 | 18.89 | 14.78 | 14.77 | 12.59 |
| text / field | 4.5 | 15.33 | 15.33 | 17.15 | 11.83 | 11.82 | 9.43 |
| text / fieldPressed | 4.5 | 14.39 | 14.39 | 15.54 | 10.60 | 10.61 | 8.32 |
| text / primaryTint (selected row) | 4.5 | 14.62 | 14.62 | 15.76 | 11.23 | 11.00 | 8.74 |
| textSecondary / surface | 4.5 | 7.41 | 7.41 | 13.13 | 8.96 | 8.95 | 8.21 |
| textSecondary / sunken | 4.5 | 6.79 | 6.79 | 11.92 | 9.39 | 9.36 | 8.80 |
| textSecondary / field | 4.5 | 6.67 | 6.67 | 11.92 | 7.81 | 7.80 | 6.88 |
| textSecondary / fieldPressed | 4.5 | 6.26 | 6.26 | 10.80 | 7.00 | 7.00 | 6.08 |
| textSubtle / surface | 4.5 | 5.56 | 5.56 | 10.00 | 6.42 | 6.38 | 6.49 |
| textSubtle / sunken | 4.5 | 5.09 | 5.09 | 9.08 | 6.73 | 6.67 | 6.96 |
| textSubtle / canvas | 4.5 | 4.87 | 4.87 | 10.00 | 6.99 | 6.94 | 7.27 |
| primary / surface (tertiary, link) | 4.5 | 7.10 | 7.10 | 9.91 | 7.28 | 7.26 | 6.79 |
| primary / fieldPressed (tertiary pressed) | 4.5 | 6.00 | 6.00 | 8.15 | 5.68 | 5.68 | 5.03 |
| onPrimary / primary | 4.5 | 7.10 | 7.10 | 9.91 | 7.28 | 7.26 | 6.79 |
| onPrimary / primaryPressed | 4.5 | 8.56 | 8.56 | 12.39 | 9.25 | 9.30 | 8.62 |
| primaryTintText / primaryTint | 4.5 | 7.35 | 7.35 | 10.33 | 8.64 | 8.45 | 7.04 |
| success / surface | 4.5 | 6.02 | 6.02 | 8.75 | 8.82 | 8.82 | 7.81 |
| success / successSoft | 4.5 | 5.16 | 5.16 | 7.40 | 7.42 | 7.42 | 6.32 |
| warning / surface | 4.5 | 5.92 | 5.92 | 9.75 | 9.22 | 9.22 | 8.18 |
| warning / warningSoft | 4.5 | 5.24 | 5.24 | 8.41 | 7.89 | 7.89 | 6.72 |
| danger / surface | 4.5 | 6.00 | 6.00 | 9.15 | 7.08 | 7.08 | 6.33 |
| danger / dangerSoft | 4.5 | 5.08 | 5.08 | 7.53 | 6.10 | 6.10 | 5.24 |
| danger / fieldPressed (danger-outline pressed) | 4.5 | 5.07 | 5.07 | 7.53 | 5.53 | 5.54 | 4.68 |
| info / infoSoft | 4.5 | 6.09 | 6.09 | 8.26 | 6.02 | 5.89 | 5.28 |
| pending / surface | 4.5 | 9.78 | 9.78 | 12.66 | 10.31 | 10.31 | 8.96 |
| pending / pendingSoft | 4.5 | 7.73 | 7.73 | 10.10 | 8.58 | 8.58 | 7.13 |
| neutral / surface | 4.5 | 5.75 | 5.75 | 9.72 | 7.67 | 7.68 | 7.16 |
| neutral / neutralSoft | 4.5 | 5.04 | 5.04 | 8.37 | 6.33 | 6.34 | 5.65 |
| text / successSoft | 4.5 | 14.59 | 14.59 | 15.97 | 11.44 | 11.43 | 9.10 |
| text / warningSoft | 4.5 | 15.07 | 15.07 | 16.30 | 11.62 | 11.61 | 9.24 |
| text / dangerSoft | 4.5 | 14.42 | 14.42 | 15.55 | 11.69 | 11.68 | 9.32 |
| onDanger / danger | 4.5 | 6.00 | 6.00 | 9.15 | 7.08 | 7.08 | 6.33 |
| onDanger / dangerPressed | 4.5 | 7.52 | 7.52 | 11.74 | 8.57 | 8.57 | 7.48 |
| disabledText / disabledFill | 4.5 | 4.70 | 4.70 | 5.42 | 5.60 | 5.56 | 5.44 |
| onTopBar / topBar | 4.5 | 17.03 | 16.92 | 18.89 | 13.59 | 13.57 | 11.24 |
| onTopBarIcon / topBar | 4.5 | 7.41 | 8.66 | 13.13 | 8.96 | 8.95 | 8.21 |
| navSelected / nav | 4.5 | 7.10 | 16.92 | 9.91 | 10.75 | 10.80 | 10.76 |
| navUnselected / nav | 4.5 | 5.56 | 8.66 | 6.10 | 7.47 | 7.41 | 8.10 |
| onAttention / attention | 4.5 | 4.99 | 7.24 | 6.54 | 7.67 | 7.67 | 7.03 |
| onInverse / inverse | 4.5 | 17.03 | 17.03 | 18.89 | 8.81 | 8.80 | 6.78 |
| inverseAction / inverse | 4.5 | 7.82 | 7.82 | 10.65 | 6.00 | 6.03 | 5.19 |
| textSecondary / mapPlate (attribution) | 4.5 | 7.41 | 7.41 | 13.13 | 8.96 | 8.95 | 8.21 |
| borderStrong / surface | 3 | 3.56 | 3.56 | 7.57 | 4.27 | 4.28 | 4.33 |
| borderStrong / field | 3 | 3.21 | 3.21 | 6.87 | 3.72 | 3.73 | 3.63 |
| borderStrong / fieldPressed | 3 | 3.01 | 3.01 | 6.22 | 3.34 | 3.35 | 3.20 |
| focus / surface | 3 | 7.10 | 7.10 | 9.91 | 7.28 | 7.26 | 6.79 |
| focus / canvas | 3 | 6.22 | 6.22 | 9.91 | 7.92 | 7.90 | 7.61 |
| navFocus / nav | 3 | 7.10 | 7.77 | 9.91 | 9.75 | 9.70 | 9.25 |
| navFocus / topBar | 3 | 7.10 | 7.77 | 9.91 | 8.39 | 8.35 | 7.41 |
| attention / nav (tab dot) | 3 | 4.99 | 7.24 | 6.54 | 8.20 | 8.19 | 7.84 |
| attention / topBar (bell count) | 3 | 4.99 | 7.24 | 6.54 | 7.05 | 7.05 | 6.28 |
| chromeSuccess / topBar | 3 | 6.02 | 9.05 | 8.75 | 8.82 | 8.82 | 7.81 |
| chromeWarning / topBar | 3 | 5.92 | 9.46 | 9.75 | 9.22 | 9.22 | 8.18 |
| chromeDanger / topBar | 3 | 6.00 | 7.24 | 9.15 | 7.08 | 7.08 | 6.33 |
| chromeInfo / topBar | 3 | 7.10 | 7.77 | 9.91 | 7.28 | 7.26 | 6.79 |
| chromePending / topBar | 3 | 9.78 | 10.58 | 12.66 | 10.31 | 10.31 | 8.96 |
| chromeNeutral / topBar | 3 | 5.75 | 8.66 | 9.72 | 7.67 | 7.68 | 7.16 |
| switchTrackOff / surface | 3 | 3.70 | 3.70 | 5.83 | 4.27 | 4.28 | 4.33 |
| switchTrackOff / fieldPressed | 3 | 3.12 | 3.12 | 4.79 | 3.34 | 3.35 | 3.20 |
| switchThumbOff / switchTrackOff | 3 | 3.70 | 3.70 | 5.83 | 4.27 | 4.28 | 4.33 |
| switchTrackOn / surface | 3 | 7.10 | 7.10 | 9.91 | 7.28 | 7.26 | 6.79 |
| switchThumbOn / switchTrackOn | 3 | 7.10 | 7.10 | 9.91 | 7.28 | 7.26 | 6.79 |

63 pairs × 6 palettes = 378 checks; failures: 0.

| Fixed pair | ≥ | ratio |
|---|---:|---:|
| 수집 구역 / 경로 / 지점 #1F52B8 on its white casing | 3 | 7.10 |
| trail 정밀 위치 #22704B on casing | 3 | 6.02 |
| trail 대략 위치 #8A5A06 on casing | 3 | 5.92 |
| trail 위치 미확인 #5D667B on casing | 3 | 5.75 |
| gap ring #B3372C on casing | 3 | 6.00 |
| device marker #121A3D on casing | 3 | 16.92 |
| media text #FFFFFF on the 72 per cent black plate over white imagery (#474747) | 4.5 | 9.29 |
| on-dark badge 실시간 #88CCA5 on opaque #1E1F21 | 4.5 | 8.82 |
| on-dark badge 지연 #E3BC7A on opaque #1E1F21 | 4.5 | 9.22 |
| on-dark badge 대기 중 #BDBFC4 on opaque #1E1F21 | 4.5 | 8.96 |
| on-dark badge 받지 못함 #E9938C on opaque #1E1F21 | 4.5 | 7.08 |
| on-dark badge label #FFFFFF on opaque #1E1F21 | 4.5 | 16.49 |
| map control focus ring #1F52B8 inside its 2 dp white halo | 3 | 7.10 |

13 fixed checks; failures: 0. One value changed while running it: the dark `switchThumbOff` is the palette `surface` (a light thumb on 흐림's track measured 2.60:1). Pairs not listed are decorative by rule 4 (`border` on anything: 1.02–1.66; `*Border` in non-high-contrast palettes: 1.18–2.49) and never carry meaning alone. Trail quality is also distinguished by line pattern, because the three trail colours have similar luminance on satellite imagery.

`scripts/check_slate_harmony.py` is extended to read this table (or `colors.json` generated from it) and assert the same 63 pairs and 13 fixed checks in CI.

### 4.5 Theme picker (설정 › 화면 테마)

Pushed page (P7, bar visible). Content top to bottom:

1. **미리 보기** — a 160 dp non-interactive sample drawn with the palette being shown right now: a mini top bar (title "작업", switcher "GEO-X-1"), one two-line row with the badge "할 일", one fact row "저장 공간 · 512 GB 남음", one text field labelled "예시 입력", a primary button "수집 시작", and a tab bar strip. It switches with the current mode, never animates colour.
2. **모드** — segmented "시스템 따름 | 밝게 | 어둡게" (48 dp; becomes a picker field at font scale ≥ 1.5). Caption under it only for 시스템 따름: "기기 설정에 따라 밝은 테마와 어두운 테마를 바꿉니다."
3. **밝은 테마** — section title (heading semantics) + radio group label for screen readers "밝은 테마". Radio rows (min 72 dp, descriptions wrap without limit): 56 × 40 dp swatch (radius 10, 1 dp `borderStrong`; top 28 dp `surface` with a 16 × 4 dp `text` bar and an 8 dp `primary` dot, bottom 12 dp `nav`), name `title-sm`, description `body` `textSecondary`:
   - 표준 — "GEO& Work와 같은 기본 화면입니다."
   - 네이비 — "위아래 막대를 네이비로 칠한 화면입니다."
   - 고대비 — "햇빛 아래에서 읽기 쉽도록 글자와 선을 진하게 합니다."
4. **어두운 테마** — same pattern: 그래파이트 "기본 어두운 화면입니다." · 네이비 "푸른빛이 도는 어두운 화면입니다." · 흐림 "밝기 차이를 줄인 회색 화면입니다."
5. When the group is not the one in use (mode 밝게 → the dark group; 어둡게 → the light group; 시스템 따름 → the group the system is not showing), its section carries the caption "어두운 모드에서 사용합니다." / "밝은 모드에서 사용합니다.", and choosing a row there shows the snackbar "어두운 모드에서 쓸 테마로 저장했습니다." (or "밝은 …").

Behaviour: changes apply immediately; no confirm and no colour animation. A palette change in the current mode re-themes in place. A mode change persists first (all three keys + the splash theme, §6.2), then calls `setApplicationNightMode`; the activity may be recreated and returns to this page at the same scroll position. The 설정 row "테마" reads "시스템 · 표준 / 그래파이트" (or "밝게 · 고대비").

---

## 5. Map and 수집 구역 (phase-split)

### 5.1 Rendering [`ui/map/GeoMapRenderer.kt`]

- **Engine:** the installed MapLibre Android 13.4.1 with the existing VWorld raster key; style layers on GeoJSON sources (`FillLayer`, `LineLayer`, `CircleLayer`), replacing the deprecated `Marker`/`Polyline` annotations of `ui/sensors/GnssMapScreen.kt`. No new library.
- **A local style always exists:** a `background` layer in the palette's `sunken` plus all overlay sources, built even when the VWorld key is absent (today `GnssMapScreen.kt:221` skips the map entirely). Raster layers are added only when a key exists.
- **Sources, updated independently:** `zone` (range + original features of the bound geometry revision; set once per revision), `trail` (recorded fixes; appended), `device` (one point + uncertainty; replaced on each sample without touching the others), `phone` (only while shown). The existing guard against obsolete style callbacks (a style generation counter) is kept when switching 일반 / 위성.
- **Layer order, bottom → top:** background · raster · range fill · range edge casing · range edge · route casing · route · area edge casing · area edge · points · trail casing · trail 정밀 · trail 대략 · trail 미확인 · gap rings · device uncertainty · device marker · phone halo · phone marker.
- **Styles (fixed colours, §4.3):** range fill #1F52B8 at 16 % (22 % over a dimmed raster); range edge #1F52B8 2 dp dashed 6/4 on a 5 dp white casing; route centreline #1F52B8 2 dp solid on 5 dp casing; area edge #1F52B8 2 dp solid on 5 dp casing; 지점 10 dp #1F52B8 disc with 2 dp white ring; trail 4 dp on a 7 dp white casing — 정밀 위치 #22704B solid, 대략 위치 #8A5A06 dashed 8/4, 위치 미확인 #5D667B dotted 2/4; observation gaps: no line, 8 dp #B3372C ring at each end; device 18 dp #121A3D disc, 3 dp white ring, 1 dp #121A3D outer line; device uncertainty circle radius u (§5.5), #121A3D at 8 % with a 1 dp #121A3D 40 % edge, drawn only when u ≥ 3 m; stale device position = hollow marker (white fill, 3 dp #121A3D ring); phone 14 dp #1F52B8, 3 dp white ring, accuracy halo #1F52B8 at 12 %.
- **Dark palettes** dim the 일반 raster (`rasterBrightnessMax`, `rasterSaturation` per the role table); 위성 is never dimmed.
- **Camera:** fit once on entry to the range bbox (plus the device when within 2 km), padding 24 dp plus the panel height; user pan/zoom is kept until an explicit "장치 위치로" or "구역 전체 보기". In mode 수집 중 the camera follows the device until the user pans; the "장치 위치로" control then shows off (rest) and turns following back on (selected) when tapped.
- **One live map at a time:** the card's `MapView` is stopped while the full-screen map is open or while the card is off-screen; the card has every gesture disabled and is a single navigation target.
- **States that must look different:** no geometry (no card; the zone mode is not offered) · range not computed ("구역 범위가 아직 계산되지 않았습니다." features drawn, no inside/outside) · invalid geometry ("수집 구역 정보를 읽을 수 없습니다." no overlay, cause in 기술 정보) · tiles failed ("배경 지도를 불러오지 못했습니다. 구역은 그대로 표시합니다.") · no map key ("배경 지도를 쓸 수 없습니다. 구역은 그대로 표시합니다.") · stale position (hollow marker + "마지막 위치 16:36:02") · no fix ("장치 위치를 확인할 수 없습니다."). Offline tile download is not offered.

### 5.2 지도 카드 [`GeoMapCard`]

- Content width; 200 dp tall on 수집 구간 and 수집 결과, 240 dp on 수집 중, 160 dp in compact height; radius 12; 1 dp `border` (`borderStrong` in 고대비).
- North-up, camera per §5.1; no gestures; one tap opens the full map in the matching mode; one accessibility node that speaks its caption.
- On-map badge top-start at a 12 dp inset (on-map variant): 수집 구간 — "구역 안" / "경계 근처" (nothing when outside: the distance is in the caption); 수집 중 — the zone vocabulary of §3.12; 수집 결과 — none.
- Attribution bottom-start on an opaque `mapPlate` plate: "© VWorld" `caption` `textSecondary`.
- **Caption under the card** (`caption`, outside the image), line 1 the summary "경로 1.2 km · 폭 20 m · 영역 1곳 · 지점 4곳", line 2 one position sentence — "장치가 구역에서 350 m 떨어져 있습니다." / "장치가 구역 안에 있습니다." / "장치가 구역 경계 근처에 있습니다." / "장치 위치를 확인할 수 없습니다." — or on 수집 결과 "구역 밖 기록 2회 · 1분 10초 · 관찰하지 못한 시간 2분".
- Loading: `sunken` box + "지도를 불러오는 중입니다".

### 5.3 Full-screen map [`GeoMapScreen`, route `map?mode=zone|run|route|location&ticket&run&device`]

- Focused flow: no bottom bar. Detail top bar titled by mode: "수집 구역" · "수집 중" · "수집 경로" · "장치 위치".
- **Floating controls** (12 dp inset, 8 dp apart, `mapPlate` + `shadow-sm`): top-end — segmented "일반 | 위성" and below it the 48 dp circles "장치 위치로" and "구역 전체 보기"; bottom-end above the panel — "확대" / "축소" (48 dp each, stacked). MapLibre's compass appears top-start only when the map is rotated.
- **Bottom panel** (`BottomSheetScaffold`), peek 128 dp, with a visible 48 dp expand/collapse button ("패널 펼치기" / "패널 접기") at its top-end besides the drag handle. Peek: line 1 the zone badge + quality ("구역 안 · 정밀 위치 98%"), line 2 the summary ("경로 1.2 km · 폭 20 m"). Expanded: 범례 (swatches + words, including the line patterns), 장치 위치 (coordinates, 위성 12개, 고도, 마지막 위치 시각), the switch row "휴대폰 위치 표시" (off by default; turning it on asks for location with the row as rationale; off again when the screen closes), the quality summary and the row "위치 품질 자세히" (S5).
- **Run action panel** (mode 수집 중): the peek adds the run line "수집 중 · 00:12:34" and a 52 dp danger-outline "수집 종료" that opens the same S1b dialog as `run`; after a confirmed stop the result opens (F5).
- **Compact landscape:** the panel becomes a 320 dp end-side panel collapsible to a 56 dp strip with the same expand button; the floating controls keep clear of it.
- Phone location never starts on entry (fixes `GnssMapScreen.kt:157-163`).

### 5.4 Zone data contract (with GEO& Work, via the receiver) [`data/zone/`]

Per ticket: `features` (GeoJSON FeatureCollection of Point · LineString · Polygon · MultiPolygon, WGS84, lon/lat order), `bufferM` (metres **per side** of a route; the displayed width is 2 × bufferM), `range` (Polygon or MultiPolygon, holes allowed — the union of route bands and areas, computed by GEO& Work), `bbox` (minLon, minLat, maxLon, maxLat), `lengthM`, `areaM2`, `revision` (opaque string that changes on every geometry edit, like the row `revision` the app already receives in `ServerTicket`) and `hash` (SHA-256 of the canonical geometry JSON). None of these exist yet: `ServerTicket` (`data/server/ServerModels.kt:200-212`) has no geometry field, so §5 ships behind the contract (§10, phase 6). Phase 2 of zone monitoring adds `accuracyM` to the device GNSS sample (`model/JetsonStatus.kt:54-74` has none today).

- The app never computes its own buffer. Missing `range` → features only and no inside/outside.
- Validation before use: closed rings (first = last), ≥ 4 positions per ring, longitude −180…180 and latitude −90…90 (a pair with |lat| > 90 is reported as swapped), no self-intersection check on the phone (trusted from GEO& Work). A failure is the "invalid geometry" state.
- Geometry is cached on disk with the ticket per server profile (§1.5) and shown offline with "마지막 동기화 …".
- **Binding:** the start transaction stores `geometryRevision` + `geometryHash` with the run. 수집 중 and 수집 결과 always draw and judge the bound revision; when the board revision has moved on, both show the note "수집 구역이 바뀌었습니다. 이 수집은 시작할 때의 구역으로 표시합니다."

### 5.5 Inside / outside [`ui/zone/ZoneMonitor.kt`, a pure, unit-tested function]

Inputs per GNSS sample: range (Polygon/MultiPolygon with holes), latitude, longitude, fix category (RTK 고정 / RTK 부동 / 단독 / 없음), `sampleTime` and the device's `observedAt` from the same status response (both device clock), HDOP, reported accuracy (Phase 2), and the previous state.

1. **Source and freshness:** samples come from `GnssSensorStatus` inside the device status, which the phone polls every 1 s over 같은 네트워크 and 직접 연결 (`IP_HEARTBEAT_INTERVAL_MILLIS`, `JetsonRepository.kt`); fields used: `latitude`, `longitude`, `fixType`/`rtkStatus`, `hdop`, `lastSampleAtEpochMillis`. Age = `observedAt − lastSampleAtEpochMillis` (one clock, no phone–device skew). A sample is stale when age > 10 s; the stream is stale when no new status arrived for 10 s (typical on 기본 연결). Stale or no fix → **위치 미확인** at once.
2. **Duplicates:** a sample counts once, keyed by `lastSampleAtEpochMillis`; the 1 s status repeats the same sample until the receiver produces a new one, and repeats never advance the counters.
3. **Signed distance s:** in a local equirectangular projection at the sample latitude (error < 0.5 % within 10 km): s < 0 inside (inside = within an outer ring of any polygon and outside all of its holes), s > 0 outside, |s| = distance to the nearest boundary edge, holes included; a point on a boundary has s = 0.
4. **Uncertainty u = max(category floor, reported accuracy)**: floors RTK 고정 0.5 m, RTK 부동 2 m, 단독 5 m. Without reported accuracy (Phase 1) the 단독 floor is max(5 m, 3 × HDOP), and 기술 정보 marks u as "추정".
5. **Candidate per sample:** 구역 안 when s ≤ −u; 구역 밖 when s ≥ u; 경계 근처 when |s| < u.
6. **Hysteresis:** the shown state changes only after **2 consecutive distinct fresh samples** give the same new candidate. After 위치 미확인 (or at the start of a run) the state is **위치 확인 중** until two distinct fresh samples agree.
7. **Warning:** a confirmed 구역 밖 during a run raises the warning strip ("GEO-X-1이 수집 구역을 벗어났습니다 · 35 m") and one `REJECT` haptic; 경계 근처 shows only its badge; a return removes the strip without a message. Nothing ever starts or stops by itself; starting is never blocked by position.
8. **Latency at the real interval:** a confirmed exit needs two distinct outside samples, so the delay after the device crosses the edge by more than u is between one and two sample intervals. With the status polled every 1 s and a new GNSS sample every second, the warning appears **1–2 s after the crossing (about 14–28 m at 50 km/h)**; if the receiver only produces a new sample every 2 s (the device route recorder's own window, `field_quality.py`), 2–4 s (28–56 m); at the 5 s run-telemetry interval it would be 5–10 s (70–140 m), which is why the monitor reads the 1 s status, not run telemetry. Returning clears it after the same delay. Acceptance on GEO-X-1 measures the real sample interval before the warning ships.
9. **Before a run** (수집 구간): badge and sentence only; no strip, no haptic.
10. **Starting outside:** no special case; the strip appears once two distinct samples confirm 구역 밖 (one to two sample intervals after start).

### 5.6 Phase 1 now, Phase 2 later

- **Phase 1 (this rebuild): phone-side, foreground-only.** The monitor runs while the app is visible (lifecycle STARTED) and a run is active. It drives the card badge, the full-map badge, the warning strip and the haptic. Excursions are recorded **as observed** and persisted with the run id (they survive process death): start = the first sample of the confirmed outside streak, end = the first sample of the confirmed return, plus the maximum distance. Every period without observation (app not visible, stream stale > 10 s, no fix) is recorded as a gap. 수집 결과 shows "구역 밖 기록 2회 · 1분 10초" with "관찰한 시간 기준" and "관찰하지 못한 시간 2분"; counts and durations are never presented as complete. No background notification and no "구역 이탈" alert setting.
- **Phase 2 (backend, later):** the device records run-scoped excursions against the frozen geometry revision; the app shows those events. The UI reads excursions through one interface (`ZoneEventsSource`: `PhoneObservedZoneEvents` in Phase 1, `DeviceZoneEvents` in Phase 2), so Phase 2 changes only the data source, drops the "관찰한 시간 기준" qualifier and may add a background alert.

---

## 6. Startup

### 6.1 Restore state and the first frame [`MainActivity`, `AppRestoreViewModel`]

- `AppRestoreState = Loading | Restored(devices) | Failed(cause)`. A credential-store or database exception is `Failed`, never "zero devices".
- The first frame is held by an `OnPreDrawListener` until the state leaves `Loading` or 300 ms pass. Content: `Restored(0)` → 장치 등록 scene; `Restored(n)` → 작업; `Loading` after 300 ms → the loading scene; `Failed` → the error scene ("장치 정보를 불러오지 못했습니다", cause, "다시 시도"). Nothing is composed under the loader, and no false first-run scene appears.
- Once the loading scene is visible it stays ≥ 400 ms, then crossfades to the app in 240 ms.
- Pipeline adoption (`discoverableCurrentPipeline`) and device reconciliation move out of the 장치 root destination into an app-scope coordinator that starts after `Restored`, so they run when 작업 is the start screen.
- No OS permission dialog at launch (§1.7).

### 6.2 Splash, night mode and palette persistence [`ui/theme/AppearanceStore`, `res/values*/themes.xml`]

1. **Mode and palette are separate.** `UiModeManager.setApplicationNightMode(MODE_NIGHT_NO | MODE_NIGHT_YES | MODE_NIGHT_AUTO)` (per app, API 31+) for 밝게 / 어둡게 / 시스템 따름; AUTO clears the app override so the system configuration applies (documented AOSP behaviour). The global `setNightMode` is never used. The palette is app state, not configuration.
2. **Three stable adaptive splash themes** keyed by the chosen dark palette: `Theme.GeoX.Splash.Graphite`, `Theme.GeoX.Splash.Navy`, `Theme.GeoX.Splash.Dim`. In `values/` each has `windowSplashScreenBackground` #FFFFFF (every light palette's surface); in `values-night/` #1E1F21, #1B1F28 and #2F3033. Icon = the existing symbol. The names are permanent (the system persists them).
3. `SplashScreen.setSplashScreenTheme(resId)` (API 31+) is called with the theme of the **chosen dark palette** whenever it changes — also while light mode is showing — so a system mode change while the process is dead still opens on the right colour.
4. **Order on every appearance change:** commit `theme_mode`, `theme_light`, `theme_dark` and the splash-theme id synchronously → `setSplashScreenTheme` → `setApplicationNightMode` (which may recreate the activity).
5. **First install:** the manifest theme is `Theme.GeoX.Splash.Graphite` and the post-splash `windowBackground` is the surface of the night configuration (#FFFFFF / #1E1F21), matching the defaults 시스템 따름 · 표준 · 그래파이트; the loader scene and the first Compose background use the same colour.
6. **Startup reconcile:** if the stored splash theme differs from the stored dark palette (for example after an upgrade or a restore), it is corrected for the next launch.
7. **Honest limit:** the first launch after upgrading from today's build may show the old starting window for one frame, because the system draws it before app code runs. Every later launch is correct for all six themes on API 31+. Tests include process-dead system mode changes, not only live picker changes.
8. **Migration:** the old `theme` preference maps to `theme_mode` with 표준 / 그래파이트.
9. **Recreation keeps everything:** route, scroll positions, form drafts (PreparationDraft, the send review, editors), pending command identities and the theme page position survive `recreate()`; a theme change never restarts pairing or a start request.

### 6.3 Loading and error scenes [`GeonLoader`, template P10]

- **geon-loader port** exactly per LOADER_SOURCE: one `Canvas`; the cached 340 px `geo_symbol`; layers base → second wave → main wave → shine; P = round(0.8 S), H = round(1.2 S), top = −round(0.14 S); main wave travels −3P and rises to `top` by 72 %, holds to 88 %, fades to 100 %; second wave (opacity .32) starts at P/2, 0.92 S and travels +2.5P, reaching 2·top by 66 %, fading to 100 %; shine (brightness 1.9, saturate .6, opacity .7) is a 62° band at 42–58 % of a 300 % mask moving from 100 % to 0 between 74 % and 96 % (ease-in-out); wave easing cubic-bezier(.45, .05, .35, 1); cycle 2400 ms; base silhouette grayscale with brightness/opacity from the role table.
- **Loading scene:** palette `surface`; loader 88 dp centred; 20 dp; "GEO-X" (`title`, `text`); 8 dp; "장치 정보를 불러오는 중입니다." (`body`, `textSecondary`). No ring, no logo, no bar.
- **Error scene:** static 56 dp symbol (grayscale, alpha .45); "장치 정보를 불러오지 못했습니다" (`title`); the cause in operator words (`body`, ≤ 360 dp); primary "다시 시도" (52 dp, 240 dp wide, centred).
- **Reduced motion:** the static full-colour symbol, no pulse; announced once ("장치 정보를 불러오는 중입니다").
- **Splash → scene hand-off:** the splash exit-animation listener removes the splash when the state is known; when restore completes during the splash, the app appears directly without the scene.

---

## 7. Templates and every inventory item

### 7.1 Template catalogue

Screens (P), modals and overlays (S), in-screen blocks (B). "Bar" = bottom bar or rail.

| Code | Template | Bar | Anatomy (top → bottom) | Rules |
|---|---|---|---|---|
| **P1** | 탭 루트 | yes | root top bar · strip (0–1) · device strip (작업) · Focus block (0–1) · sections · action area (전송 only) | no back; bell; pull-to-refresh + freshness line; scroll kept per tab |
| **P2** | 상세 | yes | detail top bar (⋮ optional) · strip (0–1) · H1 block (`board-title` + badge + `caption` meta, photo for devices) · sections · danger sections last (28 dp) · 기술 정보 (opt-in) · action area (0–1) | variants **P2-영수증** (수집 결과, 전송 상세), **P2-장치** (장치 상세), **P2-정보** (작업 상세, 앱 정보, server job, developer details) |
| **P3** | 준비 | yes | detail top bar · strip · H1 · context (ticket: badge, meta, map card, facts — manual: project picker, section radio rows, "새 구간") · 준비 상태 (passive check rows) · action area: readiness + "수집 시작" | never creates anything; "수집 시작" runs the preflight and opens S8; inline action in compact height |
| **P4** | 진행 | no | detail top bar · strip (zone / connection) · H1 + meta · map card 240 · figures · "마지막 확인 …" · rows · action area: readiness + danger-outline "수집 종료" | states change the H1, figures and action in place |
| **P5** | 목록 | yes (dev lists too) | detail top bar (⋮ for list actions) · segmented (0–1) · strip · rows grouped by state or day · "더 보기" row · B3 when empty | 수집 기록, 전송 기록, 서버 수신 결과, 장치 휴지통, 알림, 직접 연결, developer lists |
| **P6** | 파일 탐색 | yes | detail top bar ("장치 파일" or folder name; ⋮) · location line (tappable segments) · search field · type segmented 전체 \| 이미지 \| 영상 \| 센서 · rows by day · selection mode · action area when selected | modes: browse (장치 상세, 수집 결과), send (전송; the review is the action area), pick (developer); preview opens P8 (image, video) or P9 (text); location state kept per entry point |
| **P7** | 입력 | yes; **no** for editors | detail top bar · strip · optional H1 · fields in sections · inline validation · action area (above the IME) | settings pages keep the bar; editors (keyboard-first forms) are focused flows |
| **P8** | 전체 화면 | no | detail top bar · full-bleed stage (map, camera, scanner, image, video) · ≤ 4 floating controls · bottom panel | stage colours fixed (§4.3) |
| **P9** | 텍스트 | yes | detail top bar · picker field (log file) · live line · selectable text (`body` or `mono`) · action area (0–1) | 도움말, 연결 진단 기록, 실행 로그, text preview; GeoLogView rules |
| **P10** | 장면 | no | no top bar except a gear (first run); content top-aligned at 56 dp (first run) or centred (loading) | 장치 등록, 로딩, 로딩 오류 |
| **S1a / S1b** | 확인 대화상자 / 위험 확인 | — | §3.8 GeoConfirmDialog | copy deck §8 |
| **S2** | 정보 대화상자 | — | read-only rows + "닫기" | 서버 수신 영수증, 연결 상태 |
| **S3** | 선택 시트 | — | title, rows with radio or icon + one-line description, optional footer | 연결 방법, 장치 선택, 수집 작업 선택, 프로젝트 선택, 서버 선택, 실행 로그 선택, 캡처 저장 위치 |
| **S4** | 입력 시트 | — | title, fields, primary in the pinned footer | 등록 코드 입력, 장치 등록 (pairing), 새 프로젝트 |
| **S5** | 정보 시트 | — | title, read-only sections | 위치 품질, the map panel |
| **S6** | 메뉴 | — | §3.8 GeoMenu | secondary actions only |
| **S7** | 스낵바 | — | §3.8 GeoSnackbar | undo visible, accessibility timeout |
| **S8** | 수집 시작 확인 | — | §3.8 GeoStartSheet | the only start confirmation |
| **B1** | 상태 띠 | — | §3.8 GeoStatusStrip | incl. the shared ongoing-run entry |
| **B2** | 집중 블록 | — | §3.8 GeoFocusBlock | tab roots only, §1.6 |
| **B3** | 상태 블록 | — | §3.8 GeoStateBlock | empty, error, missing, permission, offline, unsupported |
| **OS** | 시스템 권한 대화상자 | — | Android's own dialog | always preceded by an in-app reason (§1.7); not drawn |
| **AUTO** | 자동 동작 | — | no surface; shown as states | |

### 7.2 All 138 inventory items: home · template · primary action

"Primary" is the one filled action (or a modal's confirm); "—" means the rows are the action. (dev) = developer only. Board ids refer to §11.

**장치 (8)**

| # | id | Home | Template | Primary | Notes |
|---|---|---|---|---|---|
| 1 | device-tab | 장치 root `devices` | P1 | — (Focus "다시 연결" when unreachable) | 현재 장치 (photo 56, badge, meta, 2-cell figures) · 확인할 일 (health items with 숨기기) · 다른 장치 · 주변에서 찾은 장치 (action row "등록" → `scan`); bell and "+" (→ `scan`) |
| 2 | device-first-run | 장치 등록 scene (0 devices) | P10 | QR 코드 스캔 | tertiary "장치 없이 서버 파일 보기"; gear → 설정; no bar |
| 3 | device-offline | Focus block on 작업 and 장치 | B2 | 다시 연결 | secondary "연결 방법" at once; "GEO-X-1 연결이 끊겼습니다" + "마지막 응답 5분 전" |
| 4 | device-detail | `device/{id}` | P2-장치 | — | sections 상태 · 센서 · 연결 · 데이터 · 관리 · 이 휴대폰 · 기술 정보; a non-selected device shows its snapshot and the primary "이 장치로 바꾸기"; ongoing-run strip during a run |
| 5 | device-detail-offline | `device/{id}` unreachable | P2-장치 + B1 | — (strip "다시 연결" / "연결 방법") | figures "마지막 값 기준"; sensors 미확인; Wi-Fi, files and power disabled with "연결되면 쓸 수 있습니다." |
| 6 | device-connect-method | sheet "GEO-X-1 연결 방법" | S3 | (choice rows) | 같은 네트워크에서 다시 찾기 · 장치에 직접 연결 · 블루투스로 기본 연결 · QR 코드로 다시 등록; scrolls; full-screen fallback |
| 7 | device-power-confirm | "GEO-X-1을 재부팅할까요?" / "GEO-X-1의 전원을 끌까요?" | S1b | 재부팅 / 전원 종료 | confirm disabled during a run with "수집 중에는 쓸 수 없습니다."; also the developer path (#96) |
| 8 | device-menu | 장치 상세 ⋮ | S6 | — | one item "연결 끊기"; undoable: snackbar "GEO-X-1 연결을 끊었습니다." + "다시 연결"; a running collection continues on the device |

**작업 (25)**

| # | id | Home | Template | Primary | Notes |
|---|---|---|---|---|---|
| 9 | tasks-tab | 작업 root `work` (start) | P1 | — (Focus per §1.6) | device strip · 수집 구간 (segmented "GEO-X-1 \| 전체", "완료 N개", "구간 직접 고르기") · 최근 수집 (receipt lines → `run_result` or `history_result/{historyId}`) · 장치 작업; cached list offline |
| 10 | task-home-empty | section state blocks | B3 | — | "이 호기에 배정된 수집 구간이 없습니다." / "GEO& Work에서 배정되면 여기에 표시됩니다." · "완료한 수집이 없습니다." · "이 장치에 등록된 작업이 없습니다." / "작업 등록은 관리자에게 요청하세요." · no profile: "GEO& Work 수집 구간을 보려면 서버 연결이 필요합니다." + tertiary "서버 연결" |
| 11 | task-pipeline-list | sheet "수집 작업 선택" (only when ambiguous, before the preflight) | S3 | (choice rows) | task name + state badge + "마지막 실행 …"; remembered per device; the draft keeps the ticket |
| 12 | task-list-empty | the same sheet, empty | B3 | — | "이 장치에 수집 작업이 없습니다." / "관리자에게 작업 등록을 요청하세요."; unreachable: "장치와 연결되면 작업 목록을 불러옵니다." |
| 13 | task-detail | `task/{pipelineId}` | P2-정보 | 이 작업으로 수집 준비 (or 진행 보기) | facts 상태 · 마지막 실행 · 마지막 결과 · 결과 저장 폴더 (→ files) · 보정 정보 중계 (when the task uses it); developer links move to `dev` |
| 14 | task-detail-missing | state on `task` | B3 | — | "작업 정보를 찾을 수 없습니다" + tertiary "작업으로" |
| 15 | ticket-detail | `ticket/{rowId}` (bar visible) | P3 | 수집 시작 | map card; 정보 (담당, 마감, GEO& Work 보고, 수집 작업 picker when ambiguous); 준비 상태 (연결, 저장 공간, 위치 품질, 카메라, 보정 정보 중계); disabled reasons by role and status; conflict note; inline action in compact height |
| 16 | ticket-detail-missing | state on `ticket` | B3 | — | "수집 구간을 찾을 수 없습니다" / "GEO& Work에서 지워졌거나 다른 호기로 옮겨졌을 수 있습니다." + tertiary "목록으로" |
| 17 | ticket-start-confirm | merged into S8 | S8 | 수집 시작 | line "GEO& Work 상태가 '진행 중'으로 바뀝니다." |
| 18 | survey-prepare | `prepare` ("구간 직접 고르기") | P3 | 수집 시작 | last valid project/section restored; project picker; section radio rows; "새 구간" (inline name, created at start); policy editor → `dev/pipeline/{id}/policy` |
| 19 | survey-start-confirm | S8 "{구간} 수집을 시작할까요?" | S8 | 수집 시작 | rows 장치 · 프로젝트 · 구간; check rows; "시작하면 이 수집의 프로젝트와 구간을 바꿀 수 없습니다." |
| 20 | survey-project-menu | sheet "프로젝트 선택" | S3 | (choice rows) | footer row "새 프로젝트" → name field in the sheet; created at start |
| 21 | active-run | `run` (focused) | P4 | 수집 종료 (danger-outline) | map card with zone badge; figures; rows 카메라 영상 · 지도 크게 보기 · 보정 정보 중계 |
| 22 | active-run-variants | `run` states | P4 | 시작 결과 확인 · 상태 다시 확인 · 결과 보기 (strip "다시 연결") | H1 "시작 확인 중" / "수집 중" / "연결이 끊겼습니다" / "수집 상태를 확인할 수 없습니다" / "종료 확인 중" / "수집이 끝났습니다" |
| 23 | active-run-stop-confirm | S1b "{구간} 수집을 종료할까요?" | S1b | 수집 종료 | dismiss "계속 수집"; confirmed termination opens `run_result` |
| 24 | active-run-menu | removed | — | — | 장치 선택 → root switcher; 알림 → root bell; 새로고침 → automatic + "상태 다시 확인" |
| 25 | run-result | `run_result/{runId}` | P2-영수증 | chain: 서버로 보내기 → 전송 보기 → 수신 확인 → 완료 보고 → 다음 수집 | loads by id; receipt; figures; map card (trail over the bound zone); 저장 폴더; 위치 품질; 기술 정보 › 실행 로그; ⋮ "기록을 휴지통으로" (+ undo); a disabled send shows its remedy |
| 26 | run-result-missing | state on `run_result` | B3 | — | "수집 결과를 찾을 수 없습니다" / "수집 기록에서 다시 찾아보세요." + tertiary "수집 기록" |
| 27 | run-result-ticket-report | S1a "{구간}을 완료로 보고할까요?" | S1a | 완료 보고 | also from Focus 6 and the verified transfer detail; server-only |
| 28 | run-result-menu | `run_result` ⋮ | S6 | — | "기록을 휴지통으로" |
| 29 | task-history | `history` | P5 | — | segmented 전체 \| 진행 중 \| 완료; rows → `run_result/{runId}` or `history_result/{historyId}`; row ⋮ "휴지통으로 이동"; "이전 기록 더 불러오기" (30 per page) |
| 30 | history-empty | state on `history` | B3 | — | "수집 기록이 없습니다" / "수집을 마치면 여기에 남습니다." |
| 31 | history-log (dev) | 기술 정보 › "실행 로그" on `run_result` and `history_result/{historyId}` | P9 | — | the stored log ("최근 64KB"), GeoLogView |
| 32 | quality-detail-sheet | sheet "위치 품질" | S5 | — | slices 정밀 위치 · 대략 위치 · 위치 없음 · 위치 미확인 with durations; problem intervals "16:36:02 ~ 16:37:10"; 관찰하지 못한 시간 |
| 33 | history-undo | snackbar "수집 기록을 휴지통으로 옮겼습니다." | S7 | 실행 취소 | visible action bound to the trash item |

**전송 (32)**

| # | id | Home | Template | Primary | Notes |
|---|---|---|---|---|---|
| 34 | transfers-tab | 전송 root `transfers` (current device) | P1 | 파일 보내기 (sticky) | title "전송" + switcher "GEO-X-1"; groups per F6; other devices only as attention rows; readiness "GEO-X-1 · 같은 네트워크 · 지금 보낼 수 있습니다." |
| 35 | transfer-cancel-confirm | S1b "{이름} 전송을 취소할까요?" | S1b | 전송 취소 | one dialog for root and detail; dismiss "계속 보내기" |
| 36 | data-hub | `files?mode=send` | P6 | 전송 시작 (after a selection) | the review is the action area (selection summary, 받는 서버, "전송 시작"); no "장치 \| 서버" tabs, no "업로드 목록" |
| 37 | data-empty-offline | browser states | B3 + B1 | — | "파일이 없습니다" · "검색 결과가 없습니다" / "다른 이름이나 유형으로 찾아보세요." · strip "GEO-X-1 연결이 끊겼습니다. 마지막으로 본 목록입니다." · "파일 목록을 불러오지 못했습니다" + "다시 시도" |
| 38 | data-delete-confirm | S1a "{이름}을 휴지통으로 옮길까요?" | S1a | 휴지통으로 이동 | "GEO-X-1 휴지통에서 복원할 수 있습니다." |
| 39 | data-menu | browser ⋮ | S6 | — | "선택", "휴지통"; with a selection "휴지통으로 이동" |
| 40 | data-undo | snackbar "{이름}을 휴지통으로 옮겼습니다." | S7 | 실행 취소 | not restorable: "…옮겼지만 이 항목은 복원할 수 없습니다." (no action) |
| 41 | device-storage-route | `files?mode=browse&root&path` "장치 파일" | P6 | — | location line, search (root only), type picker 전체 · 이미지 · 영상 · 센서 · 로그, day groups; location kept per entry point |
| 42 | device-file-preview-empty | preview | P8 / P9 / B3 | — | image (zoomable) and UTF-8 text up to 12 MiB; otherwise "미리 볼 수 없는 파일입니다" / "이미지와 텍스트 파일만 앱에서 볼 수 있습니다." |
| 43 | server-data | `server` "서버 수신 결과" | P5 | — | profile line + "마지막 동기화 …"; segmented 수신 결과 \| 휴지통; "이전 수신 결과 더 불러오기"; job detail (P2-정보: files, "서버 수신 영수증", ⋮ "서버 휴지통으로 이동"); video in P8 (재생, "다시 시도"); trash rows "서버 작업 복원" / "복원할 수 없음" / "삭제 처리 중"; "전체 128개 중 100개를 표시합니다." |
| 44 | server-no-profile | state on `server` | B3 | — | "서버 연결 정보가 없습니다" / "관리자에게 서버 연결 정보를 받아 설정하세요." + tertiary "서버 연결" (→ `settings/server`); credentials refused: "다시 로그인해야 합니다" + "다시 로그인" |
| 45 | server-job-trash-confirm | S1a "{이름}을 서버 휴지통으로 옮길까요?" | S1a | 휴지통으로 이동 | "서버 휴지통에서 복원할 수 있습니다." |
| 46 | server-empty-trash-confirm (dev) | S1b over `dev/server-trash` | S1b | 영구 삭제 | daily 휴지통 segment links there in developer mode only; "표시된 {N}개" wording kept |
| 47 | server-remove-profile-confirm (dev) | S1b over `dev/server-profiles` | S1b | 프로필 삭제 | |
| 48 | server-receipt | S2 "서버 수신 영수증" | S2 | 닫기 | badge 내용 일치 / 내용 불일치; rows 파일 · 용량 · 완료 · 검증 시각; hashes in 기술 정보 |
| 49 | server-detail-empty | states | B3 | — | "수신 결과가 없습니다" · "이 폴더에 파일이 없습니다" · "앱에서 볼 수 없는 파일입니다" · "휴지통이 비어 있습니다" · unknown mutation: "처리 결과를 확인하지 못했습니다. 같은 작업을 반복하지 말고 목록을 다시 확인하세요." + "다시 확인" |
| 50 | server-undo | snackbar "서버 휴지통으로 옮겼습니다." | S7 | 실행 취소 | |
| 51 | server-proxy-storage (dev) | `dev/server-proxy` "장치 경유 서버 조회" | P6 | — | server picker field + sessions → browser; deletion stays off |
| 52 | proxy-empty (dev) | states | B3 | — | "장치를 같은 네트워크나 직접 연결로 연결하면 조회할 수 있습니다." · "서버 데이터가 없습니다" · "빈 폴더입니다" |
| 53 | proxy-delete-confirm (dev) | S1b "서버 데이터를 삭제할까요?" | S1b | 영구 삭제 | unreachable while `deletionEnabled = false` (kept) |
| 54 | proxy-target-menu (dev) | sheet "서버 선택" | S3 | (choice rows) | |
| 55 | upload-confirm | `send?root&path&runId` "전송 확인" | P7 | 전송 시작 | H1 = section or folder; facts 파일 · 용량 · 보내는 장치 · 수집; 받는 서버 radio rows (one target = fact); "장치 원본은 그대로 보관됩니다." |
| 56 | upload-no-target | state on `send` and the browser action area | B3 | — | "받을 서버가 없습니다" / "관리자에게 받는 서버 설정을 요청하세요."; developer mode adds one link row "개발자 도구에서 업로드 서버 설정" |
| 57 | upload-progress | `transfer/{jobId}` | P2-영수증 | by state: — (sending, danger-outline "전송 취소") · 다시 보내기 · 수신 확인 · 완료 보고 · 수집 결과 / 서버에서 보기 | 관리 block "장치 원본 정리" / "장치 원본 복원"; ⋮ "기록 삭제" when finished |
| 58 | upload-cancel-confirm | = #35 | S1b | 전송 취소 | |
| 59 | upload-source-delete-confirm | S1a "장치 원본을 휴지통으로 옮길까요?" | S1a | 휴지통으로 이동 | "서버와 다시 대조한 뒤 GEO-X-1의 원본 폴더를 휴지통으로 옮깁니다. 휴지통에서 복원할 수 있습니다." |
| 60 | upload-queue | `transfers/log` "전송 기록" | P5 | — | segmented 전체 \| 실패 \| 완료 \| 취소; rows → `transfer/{jobId}`; row ⋮ "기록 삭제" |
| 61 | upload-queue-empty | state | B3 | — | "전송 기록이 없습니다" / "파일을 보내면 여기에 남습니다." |
| 62 | upload-record-delete-confirm | S1a "전송 기록을 삭제할까요?" | S1a | 기록 삭제 | "목록에서만 지웁니다. 장치 원본과 서버의 데이터는 그대로입니다." |
| 63 | local-trash | `device/{id}/trash` "휴지통" | P5 | — | rows: name · 분류 (장치 데이터 / 업로드 원본 / 수집 기록) · state; trailing "원래 위치로 복원" (action row) or "복원할 수 없음"; ⋮ "휴지통 비우기"; unsupported note |
| 64 | local-trash-empty | state | B3 | — | "휴지통이 비어 있습니다" / "휴지통으로 옮긴 장치 파일과 수집 기록이 여기에 표시됩니다." |
| 65 | local-trash-purge-confirm | S1b "GEO-X-1 휴지통을 비울까요?" | S1b | 영구 삭제 | "표시된 12개 항목을 영구 삭제합니다. 되돌릴 수 없습니다." |

**설정 (21)** — the inventory files several device screens here; their home is the 장치 tab.

| # | id | Home | Template | Primary | Notes |
|---|---|---|---|---|---|
| 66 | settings-tab | 설정 root | P1 | — | 화면 (테마) · 알림 (알림 기준, 알림 권한 row when off) · 서버 (서버 연결) · 정보 (도움말, 앱 정보) · 고급 (developer mode: 개발자 도구) |
| 67 | theme-dialog | `settings/theme` "화면 테마" | P7 | — (applies on selection) | §4.5 |
| 68 | about-dialog | `settings/about` "앱 정보" | P2-정보 | — | 앱 이름 GEO-X · 버전 1.18.0 (seven taps) · 글꼴 Pretendard (SIL OFL) |
| 69 | help-dialog | `settings/help` "도움말" | P9 | — | sections 수집 · 전송 · 연결 문제 |
| 70 | alert-settings | `settings/alerts` "알림 기준" | P7 | — (saves on change) | 장치 상태 (저장 공간 switch + slider 50–99 % "85% 이상"; 장치 온도 switch + slider 40–110 °C "80°C 이상"; −/+ steppers) · 작업 (작업 시작, 오류로 끝남) · 전송 (전송 시작, 전송 끝남); note "기준을 넘을 때마다 한 번만 알립니다."; no tabs, no diagnostics button |
| 71 | notification-rationale | block at the top of 알림 기준 + the 설정 row | B3 | 권한 허용 | "알림 권한이 꺼져 있습니다" / "켜 둔 알림을 받으려면 권한을 허용하세요." |
| 72 | notification-prompt | Android dialog | OS | (허용) | |
| 73 | network-settings | `device/{id}/wifi` "장치 Wi-Fi" | P7 | 적용 (selected network) | current network; 주변 네트워크; the selected row expands with a password field; "숨겨진 네트워크 추가" row (one place) |
| 74 | wifi-scan-rationale | block in 장치 Wi-Fi | B3 | 권한 허용 | "주변 네트워크를 찾으려면 위치 권한이 필요합니다." |
| 75 | wifi-scan-prompt | Android dialog | OS | (허용) | |
| 76 | network-menu | 장치 Wi-Fi ⋮ | S6 | — | one item "연결 상태" |
| 77 | connection-info | S2 "연결 상태" | S2 | 닫기 | 휴대폰 Wi-Fi · 장치 Wi-Fi · "같은 네트워크입니다." / "서로 다른 네트워크입니다." |
| 78 | wifi-apply-confirm | S1a "GEO-X-1을 {SSID}에 연결할까요?" | S1a | 적용 | "적용하면 지금 연결이 끊길 수 있습니다." |
| 79 | sensors-route-unreachable | 장치 상세 › 센서 (route redirects) | P2-장치 section | — | rows 카메라 · 위치(GNSS) · 움직임(IMU) with "1920 × 1080", "위성 12개 · 정밀 위치" |
| 80 | gnss-map | `map?mode=location` (also zone, run, route) | P8 | — (run mode: 수집 종료) | §5.3 |
| 81 | gnss-details-sheet | the map's persistent panel | S5 (persistent) | — | peek 128, visible expand/collapse |
| 82 | gnss-location-rationale | the switch row "휴대폰 위치 표시" | B3 (inline) | — (the switch) | "휴대폰 위치를 보려면 위치 권한이 필요합니다." / provider off: "휴대폰 위치 서비스가 꺼져 있습니다." + "위치 켜기" |
| 83 | gnss-location-prompt | Android dialog | OS | (허용) | |
| 84 | camera-preview | `camera` "카메라" | P8 | 캡처 (64 dp round shutter) | on-dark badge 실시간 / 지연; "1920 × 1080"; tertiary "저장 위치" |
| 85 | camera-preview-states | states | P8 | — (error: 다시 시도) | "카메라 영상을 기다리고 있습니다" · "지연 · 마지막 수신 16:36:02" · "영상을 받지 못했습니다" |
| 86 | camera-capture-options | sheet "캡처 저장 위치" | S3 | 캡처 | checkbox rows "장치에 저장" / "휴대폰 갤러리에 저장"; remembered |

**개발자 도구 (24)** — all under 설정 › 고급 › 개발자 도구 (`dev`).

| # | id | Home | Template | Primary | Notes |
|---|---|---|---|---|---|
| 87 | developer-mode-unlock | 앱 정보 › 버전 ×7 | (gesture) + S7 | — | countdown snackbars from the fourth tap |
| 88 | upload-target-settings (dev) | `dev/upload-targets` "업로드 서버" | P5 | — | "+" → editor; rows name + address (`mono`) |
| 89 | upload-targets-empty (dev) | state | B3 | — | "등록된 업로드 서버가 없습니다" + tertiary "서버 추가" |
| 90 | upload-target-delete-confirm (dev) | S1b "{서버}를 삭제할까요?" | S1b | 서버 삭제 | "GEO-X-1에 저장된 서버 주소와 접근 토큰을 지웁니다." |
| 91 | upload-target-editor (dev) | `dev/upload-targets/edit` (focused) | P7 | 저장 | 서버 ID · 표시 이름 · HTTPS 주소 · 접근 토큰 (masked, never shown again); danger-outline "서버 삭제" when editing |
| 92 | developer-home (dev) | `dev` "개발자 도구" | P5 | — | switches 개발자 모드 · 화면에 기술 정보 표시; 장치 (원격 터미널, 팬 제어, 네트워크 주소, 카메라·GPS 테스트) · 수집 작업 (작업 관리, 작업 등록) · 서버·전송 (업로드 서버, 서버 프로필, 서버 휴지통 비우기, 장치 경유 서버 조회) · 진단 (연결 진단 기록) |
| 93 | developer-menu (dev) | removed | — | — | "원격 터미널" is a hub row → `dev/terminal` (focused: command field, "명령 실행", output `mono`, "단일 명령 · 최대 15초 · 출력 64KB") |
| 94 | remote-command-confirm (dev) | S1b "GEO-X-1에서 명령을 실행할까요?" | S1b | 실행 | command in `mono`; "장치의 수집 사용자 권한으로 실행합니다." |
| 95 | admin-tools (dev) | removed; parts in `dev/*` and 장치 상세 | — | — | 팬 → `dev/fan` (segmented 자동 \| 수동, slider 20–100 % step 10 from `minimumManualPercent`, "적용", "자동 온도 제어 · 3,200 RPM", 확인 중 / 사용할 수 없음 / 오류 + 다시 시도); 지표 → 장치 상세; 네트워크 → 장치 Wi-Fi and `dev/network`; 등록 관리 → 장치 상세 › 등록 해제 |
| 96 | admin-power-confirm (dev) | = #7 | S1b | 재부팅 / 전원 종료 | no device UUID in the dialog |
| 97 | connection-diagnostics (dev) | `dev/diagnostics` "연결 진단 기록" | P9 | 진단 ZIP 저장 | secondary "지금 시점 표시"; "기록 방식" disclosure |
| 98 | legacy-dashboard-unrouted | retired | — | — | health → 장치 › 확인할 일; multiple runs → Focus 4b; fan → `dev/fan`; sensor shortcuts → 장치 상세 |
| 99 | pipeline-detail-dev (dev) | `dev/pipeline/{id}` "작업 관리" | P2-정보 | by state: 수집 준비 · 중지 요청 · 다시 시작 | facts (state, run, log, exit code, preflight, entrypoint, config, revision); switch "부팅 시 실행"; rows 실행 로그 · 작업 설정 · 수집 정책 · 저장 결과 · 보정 정보 중계 (raw bytes in 기술 정보); danger-outline "등록 해제" |
| 100 | pipeline-editor (dev) | `dev/pipeline/new` "작업 등록" (focused) | P7 | 작업 등록 | 표시 이름, 작업 폴더 picker, discovered entry (`mono` note), switch "부팅 시 자동 실행" |
| 101 | pipeline-picker (dev) | `dev/picker` "폴더 선택" | P6 (pick) | 이 폴더 선택 | location line instead of a path subtitle |
| 102 | pipeline-logs (dev) | `dev/pipeline/{id}/logs` "실행 로그" | P9 | — | log picker; live line; GeoLogView with "최신 로그" |
| 103 | pipeline-picker-empty (dev) | state | B3 | — | "선택할 폴더가 없습니다" / error + "다시 시도" |
| 104 | pipeline-logs-empty (dev) | state | B3 | — | "저장된 실행 로그가 없습니다" · "로그 내용이 없습니다" |
| 105 | pipeline-config-empty (dev) | state | B3 | — | "바꿀 수 있는 설정 값이 없습니다" |
| 106 | pipeline-log-menu (dev) | sheet "실행 로그 선택" | S3 | (choice rows) | start time + size + "현재 실행" / "최근 실행" / "보관 로그" |
| 107 | pipeline-config (dev) | `dev/pipeline/{id}/config` "작업 설정" (focused) | P7 | 설정 저장 | "장치 설정 다시 불러오기" tertiary when the draft needs review; `dev/pipeline/{id}/policy` "수집 정책" uses the same template (카메라 · GNSS · IMU 필수/선택/사용 안 함, 최소 여유 공간, 예상 결과, 결과 저장소, 상대 경로, 파일 패턴; primary "정책 저장") |
| 108 | pipeline-remove-confirm (dev) | S1b "{작업} 등록을 해제할까요?" | S1b | 등록 해제 | |
| 109 | pipeline-reload-confirm (dev) | S1a "장치 설정을 다시 불러올까요?" | S1a | 다시 불러오기 | dismiss "초안 유지" |
| 110 | pipeline-config-reach (dev) | access path | — | — | 설정 › 고급 › 개발자 도구 › 작업 관리 › {작업} › 작업 설정 |

**등록/연결 (19)**

| # | id | Home | Template | Primary | Notes |
|---|---|---|---|---|---|
| 111 | onboarding | merged into the 장치 등록 scene; "+" opens `scan` directly | P10 | QR 코드 스캔 | |
| 112 | connection-hub-unrouted | retired | — | — | discovered endpoints → 장치 › 주변에서 찾은 장치; permission banner → #115; direct-connect confirm → #114; "장치 연결 없이 서버 파일 보기" → 장치 등록 scene |
| 113 | connection-hub-no-device | = 장치 등록 scene | P10 | QR 코드 스캔 | |
| 114 | connection-direct-confirm | S1a "GEO-X-1에 직접 연결할까요?" | S1a | 직접 연결 | "장치의 공유기 연결과 진행 중인 전송이 끊길 수 있습니다." |
| 115 | local-network-rationale | device strip / 장치 root permission block | B3 | 권한 허용 | "같은 네트워크의 장치를 찾으려면 로컬 네트워크 권한이 필요합니다." |
| 116 | local-network-prompt | Android dialog | OS | (허용) | never at launch |
| 117 | device-list-unrouted | retired | — | — | Bluetooth link → 연결 방법 › "블루투스로 기본 연결"; reconnect → "다시 연결"; forget → #120; add → `scan` |
| 118 | bluetooth-rationale | the pairing sheet text / the connect-sheet row | B3 (inline) | 권한 허용 | "가까운 장치를 찾으려면 블루투스 권한이 필요합니다." |
| 119 | bluetooth-prompt | Android dialog | OS | (허용) | at "등록" or "블루투스로 기본 연결" |
| 120 | forget-device-confirm | S1b "GEO-X-1 등록을 해제할까요?" from 장치 상세 › 이 휴대폰 | S1b | 등록 해제 | §8 |
| 121 | qr-scanner | `scan` "QR 코드 스캔" (focused) | P8 | — (automatic) | 240 dp frame; "장치 본체의 QR 코드를 사각형 안에 맞추세요."; torch; "코드 직접 입력" |
| 122 | camera-rationale | block on the stage | B3 | 권한 허용 / 설정에서 허용 | "QR 코드를 읽으려면 카메라 권한이 필요합니다." + tertiary "코드 직접 입력" |
| 123 | camera-permission-prompt | Android dialog | OS | (허용) | |
| 124 | qr-manual-entry | sheet "등록 코드 입력" | S4 | 다음 | field "등록 코드" (multi-line, ASCII), helper "QR 아래에 있는 전체 코드를 입력하세요."; invalid → error in place |
| 125 | pairing-confirm | sheet "장치 등록" over the frozen scanner | S4 | 등록 | photo 40 · "GEO-X-1" · "등록 코드 •••• 4DE0"; secondary "다시 스캔" |
| 126 | pairing-progress-error | the same sheet | S4 | 다시 시도 (error) | one changing line + 4 dp bar + "취소"; mapped causes |
| 127 | wifi-direct | `device/{id}/direct` "직접 연결" | P5 | — (tap a device row) | note "공유기 없이 휴대폰과 장치를 바로 연결합니다. 서버로 보내기는 같은 네트워크에서만 됩니다."; peers with 연결됨 / 연결 승인 대기 중 / 연결 가능 / 이전 연결 실패 / 현재 연결할 수 없음 / 상태 확인 중 |
| 128 | wifi-direct-permission-empty | states on `direct` | B3 | 권한 허용 · 다시 찾기 · 다시 확인 · 연결 취소 | separate states: 권한 필요 · 찾는 중 · 찾은 장치 없음 · 이전 연결 정리 중 / 연결하는 중 (+ "연결 취소") · 장치 확인 중 (API) · 확인 실패 + "장치 다시 확인" · 지원하지 않음 — a joined group is not "연결됨" until the API answers |
| 129 | nearby-wifi-prompt | Android dialog | OS | (허용) | |

**알림 (3)**

| # | id | Home | Template | Primary | Notes |
|---|---|---|---|---|---|
| 130 | alerts | `alerts` "알림" (pushed on the current tab) | P5 | — | grouped by day; row: tone icon · device-first title ("GEO-X-1 온도가 82°C입니다") · message · time; new-this-visit dot; trailing 48 dp "삭제"; ⋮ "모두 지우기"; no "모두 읽음"; tap → temperature/storage → 장치 상세, task → `task` or `run_result`, upload → `transfer/{jobId}` |
| 131 | alerts-empty | state | B3 | — | "알림이 없습니다" / "장치 상태와 수집, 전송 알림이 여기에 표시됩니다." |
| 132 | alerts-clear-confirm | S1b "알림을 모두 지울까요?" | S1b | 모두 지우기 | "지운 알림은 되돌릴 수 없습니다." |

**공통 (6)**

| # | id | Home | Template | Primary | Notes |
|---|---|---|---|---|---|
| 133 | startup-loading | loading and error scenes | P10 | — (error: 다시 시도) | §6 |
| 134 | startup-permission-prompt | removed at launch; device strip "GEO-X-1 · 연결 권한이 필요합니다" | B3 / OS | 권한 허용 | one dialog for exactly the missing set |
| 135 | startup-auto-discovery | automatic; device strip 확인 중 → 연결됨 | AUTO | — | |
| 136 | startup-ticket-report-retry | automatic outbox; "보고 대기" / "보고 실패" on result and ticket rows | AUTO | — ("보고 실패" row: tertiary "다시 보고") | |
| 137 | global-connection-banner | status strip under the top bar | B1 | — (strip "다시 연결", "연결 방법") | never navigates away |
| 138 | no-other-dialog-types | none of DatePicker, TimePicker, Toast; new: `BottomSheetScaffold` (map), `PullToRefreshBox` (lists) | — | — | both in Material 3 1.3 |

### 7.3 New surfaces (not in the inventory)

지도 카드 (#15, #21, #25) · `map` modes 수집 구역 / 수집 중 / 수집 경로 · 장치 선택 sheet · 장치 › 확인할 일 and 주변에서 찾은 장치 · 전송 › 보낼 수집 · S8 수집 시작 확인 · `history_result/{historyId}` · `settings/server` and the 다시 로그인 sheet · `dev/terminal`, `dev/fan`, `dev/network`, `dev/sensor-test`, `dev/pipeline/{id}/policy`, `dev/server-profiles`, `dev/server-trash` · 기술 정보 (opt-in) · 화면 테마 page.

**서버 연결 (`settings/server`, operator page, P2-정보 + action area).** Rows: 상태 (badge 연결됨 / 다시 로그인 필요 / 연결 실패 / 확인 전, meta "마지막 확인 16:36"), 서버 (display name), 환경 (개발 / 시험 / 운영), 직원 (employee id), 프로젝트 (project id). Primary "연결 확인"; when the server refused the credentials the primary is "다시 로그인", which opens the S4 sheet "다시 로그인" (field "직원 토큰", masked, helper "관리자에게 받은 토큰을 입력하세요.", primary "다시 로그인"; the token is cleared from memory after saving and never shown). Failures use operator words ("서버 인증서를 확인할 수 없습니다. 관리자에게 문의해 주세요." · "직원 인증이 거절되었습니다. 다시 로그인하세요." · "이 계정에는 이 프로젝트 권한이 없습니다. 관리자에게 문의해 주세요." · "서버에 연결하지 못했습니다. 인터넷 연결을 확인하고 다시 시도하세요."). No profile: B3 "서버 연결 정보가 없습니다" / "관리자에게 서버 연결 정보를 받아 설정하세요." Developer mode adds nothing here; URL, environment, ids, new profiles and deletion are on `dev/server-profiles` (form fields 프로필 ID · 표시 이름 · 환경 segmented 개발 \| 시험 \| 운영 · HTTPS 서버 주소 · 직원 ID · 프로젝트 ID · 직원 토큰; primary "암호화해 저장"; "이 프로필로 연결"; danger-outline "프로필 삭제").

---

## 8. Final confirmation copy deck

Title / body / buttons (dismiss · confirm). S1a = filled primary confirm; S1b = danger-filled confirm; S8 = the start sheet. "{구간}" is the ticket or section title, "{이름}" the section label or folder name, "{N}" a count; particles come from the final-consonant helper. A confirm whose precondition is false stays disabled and the body says why (last column).

| Dialog | Type | Title | Body | Buttons | Disabled when (body adds) |
|---|---|---|---|---|---|
| 수집 시작 | S8 | "{구간} 수집을 시작할까요?" | ticket line "{구간} · {프로젝트} · GEO-X-1" (manual: rows 장치 · 프로젝트 · 구간); zone line ("구역 안" / "장치가 구역에서 350 m 떨어져 있습니다."); 시작 전 점검 rows; "GEO& Work 상태가 '진행 중'으로 바뀝니다." (ticket only); "시작하면 이 수집의 프로젝트와 구간을 바꿀 수 없습니다." | 취소 · 수집 시작 | a check failed ("점검을 통과해야 시작할 수 있습니다."); the ticket changed ("GEO& Work에서 이 구간이 바뀌었습니다. 다시 확인하세요.") |
| 수집 종료 | S1b | "{구간} 수집을 종료할까요?" | "GEO-X-1에서 진행 중인 수집을 종료합니다. 종료되면 장치에 저장된 결과를 확인합니다." | 계속 수집 · 수집 종료 | device unreachable ("연결되면 종료할 수 있습니다.") |
| 완료 보고 | S1a | "{구간}을 완료로 보고할까요?" | "GEO& Work의 상태가 '완료'로 바뀝니다. 다시 수집하려면 GEO& Work에서 상태를 바꿔야 합니다." | 취소 · 완료 보고 | server unreachable ("서버에 연결되면 보고할 수 있습니다.") |
| 재부팅 | S1b | "GEO-X-1을 재부팅할까요?" | "재부팅하는 동안 연결이 끊깁니다. 진행 중인 전송은 멈춥니다." | 취소 · 재부팅 | a run is active ("수집 중에는 재부팅할 수 없습니다.") |
| 전원 종료 | S1b | "GEO-X-1의 전원을 끌까요?" | "다시 켜려면 장치의 전원 버튼을 눌러야 합니다. 진행 중인 전송은 멈춥니다." | 취소 · 전원 종료 | a run is active ("수집 중에는 전원을 끌 수 없습니다.") |
| 등록 해제 | S1b | "GEO-X-1 등록을 해제할까요?" | "이 휴대폰에 저장된 인증 정보만 지웁니다. 장치의 파일과 설정은 그대로입니다. 다시 쓰려면 QR 코드로 다시 등록해야 합니다." | 취소 · 등록 해제 | — |
| 전송 취소 | S1b | "{이름} 전송을 취소할까요?" | "보낸 일부 데이터는 서버에 남을 수 있고, 다시 보내면 처음부터 보냅니다." | 계속 보내기 · 전송 취소 | — |
| 장치 원본 정리 | S1a | "장치 원본을 휴지통으로 옮길까요?" | "서버와 다시 대조한 뒤 GEO-X-1의 원본 폴더를 휴지통으로 옮깁니다. 휴지통에서 복원할 수 있습니다." | 취소 · 휴지통으로 이동 | the receipt no longer matches ("서버 수신 내용이 바뀌어 정리할 수 없습니다.") |
| 장치 파일 휴지통 | S1a | "{이름}을 휴지통으로 옮길까요?" | "GEO-X-1 휴지통에서 복원할 수 있습니다." | 취소 · 휴지통으로 이동 | — |
| 장치 휴지통 비우기 | S1b | "GEO-X-1 휴지통을 비울까요?" | "표시된 {N}개 항목을 영구 삭제합니다. 되돌릴 수 없습니다." | 취소 · 영구 삭제 | — |
| 전송 기록 삭제 | S1a | "전송 기록을 삭제할까요?" | "목록에서만 지웁니다. 장치 원본과 서버의 데이터는 그대로입니다." | 취소 · 기록 삭제 | — |
| 서버 휴지통 이동 | S1a | "{이름}을 서버 휴지통으로 옮길까요?" | "서버 휴지통에서 복원할 수 있습니다." | 취소 · 휴지통으로 이동 | — |
| 서버 휴지통 비우기 (dev) | S1b | "서버 휴지통을 비울까요?" | "표시된 {N}개 항목을 서버에서 영구 삭제합니다. 되돌릴 수 없습니다." + when more exist: "전체 {M}개 중 표시된 {N}개만 지웁니다." | 취소 · 영구 삭제 | the server does not support it (the action is absent) |
| 서버 프로필 삭제 (dev) | S1b | "{프로필}을 삭제할까요?" | "이 휴대폰에 저장된 서버 주소와 직원 인증 정보, 저장된 목록을 지웁니다. 서버의 데이터는 그대로입니다." | 취소 · 프로필 삭제 | — |
| 장치 경유 서버 데이터 삭제 (dev) | S1b | "서버 데이터를 삭제할까요?" | "선택한 수신 결과를 서버에서 영구 삭제합니다. 되돌릴 수 없습니다." | 취소 · 영구 삭제 | `deletionEnabled = false` (route stays off) |
| 장치 Wi-Fi 변경 | S1a | "GEO-X-1을 {SSID}에 연결할까요?" | "적용하면 지금 연결이 끊길 수 있습니다." | 취소 · 적용 | — |
| 직접 연결 | S1a | "GEO-X-1에 직접 연결할까요?" | "장치의 공유기 연결과 진행 중인 전송이 끊길 수 있습니다." | 취소 · 직접 연결 | — |
| 알림 모두 지우기 | S1b | "알림을 모두 지울까요?" | "지운 알림은 되돌릴 수 없습니다." | 취소 · 모두 지우기 | — |
| 업로드 서버 삭제 (dev) | S1b | "{서버}를 삭제할까요?" | "GEO-X-1에 저장된 서버 주소와 접근 토큰을 지웁니다." | 취소 · 서버 삭제 | — |
| 원격 명령 (dev) | S1b | "GEO-X-1에서 명령을 실행할까요?" | the command in `mono`; "장치의 수집 사용자 권한으로 실행합니다." | 취소 · 실행 | device unreachable or a command is running |
| 작업 등록 해제 (dev) | S1b | "{작업} 등록을 해제할까요?" | "GEO-X-1의 실행 서비스가 멈춥니다. 저장된 실행 기록과 측정 파일은 그대로입니다." | 취소 · 등록 해제 | — |
| 작업 설정 다시 불러오기 (dev) | S1a | "장치 설정을 다시 불러올까요?" | "작성 중인 내용을 장치의 현재 설정으로 바꿉니다." | 초안 유지 · 다시 불러오기 | — |

No confirmation (undo instead): 연결 끊기 (snackbar "GEO-X-1 연결을 끊었습니다." + "다시 연결"), 기록을 휴지통으로 ("수집 기록을 휴지통으로 옮겼습니다." + "실행 취소"), 장치 원본 복원, 휴지통 항목 복원. The S4 sheets (등록 코드 입력, 장치 등록, 다시 로그인, 새 프로젝트) are forms, not confirmations.

---

## 9. Design guide outline (written in Korean after the owner has seen the drawings)

`docs/design/STYLE_GUIDE.md` is rewritten from scratch; `docs/design/colors.json` becomes the six-palette role table of §4.2 (generated, then read by `scripts/check_slate_harmony.py`); `GEO_FIELD_DESIGN_SYSTEM.md` and `APP_REBUILD_PLAN.md` are marked superseded. Each chapter states rules, owners in code (`Geo*`), do/don't pairs taken from the boards, and the test that enforces it.

1. **원칙** — 다음 행동은 하나만 보인다 · 한 화면은 한 가지 일을 한다 · 모른다는 것도 상태다 · 장치 이름이 먼저 나온다 · 일상 화면은 개발자 모드에서도 바뀌지 않는다 · 자동으로 시작하거나 멈추는 일은 없다.
2. **구조** — four tabs and their jobs; bar visibility and focused flows; stacks and re-tap; the current device and transactional switching; route map; next-action precedence with transport remedies (§1).
3. **흐름** — F1–F8 with tap counts and the boards that show them (§2).
4. **상태와 식별** — PreparationDraft, the start transaction, immutable action targets, pending commands, run and history identity, offline cache, honest sensor/metric/transfer states (§1.5).
5. **레이아웃** — window classes from the pane, gutters 24/20/16, columns and spans, two-pane rule, inset contract, sticky rule, compact height, keyboard (§3.1–3.4).
6. **간격** — the 2…56 scale with named uses and owners (§3.3).
7. **글자와 정렬** — eleven type roles; start/centre/end; numeric columns with unit slots; identity names never truncate; Korean line breaking and its acceptance strings (§3.5–3.6).
8. **색** — mode × palette; the six role tables; role rules; controls built from roles; fixed map and media colours; the contrast table and how CI checks it; the theme picker (§4).
9. **모양과 높이** — radius 6/10/12/14/20/pill, elevation, borders, focus rings; where pills may appear (§3.7).
10. **컴포넌트** — the §3.8 catalogue: anatomy, states, the one stacking rule, Compose names, adapters for old wrappers.
11. **아이콘** — the GeoIcons stroke set, sizes, colours, the complete name list (§3.9, KIT §4).
12. **움직임과 햅틱** — 120/180/240 ms, what may move, reduced motion, haptic events (§3.10).
13. **접근성** — contrast, targets, font-scale matrix, screen-reader merging and live regions, gesture alternatives, focus return, timeouts (§3.11).
14. **문구** — rules, glossary, state vocabulary with tones, deterministic formatters, the confirmation copy deck (§3.12–3.13, §8).
15. **지도와 수집 구역** — rendering, card, full map, contract, inside/outside, Phase 1 / Phase 2 (§5).
16. **시작과 로딩** — restore state, splash themes, persistence order, loader and error scenes (§6).
17. **템플릿** — P1–P10, S1–S8, B1–B3 and the 138-item map (§7).
18. **검사** — the import lint, `check_slate_harmony.py` for six palettes, unit tests (precedence, formatters, zone monitor, identity), the device QA matrix (§3.11, §10).
19. **변경 이력** — GEO& Work token commit 3f85615f, this spec's date, later revisions.

---

## 10. Implementation plan (astra's phase order, from 560c6f9)

### 10.1 Where we start

**Done in 560c6f9** (astra phases 1–2, state fixes): undo drawn in the snackbar · invalid metrics shown as 미확인 and never cached as zero (`DeviceSnapshotStore`) · sensor freshness from sample time · six transfer groups with verification identity · `run_result/{runId}` and `history_result/{historyId}` with source-aware back · ticket preparation persisted through the task choice and process death · pipeline adoption at app level. Route names: keep the committed ones; this spec uses them throughout.

**Still open from phases 1–2** (Phase 2b below): the `NextAction` function used by production screens; the remaining PreparationDraft fields (task before preflight, persisted create request ids, geometry revision); immutable action targets with revalidation; the on-disk ticket cache; transactional device switching.

### 10.2 Rules for the parallel Codex workers

- A worker edits **only the files its slice owns** in that phase (paths relative to `app/src/main/java/com/example/jetsoncontroller/` unless stated). New files are allowed inside the slice's own packages. No two slices of a phase share a file; a file changes owner only between phases.
- Workers cannot run Gradle. Each hand-off lists the files changed, the new test classes and the exact commands for the lead; the lead builds, runs the tests and merges. Code must compile without the other slices of the same phase (depend only on code merged in earlier phases; use interfaces or adapters at the seams).
- No new dependencies. Keep public signatures used outside the slice, or add an adapter in the slice's own file.
- Korean copy exactly as in this spec. Shared state words live in `ui/model/Vocabulary.kt` (frozen after Phase 3; a later slice adds words in its own `*Words.kt`, folded in by the lead).
- `ui/JetsonApp.kt` (2,752 lines) has exactly one owner per phase: the shell slice in Phase 3, which splits it so later phases own their graph files.
- Default test command for the lead: `./gradlew :app:testDebugUnitTest --tests '<classes>' --max-workers=1 --console=plain`, plus `:app:assembleDebug` and `:app:lintDebug` when resources or compilation are affected, and `python3 scripts/check_slate_harmony.py` for colour work.

### 10.3 Phases, slices and acceptance

**Phase 2b — remaining invariants** (2 workers; may run in parallel with Phase 3 — no shared files)

| Slice | Owns | Delivers | Tests (new) |
|---|---|---|---|
| 2b-A Next action and draft | `ui/field/FieldStage.kt`, new `ui/model/NextAction.kt`, `ui/survey/SurveyRunViewModel.kt`, `data/survey/SurveySelectionStore.kt`, `model/SurveyRun.kt` | `NextAction` = operation state (FieldStage order) + transport remedy (§1.6); PreparationDraft fields of §1.5; task before preflight; `projectRequestId`/`sectionRequestId` generated once and reused on retry; start/stop target tuples with revalidation | `NextActionTest` (every row and remedy of §1.6, pending start survives reconnection), `PreparationDraftTest` (round-trip, device-scoped, process death, request-id reuse), `StartRevalidationTest` |
| 2b-B Tickets offline and report targets | `ui/tasks/ServerTicketViewModel.kt`, `data/server/TicketReportStore.kt`, new `data/server/TicketCacheStore.kt`, `data/server/ServerModels.kt` | on-disk ticket cache per profile + device scope with "마지막 동기화" time; nullable geometry fields (§5.4) parsed when present; report target tuple (ticket row/revision, run id, receipt id) revalidated at send; server-only work allowed while the device is offline | `TicketCacheStoreTest`, `TicketReportTargetTest`, `ServerTicketGeometryParseTest` |

Acceptance: the six new test classes pass; `NavigationWorkflowTest`, `TicketPreparationTest`, `SurveyRunViewModelTest` still pass; on GEO-X-1, kill the app during "시작 확인 중" → reopen shows Focus 1 and no start button; cold start offline shows the cached ticket list with "마지막 동기화 …".

**Phase 3 — tokens, components and shell** (6 workers)

| Slice | Owns | Delivers | Tests (new) |
|---|---|---|---|
| 3-A Theme and tokens | `ui/theme/Color.kt`, `GeoPalette.kt`, `Theme.kt`, `Type.kt`, `AppSpacing.kt`, `Controls.kt`, new `ui/theme/GeoShape.kt`, `GeoMotion.kt`; `docs/design/colors.json`; `scripts/check_slate_harmony.py` | the §4.2 role set for six palettes, old names kept as deprecated aliases until 7-F (`ink`→`text`, `muted`→`textSecondary`, `inkSubtle`→`textSubtle`, `controlBorder`→`borderStrong`, today's `borderStrong` #D3D9E3 → removed, `accent`/`brandSoft`→`primaryTint`, `onAccent`/`onBrandSoft`→`primaryTintText`, `unknown*`→`neutral*`, `hero*`→`topBar*`/`nav*`, `section*`→removed, `overlay`→`scrim`); type roles §3.6 incl. 고대비 weights; spacing names §3.3; radius/elevation §3.7; motion §3.10; `TABLET_UI_SCALE` and `LocalGeoUiScale` removed; the script asserts the 63 pairs × 6 + 13 fixed checks | `GeoPaletteTest` (every role present in every palette; values equal §4.2), `GeoTypeTest` |
| 3-B Icons and controls | new `ui/components/geo/GeoIcons.kt`, `GeoButtons.kt`, `GeoFields.kt`, `GeoSelection.kt` (switch, radio, checkbox rows, segmented, slider row), `GeoProgress.kt`; new `app/src/debug/.../gallery/ControlsGallery.kt` | the §3.9 stroke set from KIT §4 as `ImageVector`s; buttons with the one stacking rule; fields with 1 dp `borderStrong`; controls per §4.2 table | `GeoStackingRuleTest` (fits → row, else stack), `GeoIconsTest` (all names build) |
| 3-C Content | new `ui/components/geo/GeoRows.kt`, `GeoSection.kt`, `GeoBadge.kt`, `GeoFigureGrid.kt`, `GeoReceipt.kt`, `GeoNotes.kt`, `GeoStateBlock.kt`, `GeoTechInfo.kt`, `GeoLogView.kt`, `GeoDeviceStrip.kt`; `debug/.../gallery/ContentGallery.kt` | §3.8 rows (action rows with two targets), numeric value + unit slots (§3.5.4), figures per §3.8, log follow rule | `FigureGridRulesTest` (reflow, 미확인, stale, threshold), `LogFollowTest` |
| 3-D Overlays and strips | new `ui/components/geo/GeoSheets.kt`, `GeoDialogs.kt`, `GeoMenu.kt`, `GeoSnackbar.kt`, `GeoStatusStrip.kt`, `GeoFocusBlock.kt`, `GeoStartSheet.kt`; `ui/components/OperationMessageHost.kt` (adapter); `debug/.../gallery/OverlayGallery.kt` | sheets with full-screen fallback (< 160 dp body), dialogs S1a/S1b/S2 with scrolling body, recommended snackbar timeouts, strip priority list, S8 UI states (checking / passed / failed) | `SheetFallbackTest`, `StripPriorityTest`, `SnackbarTimeoutTest` |
| 3-E Shell and navigation | `ui/JetsonApp.kt` (exclusive), new `ui/navigation/AppNavHost.kt`, `Routes.kt`, `WorkGraph.kt`, `DeviceGraph.kt`, `TransferGraph.kt`, `SettingsGraph.kt`, `DevGraph.kt`, `FocusedRoutes.kt`; new `ui/components/geo/shell/GeoAppShell.kt`, `GeoTopBar.kt`, `GeoBottomBar.kt`, `GeoRail.kt`, `GeoActionArea.kt`, `GeoWindow.kt`; `ui/components/ControlNavigationBar.kt` (adapter); `debug/.../CobaltGalleryActivity.kt` | the split of `JetsonApp.kt` into graphs with today's screens wired unchanged; bar visibility per §1.2; re-tap pops; back to 작업; window classes, rail ≥ 600 dp, two-pane rule; inset contract §3.2 with one system-bar controller; sticky rule §3.4 | `NavigationShellTest` (bar shown/hidden per route list of §1.2, re-tap, back), `WindowClassTest`, `StickyRuleTest`; existing `NavigationWorkflowTest` kept green |
| 3-F Formatters and words | new `ui/model/Formats.kt`, `ui/model/Vocabulary.kt`; `ui/components/GeoFormat.kt` (delegates); the private size formatters' bodies only in `ui/field/GeoRunDashboard.kt:291`, `ui/field/RunResultScreen.kt:497`, `ui/storage/DirectServerScreen.kt:730`, `ui/upload/UploadProgressScreen.kt:500`, `ui/storage/DeviceStorageScreen.kt:217`, `ui/pipelines/PipelineScreens.kt:947` (delegate to `Formats.size`) | every formatter of §3.13; the state vocabulary of §3.12 as enums → (word, tone) | `FormatsTest` (all examples in §3.13, boundary promotion, HALF_UP, null → 미확인, legit 0), `VocabularyTest` |

Acceptance: the listed tests; `check_slate_harmony.py` 0 failures; `assembleDebug`, `lintDebug`; on the S22 Ultra (three-button and gesture navigation, both landscape directions) the gallery and today's 장치 상세 show no top-bar overlap (bug 1), padded status grid (bug 2), a scrolling connect sheet in landscape (bug 8), pane-based gutters (bug 10), no clipping at font scale 2.0 (bug 11).

**Phase 4 — startup and theme persistence** (3 workers)

| Slice | Owns | Delivers | Tests (new) |
|---|---|---|---|
| 4-A Startup | `MainActivity.kt`, new `ui/startup/AppRestoreViewModel.kt`, `ui/startup/StartupScenes.kt`, `ui/components/geo/GeonLoader.kt`; `app/src/main/res/values/themes.xml`, `values-night/themes.xml`, `AndroidManifest.xml` (activity theme only) | §6.1 restore state and first-frame gate; §6.3 loader and error scenes; the three adaptive splash themes; splash → scene hand-off | `AppRestoreStateTest` (loading / restored / failed; failure ≠ zero devices), `LoaderTimingTest` (300 ms gate, ≥ 400 ms, 240 ms) |
| 4-B Appearance | `ui/theme/ThemePreference.kt` → `AppearanceStore.kt`, `ui/theme/Theme.kt` (palette selection, system-bar colours per bar), new `ui/settings/ThemeScreen.kt` | §6.2 persistence order, migration of the old `theme` key, `setApplicationNightMode` + `setSplashScreenTheme` behind small wrappers; the §4.5 picker | `AppearanceStoreTest` (migration, order of calls with fake wrappers), `ThemeScreenStateTest` |
| 4-C Recreation safety | `ui/pairing/PairingViewModel.kt` (state saving only), `ui/survey/SurveyRunViewModel.kt` (saved-state hooks only, after 2b-A merged) | route, scroll, drafts and pending command ids survive `recreate()`; a theme change never restarts pairing or a start request | `RecreationSafetyTest` |

Acceptance: tests pass; device: zero devices never flash the first-run scene during restore; a failing credential store shows the error scene; all six palettes in light/dark/system; a system mode change while the process is dead opens on the right splash colour (API 31+); a theme change during pairing keeps the pairing sheet state.

**Phase 5 — one complete collection journey** (6 workers)

| Slice | Owns | Delivers |
|---|---|---|
| 5-A 작업 root and ticket | `ui/tasks/TasksHomeScreen.kt`, `TasksHomeUiState.kt`, `TicketDetailScreen.kt`, `TicketPresentation.kt`, `ui/navigation/WorkGraph.kt` | P1 작업 root with device strip, Focus block from `NextAction`, segmented queue, cached list; P3 ticket preparation with passive readiness and a map-card slot |
| 5-B Start | `ui/survey/SurveyRunScreen.kt`, `SurveyRunViewModel.kt`, `data/survey/*`, new `ui/survey/TaskChoiceSheet.kt` (the operator task sheet, reading `PipelineViewModel` without editing `ui/pipelines/*`) | manual P3 with restored project/section; S8 start sheet with preflight on "수집 시작"; the start transaction §1.5 |
| 5-C Run | `ui/field/ActiveRunScreen.kt`, `ui/field/RunTelemetryPresentation.kt`, new `ui/field/OngoingRunStrip.kt` | P4 states; stop → confirm → result; the shared ongoing-run strip provider; 보정 정보 중계 row |
| 5-D Result and history | `ui/field/RunResultScreen.kt`, `GeoRunDashboard.kt`, `FieldToolsViewModel.kt`, `QualityPresentation.kt`, new `ui/field/HistoryEntryScreen.kt` | P2-영수증 chain with remedies; history P5 with legacy entries and logs (developer 기술 정보) |
| 5-E Transfers | `ui/transfers/*`, `ui/upload/UploadConfirmScreen.kt`, `UploadProgressScreen.kt`, `UploadQueueScreen.kt`, `UploadViewModel.kt`, `ui/navigation/TransferGraph.kt` | 전송 root (current device + attention rows), send review, transfer detail with 완료 보고 and 관리 block, 전송 기록; retry/cancel/clean-up target tuples |
| 5-F Report and outbox | `ui/tasks/ServerTicketViewModel.kt`, `data/server/TicketReportStore.kt` | 완료 보고 from result, Focus 6 and transfer detail with revalidation; "보고 대기 / 보고 실패" rows with "다시 보고" |

Tests (new): `StartTransactionTest`, `StartSheetStateTest`, `RunStopFlowTest`, `ResultChainTest`, `TransferDetailActionsTest`, `ReportFlowTest`, `TapCountTest` (F3 = 3, F5 stop = 2, F6 = 3 through the navigation graph). Acceptance on GEO-X-1: ticket → collecting in 3 taps; stop → result in 2; result → send → verified → report; process death during a pending start; device switch with an open draft; offline cold start with cached tickets; a 직접 연결 result shows the send remedy; reporting while the device is offline and the server is reachable.

**Phase 6 — map display, then zone monitoring** (4 workers)

| Slice | Owns | Delivers |
|---|---|---|
| 6-A Renderer and full map | new `ui/map/GeoMapRenderer.kt`, `GeoMapScreen.kt`, `GeoMapPanel.kt`; `ui/sensors/GnssMapScreen.kt` (retired into it), `ui/sensors/MobileLocationViewModel.kt` | §5.1 and §5.3: local style without a key, separate sources, layer order, casings, patterns, controls, run action panel, landscape side panel, phone location on request |
| 6-B Map card and insertion | new `ui/map/GeoMapCard.kt`; the map-card slots in `TicketDetailScreen.kt`, `ActiveRunScreen.kt`, `RunResultScreen.kt` (slot lines only) | §5.2 card states and captions; one live map at a time |
| 6-C Zone model and monitor | new `data/zone/ZoneContract.kt`, `data/zone/ZoneValidation.kt`, `ui/zone/ZoneMonitor.kt`, `ui/zone/ZoneEventsSource.kt`, `ui/zone/PhoneObservedZoneEvents.kt` | §5.4–5.6: validation, signed distance with holes, uncertainty, freshness and duplicates, hysteresis, observation gaps persisted per run, the Phase 2 interface |
| 6-D Receiver contract | `upload_receiver/` (geometry passthrough into the ticket API and its tests) | the §5.4 fields delivered to the app once GEO& Work's contract is agreed |

Tests (new): `ZoneMonitorTest` (inside, outside, near, holes, MultiPolygon, boundary, swapped coordinates, duplicate samples, stale samples, two-sample hysteresis, initialization, latency at 1 s / 2 s / 5 s sample intervals), `ZoneValidationTest`, `MapStateTest`; receiver unit tests from `upload_receiver/`. Acceptance: no VWorld key → background + zone visible; on a real route with GEO-X-1, measure the GNSS sample interval and the warning delay, confirm the strip and one haptic on a confirmed exit, no notification, gaps recorded when the app is backgrounded; a board edit after start does not change the run's zone.

**Phase 7 — remaining surfaces, then removal** (6 workers, then 7-F alone)

| Slice | Owns | Delivers |
|---|---|---|
| 7-A 장치 | `ui/devices/*` (except `DeviceListScreen.kt`, deleted by 7-F), `ui/connection/*` (except `ConnectionHubScreen.kt`, deleted by 7-F), `ui/network/*`, `ui/sensors/SensorScreen.kt`, `SensorPresentation.kt`, `CameraPreviewScreen.kt`, `CameraPreviewViewModel.kt`, `ui/dashboard/DashboardHealth.kt`, `MetricPresentation.kt`, `StatusFreshness.kt`, `OperationalSummary.kt`, `ui/navigation/DeviceGraph.kt` | 장치 root and detail (sensors merged, 등록 해제), connect sheet, 장치 Wi-Fi, camera |
| 7-B 등록과 직접 연결 | `ui/onboarding/*`, `ui/pairing/*`, `ui/wifi/*` | 장치 등록 scene, scanner, pairing sheets, 직접 연결 states, permission blocks |
| 7-C Files and server (operator) | `ui/storage/DataHubScreen.kt`, `DeviceStorageScreen.kt`, `DeviceStorageViewModel.kt`, `LocalTrashScreen.kt`, `LocalTrashViewModel.kt`, `DirectServerScreen.kt`, `DirectServerViewModel.kt`, `MediaPresentation.kt`, `DataLocationTabs.kt` (deleted) | one browser with per-entry location, type picker, previews, trash, 서버 수신 결과 with paging, video, trash states |
| 7-D 설정 and 알림 | `ui/settings/*` (except `ThemeScreen.kt`), `ui/alerts/*`, `data/alerts/DeviceAlertMonitor.kt`, `ui/navigation/SettingsGraph.kt`, new `ui/settings/ServerConnectionScreen.kt` | 설정 root, 알림 기준 page, 앱 정보, 도움말, 서버 연결 + 다시 로그인, alert copy and destinations |
| 7-E 개발자 도구 | `ui/field/DeveloperScreen.kt`, `DeveloperDiagnosticsPanel.kt`, `ui/pipelines/*`, `ui/diagnostics/*`, `ui/upload/UploadTargetSettingsScreen.kt`, `ui/dashboard/DashboardScreen.kt`, `DashboardUiState.kt`, `DashboardViewModel.kt` (fan state moves to `dev/fan`), `ui/storage/ServerStorageScreen.kt`, `ServerStorageViewModel.kt`, new `ui/dev/*`, `ui/navigation/DevGraph.kt` | `dev` hub and every `dev/*` page (§7.2 #87–#110), server profiles editor and server-trash purge, legacy dashboard retired |
| 7-F Removal (after 7-A…E merge) | `ui/connection/ConnectionHubScreen.kt`, `ui/devices/DeviceListScreen.kt`, old wrappers in `ui/components/*` (`GeoComponents.kt`, `OperationalComponents.kt`, `StatusTone.kt`, `SectionSurface.kt`, `MetricCard.kt`, `DeviceCard.kt`, `DeviceContextHeader.kt`, `ConnectionStatusCard.kt`, `ConnectionRecoveryLayout.kt`), retired routes, `app/build.gradle.kts` (drop `material-icons-extended`), the import lint config | no adapters, no aliases, no raw Material imports outside `ui/components/geo` |

Acceptance: `InventoryReachabilityTest` (every inventory id's home of §7.2 is reachable from a root; retired routes absent); the import lint passes repo-wide; `.github/workflows/reliability.yml` checks green; the device QA matrix of §3.11 on the eight acceptance screens in all six palettes and at font scale 2.0; Samsung three-button and gesture navigation, both landscape directions, split screen, IME-open sheets, offline cold start, process death during pending commands.

**Estimate:** astra's 10–14 engineer-weeks plus dedicated device QA for the whole rebuild, of which phases 1–2 are done; device-side excursion recording (zone Phase 2) and the GEO& Work geometry contract are additional cross-service work.

---

## 11. BOARD LIST for the canvas

### 11.0 Conventions for every board

- Files go to `design-r3/canvas/project/<file>` and follow ARTBOARD_RULES and `KIT.md` exactly (copy the kit's snippets; do not restyle them).
- **Phone board:** fixed root 412 × 882 (status inset 36 painted `topBar`, navigation inset 48 painted in the colour of the bar above it). **Multi-frame board** (marked "n frames"): root `n × 412 + (n − 1) × 40` wide × 882, background #DDE1E8, frames in a flex row with a 40 px gap, each frame a complete phone screen, in the order listed. **Landscape board:** 882 × 412 (status 24 on top, navigation 48 on the end side). **Design-system board:** 1280 wide × the stated height, background #EDF0F5.
- Theme 표준 unless stated. A modal is drawn over its parent (simplified parent: real top bar + the first blocks) with the palette scrim. Android permission dialogs are never drawn; the board shows the in-app reason before them or the state after a denial.
- Bar rules of §1.2: roots and tab-owned details show the bottom bar with the owning tab lit; focused flows (등록, 수집 중, 지도, 카메라, QR, 영상, editors) do not.
- Copy: Korean from this spec only; values through §3.13 formatters.

**Sample data (use these, nothing else).** Devices: **GEO-X-1** (current; 연결됨 · 같은 네트워크; 54°C; 저장 512 GB 남음 (57%); CPU 3% · GPU 0%; 메모리 2.0 GB / 15.1 GB; 카메라 1920 × 1080 · 수신 중; 위치(GNSS) 정밀 위치 · 위성 12개 (RTK 고정); 움직임(IMU) 수신 중) and **GEO-X-2** (오프라인 · 마지막 응답 2시간 전). Tickets: **장치테스트** (호기 GEO-X-1 · 진행 중 · 마감 미정 · 담당 미지정 · 구역 안) and **신월4거리 북측 보조 구간** (할 일 · 프로젝트 수원시 도로대장 · 마감 10월 21일 · 담당 김현우 · 경로 1.2 km · 폭 20 m · 지점 4곳 · 장치에서 350 m). Active run: 신월4거리 북측 보조 구간 · 00:12:34 · 1.2 GB · 정밀 위치 98%. Finished run: folder 20261019_1636 · 16:36 종료 · 12분 · 1,204개 · 1.2 GB. Older run: 20260915_data · 신월4거리 · 9월 16일 16:36 · 1분 미만 · 896 MB. Large folder: 5,049개 파일 · 14.7 GB. Transfer: 768 MB / 1.2 GB · 12.3 MB/초 · 약 36초 남음 · 64%; failed 20261018_0912 "연결이 끊겨 멈췄습니다."; server display name "운영 서버"; Wi-Fi GEON-5G (current), GEON-2G. Unknown identifiers are bracketed placeholders ("[직원 ID]", "[프로젝트 ID]", "[IP 주소]", "[장치 이름]", "[작업 이름]"). Today is 10월 19일, so today's times print as "16:36".

### 11.1 Batches

Effort: ~22 points per batch (1 = a simple phone board; multi-frame and design-system boards count more). Every batch is independent; batches 1 and 2 define the look the others copy from the kit, so they are drawn first only if the lead wants to review them before fanning out.

**Batch 1 — 디자인 시스템 (10 boards)**

| File | 제목 | Theme | Inventory ids | State and data | Primary |
|---|---|---|---|---|---|
| r3-ds-type.dc.html | 글자와 정렬 | 표준 + 고대비 column | — | 1280 × 1600: the 11 type roles (size/line, weight, Korean sample, use); 고대비 weight step side by side; start/centre/end examples; a numeric column with value and unit slots (896 MB · 14.7 GB · 512 GB); a label/value pair normal and stacked; wrapping of "GEO-X-현장지원차량-2" and "신월4거리 북측 보조 구간 2차 재수집"; " · " joins | — |
| r3-ds-layout.dc.html | 레이아웃과 간격 | 표준 | — | 1280 × 1700: phone outlines at 412 / 360 / 320 with gutters 24 / 20 / 16; insets (status 36, top bar 64/56, bottom bar 64 + 48, action area); the spacing scale with named uses; the 72 dp two-line row anatomy; section gaps 24 / 28 / 32; sticky vs inline action (portrait vs 882 × 412); two-pane rule | — |
| r3-ds-controls.dc.html | 버튼과 입력 | 표준 | — | 1280 × 1800: primary, secondary, tertiary, danger-outline, danger-filled and icon buttons × enabled / pressed / focused / disabled / busy; a pair that fits and one that stacks; text field rest / focused / error / disabled / password; picker field; segmented with 2 and 3 segments; switch off / on / disabled; checkbox; radio; slider row with −/+; progress 64% and indeterminate; skeleton | — |
| r3-ds-content.dc.html | 목록과 상태 | 표준 | — | 1280 × 1900: navigation, two-line, action (two targets), fact, check (passed / checking / failed + remedy) and switch rows; section titles with action and count; badges in six tones + on-map + on-dark + 고대비; figure grid normal / 미확인 / 마지막 값 기준 / over threshold; receipt list and line; device strip normal and with an attention item; inline notes; state blocks (empty, error, missing, permission, offline, unsupported); 기술 정보 collapsed and expanded; log view with "최신 로그" | — |
| r3-ds-overlays.dc.html | 막대와 겹침 | 표준 + 네이비 (밝게) chrome | 138 | 1280 × 2000: top bars (root, root with the switcher on a second row, detail with ⋮) in 표준 and 네이비 (밝게); bottom bar (작업 selected, dot on 장치) and rail; status strips (인증 오류, 연결 끊김, 구역 밖, 수집 중, 마지막 동기화); Focus block; action area; choice sheet; S8 mini; S1a, S1b, S2; menu; snackbar with action; a caption "대화상자는 확인, 정보, 선택·입력 시트, 메뉴, 스낵바뿐입니다." | — |
| r3-ds-icons.dc.html | 아이콘 | 표준 | — | 1280 × 900: every `GeoIcons` name at 24 dp with its name; the four filled tab variants; 24 / 20 / 16 sizes; neutral, tone and on-primary colours | — |
| r3-ds-map.dc.html | 지도 | 표준 | — | 1280 × 1500: map card states (구역 안, 경계 근처, 350 m caption, loading, tiles failed, range not computed, no fix); overlay legend (range fill + dashed edge, route, area edge, 지점, trail solid / dashed / dotted, gap rings, device, stale hollow device, uncertainty circle, phone + halo); full-map anatomy (top-end controls, zoom, panel peek with expand button); zone badges; focus ring on a map control | — |
| r3-ds-startup.dc.html | 시작 화면 | all six | 133 | 1280 × 1400: six half-scale splash frames (#FFFFFF ×3, #1E1F21, #1B1F28, #2F3033) with the symbol; loading scene in 표준 and 그래파이트; error scene; reduced-motion static symbol; timing strip 0 · 300 ms gate · ≥ 400 ms · 240 ms crossfade | — |
| r3-common-loading.dc.html | 로딩과 오류 (2 frames) | 표준 | 133 | 1: loading scene with the animated geon-loader, "GEO-X", "장치 정보를 불러오는 중입니다."; 2: error scene "장치 정보를 불러오지 못했습니다" / "저장된 장치 정보를 읽지 못했습니다. 다시 시도하세요." | 다시 시도 (2) |
| r3-dev-dialogs-2.dc.html | 개발자 확인 2 (3 frames) | 표준 | 46, 47, 53 | 1: S1b "서버 휴지통을 비울까요?" over 서버 휴지통 비우기 ("표시된 100개 항목을 서버에서 영구 삭제합니다. 되돌릴 수 없습니다. 전체 128개 중 표시된 100개만 지웁니다."); 2: S1b "운영 서버를 삭제할까요?" over 서버 프로필; 3: S1b "서버 데이터를 삭제할까요?" over 장치 경유 서버 조회 | 영구 삭제 · 프로필 삭제 · 영구 삭제 |

**Batch 2 — 테마 (14 boards)**

| File | 제목 | Theme | Inventory ids | State and data | Primary |
|---|---|---|---|---|---|
| r3-ds-themes-light.dc.html | 밝은 테마 | 표준 · 네이비 (밝게) · 고대비 | — | 1280 × 1800: one column per palette with every role swatch and hex (KIT §1) and a 412 × 300 sample (top bar, two-line row with badge, text field, primary, bottom bar) | — |
| r3-ds-themes-dark.dc.html | 어두운 테마 | 그래파이트 · 네이비 (어둡게) · 흐림 | — | same layout | — |
| r3-work-root.dc.html | 작업 | 표준 | 9, 136 | connected, idle: device strip "GEO-X-1 · 연결됨 · 54°C · 저장 512 GB 남음 · 방금 갱신"; 수집 구간 (segmented GEO-X-1 \| 전체; 장치테스트 진행 중 · 구역 안; 신월4거리 북측 보조 구간 할 일 · 350 m; "완료 2개"; "구간 직접 고르기"); 최근 수집 (20260915_data with receipt line; a second row whose GEO& Work 보고 reads "보고 대기"); 장치 작업 "데이터 수집 · 대기"; bar 작업 lit | — |
| r3-work-run.dc.html | 수집 중 | 표준 | 21, 24 | inside the zone: detail bar "수집 중" (no ⋮); H1 신월4거리 북측 보조 구간; meta "GEO-X-1 · 수원시 도로대장"; map card 240 with badge 구역 안; figures 00:12:34 · 1.2 GB · 98% · 0회; "마지막 확인 방금"; rows 카메라 영상 (수신 중) and 지도 크게 보기; action area "GEO-X-1 · 수집 중 · 구역 안." ; no bar | 수집 종료 (danger-outline) |
| r3-theme-work-root-navy-light.dc.html | 작업 · 네이비 (밝게) | 네이비 (밝게) | 9 | r3-work-root with navy top bar (status area included), navy bottom bar and navigation inset | — |
| r3-theme-work-root-contrast.dc.html | 작업 · 고대비 | 고대비 | 9 | r3-work-root in 고대비 (white canvas, solid borders, heavier weights) | — |
| r3-theme-work-root-graphite.dc.html | 작업 · 그래파이트 | 그래파이트 | 9 | r3-work-root | — |
| r3-theme-work-root-navy-dark.dc.html | 작업 · 네이비 (어둡게) | 네이비 (어둡게) | 9 | r3-work-root | — |
| r3-theme-work-root-dim.dc.html | 작업 · 흐림 | 흐림 | 9 | r3-work-root | — |
| r3-theme-run-navy-light.dc.html | 수집 중 · 네이비 (밝게) | 네이비 (밝게) | 21 | r3-work-run with navy top bar; bottom inset follows the action area (`surface`) | 수집 종료 |
| r3-theme-run-contrast.dc.html | 수집 중 · 고대비 | 고대비 | 21 | r3-work-run | 수집 종료 |
| r3-theme-run-graphite.dc.html | 수집 중 · 그래파이트 | 그래파이트 | 21 | r3-work-run with the dimmed 일반 base map | 수집 종료 |
| r3-theme-run-navy-dark.dc.html | 수집 중 · 네이비 (어둡게) | 네이비 (어둡게) | 21 | r3-work-run, dimmed base map | 수집 종료 |
| r3-theme-run-dim.dc.html | 수집 중 · 흐림 | 흐림 | 21 | r3-work-run, dimmed base map (0.85) | 수집 종료 |

**Batch 3 — 작업 · 준비 (14 boards)**

| File | 제목 | Theme | Inventory ids | State and data | Primary |
|---|---|---|---|---|---|
| r3-work-root-run.dc.html | 작업 · 수집 중 | 표준 | 9 | Focus 4 "신월4거리 수집 중" / "00:12:34 경과 · 구역 안" above the queue | 진행 보기 |
| r3-work-focus-states.dc.html | 작업 · 확인할 상태 (4 frames) | 표준 | 3, 9 | 1: pending start, reachable ("GEO-X-1 수집 시작 결과를 확인해야 합니다"); 2: run unconfirmed, unreachable (remedy 다시 연결 + 연결 방법); 3: offline, nothing pending ("GEO-X-1 연결이 끊겼습니다" / "마지막 응답 5분 전", strip hidden); 4: verified, not reported ("신월4거리 서버 수신을 확인했습니다") | 시작 결과 확인 · 다시 연결 · 다시 연결 · 완료 보고 |
| r3-work-focus-states-2.dc.html | 작업 · 연결과 권한 (4 frames) | 표준 | 9, 98, 115, 134 | 1: credentials rejected ("GEO-X-1 인증 정보를 확인할 수 없습니다."); 2: device strip "GEO-X-1 · 연결 권한이 필요합니다"; 3: "GEO-X-1에서 수집 2건이 진행 중으로 보입니다"; 4: "GEO-X-1 기본 연결입니다" | QR 코드로 다시 등록 · 권한 허용 · 진행 보기 · 장치 Wi-Fi 설정 |
| r3-work-root-empty.dc.html | 작업 · 빈 상태 (2 frames) | 표준 | 10, 135 | 1: just registered — device strip "● 확인 중 · 장치를 찾고 있습니다", every section with its empty block; 2: no server profile — "GEO& Work 수집 구간을 보려면 서버 연결이 필요합니다." + tertiary 서버 연결 | — |
| r3-work-device-switcher.dc.html | 장치 선택 | 표준 | — (new) | S3 over the 작업 root: GEO-X-1 (연결됨, selected), GEO-X-2 (오프라인 · 마지막 응답 2시간 전); footer tertiary "장치 관리" | (choice rows) |
| r3-work-ticket.dc.html | 수집 구간 | 표준 | 15 | top of the preparation screen: H1, badge 할 일, meta "수원시 도로대장 · GEO-X-1 · 마감 10월 21일", map card 200 + caption "경로 1.2 km · 폭 20 m · 지점 4곳" / "장치가 구역에서 350 m 떨어져 있습니다.", start of 정보; sticky action area "GEO-X-1 · 같은 네트워크 · 시작할 수 있습니다."; bar 작업 lit | 수집 시작 |
| r3-work-ticket-scrolled.dc.html | 수집 구간 · 아래 | 표준 | 15 | scrolled: 정보 (담당 김현우, 마감 10월 21일, GEO& Work 보고 보고 전, 수집 작업 데이터 수집); 준비 상태 (연결 같은 네트워크, 저장 공간 512 GB 남음, 위치 품질 정밀 위치, 카메라 수신 중, 보정 정보 중계 꺼짐); same action area | 수집 시작 |
| r3-work-task-sheet.dc.html | 수집 작업 선택 (2 frames) | 표준 | 11, 12 | 1: S3 over the ticket: "데이터 수집 · 대기 · 마지막 실행 9월 16일 16:36" and "[작업 이름] · 대기"; 2: the empty sheet "이 장치에 수집 작업이 없습니다." | (choice rows) |
| r3-work-start-sheet.dc.html | 수집 시작 확인 (2 frames) | 표준 | 17, 19 | 1: preflight running (rows 확인 중, primary busy "점검하는 중"); 2: passed (rows ✓, zone line, "GEO& Work 상태가 '진행 중'으로 바뀝니다.", "시작하면 …") | 수집 시작 |
| r3-work-start-sheet-failed.dc.html | 수집 시작 확인 · 실패 | 표준 | 17, 19 | 저장 공간 row warning "저장 공간이 수집 설정의 최소값보다 적습니다 · 남은 공간 12 GB" + remedy 장치 파일; primary disabled + "점검을 통과해야 시작할 수 있습니다."; tertiary 다시 점검 | 수집 시작 (disabled) |
| r3-work-prepare.dc.html | 수집 준비 (2 frames) | 표준 | 18, 20 | 1: manual path with restored 수원시 도로대장 / 신월4거리 (selected radio), "새 구간" row, 준비 상태, action area; 2: S3 "프로젝트 선택" with footer row "새 프로젝트" | 수집 시작 |
| r3-work-task-detail.dc.html | 작업 상세 | 표준 | 13 | 데이터 수집: 상태 대기 · 마지막 실행 9월 16일 16:36 · 마지막 결과 저장 확인 · 결과 저장 폴더 20260915_data › | 이 작업으로 수집 준비 |
| r3-work-missing-states.dc.html | 찾을 수 없음 (3 frames) | 표준 | 14, 16, 26 | 1: "작업 정보를 찾을 수 없습니다" + 작업으로; 2: "수집 구간을 찾을 수 없습니다" + 목록으로; 3: "수집 결과를 찾을 수 없습니다" + 수집 기록 | — |
| r3-map-full-zone.dc.html | 지도 · 수집 구역 | 표준 | 80 | mode 수집 구역 from the ticket: zone band, an 영역, 4 지점, device 350 m away; controls; panel peek "경로 1.2 km · 폭 20 m" / "장치가 구역에서 350 m 떨어져 있습니다."; no bar | — |

**Batch 4 — 작업 · 수집, 결과, 지도, 카메라 (14 boards)**

| File | 제목 | Theme | Inventory ids | State and data | Primary |
|---|---|---|---|---|---|
| r3-work-run-outside.dc.html | 수집 중 · 구역 밖 | 표준 | 21, 22 | warning strip "GEO-X-1이 수집 구역을 벗어났습니다 · 35 m"; badge 구역 밖 · 35 m; 구역 밖 기록 1회; readiness "GEO-X-1 · 구역 밖 · 기록은 계속됩니다." | 수집 종료 |
| r3-work-run-states.dc.html | 수집 중 · 상태 (4 frames) | 표준 | 22, 137 | 1: 시작 확인 중 + note; 2: 연결 끊김 (strip + 다시 연결 / 연결 방법, figures "마지막 값 기준", 수집 종료 disabled "연결되면 종료할 수 있습니다."); 3: "수집 상태를 확인할 수 없습니다"; 4: 종료 확인 중 (busy) | 시작 결과 확인 · — · 상태 다시 확인 · — |
| r3-work-run-dialogs.dc.html | 종료와 보고 (2 frames) | 표준 | 23, 27 | 1: S1b "신월4거리 북측 보조 구간 수집을 종료할까요?" over the run; 2: S1a "신월4거리 북측 보조 구간을 완료로 보고할까요?" over the result | 수집 종료 · 완료 보고 |
| r3-work-result.dc.html | 수집 결과 | 표준 | 25, 136 | H1, meta "16:36 종료 · 12분 · GEO-X-1"; receipt rows (종료; 저장 확인 · 1,204개 · 1.2 GB; 서버 수신 미확인 "이 휴대폰에서 보낸 기록이 없습니다."; GEO& Work 보고 보고 전); action area "GEO-X-1 · 같은 네트워크 · 지금 보낼 수 있습니다."; bar 작업 lit | 서버로 보내기 |
| r3-work-result-lower.dc.html | 수집 결과 · 아래와 위치 품질 (2 frames) | 표준 | 25, 32 | 1: figures, map card (trail over the zone; caption "구역 밖 기록 2회 · 1분 10초 · 관찰하지 못한 시간 2분" with "관찰한 시간 기준"), 저장 폴더 20261019_1636 ›, 위치 품질 자세히 ›; 2: S5 "위치 품질" (slices, intervals "16:36:02 ~ 16:37:10") | 서버로 보내기 (1) |
| r3-work-history.dc.html | 수집 기록 | 표준 | 29, 33 | segmented 전체 \| 진행 중 \| 완료; day groups; rows with receipt lines (one row without a canonical run id shows "장치 저장 ○ 미확인" in its receipt line); "이전 기록 더 불러오기"; snackbar "수집 기록을 휴지통으로 옮겼습니다." + 실행 취소 | — |
| r3-work-history-entry.dc.html | 기록 상세와 실행 로그 (2 frames) | 표준 | 29, 31 | 1: `history_result` entry (facts 시작 9월 16일 16:36 · 작업 데이터 수집 · 결과 미확인; 저장 폴더; 기술 정보 expanded with "실행 로그" row, developer mode on); 2: P9 "실행 로그" (mono lines, live line "보관 로그", floating "최신 로그") | — |
| r3-map-full-run.dc.html | 지도 · 수집 중 | 표준 | 80, 81 | run mode: trail solid / dashed / dotted, device followed ("장치 위치로" selected), panel peek with "수집 중 · 00:12:34" and 수집 종료; no bar | 수집 종료 |
| r3-map-panel.dc.html | 지도 패널 (2 frames) | 표준 | 81, 82, 83 | 1: panel expanded (범례, 장치 위치 37.5665000, 126.9780000 · 위성 12개, switch "휴대폰 위치 표시" off, 위치 품질 자세히); 2: switch turned on without permission → "휴대폰 위치를 보려면 위치 권한이 필요합니다." + 권한 허용, and the provider-off line "휴대폰 위치 서비스가 꺼져 있습니다." + 위치 켜기 | — |
| r3-map-full-landscape.dc.html | 지도 · 가로 | 표준 | 80 | 882 × 412, run mode with the 320 dp end-side panel; controls clear of it | 수집 종료 |
| r3-camera-preview.dc.html | 카메라 | 표준 (fixed media) | 84 | black stage, on-dark badge 실시간, "1920 × 1080", capture bar with the 64 dp round shutter, tertiary 저장 위치 | 캡처 |
| r3-camera-states.dc.html | 카메라 · 상태 (3 frames) | 표준 | 85 | 1: "카메라 영상을 기다리고 있습니다"; 2: badge 지연 + "마지막 수신 16:36:02"; 3: "영상을 받지 못했습니다" + 다시 시도 | 다시 시도 (3) |
| r3-camera-capture-sheet.dc.html | 캡처 저장 위치 | 표준 | 86 | S3 over the camera: checkbox rows 장치에 저장 (on) / 휴대폰 갤러리에 저장 (off) | 캡처 |
| r3-common-empty-states.dc.html | 빈 목록 (4 frames) | 표준 | 30, 61, 64, 131 | 1: 수집 기록 empty; 2: 전송 기록 empty; 3: 장치 휴지통 empty; 4: 알림 empty (each a B3 under its detail top bar, bar lit for its tab) | — |

**Batch 5 — 장치와 파일 (14 boards)**

| File | 제목 | Theme | Inventory ids | State and data | Primary |
|---|---|---|---|---|---|
| r3-device-root.dc.html | 장치 | 표준 | 1, 98, 112 | 현재 장치 GEO-X-1 (photo 56, 연결됨, figures 54°C / 512 GB · 57% 남음); 확인할 일 "GEO-X-1 전송 1건이 실패했습니다." + 숨기기; 다른 장치 GEO-X-2; 주변에서 찾은 장치 "[장치 이름]" + 등록; bell and "+"; bar 장치 lit | — |
| r3-device-detail.dc.html | 장치 상세 | 표준 | 4, 79 | H1 GEO-X-1 + photo; 상태 figures (54°C; 512 GB · 57% 남음; CPU · GPU 3% · 0%; 메모리 2.0 GB / 15.1 GB); 센서 rows (카메라 1920 × 1080 수신 중; 위치(GNSS) 정밀 위치 · 위성 12개; 움직임(IMU) 수신 중); start of 연결 | — |
| r3-device-detail-lower.dc.html | 장치 상세 · 아래 | 표준 | 4, 95, 117 | 연결 (연결 방법 같은 네트워크 ›, 장치 Wi-Fi GEON-5G ›), 데이터 (장치 파일 ›, 휴지통 3개 ›), 관리 (caption "재부팅과 전원 종료는 수집 중에는 쓸 수 없습니다." + 재부팅 / 전원 종료 danger-outline 52), 이 휴대폰 (등록 해제) | — |
| r3-device-detail-offline.dc.html | 장치 상세 · 연결 끊김 | 표준 | 5, 137 | strip "GEO-X-1 연결이 끊겼습니다 · 마지막 응답 5분 전" + 다시 연결 / 연결 방법; figures with "마지막 값 기준"; sensors 미확인; disabled rows "연결되면 쓸 수 있습니다." | — |
| r3-device-connect-sheet.dc.html | 연결 방법 | 표준 | 6, 117, 118 | S3 over 장치 상세: 같은 네트워크에서 다시 찾기 · 장치에 직접 연결 · 블루투스로 기본 연결 (with "가까운 장치를 찾으려면 블루투스 권한이 필요합니다.") · QR 코드로 다시 등록 | (choice rows) |
| r3-device-dialogs.dc.html | 장치 확인 (4 frames) | 표준 | 7, 96, 114, 120 | 1: S1b 재부팅; 2: S1b 전원 종료 disabled during a run ("수집 중에는 전원을 끌 수 없습니다."); 3: S1b 등록 해제; 4: S1a "GEO-X-1에 직접 연결할까요?" | 재부팅 · 전원 종료 (disabled) · 등록 해제 · 직접 연결 |
| r3-device-wifi.dc.html | 장치 Wi-Fi (2 frames) | 표준 | 73, 74, 75 | 1: 현재 네트워크 GEON-5G; 주변 네트워크 with GEON-2G expanded (password field) and 숨겨진 네트워크 추가; 2: permission block "주변 네트워크를 찾으려면 위치 권한이 필요합니다." | 적용 · 권한 허용 |
| r3-device-wifi-dialogs.dc.html | 연결 상태와 적용 (2 frames) | 표준 | 77, 78 | 1: S2 "연결 상태" (휴대폰 Wi-Fi GEON-5G · 장치 Wi-Fi GEON-5G · "같은 네트워크입니다."); 2: S1a "GEO-X-1을 GEON-2G에 연결할까요?" | 닫기 · 적용 |
| r3-common-menus.dc.html | 메뉴 (4 frames) | 표준 | 8, 28, 39, 76 | ⋮ open on 1: 장치 상세 (연결 끊기); 2: 수집 결과 (기록을 휴지통으로); 3: 장치 파일 (선택, 휴지통, 휴지통으로 이동); 4: 장치 Wi-Fi (연결 상태) | — |
| r3-device-files-browse.dc.html | 장치 파일 | 표준 | 40, 41 | location "수집 자료 › 20261019_1636"; search; picker "유형 · 전체"; day groups (오늘, 9월 16일); rows 20261019_1636 · 1,204개 · 1.2 GB and 20260915_data · 5,049개 파일 · 14.7 GB with aligned size column; snackbar "20260915_data를 휴지통으로 옮겼습니다." + 실행 취소 | — |
| r3-device-files-states.dc.html | 장치 파일 · 상태 (4 frames) | 표준 | 37 | 1: "파일이 없습니다"; 2: "검색 결과가 없습니다"; 3: strip "GEO-X-1 연결이 끊겼습니다. 마지막으로 본 목록입니다." over the last list; 4: "파일 목록을 불러오지 못했습니다" + 다시 시도 | 다시 시도 (4) |
| r3-device-file-preview.dc.html | 미리 보기 (2 frames) | 표준 (fixed media) | 42 | 1: image preview on the black stage (a neutral grey picture area, file name in the bar); 2: "미리 볼 수 없는 파일입니다" / "이미지와 텍스트 파일만 앱에서 볼 수 있습니다." | — |
| r3-device-trash.dc.html | 장치 휴지통 | 표준 | 63 | rows with 분류 (장치 데이터 / 업로드 원본 / 수집 기록) and state (복원 가능 / 삭제 처리 중 / 복구 확인 필요), trailing "원래 위치로 복원" or "복원할 수 없음"; ⋮; retention note | — |
| r3-device-trash-dialogs.dc.html | 휴지통 확인 (2 frames) | 표준 | 38, 65 | 1: S1a "20260915_data를 휴지통으로 옮길까요?" over 장치 파일; 2: S1b "GEO-X-1 휴지통을 비울까요?" ("표시된 12개 항목을 영구 삭제합니다. 되돌릴 수 없습니다.") | 휴지통으로 이동 · 영구 삭제 |

**Batch 6 — 등록, 직접 연결, 설정, 알림 (14 boards)**

| File | 제목 | Theme | Inventory ids | State and data | Primary |
|---|---|---|---|---|---|
| r3-pair-first-run.dc.html | 장치 등록 | 표준 | 2, 111, 112, 113 | the scene with gear; heading, body, photo 120, "등록에 필요한 권한" rows; tertiary "장치 없이 서버 파일 보기"; no bar | QR 코드 스캔 |
| r3-pair-scanner.dc.html | QR 코드 스캔 (2 frames) | 표준 (fixed media) | 121, 122, 123 | 1: live scanner (240 dp frame, "장치 본체의 QR 코드를 사각형 안에 맞추세요.", torch, 코드 직접 입력); 2: permission block "카메라 권한이 꺼져 있습니다" + 설정에서 허용 + 코드 직접 입력 | — · 설정에서 허용 |
| r3-pair-sheets.dc.html | 등록 시트 (2 frames) | 표준 | 118, 119, 124, 125 | 1: S4 "등록 코드 입력" (helper "QR 아래에 있는 전체 코드를 입력하세요."); 2: S4 "장치 등록" over the frozen scanner (photo 40, GEO-X-1, "등록 코드 •••• 4DE0", "블루투스로 장치를 찾아 이 휴대폰에 등록합니다.") | 다음 · 등록 |
| r3-pair-progress-error.dc.html | 등록 진행과 오류 (2 frames) | 표준 | 126 | 1: "인증하는 중입니다." + 4 dp bar + 취소; 2: "등록하지 못했습니다" / "장치를 찾지 못했습니다. 장치 전원과 거리를 확인하세요." + 취소 | — · 다시 시도 |
| r3-pair-local-network.dc.html | 로컬 네트워크 권한 | 표준 | 115, 116, 134 | 장치 root with the block "같은 네트워크의 장치를 찾으려면 로컬 네트워크 권한이 필요합니다." and the device strip "GEO-X-1 · 연결 권한이 필요합니다" | 권한 허용 |
| r3-pair-direct.dc.html | 직접 연결 | 표준 | 127 | note; 주변 장치 "검색 중"; rows GEO-X-1 연결 가능, "이름 없는 장치" 현재 연결할 수 없음; bar 장치 lit | — |
| r3-pair-direct-states.dc.html | 직접 연결 · 상태 (4 frames) | 표준 | 128, 129 | 1: "근처 기기 권한이 필요합니다" + 권한 허용; 2: "장치를 찾고 있습니다"; 3: "찾은 장치가 없습니다" + hint + 다시 찾기; 4: "이 휴대폰은 직접 연결을 지원하지 않습니다" | 권한 허용 · — · 다시 찾기 · — |
| r3-pair-direct-connect.dc.html | 직접 연결 · 진행 (3 frames) | 표준 | 128 | 1: "장치 연결 준비 중" + 연결 취소; 2: "장치 인증과 제어 연결을 확인하고 있습니다."; 3: "장치에 연결하지 못했습니다." + 장치 다시 확인 | — · — · 장치 다시 확인 |
| r3-settings-root.dc.html | 설정 | 표준 | 66 | developer mode on: 화면 (테마 "시스템 · 표준 / 그래파이트"), 알림 (알림 기준 "6개 중 5개"), 서버 (서버 연결 "운영 서버"), 정보 (도움말, 앱 정보 1.18.0), 고급 (개발자 도구); bar 설정 lit | — |
| r3-settings-theme.dc.html | 화면 테마 | 표준 | 67 | §4.5: preview, mode segmented on 시스템 따름, 밝은 테마 (표준 selected), 어두운 테마 (그래파이트 selected) with captions | — |
| r3-settings-about-help.dc.html | 앱 정보와 도움말 (2 frames) | 표준 | 68, 69, 87 | 1: 앱 정보 with snackbar "개발자 모드까지 3번 남았습니다."; 2: 도움말 (수집 · 전송 · 연결 문제) | — |
| r3-settings-alerts.dc.html | 알림 기준 (2 frames) | 표준 | 70, 71, 72 | 1: 장치 상태 (저장 공간 85% 이상, 장치 온도 80°C 이상 with −/+), 수집, 전송 switches, note; 2: the permission block at the top | — · 권한 허용 |
| r3-settings-server.dc.html | 서버 연결 (3 frames) | 표준 | 44 | 1: 상태 연결됨, 서버 운영 서버, 환경 운영, 직원 [직원 ID], 프로젝트 [프로젝트 ID]; 2: S4 "다시 로그인" (masked field "직원 토큰"); 3: "서버 연결 정보가 없습니다" | 연결 확인 · 다시 로그인 · — |
| r3-alerts.dc.html | 알림 (2 frames) | 표준 | 130, 132 | 1: 오늘 — "GEO-X-1 온도가 82°C입니다", "GEO-X-1 데이터 수집 작업이 시작되었습니다", "GEO-X-1 20261019_1636 전송을 마쳤습니다"; 어제 — one row; trailing 삭제; 2: S1b "알림을 모두 지울까요?" | — · 모두 지우기 |

**Batch 7 — 전송 (14 boards)**

| File | 제목 | Theme | Inventory ids | State and data | Primary |
|---|---|---|---|---|---|
| r3-transfer-root.dc.html | 전송 | 표준 | 34 | title 전송 + switcher GEO-X-1; 보내는 중 (20261019_1636 64%, "768 MB / 1.2 GB · 12.3 MB/초 · 약 36초 남음"); 확인이 필요한 전송 (20261018_0912 + 다시 보내기); 보낼 수집 (20260915_data + 보내기); 서버 수신 확인; "GEO-X-2 · 확인이 필요한 전송 1건"; 서버; sticky action; bar 전송 lit | 파일 보내기 |
| r3-transfer-files-send.dc.html | 파일 보내기 | 표준 | 36 | browser in send mode, 20261019_1636 selected; action area "20261019_1636 · 1,204개 · 1.2 GB", 받는 서버 운영 서버 | 전송 시작 |
| r3-transfer-send.dc.html | 전송 확인 (2 frames) | 표준 | 55, 56 | 1: review from the result (H1 신월4거리 북측 보조 구간; facts 파일 1,204개 · 용량 1.2 GB · 보내는 장치 GEO-X-1 · 수집 16:36; 받는 서버 운영 서버; "장치 원본은 그대로 보관됩니다."); 2: "받을 서버가 없습니다" | 전송 시작 · — |
| r3-transfer-detail-sending.dc.html | 전송 상세 · 보내는 중 | 표준 | 57 | badge 보내는 중 "GEO-X-1 → 운영 서버"; progress 64%; figures 768 MB / 1.2 GB · 약 36초 · 12.3 MB/초 · 812 / 1,204개; 처리 상태 rows | 전송 취소 (danger-outline) |
| r3-transfer-detail-verified.dc.html | 전송 상세 · 수신 확인 | 표준 | 57 | badge 수신 확인; receipt row 내용 일치; 관리 block "서버와 다시 대조한 뒤 장치 원본을 휴지통으로 옮길 수 있습니다." + 장치 원본 정리 | 완료 보고 |
| r3-transfer-detail-states.dc.html | 전송 상세 · 상태 (3 frames) | 표준 | 57 | 1: 실패 "연결이 끊겨 멈췄습니다."; 2: 내용 불일치 "서버에 받은 내용이 보낸 내용과 일치하지 않습니다."; 3: 수신 확인 중 "보내기는 끝났고 서버 수신을 확인하고 있습니다." | 다시 보내기 · 다시 보내기 · 수신 확인 |
| r3-transfer-dialogs.dc.html | 전송 확인 대화상자 (3 frames) | 표준 | 35, 58, 59, 62 | 1: S1b "20261019_1636 전송을 취소할까요?"; 2: S1a "장치 원본을 휴지통으로 옮길까요?"; 3: S1a "전송 기록을 삭제할까요?" | 전송 취소 · 휴지통으로 이동 · 기록 삭제 |
| r3-transfer-log.dc.html | 전송 기록 | 표준 | 60 | segmented 전체 \| 실패 \| 완료 \| 취소; rows incl. one 취소됨 | — |
| r3-transfer-server.dc.html | 서버 수신 결과 | 표준 | 43 | "운영 서버 · 마지막 동기화 16:36"; segmented 수신 결과 \| 휴지통; job rows (20260915_data · 9월 16일 수신 · 896 MB); "이전 수신 결과 더 불러오기" | — |
| r3-transfer-server-job.dc.html | 서버 작업과 영수증 (2 frames) | 표준 | 43, 48 | 1: job detail (files, "서버 수신 영수증 ›", ⋮); 2: S2 "서버 수신 영수증" (badge 내용 일치; 파일 · 용량 · 완료 · 검증 시각) | — · 닫기 |
| r3-transfer-server-trash.dc.html | 서버 휴지통 | 표준 | 43, 49, 50 | 휴지통 segment: rows 복원 / 복원할 수 없음 / 삭제 처리 중; "전체 128개 중 100개를 표시합니다."; snackbar "서버 휴지통으로 옮겼습니다." + 실행 취소 | — |
| r3-transfer-server-states.dc.html | 서버 · 상태 (4 frames) | 표준 | 44, 49 | 1: "서버 연결 정보가 없습니다" + 서버 연결; 2: "다시 로그인해야 합니다" + 다시 로그인; 3: "수신 결과가 없습니다"; 4: "앱에서 볼 수 없는 파일입니다" | — |
| r3-transfer-server-video.dc.html | 서버 영상 (2 frames) | 표준 (fixed media) | 43 | 1: video playing on the black stage with play/pause and a position bar; 2: "영상 미리 보기를 준비하지 못했습니다. 다시 시도하세요." | — · 다시 시도 |
| r3-transfer-server-trash-confirm.dc.html | 서버 휴지통 이동 | 표준 | 45 | S1a "20260915_data를 서버 휴지통으로 옮길까요?" over the job detail | 휴지통으로 이동 |

**Batch 8 — 개발자 도구 (13 boards)** — every board shows a developer surface; bar 설정 lit except editors.

| File | 제목 | Theme | Inventory ids | State and data | Primary |
|---|---|---|---|---|---|
| r3-dev-home.dc.html | 개발자 도구 | 표준 | 92, 93, 95, 98, 110 | switches 개발자 모드 (on), 화면에 기술 정보 표시; sections 장치 · 수집 작업 · 서버·전송 · 진단 with the rows of §7.2 #92 | — |
| r3-dev-terminal-diagnostics.dc.html | 원격 터미널과 연결 진단 (2 frames) | 표준 | 93, 97 | 1: `dev/terminal` (focused): "단일 명령 · 최대 15초 · 출력 64KB", field "명령어" with "pwd", output `mono`; 2: `dev/diagnostics` P9 with "기록 방식" disclosure | 명령 실행 · 진단 ZIP 저장 |
| r3-dev-fan.dc.html | 팬 제어 (4 frames) | 표준 | 95 | 1: 자동 "자동 온도 제어"; 2: 수동 slider 40% (20–100, step 10) with −/+; 3: "팬 상태를 확인하지 못했습니다." + 다시 시도; 4: "이 장치에서는 팬 제어를 사용할 수 없습니다" | 적용 (2) · 다시 시도 (3) |
| r3-dev-device-tools.dc.html | 네트워크 주소와 센서 테스트 (2 frames) | 표준 | — (new `dev/network`, `dev/sensor-test`) | 1: Wi-Fi · 유선 · 직접 연결 · Tailscale rows with "[IP 주소]" in `mono`, "조회 16:36"; 2: "5초 동안 장치에서 새 카메라 프레임과 GPS 데이터 수신을 확인합니다." rows 카메라 수신 확인 · GPS 미확인 | 다시 조회 · 테스트 |
| r3-dev-upload-targets.dc.html | 업로드 서버 (2 frames) | 표준 | 88, 91 | 1: list (운영 서버 + "[서버 주소]" mono) with "+"; 2: editor (focused): 서버 ID · 표시 이름 · HTTPS 주소 · 접근 토큰 (masked) + 서버 삭제 | — · 저장 |
| r3-dev-pipeline-detail.dc.html | 작업 관리 | 표준 | 99, 110 | 데이터 수집: facts, switch 부팅 시 실행, rows 실행 로그 · 작업 설정 · 수집 정책 · 저장 결과 · 보정 정보 중계, danger-outline 등록 해제 | 수집 준비 |
| r3-dev-pipeline-forms.dc.html | 작업 등록 · 설정 · 정책 (3 frames) | 표준 | 100, 107 | 1: 작업 등록 (표시 이름, 작업 폴더 picker, discovered entry note, switch); 2: 작업 설정 with "장치 설정 다시 불러오기"; 3: 수집 정책 (카메라 · GNSS · IMU 필수/선택/사용 안 함, 최소 여유 공간, 예상 결과, 결과 저장소, 상대 경로, 파일 패턴) | 작업 등록 · 설정 저장 · 정책 저장 |
| r3-dev-pipeline-picker.dc.html | 폴더 선택 | 표준 | 101 | P6 pick mode with location line, folder rows | 이 폴더 선택 |
| r3-dev-pipeline-logs.dc.html | 실행 로그 (2 frames) | 표준 | 102, 106 | 1: log viewer with picker, live line "실시간", following; 2: S3 "실행 로그 선택" (현재 실행 · 최근 실행 · 보관 로그 with start times and sizes) | — |
| r3-dev-states.dc.html | 개발자 빈 상태 (4 frames) | 표준 | 89, 103, 104, 105 | 1: "등록된 업로드 서버가 없습니다" + 서버 추가; 2: "선택할 폴더가 없습니다"; 3: "저장된 실행 로그가 없습니다"; 4: "바꿀 수 있는 설정 값이 없습니다" | — |
| r3-dev-server-proxy.dc.html | 장치 경유 서버 조회 (3 frames) | 표준 | 51, 52, 54 | 1: server picker field + session rows; 2: S3 "서버 선택"; 3: "장치를 같은 네트워크나 직접 연결로 연결하면 조회할 수 있습니다." | — |
| r3-dev-server-profiles.dc.html | 서버 프로필과 서버 휴지통 (2 frames) | 표준 | 46, 47 | 1: `dev/server-profiles` editor (프로필 ID · 표시 이름 · 환경 개발 \| 시험 \| 운영 · HTTPS 서버 주소 · 직원 ID · 프로젝트 ID · 직원 토큰 masked) + 이 프로필로 연결 + 프로필 삭제; 2: `dev/server-trash` with "표시된 100개 비우기" and the shown-versus-total line | 암호화해 저장 · 표시된 100개 비우기 (danger-outline) |
| r3-dev-dialogs.dc.html | 개발자 확인 (4 frames) | 표준 | 90, 94, 108, 109 | 1: S1b "운영 서버를 삭제할까요?" over 업로드 서버; 2: S1b "GEO-X-1에서 명령을 실행할까요?" with `pwd`; 3: S1b "데이터 수집 등록을 해제할까요?"; 4: S1a "장치 설정을 다시 불러올까요?" (dismiss 초안 유지) | 서버 삭제 · 실행 · 등록 해제 · 다시 불러오기 |

Totals: 107 boards in 8 batches (10 · 14 · 14 · 14 · 14 · 14 · 14 · 13).

### 11.2 Coverage index (inventory id → boards)

| # | id | Boards |
|---|---|---|
| 1 | device-tab | r3-device-root |
| 2 | device-first-run | r3-pair-first-run |
| 3 | device-offline | r3-work-focus-states |
| 4 | device-detail | r3-device-detail, r3-device-detail-lower |
| 5 | device-detail-offline | r3-device-detail-offline |
| 6 | device-connect-method | r3-device-connect-sheet |
| 7 | device-power-confirm | r3-device-dialogs |
| 8 | device-menu | r3-common-menus |
| 9 | tasks-tab | r3-work-root, r3-theme-work-root-navy-light, r3-theme-work-root-contrast, r3-theme-work-root-graphite, r3-theme-work-root-navy-dark, r3-theme-work-root-dim, r3-work-root-run, r3-work-focus-states, r3-work-focus-states-2 |
| 10 | task-home-empty | r3-work-root-empty |
| 11 | task-pipeline-list | r3-work-task-sheet |
| 12 | task-list-empty | r3-work-task-sheet |
| 13 | task-detail | r3-work-task-detail |
| 14 | task-detail-missing | r3-work-missing-states |
| 15 | ticket-detail | r3-work-ticket, r3-work-ticket-scrolled |
| 16 | ticket-detail-missing | r3-work-missing-states |
| 17 | ticket-start-confirm | r3-work-start-sheet, r3-work-start-sheet-failed |
| 18 | survey-prepare | r3-work-prepare |
| 19 | survey-start-confirm | r3-work-start-sheet, r3-work-start-sheet-failed |
| 20 | survey-project-menu | r3-work-prepare |
| 21 | active-run | r3-work-run, r3-theme-run-navy-light, r3-theme-run-contrast, r3-theme-run-graphite, r3-theme-run-navy-dark, r3-theme-run-dim, r3-work-run-outside |
| 22 | active-run-variants | r3-work-run-outside, r3-work-run-states |
| 23 | active-run-stop-confirm | r3-work-run-dialogs |
| 24 | active-run-menu | r3-work-run |
| 25 | run-result | r3-work-result, r3-work-result-lower |
| 26 | run-result-missing | r3-work-missing-states |
| 27 | run-result-ticket-report | r3-work-run-dialogs |
| 28 | run-result-menu | r3-common-menus |
| 29 | task-history | r3-work-history, r3-work-history-entry |
| 30 | history-empty | r3-common-empty-states |
| 31 | history-log | r3-work-history-entry |
| 32 | quality-detail-sheet | r3-work-result-lower |
| 33 | history-undo | r3-work-history |
| 34 | transfers-tab | r3-transfer-root |
| 35 | transfer-cancel-confirm | r3-transfer-dialogs |
| 36 | data-hub | r3-transfer-files-send |
| 37 | data-empty-offline | r3-device-files-states |
| 38 | data-delete-confirm | r3-device-trash-dialogs |
| 39 | data-menu | r3-common-menus |
| 40 | data-undo | r3-device-files-browse |
| 41 | device-storage-route | r3-device-files-browse |
| 42 | device-file-preview-empty | r3-device-file-preview |
| 43 | server-data | r3-transfer-server, r3-transfer-server-job, r3-transfer-server-trash, r3-transfer-server-video |
| 44 | server-no-profile | r3-settings-server, r3-transfer-server-states |
| 45 | server-job-trash-confirm | r3-transfer-server-trash-confirm |
| 46 | server-empty-trash-confirm | r3-dev-dialogs-2, r3-dev-server-profiles |
| 47 | server-remove-profile-confirm | r3-dev-dialogs-2, r3-dev-server-profiles |
| 48 | server-receipt | r3-transfer-server-job |
| 49 | server-detail-empty | r3-transfer-server-trash, r3-transfer-server-states |
| 50 | server-undo | r3-transfer-server-trash |
| 51 | server-proxy-storage | r3-dev-server-proxy |
| 52 | proxy-empty | r3-dev-server-proxy |
| 53 | proxy-delete-confirm | r3-dev-dialogs-2 |
| 54 | proxy-target-menu | r3-dev-server-proxy |
| 55 | upload-confirm | r3-transfer-send |
| 56 | upload-no-target | r3-transfer-send |
| 57 | upload-progress | r3-transfer-detail-sending, r3-transfer-detail-verified, r3-transfer-detail-states |
| 58 | upload-cancel-confirm | r3-transfer-dialogs |
| 59 | upload-source-delete-confirm | r3-transfer-dialogs |
| 60 | upload-queue | r3-transfer-log |
| 61 | upload-queue-empty | r3-common-empty-states |
| 62 | upload-record-delete-confirm | r3-transfer-dialogs |
| 63 | local-trash | r3-device-trash |
| 64 | local-trash-empty | r3-common-empty-states |
| 65 | local-trash-purge-confirm | r3-device-trash-dialogs |
| 66 | settings-tab | r3-settings-root |
| 67 | theme-dialog | r3-settings-theme |
| 68 | about-dialog | r3-settings-about-help |
| 69 | help-dialog | r3-settings-about-help |
| 70 | alert-settings | r3-settings-alerts |
| 71 | notification-rationale | r3-settings-alerts |
| 72 | notification-prompt | r3-settings-alerts |
| 73 | network-settings | r3-device-wifi |
| 74 | wifi-scan-rationale | r3-device-wifi |
| 75 | wifi-scan-prompt | r3-device-wifi |
| 76 | network-menu | r3-common-menus |
| 77 | connection-info | r3-device-wifi-dialogs |
| 78 | wifi-apply-confirm | r3-device-wifi-dialogs |
| 79 | sensors-route-unreachable | r3-device-detail |
| 80 | gnss-map | r3-map-full-zone, r3-map-full-run, r3-map-full-landscape |
| 81 | gnss-details-sheet | r3-map-full-run, r3-map-panel |
| 82 | gnss-location-rationale | r3-map-panel |
| 83 | gnss-location-prompt | r3-map-panel |
| 84 | camera-preview | r3-camera-preview |
| 85 | camera-preview-states | r3-camera-states |
| 86 | camera-capture-options | r3-camera-capture-sheet |
| 87 | developer-mode-unlock | r3-settings-about-help |
| 88 | upload-target-settings | r3-dev-upload-targets |
| 89 | upload-targets-empty | r3-dev-states |
| 90 | upload-target-delete-confirm | r3-dev-dialogs |
| 91 | upload-target-editor | r3-dev-upload-targets |
| 92 | developer-home | r3-dev-home |
| 93 | developer-menu | r3-dev-home, r3-dev-terminal-diagnostics |
| 94 | remote-command-confirm | r3-dev-dialogs |
| 95 | admin-tools | r3-device-detail-lower, r3-dev-home, r3-dev-fan |
| 96 | admin-power-confirm | r3-device-dialogs |
| 97 | connection-diagnostics | r3-dev-terminal-diagnostics |
| 98 | legacy-dashboard-unrouted | r3-work-focus-states-2, r3-device-root, r3-dev-home |
| 99 | pipeline-detail-dev | r3-dev-pipeline-detail |
| 100 | pipeline-editor | r3-dev-pipeline-forms |
| 101 | pipeline-picker | r3-dev-pipeline-picker |
| 102 | pipeline-logs | r3-dev-pipeline-logs |
| 103 | pipeline-picker-empty | r3-dev-states |
| 104 | pipeline-logs-empty | r3-dev-states |
| 105 | pipeline-config-empty | r3-dev-states |
| 106 | pipeline-log-menu | r3-dev-pipeline-logs |
| 107 | pipeline-config | r3-dev-pipeline-forms |
| 108 | pipeline-remove-confirm | r3-dev-dialogs |
| 109 | pipeline-reload-confirm | r3-dev-dialogs |
| 110 | pipeline-config-reach | r3-dev-home, r3-dev-pipeline-detail |
| 111 | onboarding | r3-pair-first-run |
| 112 | connection-hub-unrouted | r3-device-root, r3-pair-first-run |
| 113 | connection-hub-no-device | r3-pair-first-run |
| 114 | connection-direct-confirm | r3-device-dialogs |
| 115 | local-network-rationale | r3-work-focus-states-2, r3-pair-local-network |
| 116 | local-network-prompt | r3-pair-local-network |
| 117 | device-list-unrouted | r3-device-detail-lower, r3-device-connect-sheet |
| 118 | bluetooth-rationale | r3-device-connect-sheet, r3-pair-sheets |
| 119 | bluetooth-prompt | r3-pair-sheets |
| 120 | forget-device-confirm | r3-device-dialogs |
| 121 | qr-scanner | r3-pair-scanner |
| 122 | camera-rationale | r3-pair-scanner |
| 123 | camera-permission-prompt | r3-pair-scanner |
| 124 | qr-manual-entry | r3-pair-sheets |
| 125 | pairing-confirm | r3-pair-sheets |
| 126 | pairing-progress-error | r3-pair-progress-error |
| 127 | wifi-direct | r3-pair-direct |
| 128 | wifi-direct-permission-empty | r3-pair-direct-states, r3-pair-direct-connect |
| 129 | nearby-wifi-prompt | r3-pair-direct-states |
| 130 | alerts | r3-alerts |
| 131 | alerts-empty | r3-common-empty-states |
| 132 | alerts-clear-confirm | r3-alerts |
| 133 | startup-loading | r3-ds-startup, r3-common-loading |
| 134 | startup-permission-prompt | r3-work-focus-states-2, r3-pair-local-network |
| 135 | startup-auto-discovery | r3-work-root-empty |
| 136 | startup-ticket-report-retry | r3-work-root, r3-work-result |
| 137 | global-connection-banner | r3-work-run-states, r3-device-detail-offline |
| 138 | no-other-dialog-types | r3-ds-overlays |

Every one of the 138 ids appears on at least one board (checked by script against §7.2).

---

## Appendix A. Contrast script over the final tables

**A.1 How it was run.** `tools/contrast.py` provides `cr()` (WCAG 2.x contrast of two sRGB hex colours) and `blend()`; the driver below parses the §4.2 table of this file (a cell "=" repeats the value to its left; `@ n %` alphas and non-hex cells are ignored), adds the aliases `info` = `primary`, `infoSoft` = `primaryTint`, `focus` = `primary`, `switchTrackOn` = `primary`, and checks 63 pairs in six palettes plus 13 fixed pairs. It was run with `python -B -` from PowerShell on 2026-10-19 after the last change to §4.2; §4.4 is the same output as a table.

**A.2 Driver (verbatim).**

```python
import re, sys
BASE = r'C:\Users\pjmsm\AppData\Local\Temp\claude\D--JetsonController\8b1a0d3f-7384-49d6-a04b-4aa33844919f\scratchpad\design-r3'
sys.path.insert(0, BASE + r'\tools')
from contrast import cr, blend
lines = open(BASE + r'\R3-final.md', encoding='utf-8').read().split('\n')
s = next(i for i, l in enumerate(lines) if l.startswith('### 4.2 Role table'))
COLS = ['표준', '네이비(밝게)', '고대비', '그래파이트', '네이비(어둡게)', '흐림']
T = {c: {} for c in COLS}
for l in lines[s + 1:]:
    if l.startswith('### ') or l.startswith('<!--'):
        break
    if not l.startswith('| `'):
        continue
    cells = [c.strip() for c in l.strip().strip('|').split('|')]
    role = re.search(r'`([A-Za-z]+)`', cells[0]).group(1)
    prev = None
    for c, v in zip(COLS, cells[1:]):
        if v == '=':
            v = prev
        m = re.search(r'#[0-9A-Fa-f]{6}', v or '')
        T[c][role] = m.group(0).upper() if m else None
        prev = v
for c in COLS:
    t = T[c]
    t['info'] = t['primary']; t['infoSoft'] = t['primaryTint']; t['focus'] = t['primary']; t['switchTrackOn'] = t['primary']
P = lambda a, b, th=4.5: (f'{a} / {b}', a, b, th)
PAIRS = [P('text','surface'), P('text','canvas'), P('text','field'), P('text','fieldPressed'), P('text','primaryTint'),
 P('textSecondary','surface'), P('textSecondary','sunken'), P('textSecondary','field'), P('textSecondary','fieldPressed'),
 P('textSubtle','surface'), P('textSubtle','sunken'), P('textSubtle','canvas'), P('primary','surface'), P('primary','fieldPressed'),
 P('onPrimary','primary'), P('onPrimary','primaryPressed'), P('primaryTintText','primaryTint'),
 P('success','surface'), P('success','successSoft'), P('warning','surface'), P('warning','warningSoft'),
 P('danger','surface'), P('danger','dangerSoft'), P('danger','fieldPressed'), P('info','infoSoft'),
 P('pending','surface'), P('pending','pendingSoft'), P('neutral','surface'), P('neutral','neutralSoft'),
 P('text','successSoft'), P('text','warningSoft'), P('text','dangerSoft'), P('onDanger','danger'), P('onDanger','dangerPressed'),
 P('disabledText','disabledFill'), P('onTopBar','topBar'), P('onTopBarIcon','topBar'), P('navSelected','nav'), P('navUnselected','nav'),
 P('onAttention','attention'), P('onInverse','inverse'), P('inverseAction','inverse'), P('textSecondary','mapPlate'),
 P('borderStrong','surface',3), P('borderStrong','field',3), P('borderStrong','fieldPressed',3), P('focus','surface',3), P('focus','canvas',3),
 P('navFocus','nav',3), P('navFocus','topBar',3), P('attention','nav',3), P('attention','topBar',3),
 P('chromeSuccess','topBar',3), P('chromeWarning','topBar',3), P('chromeDanger','topBar',3), P('chromeInfo','topBar',3),
 P('chromePending','topBar',3), P('chromeNeutral','topBar',3), P('switchTrackOff','surface',3), P('switchTrackOff','fieldPressed',3),
 P('switchThumbOff','switchTrackOff',3), P('switchTrackOn','surface',3), P('switchThumbOn','switchTrackOn',3)]
fails = 0
print('pair                                   min    ' + '  '.join(f'{c:>8}' for c in COLS))
for label, f, b, th in PAIRS:
    vals = [cr(T[c][f], T[c][b]) for c in COLS]
    fails += sum(v < th for v in vals)
    print(f'{label:<36} {th:>4}  ' + '  '.join(f'{v:8.2f}' for v in vals) + ('' if min(vals) >= th else '  FAIL'))
print(f'{len(PAIRS)} pairs x 6 palettes = {len(PAIRS)*6} checks, failures: {fails}')
W = '#FFFFFF'; plate = blend('#000000', '#FFFFFF', 0.72)
fixed = [('zone #1F52B8 / casing', '#1F52B8', W, 3), ('trail #22704B / casing', '#22704B', W, 3), ('trail #8A5A06 / casing', '#8A5A06', W, 3),
 ('trail #5D667B / casing', '#5D667B', W, 3), ('gap #B3372C / casing', '#B3372C', W, 3), ('device #121A3D / casing', '#121A3D', W, 3),
 (f'#FFFFFF / plate {plate}', '#FFFFFF', plate, 4.5), ('#88CCA5 / #1E1F21', '#88CCA5', '#1E1F21', 4.5), ('#E3BC7A / #1E1F21', '#E3BC7A', '#1E1F21', 4.5),
 ('#BDBFC4 / #1E1F21', '#BDBFC4', '#1E1F21', 4.5), ('#E9938C / #1E1F21', '#E9938C', '#1E1F21', 4.5), ('#FFFFFF / #1E1F21', '#FFFFFF', '#1E1F21', 4.5),
 ('map focus #1F52B8 / white halo', '#1F52B8', W, 3)]
ff = 0
for name, a, b, th in fixed:
    r = cr(a, b); ff += r < th
    print(f'{name:<36} {th:>4}  {r:8.2f}' + ('' if r >= th else '  FAIL'))
print(f'{len(fixed)} fixed checks, failures: {ff}')
```

**A.3 Output (verbatim).**

```text
pair                                   min          표준   네이비(밝게)       고대비     그래파이트  네이비(어둡게)        흐림
text / surface                        4.5     17.03     17.03     18.89     13.59     13.57     11.24
text / canvas                         4.5     14.91     14.91     18.89     14.78     14.77     12.59
text / field                          4.5     15.33     15.33     17.15     11.83     11.82      9.43
text / fieldPressed                   4.5     14.39     14.39     15.54     10.60     10.61      8.32
text / primaryTint                    4.5     14.62     14.62     15.76     11.23     11.00      8.74
textSecondary / surface               4.5      7.41      7.41     13.13      8.96      8.95      8.21
textSecondary / sunken                4.5      6.79      6.79     11.92      9.39      9.36      8.80
textSecondary / field                 4.5      6.67      6.67     11.92      7.81      7.80      6.88
textSecondary / fieldPressed          4.5      6.26      6.26     10.80      7.00      7.00      6.08
textSubtle / surface                  4.5      5.56      5.56     10.00      6.42      6.38      6.49
textSubtle / sunken                   4.5      5.09      5.09      9.08      6.73      6.67      6.96
textSubtle / canvas                   4.5      4.87      4.87     10.00      6.99      6.94      7.27
primary / surface                     4.5      7.10      7.10      9.91      7.28      7.26      6.79
primary / fieldPressed                4.5      6.00      6.00      8.15      5.68      5.68      5.03
onPrimary / primary                   4.5      7.10      7.10      9.91      7.28      7.26      6.79
onPrimary / primaryPressed            4.5      8.56      8.56     12.39      9.25      9.30      8.62
primaryTintText / primaryTint         4.5      7.35      7.35     10.33      8.64      8.45      7.04
success / surface                     4.5      6.02      6.02      8.75      8.82      8.82      7.81
success / successSoft                 4.5      5.16      5.16      7.40      7.42      7.42      6.32
warning / surface                     4.5      5.92      5.92      9.75      9.22      9.22      8.18
warning / warningSoft                 4.5      5.24      5.24      8.41      7.89      7.89      6.72
danger / surface                      4.5      6.00      6.00      9.15      7.08      7.08      6.33
danger / dangerSoft                   4.5      5.08      5.08      7.53      6.10      6.10      5.24
danger / fieldPressed                 4.5      5.07      5.07      7.53      5.53      5.54      4.68
info / infoSoft                       4.5      6.09      6.09      8.26      6.02      5.89      5.28
pending / surface                     4.5      9.78      9.78     12.66     10.31     10.31      8.96
pending / pendingSoft                 4.5      7.73      7.73     10.10      8.58      8.58      7.13
neutral / surface                     4.5      5.75      5.75      9.72      7.67      7.68      7.16
neutral / neutralSoft                 4.5      5.04      5.04      8.37      6.33      6.34      5.65
text / successSoft                    4.5     14.59     14.59     15.97     11.44     11.43      9.10
text / warningSoft                    4.5     15.07     15.07     16.30     11.62     11.61      9.24
text / dangerSoft                     4.5     14.42     14.42     15.55     11.69     11.68      9.32
onDanger / danger                     4.5      6.00      6.00      9.15      7.08      7.08      6.33
onDanger / dangerPressed              4.5      7.52      7.52     11.74      8.57      8.57      7.48
disabledText / disabledFill           4.5      4.70      4.70      5.42      5.60      5.56      5.44
onTopBar / topBar                     4.5     17.03     16.92     18.89     13.59     13.57     11.24
onTopBarIcon / topBar                 4.5      7.41      8.66     13.13      8.96      8.95      8.21
navSelected / nav                     4.5      7.10     16.92      9.91     10.75     10.80     10.76
navUnselected / nav                   4.5      5.56      8.66      6.10      7.47      7.41      8.10
onAttention / attention               4.5      4.99      7.24      6.54      7.67      7.67      7.03
onInverse / inverse                   4.5     17.03     17.03     18.89      8.81      8.80      6.78
inverseAction / inverse               4.5      7.82      7.82     10.65      6.00      6.03      5.19
textSecondary / mapPlate              4.5      7.41      7.41     13.13      8.96      8.95      8.21
borderStrong / surface                  3      3.56      3.56      7.57      4.27      4.28      4.33
borderStrong / field                    3      3.21      3.21      6.87      3.72      3.73      3.63
borderStrong / fieldPressed             3      3.01      3.01      6.22      3.34      3.35      3.20
focus / surface                         3      7.10      7.10      9.91      7.28      7.26      6.79
focus / canvas                          3      6.22      6.22      9.91      7.92      7.90      7.61
navFocus / nav                          3      7.10      7.77      9.91      9.75      9.70      9.25
navFocus / topBar                       3      7.10      7.77      9.91      8.39      8.35      7.41
attention / nav                         3      4.99      7.24      6.54      8.20      8.19      7.84
attention / topBar                      3      4.99      7.24      6.54      7.05      7.05      6.28
chromeSuccess / topBar                  3      6.02      9.05      8.75      8.82      8.82      7.81
chromeWarning / topBar                  3      5.92      9.46      9.75      9.22      9.22      8.18
chromeDanger / topBar                   3      6.00      7.24      9.15      7.08      7.08      6.33
chromeInfo / topBar                     3      7.10      7.77      9.91      7.28      7.26      6.79
chromePending / topBar                  3      9.78     10.58     12.66     10.31     10.31      8.96
chromeNeutral / topBar                  3      5.75      8.66      9.72      7.67      7.68      7.16
switchTrackOff / surface                3      3.70      3.70      5.83      4.27      4.28      4.33
switchTrackOff / fieldPressed           3      3.12      3.12      4.79      3.34      3.35      3.20
switchThumbOff / switchTrackOff         3      3.70      3.70      5.83      4.27      4.28      4.33
switchTrackOn / surface                 3      7.10      7.10      9.91      7.28      7.26      6.79
switchThumbOn / switchTrackOn           3      7.10      7.10      9.91      7.28      7.26      6.79
63 pairs x 6 palettes = 378 checks, failures: 0
zone #1F52B8 / casing                   3      7.10
trail #22704B / casing                  3      6.02
trail #8A5A06 / casing                  3      5.92
trail #5D667B / casing                  3      5.75
gap #B3372C / casing                    3      6.00
device #121A3D / casing                 3     16.92
#FFFFFF / plate #474747               4.5      9.29
#88CCA5 / #1E1F21                     4.5      8.82
#E3BC7A / #1E1F21                     4.5      9.22
#BDBFC4 / #1E1F21                     4.5      8.96
#E9938C / #1E1F21                     4.5      7.08
#FFFFFF / #1E1F21                     4.5     16.49
map focus #1F52B8 / white halo          3      7.10
13 fixed checks, failures: 0
```
