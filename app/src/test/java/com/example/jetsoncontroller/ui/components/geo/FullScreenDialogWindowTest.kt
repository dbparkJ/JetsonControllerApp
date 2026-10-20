package com.example.jetsoncontroller.ui.components.geo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A full-screen dialog that does not cover the display slides its bottom under a 3-button navigation bar. */
class FullScreenDialogWindowTest {
    @Test fun everyFullScreenDialogCoversTheDisplayFirst() {
        val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
            File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
        val fullScreen = Regex("""DialogProperties\([^)]*decorFitsSystemWindows = false\)""")
        val covered = Regex("""DialogProperties\([^)]*decorFitsSystemWindows = false\)\) \{\n\s*GeoDialogCoversDisplay\(\)""")
        val sources = ui.walkTopDown().filter { it.extension == "kt" }
            .associate { it.name to it.readText().replace("\r\n", "\n") }
            .filterValues { fullScreen.containsMatchIn(it) }
        assertTrue("the map window, the overlay dialog and the full-screen sheet", sources.keys.containsAll(
            setOf("GeoMapWindow.kt", "GeoDialogs.kt", "GeoSheets.kt")))
        sources.forEach { (name, source) ->
            assertEquals(name, fullScreen.findAll(source).count(), covered.findAll(source).count())
        }
    }
}
