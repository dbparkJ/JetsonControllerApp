package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineExecution
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.field.resultTestRun
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.*
import org.junit.Test

class WorkTaskDetailPresentationTest {
    private val pipeline = ManagedPipeline("capture", "데이터 수집", state = PipelineState.STOPPED,
        entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv",
        execution = PipelineExecution(exitCode = 0))

    @Test fun `task status and last result carry badge tones without claiming storage from exit code`() {
        val rows = taskDetailRows(pipeline, true)
        assertEquals("대기", rows.first { it.label == "상태" }.value)
        assertEquals(Tone.UNKNOWN, rows.first { it.label == "상태" }.tone)
        assertEquals("정상 종료", rows.first { it.label == "마지막 결과" }.value)
        assertEquals(Tone.SUCCESS, rows.first { it.label == "마지막 결과" }.tone)
        assertEquals("미확인", taskDetailRows(pipeline, false).first { it.label == "상태" }.value)
    }

    @Test fun `storage badge is confirmed only by the matching saved collection`() {
        val result = resultTestRun()
        val run = TaskRun("capture/run.log", result.pipelineId, "데이터 수집", result.logId, result.startedAt,
            finishedAt = result.finishedAt, state = result.state, runId = result.runId,
            contextSnapshot = result.contextSnapshot, output = result.output)
        fun storage(entry: TaskRun) = taskDetailRows(pipeline, true, lastRun = entry).first { it.label == "마지막 결과" }
        assertEquals("저장 확인", storage(run).value)
        assertEquals(Tone.SUCCESS, storage(run).tone)
        assertEquals("미확인", storage(run.copy(contextSnapshot = null)).value)
        assertEquals(Tone.UNKNOWN, storage(run.copy(contextSnapshot = null)).tone)
        assertEquals("정상 종료", storage(run.copy(pipelineId = "other")).value)
        assertEquals("기록 없음", taskDetailRows(pipeline, true, lastRun = run.copy(pipelineId = "other"))
            .first { it.label == "마지막 실행" }.value)
        assertEquals("20261019_1636", taskDetailRows(pipeline, true, lastRun = run)
            .first { it.label == "결과 저장 폴더" }.value)
    }
}
