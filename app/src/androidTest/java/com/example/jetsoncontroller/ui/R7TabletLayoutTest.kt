package com.example.jetsoncontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.survey.SurveyRunUiState
import com.example.jetsoncontroller.ui.tasks.*
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fixed dp viewports fit inside the test surface without changing device configuration. */
class R7TabletLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun railAndBottomTabsKeepTheSameFourDestinationsAndAttention() {
        var width by mutableFloatStateOf(800f)
        compose.setContent { Viewport(width, 1333f) {} }
        compose.onNodeWithTag("레일").assertWidthIsEqualTo(80.dp)
        listOf("작업", "장치", "전송", "설정").forEach { compose.onNodeWithText(it).assertHasClickAction() }
        compose.onNodeWithText("장치").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "확인할 알림 있음"))
        compose.runOnIdle { width = 599f }
        compose.onNodeWithTag("레일").assertDoesNotExist()
        compose.onNodeWithTag("하단 탐색").assertExists()
        listOf("작업", "장치", "전송", "설정").forEach { compose.onNodeWithText(it).assertHasClickAction() }
    }

    @Test fun portraitAndLandscapeMeasureTheSpecifiedWidthsAnd24DpGap() {
        var width by mutableFloatStateOf(800f)
        var height by mutableFloatStateOf(1333f)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            Viewport(width, height, scale) {
                GeoPaneLayout(singlePane = GeoPaneSelection.LIST, detailFill = true,
                    list = { Box(Modifier.fillMaxSize().testTag("목록")) },
                    detail = { Box(Modifier.fillMaxSize().testTag("상세")) })
            }
        }
        compose.onNodeWithTag("목록").assertWidthIsEqualTo(720.dp)
        compose.onNodeWithTag("상세").assertDoesNotExist()
        compose.runOnIdle { width = 1333f; height = 800f }
        compose.onNodeWithTag("목록").assertWidthIsEqualTo(400.dp)
        compose.onNodeWithTag("상세").assertWidthIsEqualTo(781.dp)
        val list = compose.onNodeWithTag("목록").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithTag("상세").fetchSemanticsNode().boundsInRoot
        assertEquals(24f * .5f, detail.left - list.right, 1f)
        compose.runOnIdle { scale = 1.3f }
        compose.onNodeWithTag("목록").assertWidthIsEqualTo(416.dp)
        compose.runOnIdle { scale = 1.5f }
        compose.onNodeWithTag("목록").assertWidthIsEqualTo(720.dp)
        compose.onNodeWithTag("상세").assertDoesNotExist()
    }

    @Test fun selectedDetailScrollDraftAndConfirmationSurviveResizeAndRecreation() {
        var width by mutableFloatStateOf(1333f)
        var observedScroll: LazyListState? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Viewport(width, 800f) {
                var selected by rememberSaveable { mutableStateOf("장치 2") }
                GeoPaneLayout(singlePane = GeoPaneSelection.DETAIL,
                    list = { GeoButton("장치 2", { selected = "장치 2" }) },
                    detail = {
                        var query by rememberSaveable { mutableStateOf("수집 자료") }
                        var confirming by rememberSaveable { mutableStateOf(false) }
                        val scroll = rememberLazyListState()
                        SideEffect { observedScroll = scroll }
                        if (confirming) GeoConfirmDialog("선택한 대상 확인", selected, "닫기",
                            onConfirm = { confirming = false }, onDismissRequest = { confirming = false })
                        Column(Modifier.fillMaxSize().testTag("선택 상세")) {
                            GeoTextField(query, { query = it }, "검색", showLabel = false)
                            GeoActionArea("선택 상세", action = {
                                GeoButton("대상 확인", { confirming = true }, Modifier.fillMaxWidth().testTag("상세 행동"))
                            }) { inlineAction ->
                                LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("상세 스크롤")) {
                                    items(60) { Text("자료 $it", Modifier.height(48.dp)) }
                                    item { inlineAction() }
                                }
                            }
                        }
                    })
            }
        }
        compose.onNodeWithContentDescription("검색").performTextReplacement("카메라")
        compose.onNodeWithTag("상세 스크롤").performScrollToIndex(20)
        var index = 0
        compose.runOnIdle { index = requireNotNull(observedScroll).firstVisibleItemIndex }
        val detail = compose.onNodeWithTag("선택 상세").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("상세 행동").fetchSemanticsNode().boundsInRoot
        assertTrue(action.left >= detail.left && action.right <= detail.right)
        compose.onNodeWithTag("상세 행동").performClick()
        compose.runOnIdle { width = 800f }
        compose.onNodeWithText("선택한 대상 확인").assertExists()
        compose.runOnIdle { assertEquals(index, requireNotNull(observedScroll).firstVisibleItemIndex) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("선택한 대상 확인").assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("카메라").assertExists()
        compose.runOnIdle { width = 1333f }
        compose.runOnIdle { assertEquals(index, requireNotNull(observedScroll).firstVisibleItemIndex) }
    }

    @Test fun aConfirmationOpenedInTheListRemainsOpenWhenTheListCollapses() {
        var width by mutableFloatStateOf(1333f)
        compose.setContent {
            Viewport(width, 800f) {
                GeoPaneLayout(singlePane = GeoPaneSelection.DETAIL, list = {
                    var confirming by rememberSaveable { mutableStateOf(false) }
                    GeoButton("전송 취소", { confirming = true })
                    if (confirming) GeoConfirmDialog("전송을 취소할까요?", "선택한 전송", "계속 보내기",
                        onConfirm = { confirming = false }, onDismissRequest = { confirming = false })
                }, detail = { Text("전송 상세") })
            }
        }
        compose.onNodeWithText("전송 취소").performClick()
        compose.runOnIdle { width = 800f }
        compose.onNodeWithText("전송을 취소할까요?").assertExists()
    }

    @Test fun workHomeRendersAtBothTabletDesignSizes() {
        var width by mutableFloatStateOf(800f)
        var height by mutableFloatStateOf(1333f)
        compose.setContent {
            Viewport(width, height) {
                val state = buildTasksHomeUiState(TasksHomeInput("GEO-X-1", true,
                    SurveyRunUiState(), emptyList(), emptyList(), emptyList(), false))
                    .copy(focus = null, ticketSection = ServerTicketUiState())
                TasksHomeScreen(state, onSelectDevice = {}, onStartSurvey = {}, onChangeSection = {},
                    onOpenActiveRun = {}, onOpenRun = {}, onOpenHistory = {}, onOpenTask = {},
                    onCamera = {}, onSendFiles = {}, onDeviceMap = {}, onDeviceFiles = {})
            }
        }
        compose.onNodeWithText("빠른 기능").assertExists()
        compose.runOnIdle { width = 1333f; height = 800f }
        compose.onNodeWithText("빠른 기능").assertExists()
        compose.onNodeWithText("수집 구간").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("최근 수집").assertExists()
    }

    @Composable
    private fun Viewport(width: Float, height: Float, scale: Float = 1f, content: @Composable () -> Unit) {
        val window = geoWindow(width, height, scale)
        CompositionLocalProvider(LocalDensity provides Density(.5f, scale), LocalGeoWindow provides window,
            LocalGeoChrome provides GeoChromeMeasurements(viewportDp = height)) {
            JetsonControllerTheme {
                if (window.rail) Row(Modifier.requiredSize(width.dp, height.dp)) {
                    GeoRail(GeoTab.DEVICES, {}, Modifier.testTag("레일"), setOf(GeoTab.DEVICES, GeoTab.TRANSFERS))
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                } else Column(Modifier.requiredSize(width.dp, height.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    GeoBottomBar(GeoTab.DEVICES, {}, Modifier.testTag("하단 탐색"), attentionSections = setOf(GeoTab.DEVICES))
                }
            }
        }
    }
}
