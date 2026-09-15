# AG03 Astra 검수

- 검수일: 2026-09-14
- 검수자: Astra / Worker: GPT-5.6 Sol High
- 대상: `work/android-connectivity-20260914`, adapter `e1d9a8e`, 구현 `3945b5b`
- Verdict: **PASS** — 수정 확인 후 로컬 연결·복구 범위

등록 장비 선택·transport 수명주기, mutation 유실/지연 처리, RTK 중계 소유권과 AG04 Android 모델 계약을 검수했다. 기존 명시 장비 선택과 LAN/Direct 정책을 유지하며 start/stop 응답 유실은 재전송 없이 상태를 조회한다. 조회 결과가 있어도 원래 mutation의 RESULT_UNKNOWN을 성공으로 바꾸지 않는다.

초기 구현에서 결과 미확인 start 후 관찰이 불충분해도 RTK가 정리될 수 있었고, 장치 전환의 비동기 cleanup이 새 client 중계를 정리할 가능성이 있었다. Worker가 동일 pipeline의 확인된 terminal 상태 또는 확정 실패에만 cleanup하도록 수정했다. client identity로 prepare 재사용·cleanup·heartbeat를 제한하고 prepare 실패 정리와 foreground service 수명을 같은 mutex로 보호했다. NTRIP 미설정 반환 시 이전 service도 정리한다.

선택 장비 전환, 유실/지연/중복 start-stop, 실제 TLS test server와 GET 재조회, 중계 소유권 및 legacy/new JSON을 다루는 JVM XML 70개 테스트의 failure/error/skip 0을 확인했다. `git diff --check`도 통과했다. runtime의 observedAt/run identity/execution/control은 optional이며 새 등록의 reboot autostart 기본값은 false다.

실제 Android 잠금·백그라운드·무선 전환·BLE 및 셀룰러 NTRIP 전달은 검증하지 않았다. 이전 장비의 unregister 요청 실패 시 기존 backend lease 만료에 의존할 수 있다. 최종 통합 build/lint/full JVM과 실장치 연결 시험을 구분하며, 이 PASS는 장치 배포·현장 승인에 해당하지 않는다.
