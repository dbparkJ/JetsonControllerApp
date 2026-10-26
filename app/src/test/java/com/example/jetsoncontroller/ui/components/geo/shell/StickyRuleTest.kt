package com.example.jetsoncontroller.ui.components.geo.shell

import org.junit.Assert.*
import org.junit.Test

class StickyRuleTest {
    @Test
    fun `measured chrome and action preserve at least 280 dp of body`() {
        assertTrue(stickyAction(882f, 92f, 112f, 0f, 108f))
        assertTrue(stickyAction(592f, 92f, 112f, 0f, 108f))
        assertFalse(stickyAction(591f, 92f, 112f, 0f, 108f))
        assertFalse(stickyAction(882f, 92f, 112f, 0f, 400f))
    }

    @Test
    fun `compact height is inline even if the arithmetic alone fits`() {
        assertFalse(stickyAction(412f, 24f, 0f, 0f, 52f))
        assertFalse(stickyAction(440f, 24f, 48f, 0f, 52f))
        assertTrue(stickyAction(480f, 24f, 48f, 0f, 52f))
        assertFalse(stickyAction(530f, 24f, 48f, 0f, 52f, compactHeight = true))
    }

    @Test
    fun `keyboard replaces bottom navigation and still applies the remaining viewport rule`() {
        assertTrue(stickyAction(882f, 92f, 0f, 300f, 108f))
        assertFalse(stickyAction(882f, 92f, 0f, 403f, 108f))
        assertTrue(stickyAction(412f, 92f, 48f, 0f, 108f, scene = true))
    }

    @Test
    fun `action area grows but never shrinks within a visible screen`() {
        var height = retainedActionHeight(0f, 108f)
        assertEquals(108f, height, 0f)
        height = retainedActionHeight(height, 84f)
        assertEquals(108f, height, 0f)
        height = retainedActionHeight(height, 156f)
        assertEquals(156f, retainedActionHeight(height, 108f), 0f)
        assertEquals(84f, retainedActionHeight(0f, 84f), 0f)
    }
    @Test
    fun `tablet actions move inline for IME even with a tall remaining viewport`() {
        assertFalse(stickyAction(1333f, 64f, 0f, 300f, 108f, inlineForIme = true))
        assertTrue(stickyAction(1333f, 64f, 48f, 0f, 108f, inlineForIme = true))
    }
}
