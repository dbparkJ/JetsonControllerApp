# GEO-X Android UI 인벤토리

조사 범위: `app/src/main/java/com/example/jetsoncontroller/ui/` 전체, `MainActivity.kt`, `JetsonApp.kt`. `JetsonApp`의 `NavHost`에는 route 등록 41개가 있고 `navigation()` 중첩 그래프는 없습니다. 탭 root는 장치·작업·전송·설정입니다. 아래 합계는 표의 항목 수이며, 같은 화면의 별도 상태와 modal surface는 각각 셉니다.

안전도: **safe** 열기만 하면 UI 표시. **careful** 열기는 안전하지만 버튼/확정 동작은 장치·서버·데이터·설정을 바꾸므로 조작하지 말고 닫기. **unsafe** 진입 즉시 검색·스트림·위치 추적·자동 읽음 같은 동작이 시작됨. `취소/복귀`에는 각 항목의 빠져나오는 방법을 적었습니다.

진입 경로는 앱 cold start 기준입니다. “현재 앱 내 경로 없음”은 route만 등록되어 있고 코드에서 navigate 호출을 찾지 못해 일반 tester가 폰 UI만으로 열 수 없는 항목입니다. `PIPELINES`는 등록된 작업이 없거나 선택이 모호할 때 쓰는 작업 선택 화면이며, 개발자 전용 경로와 구별했습니다.

## 그룹별 합계

| 그룹 | 항목 수 |
|---|---:|
| 장치 | 8 |
| 작업 | 25 |
| 전송 | 32 |
| 설정 | 21 |
| 개발자 도구 | 24 |
| 등록/연결 | 19 |
| 알림 | 3 |
| 공통 | 6 |
| **총계** | **138** |

## 장치 (8)

| id | 화면/모달 | 종류 | file:line | 들어가는 길 | 전제 조건 | 안전 |
|---|---|---|---|---|---|---|
| device-tab | 장치 · 등록 장치 홈 | 화면·하단 탭 | `JetsonApp.kt:1091` / `DevicesHomeScreen.kt:83` | 앱 cold start → 장치 탭(기본 화면) | 없음. 저장 장치가 있으면 선택 장치와 최근 상태 표시 | safe — 홈/다른 탭으로 이동 가능 |
| device-first-run | QR 코드로 장치 등록 | 화면 상태 | `DevicesHomeScreen.kt:164` / `:265` | cold start → 장치 탭의 첫 실행 카드 | 등록 장치 0대 | careful — “QR 코드로 장치 등록”은 등록 흐름 진입. Android 뒤로로 홈 복귀 |
| device-offline | 연결 끊김/마지막 응답 장치 카드 | 화면 상태 | `DevicesHomeScreen.kt:120` / `:305` | 장치 탭 → 저장 장치 카드 | 등록 장치가 오프라인이거나 마지막 snapshot만 있음 | careful — “다시 연결/연결 방법”은 연결 시도. 홈 탭 유지 또는 뒤로 |
| device-detail | 장치 상세 | 화면 | `JetsonApp.kt:1161` / `DeviceDetailScreen.kt:68` | 장치 탭 → 현재 장치 카드 탭 | 등록 장치 1대 이상; 온라인이면 메뉴·제어 항목 추가 표시 | careful — 재부팅/전원/연결 변경 버튼을 누르지 말고 뒤로 |
| device-detail-offline | 오프라인 장치 상세·마지막 상태 | 화면 상태 | `DeviceDetailScreen.kt:68, 226` | 장치 탭 → 오프라인 장치 카드 → 상세 | 장치가 오프라인이고 snapshot 또는 미확인 상태 | safe — 표시 전용 상태, 뒤로 |
| device-connect-method | “장치 연결 방법” | ModalBottomSheet | `DevicesHomeScreen.kt:224` / `ConnectMethodSheet.kt:43` | 장치 탭 또는 상세 → 오프라인 카드의 “연결 방법” | 등록 장치 있음 | careful — 같은 네트워크/직접 연결/QR 재등록은 연결을 변경. 바깥 탭·아래로 스와이프·뒤로 |
| device-power-confirm | “장치 재부팅” / “장치 전원 종료” | AlertDialog | `DeviceDetailScreen.kt:112` | 장치 탭 → 상세 → 위험 동작의 재부팅 또는 전원 종료 | 온라인, 진행 중 수집 없음, 전원 명령 capability일 때 확정 버튼 활성 | careful — “취소” 또는 뒤로. 확인 버튼은 Jetson 재부팅/종료 |
| device-menu | “연결 끊기” | DropdownMenu | `DeviceDetailScreen.kt:166` | 장치 탭 → 상세 → 우측 상단 ⋮ | 장치 온라인 | careful — 메뉴만 보고 바깥 탭/뒤로. “연결 끊기” 선택 금지 |

## 작업 (25)

