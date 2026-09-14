package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.QualityProblemInterval
import com.example.jetsoncontroller.model.RoutePoint
import com.example.jetsoncontroller.model.RunQuality
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.components.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityPresentationTest {
    @Test
    fun `fix ratio is observation with exact denominator and separate unknown time`() {
        val presentation = runQualityPresentation(
            RunQuality(
                sampleState = "OBSERVED",
                elapsedDurationMillis = 180_000,
                rtkObservedDurationMillis = 100_000,
                rtkUnknownDurationMillis = 20_000,
                rtkFixDurationMillis = 75_000,
                rtkFixRatio = .75,
                observationCount = 12
            )
        )

        assertEquals("RTK FIX 관찰 비율 75.0%", presentation.headline)
        assertTrue(presentation.detail.contains("RTK 관찰 1분 40초"))
        assertTrue(presentation.detail.contains("FIX 유지 1분 15초"))
        assertTrue(presentation.detail.contains("RTK 미확인 20초"))
        assertTrue(presentation.detail.contains("판정 기준 없음"))
        assertFalse(presentation.detail.contains("통과"))
    }

    @Test
    fun `unknown classification stays distinct from no samples`() {
        assertEquals(
            "RTK 상태 분류 미확인",
            runQualityPresentation(RunQuality(sampleState = "UNKNOWN")).headline
        )
        assertEquals(
            "RTK 비율 산정 표본 없음",
            runQualityPresentation(RunQuality(sampleState = "NO_SAMPLES")).headline
        )
    }

    @Test
    fun `problem labels preserve requirement without inventing severity`() {
        val required = qualityProblemLabel(problem("GNSS_LOST", "REQUIRED"))
        val optional = qualityProblemLabel(problem("SENSOR_STALE", "OPTIONAL"))
        val unspecified = qualityProblemLabel(problem("RTK_UNKNOWN", "UNSPECIFIED"))

        assertTrue(required.contains("필수"))
        assertTrue(optional.contains("선택"))
        assertTrue(unspecified.contains("요구 수준 미지정"))
    }

    @Test
    fun `map issues require a known valid route point`() {
        val route = listOf(
            RoutePoint(37.1, 127.1, 1),
            RoutePoint(Double.NaN, 127.2, 2),
            RoutePoint(95.0, 127.3, 3)
        )
        val quality = RunQuality(problemIntervals = listOf(
            problem("GNSS_LOST", "REQUIRED", 0),
            problem("SENSOR_STALE", "OPTIONAL", null),
            problem("RTK_UNKNOWN", "UNSPECIFIED", 1),
            problem("CLOCK_GAP", "UNSPECIFIED", 2),
            problem("SENSOR_ERROR", "REQUIRED", 20)
        ))

        val markers = locatedQualityProblems(route, quality)

        assertEquals(1, markers.size)
        assertEquals(0, markers.single().routeIndex)
        assertTrue(markers.single().title.contains("GNSS 수신 끊김"))
    }

    @Test
    fun `offline running history is an unconfirmed last observation`() {
        val run = TaskRun("id", "pipeline", "label", "log", "start", state = "RUNNING")
        assertEquals(
            "최근 실행 보고 · 현재 미확인" to StatusTone.WARNING,
            historyRunPresentation(run, online = false, historyCurrent = false)
        )
        assertEquals(
            "최근 실행 보고 · 현재 미확인" to StatusTone.WARNING,
            historyRunPresentation(run, online = true, historyCurrent = false)
        )
        assertEquals(
            "진행 중" to StatusTone.INFO,
            historyRunPresentation(run, online = true, historyCurrent = true)
        )
    }

    @Test
    fun `late route responses cannot cross run or request scopes`() {
        assertTrue(routeResponseIsCurrent(1, 1, 2, 2, "run-b", "run-b"))
        assertFalse(routeResponseIsCurrent(1, 1, 1, 2, "run-b", "run-a"))
        assertFalse(routeResponseIsCurrent(1, 2, 2, 2, "run-b", "run-b"))
    }

    private fun problem(kind: String, requirement: String, index: Int? = null) =
        QualityProblemInterval(
            kind = kind,
            requirement = requirement,
            startedAtEpochMillis = 1,
            startRoutePointIndex = index
        )
}
