package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.ui.components.StatusTone

/**
 * 현장 업무의 단계.
 *
 * 이 파일이 이번 IA 재편의 중심입니다. 이전 구조에서는 사용자가 "지금 뭘 해야 하는지"를
 * 탭을 옮겨다니며 스스로 알아내야 했습니다 — 홈 → 작업 → 프로그램 선택 → 조사 준비 →
 * 점검 → 시작. 가장 중요한 업무가 가장 깊은 곳에 있었고, 시작을 누른 뒤에 갈 화면은
 * 아예 없었습니다.
 *
 * 이제 앱이 상태에서 단계를 계산하고, 현장 홈의 주 카드가 그 단계 하나만 제시합니다.
 *
 * 순서가 중요합니다. [judgeFieldStage] 의 분기 순서는 UI 편의가 아니라 안전 규칙입니다:
 * 결과가 확인되지 않은 요청이 있으면 그것이 무엇보다 먼저 나옵니다. 그 상태에서 사용자를
 * '새 수집 시작' 으로 보내면 같은 구간을 두 번 기록하게 되기 때문입니다.
 */
enum class FieldStage(
    /** 연결 → 준비 → 점검 → 수집 → 결과 5단계 인디케이터에서의 위치. */
    val step: Int
) {
    /** 조작할 장치가 없다. */
    NOT_CONNECTED(0),

    /** 장치의 현재 실행 상태를 모른다. 성공도 실패도 아니다. */
    RUN_UNCONFIRMED(3),

    /** 시작 요청은 접수됐고 결과는 아직 열려 있다. */
    START_PENDING(3),

    /** 장치가 실행 중이라고 실제로 보고했다. */
    COLLECTING(3),

    /** 실행은 끝났고 장치에 무엇이 남았는지는 확인 전이다. */
    NEEDS_RESULT(4),

    /** 연결은 됐지만 어느 구간을 조사할지 정하지 않았다. */
    NEEDS_SURVEY(1),

    /** 조사 범위는 정해졌고 장비 점검을 하지 않았다. */
    NEEDS_PREFLIGHT(2),

    /** 점검까지 통과했고 시작만 남았다. */
    READY_TO_START(2)
}

/** 홈의 주 카드가 누르면 가는 곳. */
enum class FieldDestination {
    CONNECT,
    SURVEY_PREP,
    PREFLIGHT,
    ACTIVE_RUN,
    RUN_RESULT
}

/**
 * 단계 판정에 필요한 사실만 모은 입력.
 *
 * ViewModel 이나 Compose 를 참조하지 않으므로 JVM 단위 테스트로 전부 검증할 수 있습니다.
 * 이 프로젝트에서 실기 검증이 어려운 분기일수록 여기로 내려야 합니다.
 */
data class FieldStageInput(
    /** 앱이 장치를 조회·제어할 수 있는가. 인터넷 연결 여부와 무관합니다. */
    val connected: Boolean = false,
    /** 앱이 방금 장치에서 실행 목록을 받아 확인했는가. */
    val runStateConfirmed: Boolean = false,
    /** 장치가 실행 중이라고 확인해 준 실행. */
    val activeRunId: String? = null,
    /** 보냈지만 결과를 못 받은 시작 요청. */
    val pendingStartRequestId: String? = null,
    /** 있었던 것은 아는데 지금 어떤 상태인지 모르는 실행. */
    val unconfirmedRunId: String? = null,
    /** 종료됐고 저장 결과 확인이 끝나지 않은 실행. */
    val runAwaitingResultId: String? = null,
    val surveySelected: Boolean = false,
    val preflightReady: Boolean = false,
    /** "자전거도로 정기 조사 · A구간" 같은 사람이 읽는 조사 범위. */
    val surveyLabel: String? = null,
    /** "00:42:18" 같은 경과 시간. 장비가 준 값이 없으면 null. */
    val elapsedLabel: String? = null,
    /** 마지막으로 장치 상태를 확인한 시각. "09:41" 형식. */
    val lastObservedLabel: String? = null,
    /** 등록은 돼 있지만 연결되지 않은 장치 수. */
    val registeredDeviceCount: Int = 0
)

/**
 * 홈의 주 카드에 그대로 들어가는 한 벌의 문구.
 *
 * [detail] 은 선택 사항이 아닙니다. 상태만 말하고 그것이 사용자의 작업에 어떤 의미인지
 * 말하지 않는 카드는 두 번째부터 읽히지 않습니다.
 */
data class FieldStagePlan(
    val stage: FieldStage,
    val eyebrow: String,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val destination: FieldDestination,
    val tone: StatusTone
)

/**
 * 상태에서 단계를 판정한다.
 *
 * 분기 순서가 곧 안전 정책입니다:
 *
 *  1. 결과 미확인 시작 요청 — 중복 실행을 만들 수 있으므로 무조건 먼저.
 *  2. 상태를 모르는 실행 — 연결이 끊겼든 응답이 유실됐든, 모른다는 사실 자체가 행동을
 *     바꿉니다. '수집 중지' 로도 '정상' 으로도 단정하지 않습니다.
 *  3. 미연결 — 위 두 가지가 없을 때만 단순 미연결입니다. 실행 기록이 있는 채로 연결이
 *     끊긴 경우는 2번에서 이미 잡힙니다.
 *  4. 그 다음에야 평상시 순서(수집 중 → 결과 확인 → 조사 선택 → 점검 → 시작).
 */
