from pathlib import Path
import json,hashlib
P=Path(__file__).resolve().parents[1]
def main():
 r=json.loads((P/'evidence/prototype-validation.json').read_text(encoding='utf-8'));s=r['summary'];m=json.loads((P/'evidence/plugin-mock-test.json').read_text());c=json.loads((P/'evidence/set-consistency.json').read_text())
 report=f'''# Slate Harmony V7 — 실제 수행한 검증과 한계

기록일: 2026-09-09. 이 보고서는 패키지 제작 환경의 검사다. 원격 앱·실기기·실제 Figma·사용자 선호 검사가 아니다.

## 실행 결과

| 검사 | 실제 결과 | 범위 |
|---|---|---|
| 로컬 화면 생성 | 32개 상태, 각 824×1784 PNG | Chromium HTML 렌더링, 논리 크기 412×892 |
| 보드 | 6개 | 실제 로컬 PNG를 합성한 소개/비교/색상 보드 |
| 가로 경계 검사 | {s['layoutCases']}조합 중 넘침 {s['horizontalOverflowCases']} | 32상태 × 412/1.0, 360/1.0, 360/1.3, 360/선형2.0 |
| 대표 상호작용 | {s['interactionPasses']}/{s['interactionTests']} | 탭·선택·확인·취소·복구·다크 유지; 서버/API 없음 |
| 렌더된 텍스트 | {s['textNodesInspectedAtDefaultWidth']}개 노드 / {s['uniqueRenderedTextPairs']}개 고유 쌍 | DOM의 실제 계산색과 가장 가까운 불투명 조상 배경 |
| 텍스트 대비 | {s['renderedTextPairPasses']}/{s['uniqueRenderedTextPairs']} 쌍 4.5:1 이상 | 최저 {min(p['ratio'] for p in r['textPairs']):.4f}:1; 최종 값 반올림 전 판정 |
| 렌더된 아이콘 | {s['iconNodesInspectedAtDefaultWidth']}개 / 고유 {s['uniqueRenderedIconPairs']}쌍 | SVG stroke와 인접 배경, 장식 아이콘도 포함 |
| 아이콘 대비 | {s['renderedIconPairPasses']}/{s['uniqueRenderedIconPairs']} 쌍 3:1 이상 | 이것만으로 모든 비텍스트/포커스 감사가 완료되지는 않음 |
| 역할 계약 쌍 | {s['tokenPairPasses']}/{s['tokenPairs']} | 텍스트 4.5, controlBorder/focusRing 3.0 목표 |
| 브라우저 런타임 오류 | {s['runtimeErrors']} | 검사한 경로 내 |
| 프로토타입 외부 요청 | {s['externalRequests']} | 로컬 검사 중 관찰; 장비 접속 없음 |
| Figma JS 구문/모의 검사 | 통과 | 화면 {m['screenCount']}, variant {m['componentVariants']}, 변수 {m['variables']}, 텍스트 스타일 {m['textStyles']}의 생성 경로 |
| 중복 생성 방지/PNG 메시지 | 모의 API에서 통과 | 실제 Figma 생성·export 아님 |
| 패키지 정합성 | {c['status']} | JSON/HTML/Figma 입력/Kotlin/스타일 표/프롬프트 동기화 |
| 원본 캡처 무결성 | SHA-256 일치 | 사용자 원본 8개 |

전체 수치와 각 쌍의 계산값은 `prototype-validation.json`, `contrast-pairs.csv`, `plugin-mock-test.json`, `set-consistency.json`에 있다. 렌더된 실제 글자 658개는 기본 폭/기본 확대 검사에서 센 것이며 128개 모든 크기 조합의 합이 아니다.

## 검사 중 수정한 부분

첫 검사에서 다크의 비활성/요청 중 버튼 설명 `onDisabled`가 배경과 약 4.408:1이었다. 비활성에 대한 표준 예외 여부와 관계없이 이 앱의 설명 가독성 목표를 지키기 위해 전경 명도를 조정하고 전체 렌더와 검사를 다시 실행했다. 최종 자료에 실패한 옛 색상은 남기지 않았다.

## 직접 확인한 시안

라이트·다크 소개 보드, V6/V7 비교 보드, 다크 재부팅 확인창을 확대해 검토했다. 정상 섹션이 같은 계열로 보이는지, 위험 의미색이 국소 영역에 남는지, 확인창 문구·버튼이 읽히는지 확인했다. 모든 128조합의 완전한 수동 시각 검수를 했다는 뜻은 아니다.

## 미실행 / 자동 검사로 증명하지 못하는 것

- Android 앱 소스 반영·Gradle 빌드·네이티브 UI 테스트·TalkBack·실기기 연결: **NOT_RUN**.
- 실제 Figma 파일 생성·Auto Layout 렌더·폰트 폴백·실제 PNG export: **NOT_RUN**.
- 논문 결과의 재현 실험, 이 앱의 사용자 선호·조화감·눈 피로·작업 시간 개선 평가: **NOT_RUN**.
- 실제 디스플레이 휘도/조도, 야외 햇빛, 야간 모드, 색각 다양성, 장시간 사용: **NOT_RUN**.
- 가로 경계 검사는 모든 수직 스크롤·가림·버튼 접근성을 증명하지 않는다. 실제 앱에서 별도 확인한다.
- 토큰 대비는 sRGB 디지털 계산이다. 스크린샷의 안티앨리어싱 픽셀이나 디스플레이 광학 계측이 아니다.
- 논문의 두 색 사각형/교정 읽기/대시보드/태블릿 결과가 이 앱의 모든 과제로 바로 일반화되지는 않는다.

## 시안의 출처

PNG는 `prototype.html`을 Chromium으로 렌더링했다. Figma export/실제 앱 캡처/사용자 실측 화면이라고 부르지 않는다. 보드의 전화 화면은 이 PNG만 사용했으며 생성형 이미지로 UI를 대체하지 않았다. 모든 수치·파일·연결 상태는 격리된 디자인 예시다.
'''
 (P/'evidence/VALIDATION.md').write_text(report,encoding='utf-8')
 status={'package':'Slate Harmony V7','localPreview':'RENDERED_AND_CHECKED','figmaPluginSource':'GENERATED','figmaPluginMock':'PASS','realFigmaExecution':'NOT_RUN','realFigmaExport':'NOT_RUN','nativeBuild':'NOT_RUN','nativeDevice':'NOT_RUN','userComfortStudy':'NOT_RUN','repositoryWrites':False}
 (P/'evidence/figma-status.json').write_text(json.dumps(status,indent=2),encoding='utf-8')
 print('Validation report written')
if __name__=='__main__':main()
