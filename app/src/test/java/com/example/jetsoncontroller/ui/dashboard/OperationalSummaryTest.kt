package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.UploadJob
import com.example.jetsoncontroller.model.UploadJobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalSummaryTest {
    @Test
    fun `offline app does not claim collection stopped`() {
        val summary = operationalSummary(
            state = DashboardUiState(isOnline = false, fullControlAvailable = false),
            pipelines = emptyList(),
            uploads = emptyList(),
            tasksConfirmed = false
        )

        assertEquals("오프라인", summary.connection.value)
        assertEquals("현재 상태 미확인", summary.collection.value)
        assertTrue(summary.collection.detail.contains("중단을 판단하지 않습니다"))
        assertEquals(HomeActionDestination.CONNECTION, summary.nextAction.destination)
    }

    @Test
    fun `only observed running pipeline is called collection in progress`() {
        val starting = pipeline("starting", PipelineState.STARTING)
        val startingSummary = operationalSummary(
            DashboardUiState(isOnline = true, fullControlAvailable = true),
            listOf(starting),
            emptyList(),
            tasksConfirmed = true
        )
        assertEquals("변경 확인 중", startingSummary.collection.value)

        val runningSummary = operationalSummary(
            DashboardUiState(isOnline = true, fullControlAvailable = true),
            listOf(pipeline("running", PipelineState.RUNNING).copy(activeRunId = "run-1")),
            emptyList(),
            tasksConfirmed = true
        )
        assertEquals("수집 중", runningSummary.collection.value)
    }

    @Test
    fun `cached running state requires a fresh observation and active run id`() {
        val cached = pipeline("running", PipelineState.RUNNING).copy(activeRunId = "run-1")
        val summary = operationalSummary(
            DashboardUiState(isOnline = false, fullControlAvailable = false),
            listOf(cached), emptyList(), tasksConfirmed = false
        )

        assertEquals("최근 실행 보고 · 현재 미확인", summary.collection.value)
        assertEquals(com.example.jetsoncontroller.ui.components.StatusTone.WARNING, summary.collection.tone)
    }

    @Test
    fun `failed and unknown are not presented as collection stopped`() {
        listOf(PipelineState.FAILED, PipelineState.UNKNOWN).forEach { state ->
            val summary = operationalSummary(
                DashboardUiState(isOnline = true, fullControlAvailable = true),
                listOf(pipeline("problem", state)), emptyList(), tasksConfirmed = true
            )
            assertEquals("실행 결과 확인 필요", summary.collection.value)
        }
    }

    @Test
    fun `wifi flag alone never claims internet availability`() {
        val summary = operationalSummary(
            DashboardUiState(
                isOnline = true,
                fullControlAvailable = true,
                status = JetsonStatus(wifiConnected = true)
            ),
            emptyList(),
            emptyList(),
            tasksConfirmed = true
        )

        assertEquals("현재 상태 미확인", summary.internet.value)
        assertTrue(summary.internet.detail.contains("장비 Wi-Fi와 별도로"))
    }

    @Test
    fun `verified receiver evidence is distinct from current reachability`() {
        val job = UploadJob(
            id = "job", rootId = "r", relativePath = "run", targetId = "server",
            state = UploadJobState.COMPLETED, bytesTotal = 1, bytesTransferred = 1,
            filesTotal = 1, filesTransferred = 1, currentFile = null, errorMessage = null,
            verification = com.example.jetsoncontroller.model.UploadVerification(
                jobId = "job", state = "COMPLETED", matched = true, deletionAllowed = false
            )
        )
        val summary = operationalSummary(
            DashboardUiState(isOnline = true, fullControlAvailable = true),
            emptyList(), listOf(job), tasksConfirmed = true
        )

        assertEquals("수신 증거 있음", summary.internet.value)
        assertTrue(summary.internet.detail.contains("현재 도달성"))
    }

    @Test
    fun `home upload summary prioritizes active transfer then newest completed or failed`() {
        val completed = upload("completed", UploadJobState.COMPLETED)
        val failed = upload("failed", UploadJobState.FAILED)
        val active = upload("active", UploadJobState.UPLOADING)

        assertEquals("active", homeUploadSummaryJob(listOf(completed, active, failed))?.id)
        assertEquals("failed", homeUploadSummaryJob(listOf(failed, completed))?.id)
        assertEquals(null, homeUploadSummaryJob(listOf(upload("cancelled", UploadJobState.CANCELLED))))
    }

    @Test
    fun `rtk label reports observation without inventing a pass threshold`() {
        val summary = operationalSummary(
            DashboardUiState(
                isOnline = true,
                fullControlAvailable = true,
                statusFreshness = StatusFreshness.CURRENT,
                status = JetsonStatus(
                    sensorTelemetryAvailable = true,
                    sensorTelemetryFresh = true,
                    gnssSensor = GnssSensorStatus(
                        configured = true,
                        connected = true,
                        fixName = "RTK FIX",
                        rtkStatus = "fixed",
                        satellites = 18
                    )
                )
            ),
            emptyList(), emptyList(), tasksConfirmed = true
        )

        assertTrue(summary.positioning.value.contains("RTK FIX"))
        assertTrue(summary.positioning.detail.contains("위성 18개"))
    }

    private fun pipeline(id: String, state: PipelineState) = ManagedPipeline(
        id = id,
        label = id,
        state = state,
        entrypoint = "run.py",
        config = "config.yaml",
        virtualenv = "venv"
    )

    private fun upload(id: String, state: UploadJobState) = UploadJob(
        id = id,
        rootId = "recordings",
        relativePath = id,
        targetId = "server",
        state = state,
        bytesTotal = null,
        bytesTransferred = null,
        filesTotal = null,
        filesTransferred = null,
        currentFile = null,
        errorMessage = null
    )
}
