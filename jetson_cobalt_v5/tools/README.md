# 선택적 로컬 재생성 도구

이미 생성된 HTML/PNG/플러그인을 보기·전달하는 데 이 도구 실행은 필요하지 않습니다.

`python tools/rebuild.py`는 토큰→scene→HTML→17개 PNG→측정 레이아웃→Figma용 코드→로컬 검사를 순서대로 실행합니다. 저장소·Figma·장비에 연결하지 않으며 실제 앱 코드를 생성하거나 설치하지 않습니다.

재생성에는 Python, Pillow, Playwright, Chromium과 한글 폰트가 필요합니다. Figma 코드 구문/mock 검사에는 Node.js를 사용합니다. 이 패키지는 도구 설치나 계정 연결을 자동으로 수행하지 않습니다.

`design/tokens.json`이 색상 기준입니다. `tools/build_design.py`는 이 파일을 읽으며 리빌드 시 덮어쓰지 않습니다. 화면 구조를 바꾸려면 `tools/build_design.py`, 렌더링 동작은 `tools/prototype_template.html`, Figma 생성 동작은 `figma-plugin/code.template.js`를 수정합니다. 미리보기 패널과 소개 보드의 장식색도 바꿀 때는 해당 template 및 `make_boards.py`를 함께 대조하세요.

브라우저 경로는 `BROWSER_EXECUTABLE` 환경변수가 있으면 사용하고, 없으면 시스템 chromium 경로나 Playwright의 설치본을 사용합니다. 보드 폰트 경로는 `CJK_REGULAR_FONT`, `CJK_BOLD_FONT`로 지정할 수 있습니다. 기본 경로는 제작 환경의 Linux Noto CJK 파일 경로입니다. **폰트 바이너리는 포함되어 있지 않습니다.**

`audit_set.py`는 현재 JSON/문서/프로토타입/플러그인의 색상 일치, 이미지 목록, 초록색 계열 제외 규칙, 이전 기준 이미지와의 대조, 프롬프트 일치를 검사합니다. 새 디자인을 의도적으로 바꾸면 원본 이미지 대조 결과가 달라질 수 있으며, 이를 고의 변경 이력으로 남겨야 합니다.

검사 스크립트는 네이티브 렌더링·TalkBack·실기기·실제 Figma를 검사하지 않습니다. mock export 데이터는 실제 PNG가 아닙니다. 수정 후 새 검증 결과로 `evidence/VALIDATION.md`와 패키지 manifest를 갱신하세요.
