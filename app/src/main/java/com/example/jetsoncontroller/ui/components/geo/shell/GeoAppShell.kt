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
            modifier.fillMaxSize().background(if (mediaInsets) androidx.compose.ui.graphics.Color.Black else colors.canvas)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            val showNavigation = navigationVisible && imeDp == 0f
            val geoWindow = geoWindow(maxWidth.value,
                (maxHeight.value - topDp - bottomDp).coerceAtLeast(0f), density.fontScale, showNavigation)
            com.example.jetsoncontroller.ui.theme.GeoSystemBars(navigationBarUsesSurface = !showNavigation || geoWindow.rail, media = mediaInsets)
            CompositionLocalProvider(
                LocalGeoWindow provides geoWindow,
                LocalGeoChrome provides GeoChromeMeasurements(maxHeight.value, topChrome,
                    if (showNavigation && !geoWindow.rail) bottomChrome else if (imeDp == 0f) bottomDp else 0f, imeDp)
            ) {
                Column(Modifier.fillMaxSize().imePadding()) {
                    GeoTopBar(Modifier.onSizeChanged { topChrome = it.height / density.density }, mediaInsets = mediaInsets, content = topBar)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (showNavigation && geoWindow.rail) GeoRail(selected, onSelect,
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                val column = if (geoWindow.twoPane && paneLayout) Modifier else Modifier.widthIn(max = 720.dp)
                                Box(column.fillMaxSize()
                                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                    .consumeWindowInsets(WindowInsets.navigationBars)) { content() }
                            }
                            if ((!showNavigation || geoWindow.rail) && imeDp == 0f) {
                                // A rail protects neither the bottom nor the far-side system bar.
                                Spacer(Modifier.fillMaxWidth().background(if (mediaInsets) androidx.compose.ui.graphics.Color.Black else colors.surface)
                                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
                            }
                        }
                    }
                    if (showNavigation && !geoWindow.rail) {
                        GeoBottomBar(selected, onSelect, Modifier.onSizeChanged { bottomChrome = it.height / density.density })
                    }
                }
            }
        }
    }
}
