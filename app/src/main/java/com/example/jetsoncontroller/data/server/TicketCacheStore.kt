package com.example.jetsoncontroller.data.server

import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class TicketCacheSnapshot(
    val profileId: String,
    val deviceId: String,
    val tickets: List<ServerTicket>,
    val units: List<String>,
    val deviceUnit: String?,
    val selectedUnit: String?,
    val capabilities: ServerCapabilities?,
    val integration: ServerTicketIntegration?,
    val cachedAt: String
)

private data class TicketCacheData(
    val snapshots: List<TicketCacheSnapshot> = emptyList()
)

/** Disk cache for ticket content only, isolated by server profile and device. */
class TicketCacheStore(
    private val file: File,
    private val gson: Gson = Gson()
) {
    @Synchronized
    fun read(profileId: String, deviceId: String): TicketCacheSnapshot? = readAll()
        .firstOrNull { it.profileId == profileId && it.deviceId.equals(deviceId, ignoreCase = true) }

    @Synchronized
    fun save(snapshot: TicketCacheSnapshot) {
        require(snapshot.profileId.isNotBlank() && snapshot.deviceId.isNotBlank()) {
            "Ticket cache scope is required"
        }
        val data = readAllData()
        val normalized = snapshot.copy(tickets = snapshot.tickets.toList(), units = snapshot.units.toList())
        val next = data.copy(snapshots = data.snapshots.filterNot {
            it.profileId == snapshot.profileId && it.deviceId.equals(snapshot.deviceId, ignoreCase = true)
        } + normalized)
        write(next)
    }

    private fun readAll(): List<TicketCacheSnapshot> = readAllData().snapshots

    private fun readAllData(): TicketCacheData {
        if (!file.isFile) return TicketCacheData()
        val parsed = runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), TicketCacheData::class.java)
        }.getOrNull() ?: return TicketCacheData()
        return parsed.copy(snapshots = parsed.snapshots.orEmpty().filter {
            !it.profileId.isNullOrBlank() && !it.deviceId.isNullOrBlank()
        }.map { snapshot ->
            snapshot.copy(tickets = snapshot.tickets.orEmpty(), units = snapshot.units.orEmpty())
        })
    }

    private fun write(data: TicketCacheData) {
        file.parentFile?.mkdirs()
        check(file.parentFile?.isDirectory == true) { "Ticket cache storage is unavailable" }
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
