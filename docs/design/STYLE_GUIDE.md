# GEO-X 디자인 가이드

GEO-X Android 화면의 설계·구현·검수에 적용하는 R3 규칙이다. 별도 표시가 없으면 필수다. 길이·간격은 dp, 글자 크기·줄높이는 sp, 시간은 ms다.

- 규칙 기준: `R3-final.md`(2026-10-19) §1·§3·§4·§5·§6, 확인 문구는 §8, 장 구성은 §9다. 도형·치수는 `KIT.md`를 따른다.
- 도면 기준은 412 × 882 dp, status 영역 36 dp, 하단 시스템 영역 48 dp다. 실제 inset은 시스템에서 읽는다.
- 색상 값은 [colors.json](colors.json)을 참조한다. 여섯 팔레트의 hex 표를 중복하지 않는다.
- 이 문서는 구현의 합격 기준이다. `R3 목표`로 표시한 API는 설계상 이름이며 현재 구현된 API와 구분한다.

| 구현 진입점 | 담당 API |
|---|---|
| [ui/theme/](../../app/src/main/java/com/example/jetsoncontroller/ui/theme) | `GeoPalette`, `GeoColors`, `LocalGeoColors`, `LocalGeoType`, `GeoSpace`, `GeoShape`, `GeoElevation`, `GeoMotion` |
| [ui/components/geo/](../../app/src/main/java/com/example/jetsoncontroller/ui/components/geo) | `GeoButton`, `GeoRow`, `GeoSheet` 등 공통 컴포넌트 |
| [geo/shell/](../../app/src/main/java/com/example/jetsoncontroller/ui/components/geo/shell) | `GeoAppShell`, `GeoWindow`, `GeoPaneLayout`, `GeoActionArea` |
| [NextAction.kt](../../app/src/main/java/com/example/jetsoncontroller/ui/model/NextAction.kt) | `nextAction`, `NextAction`, `OperationState`, `TransportRemedy` |
| [Vocabulary.kt](../../app/src/main/java/com/example/jetsoncontroller/ui/model/Vocabulary.kt) | `VocabularyEntry`와 분야별 상태 enum의 `word`·`tone` |
| [Formats.kt](../../app/src/main/java/com/example/jetsoncontroller/ui/model/Formats.kt) | `Formats.size`, `Formats.time` 등 공통 표기 |

## 1. 원칙

1. 한 화면은 한 가지 일을 맡는다. 채워진 주요 행동은 화면당 하나 이하로 둔다.
2. 다음 행동은 현재 상태에서 계산한다. 같은 행동을 여러 위치에 중복하지 않는다.
3. 모르는 값은 상태로 표시한다. 미확인을 0·대시·성공으로 대신하지 않는다.
4. 장치 대상 알림·상태 띠·준비 문구에는 장치 이름을 먼저 쓴다.
5. 개발자 모드에서도 일상 화면의 구조와 주요 행동을 유지한다.
6. 수집은 사용자가 시작하고 종료한다. 재연결·구역 이탈·화면 진입으로 자동 시작·종료하지 않는다.
7. 배경 사건은 현재 자리에서 표시한다. 탭이나 화면을 자동으로 바꾸지 않는다.
8. 상태는 색·아이콘·문구를 함께 쓴다.

| 적용 | 금지 |
|---|---|
| `미확인`, 오래된 값의 `마지막 값 기준` | 응답하지 않은 센서의 `0%` |
| 종료 버튼 위 결과·복구 가능성 설명 | 설명 없이 빨간 버튼만 표시 |
| 섹션과 행으로 구성한 목록 | 같은 상태 타일을 반복한 대시보드 |

## 2. 구조

### 2.1 네 탭과 소유권

| 탭 | 역할 | 루트 구성·주요 경로 |
|---|---|---|
| 작업 | 준비·진행 확인, 시작 화면 | 장치 요약 → 집중 블록 0–1개 → 수집 구간 → 최근 수집 → 장치 작업. `work`, `ticket/{rowId}`, `prepare`, `task/{pipelineId}`, `history` |
| 장치 | 등록 장치·하드웨어 관리 | 현재 장치 → 확인할 일 → 다른 장치 → 주변 장치. `devices`, `device/{deviceId}`, Wi-Fi·센서·파일·휴지통 |
| 전송 | 현재 장치→서버 전송 | 보내는 중 → 확인이 필요한 전송 → 보낼 수집 → 대기 중 → 수신 확인 중 → 서버 수신 확인 → 다른 장치의 주의 항목 → 서버. `transfers`, `transfer/{jobId}`, `send`, `server` |
| 설정 | 화면·알림·서버·정보·고급 | `settings/*`, `settings/server`, `dev/*` |

- `GeoBottomBar`·`GeoRail`은 루트와 탭 소유 목록·상세에 유지한다. 수집 구간도 포함한다.
- 장치 등록, `run`, 전체 지도, 카메라·QR·이미지·영상, 키보드 중심 편집기는 집중 흐름이며 탭 막대를 숨긴다.
- 키보드 중심 편집기는 `dev/terminal`, `dev/upload-targets/edit`, `dev/pipeline/new`, `dev/pipeline/{id}/config`, `dev/pipeline/{id}/policy`, `dev/server-profiles/edit`다.
- 교차 링크는 현재 탭 스택에 쌓고 해당 탭 선택을 유지한다. 지도는 구간·수집·결과·장치에 속한다.
- 벨은 작업·장치·전송 루트에 둔다. 알림은 현재 탭에 쌓는다. 지도·알림 탭을 추가하지 않는다.
- 장치·전송 탭의 주의 표시는 8 dp `attention` 점이며 숫자가 없다.
- 벨 숫자는 24시간 이내 읽지 않은 알림이다. 알림 화면을 열면 모두 읽음 처리한다.

### 2.2 스택과 장치 전환

1. 탭별 중첩 그래프와 `saveState`·`restoreState`를 사용한다. 전환 시 해당 스택·스크롤을 복원한다.
2. 선택 탭을 다시 누르면 그 탭 루트로 간다.
3. 장치·전송·설정 루트의 시스템 뒤로는 작업 루트, 작업 루트의 뒤로는 앱 밖으로 간다.
4. 집중 흐름의 뒤로와 직접 연결 성공은 연 화면으로 돌아간다.
5. 시작 수락은 `ticket/prepare`를 `run`으로, 사용자 종료 확인은 `run`을 `run_result/{runId}`로 교체한다.
6. `launchSingleTop`으로 중복 진입을 막는다. 결과의 뒤로는 원래 진입점으로 돌아간다.
7. 현재 장치는 하나다. 작업·전송 루트에 전환기를 두고 장치 루트는 전체를 나열한다.
8. 전환 전 초안을 장치별로 보존한다. 이전 장치 대상 확인 창은 닫는다.
9. 전환 문구는 `장치를 바꿔서 열려 있던 확인 창을 닫았습니다.`다. 새 응답 전에는 `확인 중`이다.
10. 열린 상세는 자신의 장치 대상을 유지하고 meta에 이름을 표시한다.
11. 등록 0대는 장치 등록 장면이다. 첫 등록 후 작업으로 간다.

### 2.3 다음 행동 우선순위

`nextAction(NextActionInput)`의 첫 일치 결과를 사용한다. 집중 블록·장치 요약·준비 문구가 같은 결과를 공유한다.

| 순서 | 상태 | 다음 행동 |
|---|---|---|
| 0 | 등록 장치 없음 | 장치 등록의 `QR 코드 스캔` |
| 1 | 시작 요청 결과 미확인 | `시작 결과 확인` |
| 2 | 수집 상태 미확인, 연결됐지만 상태 미확정 포함 | `상태 다시 확인` |
| 3 | 미처리 명령 없이 장치 연결 불가 | 연결 복구 |
| 4 | 수집 진행 중, 여러 수집으로 보이는 충돌 포함 | `진행 보기` |
| 5 | 종료됐으나 결과를 열지 않음 | `결과 보기` |
| 6 | 서버 수신 확인·구간 연결·보고 전 | `완료 보고` |
| 7 | 기본 연결만 사용 가능 | `장치 Wi-Fi 설정` |
| 8 | 대기 | 집중 블록 없이 구간 목록 |

- 작업 상태와 `TransportRemedy`를 분리한다. 다시 연결해도 시작 결과 미확인 상태는 유지한다.
- 인증 거절은 `QR 코드로 다시 등록`, 권한 부족은 `권한 허용`을 제공한다.
- 일반 연결 끊김은 `다시 연결`·`연결 방법`을 즉시 함께 제공한다. 15초 뒤에 드러내지 않는다.
- 미전송·전송 실패·보고 실패는 해당 행과 전송 탭 점에 둔다. 집중 블록을 차지하지 않는다.
- 다른 루트·상세는 `수집 중` 정보 띠와 `진행 보기`를 제공한다. 작업은 집중 블록으로 대신한다.

### 2.4 개발자 기능

- 설정 고급에 `개발자 도구`를 둔다. 앱 정보의 버전 7회로 모드를 해제한다.
- 기술 정보는 개발자 모드와 `화면에 기술 정보 표시`가 모두 켜졌을 때만 상세 끝에 접혀 있다.
- 일상 화면의 개발자 링크는 두 곳이다: 서버 휴지통의 `개발자 도구에서 비우기`, 받을 서버 없음의 `개발자 도구에서 업로드 서버 설정`.
- 서버 연결은 상태·서버 이름·환경·직원·프로젝트와 `연결 확인/다시 로그인`을 제공하는 운영자 화면이다.
- URL·프로필 편집·토큰 관리·원격 명령은 `dev/*`에 둔다. 인증 정보는 표시하지 않는다.

## 3. 흐름

탭 수는 확인 버튼을 포함하며 스크롤·입력·대기·OS 권한은 제외한다. 템플릿 코드는 §17을 따른다.

| 흐름 | 기준 탭 수 | 순서·검수 기준 |
|---|---:|---|
| F1 첫 등록 | 2 | P10 QR 코드 스캔 → P8 QR 읽기 → S4 등록 → 작업. 취소 후 코드 유지 |
| F2 일상 확인 | 0 / 1 / 2 | 장치 요약 / 장치 상세 / 카메라. 주의 항목은 요약과 탭 점에서 먼저 노출 |
| F3 구간에서 시작 | 3 | 구간 행 → P3 수집 시작 → S8 수집 시작. 열린 구간은 2회 |
| F3 모호한 작업 | 4 | 점검 전 S3 작업 선택 추가. 구간 초안 유지 |
| F3 수동 반복 | 3 | 구간 직접 고르기 → 마지막 유효 프로젝트·구간 복원 → S8 확인 |
| F4 진행 확인 | 0 / 1 | 시작 직후 / 다른 화면의 진행 보기. 원격 종료는 현재 화면의 결과 보기 |
| F5 종료→결과 | 2 | 수집 종료 → S1b 수집 종료 → 종료 확인 후 결과 |
| F5 결과→전송 | 2 | 서버로 보내기 → 전송 시작 |
| F5 완료 보고 | 2 | 결과·집중 블록·검증된 전송 상세의 완료 보고 → S1a 확인 |
| F6 임의 파일 전송 | 3 + 폴더 이동 | 파일 보내기 → 폴더 선택 → 탐색기 행동 영역의 전송 시작 |
| F6 보관 수집 전송 | 2 | 보낼 수집 행의 보내기 → 전송 시작 |
| F7 연결 복구 | 재시도 1, 직접 연결 3–5 | 연결 방법 즉시 제공. 탐색·연결·장치 인증·재시도·취소 구분 |
| F8 개발자 도구 | 잠김 11, 해제 후 2 | 설정 → 앱 정보 → 버전 7회 → 뒤로 → 개발자 도구 |