| id | 화면/모달 | 종류 | file:line | 들어가는 길 | 전제 조건 | 안전 |
|---|---|---|---|---|---|---|
| tasks-tab | 작업 | 화면·하단 탭 | `JetsonApp.kt:1538` / `TasksHomeScreen.kt:54` | cold start → 하단 “작업” | 없음; 실행 중 수집·최근 이력·티켓은 상태에 따라 표시 | careful — 수집 시작/티켓 선택은 진행 상태를 바꿀 수 있음. 장치 탭으로 이동 |
| task-home-empty | 아직 고른 구간이 없습니다 / 완료한 수집이 없습니다 / 등록된 다른 작업이 없습니다 | 화면 상태 | `TasksHomeScreen.kt:88` / `TasksHomeUiState.kt:94` | 작업 탭 → 구간·최근 수집·다른 작업 섹션 확인 | 선택 구간/최근 run/추가 pipeline 데이터에 따라 각 empty copy 표시 | safe — 보기만 하고 다른 탭으로 이동 |
| task-pipeline-list | 수집 작업 선택 | 화면 (`pipelines`) | `JetsonApp.kt:1736` / `PipelineScreens.kt:98` | 작업 탭 → “새 수집 준비”; 작업이 없거나 여러 개라 선택이 필요할 때 표시 | 장치가 등록되어야 유용. 온라인 연결이면 최신 작업, 아니면 offline 상태 | careful — 목록의 시작/중지/등록 해제 조작 금지. 뒤로 |
| task-list-empty | 등록된 작업 없음 / 작업 목록을 불러오지 못함 | 화면 상태 | `PipelineScreens.kt:181` | 작업 탭 → 새 수집 준비 → 수집 작업 선택 화면 | 작업이 0개; 연결·목록 freshness에 따라 문구 달라짐 | safe — retry/추가 버튼을 누르지 않고 뒤로 |
| task-detail | 작업 상세 | 화면 (`task_detail/{pipelineId}`) | `JetsonApp.kt:1658` / `TaskDetailScreen.kt:39` | 작업 탭 → 표시된 작업 항목 → 작업 상세 | 등록된 pipeline이 있고 목록에서 선택 가능 | careful — 개발자 로그/설정 링크는 조회 전용 진입, 화면 안 저장 동작은 피하고 뒤로 |
| task-detail-missing | 작업 정보를 찾을 수 없습니다 | 대체 화면 상태 | `JetsonApp.kt:1664` | 정상 UI 경로 없음; 이미 연 작업이 삭제되었거나 잘못된 pipelineId route를 열 때 | stale/존재하지 않는 route argument | safe — “작업으로 돌아가기” 또는 뒤로 |
| ticket-detail | 수집 구간 | 화면 (`ticket_detail/{rowId}`) | `JetsonApp.kt:1613` / `TicketDetailScreen.kt:46` | 작업 탭 → 서버 티켓 목록 → 티켓 행 탭 | Direct Server profile 선택·티켓 로드 필요; 권한에 따라 시작 가능 여부 상이 | careful — “이 구간으로 수집 시작”은 누르지 말고 뒤로 |
| ticket-detail-missing | 수집 구간을 찾을 수 없습니다 | 대체 화면 상태 | `JetsonApp.kt:1617` | 정상 UI 경로 없음; 티켓 목록에서 상세를 연 뒤 티켓이 사라졌을 때만 | stale rowId 또는 목록 갱신 중 | safe — “작업으로 돌아가기” 또는 뒤로 |
| ticket-start-confirm | 수집 시작 | AlertDialog | `TicketDetailScreen.kt:55` | 작업 탭 → 서버 티켓 선택 → “이 구간으로 수집 시작” | 시작 권한이 있는 ticket; profile/device/pipeline 연결 필요 | careful — “취소” 또는 뒤로. “시작”은 ticket 상태 및 수집 준비 변경 |
| survey-prepare | 수집 준비 · “수집할 구간을 선택하세요” | 화면 (`survey_run/{pipelineId}`) | `JetsonApp.kt:1791` / `SurveyRunScreen.kt:69` | 작업 탭 → 수집 준비/작업 선택 → pipeline 선택 | 장치 및 수집 작업 등록; 서버 프로젝트·저장 정책은 로딩/권한에 따라 다름 | careful — 프로젝트/구간 변경은 초안을 바꿈. 뒤로 |
| survey-start-confirm | 수집을 시작할까요? | AlertDialog | `SurveyRunScreen.kt:279` | 수집 준비 → 점검 완료 후 “수집 시작” | project/section 및 preflight 등 `canStart` 조건 만족 | careful — “취소”. “수집 시작”은 장치에서 실제 수집 시작 |
| survey-project-menu | 프로젝트 선택 / 새 프로젝트 만들기 | ExposedDropdownMenu | `SurveyRunScreen.kt:351` | 수집 준비 → 프로젝트 필드 탭 | context 잠금/작업 중이 아니어야 함 | careful — 메뉴 열기는 안전. 기존 선택은 초안 변경, “새 프로젝트 만들기”는 폼만 엶. 바깥 탭/뒤로 |
| active-run | 수집 중 / 시작 확인 중 / 수집 종료 / 수집 상태 | 화면 (`active_run`) | `JetsonApp.kt:1865` / `ActiveRunScreen.kt:43` | 이미 수집 중이면 작업 탭의 진행 카드 또는 장치 탭 → “진행 보기” | 진행 중·대기 중인 기존 run; 안전한 열람에는 이미 존재하는 run 사용 | careful — 종료·재시도·새로고침 조작 없이 뒤로. 화면 진입 자체는 collection을 시작하지 않음 |
| active-run-variants | 시작 미확인·오프라인·활성·종료 상태 | 화면 상태 | `ActiveRunScreen.kt:121, 217` | active_run 화면에서 현재 run 상태에 따라 자동 표시 | 시작 응답 대기, 연결 유무, 실제 active/ended 상태 | careful — “결과 보기”는 읽기 화면, stop 버튼은 누르지 말고 뒤로 |
| active-run-stop-confirm | 수집을 종료할까요? | AlertDialog | `ActiveRunScreen.kt:129` | 활성 수집 화면 → “수집 종료” | run active, 온라인, 일치하는 stop target, 중지 작업 없음 | careful — “계속 수집”/뒤로. “수집 종료”는 장치 작업 중지 |
| active-run-menu | 장치 선택 / 알림 / 수집 상태 새로고침 | DropdownMenu | `ActiveRunScreen.kt:186` | active_run → 우측 상단 ⋮ | active_run 화면 | safe — 메뉴만 확인하고 바깥 탭/뒤로 |
| run-result | 수집 결과 | 화면 (`run_result`) | `JetsonApp.kt:1934` / `RunResultScreen.kt:32` | 기존 수집 종료 후 active_run → “결과 보기” | 완료 run 및 저장 결과 필요. 화면 진입 시 결과 확인 상태를 자동 acknowledge함 | unsafe — 들어오자마자 로컬 결과 확인 상태를 기록함. 테스트 뒤 바로 뒤로 |
| run-result-missing | 수집 결과를 찾을 수 없는 상태 | 대체/빈 상태 | `RunResultScreen.kt:32, 132` | 기존 완료 run이 없는 상태에서 결과 route를 복원하거나 run 상태가 사라질 때 | 유효한 latest run 없음 | safe — 뒤로/작업 이력으로 복귀 |
| run-result-ticket-report | 구간 완료 보고 | AlertDialog | `RunResultScreen.kt:227` | 결과 화면 → “구간 완료 보고” | ticket 연결, 완료 보고 권한, 서버 수신 확인 등 조건 충족 | careful — “취소” 또는 뒤로. “보고”는 서버 ticket 업데이트 |
| run-result-menu | 장치 선택 / 알림 / 결과 새로고침 | DropdownMenu | `RunResultScreen.kt:85` | 결과 화면 → 우측 상단 ⋮ | 결과 화면 | safe — 메뉴만 열고 바깥 탭/뒤로 |
| task-history | 수집 이력 | 화면 (`task_history`) | `JetsonApp.kt:2020` / `GeoRunDashboard.kt:35` | 작업 탭 → “수집 이력” 또는 완료된 run 카드의 이력 보기 | 이력은 장치 로컬/서버 상태에 따라 비거나 offline 표시 | careful — 이력 삭제는 스와이프/삭제 동작으로 바로 바뀔 수 있음. 손대지 않고 뒤로 |
| history-empty | 수집 이력이 없습니다 | 화면 상태 | `GeoRunDashboard.kt:190` | 작업 탭 → 수집 이력 | 필터 결과가 비었고 이력 로딩이 끝난 상태 | safe — 뒤로 |
| history-log | 저장된 로그 · 최근 64KB | AlertDialog | `GeoRunDashboard.kt:60` | 개발자 모드 → 수집 이력 → 로그 보기 | developer mode on, 로그가 열려 있음 | safe — “닫기” 또는 뒤로 |
| quality-detail-sheet | 위치 품질 상세 | ModalBottomSheet | `QualityPresentation.kt:197` | 작업 → 수집 이력/결과의 위치 품질 카드 → “품질 상세 보기” | RunQuality 데이터가 있는 run | safe — 아래로 스와이프/뒤로. GNSS 맵 상세 안에서도 재사용 |
| history-undo | “실행 취소” 삭제 복구 메시지 | Snackbar action | `GeoRunDashboard.kt:64` / `OperationMessageHost.kt:22` | 수집 이력 → 기존 완료 run을 삭제/휴지통 이동한 직후 | 삭제 가능한 기존 이력; 먼저 데이터 변경이 일어나므로 실제 운영 데이터로 재현하지 말 것 | careful — snackbar의 “실행 취소”는 복구 동작. 보지 않으려면 이력을 삭제하지 말고, 이미 떴으면 뒤로/시간 만료 |

