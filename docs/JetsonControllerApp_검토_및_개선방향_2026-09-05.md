# JetsonControllerApp 검토 보고서

> **판단: 전면 재작성보다 기존 저장소 개선을 권장한다. 단, 연결 제어·복구 계층은 부분 재설계한다.**

- 검토일: 2026년 9월 5일
- 저장소: `dbparkJ/JetsonControllerApp`
- 기준: `main`, `fe888da910fe7456ca60132d2c9e7c3645a6565a`
- 기준 커밋 일시: 2026년 9월 1일 13:33:02 KST
- 앱 빌드 설정: `versionName=1.15.1`, `versionCode=20`, `minSdk=31`, `targetSdk=37` [S01]
- 문서 성격: 검토 결과 및 향후 개선 설계. **실행 지시나 변경 완료 보고가 아니다.**

## 1. 검토 범위와 판단의 한계

Android 앱의 연결 매니저·자동 연결 정책·인증·권한·주요 화면 및 상태 관리, Jetson의 Wi-Fi Direct 데몬·API·상태 수집, 업로드 수신 API, 테스트 구성과 운영 문서를 확인했다. 저장소 전반의 구조를 검토하되, 특히 연결과 사용자 흐름을 깊게 추적했다. 모든 파일의 모든 줄을 감사한 결과나 보안 인증 결과는 아니다.

이번 검토에서는 앱을 실행하거나 APK를 빌드하지 않았고, 저장소 테스트도 실행하지 않았다. 실제 휴대전화·Jetson·무선 어댑터를 이용한 연결 재현 및 화면 캡처 검증은 하지 않았다. 현재 현장에 설치된 APK와 backend가 이 커밋과 일치하는지도 확인되지 않았다. 따라서 아래에서 **코드상 확인된 결함/동작**과 **실기기 재현이 필요한 원인 후보**를 구분한다. 화면 평가는 Compose 화면 구성과 내비게이션 코드에 근거한다.

저장소 파일, 브랜치, 커밋, 배포 설정은 변경하지 않았다.

## 2. 리빌딩보다 개선을 권하는 이유

현재 문제는 기반 기술을 잘못 선택한 것이라기보다, 기능이 늘면서 연결·복구·화면 상태를 일관되게 관리하기 어려워진 데 가깝다. 파일이 크다는 이유만으로 재작성하지 않는다. 아래처럼 재사용할 기반이 실제로 존재한다.

| 유지할 기반 | 확인 내용 | 판단 |
|---|---|---|
| Android UI | Compose/Material 3, 공통 테마, 화면별 ViewModel | 프레임워크 교체보다 정보 구조·상태 표현 개선 |
| 장비 등록·인증 | QR 비밀값 기반 인증서 증명, 인증서 고정, 암호화 자격증명 저장 | 연결 문제 해결 중 인증을 우회하거나 삭제하지 않음 |
| 사용자 보호 | 전원 동작 확인, 상태 최신성 표시, 일부 이전 연결 응답 방어 | 이미 구현된 보호장치를 보존하고 적용 범위를 확대 |
| 장비 서비스 | API·상태 수집·Wi-Fi·센서·작업 실행 분리의 기초 | 서비스별 결함을 고치고 연결 제어 책임을 명료하게 만듦 |
| 업로드 수신 API | 요청 크기 제한, 청크 해시·offset, 완료 절차 | 앱 재작성 때문에 데이터 전송 프로토콜까지 바꾸지 않음 |
| 테스트 | Wi-Fi Direct 드라이버 분기·검색 복구 등 단위 테스트 존재 | 기존 테스트에 장애 주입·비동기 순서 테스트를 보강 |

근거: [S01][S07][S11][S12][S15][S19][S21]

권장 방식은 **기존 저장소 안에서 작은 변경 단위로 안정화한 뒤, 연결 코어를 추출하는 점진적 개선**이다. 전체를 새로 만들면 QR 등록·인증·BLE·파일·업로드·작업 실행 호환성을 다시 검증해야 한다. 반면 현재 발견된 연결 결함은 상당 부분 독립적으로 고칠 수 있다.

## 3. 우선순위 요약

P1은 현장 신뢰성에 영향을 주어 먼저 해결할 항목, P2는 이어서 개선할 사용자 경험·유지보수 항목이다. 실제 장애 빈도나 영향률을 측정한 등급은 아니다.

| ID | 우선순위 | 문제 | 확인 수준 |
|---|---|---|---|
| CON-01 | P1 | Jetson 연결 작업의 `TimeoutExpired` 누락으로 `CONNECTING` 고착 가능 | 예외 처리 누락과 복구 공백을 코드에서 확인 |
| CON-02 | P1 | Android 연결 타임아웃이 앱 상태만 초기화 | 타임아웃 경로에 OS 협상 정리가 없는 것 확인 |
| CON-03 | P1 | 일반 Wi-Fi 사용 중 자동 Direct 차단 + 수동 Direct 진입점 부재 | 정책과 현재 내비게이션 조합에서 확인 |
| CON-04 | P1 | BLE 준비 완료가 Direct API 확인 시도를 무효화하고 재검사가 빠질 가능성 | 이벤트 순서에 따른 경합 후보, 재현 필요 |
| NET-01 | P1 | 비동기 API에서 동기 상태 수집·외부 명령을 직접 호출 | 실행 구조 확인, 지연 영향량은 미측정 |
| STATE-01 | P1 | 이전 IP 연결의 늦은 상태 응답이 새 상태에 반영될 수 있음 | 성공 응답 적용 전 세션 확인 누락 |
| UX-01 | P1 | 일시 끊김이 화면 이탈·작업 초안·탐색 경로 초기화로 이어짐 | 내비게이션 및 ViewModel 초기화 확인 |
| UX-02 | P2 | 등록·발견·연결·제어 가능 상태 및 버튼 문구가 불명확 | 화면 구성에서 확인 |
| DATA-01 | P2 | 측정 실패를 0/false로 반환하는 항목 존재 | 상태 수집 구현에서 확인 |
| OPS-01 | P2 | 운영 문서와 현재 연결 흐름 불일치, 빌드 검증 보강 필요 | 문서/코드 비교 및 빌드 설정 확인 |

