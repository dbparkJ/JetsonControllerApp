package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.ui.components.ConnectionRecoveryLayout
import com.example.jetsoncontroller.ui.components.ControlNavigationBar
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Presentation-only disconnection state; never creates a Repository or sends commands. */
class ConnectionNoticeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val notice = "장비 연결을 기다리고 있습니다. 작성한 내용은 유지됩니다."

    @Test fun noticeDoesNotMoveTabsOrRepeatAcrossRoutesRestorationAndDeviceSwitches() {
        var connected by mutableStateOf(true)
        var device by mutableStateOf("A")
        var message by mutableStateOf<String?>(notice)
        var resolved = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            JetsonControllerTheme {
                ConnectionRecoveryLayout(device, connected, message, { resolved++ }) { modifier ->
                    Scaffold(modifier = modifier.testTag("screen"), bottomBar = {
                        Box(Modifier.testTag("navigation")) {
                            ControlNavigationBar(ControlSection.OVERVIEW, {})
                        }
                    }) { padding -> Text("현재 화면", Modifier.padding(padding)) }
                }
            }
        }
        val navigation = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        val screen = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        assertEquals(screen.bottom, navigation.bottom)
        compose.runOnIdle { connected = false }
        compose.onNodeWithText(notice).assertIsDisplayed()
        assertEquals(navigation, compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot)
        assertEquals(screen, compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("연결 문제 해결").performClick()
        compose.runOnIdle { assertEquals(1, resolved) }
        compose.runOnIdle { message = null }
        compose.runOnIdle { message = notice }
        compose.onAllNodesWithText(notice).assertCountEquals(0)
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodesWithText(notice).assertCountEquals(0)
        compose.runOnIdle { device = "B" }
        compose.onNodeWithText(notice).assertIsDisplayed()
        compose.onNodeWithText("연결 문제 해결").performClick()
        compose.runOnIdle { device = "A" }
        compose.onAllNodesWithText(notice).assertCountEquals(0)
        compose.runOnIdle { connected = true }
        compose.runOnIdle { connected = false }
        compose.onNodeWithText(notice).assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, resolved) }
    }

    @Test fun noticeExpiresAndLeavesThreePrimaryTabsAtTheBottom() {
        compose.setContent {
            JetsonControllerTheme(darkTheme = true) {
                ConnectionRecoveryLayout("A", false, notice, {}) { modifier ->
                    Scaffold(modifier = modifier, bottomBar = {
                        Box(Modifier.testTag("navigation")) {
                            ControlNavigationBar(ControlSection.OVERVIEW, {})
                        }
                    }) { padding -> Text("통신 없는 화면 테스트", Modifier.padding(padding)) }
                }
            }
        }
        compose.onNodeWithText(notice).assertIsDisplayed()
        val navigation = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        capture("notice-visible")
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(notice).fetchSemanticsNodes().isEmpty()
        }
        listOf("홈", "파일", "설정").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        assertEquals(navigation, compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot)
        capture("notice-expired")
    }

    private fun capture(name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "slate-v7-notice-captures").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
