# AG04 Jetson 수집 Runtime

Branch: `work/jetson-pipeline-runtime-20260914`
Model: GPT-5.6 Sol XHigh

소유: pipeline 관련 backend, runner, tests. RTK 전용 코드는 AG06, 데이터 보관 lifecycle은 AG05와 분리한다.

외부 수집 프로그램의 등록/시작/중지/복구, 중복 명령 경계, 실행 메타데이터, 저장 사전점검을 안정화한다. 휴대전화가 끊겨도 이미 시작한 수집은 계속한다. 재연결 시 실제 실행 상태가 기준이다. 재부팅 자동 재시작은 설정 가능성을 두되 기본은 사람 확인 후 재시작 방향이다.

작업 후 `AGENT_04_EXECUTION_REPORT.md`.
