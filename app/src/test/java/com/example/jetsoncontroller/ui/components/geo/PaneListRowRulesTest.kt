package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneListRowRulesTest {
    @Test fun `single row rounds both ends of the card`() {
        assertEquals(PaneListRowCorners(true, true), paneListRowCorners(0, 1))
    }

    @Test fun `first row rounds only the top of the card`() {
        assertEquals(PaneListRowCorners(true, false), paneListRowCorners(0, 3))
    }

    @Test fun `last row rounds only the bottom of the card`() {
        assertEquals(PaneListRowCorners(false, true), paneListRowCorners(2, 3))
    }

    @Test fun `middle row stays square`() {
        assertEquals(PaneListRowCorners(false, false), paneListRowCorners(1, 3))
    }

    @Test fun `two row card has no square middle row`() {
        assertEquals(PaneListRowCorners(true, false), paneListRowCorners(0, 2))
        assertEquals(PaneListRowCorners(false, true), paneListRowCorners(1, 2))
    }

    @Test fun `selection hides both neighboring dividers for every row position`() {
        (1..4).forEach { count ->
            (0 until count).forEach { selectedIndex ->
                (0 until count).forEach { index ->
                    val expected = index < count - 1 && index != selectedIndex && index + 1 != selectedIndex
                    assertEquals("count=$count selected=$selectedIndex boundary=$index", expected,
                        paneListRowDivider(index, count, index == selectedIndex, index + 1 == selectedIndex))
                }
            }
        }
    }

    @Test fun `unselected rows keep their inner divider but never a final divider`() {
        assertTrue(paneListRowDivider(0, 2, selected = false, nextSelected = false))
        assertFalse(paneListRowDivider(1, 2, selected = false, nextSelected = false))
        assertFalse(paneListRowDivider(0, 1, selected = false, nextSelected = false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty card has no row corners`() {
        paneListRowCorners(0, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `row outside the card is rejected`() {
        paneListRowCorners(3, 3)
    }
}
