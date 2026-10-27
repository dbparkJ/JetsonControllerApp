package com.example.jetsoncontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.JetsonControllerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native measurements and pixels, without repositories or hardware operations. */
class R7ComponentLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rowsKeepA72DpStatusColumnAndFirstTitleLineAlignment() {
        var pixelDensity = 1f
        var scale by mutableFloatStateOf(1f)
        var multiline by mutableStateOf(false)
        var withMeta by mutableStateOf(true)
        var withValue by mutableStateOf(false)
        compose.setContent {
            val base = LocalDensity.current
            pixelDensity = base.density
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                JetsonControllerTheme {
                    GeoRowList(GeoRowTrailing.Status, Modifier.width(364.dp)) {
                        GeoRow(if (multiline) "장치 1\n첫 번째" else "장치 1", {},
                            Modifier.testTag("행1"), meta = if (withMeta) "방금 갱신" else null,
                            value = if (withValue) "23" else null, badge = if (withValue) null else ({
                                Box(Modifier.fillMaxWidth().testTag("상태1")) { GeoBadge("연결됨", Tone.SUCCESS) }
                            }))
                        GeoRow(if (multiline) "장치 2\n두 번째" else "장치 2", {},
                            Modifier.testTag("행2"), meta = if (withMeta) "마지막 확인\n저장된 정보" else null,
                            value = if (withValue) "24" else null, badge = if (withValue) null else ({
                                Box(Modifier.fillMaxWidth().testTag("상태2")) { GeoBadge("대기", Tone.UNKNOWN) }
                            }))
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 1.1f, 1.29f)) {
            for (multipleLines in listOf(false, true)) {
                for (meta in listOf(false, true)) {
                    for (value in listOf(false, true)) {
                        compose.runOnIdle {
                            scale = fontScale
                            multiline = multipleLines
                            withMeta = meta
                            withValue = value
                        }
                        val statuses = (1..2).map { index ->
                            val title = if (multipleLines) "장치 $index\n${if (index == 1) "첫 번째" else "두 번째"}" else "장치 $index"
                            val status = if (value) compose.onNodeWithText(if (index == 1) "23" else "24", useUnmergedTree = true)
                                else compose.onNodeWithTag("상태$index", useUnmergedTree = true)
                            val bounds = status.fetchSemanticsNode().boundsInRoot
                            val titleNode = compose.onNodeWithText(title, useUnmergedTree = true)
                            val layout = titleNode.textLayout()
                            assertEquals(if (multipleLines) 2 else 1, layout.lineCount)
                            val center = titleNode.firstLineCenter(layout)
                            val context = "scale=$fontScale multiline=$multipleLines meta=$meta value=$value row=$index"
                            assertEquals(context, 72f * pixelDensity, bounds.width, 1f)
                            assertEquals(context, center, bounds.center.y, 1f)
                            val arrow = compose.onNode(hasTestTag("geo-row-chevron") and
                                hasAnyAncestor(hasTestTag("행$index")), useUnmergedTree = true)
                                .fetchSemanticsNode().boundsInRoot
                            assertEquals(context, 24f * pixelDensity, arrow.width, 1f)
                            assertEquals(context, center, arrow.center.y, 1f)
                            bounds
                        }
                        assertEquals(statuses[0].right, statuses[1].right, 1f)
                    }
                }
            }
        }
    }

