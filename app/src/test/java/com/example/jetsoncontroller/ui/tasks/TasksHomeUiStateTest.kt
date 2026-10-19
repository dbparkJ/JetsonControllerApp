package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineExecution
import com.example.jetsoncontroller.model.PipelineOutputManifest
import com.example.jetsoncontroller.model.PipelinePreflight
import com.example.jetsoncontroller.model.PipelinePreflightChecks
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.PipelineRunOutput
import com.example.jetsoncontroller.model.PipelineRunPolicy
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.PreflightCheck
import com.example.jetsoncontroller.model.PreflightStorageCheck
import com.example.jetsoncontroller.model.SurveyContextSnapshot
import com.example.jetsoncontroller.model.SurveyProject
import com.example.jetsoncontroller.model.SurveyProjectSummary
import com.example.jetsoncontroller.model.SurveySection
import com.example.jetsoncontroller.model.SurveySectionSummary
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.model.UploadContext
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_DEVICE_ID = "7f3a9c2e-1b4d-4e8a-9c0f-00000000abcd"

class TasksHomeUiStateTest {
    private val project = SurveyProject("project-1", "한빛시 도로대장", 1, "created", "updated")
    private val section = SurveySection("section-4", "project-1", "4구간", 1, "created", "updated")

    @Test
    fun `start is enabled when connected with a section and a policy`() {
        val state = buildTasksHomeUiState(input())

        assertTrue(state.pinned.startEnabled)
        assertNull(state.pinned.startBlockedReason)
        assertEquals("한빛시 도로대장 · 4구간", state.pinned.scopeLabel)
        assertEquals("마지막으로 고른 구간", state.pinned.scopeCaption)
    }

    @Test
    fun `start waits for a connection and says why in one line`() {
        val state = buildTasksHomeUiState(input(connected = false))

        assertFalse(state.pinned.startEnabled)
        assertEquals("장치와 연결되면 점검을 시작할 수 있습니다.", state.pinned.startBlockedReason)
    }

    @Test
    fun `no selected device shows a neutral name and never the device id`() {
        val state = buildTasksHomeUiState(input(deviceName = null, connected = false))

        assertEquals("선택한 장치", state.deviceName)
        assertFalse(state.hasDevice)
        assertFalse(state.pinned.startEnabled)
        assertFalse(state.toString().contains(TEST_DEVICE_ID))
    }

    @Test
    fun `a missing section blocks the start and asks the operator to pick one`() {
        val state = buildTasksHomeUiState(input(survey(section = null)))

        assertNull(state.pinned.scopeLabel)
        assertEquals("아직 고른 구간이 없습니다", state.pinned.scopeCaption)
        assertEquals("구간을 고르면 점검을 시작할 수 있습니다.", state.pinned.startBlockedReason)
    }

    @Test
    fun `choosing a section needs a connected device`() {
        val offline = buildTasksHomeUiState(input(survey(section = null)).copy(connected = false))
        val online = buildTasksHomeUiState(input(survey(section = null)).copy(connected = true))

        assertFalse(offline.pinned.changeEnabled)
        assertTrue(online.pinned.changeEnabled)
    }

    @Test
    fun `a running collection shows its card, blocks a new start, and stays out of recent`() {
        val survey = survey(activeRun = pipelineRun("run-live", active = true))
        val state = buildTasksHomeUiState(
            input(survey, runs = listOf(taskRun("run-live", active = true), taskRun("run-old")))
        )

        assertEquals("수집 중 · MMS-4DE0", state.activeRun?.caption)
        assertFalse(state.pinned.startEnabled)
        assertEquals("수집이 끝난 뒤 새 점검을 시작할 수 있습니다.", state.pinned.startBlockedReason)
        assertEquals(listOf("run-old"), state.recent.map { it.runId })
    }

