package com.example.jetsoncontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Shared-kit measurements only; execution and physical inset checks belong to the supervisor. */
class R8ComponentLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun iconAndPhotoRowsUseTheirOwnTextInsetsAndMinimumHeights() {
        var pixelsPerDp = 1f
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            JetsonControllerTheme(GeoPalette.STANDARD) {
                GeoRowList(GeoRowTrailing.None, Modifier.width(364.dp)) {
                    GeoRow("한 줄", {}, Modifier.testTag("single"), leading = { GeoIconTile(GeoIcons.camera) })
                    GeoRow("두 줄", {}, Modifier.testTag("double"), meta = "마지막 확인",
                        leading = { GeoIconTile(GeoIcons.camera) })
                    GeoRow("사진 행", {}, Modifier.testTag("photo"), meta = "장치 ID",
                        leadingIsPhoto = true, leading = { Box(Modifier.size(40.dp)) }, divider = false)
                }
            }
        }
        compose.onNodeWithTag("single").assertHeightIsEqualTo(56.dp)
        compose.onNodeWithTag("double").assertHeightIsEqualTo(72.dp)
        listOf("한 줄" to 56f, "두 줄" to 56f, "사진 행" to 68f).forEach { (title, x) ->
            val bounds = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val group = compose.onNodeWithTag(if (title == "사진 행") "photo" else if (title == "한 줄") "single" else "double")
                .fetchSemanticsNode().boundsInRoot
            assertEquals(x * pixelsPerDp, bounds.left - group.left, 1f)
        }
    }

    @Test fun collapsingTheHeaderKeepsThePrimaryActionAboveNavigationAndOutsideTheTodayCard() {
        lateinit var header: GeoLargeTitleState
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1f)) {
                JetsonControllerTheme(GeoPalette.STANDARD) {
                    Column(Modifier.requiredSize(412.dp, 760.dp)) {
                        Box(Modifier.weight(1f)) {
                            header = rememberGeoLargeTitleState()
                            GeoLargeTitleScaffold("작업", subtitle = "오늘의 수집", state = header,
                                primaryAction = GeoButtonAction("수집 준비", {})) {
                                LazyColumn {
                                    item { GeoTodayCard("중앙로 동측", Modifier.testTag("today")) }
                                    items(30) { index -> GeoRow("항목 $index", {}) }
                                }
                            }
                        }
                        GeoBottomBar(GeoTab.TASKS, {}, Modifier.testTag("navigation"))
                    }
                }
            }
        }
        val action = compose.onNode(hasText("수집 준비") and hasClickAction())
        val before = action.fetchSemanticsNode().boundsInRoot
        compose.onNode(hasText("수집 준비") and hasAnyAncestor(hasTestTag("today"))).assertDoesNotExist()
        compose.runOnIdle { header.connection.onPreScroll(Offset(0f, -10_000f), NestedScrollSource.UserInput) }
        compose.runOnIdle { assertTrue(header.collapsed) }
        compose.onNodeWithTag("geo-compact-bar").assertHeightIsEqualTo(64.dp)
        val after = action.fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        val navigation = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(after.bottom <= navigation.top)
        action.assertHeightIsEqualTo(56.dp)
    }

    @Test fun onePointThreeTextStacksStatusWhileSelectionAndChipWidthRemainStable() {
        var scale by mutableFloatStateOf(1f)
        var selected by mutableStateOf(false)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                JetsonControllerTheme(GeoPalette.NAVY_LIGHT) {
                    Column(Modifier.width(364.dp).background(LocalGeoColors.current.canvas)) {
                        GeoRow("장치 상태", {}, meta = "긴 원인도 끝까지 표시합니다", badge = {
                            Box(Modifier.fillMaxWidth().testTag("status")) { GeoBadge("연결됨", Tone.SUCCESS) }
                        })
                        GeoFilterChip("확인 필요 1", selected, { selected = !selected }, Modifier.testTag("chip"))
                    }
                }
            }
        }
        val chip = compose.onNodeWithTag("chip")
        val width = chip.fetchSemanticsNode().boundsInRoot.width
        chip.performClick().assertIsSelected()
        assertEquals(width, chip.fetchSemanticsNode().boundsInRoot.width, .5f)
        compose.runOnIdle { scale = 1.3f }
        val title = compose.onNodeWithText("장치 상태", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("status", true).fetchSemanticsNode().boundsInRoot
        assertTrue(status.top >= title.bottom)
    }

    @Test fun tabletCaptureIsAn80DpTargetOnTheRight() {
        compose.setContent {
            CompositionLocalProvider(LocalGeoWindow provides geoWindow(1280f, 800f, 1f)) {
                JetsonControllerTheme(GeoPalette.GRAPHITE) {
                    GeoImmersiveMediaFrame(Modifier.requiredSize(1280.dp, 800.dp).testTag("media"),
                        captureAction = {}, captureEnabled = false) {}
                }
            }
        }
        val media = compose.onNodeWithTag("media").fetchSemanticsNode().boundsInRoot
        val capture = compose.onNodeWithContentDescription("캡처").assertIsNotEnabled()
        val bounds = capture.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.center.x > media.center.x)
        // Safe padding belongs to the outer placement; the clickable capture itself stays 80dp.
        capture.assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
    }
}
