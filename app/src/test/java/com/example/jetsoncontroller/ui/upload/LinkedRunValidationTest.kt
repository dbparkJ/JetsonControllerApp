package com.example.jetsoncontroller.ui.upload

import com.example.jetsoncontroller.model.PipelineRun
import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedRunValidationTest {
    private val run = Gson().fromJson(RUN, PipelineRun::class.java)

    @Test fun `canonical completed output matches selected device and path`() {
        assertTrue(linkedRunMatchesSource(run, "device-a", "data", "project/section/run"))
    }

    @Test fun `wrong device or path cannot reuse trusted upload context`() {
        assertFalse(linkedRunMatchesSource(run, "device-b", "data", "project/section/run"))
        assertFalse(linkedRunMatchesSource(run, "device-a", "data", "other"))
    }

    private companion object {
        const val RUN = """{"runId":"capture/run.log","logId":"run.log","pipelineId":"capture",
          "deviceId":"device-a","state":"COMPLETED","active":false,"startedAt":"start",
          "contextSnapshot":{"deviceId":"device-a","surveyProjectId":"project","surveyProjectLabel":"Project",
            "surveyProjectRevision":1,"surveySectionId":"section","surveySectionLabel":"Section",
            "surveySectionRevision":1,"capturedAt":"captured"},
          "policySnapshot":{"pipelineId":"capture","revision":"policy","outputRootId":"data","updatedAt":"updated"},
          "preflightSnapshot":{"preflightId":"preflight","pipelineId":"capture","deviceId":"device-a",
            "surveyProject":{"surveyProjectId":"project","label":"Project","revision":1},
            "surveySection":{"surveySectionId":"section","surveyProjectId":"project","label":"Section","revision":1},
            "contextSnapshot":{"deviceId":"device-a","surveyProjectId":"project","surveyProjectLabel":"Project",
              "surveyProjectRevision":1,"surveySectionId":"section","surveySectionLabel":"Section",
              "surveySectionRevision":1,"capturedAt":"captured"},
            "policy":{"pipelineId":"capture","revision":"policy","outputRootId":"data","updatedAt":"updated"},
            "sourceRevision":"source","configRevision":"config","checkedAt":"checked","checkedAtEpochMillis":1,
            "checks":{"time":{},"storage":{"rootId":"data","path":"project/section/run"}},"ready":true},
          "sourceRevision":"source","configRevision":"config",
          "output":{"outputId":"output","rootId":"data","path":"project/section/run","manifestState":"FINAL"},
          "uploadContext":{"schemaVersion":1,"surveyProjectId":"project","surveySectionId":"section","runId":"capture/run.log",
            "deviceId":"device-a","pipelineId":"capture","sourceRevision":"source","configSha256":"config",
            "outputId":"output","createdAt":"created"}}"""
    }
}
