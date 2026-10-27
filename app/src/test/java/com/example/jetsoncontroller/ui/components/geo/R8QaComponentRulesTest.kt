package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.ui.graphics.Color
import com.example.jetsoncontroller.ui.components.geo.shell.geoHeaderActionsStack
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoPalette
import org.junit.Assert.*
import org.junit.Test

/** QA regression contracts; geometry itself is verified by the supervisor's gallery. */
class R8QaComponentRulesTest {
    private fun source(name: String) = r8UiSource("components/geo/$name.kt")
    private fun header() = source("shell/GeoLargeTitleScaffold")

    @Test fun qa01RootActionsUseMeasuredTitleAndExactFit() {
        listOf(1f, 1.3f, 2f).forEach {
            assertFalse(geoHeaderActionsStack(312f, it, 200f, 104f))
            assertTrue(geoHeaderActionsStack(311f, it, 200f, 104f))
        }
        assertTrue(header().contains("subcompose(\"action-measure\")"))
    }

    @Test fun qa02SubtitleStatusBelongsToTheSupportingFlow() {
        assertTrue(header().contains("subtitleStatus: GeoStatusSpec? = null"))
        assertTrue(header().contains("GeoSubtitleLine(subtitle, subtitleStatus)"))
        assertTrue(header().contains("FlowRow(horizontalArrangement"))
    }

    @Test fun qa03GroupedStatesAndSkeletonsOwnOneInset() {
        assertTrue(source("GeoRows").contains("LocalGeoGroupInset provides true"))
        listOf("GeoStateBlock", "GeoSkeleton", "GeonLoader").forEach {
            assertTrue(source(it).contains(".geoGroupInset()"))
        }
        assertTrue(source("GeoSkeleton").contains("LocalGeoGroupInset provides false"))
        assertTrue(source("GeoNotes").contains("vertical = if (LocalGeoGroupInset.current) 12.dp else 16.dp"))
    }

    @Test fun qa04ReadOnlyListsDoNotReserveAnEmptyChevronColumn() {
        assertFalse(geoReserveChevron(false, emptyList()))
        assertFalse(geoReserveChevron(false, listOf(false, false)))
        assertTrue(geoReserveChevron(false, listOf(false, true)))
        assertTrue(geoReserveChevron(true, emptyList()))
    }

    @Test fun qa05SelectionRowsShareInsetsAndSkipTheGroupBottomDivider() {
        assertTrue(source("GeoSelection").contains(".padding(horizontal = 16.dp, vertical = 12.dp)"))
        assertTrue(source("GeoRows").contains("group.rowBottoms.values.maxOrNull()"))
        assertFalse(geoGroupLastRow(72f, 144f))
        assertFalse(geoGroupLastRow(142.9f, 144f))
        assertTrue(geoGroupLastRow(143f, 144f))
        assertTrue(geoGroupLastRow(144f, 144f))
        assertTrue(source("GeoRows").contains("if (enabled && !last) drawLine"))
    }

    @Test fun qa06UnknownMetricsUseOneRoleAndRetainTheValueLineHeight() {
        listOf(null, "", "미확인").forEach {
            assertTrue(figurePresentation(GeoFigure("용량", it)).unknown)
        }
        assertFalse(figurePresentation(GeoFigure("용량", "0")).unknown)
        val figures = source("GeoFigureGrid")
        assertTrue(figures.contains(".padding(horizontal = 16.dp, vertical = 12.dp)"))
        assertTrue(figures.contains("if (shown.unknown) type.titleSm"))
        assertTrue(figures.contains("heightIn(min = with(density) { figureStyle.lineHeight.toDp() })"))
        assertTrue(source("GeoCards").contains("type.clock.lineHeight.toDp()"))
    }

    @Test fun qa07OrdinaryStatusNeverHasASoftPill() {
        GeoPalette.entries.forEach { palette ->
            Tone.entries.forEach { tone ->
                assertEquals(Color.Transparent, geoBadgeColors(palette.colors, tone, GeoBadgeVariant.SOFT).fill)
                assertEquals(Color.Transparent, geoBadgeColors(palette.colors, tone, GeoBadgeVariant.INLINE).fill)
            }
        }
    }