- 결과의 주요 행동은 장치 저장 확인 → 보내기 → 전송 보기 → 수신 확인 → 완료 보고 → 다음 수집 순이다.
- 직접 연결에서 서버 전송이 불가하면 이유와 `장치 Wi-Fi 설정`을 함께 제공한다.
- 파일 탐색은 한 구현이다. `browse/send/pick`과 진입점별 현재 폴더는 따로 보존한다.
- 종류 필터는 `전체 · 이미지 · 영상 · 센서 · 로그` picker다. 5분할 segmented를 만들지 않는다.
- 서버 결과에 `이전 수신 결과 더 불러오기`를 둔다. 휴지통은 `전체 128개 중 100개를 표시합니다.`처럼 전체/표시 수를 구분한다.

## 4. 상태와 식별

### 4.1 준비와 시작

`PreparationDraft`는 `model/SurveyRun.kt`에 있다. 저장·변환 진입점은 `data/survey/`와 `ui/survey/PreparationDraftAdapter.kt`다.

| 보존 항목 | 규칙 |
|---|---|
| 장치·서버 프로필 | 장치별 디스크 초안, 다른 장치와 섞지 않음 |
| 구간 row ID·`revision`·작업 | 작업 선택·장치 전환·프로세스 종료 뒤 복원 |
| 프로젝트·구간 | 기존 ID/`revision` 또는 생성 의도. 준비 화면에서는 생성하지 않음 |
| 생성 요청 ID | 결정 때 한 번 만든 `clientRequestId`를 재시도에서 재사용 |
| 구역 `revision`·`hash` | 캐시·시작·수집 결과를 같은 geometry에 연결 |

1. 준비 상태는 telemetry만 사용한다. 진입 시 자동 preflight를 실행하지 않는다.
2. `수집 시작`을 누르면 S8 안에서 preflight를 실행한다. 확정 시 해당 작업·문맥으로 통과한 결과가 2분 이내여야 한다.
3. 장치·제어 권한·활성/미확인 수집·구간 `revision`을 실행 직전에 재검증한다.
4. 생성 → 문맥을 묶은 시작 요청 → GEO& Work outbox → 시작 확인 중 순으로 처리한다.
5. 시작에 장치·작업·프로젝트·구간·preflight·구간 `revision`·구역 `revision`/`hash`를 묶는다.
6. 시작 수락 후 초안을 지운다. 같은 장치에서 다른 구간을 열면 준비 초안을 교체한다.

### 4.2 행동 대상과 정직한 상태

| 행동 | 실행까지 고정할 대상·재검증 |
|---|---|
| 수집 종료 | 장치+`runId`; 현재 활성 수집 대조 |
| 완료 보고 | 프로필+구간 row/`revision`+`runId`+receipt; 검증·타인 변경 확인 |
| 다시 보내기·취소 | 장치+`jobId`+원본 경로+target ID; 현재 전송 상태 |
| 원본 정리·복원 | 장치+job+원본+receipt; 실행 시 서버와 다시 대조 |
| 실행 취소 | 휴지통 항목 ID+원래 경로; 복원 가능 여부 |
| 재부팅·전원 종료 | 장치; 활성 수집이 있으면 금지 |
| 등록 해제 / Wi-Fi | 장치 / 장치+SSID; 대상·연결 확인 |

- 대상이 바뀌면 실행하지 않고 이유를 표시한다. 현재 선택 장치로 대상을 바꾸지 않는다.
- 미확인 시작·수집은 프로세스 종료 후 복원한다. 재연결만으로 지우거나 새 시작을 허용하지 않는다.
- `run_result/{runId}`는 ID로 읽는다. `latestRun`으로 대신하지 않는다.
- canonical run ID 없는 기록은 `history_result/{historyId}`로 열고 그 기록의 사실·로그·폴더를 표시한다.
- 구간·geometry는 프로필·장치별 캐시다. 오프라인은 마지막 동기화 시각을 표시한다.
- 센서는 표본 신선도와 장치 가용성으로 판단한다. 새 status 응답만으로 수신 중이라 하지 않는다.
- 잘못된 측정값은 표시·snapshot 모두 null, 실제 0은 0이다.
- receipt의 job·target·remote session·source가 모두 맞아야 서버 수신 확인이다.
- 장치 오프라인은 서버 영수증 조회·검증된 결과의 보고를 막지 않는다.

## 5. 레이아웃

### 5.1 창·pane·그리드

담당 API: `geoWindow`, `GeoWindow`, `LocalGeoWindow`, `GeoPaneLayout`, `GeoSpace.gutterForPane`.

| 항목 | 규칙 |
|---|---|
| 너비 등급 | 현재 창의 수평 안전 영역 적용 후: compact < 600, medium 600–839, expanded ≥ 840 |
| 높이 등급 | 사용 가능 높이 < 480이면 compact height |
| 내비게이션 | 너비 < 600 하단 막대, ≥ 600은 80 dp rail. 모든 방향 동일 |
| pane | 창 너비 − 수평 안전 inset − 표시 중 rail 80 |
| gutter | pane ≥ 390: 24, 340–389: 20, < 340: 16 |
| 본문 | 최대 720, pane 중앙. 바깥 `canvas`/본문 `surface` |
| 그리드 | compact 4열, medium·expanded 단일 pane 8열, 간격 16 |
| span | 섹션·행·노트·띠·지도·행동 영역 전체. `figure` cell 2/4 또는 4/8, 버튼 쌍 절반씩 |
| 고정 크기 | 아이콘·견본·사진. `label`/value는 별도 40%/60% |

- `orientation`, `smallestScreenWidthDp`, `TABLET_UI_SCALE`로 배치·density를 바꾸지 않는다. 1 dp는 모든 기기에서 1 dp다.
- 두 pane은 expanded, 높이 ≥ 480, font scale < 1.5일 때만 후보가 된다.
- 최종 조건은 `pane ≥ 320×max(1,fontScale) + 1 + 400×max(1,fontScale) + 2×gutter`다.
- 두 pane은 작업 대기열/구간, 장치 목록/상세, 전송 목록/상세, 설정 목록/페이지에 쓴다.
- rail은 위 정렬·항목 간격 8이며 안 맞으면 세로 스크롤한다.
- 세로 리듬은 4의 배수다. 텍스트 높이는 최소값이며 고정하지 않는다.

### 5.2 Inset 계약: 가장자리마다 소유자 하나

| 영역 | 소유자·처리 |
|---|---|
| Edge-to-edge | `MainActivity`의 `enableEdgeToEdge()`, `isNavigationBarContrastEnforced = false`. 앱이 시스템 영역을 칠함 |
| 수평 safe drawing | `GeoAppShell`이 top bar·본문·행동 영역·하단 막대·rail에 한 번 적용. 하위 중복 금지 |
| 상단 status/cutout | `GeoTopBar`가 한 번 적용하고 `topBar`로 칠함. top bar 없는 장면·미디어는 자체 `statusBars`와 장면색 |
| 하단 navigation | 하단 막대(`nav`), 막대 없는 고정 행동 영역(`surface`), 시트 footer 중 하나. 모두 없으면 스크롤 끝 패딩 |
| IME | 폼 본문/행동 영역 공통 부모가 `imePadding`과 `consumeWindowInsets` 한 번. 하단 막대를 숨기고 navigation 중복 금지 |
| Rail의 다른 변 | rail과 별개로 본문 하단·반대편 navigation inset 적용 |
| 시스템 아이콘 | `JetsonControllerTheme`이 상단·하단의 실제 배경으로 각각 결정. 시트·`scrim`·미디어 전환 때 갱신 |

- 네이비 밝게의 status 아이콘은 밝게 한다. 집중 흐름 하단은 해당 `surface`·미디어 배경을 따른다.
- 같은 inset을 `Scaffold`·화면·자식에서 거듭 적용하지 않는다.

### 5.3 행동 영역·낮은 창·IME

1. 행동은 한 번 선언한다. `GeoActionArea`가 고정 또는 스크롤 끝 배치를 결정한다.
2. `stickyAction`: `viewport − top chrome − bottom chrome − IME − action ≥ 280`이면 고정한다. 모두 실측값이다.
3. 높이 < 480이면 inline이다. 장치 등록 장면의 주요 행동은 고정한다.
4. `retainedActionHeight`로 보이는 동안 최대 높이를 유지한다. 문구가 짧아져도 버튼을 옮기지 않는다.
5. 행동 영역은 `surface`·상단 `border` 1·세로 12·수평 gutter다. `body` 준비 문구 뒤 8에 버튼을 둔다.
6. 탭 루트 행동 영역은 전송의 파일 보내기에만 둔다.
7. 낮은 창은 상태 띠 한 줄+행동, 지도 카드 160이다. 수집 중 지도/수치, 결과 영수증/지도는 두 열로 배치할 수 있다.
8. IME가 열리면 초점 필드를 보이게 이동하고 남은 높이를 다시 계산한다.
9. 버튼 쌍·대화상자·시트 footer는 §10의 같은 쌓기 규칙을 사용한다.

## 6. 간격

`GeoSpace`를 사용한다. 화면에 별도 간격 값을 만들지 않는다.

| 토큰 | dp | 이름·용도 |
|---|---:|---|
| `space1` | 2 | `focusOffset`, 아이콘 광학 보정 |
| `space2` | 4 | `rowMetaGap`, segmented inset, 줄바꿈 배지 사이, 진행 막대 높이 |
| `space3` | 6 | `captionIconGap`, 배지 점/글자 |
| `space4` | 8 | `sectionTitleGap`, `fieldLabelGap`, `buttonGap`, 준비 문구/버튼, 배지 좌우, rail 항목 |
| `space5` | 10 | `buttonIconGap`, 메뉴 아이콘/글자 |
| `space6` | 12 | `rowPadding`, `actionPadding`, `overlayInset`, `helperGap` |
| `space7` | 14 | `figureLabelGap` |
| `space8` | 16 | `singleRowPadding`, `leadingGap`, `gridGap`, `containedPadding`, 좁은 gutter |
| `space9` | 20 | 중간 gutter, loader/앱 이름 |
| `space10` | 24 | `section`, `dialogPadding`, `sheetPadding`, 넓은 gutter |
| `space11` | 28 | `dangerSection`, 관리·이 휴대폰 앞 |
| `space12` | 32 | `detailSection`, `sceneGap`, H1/첫 섹션 |
| `space13` | 40 | `scrollEnd`, 행동 영역·chrome 위 스크롤 끝 |
| `space14` | 56 | `sceneTop`, 장치 등록·전체 상태의 위 여백 |

- 섹션 위 24는 `GeoSection`만 소유한다. 위험 섹션은 24를 28로 교체한다.
- 첫 섹션은 루트 위 16 또는 H1 뒤 32를 사용한다. 섹션 간격을 추가하지 않는다.
- 부모 목록·Column은 섹션 사이 spacedBy를 추가하지 않는다.
- 내부 패딩은 컴포넌트가 소유한다. contained `figure`의 16을 중복하지 않는다.
- 두 줄 행 최소는 `12+24+4+20+12=72`, 한 줄 행은 `16+24+16=56`이다.

## 7. 글자와 정렬

### 7.1 글자 역할

`PretendardFamily`, `LocalGeoType.current`를 사용한다. Variable의 400·500·600·700을 `FontVariation`으로 지정한다. 로그·식별자는 시스템 monospace다.

| `GeoTypeRoles` | 크기/줄높이 | 굵기 | 자간(em) | 용도 |
|---|---|---|---|---|
| `count` | 12/16 | 600 | 0 | 벨 숫자 |
| `caption` | 13/20 | 400 | −0.003 | meta·시각·도움말·오류·지도 설명 |
| `label` | 14/20 | 500 | 0 | 필드·segmented·범례 |
| `body` | 15/24 | 400 | −0.003 | 본문·사실·대화상자·상태 띠·준비 문구·복구 설명 |
| `titleSm` | 16/24 | 500 | −0.003 | 행 제목, 섹션·장치 이름·집중 제목은 600 |
| `title` | 18/26 | 600 | −0.015 | 상세 top bar·시트·대화상자, `figure` 미확인 |
| `boardTitle` | 22/30 | 700 | −0.025 | 상세 H1 |
| `heading` | 24/32 | 700 | −0.025 | 루트 제목·수치·수집 시계 |
| `button` | 16/20 | 600 | 0 | 모든 버튼 |
| `input` | 16/24 | 400 | 0 | 입력·선택 값 |
| `mono` | 13/20 | 400 | 0 | 기술 정보·터미널·로그 |

