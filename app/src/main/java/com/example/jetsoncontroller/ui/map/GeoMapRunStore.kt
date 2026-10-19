package com.example.jetsoncontroller.ui.map

import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.google.gson.Gson
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

/** Immutable first binding. Ticket edits and later preparation drafts cannot replace it. */
internal class GeoMapRunStore(private val root: File) {
    private data class Bound(val deviceId: String, val runId: String, val geometry: GeoMapGeometry)
    private fun file(deviceId: String, runId: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest("${deviceId.lowercase(Locale.ROOT)}\u0000$runId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "map-runs/$key.json")
    }
    fun read(deviceId: String?, runId: String?): GeoMapGeometry? {
        if (deviceId.isNullOrBlank() || runId.isNullOrBlank()) return null
        return runCatching {
            val bound = Gson().fromJson(file(deviceId, runId).readText(Charsets.UTF_8), Bound::class.java)
            bound.geometry.takeIf { bound.deviceId.equals(deviceId, true) && bound.runId == runId }
        }.getOrNull()
    }
    private fun geometryFile(rowId: String, revision: String, hash: String): File =
        File(root, "map-geometries/${file("geometry:$rowId:$revision", hash).name}")

    fun cache(ticket: ServerTicket) {
        val revision = ticket.zoneRevision?.takeIf { it.isNotBlank() } ?: return
        val hash = ticket.zoneHash?.takeIf { it.isNotBlank() } ?: return
        val geometry = mapGeometry(ticket)
        if (geometry.state == GeoGeometryState.ABSENT) return
        val file = geometryFile(ticket.rowId, revision, hash)
        if (file.exists()) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(Gson().toJson(geometry), Charsets.UTF_8)
        }
    }

    fun bindAccepted(deviceId: String, runId: String, start: PendingContextualStart, candidates: List<ServerTicket>): GeoMapGeometry? {
        read(deviceId, runId)?.let { return it }
        if (!start.deviceId.equals(deviceId, true)) return null
        val rowId = start.ticketRowId ?: return null
        val revision = start.geometryRevision ?: return null
        val hash = start.geometryHash ?: return null
        val ticket = candidates.firstOrNull { it.rowId == rowId && it.revision == start.ticketRevision && it.zoneRevision == revision && it.zoneHash == hash }
        val geometry = if (ticket != null) mapGeometry(ticket) else runCatching {
            Gson().fromJson(geometryFile(rowId, revision, hash).readText(Charsets.UTF_8), GeoMapGeometry::class.java)
        }.getOrNull()
        return boundMapGeometry(geometry, revision, hash)?.let { freeze(deviceId, runId, it) }
    }
    @Synchronized
    internal fun freeze(deviceId: String, runId: String, geometry: GeoMapGeometry): GeoMapGeometry? {
        read(deviceId, runId)?.let { return it }
        val file = file(deviceId, runId)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        return runCatching {
            temporary.writeText(Gson().toJson(Bound(deviceId, runId, geometry)), Charsets.UTF_8)
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            geometry
        }.getOrNull()
    }
}
