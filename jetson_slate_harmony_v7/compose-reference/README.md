# Compose 통합 참고 코드

`SlateHarmonyColors.kt`는 `design/tokens.json`에서 생성한 역할값과 ColorScheme 매핑이다. `SlateHarmonySection.kt`는 짧은 섹션의 표면·제목·여백만 정리하는 비상호작용 래퍼다.

**현재 앱에 적용·컴파일한 패치가 아니다.** 실제 패키지명·Material3 버전·타입·visibility를 먼저 맞춘다. 기존 컬러 타입/Section 컴포넌트가 있으면 그곳을 갱신하고 중복 테마 엔진을 만들지 않는다. 새 역할을 기존 토큰에 매핑한 뒤 Kotlin 파일을 생성하는 것이 더 적절할 수 있다.

`slateHarmonyColorScheme(darkTheme)`에는 앱이 실제 선택한 테마값을 넣는다. `isSystemInDarkTheme()`를 새 공급자에서 다시 읽어 앱 설정과 어긋나게 하지 않는다. `surfaceContainer*` 등 참조 API가 현재 버전에 없다면 호환되는 역할 매핑을 검토하고, 색상 수정만을 위해 의존성을 일괄 업그레이드하지 않는다.

주요 버튼은 primary/onPrimary, 배지와 선택에는 각각 해당 전경/배경 쌍을 사용한다. 입력 경계는 controlBorder, 장식 경계는 border, 포커스는 focusRing으로 분리한다. 경고는 ColorScheme tertiary가 아니라 별도의 warning/warningBg로 명시한다.

섹션 예제는 명령·네트워크·상태 저장을 수행하지 않는다. 긴 파일 목록을 이 Column에 통째로 넣지 말고 기존 LazyColumn에서 항목별 가상화를 유지한다. 기존 callback·semantics·testTag·클릭 영역과 데이터 freshness를 보존한다.

확정된 디자인을 적용한 실제 앱에서 빌드·테스트·실기 검증을 진행해야 한다. 이 예제 파일의 존재를 앱 반영 완료로 보고하지 않는다.