fun judgeFieldStage(input: FieldStageInput): FieldStage = when {
    input.pendingStartRequestId != null -> FieldStage.START_PENDING
    input.unconfirmedRunId != null -> FieldStage.RUN_UNCONFIRMED
    input.connected && !input.runStateConfirmed -> FieldStage.RUN_UNCONFIRMED
    !input.connected -> FieldStage.NOT_CONNECTED
    input.activeRunId != null -> FieldStage.COLLECTING
    input.runAwaitingResultId != null -> FieldStage.NEEDS_RESULT
    !input.surveySelected -> FieldStage.NEEDS_SURVEY
    !input.preflightReady -> FieldStage.NEEDS_PREFLIGHT
    else -> FieldStage.READY_TO_START
}

/** 판정된 단계를 홈 카드 한 벌로 바꾼다. */
fun fieldStagePlan(input: FieldStageInput): FieldStagePlan {
    val stage = judgeFieldStage(input)
    val survey = input.surveyLabel
    return when (stage) {
        FieldStage.START_PENDING -> FieldStagePlan(
            stage = stage,
            eyebrow = "단계 3 · 결과 대기",
            title = "시작 요청의 결과를 확인하세요",
            detail = "요청은 보냈지만 장치의 응답을 받지 못했습니다. 장치에서 이미 시작됐을 수 있으므로, " +
                "새로 시작하기 전에 같은 요청 ID로 결과부터 확인합니다.",
            actionLabel = "같은 요청 ID로 결과 확인",
            destination = FieldDestination.ACTIVE_RUN,
            tone = StatusTone.PENDING
        )

        FieldStage.RUN_UNCONFIRMED -> FieldStagePlan(
            stage = stage,
            eyebrow = "단계 3 · 상태 미확인",
            title = "수집 상태를 확인하지 못했습니다",
            detail = buildString {
                append("앱이 장치의 현재 실행 상태를 모릅니다. 앱 연결이 끊겨도 Jetson의 수집은 계속될 수 있습니다.")
                input.lastObservedLabel?.let { append(" 마지막으로 확인한 시각은 $it 입니다.") }
            },
            actionLabel = if (input.connected) "실행 상태 다시 조회" else "장치에 다시 연결",
            destination = if (input.connected) FieldDestination.ACTIVE_RUN else FieldDestination.CONNECT,
            tone = StatusTone.UNKNOWN
        )

        FieldStage.NOT_CONNECTED -> FieldStagePlan(
            stage = stage,
            eyebrow = "단계 0 · 다음 행동",
            title = "장치에 연결하세요",
            detail = if (input.registeredDeviceCount > 0) {
                "등록된 장치 ${input.registeredDeviceCount}대가 있습니다. 연결은 제어 경로를 여는 것이고, " +
                    "수집을 시작하지는 않습니다."
            } else {
                "등록된 장치가 없습니다. 장치의 QR로 먼저 등록해야 합니다."
            },
            actionLabel = if (input.registeredDeviceCount > 0) "장치 연결" else "QR로 장치 등록",
            destination = FieldDestination.CONNECT,
            tone = StatusTone.INFO
        )

        FieldStage.COLLECTING -> FieldStagePlan(
            stage = stage,
            eyebrow = "단계 3 · 진행 중",
            title = input.elapsedLabel?.let { "수집 중 · $it" } ?: "수집 중",
            detail = buildString {
                append(survey ?: "현재 실행")
                append(" · 실행 중에는 프로젝트·구간·정책을 바꿀 수 없습니다.")
            },
            actionLabel = "수집 중 화면 열기",
            destination = FieldDestination.ACTIVE_RUN,
            tone = StatusTone.SUCCESS
        )

        FieldStage.NEEDS_RESULT -> FieldStagePlan(
            stage = stage,
            eyebrow = "단계 4 · 다음 행동",
            title = "저장 결과를 확인하세요",
            detail = "실행은 종료됐습니다. 장치에 무엇이 남았는지는 아직 확인 전이고, " +
                "서버 전송은 시작하지 않았습니다. 이 셋은 서로 다른 사실입니다.",
            actionLabel = "결과 요약 보기",
            destination = FieldDestination.RUN_RESULT,
            tone = StatusTone.PENDING
        )

        FieldStage.NEEDS_SURVEY -> FieldStagePlan(
            stage = stage,
            eyebrow = "단계 1 · 다음 행동",
            title = "조사할 구간을 고르세요",
            detail = survey?.let { "최근 조사: $it" }
                ?: "프로젝트와 조사 구간을 선택해야 수집 결과가 어디에 속하는지 남습니다.",
            actionLabel = "조사 준비",
            destination = FieldDestination.SURVEY_PREP,
            tone = StatusTone.INFO
        )

        FieldStage.NEEDS_PREFLIGHT -> FieldStagePlan(
            stage = stage,
            eyebrow = "단계 2 · 다음 행동",
            title = "시작 전 점검이 필요합니다",
            detail = buildString {
                survey?.let { append(it); append(" · ") }
                append("장비 시간, 저장 공간, 필수 센서를 실제로 조회해 확인합니다.")
            },
            actionLabel = "시작 전 점검",
            destination = FieldDestination.PREFLIGHT,
            tone = StatusTone.INFO
        )

        FieldStage.READY_TO_START -> FieldStagePlan(
            stage = stage,
            eyebrow = "단계 2 · 시작 가능",
            title = "수집을 시작할 수 있습니다",
            detail = buildString {
                survey?.let { append(it); append(" · ") }
                append("점검을 통과했습니다. 시작하면 조사 범위와 결과 폴더가 이 실행에 고정됩니다.")
            },
            actionLabel = "확인 후 수집 시작",
            destination = FieldDestination.PREFLIGHT,
            tone = StatusTone.SUCCESS
        )
    }
}
