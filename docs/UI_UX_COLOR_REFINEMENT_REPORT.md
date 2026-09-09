# Slate Harmony V7 색상 적용 보고서

2026-09-09. 코드·로컬 검사는 완료했으며 **승인 기기 화면 검증은 미완료**다.
연구는 설계 참고다. 최적색, 눈 피로 감소, 사용자 선호 개선을 측정하거나 입증하지 않았다.

## 시작 기준과 사용자 작업

- 정확한 지시 MD: `/home/jm/ControllerApp/JetsonControllerApp/jetson_slate_harmony_v7/JETSONCONTROLLER_SLATE_HARMONY_V7.md`
- 시작 브랜치: `codex/connection-stability-v3-20260908`
- 시작 HEAD: `f2113afd1300a1f8b1f5a00eabee7a569c2d727c`
- 원격 fetch 및 기본 브랜치 확인: `main`, 당시 `d9764e756ae64161acd12c9e7bf93ca18ea98909`.
  시작 HEAD는 원격 작업 브랜치와 일치했고 main은 그 조상이다. 과거 SHA로 돌아가지 않았다.
- 저장소·상위 경로에서 적용되는 AGENTS.md 없음. `00_START_HERE.md`, 실행 지시서,
  연구 자료, 정확한 JSON·스타일 가이드·Compose 참고 소스·제공 검사 범위를 읽었다.
- 시작 당시 Compose V5와 backend 진단 변경 등이 미커밋 상태였다.
  [시작 상태·전체 파일 해시](evidence/slate-v7/start.json), 로컬 `.artifacts/slate-v7-start/files/`
  원본 사본, `tracked.patch`, 변경 전 APK를 보관했다. reset/clean/stash/강제 push 없음.
- `30367c4`: 기존 backend 관측 주파수 진단 변경 보존.
  `b5f5267`: 기존 앱·문서·V5 자료를 작업 시작 바이트 그대로 보존.
  별도의 V7 변경 커밋으로 현재 작업과 구분한다. 기존 파일은 삭제하지 않았다.
- backend·data·model·ViewModel·테마 preference·MainActivity·JetsonApp 145개 파일의
  시작 대비 SHA-256이 일치한다. [보존 검사](evidence/slate-v7/behavior-source-preservation.json).
  이는 시작 전부터 있던 변경이 없다는 의미가 아니다.

## 실제 색상 원천과 적용

| 원천 | V7 적용 |
|---|---|
| `ui/theme/Color.kt` | 기존 Cobalt 타입·provider 유지, Light/Dark 66개 정확한 역할 통합 |
| `ui/theme/Theme.kt` | 모든 Material ColorScheme 전경/배경, container·outline·onError 명시 |
| 시스템 바 | 상단 canvas, 하단 surface, 앱에서 선택한 darkTheme로 아이콘 극성 유지 |
| Material3 | 동적 색상 없음, LocalTonalElevationEnabled=false로 명시적 표면 변색 방지 |
| 홈 | hero의 짙은 흰 글자 카드→V7 hero/heroText, 주 버튼 primary/onPrimary, 준비 soft·이력 raised |
| 작업 목록·상세 | 진행 작업 hero, 일반 카드 base, 최근 결과 raised, 상태 배지 별도 의미색 |
| 데이터 | 서버 목적지 soft, 파일 행 base, 선택 accent/onAccent + RadioButton/semantics, 이력 raised |
| 설정 | 장비 soft, 앱 설정 raised, 위험 목록 neutral sectionDanger, 개별 위험 문구·확정은 danger |
| 센서·알림·상태 배너 | 정상 success 중립, 실제 경고 warning/warningBg, 실패 danger/dangerBg |
| `Controls.kt` | 기존 Material 버튼 callback·enabled·semantics 전달, opaque 비활성 쌍·내부 포커스 링 |
| 입력 | 불투명 surface, controlBorder/focusRing, 입력 전경·라벨은 기존 Material 역할 |
| 네트워크 선택 행 | 불투명 accent, 보조·신호 설명 onAccent로 다크 대비 보완 |
| 기존 반투명 일반 표면 | 파일·전송·지도 안내를 불투명 표면으로 변경, 전체 opacity 필터 없음 |

