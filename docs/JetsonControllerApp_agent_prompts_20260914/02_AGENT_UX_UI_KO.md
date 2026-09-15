# AG02 UX/UI 전체 재설계

Branch: `work/ux-ui-system-20260914`
Model: GPT-5.6 Sol XHigh

소유: `app/.../ui/**`, `docs/design/**`, UI 리소스/테스트.

현재 화면 체계를 전면 재설계한다. 홈은 연결/준비/수집 상태와 다음 행동을 우선한다. 일반 사용자와 관리자/개발자 영역을 분리한다. 연결/수집/인터넷/GNSS·RTK 상태를 구분한다. Android 휴대전화와 태블릿을 반응형으로 지원한다. 오류는 다음 행동을 설명한다. 항목 제거는 이중 스와이프 대신 복구 가능한 soft-delete+Undo+휴지통 UX를 사용한다. backend가 지원하지 않으면 성공을 가장하지 말고 통합 요청으로 남긴다.

작업 후 `AGENT_02_EXECUTION_REPORT.md`.