## 전송 (32)

| id | 화면/모달 | 종류 | file:line | 들어가는 길 | 전제 조건 | 안전 |
|---|---|---|---|---|---|---|
| transfers-tab | 전송 | 화면·하단 탭 | `JetsonApp.kt:1465` / `TransfersHomeScreen.kt:51` | cold start → 하단 “전송” | 없음; 활성·대기·실패 전송은 데이터에 따라 표시 | careful — 파일 선택/재시도/취소는 동작을 일으킴. 다른 탭으로 이동 |
| transfer-cancel-confirm | 전송을 취소할까요? | AlertDialog | `TransfersHomeScreen.kt:60` | 전송 탭 → 진행 중 카드 → “전송 취소” | active transfer 1건 이상 | careful — “계속 보내기”/뒤로. “전송 취소”는 현재 업로드를 중단 |
| data-hub | 파일 | 화면 (`data_hub`) | `JetsonApp.kt:2224` / `DataHubScreen.kt:84` | 전송 탭 → “보낼 파일 고르기”; 또는 장치 상세 → 파일 | 파일 탐색은 장치 연결 필요; offline이면 연결 안내와 마지막 상태 | careful — 삭제/전송은 누르지 말고 뒤로 |
| data-empty-offline | 파일 없음 / 검색 결과 없음 / 장치 연결 필요 / 불러오기 오류 | 화면 상태 | `DataHubScreen.kt:455` | 전송 탭 → 보낼 파일 고르기 | 비어 있는 장치 저장소, 검색어 불일치, offline 또는 API 오류 중 하나 | safe — 새로고침/재연결 버튼을 누르지 않고 뒤로 |
| data-delete-confirm | 장치 데이터를 휴지통으로 옮길까요? | AlertDialog | `DataHubScreen.kt:146` | 파일 → 항목 선택 또는 행 삭제 → “휴지통으로 이동” | 연결 가능하고 파일 항목이 존재해야 confirm 활성 | careful — “취소”/뒤로. 확인은 장치 파일을 휴지통으로 이동 |
| data-menu | 업로드 목록 / 새로고침 / 휴지통 / 알림 / 선택 항목 이동 | DropdownMenu | `DataHubScreen.kt:203` | 파일 화면 → 상단 ⋮ | 파일 화면; 항목 관련 메뉴는 선택 항목이 있을 때 | careful — 메뉴만 보고 바깥 탭/뒤로. 이동 메뉴는 삭제 확인을 엶 |
| data-undo | “실행 취소” 장치 파일 복구 | Snackbar action | `DataHubScreen.kt:174` / `OperationMessageHost.kt:22` | 파일 → 항목 휴지통 이동을 확정한 직후 | 삭제/이동 작업이 먼저 필요; 전용 테스트 자료에서만 관찰 | careful — “실행 취소”를 누르면 장치 데이터 복구. 화면 밖 탭/만료로 닫기 |
| device-storage-route | 장치 저장소/파일 미리보기 | 화면 (`storage?rootId={rootId}&path={path}`) | `JetsonApp.kt:1264` / `DeviceStorageScreen.kt:48` | 장치 상세 → 파일·저장 경로 또는 작업 결과 → 저장 폴더 | 장치 연결 및 유효 root/path; 사진/텍스트는 미리보기 | careful — 파일 삭제/전송은 하지 말고 상위 폴더/뒤로 |
| device-file-preview-empty | 미리볼 수 없는 파일입니다 | 화면 상태 | `DeviceStorageScreen.kt:86` | 저장소 → 앱 미리보기를 지원하지 않는 파일 탭 | 파일 다운로드 가능하지만 이미지/UTF-8 텍스트가 아님 | safe — 뒤로 |
| server-data | 서버 데이터 | 화면 (`server_storage`) | `JetsonApp.kt:1317` / `DirectServerScreen.kt:50` | 설정 탭 → 서버 연결 또는 전송 탭 → 서버 파일 | 프로필이 있으면 직원 인증/프로젝트 권한으로 연결; 없으면 빈 안내 | careful — 조회는 안전, 휴지통·프로필 편집/삭제는 누르지 말고 뒤로 |
| server-no-profile | 서버 연결 정보가 없습니다 / 서버 연결 확인 필요 | 화면 상태 | `DirectServerScreen.kt:285` | 서버 데이터 → 작업 탭(서버 결과) | 서버 profile 없음 또는 서버 연결 전 | safe — “서버 연결”을 누르지 않고 뒤로 |
| server-job-trash-confirm | 서버 작업을 휴지통으로 이동할까요? | AlertDialog | `DirectServerScreen.kt:76` | 서버 데이터 → 작업 목록의 허용된 작업 → 휴지통 동작 | 선택 profile 및 삭제 가능한 role, job 선택 | careful — “취소”/뒤로. 확인은 서버 작업을 이동 |
| server-empty-trash-confirm | 서버 휴지통을 비울까요? | AlertDialog | `DirectServerScreen.kt:88` | 서버 데이터 → 휴지통 탭 → 휴지통 비우기 | profile, 표시된 휴지통 항목, 권한 필요 | careful — “취소”/뒤로. “영구 삭제” 금지 |
| server-remove-profile-confirm | 서버 프로필을 삭제할까요? | AlertDialog | `DirectServerScreen.kt:123` | 서버 데이터 → 프로필 탭 → 프로필 삭제 | 로컬에 저장된 프로필 존재 | careful — “취소”/뒤로. 로컬 주소·토큰·캐시 삭제 |
| server-receipt | 서버 수신 영수증 | AlertDialog | `DirectServerScreen.kt:660` | 서버 데이터 → 작업 선택 → 영수증/수신 확인 | 업로드 완료 세션 및 receipt 있음 | safe — “닫기”/뒤로 |
| server-detail-empty | 수신 결과를 선택하세요 / 표시할 파일이 없습니다 / 앱에서 표시할 수 없는 파일 / 휴지통이 비어 있습니다 | 화면 상태 묶음 | `DirectServerScreen.kt:408, 454, 466, 531` | 서버 데이터 → 작업/폴더/휴지통에서 선택 없이 열거나 비어 있는 폴더·휴지통을 표시 | profile과 section은 있으나 선택 job/files/trash 내용에 따라 분기 | safe — 새로고침/복원/휴지통 동작 없이 뒤로 |
| server-undo | “실행 취소” 서버 휴지통 작업 복구 | action banner | `DirectServerScreen.kt:186` | 서버 데이터 → 작업을 휴지통으로 이동한 뒤 표시 | 서버 항목 이동이 선행되어야 함 | careful — “실행 취소”는 서버 복구. 누르지 말고 뒤로/메시지 닫기 |
| server-proxy-storage | Jetson 경유 서버 파일 | 화면 (`server_proxy_storage`) | `JetsonApp.kt:1352` / `ServerStorageScreen.kt:64` | 개발자 도구 → 서버·전송 설정 → Jetson 경유 서버 조회 | developer mode on, 장치 온라인, proxy capability 및 server target/session | careful — 화면 진입 시 서버 목록 조회(read). 삭제 기능은 route에서 `deletionEnabled=false`; 뒤로 |
| proxy-empty | 서버 저장소 대상/세션 없음 또는 빈 폴더 | 화면 상태 | `ServerStorageScreen.kt:211, 380` | 서버 proxy 화면 → target/session/폴더 상태 확인 | 개발자 모드, 서버 target/session 또는 데이터에 따라 상태 | safe — 뒤로 |
| proxy-delete-confirm | 서버 데이터를 삭제할까요? | AlertDialog | `ServerStorageScreen.kt:80` | 현재 route 설정에서는 앱 내 도달 불가: `deletionEnabled=false`; 활성화된 빌드에서 선택 세션 삭제 | 현재 production NavHost 설정에서 delete action 비활성 | careful — 열 수 있는 변형에서는 “취소”/뒤로. “삭제”는 서버 파일 영구 삭제 |
| proxy-target-menu | 서버 선택 | DropdownMenu | `ServerStorageScreen.kt:276` | Jetson proxy 저장소 → target 선택 버튼 | 개발자 모드, target 1개 이상 | safe — 메뉴만 열고 바깥 탭/뒤로 |
| upload-confirm | 전송 확인 · 보낼 자료를 확인하세요 | 화면 (`upload_confirm/{rootId}`) | `JetsonApp.kt:1379` / `UploadConfirmScreen.kt:31` | 파일 → 파일/폴더 선택 후 전송; 또는 결과/이력 → 업로드 준비 | 선택 장치, LAN/Wi‑Fi Direct 업로드 연결, upload capability, 유효 source 및 target 필요 | careful — “전송 시작” 누르지 말고 뒤로. 이 화면 진입은 source 요약 조회만 함 |
| upload-no-target | 선택할 서버가 없습니다 | 화면 상태 | `UploadConfirmScreen.kt:178` | 전송 확인 → target이 비어 있는 상태 | 대상 서버 미설정 또는 아직 로딩 중 | safe — 개발자 모드 서버 관리 링크를 누르지 않고 뒤로 |
| upload-progress | 전송 / 전송 결과 | 화면 (`upload_progress`) | `JetsonApp.kt:1436` / `UploadProgressScreen.kt:58` | 전송 탭의 기존 전송 카드 → 상세; 또는 기존 작업 큐 항목 선택 | 기존 upload job이 있어야 안전하게 진입 가능. 이 audit 목적의 신규 upload 시작은 피할 것 | careful — 화면은 기존 진행 상태 표시. 취소/원본 삭제/재시도/검증 버튼은 조작하지 말고 뒤로 |
| upload-cancel-confirm | 업로드를 취소할까요? | AlertDialog | `UploadProgressScreen.kt:87` | 기존 활성 전송 상세 → “업로드 취소” | 실제 진행 중 job 필요 | careful — “계속 업로드”/뒤로. “업로드 취소”는 서버 전송 중단 |
| upload-source-delete-confirm | 장치의 원본을 휴지통으로 옮길까요? | AlertDialog | `UploadProgressScreen.kt:104` | 완료·검증된 전송 상세 → 장치 원본 정리 | 완료 상태, 일치한 서버 검증, 삭제 허용, 장치 연결 | careful — “취소”/뒤로. 원본 이동은 데이터 변경 |
| upload-queue | 업로드 확인 · 업로드 이력 | 화면 (`upload_queue`) | `JetsonApp.kt:1690` / `UploadQueueScreen.kt:51` | 파일 화면 ⋮ → 업로드 목록; 전송 이력 진입점 | 장치 선택, 기존 큐/오류/완료 상태는 선택 | careful — 기록 삭제/대상 관리 조작 없이 뒤로 |
| upload-queue-empty | 업로드 기록이 없습니다 | 화면 상태 | `UploadQueueScreen.kt:125` | 업로드 목록 → queue가 비어 있음 | 기록 없음; 로딩/오류 문구는 별도 inline 상태 | safe — 새로고침/대상 관리 없이 뒤로 |
| upload-record-delete-confirm | 업로드 기록을 삭제할까요? | AlertDialog | `UploadQueueScreen.kt:70` | 업로드 목록 → 완료/실패/취소 기록의 삭제 아이콘 | 삭제 가능한 과거 기록 선택 | careful — “취소”/뒤로. 장치 원본/서버 파일은 보존되지만 앱 이력 삭제 |
| local-trash | 휴지통 | 화면 (`local_trash`) | `JetsonApp.kt:2251` / `LocalTrashScreen.kt:35` | 파일 메뉴 → 휴지통; 또는 작업 이력 → 휴지통 아이콘 | 장치 연결 시 항목/지원 여부를 불러옴; 오프라인 안내도 표시 | careful — 복원/비우기는 데이터 변경. 뒤로 |
| local-trash-empty | 휴지통이 비어 있습니다 | 화면 상태 | `LocalTrashScreen.kt:145` | 휴지통 → 온라인 상태에서 로드 완료, 항목 0개 | 장치 online 및 load 완료 | safe — 뒤로 |
| local-trash-purge-confirm | 휴지통을 완전히 비울까요? | AlertDialog | `LocalTrashScreen.kt:44` | 휴지통 → “휴지통 비우기” | 온라인, 비우기 지원, 항목 1개 이상 | careful — “취소”/뒤로. “영구 삭제”는 복구 불가 |

