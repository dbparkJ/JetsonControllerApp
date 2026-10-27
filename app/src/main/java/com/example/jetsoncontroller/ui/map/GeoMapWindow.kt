package com.example.jetsoncontroller.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.jetsoncontroller.ui.components.geo.GeoDialogCoversDisplay
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoPalette
import com.example.jetsoncontroller.ui.theme.systemBarAppearance

/** The focused map has its own window, so the activity's system-bar controller cannot paint it. */
@Composable
internal fun GeoMapWindow(onBack: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        GeoDialogCoversDisplay()
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val colors = LocalGeoColors.current
        val bars = systemBarAppearance(LocalGeoPalette.current, navigationBarUsesSurface = true)
        SideEffect {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.setDimAmount(0f)
                @Suppress("DEPRECATION")
                it.statusBarColor = bars.statusBar.toArgb()
                @Suppress("DEPRECATION")
                it.navigationBarColor = bars.navigationBar.toArgb()
                it.isStatusBarContrastEnforced = false
                it.isNavigationBarContrastEnforced = false
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = bars.darkStatusIcons
                    isAppearanceLightNavigationBars = bars.darkNavigationIcons
                }
            }
        }
        // The map draws beneath both system bars; its controls and sheet own safe insets.
        Box(Modifier.fillMaxSize().background(colors.canvas)) { content() }
    }
}
