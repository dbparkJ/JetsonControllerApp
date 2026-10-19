package com.example.jetsoncontroller.ui.components.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFollowTest {
    @Test
    fun `empty log is at the end and has nothing to scroll`() {
        assertTrue(logAtEnd(0, null, null, 400))
        assertFalse(shouldFollowLogAppend(following = true, hasLines = false, userScrolling = false))
    }

    @Test
    fun `the last line must be fully visible to count as the end`() {
        assertFalse(logAtEnd(12, 10, 300, 400))
        assertFalse(logAtEnd(12, 11, 401, 400))
        assertFalse(logAtEnd(12, null, null, 400))
        assertTrue(logAtEnd(12, 11, 400, 400))
        assertTrue(logAtEnd(12, 11, 300, 400))
    }

    @Test
    fun `a user scrolling away pauses following through later appends`() {
        val following = logFollowAfterScroll(true, atEnd = false, userScrolling = true, programmatic = false)
        assertFalse(following)
        assertFalse(shouldFollowLogAppend(following, hasLines = true, userScrolling = false))
        assertFalse(logFollowAfterScroll(following, atEnd = false, userScrolling = false, programmatic = false))
    }

    @Test
    fun `append relayout alone cannot detach the user from the end`() {
        val following = logFollowAfterScroll(true, atEnd = false, userScrolling = false, programmatic = false)
        assertTrue(following)
        assertTrue(shouldFollowLogAppend(following, hasLines = true, userScrolling = false))
    }

    @Test
    fun `returning to the end resumes following`() {
        val following = logFollowAfterScroll(false, atEnd = true, userScrolling = true, programmatic = false)
        assertTrue(following)
        assertTrue(shouldFollowLogAppend(following, hasLines = true, userScrolling = false))
    }

    @Test
    fun `a final idle layout at the end also resumes following`() {
        assertTrue(logFollowAfterScroll(false, atEnd = true, userScrolling = false, programmatic = false))
    }

    @Test
    fun `programmatic movement cannot pause following`() {
        assertTrue(logFollowAfterScroll(true, atEnd = false, userScrolling = true, programmatic = true))
    }

    @Test
    fun `an append never interrupts a scroll gesture`() {
        assertFalse(shouldFollowLogAppend(following = true, hasLines = true, userScrolling = true))
    }
}
