package com.example.jetsoncontroller.ui.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakableFileNameTest {
    @Test
    fun `names wrap after separators and read the same without the break marks`() {
        val name = "2026-09-02-14-44-32_raw"
        val shown = breakableFileName(name)
        assertEquals(name, shown.replace("​", ""))
        assertEquals("2026-​09-​02-​14-​44-​32_​raw", shown)
        assertEquals("a.​jpg", breakableFileName("a.jpg"))
        // A trailing separator gets no mark after it.
        assertEquals("run-", breakableFileName("run-"))
        assertEquals("26_​camera_​record", breakableFileName("26_camera_record"))
    }
}
