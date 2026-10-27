package com.example.jetsoncontroller.ui.components.geo.shell

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTab

data class GeoChromeMeasurements(
    val viewportDp: Float = 0f,
    val topDp: Float = 0f,
    val bottomDp: Float = 0f,
    val imeDp: Float = 0f
)

val LocalGeoChrome = staticCompositionLocalOf { GeoChromeMeasurements() }

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Horizontal safe drawing and IME each have one ancestor for all chrome and body. */
@Composable
fun GeoAppShell(
    selected: GeoTab,
    onSelect: (GeoTab) -> Unit,
    navigationVisible: Boolean = true,
    modifier: Modifier = Modifier,
    mediaInsets: Boolean = false,
    /** The current screen lays out its own list | detail panes (GeoPaneLayout); every other screen keeps the 720 dp column. */
    paneLayout: Boolean = false,
    attentionSections: Set<GeoTab> = emptySet(),
    focusedMediaPreview: Boolean = false,
    adaptivePreview: Boolean = false,
    tabletNavigationVisible: Boolean = false,
    landscapeMedia: Boolean = false,
    mapBackdrop: Boolean = false,
    topBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val inherited = LocalDensity.current
    val density = Density(view.resources.displayMetrics.density, inherited.fontScale)
    val window = view.context.activity()?.window
    SideEffect {
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            it.isNavigationBarContrastEnforced = false
        }
        // JetsonControllerTheme remains the single system-bar icon controller.
    }
    CompositionLocalProvider(LocalDensity provides density) {
        val colors = LocalGeoColors.current
        val imeDp = with(density) { WindowInsets.ime.getBottom(this).toDp().value }
        val bottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
        val topDp = with(density) { WindowInsets.safeDrawing.getTop(this).toDp().value }
        var topChrome by remember { mutableFloatStateOf(0f) }
        var bottomChrome by remember { mutableFloatStateOf(0f) }
        BoxWithConstraints(
            modifier.fillMaxSize().background(if (mediaInsets && !adaptivePreview) com.example.jetsoncontroller.ui.theme.GeoFixedColors.mediaStage else colors.canvas)
                .then(if (mediaInsets && !adaptivePreview) Modifier else Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
        ) {
            val safeHeight = (maxHeight.value - topDp - bottomDp).coerceAtLeast(0f)
            val navigationRequested = (navigationVisible || (tabletNavigationVisible && maxWidth.value >= 600f)) && imeDp == 0f
            val candidate = geoWindow(maxWidth.value, safeHeight, density.fontScale, navigationRequested)
            val previewInPane = adaptivePreview && candidate.twoPane
            val showNavigation = navigationRequested && (!focusedMediaPreview || previewInPane)
            val floatingNavigation = mediaInsets && landscapeMedia && maxWidth.value >= 600f && showNavigation
            val standardNavigation = showNavigation && !floatingNavigation
            val geoWindow = geoWindow(maxWidth.value, safeHeight, density.fontScale, standardNavigation).let {
                if (focusedMediaPreview && !previewInPane) it.copy(twoPane = false) else it
            }
            val mediaChrome = mediaInsets && !previewInPane
            val fullBleed = mediaChrome || (mapBackdrop && maxWidth.value < 600f)
            com.example.jetsoncontroller.ui.theme.GeoSystemBars(navigationBarUsesSurface = standardNavigation && !geoWindow.rail, media = mediaChrome)
            CompositionLocalProvider(
                LocalGeoWindow provides geoWindow,
                LocalGeoChrome provides GeoChromeMeasurements(maxHeight.value, if (fullBleed) 0f else topChrome,
                    if (fullBleed) 0f else if (standardNavigation && !geoWindow.rail) bottomChrome else if (imeDp == 0f) bottomDp else 0f, imeDp)
            ) {
                Column(Modifier.fillMaxSize().imePadding()) {
                    if (!fullBleed) GeoTopBar(Modifier.onSizeChanged { topChrome = it.height / density.density }, content = topBar)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (standardNavigation && geoWindow.rail) GeoRail(selected, onSelect,
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)), attentionSections)
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                val column = if (mapBackdrop || fullBleed || (geoWindow.twoPane && paneLayout) ||
                                    (landscapeMedia && geoWindow.landscapeLayout)) Modifier else Modifier.widthIn(max = 720.dp)
                                Box(column.fillMaxSize().then(if (fullBleed) Modifier else Modifier
                                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                    .consumeWindowInsets(WindowInsets.navigationBars))) { content() }
                            }
                            if ((!standardNavigation || geoWindow.rail) && imeDp == 0f && !fullBleed) {
                                // A rail protects neither the bottom nor the far-side system bar.
                                Spacer(Modifier.fillMaxWidth().background(colors.canvas)
                                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
                            }
                        }
                    }
                    if (standardNavigation && !geoWindow.rail) {
                        GeoBottomBar(selected, onSelect, Modifier.onSizeChanged { bottomChrome = it.height / density.density },
                            attentionSections = attentionSections)
                    }
                }
                if (floatingNavigation) GeoFloatingMediaNavigation(selected, onSelect,
                    Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(24.dp))
            }
        }
    }
}
