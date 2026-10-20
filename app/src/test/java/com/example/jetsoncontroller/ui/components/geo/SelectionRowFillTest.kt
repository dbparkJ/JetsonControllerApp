package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.example.jetsoncontroller.ui.theme.GeoPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionRowFillTest {
    @Test
    fun `selected choices use the palette selection fill in sheets`() {
        GeoPalette.entries.forEach { palette ->
            listOf(Role.RadioButton, Role.Checkbox).forEach { role ->
                assertEquals(palette.name, palette.colors.primaryTint,
                    selectionRowFill(palette.colors, role, true, true, true, false))
            }
        }
    }

    @Test
    fun `unselected choices and ordinary controls keep the surface visible`() {
        GeoPalette.entries.forEach { palette ->
            assertEquals(Color.Transparent,
                selectionRowFill(palette.colors, Role.RadioButton, true, false, true, false))
            assertEquals(Color.Transparent,
                selectionRowFill(palette.colors, Role.RadioButton, false, true, true, false))
            assertEquals(Color.Transparent,
                selectionRowFill(palette.colors, Role.Checkbox, false, true, true, false))
            assertEquals(Color.Transparent,
                selectionRowFill(palette.colors, Role.Switch, true, true, true, false))
        }
    }

    @Test
    fun `press feedback takes priority over selection without changing the selection`() {
        GeoPalette.entries.forEach { palette ->
            assertEquals(palette.colors.fieldPressed,
                selectionRowFill(palette.colors, Role.RadioButton, true, true, true, true))
            assertEquals(palette.colors.fieldPressed,
                selectionRowFill(palette.colors, Role.Checkbox, true, false, true, true))
            assertEquals(palette.colors.primaryTint,
                selectionRowFill(palette.colors, Role.RadioButton, true, true, true, false))
        }
    }

    @Test
    fun `disabled choices do not show active selection or press feedback`() {
        GeoPalette.entries.forEach { palette ->
            listOf(Role.RadioButton, Role.Checkbox).forEach { role ->
                assertEquals(Color.Transparent,
                    selectionRowFill(palette.colors, role, true, true, false, false))
                assertEquals(Color.Transparent,
                    selectionRowFill(palette.colors, role, true, true, false, true))
            }
        }
    }
}
