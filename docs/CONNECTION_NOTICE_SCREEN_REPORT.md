# 휴대전화 설치·화면 이동 및 일회성 연결 안내 검증

> 과거 검증 설명입니다. 이 문서가 가리키는 V5/V7 작업 패키지와 실행 지시서는 정리되었으며 Git 이력에서 확인할 수 있습니다. 현재 구현 기준은 앱 코드와 `docs/design/`입니다.

검증 시작 2026-09-11, 후속 안내 수정 2026-09-12. 사용자 지정 Android
`100.94.106.23:43725`의 SM-S908N(Android 16)에서 진행했다.
사용자는 Jetson 장비와 떨어져 있으므로 장비 테스트를 제외하고 **화면 연결성만** 확인하도록 했다.
Jetson 작업 시작·전송·RTK·전원·네트워크 정책 검증은 수행하지 않는다.

## 설치와 첫 화면 검증

- 기존 V5 앱의 홈·작업·데이터·설정과 장비 목록을 설치 전에 캡처했다.
- 검증된 V7 APK를 `adb install -r`로 데이터 유지 업데이트했다.
  기기에 설치된 APK SHA-256은 빌드 산출물과 일치한다.
- 장비 인증 정보 저장 파일 SHA-256은 설치 전후 동일하다. 앱 데이터·등록 장비를 지우지 않았다.
- 기존 UI 테스트 16개가 30.091초에 모두 통과했다. 실제 Android에서 실행한 Compose fixture이며
  정상 장비와 실제 통신한 결과는 아니다. 상세·시작 확인 취소·장비별 선택 복원·입력 포커스·
  테마 전환 중 스크롤 유지·360dp/선형 2배 글꼴 화면을 포함한다.
- 실제 제품 앱에서 라이트/다크 4개 루트와 데이터→장비 파일/서버 대상,
  설정→센서/네트워크/진단/알림 설정의 진입 및 뒤로 가기를 확인했다.
  장비 미연결 화면은 빈 목록·미확인 안내로 표시되며 예시 데이터를 넣지 않았다.
- 진단에서 뒤로 가면 설정의 기존 스크롤 위치로 돌아왔다. 앱 테마는 검증 후 기존 LIGHT로 복원했다.
- 무선 ADB가 한 차례 offline으로 바뀌었다. 같은 대상의 로컬 ADB transport를 다시 연결해 복구했다.
  전후 앱 PID는 24093으로 같았고 이를 앱 또는 Jetson 통신 장애라고 판정하지 않았다.
- 당시 앱 로그와 crash buffer에 FATAL EXCEPTION은 없었다. ashmem deprecation 및
  `libpenguin.so` 로드 실패 메시지는 관찰됐으나 검사한 화면에서 중단은 없었다.
  특정 라이브러리의 원인을 확정하거나 전체 런타임 오류가 없다고 주장하지 않는다.

## 사용자가 발견한 하단 안내 문제

연결 대기 문구가 `ConnectionRecoveryLayout`의 Column 마지막 항목으로 붙어 있어,
홈·작업·데이터·설정 탭 아래에 상시 공간이 생겼다. 사용자가 안내는 일회성으로 표시하고
탭은 항상 가장 아래에 있도록 요청했다.

- 기존 하단 안내와 하단 inset 선점 코드를 제거했다. 각 화면은 전체 높이와 자체 system inset을 유지한다.
- 동일 문구와 연결 문제 해결 행동은 상단 Snackbar로 잠깐 표시한다.
  Material Short 기간을 쓰며 접근성 설정의 권장 표시 시간은 존중한다. 닫기와 문제 해결 행동도 가능하다.
- 장비별로 현재 연결 대기 구간의 표시 여부를 rememberSaveable에 보관한다.
  탭 이동, 연결 안내가 없는 화면을 거친 복귀, 화면 복원, A→B→A에서 같은 안내를 다시 띄우지 않는다.
  정상 연결이 확인되면 해당 장비의 표시 여부를 해제해 다음 연결 손실 때 새 안내를 허용한다.
