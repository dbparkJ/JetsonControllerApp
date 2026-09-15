package com.example.jetsoncontroller.ui.components

/**
 * 제품의 상태 어휘.
 *
 * [PENDING] 과 [UNKNOWN] 이 이 목록의 핵심입니다. 운영 브리프의 상태표는 "시작 결과 미확인",
 * "값이 오래됨", "중지 요청 중", "캐시 목록" 을 성공으로도 실패로도 표시하지 말라고
 * 요구합니다. 회색 '완료' 를 초록 '완료' 로 읽은 직원은 중복 수집을 시작하거나, 기록되지
 * 않은 구간을 두고 현장을 떠납니다.
 *
 * - [SUCCESS]  확인됨.      장치나 서버가 실제로 돌려준 결과.
 * - [WARNING]  주의.        진행할 수 있지만 사용자가 알아야 할 제한이 있음.
 * - [ERROR]    차단·실패.    시작할 수 없거나, 일어나지 않은 것이 확인됨.
 * - [INFO]     안내.        판정이 아니라 맥락.
 * - [PENDING]  처리 중.      요청은 접수됐고 결과는 아직 열려 있음.
 * - [UNKNOWN]  미확인.      모른다. 성공이나 실패의 대용이 아님.
 *
 * 이 enum은 Compose에 의존하지 않는 별도 파일에 둡니다. 단계 판정 같은 순수 로직이
 * 상태 어휘를 쓰면서도 UI 없이 JVM에서 테스트될 수 있어야 하기 때문입니다.
 */
enum class StatusTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    PENDING,
    UNKNOWN
}
