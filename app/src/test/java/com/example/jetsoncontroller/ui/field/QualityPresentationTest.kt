package com.example.jetsoncontroller.ui.field

import com.example.jetsoncontroller.model.QualityProblemInterval
import com.example.jetsoncontroller.model.RoutePoint
import com.example.jetsoncontroller.model.RunQuality
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.PipelineOutputManifest
import com.example.jetsoncontroller.model.PipelineRunOutput
import com.example.jetsoncontroller.model.SurveyContextSnapshot
import com.example.jetsoncontroller.ui.components.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityPresentationTest {
    @Test
    fun `precision summary uses known denominator and keeps unknown time separate`() {
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

        assertEquals("정밀 위치 75%", presentation.headline)
        assertTrue(presentation.detail.contains("분류 1분 40초"))
        assertTrue(presentation.detail.contains("비율은 확인된 관찰 시간 기준"))
        assertTrue(presentation.detail.contains("미확인 20초"))
        assertTrue(presentation.slices.any { it.label == "정밀 위치 외" })
        assertTrue(presentation.slices.any { it.label == "위치 미확인" })
        assertFalse(presentation.detail.contains("RTK"))
    }

    @Test
    fun `unknown classification stays distinct from no samples`() {
        assertEquals(
            "위치 품질 미확인",
            runQualityPresentation(RunQuality(sampleState = "UNKNOWN")).headline
        )
        assertEquals(
            "위치 품질 미확인",
            runQualityPresentation(RunQuality(sampleState = "NO_SAMPLES")).headline
        )
    }

    @Test
    fun `problem labels preserve requirement without inventing severity`() {
        val required = qualityProblemLabel(problem("GNSS_LOST", "REQUIRED"))
        val optional = qualityProblemLabel(problem("SENSOR_STALE", "OPTIONAL"))
        val unspecified = qualityProblemLabel(problem("RTK_UNKNOWN", "UNSPECIFIED"))

        assertEquals("위치 수신 끊김", required)
        assertEquals("센서 갱신 지연", optional)
        assertEquals("위치 상태 미확인", unspecified)
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
        assertTrue(markers.single().title.contains("위치 수신 끊김"))
    }

    @Test
    fun `exact precision distinguishes approximate none and unknown while preserving zero fixed`() {
        val presentation = runQualityPresentation(RunQuality(
            sampleState = "OBSERVED",
            rtkObservedDurationMillis = 6_000,
            rtkUnknownDurationMillis = 4_000,
            rtkFixDurationMillis = 0,
            rtkFixRatio = 0.0,
            locationPrecision = listOf(
                com.example.jetsoncontroller.model.LocationPrecisionDuration("FLOAT", 4_000, 2.0 / 3.0),
                com.example.jetsoncontroller.model.LocationPrecisionDuration("NO_FIX", 2_000, 1.0 / 3.0)
            )
        ))

        assertEquals("정밀 위치 0%", presentation.headline)
        assertEquals(listOf("대략 위치", "위치 없음", "위치 미확인"), presentation.slices.map { it.label })
    }

    @Test
    fun `server fix states are grouped into the three user precision categories`() {
        val presentation = runQualityPresentation(RunQuality(
            sampleState = "OBSERVED",
            rtkObservedDurationMillis = 10_000,
            rtkUnknownDurationMillis = 0,
            rtkFixDurationMillis = 2_000,
            rtkFixRatio = .2,
            locationPrecision = listOf(
                com.example.jetsoncontroller.model.LocationPrecisionDuration("FIXED", 2_000, .2),
                com.example.jetsoncontroller.model.LocationPrecisionDuration("FLOAT", 3_000, .3),
                com.example.jetsoncontroller.model.LocationPrecisionDuration("STANDALONE", 5_000, .5)
            )
        ))

        assertEquals(listOf("정밀 위치", "대략 위치"), presentation.slices.map { it.label })
        assertEquals(8_000L, presentation.slices.last().durationMillis)
    }

    @Test
    fun `running history never claims more than observation freshness supports`() {
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
            "실행 보고" to StatusTone.INFO,
            historyRunPresentation(run, online = true, historyCurrent = true)
        )
    }

    @Test
    fun `late route responses cannot cross run or request scopes`() {
        assertTrue(routeResponseIsCurrent(1, 1, 2, 2, "run-b", "run-b"))
        assertFalse(routeResponseIsCurrent(1, 1, 1, 2, "run-b", "run-a"))
        assertFalse(routeResponseIsCurrent(1, 2, 2, 2, "run-b", "run-b"))
    }

    @Test
    fun `history transfer uses canonical manifest run id and rejects mismatches`() {
        val context = SurveyContextSnapshot(
            deviceId = "device",
            surveyProjectId = "project", surveyProjectLabel = "Project", surveyProjectRevision = 1,
            surveySectionId = "section", surveySectionLabel = "Section", surveySectionRevision = 1,
            capturedAt = "2026-09-15T00:00:00Z"
        )
        val output = PipelineRunOutput(
            rootId = "recordings", path = "run", outputId = "output", manifestState = "FINAL",
            manifest = PipelineOutputManifest(runId = "run-1", generatedAt = "2026-09-15T00:00:00Z")
        )
        val legacyHistory = TaskRun("pipeline/log", "pipeline", "label", "log", "start",
            contextSnapshot = context, output = output)

        assertEquals("run-1", canonicalHistoryRunId(legacyHistory))
        assertEquals(null, canonicalHistoryRunId(legacyHistory.copy(runId = "other-run")))
        assertEquals(null, canonicalHistoryRunId(legacyHistory.copy(contextSnapshot = null)))
    }

    private fun problem(kind: String, requirement: String, index: Int? = null) =
        QualityProblemInterval(
            kind = kind,
            requirement = requirement,
            startedAtEpochMillis = 1,
            startRoutePointIndex = index
        )
}
