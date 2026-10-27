package com.example.jetsoncontroller.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.jetsoncontroller.model.DeviceNetworkAddress
import com.example.jetsoncontroller.model.DeviceNetworkDiagnostics
import com.example.jetsoncontroller.model.SensorTestResult
import com.example.jetsoncontroller.ui.dev.DevNetworkScreen
import com.example.jetsoncontroller.ui.dev.DevSensorTestScreen
import com.example.jetsoncontroller.ui.field.DeveloperScreen
import com.example.jetsoncontroller.ui.field.FieldState
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeveloperDiagnosticsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addressesAndSensorTestsAreAvailableInsideDeveloperTools() {
        var refreshes = 0
        var requested = ""
        var sensors by mutableStateOf(false)
        var sensorState by mutableStateOf(FieldState(deviceId = "jetson", online = true))
        compose.setContent {
            JetsonControllerTheme {
                if (!sensors) DevNetworkScreen(
                    state = FieldState(deviceId = "jetson", online = true,
                        network = DeviceNetworkDiagnostics(listOf(
                            DeviceNetworkAddress("wlan0", "Wi-Fi", "111.111.111.45", "IPv4", "https://111.111.111.45:8765"),
                            DeviceNetworkAddress("tailscale0", "Tailscale", "100.76.81.78", "IPv4", "https://100.76.81.78:8765")
                        ))),
                    onBack = {}, onRefresh = { refreshes++ }
                ) else DevSensorTestScreen(sensorState,
                    { requested = it }, {}, {}, {})
            }
        }
        compose.onNodeWithText("111.111.111.45").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("100.76.81.78").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다시 조회").performClick()
        compose.runOnIdle { assertTrue(refreshes >= 2) }
        compose.runOnIdle { sensors = true }
        compose.onNodeWithText("테스트").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("camera", requested) }
        compose.runOnIdle { sensorState = sensorState.copy(sensorTests = mapOf("camera" to SensorTestResult("camera", "PASSED", "수신 확인"))) }
        compose.runOnIdle { assertEquals("gps", requested) }
    }

    @Test fun offlineStateDisablesDeviceTests() {
        compose.setContent {
            JetsonControllerTheme {
                DevSensorTestScreen(FieldState(), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("테스트").assertIsNotEnabled()
        compose.onNodeWithText("장치에 연결하면 테스트할 수 있습니다.").assertIsDisplayed()
    }

    @Test fun gpsNoFixIsShownSeparatelyFromReceiverFailure() {
        compose.setContent {
            JetsonControllerTheme {
                DevSensorTestScreen(
                    FieldState(deviceId = "jetson", online = true, sensorTests = mapOf(
                        "gps" to SensorTestResult("gps", "PASSED", "위성 위치는 아직 확정되지 않았습니다.")
                    )), onBack = {}, onTest = {}, onCamera = {}, onGps = {}
                )
            }
        }
        compose.onNode(hasStateDescription("수신 확인")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("위성 위치는 아직 확정되지 않았습니다.").performScrollTo().assertIsDisplayed()
    }
}
