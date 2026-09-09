package com.example.jetsoncontroller.data.diagnostics

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.jetsoncontroller.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class DiagnosticHealth(
    val initialized: Boolean = false,
    val writtenRecords: Long = 0,
    val droppedRecords: Long = 0,
    val writeFailures: Long = 0,
    val fileCount: Int = 0,
    val storedBytes: Long = 0,
    val lastWrittenUtcMs: Long? = null,
    val lastIncidentUtcMs: Long? = null,
    val lastMarkerUtcMs: Long? = null,
    val incidentCapped: Boolean = false
)

/** Local-only best-effort recorder. Logging must never change connection success or cancellation. */
object ConnectionDiagnostics {
    const val MAX_BYTES = 7L * 1024 * 1024
    const val RETENTION_DAYS = 7
    private val runId = UUID.randomUUID().toString()
    private val salt = UUID.randomUUID().toString()
    private val sequence = AtomicLong()
    private val dropped = AtomicLong()
    private val failures = AtomicLong()
    private val written = AtomicLong()
    private val mutableHealth = MutableStateFlow(DiagnosticHealth())
    val health: StateFlow<DiagnosticHealth> = mutableHealth.asStateFlow()
    @Volatile private var writer: ThreadPoolExecutor? = null
    @Volatile private var store: DiagnosticFileStore? = null
    @Volatile private var identity: Map<String, Any> = emptyMap()
    @Volatile private var testSink: ((String, Map<String, Any?>, Boolean) -> Unit)? = null

    fun newId(): String = UUID.randomUUID().toString()

    /** Stable only in this process; never emits the input, including addresses or device IDs. */
    fun privateRef(value: String?): String? = try {
        value?.let { hash((salt + ":" + it).toByteArray()).take(16) }
    } catch (_: Exception) { null }

