package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.model.PipelineRunOutput
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.PipelineOutputManifest
import com.example.jetsoncontroller.model.SurveyContextSnapshot
import org.junit.Assert.*
import org.junit.Test

/** F6 routing contracts used by TransferGraph; device QA exercises the composed three-tap journey. */
class TapCountTest {
    @Test fun `F6 file selection keeps the review in the browser and accepted start opens detail`() {
        var destination = "transfers"
        var taps = 0
        listOf(TransferSendEvent.PICK_FILES, TransferSendEvent.SELECT_SOURCE, TransferSendEvent.START_ACCEPTED).forEach { event ->
            taps++
            transferSendDestination(event)?.let { destination = it }
            when (event) {
                TransferSendEvent.PICK_FILES, TransferSendEvent.SELECT_SOURCE -> assertEquals("data_hub", destination)
                TransferSendEvent.START_ACCEPTED -> assertEquals("upload_progress", destination)
                else -> fail("Unexpected action")
            }
        }
        assertEquals(3, taps)
        assertNull(transferSendDestination(TransferSendEvent.SELECT_SOURCE))
    }

    @Test fun `saved run send opens confirmation for that canonical run and preserves encoded source identity`() {
        val route = storedRunSendRoute(run())
        assertEquals("upload_confirm/data%2Fa?path=capture%2Fold%20run&runId=run%2Fold%201", route)
        assertNotEquals(storedRunSendRoute(run().copy(runId = "new-run")), route)
        assertEquals("upload_queue", transferSendDestination(TransferSendEvent.OPEN_LOG))
    }

    @Test fun `active legacy and incomplete sources cannot become send confirmation targets`() {
        val run = run()
        assertNull(storedRunSendRoute(run.copy(active = true)))
        assertNull(storedRunSendRoute(run.copy(finishedAt = null)))
        assertNull(storedRunSendRoute(run.copy(runId = null, contextSnapshot = null)))
        assertNull(storedRunSendRoute(run.copy(output = null)))
        assertNull(storedRunSendRoute(run.copy(output = run.output!!.copy(outputId = ""))))
    }

    private fun run() = TaskRun("history", "capture", "수집 결과", "log", "2026-10-19T07:36:00Z",
        finishedAt = "2026-10-19T07:50:00Z", runId = "run/old 1", deviceId = "device-a",
        contextSnapshot = SurveyContextSnapshot(deviceId = "device-a", surveyProjectId = "project", surveyProjectLabel = "프로젝트",
            surveyProjectRevision = 1, surveySectionId = "section", surveySectionLabel = "구간", surveySectionRevision = 1,
            capturedAt = "2026-10-19T07:36:00Z"),
        output = PipelineRunOutput("data/a", "capture/old run", "output-a", "FINAL",
            PipelineOutputManifest(runId = "run/old 1", generatedAt = "2026-10-19T07:50:00Z")))
}
