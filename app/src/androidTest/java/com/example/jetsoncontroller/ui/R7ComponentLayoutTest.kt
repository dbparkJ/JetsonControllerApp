package com.example.jetsoncontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.setContent {
            JetsonControllerTheme {
                pixelDensity = LocalDensity.current.density
                GeoRowList(GeoRowTrailing.Status, Modifier.width(364.dp)) {
                    GeoRow("장치 1", {}, meta = "방금 갱신", badge = {
                        Box(Modifier.fillMaxWidth().testTag("상태1")) { GeoBadge("연결됨", Tone.SUCCESS) }
                    })
                    GeoRow("장치 2", {}, meta = "마지막 확인\n저장된 정보", badge = {
                        Box(Modifier.fillMaxWidth().testTag("상태2")) { GeoBadge("대기", Tone.UNKNOWN) }
                    })
                }
            }
        }
        val a = compose.onNodeWithTag("상태1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag("상태2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val titleA = compose.onNodeWithText("장치 1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val titleB = compose.onNodeWithText("장치 2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(72f * pixelDensity, a.width, 1f)
        assertEquals(a.width, b.width, 1f)
        assertEquals(a.right, b.right, 1f)
        assertEquals(titleA.center.y, a.center.y, 1f)
        assertEquals(titleB.center.y, b.center.y, 1f)
    }

    @Test fun largeTextMovesStatusBelowTitleInTheSameRightColumn() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.5f)) {
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
