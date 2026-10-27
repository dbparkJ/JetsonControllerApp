package com.example.jetsoncontroller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.theme.*
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Offline visual inventory. Each method can be selected with am instrument -e class …#work. */
class R8ScreenGalleryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun work() = captureGroup("work", r8WorkScenes())
    @Test fun run() = captureGroup("run", r8RunScenes())
    @Test fun device() = captureGroup("device", r8DeviceScenes())
    @Test fun transfer() = captureGroup("transfer", r8TransferScenes())
    @Test fun settings() = captureGroup("settings", r8SettingsScenes())
    @Test fun overlay() = captureGroup("overlay", r8OverlayScenes())
    @Test fun startup() = captureGroup("startup", r8StartupScenes())

    private data class Window(val name: String, val width: Float, val height: Float, val font: Float)
    private data class Frame(val scene: R8GalleryScene, val window: Window, val palette: GeoPalette, val key: Int)

    private val windows = listOf(
        Window("padL", 1280f, 800f, 1f), Window("padL", 1280f, 800f, 1.3f),
        Window("padP", 800f, 1280f, 1f), Window("phone", 412f, 882f, 1f),
        Window("phone", 412f, 882f, 1.3f), Window("phone", 412f, 882f, 2f)
    )

    private fun captureGroup(group: String, scenes: List<R8GalleryScene>) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = InstrumentationRegistry.getArguments().getString("captureFolder") ?: "r8-gallery"
        // The runner argument is a relative folder, never a way to overwrite another app directory.
        require(folder.isNotBlank() && !File(folder).isAbsolute && folder.split('/', '\\').none { it == ".." })
        val external = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val destination = File(external, folder).canonicalFile
        require(destination.toPath().startsWith(external.canonicalFile.toPath()))
        val groupDir = File(destination, group).apply { check(mkdirs() || isDirectory) }
        val rows = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var active by mutableStateOf<Frame?>(null)
        var frameKey = 0
        // Keep Compose alive while scenes navigate through lazy content and open overlays.
        compose.mainClock.autoAdvance = true
        compose.setContent { active?.let { frame -> key(frame.key) { R8GalleryEnvironment { GalleryWindow(frame) } } } }

        fun publish() {
            File(groupDir, "index.tsv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            File(groupDir, "failures.txt").writeText(failures.joinToString("\n", postfix = if (failures.isEmpty()) "" else "\n"), Charsets.UTF_8)
            val groups = listOf("work", "run", "device", "transfer", "settings", "overlay", "startup")
            File(destination, "index.tsv").writeText(
                "파일 경로\tSTYLE-R8 §6 경로·함수\t상태 설명\t창\t글꼴\t팔레트\n" +
                    groups.map { File(destination, "$it/index.tsv") }.filter(File::isFile).joinToString("") { it.readText(Charsets.UTF_8) },
                Charsets.UTF_8)
            File(destination, "failures.txt").writeText(
                groups.map { File(destination, "$it/failures.txt") }.filter(File::isFile).joinToString("") { it.readText(Charsets.UTF_8) },
                Charsets.UTF_8)
        }

        r8GalleryOmissions(group).forEach { (source, reason) ->
            rows += tsv("-", source, "미촬영: $reason", "all", "all", "STANDARD")
        }
        publish()
        scenes.forEachIndexed { index, scene ->
            val variants = windows.map { it to GeoPalette.STANDARD } + if (scene.darkPalettes) listOf(
                windows.first() to GeoPalette.GRAPHITE, windows.first() to GeoPalette.NAVY_DARK
            ) else emptyList()
            variants.forEach variant@{ (window, palette) ->
                val font = String.format(Locale.US, "%.1f", window.font)
                val relative = "$group/${String.format(Locale.US, "%02d", index + 1)}-${scene.id}-${window.name}-$font-${palette.name}.png"
                if (scene.overlay && window.name != "padL") {
                    rows += tsv("-", scene.source, "미촬영 ${scene.id}: 축소 창의 별도 오버레이는 실제 창 좌표를 사용하므로 제외", window.name, font, palette.name)
                    return@variant
                }
                try {
                    compose.mainClock.autoAdvance = true
                    val deadlineNanos = System.nanoTime() + SCENE_TIMEOUT_NANOS
                    fun checkDeadline(stage: String) {
                        check(System.nanoTime() <= deadlineNanos) {
                            "장면 준비 제한 시간 초과(${SCENE_TIMEOUT_MILLIS}ms): ${scene.id} / $stage"
                        }
                    }
                    // Dispose every previous owner, including Dialog/ModalBottomSheet windows.
                    compose.runOnUiThread { active = null }
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    compose.runOnUiThread { active = Frame(scene, window, palette, ++frameKey) }
                    settle()
                    checkDeadline("화면 표시")
                    compose.mainClock.autoAdvance = true
                    scene.prepare?.invoke(compose)
                    checkDeadline("장면 준비")
                    compose.mainClock.autoAdvance = false
                    settle()
                    checkDeadline("촬영 직전")
                    if (scene.overlay) check(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) {
                        "별도 오버레이 창이 열리지 않았습니다: ${scene.id}"
                    }
                    val bitmap = if (scene.overlay) {
                        instrumentation.uiAutomation.takeScreenshot() ?: error("기기 전체 화면을 캡처하지 못했습니다")
                    } else {
                        compose.onNodeWithTag(VIEWPORT, useUnmergedTree = true).captureToImage().asAndroidBitmap()
                    }
                    try {
                        File(destination, relative).outputStream().use { output ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG 저장 실패" }
                        }
                    } finally { bitmap.recycle() }
                    rows += tsv(relative, scene.source, scene.description, window.name, font, palette.name)
                } catch (error: Throwable) {
                    if (error is VirtualMachineError || error is ThreadDeath) throw error
                    val firstLine = "${error.javaClass.simpleName}: ${error.message.orEmpty().lineSequence().firstOrNull().orEmpty()}"
                    failures += "$relative\t${firstLine.replace('\t', ' ')}"
                    rows += tsv("-", scene.source, "실패 $relative: $firstLine", window.name, font, palette.name)
                } finally {
                    compose.mainClock.autoAdvance = true
                    publish()
                }
            }
        }
        try {
            compose.runOnUiThread { active = null }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            failures += "$group/정리\t${error.javaClass.simpleName}: ${error.message.orEmpty().lineSequence().firstOrNull().orEmpty()}"
        } finally { publish() }
        if (failures.isNotEmpty()) fail("${failures.size}개 캡처 실패: ${File(destination, "failures.txt").absolutePath}")
    }

    private fun settle() {
        // Finish finite transitions; leave infinite loaders at an explicit, frozen clock time.
        compose.mainClock.advanceTimeBy(1_200L)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Composable
    private fun GalleryWindow(frame: Frame) {
        val spec = frame.window
        val scene = frame.scene
        val actualDensity = LocalView.current.resources.displayMetrics.density
        CompositionLocalProvider(LocalDensity provides Density(actualDensity, spec.font)) {
            JetsonControllerTheme(palette = frame.palette) {
                if (spec.name == "padL") {
                    Box(Modifier.fillMaxSize().testTag(VIEWPORT)) {
                        if (scene.source.startsWith("startup/") && !scene.panes) scene.content()
                        else GeoAppShell(selected = scene.tab, onSelect = {}, navigationVisible = scene.navigation,
                            paneLayout = scene.panes, mediaInsets = scene.media,
                            landscapeMedia = scene.camera, mapBackdrop = scene.activeRun,
                            tabletNavigationVisible = scene.camera || scene.activeRun) { scene.content() }
                    }
                } else {
                    // R7LandscapeLayoutTest.Viewport: fit logical dp inside the actual safe viewport.
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        val safeTop = WindowInsets.safeDrawing.getTop(LocalDensity.current)
                        val safeBottom = WindowInsets.safeDrawing.getBottom(LocalDensity.current)
                        val widthPx = constraints.maxWidth.toFloat()
                        val heightPx = (constraints.maxHeight - safeTop - safeBottom).coerceAtLeast(1).toFloat()
                        val density = minOf(actualDensity, widthPx / spec.width, heightPx / spec.height)
                        val showNavigation = scene.navigation || ((scene.camera || scene.activeRun) && spec.width >= 600f)
                        val window = geoWindow(spec.width, spec.height, spec.font, navigationVisible = showNavigation)
                        var bottom by remember { mutableFloatStateOf(if (showNavigation && !window.rail) 72f else 0f) }
                        CompositionLocalProvider(LocalDensity provides Density(density, spec.font),
                            LocalGeoWindow provides window,
                            LocalGeoChrome provides GeoChromeMeasurements(viewportDp = spec.height, bottomDp = bottom)) {
                            Column(Modifier.requiredSize(spec.width.dp, spec.height.dp).background(LocalGeoColors.current.canvas).testTag(VIEWPORT)) {
                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                    if (showNavigation && window.rail) GeoRail(scene.tab, {}, Modifier.fillMaxHeight())
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                                        Box((if (scene.panes || scene.media || scene.activeRun) Modifier else Modifier.widthIn(max = 720.dp)).fillMaxSize()) {
                                            scene.content()
                                        }
                                    }
                                }
                                if (showNavigation && !window.rail) GeoBottomBar(scene.tab, {}, Modifier.onSizeChanged { bottom = it.height / density })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun tsv(vararg fields: String) = fields.joinToString("\t") { it.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ') }

    private companion object {
        const val VIEWPORT = "r8-gallery-viewport"
        const val SCENE_TIMEOUT_MILLIS = 20_000L
        const val SCENE_TIMEOUT_NANOS = SCENE_TIMEOUT_MILLIS * 1_000_000L
    }
}
