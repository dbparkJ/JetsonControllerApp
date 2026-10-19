package com.example.jetsoncontroller.ui.components.geo.shell

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
import com.example.jetsoncontroller.ui.components.ControlSection
import com.example.jetsoncontroller.ui.components.LocalControlNavigationRailVisible

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
    selected: ControlSection,
    onSelect: (ControlSection) -> Unit,
    navigationVisible: Boolean = true,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val inherited = LocalDensity.current
    // The legacy theme scales tablet density; shell breakpoints and descendants use real dp.
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
        val colors = shellColors()
        val imeDp = with(density) { WindowInsets.ime.getBottom(this).toDp().value }
        val bottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
        val topDp = with(density) { WindowInsets.safeDrawing.getTop(this).toDp().value }
        var topChrome by remember { mutableFloatStateOf(0f) }
        var bottomChrome by remember { mutableFloatStateOf(0f) }
        BoxWithConstraints(
            modifier.fillMaxSize().background(colors.canvas)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            val showNavigation = navigationVisible && imeDp == 0f
            val geoWindow = geoWindow(maxWidth.value,
                (maxHeight.value - topDp - bottomDp).coerceAtLeast(0f), density.fontScale, showNavigation)
            CompositionLocalProvider(
                LocalGeoWindow provides geoWindow,
                LocalGeoChrome provides GeoChromeMeasurements(maxHeight.value, topChrome,
                    if (showNavigation && !geoWindow.rail) bottomChrome else if (imeDp == 0f) bottomDp else 0f, imeDp),
                // Every old screen's bar becomes a no-op, including focused flows.
                LocalControlNavigationRailVisible provides true
            ) {
                Column(Modifier.fillMaxSize().imePadding()) {
                    GeoTopBar(Modifier.onSizeChanged { topChrome = it.height / density.density }, topBar)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (showNavigation && geoWindow.rail) GeoRail(selected, onSelect,
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                val column = if (geoWindow.twoPane) Modifier else Modifier.widthIn(max = 720.dp)
                                Box(column.fillMaxSize()
                                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                    .consumeWindowInsets(WindowInsets.navigationBars)) { content() }
                            }
                            if ((!showNavigation || geoWindow.rail) && imeDp == 0f) {
                                // A rail protects neither the bottom nor the far-side system bar.
                                Spacer(Modifier.fillMaxWidth().background(colors.surface)
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
