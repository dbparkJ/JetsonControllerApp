# Merge-stage Runbook

Branch: `work/merge-stage-20260914`

진입: 실행 보고서와 Astra verdict 확인. FAIL branch는 통합하지 않는다.

순서:
1. main 동기화
2. AG01
3. AG04
4. AG05
5. AG06
6. AG03
7. AG02
8. merge-stage 전용 adapter/conflict commit
9. AG07
10. Android build/lint/JVM tests + backend tests + upload_receiver tests + 디자인 검사
11. Astra 통합 검수
12. PM Agent 평가
13. 인간 PM의 Android 화면/Orin NX 실기
14. 승인 후 main
15. main 검증 뒤 feature branch 정리

실기 핵심: 장치 연결, 작업 시작/종료, 연결 단절 중 수집 지속, 재연결 복구, 저장 확인, 서버 수신 확인, Jetson 없는 서버 조회, RTK 품질 요약.