- `badge`는 `caption`/600, `sectionTitle`은 `titleSm`/600, `tab`은 `caption`/500, `tabSelected`는 `caption`/700이다.
- `figure`는 `heading`+`fontFeatureSettings = "tnum"`이다.
- 고대비는 `caption`·`body` 400→500, `label` 500→600, 행 제목 500→600이다. `GeoType.forPalette`로 선택한다.
- 임의의 17·19·21·26·28·32 sp 역할을 추가하지 않는다.

### 7.2 정렬·줄바꿈

| 대상 | 규칙 |
|---|---|
| 제목·본문·행·시트·대화상자·노트·집중/상태 블록 | 시작 정렬, 위부터 배치 |
| 버튼·segmented·탭, 로딩/오류 장면, 중앙 QR 안내 | 가운데 정렬 |
| 행 끝 값·섹션 건수·진행률 | 끝 정렬 |
| `GeoNumericRows` | 가장 넓은 값으로 공통 숫자 슬롯 실측, 숫자 끝/별도 단위 슬롯 시작 정렬, `tnum` |
| `GeoFactRow` | `label` 40% 시작/value 60% 끝. 어느 쪽이든 2줄 초과, scale ≥ 1.5, 너비 < 320이면 `label` 위/value 아래 모두 시작 |
| `GeoFigureGrid` | cell별 `label`·값·설명 시작 정렬 |

- 장치·구간·프로젝트·파일·폴더 이름은 줄 수 제한·말줄임이 없다. 접근성 `label`도 전체 이름이다.
- 자유 서술만 제한할 수 있다. 그때는 제자리에서 펼치는 더 보기를 제공한다.
- 한글은 `LineBreak.Paragraph`다. API 33+ `LineBreak.WordBreak.Phrase`는 실제 기기의 어절 줄바꿈 검증 뒤 유지한다.
- 줄보다 긴 단일 토큰만 grapheme 경계에서 비상 줄바꿈한다. 이름 전체를 줄바꿈 불가로 묶지 않는다.
- 숫자와 분리되는 단위 사이는 NBSP(U+00A0)다. 배치용 `\n`을 문구에 넣지 않는다.
- 검수 문자열: `GEO-X-현장지원차량-2`, `신월4거리 북측 보조 구간 2차 재수집`, `같은 네트워크에 연결하면 모든 기능을 쓸 수 있습니다.`
- 문장은 마침표로 끝낸다. 제목·`label`·버튼·배지·행 제목은 붙이지 않는다. 사실 연결은 ` · `다.

## 8. 색

### 8.1 여섯 테마

테마는 모드 × 팔레트다. `ThemeMode.SYSTEM`·`ThemeMode.LIGHT`·`ThemeMode.DARK`와 밝은/어두운 팔레트를 각각 저장한다. 첫 설치는 `시스템 따름 · 표준 · 그래파이트`다.

| 모드 | `GeoPalette` | 이름 | 적용 |
|---|---|---|---|
| 밝게 | `STANDARD` | 표준 | GEO& Work의 기본 밝은 화면 |
| 밝게 | `NAVY_LIGHT` | 네이비 | 표준 본문과 네이비 상·하단 chrome. 상세 top bar·status·rail·막대 아래 navigation까지 포함 |
| 밝게 | `HIGH_CONTRAST` | 고대비 | 흰 표면·진한 선·굵은 글자·상태 실선. 야외 가독성용 |
| 어둡게 | `GRAPHITE` | 그래파이트 | 기본 어두운 화면 |
| 어둡게 | `NAVY_DARK` | 네이비 | 푸른빛 어두운 화면 |
| 어둡게 | `MIST` | 흐림 | 밝기 차이를 줄인 회색 화면 |

- 팔레트는 `GeoPalette.colors`, 역할은 `LocalGeoColors.current`로 읽는다.
- 실제 집합은 `GeoStandard`, `GeoNavyLight`, `GeoHighContrast`, `GeoGraphite`, `GeoNavyDark`, `GeoMist`다.
- 값은 `colors.json`의 `palettes`·`color`·`fixed`·`roleAliases`를 참조한다.
- 네이비 밝게는 하단 막대 옵션이 아니다. 집중 흐름도 네이비 top bar를 유지한다.
- 고대비의 활성 글자 쌍은 ≥ 6:1, 본문은 ≥ 9:1이다. 어두운 팔레트를 햇빛용으로 안내하지 않는다.

### 8.2 역할과 컨트롤

| 역할 | 사용 규칙 |
|---|---|
| `canvas`/`surface`/`sunken` | 바깥·본문·낮은 표면. 임의 tonal elevation 없음 |
| `text`/`textSecondary`/`textSubtle` | 본문·보조·약한 보조. `textSubtle`은 `surface`/`sunken`/`canvas`에서만 |
| `primary`/`onPrimary` | 하나의 채운 주요 버튼·tertiary·`focus`·진행률·선택 컨트롤 |
| `success`/`warning`/`danger`/`info`/`pending`/`neutral` | 배지·점·상태 아이콘·띠·집중 블록·노트. 수치 자체에 칠하지 않음 |
| `*Soft` | 같은 tone 글자 또는 `text`만 올림 |
| `border` / `borderStrong` | 장식 구분선 / 조작 경계 |
| `*Border` | 고대비 외에는 장식용. 의미 있는 상태 테두리는 tone 원색 |
| `danger`/`dangerPressed`/`onDanger` | 파괴 행동·실패. `danger` outline은 `dangerBorder`가 아닌 `danger` |
| `field`/`fieldPressed`/`disabledFill`/`disabledText` | 입력·눌림·사용 불가. 글자 alpha를 낮추지 않음 |
| `topBar*`/`nav*`/`chrome*`/`attention` | 상·하단·rail 전용. 네이비 본문 강조판 금지 |
| `mapPlate` | 지도 배지·조작부·저작권의 불투명 판 |
| `inverse`/`onInverse`/`inverseAction` | 스낵바 |

- 입력·picker는 항상 1 dp `borderStrong`, `focus`는 2 dp `primary`, 오류는 2 dp `danger`+아이콘+문구다.
- switch는 track 52 × 32, thumb 24, inset 4다. 꺼짐 `switchTrackOff`/`switchThumbOff`, 켜짐 `primary`/`switchThumbOn`이다.
- checkbox는 20·반경 6·경계 2다. 선택은 `primary`와 2 dp `onPrimary` 체크다.
- radio는 20·경계 2다. 선택은 `primary` ring과 10 dp 점이다.
- 선택 행은 `primaryTint`+radio/check, 제목은 `text`다. 일반 행의 상태색 배경은 금지다.
- 지도·미디어는 `GeoFixedColors`다. 테마에 따라 overlay 색을 바꾸지 않는다.
- 미디어는 검정 stage·검정 72% 안내판·흰 글자다. 배지는 불투명 `mediaBadge`와 `mediaLive`/`mediaDelayed`/`mediaPending`/`mediaFailed`를 사용한다.

### 8.3 화면 테마 선택기

1. `settings/theme`은 P7이며 막대를 유지한다. 미리 보기 → 모드 → 밝은 테마 → 어두운 테마 순이다.
2. 미리 보기는 기본 160 dp의 비조작 샘플이다. 작업 top bar·전환기·두 줄 행/할 일 배지·저장 공간·예시 입력·수집 시작·탭 막대를 포함한다.
3. 미리 보기는 현재 표시 모드를 따른다. 큰 글자는 필요한 높이로 늘린다. 색 애니메이션은 없다.
4. 모드는 시스템 따름/밝게/어둡게다. scale ≥ 1.5 또는 `label`이 안 맞으면 `GeoPickerField`로 바꾼다.
5. 시스템 따름 설명은 `기기 설정에 따라 밝은 테마와 어두운 테마를 바꿉니다.`다.
6. 두 그룹에 각각 제목·radio group `label`을 준다. 행 최소 72, 설명은 무제한 줄바꿈이다.
7. 견본은 56 × 40·반경 10·`borderStrong` 1이다. 위 28은 `surface`·16 × 4 `text` bar·8 dp `primary` 점, 아래 12는 `nav`다.

| 선택지 | 설명 |
|---|---|
| 표준 | GEO& Work와 같은 기본 화면입니다. |
| 네이비, 밝게 | 위아래 막대를 네이비로 칠한 화면입니다. |
| 고대비 | 햇빛 아래에서 읽기 쉽도록 글자와 선을 진하게 합니다. |
| 그래파이트 | 기본 어두운 화면입니다. |
| 네이비, 어둡게 | 푸른빛이 도는 어두운 화면입니다. |
| 흐림 | 밝기 차이를 줄인 회색 화면입니다. |

- 비활성 모드 그룹에는 `어두운 모드에서 사용합니다.` 또는 `밝은 모드에서 사용합니다.`를 붙인다.
- 그 그룹 선택 시 `어두운 모드에서 쓸 테마로 저장했습니다.` 또는 밝은 모드 문구를 표시한다.
- 즉시 적용하며 저장·확인 버튼은 없다. 모드 변경 저장 순서는 §16을 따른다.
- 설정 요약은 `시스템 · 표준 / 그래파이트` 또는 `밝게 · 고대비`다. 재생성 후 페이지·스크롤을 복원한다.

## 9. 모양과 높이

| `GeoShape` | 반경 dp | 용도 |
|---|---:|---|
| `sm` | 6 | checkbox·skeleton·작은 견본 |
| `md`/`control` | 10 | 모든 일반 버튼·입력·picker·테마 견본 |
| `lg`/`card` | 12 | 지도·집중 블록·노트·메뉴·스낵바·지도 패널 |
| `xl`/`dialog` | 14 | 대화상자 |
| `xxl`/`sheetTop` | 20 | 시트 위 모서리 |
| `pillRadius`/`pill` | 999 | 상태 배지·segmented track/thumb만 |
| `circle` | 원 | 아이콘 버튼·지도 조작부 |
| `progress` | 2 | 높이 4 진행 막대 끝 |

- 본문은 `GeoElevation.none`(0)이다. 모든 구역을 그림자·카드 테두리로 감싸지 않는다.
- 지도 조작부·지도 배지·switch thumb는 작은 그림자, 메뉴·대화상자·시트·스낵바는 큰 그림자다.
- `GeoElevation.small`은 2, `GeoElevation.large`는 16이다. 정밀 그림자는 `shadowSmall`/`shadowLarge`/`shadowSwitch`를 따른다.
- 어두운 큰 overlay는 흰색 10% outline+그림자다. 본문에는 outline을 넣지 않는다.
- `topBarBorder`는 내용이 아래로 스크롤됐을 때만 표시한다.
- `focus`는 ring 2+offset 2+바깥 `focusHalo` 4다. chrome은 `navFocus`다. ring을 잘라 내지 않는다.
- 지도 `focus`는 고정 `mapFocus` ring 2 dp와 흰 halo 2 dp다. tile 위에서도 경계가 보여야 한다.

## 10. 컴포넌트

### 10.1 공통 상태·버튼 쌓기

- 화면은 `ui/components/geo`·`geo/shell` API를 사용한다. 컴포넌트는 전달된 상태를 그리며 신선도·연결·검증을 추론하지 않는다.
- 화면에서 `androidx.compose.material.*`, `androidx.compose.material3.*`, `androidx.compose.material.icons.*`를 직접 import하지 않는다. 기반 Material은 공통 컴포넌트·theme 내부에서 처리한다.

| 상태 | 표시·동작 |
|---|---|
| enabled | 기본 역할·경계·전체 `label` |
| pressed | 행·secondary·tertiary·icon은 `fieldPressed`, 채운 버튼은 `primaryPressed`/`dangerPressed`. ripple 없음 |
| focused | §9 ring과 잘림 없는 바깥 여유 |
| disabled | 자리 유지·옆/아래 이유·가능한 복구 행동 |
| busy | 16 dp 원형 진행+현재 진행형 `label`, 입력 차단·크기 유지 |
| selected | thumb·check·radio·채운 탭 등 형태와 선택 의미 |
| error | tone 아이콘+원인+복구. 색만 바꾸지 않음 |