    @Test fun qa08CopyControlAlignsWithTheFirstValueLineAndPropertiesUseBaselines() {
        assertTrue(source("GeoR8Content").contains("labelHeight.toDp() }).alignToFirstTitleLine(alignment)"))
        assertTrue(source("GeoProperty").contains("LocalPropertyLabelWidth provides sharedLabelWidth"))
        assertTrue(source("GeoProperty").contains("labelContent(Modifier.width(labelWidth).alignByBaseline())"))
        assertTrue(source("GeoProperty").contains("valueBlock(Modifier.weight(1f).alignByBaseline())"))
        assertEquals(-12, firstTitleLineOffset(0f, 24f, 72, 48, false))
    }

    @Test fun qa09FooterNotesPrecedeActionsAndKeepTwelveDpBottomSpace() {
        val buttons = source("GeoButtons").substringAfter("fun GeoButtonActions(")
        assertTrue(buttons.indexOf("note?.invoke()") < buttons.indexOf("val row = geoActionsFit"))
        assertTrue(header().contains("vertical = 12.dp"))
        assertTrue(header().contains("secondaryAction = secondaryAction, note = note"))
    }

    @Test fun qa10PaneToolbarReusesItsParentGutter() {
        assertTrue(header().contains("padding(horizontal = if (LocalGeoPane.current) 0.dp else 12.dp)"))
    }

    @Test fun qa11MediaHasIndependentSummaryCaptionAndErrorAction() {
        val media = source("GeoMedia")
        listOf("info: GeoMediaInfoSpec? = null", "caption: (@Composable () -> Unit)? = null",
            "state: GeoStateContent? = null", "onRetry: (() -> Unit)? = null",
            "retryEnabled: Boolean = true",
            "loadingText: String? = null", "maxLines = 2", "GeonLoader(media = true)").forEach {
            assertTrue(it, media.contains(it))
        }
        assertEquals(2, Regex("clip\\(GeoShape.circle\\).background\\(GeoFixedColors.mediaPlate").findAll(media).count())
    }

    @Test fun qa12StateColumnAndItsActionHaveAMaximumWidth() {
        assertTrue(source("GeoStateBlock").contains("Modifier.widthIn(max = 560.dp).fillMaxWidth()"))
    }

    @Test fun qa13StackedActionsKeepCallerOrder() {
        val actions = source("GeoButtons").substringAfter("fun GeoButtonActions(")
        assertFalse(actions.contains("sortedBy"))
        assertTrue(actions.contains("actions.forEach { action(it, Modifier.fillMaxWidth()) }"))
    }

    @Test fun qa14LeadingControlsUseFirstTitleLineAtEveryScale() {
        assertTrue(source("GeoRows").contains(".height(anatomy.leadingSizeDp.dp)\n                    .alignToFirstTitleLine(titleAlignment)"))
        assertTrue(source("GeoSelection").contains("Modifier.size(24.dp).alignToFirstTitleLine(titleAlignment)"))
        assertTrue(source("GeoSelection").contains("onTextLayout = titleAlignment::onTextLayout"))
    }

    @Test fun qa15ActionWidthsShareMeasuredLabelsAndStackOnlyOnOverflow() {
        assertEquals(88f, geoActionWidth(emptyList()), 0f)
        assertEquals(137f, geoActionWidth(listOf(80f, 137f, 92f)), 0f)
        assertFalse(geoActionStacks(289f, 137f, 144f))
        assertTrue(geoActionStacks(288f, 137f, 144f))
        assertTrue(source("GeoRows").contains("textAlign = TextAlign.Center, maxLines = 1, softWrap = false"))
    }

    @Test fun qa16QuickTilesMeasureAllLabelsAndStartAtTheTop() {
        val cards = source("GeoCards")
        assertTrue(cards.contains("val labelHeight = actions.maxOfOrNull"))
        assertTrue(cards.contains("Modifier.width(104.dp).height(tileHeight)"))
        assertTrue(cards.contains("Modifier.weight(1f).fillMaxHeight()"))
        assertTrue(cards.contains("Arrangement.spacedBy(8.dp, Alignment.Top)"))
    }

