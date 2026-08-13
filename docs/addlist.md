1. ~~GitHub `main`과 비교해 먼저 병합 가능한 서버 변경을 검증하고 병합한다.~~
   - PR #4의 upload receiver 변경을 backend 56개, receiver 24개 시험 후 `main`에 선병합했다.
2. ~~Jetson 응답 인증 실패의 앱·서버 원인을 구분하고 복구 흐름을 개선한다.~~
   - 세션/응답 서명 재시도 후에도 실패하면 backend 버전 불일치와 QR 재등록을 구분해 안내한다.
   - 현재 장비의 실행 backend가 `1.0.0`, 저장소 backend가 `1.2.0`인 배포 drift를 확인했다.
3. ~~UI/UX 실행 명세의 P0 흐름을 중심으로 앱을 대대적으로 개선한다.~~
   - 장비 중심 홈, 3단계 onboarding, QR 수동 입력/torch/haptic, pairing stepper를 구현했다.
   - 삭제 확인, health-first dashboard, active work, freshness/stale, 공통 banner/status token을 구현했다.
   - Android unit test 33개와 핵심 화면 Compose regression test 5개를 구성했다.
4. 업로드 receiver를 실제 Jetson target으로 연결한다.
   - 공인 HTTPS receiver `https://125-142-22-24.sslip.io`의 readiness를 확인하고 앱 target 편집기에 운영값을 기본 제공한다.
   - receiver PC의 장비별 token 전달과 Jetson의 root 전용 target 저장은 운영 자격 증명이 필요한 마지막 단계다.