    @Test
    fun `recent keeps the three newest finished runs, each with a three-cell receipt`() {
        val runs = (1..5).map { index ->
            taskRun("run-$index", startedAt = "2026-10-1${index}T00:00:00Z", finishedAt = "2026-10-1${index}T00:30:00Z")
        }
        val state = buildTasksHomeUiState(input(runs = runs))

        assertEquals(listOf("run-1", "run-2", "run-3"), state.recent.map { it.runId })
        state.recent.forEach { row ->
            assertEquals(listOf("수집 종료", "장치 저장", "서버 수신"), row.cells.map { it.title })
        }
    }

    @Test
    fun `recent meta shows start time, duration and stored size`() {
        val run = taskRun(
            "run-a",
            startedAt = "2026-10-15T00:12:00Z",
            finishedAt = "2026-10-15T00:54:00Z",
            bytes = 18L * 1024L * 1024L * 1024L
        )

        val row = buildTasksHomeUiState(input(runs = listOf(run))).recent.single()

        assertEquals("한빛시 도로대장 · 4구간", row.title)
        assertEquals("10월 15일 00:12 · 42분 · 19.3\u00a0GB", row.meta)
    }

    @Test
    fun `other tasks are sorted by label and show their last run time or none`() {
        val pipelines = listOf(
            pipeline("sensor", "센서 점검", PipelineExecution(startedAt = "2026-10-12T06:40:00Z", exitCode = 0)),
            pipeline("road", "도로 수집", execution = null)
        )

        val state = buildTasksHomeUiState(input(pipelines = pipelines))

        assertEquals(listOf("도로 수집", "센서 점검"), state.otherTasks.map { it.title })
        assertEquals("실행 기록 없음", state.otherTasks[0].caption)
        assertEquals("마지막 실행 10월 12일 06:40", state.otherTasks[1].caption)
    }

    @Test
    fun `developer mode leaves the daily task screen unchanged`() {
        assertEquals(
            "작업 등록은 관리자에게 요청하세요.",
            buildTasksHomeUiState(input()).otherTasksNote
        )
        assertEquals(
            "작업 등록은 관리자에게 요청하세요.",
            buildTasksHomeUiState(input(developerModeEnabled = true)).otherTasksNote
        )
    }

    @Test
    fun `task detail shows status and last result without log, config or editing rows`() {
        val rows = taskDetailRows(
            pipeline("road", "도로 수집", PipelineExecution(startedAt = "2026-10-12T06:40:00Z", exitCode = 0)),
            connected = true,
            zone = ZoneOffset.UTC
        )

        assertEquals(listOf("상태", "마지막 실행", "마지막 결과", "결과 저장 폴더"), rows.map { it.label })
        assertEquals("10월 12일 06:40", rows.first { it.label == "마지막 실행" }.value)
        assertEquals("정상 종료", rows.first { it.label == "마지막 결과" }.value)
        assertEquals("설정되지 않음", rows.first { it.label == "결과 저장 폴더" }.value)
        assertTrue(taskDeveloperRows(pipeline("road", "도로 수집")).any { it.label == "작업 ID" })
    }

    @Test
    fun `formatted task time is missing for blank or non-instant text`() {
        assertNull(formatTaskTime(null, ZoneOffset.UTC))
        assertNull(formatTaskTime("", ZoneOffset.UTC))
        assertNull(formatTaskTime("not a time", ZoneOffset.UTC))
        assertEquals("10월 12일 06:40", formatTaskTime("2026-10-12T06:40:00Z", ZoneOffset.UTC))
    }

    private fun input(
        survey: SurveyRunUiState = survey(),
        connected: Boolean = true,
        deviceName: String? = "MMS-4DE0",
        runs: List<TaskRun> = emptyList(),
        pipelines: List<ManagedPipeline> = emptyList(),
        developerModeEnabled: Boolean = false
    ) = TasksHomeInput(
        deviceName = deviceName,
        connected = connected,
        survey = survey,
        runs = runs,
        uploadJobs = emptyList(),
        pipelines = pipelines,
        developerModeEnabled = developerModeEnabled,
        zone = ZoneOffset.UTC
    )