## 4. Wi-Fi Direct 문제 상세

### CON-01. Jetson 연결 작업이 예외 후 CONNECTING에 남을 수 있음

**근거 파일:** `backend/jetson_control/wifi_direct.py`의 `_run()`, `_activate_peer()`, `request_connection()`, `monitor()` [S16]

`_run()`은 `subprocess.run(..., timeout=...)`을 사용한다. 비정상 종료 코드는 `WifiDirectError`로 변환하지만, 시간 초과 예외를 일관되게 변환하지 않는다. `_activate_peer()`는 `OSError`, `ValueError`, `WifiDirectError`만 처리한다. `subprocess.TimeoutExpired`는 `SubprocessError` 계열이므로 해당 예외 목록에서 빠져 있다. [E01]

가능한 고장 경로는 다음과 같다.

```text
Android가 연결 요청
  → backend 상태 CONNECTING
  → nmcli / wpa_cli 등 외부 명령 시간 초과
  → 연결 작업 스레드에서 처리되지 않은 예외
  → 정상 정리·일반 Wi-Fi 복구·DISCOVERABLE 복귀 누락 가능
  → 새 요청은 상태가 DISCOVERABLE이 아니라 거절
```

그룹이 만들어지지 않은 경우를 기준으로 `monitor()`에는 죽은 연결 작업이나 오래된 `CONNECTING`을 복구하는 분기가 없다. 메인 데몬이 살아 있을 수 있으므로 단순한 프로세스 재시작 정책만으로 해결된다고 볼 수도 없다.

**개선 방향:** 외부 명령의 시간 초과를 연결 오류 체계로 통합하고, 성공·실패·취소가 모두 정리 경로를 통과하게 한다. 연결 시도마다 식별자와 단조 시계 기반 종료 시한을 둔다. 연결 작업이 죽거나 시한을 넘기면 그룹·DHCP·일반 Wi-Fi 복구를 수행한 후 명시적인 재시도 가능 상태 또는 복구 실패 상태로 전환한다.

**검증 기준:** 연결 활성화 단계에 `TimeoutExpired`를 주입해도 무기한 `CONNECTING`에 남지 않는다. 정리 실패를 숨기지 않는다. 다음 정상 요청을 받을 수 있거나 사용자가 수행할 조치를 명확히 안내한다. 이 검증은 기존 `FakeRunner` 기반 테스트에 추가하기 적합하다. [S19]

### CON-02. Android 타임아웃이 실제 연결 협상을 취소하지 않음

**근거 파일:** `WifiDirectManager.kt`의 `scheduleConnectionTimeout()`, `resetDisconnectedState()`, `cancelConnect()`, `disconnect()` [S02]

타임아웃 시 `resetDisconnectedState()`를 호출해 앱의 연결 중 표시와 주소 등을 지운다. 이 함수에는 `WifiP2pManager.cancelConnect()`나 그룹 정리 요청이 없다. 따라서 타임아웃 이후 Android가 아직 협상·그룹 생성 중인 상황에 대한 정리가 이 경로에서 보장되지 않는다. Android의 협상 취소는 별도의 `cancelConnect()` 동작이다. [E02]

이 상태에서 새 시도를 시작하면 늦은 연결 이벤트나 OS의 BUSY 응답과 겹칠 수 있다. 단, 실제 기기에서 OS 협상이 타임아웃 시점까지 남는지와 빈도는 로그로 확인해야 한다.

**개선 방향:** `연결 중 → 정리 중 → 재시도 가능` 단계를 분리한다. 취소와 그룹 제거를 현재 시도에 묶고, 완료 또는 정리 타임아웃 전에는 새 연결 시도를 중첩하지 않는다. 기존 그룹 전체를 무조건 삭제하기보다 이 앱의 대상 세션과 정리 범위를 확인한다.

추가로 현재 취소·그룹 제거의 비동기 콜백은 연결 시작 콜백과 같은 수준의 세대 검사를 갖추지 않았다. 이전 정리 결과가 이후 연결의 상태를 지우지 않도록 같은 세션 규칙을 적용해야 한다. [S02]

**검증 기준:** 타임아웃 직후 재시도, 취소 직후 재시도, 늦은 성공 콜백 도착을 반복해도 이전 시도가 새 시도를 종료하거나 화면에 연결 완료를 표시하지 않는다.

### CON-03. 안전한 자동 연결 정책에 수동 복구 경로가 빠져 있음

