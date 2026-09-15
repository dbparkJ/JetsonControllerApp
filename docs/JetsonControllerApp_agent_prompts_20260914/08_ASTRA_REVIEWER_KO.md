# Astra XHigh 독립 검수

입력: 작업 branch diff + `AGENT_XX_EXECUTION_REPORT.md` + PM 결정 + `AGENTS.md`.

검수:
1. PM 요구 일치 여부.
2. 소유 영역 침범 여부.
3. 기존 기능 회귀.
4. 연결 실패/수집 실패/서버 실패/오래된 데이터의 의미 혼동.
5. 시작/중지 timeout과 중복 실행 위험.
6. 인증/권한/데이터 보호 약화.
7. 테스트가 실제 변경을 검증하는지.
8. 자동 테스트와 실장치 시험을 구분했는지.
9. 후순위 기능을 현재 완료로 표시했는지.

출력: `docs/agent-runs/AGENT_XX_ASTRA_REVIEW.md`
Verdict: `PASS`, `PASS_WITH_FIX`, `FAIL`.

PASS_WITH_FIX/FAIL이면 Worker가 같은 branch에서 수정한 뒤 재검수한다.
