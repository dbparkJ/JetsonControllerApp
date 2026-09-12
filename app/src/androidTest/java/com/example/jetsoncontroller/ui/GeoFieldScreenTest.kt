package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.model.*
import com.example.jetsoncontroller.ui.components.GeoWelcomeScene
import com.example.jetsoncontroller.ui.field.*
import com.example.jetsoncontroller.ui.storage.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

/** Local UI fixtures only. No repository, real task, sensor, or remote command. */
class GeoFieldScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun welcomeShowsRequiredBrandInBothThemes() {
        var dark by mutableStateOf(false)
        compose.setContent { JetsonControllerTheme(darkTheme = dark) { GeoWelcomeScene() } }
        compose.onNodeWithText("GEO&").assertIsDisplayed()
        compose.onNodeWithText("도로관리장치 제어").assertIsDisplayed()
        capture("welcome-light")
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("도로관리장치 제어").assertIsDisplayed()
        capture("welcome-dark")
    }

    @Test fun historyFiltersAndPlusOpenSelectionWithoutRunningAnything() {
        var newRequests = 0
        val runs = listOf(TaskRun("a", "capture", "진행 화면 예시", "a.log", "2026-09-12T01:00:00Z", state = "RUNNING"),
            TaskRun("b", "capture", "완료 화면 예시", "b.log", "2026-09-11T01:00:00Z", state = "COMPLETED"))
        compose.setContent { JetsonControllerTheme {
            GeoRunDashboard(FieldState(deviceId = "fixture", runs = runs), emptyList(), "GEO& UI 테스트",
                0, {}, {}, {}, { newRequests++ }, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("최근 실행 10개 · 완료 탭에서 이전 기록을 확인하세요.").assertIsDisplayed()
        capture("tasks-all")
        compose.onAllNodesWithText("완료")[0].performClick()
        compose.onNodeWithText("진행 화면 예시").assertDoesNotExist()
        compose.onNodeWithText("완료 화면 예시").assertIsDisplayed()
        compose.onNodeWithContentDescription("새 작업 시작하기").performClick()
        compose.runOnIdle { assertEquals(1, newRequests) }
    }

    @Test fun dateAndTypeFiltersPreserveSingleFileAndFolderTransfers() {
        val now = Instant.now().toString()
        val entries = listOf(RemoteFileEntry("road.jpg", "road.jpg", RemoteEntryType.FILE, 100, now),
            RemoteFileEntry("track.csv", "track.csv", RemoteEntryType.FILE, 20, now))
        var requested: String? = null
        compose.setContent { JetsonControllerTheme {
            DeviceStorageScreen(DeviceStorageUiState(deviceId = "fixture", controlAvailable = true,
                currentRoot = RemoteRoot("recordings", "기록", null), entries = entries), true, null,
                {}, {}, {}, {}, {}, { _, path -> requested = path }, {})
        } }
        compose.onNodeWithText("이미지").performClick()
        compose.onNodeWithText("road.jpg").assertIsDisplayed()
        compose.onNodeWithText("track.csv").assertDoesNotExist()
        compose.onNodeWithText("오늘 (1)").assertIsDisplayed()
        capture("data-images")
        compose.onNodeWithContentDescription("파일 업로드").performClick()
        compose.runOnIdle { assertEquals("road.jpg", requested) }
        compose.onNodeWithText("업로드").performClick()
        compose.runOnIdle { assertEquals("", requested) }
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "geo-field-captures").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
