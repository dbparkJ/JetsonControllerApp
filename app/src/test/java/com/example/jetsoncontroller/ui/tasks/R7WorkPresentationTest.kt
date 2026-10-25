package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MetricValidity
import com.example.jetsoncontroller.ui.components.geo.figurePresentation
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class R7WorkPresentationTest {
    private val now = Instant.parse("2026-10-25T17:00:00Z")
    private fun input(status: JetsonStatus?) = TasksHomeInput("장치", true,
        SurveyRunUiState(runStateConfirmed = true), emptyList(), emptyList(), emptyList(), false,
        status = status, now = now)

    @Test fun `normal photo figures are neutral and preserve explicit zero readings`() {
        val status = JetsonStatus(collectedAtEpochMillis = now.toEpochMilli(), storageTotalBytes = 1_000,
            storageAvailableBytes = 0, temperatureC = 0f, cpuPercent = 0, gpuPercent = 0,
            metricValidity = mapOf("temperatureC" to MetricValidity("valid")))
        val figures = workDeviceFigures(input(status))
        assertEquals(listOf("저장 여유", "온도", "CPU", "GPU"), figures.map { it.label })
        assertTrue(figures.all { !figurePresentation(it).unknown && !figurePresentation(it).warning && !it.stale })
        assertTrue(figures.all { it.value!!.startsWith("0") })
    }

    @Test fun `invalid readings and a device switch cannot inherit old figures`() {
        val invalid = JetsonStatus(collectedAtEpochMillis = now.toEpochMilli(), storageTotalBytes = 1_000,
            storageAvailableBytes = 500, temperatureC = 49f, cpuPercent = 32, gpuPercent = 48,
            metricValidity = listOf("storageAvailableBytes", "temperatureC", "cpuPercent", "gpuPercent")
                .associateWith { MetricValidity("unavailable") })
        assertTrue(workDeviceFigures(input(invalid)).all { figurePresentation(it).unknown })
        val old = invalid.copy(metricValidity = emptyMap())
        assertTrue(workDeviceFigures(input(old).copy(checkingDevice = true)).all { figurePresentation(it).unknown })
        assertTrue(workDeviceFigures(input(null)).all { figurePresentation(it).unknown })
    }

    @Test fun `an unobserved default snapshot never invents zero cpu or gpu readings`() {
        assertTrue(workDeviceFigures(input(JetsonStatus())).all { figurePresentation(it).unknown })
    }

    @Test fun `only exceeded limits color figures and old values retain their evidence`() {
        val status = JetsonStatus(collectedAtEpochMillis = now.minusSeconds(60).toEpochMilli(),
            storageTotalBytes = 1_000, storageAvailableBytes = 100, storagePercent = 90,
            temperatureC = 85f, cpuPercent = 32, gpuPercent = 48)
        val shown = workDeviceFigures(input(status)).map(::figurePresentation)
        assertTrue(shown.take(2).all { it.warning && it.staleDetail == "마지막 값 기준" })
        assertTrue(shown.drop(2).all { !it.warning && it.detail == "마지막 값 기준" })
    }

    @Test fun `passive storage availability never claims preflight success`() {
        val rows = ticketReadinessRows(TicketPassiveReadiness(connection = "같은 네트워크",
            storage = "512 GB 남음", position = "정밀 위치", camera = "수신 중", controlAvailable = true))
        assertEquals("점검 전", rows.single { it.label == "저장 공간" }.status)
        assertEquals(Tone.UNKNOWN, rows.single { it.label == "저장 공간" }.tone)
        assertEquals(Tone.SUCCESS, rows.single { it.label == "위치 품질" }.tone)
        assertEquals(Tone.UNKNOWN, ticketReadinessRows(TicketPassiveReadiness())
            .single { it.label == "위치 품질" }.tone)
    }
}
