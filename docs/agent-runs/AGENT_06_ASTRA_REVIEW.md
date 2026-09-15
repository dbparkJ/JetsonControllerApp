# AG06 Astra 검수

- 검수일: 2026-09-14
- 검수자: Astra / Worker: GPT-5.6 Sol High
- 대상: `work/06-field-quality-20260914`, `6827e1c`
- Verdict: **PASS** — 지적 사항 수정 후 로컬 품질 기록·계산 범위

품질 sampler/summary, route recorder, history/route API와 nullable Android 모델을 검수했다. 관찰 시각·표본 시각·coverage window를 구분하고 저장된 근거로만 시간과 FIX 비율을 계산한다. 합격 임계값을 새로 만들지 않는다. GNSS 결측 또는 판독 불가를 측정된 0%로 표시하지 않으며 RTK 분모와 미관찰 시간을 별도로 제공한다.

Worker가 다음 지적을 수정했다.

- 첫 표본도 timestamp가 없거나 미래·오래된 값이면 ACTIVE로 보지 않는다. 센서별 유효 high-watermark를 유지해 시각 역행과 이후 회복을 구분한다.
- recorder 시계 역행 이후 이미 계산한 구간을 다시 합산하지 않는다. coverage 밖의 시간은 unknown으로 남긴다.
- fractional NMEA category를 FIX로 변환하지 않는다. 해석 불가 RTK category도 FIX 비율 분모에서 제외한다.
- 실제 route 시간과 겹치지 않는 문제 구간에 임의 지도 index를 만들지 않는다. 유효하지 않은 위치 표본은 품질 근거로 보존하고 route point에서 제외한다.
- 품질 기록은 bounded sidecar이며 센서/기록 실패를 pipeline 종료로 전환하지 않는다. legacy 기록에는 quality가 nullable이다.

backend 관련 테스트 64개 통과와 Android XML의 11개 테스트(quality interpreter 및 API compatibility), `git diff --check`를 확인했다. 최종 전체 회귀 검증은 merge-stage에서 수행한다.

런타임의 필수 센서 정책 source는 아직 없다. 생성자는 명시 정책을 받지만 실제 runner는 UNSPECIFIED를 기록하며 필수 센서 preflight를 차단하지 않는다. AG02의 화면 연결과 실제 장시간 센서·저장·전화 단절 시험은 별도 확인 대상이다. 8 MiB 기록 한도 도달 이후 전체 실행 품질을 보장하지 않으며 truncated 표시를 유지해야 한다. 이 PASS는 현장 운영 승인에 해당하지 않는다.
