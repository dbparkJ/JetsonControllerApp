package com.example.jetsoncontroller.ui.components.geo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A full-screen dialog that does not cover the display slides its bottom under a 3-button navigation bar. */
class FullScreenDialogWindowTest {
    @Test fun overlayUsesTheSheetPaletteScrimWithoutAdditionalPlatformDim() {
        val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
            File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
        val dialog = File(ui, "components/geo/GeoDialogs.kt").readText()
        val sheet = File(ui, "components/geo/GeoSheets.kt").readText()
        val window = File(ui, "components/geo/GeoDialogWindow.kt").readText()
        assertTrue(sheet.contains("scrimColor = t.scrim"))
        assertTrue(dialog.contains(".background(t.scrim)"))
        assertTrue(dialog.contains("GeoDialogCoversDisplay(useThemeScrim = true)"))
        assertTrue(window.contains("if (useThemeScrim) window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)"))
    }

    @Test fun everyFullScreenDialogCoversTheDisplayFirst() {
        val ui = listOf(File("src/main/java/com/example/jetsoncontroller/ui"),
            File("app/src/main/java/com/example/jetsoncontroller/ui")).first { it.isDirectory }
        val fullScreen = Regex("""DialogProperties\([^)]*decorFitsSystemWindows = false\)""")
        val covered = Regex("""DialogProperties\([^)]*decorFitsSystemWindows = false\)\) \{\n\s*GeoDialogCoversDisplay\([^)]*\)""")
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
