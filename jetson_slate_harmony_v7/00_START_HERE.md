# Slate Harmony V7 — 시작 안내

**이 세트를 V5/V6 대신 사용한다.** 기존 기능을 다시 만드는 패키지가 아니라, 라이트·다크 색상 조화와 섹션 표면을 다시 정리한 색상 적용 패키지다.

1. `screens/00-light-dark.png`와 `00-before-after.png`에서 새 방향과 V6 대비 차이를 본다.
2. `COLOR_RESEARCH.md`에서 학술 연구 7편의 결과·확인 범위·한계를 확인한다.
3. `SLATE_HARMONY_STYLE_GUIDE.md`와 `design/tokens.json`을 정확한 배색 기준으로 읽는다.
4. AI-agent에는 이 폴더와 `START_PROMPT.txt`를 전달하고, `JETSONCONTROLLER_SLATE_HARMONY_V7.md`로 현재 코드에 최소 변경을 적용한다.

## 들어 있는 것

| 경로 | 내용 |
|---|---|
| `JETSONCONTROLLER_SLATE_HARMONY_V7.md` | 이번 적용 지시서. 전체 완료 뒤 이 파일만 삭제 |
| `SLATE_HARMONY_STYLE_GUIDE.md` | 영구 스타일 가이드 |
| `COLOR_RESEARCH.md` | 논문·공식 자료·설계 추론·미검증을 구분한 리서치 |
| `research/` | 리서치 사본과 자료 목록 |
| `design/` | JSON 토큰·OKLCH CSV·화면 구조·Figma 입력 데이터 |
| `screens/` | 32개 대표 상태 PNG + 소개/비교 보드 6개 |
| `prototype.html` | 외부 요청 없이 여는 로컬 디자인 갤러리 |
| `compose-reference/` | 현재 Compose에 통합할 참고 소스 |
| `figma-plugin/` | 동일 디자인을 편집용 레이어로 만드는 로컬 플러그인 코드 |
| `evidence/` | 실행한 로컬 검사와 미실행 범위 |
| `reference/` | 원본 8장과 V6 비교 이미지. 활성 팔레트 아님 |
| `tools/` | 로컬 시안·검사·패키지 재생성 도구 |

## 중요한 범위

네이티브 앱·GitHub 원격 코드를 이번 패키지 제작 과정에서 수정하지 않았다. 제공 PNG는 로컬 HTML 렌더링이며 실제 앱/Figma export가 아니다. Figma 코드는 구문·모의 검사만 했고 실제 실행은 하지 않았다. 연구는 정확한 HEX를 검증한 사용자 연구가 아니다.

`prototype.html`은 브라우저에서 열 수 있다. 작은 창에서는 보조 패널이 숨겨지므로 큰 창에서 화면·테마·글꼴 선택을 확인한다. 클릭 동작은 디자인 데모의 제한된 흐름이며 실제 장비·카메라·서버·계정 기능이 아니다.

추가 플러그인 연결·원본 Claude 링크·Figma 구독 변경을 전체 작업의 선행 조건으로 삼지 않는다. 편집용 Figma 파일이 필요할 때만 `figma-plugin/README.md`의 로컬 실행 절차를 따른다. 원격 push/병합/배포나 장비 전원·네트워크 조작은 별도 승인 범위다.
