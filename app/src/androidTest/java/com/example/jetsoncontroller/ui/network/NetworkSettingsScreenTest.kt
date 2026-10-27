package com.example.jetsoncontroller.ui.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.data.network.WifiSecurity
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingSecuredNetworkExpandsPasswordFormInline() {
        var applied: WifiApplyTarget? = null
        val accessPoint = WifiAccessPoint(
            ssid = "Office Wi-Fi",
            rssi = -48,
            security = WifiSecurity.PERSONAL
        )

        composeRule.setContent {
            var state by mutableStateOf(
                NetworkSettingsUiState(
                    deviceId = "device-a",
                    transportType = TransportType.BLE,
                    accessPoints = listOf(accessPoint)
                )
            )
            JetsonControllerTheme {
                NetworkSettingsScreen(
                    state = state,
                    onBack = {},
                    onSsidChange = {},
                    onPasswordChange = { state = state.copy(password = it) },
                    onHiddenChange = {},
                    onSubmit = {},
                    wifiScanPermissionGranted = true,
                    onRequestWifiScanPermission = {},
                    onScanAccessPoints = {},
                    onApply = { applied = it },
                    onSelectAccessPoint = {
                        state = state.copy(
                            ssid = it.ssid,
                            selectedAccessPointSsid = it.ssid
                        )
                    }
                )
            }
        }

        composeRule.onAllNodesWithTag("wifi-selected-network-form").assertCountEquals(0)
        composeRule.onNodeWithText("Office Wi-Fi").performScrollTo().performClick()
        composeRule.onNodeWithTag("wifi-selected-network-form").assertIsDisplayed()
        composeRule.onNodeWithText("비밀번호").assertIsDisplayed()
        val apply = composeRule.onNode(hasText("적용") and hasClickAction())
        apply.assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("비밀번호").performTextInput("password123")
        apply.assertIsEnabled().performClick()
        composeRule.onNodeWithText("Office Wi-Fi에 연결할까요?", substring = true).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, applied) }
        composeRule.onNodeWithText("취소").performClick()
        composeRule.runOnIdle { assertEquals(null, applied) }
        apply.performClick()
        composeRule.onNode(hasText("적용") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        composeRule.runOnIdle { assertEquals("Office Wi-Fi", applied?.ssid); assertEquals("device-a", applied?.deviceId) }
        composeRule.onNodeWithText("Office Wi-Fi에 연결할까요?", substring = true).assertDoesNotExist()
    }

    @Test
    fun currentJetsonNetworkIsCheckedAndDoesNotOpenReconnectForm() {
        val accessPoint = WifiAccessPoint(
            ssid = "Lab Wi-Fi",
            rssi = -52,
            security = WifiSecurity.PERSONAL
        )

        composeRule.setContent {
            JetsonControllerTheme {
                NetworkSettingsScreen(
                    state = NetworkSettingsUiState(
                        deviceId = "device-a",
                        currentWifiSsid = accessPoint.ssid,
                        wifiConnected = true,
                        transportType = TransportType.LAN,
                        accessPoints = listOf(accessPoint)
                    ),
                    onBack = {},
                    onSsidChange = {},
                    onPasswordChange = {},
                    onHiddenChange = {},
                    onSubmit = {},
                    wifiScanPermissionGranted = true,
                    onRequestWifiScanPermission = {},
                    onScanAccessPoints = {},
                    onSelectAccessPoint = {}
                )
            }
        }

        composeRule.onNode(hasStateDescription("연결됨"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("wifi-access-point-Lab Wi-Fi")
            .assertIsNotEnabled()
        composeRule.onAllNodesWithTag("wifi-selected-network-form").assertCountEquals(0)
    }
}
