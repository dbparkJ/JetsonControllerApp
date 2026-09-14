# AG02 Astra 검수

- 검수일: 2026-09-14
- 검수자: Astra / Worker: GPT-5.6 Sol High
- 대상: `work/ux-ui-system-20260914`, base `ecd1e08`, 최종 `0b8662c`
- Verdict: **PASS** — 지적 사항 수정 후 화면 구현 범위

홈·연결·설정·저장·작업·이력·지도 diff, 독립 서버 ViewModel, AG03/04 실행 모델과 AG05/06 연결을 검수했다. 홈은 연결/수집/인터넷/GNSS·RTK를 분리하고 fresh 조회와 activeRunId가 있어야 현재 실행을 확정한다. 반응형 상태 카드와 일반/관리자 도구 구분을 적용하며 로컬 화면 구분을 권한 부여로 해석하지 않는다.

Worker가 다음 검수 지적을 수정했다.

- offline 또는 실행 ID가 없는 RUNNING을 성공 상태로 표시하지 않는다. 이력의 실행 보고도 현재 동작과 구분한다.
- 직접 서버 진입에 Jetson 연결을 요구하지 않고 background 장치 등록 실패가 독립 서버 화면을 닫지 않는다.
- 요청 generation과 profile/run identity로 이전 응답을 차단한다. 프로필/credential 변경 시 이전 데이터·Undo·확인 대상을 초기화한다.
- 인증/권한/TLS/환경·직원·프로젝트 오류에 cache를 보여주지 않는다. availability 오류만 범위가 일치하는 마지막 성공 cache로 복구한다.
- 토큰을 saved instance state에 넣지 않고 password IME를 사용한다. VIEWER/미확인 역할의 제거·복원을 제한한다.
- Undo 메시지를 갱신 메시지와 분리하고 복원 후 데이터·휴지통을 갱신한다. UNKNOWN mutation은 성공으로 표시하거나 자동 재전송하지 않는다.
- 본문 읽기·프로필 저장소 오류를 처리하고 취소는 전파한다. 영상 임시 파일은 IO에서 생성하며 취소/화면 종료 때 소유 파일과 재생을 정리하고 준비 실패는 재시도 UI로 표시한다.
- 품질의 실제 RTK 분모·FIX 유지·미관찰 시간, nullable 위치와 전체 문제 시각을 표시한다. route/run 변경 뒤 이전 품질 응답이 섞이지 않는다.

앞선 targeted build/lint/assemble 성공, 최종 관련 JVM XML 28개 failure/error/skip 0 및 slate 66 token/94 pair 검사 성공을 확인했다. 마지막 작은 I/O·표현 보정은 source 검수를 마쳤으며 최종 merge-stage 전체 build/lint/JVM 결과는 통합 검수서에 기록한다. Kotlin helper와 repository 시험은 Android 렌더링·실제 lifecycle 검증을 대체하지 않는다.

장비 파일/폴더와 실행 이력 삭제는 여전히 영구 삭제이며 복구 가능하다고 표시하지 않는다. 일반 제거 전체의 soft-delete 통합은 backend 후속 요청이다. 프로젝트·조사 구간, 통합 preflight/run/output chain도 미완료다. phone/tablet screenshot·회전·실제 video/Keystore·공개 서버·센서는 미실행이므로 전체 제품 또는 현장 승인으로 이 PASS를 확대하지 않는다.
