package com.example.jetsoncontroller.ui.survey

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.data.survey.PendingContextualStart
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

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

        compose.onNodeWithText("수집할 구간을 선택하세요").assertIsDisplayed()
        compose.onNodeWithText("시작 상태 확인").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("프로젝트 또는 조사 구간 만들기").assertIsNotEnabled()
        compose.onNodeWithText("정책 저장").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun defaultPreparationHidesTechnicalPolicyAndIdentifiers() {
        show(readyState())

        compose.onNodeWithText("서울 도로 조사").assertIsDisplayed()
        compose.onNodeWithText("강남대로 1구간").assertIsDisplayed()
        compose.onNodeWithText("정책 편집 · 관리자 설정").assertDoesNotExist()
        compose.onNodeWithText("점검 ID").assertDoesNotExist()
        compose.onNodeWithText("예상 파일 패턴 · 한 줄에 하나").assertDoesNotExist()
        capture("survey-check")
    }

    @Test fun preparationShowsProjectAndExplicitSectionSelection() {
        show(readyState().copy(preflight = null))

        compose.onNodeWithText("수집할 구간을 선택하세요").assertIsDisplayed()
        compose.onNodeWithText("강남대로 1구간").assertIsDisplayed()
        compose.onNodeWithText("테헤란로 2구간").assertIsDisplayed()
        compose.onNodeWithContentDescription("선택됨").assertIsDisplayed()
        compose.onNodeWithText("장치 점검").assertIsEnabled()
        capture("survey-prep")
    }

    @Test fun largeFontKeepsStartActionReachable() {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f)
            ) {
                JetsonControllerTheme {
                    SurveyRunScreen(
                        state = readyState(), onBack = {}, onRefresh = {}, onSelectProject = {},
                        onSelectSection = {}, onCreateProject = {}, onCreateSection = {},
                        onSensorRequirement = { _, _ -> }, onMinFreeBytes = {}, onOutputMinFiles = {},
                        onOutputMinBytes = {}, onOutputPatterns = {}, onOutputRoot = {}, onOutputPath = {},
                        onSavePolicy = {}, onPreflight = {}, onStart = {}, onRetryPendingStart = {},
                        onDismissMessage = {}
                    )
                }
            }
        }

        compose.onNodeWithText("수집 시작").assertIsDisplayed().assertIsEnabled()
        capture("survey-start-2x")
    }

    @Test fun readyPreflightStillRequiresExplicitConfirmationBeforeStart() {
        var starts = 0
        show(readyState(), onStart = { starts++ })

        compose.onNodeWithText("수집 시작").assertIsEnabled().performClick()
        compose.onNodeWithText("수집을 시작할까요?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithTag("confirm-start").performClick()
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
        val project = SurveyProject("project", "서울 도로 조사", 1, "c", "u")
        val section = SurveySection("section", "project", "강남대로 1구간", 1, "c", "u")
        val secondSection = SurveySection("section-2", "project", "테헤란로 2구간", 1, "c", "u")
        val policy = PipelineRunPolicy(
            pipelineId = "capture", revision = "policy", requiredSensors = listOf("camera"),
            outputRootId = "data", updatedAt = "u"
        )
        val context = SurveyContextSnapshot(
            deviceId = "MMS-4DE0", surveyProjectId = "project", surveyProjectLabel = "서울 도로 조사",
            surveyProjectRevision = 1, surveySectionId = "section", surveySectionLabel = "강남대로 1구간",
            surveySectionRevision = 1, capturedAt = "c"
        )
        val preflight = PipelinePreflight(
            preflightId = "preflight", pipelineId = "capture", deviceId = "MMS-4DE0",
            surveyProject = SurveyProjectSummary("project", "서울 도로 조사", 1),
            surveySection = SurveySectionSummary("section", "project", "강남대로 1구간", 1),
            contextSnapshot = context, policy = policy, sourceRevision = "source", configRevision = "config",
            checkedAt = "checked", checkedAtEpochMillis = 1,
            checks = PipelinePreflightChecks(
                time = PreflightCheck("READY"),
                storage = PreflightStorageCheck("READY", "data", "", 100, 0),
                sensors = listOf(PreflightSensorCheck("camera", "REQUIRED", "ACTIVE"))
            ), ready = true
        )
        return SurveyRunUiState(
            deviceId = "MMS-4DE0", online = true, pipelineId = "capture", pipelineLabel = "도로 영상 수집",
            projects = listOf(project), sections = listOf(section, secondSection), selectedProject = project,
            selectedSection = section, policy = policy, policyDraft = policy.toDraft(), preflight = preflight
        )
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "geo-field-ia-captures").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
