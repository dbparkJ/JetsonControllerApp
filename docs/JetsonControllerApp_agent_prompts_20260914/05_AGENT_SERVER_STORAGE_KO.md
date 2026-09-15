# AG05 서버 직접 조회·Storage

Branch: `work/05-storage-20260914`
Model: GPT-5.6 Sol XHigh

소유: `upload_receiver/**`, 독립 Android `data/server/**`·`data/storage/**`, 관련 tests.

Jetson 없이 휴대전화 인터넷으로 서버 작업/파일/수신 상태를 조회하고, 이미지·영상 preview/경로 요약에 필요한 계약을 만든다. 최근 목록은 마지막 갱신 시각과 함께 cache한다. 개발/시험/운영 서버를 구분한다. 직원별 인증과 역할/프로젝트 권한을 유지하며 기존 보안을 우회하지 않는다. 복구 가능한 보관/휴지통/복원 계약도 검토한다.

작업 후 `AGENT_05_EXECUTION_REPORT.md`.