    private fun survey(
        section: SurveySection? = this.section,
        activeRun: PipelineRun? = null
    ) = SurveyRunUiState(
        deviceId = TEST_DEVICE_ID,
        online = true,
        pipelineId = "pipe",
        selectedProject = project,
        selectedSection = section,
        policy = policy(),
        activeRun = activeRun
    )

    private fun policy() = PipelineRunPolicy(
        pipelineId = "pipe",
        revision = "policy",
        outputRootId = "data",
        updatedAt = "updated"
    )

    private fun pipeline(
        id: String,
        label: String,
        execution: PipelineExecution? = null
    ) = ManagedPipeline(
        id = id,
        label = label,
        state = PipelineState.STOPPED,
        entrypoint = "run.py",
        config = "config.yaml",
        virtualenv = "venv",
        execution = execution
    )

    private fun taskRun(
        id: String,
        startedAt: String = "2026-10-14T06:00:00Z",
        finishedAt: String? = "2026-10-14T06:42:00Z",
        active: Boolean = false,
        bytes: Long? = null
    ): TaskRun {
        val manifest = bytes?.let {
            PipelineOutputManifest(runId = id, generatedAt = startedAt, fileCount = 10, bytesTotal = it)
        }
        val output = PipelineRunOutput(
            rootId = "data",
            path = "runs/$id",
            outputId = "out-$id",
            manifestState = if (manifest != null) "FINAL" else "PENDING",
            manifest = manifest
        )
        return TaskRun(
            id = id,
            pipelineId = "pipe",
            label = "도로 수집",
            logId = "log-$id",
            startedAt = startedAt,
            finishedAt = finishedAt,
            state = if (active) "RUNNING" else "COMPLETED",
            exitCode = if (active) null else 0,
            runId = id,
            deviceId = TEST_DEVICE_ID,
            contextSnapshot = contextSnapshot(),
            output = output,
            active = active
        )
    }

    private fun pipelineRun(runId: String, active: Boolean): PipelineRun {
        val policy = policy()
        val output = PipelineRunOutput("data", "runs/$runId", "out-$runId", "PENDING")
        val context = contextSnapshot()
        val preflight = PipelinePreflight(
            preflightId = "preflight",
            pipelineId = "pipe",
            deviceId = TEST_DEVICE_ID,
            surveyProject = SurveyProjectSummary(project.surveyProjectId, project.label, project.revision),
            surveySection = SurveySectionSummary(section.surveySectionId, project.surveyProjectId, section.label, section.revision),
            contextSnapshot = context,
            policy = policy,
            sourceRevision = "source",
            configRevision = "config",
            checkedAt = "checked",
            checkedAtEpochMillis = 1L,
            checks = PipelinePreflightChecks(
                time = PreflightCheck("READY"),
                storage = PreflightStorageCheck("READY", "data", "", 100L, 0L)
            ),
            ready = true
        )
        return PipelineRun(
            runId = runId,
            logId = "log-$runId",
            pipelineId = "pipe",
            deviceId = TEST_DEVICE_ID,
            state = if (active) "RUNNING" else "COMPLETED",
            startedAt = "2026-10-17T01:00:00Z",
            finishedAt = if (active) null else "2026-10-17T01:30:00Z",
            contextSnapshot = context,
            policySnapshot = policy,
            preflightSnapshot = preflight,
            sourceRevision = "source",
            configRevision = "config",
            output = output,
            uploadContext = UploadContext(
                surveyProjectId = project.surveyProjectId,
                surveySectionId = section.surveySectionId,
                runId = runId,
                deviceId = TEST_DEVICE_ID,
                pipelineId = "pipe",
                sourceRevision = "source",
                configSha256 = "config",
                outputId = output.outputId,
                createdAt = "created"
            ),
            active = active
        )
    }

    private fun contextSnapshot() = SurveyContextSnapshot(
        deviceId = TEST_DEVICE_ID,
        surveyProjectId = project.surveyProjectId,
        surveyProjectLabel = project.label,
        surveyProjectRevision = project.revision,
        surveySectionId = section.surveySectionId,
        surveySectionLabel = section.label,
        surveySectionRevision = section.revision,
        capturedAt = "captured"
    )
}