**근거 파일:** `AutomaticConnectionPolicy.kt`, `JetsonRepository.kt`, `ConnectionHubScreen.kt`, `JetsonApp.kt` [S03][S04][S05][S06]

일반 Wi-Fi에 연결돼 있으면 자동 Direct 연결을 막는다. 단일 무선 장치에서 Direct가 기존 일반 Wi-Fi를 끊을 수 있다는 코드의 설계 취지는 타당하다. 따라서 이 조건을 단순히 삭제하는 것은 권하지 않는다.

문제는 현재 `WIFI_DIRECT` 화면 자체는 정의되어 있지만, 검토한 앱 내비게이션에는 그 화면으로 이동하는 사용자 진입점이 없고, 연결 허브의 도구도 새 장비 등록만 제공한다는 점이다.

```text
휴대전화가 일반 Wi-Fi에 연결됨
  + 대상 Jetson은 그 네트워크에서 연결되지 않음
  → 자동 Direct는 정책상 생략
  → 사용자에게 명시적 Direct 전환 경로도 보이지 않음
```

이 조합은 사용자가 환경에 따라 “될 때도 있고 안 될 때도 있다”고 느낄 수 있는 구체적인 원인 후보다.

**개선 방향:** 장비 상세의 `연결 문제 해결`에서 `장비에 직접 연결`을 제공한다. “장비의 공유기 연결과 서버 업로드가 중단될 수 있습니다”라는 영향을 설명한 뒤 사용자가 전환을 선택하게 한다. 자동 정책과 사용자의 명시적 선택을 구분하고, 전환 이후에는 정책이 그 선택을 즉시 되돌리지 않도록 한다.

**검증 기준:** 같은 Wi-Fi, 다른 Wi-Fi, 일반 Wi-Fi 없음, 장비 LAN 미응답에서 각각 사용자가 다음 행동을 찾을 수 있다. 대상 장비의 네트워크 상태를 고려하지 않은 강제 전환은 하지 않는다.

### CON-04. BLE 연결 완료와 Direct API 검사 사이의 경합 후보

**근거 파일:** `JetsonRepository.kt`의 BLE 상태 수집, `ensureAutomaticBleReconnectLoop()`, `probeWifiDirectApi()` 및 정책의 `wifiDirectProbeSignals()` [S03][S04]

Direct 링크가 연결됐더라도 API 확인이 끝나기 전에는 활성 제어 전송 수단이 확정되지 않을 수 있다. 이때 BLE 재연결이 허용될 수 있고, BLE가 `Ready`가 되면 공유 IP 연결 세대가 증가한다. Direct 검사는 세대가 달라져 종료될 수 있다.

그런데 Direct 검사 재실행 신호는 연결 여부·호스트·LAN 연결 대기 여부를 중심으로 만들어진다. BLE 준비 완료만으로 그 값이 바뀌지 않으면 중단된 검사가 다시 시작되지 않을 가능성이 있다.

**확인 수준:** 소스 흐름으로 도출한 경합 후보다. 실제 이 순서가 발생했는지는 재현과 이벤트 로그가 필요하다.

**개선 방향:** BLE·LAN·Direct가 각각 독립적으로 연결 우선권을 가져가는 대신, 하나의 연결 조정자가 선택한 장비와 현재 시도를 관리한다. 무효화된 검사에는 단순 조용한 종료가 아니라 대체 경로 확정 또는 재예약이라는 후속 결과가 있어야 한다.

**검증 기준:** Direct 그룹 생성 직후 BLE 인증 완료를 의도적으로 발생시켜도 `링크 연결 / 제어 불가` 중간 상태에서 멈추지 않는다.

## 5. Wi-Fi 문제처럼 보일 수 있는 다른 결함

### NET-01. API 응답 지연을 무선 장애와 분리해야 함

`api.py`의 `async def device_status()`는 동기 `status_service.collect()`를 직접 실행한다. 수집기는 `systemctl`, `nmcli`, 디스크·센서 읽기와 CPU 샘플링 등을 포함한다. 이 호출은 작성된 그대로 현재 비동기 실행 흐름을 막을 수 있다. FastAPI가 직접 호출한 일반 유틸리티 함수까지 자동으로 스레드풀에 옮겨주지는 않는다. [S17][S18][E03]

앱은 IP 상태를 주기적으로 읽고 실패를 누적하므로, 무선 링크가 살아 있어도 backend 응답 지연이 사용자에게 연결 실패처럼 보일 수 있다. 실제 지연량과 장애 연관성은 측정해야 한다. [S03][S04]

**개선 방향:** 시스템 지표를 별도 수집 루프에서 갱신하고 API는 마지막 스냅샷과 수집 시각을 빠르게 반환하게 한다. 외부 명령·블로킹 I/O는 적절한 작업 실행기로 분리한다. API 프로세스 전체 응답성과 개별 센서 조회 실패를 구분한다.

이미 카메라 프리뷰와 업로드 수신 서버에 `run_in_threadpool` 사용 사례가 있어 같은 저장소 내 좋은 패턴을 확장할 수 있다. [S17][S21]

또한 연결 확인, 짧은 상태 조회, 대용량 파일에 동일한 HTTP 읽기 제한을 적용하고 있다. 현재 LocalApiClient의 연결 제한은 5초, 읽기·쓰기는 45초이며 연결 검사 전체의 별도 시한은 보이지 않는다. 재시도 횟수만 줄이거나 늘리기보다 용도별 제한과 전체 연결 시한을 설계해야 한다. [S11]

