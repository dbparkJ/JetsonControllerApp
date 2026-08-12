package com.example.jetsoncontroller.ui.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jetsoncontroller.data.network.WifiAccessPoint
import com.example.jetsoncontroller.data.network.WifiSecurity
import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingSecuredNetworkExpandsPasswordFormInline() {
        val accessPoint = WifiAccessPoint(
            ssid = "Office Wi-Fi",
            rssi = -48,
            security = WifiSecurity.PERSONAL
        )

        composeRule.setContent {
            var state by mutableStateOf(
                NetworkSettingsUiState(
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
        composeRule.onNodeWithText("Office Wi-Fi").performClick()
        composeRule.onNodeWithTag("wifi-selected-network-form").assertIsDisplayed()
        composeRule.onNodeWithText("비밀번호").assertIsDisplayed()
        composeRule.onNodeWithTag("wifi-connect-button").assertIsDisplayed()
    }
}
