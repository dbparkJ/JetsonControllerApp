package com.example.jetsoncontroller.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jetsoncontroller.data.alerts.AlertDestination
import com.example.jetsoncontroller.data.alerts.AlertRecord
import com.example.jetsoncontroller.data.alerts.AlertSeverity
import com.example.jetsoncontroller.data.transport.TransportState
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.RegisteredDevice
import com.example.jetsoncontroller.ui.alerts.AlertCenterScreen
import com.example.jetsoncontroller.ui.alerts.AlertCenterUiState
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.connection.deviceConnectionUi
import com.example.jetsoncontroller.ui.devices.DeviceDetailScreen
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.jetsoncontroller.ui.onboarding.FirstDeviceOnboardingScreen
import com.example.jetsoncontroller.ui.pairing.PairingPhase
import com.example.jetsoncontroller.ui.pairing.PairingScreen
import com.example.jetsoncontroller.ui.pairing.PairingUiState
import com.example.jetsoncontroller.ui.pairing.QrScannerScreen
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreWorkflowScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingExplainsJourneyBeforeCamera() {
        composeRule.setContent {
            JetsonControllerTheme {
                FirstDeviceOnboardingScreen(onScanQr = {}, onBack = {})
            }
        }

        composeRule.onNodeWithText("GEO-X에 장치를 등록하세요").assertIsDisplayed()
        composeRule.onNodeWithText("장치 본체의 QR 코드를 스캔하면 GEO-X에서 장치를 관리할 수 있습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("등록에 필요한 권한").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("QR 코드를 읽습니다.").assertIsDisplayed()
        composeRule.onNodeWithText("QR 코드 스캔").assertIsDisplayed()
    }

    @Test
    fun deniedCameraStillOffersManualRegistration() {
        composeRule.setContent {
            JetsonControllerTheme {
                QrScannerScreen(
                    cameraPermissionGranted = false,
                    errorMessage = null,
                    onRequestCameraPermission = {},
                    onQrScanned = { false },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("코드 직접 입력").performClick()
        composeRule.onNodeWithText("QR 아래에 있는 전체 코드를 입력하세요.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("다음").assertIsNotEnabled()
        composeRule.onNodeWithText("등록 코드를 입력하세요.").assertIsDisplayed()
    }

    @Test
    fun registeredDeviceDeleteRequiresConfirmation() {
        val device = RegisteredDevice(
            deviceId = "00000000-0000-0000-0000-000000000001",
            deviceName = "MMS-Test"
        )
        var forgotten = 0
        composeRule.setContent {
            JetsonControllerTheme {
                DeviceDetailScreen(
                    deviceName = device.deviceName, dashboard = DashboardUiState(), snapshot = null,
                    connection = deviceConnectionUi(false, null), lastSeenText = null, freshness = null,
                    runActive = false, developerModeEnabled = false, deviceId = device.deviceId,
                    onBack = {}, onDisconnect = {}, onStartCollection = {}, onViewRun = {},
                    onCamera = {}, onGnss = {}, onSameNetwork = {}, onDirectConnect = {},
                    onQrRegister = {}, onSaveDiagnostics = {}, onNetwork = {}, onFiles = {},
                    onReboot = {}, onShutdown = {}, onPipelines = {}, onFan = {}, onDiagnostics = {},
                    onForget = { forgotten++ }
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("등록 해제"))
        composeRule.onNodeWithText("등록 해제").performClick()
        composeRule.onNodeWithText("MMS-Test 등록을 해제할까요?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, forgotten) }
        composeRule.onNodeWithText("취소").performClick()
        composeRule.runOnIdle { assertEquals(0, forgotten) }
    }

    @Test
    fun deviceHubShowsAuthenticatedControlReadiness() {
        val device = RegisteredDevice(
            deviceId = "00000000-0000-0000-0000-000000000001",
            deviceName = "MMS-Test"
        )
        var opened = 0
        composeRule.setContent {
            JetsonControllerTheme {
                DailyDeviceRoot(45f, device, TransportType.WIFI_DIRECT) { opened++ }
            }
        }

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "연결됨")).assertIsDisplayed()
        composeRule.onNodeWithText(device.deviceName).performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
        composeRule.onAllNodesWithText("온라인").assertCountEquals(0)
    }

    @Test
    fun pairingShowsDetailedCurrentStage() {
        composeRule.setContent {
            JetsonControllerTheme {
                PairingScreen(
                    state = PairingUiState(phase = PairingPhase.AUTHENTICATING),
                    onStartPairing = {},
                    onCancel = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText("등록 점검").assertIsDisplayed()
        composeRule.onAllNodesWithText("인증하는 중입니다.")[0].assertIsDisplayed()
        composeRule.onNode(hasStateDescription("진행 중")).assertIsDisplayed()
        composeRule.onNode(hasStateDescription("등록됨")).assertDoesNotExist()
    }

    @Test
    fun pairingWithoutRegistrationCodeShowsWhyItsFooterIsDisabled() {
        var starts = 0
        composeRule.setContent {
            JetsonControllerTheme {
                PairingScreen(PairingUiState(phase = PairingPhase.QR_SCANNED), { starts++ }, {}, {})
            }
        }
        composeRule.onNodeWithText("등록").assertIsNotEnabled()
        composeRule.onNodeWithText("QR 코드로 다시 등록하세요.").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, starts) }
    }

    @Test
    fun deviceRootShowsHealthAndCurrentDevice() {
        composeRule.setContent {
            JetsonControllerTheme {
                DailyDeviceRoot(45f)
            }
        }

        composeRule.onNodeWithText("장치 목록").assertIsDisplayed()
        composeRule.onNodeWithText("GEO-X-1").assertIsDisplayed()
        composeRule.onNodeWithText("확인할 일이 없습니다.").assertIsDisplayed()

    }

    @Test
    fun healthyDeviceRootShowsItsEmptyAttentionState() {
        composeRule.setContent {
            JetsonControllerTheme {
                DailyDeviceRoot(45f)
            }
        }

        composeRule.onNodeWithText("확인할 일이 없습니다.").assertIsDisplayed()
    }

    @Test
    fun deviceRootAttentionCanBeHidden() {
        composeRule.setContent {
            JetsonControllerTheme {
                DailyDeviceRoot(85f)
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("GEO-X-1 온도 확인"))
        composeRule.onNodeWithText("GEO-X-1 온도 확인").assertIsDisplayed()
        composeRule.onNodeWithText("숨기기").performClick()
        composeRule.onAllNodesWithText("GEO-X-1 온도 확인").assertCountEquals(0)

    }

    @androidx.compose.runtime.Composable
    private fun DailyDeviceRoot(temperature: Float,
        device: RegisteredDevice = RegisteredDevice("device-a", "GEO-X-1"),
        transport: TransportType = TransportType.LAN, onOpen: () -> Unit = {}) {
        com.example.jetsoncontroller.ui.devices.DevicesHomeScreen(
            registeredDevices = listOf(device),
            selectedDeviceId = device.deviceId, currentDashboard = DashboardUiState(isOnline = true,
                statusFreshness = StatusFreshness.CURRENT, transportType = transport,
                status = JetsonStatus(temperatureC = temperature)), snapshots = emptyMap(),
            availableDeviceIds = emptySet(), connectingDeviceId = null, connectFailedDeviceId = null,
            runActive = false, attention = null, unreadAlertCount = 0, nowEpochMillis = 1,
            developerModeEnabled = false, onSectionSelected = {}, onAlertsClick = {}, onAddDevice = {},
            onServerFiles = {}, onOpenDevice = onOpen, onReconnect = {}, onStartCollection = {}, onViewRun = {},
            onSelectDevice = {}, onAttentionClick = {}, onSameNetwork = {}, onDirectConnect = {},
            onQrRegister = {}, onSaveDiagnostics = {}, healthItems = if (temperature < 80f) emptyList()
                else listOf(com.example.jetsoncontroller.ui.devices.DeviceHealthItem("heat", "GEO-X-1 온도 확인", "온도를 확인하세요.")))
    }

    @Test
    fun alertHistoryOpensItsDestination() {
        val alert = AlertRecord(
            id = "storage-warning",
            title = "Jetson 저장공간 경고",
            message = "저장공간 사용량이 95%입니다.",
            destination = AlertDestination.STORAGE,
            severity = AlertSeverity.WARNING,
            createdAtEpochMillis = 1L
        )
        var openedAlertId: String? = null

        composeRule.setContent {
            JetsonControllerTheme {
                AlertCenterScreen(
                    state = AlertCenterUiState(listOf(alert), unreadCount = 1),
                    onBack = {},
                    onAlertClick = { openedAlertId = it.id },
                    onDelete = {},
                    onMarkAllRead = {},
                    onClear = {}
                )
            }
        }

        composeRule.onNodeWithText("Jetson 저장공간 경고").performClick()
        composeRule.runOnIdle { assertEquals(alert.id, openedAlertId) }
    }
}