### STATE-01. 늦은 이전 연결 응답이 상태를 덮어쓸 수 있음

`JetsonRepository.refreshStatus()`는 호출 전 전송 객체를 가져오지만 성공 응답을 `updateStatus()`에 반영하기 직전에 그 객체가 현재 세션인지 확인하지 않는다. 실패 경로도 현재 전송 객체인지 판단하기 전에 공유 실패 횟수를 올린다. [S03]

**영향 후보:** 장비 전환 또는 연결 해제 직전에 시작한 요청이 나중에 끝나 새 화면의 수치·상태 최신성을 오염시킬 수 있다. 실제 발생을 재현한 것은 아니다.

**개선 방향:** 응답의 적용 조건을 `deviceId + sessionId + requestId`로 통일한다. 상태뿐 아니라 팬 제어, 파일 조회, 작업 시작·중지 결과에도 동일한 규칙을 적용한다. 변경 명령은 요청 수락과 실제 처리 완료를 구분한다.

저장소·작업 ViewModel에는 이미 연결 세대 검사와 작업 취소가 있어, 코드 전체가 방어 로직 없이 작성된 것은 아니다. 이 방어 규칙이 계층마다 다르게 구현된 것이 문제다. [S09][S10]

### DATA-01. 0이라는 값과 측정 불가를 구분해야 함

상태 수집 중 일부 예외는 CPU/GPU/온도/저장소 수치를 0으로 반환하거나 서비스 상태를 false로 반환한다. 그러면 “측정하지 못함”과 “실제 사용량 0/중지”가 구분되지 않는다. [S18]

**개선 방향:** 지표별 값과 별도로 `validity`, `observedAt`, 실패 이유를 둔다. 측정 불가라면 `확인 불가`로 표시하고 지난 유효값을 표시할 때는 측정 시각을 함께 보여준다. HTTP 응답이 최근 도착했다는 사실만으로 모든 센서 측정이 최신이라고 판단하지 않는다.

## 6. 디자인·사용감 개선

### 6.1 이미 구현된 부분은 유지

현재 코드에는 장비 중심 연결 허브, 첫 등록 안내, 대시보드 상태 최신성 안내, 재부팅·종료 확인, 공통 테마와 하단 탭이 있다. 따라서 “온보딩이 없다”, “전원 동작 확인이 없다”, “오래된 상태 표시가 없다”는 평가로 되돌아가면 안 된다. [S05][S06][S07][S15]

현재 디자인의 핵심 문제는 특정 색상이 아니라 **화면 간 상태 설명과 복구 동작이 일치하지 않는 점**이다.

### 6.2 UX-01: 연결이 끊겨도 사용자를 원래 작업에 남겨둠

현재 일부 화면은 `Disconnected/Error`에서 연결 허브로 이동하고 중간 내비게이션을 제거한다. 저장소와 작업 ViewModel은 연결이 사라졌을 때 각각 기본 상태로 초기화된다. 따라서 파일 탐색 경로나 작업 초안·설정 편집 상태를 잃을 수 있다. [S05][S09][S10]

권장 흐름:

```text
작업 설정 편집 중
  → 연결 단절
  → 같은 화면에 머무름
  → 상단: “장비와 다시 연결 중입니다. 작성한 내용은 유지됩니다.”
  → 저장·실행 버튼만 일시 비활성화
  → 재연결 후 현재 장비와 설정 버전 확인
  → 같은 화면에서 계속 편집
```

같은 장비 재연결 시에는 초안·스크롤·선택 파일을 보존한다. 다른 장비로 전환할 때는 이전 장비의 초안을 자동 적용하지 않고 별도로 저장한다. 저장 중 끊긴 경우에는 “실패”를 단정하지 않고 서버 반영 여부를 다시 확인한다.

### 6.3 UX-02: 장비 상태와 버튼의 의미를 일치시킴

현재 연결 허브는 장비를 네트워크에서 발견했지만 아직 제어 연결하지 않은 상태와 완전히 찾을 수 없는 상태를 충분히 구분하지 않는다. `오프라인 상태 보기` 버튼이 실제로 자동 재연결을 시작하는 동작과도 어긋난다. [S05][S06]

권장 표현:

| 실제 상황 | 표시 | 주요 행동 |
|---|---|---|
| 인증정보만 저장됨 | 등록됨 · 마지막 확인 시각 | 연결 시도 |
| 네트워크에서 최근 발견 | 장비 발견됨 | 연결 |
| 연결 진행 중 | 장비 확인 중 / 연결 준비 중 | 취소 |
| BLE만 준비됨 | 기본 연결 · 일부 기능 사용 가능 | 전체 제어 연결 |
| 인증·기능 확인 완료 | 제어 가능 | 장비 열기 |
| 잠시 응답 없음 | 재연결 중 · 마지막 상태 시각 | 기다리기 / 문제 해결 |
| 재시도 종료 | 연결하지 못함 · 원인 요약 | 원인에 맞는 재시도 |

“등록됨”은 영구 등록 상태, “발견됨”은 최근 관측, “제어 가능”은 인증된 현재 세션이라는 서로 다른 개념으로 유지한다. 개발 용어인 API·GO·DHCP는 기본 화면에 늘어놓지 않고 상세 진단에 둔다.