- `GeoButton(busy = true)`에는 busyLabel, 사용 불가에는 disabledReason을 지정한다.
- 버튼 쌍은 같은 너비의 절반에서 모든 `label`이 한 줄에 들어갈 때만 나란히 둔다. 간격 8이다.
- 대화상자는 자연 너비·끝 정렬이다. 안 맞으면 같은 규칙으로 쌓는다.
- 쌓을 때 주요/확정 버튼을 위에 전체 너비로 둔다. `label` 최대 2줄, 높이는 증가한다.
- 화면 쌍·대화상자·시트 footer 모두 `GeoButtonActions`, `GeoActionLayout.EqualShares`·`GeoActionLayout.Dialog`의 같은 규칙이다.

### 10.2 Shell·상태·목록

| API | 사용처·형태 | 상태·제약 |
|---|---|---|
| `GeoAppShell`/`GeoPaneLayout` | chrome·inset·pane | §2·§5 |
| `GeoTopBar` | 루트 최소 64/`heading`. 상세 최소 56/뒤로 48, 가장자리 4/제목 시작 64 | 제목+전환기 안 맞으면 전환기만 둘째 행 최소 48. 벨은 첫 행. 상세 제목 `title`/최대 2줄, trailing ≤ 2. 로고 없음 |
| `GeoBottomBar`/`GeoRail` | 4탭, 하단 최소 64+inset, 아이콘 24 | 선택 채움·700, 비선택 outline·500. Role.Tab, 큰 글자에서 높이 증가 |
| `GeoStatusStrip` | top bar 아래 full bleed, 최소 48/세로 12/gutter, 아이콘 20+간격 12+`body` | 하나만. `highestPriorityStrip`: 인증 오류 > 연결 끊김 > 구역 밖 > 수집 중 > 마지막 동기화. polite |
| `GeoFocusBlock` | 탭 루트, 반경 12/패딩 16/아이콘 20/`titleSm` 600/`body` | 하나 이하, §2.3 우선순위. 성공 축하용 금지 |
| `GeoDeviceStrip` | 작업 루트 최소 72, 사진 40·이름·배지·meta | 큰 글자는 배지 줄바꿈, 첫 주의 항목으로 마지막 meta 대체. 상태 미확인·연결 끊김 집중 블록과 중복 금지 |
| `GeoSection` | 제목·선택적 건수/tertiary/설명 | `heading` 의미, 아래 8. 상자·장식 아이콘·eyebrow 없음 |
| `GeoRow` | 탐색, 최소 56/72, leading 24 또는 사진 40, `titleSm`/meta `caption` | 한 target, 전체 이름 줄바꿈, disabled 이유 meta |
| `GeoActionRow` | 상세 내용+trailing secondary | 독립 target 2개·간격 8. 내용 상세 보기/버튼 다시 보내기·복원·보내기·등록 |
| `GeoFactRow`/`GeoNumericRows` | 사실·숫자 열 | §7.2 쌓기·슬롯 |
| `GeoCheckRow` | 준비/preflight, 최소 56, tone 아이콘 20 | 행 순서 고정, 실패 행 아래 `body` 이유·복구 |
| `GeoFigureGrid` | gutter 정렬 2열/간격 16, `caption`→14→`heading`→`caption` | cell < 132 또는 scale ≥ 1.5면 1열. 설명 슬롯 유지. contained 패딩 16 한 번 |
| `GeoReceipt`/`GeoReceiptLine` | 수집 종료·장치 저장·서버 수신·GEO& Work 보고 / 행 안 요약 | 첫 미완료가 행동 결정. 요약 `label`/점/value는 묶어 줄바꿈, scale ≥ 1.5 한 묶음/줄 |
| `GeoBadge` | 최소 24, 좌우 8, 점 6+간격 6, `caption` 600 | `SOFT`/`ON_MAP`/`ON_DARK`. 행당 하나·비조작·시간 금지. 고대비 tone 실선 1 |

- `figure` 미확인은 `title`/`neutral`이다. 오래된 값은 실제 값+마지막 값 기준이다.
- 임계 초과 값은 `text`를 유지한다. `label` 앞 `warning` 16과 `기준 80°C 초과` 설명을 추가한다.

### 10.3 행동·선택·입력·진행

버튼 style은 `GeoButtonStyle`이다.

| API·style | 용도·치수 | 상태·제약 |
|---|---|---|
| `GeoButton`/`Primary` | 주요, 높이 52/반경 10 | 하나 이하, `primary`/`onPrimary`, 행동 영역 전체 너비·최대 560 |
| `Secondary` | 보조, 높이 48/`borderStrong` 1 | `primary` 옆 높이 52 |
| `Tertiary` | 섹션·띠·상태, 높이 48/좌우 12 | `primary` 글자 |
| `DangerOutline` | 종료·재부팅·전원·취소·원본 정리·등록 해제·휴지통 | 높이 48, 중요 행동·행동 영역 52. 결과 설명 위에 표시 |
| `DangerFilled` | S1b 최종 확인 | 높이 52, `danger`/`onDanger`. 일반 화면 금지 |
| `GeoIconButton` | 48 × 48/아이콘 24 | 원형 눌림·content description 필수 |
| `GeoSwitchRow`/`GeoRadioRow`/`GeoCheckboxRow`/`GeoRadioGroup` | 최소 56/설명 있으면 72 | 행 전체 조작·Role·선택 의미. radio 그룹 `label` |
| `GeoSliderRow` | 기준값+현재 값 | 양끝 48 dp −/+ 한 단계 조정, 값 변경 낭독 |
| `GeoSegmented` | 2–4개 동등한 보기, 최소 48/항목 ≥ 72/inset 4 | 선택 600/나머지 500, 안 맞거나 scale ≥ 1.5면 picker |
| `GeoPickerField` | 선택 시트, 최소 56/반경 10 | `caption` `label`/`input` 값/chevron 20·사용 불가 이유 |
| `GeoTextField` | `label` 위 8, 최소 56/반경 10/helper 아래 12 | `focus`·error·disabled, 비밀번호 보기 48, 숫자 키보드+단위, 여러 줄 ≥ 3 |
| `GeoProgress` | 높이 4, `field` track/`primary` indicator | 확정 floor %·끝 정렬, 불확정은 top bar 아래 선형. 원형은 busy 내부 |
| `GeoSkeleton` | 행 모양 `sunken` 막대/반경 6 | 첫 로드 > 300 ms만, 정적 |

### 10.4 Overlay·설명·기술 정보

| API | 용도·형태 | 상태·제약 |
|---|---|---|
| `GeoSheet` | `GeoSheetKind.CHOICE`/`FORM`/`INFO`, 위 반경 20/handle 32 × 4/좌우 24/`title` | 본문 스크롤·닫기 경로, footer는 navigation 위. 본문 < 160이면 닫기 top bar 있는 전체 화면 |
| `GeoSheetFooter` | `primary`+선택적 secondary | 공통 쌓기 규칙 |
| `GeoStartSheet` | S8·`GeoStartSheetState`·`GeoStartPhase` | 구간·프로젝트·장치·구역·시작 전 점검·GEO& Work 안내. `CHECKING` 점검하는 중, `FAILED` 이유/복구/다시 점검·확정 불가 |
| `GeoConfirmDialog` | S1a `GeoConfirmKind.STANDARD` / S1b `DANGER`, 너비 280–560/패딩 24/반경 14 | 제목+결과+복구 가능성, 스크롤 `body`, 동작 이름 확정, 전제 실패 이유·비활성 |
| `GeoInfoDialog` | S2 읽기 전용 | 사실 행+tertiary 닫기 하나 |
| `GeoMenu` | 부차 행동, 반경 12/행 ≥ 48/`body` | 위험 항목은 구분선 뒤 맨 아래 |
| `GeoSnackbar` | S7, 반경 12, bottom chrome 위 | 하나만, 행동 노출. 행동 있음 8초/없음 4초에 권장 timeout 적용 |
| `GeoInlineNote` | 반경 12/패딩 16/아이콘 20/`body` | `GeoNoteKind.HELP`는 세션 닫기 가능, `LIVE_ERROR`는 숨기기 금지 |
| `GeoStateBlock` | 위 정렬, `titleSm` 600/`body` 1–2문장/행동 하나 | `EMPTY`/`ERROR`/`MISSING`/`PERMISSION`/`OFFLINE`/`UNSUPPORTED`. 전체 상태 위 56, 중앙 장식 금지 |
| `GeoTechInfo` | 상세 끝 기술 정보, 기본 접힘 | 개발자 두 설정 모두 필요, `mono` 값마다 복사 48 |
| `GeoLogView` | 선택 가능한 `mono`, 실시간/보관 로그 | 끝에 있을 때만 따라감. 벗어나면 최신 로그 제공 |
| R3 목표 `GeoMapCard`/`GeoMapScreen`/`GeoRunPanel` | 지도·진행 행동 | §15 |
| R3 목표 `GeoMediaStage` | 카메라·QR·영상·이미지 | 검정 stage·고정색·on-dark 배지, QR 직접 입력·영상 재시도 |
| `geo_device` 사진 | 40/56/120, 프레임 없음 | 장식이며 글자 이름 병기 |

- 시트·대화상자는 header/footer 실측 후 `body` < 160이면 전체 화면으로 바꾼다. 글자를 축소하지 않는다.
- 시트·대화상자·메뉴가 닫히면 초점을 opener로 돌린다.
- 기존 wrapper는 공통 API adapter 뒤 호출부를 옮긴다. deprecated alias를 새 화면에 쓰지 않는다.
- StatusChip은 `GeoBadge`, RunReceiptStrip 타일은 `GeoReceipt`/`GeoReceiptLine`으로 바꾼다.
- `GeoSection`(tone) 왼쪽 선·`GeoStatusTile`·ConnectionStepper·수집 단계 탭·DeviceContextHeader·DataLocationTabs·FilterChip·네이비 다음 행동 띠를 새로 쓰지 않는다.

## 11. 아이콘

- `GeoIcons` `ImageVector` 한 세트만 쓴다. 24 dp grid·선 1.75·둥근 cap/join·기본 outline이다.
- 선택 탭만 채운 glyph다. `tabWorkFilled`/`tabDeviceFilled`/`tabTransferFilled`/`tabSettingsFilled` 함수를 쓴다.
- 크기: bar·navigation·행 leading 24, 버튼·띠·노트·chevron 20, 준비·`caption`·수치 `warning` 16이다.
- 중립 `textSecondary`, chrome `onTopBarIcon`, 상태 tone, `primary` 내부 `onPrimary`다.
- 섹션 제목·설정 행·상태 블록에 장식 아이콘을 넣지 않는다.
- 아이콘 단독은 뒤로·닫기·더 보기·벨·추가·지도 조작만 허용하며 content description을 붙인다.
- `refresh`는 다시 시도 버튼 안에서만 쓴다. top bar·메뉴 새로고침은 금지다.

| 분류 | `GeoIcons` 이름 |
|---|---|
| 탭 | `tabWork`, `tabDevice`, `tabTransfer`, `tabSettings` 및 Filled 함수 |
| 공통 | `back`, `close`, `more`, `bell`, `add`, `search`, `chevronRight`, `chevronDown`, `chevronUp`, `copy`, `refresh` |
| 작업·파일 | `send`, `trash`, `restore`, `play`, `pause`, `stop`, `folder`, `file`, `image`, `video`, `log` |
| 미디어 | `torch`, `torchOff`, `camera`, `qr`, `eye`, `eyeOff` |
| 지도 | `myLocation`, `fitZone`, `zoomIn`, `zoomOut`, `layers`, `expandPanel`, `collapsePanel` |
| 장치·개발자 | `wifi`, `bluetooth`, `link`, `power`, `reboot`, `fan`, `terminal` |
| 상태 | `success`, `warning`, `danger`, `info`, `pending`, `unknown`, `spinner` |

