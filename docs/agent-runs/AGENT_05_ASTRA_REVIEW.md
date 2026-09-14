# AG05 Astra 검수

- 검수일: 2026-09-14
- 검수자: Astra / Worker: GPT-5.6 Sol High
- 대상: `work/05-storage-20260914`, 구현 `00bfa15`, 문서 보정 `9409328`
- Verdict: **PASS** — 수정 확인 후 직접 조회·보관 구현 범위

receiver schema/service/API, 관리 CLI, 설치 환경 설정과 Android 독립 data/cache/credential 코드를 검수했다. 새 직원 인증은 장치 인증을 재사용하지 않는다. 역할과 프로젝트 grant를 확인하고 세션별 장치 소유권 검사를 유지한다. 장비의 access project 재배정을 막아 과거 파일의 권한 경계가 바뀌지 않게 한다. 이 access project가 조사 구간/실행의 업무 context를 구현한 것은 아니다.

다음 초기 지적을 Worker가 수정한 것을 확인했다.

- rename 이후 fsync 또는 DB commit이 실패해도 영속 transition을 보존하고 실제 저장 위치로 복구한다.
- 취소를 cache fallback으로 변환하지 않고 coroutine cancellation을 전파한다.
- 인증/권한 거절, TLS 오류, 환경·사용자·프로젝트 불일치에서는 이전 cache로 우회하지 않는다.
- HTTPS만 허용하고 redirect 및 자동 connection retry를 끈다. mutation 응답 유실은 UNKNOWN으로 돌려준다.
- 관리 명령 예제에 올바른 PYTHONPATH와 배포된 receiver 환경을 사용한다.

receiver 테스트 37개 통과와 Android XML 결과의 `DirectServerRepositoryTest` 4개 통과를 확인했다. 관련 테스트는 인증 namespace/환경/프로젝트 격리, viewer 삭제 거절, 객체 검증 receipt, 양방향 rename 후 fsync 장애 복구, cache scope·credential revision, 권한 거절과 취소를 다룬다. 최종 `git diff --check`도 통과했다.

직원 token은 Android Keystore AES-GCM으로 저장하며 서버에는 pepper HMAC만 저장한다. receiver가 실제 객체를 검증한 결과만 `matched=true`로 돌려준다. 캐시는 마지막 갱신 시각을 보존하며 현재 서버 상태로 표시하지 않는다. 기존 device upload/library 보안과 호환 경로를 유지한다.

AG02가 독립 진입·로그인·캐시·미리보기·휴지통 화면을 연결해야 사용자 흐름이 완성된다. 실제 Android Keystore, 공개 HTTPS, 저장 장치 crash recovery, 운영 직원 계정은 실장치/배포 검증 대상이다. 휴지통 목록은 현재 최대 200개이며 영구 purge/보존 기간 및 조직 IdP 연계는 구현 범위 밖이다. 기존 device-token 영구 삭제 API의 정책도 별도 운영 검토가 필요하다. 이 PASS는 현장 운영 또는 배포 승인이 아니다.
