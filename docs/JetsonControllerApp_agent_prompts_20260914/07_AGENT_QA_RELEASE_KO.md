# AG07 QA·CI·릴리스

Branch: `work/07-qa-release-20260914`
Model: GPT-5.6 Sol High/XHigh

소유: `.github/workflows/**`, `scripts/**`, `docs/qa/**`, 독립 test harness. 기능 코드는 수정하지 않는다.

Android 휴대전화+태블릿+Orin NX 지원 matrix, 핵심 업무 acceptance matrix, 자동/실장치 검증 구분, 시험표+로그+대표 화면 증거, demo freeze checklist와 내부 운영 release checklist, rollback/진단 절차를 만든다. CI에서 Android/backend/upload_receiver 검증 공백을 조사해 보완한다. 데이터 손실/잘못된 장치 제어/인증 문제는 운영 release blocker다.

작업 후 `AGENT_07_EXECUTION_REPORT.md`.
