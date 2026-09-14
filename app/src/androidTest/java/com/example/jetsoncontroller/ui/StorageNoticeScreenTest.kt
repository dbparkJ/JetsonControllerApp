package com.example.jetsoncontroller.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.ui.storage.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class StorageNoticeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deviceDeletionNoticeExpiresAndDoesNotReplayOnReturn() {
        val message = "테스트 데이터를 장치에서 삭제했습니다."
        var state by mutableStateOf(DeviceStorageUiState(deviceId = "test", controlAvailable = true,
            currentRoot = RemoteRoot("recordings", "수집 데이터", null), message = message))
        var visible by mutableStateOf(true)
        compose.setContent { JetsonControllerTheme {
            if (visible) DeviceStorageScreen(state, true, null, {}, {}, {}, {}, {}, { _, _ -> }, {},
                onDismissMessage = { shown -> if (state.message == shown) state = state.copy(message = null) })
        } }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.waitUntil(12_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { assertNull(state.message); visible = false }
        compose.runOnIdle { visible = true }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(message = message) }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithContentDescription("메시지 닫기").performClick()
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { assertNull(state.message) }
    }

    @Test fun leavingServerStorageConsumesItsDeletionNotice() {
        val message = "서버의 업로드 데이터를 삭제했습니다."
        var state by mutableStateOf(ServerStorageUiState(deviceId = "test", message = message))
        var visible by mutableStateOf(true)
        compose.setContent { JetsonControllerTheme {
            if (visible) ServerStorageScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                onDismissMessage = { shown -> if (state.message == shown) state = state.copy(message = null) })
        } }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { visible = false }
        compose.runOnIdle { assertNull(state.message); visible = true }
        compose.onNodeWithText(message).assertDoesNotExist()
    }
}
