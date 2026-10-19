package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoIconsTest {
    private val icons = GeoIcons.all(Color.Black, Color.White)

    @Test
    fun `every KIT name and spec alias builds`() {
        val expected = setOf(
            "tabWork", "tabWork-filled", "tabDevice", "tabDevice-filled",
            "tabTransfer", "tabTransfer-filled", "tabSettings", "tabSettings-filled",
            "back", "close", "more", "bell", "add", "zoomIn", "zoomOut", "search",
            "chevronRight", "chevronDown", "chevronUp", "expandPanel", "collapsePanel",
            "copy", "refresh", "send", "trash", "restore", "play", "pause", "stop",
            "torch", "torchOff", "camera", "qr", "myLocation", "fitZone", "layers",
            "eye", "eyeOff", "wifi", "bluetooth", "link", "power", "reboot", "fan",
            "terminal", "folder", "file", "image", "video", "log", "success", "warning",
            "danger", "info", "pending", "unknown", "spinner"
        )
        assertEquals(expected, icons.keys)
        icons.forEach { (name, vector) ->
            assertEquals(name, 24.dp, vector.defaultWidth)
            assertEquals(name, 24.dp, vector.defaultHeight)
            assertEquals(name, 24f, vector.viewportWidth, 0f)
            assertEquals(name, 24f, vector.viewportHeight, 0f)
            assertTrue(name, vector.root.size > 0)
            for (index in 0 until vector.root.size) {
                assertTrue(name, (vector.root[index] as VectorPath).pathData.isNotEmpty())
            }
        }
    }

    @Test
    fun `all strokes use the KIT width and round caps and joins`() {
        icons.forEach { (name, vector) ->
            for (index in 0 until vector.root.size) {
                val path = vector.root[index] as VectorPath
                if (path.stroke != null) {
                    assertEquals(name, 1.75f, path.strokeLineWidth, 0f)
                    assertEquals(name, StrokeCap.Round, path.strokeLineCap)
                    assertEquals(name, StrokeJoin.Round, path.strokeLineJoin)
                }
            }
        }
    }

    @Test
    fun `filled tabs knock out inner lines with the supplied navigation color`() {
        listOf("tabWork-filled", "tabDevice-filled", "tabTransfer-filled", "tabSettings-filled").forEach { name ->
            val vector = icons.getValue(name)
            assertEquals(SolidColor(Color.Black), (vector.root[0] as VectorPath).fill)
            assertTrue(name, (0 until vector.root.size).any {
                val path = vector.root[it] as VectorPath
                path.stroke == SolidColor(Color.White) || path.fill == SolidColor(Color.White)
            })
        }
        val dark = GeoIcons.tabSettingsFilled(Color.White, Color.Black)
        assertEquals(SolidColor(Color.Black), (dark.root[1] as VectorPath).fill)
    }

    @Test
    fun `KIT aliases share their source glyph`() {
        assertSame(GeoIcons.add, GeoIcons.zoomIn)
        assertSame(GeoIcons.chevronUp, GeoIcons.expandPanel)
        assertSame(GeoIcons.chevronDown, GeoIcons.collapsePanel)
    }

    @Test
    fun `dots are filled without strokes and spinner track is translucent`() {
        val more = GeoIcons.more
        assertEquals(3, more.root.size)
        for (index in 0 until more.root.size) {
            val path = more.root[index] as VectorPath
            assertEquals(SolidColor(Color.Black), path.fill)
            assertEquals(null, path.stroke)
        }
        assertEquals(0.25f, (GeoIcons.spinner.root[0] as VectorPath).strokeAlpha, 0f)
    }
}
