package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoPalette
import com.example.jetsoncontroller.ui.theme.geoWorkCardColors
import org.junit.Assert.*
import org.junit.Test

class R7ComponentRulesTest {
    @Test fun `first title line alignment uses measured pixels rather than the line height token`() {
        // The reported tablet failure: 36px cell, 29px trimmed single-line title.
        assertEquals(-3, firstTitleLineOffset(0f, 29f, 29, 36, titleCentered = false))
        assertEquals(0, firstTitleLineOffset(0f, 29f, 29, 60, titleCentered = true))
        // A centered multiline title needs a shift to its first line, including without meta.
        assertEquals(-18, firstTitleLineOffset(0f, 29f, 65, 60, titleCentered = true))
        // First-line metrics can have a nonzero top; taller actions retain their measured size.
        assertEquals(-22, firstTitleLineOffset(2f, 38f, 70, 84, titleCentered = false))
        listOf(1f, 1.5f, 2f).forEach { density ->
            val offset = firstTitleLineOffset(0f, 20f * density, (44 * density).toInt(),
                (40 * density).toInt(), titleCentered = false)
            assertEquals(-10f * density, offset.toFloat(), 0.5f)
        }
    }

    @Test fun `status lists share the longest width between 72 and 120 dp`() {
        assertEquals(72f, rowStatusWidth(emptyList()), 0f)
        assertEquals(72f, rowStatusWidth(listOf(30f, 65f)), 0f)
        assertEquals(94f, rowStatusWidth(listOf(60f, 94f, 75f)), 0f)
        assertEquals(120f, rowStatusWidth(listOf(94f, 145f)), 0f)
        assertFalse(rowStatusStacks(332f, 1.29f, 94f))
        assertTrue(rowStatusStacks(332f, 1.3f, 94f))
        assertTrue(rowStatusStacks(332f, 1.3f, 145f))
        assertTrue(rowStatusStacks(332f, 1.5f, 72f))
        assertTrue(rowStatusStacks(279f, 1f, 72f))
    }

    @Test fun `warning evidence retains its tone whether or not the stage is finished`() {
        GeoReceiptStep.entries.forEach { step ->
            listOf(false, true).forEach { done ->
                assertEquals(Tone.WARNING, receiptTone(step, GeoReceiptItem("비정상 종료", Tone.WARNING, done = done)))
                assertEquals(Tone.ERROR, receiptTone(step, GeoReceiptItem("실패", Tone.ERROR, done = done)))
            }
        }
        listOf("정상 종료", "중지됨").forEach { word ->
            assertEquals(Tone.SUCCESS, receiptTone(GeoReceiptStep.COLLECTION,
                GeoReceiptItem(word, Tone.SUCCESS, done = true)))
        }
    }

    @Test fun `work cards select existing roles in all six palettes`() {
        GeoPalette.entries.forEach { palette ->
            val c = palette.colors
            val selected = geoWorkCardColors(palette)
            assertEquals(if (palette.isDark) c.sunken else c.inverse, selected.background)
            assertEquals(if (palette.isDark) c.text else c.onInverse, selected.content)
            assertEquals(if (palette.isDark) c.primary else c.inverseAction, selected.action)
            assertEquals(if (palette.isDark) c.onPrimary else c.inverse, selected.onAction)
        }
    }

    @Test fun `receipt never treats success tone as confirmation`() {
        val unconfirmed = GeoReceiptItem("미확인", Tone.SUCCESS, done = false)
        assertEquals(Tone.PENDING, receiptTone(GeoReceiptStep.SERVER, unconfirmed))
        assertEquals(Tone.UNKNOWN, receiptTone(GeoReceiptStep.REPORT, GeoReceiptItem("보고 전")))
        assertEquals(Tone.SUCCESS, receiptTone(GeoReceiptStep.SERVER, unconfirmed.copy(done = true)))
        assertEquals(4, GeoReceiptState().rows().size)
    }

    @Test fun `inapplicable report keeps a neutral word and hollow dot even with stale completion`() {
        listOf(false, true).forEach { done ->
            val item = GeoReceiptItem("해당 없음", Tone.SUCCESS, done = done)
            assertEquals(Tone.UNKNOWN, receiptTone(GeoReceiptStep.REPORT, item))
            assertFalse(receiptDone(GeoReceiptStep.REPORT, item))
        }
    }

    @Test fun `tablet quick actions reflow from three to two then one column`() {
        assertEquals(3, quickActionColumns(1.29f))
        assertEquals(2, quickActionColumns(1.3f))
        assertEquals(2, quickActionColumns(1.5f))
        assertEquals(1, quickActionColumns(2f))
    }
}
