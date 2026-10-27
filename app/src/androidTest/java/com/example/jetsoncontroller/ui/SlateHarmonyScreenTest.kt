package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.model.RemoteEntryType
import com.example.jetsoncontroller.model.RemoteFileEntry
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.model.ManagedPipeline
import com.example.jetsoncontroller.model.PipelineState
import com.example.jetsoncontroller.ui.pipelines.PipelineListScreen
import com.example.jetsoncontroller.ui.pipelines.PipelineUiState
import com.example.jetsoncontroller.ui.storage.DataHubScreen
import com.example.jetsoncontroller.ui.storage.DeviceStorageUiState
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Native UI fixtures only: no Repository, radios, or device commands. */
class SlateHarmonyScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun themeChangePreservesSearchSelectionScrollAndInputFocus() {
        var dark by mutableStateOf(false)
        var transfers = 0
        val folders = (0..15).map {
            RemoteFileEntry("검사 폴더 $it", "inspection$it", RemoteEntryType.DIRECTORY,
                sizeBytes = 1024L * (it + 1), modifiedAt = "2026-09-15T12:${it.toString().padStart(2, '0')}:00Z")
        }
        val storage = DeviceStorageUiState(deviceId = "A", controlAvailable = true,
            currentRoot = RemoteRoot("recordings", "수집 자료", null), entries = folders)
        compose.setContent {
            JetsonControllerTheme(darkTheme = dark) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    DataHubScreen(storage, true, "", 0,
                        {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> transfers++ }, {})
                }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("검사 폴더 15"))
        compose.onNodeWithText("보낼 파일 선택").performClick()
        compose.onNode(hasText("선택") and hasAnyAncestor(hasTestTag("file-selection-inspection15")))
            .performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("선택한 파일 보내기").assertIsEnabled()
        val before = compose.onNodeWithText("검사 폴더 15").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("검사 폴더 15").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("검사 폴더 15").fetchSemanticsNode().boundsInRoot)
        list.performScrollToIndex(0)
        compose.onNodeWithContentDescription("검색").performTextInput("검사")
        compose.onNodeWithContentDescription("검색").assertIsFocused()
        capture("selection-focus-dark")
        compose.runOnIdle { dark = false }
        compose.onNodeWithContentDescription("검색").assertIsFocused().assertTextContains("검사")
        compose.onNodeWithText("선택한 파일 보내기").assertIsEnabled()
        capture("selection-focus-light")
        compose.runOnIdle { assertEquals(0, transfers) }
    }

    @Test fun pipelinePreparationActionSurvivesThemeChangeWithoutLegacyCommand() {
        var dark by mutableStateOf(false)
        var calls = 0
        var preparations = 0
        var detailId by mutableStateOf<String?>(null)
        val task = ManagedPipeline("task", "검사 작업", state = PipelineState.STOPPED,
            entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv")
        compose.setContent {
            JetsonControllerTheme(darkTheme = dark) {
                PipelineListScreen(PipelineUiState(deviceId = "A", controlAvailable = true,
                    pipelines = listOf(task), observedAtMillis = 1000), {}, {}, {},
                    { _, _ -> calls++ }, {}, {}, {}, {}, {}, {}, startCapability = true, nowMillis = 1001,
                    onPrepareRun = { preparations++ }, detailId = detailId, onDetails = { detailId = it.id })
            }
        }
        compose.onNode(hasText(task.label) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("수집 준비").assertIsDisplayed().assertIsEnabled().performClick()
        capture("pipeline-selection-light")
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("수집 준비").assertIsDisplayed().assertIsEnabled()
        capture("pipeline-selection-dark")
        compose.runOnIdle { assertEquals(0, calls); assertEquals(1, preparations) }
    }

    private fun capture(name: String, dialog: Boolean = false) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "slate-v7-captures").apply { mkdirs() }
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
