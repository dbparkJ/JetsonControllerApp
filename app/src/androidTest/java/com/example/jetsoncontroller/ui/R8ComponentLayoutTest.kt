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
import androidx.compose.ui.semantics.SemanticsActions
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

    @Test fun photoWrappersKeepTheirFortyDpImageAndSixtyEightDpTextInset() {
        var pixelsPerDp = 1f
        var selected by mutableStateOf(false)
        compose.setContent {
            pixelsPerDp = LocalDensity.current.density
            JetsonControllerTheme {
                GeoRowList(GeoRowTrailing.None, Modifier.width(364.dp)) {
                    GeoPaneListRow("선택 사진", { selected = !selected }, selected, 0, 2, false,
                        Modifier.testTag("photo-pane"), meta = "장치 상태", leadingIsPhoto = true,
                        leading = { Box(Modifier.size(40.dp).testTag("pane-photo")) })
                    GeoActionRow("행동 사진", "등록", {}, {}, Modifier.testTag("photo-action"),
                        meta = "주변 장치", leadingIsPhoto = true,
                        leading = { Box(Modifier.size(40.dp).testTag("action-photo")) }, divider = false)
                }
            }
        }
        listOf(Triple("선택 사진", "photo-pane", "pane-photo"), Triple("행동 사진", "photo-action", "action-photo")).forEach { (title, rowTag, photoTag) ->
            val row = compose.onNodeWithTag(rowTag, true).fetchSemanticsNode().boundsInRoot
            val text = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val photo = compose.onNodeWithTag(photoTag, true).assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(16f * pixelsPerDp, photo.left - row.left, 1f)
            assertEquals(68f * pixelsPerDp, text.left - row.left, 1f)
        }
        val before = compose.onNodeWithText("선택 사진", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNode(hasText("선택 사진") and hasClickAction()).performClick().assertIsSelected()
        assertEquals(before, compose.onNodeWithText("선택 사진", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
    }

    @Test fun titlePhotoCentersBesideTheWholeTitleBlockStacksOnOverflowAndDisappearsWhenCollapsed() {
        var width by mutableFloatStateOf(412f)
        var scale by mutableFloatStateOf(1f)
        lateinit var header: GeoLargeTitleState
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.5f, scale)) {
                JetsonControllerTheme {
                    header = rememberGeoLargeTitleState()
                    GeoLargeTitleHeader("장치 이름", header, Modifier.requiredWidth(width.dp), subtitle = "마지막 응답",
                        titleTrailingContent = { Box(Modifier.size(80.dp).testTag("title-photo")) })
                }
            }
        }
        val title = compose.onNodeWithText("장치 이름").fetchSemanticsNode().boundsInRoot
        val subtitle = compose.onNodeWithText("마지막 응답").fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithTag("title-photo").assertWidthIsEqualTo(80.dp).fetchSemanticsNode().boundsInRoot
        assertTrue(photo.left >= title.right)
        assertEquals((title.top + subtitle.bottom) / 2f, photo.center.y, 1f)
        listOf(Triple(412f, 1.3f, false), Triple(600f, 2f, false),
            Triple(240f, 1f, true), Triple(240f, 2f, true)).forEach { (w, font, stacked) ->
            compose.runOnIdle { width = w; scale = font }
            val heading = compose.onNodeWithText("장치 이름").fetchSemanticsNode().boundsInRoot
            val supporting = compose.onNodeWithText("마지막 응답").fetchSemanticsNode().boundsInRoot
            val currentPhoto = compose.onNodeWithTag("title-photo").fetchSemanticsNode().boundsInRoot
            if (stacked) assertTrue(currentPhoto.top >= supporting.bottom)
            else {
                assertTrue(currentPhoto.left >= heading.right)
                assertEquals((heading.top + supporting.bottom) / 2f, currentPhoto.center.y, 1f)
            }
        }
        compose.runOnIdle { header.connection.onPreScroll(Offset(0f, -10_000f), NestedScrollSource.UserInput) }
        compose.onNodeWithTag("title-photo").assertDoesNotExist()
        compose.onNodeWithTag("geo-compact-bar").assertHeightIsEqualTo(64.dp)
    }

    @Test fun propertyLabelsShareMeasuredWidthAndStackWhenValueSpaceOrFontRequiresIt() {
        var width by mutableFloatStateOf(608f)
        var scale by mutableFloatStateOf(1f)
        var explicitWidth by mutableStateOf<androidx.compose.ui.unit.Dp?>(null)
        var withIcons by mutableStateOf(false)
        val properties = listOf("담당" to "관리자", "GEO& Work 보고" to "보고 전", "마지막 서버 수신 확인" to "확인됨")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(.5f, scale)) {
                JetsonControllerTheme {
                    GeoPropertyList(properties.map { it.first }, Modifier.requiredWidth(width.dp),
                        hasIcons = withIcons, labelWidth = explicitWidth) {
                        properties.forEach { (label, value) ->
                            GeoPropertyRow(label, value, icon = if (withIcons) GeoIcons.info else null)
                        }
                    }
                }
            }
        }
        fun checkColumns(expectedWidth: Float? = null) {
            val values = properties.map { (label, value) ->
                val labelNode = compose.onNodeWithText(label, useUnmergedTree = true)
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                labelNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.single().lineCount)
                assertFalse(layouts.single().hasVisualOverflow)
                val labelBounds = labelNode.fetchSemanticsNode().boundsInRoot
                val valueBounds = compose.onNodeWithText(value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(valueBounds.left - labelBounds.right >= 8f * .5f - 1f)
                assertTrue(valueBounds.top < labelBounds.bottom)
                valueBounds
            }
            assertEquals(values[0].left, values[1].left, .5f)
            val firstLabel = compose.onNodeWithText(properties.first().first, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            if (expectedWidth != null) {
                val iconInset = if (withIcons) 26f else 0f
                assertEquals((expectedWidth + 8f - iconInset) * .5f, values.first().left - firstLabel.left, 1f)
            } else assertTrue(values.first().left - firstLabel.left > (104f + 8f) * .5f)
        }
        checkColumns()
        compose.runOnIdle { explicitWidth = 180.dp; withIcons = true }
        checkColumns(180f)
        compose.runOnIdle { explicitWidth = null; withIcons = false }
        listOf(260f to 1f, 608f to 1.3f).forEach { (w, font) ->
            compose.runOnIdle { width = w; scale = font }
            properties.forEach { (label, value) ->
                val labelBounds = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val valueBounds = compose.onNodeWithText(value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(labelBounds.left, valueBounds.left, .5f)
                assertTrue(valueBounds.top >= labelBounds.bottom)
            }
        }
    }

    @Test fun bothOverlayFooterActionsAndStandaloneButtonShowTheirDisabledReasons() {
        compose.setContent {
            JetsonControllerTheme {
                Column(Modifier.width(364.dp)) {
                    GeoSheetFooter(GeoOverlayAction("저장", {}, enabled = false, disabledReason = "저장 위치를 고르세요."),
                        GeoOverlayAction("다음", {}, enabled = false, disabledReason = "등록 코드를 확인하세요."))
                    GeoOverlayButton(GeoOverlayAction("실행", {}, enabled = false, disabledReason = "연결을 확인하세요."))
                }
            }
        }
        listOf("저장", "다음", "실행").forEach { compose.onNode(hasText(it) and hasClickAction()).assertIsNotEnabled() }
        listOf("저장 위치를 고르세요.", "등록 코드를 확인하세요.", "연결을 확인하세요.").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }
}
