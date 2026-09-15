# AG02 UX/UI 실행 보고서

## 구현 결과

- 홈 화면을 장비 연결, 수집 상태, 인터넷·서버, GNSS·RTK의 독립 상태와 다음 조치 중심으로 재구성했다. 전화·장비 연결이나 장비 Wi-Fi를 인터넷 연결로 오인하지 않으며, 최신 관찰과 `activeRunId`가 함께 있을 때만 수집 실행 중으로 표시한다. 화면 폭에 따라 상태 카드와 현재 작업 영역이 휴대전화 1열, 중간 폭 2열, 태블릿 4열·2단 구성으로 바뀐다.
- 일반 작업자 설정과 관리자 도구를 분리하고 네트워크, 장비 대상, 진단, 팬·시스템, 전원, 등록 기능을 관리자 화면에 유지했다. 이 구분은 앱 안의 표시 범위일 뿐 서버 권한 부여나 인증이 아니라는 설명을 넣었다. 기존 장비 저장소, 업로드, 실행 이력, 카메라, GNSS 지도와 파이프라인 기능은 계속 접근할 수 있다.
- Jetson 등록이나 IP 없이 사용할 수 있는 수신 서버 직접 연결 화면을 연결 허브, 데이터, 설정에서 열 수 있게 했다. 프로필은 HTTPS 주소, 환경, 직원, 프로젝트로 범위를 고정하고 Android Keystore 기반 저장소의 인증 토큰을 사용한다. 네트워크 결과와 이전에 인증된 cache 결과, 마지막 성공 갱신 시각을 구분한다. 401/403/409, 인증서, 환경·직원·프로젝트 불일치에는 cache를 노출하지 않고 범위 데이터를 지운다.
- 서버 작업 pagination, 파일 탐색, downsample 이미지, 제한된 임시 파일 video, text preview, receipt, 휴지통, 복원과 Undo를 연결했다. VIEWER는 읽기만 가능하다. mutation 결과가 `UNKNOWN`이면 성공·실패를 단정하거나 자동 재시도하지 않고 목록·휴지통 확인을 안내한다. 프로필 전환, 로그아웃, 화면 범위 전환은 진행 중 요청을 취소하고 generation과 선택 ID로 늦은 응답을 차단한다. 복원 후 데이터와 휴지통을 모두 갱신한다.
- 장비 저장소에서 선택한 실제 파일·폴더 삭제는 복구할 수 없음을 확인 창에 명시했다. 실행 이력 삭제는 별도 동작이며 수집 원본을 유지한다. 수신 서버는 backend가 지원하는 soft delete와 복원을 사용한다.
- 파이프라인 화면은 `RUNNING`과 non-blank `activeRunId`가 최신 관찰에 함께 있을 때만 실행을 확정한다. 시작·중지 전이, stale 상태와 실행 ID 누락을 확인 필요 상태로 표시하고 실행·로그 ID, 시작·종료 시각, 종료 코드와 저장 사전점검 근거를 노출한다. `storagePreflight=passed`만 통과로 표시하며 `not_configured`는 기준 미설정이다. 자동 시작 설명과 기본 선택은 꺼짐이다.
- 작업 이력과 GNSS 지도에 nullable 품질 근거를 연결했다. RTK FIX 비율, 정확한 RTK 관찰 분모, FIX 유지 시간, RTK·기록 미확인 시간을 분리하고 통과·실패 판정을 만들지 않는다. `UNKNOWN`, `NO_SAMPLES`, `INSUFFICIENT_TIMING`을 서로 다르게 표시한다. 필수·선택·미지정 문제 구간과 전체 시각을 볼 수 있으며, 유효한 route index와 좌표가 있는 문제만 지도 marker로 표시한다. marker는 50개로 제한하고 전체 관찰 목록 접근을 유지한다.
- 서버 전용 화면을 보는 중 백그라운드 Jetson 등록 실패가 온보딩으로 강제 이동시키지 않도록 navigation 범위를 제한했다.

## 통합 계약

- 제품 계약 문서 `8633f4b`, AG05 직접 서버 저장소 `00bfa15`, AG03/AG04 Android runtime 근거 adapter `e1d9a8e`, AG06 field quality `6827e1c`를 branch merge로 통합했다.
- data/model/backend 계약은 각 담당 commit을 그대로 사용했고 AG02 변경은 `ui/**`, UI JVM test와 이 보고서에 한정했다.

## 검증

다음 Android 검증이 통과했다.

```text
./gradlew :app:testDebugUnitTest --tests '*OperationalSummaryTest' --tests '*DirectServerPresentationTest' --max-workers=1 --console=plain :app:assembleDebug :app:lintDebug
BUILD SUCCESSFUL in 2m 26s

./gradlew :app:testDebugUnitTest --tests '*QualityPresentationTest' --tests '*TaskPresentationTest' --tests '*OperationalSummaryTest' --tests '*DirectServerPresentationTest' --tests '*NavigationWorkflowTest' --max-workers=1 --console=plain
BUILD SUCCESSFUL in 23s
```

두 번째 실행 뒤 적용한 전체 문제 시각 접근, 실행 이력 freshness, direct-server local I/O exception 처리의 작은 source 보정은 root 최종 통합 compile/test 대상이다. `git diff --check`가 통과했다. 색상 token 검사는 다음 결과로 통과했다.

```text
python3 scripts/check_slate_harmony.py
66 native tokens, 94 role pairs, 0 failures
```

## 장치 검증 공백

Android 기기나 emulator에서 screenshot, 화면 회전·태블릿 실측, 영상 재생, Keystore 재시작 복원, 실제 수신 서버의 권한·offline cache·pagination·휴지통 작업을 실행하지 않았다. Jetson 배포, service restart, 실제 카메라/GNSS/RTK 수집도 수행하지 않았다. main 또는 통합 branch에는 병합하지 않았다.