[최종 토큰](evidence/slate-v7/final-tokens.json), [영구 가이드](UI_UX_DESIGN_SYSTEM.md),
[소스 대비 계산](evidence/slate-v7/contrast.json)을 별도로 보관했다.

## 문서와 다르게 적용한 이유

1. 현재 코드의 활성 테마는 V6가 아니라 Cobalt V5였다. V6 파일을 가져오거나 두 번째
   테마 공급자를 만들지 않고 실제 기존 타입에 V7을 통합했다. 이름만 Cobalt가 남으며 값은 V7이다.
2. 참고 ColorScheme의 surfaceContainerHigh=raised와 달리 실제 모달·일반 카드는 base로
   매핑했다. 스타일 가이드의 모달=base 계약을 우선하고 의미 있는 섹션에 raised를 직접 적용했다.
3. 기존 LazyColumn의 항목·키·상태 소유자를 유지했다. 짧은 그룹만 SectionSurface로 감싸며
   장비별 선택·초안·스크롤 구조, 탭, 콜백과 안전 확인 조건은 변경하지 않았다.
4. Material 기본 비활성 alpha 대신 V7 disabled/onDisabled를 사용한다. 포커스 링은
   컨트롤 안쪽에 중립 받침과 함께 그려 primary 배경이나 부모 clip에서 식별되도록 했다.
   실기에서 실제 포커스 경로와 라벨 가림 확인은 아직 필요하다.
5. 선택 배경의 muted 대비는 다크 4.229:1로 목표 미달이었다. 선택한 폴더 설명과
   네트워크 행 보조 문구에 onAccent를 사용했다. 새 입력 바탕은 surface로 분리했다.
6. 카메라/QR 영상은 검정 바탕·흰 안내문·부분 검정 오버레이를 보존했다. 영상 픽셀은 테마
   표면이 아니며 가독성을 유지해야 한다. 지도 J/M 마커는 V7 Light primary/warning과
   흰 문자·테두리로 조정했다. 테마 변경으로 지도와 위치 추적을 재생성하지 않는다.
   외부 지도·영상의 임의 픽셀 대비를 토큰 검사 통과로 간주하지 않는다.
7. V7 색상 적용에는 서버 API 추가가 필요 없다. 마지막 사용자 요청은 기존 backend 변경까지
   검증·커밋·통합하는 범위로 해석했다. 추가 통신·인증·복구 정책이나 배포를 만들지 않았다.

## 검증 결과와 증거 수준

