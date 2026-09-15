package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.ui.components.StatusTone
import com.example.jetsoncontroller.ui.dashboard.DashboardScreen
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.field.*
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

    @Test fun unknownActiveRunDisablesStopAndAllowsRefresh() {
        var refreshes = 0
        var stops = 0
        compose.setContent { JetsonControllerTheme {
            ActiveRunScreen(
                deviceName = "화면 테스트", connectionLabel = "연결 끊김",
                connectionTone = StatusTone.UNKNOWN, run = null, elapsedLabel = null,
                lastObservedLabel = null, sensorSummary = null, storageSummary = null,
                stopInProgress = false, online = false, unreadCount = 0,
                onDevices = {}, onAlerts = {}, onBack = {}, onRefresh = { refreshes++ },
                onStop = { stops++ }, onCamera = {}, onMap = {}
            )
        } }
        compose.onNodeWithText("수집 중지").assertIsNotEnabled()
        compose.onNodeWithText("실행 상태 다시 조회").performClick()
        compose.runOnIdle { assertEquals(1, refreshes); assertEquals(0, stops) }
        capture("active-run-unknown")
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
        compose.onNodeWithText("전송 조건 확인").assertIsNotEnabled()
        compose.onNodeWithText("실행 이력 열기").performClick()
        compose.runOnIdle { assertEquals(1, history); assertEquals(0, transfers) }
        capture("result-unknown-dark")
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
