package com.example.jetsoncontroller.ui.components.geo

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.example.jetsoncontroller.ui.theme.LocalGeoPalette

/**
 * A full-screen Compose dialog (decorFitsSystemWindows = false) still gets a window that fits between
 * the system bars, yet its content is laid out for the whole display. On the S22 with 3-button
 * navigation the window stopped at the bar while the content ran 168 px below it, and the insets the
 * content pads itself with read zero. Called first inside such a dialog, this lets the window cover
 * the display, so the insets arrive and the bottom of the content stays above the bar.
 */
@Composable
internal fun GeoDialogCoversDisplay() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val dark = LocalGeoPalette.current.isDark
    SideEffect {
        val attributes = window?.attributes ?: return@SideEffect
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.Transparent.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        val cutout = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        if (attributes.fitInsetsTypes != 0 || attributes.layoutInDisplayCutoutMode != cutout) {
            attributes.fitInsetsTypes = 0
            attributes.layoutInDisplayCutoutMode = cutout
            window.attributes = attributes
        }
    }
}
