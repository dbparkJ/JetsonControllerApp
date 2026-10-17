package com.example.jetsoncontroller.data.server

import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class PendingTicketReport(
    val key: String,
    val rowId: String,
    val runId: String,
    val deviceId: String,
    val profileId: String,
    val createdAt: String
)

data class PendingTicketSelection(
    val profileId: String,
    val rowId: String,
    val title: String,
    val project: String?,
    val unit: String?,
    val deviceId: String,
    val pipelineId: String,
    val clientRequestId: String? = null
)

data class TicketRunLink(
    val runId: String,
    val profileId: String,
    val rowId: String,
    val title: String,
    val project: String?,
    val unit: String?,
    val deviceId: String,
    val pipelineId: String?
)

private data class TicketReportStoreData(
    val pendingReports: List<PendingTicketReport> = emptyList(),
    val links: List<TicketRunLink> = emptyList(),
    val pendingSelection: PendingTicketSelection? = null,
    val settledKeys: List<String> = emptyList(),
    val conflictRowIds: List<String> = emptyList(),
    val responseCodes: Map<String, Int> = emptyMap(),
    val errorCodes: Map<String, String> = emptyMap()
)

/** Small atomic local outbox and run-to-ticket index, independent of network availability. */
class TicketReportStore(
    private val file: File,
    private val gson: Gson = Gson()
) {
    @Synchronized
    fun pendingReports(): List<PendingTicketReport> = read().pendingReports

    /** A repeated enqueue with the same idempotency key preserves the original report. */
    @Synchronized
    fun enqueue(report: PendingTicketReport) {
        require(report.key == ticketReportKey(report.runId, report.key.endsWith(":completed"))) {
            "Ticket report key does not match its run and action"
        }
        val data = read()
        if (report.key in data.settledKeys) return
        if (data.pendingReports.any { it.key == report.key }) return
        write(data.copy(pendingReports = data.pendingReports + report))
    }

    @Synchronized
    fun removeReport(key: String, settled: Boolean = true) {
        val data = read()
        val next = data.pendingReports.filterNot { it.key == key }
        val settledKeys = if (settled && key !in data.settledKeys) data.settledKeys + key else data.settledKeys
        if (next.size != data.pendingReports.size || settledKeys != data.settledKeys) {
            write(data.copy(pendingReports = next, settledKeys = settledKeys))
        }
    }

    @Synchronized
    fun isSettled(key: String): Boolean = key in read().settledKeys

    @Synchronized
    fun markConflict(rowId: String) {
        if (rowId.isBlank()) return
        val data = read()
        if (rowId !in data.conflictRowIds) write(data.copy(conflictRowIds = data.conflictRowIds + rowId))
    }

    @Synchronized
    fun isConflict(rowId: String): Boolean = rowId in read().conflictRowIds

    @Synchronized
    fun applyResponse(key: String, statusCode: Int?, errorCode: String? = null) {
        if (!ticketReportResponseIsTerminal(statusCode)) return
        val data = read()
        val responseCodes = statusCode?.let { data.responseCodes + (key to it) } ?: data.responseCodes
        val errorCodes = if (errorCode.isNullOrBlank()) data.errorCodes - key
            else data.errorCodes + (key to errorCode)
        write(data.copy(
            pendingReports = data.pendingReports.filterNot { it.key == key },
            settledKeys = (data.settledKeys + key).distinct(),
            responseCodes = responseCodes,
            errorCodes = errorCodes
        ))
    }

    @Synchronized
    fun responseCode(key: String): Int? = read().responseCodes[key]

    @Synchronized
    fun errorCode(key: String): String? = read().errorCodes[key]

    @Synchronized
    fun savePendingSelection(selection: PendingTicketSelection) {
        write(read().copy(pendingSelection = selection))
    }

    @Synchronized
    fun pendingSelection(deviceId: String, pipelineId: String): PendingTicketSelection? =
        read().pendingSelection?.takeIf {
            it.deviceId.equals(deviceId, ignoreCase = true) && it.pipelineId == pipelineId
        }

    @Synchronized
    fun pendingSelectionForDevice(deviceId: String): PendingTicketSelection? =
        read().pendingSelection?.takeIf { it.deviceId.equals(deviceId, ignoreCase = true) }

    @Synchronized
    fun armPendingSelection(deviceId: String, pipelineId: String, clientRequestId: String) {
        val data = read()
        val selection = data.pendingSelection?.takeIf {
            it.deviceId.equals(deviceId, ignoreCase = true) && it.pipelineId == pipelineId
        } ?: return
        write(data.copy(pendingSelection = selection.copy(clientRequestId = clientRequestId)))
    }

    /** Binds the queued ticket selection to the accepted Jetson run and clears the one-shot intent. */
    @Synchronized
    fun bindPendingSelection(
        runId: String,
        deviceId: String,
        pipelineId: String,
        clientRequestId: String?
    ): TicketRunLink? {
        val data = read()
        val selection = data.pendingSelection?.takeIf {
            clientRequestId != null && it.clientRequestId == clientRequestId &&
                it.deviceId.equals(deviceId, ignoreCase = true) && it.pipelineId == pipelineId
        } ?: return data.links.firstOrNull { it.runId == runId }
        val link = TicketRunLink(
            runId = runId,
            profileId = selection.profileId,
            rowId = selection.rowId,
            title = selection.title,
            project = selection.project,
            unit = selection.unit,
            deviceId = selection.deviceId,
            pipelineId = selection.pipelineId
        )
        write(data.copy(
            links = (data.links.filterNot { it.runId == runId } + link),
            pendingSelection = null
        ))
        return link
    }

    @Synchronized
    fun saveServerRunLink(
        profileId: String,
        deviceId: String,
        ticket: ServerTicket
    ): TicketRunLink? {
        val runId = ticket.runId?.takeIf(String::isNotBlank) ?: return null
        val data = read()
        val link = TicketRunLink(
            runId = runId,
            profileId = profileId,
            rowId = ticket.rowId,
            title = ticket.title,
            project = ticket.project,
            unit = ticket.unit,
            deviceId = deviceId,
            pipelineId = null
        )
        write(data.copy(links = data.links.filterNot { it.runId == runId } + link))
        return link
    }

    @Synchronized
    fun link(runId: String): TicketRunLink? = read().links.firstOrNull { it.runId == runId }

    @Synchronized
    fun linksForRow(rowId: String): List<TicketRunLink> = read().links.filter { it.rowId == rowId }

    @Synchronized
    fun clearPendingSelection(deviceId: String, pipelineId: String) {
        val data = read()
        val selection = data.pendingSelection ?: return
        if (selection.deviceId.equals(deviceId, true) && selection.pipelineId == pipelineId) {
            write(data.copy(pendingSelection = null))
        }
    }

    private fun read(): TicketReportStoreData {
        if (!file.isFile) return TicketReportStoreData()
        val parsed = runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), TicketReportStoreData::class.java)
        }.getOrNull() ?: return TicketReportStoreData()
        return parsed.copy(
            pendingReports = parsed.pendingReports.orEmpty(),
            links = parsed.links.orEmpty(),
            settledKeys = parsed.settledKeys.orEmpty(),
            conflictRowIds = parsed.conflictRowIds.orEmpty(),
            responseCodes = parsed.responseCodes.orEmpty(),
            errorCodes = parsed.errorCodes.orEmpty()
        )
    }

    private fun write(data: TicketReportStoreData) {
        file.parentFile?.mkdirs()
        check(file.parentFile?.isDirectory == true) { "Ticket report storage is unavailable" }
        val temporary = File(file.parentFile, ".${file.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(gson.toJson(data).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }
}

fun ticketReportKey(runId: String, completed: Boolean): String =
    "run:$runId:${if (completed) "completed" else "started"}"

/** 202 and all terminal client errors remove an outbox entry; transport and 5xx errors retain it. */
fun ticketReportResponseIsTerminal(statusCode: Int?): Boolean =
    statusCode == 202 || (statusCode != null && statusCode in 400..499)