    @Test fun largeTextMovesStatusBelowTitleInTheSameRightColumn() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f)) {
                JetsonControllerTheme {
                    GeoRow("장치 정보", {}, Modifier.width(364.dp), badge = {
                        Box(Modifier.fillMaxWidth().testTag("후행")) { GeoBadge("연결됨", Tone.SUCCESS) }
                    })
                }
            }
        }
        val title = compose.onNodeWithText("장치 정보", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("후행", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(status.top >= title.bottom)
    }

    @Test fun deviceStatusBadgeStaysOnOneLineAtLargeFontScales() {
        var scale by mutableFloatStateOf(1.1f)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                JetsonControllerTheme {
                    GeoBadge("연결 확인 중", Tone.UNKNOWN,
                        Modifier.widthIn(min = 72.dp).testTag("device-status"),
                        minWidth = 72.dp, singleLine = true)
                }
            }
        }
        listOf(1.1f, 2f).forEach { fontScale ->
            compose.runOnIdle { scale = fontScale }
            val badge = compose.onNodeWithTag("device-status")
            badge.assertIsDisplayed()
            val layout = badge.textLayout()
            assertEquals("scale=$fontScale", 1, layout.lineCount)
            assertFalse("scale=$fontScale", layout.hasVisualOverflow)
            assertEquals("연결 확인 중", layout.layoutInput.text.text)
        }
    }

    @Test fun actionRowsAlignActionsWithTheMeasuredFirstTitleLine() {
        var scale by mutableFloatStateOf(1f)
        var multiline by mutableStateOf(false)
        var withMeta by mutableStateOf(false)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                JetsonControllerTheme {
                    Box(Modifier.padding(top = 24.dp)) {
                        GeoActionRow(if (multiline) "장치\n첫 번째" else "장치", "실행", {}, {},
                            Modifier.width(364.dp), meta = if (withMeta) "마지막 확인" else null)
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 1.1f, 1.29f)) {
            for (multipleLines in listOf(false, true)) {
                for (meta in listOf(false, true)) {
                    compose.runOnIdle { scale = fontScale; multiline = multipleLines; withMeta = meta }
                    val title = compose.onNodeWithText(if (multipleLines) "장치\n첫 번째" else "장치", useUnmergedTree = true)
                    val action = compose.onNode(hasText("실행") and hasClickAction()).fetchSemanticsNode().boundsInRoot
                    assertEquals("scale=$fontScale multiline=$multipleLines meta=$meta",
                        title.firstLineCenter(), action.center.y, 1f)
                }
            }
        }
    }

    @Test fun preparationRowsAlignBadgesAndNumericValuesWithTheFirstTitleLine() {
        var scale by mutableFloatStateOf(1f)
        var multiline by mutableStateOf(false)
        var withMeta by mutableStateOf(false)
        var numeric by mutableStateOf(false)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                JetsonControllerTheme {
                    GeoPreparationStatusRow(if (multiline) "점검\n첫 번째" else "점검",
                        if (withMeta) "마지막 확인\n저장된 정보" else null,
                        if (numeric) "23" else "확인됨", Tone.SUCCESS,
                        Modifier.width(364.dp), numericValue = numeric)
                }
            }
        }
        for (fontScale in listOf(1f, 1.1f, 1.29f)) {
            for (multipleLines in listOf(false, true)) {
                for (meta in listOf(false, true)) {
                    for (value in listOf(false, true)) {
                        compose.runOnIdle { scale = fontScale; multiline = multipleLines; withMeta = meta; numeric = value }
                        val title = compose.onNodeWithText(if (multipleLines) "점검\n첫 번째" else "점검", useUnmergedTree = true)
                        val status = if (value) compose.onNodeWithText("23", useUnmergedTree = true)
                            else compose.onNode(hasStateDescription("확인됨"), useUnmergedTree = true)
                        assertEquals("scale=$fontScale multiline=$multipleLines meta=$meta numeric=$value",
                            title.firstLineCenter(), status.fetchSemanticsNode().boundsInRoot.center.y, 1f)
                    }
                }
            }
        }
    }

    @Test fun deviceCardStatusAlignsWithTitleWhileTodayCardStatusFollowsDescription() {
        var scale by mutableFloatStateOf(1f)
        var multiline by mutableStateOf(false)
        var device by mutableStateOf(false)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                JetsonControllerTheme {
                    val title = if (multiline) "제목\n첫 줄" else "제목"
                    if (device) GeoDevicePhotoCard(title, "연결됨", Tone.SUCCESS, "마지막 확인",
                        List(4) { GeoFigure("항목 $it", "1") }, Modifier.width(364.dp))
                    else GeoWorkCard(title, GeoButtonAction("열기", {}), Modifier.width(364.dp),
                        meta = "장치 정보", status = "연결됨")
                }
            }
        }
        for (fontScale in listOf(1f, 1.1f, 1.29f)) {
            for (multipleLines in listOf(false, true)) {
                for (photoCard in listOf(false, true)) {
                    compose.runOnIdle { scale = fontScale; multiline = multipleLines; device = photoCard }
                    val title = compose.onNodeWithText(if (multipleLines) "제목\n첫 줄" else "제목", useUnmergedTree = true)
                    val context = "scale=$fontScale multiline=$multipleLines device=$photoCard"
                    if (photoCard) {
                        val status = compose.onNode(hasStateDescription("연결됨"), useUnmergedTree = true)
                            .fetchSemanticsNode().boundsInRoot
                        assertEquals(context, title.firstLineCenter(), status.center.y, 1f)
                    } else {
                        val description = compose.onNodeWithText("장치 정보 · 연결됨", useUnmergedTree = true)
                            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                        assertTrue(context, description.top >= title.fetchSemanticsNode().boundsInRoot.bottom)
                        compose.onNode(hasStateDescription("연결됨"), useUnmergedTree = true).assertDoesNotExist()
                    }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun SemanticsNodeInteraction.firstLineCenter(layout: TextLayoutResult = textLayout()): Float =
        fetchSemanticsNode().boundsInRoot.top + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f

    @Test fun filterWidthAndHeightStayTheSameWhenSelected() {
        var selected by mutableStateOf(false)
        compose.setContent {
            JetsonControllerTheme {
                GeoFilterChip("연결됨", selected, { selected = !selected }, Modifier.testTag("필터"))
            }
        }
        val chip = compose.onNodeWithTag("필터")
        chip.assertIsNotSelected().assertHeightIsEqualTo(48.dp)
        val before = chip.fetchSemanticsNode().boundsInRoot
        chip.performClick().assertIsSelected().assertHeightIsEqualTo(48.dp)
        assertEquals(before, chip.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun reducedMotionLoaderKeepsTheSamePixelsAcrossTheWholeCycle() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            JetsonControllerTheme {
                GeonLoader(Modifier.testTag("로더"), size = 128.dp, reducedMotion = true)
            }
        }
        val node = compose.onNodeWithTag("로더")
        val first = node.captureToImage()
        val expected = IntArray(first.width * first.height)
        first.readPixels(expected)
        listOf(600L, 600L, 600L, 600L).forEach { millis ->
            compose.mainClock.advanceTimeBy(millis)
            val next = node.captureToImage()
            val actual = IntArray(next.width * next.height)
            next.readPixels(actual)
            assertArrayEquals(expected, actual)
        }
    }
}
