package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.server.ServerTicket
import com.example.jetsoncontroller.data.server.ServerTicketStatus
import com.example.jetsoncontroller.model.CameraSensorStatus
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.devices.DeviceTitleBar
import com.example.jetsoncontroller.ui.devices.DevicesHomeScreen
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.sensors.*
import com.example.jetsoncontroller.ui.settings.ThemeScreen
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import com.example.jetsoncontroller.ui.tasks.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import com.example.jetsoncontroller.ui.theme.GeoPalette
import com.example.jetsoncontroller.ui.upload.UploadProgressScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Run on the supervisor's device; these fixtures never connect to a Jetson. */
class R7LandscapeLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deviceSelectionFillMatchesCardWidthWithoutMovingRowContentInAllPalettes() {
        var palette by mutableStateOf(GeoPalette.STANDARD)
        var rowCount by mutableIntStateOf(1)
        var highlighted by mutableStateOf<String?>(null)
        val devices = (0..2).map { RegisteredDevice("device-$it", "GEO-X-${it + 1}") }
        compose.setContent {
            Viewport(1280f, 776f, rail = true) {
                JetsonControllerTheme(palette = palette) {
                    GeoPaneLayout(list = {
                        DevicesHomeScreen(registeredDevices = devices.take(rowCount),
                            selectedDeviceId = devices.first().deviceId, highlightedDeviceId = highlighted,
                            currentDashboard = DashboardUiState(isOnline = true, statusFreshness = StatusFreshness.CURRENT),
                            snapshots = emptyMap(), availableDeviceIds = emptySet(),
                            connectingDeviceId = null, connectFailedDeviceId = null,
                            runActive = false, attention = null, unreadAlertCount = 0, nowEpochMillis = 1L,
                            developerModeEnabled = false, onSectionSelected = {}, onAlertsClick = {}, onAddDevice = {},
                            onServerFiles = {}, onOpenDevice = {}, onReconnect = {}, onStartCollection = {}, onViewRun = {},
                            onSelectDevice = {}, onAttentionClick = {}, onSameNetwork = {}, onDirectConnect = {},
                            onQrRegister = {}, onSaveDiagnostics = {})
                    }, detail = {})
                }
            }
        }
        GeoPalette.entries.forEach { choice ->
            listOf(1, 3).forEach { count ->
                (0 until count).forEach { index ->
                    compose.runOnIdle { palette = choice; rowCount = count; highlighted = null }
                    val device = devices[index]
                    val rowTag = "device-list-row-${device.deviceId}"
                    val title = compose.onNodeWithText(device.deviceName, useUnmergedTree = true)
                    val chevron = compose.onNode(hasTestTag("geo-row-chevron") and
                        hasAnyAncestor(hasTestTag(rowTag)), useUnmergedTree = true)
                    val titleBefore = title.fetchSemanticsNode().boundsInRoot
                    val chevronBefore = chevron.fetchSemanticsNode().boundsInRoot
                    compose.runOnIdle { highlighted = device.deviceId }
                    compose.onNode(hasText(device.deviceName) and hasClickAction()).assertIsSelected()
                    assertEquals(titleBefore, title.fetchSemanticsNode().boundsInRoot)
                    assertEquals(chevronBefore, chevron.fetchSemanticsNode().boundsInRoot)

                    val card = compose.onNodeWithTag("device-list-card", useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInRoot
                    val row = compose.onNodeWithTag(rowTag, useUnmergedTree = true)
                    val bounds = row.fetchSemanticsNode().boundsInRoot
                    assertEquals(card.left, bounds.left, .5f)
                    assertEquals(card.right, bounds.right, .5f)
                    // Sample the painted edges at mid-height, away from the rounded corners and content.
                    val pixels = row.captureToImage().toPixelMap()
                    val expected = choice.colors.primaryTint.toArgb()
                    assertEquals("${choice.name} row=$index left fill", expected, pixels[1, pixels.height / 2].toArgb())
                    assertEquals("${choice.name} row=$index right fill", expected,
                        pixels[pixels.width - 2, pixels.height / 2].toArgb())
                }
            }
        }
    }

