package com.example.jetsoncontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoPalette
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import com.example.jetsoncontroller.ui.tasks.TasksHomeInput
import com.example.jetsoncontroller.ui.tasks.TasksHomeScreen
import com.example.jetsoncontroller.ui.tasks.buildTasksHomeUiState
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R7PhoneLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quickActionsRemainAvailableWithoutServerConfiguration() {
        var connected by mutableStateOf(true)
        compose.setContent {
            JetsonControllerTheme(palette = GeoPalette.STANDARD) {
                val state = buildTasksHomeUiState(TasksHomeInput("GEO-X-1", connected,
                    SurveyRunUiState(), emptyList(), emptyList(), emptyList(), false)).copy(focus = null)
                TasksHomeScreen(state, onSelectDevice = {}, onStartSurvey = {}, onChangeSection = {},
                    onOpenActiveRun = {}, onOpenRun = {}, onOpenHistory = {}, onOpenTask = {},
                    onCamera = {}, onSendFiles = {}, onDeviceMap = {}, onDeviceFiles = {})
            }
        }
        listOf(true, false).forEach { online ->
            compose.runOnIdle { connected = online }
            compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("빠른 기능"))
            listOf("카메라 영상", "파일 보내기", "장치 위치 지도", "수집 기록", "장치 파일")
                .forEach { label ->
                    compose.onNodeWithTag("geo-horizontal-tiles").performScrollToNode(hasText(label))
                    compose.onNodeWithText(label).assertExists()
                }
        }
    }

    @Test fun longFiguresRemainInsideFourTilesAtEveryPhoneFontScale() {
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                JetsonControllerTheme(palette = GeoPalette.GRAPHITE) {
                    Box(Modifier.width(364.dp).padding(16.dp)) {
                        GeoFigureGrid(listOf(GeoFigure("저장 여유", "446", unit = "GB"),
                            GeoFigure("저장 공간", "1,024", unit = "GB"),
                            GeoFigure("CPU", "100", unit = "%"), GeoFigure("온도", "59", unit = "°C")))
                    }
                }
            }
        }
        listOf(1f, 1.1f, 1.2f, 1.3f).forEach { fontScale ->
            compose.runOnIdle { scale = fontScale }
            val bounds = listOf("446", "1,024", "100", "59").map { value ->
                val node = compose.onNodeWithText(value, useUnmergedTree = true)
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertFalse("$value at $fontScale", layout.hasVisualOverflow)
                assertTrue(layout.lineCount >= 1)
                assertEquals(22f, layout.layoutInput.style.fontSize.value, 0f)
                val text = node.fetchSemanticsNode()
                val tile = requireNotNull(text.parent).boundsInRoot
                assertTrue(text.boundsInRoot.left >= tile.left - 1f)
                assertTrue(text.boundsInRoot.right <= tile.right + 1f)
                tile
            }
            assertEquals(if (fontScale >= 1.3f) 2 else 4, bounds.map { it.left }.distinct().size)
            listOf("GB", "%", "°C").forEach { unit ->
                compose.onAllNodesWithText(unit, useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                    val tile = requireNotNull(node.parent).boundsInRoot
                    assertTrue(node.boundsInRoot.left >= tile.left - 1f)
                    assertTrue(node.boundsInRoot.right <= tile.right + 1f)
                }
            }
        }
    }

    @Test fun sensorStatusesShareOneMeasuredColumnWithoutWrapping() {
        var scale by mutableFloatStateOf(1f)
        var density = 1f
        compose.setContent {
            val base = LocalDensity.current
            density = base.density
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                JetsonControllerTheme(palette = GeoPalette.GRAPHITE) {
                    GeoRowList(GeoRowTrailing.Status, Modifier.width(332.dp)) {
                        GeoRow("카메라", {}, meta = "1920 × 1200", badge = {
                            Box(Modifier.fillMaxWidth().testTag("camera-status")) { GeoBadge("수신 중", Tone.SUCCESS) }
                        })
                        GeoRow("위치(GNSS)", {}, meta = "위성 0개", badge = {
                            Box(Modifier.fillMaxWidth().testTag("gnss-status")) { GeoBadge("위치 미확인", Tone.UNKNOWN) }
                        })
                        GeoDeviceStatusRow("움직임(IMU)", "수신 중", Tone.SUCCESS, leading = {})
                    }
                }
            }
        }
        listOf(1f, 1.1f, 1.2f, 1.3f).forEach { fontScale ->
            compose.runOnIdle { scale = fontScale }
            val camera = compose.onNodeWithTag("camera-status", true).fetchSemanticsNode().boundsInRoot
            val gnss = compose.onNodeWithTag("gnss-status", true).fetchSemanticsNode().boundsInRoot
            assertEquals(camera.width, gnss.width, 1f)
            assertEquals(camera.right, gnss.right, 1f)
            assertTrue(gnss.width / density in 72f..121f)
            assertTrue(gnss.height / density <= maxOf(24f, 20f * fontScale) + 1f)
        }
    }

    @Test fun aStatusWiderThan120DpMovesWholeToTheNextLine() {
        compose.setContent {
            JetsonControllerTheme(palette = GeoPalette.GRAPHITE) {
                GeoRowList(GeoRowTrailing.Status, Modifier.width(332.dp)) {
                    GeoRow("장치", {}, badge = {
                        Box(Modifier.testTag("long-status")) { GeoBadge("장치 상태를 다시 확인하세요", Tone.WARNING) }
                    })
                }
            }
        }
        val title = compose.onNodeWithText("장치", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("long-status", true).fetchSemanticsNode().boundsInRoot
        assertTrue(status.top >= title.bottom)
    }

    @Test fun standaloneBadgesRetainWrappingOutsideTheRowStatusColumn() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            JetsonControllerTheme(palette = GeoPalette.STANDARD) {
                Box(Modifier.testTag("standalone-badge")) {
                    GeoBadge("장치 상태를 다시 확인하세요", Tone.WARNING, Modifier.width(90.dp))
                }
            }
        }
        val badge = compose.onNodeWithTag("standalone-badge", true).fetchSemanticsNode().boundsInRoot
        assertTrue(badge.height / density > 40f)
    }

    @Test fun rowsWithoutMetadataCenterTitleStatusAndLeadingTile() {
        compose.setContent {
            JetsonControllerTheme(palette = GeoPalette.GRAPHITE) {
                GeoRow("휴지통", {}, Modifier.width(332.dp), value = "2개",
                    leading = { Box(Modifier.size(24.dp).testTag("leading-tile")) })
            }
        }
        val title = compose.onNodeWithText("휴지통", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("2개", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tile = compose.onNodeWithTag("leading-tile", true).fetchSemanticsNode().boundsInRoot
        assertEquals(tile.center.y, title.center.y, 1f)
        assertEquals(title.center.y, value.center.y, 1f)
    }

    @Test fun anUnselectedFileBrowserHasNoActionSurfaceOrReservedBottomSpace() {
        var visible by mutableStateOf(false)
        compose.setContent {
            JetsonControllerTheme(palette = GeoPalette.GRAPHITE) {
                Box(Modifier.size(364.dp, 600.dp).testTag("viewport")) {
                    GeoActionArea("file-selection", visible = visible, action = {
                        Text("전송 시작", Modifier.testTag("send-action"))
                    }) { inlineAction ->
                        Column(Modifier.fillMaxSize().testTag("body")) { inlineAction() }
                    }
                }
            }
        }
        compose.onNodeWithTag("send-action").assertDoesNotExist()
        assertEquals(compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot.height,
            compose.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("send-action").assertExists()
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("send-action").assertDoesNotExist()
    }

    @Test fun hiddenActionsKeepLocationStateScopedToTheVisibilityKey() {
        var location by mutableIntStateOf(1)
        var visible by mutableStateOf(false)
        compose.setContent {
            JetsonControllerTheme(palette = GeoPalette.STANDARD) {
                GeoActionArea(location, visible = visible, action = {}) {
                    var query by remember { mutableStateOf("수집 자료") }
                    GeoTextField(query, { query = it }, "검색", showLabel = false)
                }
            }
        }
        compose.onNodeWithContentDescription("검색").performTextClearance()
        compose.onNodeWithContentDescription("검색").performTextInput("카메라")
        compose.onNodeWithText("카메라").assertExists()
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("카메라").assertExists()
        compose.runOnIdle { location = 2 }
        compose.onNodeWithText("수집 자료").assertExists()
        compose.onNodeWithText("카메라").assertDoesNotExist()
    }

    @Test fun inlineSearchKeepsItsAccessibleNameAndPlaceholder() {
        var query by mutableStateOf("")
        compose.setContent {
            JetsonControllerTheme(palette = GeoPalette.GRAPHITE) {
                GeoTextField(query, { query = it }, "검색", showLabel = false,
                    leadingIcon = GeoIcons.search, placeholder = "파일 이름 검색")
            }
        }
        compose.onNodeWithText("검색").assertDoesNotExist()
        compose.onNodeWithText("파일 이름 검색", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("검색").performTextInput("capture")
        compose.onNodeWithText("파일 이름 검색", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("capture").assertExists()
    }
}