## 설정 (21)

| id | 화면/모달 | 종류 | file:line | 들어가는 길 | 전제 조건 | 안전 |
|---|---|---|---|---|---|---|
| settings-tab | 설정 | 화면·하단 탭 | `JetsonApp.kt:2261` / `SettingsHubScreen.kt:43` | cold start → 하단 “설정” | 없음 | safe — 다른 탭으로 이동 |
| theme-dialog | 화면 테마 | AlertDialog | `SettingsHubScreen.kt:71` | 설정 → 앱 → 화면 테마 | 없음 | careful — 항목 선택은 앱 표시 설정 변경. “취소”/뒤로 |
| about-dialog | 앱 정보 | AlertDialog | `SettingsHubScreen.kt:104` | 설정 → 정보 → 앱 정보 | 없음; 타이틀/내용 7회 탭은 개발자 모드 unlock 동작 | careful — 보기만 한 뒤 “확인”/뒤로; 연속 탭하지 말 것 |
| help-dialog | 현장 수집 도움말 | AlertDialog | `SettingsHubScreen.kt:130` | 설정 → 정보 → 도움말 | 없음 | safe — “도움말 닫기”/뒤로 |
| alert-settings | 알림 설정 | 화면 (`alert_settings`) | `JetsonApp.kt:2326` / `AlertSettingsScreen.kt:53` | 설정 → 알림 기준 | 없음; 장치 상태/작업/업로드 내부 탭 3개 | careful — 스위치/임계값 변경은 로컬 알림 설정 변경. 뒤로 |
| notification-rationale | 알림 권한이 꺼져 있습니다 | 권한 안내 banner | `AlertSettingsScreen.kt:87, 174` | 설정 → 알림 기준; 알림 권한 거부 상태 | Android 13+ 또는 알림 권한이 없는 상태 | careful — “권한 허용”을 누르지 말고 뒤로 |
| notification-prompt | Android 알림 권한 허용 | OS permission dialog | `MainActivity.kt:298` | 위 권한 안내 → “권한 허용” | Android 13+; 권한 미허용 | careful — OS “허용 안 함”/뒤로. 허용은 시스템 권한 변경 |
| network-settings | 네트워크 · 장치 Wi‑Fi | 화면 (`network_settings`) | `JetsonApp.kt:1236` / `NetworkSettingsScreen.kt:101` | 장치 탭 → 상세 → 네트워크 | 저장 장치가 있으면 화면 열 수 있음; Wi‑Fi provisioning은 full-control 연결 필요 | unsafe — 권한이 이미 있으면 진입 effect가 주변 Wi‑Fi AP scan 시작. 뒤로하면 scan 종료 |
| wifi-scan-rationale | 검색 권한 허용 / Wi‑Fi 확인 권한 허용 | 권한 안내 UI | `NetworkSettingsScreen.kt:235, 388` | 네트워크 → 다른 네트워크 → 권한 안내 영역 | 위치 권한 미허용 | careful — 권한 버튼을 누르지 말고 뒤로 |
| wifi-scan-prompt | Android 위치 권한 (Wi‑Fi 검색) | OS permission dialog | `MainActivity.kt:285` | 네트워크 → 권한 안내 → 검색 권한 허용 | 위치 권한 미허용 | careful — 거부/뒤로. 허용하면 OS 권한 변경 및 AP scan 가능 |
| network-menu | 연결 상태 보기 / 숨겨진 네트워크 추가 | DropdownMenu | `NetworkSettingsScreen.kt:188` | 네트워크 화면 → 상단 ⋮ | 네트워크 화면 | safe — 메뉴만 확인, 바깥 탭/뒤로 |
| connection-info | 연결 상태 | AlertDialog | `NetworkSettingsScreen.kt:146` | 네트워크 → ⋮ → 연결 상태 보기 | 연결 상태가 조회된 화면 | safe — “닫기”/뒤로 |
| wifi-apply-confirm | 장치 Wi‑Fi 변경 | AlertDialog | `NetworkSettingsScreen.kt:122` | 네트워크 → SSID/암호 입력 또는 AP 선택 → 이 네트워크에 연결 | Wi‑Fi provisioning 지원, 네트워크 선택·필수 입력 완료 | careful — “취소”/뒤로. “적용”은 장치 네트워크를 바꾸고 현재 연결 끊을 수 있음 |
| sensors-route-unreachable | 센서 상태 | 화면 (`sensors`) | `JetsonApp.kt:2126` / `SensorScreen.kt:46` | 현재 앱 UI에서 진입 경로 없음. `SettingsHubScreen`에 전달된 onSensors callback은 화면에서 호출되지 않음 | route 등록만 확인, phone-only 경로 없음 | safe — 열 수 있다면 뒤로; 정상 tester 경로는 현재 없음 |
| gnss-map | 위치 지도 | 화면 (`gnss_map`) | `JetsonApp.kt:2183` / `GnssMapScreen.kt:146` | 장치 탭 → 장치 상세 → 위치(GNSS); 진행 중 수집/이력 지도도 가능 | 앱 진입 시 모바일 위치 tracker를 시작함; 장치 GNSS/이력은 있으면 함께 표시 | unsafe — 모바일 위치 추적을 자동 시작. 상단 뒤로로 tracking 중단 |
| gnss-details-sheet | 위치 품질 상세 | ModalBottomSheet | `GnssMapScreen.kt:180` | 위치 지도 → 하단 위치 품질/상세 보기 | 맵 상세 표시; 위치 permission 없으면 권한 안내가 함께 보임 | careful — sheet만 보기. 아래로 스와이프/뒤로 |
| gnss-location-rationale | 권한 허용 / 위치 켜기 | 권한·설정 안내 | `GnssMapScreen.kt:203, 429` | 위치 지도 → 위치 품질 상세 → 모바일 위치 action | 위치 권한 미허용 또는 기기 위치 서비스 꺼짐 | careful — “권한 허용/위치 켜기”를 누르지 말고 sheet 닫기 |
| gnss-location-prompt | Android 정밀/대략 위치 권한 | OS permission dialog | `GnssMapScreen.kt:165, 203` | 위치 지도 상세 → “권한 허용” | 위치 권한 미허용 | careful — 거부/뒤로. 허용하면 시스템 권한 변경 및 위치 추적 |
| camera-preview | 카메라 프리뷰 | 화면 (`camera_preview`) | `JetsonApp.kt:2151` / `CameraPreviewScreen.kt:43` | 장치 상세 → 센서 → 카메라; 또는 기존 active run → 카메라 | 장치 online, camera sensor active면 화면 진입과 함께 프레임 stream/poll 시작 | unsafe — 카메라 프레임을 자동 조회. 상단 뒤로로 스트림 중단 |
| camera-preview-states | 카메라 센서 데이터 대기 중 / LIVE / 지연 · 마지막 수신 / 프레임 대기 / 오류 | 화면 상태 | `CameraPreviewScreen.kt:91` | 카메라 프리뷰 route에서 sensor/frame 상태에 따라 표시 | camera 미가동·첫 프레임 대기·기존 프레임·오류 | unsafe — 프리뷰 route 자체가 조건에 맞으면 프레임 조회 시작; 뒤로 |
| camera-capture-options | 캡처 저장 위치 | AlertDialog | `CameraPreviewScreen.kt:56` | 카메라 프리뷰 → 캡처 아이콘 | live 프레임이 있을 때 버튼 활성 | careful — “취소”/뒤로. “캡처”는 장치/모바일 갤러리에 파일 저장 |