    @Synchronized
    fun initialize(context: Context) {
        if (writer != null) return
        try {
            val appContext = context.applicationContext
            val fileStore = DiagnosticFileStore(File(appContext.noBackupFilesDir, "connection-diagnostics"))
            val executor = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(256), { task ->
                Thread(task, "connection-diagnostics").apply { isDaemon = true }
            }, ThreadPoolExecutor.AbortPolicy())
            store = fileStore
            val started = DiagnosticRecord("app_started", System.currentTimeMillis(), SystemClock.elapsedRealtime(), sequence.incrementAndGet(), runId)
            executor.execute {
                try {
                    identity = collectIdentity(appContext)
                    publish(fileStore.append(started.copy(fields = identity)), started.utcMs)
                } catch (_: Exception) { recordWriteFailure() }
            }
            writer = executor
            mutableHealth.update { it.copy(initialized = true) }
            ContextCompat.registerReceiver(appContext, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    recordPowerState(context)
                }
            }, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            recordPowerState(appContext)
        } catch (_: Exception) { recordWriteFailure() }
    }

    fun record(event: String, fields: Map<String, Any?> = emptyMap(), incident: Boolean = false) {
        try {
            if (event !in DiagnosticPrivacy.events) return
            val safe = DiagnosticPrivacy.fields(fields)
            testSink?.invoke(event, safe, incident)
            val executor = writer ?: return
            val record = DiagnosticRecord(event, System.currentTimeMillis(), SystemClock.elapsedRealtime(), sequence.incrementAndGet(), runId, safe, incident)
            executor.execute {
                try { store?.let { publish(it.append(record.copy(droppedRecords = dropped.get(), writeFailures = failures.get())), record.utcMs, record.event) } }
                catch (_: Exception) { recordWriteFailure() }
            }
        } catch (_: Exception) {
            dropped.incrementAndGet()
            updateCounters()
        }
    }

    fun recordPowerState(context: Context) {
        try {
            val manager = context.getSystemService(PowerManager::class.java)
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            record("power_state", mapOf(
                "interactive" to manager.isInteractive,
                "idle" to manager.isDeviceIdleMode,
                "powerSave" to manager.isPowerSaveMode,
                "charging" to plugged?.takeIf { it >= 0 }?.let { it != 0 }
            ))
        } catch (_: Exception) { /* Permission/OS snapshots are optional, never connection policy. */ }
    }

    /** User chooses a destination through SAF. Only this logger's private bounded files are included. */
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val fileStore = store ?: error("Diagnostic logger unavailable")
        val executor = writer ?: error("Diagnostic logger unavailable")
        val completion = CompletableFuture<DiagnosticSnapshot>()
        // Snapshot behind earlier accepted events; bounded memory, no second disk archive.
        executor.execute {
          try {
            val healthSnapshot = health.value
            val snapshot = fileStore.snapshot(linkedMapOf(
            "schema" to 1, "source" to "android", "exportUtcMs" to System.currentTimeMillis(),
            "currentRunId" to runId, "identity" to identity,
            "writtenRecordsThisRun" to healthSnapshot.writtenRecords,
            "droppedRecordsThisRun" to healthSnapshot.droppedRecords,
            "writeFailuresThisRun" to healthSnapshot.writeFailures,
            "incidentCappedThisRun" to healthSnapshot.incidentCapped,
            "ringMaxBytes" to 4 * 1024 * 1024, "incidentMaxBytes" to 3 * 1024 * 1024,
            "retentionDays" to RETENTION_DAYS, "postWindowMs" to 60_000,
            "clock" to "utcMs=wall clock; elapsedMs=elapsedRealtime including sleep; cross-host offsets must be measured",
            "exportLimits" to "Snapshot memory is bounded to 7MiB plus 64KiB manifest. ZIP compression and document-provider writes occur outside the diagnostic writer. Events accepted after the snapshot request appear in the next export.",
            "limits" to "Best effort while process runs. No wake lock or foreground service. OS/process death can lose queued tail or leave a partial JSONL row; no rows does not prove link loss. Incident prelude max 512KiB and postlude up to 60s/remaining 1MiB; repeated failures coalesce for 120s. Events after export was queued appear in the next export. No kernel or radio journal is captured."
            ), System.currentTimeMillis())
            completion.complete(snapshot)
          } catch (failure: Exception) { completion.completeExceptionally(failure) }
        }
        completion.get().writeZip(output)
    }

    internal fun setSinkForTests(sink: ((String, Map<String, Any?>, Boolean) -> Unit)?) {
        testSink = sink
    }

    private fun publish(state: DiagnosticStoreState, utcMs: Long, event: String? = null) {
        val count = written.incrementAndGet()
        mutableHealth.update { previous -> DiagnosticHealth(
            initialized = true, writtenRecords = count,
            droppedRecords = dropped.get(), writeFailures = failures.get(),
            fileCount = state.files, storedBytes = state.bytes,
            lastWrittenUtcMs = utcMs, lastIncidentUtcMs = state.lastIncidentUtcMs,
            lastMarkerUtcMs = if (event == "user_marker") utcMs else previous.lastMarkerUtcMs,
            incidentCapped = state.incidentCapped
        ) }
    }

    private fun recordWriteFailure() {
        failures.incrementAndGet()
        updateCounters()
    }

    private fun updateCounters() {
        mutableHealth.update { it.copy(droppedRecords = dropped.get(), writeFailures = failures.get()) }
    }

    private fun collectIdentity(context: Context): Map<String, Any> = buildMap {
        put("versionCode", BuildConfig.VERSION_CODE)
        put("versionName", BuildConfig.VERSION_NAME)
        put("buildId", BuildConfig.DIAGNOSTICS_BUILD_ID)
        put("sdkInt", Build.VERSION.SDK_INT)
        val apks = listOf(context.applicationInfo.sourceDir) + context.applicationInfo.splitSourceDirs.orEmpty()
        put("apkSha256", apks.mapIndexed { index, path ->
            mapOf("index" to index, "sha256" to try { hashFile(File(path)) } catch (_: Exception) { "UNAVAILABLE" })
        })
        val signatures = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.map { hash(it.toByteArray()) }.orEmpty()
        } catch (_: Exception) { emptyList() }
        put("signingCertificateSha256", signatures)
    }

    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
