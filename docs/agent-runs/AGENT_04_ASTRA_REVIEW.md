# AG04 Astra 검수

- 검수일: 2026-09-14
- 검수자: Astra / Worker: GPT-5.6 Sol High
- 대상: `work/jetson-pipeline-runtime-20260914`, `bf48810`
- Verdict: **PASS** — 지적 사항 수정 후 로컬 구현 범위

PipelineManager, 등록 API/CLI, runner, systemd unit과 관련 테스트를 검수했다. 변경은 runtime 소유 범위에 한정되며 RTK와 데이터 보관 lifecycle은 수정하지 않았다. 기존 API 필드를 보존하면서 실제 systemd 상태와 optional 실행 메타데이터를 추가한다. 전화 단절에 따른 stop/restart 경로를 추가하지 않았고 등록 기본값은 부팅 자동 시작 해제다.

초기 검수에서 발견한 다음 문제를 Sol이 수정했고 최종 diff와 테스트로 확인했다.

- `linked`/`enabled-runtime`을 영속 부팅 enable로 오인하지 않는다.
- 현재 systemd InvocationID와 다른 과거 로그를 활성 실행 ID로 확정하지 않는다. 잘못된 로그 날짜도 정상 조회를 깨뜨리지 않는다.
- 외부 프로그램의 exit 78만으로 저장 사전점검 실패라고 표시하지 않는다. runner 근거를 사용하고 프로그램 자체의 exit 78은 일반 장애 복구를 유지한다.
- 신뢰할 runner header와 child stdout 사이에 빈 줄 경계를 둔다.
- 임의의 1 GiB 저장 차단 기준을 제거했다. 명시된 저장 경로는 실제 쓰기·fsync·삭제 probe로 검사하며, 경로가 없으면 `not_configured`로 구분한다.
- 로그 본문이 잘려도 종료 footer를 보존한다.

관련 runtime/API 테스트 86개 통과와 `git diff --check`를 확인했다. 최초 전체 backend 실행은 255건 중 251 통과, 2 skip, DBus import 오류 2건이었다. 검수자가 동일 Python 버전의 기존 시스템 DBus/GObject 모듈을 환경 변경 없이 불러올 수 있음을 확인했으며 전체 통합 시험에서 재검증한다.

Android optional 모델/화면 연결은 AG03/AG02 통합 대상이다. 프로젝트·조사 구간 및 실행별 예상 출력 파일 수를 새로 구현한 변경은 아니다. 실제 Jetson systemd·저장 장치·센서·전화 단절 시험과 배포는 수행하지 않았으며 이 PASS는 운영 승인에 해당하지 않는다.
