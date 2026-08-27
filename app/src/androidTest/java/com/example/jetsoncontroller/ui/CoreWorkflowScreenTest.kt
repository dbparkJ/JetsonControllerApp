package com.example.jetsoncontroller.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.jetsoncontroller.ui.dashboard.DashboardScreen
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.dashboard.StatusFreshness
import com.example.jetsoncontroller.ui.connection.ConnectionHubScreen
import com.example.jetsoncontroller.ui.devices.DeviceListScreen
import com.example.jetsoncontroller.ui.devices.DeviceListUiState
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

        composeRule.onNodeWithText("Jetson을 등록합니다").assertIsDisplayed()
        composeRule.onNodeWithText("장비 등록").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi 설정").assertIsDisplayed()
        composeRule.onNodeWithText("QR 스캔").assertIsDisplayed()
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

        composeRule.onNodeWithText("등록 코드 직접 입력").performClick()
        composeRule.onNodeWithText("QR을 읽기 어렵다면 QR 아래의 전체 등록 코드를 입력하세요.")
            .assertIsDisplayed()
    }

    @Test
    fun registeredDeviceDeleteRequiresConfirmation() {
        val device = RegisteredDevice(
            deviceId = "00000000-0000-0000-0000-000000000001",
            deviceName = "MMS-Test"
        )
        composeRule.setContent {
            JetsonControllerTheme {
                DeviceListScreen(
                    state = DeviceListUiState(
                        permissionGranted = true,
                        registeredDevices = listOf(device)
                    ),
                    onScanClick = {},
                    onRequestBluetoothPermission = {},
                    onConnect = {},
                    onReconnect = {},
                    onForget = {},
                    onAddDeviceClick = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("저장된 장비 삭제").performClick()
        composeRule.onNodeWithText("MMS-Test 등록을 삭제할까요?").assertIsDisplayed()
        composeRule.onNodeWithText("취소").assertIsDisplayed()
    }

    @Test
    fun deviceHubUsesTheSameThreeStageConnectionLabel() {
        val device = RegisteredDevice(
            deviceId = "00000000-0000-0000-0000-000000000001",
            deviceName = "MMS-Test"
        )
        composeRule.setContent {
            JetsonControllerTheme {
                ConnectionHubScreen(
                    onAddDevice = {},
                    onOpenDashboard = {},
                    unreadAlertCount = 0,
                    onAlertsClick = {},
                    registeredDevices = listOf(device),
                    transportState = TransportState.Connected(
                        type = TransportType.WIFI_DIRECT,
                        deviceId = device.deviceId,
                        deviceName = device.deviceName
                    ),
                    lanEndpoints = emptyList(),
                    lanError = null,
                    connectingLanDeviceId = null,
                    localNetworkPermissionGranted = true,
                    onRequestLocalNetworkPermission = {},
                    onRefreshLan = {},
                    onConnectLan = {},
                    onReconnectDevice = {}
                )
            }
        }

        composeRule.onNodeWithText("핸드폰과 연결").assertIsDisplayed()
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

        composeRule.onNodeWithText("장비 확인").assertIsDisplayed()
        composeRule.onNodeWithText("인증 중...").assertIsDisplayed()
        composeRule.onNodeWithText("상태 동기화").assertIsDisplayed()
    }

    @Test
    fun dashboardPrioritizesHealthAndCurrentWork() {
        composeRule.setContent {
            JetsonControllerTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        statusFreshness = StatusFreshness.CURRENT,
                        isOnline = true,
                        fullControlAvailable = true
                    ),
                    pipelines = emptyList(),
                    uploads = emptyList(),
                    unreadAlertCount = 0,
                    onAlertsClick = {},
                    onDisconnect = {},
                    onRefreshFan = {},
                    onSetFanAuto = {},
                    onSetFanManual = {},
                    onReboot = {},
                    onShutdown = {},
                    onStorageClick = {},
                    onNetworkSettingsClick = {},
                    onUploadQueueClick = {},
                    onPipelinesClick = {},
                    onSectionSelected = {},
                    onDismissOperationMessage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("정상 작동 중").assertIsDisplayed()
        composeRule.onNodeWithText("진행 중인 작업").assertIsDisplayed()
        composeRule.onNodeWithText("현재 진행 중인 작업이 없습니다.").assertIsDisplayed()
    }

    @Test
    fun healthyDashboardCardCanBeSwipedAway() {
        composeRule.setContent {
            JetsonControllerTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        statusFreshness = StatusFreshness.CURRENT,
                        isOnline = true,
                        fullControlAvailable = true
                    ),
                    pipelines = emptyList(),
                    uploads = emptyList(),
                    unreadAlertCount = 0,
                    onAlertsClick = {},
                    onDisconnect = {},
                    onRefreshFan = {},
                    onSetFanAuto = {},
                    onSetFanManual = {},
                    onReboot = {},
                    onShutdown = {},
                    onStorageClick = {},
                    onNetworkSettingsClick = {},
                    onUploadQueueClick = {},
                    onPipelinesClick = {},
                    onSectionSelected = {},
                    onDismissOperationMessage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("dashboard-health-card")
            .performTouchInput { swipeLeft() }
        composeRule.onAllNodesWithText("정상 작동 중").assertCountEquals(0)
    }

    @Test
    fun dashboardAttentionCanBeDismissed() {
        composeRule.setContent {
            JetsonControllerTheme {
                DashboardScreen(
                    state = DashboardUiState(
                        statusFreshness = StatusFreshness.CURRENT,
                        isOnline = true,
                        fullControlAvailable = true,
                        status = JetsonStatus(temperatureC = 85f)
                    ),
                    pipelines = emptyList(),
                    uploads = emptyList(),
                    unreadAlertCount = 0,
                    onAlertsClick = {},
                    onDisconnect = {},
                    onRefreshFan = {},
                    onSetFanAuto = {},
                    onSetFanManual = {},
                    onReboot = {},
                    onShutdown = {},
                    onStorageClick = {},
                    onNetworkSettingsClick = {},
                    onUploadQueueClick = {},
                    onPipelinesClick = {},
                    onSectionSelected = {},
                    onDismissOperationMessage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("확인이 필요합니다").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("상태 알림 닫기").performClick()
        composeRule.onAllNodesWithText("확인이 필요합니다").assertCountEquals(0)
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
