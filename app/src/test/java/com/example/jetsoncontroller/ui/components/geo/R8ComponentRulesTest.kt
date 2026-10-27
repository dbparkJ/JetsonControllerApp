package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleState
import com.example.jetsoncontroller.ui.components.geo.shell.geoHeaderCollapsed
import org.junit.Assert.*
import org.junit.Test

class R8ComponentRulesTest {
    @Test fun headerChangesOnlyAtTheMeasuredCollapseBoundary() {
        assertFalse(geoHeaderCollapsed(0f, 0f))
        assertFalse(geoHeaderCollapsed(39f, 40f))
        assertTrue(geoHeaderCollapsed(39.5f, 40f))
        val state = GeoLargeTitleState()
        state.updateRange(80f)
        assertFalse(state.collapsed)
        assertEquals(Offset(0f, -80f), state.connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput))
        assertTrue(state.collapsed)
        state.updateRange(40f)
        assertEquals(40f, state.offsetPx, 0f)
        assertEquals(Offset(0f, 40f), state.connection.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput))
        assertFalse(state.collapsed)
        state.updateRange(-1f)
        assertEquals(0f, state.rangePx, 0f)
        assertEquals(0f, state.offsetPx, 0f)
    }

    @Test fun rowGeometrySeparatesIconsAndPhotosWithoutChangingTextOnSelection() {
        assertEquals(GeoRowAnatomy(56, 16, 24, 56, 56), geoRowAnatomy(false))
        assertEquals(GeoRowAnatomy(72, 16, 24, 56, 56), geoRowAnatomy(true))
        assertEquals(GeoRowAnatomy(72, 16, 40, 68, 68), geoRowAnatomy(true, photo = true))
        listOf(1f, 1.29f, 1.3f, 1.5f, 2f).forEach { scale ->
            assertEquals(scale >= 1.3f, rowStatusStacks(364f, scale, 72f))
        }
    }

    @Test fun chipWidthDependsOnlyOnTextAndInsetsAndNeverOnSelection() {
        assertEquals(48, geoFilterWidthPx(8, 16, 48))
        assertEquals(96, geoFilterWidthPx(64, 16, 48))
        assertEquals(144, geoFilterWidthPx(96, 24, 72))
    }

    @Test fun reducedMotionKeepsColourFeedbackButSuppressesScale() {
        assertEquals(.97f, geoPressedScale(true, false), 0f)
        assertEquals(1f, geoPressedScale(false, false), 0f)
        assertEquals(1f, geoPressedScale(true, true), 0f)
    }

    @Test fun keyboardKindsKeepServerAndIdentifierInputLiteral() {
        assertEquals(KeyboardType.Uri, geoKeyboardOptions(GeoInputKind.SERVER_ADDRESS).keyboardType)
        listOf(GeoInputKind.PORT, GeoInputKind.CODE).forEach {
            assertEquals(KeyboardType.Number, geoKeyboardOptions(it).keyboardType)
        }
        val id = geoKeyboardOptions(GeoInputKind.IDENTIFIER, ImeAction.Next)
        assertEquals(KeyboardCapitalization.None, id.capitalization)
        assertEquals(false, id.autoCorrectEnabled)
        assertEquals(ImeAction.Next, id.imeAction)
        assertEquals(ImeAction.Search, geoKeyboardOptions(GeoInputKind.SEARCH).imeAction)
    }
}