## 12. 움직임과 햅틱

`GeoMotion`, `geoReducedMotion()`을 사용한다. easing은 `CubicBezierEasing(0.2f, 0f, 0f, 1f)`다.

| 토큰 | ms | 적용 |
|---|---:|---|
| `fast` | 120 | 눌림·top bar 선·아이콘색·탭 crossfade. 탭 slide 없음 |
| `normal` | 180 | 배지 tone·segmented/switch·메뉴·상태 띠 높이·버튼 `label`·진행률 |
| `slow` | 240 | 시트·대화상자, push/pop fade+수평 24(뒤로 반대), loader→앱, 지도 `camera`·marker |
| `loaderCycle` | 2400 | geon-loader 한 주기 |

- 숫자는 즉시 교체한다. 카운트업·숫자 보간은 금지다.
- bounce·overshoot·shimmer·pulse는 금지다. 진행·loader·장치 marker 외에는 자발적 움직임이 없다.
- 실시간 값으로 주요 버튼을 옮기지 않는다.
- animator scale 0·애니메이션 제거를 실행 중에도 관찰한다. 줄인 움직임은 모든 duration 0이다.
- 줄인 움직임은 정적 원색 loader, 지도 `camera`/marker 즉시 이동, 시트 제자리 표시다.
- 시작·종료 수락 `CONFIRM`, 확정 구역 이탈 `REJECT` 한 번, QR 읽기 `LONG_PRESS`다.
- 시스템 햅틱 설정을 따르고 보이는 변화와 함께 제공한다.

## 13. 접근성

| 항목 | 합격 기준 |
|---|---|
| 대비 | 실제 배경에서 글자 ≥ 4.5:1, 경계·`focus`·의미 아이콘·지도 stroke ≥ 3:1. 고대비는 §8 |
| target | 모두 ≥ 48 × 48. 주요·종료·재부팅·전원·보내기 높이 ≥ 52. 독립 target 사이 ≥ 8 |
| 글자 확대 | font scale 2.0까지 잘림·겹침·숨은 행동 없음. 높이 증가·쌓기·picker·한 pane·스크롤 |
| 읽기 순서 | 제목 `heading`, 일반 행 merged node 하나, action row 두 node, 배지 stateDescription |
| live region | 상태 띠 polite, 수집 header 분당 최대 1회, 전송 10%마다, loader 한 번 |
| 초점 | 아이콘 설명·의미 순서·overlay 종료 후 opener 복귀 |
| timeout | `calculateRecommendedTimeoutMillis`로 권장 스낵바 시간 적용 |
| 방향 | 세로·가로 양방향·분할 창. 방향 잠금 금지 |

- pull-to-`refresh` 목록은 60초보다 오래된 freshness 옆 다시 확인과 접근성 행동 새로 고침을 제공한다.
- 지도는 확대/축소·장치 위치로·구역 전체 보기, 패널은 보이는 펼치기/접기 버튼을 제공한다.
- slider −/+, 기술 값 복사를 제공한다. swipe·drag·길게 누르기 전용 행동은 없다.
- 상태는 아이콘/점+단어, 탭은 채움+굵기+색, 지도는 선 패턴+범례로 구분한다.
- 지도 카드는 `caption`을 읽는 단일 탐색 node다. 사진·장식 glyph는 중복 낭독하지 않는다.
- 장갑 조작을 위해 창이 허용하면 주요 행동을 아래 40%에 둔다. 상태·복구 설명은 `body`다.

| 검수 축 | 값 |
|---|---|
| 창 dp | 412 × 882, 360 × 780, 320 × 640, 882 × 412 양방향, 분할 412 × 440 |
| font scale | 1.0, 1.1, 1.3, 1.5, 2.0 |
| 화면 | 작업·수집 구간·S8·수집 중·결과·장치 상세·전송·설정 |
| 시스템 | Samsung 3버튼·gesture, 좌/우 landscape navigation, 위/옆 cutout, IME 폼·시트 |
| 내용·동작 | §7 긴 이름, TalkBack·키보드 `focus`·실행 중 애니메이션 제거 |

## 14. 문구

### 14.1 기본 문법

1. 존댓말을 쓴다. 상태·설명은 `~습니다`, 안내는 `~세요`다. `~해 주세요`는 다른 사람에게 연락을 부탁할 때만 쓴다.
2. 버튼은 수집 시작·다시 연결·서버로 보내기·완료 보고 같은 명사형이며 가능하면 8자 이내다.
3. 확정 버튼은 동작을 반복한다. 확인·예·OK를 확정 `label`로 쓰지 않는다.
4. 느낌표 `!`, 이모지, 말줄임표 `…`를 쓰지 않는다. busy는 `보내는 중`이다.
5. 장치는 장치, 전화는 휴대폰, 받는 곳은 서버, 서비스는 GEO& Work로 쓴다.
6. 오류는 원인 범주와 다음 행동을 쓴다. 단독 확인 필요로 끝내지 않는다.
7. HTTP·예외·원시 서버 코드·IP·URL·UUID·경로는 기술 정보에 둔다.
8. 알 수 없는 보고 코드는 `GEO& Work가 보고를 받지 않았습니다. 잠시 후 다시 보고하세요.`로 표시한다.
9. 알림·상태 띠·준비 문구는 장치 이름부터 쓴다. 수집 확인 제목은 확정 문안의 구간 이름을 따른다.
10. 이름의 받침에 따라 을/를·이/가·은/는·으로/로를 고른다. 고정 조사를 붙이지 않는다.
11. 한 줄에 사실 하나를 우선한다. 안내 문장은 가능하면 40자 이내다.
12. 필드 `label`은 위, placeholder는 `예: GEON-5G` 같은 예시만 쓴다.

| 패턴 | 형식 |
|---|---|
| 빈 상태 | `[무엇]이 없습니다.` + `[어떻게 하면] 여기에 표시됩니다.` + 행동 하나 |
| 확인 | `[대상]을 [동작]할까요?` + 결과 + 복구 가능성 |
| 준비 | `[장치] · [상태] · [문장].` |
| 신선도 | 방금 갱신·3분 전 갱신·16:36 갱신. 배지 금지 |
| 오래된 값 | 마지막 응답 3분 전·마지막 값 기준·마지막 동기화 16:36 |

### 14.2 용어집

| 사용할 말 | 사용하지 않을 말 | 뜻 |
|---|---|---|
| 수집 구간 | 티켓·구간 티켓·조사 구간 | GEO& Work에서 호기에 배정된 행 |
| 수집 구역 | 범위·지오메트리 | 구간 geometry와 계산된 `range` |
| 지점·경로·영역 | Point·LineString·Polygon | 표시용 feature 이름 |
| 수집 | 조사·실행·run | 장치의 한 번 수집 |
| 작업 | 파이프라인·태스크 | 장치에 등록된 작업 |
| 장치·휴대폰 | 장비·Jetson·기기·모바일 | 수집 장치·운영자 전화 |
| 전송 / 보내기 | 업로드 | 장소·상태 / 행동 |
| 장치 저장 | 매니페스트·FINAL | 장치가 결과 폴더를 확인함 |
| 서버 수신 | 업로드 완료 | 서버가 같은 내용을 받았는지 확인함 |
| 완료 보고 | 보고 전송 | GEO& Work 구간을 완료로 바꿈 |
| 연결 방법 | 연결 문제 해결 | 장치에 접근하는 수단 |
| 같은 네트워크·직접 연결·기본 연결 | LAN·Wi-Fi Direct·BLE | 운영자용 연결 이름 |
| 보정 정보 중계 | NTRIP relay·RTK relay | 휴대폰의 보정 전달 |
| 휴지통 / 영구 삭제 | 되돌릴 수 있는 이동에 삭제 | 복원 가능 / 불가 |
| 등록 해제 | 장치 삭제·잊기·작업 삭제 | 휴대폰의 장치 또는 장치의 작업 등록 제거 |
| 정밀 위치·대략 위치·위치 없음·위치 미확인 | RTK FIX·RTK FLOAT·NO_FIX | 위치 품질 |
| 관찰하지 못한 시간 | 데이터 누락 | 휴대폰이 구역을 관찰하지 못한 기간 |

### 14.3 상태 어휘와 tone

`Vocabulary.kt`의 enum을 사용한다. 아래 `danger`는 `StatusTone.ERROR`, `neutral`은 `StatusTone.UNKNOWN`이다. 나머지는 `SUCCESS`/`WARNING`/`INFO`/`PENDING`이다.

| 분야·API | 단어 → tone |
|---|---|
| 연결 `ConnectionState` | 연결됨→`success` · 기본 연결→`warning` · 연결 가능→`info` · 확인 중→`pending` · 응답 없음→`warning` · 오프라인→`neutral` · 인증 필요→`danger` · 권한 필요→`warning` |
| 센서 `SensorState` | 수신 중→`success` · 대기 중→`info` · 지연/미연결→`warning` · 미설정/미확인→`neutral` |
| 위치 `PositionQuality` | 정밀 위치→`success` · 대략 위치→`warning` · 위치 없음→`danger` · 위치 미확인→`neutral` |
| 구역 `ZoneState` | 위치 확인 중→`pending` · 구역 안→`success` · 경계 근처→`info` · 구역 밖→`warning` · 위치 미확인→`neutral`. 밖은 구역 밖 · 35 m |
| 구간 `TicketSegmentState` | 할 일→`neutral` · 진행 중→`info` · 막힘→`warning` · 완료→`success` · 상태 미확인→`neutral` |
| 수집 `CollectionState` | 시작 확인 중/종료 확인 중→`pending` · 수집 중→`info` · 종료→`success` · 비정상 종료→`warning` · 시작하지 못함→`danger` · 상태 미확인→`neutral` |
| 저장 `DeviceStorageState` | 확인 중→`pending` · 저장 확인→`success` · 다시 확인→`warning` · 미확인→`neutral` |
| 전송 `TransferState` | 대기 중→`neutral` · 파일 확인 중→`pending` · 보내는 중→`info` · 수신 확인 중→`pending` · 수신 확인→`success` · 내용 불일치/실패→`danger` · 취소됨→`neutral` |
| 보고 `CompletionReportState` | 보고 전→`neutral` · 보고 대기→`pending` · 보고함→`success` · 먼저 바뀜→`warning` · 보고 실패→`danger` |
| 작업 `TaskState` | 대기→`neutral` · 수집 중→`info` · 시작 요청 중/중지 요청 중/시간 맞추는 중→`pending` · 재시도 중→`warning` · 실패→`danger` · 미확인→`neutral` |
| 중계 `CorrectionRelayState` | 중계 중→`success` · 연결하는 중→`pending` · 꺼짐→`neutral` · 오류→`danger` |
| 서버 연결 `ServerConnectionState` | 연결됨→`success` · 다시 로그인 필요→`warning` · 연결 실패→`danger` · 확인 전→`neutral` |
| 서버 결과 `ServerJobState` | 수신 완료→`success` · 보내는 중→`info` · 실패→`danger` · 휴지통→`neutral` · 삭제 처리 중→`pending` |
| 휴지통 `TrashItemState` | 복원 가능→`neutral` · 복원 중/삭제 처리 중→`pending` · 복원할 수 없음→`warning` |

### 14.4 형식

화면별 formatter를 만들지 않는다. `Formats`에 typed 값을 전달한다. 기본 반올림은 `HALF_UP`, 시각은 `Asia/Seoul`이다. 미확인·잘못된 입력은 미확인, 알 수 없는 ETA는 줄 전체를 생략한다.

