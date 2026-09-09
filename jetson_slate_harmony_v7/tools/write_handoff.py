"""Generate synchronized handoff documents and Kotlin reference from canonical tokens."""
from pathlib import Path
import json,sys
from color_math import contrast
P=Path(__file__).resolve().parents[1]
t=json.loads((P/'design/tokens.json').read_text(encoding='utf-8'))['color']; l=t['light']; d=t['dark']
table='| 역할 | Light | Dark |\n|---|---|---|\n'+'\n'.join(f'| `{k}` | `{v}` | `{d[k]}` |' for k,v in l.items())
summary='| 용도 | Light | Dark |\n|---|---|---|\n'+'\n'.join(f'| {name} | `{l[k]}` | `{d[k]}` |' for k,name in [('canvas','전체 바탕'),('surface','기본 표면'),('sectionSoft','부드러운 섹션'),('sectionRaised','한 단계 강조한 섹션'),('hero','현재 작업'),('primary','주요 버튼'),('ink','본문'),('muted','보조 설명')])
checks='| 조합 | Light | Dark |\n|---|---|---|\n'
for a,b in [('onPrimary','primary'),('ink','surface'),('muted','hero'),('onAccent','accent'),('controlBorder','surface')]:
 checks+=f"| `{a} / {b}` | {contrast(l[a],l[b]):.2f}:1 | {contrast(d[a],d[b]):.2f}:1 |\n"
