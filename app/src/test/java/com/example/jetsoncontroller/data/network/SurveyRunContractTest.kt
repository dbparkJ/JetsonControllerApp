package com.example.jetsoncontroller.data.network

import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineRun
import com.example.jetsoncontroller.model.SurveyProjectsResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyRunContractTest {
    private val gson = Gson()

    @Test
    fun `final contextual start fixture decodes without internal service fields`() {
        val pipeline = gson.fromJson(CONTEXTUAL_START_JSON, ManagedPipeline::class.java)

        assertEquals("capture", pipeline.id)
        assertEquals("capture/run-20260914T010001Z.log", pipeline.contextualStart!!.runId)
        assertEquals("project-1", pipeline.contextualStart!!.contextSnapshot.surveyProjectId)
        assertEquals(7, pipeline.contextualStart!!.contextSnapshot.surveySectionRevision)
        assertEquals("READY", pipeline.contextualStart!!.preflightSnapshot.checks.time.state)
        assertEquals("11111111-1111-4111-8111-111111111111", pipeline.contextualStart!!.preflightSnapshot.bootId)
        assertEquals(1_000_000L, pipeline.contextualStart!!.preflightSnapshot.checks.sensors.single().sampleAtEpochMillis)
        assertEquals("output-1", pipeline.contextualStart!!.output.outputId)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", pipeline.contextualStart!!.uploadContext.configSha256)
        assertEquals("PENDING", pipeline.contextualStart!!.output.manifestState)
    }

    @Test
    fun `canonical pipeline run fixture keeps immutable context policy preflight and manifest`() {
        val runJson = """{
            "schemaVersion":1,"runId":"capture/run-20260914T010001Z.log","logId":"run-20260914T010001Z.log","pipelineId":"capture",
            "deviceId":"device-a","state":"COMPLETED","active":false,
            "startedAt":"2026-09-14T01:00:01Z","finishedAt":"2026-09-14T01:10:00Z","exitCode":0,
            "contextSnapshot":$CONTEXT_JSON,"policySnapshot":$POLICY_JSON,
            "preflightSnapshot":$PREFLIGHT_JSON,"sourceRevision":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sourceDirty":false,"release":"1.17.0","configRevision":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "uploadContext":{"schemaVersion":1,"surveyProjectId":"project-1","surveySectionId":"section-1",
              "runId":"capture/run-20260914T010001Z.log","deviceId":"device-a","pipelineId":"capture",
              "sourceRevision":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","configSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","outputId":"output-1",
              "createdAt":"2026-09-14T01:00:01Z"},
            "output":{"outputId":"output-1","rootId":"collections","path":"project-1/section-1/run-20260914T010001Z",
              "manifestState":"FINAL","manifest":{"schemaVersion":1,"runId":"capture/run-20260914T010001Z.log",
                "generatedAt":"2026-09-14T01:10:00Z","finishedAt":"2026-09-14T01:10:00Z",
                "fileCount":12,"bytesTotal":3456,"truncated":false,
                "matchedPatterns":[{"pattern":"*.jpg","fileCount":10,"bytesTotal":3000}],
                "expected":{"minFiles":2,"minBytes":100,"patterns":["*.jpg"]},
                "expectationState":"SATISFIED"}}
        }""".trimIndent()

        val run = gson.fromJson(runJson, PipelineRun::class.java)

        assertFalse(run.active)
        assertEquals("section-1", run.contextSnapshot.surveySectionId)
        assertEquals("output-1", run.uploadContext.outputId)
        assertEquals("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", run.policySnapshot.revision)
        assertEquals(12, run.output.manifest!!.fileCount)
        assertEquals("SATISFIED", run.output.manifest!!.expectationState)
        assertFalse(run.sourceDirty!!)
        assertEquals("1.17.0", run.release)
    }

    @Test
    fun `project list is wrapped and legacy pipeline remains nullable`() {
        val projects = gson.fromJson(
            """{"projects":[{"surveyProjectId":"p","label":"P","revision":2,
              "createdAt":"a","updatedAt":"b"}]}""",
            SurveyProjectsResponse::class.java
        )
        val legacy = gson.fromJson(
            """{"id":"legacy","label":"Legacy","entrypoint":"run.py","config":"c","virtualenv":"v"}""",
            ManagedPipeline::class.java
        )

        assertEquals(2, projects.projects.single().revision)
        assertNull(legacy.runPolicy)
        assertNull(legacy.contextualStart)
    }

    private companion object {
        const val CONTEXT_JSON = """{"schemaVersion":1,"deviceId":"device-a",
          "surveyProjectId":"project-1","surveyProjectLabel":"Project 1","surveyProjectRevision":3,
          "surveySectionId":"section-1","surveySectionLabel":"Section 1","surveySectionRevision":7,
          "capturedAt":"2026-09-14T01:00:00Z"}"""
        const val POLICY_JSON = """{"schemaVersion":1,"pipelineId":"capture","policyVersion":4,
          "revision":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","configured":true,"requiredSensors":["camera","gnss"],
          "optionalSensors":["imu"],"minFreeBytes":2048,"outputRootId":"collections",
          "outputPath":"project-1/section-1","expectedOutput":{"minFiles":2,"minBytes":100,
          "patterns":["*.jpg"]},"updatedAt":"2026-09-14T00:59:00Z"}"""
        const val PREFLIGHT_JSON = """{"schemaVersion":1,"preflightId":"preflight-1",
          "pipelineId":"capture","deviceId":"device-a","bootId":"11111111-1111-4111-8111-111111111111",
          "surveyProject":{"surveyProjectId":"project-1","label":"Project 1","revision":3},
          "surveySection":{"surveySectionId":"section-1","surveyProjectId":"project-1",
            "label":"Section 1","revision":7},"contextSnapshot":$CONTEXT_JSON,
          "policy":$POLICY_JSON,"sourceRevision":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sourceDirty":false,"release":"1.17.0","configRevision":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          "checkedAt":"2026-09-14T01:00:00Z","checkedAtEpochMillis":1000000,
          "checks":{"time":{"state":"READY","observedAt":"2026-09-14T01:00:00Z",
            "source":"chrony","synchronizedAtEpochMillis":999000},
            "storage":{"state":"READY","rootId":"collections","path":"project-1/section-1",
              "availableBytes":99999,"requiredBytes":2048},
            "sensors":[{"sensor":"camera","requirement":"REQUIRED","state":"ACTIVE",
              "sampleAtEpochMillis":1000000,"ageMillis":0}]},"ready":true,"problems":[]}"""
        const val CONTEXTUAL_START_JSON = """{"id":"capture","label":"Capture",
          "entrypoint":"run.py","config":"c","virtualenv":"v","state":"STARTING",
          "runPolicy":$POLICY_JSON,"contextualStart":{"runId":"capture/run-20260914T010001Z.log","clientRequestId":"request-1",
          "outcome":"START_ACCEPTED","contextSnapshot":$CONTEXT_JSON,
          "preflightSnapshot":$PREFLIGHT_JSON,
          "output":{"outputId":"output-1","rootId":"collections","path":"project-1/section-1/run-20260914T010001Z",
            "manifestState":"PENDING","manifest":null},
          "uploadContext":{"schemaVersion":1,"surveyProjectId":"project-1","surveySectionId":"section-1",
            "runId":"capture/run-20260914T010001Z.log","deviceId":"device-a","pipelineId":"capture",
            "sourceRevision":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","configSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","outputId":"output-1",
            "createdAt":"2026-09-14T01:00:01Z"},
          "statusUrl":"/v1/pipeline-runs/capture/run-20260914T010001Z.log"}}"""
    }
}
