package com.example.jetsoncontroller.ui

import com.example.jetsoncontroller.ui.navigation.Routes
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** JVM guard for the shared kit boundary; no custom Android lint module is required. */
class SourceImportLintTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private fun sources() = listOf("main", "debug", "androidTest").flatMap { set ->
        File(app, "src/$set/java/com/example/jetsoncontroller/ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.toList()
    }
    /** Sources compare as LF text whatever line endings the checkout uses (core.autocrlf on Windows). */
    private fun File.lfText() = readText().replace("\r\n", "\n")
    private val controls = "Button|OutlinedButton|TextButton|FilledTonalButton|TopAppBar|Card|FilterChip|AlertDialog|ListItem"
    private val rawImport = Regex("(?m)^\\s*import\\s+androidx\\.compose\\.material(?:3)?\\.(?:$controls|\\*)(?:\\s|$|\\.)")
    private val rawCall = Regex("androidx\\.compose\\.material(?:3)?\\.(?:$controls)\\s*\\(")

    @Test fun screensImportSharedControlsAndIconsOnly() {
        val files = sources()
        assertTrue("Source scan must include main, debug and instrumented fixtures", files.size > 100)
        val violations = files.flatMap { file ->
            val source = file.lfText()
            val kit = file.invariantSeparatorsPath.contains("/ui/components/geo/")
            buildList {
                if (!kit && (rawImport.containsMatchIn(source) || rawCall.containsMatchIn(source))) add("${file.path}: raw Material control or wildcard import")
                if (Regex("(?m)^\\s*import\\s+androidx\\.compose\\.material\\.icons(?:\\.|$)").containsMatchIn(source)) add("${file.path}: Material icon")
            }
        }
        assertEquals(violations.joinToString("\n"), emptyList<String>(), violations)
        assertFalse(File(app, "build.gradle.kts").lfText().contains("material.icons.extended"))
    }

    @Test fun importGuardCatchesAliasesWildcardsAndQualifiedCalls() {
        listOf("Button", "OutlinedButton", "TextButton", "FilledTonalButton", "TopAppBar", "Card", "FilterChip", "AlertDialog", "ListItem").forEach {
            assertTrue(it, rawImport.containsMatchIn("import androidx.compose.material3.$it as RawControl"))
            assertTrue(it, rawCall.containsMatchIn("androidx.compose.material3.$it("))
        }
        assertTrue(rawImport.containsMatchIn("import androidx.compose.material3.*"))
        assertTrue(rawImport.containsMatchIn("import androidx.compose.material.Button"))
        assertFalse(rawImport.containsMatchIn("import androidx.compose.material3.CardDefaults"))
        assertFalse(rawImport.containsMatchIn("import androidx.compose.material3.Text"))
    }

    @Test fun retiredScreensWrappersAndTokenSeamsAreAbsent() {
        val ui = File(app, "src/main/java/com/example/jetsoncontroller/ui")
        val removed = listOf("connection/ConnectionHubScreen.kt", "devices/DeviceListScreen.kt", "dashboard/DashboardScreen.kt",
            "components/GeoComponents.kt", "components/OperationalComponents.kt", "components/StatusTone.kt",
            "components/SectionSurface.kt", "components/MetricCard.kt", "components/DeviceCard.kt", "components/DeviceContextHeader.kt",
            "components/ConnectionStatusCard.kt", "components/ConnectionRecoveryLayout.kt", "components/ControlNavigationBar.kt",
            "components/GeoFormat.kt", "components/OperationMessageHost.kt", "components/OperationalUi.kt", "tasks/TaskDetailScreen.kt")
        removed.forEach { assertFalse("Retired file returned: $it", File(ui, it).exists()) }
        val oldApi = Regex("\\b(?:ConnectionHubScreen|DeviceListScreen|StatusTone|ControlSection|ControlNavigationBar|LocalControlNavigationRailVisible|OverlayAdapter|OverlayTokens|ContentColors|GeoControlColors|ShellColors|OperationMessageHost|formatGeoDateTime|DataLocationTabs|GeoOverlayTone|GeoLight|GeoDark)\\b")
        sources().forEach { assertFalse(it.path, oldApi.containsMatchIn(it.lfText())) }
        ui.resolve("theme").walkTopDown().filter { it.extension == "kt" }.forEach {
            assertFalse("Deprecated theme alias: ${it.path}", it.lfText().contains("@Deprecated"))
        }
        val oldColor = Regex("\\bval\\s+(?:ink|inkStrong|muted|inkSubtle|controlBorder|accent|onAccent|brandSoft|onBrandSoft|hero\\w*|section(?:Base|Soft|Raised|Danger|Border)|unknown(?:Bg|Border)?|overlay)\\b")
        assertFalse("Retired colour roles", oldColor.containsMatchIn(ui.resolve("theme/Color.kt").lfText()))
        val routes = ui.resolve("navigation/Routes.kt").lfText()
        listOf("connection_hub", "devices_ble", "admin_tools", "dashboard").forEach { assertFalse(it, routes.contains("\"$it\"")) }
    }
}