| API | 규칙 | 예 |
|---|---|---|
| `Formats.size` | 십진 SI 1000. B 정수, KB–TB 값 < 100은 소수 1자리 유지/≥ 100 정수, 반올림 1000이면 단위 승격 | 0→0 B, 1000→1.0 KB, 99,950→100 KB, 896,000,000→896 MB, 999,600,000→1.0 GB |
| `Formats.free` | size 뒤 남음 | 512 GB 남음 |
| `Formats.memory` | 입력 KiB를 1024²로 나눈 이진 메모리, 소수 1자리 | 2.0 GB / 15.1 GB |
| `Formats.speed` | size 뒤 /초 | 12.3 MB/초 |
| `Formats.percent` | 0–100 제한 후 정수 반올림, 실제 0 유지 | 57%, 0% |
| `Formats.progress` | 내림, done=total일 때만 100%, 초과·잘못된 total 미확인 | 64% |
| `Formats.temperature` | 정수 °C, 숫자/기호 붙임 | 54°C |
| `Formats.clock` | HH:MM:SS, 시간 상한 없음 | 00:12:34, 101:02:03 |
| `Formats.duration` | < 60초 1분 미만, < 60분 분, 이후 시간+분·0분 생략 | 1분 미만, 12분, 1시간 5분, 2시간 |
| `Formats.eta` | 약+duration+남음, < 60초 별도 | 약 4분 남음, 1분 미만 남음 |
| `Formats.time` | 오늘 HH:mm, 어제 접두어, 올해 M월 d일, 다른 해 연도 | 16:36, 어제 16:36, 10월 19일 16:36, 2025년 9월 16일 16:36 |
| `Formats.freshness` | ≤ 15초 방금, < 60분 내린 분·최소 1, 이후 time+갱신. 분당 최대 한 번 재계산 | 방금 갱신, 3분 전 갱신, 16:36 갱신 |
| `Formats.distance` | < 10 m는 1 m, < 100은 5 m, < 1000은 10 m 단위, 이후 km 소수 1자리 | 7 m, 35 m, 350 m, 1.2 km |
| `Formats.length`/`Formats.width` | 길이 < 1000은 10 m/이후 km 소수 1자리. 폭은 2×`bufferM` 정수 | 1.2 km, 폭 20 m |
| `Formats.area` | < 1000은 10 m², < 1,000,000은 100 m²·천 단위 구분, 이후 km² 소수 1자리 | 3,400 m² |
| `Formats.itemCount`/`Formats.caseCount`/`Formats.deviceCount`/`Formats.occurrenceCount`/`Formats.satelliteCount` | 천 단위 구분+해당 단위 | 1,204개, 3건, 2대, 2회, 위성 12개 |
| `Formats.coordinate` | 위도 먼저 소수 7자리·`tnum`, 지도 패널·기술 정보만 | 37.5665000, 126.9780000 |

- 분리 단위 공백은 NBSP다. %·°C·개/건/대/회는 붙인다.
- 초 단위 시각은 수집 시작·종료 구간에만 쓴다. 일반 meta는 분까지다.
- 기존 `formatGeoDateTime`은 호환용이다. 새 화면의 시각 분기는 `Formats.time`이다.

### 14.5 확인 문구: 확정 문안

문구는 그대로 사용하고 자리표시자만 바꾼다. GEO-X-1은 대상 장치 이름이다. 조사는 받침에 맞춘다. S1a는 `primary`, S1b는 `danger`-filled, S8은 시작 시트다.

| 행동·형식 | 제목 | 본문 | 취소 / 확정 |
|---|---|---|---|
| 시작 S8 | {구간} 수집을 시작할까요? | 구간·프로젝트·장치, 구역 문구, 시작 전 점검. 구간 연동 시 `GEO& Work 상태가 '진행 중'으로 바뀝니다.`. `시작하면 이 수집의 프로젝트와 구간을 바꿀 수 없습니다.` | 취소 / 수집 시작 |
| 종료 S1b | {구간} 수집을 종료할까요? | GEO-X-1에서 진행 중인 수집을 종료합니다. 종료되면 장치에 저장된 결과를 확인합니다. | 계속 수집 / 수집 종료 |
| 보고 S1a | {구간}을 완료로 보고할까요? | GEO& Work의 상태가 '완료'로 바뀝니다. 다시 수집하려면 GEO& Work에서 상태를 바꿔야 합니다. | 취소 / 완료 보고 |
| 재부팅 S1b | GEO-X-1을 재부팅할까요? | 재부팅하는 동안 연결이 끊깁니다. 진행 중인 전송은 멈춥니다. | 취소 / 재부팅 |
| 전원 S1b | GEO-X-1의 전원을 끌까요? | 다시 켜려면 장치의 전원 버튼을 눌러야 합니다. 진행 중인 전송은 멈춥니다. | 취소 / 전원 종료 |
| 등록 해제 S1b | GEO-X-1 등록을 해제할까요? | 이 휴대폰에 저장된 인증 정보만 지웁니다. 장치의 파일과 설정은 그대로입니다. 다시 쓰려면 QR 코드로 다시 등록해야 합니다. | 취소 / 등록 해제 |
| 전송 취소 S1b | {이름} 전송을 취소할까요? | 보낸 일부 데이터는 서버에 남을 수 있고, 다시 보내면 처음부터 보냅니다. | 계속 보내기 / 전송 취소 |
| 원본 정리 S1a | 장치 원본을 휴지통으로 옮길까요? | 서버와 다시 대조한 뒤 GEO-X-1의 원본 폴더를 휴지통으로 옮깁니다. 휴지통에서 복원할 수 있습니다. | 취소 / 휴지통으로 이동 |
| 장치 파일 S1a | {이름}을 휴지통으로 옮길까요? | GEO-X-1 휴지통에서 복원할 수 있습니다. | 취소 / 휴지통으로 이동 |
| 장치 휴지통 S1b | GEO-X-1 휴지통을 비울까요? | 표시된 {N}개 항목을 영구 삭제합니다. 되돌릴 수 없습니다. | 취소 / 영구 삭제 |
| 전송 기록 S1a | 전송 기록을 삭제할까요? | 목록에서만 지웁니다. 장치 원본과 서버의 데이터는 그대로입니다. | 취소 / 기록 삭제 |
| 서버 휴지통 이동 S1a | {이름}을 서버 휴지통으로 옮길까요? | 서버 휴지통에서 복원할 수 있습니다. | 취소 / 휴지통으로 이동 |
| 서버 휴지통 S1b, dev | 서버 휴지통을 비울까요? | 표시된 {N}개 항목을 서버에서 영구 삭제합니다. 되돌릴 수 없습니다. 더 있으면 `전체 {M}개 중 표시된 {N}개만 지웁니다.` 추가 | 취소 / 영구 삭제 |
| 서버 프로필 S1b, dev | {프로필}을 삭제할까요? | 이 휴대폰에 저장된 서버 주소와 직원 인증 정보, 저장된 목록을 지웁니다. 서버의 데이터는 그대로입니다. | 취소 / 프로필 삭제 |
| 경유 서버 S1b, dev | 서버 데이터를 삭제할까요? | 선택한 수신 결과를 서버에서 영구 삭제합니다. 되돌릴 수 없습니다. | 취소 / 영구 삭제 |
| Wi-Fi S1a | GEO-X-1을 {SSID}에 연결할까요? | 적용하면 지금 연결이 끊길 수 있습니다. | 취소 / 적용 |
| 직접 연결 S1a | GEO-X-1에 직접 연결할까요? | 장치의 공유기 연결과 진행 중인 전송이 끊길 수 있습니다. | 취소 / 직접 연결 |
| 알림 S1b | 알림을 모두 지울까요? | 지운 알림은 되돌릴 수 없습니다. | 취소 / 모두 지우기 |
| 업로드 서버 S1b, dev | {서버}를 삭제할까요? | GEO-X-1에 저장된 서버 주소와 접근 토큰을 지웁니다. | 취소 / 서버 삭제 |
| 원격 명령 S1b, dev | GEO-X-1에서 명령을 실행할까요? | 명령을 `mono`로 표시. `장치의 수집 사용자 권한으로 실행합니다.` | 취소 / 실행 |
| 작업 등록 해제 S1b, dev | {작업} 등록을 해제할까요? | GEO-X-1의 실행 서비스가 멈춥니다. 저장된 실행 기록과 측정 파일은 그대로입니다. | 취소 / 등록 해제 |
| 설정 재로딩 S1a, dev | 장치 설정을 다시 불러올까요? | 작성 중인 내용을 장치의 현재 설정으로 바꿉니다. | 초안 유지 / 다시 불러오기 |

| 확정 불가 조건 | 추가 문구·처리 |
|---|---|
| 시작 점검 실패 | 점검을 통과해야 시작할 수 있습니다. |
| 구간 `revision` 변경 | GEO& Work에서 이 구간이 바뀌었습니다. 다시 확인하세요. |
| 종료 대상 연결 불가 | 연결되면 종료할 수 있습니다. |
| 보고 서버 연결 불가 | 서버에 연결되면 보고할 수 있습니다. |
| 수집 중 재부팅 / 전원 | 수집 중에는 재부팅할 수 없습니다. / 수집 중에는 전원을 끌 수 없습니다. |
| 원본 정리 receipt 불일치 | 서버 수신 내용이 바뀌어 정리할 수 없습니다. |
| 서버 영구 삭제 미지원 | 행동 없음. `deletionEnabled`=false인 경유 삭제 경로는 닫음 |
| 원격 명령 연결 불가·다른 명령 진행 | 해당 이유 표시·확정 비활성 |

- 연결 끊기·기록 휴지통 이동·원본 복원·휴지통 복원은 별도 확인 없이 처리하고 스낵바·복구 행동을 제공한다.
- 연결 해제는 `GEO-X-1 연결을 끊었습니다.`+`다시 연결`, 기록 이동은 `수집 기록을 휴지통으로 옮겼습니다.`+`실행 취소`다.
- 등록 코드·장치 등록·다시 로그인·새 프로젝트는 S4 폼이다. 확인 대화상자를 추가하지 않는다.

## 15. 지도와 수집 구역

R3 목표 책임은 `GeoMapRenderer`, `GeoMapCard`, `GeoMapScreen`, `GeoRunPanel`, `ZoneMonitor`, `ZoneEventsSource`다. 고정 색은 현재 구현된 `GeoFixedColors`를 사용한다.

### 15.1 데이터 계약

- 구간마다 `features`·`bufferM`·`range`·`bbox`·`lengthM`·`areaM2`·`revision`·`hash`를 받는다.
- `features`는 WGS84 `GeoJSON` Point·LineString·Polygon·MultiPolygon이다. 좌표는 경도/위도 순이다.
- `range`는 GEO& Work가 계산한 Polygon/MultiPolygon이며 hole을 허용한다. 앱이 buffer를 재계산하지 않는다.
- `bufferM`은 경로 한쪽 폭이다. 표시 폭은 `Formats.width`의 2×`bufferM`이다.
- ring은 닫혀 있고 위치가 4개 이상이다. 경도 −180…180·위도 −90…90을 검증한다. 자체 교차 검사는 하지 않는다.
- `range`가 없으면 feature만 표시하고 안/밖 판정은 하지 않는다.
- geometry를 구간과 함께 프로필별 디스크에 캐시한다. 시작 시 `revision`/`hash`를 고정한다.
- 진행·결과는 고정 `revision`이다. 변경 시 `수집 구역이 바뀌었습니다. 이 수집은 시작할 때의 구역으로 표시합니다.`를 표시한다.

### 15.2 렌더링·카메라

- 네이버 지도 Android SDK(NCP Maps, `ncpKeyId`)를 쓴다. 일반은 `Basic`, 위성은 라벨이 있는 `Hybrid`다. 키가 없으면 `None`으로 `sunken` 배경과 overlay만 그린다. 아직 받지 못한 곳도 네이버 격자 대신 `sunken`으로 채운다.
- overlay: 구역은 `PolygonOverlay` 두 겹(채움과 흰 casing 외곽선, 그 위 파란 경계선), 경로·궤적은 흰 casing 위 `PolylineOverlay`, 지점·공백·장치·휴대폰은 비트맵 `Marker`, 불확실성은 미터 반경 `CircleOverlay`다.
- 네이버는 바깥 고리를 시계 방향, 구멍을 반시계 방향으로, 닫는 점 없이 받는다. GeoJSON(RFC 7946)은 반대로 감기므로 고리를 뒤집는다.
- zone/trail/device/phone은 따로 갱신한다. overlay는 바뀐 것만 고친다. 같은 품질의 궤적 구간은 한 선으로 합친다.
- 그리는 순서(전역 z): `range`·영역 채움/casing → 경계 → route casing/route → points → trail casing → 정밀/대략/미확인 → gap ring → 장치 불확실성. 여기까지는 네이버 라벨 아래다. 장치·휴대폰 marker는 라벨 위다.

