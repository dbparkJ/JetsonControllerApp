package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.dashboard.DashboardScreen
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.field.*
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Screen fixtures: callbacks are counted without issuing device commands. */
class FieldStageScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dashboardStageActionOpensSurveyPreparation() {
        var destination: FieldDestination? = null
        val plan = fieldStagePlan(FieldStageInput(connected = true, runStateConfirmed = true))
        compose.setContent { JetsonControllerTheme {
            DashboardScreen(
                state = DashboardUiState(isOnline = true), pipelines = emptyList(),
                uploads = emptyList(), unreadAlertCount = 0, onAlertsClick = {},
                onDisconnect = {}, onRefreshFan = {}, onSetFanAuto = {}, onSetFanManual = {},
                onReboot = {}, onShutdown = {}, onStorageClick = {}, onNetworkSettingsClick = {},
                onUploadQueueClick = {}, onPipelinesClick = {}, onSectionSelected = {},
                onDismissOperationMessage = {}, onBack = {}, stagePlan = plan,
                onStageAction = { destination = it }
            )
        } }
        compose.onNodeWithText(plan.title).assertIsDisplayed()
        compose.onNodeWithText(plan.actionLabel).performClick()
        compose.runOnIdle { assertEquals(FieldDestination.SURVEY_PREP, destination) }
        capture("stage-action")
    }

    @Test fun largeFontHomeKeepsPrimaryStageActionReachable() {
        var destination: FieldDestination? = null
        val plan = fieldStagePlan(FieldStageInput(connected = true, runStateConfirmed = true))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JetsonControllerTheme {
                    DashboardScreen(
                        state = DashboardUiState(isOnline = true), pipelines = emptyList(),
                        uploads = emptyList(), unreadAlertCount = 0, onAlertsClick = {},
                        onDisconnect = {}, onRefreshFan = {}, onSetFanAuto = {}, onSetFanManual = {},
                        onReboot = {}, onShutdown = {}, onStorageClick = {}, onNetworkSettingsClick = {},
                        onUploadQueueClick = {}, onPipelinesClick = {}, onSectionSelected = {},
                        onDismissOperationMessage = {}, onBack = {}, tasksConfirmed = true,
                        stagePlan = plan, onStageAction = { destination = it }
                    )
                }
            }
        }

        compose.onNodeWithText(plan.actionLabel).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(FieldDestination.SURVEY_PREP, destination) }
    }

    @Test fun unknownActiveRunOffersRefreshWithoutStop() {
        var refreshes = 0
        var stops = 0
        compose.setContent { JetsonControllerTheme {
            ActiveRunScreen(
                deviceName = "화면 테스트", connectionLabel = "연결 끊김",
                connectionTone = StatusTone.UNKNOWN, run = null, elapsedLabel = null,
                lastObservedLabel = null, sensorSummary = null, storageSummary = null,
                stopInProgress = false, online = true, unreadCount = 0,
                onDevices = {}, onAlerts = {}, onBack = {}, onRefresh = { refreshes++ },
                onStop = { stops++ }, onCamera = {}, onMap = {}
            )
        } }
        compose.onNodeWithText("수집 종료").assertDoesNotExist()
        compose.onNodeWithText("수집 상태 다시 확인").performClick()
        compose.runOnIdle { assertEquals(1, refreshes); assertEquals(0, stops) }
        capture("active-run-unknown")
    }

    @Test fun pendingStartRetriesSameRequestAndCannotStop() {
        var retries = 0
        var stops = 0
        compose.setContent { JetsonControllerTheme {
            ActiveRunScreen(
                deviceName = "화면 테스트", connectionLabel = "연결됨",
                connectionTone = StatusTone.SUCCESS, run = null, elapsedLabel = null,
                lastObservedLabel = "13:21", sensorSummary = null, storageSummary = null,
                stopInProgress = false, online = true, pendingStart = true,
                unconfirmedRunId = "run-technical-id", onRetryPendingStart = { retries++ },
                unreadCount = 0, onDevices = {}, onAlerts = {}, onBack = {}, onRefresh = {},
                onStop = { stops++ }, onCamera = {}, onMap = {}
            )
        } }

        compose.onNodeWithText("시작 상태 확인").assertIsEnabled().performClick()
        compose.onNodeWithText("수집 종료").assertDoesNotExist()
        compose.onNodeWithText("run-technical-id").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(0, stops) }
        capture("active-run-pending")
    }

    @Test fun retainedUnconfirmedRunRefreshesStatusWithoutClaimingItIsActive() {
        var refreshes = 0
        var retries = 0
        var stops = 0
        compose.setContent { JetsonControllerTheme {
            ActiveRunScreen(
                deviceName = "MMS-4DE0", connectionLabel = "연결됨",
                connectionTone = StatusTone.SUCCESS, run = activeRun(), elapsedLabel = "00:24",
                lastObservedLabel = "방금", sensorSummary = "GPS 신호 정상", storageSummary = "확인 중",
                stopInProgress = false, online = true, stopAvailable = true,
                unconfirmedRunId = "run-1", onRetryPendingStart = { retries++ }, unreadCount = 0,
                onDevices = {}, onAlerts = {}, onBack = {}, onRefresh = { refreshes++ },
                onStop = { stops++ }, onCamera = {}, onMap = {}
            )
        } }

        compose.onNodeWithText("수집 상태").assertIsDisplayed()
        compose.onNodeWithText("수집이 진행 중입니다").assertDoesNotExist()
        compose.onNodeWithText("수집 종료").assertDoesNotExist()
        compose.onNodeWithText("수집 상태 확인").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(0, retries)
            assertEquals(0, stops)
        }
    }

    @Test fun missingResultDisablesTransferAndOffersHistory() {
        var history = 0
        var transfers = 0
        compose.setContent { JetsonControllerTheme(darkTheme = true) {
            RunResultScreen(
                deviceName = "화면 테스트", connectionLabel = "연결 끊김",
                connectionTone = StatusTone.UNKNOWN, run = null, uploadEnabled = false,
                uploadDisabledReason = "장치 연결 필요", serverReceiptLabel = null,
                online = false, unreadCount = 0, onDevices = {}, onAlerts = {},
                onBack = { history++ }, onRefresh = {}, onPrepareUpload = { transfers++ },
                onOpenFiles = {}, onNewSurvey = {}
            )
        } }
        compose.onNodeWithText("파일 전송 준비").assertIsNotEnabled()
        compose.onNodeWithText("수집 이력 열기").performClick()
        compose.runOnIdle { assertEquals(1, history); assertEquals(0, transfers) }
        capture("result-unknown-dark")
    }

    @Test fun terminalResultKeepsThreeIndependentFactsAndEnablesTransfer() {
        var transfers = 0
        val run = terminalRun()
        compose.setContent { JetsonControllerTheme {
            RunResultScreen(
                deviceName = "MMS-4DE0", connectionLabel = "연결됨",
                connectionTone = StatusTone.SUCCESS, run = run, uploadEnabled = true,
                uploadDisabledReason = "", serverReceiptLabel = null, online = true,
                unreadCount = 0, onDevices = {}, onAlerts = {}, onBack = {}, onRefresh = {},
                onPrepareUpload = { transfers++ }, onOpenFiles = {}, onNewSurvey = {}
            )
        } }

        compose.onNodeWithText("수집 종료 확인").assertIsDisplayed()
        compose.onNodeWithText("장치 저장 완료").assertIsDisplayed()
        compose.onNodeWithText("서버 수신 미확인").assertIsDisplayed()
        capture("result-terminal")
        compose.onNodeWithText("파일 전송 준비").assertIsEnabled().performClick()
        compose.onNodeWithText("/data/raw/run-1").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, transfers) }
    }

    @Test fun confirmedActiveRunRequiresConfirmationAndCallsStopOnce() {
        var stops = 0
        compose.setContent { JetsonControllerTheme {
            ActiveRunScreen(
                deviceName = "MMS-4DE0", connectionLabel = "연결됨",
                connectionTone = StatusTone.SUCCESS, run = activeRun(), elapsedLabel = null,
                lastObservedLabel = "2초 전", sensorSummary = "GPS 신호 정상",
                storageSummary = "확인 중", stopInProgress = false, online = true,
                stopAvailable = true, unreadCount = 0, onDevices = {}, onAlerts = {}, onBack = {},
                onRefresh = {}, onStop = { stops++ }, onCamera = {}, onMap = {}
            )
        } }

        compose.onNodeWithText("수집이 진행 중입니다").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithText("위치 정보 확인").assertIsDisplayed()
        capture("active-running")
        compose.onNodeWithText("수집 종료").assertIsEnabled().performClick()
        compose.onNodeWithText("수집을 종료할까요?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, stops) }
        captureDialog("active-stop-confirm")
        compose.onNodeWithTag("confirm-stop").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }

    @Test fun stopConfirmationClosesWhenRunCanNoLongerBeStopped() {
        var stops = 0
        val stopAvailable = mutableStateOf(true)
        compose.setContent { JetsonControllerTheme {
            ActiveRunScreen(
                deviceName = "MMS-4DE0", connectionLabel = "연결됨",
                connectionTone = StatusTone.SUCCESS, run = activeRun(), elapsedLabel = "00:24",
                lastObservedLabel = "방금", sensorSummary = null, storageSummary = "확인 중",
                stopInProgress = false, online = true, stopAvailable = stopAvailable.value,
                unreadCount = 0, onDevices = {}, onAlerts = {}, onBack = {}, onRefresh = {},
                onStop = { stops++ }, onCamera = {}, onMap = {}
            )
        } }

        compose.onNodeWithText("수집 종료").performClick()
        compose.onNodeWithText("수집을 종료할까요?").assertIsDisplayed()
        compose.runOnIdle { stopAvailable.value = false }
        compose.waitForIdle()
        compose.onNodeWithText("수집을 종료할까요?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, stops) }
    }

    @Test fun largeFontKeepsPendingRecoveryActionReachable() {
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f)
            ) {
                JetsonControllerTheme {
                    ActiveRunScreen(
                        deviceName = "큰 글꼴 화면 테스트 장치", connectionLabel = "연결됨",
                        connectionTone = StatusTone.SUCCESS, run = null, elapsedLabel = null,
                        lastObservedLabel = null, sensorSummary = null, storageSummary = null,
                        stopInProgress = false, online = true, pendingStart = true,
                        unreadCount = 0, onDevices = {}, onAlerts = {}, onBack = {}, onRefresh = {},
                        onStop = {}, onCamera = {}, onMap = {}
                    )
                }
            }
        }

        compose.onNodeWithText("시작 상태 확인").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("수집 종료").assertDoesNotExist()
        capture("active-pending-2x")
    }

    private fun activeRun(): PipelineRun = terminalRun().copy(
        state = "RUNNING", finishedAt = null, exitCode = null, active = true
    )

    private fun terminalRun(): PipelineRun {
        val context = SurveyContextSnapshot(
            deviceId = "device-a", surveyProjectId = "project", surveyProjectLabel = "서울 도로 조사",
            surveyProjectRevision = 1, surveySectionId = "section", surveySectionLabel = "강남대로 1구간",
            surveySectionRevision = 1, capturedAt = "2026-09-15T13:00:00Z"
        )
        val policy = PipelineRunPolicy(
            pipelineId = "capture", revision = "policy-r1", requiredSensors = listOf("camera"),
            expectedOutput = ExpectedOutputPolicy(minFiles = 1), outputRootId = "data", updatedAt = "now"
        )
        val preflight = PipelinePreflight(
            preflightId = "preflight-1", pipelineId = "capture", deviceId = "device-a",
            surveyProject = SurveyProjectSummary("project", "서울 도로 조사", 1),
            surveySection = SurveySectionSummary("section", "project", "강남대로 1구간", 1),
            contextSnapshot = context, policy = policy, sourceRevision = "source-r1", configRevision = "config-r1",
            checkedAt = "now", checkedAtEpochMillis = 1,
            checks = PipelinePreflightChecks(
                time = PreflightCheck("READY"),
                storage = PreflightStorageCheck("READY", "data", "/raw", 1024, 0),
                sensors = listOf(PreflightSensorCheck("camera", "REQUIRED", "ACTIVE"))
            ), ready = true
        )
        val manifest = PipelineOutputManifest(
            runId = "run-1", generatedAt = "now", finishedAt = "now",
            fileCount = 12, bytesTotal = 2_400_000_000L,
            expected = ExpectedOutputPolicy(minFiles = 1), expectationState = "PASSED"
        )
        val output = PipelineRunOutput("data", "/raw/run-1", "output-1", "FINAL", manifest)
        val upload = UploadContext(
            surveyProjectId = "project", surveySectionId = "section", runId = "run-1",
            deviceId = "device-a", pipelineId = "capture", sourceRevision = "source-r1",
            configSha256 = "config-r1", outputId = "output-1", createdAt = "now"
        )
        return PipelineRun(
            runId = "run-1", logId = "log-1", pipelineId = "capture", deviceId = "device-a",
            state = "FINISHED", startedAt = "13:00", finishedAt = "13:24", exitCode = 0,
            contextSnapshot = context, policySnapshot = policy, preflightSnapshot = preflight,
            sourceRevision = "source-r1", configRevision = "config-r1", output = output,
            uploadContext = upload, active = false
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

    private fun captureDialog(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null), "geo-field-ia-captures").apply { mkdirs() }
        compose.onNode(isDialog()).captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
