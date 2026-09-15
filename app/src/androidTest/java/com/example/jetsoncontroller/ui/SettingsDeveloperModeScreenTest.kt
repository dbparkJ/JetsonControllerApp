package com.example.jetsoncontroller.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.jetsoncontroller.ui.dashboard.DashboardUiState
import com.example.jetsoncontroller.ui.field.DeveloperScreen
import com.example.jetsoncontroller.ui.field.FieldState
import com.example.jetsoncontroller.ui.settings.SettingsHubScreen
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsDeveloperModeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appInfoSevenTapsUnlocksDeveloperToolsAndDisablingRelocksThem() {
        var enabled by mutableStateOf(false)
        compose.setContent {
            JetsonControllerTheme {
                SettingsFixture(enabled) { enabled = it }
            }
        }

        compose.onNodeWithText("개발자 모드").assertDoesNotExist()
        compose.onNodeWithText("개발자 도구").assertDoesNotExist()
        compose.onNodeWithText("앱 정보").performScrollTo().performClick()
        repeat(5) { compose.onNodeWithTag("developer-unlock").performClick() }
        compose.onNodeWithText("개발자 모드").assertDoesNotExist()
        compose.onNodeWithTag("developer-unlock").performClick()
        compose.onNodeWithText("개발자 도구").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue(enabled) }

        compose.onNodeWithText("개발자 모드").performScrollTo().performClick()
        compose.onNodeWithText("개발자 도구").assertDoesNotExist()
        compose.runOnIdle { assertFalse(enabled) }
        compose.onNodeWithText("개발자 모드").assertDoesNotExist()
    }

    @Test fun previouslyEnabledPreferenceStillShowsItsDisableControl() {
        compose.setContent {
            JetsonControllerTheme { SettingsFixture(true) {} }
        }

        compose.onNodeWithText("개발자 모드").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("개발자 도구").performScrollTo().assertIsDisplayed()
    }

    @Test fun disablingModeInsideDeveloperScreenImmediatelyRequestsExit() {
        var enabled by mutableStateOf(true)
        var exitRequested by mutableStateOf(false)
        compose.setContent {
            JetsonControllerTheme {
                DeveloperScreen(
                    state = FieldState(deviceId = "test-device", online = true),
                    onBack = {},
                    onExecute = {},
                    onLogs = {},
                    onDiagnostics = {},
                    developerModeEnabled = enabled,
                    onDeveloperModeChange = { enabled = it },
                    onDeveloperDisabled = { exitRequested = true }
                )
            }
        }

        compose.onNodeWithText("개발자 모드 켜짐").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(enabled)
            assertTrue(exitRequested)
        }
    }

    @Test fun helpSheetOpensFromItsRowAndCanBeDismissed() {
        compose.setContent {
            JetsonControllerTheme { SettingsFixture(false) {} }
        }

        compose.onNodeWithText("도움말").performScrollTo().performClick()
        compose.onNodeWithText("수집 후 파일에서 저장 결과와 서버 수신을 확인할 수 있습니다.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("도움말 닫기").performClick()
        compose.onNodeWithText("수집 후 파일에서 저장 결과와 서버 수신을 확인할 수 있습니다.", substring = true)
            .assertDoesNotExist()
    }

    @androidx.compose.runtime.Composable
    private fun SettingsFixture(
        developerModeEnabled: Boolean,
        onDeveloperModeChange: (Boolean) -> Unit
    ) {
        SettingsHubScreen(
            state = DashboardUiState(deviceName = "테스트 장비"),
            deviceId = "test-device",
            unreadCount = 0,
            onDevices = {}, onAlerts = {}, onNetwork = {}, onSensors = {},
            onTargets = {}, onDiagnostics = {}, onAlertSettings = {}, onServerStorage = {},
            onRefreshFan = {}, onFanAuto = {}, onFanManual = {},
            onReboot = {}, onShutdown = {}, onDismissMessage = {}, onSection = {},
            onDeveloper = {}, onAdminTools = {},
            developerModeEnabled = developerModeEnabled,
            onDeveloperModeChange = onDeveloperModeChange
        )
    }
}
