package com.example.jetsoncontroller.data.diagnostics

import com.google.gson.Gson
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DiagnosticRecord(
    val event: String,
    val utcMs: Long,
    val elapsedMs: Long,
    val seq: Long,
    val runId: String,
    val fields: Map<String, Any> = emptyMap(),
    val incident: Boolean = false,
    val droppedRecords: Long = 0,
    val writeFailures: Long = 0
)

internal data class DiagnosticStoreLimits(
    val segmentBytes: Int = 1024 * 1024,
    val ringSegments: Int = 4,
    val incidentBytes: Int = 1024 * 1024,
    val incidentCount: Int = 3,
    val retentionMs: Long = 7L * 24 * 60 * 60 * 1000,
    val postWindowMs: Long = 60_000,
    val incidentCooldownMs: Long = 120_000
)

internal data class DiagnosticStoreState(
    val files: Int,
    val bytes: Long,
    val lastIncidentUtcMs: Long?,
    val incidentCapped: Boolean
)

internal class DiagnosticSnapshot(private val entries: List<Pair<String, ByteArray>>) {
    /** Slow document providers only block the exporter, never the diagnostic writer. */
    fun writeZip(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}

/** Single worker owns all mutation; export takes this same monitor for a consistent snapshot. */
internal class DiagnosticFileStore(
    private val directory: File,
    private val limits: DiagnosticStoreLimits = DiagnosticStoreLimits()
) {
    private val gson = Gson()
    private var activeIncident: File? = null
    private var activeUntilMs = 0L
    private var lastIncidentElapsedMs: Long? = null
    private var lastIncidentUtcMs: Long? = null
    private var incidentCapped = false

    init {
        require(limits.segmentBytes >= 1024 && limits.ringSegments >= 1)
        require(limits.incidentBytes >= 1024 && limits.incidentCount >= 1)
    }

    @Synchronized
    fun append(record: DiagnosticRecord): DiagnosticStoreState {
        check(directory.isDirectory || directory.mkdirs()) { "Diagnostic directory unavailable" }
        prune(record.utcMs)
        if (record.elapsedMs >= activeUntilMs) activeIncident = null
        val line = encode(record)
        require(line.size <= limits.segmentBytes && line.size <= limits.incidentBytes / 2)
        val shouldStart = record.incident && activeIncident == null &&
            (record.event == "user_marker" || lastIncidentElapsedMs == null ||
                record.elapsedMs - lastIncidentElapsedMs!! >= limits.incidentCooldownMs)
        if (shouldStart) {
            startIncident(record)
        }
        val current = ring(0)
        if (current.length() + line.size > limits.segmentBytes) rotate()
        current.appendBytes(line)
        activeIncident?.let { incident ->
            if (incident.length() + line.size <= limits.incidentBytes) {
                incident.appendBytes(line)
            } else {
                incidentCapped = true
            }
        }
        return state()
    }

    @Synchronized
    fun state(): DiagnosticStoreState {
        val files = files()
        val newestIncident = files.filter { it.name.startsWith("incident-") }
            .maxByOrNull(::incidentOrder)?.name?.split('-')?.getOrNull(2)?.toLongOrNull()
        return DiagnosticStoreState(files.size, files.sumOf(File::length), lastIncidentUtcMs ?: newestIncident, incidentCapped)
    }

    fun export(output: OutputStream, manifest: Map<String, Any>, nowUtcMs: Long) {
        snapshot(manifest, nowUtcMs).writeZip(output)
    }

    @Synchronized
    fun snapshot(manifest: Map<String, Any>, nowUtcMs: Long): DiagnosticSnapshot {
        check(directory.isDirectory || directory.mkdirs()) { "Diagnostic directory unavailable" }
        prune(nowUtcMs)
        val manifestBytes = gson.toJson(manifest).toByteArray(Charsets.UTF_8)
        require(manifestBytes.size <= 64 * 1024) { "Diagnostic manifest exceeds cap" }
        val entries = mutableListOf("manifest.json" to manifestBytes)
        var remainingBytes = limits.segmentBytes.toLong() * limits.ringSegments +
            limits.incidentBytes.toLong() * limits.incidentCount
        val selected = files().sortedBy(File::getName)
        require(selected.size <= limits.ringSegments + limits.incidentCount) { "Diagnostic file count exceeds cap" }
        selected.forEach { file ->
            val length = file.length()
            val perFileCap = if (file.name.startsWith("incident-")) limits.incidentBytes else limits.segmentBytes
            require(length <= perFileCap && length <= remainingBytes) { "Diagnostic file exceeds cap" }
            val bytes = ByteArray(length.toInt())
            file.inputStream().use { input ->
                var position = 0
                while (position < bytes.size) {
                    val read = input.read(bytes, position, bytes.size - position)
                    check(read > 0) { "Diagnostic file changed during snapshot" }
                    position += read
                }
                check(input.read() == -1) { "Diagnostic file grew during snapshot" }
            }
            remainingBytes -= bytes.size
            entries += file.name to bytes
        }
        return DiagnosticSnapshot(entries)
    }

    private fun startIncident(record: DiagnosticRecord) {
        // Storage order survives process restart and wall-clock rollback; UTC alone is not an order.
        val order = files().filter { it.name.startsWith("incident-") }.maxOfOrNull(::incidentOrder)?.plus(1) ?: 1L
        val incident = File(directory, "incident-$order-${record.utcMs}-${record.runId.take(8)}-${record.seq}.jsonl")
        val prelude = ArrayDeque<ByteArray>()
        var bytes = 0
        // Keep complete JSONL rows, newest half MiB at most; reserve half for the trigger/postlude.
        (limits.ringSegments - 1 downTo 0).map(::ring).filter(File::isFile).forEach { file ->
            file.useLines { lines ->
                lines.forEach { text ->
                    val line = (text + "\n").toByteArray(Charsets.UTF_8)
                    if (line.size <= limits.incidentBytes / 2) {
                        prelude.addLast(line)
                        bytes += line.size
                        while (bytes > limits.incidentBytes / 2) bytes -= prelude.removeFirst().size
                    }
                }
            }
        }
        // Reserve one slot before writing, so a new prelude cannot temporarily exceed the disk cap.
        trimIncidents(limits.incidentCount - 1)
        incident.outputStream().use { output -> prelude.forEach(output::write) }
        activeIncident = incident
        activeUntilMs = record.elapsedMs + limits.postWindowMs
        lastIncidentElapsedMs = record.elapsedMs
        lastIncidentUtcMs = record.utcMs
        incidentCapped = false
        trimIncidents()
    }

    private fun encode(record: DiagnosticRecord): ByteArray =
        (gson.toJson(linkedMapOf(
            "schema" to 1, "source" to "android", "runId" to record.runId,
            "seq" to record.seq, "utcMs" to record.utcMs, "elapsedMs" to record.elapsedMs,
            "event" to record.event, "incidentTrigger" to record.incident,
            "droppedRecordsThisRun" to record.droppedRecords, "writeFailuresThisRun" to record.writeFailures,
            "fields" to record.fields
        )) + "\n").toByteArray(Charsets.UTF_8)

    private fun rotate() {
        deleteChecked(ring(limits.ringSegments - 1))
        for (index in limits.ringSegments - 2 downTo 0) {
            val source = ring(index)
            if (source.exists()) check(source.renameTo(ring(index + 1))) { "Diagnostic rotation failed" }
        }
    }

    private fun prune(nowUtcMs: Long) {
        files().filter { nowUtcMs - it.lastModified() > limits.retentionMs }.forEach { file ->
            if (file == activeIncident) activeIncident = null
            deleteChecked(file)
        }
        trimIncidents()
    }

    private fun trimIncidents(keep: Int = limits.incidentCount) {
        files().filter { it.name.startsWith("incident-") }
            .sortedWith(compareByDescending<File> { it == activeIncident }.thenByDescending(::incidentOrder))
            .drop(keep).forEach(::deleteChecked)
    }

    private fun incidentOrder(file: File): Long = file.name.split('-').getOrNull(1)?.toLongOrNull() ?: 0L

    private fun deleteChecked(file: File) {
        if (file.exists()) check(file.delete()) { "Diagnostic retention failed" }
    }

    private fun ring(index: Int) = File(directory, "events-$index.jsonl")
    private fun files(): List<File> = directory.listFiles()?.filter {
        it.isFile && (it.name.matches(Regex("events-[0-9]+\\.jsonl")) ||
            it.name.matches(Regex("incident-[0-9]+-[0-9]+-[0-9a-f]{8}-[0-9]+\\.jsonl")))
    }.orEmpty()
}
