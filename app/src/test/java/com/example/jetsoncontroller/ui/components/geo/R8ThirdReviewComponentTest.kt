package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.*
import org.junit.Test

class R8ThirdReviewComponentTest {
    private fun source(name: String) = r8UiSource("components/geo/$name.kt")

    @Test fun mediaLoaderStaysVisibleAtRevealAndFadeBoundaries() {
        val base = loaderBaseAlpha(media = true)
        assertTrue(base >= .6f)
        (0..240).forEach { tick ->
            val frame = loaderFrame(64f, tick / 240f)
            val revealedAlpha = frame.foregroundAlpha + base * (1f - frame.foregroundAlpha)
            assertTrue(revealedAlpha >= base)
        }
        assertEquals(.16f, loaderBaseAlpha(media = false), 0f)
        val loader = source("GeonLoader")
        assertTrue(loader.contains("ColorFilter.tint(GeoFixedColors.mediaText)"))
        assertTrue(loader.contains("alpha = loaderBaseAlpha(media), colorFilter = tint"))
        assertTrue(source("GeoMedia").contains("GeonLoader(media = true)"))
    }

    @Test fun largerTextMovesLongDescriptionsBelowTheSharedActionColumn() {
        // These titles fit alongside the action; the description is what needs room.
        listOf(1.3f, 1.5f, 2f).forEach { scale ->
            assertTrue(geoActionStacks(312f, 100f, 120f, scale, bodyLines = 3))
            assertFalse(geoActionStacks(312f, 100f, 120f, scale, bodyLines = 2))
        }
        assertFalse(geoActionStacks(312f, 100f, 120f, 1.29f, bodyLines = 3))
        // A long title and a photo consume their actual width, even at the default scale.
        assertTrue(geoActionStacks(272f, 100f, 180f))
        assertFalse(geoActionStacks(288f, 100f, 180f))
        val rows = source("GeoRows").substringAfter("fun GeoActionRow(")
        assertFalse(rows.contains("coerceAtMost(144f)"))
        assertTrue(rows.contains("maxWidth - horizontalPadding - leadingWidth"))
        assertTrue(rows.contains("Constraints(maxWidth = inlineBodyWidth)).lineCount"))
        assertTrue(rows.contains("disabledReason?.takeIf { !actionEnabled }"))
        assertTrue(rows.contains("if (stacked) FixedRowAction"))
        assertTrue(rows.contains("textAlign = TextAlign.Center, maxLines = 1, softWrap = false"))
    }

    @Test fun absentStatusDoesNotCreateAColumnOrAStackedLine() {
        listOf(null, "", " ").forEach { value ->
            assertFalse(geoRowHasStatus(enabled = true, value = value, hasBadge = false))
        }
        assertTrue(geoRowHasStatus(enabled = true, value = "미확인", hasBadge = false))
        assertTrue(geoRowHasStatus(enabled = true, value = null, hasBadge = true))
        assertFalse(geoRowHasStatus(enabled = false, value = "연결됨", hasBadge = true))
        assertEquals(0f, rowStatusWidth(emptyList()), 0f)
        assertEquals(94f, rowStatusWidth(listOf(76f, 94f)), 0f)
        val rows = source("GeoRows").substringBefore("private fun RowStatusCell(")
        assertTrue(rows.contains("if (hasStatus) widths?.set(rowKey, requiredWidth) else widths?.remove(rowKey)"))
        assertTrue(rows.contains("val stacked = hasStatus &&"))
        assertTrue(rows.contains("if (hasStatus && !stacked) RowStatusCell"))
        assertTrue(source("GeoRows").contains("value?.takeIf(String::isNotBlank)?.let"))
    }

    @Test fun subtitleStatusAndSupportingTextShareTheirFirstBaseline() {
        val subtitle = source("shell/GeoLargeTitleScaffold").substringAfter("private fun GeoSubtitleLine(")
        assertTrue(subtitle.contains("GeoBadge(it.word, it.tone, Modifier.alignByBaseline())"))
        assertTrue(subtitle.contains("Text(if (status == null) it else \"· \$it\", Modifier.alignByBaseline()"))
    }

    @Test fun ordinaryStateColumnsStartAtTheirPaneTitleWhileMediaCanCenter() {
        val state = source("GeoStateBlock")
        assertTrue(state.contains("if (fullScreen || LocalGeoMediaStage.current)"))
        assertTrue(state.contains("else androidx.compose.ui.Alignment.TopStart"))
        assertTrue(state.contains("Modifier.widthIn(max = 560.dp).fillMaxWidth()"))
    }

    @Test fun customIdentityRowParticipatesInGroupBoundariesWithoutABottomLine() {
        val identity = r8UiSource("devices/DeviceScreenComponents.kt")
            .substringAfter("internal fun DeviceIdentityRow(").substringBefore("internal fun DevicePowerButtons(")
        assertTrue(identity.contains("contentDivider(startIndent = 56.dp, enabled = false)"))
        assertFalse(geoGroupLastRow(144f, 216f))
        assertTrue(geoGroupLastRow(216f, 216f))
    }
}