## 개발자 도구 (24)

| id | 화면/모달 | 종류 | file:line | 들어가는 길 | 전제 조건 | 안전 |
|---|---|---|---|---|---|---|
| developer-mode-unlock | 개발자 모드 켜기 | 설정 상태/제스처 | `SettingsHubScreen.kt:58, 202` | 설정 → 앱 정보 → 앱 정보 dialog의 제목/본문을 총 7회 탭 | 개발자 모드 off | careful — 테스트 접근을 위해서만 unlock; 토글은 앱 설정 변경. dialog 확인/뒤로 |
| upload-target-settings | 업로드 서버 | 화면 (`upload_servers`) | `JetsonApp.kt:1716` / `UploadTargetSettingsScreen.kt:62` | 개발자 도구 → 서버·전송 설정 → 업로드 서버; 또는 전송 확인 → 서버 관리 | developer mode on; 저장은 full-control 연결 필요 | careful — 추가/편집/삭제는 업로드 서버 설정을 변경. 뒤로 |
| upload-targets-empty | 등록된 서버가 없습니다 | 화면 상태 | `UploadTargetSettingsScreen.kt:181` | 업로드 서버 화면 → target 0개 | 개발자 모드 및 target 미설정 | safe — “새 서버”를 열지 않고 뒤로 |
| upload-target-delete-confirm | 업로드 서버 삭제 | AlertDialog | `UploadTargetSettingsScreen.kt:106` | 업로드 서버 → 기존 편집 가능 target의 삭제 아이콘 | target 존재 및 controlAvailable | careful — “취소”/뒤로. 확인은 서버 target/token 삭제 |
| upload-target-editor | 업로드 서버 추가 / 업로드 서버 수정 | AlertDialog | `UploadTargetSettingsScreen.kt:88, 263` | 업로드 서버 → + 또는 기존 target 편집 아이콘 | 개발자 모드; target 없으면 추가, 있으면 수정 | careful — 열기만 안전. 저장은 주소/label/인증 정보를 변경; 닫기/취소/뒤로 |
| developer-home | 개발자 도구 | 화면 (`developer`) | `JetsonApp.kt:2305` / `DeveloperScreen.kt:59` | 개발자 모드 켜기 → 설정에 생긴 개발자 도구 | 개발자 모드 on | careful — 원격 명령/센서 테스트/진단 저장은 실행하지 말고 뒤로 |
| developer-menu | 원격 터미널 열기/닫기 | DropdownMenu | `DeveloperScreen.kt:122` | 개발자 도구 → 상단 ⋮ | developer mode on | safe — 메뉴만 열고 바깥 탭/뒤로 |
| remote-command-confirm | 원격 명령 실행 | AlertDialog | `DeveloperScreen.kt:87` | 개발자 도구 → 원격 터미널 → 명령어 입력 → 명령 실행 | 장치 online 및 non-empty command | careful — “취소”/뒤로. “실행”은 Jetson에서 명령 실행 |
| admin-tools | 관리자 도구 | 화면 (`admin_tools`) | `JetsonApp.kt:2285` / `SettingsHubScreen.kt:326` | 개발자 도구 → “장치 관리” 또는 “서버·전송 설정” | developer mode on; 일부 항목은 연결·권한 필요 | careful — 팬/전원/서버 프로필 작업을 누르지 말고 뒤로 |
| admin-power-confirm | 장치 재부팅 / 장치 전원 종료 | AlertDialog | `SettingsHubScreen.kt:334` | 관리자 도구 → 위험 동작의 재부팅/전원 종료 | 온라인 및 power capability; 버튼에 따라 동적 제목 | careful — “취소”/뒤로. 확인은 실제 전원 명령 |
| connection-diagnostics | 연결 진단 기록 | 화면 (`connection_diagnostics`) | `JetsonApp.kt:2146` / `ConnectionDiagnosticsScreen.kt:43` | 개발자 도구 → 연결 진단 | developer mode on; 연결 없이도 열림 | careful — 열기는 기록 열람. “지금 끊김 시점 표시/진단 ZIP 저장”은 로컬 로그·파일 변경; 뒤로 |
| legacy-dashboard-unrouted | 장치 대시보드 (legacy composable) | 미등록 화면 | `DashboardScreen.kt:117` | 현재 앱 내 route/호출자 없음; cold start에서 열 수 없음 | 화면 함수만 남아 있음 | safe — UI redesign 대상으로는 source 확인 가능, phone-only 진입 불가 |
| pipeline-detail-dev | 작업 상세 (개발자 관리 route) | 화면 (`pipeline_detail/{pipelineId}`) | `JetsonApp.kt:1736` / `PipelineScreens.kt:98` | 개발자 도구 → 수집 작업 관리 → 작업 상세 | developer mode on, pipeline ID 존재, 장치 연결 시 제어 활성 | careful — 작업 start/stop/remove 조작 금지. 뒤로 |
| pipeline-editor | 수집 작업 등록/편집 | 화면 (`pipeline_editor`) | `JetsonApp.kt:2051` / `PipelineScreens.kt:299` | 개발자 모드 → 수집 작업 관리 → 작업 추가/편집 | developer mode on, 장치 full control 필요 | careful — 저장/등록은 device pipeline 변경. 입력 없이 뒤로 |
| pipeline-picker | 출력 위치 선택 | 화면 (`pipeline_picker`) | `JetsonApp.kt:2066` / `PipelineScreens.kt:447` | 작업 editor → 출력 경로 선택 | developer mode on, editor draft 및 장치 파일 root | careful — 항목 선택은 draft에 반영. 뒤로/상위 폴더 |
| pipeline-logs | 실행 로그 | 화면 (`pipeline_logs/{pipelineId}`) | `JetsonApp.kt:2092` / `PipelineScreens.kt:573` | 개발자 도구 → 수집 작업 관리 → 작업 로그 | developer mode on, 작업 선택; 화면 진입 시 로그 stream 시작 | unsafe — 화면 진입이 read-only 서버 log stream을 시작함. 상단 뒤로로 subscription 중지 |
| pipeline-picker-empty | 출력 위치 선택 목록이 비어 있거나 오류 | 화면 상태 | `PipelineScreens.kt:447` | 작업 editor → 출력 위치 선택 → root/entries가 없는 상태 | 개발자 모드, roots 또는 폴더가 비어 있거나 읽기 오류 | safe — 폴더 선택/새로고침 없이 뒤로 |
| pipeline-logs-empty | 저장된 실행 로그가 없습니다 / 로그 내용이 없습니다 | 화면 상태 | `PipelineScreens.kt:603` | 개발자 도구 → 수집 작업 관리 → 로그 | 선택 pipeline에 저장 로그가 없거나 선택 log content가 비어 있음 | safe — 로그 stream은 화면을 나가면 중지; 뒤로 |
| pipeline-config-empty | 편집할 설정 값이 없습니다 | 화면 상태 | `PipelineScreens.kt:830` | 개발자 도구 → 수집 작업 관리 → 장치 설정 | 불러오기가 끝났고 config field 0개 | safe — 다시 불러오기/저장 없이 뒤로 |
| pipeline-log-menu | 실행 로그 선택 | DropdownMenu | `PipelineScreens.kt:688` | 실행 로그 → 로그 선택 버튼 | 선택 작업에 로그 파일이 있음 | safe — 메뉴만 보고 바깥 탭/뒤로 |
| pipeline-config | 장치 작업 설정 | 화면 (`pipeline_config/{pipelineId}`) | `JetsonApp.kt:2110` / `PipelineScreens.kt:758` | 개발자 도구 → 수집 작업 관리 → 설정 | developer mode on, pipeline ID, 장치 연결 | careful — 저장은 장치 설정 변경. 뒤로 |
| pipeline-remove-confirm | 작업 등록을 해제할까요? | AlertDialog | `PipelineScreens.kt:121` | 개발자 모드 → 수집 작업 관리 → 작업 상세/메뉴 → 등록 해제 | developer mode on, pipeline 선택 및 control connection | careful — “취소”/뒤로. 확인은 실행 서비스 등록 해제 |
| pipeline-reload-confirm | 장치 설정을 다시 불러올까요? | AlertDialog | `PipelineScreens.kt:765` | 장치 작업 설정 → 작성 중 설정 다시 불러오기 | 기존 설정 초안이 있으면 의미 있음 | careful — “초안 유지”/뒤로. 재불러오기는 작성 초안을 덮음 |
| pipeline-config-reach | 작업 설정/로그의 개발자 접근조건 | 접근 안내 | `JetsonApp.kt:224, 2051` | 설정 탭 → 앱 정보 7회 탭 → 개발자 도구 → 수집 작업 관리 | 개발자 모드 on; route는 직접 입력만으로 노출되지 않음 | safe — 접근만 확인. 편집/저장 없이 뒤로 |