### 6.4 연결 진행 화면

사용자가 보는 단계는 `장비 찾기 → 연결 준비 → 장비 확인 → 제어 가능` 정도로 단순화한다. 내부에서는 검색·P2P 협상·주소 할당·HTTPS 도달·인증·기능 확인을 구분해 기록한다.

무한 스피너 대신 현재 단계와 취소를 제공한다. 자동 재시도 중에는 매번 강한 오류 배너를 띄우지 않고, 시도가 종료되면 다음 행동을 제시한다. 권한 거부는 권한 설정으로, 장비 미발견은 전원·거리 확인으로, 링크는 정상이지만 API 무응답이면 장비 서비스 점검으로 연결한다. 인증 실패에 단순 재검색을 무한 권하지 않는다.

### 6.5 대시보드는 지표 목록보다 작업 중심

현재 홈에는 연결 요약, 건강 상태, 진행 작업, 시스템 지표, 여러 빠른 작업과 전원·팬 제어가 함께 들어 있다. [S07]

추천 우선순위는 `현재 장비 → 현재 작업/다음 행동 → 작업에 필요한 상태 → 자세한 지표`다. 기술 설정과 전원 제어는 2차 화면으로 낮춘다.

아래는 **제안 와이어프레임이며 실제 장비 수치가 아니다.**

```text
내 장비 / MMS-01                         알림
제어 가능 · 직접 연결          연결 상세

현재 작업
도로 수집 · 실행 중
진행 시간 00:24:18     저장 데이터 2.3 GB
[ 수집 중지 ]              [ 작업 상세 ]

수집 준비 상태
카메라  정상     위치  확인 중     저장 공간  충분

주의할 사항
정밀 위치 보정이 아직 준비되지 않았습니다.
[ 위치 상태 확인 ]

최근 데이터                          모두 보기
[ 오늘 수집한 데이터 ]

홈       작업       데이터       센서       설정
```

실행 중지 버튼이 항상 위험 동작이라는 의미는 아니다. 해당 작업의 데이터 마감·손실 가능성에 맞춰 확인 수준을 설계한다. 작업마다 불필요한 확인을 반복하지 않되, 전원 종료와 원본 데이터 삭제는 명확히 구분한다.

### 6.6 화면별 개선안

| 화면 | 권장 변경 | 완료 기준 |
|---|---|---|
| 장비 목록 | 발견 상태·연결 진행률·마지막 확인·문제 해결 진입점 | 장비가 안 될 때 다음 행동을 찾을 수 있음 |
| 연결 상세 | 자동/직접 연결, 단계별 실패 원인, 재시도·취소 | OS 상태와 앱 표시가 어긋나지 않음 |
| 홈 | 현재 작업과 가장 중요한 3개 안팎의 상태 중심 | 한 화면에서 지금 가능한 작업을 이해함 |
| 작업 | 시작 전 준비상태 점검, 작업별 실행 결과, 초안 보존 | 잠시 끊겨도 입력과 작업 맥락 보존 |
| 데이터 | 장치 데이터/서버 데이터 분리, 진행·완료·실패 모두 접근 | 업로드 종료 후에도 결과를 쉽게 확인 |
| 센서 | 정상/준비 중/측정 불가/오래된 값 구분 | 0을 정상 측정으로 오해하지 않음 |
| 설정 | 앱 설정과 장비 설정 분리 | 오프라인에서도 앱 알림·표시 설정 가능 |

현재 홈의 업로드 바로가기는 활성 업로드가 있을 때만 활성화되고, 앱 알림 설정 화면도 연결 제한을 받는다. 이처럼 기능의 필요 조건보다 큰 범위로 UI를 잠그는 부분을 줄인다. [S05][S07]

### 6.7 시각 스타일과 권한

기존 Teal 계열과 밝은/어두운 테마를 유지할 수 있다. 상태·배너·버튼의 색상 의미와 간격을 일관되게 하고, 장식 카드보다 작업별 정보 위계를 먼저 개선한다. 성공은 실제 제어 준비 완료, 오류는 사용자 개입이 필요한 실패에 사용한다. 상태를 색상 하나로만 전달하지 않는다. [S15]

제안 화면 규격은 본문 약 16sp, 주요 행동 높이 약 52~56dp, 터치 영역 최소 48dp를 시작점으로 한다. 큰 글꼴, 작은 화면, 화면 회전, 키보드 표시, 밝은 야외 환경에서 실제 확인한다. 이는 이번 검토에서 측정한 현재 UI의 위반 목록이 아니라 향후 디자인·검증 기준이다.

현재 시작 시 여러 연결 관련 권한을 한 번에 요청한다. 기능의 목적을 설명한 뒤 연결·QR 스캔·Wi-Fi 검색 단계에 필요한 권한을 요청하도록 바꾸는 편을 권한다. 사용자가 거부해도 장비 등록정보와 도움말은 볼 수 있어야 한다. [S13][S14]

## 7. 권장 구조: 연결 코어를 먼저 정리

### Android

```text
화면 / 화면별 ViewModel
           ↓
선택 장비 세션(DeviceSession)
           ↓
연결 조정자(ConnectionOrchestrator)
           ├─ BLE 연결 어댑터
           ├─ LAN 발견·연결 어댑터
           └─ Wi-Fi Direct 어댑터

인증된 세션을 공유하는 기능별 Repository
  ├─ 상태·센서
  ├─ 작업 실행·설정
  ├─ 장치/서버 파일
  └─ 업로드
```

