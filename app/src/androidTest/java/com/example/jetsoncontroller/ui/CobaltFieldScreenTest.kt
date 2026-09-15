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
                Box(Modifier.fillMaxSize()) {
                    key(screen, dark, scale) { CobaltGallery(screen, dark) }
                }
            }
        }
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), InstrumentationRegistry.getArguments().getString("captureFolder") ?: "cobalt-captures").apply { mkdirs() }
        for (theme in listOf(false, true)) for (font in listOf(1f, 2f)) {
            for (page in listOf("home", "tasks", "data", "settings", "offline", "detail")) {
                compose.runOnIdle { screen = page; dark = theme; scale = font }
                compose.waitForIdle()
                compose.onNodeWithText("홈", useUnmergedTree = true).assertIsDisplayed()
                org.junit.Assert.assertTrue(compose.onAllNodesWithText("파일", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
                if (page == "data") {
                    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("야간 라인 검사"))
                    compose.onNodeWithText("선택").performClick()
                    compose.onNodeWithText("야간 라인 검사").performClick()
                    compose.onNodeWithText("서버로 전송").assertIsEnabled()
                }
                if (page == "detail") {
                    compose.onNodeWithText("run-demo", substring = true).assertDoesNotExist()
                    compose.onNodeWithText("수집 중").assertIsDisplayed()
                }
                val file = File(folder, "$page-${if (theme) "dark" else "light"}-${font}.png")
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
    }

    @Test fun singleFileTargetSurvivesFilteringAndClearsAcrossDevices() {
        var device by mutableStateOf("A")
        var transfer: Pair<String, String>? = null
        val entry = RemoteFileEntry("검사 폴더", "inspection", RemoteEntryType.DIRECTORY,
            sizeBytes = 4096, modifiedAt = "2026-09-15T12:00:00Z")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            JetsonControllerTheme {
                com.example.jetsoncontroller.ui.storage.DataHubScreen(
                    device,
                    com.example.jetsoncontroller.ui.storage.DeviceStorageUiState(
                        deviceId = device,
                        controlAvailable = true,
                        currentRoot = RemoteRoot("recordings", "수집 자료", null),
                        entries = listOf(entry)
                    ),
                    true, "", 0, {}, {}, {}, {}, {}, {}, {}, {},
                    { root, path -> transfer = root to path }, {}
                )
            }
        }
        compose.onNodeWithText("서버로 전송").assertDoesNotExist()
        compose.onNodeWithText("선택").performClick()
        compose.onNodeWithText("검사 폴더").performScrollTo().performClick()
        compose.onNodeWithText("서버로 전송").assertIsEnabled()
        compose.onNodeWithTag("file-search").performTextInput("숨김")
        compose.onNodeWithText("검색 결과가 없습니다").assertIsDisplayed()
        // Filtering only changes the file list. The explicit transfer target remains
        // visible in the selection summary until the operator clears it.
        compose.onNodeWithText("검사 폴더", substring = true).assertIsDisplayed()
        compose.onNodeWithText("서버로 전송").performClick()
        compose.runOnIdle { assertEquals("recordings" to "inspection", transfer) }
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("서버로 전송").assertDoesNotExist()
        compose.onNodeWithTag("file-search").performTextClearance()
        compose.onNodeWithText("선택").performClick()
        compose.onNodeWithText("검사 폴더").performScrollTo().performClick()
        compose.onNodeWithText("서버로 전송").assertIsEnabled()
        compose.runOnIdle { device = "B" }
        compose.onNodeWithText("서버로 전송").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("서버로 전송").assertDoesNotExist()
    }

    @Test fun cachedFilesExplainOfflineStateAndRouteOpenToReconnect() {
        var reconnects = 0
        val cached = RemoteFileEntry(
            "최근 수집",
            "latest",
            RemoteEntryType.DIRECTORY,
            sizeBytes = null,
            modifiedAt = "2026-09-15T12:00:00Z"
        )
        compose.setContent {
            JetsonControllerTheme {
                com.example.jetsoncontroller.ui.storage.DataHubScreen(
                    deviceName = "MMS-4DE0",
                    state = com.example.jetsoncontroller.ui.storage.DeviceStorageUiState(
                        deviceId = "A",
                        controlAvailable = false,
                        currentRoot = RemoteRoot("recordings", "수집 자료", null),
                        entries = listOf(cached)
                    ),
                    serverUploadEnabled = true,
                    unavailableReason = "",
                    unreadCount = 0,
                    onDevices = { reconnects++ },
                    onAlerts = {},
                    onRefresh = {},
                    onNavigateBack = {},
                    onDirectoryClick = { error("offline folder must not be opened remotely") },
                    onFileClick = { error("offline file must not be opened remotely") },
                    onDeleteClick = {},
                    onHistory = {},
                    onTransfer = { _, _ -> },
                    onSection = {}
                )
            }
        }

        compose.onNodeWithText("연결 끊김 · 마지막으로 확인한 파일 목록입니다.").assertIsDisplayed()
        compose.onNodeWithText("최근 수집").performClick()
        compose.runOnIdle { assertEquals(1, reconnects) }
        compose.onNodeWithText("선택").performClick()
        compose.onNodeWithText("최근 수집").performClick()
        compose.onNodeWithText("서버로 전송").assertIsNotEnabled()
    }

    @Test fun choosingPipelineOpensPreparationWithoutSendingLegacyCommand() {
        val task = ManagedPipeline("task", "검사 작업", state = PipelineState.STOPPED,
            entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv")
        var device by mutableStateOf("A")
        var calls = 0
        var preparations = 0
        compose.setContent {
            JetsonControllerTheme {
                PipelineListScreen(PipelineUiState(deviceId = device, controlAvailable = true,
                    pipelines = listOf(task), observedAtMillis = 1000), {}, {}, {},
                    { _, _ -> calls++ }, {}, {}, {}, {}, {}, {}, startCapability = true, nowMillis = 1001,
                    onPrepareRun = { preparations++ })
            }
        }
        compose.onNodeWithText("이 작업으로 수집 준비").performScrollTo().performClick()
        compose.runOnIdle { device = "B" }
        compose.runOnIdle { assertEquals(0, calls); assertEquals(1, preparations) }
    }
}
