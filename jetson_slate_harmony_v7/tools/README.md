# 로컬 재생성 도구

완성된 PNG와 단독 HTML은 재생성 없이 사용할 수 있다. 아래는 유지보수용이며 앱 저장소나 장비를 수정하지 않는다.

## 환경

Python 3.10 이상, `Pillow`, `playwright`, Chromium 계열 브라우저, Node.js가 필요하다. 한국어 렌더링을 위해 시스템에 Noto Sans CJK KR 또는 동일하게 설정한 한국어 글꼴이 필요하다. 폰트 바이너리는 배포하지 않는다. 환경 설치는 사용자 정책과 네트워크 접근 범위 안에서 수행한다.

```bash
python -m pip install pillow playwright
python -m playwright install chromium
# 별도 시스템 Chromium을 사용하면 BROWSER_EXECUTABLE 환경변수로 지정 가능
python tools/rebuild.py
```

플랫폼별 폰트 위치가 다르면 `CJK_REGULAR_FONT`, `CJK_BOLD_FONT`로 보드용 폰트 경로를 지정한다. 브라우저의 실제 폰트 폴백은 별도 확인한다. 프로토타입의 논리 크기와 화면 캡처는 412×892, 2x(824×1784)이며 실제 Android dp/sp를 의미하지 않는다.

`make_palette.py` → `build_design.py` → HTML 조립 → `render_screens.py`(PNG + 측정값) → Figma 입력 임베딩 → 문서/Kotlin 갱신 → 브라우저 검사 → 보드 생성 → 플러그인 모의 검사 → 무결성 검사 순서다.

정확한 색 변경은 `make_palette.py`에서 저채도 원칙과 역할 관계를 유지하면서 작성한다. `tokens.json`만 수동 변경한 뒤 `rebuild.py`를 실행하면 제작 원본으로 다시 생성되므로 두 파일의 책임을 혼동하지 않는다. 구현 에이전트는 최종 제공된 `tokens.json`을 입력 기준으로 사용하면 된다.

연구 보고서는 편집 문서이고 자동 연구 생성 기능이 아니다. 이를 수정하면 최상위 `COLOR_RESEARCH.md`와 `research/COLOR_RESEARCH.md`를 함께 동기화한다. 생성 도구는 논문을 추가로 다운로드하거나 Figma에 접속하지 않는다.

검사 범위는 로컬 HTML·색상 수치·모의 API다. `tools/test_plugin_mock.js`의 성공은 Figma 렌더러·권한·export의 성공이 아니다. `export_snapshots.py`는 통합 렌더 도구로 연결되는 호환 진입점이므로 두 번 실행할 필요가 없다.