`JetsonRepository`를 기계적으로 여러 파일로 나누는 것보다 **누가 연결의 최종 결정을 내리는지**를 하나로 정하는 것이 중요하다. [S03]

필수 규칙은 다음과 같다.

1. 연결 시도·취소·전환·복구를 한 흐름에서 직렬화한다.
2. 상태 반영은 현재 장비·세션에 대해서만 한다.
3. 연결 취소 후 늦은 콜백이 새 연결을 만들지 못한다.
4. BLE 연결, IP 링크 연결, 인증 완료, 기능 사용 가능을 구분한다.
5. 짧은 장애와 사용자의 명시적 연결 해제를 구분한다.
6. 설정·파일 초안은 연결 객체의 수명과 분리한다.

화면별 ViewModel 수명과 백그라운드 조회도 정리한다. 현재 루트에서 여러 ViewModel을 생성하고, 작업 목록 폴링은 연결 상태를 따라 시작한다. 백그라운드에서도 필요한 작업과 화면이 보일 때만 필요한 조회를 분리한다. 다만 RTK처럼 지속되어야 하는 기능을 단순히 화면 이탈 시 종료하지 않는다. RTK foreground service 선언은 이미 있으므로 이를 없는 기능처럼 평가하면 안 된다. [S05][S10][S14]

### Jetson backend

Wi-Fi 제어는 상태 전이, 외부 명령 실행, 그룹·DHCP 자원 관리, 기존 Wi-Fi 복구를 분리하되 한 연결 시도의 소유권으로 묶는다. API에는 가벼운 상태 스냅샷을 제공한다. 드라이버별 분기와 동시 인터페이스/채널 제한은 실제 장비에서 검증한다. 현재 코드가 드라이버를 고려하지 않는 것은 아니며, 이미 있는 분기에 실패 복구를 보강하는 작업이다. [S16][S19]

전체 기술 스택, 인증 프로토콜, API 버전, 업로드 포맷을 한꺼번에 바꾸는 작업은 이번 안정화 범위에서 제외하는 것이 좋다.

## 8. 운영·문서·검증 체계

`docs/WIFI_DIRECT_SETUP.md`는 최초 등록 후 Wi-Fi 설정으로 이동하고 연결 허브에 Direct 메뉴가 있다고 설명한다. 현재 앱은 등록 완료 후 대시보드로 이동하며 허브에 그 메뉴가 없다. 또한 일반 Wi-Fi를 유지한다는 설명은 현재 backend의 동시 연결 미지원 어댑터용 중단·복구 분기까지 충분히 반영하지 못한다. 사용자가 문서를 따라가도 해결되지 않는 상태다. [S05][S06][S16][S20]

운영 문서에는 앱 버전, backend 커밋, 어댑터·드라이버, 연결 방식별 인터넷 사용 가능 여부, 알려진 제약과 검증 결과를 연결해야 한다. 문서의 명령은 실제 배포된 버전 기준으로 검증한다.

현재 빌드 설정에는 Navigation 관련 lint 검사 여러 개를 호환성 문제로 끈 흔적이 있다. 원인을 해결하고 검사를 단계적으로 복구한다. 저장소 루트 트리에서는 `.github` 디렉터리를 확인하지 못했으므로 저장소에 커밋된 GitHub Actions 검증 체계도 정비할 대상으로 본다. 이것이 외부 CI나 수동 검증이 전혀 없다는 뜻은 아니다. [S01][S22]

## 9. 우선 수집할 진단 정보

같은 성공/실패를 비교할 수 있도록, 아래 정보가 한 시도 식별자로 묶여야 한다.

| 구분 | 기록할 정보 |
|---|---|
| 빌드 | APK 버전·앱 커밋·backend 커밋 |
| 휴대전화 | 모델, Android 버전, Wi-Fi/위치·주변 기기 권한, 일반 Wi-Fi·모바일 데이터·VPN 상태 |
| Jetson | OS·커널, Wi-Fi 어댑터·드라이버, NetworkManager 버전, P2P 동시 인터페이스·채널 지원 |
| 연결 단계 | 발견, 협상, 그룹/IP, HTTPS, 인증, 기능 확인 각각 시작/종료/실패 이유 |
| 전환 | BLE/LAN/Direct 선택 이유, 이전 세션 종료 이유 |
| backend | 외부 명령 종류, 종료 코드·타임아웃, DHCP·그룹·Wi-Fi 복구 결과 |

장치의 `/run/jetson-control/wifi-direct.json`, `jetson-wifi-direct.service`·NetworkManager 로그, `iw dev`·인터페이스 주소 정보를 활용할 수 있다. [S20]

QR secret, Wi-Fi 암호, 인증 토큰, RTK 비밀번호는 진단 파일에 넣지 않는다. SSID·장비명·IP 등도 공유 범위에 맞게 가명화한다. 성공 로그와 실패 로그를 함께 확보해 “장비 미발견”, “P2P 미완성”, “API 지연”, “인증 실패”를 구분한다.

## 10. 구현 전에 고정할 검증 기준

아래는 **향후 검증안**이며 이번에 통과한 결과가 아니다.

