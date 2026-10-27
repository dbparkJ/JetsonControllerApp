package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleState
import com.example.jetsoncontroller.ui.components.geo.shell.geoHeaderCollapsed
import com.example.jetsoncontroller.ui.components.geo.shell.geoHeaderActionsStack
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.*
import org.junit.Test

class R8ComponentRulesTest {
    @Test fun todayDeadlineMovesBelowTheLabelAtLargeTextOrMeasuredOverflow() {
        assertFalse(geoTodayLabelStacks(280f, 1f, 168f, 104f))
        assertTrue(geoTodayLabelStacks(279f, 1f, 168f, 104f))
        assertFalse(geoTodayLabelStacks(720f, 1.29f, 168f, 104f))
        listOf(1.3f, 1.5f, 2f).forEach { assertFalse(geoTodayLabelStacks(720f, it, 168f, 104f)) }
        val source = r8UiSource("components/geo/GeoCards.kt")
        assertTrue(source.contains("deadline: String? = null"))
        assertTrue(source.contains("geoTodayLabelStacks(maxWidth.value, density.fontScale, labelWidth, deadlineWidth)"))
    }

    @Test fun compactReceiptKeepsAllFourNamesAndTheirIndependentStates() {
        val state = GeoReceiptState(
            collection = GeoReceiptItem("종료", Tone.SUCCESS, done = true),
            storage = GeoReceiptItem("저장 확인", Tone.SUCCESS, done = true))
        assertEquals(listOf(true, true, false, false), state.rows().map { receiptDone(it.first, it.second) })
        assertEquals("수집 종료: 종료, 장치 저장: 저장 확인, 서버 수신: 미확인, GEO& Work 보고: 보고 전", receiptDescription(state))
        assertFalse(receiptDone(GeoReceiptStep.SERVER, GeoReceiptItem("미확인", Tone.SUCCESS)))
        assertFalse(receiptDone(GeoReceiptStep.REPORT, GeoReceiptItem("해당 없음", Tone.SUCCESS, done = true)))
        assertTrue(receiptDone(GeoReceiptStep.REPORT, GeoReceiptItem("보고 완료", Tone.SUCCESS, done = true)))
    }

    @Test fun compactReceiptUsesFourEightDpDotsWithFourDpGapsAndKeepsTheFullDefault() {
        val source = r8UiSource("components/geo/GeoReceipt.kt")
        assertTrue(source.contains("compact: Boolean = false"))
        assertTrue(source.contains("modifier.size(44.dp, 8.dp)"))
        assertTrue(source.contains("(4 + index * 12).dp.toPx()"))
        assertTrue(source.contains("contentDescription = receiptDescription(state)"))
        assertTrue(source.contains("else ReceiptTrack(state, modifier)"))
    }

    @Test fun selectionTitleUsesTheSharedRoleAndLargeTextPlacesBadgesBelowIt() {
        val source = r8UiSource("components/geo/GeoSelection.kt")
        assertTrue(source.contains("val stackBadge = LocalDensity.current.fontScale >= 1.3f"))
        assertTrue(source.contains("style = LocalGeoType.current.titleSm"))
        assertTrue(source.contains("if (!stackBadge) badge?.invoke()"))
        assertTrue(source.contains("if (stackBadge && badge != null) Box(Modifier.align(Alignment.End))"))
    }

    @Test fun bothHeadersMeasureActionsAndRetainTheirOrder() {
        assertFalse(geoHeaderActionsStack(312f, 1f, 104f, 80f))
        assertFalse(geoHeaderActionsStack(312f, 1.29f, 104f, 80f))
        assertFalse(geoHeaderActionsStack(312f, 1.3f, 104f, 80f))
        assertFalse(geoHeaderActionsStack(720f, 1f, 160f, 80f))
        assertTrue(geoHeaderActionsStack(200f, 1f, 104f, 100f))
        assertFalse(geoHeaderActionsStack(200f, 2f, 0f))
        val source = r8UiSource("components/geo/shell/GeoLargeTitleScaffold.kt")
        val expanded = source.substringAfter("subcompose(\"large\")").substringBefore("subcompose(\"compact\")")
        val compact = source.substringAfter("subcompose(\"compact\")")
        listOf(expanded, compact).forEach {
            assertTrue(it.contains("if (stackActions) Row("))
            assertTrue(it.contains("content = actions"))
            assertFalse(it.contains("sorted"))
        }
        assertFalse(compact.contains("vertical = 8.dp"))
    }

    @Test fun mapFooterAppliesTheCallerModifierToItsButton() {
        val source = r8UiSource("components/geo/GeoMapSheetScaffold.kt")
        assertTrue(source.contains("primaryActionModifier: Modifier = Modifier"))
        assertTrue(source.contains("GeoButton(primaryAction.label, primaryAction.onClick, primaryActionModifier.fillMaxWidth()"))
    }

    @Test fun mapBackdropBypassesTheColumnLimitEvenWhenLargeTextDisablesTwoPanes() {
        val source = r8UiSource("components/geo/shell/GeoAppShell.kt")
        val column = source.substringAfter("val column =").substringBefore("Box(column")
        assertTrue(column.contains("if (mapBackdrop || fullBleed ||"))
        assertTrue(column.contains("else Modifier.widthIn(max = 720.dp)"))
    }

    @Test fun forbiddingRevealOverridesAnyPreviouslySavedVisibleState() {
        listOf(false, true).forEach { previouslyVisible ->
            assertTrue(geoPasswordMasked(true, false, previouslyVisible))
            assertFalse(geoPasswordMasked(false, false, previouslyVisible))
        }
        assertTrue(geoPasswordMasked(true, true, false))
        assertFalse(geoPasswordMasked(true, true, true))
        val source = r8UiSource("components/geo/GeoFields.kt")
        assertTrue(source.contains("allowPasswordReveal: Boolean = true"))
        assertTrue(source.contains("if (password && allowPasswordReveal) {"))
        assertTrue(source.contains("visualTransformation = if (geoPasswordMasked(password, allowPasswordReveal, passwordVisible))"))
    }

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
