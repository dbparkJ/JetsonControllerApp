package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.ui.components.geo.GeoFigure
import com.example.jetsoncontroller.ui.components.geo.figurePresentation

/** Keep the complete elapsed value and capacity visible at the current measured text size. */
internal fun runClockStacks(availableDp: Float, fontScale: Float, clockDp: Float, capacityDp: Float): Boolean =
    fontScale >= 1.3f || clockDp + capacityDp + 16f > availableDp

/** Size formatters join the number and unit with a nonbreaking space. */
internal fun runFigureParts(figure: GeoFigure): Pair<String, String> {
    val shown = figurePresentation(figure)
    if (shown.unknown) return shown.value to ""
    if (figure.unit.isNotEmpty()) return shown.value to figure.unit
    val match = Regex("^([+-]?[\\d.,]+)[\\s\\u00a0]*(.*)$").matchEntire(shown.value)
        ?: return shown.value to ""
    return match.groupValues[1] to match.groupValues[2]
}

internal fun runHeadingTitle(state: ActiveRunState, section: String?, project: String?): String = when (state) {
    ActiveRunState.START_CHECKING -> section?.takeIf { it.isNotBlank() }
        ?: project?.takeIf { it.isNotBlank() } ?: "수집 구간"
    ActiveRunState.UNKNOWN -> "수집 상태를 확인할 수 없습니다"
    ActiveRunState.STOP_CHECKING -> "종료 확인 중"
    ActiveRunState.ENDED -> "수집이 끝났습니다"
    else -> section ?: if (state == ActiveRunState.DISCONNECTED) "연결이 끊겼습니다" else "수집 중"
}