| 시나리오 | 기대 결과 |
|---|---|
| nmcli/wpa_cli 시간 초과 주입 | 연결 작업 종료·정리·복구 결과가 확정되고 영구 CONNECTING 없음 |
| Android 연결 시간 초과 | OS 협상 정리 후 재시도 가능 |
| 취소 직후 재시도·늦은 콜백 | 이전 시도가 새 연결을 활성화하거나 종료하지 않음 |
| BLE 준비 완료와 Direct API 확인 동시 발생 | 전송 수단이 최종 확정되거나 명확한 실패로 귀결 |
| 장비 A 요청 지연 중 B 전환 | A의 상태·명령 결과가 B 화면에 반영되지 않음 |
| 일반 Wi-Fi는 있으나 대상 LAN은 없음 | 직접 연결 선택 또는 구체적 문제 해결 안내 |
| backend 상태 수집 지연 | API 전체가 연쇄 정지하지 않으며 지표가 오래됐음을 표시 |
| 작업 설정·파일 탐색 중 순간 단절 | 같은 장비 재연결 후 초안·경로·화면 유지 |
| 업로드 시작 직후 앱 연결 단절 | 서버 작업의 실제 수락·진행 결과를 재조회하여 중복 시작 방지 |
| 화면 잠금·백그라운드·복귀 | 필요한 RTK 중계와 사용자 기대 상태가 유지 또는 명확하게 복구 |
| 권한 거부·설정에서 권한 변경 | 막다른 화면이나 무한 자동 재시도 없음 |
| 큰 글꼴·회전·키보드 표시 | 주요 행동·오류 메시지·취소가 가려지지 않음 |

핵심 휴대전화/어댑터 조합별로 최초 연결·재연결을 예컨대 50회 반복해 성공률과 단계별 소요시간을 기록한다. 숫자는 검증 규모의 제안이지 현재 성공률이 아니다. 합격 임계값은 실제 현장 허용 지연과 수동 복구 허용 빈도를 정한 뒤 고정한다. 인증 우회, 다른 장비 제어, 원본 데이터 오삭제, 이전 세션 응답 오염, 무기한 대기는 허용하지 않는다.

## 11. 권장 진행 순서

| 단계 | 작업 | 다음 단계 진입 조건 |
|---|---|---|
| 1. 기준 고정 | 배포 버전 확인, 증상 분류, 시도별 진단·회귀 테스트 추가 | 동일 조건의 성공·실패를 구분할 수 있음 |
| 2. 직접 결함 수정 | CON-01·02, NET-01, STATE-01을 작은 단위로 수정 | 시간 초과·늦은 응답·취소 테스트 통과 |
| 3. 복구 경로 복원 | 수동 Direct 진입, 오류별 안내, 자동 정책 우선권 정리 | 일반 Wi-Fi 유무에 따른 막다른 흐름 제거 |
| 4. 연결 코어 추출 | 단일 조정자와 장비별 세션, CON-04 경합 테스트 | 기능·인증 호환성을 유지하며 전이 규칙 통일 |
| 5. 사용자 경험 개편 | 화면·초안 보존, 홈 우선순위, 앱/장비 설정 분리 | 대표 작업을 중단·복귀 조건에서 수행 가능 |
| 6. 배포 품질 강화 | 실기기 조합 반복, CI·lint·문서 정비 | 알려진 제약과 검증 결과를 포함한 배포 가능 |

각 단계를 별도 변경 단위로 검토하고 되돌릴 수 있게 한다. 큰 리팩터링과 UI 변경, backend 프로토콜 변경을 한 번에 섞지 않는다. “타임아웃 숫자만 늘리기”, “인증 검사를 꺼서 연결되게 만들기”, “실패할 때 무조건 전체 네트워크 재시작하기”는 해결 완료 기준이 아니다.

## 12. 최종 판단

**현재 근거로는 새 저장소를 만들어 전면 재작성할 이유보다 기존 저장소를 개선할 이유가 더 강하다.**

사용자가 겪는 불안정성은 사소한 화면 버그만의 문제는 아니다. 연결 상태의 소유권, 실패 후 복구, 늦은 응답 처리, 화면 맥락 보존이라는 공통 문제가 여러 증상으로 나타날 수 있다. 그렇다고 인증·업로드·센서·작업 실행 기반까지 폐기할 필요는 없다.

가장 먼저 할 일은 색상 교체가 아니라 **Jetson 시간 초과 복구 → Android 연결 정리 → 명시적 Direct 전환 → 끊겨도 유지되는 작업 화면**이다. 그다음 단일 연결 조정자와 작업 중심 디자인을 완성하는 방향을 권한다.

---

## 근거 자료

아래 소스는 모두 검토 기준 커밋에 고정되어 있다. 특정 결함의 근거는 본문의 함수명을 기준으로 찾을 수 있다. 대용량 파일 중 연결과 화면 상태에 관련된 구간을 중심으로 검토했으며, 링크 제공이 해당 파일 전체의 무결성을 검증했다는 의미는 아니다.

