package com.example.jetsoncontroller.ui.transfers

import com.example.jetsoncontroller.model.TaskRun
import com.example.jetsoncontroller.ui.field.canonicalHistoryRunId
import java.net.URLEncoder

internal fun storedRunSendRoute(run: TaskRun): String? {
    val runId = canonicalHistoryRunId(run) ?: return null
    val output = run.output ?: return null
    if (run.active || run.finishedAt == null || output.rootId.isBlank() || output.path.isBlank() ||
        output.outputId.isBlank()) return null
    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    return "upload_confirm/${encode(output.rootId)}?path=${encode(output.path)}&runId=${encode(runId)}"
}

/** Selection is reviewed in the browser; only acceptance of the start opens the detail. */
internal enum class TransferSendEvent { PICK_FILES, SELECT_SOURCE, START_ACCEPTED, OPEN_LOG }

internal fun transferSendDestination(event: TransferSendEvent): String? = when (event) {
    TransferSendEvent.PICK_FILES -> "data_hub"
    TransferSendEvent.SELECT_SOURCE -> null
    TransferSendEvent.START_ACCEPTED -> "upload_progress"
    TransferSendEvent.OPEN_LOG -> "upload_queue"
}
