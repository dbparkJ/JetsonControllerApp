package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.data.alerts.AlertSettings
import kotlin.math.roundToInt

internal fun alertThresholdValue(value: Float, range: IntRange): Int = value.roundToInt().coerceIn(range)

internal data class SettingsHelpSection(val title: String, val paragraphs: List<String>)

internal val settingsHelpSections = listOf(
    SettingsHelpSection("수집", listOf(
        "작업 탭의 수집 구간에서 구간을 고른 뒤 '수집 시작'을 누르세요.",
        "시작 전 점검을 통과해야 수집이 시작됩니다.",
        "마치려면 '수집 종료'를 누르세요. 종료가 확인되면 수집 결과가 열립니다."
    )),
    SettingsHelpSection("전송", listOf(
        "수집 결과에서 '서버로 보내기'를 누르세요. 서버로 보내기는 같은 네트워크에서만 됩니다.",
        "서버 수신을 확인한 뒤 GEO& Work에 완료를 보고할 수 있습니다.",
        "보내지 못한 수집은 전송 탭의 보낼 수집에 남습니다."
    )),
    SettingsHelpSection("연결 문제", listOf(
        "연결이 끊기면 '다시 연결'을 누르세요. 수집은 장치에서 계속될 수 있습니다.",
        "같은 네트워크에서 찾지 못하면 '연결 방법'을 여세요. 장치에 직접 연결하거나 블루투스로 기본 연결할 수 있습니다.",
        "인증 정보를 확인할 수 없다고 나오면 QR 코드로 다시 등록하세요."
    ))
)

internal const val ALERT_SETTING_COUNT = 6

internal fun alertSettingsSummary(settings: AlertSettings): String {
    val enabled = listOf(
        settings.storageEnabled,
        settings.temperatureEnabled,
        settings.pipelineStartedEnabled,
        settings.pipelineFailedEnabled,
        settings.uploadStartedEnabled,
        settings.uploadEndedEnabled
    ).count { it }
    return ALERT_SETTING_COUNT.toString() + "개 중 " + enabled + "개"
}
