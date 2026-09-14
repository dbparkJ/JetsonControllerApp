package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.model.UploadTarget
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineFolderDiscovery
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class ApiCompatibilityTest {
    @Test
    fun `all Retrofit service signatures are resolvable`() {
        Retrofit.Builder()
            .baseUrl("https://127.0.0.1/")
            .addConverterFactory(GsonConverterFactory.create())
            .validateEagerly(true)
            .build()
            .create(LocalControlApi::class.java)
    }

    @Test
    fun `legacy upload target responses remain readable`() {
        val target = Gson().fromJson(
            """{"id":"external","label":"External"}""",
            UploadTarget::class.java
        )

        assertEquals("external", target.id)
        assertNull(target.baseUrl)
        assertFalse(target.editable)
    }

    @Test
    fun `sensor telemetry and legacy status responses remain readable`() {
        val gson = Gson()
        val telemetry = gson.fromJson(
            """{
                "sensorTelemetryAvailable":true,
                "sensorTelemetryFresh":true,
                "cameraSensor":{"active":true,"frameWidth":1280,"frameHeight":720},
                "gnssSensor":{"active":true,"fixType":"rtk_fixed","latitude":37.5,"longitude":127.0},
                "imuSensor":{"active":true,"source":"oak"}
            }""".trimIndent(),
            JetsonStatus::class.java
        )
        val legacy = gson.fromJson(
            """{"cameraRunning":true,"gnssRunning":true}""",
            JetsonStatus::class.java
        )

        assertTrue(telemetry.cameraSensor.active)
        assertEquals("rtk_fixed", telemetry.gnssSensor.fixType)
        assertEquals(127.0, telemetry.gnssSensor.longitude!!, 0.0)
        assertTrue(legacy.cameraRunning)
        assertEquals("none", legacy.gnssSensor.fixType)
    }

    @Test
    fun `pipeline execution evidence is additive and legacy defaults remain safe`() {
        val gson = Gson()
        val current = gson.fromJson(
            """{
                "id":"capture","label":"Capture","entrypoint":"capture.py",
                "config":"capture.yaml","virtualenv":".venv","state":"RUNNING",
                "observedAt":"2026-09-14T01:02:03Z","activeRunId":"capture/run-1.log",
                "execution":{"runId":"capture/run-1.log","logId":"run-1.log","active":true,
                    "startedAt":"2026-09-14T01:00:00Z","sourceDirty":false,
                    "storageAvailableBytes":4096,"storageRequiredBytes":1024,
                    "storagePreflight":"passed"},
                "control":{"action":"start","commandIssued":false,"outcome":"ALREADY_SATISFIED"}
            }""".trimIndent(),
            ManagedPipeline::class.java
        )
        val legacy = gson.fromJson(
            """{"id":"legacy","label":"Legacy","entrypoint":"run.py",
                "config":"config.yaml","virtualenv":".venv"}""",
            ManagedPipeline::class.java
        )
        val legacyDiscovery = gson.fromJson(
            """{"pipelineId":"legacy","repository":"repo","virtualenv":".venv",
                "entrypoint":"run.py","config":"config.yaml","workingDirectory":".",
                "resultsDirectory":"results","resultsExists":false,"logDirectory":"logs"}""",
            PipelineFolderDiscovery::class.java
        )

        assertEquals("capture/run-1.log", current.activeRunId)
        assertTrue(current.execution!!.active)
        assertEquals(4096L, current.execution!!.storageAvailableBytes)
        assertEquals("ALREADY_SATISFIED", current.control!!.outcome)
        assertNull(legacy.observedAt)
        assertNull(legacy.activeRunId)
        assertNull(legacy.execution)
        assertNull(legacy.failureKind)
        assertNull(legacy.control)
        assertFalse(legacyDiscovery.autostartDefault)
    }

    @Test
    fun `unsigned status maps only recoverable responses to typed errors`() {
        assertTrue(unsignedResponseException(401) is JetsonSessionExpiredException)
        assertTrue(unsignedResponseException(404) is JetsonEndpointUnavailableException)
        assertTrue(unsignedResponseException(405) is JetsonEndpointUnavailableException)

        val serverError = unsignedResponseException(500)
        assertTrue(serverError is JetsonUnsignedServerErrorException)
        assertTrue(serverError.message.orEmpty().contains("HTTP 500"))
    }

    @Test
    fun `legacy fallback runs only when endpoint is unavailable`() = runBlocking {
        var fallbackCalls = 0
        val value = withLegacyEndpointFallback(
            call = { throw JetsonEndpointUnavailableException() },
            fallback = {
                fallbackCalls += 1
                "legacy"
            }
        )

        assertEquals("legacy", value)
        assertEquals(1, fallbackCalls)
    }

    @Test(expected = IOException::class)
    fun `legacy fallback does not hide integrity failures`() {
        runBlocking {
            withLegacyEndpointFallback(
                call = { throw IOException("invalid signature") },
                fallback = { "legacy" }
            )
        }
    }
}
