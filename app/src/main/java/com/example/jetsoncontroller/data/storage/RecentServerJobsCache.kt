package com.example.jetsoncontroller.data.storage

import com.example.jetsoncontroller.data.server.ServerEndpointProfile
import com.example.jetsoncontroller.data.server.ServerJobsResponse
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class ServerJobsCacheKey(
    val environment: String,
    val baseUrl: String,
    val employeeId: String,
    val projectId: String
) {
    fun stableId(): String {
        val value = listOf(environment, baseUrl, employeeId, projectId).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun from(profile: ServerEndpointProfile): ServerJobsCacheKey {
            val checked = profile.validated()
            return ServerJobsCacheKey(
                environment = checked.environment.wireValue,
                baseUrl = checked.baseUrl,
                employeeId = checked.employeeId,
                projectId = checked.projectId
            )
        }
    }
}

data class CachedServerJobs(
    val key: ServerJobsCacheKey,
    val credentialRevision: String,
    val cachedAtEpochMillis: Long,
    val response: ServerJobsResponse
)

class RecentServerJobsCache(
    private val directory: File,
    private val gson: Gson = Gson()
) {
    @Synchronized
    fun read(key: ServerJobsCacheKey, credentialRevision: String): CachedServerJobs? {
        val file = cacheFile(key)
        if (!file.isFile) return null
        return runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), CachedServerJobs::class.java)
        }.getOrNull()?.takeIf {
            it.key == key && it.credentialRevision == credentialRevision
        }
    }

    @Synchronized
    fun write(entry: CachedServerJobs) {
        directory.mkdirs()
        check(directory.isDirectory) { "Server cache directory is unavailable" }
        val destination = cacheFile(entry.key)
        val temporary = File(directory, ".${destination.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(gson.toJson(entry).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun invalidate(key: ServerJobsCacheKey) {
        cacheFile(key).delete()
    }

    @Synchronized
    fun clear() {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".json") || file.name.contains(".tmp"))) {
                file.delete()
            }
        }
    }

    private fun cacheFile(key: ServerJobsCacheKey): File = File(directory, "${key.stableId()}.json")
}