| 대상·`GeoFixedColors` | 형태·치수 |
|---|---|
| 구역 `mapZone` | fill 16%, dimmed raster 위 22%. `range` 경계 2·dash 6/4·흰 casing 5 |
| 경로·영역 `mapZone` | 2 dp 실선·흰 casing 5 |
| 지점 `mapZone` | 지름 10·흰 ring 2 |
| 궤적 `mapTrailPrecise`/`mapTrailApproximate`/`mapTrailUnknown` | 4·흰 casing 7. 정밀 실선, 대략 dash 8/4, 미확인 dot 2/4 |
| 공백 `mapGap` | 선 없음, 양끝 8 dp ring·흰 casing |
| 장치 `mapDevice` | 원 18·흰 ring 3·바깥 선 1. stale는 흰 속/장치색 ring 3 |
| 불확실성 `mapDevice` | u ≥ 3 m일 때 반경 u, fill 8%·경계 1/40% |
| 휴대폰 `mapPhone` | 원 14·흰 ring 3·정확도 halo 12%. 켠 동안만 |

- 어두운 팔레트(그래파이트·네이비 어둡게·흐림)는 일반 지도에 네이버 야간 모드를 켠다. 위성은 그대로다. 구역 채움은 야간 일반 지도에서 22 %, 그 밖에는 16 %다.
- 로딩·실패: 네이버가 첫 로드를 알리면 끝난다. 보이는 지도가 15초 안에 로드되지 않거나 인증이 거부되면(401·429) "배경 지도를 불러오지 못했습니다. 구역은 그대로 표시합니다."를 띄운다.
- 진입 때 한 번 `range` `bbox`에 맞춘다. 장치가 2 km 이내면 포함한다. 패딩 24+패널 높이다.
- 사용자 pan/zoom을 유지한다. 장치 위치로·구역 전체 보기만 명시적으로 재정렬한다.
- 진행 mode는 장치를 따르되 사용자 pan으로 해제한다. 장치 위치로를 누르면 선택 상태와 따라가기를 복원한다.
- live map은 하나만 실행한다. 전체 지도 진입·카드 화면 밖에서는 카드 `MapView`를 정지한다.

### 15.3 카드·전체 지도

| 항목 | 규칙 |
|---|---|
| 카드 | 본문 너비, 구간·결과 200/진행 240/낮은 창 160, 반경 12, `border` 1·고대비 `borderStrong` |
| 조작 | north-up·제스처 없음, 하나의 탐색 target, 같은 mode 전체 지도 진입 |
| 배지 | 위 시작 12. 구간은 구역 안/경계 근처만, 진행은 모든 구역 상태, 결과 없음 |
| 저작권 | SDK가 그리는 NAVER 로고를 아래 시작에 둔다(카드 8, 전체 지도 12+패널 높이). 가리거나 숨기지 않는다. 전체 지도에서는 로고를 눌러 법적 고지를 연다 |
| 카드 밖 설명 | 경로 1.2 km · 폭 20 m · 영역 1곳 · 지점 4곳 + 위치 문장. 결과는 밖 기록·관찰 공백 |
| 전체 지도 | mode zone/run/route/location과 ticket/run/device 식별. 제목 수집 구역/수집 중/수집 경로/장치 위치, 탭 막대 없음 |
| 조작부 | 끝쪽 inset 12/간격 8, `mapPlate`·작은 그림자. 일반/위성·장치 위치로·구역 전체 보기·패널 위 확대/축소 48 |
| 패널 | `BottomSheetScaffold`, peek 128+별도 navigation inset. handle과 48 dp 펼치기/접기 버튼 |
| 확장 | 선 패턴 포함 범례, 좌표·위성·고도·마지막 위치, 휴대폰 위치 표시, 품질 요약·위치 품질 자세히 |
| 수집 패널 | 수집 중·시계·52 dp `danger`-outline 수집 종료. 같은 S1b 확인 후 결과 |
| 낮은 가로 창 | 끝쪽 패널 320, 56 strip으로 접기. 조작부는 패널을 피함 |

- 위치 문장은 `장치가 구역에서 350 m 떨어져 있습니다.`, `장치가 구역 안에 있습니다.`, `장치가 구역 경계 근처에 있습니다.`, `장치 위치를 확인할 수 없습니다.` 중 하나다.
- 휴대폰 위치는 기본 꺼짐이다. 켤 때만 이유·권한을 요청하고 화면을 닫으면 끈다.
- compass는 회전됐을 때만 위 시작에 표시한다.

| 상태 | 표시·동작 |
|---|---|
| geometry 없음 | 카드·구역 mode 없음 |
| `range` 계산 전 | feature만, 구역 범위가 아직 계산되지 않았습니다. |
| geometry 오류 | overlay 없음, 수집 구역 정보를 읽을 수 없습니다. 원인은 기술 정보 |
| tile 실패 | overlay 유지, 배경 지도를 불러오지 못했습니다. 구역은 그대로 표시합니다. |
| 키 없음 | overlay 유지, 배경 지도를 쓸 수 없습니다. 구역은 그대로 표시합니다. |
| 오래된 위치 | 빈 marker+마지막 위치 16:36:02 |
| fix 없음 | 장치 위치를 확인할 수 없습니다. |
| 로딩 | `sunken` 상자+지도를 불러오는 중입니다 |

- 오프라인 tile 다운로드는 제공하지 않는다.

### 15.4 구역 판정·관찰 범위

1. 1초 device status GNSS를 사용한다. 5초 run telemetry로 판정하지 않는다.
2. age는 같은 장치 시계의 `observedAt`−`lastSampleAtEpochMillis`다. age > 10초·status 중단 > 10초·fix 없음은 즉시 위치 미확인이다.
3. 같은 `lastSampleAtEpochMillis`는 한 표본이다. 반복 응답으로 연속 횟수를 늘리지 않는다.
4. signed distance s는 안 음수/밖 양수/경계 0이다. 모든 polygon과 hole의 가장 가까운 경계를 고려한다.
5. 표본 위도의 국소 등거리 원통 투영을 쓴다. 10 km 이내 오차 < 0.5% 기준이다.
6. u=max(품질 하한, 보고 정확도)다. 하한은 RTK 고정 0.5 m/부동 2 m/단독 5 m다. Phase 1 단독은 max(5 m, 3×HDOP)이며 기술 정보에 추정으로 표시한다.
7. 후보는 s≤−u 안, s≥u 밖, abs(s)<u 경계 근처다.
8. 서로 다른 신선한 표본 2개가 같은 새 후보일 때 바꾼다. 시작·미확인 복귀는 합의 전 위치 확인 중이다.
9. 수집 중 확정 이탈은 `GEO-X-1이 수집 구역을 벗어났습니다 · 35 m` 띠와 `REJECT` 한 번이다. 복귀는 띠 제거, 경계 근처는 배지만이다.
10. 시작 전은 위치 문장·배지만 표시한다. 구역 밖·위치 상태로 시작을 막거나 자동 종료하지 않는다.
11. 1초마다 새 표본이면 지연 1–2초, 2초 표본이면 2–4초다. 출하 전 실제 표본 간격을 측정한다.

| 단계 | 관찰·기록·문구 |
|---|---|
| Phase 1, 이번 rebuild | 휴대폰 lifecycle STARTED·수집 중일 때만. 이탈/복귀는 확정 streak의 첫 표본, 최대 거리와 함께 run ID로 보존 |
| Phase 1 공백 | 앱 비표시·10초 초과 stale·fix 없음 기록. 결과는 구역 밖 기록 2회 · 1분 10초, 관찰한 시간 기준, 관찰하지 못한 시간 2분 병기 |
| Phase 1 알림 | 백그라운드 구역 알림·구역 이탈 설정 없음. 횟수·시간을 전체의 완전한 기록처럼 표현하지 않음 |
| Phase 2, 후속 | 장치가 고정 `revision`으로 기록. `ZoneEventsSource`의 `PhoneObservedZoneEvents`를 `DeviceZoneEvents`로 바꾸고 관찰 한정 문구 제거 |

## 16. 시작과 로딩

### 16.1 복원과 첫 프레임

R3 목표 책임은 `AppRestoreViewModel`·`AppRestoreState`·앱 범위 coordinator다.

| 상태 | 화면 |
|---|---|
| `Loading` 300 ms 이내 | `OnPreDrawListener`로 첫 프레임 유지 |
| `Loading` 300 ms 초과 | 로딩 장면만. 아래 앱 화면은 compose하지 않음 |
| `Restored`(0) | 장치 등록 |
| `Restored`(n) | 작업 |
| `Failed`(cause) | 읽기 실패 원인+다시 시도. 장치 0대로 처리 금지 |

- 로딩 장면은 나타난 뒤 최소 400 ms, 이후 crossfade 240이다. 줄인 움직임 전환은 0이다.
- splash 중 복원이 끝나면 앱으로 바로 간다. exit listener가 splash를 제거한다.
- 장치 reconciliation·진행 작업 adoption은 `Restored` 뒤 앱 범위에서 시작한다. 장치 탭에 의존하지 않는다.
- 시작 시 OS 권한 대화상자는 없다. QR·등록·직접 연결·Wi-Fi 검색·알림 설정·휴대폰 위치를 사용할 때 필요한 권한만 요청한다.

### 16.2 저장·모드·splash

R3 목표 저장 책임은 `AppearanceStore`다. 현재 theme 진입은 `JetsonControllerTheme`이다.

1. `theme_mode`·`theme_light`·`theme_dark`·splash theme ID를 동기 commit한다.
2. 선택한 어두운 팔레트의 `SplashScreen.setSplashScreenTheme(resId)`를 호출한다. API 31+이며 밝은 모드에서도 호출한다.
3. `UiModeManager.setApplicationNightMode`를 호출한다. 밝게 `MODE_NIGHT_NO`, 어둡게 `MODE_NIGHT_YES`, 시스템 `MODE_NIGHT_AUTO`다. 전역 `setNightMode` 금지다.
4. 재생성 후 경로·스크롤·준비/전송/편집 초안·명령 ID를 복원한다. 등록·시작 요청을 재실행하지 않는다.

| 안정적인 splash theme 이름 | values/ | values-night/ |
|---|---|---|
| `Theme.GeoX.Splash.Graphite` | 밝은 `surface` | 그래파이트 `surface` |
| `Theme.GeoX.Splash.Navy` | 밝은 `surface` | 어두운 네이비 `surface` |
| `Theme.GeoX.Splash.Dim` | 밝은 `surface` | 흐림 `surface` |

- manifest 기본은 Graphite다. 첫 설치 설정·post-splash·loader·첫 Compose 배경을 일치시킨다.
- splash는 `surface`+중앙 심볼 96만이다. GEO& wordmark·문구·ring을 추가하지 않는다.
- theme 이름은 시스템이 보존하므로 바꾸지 않는다. 저장 팔레트와 splash ID 불일치는 다음 실행용으로 바로잡는다.
- 이전 theme 값은 모드로 옮기고 팔레트는 표준/그래파이트로 초기화한다.
- 구버전 첫 업그레이드는 이전 시작 창 한 프레임이 남을 수 있다. 이후 여섯 테마와 프로세스 종료 중 시스템 모드 변경을 검수한다.

### 16.3 Loader·오류 장면

R3 목표 GeonLoader는 원본 geon-loader의 `Canvas` 이식이다. 대체 animation을 만들지 않는다.