- [S01] [Android 빌드 설정 — `app/build.gradle.kts`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/build.gradle.kts)
- [S02] [Wi-Fi Direct 플랫폼 처리 — `app/src/main/java/com/example/jetsoncontroller/data/network/WifiDirectManager.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/network/WifiDirectManager.kt)
- [S03] [중앙 연결·기능 Repository — `app/src/main/java/com/example/jetsoncontroller/data/repository/JetsonRepository.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/repository/JetsonRepository.kt)
- [S04] [자동 연결 정책 — `app/src/main/java/com/example/jetsoncontroller/data/repository/AutomaticConnectionPolicy.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/repository/AutomaticConnectionPolicy.kt)
- [S05] [앱 내비게이션과 전역 ViewModel — `app/src/main/java/com/example/jetsoncontroller/ui/JetsonApp.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/JetsonApp.kt)
- [S06] [연결 허브 — `app/src/main/java/com/example/jetsoncontroller/ui/connection/ConnectionHubScreen.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/connection/ConnectionHubScreen.kt)
- [S07] [대시보드 화면 — `app/src/main/java/com/example/jetsoncontroller/ui/dashboard/DashboardScreen.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/dashboard/DashboardScreen.kt)
- [S08] [대시보드 상태 관리 — `app/src/main/java/com/example/jetsoncontroller/ui/dashboard/DashboardViewModel.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/dashboard/DashboardViewModel.kt)
- [S09] [저장소 상태 관리 — `app/src/main/java/com/example/jetsoncontroller/ui/storage/DeviceStorageViewModel.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/storage/DeviceStorageViewModel.kt)
- [S10] [작업 상태 관리 — `app/src/main/java/com/example/jetsoncontroller/ui/pipelines/PipelineViewModel.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/pipelines/PipelineViewModel.kt)
- [S11] [HTTPS·인증 클라이언트 — `app/src/main/java/com/example/jetsoncontroller/data/network/LocalApiClient.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/network/LocalApiClient.kt)
- [S12] [장비 자격증명 저장 — `app/src/main/java/com/example/jetsoncontroller/data/credentials/DeviceCredentialStore.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/credentials/DeviceCredentialStore.kt)
- [S13] [권한 요청 진입점 — `app/src/main/java/com/example/jetsoncontroller/MainActivity.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/MainActivity.kt)
- [S14] [앱 매니페스트 — `app/src/main/AndroidManifest.xml`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/AndroidManifest.xml)
- [S15] [화면 테마 — `app/src/main/java/com/example/jetsoncontroller/ui/theme/Theme.kt`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/theme/Theme.kt)
- [S16] [Jetson Wi-Fi Direct 서비스 — `backend/jetson_control/wifi_direct.py`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/backend/jetson_control/wifi_direct.py)
- [S17] [Jetson API — `backend/jetson_control/api.py`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/backend/jetson_control/api.py)
- [S18] [시스템 상태 수집기 — `backend/jetson_control/status.py`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/backend/jetson_control/status.py)
- [S19] [Wi-Fi Direct 단위 테스트 — `backend/tests/test_wifi_direct.py`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/backend/tests/test_wifi_direct.py)
- [S20] [Wi-Fi Direct 운영 문서 — `docs/WIFI_DIRECT_SETUP.md`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/docs/WIFI_DIRECT_SETUP.md)
- [S21] [업로드 수신 서버 API — `upload_receiver/upload_receiver/app.py`](https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/upload_receiver/upload_receiver/app.py)

- [S22] [검토 기준 커밋의 저장소 루트](https://github.com/dbparkJ/JetsonControllerApp/tree/fe888da910fe7456ca60132d2c9e7c3645a6565a)

### 공식 기술 문서

- [E01] [Python subprocess: 시간 초과 예외와 SubprocessError](https://docs.python.org/3/library/subprocess.html)
- [E02] [Android Wi-Fi Direct: 연결 요청·이벤트·협상 취소](https://developer.android.com/develop/connectivity/wifi/wifip2p)
- [E03] [FastAPI: async 함수 안에서 직접 호출하는 동기 유틸리티 함수](https://fastapi.tiangolo.com/async/)

[S01]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/build.gradle.kts
[S02]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/network/WifiDirectManager.kt
[S03]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/repository/JetsonRepository.kt
[S04]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/repository/AutomaticConnectionPolicy.kt
[S05]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/JetsonApp.kt
[S06]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/connection/ConnectionHubScreen.kt
[S07]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/dashboard/DashboardScreen.kt
[S08]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/dashboard/DashboardViewModel.kt
[S09]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/storage/DeviceStorageViewModel.kt
[S10]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/pipelines/PipelineViewModel.kt
[S11]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/network/LocalApiClient.kt
[S12]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/data/credentials/DeviceCredentialStore.kt
[S13]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/MainActivity.kt
[S14]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/AndroidManifest.xml
[S15]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/app/src/main/java/com/example/jetsoncontroller/ui/theme/Theme.kt
[S16]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/backend/jetson_control/wifi_direct.py
[S17]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/backend/jetson_control/api.py
[S18]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/backend/jetson_control/status.py
[S19]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/backend/tests/test_wifi_direct.py
[S20]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/docs/WIFI_DIRECT_SETUP.md
[S21]: https://github.com/dbparkJ/JetsonControllerApp/blob/fe888da910fe7456ca60132d2c9e7c3645a6565a/upload_receiver/upload_receiver/app.py
[E01]: https://docs.python.org/3/library/subprocess.html
[E02]: https://developer.android.com/develop/connectivity/wifi/wifip2p
[E03]: https://fastapi.tiangolo.com/async/
[S22]: https://github.com/dbparkJ/JetsonControllerApp/tree/fe888da910fe7456ca60132d2c9e7c3645a6565a