## 등록/연결 (19)

| id | 화면/모달 | 종류 | file:line | 들어가는 길 | 전제 조건 | 안전 |
|---|---|---|---|---|---|---|
| onboarding | 새 장치 등록 | 화면 (`onboarding`) | `JetsonApp.kt:940` / `FirstDeviceOnboardingScreen.kt:36` | 장치 탭 → 우측 + 또는 첫 실행 “QR 코드로 장치 등록” | 없음 | careful — “QR 코드 스캔”은 다음 화면에서 카메라를 켤 수 있음. 뒤로 |
| connection-hub-unrouted | 장치 연결 | 화면 (`connection_hub`) | `JetsonApp.kt:903` / `ConnectionHubScreen.kt:78` | 현재 앱 내 navigate 호출자 없음; cold start에서 phone UI로 진입 불가 | route 등록만 됨. 현재 장치 홈이 대체 진입점 | safe — 열 수 있다면 뒤로 |
| connection-hub-no-device | 등록된 장치가 없습니다 | 화면 상태 | `ConnectionHubScreen.kt:164` | 현재 진입 불가 route의 장치 미등록 상태 | 등록 장치 0대 | safe — QR 등록을 시작하지 않고 뒤로 |
| connection-direct-confirm | 장치에 직접 연결할까요? | AlertDialog | `ConnectionHubScreen.kt:99` | 현재 진입 불가 connection hub에서 장치의 “직접 연결” 문제 해결 | 등록 장치·기존 공유기 연결 있음 | careful — “취소”/뒤로. 확정은 네트워크 연결 전환 |
| local-network-rationale | 로컬 네트워크 권한 허용 | 권한 안내 banner | `ConnectionHubScreen.kt:230` | 현재 진입 불가 connection hub → 로컬 네트워크 권한 배너 | Android API 37+ 및 권한 미허용 | careful — “권한 허용”을 누르지 말고 뒤로 |
| local-network-prompt | Android 로컬 네트워크 권한 | OS permission dialog | `MainActivity.kt:291` | 연결 허브의 권한 배너 → 권한 허용; 등록 장치 cold start에서도 누락 권한이면 자동 요청 | API 37+ | careful — 거부/뒤로. 허용은 시스템 permission 변경 및 discovery 허용 |
| device-list-unrouted | Bluetooth · Jetson 장치 검색 | 화면 (`devices_ble`) | `JetsonApp.kt:950` / `DeviceListScreen.kt:55` | 현재 앱 내 route 이동 호출자 없음; cold start phone UI로 진입 불가 | BLE 목록은 legacy route | safe — 열 수 있다면 뒤로 |
| bluetooth-rationale | 주변 기기 권한 허용 | 권한 안내 UI | `DeviceListScreen.kt:160` | 현재 진입 불가 BLE 화면의 주변 기기 권한 카드 | Bluetooth 권한 미허용 | careful — 권한 허용을 누르지 말고 뒤로 |
| bluetooth-prompt | Android Bluetooth/위치 권한 | OS permission dialog | `MainActivity.kt:270` / `:35` | 등록 장치 없는 신규 앱은 BLE 화면의 권한 버튼(현재 route unreachable); 등록 장치가 있으면 cold start 자동 요청 | Android 버전별 Bluetooth scan/connect 및 fine location 권한 미허용 | careful — 거부/뒤로. 허용하면 스캔/자동 연결이 가능 |
| forget-device-confirm | 장치 등록을 삭제할까요? | AlertDialog | `DeviceListScreen.kt:65` | 현재 진입 불가 BLE 목록 → 저장 장치의 삭제 아이콘 | 등록 장치가 목록에 있어야 함 | careful — “취소”/뒤로. 확인은 휴대전화 저장 인증정보 삭제 |
| qr-scanner | 장치 QR 스캔 | 화면 (`qr_scanner`) | `JetsonApp.kt:993` / `QrScannerScreen.kt:82` | 새 장치 등록 → QR 코드 스캔 | CAMERA 권한이 있으면 진입 즉시 CameraX preview와 QR analyzer 시작 | unsafe — 권한이 있으면 카메라/스캔 자동 시작. 상단 뒤로로 camera 해제 |
| camera-rationale | 카메라 권한 안내 · 수동 코드 입력 | 권한 안내 화면 상태 | `QrScannerScreen.kt:164, 371` | QR 스캐너 → CAMERA 권한 미허용 | 권한 미허용일 때 카메라 대신 표시 | careful — “권한 허용” 누르지 말고 뒤로; 수동 코드 버튼도 제출하지 말 것 |
| camera-permission-prompt | Android 카메라 권한 | OS permission dialog | `MainActivity.kt:275` | QR 스캐너 권한 안내 → 권한 허용 | CAMERA 권한 미허용 | careful — 거부/뒤로. 허용하면 QR 화면에서 즉시 카메라 시작 |
| qr-manual-entry | 등록 코드 직접 입력 | AlertDialog | `QrScannerScreen.kt:100` | QR 스캐너 → “코드 직접 입력” | 수동 코드를 입력하면 confirm 활성 | careful — “취소”/뒤로. 확인은 코드 제출 후 pairing으로 이동 |
| pairing-confirm | 장치를 찾을 준비가 됐습니다 | 화면 (`pairing`, QR_SCANNED 상태) | `JetsonApp.kt:1020` / `PairingScreen.kt:50, 81` | QR을 읽거나 수동 등록 코드를 확인 → 자동 진입 | QR/code가 유효 | careful — “취소”/뒤로. “연결 및 인증”은 실제 BLE 연결 시작 |
| pairing-progress-error | 장치 찾는 중·연결 중·인증 중·오류 | 화면 상태 (`pairing`) | `PairingScreen.kt:50, 166` | pairing 확인 화면 → “연결 및 인증”; 오류 뒤 재시도 | BLE 권한 및 등록 QR 정보 필요 | unsafe — BLE scan/connect/auth 동작 중. 화면 “취소”로 중단 후 장치 홈 복귀 |
| wifi-direct | Wi‑Fi Direct | 화면 (`wifi_direct`) | `JetsonApp.kt:1046` / `WifiDirectScreen.kt:53` | 장치 탭/상세 → 연결 방법 → 장치에 직접 연결 | 등록 장치 및 Android nearby Wi‑Fi permission | unsafe — 권한이 이미 있으면 화면 진입 시 peer discovery 자동 시작. 뒤로/“연결 취소” |
| wifi-direct-permission-empty | 주변 기기 권한 필요 / 검색 중 / 기기 없음 / 연결됨 | 화면 상태 | `WifiDirectScreen.kt:107` | Wi‑Fi Direct 화면 진입 | 권한 미허용이면 안내, 미지원이면 empty, peers/연결 상태에 따라 교체 | careful — “권한 허용/다시 검색/연결”은 누르지 말고 뒤로 |
| nearby-wifi-prompt | Android 주변 Wi‑Fi 기기 권한 | OS permission dialog | `MainActivity.kt:280, 111` | Wi‑Fi Direct 권한 안내 → 권한 허용 | Android 13+ `NEARBY_WIFI_DEVICES` 또는 구버전 location 미허용 | careful — 거부/뒤로. 허용하면 peer discovery 자동 시작 |

