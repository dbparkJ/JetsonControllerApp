package com.example.jetsoncontroller.ui.tasks

import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.field.canonicalHistoryRunId
import com.example.jetsoncontroller.ui.runResultRoute
import java.net.URLEncoder

/** A legacy runId alone cannot turn a saved log entry into a canonical collection result. */
internal fun workHistoryResultRoute(run: TaskRun, deviceId: String): String {
    val runId = canonicalHistoryRunId(run)
    if (runId != null) return runResultRoute(runId, deviceId, run.pipelineId, run.id)
    fun part(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    return "history_result/${part(run.id)}?deviceId=${part(deviceId)}"
}
