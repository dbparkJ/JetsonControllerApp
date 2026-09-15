# JetsonControllerApp 멀티 에이전트 실행 세트

기준: `dbparkJ/JetsonControllerApp` / `main@e41b752a72be4e5367c1108ab32a2725c8fb4dec`

Worker는 GPT-5.6 Sol High/XHigh, 독립 검수는 Astra XHigh, 이후 PM Agent가 7개 결과를 통합 평가한다. 최종 승인자는 인간 PM이다.

브랜치:
- AG01 `work/operator-workflow-20260914`
- AG02 `work/ux-ui-system-20260914`
- AG03 `work/android-connectivity-20260914`
- AG04 `work/jetson-pipeline-runtime-20260914`
- AG05 `work/05-storage-20260914`
- AG06 `work/06-field-quality-20260914`
- AG07 `work/07-qa-release-20260914`
- 통합 `work/merge-stage-20260914`

모든 Worker는 루트 `AGENTS.md`를 읽고, 담당 영역 외 변경은 `INTEGRATION_REQUEST`로 남긴다. 작업 후 `docs/agent-runs/AGENT_XX_EXECUTION_REPORT.md`를 작성한다. Astra 검수 전에는 main에 병합하지 않는다.

병합 권장 순서: AG01 → AG04 → AG05 → AG06 → AG03 → AG02 → AG07 → 통합 검증 → Astra 통합 검수 → PM Agent → 인간 PM → main.

후순위: 실시간 검지 시 검지 사진+메타데이터 전송, 검사 구간 geofence 기반 시작 보조, Xavier NX 확대, RTK 판정 기준 고도화, 원본 자동 업로드.
