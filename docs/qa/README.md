# QA·Release 문서

- [지원·검증 matrix](SUPPORT_MATRIX_KO.md): phone, tablet, Orin NX, receiver 대상과 current qualification
- [요구사항 acceptance matrix](ACCEPTANCE_MATRIX_KO.md): 42개 REQ ID의 자동·실장치·demo·운영 판정
- [Evidence template](EVIDENCE_TEMPLATE_KO.md): 시험표, log, screenshot, 장애 주입 기록 형식
- [Release checklist](RELEASE_CHECKLIST_KO.md): demo freeze, 내부 운영 gate, hard blocker query
- [Rollback·진단](ROLLBACK_DIAGNOSIS_KO.md): Android, Jetson, receiver 복구와 증거 보존

정적 일관성 검사는 repository root에서 실행한다.

```bash
python3 scripts/check_qa_acceptance.py
```

이 명령은 product traceability의 모든 requirement ID가 acceptance matrix에 정확히 한 번 있는지와 각 evidence 열의 결과 vocabulary를 확인한다. 기능 합격이나 실장치 실행을 대신하지 않는다.