| 실행 | 결과 | 증거/한계 |
|---|---|---|
| 변경 전 실제 소스 APK 빌드 | PASS | `before/build.txt`, APK 로컬 보관. 기기 캡처 아님 |
| 최종 assembleDebug / lintDebug / testDebugUnitTest / assembleDebugAndroidTest | PASS | `after/build-verified.txt`, exit 0 |
| JVM 테스트 | 196, 실패 0, 오류 0, skip 0 | 기존 전송·인증·복구·상태 보존 회귀 포함 |
| Lint | 오류 0, 경고 73 | 기존 detector 제외·SDK·Kotlin·Compose 버전 변경 없음 |
| backend 전체 unittest | 234, 실패 0, 오류 0, skip 0 | `backend-tests-final.txt`, 가상환경+시스템 모듈 경로, 장비 모의 객체 |
| V7 소스 토큰 | 66/66 일치 | `scripts/check_slate_harmony.py` |
| 선언한 불투명 역할 대비 | 94/94 PASS | 텍스트 4.5:1, 필수 그래픽 3:1 목표. 실제 렌더 픽셀 아님 |
| 제공 패키지 정합성 검사 재실행 | PASS | `local-package-audit.txt`, 기존 HTML 측정 산출물 정합성 확인 |
| 제공 Figma JS 모의 검사 재실행 | PASS | `local-plugin-mock.txt`, 실제 Figma 생성·렌더·export 아님 |
| 네이티브 instrumentation 실행 | NOT_RUN | ADB 연결 기기 0, 승인 대상 식별자 미제공 |
| 실제 앱 변경 전후 캡처 | NOT_CAPTURED | 이번 세션 캡처 0장. 과거 V5 캡처를 이번 증거로 재분류하지 않음 |
| OS 최대 글꼴·약 360dp·키보드·선택·포커스 | NOT_RUN_ON_DEVICE | Compose fixture 테스트는 컴파일만 완료 |
| 실기 자연 연결 손실·장비 전환·밝기/색 보정/환경광 | NOT_RUN | 상태 객체 테스트와 실기 확인을 구분 |
| 실제 Figma 생성·PNG export | NOT_RUN | 이번 네이티브 작업의 필수 선행 조건으로 삼지 않음 |

초기 backend 실행은 기본 Python에서 ruamel, 기존 가상환경에서 dbus 모듈이 없어 실패했다.
의존성 버전이나 제품 코드를 바꾸지 않고 기존 가상환경 Python의 sys.path 끝에
`/usr/lib/python3/dist-packages`를 추가해 전체 234개를 재실행했다. 초기 실패 로그도 남겼다.
모의 배포 테스트의 DEPLOYED/FAILED_ROLLED_BACK 문구는 임시 디렉터리에 대한 결과다.
운영 장비 재부팅·네트워크 중단·파일 삭제·backend 설치를 수행하지 않았다.

제공 패키지의 기존 32 PNG·128조합 HTML 결과는 패키지 제작자의 로컬 증거다.
이번 작업에서 브라우저 갤러리를 다시 렌더링하거나 그 값을 네이티브 검증으로 주장하지 않는다.

## 남은 필수 작업

승인 Android 기기의 식별자와 연결이 필요하다. 다음 항목은 **백로그이며 통과 아님**이다.

- 실제 제품의 라이트/다크 4개 루트·작업/전송/센서 상세·시작/위험 확인창 전후 캡처.
  변경 전 APK를 확보했지만 임의 다운그레이드·앱 데이터 삭제로 비교하지 않는다.
  이미 V7이 설치된 기기라면 별도 승인 테스트 환경에서 보존한 기준 소스로 비교한다.
- 승인 대상에 데이터 보존 업데이트 후 `CobaltFieldScreenTest`의 24 fixture 캡처,
  `SlateHarmonyScreenTest`의 테마 전환 중 선택·검색·스크롤·입력 포커스·확인 취소 검사,
  기존 CoreWorkflow/NetworkSettings 회귀 실행. fixture 수치는 장비 실측으로 보고하지 않는다.
- OS 최대 글꼴·실제 360dp 근처 폭, 스크롤 끝·키보드·하단바 가림, 버튼 포커스 잘림과
  선택 표시, 장비 A→B→A 초안·선택·스크롤 복귀 확인. LocalDensity 2배는 OS 비선형 확대와 다르다.
- 밝기·자동 밝기·화면 색 보정·야간 모드·주변 밝음/어두움 기록.
  끊김 상태는 모의 상태 또는 자연 발생 사건으로 검증하고 운영 네트워크를 의도적으로 끊지 않는다.
- 기기별 실행 수·실패·skip·APK 해시·캡처 원본을 `docs/evidence/slate-v7/`에 추가.

전체 완료 조건 미충족이므로 시작 때 기록한 V7 지시 MD는 **유지**한다.
위 항목과 영구 증거가 완료된 뒤에만 그 정확한 한 파일을 마지막 정리에서 삭제할 수 있다.