## 알림 (3)

| id | 화면/모달 | 종류 | file:line | 들어가는 길 | 전제 조건 | 안전 |
|---|---|---|---|---|---|---|
| alerts | 알림 | 화면 (`alerts`) | `JetsonApp.kt:1219` / `AlertCenterScreen.kt:79` | 장치 홈/상세·작업·결과의 알림 아이콘 | 없음; 미읽음이 있을 때 열기 즉시 모두 읽음 처리 | unsafe — 미읽음 알림이 있으면 진입 자체가 읽음 상태로 기록. 확인 후 뒤로 |
| alerts-empty | 새 알림이 없습니다 | 화면 상태 | `AlertCenterScreen.kt:89, 148` | 알림 화면 → 알림 0건 | 저장된 알림 없음 | safe — 뒤로 |
| alerts-clear-confirm | 알림 내역을 모두 삭제할까요? | AlertDialog | `AlertCenterScreen.kt:97` | 알림 → 기록이 있을 때 상단 전체 삭제 아이콘 | 알림 항목 1개 이상 | careful — “취소”/뒤로. 전체 삭제는 복구 불가 |

## 공통 (6)

| id | 화면/모달 | 종류 | file:line | 들어가는 길 | 전제 조건 | 안전 |
|---|---|---|---|---|---|---|
| startup-loading | GEO 로딩 장면 | 앱 전체 loading overlay | `MainActivity.kt:230, 306` / `GeoBrand.kt:127` | cold start → 저장 장치 복원 300ms 초과 시 장치 홈 위에 자동 표시 | 느린 credential store restore에서만 보임 | safe — 별도 취소 없음; restore 완료 시 자동 사라짐, Android 뒤로는 앱 종료 |
| startup-permission-prompt | Android 연결 권한 요청 | OS permission dialog | `MainActivity.kt:226` | 등록 장치가 있는 상태로 앱 cold start; 누락된 connectivity permission이면 화면 진입 직후 자동 요청 | 저장 장치 1대 이상 및 Bluetooth/location/nearby Wi‑Fi/local network 중 하나 이상 미허용 | unsafe — 앱 진입이 OS 권한창을 띄움. Android 거부/뒤로로 닫기; 허용하면 자동 discovery/reconnect도 가능 |
| startup-auto-discovery | 장치 홈 초기 자동 연결 검색 | 시작 상태/자동 동작 | `JetsonApp.kt:763` / `JetsonRepository.kt:1051` | 등록된 장치가 있는 상태로 cold start; 필요한 권한이 있으면 자동 connectivity configure | 저장된 장치 및 권한 상태. LAN discovery/BLE 자동 재연결/scan이 권한과 target 조건에 따라 진행 | unsafe — 앱 진입만으로 자동 discovery/reconnect 가능. 장치 탭에서 연결 상태를 확인하고 앱을 닫거나 권한 미허용 상태로 테스트 |
| startup-ticket-report-retry | 완료 보고 자동 재시도 | 공통 자동 동작 | `JetsonApp.kt:861` / `ServerTicketViewModel.kt:339` | cold start 또는 앱 복귀; 화면 진입과 무관하게 pending ticket report가 있으면 재시도 | 이전에 report가 전송 대기 상태로 저장되어 있어야 함 | unsafe — foreground에서 서버에 완료 보고 재시도; UI 취소 경로 없음. 테스트 기기에 pending report를 두지 않기 |
| global-connection-banner | 연결 문제 해결 | 공통 action banner | `ConnectionRecoveryLayout.kt:29` | 장치 연결이 필요한 화면을 열었는데 full-control 연결이 끊긴 경우 화면 위에 표시 | 해당 화면 route + 장치 offline/연결 손실 | safe — banner의 연결 문제 해결은 장치 홈으로 이동만 함. 화면 back |
| no-other-dialog-types | DialogProperties / DatePicker / TimePicker / BottomSheetScaffold / Toast | 검색 결과 | `ui/` 전체 search | 해당 없음 | Compose tree에서 사용처를 찾지 못함. Android OS 권한 prompt는 위에 별도 수록 | safe — 해당 없음 |








