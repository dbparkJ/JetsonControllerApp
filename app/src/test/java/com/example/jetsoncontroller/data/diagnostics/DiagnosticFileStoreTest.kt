package com.example.jetsoncontroller.data.diagnostics

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class DiagnosticFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val runId = "11111111-2222-3333-4444-555555555555"
    private val now = System.currentTimeMillis()
    private val smallLimits = DiagnosticStoreLimits(segmentBytes = 1024, incidentBytes = 1024)

    private fun record(sequence: Long, elapsed: Long = sequence, incident: Boolean = false, event: String = "api_response") =
        DiagnosticRecord(event, now + sequence, elapsed, sequence, runId, mapOf("success" to true), incident)

    @Test fun `rotation and incident floods stay within total cap and preserve complete rows`() {
        val folder = temporary.newFolder()
        val store = DiagnosticFileStore(folder, smallLimits)
        repeat(150) { index ->
            store.append(record(index.toLong(), incident = index % 5 == 0, event = if (index % 5 == 0) "user_marker" else "api_response"))
        }
        assertTrue(store.state().bytes <= 7 * 1024)
        assertTrue(store.state().files <= 7)
        folder.listFiles()!!.forEach { file ->
            assertTrue(file.length() <= 1024)
            file.readLines().forEach { line -> assertEquals(1, JsonParser.parseString(line).asJsonObject["schema"].asInt) }
        }
        assertTrue(File(folder, "events-0.jsonl").readText().contains("\"seq\":149"))
    }

    @Test fun `incident keeps previous success failure trigger and bounded post window`() {
        val folder = temporary.newFolder()
        val store = DiagnosticFileStore(folder)
        store.append(record(1, elapsed = 0))
        store.append(record(2, elapsed = 100, incident = true, event = "api_failure"))
        store.append(record(3, elapsed = 59_000))
        store.append(record(4, elapsed = 60_100))
        val incident = folder.listFiles()!!.single { it.name.startsWith("incident-") }.readText()
        assertTrue(incident.contains("\"seq\":1"))
        assertTrue(incident.contains("\"seq\":2"))
        assertTrue(incident.contains("\"seq\":3"))
        assertFalse(incident.contains("\"seq\":4"))
    }

    @Test fun `automatic failures coalesce and a later user marker can preserve a new incident`() {
        val folder = temporary.newFolder()
        val store = DiagnosticFileStore(folder)
        store.append(record(1, elapsed = 0, incident = true, event = "api_failure"))
        store.append(record(2, elapsed = 61_000, incident = true, event = "api_failure"))
        assertEquals(1, folder.listFiles()!!.count { it.name.startsWith("incident-") })
        store.append(record(3, elapsed = 62_000, incident = true, event = "user_marker"))
        assertEquals(2, folder.listFiles()!!.count { it.name.startsWith("incident-") })
    }

    @Test fun `postlude stops at cap while ordinary ring logging continues`() {
        val folder = temporary.newFolder()
        val store = DiagnosticFileStore(folder, smallLimits)
        store.append(record(1, incident = true))
        repeat(50) { store.append(record(it + 2L)) }
        assertTrue(store.state().incidentCapped)
        assertTrue(File(folder, "events-0.jsonl").readText().contains("\"seq\":51"))
        assertTrue(folder.listFiles()!!.single { it.name.startsWith("incident-") }.length() <= 1024)
    }

    @Test fun `retention removes old diagnostic files and never exports unrelated private files`() {
        val folder = temporary.newFolder()
        val old = File(folder, "events-1.jsonl").apply { writeText("old\n") }
        assertTrue(old.setLastModified(now - 8L * 24 * 60 * 60 * 1000))
        File(folder, "credentials.json").writeText("DO_NOT_EXPORT")
        val store = DiagnosticFileStore(folder)
        store.append(record(1))
        assertFalse(old.exists())
        val bytes = ByteArrayOutputStream()
        store.export(bytes, mapOf("schema" to 1), now)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(setOf("manifest.json", "events-0.jsonl"), entries.keys)
        assertFalse(entries.values.any { it.contains("DO_NOT_EXPORT") })
    }

    @Test fun `disk failure is detectable and existing evidence is preserved`() {
        val blocked = temporary.newFile("blocked")
        blocked.writeText("existing evidence")
        val store = DiagnosticFileStore(blocked)
        assertThrows(IllegalStateException::class.java) { store.append(record(1)) }
        assertEquals("existing evidence", blocked.readText())
    }

    @Test fun `existing files survive a new process and incident trigger timestamp remains identifiable`() {
        val folder = temporary.newFolder()
        DiagnosticFileStore(folder).append(record(1, incident = true, event = "user_marker"))
        val restarted = DiagnosticFileStore(folder)
        assertEquals(now + 1, restarted.state().lastIncidentUtcMs!!)
        restarted.append(record(2).copy(runId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
        val text = File(folder, "events-0.jsonl").readText()
        assertTrue(text.contains(runId))
        assertTrue(text.contains("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
    }

    @Test fun `wall clock rollback cannot evict newest incident or exceed incident count`() {
        val folder = temporary.newFolder()
        val store = DiagnosticFileStore(folder)
        repeat(3) { index ->
            store.append(record(index + 1L, elapsed = index * 120_000L, incident = true, event = "user_marker"))
        }
        val rollbackRecord = record(4, elapsed = 400_000, incident = true, event = "user_marker").copy(utcMs = now - 60_000)
        store.append(rollbackRecord)
        store.append(record(5, elapsed = 400_001).copy(utcMs = now - 59_999))
        var incidents = folder.listFiles()!!.filter { it.name.startsWith("incident-") }
        assertEquals(3, incidents.size)
        assertTrue(incidents.single { it.name.startsWith("incident-4-") }.readText().contains("\"seq\":5"))
        assertFalse(incidents.any { it.name.startsWith("incident-1-") })
        DiagnosticFileStore(folder).append(record(6, elapsed = 1, incident = true, event = "user_marker").copy(utcMs = now - 120_000))
        incidents = folder.listFiles()!!.filter { it.name.startsWith("incident-") }
        assertEquals(3, incidents.size)
        assertTrue(incidents.any { it.name.startsWith("incident-5-") })
        assertFalse(incidents.any { it.name.startsWith("incident-2-") })
    }

    @Test fun `blocked export destination does not hold the file store writer`() {
        val folder = temporary.newFolder()
        val store = DiagnosticFileStore(folder)
        store.append(record(1))
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val export = executor.submit {
                store.export(object : OutputStream() {
                    override fun write(value: Int) {
                        blocked.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                }, mapOf("schema" to 1), now)
            }
            assertTrue(blocked.await(5, TimeUnit.SECONDS))
            executor.submit { store.append(record(2)) }.get(2, TimeUnit.SECONDS)
            assertTrue(File(folder, "events-0.jsonl").readText().contains("\"seq\":2"))
            release.countDown()
            export.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `oversize files are rejected before snapshot allocation`() {
        val folder = temporary.newFolder()
        File(folder, "events-0.jsonl").writeBytes(ByteArray(2048))
        assertThrows(IllegalArgumentException::class.java) {
            DiagnosticFileStore(folder, smallLimits).snapshot(mapOf("schema" to 1), now)
        }
    }
}
