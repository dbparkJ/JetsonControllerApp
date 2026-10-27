package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*

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
import com.example.jetsoncontroller.ui.components.geo.shell.GeoBottomBar
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab
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
            for (page in listOf("welcome", "home", "tasks", "data", "settings", "offline", "detail")) {
                compose.runOnIdle { screen = page; dark = theme; scale = font }
                compose.waitForIdle()
                if (page != "welcome") {
                    compose.onNode(hasText("장치") and hasClickAction()).assertIsDisplayed()
                    compose.onNode(hasText("전송") and hasClickAction()).assertIsDisplayed()
                }
                if (page == "data") {
                    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("야간 라인 검사"))
                    compose.onNodeWithText("보낼 파일 선택").performClick()
                    selectFile("inspection")
                    compose.onNodeWithText("선택한 파일 보내기").assertIsEnabled()
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
        compose.onNodeWithText("선택한 파일 보내기").assertDoesNotExist()
        compose.onNodeWithText("보낼 파일 선택").performClick()
        selectFile("inspection")
        compose.onNodeWithText("선택한 파일 보내기").assertIsEnabled()
        compose.onNodeWithContentDescription("검색").performTextInput("숨김")
        compose.onNodeWithText("검색 결과가 없습니다").assertIsDisplayed()
        // Filtering keeps the pinned transfer target, verified by the callback below.
        compose.onNodeWithText("선택한 파일 보내기").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("recordings" to "inspection", transfer) }
        clearFileSelection()
        compose.onNodeWithText("선택한 파일 보내기").assertDoesNotExist()
        compose.onNodeWithContentDescription("검색").performTextClearance()
        compose.onNodeWithText("보낼 파일 선택").performClick()
        selectFile("inspection")
        compose.onNodeWithText("선택한 파일 보내기").assertIsEnabled()
        compose.runOnIdle { device = "B" }
        compose.onNodeWithText("선택한 파일 보내기").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("선택한 파일 보내기").assertDoesNotExist()
    }

    @Test fun cachedFilesExplainOfflineStateAndRouteOpenToReconnect() {
        var reconnects = 0
        var uploadQueueOpens = 0
        val cached = RemoteFileEntry(
            "최근 수집",
            "latest",
            RemoteEntryType.DIRECTORY,
            sizeBytes = null,
            modifiedAt = "2026-09-15T12:00:00Z"
        )
        compose.setContent {
            JetsonControllerTheme {
                Column {
                Box(Modifier.weight(1f)) {
                com.example.jetsoncontroller.ui.storage.DataHubScreen(
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
                    onHistory = { uploadQueueOpens++ },
                    onTransfer = { _, _ -> },
                    onSection = {}
                )
                }
                GeoBottomBar(GeoTab.TRANSFERS, { if (it == GeoTab.TRANSFERS) uploadQueueOpens++ })
                }
            }
        }

        compose.onNodeWithText("연결이 끊겼습니다. 마지막으로 본 목록입니다.", substring = true).assertIsDisplayed()
        compose.onNode(hasText("전송") and hasClickAction()).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, uploadQueueOpens) }
        compose.onNode(hasText("최근 수집") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, reconnects) }
        compose.onNodeWithText("보낼 파일 선택").assertIsNotEnabled()
        compose.onNodeWithText("장치에 연결되면 파일을 선택할 수 있습니다.").assertIsDisplayed()
    }

    @Test fun choosingPipelineOpensPreparationWithoutSendingLegacyCommand() {
        val task = ManagedPipeline("task", "검사 작업", state = PipelineState.STOPPED,
            entrypoint = "collect.py", config = "config.yaml", virtualenv = "venv")
        var device by mutableStateOf("A")
        var calls = 0
        var preparations = 0
        var detailId by mutableStateOf<String?>(null)
        compose.setContent {
            JetsonControllerTheme {
                PipelineListScreen(PipelineUiState(deviceId = device, controlAvailable = true,
                    pipelines = listOf(task), observedAtMillis = 1000), {}, {}, {},
                    { _, _ -> calls++ }, {}, {}, {}, {}, {}, {}, startCapability = true, nowMillis = 1001,
                    onPrepareRun = { preparations++ }, detailId = detailId, onDetails = { detailId = it.id })
            }
        }
        compose.onNode(hasText(task.label) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("수집 준비").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { device = "B" }
        compose.runOnIdle { assertEquals(0, calls); assertEquals(1, preparations) }
    }

    private fun selectFile(path: String) {
        compose.onNode(hasText("선택") and hasAnyAncestor(hasTestTag("file-selection-$path")))
            .performScrollTo().performClick().assertIsOn()
    }

    private fun clearFileSelection() {
        compose.onNodeWithContentDescription("파일 메뉴").performClick()
        compose.onNodeWithText("선택 해제").performClick()
    }
}
