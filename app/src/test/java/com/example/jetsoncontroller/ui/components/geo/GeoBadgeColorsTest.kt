package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.ui.graphics.Color
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoFixedColors
import com.example.jetsoncontroller.ui.theme.GeoPalette
import org.junit.Assert.*
import org.junit.Test

class GeoBadgeColorsTest {
    @Test fun `inline status has no fill and keeps each tone in every palette`() {
        GeoPalette.entries.forEach { palette ->
            val c = palette.colors
            val expected = mapOf(Tone.SUCCESS to c.success, Tone.WARNING to c.warning, Tone.ERROR to c.danger,
                Tone.INFO to c.primary, Tone.PENDING to c.pending, Tone.UNKNOWN to c.neutral)
            expected.forEach { (tone, ink) ->
                assertEquals("$palette $tone", GeoBadgeColors(ink, Color.Transparent), geoBadgeColors(c, tone, GeoBadgeVariant.INLINE))
            }
        }
    }

    @Test fun `legacy soft statuses use transparent fill`() {
        GeoPalette.entries.forEach { palette ->
            val c = palette.colors
            val expected = mapOf(Tone.SUCCESS to c.successSoft, Tone.WARNING to c.warningSoft, Tone.ERROR to c.dangerSoft,
                Tone.INFO to c.primaryTint, Tone.PENDING to c.pendingSoft, Tone.UNKNOWN to c.neutralSoft)
            expected.forEach { (tone, fill) ->
                val soft = geoBadgeColors(c, tone, GeoBadgeVariant.SOFT)
                assertEquals(Color.Transparent, soft.fill)
                assertEquals(geoBadgeColors(c, tone, GeoBadgeVariant.INLINE).ink, soft.ink)
            }
        }
    }

    @Test fun `identifier tags always use primary ink on primary tint`() {
        GeoPalette.entries.forEach { palette ->
            val c = palette.colors
            Tone.entries.forEach { tone ->
                assertEquals(GeoBadgeColors(c.primary, c.primaryTint), geoBadgeColors(c, tone, GeoBadgeVariant.TAG))
            }
        }
    }

    @Test fun `map badges retain an opaque plate and dark badges keep fixed media tones`() {
        val expectedMedia = mapOf(Tone.SUCCESS to GeoFixedColors.mediaLive, Tone.WARNING to GeoFixedColors.mediaDelayed,
            Tone.ERROR to GeoFixedColors.mediaFailed, Tone.INFO to GeoFixedColors.mediaText,
            Tone.PENDING to GeoFixedColors.mediaPending, Tone.UNKNOWN to GeoFixedColors.mediaPending)
        GeoPalette.entries.forEach { palette ->
            Tone.entries.forEach { tone ->
                val onMap = geoBadgeColors(palette.colors, tone, GeoBadgeVariant.ON_MAP)
                assertEquals(palette.colors.mapPlate, onMap.fill)
                assertEquals(1f, onMap.fill.alpha, 0f)
                assertEquals(geoBadgeColors(palette.colors, tone, GeoBadgeVariant.INLINE).ink, onMap.ink)
                assertEquals(GeoBadgeColors(expectedMedia.getValue(tone), GeoFixedColors.mediaBadge),
                    geoBadgeColors(palette.colors, tone, GeoBadgeVariant.ON_DARK))
            }
        }
    }
}
