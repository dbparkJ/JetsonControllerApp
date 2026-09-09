# Cobalt Field — Figma 로컬 개발 플러그인

이 플러그인은 제공된 시안을 **편집 가능한 Figma 텍스트·벡터·Auto Layout·컴포넌트**로 생성하기 위한 코드입니다. 네트워크 요청/API 키/장비 제어 기능이 없습니다. 실제 Figma에서는 아직 실행하지 않았으며, 로컬 구문·mock 검사만 마쳤습니다.

## 설치

Figma Desktop의 개발 플러그인 생성 기능으로 **UI가 있는 새 플러그인**을 만드세요. 이때 Figma가 생성한 `manifest.json`의 ID를 사용합니다. 임의의 ID는 패키지에 넣지 않았습니다. 공식 시작 안내: <https://developers.figma.com/docs/plugins/plugin-quickstart-guide/>

이 폴더에서 다음을 실행하면 Figma가 생성한 ID를 유지하면서 코드를 설치합니다.

```bash
python install.py "Figma가 생성한 manifest.json의 실제 전체 경로"
```

`install.py`는 기존 `manifest.json`, `code.js`, `ui.html`을 시각이 붙은 backup 폴더로 복사하고, 생성된 ID를 보존한 manifest와 이 패키지의 코드를 설치합니다. 계정·서버에 접속하지 않습니다.

Python을 사용하지 않을 때는 `code.js`, `ui.html`을 새 플러그인 폴더로 복사하고 `manifest.template.json`의 내용을 생성된 manifest에 반영하되, **Figma가 발급한 기존 `id` 항목을 유지**하세요. template 파일을 완성 manifest라고 간주하거나 임의의 ID를 입력하지 마세요.

Figma Design의 빈 페이지에서 해당 개발 플러그인을 실행하고 **새 디자인 생성**을 누릅니다. 글꼴이 없으면 오류 메시지에 따라 Noto Sans KR 사용 가능 여부를 확인합니다. 폰트 파일은 패키지에 포함하지 않습니다.

## 생성되는 구성

| 항목 | 내용 |
|---|---|
| `CFV5 / Screens` | 17개 화면·상태, 설명 보드, 대표 이동 연결 |
| `CFV5 / Components` | SVG 아이콘 마스터, Button/Badge variants |
| 변수 | Primitive alias, Light/Dark 단일 모드 컬렉션, 간격·모서리 |
| 텍스트 | 편집 가능한 한국어 텍스트 및 스타일 |
| 파일 안전 | 기존 레이어 유지; 같은 이름 보드가 있으면 중복 생성 차단 |

실제 Figma 런타임은 mock과 다릅니다. 글자 잘림·폰트 폴백·자동 높이·모달·스크롤·prototype 연결을 실행 후 검수하세요. 계정 기능/플랫폼 버전에 따른 오류는 메시지에 표시됩니다. 일부 이동 연결의 실패가 생기면 화면 생성과 구분해 경고를 남깁니다.

## Figma에서 PNG 얻기

생성 후 플러그인 창에서 화면을 선택하고 **선택 화면 PNG 생성 · 2배**를 누르면 실제 Figma `exportAsync`를 호출합니다. 성공하면 창 아래에 저장 링크가 나타납니다. 이때 저장한 파일만 Figma export 결과입니다.

플러그인을 닫았거나 다시 열어 화면 목록이 초기화됐다면 캔버스에서 원하는 `Screen/...` 프레임을 선택하고 Figma 기본 Export 기능에서 PNG/2x로 내보낼 수 있습니다. 전체 컴포넌트 보드가 아니라 개별 앱 프레임을 선택하세요.

이 패키지의 `screens/*.png`는 **로컬 HTML 렌더링**이며 이 단계에서 얻은 Figma PNG가 아닙니다. 실제 export를 하지 않았으면 완료로 보고하지 마세요.

## 오류·안전

코드는 현재 페이지의 오른쪽 빈 영역에 보드를 추가하며 기존 레이어를 삭제하지 않습니다. 부분 실패 시 생성 중이던 보드를 `PARTIAL`로 표시합니다. 이 경우 오류 단계를 확인하고 Figma 실행 취소로 이번 생성분을 되돌린 뒤 재시도하세요. 다른 사용자 레이어를 일괄 삭제하지 마세요.

목표는 실제 편집 가능한 디자인 인계입니다. 시안 PNG를 한 장 붙인 프레임으로 대체하거나 아직 생성하지 않은 파일을 완성 `.fig`라고 명명하지 않습니다.

## 코드 구성

- `manifest.template.json`: ID를 제외한 manifest 설정. Figma가 발급한 ID와 결합해서 사용합니다.
- `install.py`: 생성된 개발 플러그인 ID를 보존하는 설치 도구.
- `code.js`: 데이터가 포함된 실행 코드. npm 설치/번들 과정이 필요하지 않습니다.
- `ui.html`: 생성·진행·오류·PNG export UI.
- `code.template.js`: 유지보수용 코드 원본. 실제 실행에는 `code.js`를 사용합니다.

공식 API 근거: [Manifest](https://developers.figma.com/docs/plugins/manifest/), [Plugin API](https://developers.figma.com/docs/plugins/api/figma/), [PNG export](https://developers.figma.com/docs/plugins/api/properties/nodes-exportasync/).
