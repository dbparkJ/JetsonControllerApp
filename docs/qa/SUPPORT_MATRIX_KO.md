# 지원·검증 Matrix

이 문서는 제품이 목표로 하는 조합과 현재 release에서 실제로 검증된 조합을 구분한다. `대상`은 지원 의도이고 `QUALIFIED`만 현재 commit의 실장치 증거가 있다는 뜻이다.

## 구성 요소 기준

| 구성 요소 | 코드 기준 | 자동 검증 | 현재 실장치 자격 | 제한 |
|---|---|---|---|---|
| Android 휴대전화 | Android 12/API 31 이상, compile/target SDK 37, JVM 25 build | full JVM/build/lint CI 구성 | NOT_QUALIFIED | 현재 release commit의 phone 설치·권한·잠금·radio 시험 없음 |
| Android 태블릿 | Android 12/API 31 이상, responsive Compose layout | phone과 같은 APK/JVM contract | NOT_QUALIFIED | 현재 release commit의 tablet screenshot·navigation·P2P 시험 없음 |
| Jetson Orin NX | Linux/systemd backend, Python venv, BLE, LAN, 선택적 Wi-Fi Direct | backend discovery 및 install/runtime fixture | NOT_QUALIFIED | JetPack/Ubuntu image, 무선 chipset/driver, sensor 장치 조합을 고정한 current device 시험 없음 |
| Upload receiver | Linux, Python 3.11 CI, HTTPS reverse proxy/receiver service | receiver discovery CI 구성 | NOT_QUALIFIED | 공개 DNS/TLS, filesystem, service account, backup/restore 운영 시험 없음 |

Xavier NX는 이번 지원 matrix의 대상이 아니다. 과거 조사나 다른 APK/commit의 장치 결과는 현재 release의 `QUALIFIED` 근거로 승격하지 않는다.

## 필수 조합

| ID | Android | Jetson/Server | 경로·시나리오 | 자동 증거 | Current release device evidence | Gate |
|---|---|---|---|---|---|---|
| SUP-01 | Phone API 31+ | Orin NX | BLE 등록 → LAN 제어 → start/stop | repository/backend unit | NOT_RUN | INTERNAL_BLOCKED |
| SUP-02 | Phone API 31+ | Orin NX | 인프라 Wi-Fi 없음 → Wi-Fi Direct fallback | Android/backend P2P unit | NOT_RUN | INTERNAL_BLOCKED |
| SUP-03 | Tablet API 31+ | Orin NX | BLE 등록 → LAN 제어 → start/stop | shared Android/backend unit | NOT_RUN | INTERNAL_BLOCKED |
| SUP-04 | Tablet API 31+ | Orin NX | Wi-Fi Direct 수동 선택·복구 | shared Android/backend P2P unit | NOT_RUN | INTERNAL_BLOCKED |
| SUP-05 | Phone API 31+ | Orin NX | 화면 잠금·일시 단절 중 수집 지속 → 동일 run 재조회 | stale-session/runtime unit | NOT_RUN | INTERNAL_BLOCKED |
| SUP-06 | Tablet API 31+ | Orin NX | 화면 잠금·일시 단절 중 수집 지속 → 동일 run 재조회 | stale-session/runtime unit | NOT_RUN | INTERNAL_BLOCKED |
| SUP-07 | Phone API 31+ | Receiver | Jetson 전원 OFF, LTE/인터넷 direct server 조회 | direct repository/receiver unit | NOT_RUN | INTERNAL_BLOCKED |
| SUP-08 | Tablet API 31+ | Receiver | stale cache, preview, trash/restore | data/UI branch + receiver unit | NOT_RUN | INTERNAL_BLOCKED |
| SUP-09 | 해당 없음 | Orin NX → Receiver | HTTPS upload, resume, receipt matched, source 보존 | backend/receiver integration fixtures | NOT_RUN | INTERNAL_BLOCKED |
| SUP-10 | Phone/Tablet | Orin NX + Receiver | run→output→upload→server receipt identity | 부분 contract만 존재 | NOT_RUN | INTERNAL_BLOCKED |

## 실장치 실행 시 기록할 환경

각 행의 결과에는 다음 값을 빠짐없이 기록한다.

- repository commit과 dirty 여부, APK versionName/versionCode와 SHA-256
- Android 제조사/모델, Android/API, form factor, 배터리 최적화 상태
- Jetson 모델, JetPack/L4T/Ubuntu, kernel, Python, BlueZ, NetworkManager
- Wi-Fi chipset/driver, LAN 또는 P2P 주파수, 인프라 인터넷 동시성
- pipeline ID, source revision, config SHA-256, run/log ID
- receiver environment, server build revision, TLS hostname, project/employee reference
- 시작/종료 시각과 timezone, test operator, evidence directory

장비 UUID, 직원 token, QR secret, NTRIP credential, private URL query는 원문으로 기록하지 않는다. 필요한 상관관계는 진단 시스템의 비식별 reference나 별도 보안 저장소의 record ID를 사용한다.

실행 형식과 screenshot 규칙은 [EVIDENCE_TEMPLATE_KO.md](EVIDENCE_TEMPLATE_KO.md)를 따른다.