    @Test fun qa17TodayDeadlineStacksOnlyOnMeasuredOverflowAndStatusFollowsMeta() {
        assertFalse(geoTodayLabelStacks(280f, 2f, 168f, 104f))
        assertTrue(geoTodayLabelStacks(279f, 1f, 168f, 104f))
        val cards = source("GeoCards")
        assertFalse(cards.contains("deadline?.let { Text(it, Modifier.align(Alignment.End)"))
        assertTrue(cards.contains("listOfNotNull(meta, descriptionStatus ?: status)"))
    }

    @Test fun qa18DetailToolbarDoesNotConsultTheRootStackPolicy() {
        val tools = header().substringAfter("if (showTools) GeoDetailTools").substringBefore("Column(Modifier.padding")
        assertTrue(tools.contains("content = actions"))
        assertFalse(tools.contains("if (!stackActions)"))
        assertTrue(header().contains("detail && (back != null || actionWidth > 0)"))
    }

    @Test fun qa19NumbersAndUnitsRemainIntactAndMeasureWithTabularFigures() {
        val displayed = geoBreakableStatus("16.4 KB")
        assertEquals("16.4 KB", displayed.replace("\u2060", ""))
        assertTrue(displayed.contains("K\u2060B"))
        assertEquals(1, displayed.count { it == ' ' })
        assertTrue(source("GeoRows").contains("type.inlineStatus.copy(fontFeatureSettings = \"tnum\"), softWrap = false"))
    }

    @Test fun qa20AddressBreaksPreserveTheCopySource() {
        val original = "https://receiver.example.invalid/a_b-0/"
        val displayed = geoBreakableValue(original)
        assertEquals(original, displayed.replace("\u200B", ""))
        listOf("/\u200B", ".\u200B", "_\u200B", "-\u200B").forEach { assertTrue(displayed.contains(it)) }
        assertTrue(source("GeoR8Content").contains("clipboard.setText(AnnotatedString(value))"))
        assertTrue(geoPropertyListStacks(1.3f, 500f, listOf(1)))
    }

    @Test fun qa21IndependentActionRowsKeepTwelveDpVerticalPadding() {
        assertTrue(source("GeoRows").substringAfter("fun GeoActionRow(")
            .contains("contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)"))
    }

    @Test fun qa22ReceiptDisplayDeduplicatesNamesWithoutChangingEvidence() {
        assertEquals("완료", receiptTrackWord(0, "종료"))
        assertEquals("완료", receiptTrackWord(1, "저장"))
        assertEquals("미확인", receiptTrackWord(2, "미확인"))
        assertEquals("보고 전", receiptTrackWord(3, "보고 전"))
        assertFalse(receiptDone(GeoReceiptStep.SERVER, GeoReceiptItem("수신", Tone.SUCCESS)))
    }

    @Test fun qa23InverseProgressUsesDistinctTrackAndFill() {
        assertTrue(source("GeoCards").contains("LocalGeoInverseSurface provides true"))
        assertTrue(source("GeoProgress").contains("if (inverse) c.text.copy(alpha = .20f) else c.field"))
        assertTrue(source("GeoProgress").contains("if (inverse) c.text else c.primary"))
    }

    @Test fun qa24MediaPropertiesIncludeTheSubtleLabelRole() {
        assertTrue(source("GeoMedia").contains("textSubtle = GeoFixedColors.mediaText"))
        assertTrue(source("GeoProperty").contains("Text(label, style = type.propertyLabel, color = mediaLabel)"))
    }

    @Test fun qa25DisabledReasonsShareTheWholeActionGroup() {
        val actions = source("GeoButtons").substringAfter("fun GeoButtonActions(")
        assertTrue(actions.contains("actions.filter { !it.enabled }.mapNotNull { it.disabledReason }.distinct()"))
        assertTrue(actions.contains("action.icon, disabledReason = null"))
        assertTrue(actions.indexOf("reasons.forEach") < actions.indexOf("if (row)"))
    }
}