| 항목 | 값·규칙 |
|---|---|
| 원본·층 | 캐시한 340 px `geo_symbol`, base → second wave → main wave → shine |
| 크기 | S=88, P=round(0.8S)=70, H=round(1.2S)=106, top=−round(0.14S)=−12 |
| main wave | −3P 이동, 72%에 top 도달/88%까지 유지/100%까지 사라짐 |
| second wave | opacity .32, 시작 P/2·0.92S, +2.5P 이동, 66%에 2×top 도달/100%까지 사라짐 |
| shine | brightness 1.9/saturation .6/opacity .7, 62°·42–58% band·300% mask, 74–96%에 100%→0 |
| 주기·easing | 2400, wave cubic-bezier(.45,.05,.35,1), shine ease-in-out |
| base | 밝게 brightness 1.74/opacity 1, 그래파이트·네이비 .9/.28, 흐림 .96/.28, grayscale |
| 로딩 | `surface`, 중앙 loader 88 → 20 → GEO-X(`title`) → 8 → 장치 정보를 불러오는 중입니다.(`body`/`textSecondary`) |
| 오류 | 정적 심볼 56/grayscale/alpha .45 → 20 → 장치 정보를 불러오지 못했습니다(`title`) → 8 → 운영자용 원인(`body`/최대 360) → 24 → 다시 시도(너비 240/높이 52) |
| 줄인 움직임 | 원색 심볼 하나, wave·shine·pulse 없음. 로딩 안내 한 번 낭독 |

- 로딩·오류는 중앙 정렬이다. top bar·탭 막대·추가 ring·추가 로고는 없다.
- loader shine mask 외 gradient는 금지다.

## 17. 템플릿

### 17.1 화면·overlay·블록

| 코드 | 용도·구조 | 탭 막대 |
|---|---|---|
| P1 | 루트 top bar → 띠 → 장치 요약/집중 블록 → 섹션, 행동 영역은 전송만 | 유지 |
| P2 | 상세 top bar → 띠 → H1/배지/meta → 섹션 → 관리 → 기술 정보 → 행동. 장치·영수증·정보 변형 | 유지 |
| P3 | 구간/지도 또는 수동 프로젝트·구간 → 수동적 준비 상태 → 시작 → S8 | 유지 |
| P4 | H1/meta → 지도 240 → 수치 → 마지막 확인 → 카메라·큰 지도·중계 → 종료 | 숨김 |
| P5 | 보기 선택 → 상태/날짜별 행 → 더 보기 → 빈 상태 | 유지 |
| P6 | 위치 경로 → 검색 → 5종 picker → 날짜별 행 → 선택/전송 검토 행동 | 유지 |
| P7 | 필드 섹션 → inline 검증 → 행동 | 설정 유지, 키보드 중심 편집기 숨김 |
| P8 | top bar → 지도·카메라·QR·영상 stage → 조작부·패널 | 숨김 |
| P9 | 로그 picker → 실시간/보관 → 선택 가능한 텍스트 → 행동 | 유지 |
| P10 | 등록은 위 56, 로딩·오류 중앙. 등록에만 설정 진입 | 숨김 |
| S1a/S1b | 일반/위험 `GeoConfirmDialog` | 해당 없음 |
| S2 | `GeoInfoDialog` | 해당 없음 |
| S3/S4/S5 | 선택/폼/정보 `GeoSheet`, 지도는 persistent panel | 해당 없음 |
| S6/S7/S8 | `GeoMenu`/`GeoSnackbar`/`GeoStartSheet` | 해당 없음 |
| B1/B2/B3 | `GeoStatusStrip`/`GeoFocusBlock`/`GeoStateBlock` | 부모를 따름 |
| OS/AUTO | 시스템 권한 / 화면 없는 상태 처리 | OS 대화상자는 앱이 그리지 않음 |

- 알림 기준은 저장 공간 50–99%, 장치 온도 40–110°C, 작업·전송 시작/종료를 한 페이지에 둔다.
- `fan`은 자동·지원 최솟값·수동 비율과 로딩/오류/미지원 상태를 유지한다.
- 보정 정보 중계는 준비·진행에 운영자 진입점을 둔다.
- 서버 영상 재생·재시도, 휴지통 복원 불가·영구 삭제 미지원 상태를 유지한다.

### 17.2 기존 138항목 대응

번호는 `R3-final.md` §7.2 inventory다. 각 번호를 한 번씩 배정한다. 추가 상태·overlay는 부모 템플릿을 바꾸지 않는다. 소유 탭·경로는 §2다.

| 템플릿·처리 | 원본 번호 | 목적지·예외 |
|---|---|---|
| P1 | 1, 9, 34, 66 | 장치·작업·전송·설정 |
| P2 장치 | 4, 5, 79 | 상세·오프라인·센서. 5는 B1 병행 |
| P2 정보 | 13, 68, 99 | 작업 상세·앱 정보·개발자 작업 관리 |
| P2 영수증 | 25, 57 | 수집 결과·전송 상세 |
| P3 | 15, 18 | 구간·수동 준비 |
| P4 | 21, 22 | 진행·상태 변형 |
| P5 | 29, 43, 60, 63, 88, 92, 127, 130 | 기록·서버·전송 기록·휴지통·업로드 서버·개발자 hub·직접 연결·알림 |
| P6 | 36, 41, 51, 101 | 보내기·장치 파일·경유 서버·폴더 선택 |
| P7 | 55, 67, 70, 73, 91, 100, 107 | 전송 확인·테마·알림·Wi-Fi·서버 편집·작업 등록·설정 |
| P8 | 80, 84, 85, 121 | 지도·카메라·상태·QR |
| P9 | 31, 69, 97, 102 | 기록 로그·도움말·연결 진단·작업 로그 |
| P10 | 2, 111, 113, 133 | 등록 통합·시작 로딩/오류 |
| S1a | 27, 38, 45, 59, 62, 78, 109, 114 | 보고·휴지통·정리·기록 삭제·Wi-Fi·재로딩·직접 연결 |
| S1b | 7, 23, 35, 46, 47, 53, 58, 65, 90, 94, 96, 108, 120, 132 | 위험 확인. 58=35, 96=7 |
| S2 | 48, 77 | 영수증·연결 상태 |
| S3 | 6, 11, 20, 54, 86, 106 | 연결·작업·프로젝트·서버·캡처 위치·로그 선택 |
| S4 | 124, 125, 126 | 코드·등록·같은 시트의 진행/오류 |
| S5 | 32, 81 | 위치 품질·지도 패널 |
| S6 | 8, 28, 39, 76 | 장치·결과·탐색기·Wi-Fi 메뉴 |
| S7 | 33, 40, 50, 87 | 기록·파일·서버 이동 복구·개발자 해제 |
| S8 | 17, 19 | 단일 시작 확인 |
| B1 | 137 | 전역 연결 띠 |
| B2 | 3 | 복구 집중 블록 |
| B3 | 10, 12, 14, 16, 26, 30, 37, 44, 49, 52, 56, 61, 64, 71, 74, 82, 89, 103, 104, 105, 115, 118, 122, 128, 131, 134 | 빈/오류/권한/미지원. 37은 B1 병행, 134는 사용할 때 OS로 연결 |
| OS | 72, 75, 83, 116, 119, 123, 129 | 알림·Wi-Fi·위치·로컬 네트워크·Bluetooth·카메라·근처 Wi-Fi |
| AUTO | 135, 136 | 자동 탐색·보고 outbox. 보고 대기/실패는 행에 표시 |
| P8/P9/B3 | 42 | 파일 미리 보기·빈 상태 |
| 이동 경로 | 110 | 개발자 작업 설정 접근 |
| 통합·퇴역 | 24, 93, 95, 98, 112, 117 | 진행/개발자 메뉴·관리 hub·legacy·연결 hub·구형 목록을 별도 화면으로 복구하지 않음 |
| 공통 제한 | 138 | `DatePicker`·`TimePicker`·`Toast` 추가 없음. 지도 패널·목록 새로고침은 공통 구조 |

- 새 서버 연결은 `settings/server`의 P2 정보+행동 영역, 프로필 편집은 `dev/server-profiles`다.
- 새 지도·중계·`fan`·기술 정보도 같은 템플릿·상태 규칙을 따른다.

## 18. 검사

### 18.1 금지 항목

| 금지 | 확인할 대안 |
|---|---|
| AI 시안의 상투적 gradient·glow·glass·장식 일러스트·의미 없는 색 강조 | 평면 `surface`·역할색. gradient 예외는 loader shine mask만 |
| eyebrow·장식 단계 번호·왼쪽 색 띠·반복 상태 타일·네이비 다음 행동판 | H1·`GeoSection`·행·`GeoReceipt`·`GeoFocusBlock` |
| 배지·segmented 외 `pill`·`pill` 버튼·filter chip | 반경 10 버튼·picker. 원형 아이콘 버튼 별도 허용 |
| raw Material·Material 아이콘 화면 import | 공통 Geo API·`GeoIcons` |
| 이름 말줄임·이름 없는 장치 오류·미확인을 0 | 전체 이름·대상+원인+복구·미확인 |
| 일반 행 tint·반투명 글자·수치 상태색 | 역할 글자색·아이콘+설명, 선택 행 tint만 허용 |
| top bar 새로고침·전송 bar 장식 home/`folder` | freshness+다시 확인·글자 경로 |
| shimmer·pulse·bounce·카운트업 | 정적 skeleton·정해진 motion |
| 느낌표·이모지·확인/예/OK 확정 | §14 동작 이름·문구 |
| 가짜 status 아이콘·앱이 그린 OS 권한 | 실제 시스템 영역·사용 시점 권한 |

### 18.2 코드·문서 검사

| 검사 | 확인할 것 |
|---|---|
| 문서 변경 | 변경 내용·링크·Kotlin 이름·단위·표·상호 참조. 문서만 바꾸면 Android 빌드·장치 검사는 불필요 |
| 색 | `scripts/check_slate_harmony.py`: Kotlin/JSON 일치, 63쌍×6=378와 고정색 13쌍 대비 |
| 글자·팔레트 | `GeoTypeTest`, `GeoPaletteTest`, `AdaptiveThemeTest` |
| 상태·표기 | `NextActionTest`, `VocabularyTest`, `FormatsTest` |
| 컴포넌트 | `GeoStackingRuleTest`, `GeoStartSheetRulesTest`, `FigureGridRulesTest`, `ReceiptOrderTest`, `StripPriorityTest` |
| overlay·로그·shell | `SheetFallbackTest`, `SnackbarTimeoutTest`, `LogFollowTest`, `WindowClassTest`, `StickyRuleTest`, `GeoIconsTest` |
| 식별·초안 | `PreparationDraftTest`, `TicketPreparationTest`, geometry 파싱·대상 불일치·같은 요청 ID 재시도 |
| import 경계 | 이관 화면의 raw Material import 0. 자동 import lint 도입 전에도 수동 검토 |
| CI | `.github/workflows/reliability.yml`의 색·Android·backend·receiver·QA 계약 gate |

- 수치 대비 통과와 실제 화면 검수를 구분한다. 지도 판·`focus`·stale·on-dark의 실제 배경을 확인한다.
- 지도 순수 함수는 hole·MultiPolygon·경계·중복·stale·2표본·관찰 공백을 검사한다. 목표 API 구현 때 연결한다.
- 장치 검수는 §13 행렬·F1–F8 탭 수·실제 GNSS 간격·프로세스 종료 후 테마/명령/초안 복원을 포함한다.
- 코드·색 작업에서 관련 검사를 선택한다. 문서 편집으로 배포·서비스 재시작·센서 작동을 수행하지 않는다.

## 19. 변경 이력

| 기준 | 내용 |
|---|---|
| GEO& Work `3f85615f` | R3 web token 기준. 밝은 표준·어두운 3종 출처 |
| 2026-10-19 `R3-final.md`·`KIT.md` | shell·규칙·여섯 팔레트·지도·시작·확인 문구 기준 |
| 2026-10-20 | 전체 교체. 19개 장·실제 Kotlin API·토큰 참조·템플릿 대응·검수 기준 |

- 후속 변경은 날짜·규칙·API·검사 근거를 이 표에 추가한다.
- 이전 설계 문서·보고서의 상충 규칙을 이 가이드의 근거로 사용하지 않는다.
