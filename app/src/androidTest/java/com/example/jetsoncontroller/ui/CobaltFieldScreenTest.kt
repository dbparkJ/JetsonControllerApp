package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.pipelines.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File

class CobaltFieldScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun galleryLightDarkAndLargeTextRendersNativeScreens() {
        var screen by mutableStateOf("home")
        var dark by mutableStateOf(false)
        var scale by mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    key(screen, dark, scale) { CobaltGallery(screen, dark) }
                }
            }
        }
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "cobalt-captures").apply { mkdirs() }
        for (theme in listOf(false, true)) for (font in listOf(1f, 2f)) {
            for (page in listOf("home", "tasks", "data", "settings", "offline", "detail")) {
                compose.runOnIdle { screen = page; dark = theme; scale = font }
                compose.waitForIdle()
                compose.onNodeWithText("홈", useUnmergedTree = true).assertIsDisplayed()
                org.junit.Assert.assertTrue(compose.onAllNodesWithText("데이터", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
                if (page == "data") {
                    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("야간 라인 검사"))
                    compose.onNodeWithText("야간 라인 검사").performClick()
                    compose.onNodeWithText("선택한 폴더 전송 확인").assertIsEnabled()
                }
                val file = File(folder, "$page-${if (theme) "dark" else "light"}-${font}.png")
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
    }

    @Test fun folderSelectionIsExplicitAndRestoresPerDevice() {
        var device by mutableStateOf("A")
        val task = ManagedPipeline("task", "검사 폴더", entrypoint = "collect.py", config = "config.yaml",
            virtualenv = "venv", outputRootId = "recordings", outputPath = "inspection")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            JetsonControllerTheme {
                holder.SaveableStateProvider(device) {
                    com.example.jetsoncontroller.ui.storage.DataHubScreen(device, listOf(task),
                        com.example.jetsoncontroller.ui.upload.UploadUiState(deviceId = device), true, "", 0,
                        {}, {}, {}, {}, {}, { _, _ -> }, {})
                }
            }
        }
        compose.onNodeWithText("선택한 폴더 전송 확인").assertIsNotEnabled()
        compose.onNodeWithText("검사 폴더").performScrollTo().performClick()
        compose.onNodeWithText("선택한 폴더 전송 확인").assertIsEnabled()
        compose.runOnIdle { device = "B" }
        compose.onNodeWithText("선택한 폴더 전송 확인").assertIsNotEnabled()
        compose.runOnIdle { device = "A" }
        compose.onNodeWithText("선택한 폴더 전송 확인").assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("선택한 폴더 전송 확인").assertIsEnabled()
    }

    @Test fun canceledPreflightAndDeviceSwitchSendNoCommands() {
        val task = ManagedPipeline("task", "검사 작업", state = PipelineState.STOPPED,
            entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv")
        var device by mutableStateOf("A")
        var calls = 0
        compose.setContent {
            JetsonControllerTheme {
                PipelineListScreen(PipelineUiState(deviceId = device, controlAvailable = true,
                    pipelines = listOf(task), observedAtMillis = 1000), {}, {}, {},
                    { _, _ -> calls++ }, {}, {}, {}, {}, {}, {}, startCapability = true, nowMillis = 1001)
            }
        }
        compose.onNodeWithText("시작 전 확인").performScrollTo().performClick()
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("시작 전 확인").performScrollTo().performClick()
        compose.runOnIdle { device = "B" }
        compose.onAllNodesWithText("작업 시작 요청").assertCountEquals(0)
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
