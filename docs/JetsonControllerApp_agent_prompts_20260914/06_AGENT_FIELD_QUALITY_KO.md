# AG06 GNSS/RTK·현장 품질

Branch: `work/06-field-quality-20260914`
Model: GPT-5.6 Sol XHigh

소유: Android RTK/품질 모델, `backend/jetson_control/mobile_rtk.py`, 독립 품질 계산 코드와 tests.

RTK FIX 비율/시간/문제 구간을 계산해 사용자가 판단하기 쉬운 요약을 제공한다. 근거 없는 pass/fail 임계값은 만들지 않는다. 작업 중 RTK 품질 저하는 우선 수집 지속+문제 구간 기록이다. 필수/선택 센서 상태를 구분하고 지도/작업 이력에서 문제 구간을 표현할 데이터 계약을 만든다.

작업 후 `AGENT_06_EXECUTION_REPORT.md`.
