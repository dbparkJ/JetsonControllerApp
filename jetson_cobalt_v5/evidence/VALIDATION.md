# Cobalt Field V5 — 제작·검증 기록

작성: 2026-09-08. 검사 대상은 이 패키지의 로컬 시안과 생성 코드입니다. Android·Jetson·실제 Figma 결과와 구분합니다.

## 이번에 실행한 검사

| 검사 | 이번 결과 | 경계 |
|---|---|---|
| 로컬 PNG 렌더링 | 개별 화면 17개 + 소개/전체 상태 보드 2개, 렌더링 오류 0개 | 로컬 Chromium HTML 렌더링 |
| 폭/글꼴 | 51개 조합, 텍스트 화면 가로 경계 넘침 0개 | 412/1.0, 360/1.0, 360/선형2.0; Android 비선형 글꼴 실측 아님 |
| 대표 동작 | 10개 통과 | 탭 이동, 선택·확인창, 취소, 연결 화면; 전체 제품 기능 검증 아님 |
| 대표 본문 대비 | Light/Dark 24쌍, 모두 4.5:1 이상 | 앱 전체 접근성 인증/모든 비텍스트 대비 검사 아님 |
| Figma 코드 | JS 구문, mock 생성, 중복 생성 방지, export 메시지 경로 통과 | 실제 API·렌더러·실제 PNG export는 미실행 |
| Figma mock 출력 | 화면 17개, Button/Badge variant 18개, 변수 151개, 텍스트 스타일 38개 생성 경로 | 실제 Figma 파일에서 세거나 출판한 객체 수가 아님 |
| 팔레트 동기화 | tokens→scene→HTML→Figma 데이터/코드→MD 표 일치 | Light/Dark 각각 23개 의미 역할 |
| 초록색 제거 | 활성 색상 역할에 초록 계열 없음, 구형 주요 색상 코드 잔존 없음 | 원본 참고 캡처는 증거 보존을 위해 색 변경하지 않음 |
| 직전 홈 시안 대조 | `01-home-light.png`의 픽셀 값 동일 | 직전 마지막 Cobalt 홈과의 비교; Android 화면과의 비교 아님 |
| 직전 보드 대조 | 동일 크기, RGB 평균 절대 차 약 0.00070828/255 | 상태색·보드 스와치 등 극소 차이. 전체 보드를 완전히 동일한 이미지라고 주장하지 않음 |
| 원본 참고 캡처 | 8개 SHA-256 동일 | 원본 내용 유지 |
| 한글 문서/자료 | UTF-8, 프롬프트 파일과 MD 내 프롬프트 동일 | Markdown과 패키지 구성 정합성 |

## 미실행

실제 Figma 파일 생성·변수 바인딩 렌더 검수·프로토타입 동작·PNG export, 네이티브 Android 빌드·TalkBack·실기기 연결/업로드/센서, 운영 장비 제어는 하지 않았습니다. 이번 묶음 제작은 원격 저장소나 초기 Figma 초안을 수정하지 않았습니다.

## 검사 원본

`prototype-validation.json`, `plugin-mock-test.json`, `render-layouts.json`, `set-consistency.json`, `source-provenance.json`, `figma-status.json`을 확인합니다. `reference/last_shown_cobalt`와 이번 `screens`를 직접 대조할 수 있습니다. 테스트 재실행 없이 결과 파일만 수정해 통과로 표시하지 않습니다.