style=f'''# Slate Harmony V7 — 라이트·다크 색상 스타일 가이드

작성: 2026-09-09 · JetsonControllerApp · **영구 보관 / 삭제 대상 아님**

## 1. 이번 변경의 핵심

V6의 cool / neutral / warm 섹션 배색을 **같은 색상 방향의 base / soft / raised 표면 단계**로 교체한다. 색마다 채도만 낮추는 방식이 아니라, 배경·섹션·본문·행동의 관계를 다시 정한다.

이 파일의 정확한 HEX는 `design/tokens.json`과 동일하다. 제작상의 OKLCH 값은 `design/palette-oklch.csv`로 확인한다. 연구 근거와 한계는 `COLOR_RESEARCH.md`의 R1~R7 및 A1~A5에 있다. **이 팔레트가 논문에서 검증된 최적색이거나 시각 피로를 줄인다는 주장은 하지 않는다.**

사용자 선호에 따라 초록·청록·라임·형광 코발트는 사용하지 않는다. 기존 탭·기능·명령·입력 보존·안전 정책은 바꾸지 않는다.

## 2. 색을 사용하는 순서

**큰 면적은 중립색 → 섹션은 명도 단계 → 주요 행동은 슬레이트 블루 → 경고·실패만 의미색.** 색이 적다고 모든 요소를 회색으로 같게 만드는 것은 아니다. 읽는 글자, 누르는 버튼, 선택한 항목의 차이는 남긴다.

라이트는 약간 차가운 오프화이트와 짙은 회색 글자를 사용한다. 작업 카드만 살짝 블루그레이로 구분하고, 흰 글자를 넓은 카드 전체에 사용하지 않는다. 다크는 남색이 아닌 차콜 계열의 명도 단계를 사용하고 본문은 부드러운 밝은 회색, 주요 버튼은 저채도 밝은 슬레이트로 유지한다. 다크를 라이트의 자동 반전으로 만들지 않는다.

{summary}

## 3. 단일 색상 토큰

{table}

`onPrimary`, `onAccent`, `heroText`, `heroMuted`, `onDanger`를 배경과 함께 바꾼다. 배경만 치환하고 기존 흰색·검은색 글자를 남기면 안 된다. `success`는 중립 계열이며 반드시 체크와 ‘완료/확인됨’ 문구를 함께 쓴다. `info`는 요청·진행 상태에, `warning`은 주의에, `danger`는 실패·파괴적 확정 행동에 사용한다. 연결·센서·전송의 실제 판정은 색상 테마가 바꾸지 않는다.

`sectionDanger`는 일반 중립 표면과 같다. **위험 동작 목록 전체는 중립색**으로 묶고 위험 아이콘·제목·확정 버튼만 danger 역할로 표시한다. 실제 실패 메시지나 확인창의 아이콘 타일에는 작은 `dangerBg` 영역을 사용할 수 있다. 경고와 오류를 조화라는 이유로 감추지 않는다.

## 4. 섹션 표면 계약

| 영역 | 표면 | 구현 |
|---|---|---|
| 전체 앱 / 스크롤 바탕 | canvas | 같은 테마의 루트와 시스템 영역을 조율 |
| 일반 카드·모달·파일 행·하단바 | surface / sectionBase | 표면의 기본 단계 |
| 준비 상태·장비 설정·서버 목적지·센서 그룹 | sectionSoft | 제목과 관련 행을 함께 묶음 |
| 결과 요약·최근 이력·앱 설정 | sectionRaised | 같은 색 계열에서 한 단계 다른 명도 |
| 현재 작업·실행 중 대표 작업 | hero | 정상 섹션보다 약간 더 색도를 허용 |
| 위험 동작 목록 | sectionDanger | 중립 바탕; 개별 위험 텍스트와 아이콘으로 구분 |
| 선택 탭·선택 강조 | navSelected / accent | 선택 아이콘·라벨·체크 의미 함께 유지 |

`sectionRaised`는 **시각 강조 단계**라는 이름이지 모든 Light 표면이 반드시 더 밝아야 한다는 뜻이 아니다. 라이트에서는 기본 표면보다 어두워져 구분되고, 다크에서는 밝아져 구분된다. 이번 정확한 값은 디자인 결정이며 학술적 표준 간격이 아니다.

섹션 사이는 기존 약 16~20dp 간격을 유지하며 제목·여백·배경 차이를 함께 사용한다. 경계가 부족하면 먼저 간격·제목·그룹 범위를 확인한다. 색상 차이를 과도하게 키우거나 섹션마다 새 유채색을 추가하지 않는다. 행 번호에 따라 번갈아 색을 칠하지 않는다. 같은 기능은 같은 표면 역할을 사용한다.

## 5. 버튼·선택·경계

주요 행동은 `primary/onPrimary`로 표시한다. 작은 면적에 집중하므로 다크의 주요 버튼은 섹션보다 밝아도 된다. 저채도 tonal 버튼은 보조 행동·선택 상태에 사용하고, 모든 활성 버튼을 비활성 회색과 같게 하지 않는다.

기존 `border/sectionBorder`는 약한 장식 경계다. 입력·아웃라인 버튼처럼 경계가 가용성을 전달할 때는 **새 `controlBorder`**를 사용한다. 포커스에는 `focusRing`을 사용한다. 색을 줄이려고 모든 레이어의 opacity를 일괄 낮추지 않는다.

체크박스는 체크 기호, 탭은 선택 표시·강조 라벨, 실패는 아이콘과 이유를 동반한다. 작은 미세 색차만으로 상태를 전달하지 않는다. 입력·버튼의 크기와 기존 semantics/testTag/포커스 동작을 유지한다.

## 6. 명도·색도 제작 규칙

큰 일반 표면과 브랜드색은 OKLCH의 같은 260° 방향을 출발점으로 만들었다. 큰 중립 표면은 낮은 색도를 유지하고, 현재 작업·주요 버튼에만 상대적으로 더 높은 색도를 부여했다. 경고/오류는 의미를 위한 별도 색상 예외다.

제작 좌표와 최종 8비트 sRGB의 재계산 값은 반올림 때문에 다를 수 있다. 매우 낮은 색도의 hue 각도는 작은 RGB 변화에 민감하므로 ‘모든 최종 픽셀의 hue가 정확히 260°’를 검사 목표로 삼지 않는다. OKLCH 색도는 HSL 채도나 연구의 Munsell 채도와 동일한 수치가 아니며, 색도 감소율을 ‘피로 감소율’로 보고하지 않는다. [A4]

## 7. 대비와 가독성

본문·보조 설명·버튼 텍스트는 4.5:1 이상을 자체 목표로, 필수 컨트롤/포커스/상태 그래픽은 인접색과 3:1 이상을 검사한다. 장식용 섹션 배경 간격에 3:1을 강요하지 않는다. 이 숫자는 W3C 대비 기준을 참고한 구현 검사이며 실제 눈의 편안함을 측정한 점수가 아니다. [A1][A2][A3]

대표 불투명 색 쌍 계산:

{checks}

반올림한 표가 아니라 원래 계산값으로 통과 여부를 판정한다. 모든 임의 조합이 통과하는 팔레트라는 뜻이 아니다. 새로운 배경 위에 글자/아이콘을 놓을 때 사용 쌍을 다시 검사한다.

기존 글꼴 크기·한국어 줄바꿈·최소 터치 영역은 유지한다. 작은 글씨로 줄여 화면에 우겨넣지 않는다. 로컬 PNG는 Noto Sans CJK KR, Figma 코드는 Noto Sans KR을 우선하며, 네이티브에서는 기존 SansSerif/프로젝트 폰트를 보존한다. 폰트 파일은 제공하지 않는다.

## 8. Compose / Figma 적용

`compose-reference/SlateHarmonyColors.kt`와 `SlateHarmonySection.kt`는 **통합 참고 코드이며 컴파일·설치된 앱 패치가 아니다.** 실제 Material3 버전과 기존 토큰 타입을 확인해 해당 위치에 적용한다. 같은 책임의 두 번째 테마 엔진을 만들지 않는다.

ColorScheme의 primary/secondary/tertiary, container/on-container, surfaceContainer 계열과 outline을 매핑한다. tertiary를 경고색으로 임의 매핑해 일반 선택 요소가 황갈색이 되지 않게 한다. 앱이 선택한 테마를 주입하고 OS 설정을 별도로 읽어 테마가 엇갈리게 하지 않는다. 명시적 섹션의 tonal/shadow elevation을 0으로 시작하고 상위 tonal elevation·동적 색상의 덮어쓰기를 점검한다. [A5]

Figma 생성 코드도 동일 토큰과 32개 대표 상태를 포함한다. **실제 Figma 생성·텍스트 렌더링·PNG export는 이번에 실행하지 않았다.** 로컬 시안과 혼동하지 않는다. 초록색·이전 V6 warm/cool 토큰이 남는지 생성 전 자료를 검사한다.

## 9. 상태 시안과 검증 한계

`screens/`의 번호가 붙은 32개 PNG는 라이트·다크, 작업·데이터·설정, 연결 끊김, 큰 글꼴, 등록 전, 장비 선택, 확인창의 **로컬 렌더링 시안**이다. 시안 수치·파일·연결 상태는 데모이며 앱에 정상 기본값으로 넣지 않는다. 상세는 전체 기능을 구현한 앱이 아니라 색상 적용 범위를 보여준다.

검사 결과는 `evidence/VALIDATION.md`에 실행 범위별로 기록한다. 접근 가능한 실제 Android·Jetson에서 UI를 검증하기 전에는 실기 완료로 표시하지 않는다. 전체 접근성 인증, 눈 피로 감소, 한국 사용자 선호 검증도 아직 수행하지 않았다.

## 10. 근거

학술 논문 R1~R7과 공식 문서 A1~A5의 제목·DOI·링크·확인 범위·한계는 `COLOR_RESEARCH.md`에서 확인한다. 스타일 가이드와 연구 보고서는 작업 완료 뒤에도 보관한다.
'''
(P/'SLATE_HARMONY_STYLE_GUIDE.md').write_text(style,encoding='utf-8')
prompt='''00_START_HERE.md와 JETSONCONTROLLER_SLATE_HARMONY_V7.md를 읽고,
현재 JetsonControllerApp의 라이트·다크 색상과 섹션 표면을 V7로 조정해.

COLOR_RESEARCH.md는 연구 근거와 한계를 설명하는 자료야.
논문에 없는 최적색·피로 감소 효과를 사실처럼 주장하지 마.
정확한 구현값은 design/tokens.json과 SLATE_HARMONY_STYLE_GUIDE.md를 사용해.
V6의 차가운/따뜻한 섹션 배색을 같은 회청색 계열의 명도 단계로 교체하고,
초록·청록·라임·네온 파랑으로 되돌리지 마.

먼저 AGENTS.md, 최신 브랜치·HEAD·미커밋 변경, 실제 색상 원천을 확인하고
사용자 작업을 보존해. 문서의 과거 SHA로 되돌리거나 구형 테마를 덮어쓰지 마.
현재 실제 화면에 맞게 역할을 매핑하고 필요한 최소 변경을 스스로 판단해.

canvas / sectionBase / sectionSoft / sectionRaised / hero를 일관되게 적용해.
배경과 전경을 함께 바꾸고, primary 버튼·선택·controlBorder·focusRing의
식별성을 유지해. 전체 opacity를 낮추거나 경고·실패를 회색으로 숨기지 마.
위험 목록 전체는 중립 표면으로 두되 실제 오류·위험 행동만 국소 강조해.

이번에는 탭·기능·통신·인증·재연결·전송 단위·RTK·안전 정책을 바꾸지 마.
스크롤·선택·초안과 장비별 상태를 보존하고, 현재 코드·기기의 제약과 맞지 않는
세부 배치는 근거를 확인해 조정해. PNG나 HTML을 제품 화면으로 붙이지 마.

코드 수정 → 빌드·자동 검사 → 접근 가능한 승인 기기의 화면 검증까지 진행해.
라이트/다크의 모든 루트와 상세·확인창, 큰 글꼴, 끊김, 포커스·선택을 확인해.
제공 로컬 검사와 실제 네이티브/Figma 검증을 구분하고 미실행을 통과로 쓰지 마.
운영 장비 재부팅·네트워크 중단·파일 삭제로 색상을 검증하지 마.

최종 토큰·실제 앱 변경 전후 캡처·검증·문서와 다르게 적용한 이유를 보관해.
전체 완료 조건을 충족하면 마지막에 작업 시작 때 기록한
JETSONCONTROLLER_SLATE_HARMONY_V7.md의 정확한 한 파일만 삭제해.
필수 구현·검증이 남으면 그 MD를 유지하고 다른 문서·코드·자료는 삭제하지 마.
'''
(P/'START_PROMPT.txt').write_text(prompt,encoding='utf-8')
agent=f'''# JetsonControllerApp — Slate Harmony V7 실행 지시서

작성: 2026-09-09 · 대상: `dbparkJ/JetsonControllerApp`  
범위: **연구를 참고한 라이트·다크 색 조화 + 섹션 표면 재정리**  
상태: 로컬 시안·토큰·적용 지시서. 원격 앱 코드 반영·네이티브 설치 완료본이 아니다.

## 0. 우선순위와 교체 범위

V5/V6의 배색 지침을 이 V7로 대체한다. 특히 V6의 cool / neutral / warm / danger라는 큰 섹션별 색상 구분을 더 이상 활성 지침으로 쓰지 않는다. 기존 기능·정보 구조·통신·복원·안전 계약은 보존한다. 문서 여러 개를 동시에 적용하여 새 리빌딩을 시작하지 않는다.

판단 순서는 **최신 사용자 요구와 실제 안전/장치 사실 → 현재 기능 보존 → V7 색상 역할 → 로컬 시안의 픽셀**이다. 논문은 설계 원칙의 근거이지 장치 제약보다 우선하는 절대 명세가 아니다. 연구에서 확인되지 않은 ‘최적 팔레트’·‘피로 감소’를 완료 보고에 쓰지 않는다.

## 1. 시작 전 확인 — 코드부터

작업 지침으로 읽은 이 MD의 정확한 경로를 기록한다. `AGENTS.md`, 현재 브랜치·HEAD·미커밋 변경·최근 작업을 확인한다. V6 기록의 과거 SHA나 구형 main으로 되돌리지 않는다. 원격 조회를 이번 자료가 다시 수행했다고 가정하지 않는다.

```bash
git status --short
git branch --show-current
git rev-parse HEAD
git log -5 --oneline
rg 'Cobalt|Slate|TealPrimary|sectionCool|sectionWarm|surfaceContainer|Color\\(0x' app/src/main
```

`reset --hard`, `clean -fd`, 강제 push, 임의 stash를 사용하지 않는다. 최신 적용 브랜치가 확인되지 않으면 그 코드를 추측하여 수정하지 말고 독립적인 토큰·검사를 진행하며 차단 항목을 기록한다. 원격 push·병합·배포는 별도 승인 범위에서만 한다.

색상 원천을 실제로 찾아 기록한다: 기존 ColorScheme·커스텀 토큰·하드코딩된 Surface·Button·상태 배지·시스템 바·동적 색상·tonal elevation. 파일 이름 하나만 치환하고 이전 팔레트가 남게 하지 않는다.

## 2. 읽을 자료

| 자료 | 용도 |
|---|---|
| `design/tokens.json` | 정확한 Light/Dark 색상값 |
| `SLATE_HARMONY_STYLE_GUIDE.md` | 역할·전경/배경 쌍·섹션 위계 |
| `COLOR_RESEARCH.md` | 7편 연구·공식 지침의 관찰과 한계 |
| `screens/` | 새 배색의 대표 상태 32개, 실제 앱 캡처가 아님 |
| `prototype.html` | 데모 상호작용·테마·텍스트 확대 확인 |
| `compose-reference/` | 기존 Compose 구조에 통합할 참고 코드 |
| `figma-plugin/` | 선택적인 편집용 생성 코드; 실제 Figma 실행 미검증 |
| `evidence/` | 이번 로컬 검사의 정확한 범위와 결과 |
| `reference/previous_v6/` | 비교 자료, 활성 디자인 기준이 아님 |

PNG·HTML의 LAN·작업·파일·센서·시간·용량은 데모다. 새로운 API 필드나 성공 기본값으로 붙이지 않는다. 로컬 갤러리의 상태 선택기를 release에 노출하지 않는다.

## 3. 디자인 계약

{summary}

정확한 전체 역할은 스타일 가이드에 있다. 일반 표면은 동일 계열로 통일한다. 하드코딩된 베이지·갈색 섹션을 제거하고 `sectionRaised`로 대체한다. 초록·청록·라임·형광 파랑을 새로운 대체 강조로 추가하지 않는다.

### 역할 매핑

| V6 / 기존 용도 | V7 역할 | 주의 |
|---|---|---|
| sectionCool: 준비/장비/목적지 | sectionSoft | 제목·관련 행에 동일 배경 |
| sectionNeutral: 기본 카드/선택 | sectionBase | 기본 표면으로 정리 |
| sectionWarm: 결과/최근 이력/앱 설정 | sectionRaised | 따뜻한 hue 대신 명도 단계 |
| sectionDanger: 위험 목록 | sectionDanger(중립) | 붉은 글자·아이콘·확정만 유지 |
| 현재 작업 | hero + heroText/heroMuted | 넓은 포화색·흰 글자 카드 금지 |
| 활성 주요 버튼 | primary + onPrimary | 비활성 색처럼 보이지 않게 |
| 선택 배경 | accent/navSelected + 전경 쌍 | 체크/라벨/semantics 함께 |
| 장식 테두리 | border/sectionBorder | 가용성을 전달하는 유일한 경계로 쓰지 않음 |
| 입력/아웃라인 버튼 경계 | controlBorder | 장식 경계와 분리 |
| 포커스 | focusRing | 배경과 대비 및 잘림 확인 |

섹션은 제목·여백·배경 차이를 함께 사용한다. 줄마다 번갈아 칠하지 않는다. 단순 설정 행은 같은 표면을 공유하며 중첩 카드와 그림자를 늘리지 않는다. 긴 목록은 LazyColumn 가상화를 보존하고 한 개의 거대한 Column으로 묶지 않는다.

‘전체 채도 필터’·‘전체 opacity 감소’로 처리하지 않는다. 낮은 채도와 높은 글자 대비는 동시에 유지할 수 있다. 모든 배경 변경은 on색 변경과 한 단위로 검토한다.

## 4. 기존 기능은 그대로

홈·작업·데이터·설정 4탭과 현재 구현된 기능·버튼 동작을 유지한다. 현재 작업을 최우선으로 읽히게 하되 기능 추가/삭제·대형 레이아웃 변경을 이번 색상 수정에 포함하지 않는다.

기존 등록·인증·업로드 단위·LAN/Direct/BLE capability·모바일 RTK 경로·센서/프레임 최신성·명령 pending·재연결/백오프/세대 가드·위험 확인을 변경하지 않는다. 테마 전환이 재연결이나 장비 명령을 유발하면 회귀다. 장비별 선택·초안·스크롤도 보존한다.

성공 중립화는 색만 바꾸는 것이다. 기존 정상·실패·미확인 상태 판정을 바꾸지 않는다. 실패를 시각적으로 숨기거나 warning/danger 역할을 일반 섹션색으로 대체하지 않는다. 모달의 취소/확정 의미도 그대로다.

## 5. 구현 순서

| 단계 | 작업 | 종료 조건 |
|---|---|---|
| A | 최신 코드·테마·사용자 변경 파악 | 수정 지점과 보존 범위 기록 |
| B | 기존 토큰 및 ColorScheme 역할 갱신 | Light/Dark 전경·배경과 경계 동기화 |
| C | 섹션 온도 배색을 명도 단계로 교체 | 같은 역할의 섹션이 같은 표면 사용 |
| D | 상세·모달·선택·비활성·포커스·시스템 바 확인 | 기본 보라/초록/옛 코발트 잔존 없음 |
| E | 자동·시각·승인 실기 검사 | 실제 실행 결과와 미실행 구분 |
| F | 영구 기록과 정리 | 최종 토큰·캡처·차이·검증 보관 |

`SlateHarmonyColors.kt`를 복사해 두 번째 테마 공급자를 만드는 대신 기존 타입에 통합한다. 참고 코드의 패키지·visibility·Material3 API를 현재 프로젝트에 맞춘다. UI 작업 때문에 SDK/Kotlin/Compose/Gradle을 통째로 올리지 않는다.

앱의 선택 테마값을 사용한다. 시스템 테마를 독립적으로 두 번 읽지 않는다. 배경색을 덮는 Material3 elevation과 dynamic color를 검사하되 기존 사용자 설정을 무단 초기화하지 않는다. 참고 Section 컴포넌트는 짧은 그룹용이며 클릭 동작을 추가하지 않는다.

## 6. 최소 수용 기준

| ID | 검사 | 통과 조건 |
|---|---|---|
| C01 | 4개 루트 양 테마 | 동일 토큰 사용, 옛 온도별 섹션색 없음 |
| C02 | 섹션 구분 | 제목·여백·표면으로 준비/결과·장비/앱을 구분 |
| C03 | 행동 위계 | 주요 버튼·보조·비활성·선택이 혼동되지 않음 |
| C04 | 텍스트 | 실제 사용 쌍 4.5:1 목표, on색 누락 없음 |
| C05 | 비텍스트 | 필수 경계/포커스/상태 그래픽 3:1 목표 |
| C06 | 경고·실패 | 의미·조치·문구 그대로, 색에만 의존하지 않음 |
| C07 | 큰 글꼴·좁은 폭 | OS 최대 글꼴·약 360dp에서 중요 내용/버튼 접근 |
| C08 | 상세·모달·키보드 | 스크롤·하단바·포커스·확정 동작 가림 없음 |
| C09 | 복원·장비 전환 | 선택·초안·스크롤 유지, 잘못된 대상 명령 없음 |
| C10 | 통신 회귀 | API·전송·재연결·센서·전원 정책 무변경 |
| C11 | 증거 분리 | 로컬/Figma/네이티브/실기 검증을 각각 보고 |
| C12 | 팔레트의 한계 | 피로 감소·사용자 선호 검증 미실시를 숨기지 않음 |

현재 wrapper·CI에 맞게 실행한다.

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --console=plain
# 승인된 테스트 기기나 에뮬레이터를 명확히 선택한 뒤:
./gradlew :app:connectedDebugAndroidTest --console=plain
```

Windows는 동일 프로젝트의 `gradlew.bat`를 사용한다. 테스트 수·실패·skip·종료코드와 로그를 기록한다. 테스트 0개나 도구 체인 오류를 통과로 보고하지 않는다. 캡처 때문에 운영 장비를 재부팅하거나 네트워크를 끊거나 파일을 지우지 않는다.

실제 장비에서는 OS 글꼴 확대, 디스플레이 밝기, 화면 색 보정/야간 모드, 밝은/어두운 환경을 기록한다. 로컬 HTML의 선형 글꼴 2배는 Android 비선형 확대의 재현이 아니다. 논문에 나온 실험 조건을 지금 장치의 상태로 가정하지 않는다.

## 7. 보고와 인계

`docs/UI_UX_DESIGN_SYSTEM.md` 또는 기존 가이드에 최종 색상 역할과 변경 이유를 남긴다. `docs/UI_UX_COLOR_REFINEMENT_REPORT.md`에는 수정 브랜치/HEAD/파일, 색상 지점별 매핑, 대비 결과, 실제 앱 전후 캡처, 장치별 확인과 미완료를 기록한다.

판정은 **디자인 자료 갱신 / 실제 코드 반영 / 네이티브 자동 검사 / 실기기 / 실제 Figma 생성·export**로 분리한다. 이 패키지의 연구 검토와 로컬 검사는 앱 자체의 통과 증거가 아니다. 실제 Figma 사용이 별도 필수가 아니라면 MCP 연결을 전체 네이티브 작업의 선행 조건으로 만들지 않는다.

## 8. 시작 프롬프트

```text
{prompt.rstrip()}
```

## 9. 마지막 정리 — 전체 완료 후 이 지시 MD 한 개만 삭제

이번 범위의 실제 코드 반영, 필수 자동 검사, 승인된 필수 실기 확인, 영구 가이드·실제 캡처·보고 보관이 완료된 뒤에만 **처음 기록한 `JETSONCONTROLLER_SLATE_HARMONY_V7.md`의 정확한 한 경로를 마지막 단계에서 삭제한다.**

필수 구현·검증이 남거나 최신 작업 대상을 확인하지 못하면 이 MD를 유지하고 차단 사유와 다음 행동을 기록한다. 미실행을 완료로 바꾸거나 기존 검사를 꺼서 통과시키지 않는다.

스타일 가이드·연구 보고서·토큰·시안·참고 코드·테스트·원본 자료·다른 지시 문서·`AGENTS.md`는 삭제하지 않는다. 와일드카드·디렉터리·MD 일괄 삭제는 금지한다.
'''
(P/'JETSONCONTROLLER_SLATE_HARMONY_V7.md').write_text(agent,encoding='utf-8')
# Kotlin reference: all role values generated directly from canonical JSON.
fields='\n'.join('    val '+k+': Color,' for k in l).rstrip(',')
instances='\n\n'.join('private val '+name+' = SlateHarmonyTokens(\n'+'\n'.join(f'    {k} = Color(0xFF{v[1:]}),' for k,v in vals.items()).rstrip(',')+'\n)' for name,vals in [('LightHarmonyTokens',l),('DarkHarmonyTokens',d)])
kt='''package com.example.jetsoncontroller.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Integration reference only: NOT compiled against the target repository.
 * Adapt to the existing theme type and Material3 version, do not create parallel state.
 */
@Immutable
internal data class SlateHarmonyTokens(
'''+fields+'\n)\n\n'+instances+'''

internal fun slateHarmonyTokens(darkTheme: Boolean): SlateHarmonyTokens =
    if (darkTheme) DarkHarmonyTokens else LightHarmonyTokens

internal fun slateHarmonyColorScheme(darkTheme: Boolean): ColorScheme {
    val p = slateHarmonyTokens(darkTheme)
    val opposite = slateHarmonyTokens(!darkTheme)
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.accent,
        onPrimaryContainer = p.onAccent,
        inversePrimary = opposite.primary,
        secondary = p.primary,
        onSecondary = p.onPrimary,
        secondaryContainer = p.sectionSoft,
        onSecondaryContainer = p.ink,
        tertiary = p.primary,
        onTertiary = p.onPrimary,
        tertiaryContainer = p.sectionRaised,
        onTertiaryContainer = p.ink,
        background = p.canvas,
        onBackground = p.ink,
        surface = p.surface,
        onSurface = p.ink,
        surfaceVariant = p.sectionSoft,
        onSurfaceVariant = p.muted,
        surfaceTint = p.primary,
        inverseSurface = opposite.surface,
        inverseOnSurface = opposite.ink,
        error = p.danger,
        onError = p.onDanger,
        errorContainer = p.dangerBg,
        onErrorContainer = p.danger,
        outline = p.controlBorder,
        outlineVariant = p.border,
        scrim = Color(0xFF101419),
        surfaceBright = if (darkTheme) p.sectionRaised else p.surface,
        surfaceDim = if (darkTheme) p.canvas else p.sectionRaised,
        surfaceContainerLowest = if (darkTheme) p.canvas else p.surface,
        surfaceContainerLow = if (darkTheme) p.surface else p.canvas,
        surfaceContainer = p.sectionSoft,
        surfaceContainerHigh = p.sectionRaised,
        surfaceContainerHighest = p.sectionRaised
    )
}
'''
(P/'compose-reference/SlateHarmonyColors.kt').write_text(kt,encoding='utf-8')
print('Handoff / guide / prompt / Kotlin tokens generated')
if __name__=='__main__':pass
