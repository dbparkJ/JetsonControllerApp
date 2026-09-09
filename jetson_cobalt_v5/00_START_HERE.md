# Jetson Controller — Cobalt Field V5 / 여기서 시작

**이 폴더 하나가 최신 통합 세트입니다. V4의 초록·라임색 MD를 함께 사용하지 마세요.**

## 시작 순서

`JETSONCONTROLLER_COBALT_FIELD_V5.md` → `COBALT_FIELD_STYLE_GUIDE.md` → `design/tokens.json` → `screens/00-overview.png` 및 개별 화면을 읽습니다. `START_PROMPT.txt`를 구현 에이전트의 시작 입력으로 사용합니다. 이 폴더를 그대로 전달하면 상대경로가 유지됩니다.

새 색상만 적용하는 작은 패치가 아니라, 이전에 요청한 UI 리빌딩 계약까지 포함한 문서입니다. 이미 구현된 기능과 새 화면은 불필요하게 다시 만들지 않고 현재 저장소 상태와 대조합니다.

## 보기·구현·검증 자료

| 위치 | 역할 |
|---|---|
| `JETSONCONTROLLER_COBALT_FIELD_V5.md` | 단독으로 읽어도 되는 통합 작업 지시서. 기능 재연결·테스트·실기검증·완료 후 자기 파일 제거 지시 포함 |
| `COBALT_FIELD_STYLE_GUIDE.md` | 영구 스타일 가이드. 코드 반영 후에도 유지 |
| `START_PROMPT.txt` | 그대로 복사할 시작 프롬프트 |
| `prototype.html` | 더블클릭해 브라우저에서 볼 수 있는 독립 로컬 시안. 장비 통신 없음 |
| `screens/` | 개별 앱 시안 17개와 보드 2개, 모두 코발트 테마 |
| `design/tokens.json` | Light/Dark 의미 기반 색상·글꼴·간격·터치 규칙 |
| `design/scene-spec.json` | 같은 화면의 구조 데이터 |
| `design/figma-import-data.json` | 로컬에서 측정한 Figma 생성용 레이아웃 데이터 |
| `figma-plugin/` | 같은 팔레트의 편집 가능한 Figma 레이어 생성/PNG 내보내기 코드; 실제 Figma 실행은 미검증 |
| `reference/` | 처음 제공된 캡처 8장 및 마지막에 보여준 Cobalt 홈/보드. 원본의 초록색은 최신 스타일 기준이 아님 |
| `evidence/` | 이번 로컬 검사 결과·원본 대조·정합성 검사 |
| `tools/` | 선택적인 재생성/검사 도구. 실제 앱 코드가 아님 |
| `CHANGELOG_V5.md` | V4→V5 변경 범위와 적용 우선순위 |

## 디자인 기준을 섞지 않습니다

**마지막 Cobalt Field 시안 + 이 세트의 tokens**가 색상과 시각 구성의 기준입니다. 이전 생성형 홍보 보드의 하드웨어 사진, iOS 프레임, 그라데이션, 초록색 완료 상태, 가짜 진행률·ETA는 포함하지 않습니다. 원본 캡처는 기존 동선을 이해하는 근거입니다.

핵심 Light 색: 네이비 `#172B4D`, 코발트 `#2456D8`, 아이스 블루 `#DCE7FF`, 오프화이트 `#F7F5F2`. Dark 및 상태 역할은 가이드/토큰에 함께 정의합니다. **완료 상태에도 초록색을 쓰지 않고 인디고와 문구·아이콘으로 구분합니다.**

## 완료와 미완료를 구분합니다

이번 세트는 로컬 시안과 인계 문서입니다. 실제 Android 앱 구현, 저장소 커밋/배포, 스마트폰·Jetson 실측, 완성된 Figma 파일 또는 Figma PNG 내보내기 결과가 아닙니다. Figma 플러그인은 새 색상 데이터로 동기화하고 구문/mock 검사를 했지만 실제 Figma 렌더링은 후속 확인이 필요합니다.

로컬 시안 검사 결과와 실제 장비 검증 결과를 혼동하지 마세요. `evidence/VALIDATION.md`에 검사 범위를 적었습니다. 브라우저·Figma가 막혀도 가능한 네이티브 구현과 자동 테스트는 계속하고, 미검증 항목만 명확히 남깁니다.

## 작업 지침 파일 제거

필수 구현·검증·인계가 끝나면 `JETSONCONTROLLER_COBALT_FIELD_V5.md`의 **작업 시작 시 기록한 정확한 경로 한 개만 마지막에 삭제**합니다. 이 START 문서, 영구 스타일 가이드, 코드·테스트·이미지·토큰·다른 MD는 삭제 대상이 아닙니다. 필수 작업이 남으면 지시 MD를 유지합니다.