- 표시 여부는 UI 상태이며 Repository·인증·재연결·명령·전송 정책을 바꾸지 않는다.
  기존 헤더의 오프라인/미확인 설명과 화면별 제어 제한은 유지한다.
- `ConnectionNoticeScreenTest`는 안내 전후 화면·탭 경계 좌표가 같은지, 탭이 가장 아래인지,
  안내 자동 소멸, 화면/장비/복원 간 중복 방지와 정상 연결 후 새 안내를 모의 UI 상태로 검사한다.

## 증거 위치와 범위

`docs/evidence/slate-v7/device-20260911/`에 실제 제품 before/after 원본,
`native-fixtures/` 24장과 `native-focus-dialogs/` 4장을 구분해 보관한다.
`verification.json`은 첫 V7 설치와 16개 검사 결과이며, 후속 안내 수정은 별도 결과로 기록한다.

OS font_scale 1.1, density override 560, 1440×3088 환경에서 제품 화면을 확인했다.
OS 최대 글꼴·환경광·디스플레이 보정·장시간 사용 및 실제 Jetson 장치 상태는 이번 범위에서 검사하지 않았다.
Figma 생성·export는 수행하지 않았다. 앱 연결 안내가 사라지는 것은 장비 연결 성공을 뜻하지 않는다.
원래 V7 지시 MD의 넓은 수용 조건을 모두 수행했다고 간주하지 않고 해당 파일은 유지한다.

## 수정본 최종 검증 — 2026-09-12

- Debug 앱·계측 APK 빌드 성공. JVM 196개 통과(실패/오류/건너뜀 0), Lint 오류 0·경고 73.
- 같은 휴대전화에 데이터 유지 재설치 성공. 설치 APK와 빌드 SHA-256 일치,
  인증 저장 파일 SHA-256도 최초 설치 전과 동일하다.
- 기존 16개와 신규 연결 안내 2개를 합한 네이티브 UI 테스트 **18개가 35.955초에 통과**했다.
  신규 테스트는 표시/소멸 전후 탭 위치, 일회성 표시, 화면 복원·장비 전환, 콜백을 검증한다.
- 실제 제품에서 첫 상단 안내를 캡처한 뒤 홈→작업→데이터→설정을 이동했다.
  네 화면 모두 안내가 반복되지 않고 탭 라벨의 물리 좌표가 y=2805..2867로 동일했다.
  기존 하단 안내 때의 y=2343..2405보다 462px 내려가, Android 시스템 내비게이션 위의 앱 하단에 유지된다.
- 최종 실행 PID 4380의 관찰 로그와 crash buffer에 FATAL EXCEPTION은 없었다.
  최종 화면은 홈, 테마는 기존 LIGHT로 두었다. 네트워크 설정·장비 상태는 조작하지 않았다.
- 소스 토큰 66개·불투명 역할 대비 94쌍 통과. 이는 렌더링 픽셀이나 Figma 검사가 아니다.
- 이번 추가 변경은 Compose 표시 상태와 테스트·기록에 한정한다. 이전 통합된 backend 변경을 보존하며
  추가 서버 API나 통신 동작은 필요하지 않아 변경하지 않았다.

[최종 결과 JSON](evidence/slate-v7/device-20260911/notice-verification.json),
[빌드·단위 검사](evidence/slate-v7/device-20260911/notice-build-verification.json),
[실제 설치·인증 보존](evidence/slate-v7/device-20260911/notice-install-verification.json),
[18개 계측 검사 원본](evidence/slate-v7/device-20260911/native-ui-tests-notice.txt),
[전체 캡처 분류·해시](evidence/slate-v7/device-20260911/capture-manifest.json).

실제 앱 변경 전후:
[기존 하단 안내](evidence/slate-v7/device-20260911/after/before-notice-fix.png),
[첫 상단 안내](evidence/slate-v7/device-20260911/notice-final/first-notice.png),
[최종 홈](evidence/slate-v7/device-20260911/notice-final/home-light.png),
[작업](evidence/slate-v7/device-20260911/notice-final/tasks-light.png),
[데이터](evidence/slate-v7/device-20260911/notice-final/data-light.png),
[설정](evidence/slate-v7/device-20260911/notice-final/settings-light.png).