    @Test fun exhibitionHomeShowsCoreActionsOnceAndAlignsManualButton() {
        var opened: String? = null
        compose.setContent {
            Viewport(1280f, 776f, rail = true) {
                val state = buildTasksHomeUiState(TasksHomeInput("GEO-X-1", true,
                    SurveyRunUiState(), emptyList(), emptyList(), emptyList(), false)).copy(
                    focus = null,
                    deviceStrip = GeoDeviceStripState("GEO-X-1", "연결됨", Tone.SUCCESS, emptyList(), "방금 갱신"),
                    deviceFigures = listOf(GeoFigure("저장 여유", "564", unit = "GB"),
                        GeoFigure("온도", "55", unit = "°C"), GeoFigure("CPU", "30", unit = "%"), GeoFigure("GPU", "0", unit = "%")),
                    ticketSection = ServerTicketUiState(tickets = listOf(
                        ServerTicket("today", "전시 수집 구간", dueDate = "2026-10-26", status = ServerTicketStatus(name = "할 일", category = "OPEN")))),
                    recent = (1..3).map { RecentRunState("run-$it", "최근 수집 $it", "10월 23일 · 32분 · 63.6 GB", emptyList()) })
                TasksHomeScreen(state, onSelectDevice = {}, onStartSurvey = {}, onChangeSection = {},
                    onOpenActiveRun = {}, onOpenRun = {}, onOpenHistory = {}, onOpenTask = {},
                    onOpenTicket = { opened = it }, onCamera = {}, onSendFiles = {}, onDeviceMap = {}, onDeviceFiles = {})
            }
        }
        listOf("저장 여유", "준비하기", "빠른 기능", "최근 수집 1", "최근 수집 2", "장치\n파일").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onAllNodesWithText("전시 수집 구간").assertCountEquals(1)
        compose.onNodeWithText("최근 수집 3").assertDoesNotExist()
        compose.onNode(hasText("구간 직접 고르기") and hasClickAction()).assertWidthIsEqualTo(608.dp)
        val bottom = compose.onNodeWithTag("검사 창").fetchSemanticsNode().boundsInRoot.bottom
        val quick = compose.onNodeWithText("장치\n파일").fetchSemanticsNode().boundsInRoot
        assertTrue("빠른 기능 전체가 첫 화면 안에 있어야 합니다", quick.bottom <= bottom)
        compose.onNodeWithText("준비하기").performClick()
        compose.runOnIdle { assertEquals("today", opened) }
    }

    @Test fun cameraGrowsBeyond720DpAndKeepsControlsBesideTheImage() {
        var width by mutableFloatStateOf(1280f)
        var height by mutableFloatStateOf(776f)
        val frame = Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888)
        compose.setContent {
            Viewport(width, height, rail = false) {
                CameraPreviewScreen(CameraPreviewUiState(frame = frame, targetConnected = true,
                    checkedAtElapsedMillis = 10_000L, frameFreshness = CameraFrameFreshness(receivedAt = 0L),
                    targetStillCurrent = { true }), CameraSensorStatus(frameWidth = 1280, frameHeight = 800), {}, {})
            }
        }
        val image = compose.onNodeWithContentDescription("장치 카메라 마지막 수신 영상").fetchSemanticsNode().boundsInRoot
        val capture = compose.onNodeWithText("캡처").assertIsNotEnabled().fetchSemanticsNode().boundsInRoot
        assertTrue(image.width > 720f * .5f)
        assertTrue(capture.left >= image.right)
        compose.onNodeWithText("카메라 영상").assertDoesNotExist()
        compose.onNodeWithText("1280 × 800").assertIsDisplayed()
        compose.onNodeWithText("저장 위치").performClick()
        compose.onNodeWithText("캡처 저장 위치").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { width = 800f; height = 1280f }
        compose.onNodeWithText("카메라 영상").assertExists()
        compose.onNodeWithContentDescription("뒤로").assertExists()
    }

    @Test fun settingsDetailBackReturnsWhenNarrowed() {
        var width by mutableFloatStateOf(1280f)
        var height by mutableFloatStateOf(776f)
        compose.setContent {
            Viewport(width, height, rail = true) {
                GeoPaneLayout(singlePane = GeoPaneSelection.DETAIL,
                    list = { DeviceTitleBar("설정", root = true) },
                    detail = { ThemeScreen(onBack = {}) })
            }
        }
        compose.onNodeWithText("화면 테마").assertExists()
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
        compose.runOnIdle { width = 800f; height = 1280f }
        compose.onNodeWithContentDescription("뒤로").assertExists()
    }

    @Test fun emptyTransferDetailExplainsSelectionInsteadOfWaitingForever() {
        compose.setContent {
            Viewport(1280f, 776f, rail = true) {
                GeoPaneLayout(singlePane = GeoPaneSelection.LIST, list = { DeviceTitleBar("전송", root = true) },
                    detail = {
                        UploadProgressScreen(job = null, verification = null, isLoading = false,
                            message = null, error = null, onCancel = {}, onRetry = {}, onVerify = {},
                            onDeleteSource = {}, onRestoreSource = {}, onBack = {})
                    })
            }
        }
        compose.onNodeWithText("전송 내역을 선택하세요").assertIsDisplayed()
        compose.onNodeWithText("전송을 확인하고 있습니다").assertDoesNotExist()
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
    }

    @Composable
    private fun Viewport(width: Float, height: Float, rail: Boolean, content: @Composable () -> Unit) {
        val window = geoWindow(width, height, 1.1f, navigationVisible = rail)
        CompositionLocalProvider(LocalDensity provides Density(.5f, 1.1f), LocalGeoWindow provides window,
            LocalGeoChrome provides GeoChromeMeasurements(viewportDp = height)) {
            JetsonControllerTheme {
                Row(Modifier.requiredSize(width.dp, height.dp).testTag("검사 창")) {
                    if (rail) Spacer(Modifier.width(80.dp).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            }
        }
    }
}
