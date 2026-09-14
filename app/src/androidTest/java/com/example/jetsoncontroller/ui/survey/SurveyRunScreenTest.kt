package com.example.jetsoncontroller.ui.survey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SurveyRunScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unknownStartKeepsSameRequestActionAndLocksContext() {
        var retries = 0
        show(
            state = SurveyRunUiState(
                deviceId = "device-a", online = true, pipelineId = "capture", pipelineLabel = "도로 수집",
                pendingStart = PendingContextualStart(
                    "capture", "project", 1, "section", 1, "policy", "preflight", "request"
                )
            ),
            onRetry = { retries++ }
        )

        compose.onNodeWithText("시작 결과 미확인 · 저장된 요청 ID 재사용").assertIsDisplayed()
        compose.onNodeWithText("같은 요청 ID로 시작 결과 확인").assertIsEnabled().performClick()
        compose.onNodeWithText("새 프로젝트 이름").assertIsNotEnabled()
        compose.onNodeWithText("정책 저장").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun readyPreflightStillRequiresExplicitConfirmationBeforeStart() {
        var starts = 0
        show(readyState(), onStart = { starts++ })

        compose.onNodeWithText("확인 후 수집 시작").assertIsEnabled().performClick()
        compose.onNodeWithText("이 조사 범위로 수집을 시작할까요?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithText("점검 근거로 시작").performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    private fun show(
        state: SurveyRunUiState,
        onRetry: () -> Unit = {},
        onStart: () -> Unit = {}
    ) = compose.setContent { JetsonControllerTheme { SurveyRunScreen(
        state, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, onStart, onRetry, {}
    ) } }

    private fun readyState(): SurveyRunUiState {
        val project = SurveyProject("project", "P", 1, "c", "u")
        val section = SurveySection("section", "project", "S", 1, "c", "u")
        val policy = PipelineRunPolicy(
            pipelineId = "capture", revision = "policy", requiredSensors = listOf("camera"),
            outputRootId = "data", updatedAt = "u"
        )
        val context = SurveyContextSnapshot(
            deviceId = "device-a", surveyProjectId = "project", surveyProjectLabel = "P",
            surveyProjectRevision = 1, surveySectionId = "section", surveySectionLabel = "S",
            surveySectionRevision = 1, capturedAt = "c"
        )
        val preflight = PipelinePreflight(
            preflightId = "preflight", pipelineId = "capture", deviceId = "device-a",
            surveyProject = SurveyProjectSummary("project", "P", 1),
            surveySection = SurveySectionSummary("section", "project", "S", 1),
            contextSnapshot = context, policy = policy, sourceRevision = "source", configRevision = "config",
            checkedAt = "checked", checkedAtEpochMillis = 1,
            checks = PipelinePreflightChecks(
                time = PreflightCheck("READY"),
                storage = PreflightStorageCheck("READY", "data", "", 100, 0),
                sensors = listOf(PreflightSensorCheck("camera", "REQUIRED", "ACTIVE"))
            ), ready = true
        )
        return SurveyRunUiState(
            deviceId = "device-a", online = true, pipelineId = "capture", pipelineLabel = "도로 수집",
            projects = listOf(project), sections = listOf(section), selectedProject = project,
            selectedSection = section, policy = policy, policyDraft = policy.toDraft(), preflight = preflight
        )
    }
}
