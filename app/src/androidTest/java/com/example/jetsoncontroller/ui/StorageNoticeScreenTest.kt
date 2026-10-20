package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.model.TrashEntry
import com.example.jetsoncontroller.model.RemoteRoot
import com.example.jetsoncontroller.data.server.ServerCapabilities
import com.example.jetsoncontroller.data.server.ServerEmployee
import com.example.jetsoncontroller.data.server.ServerTrashJob
import com.example.jetsoncontroller.ui.storage.*
import com.example.jetsoncontroller.ui.components.DismissibleNoticeBanner
import com.example.jetsoncontroller.ui.components.NoticeDismissalProvider
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

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

    @Test fun dismissedHelpStaysHiddenUntilSemanticContentKeyChanges() {
        var version by mutableStateOf(1)
        compose.setContent { JetsonControllerTheme {
            NoticeDismissalProvider {
                DismissibleNoticeBanner(
                    noticeKey = "storage-help.v$version",
                    message = "파일 도움말 $version"
                )
            }
        } }

        compose.onNodeWithText("파일 도움말 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("메시지 닫기").performClick()
        compose.onNodeWithText("파일 도움말 1").assertDoesNotExist()
        compose.runOnIdle { version = 2 }
        compose.onNodeWithText("파일 도움말 2").assertIsDisplayed()
    }

    @Test fun localTrashConfirmationUsesOriginalIdsAndClosesAcrossDevicesAtLargeText() {
        val first = TrashEntry(
            "trash-a", "STORAGE", "TRASHED", name = "첫 수집 폴더",
            restoreSupported = true, purgeSupported = true
        )
        var state by mutableStateOf(
            LocalTrashUiState(
                deviceId = "device-a", online = true, entries = listOf(first), emptySupported = true
            )
        )
        var emptied: Pair<String, List<String>>? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                JetsonControllerTheme {
                    LocalTrashScreen(state, {}, {}, {}, { device, ids -> emptied = device to ids }, {})
                }
            }
        }

        compose.onNodeWithContentDescription("휴지통 메뉴").performClick()
        compose.onNodeWithText("휴지통 비우기").performClick()
        assertEquals(
            2f,
            compose.onNodeWithTag("local-trash-empty-dialog-title").fetchSemanticsNode()
                .config[TrashDialogFontScaleKey],
            0f
        )
        captureDialog("storage-trash-local-confirm")
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithTag("local-trash-empty-dialog-title").assertDoesNotExist()

        compose.onNodeWithContentDescription("휴지통 메뉴").performClick()
        compose.onNodeWithText("휴지통 비우기").performClick()
        compose.runOnIdle {
            state = state.copy(entries = state.entries + first.copy(trashId = "trash-new", name = "새 항목"))
        }
        compose.onNodeWithText("영구 삭제").performClick()
        compose.runOnIdle { assertEquals("device-a" to listOf("trash-a"), emptied) }

        compose.onNodeWithContentDescription("휴지통 메뉴").performClick()
        compose.onNodeWithText("휴지통 비우기").performClick()
        compose.runOnIdle { state = state.copy(deviceId = "device-b") }
        compose.onNodeWithTag("local-trash-empty-dialog-title").assertDoesNotExist()
        compose.runOnIdle { assertEquals("device-a" to listOf("trash-a"), emptied) }
    }

    @Test fun offlineLocalTrashOffersOneRecheckAndNamesRunLogsByTime() {
        val log = TrashEntry(
            "trash-log", "RUN_HISTORY", "TRASHED", name = "run-20260916T073608.169374Z-4190931992.log",
            trashedAt = "2026-10-20T05:02:00Z", restoreSupported = true
        )
        compose.setContent {
            JetsonControllerTheme {
                LocalTrashScreen(
                    LocalTrashUiState(
                        deviceId = "device-a", online = false, entries = listOf(log), hasLoaded = true,
                        error = "장치에 연결한 뒤 휴지통을 확인하세요.", deviceName = "GEO-X-1"
                    ),
                    {}, {}, {}, { _, _ -> }, {}
                )
            }
        }

        compose.onAllNodesWithText("다시 확인").assertCountEquals(1)
        compose.onNodeWithText(localTrashTitle(log)).assertIsDisplayed()
        compose.onAllNodesWithText("run-", substring = true).assertCountEquals(0)
    }

    @Test fun serverTrashExplainsVisibleCountAndOpensDeveloperPurge() {
        val visible = ServerTrashJob(
            "session-a", "client-a", "수집 결과", 1024, 1, "TRASHED", "now",
            purgeSupported = true, restoreSupported = true
        )
        var state by mutableStateOf(
            DirectServerUiState(
                selectedProfileId = "profile-a",
                capabilities = ServerCapabilities(
                    1, "production", ServerEmployee("employee", "작업자", "OPERATOR"),
                    emptyList(), 1024, "now"
                ),
                section = DirectServerSection.TRASH,
                trash = listOf(visible),
                trashEmptySupported = true,
                trashTotal = 3,
                trashNextOffset = 1
            )
        )
        var developerOpens = 0
        var purges = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                JetsonControllerTheme {
                    DirectServerScreen(
                        state = state,
                        onBack = {},
                        onSection = {},
                        onSelectProfile = {},
                        onSaveProfile = { _, _ -> },
                        onConnect = {},
                        onRefreshJobs = {},
                        onLoadMoreJobs = {},
                        onOpenJob = {},
                        onOpenDirectory = {},
                        onOpenFile = {},
                        onReceipt = {},
                        onMoveToTrash = {},
                        onRestore = {},
                        onUndoTrash = {},
                        onRefreshTrash = {},
                        onEmptyTrash = { _, _ -> purges++ },
                        onRemoveProfile = {}, onDismissMessage = {},
                        developerModeEnabled = true,
                        onDeveloperTrash = { developerOpens++ }
                    )
                }
            }
        }

        compose.onNodeWithText("전체 3개 중 1개를 표시합니다.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("표시된 1개 비우기").assertDoesNotExist()
        compose.onNodeWithText("개발자 도구에서 비우기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, developerOpens); assertEquals(0, purges) }
        compose.onNode(isDialog()).assertDoesNotExist()

    }

    private fun captureDialog(name: String) {
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "$name.png")
        compose.onNode(isDialog()).captureToImage().asAndroidBitmap().let { bitmap ->
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
