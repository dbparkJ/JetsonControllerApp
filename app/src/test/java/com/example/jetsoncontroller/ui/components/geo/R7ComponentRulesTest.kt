package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoPalette
import com.example.jetsoncontroller.ui.theme.geoWorkCardColors
import org.junit.Assert.*
import org.junit.Test

class R7ComponentRulesTest {
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

    @Test fun `quick actions reflow from five to two then one column`() {
        assertEquals(5, quickActionColumns(1.49f))
        assertEquals(2, quickActionColumns(1.5f))
        assertEquals(1, quickActionColumns(2f))
    }
}
