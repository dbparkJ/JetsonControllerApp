package com.example.jetsoncontroller.ui.components.geo.shell

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Owns the status inset; the existing screen supplies its unchanged title row below it. */
@Composable
fun GeoTopBar(modifier: Modifier = Modifier, mediaInsets: Boolean = false, content: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().background(if (mediaInsets) androidx.compose.ui.graphics.Color.Black else LocalGeoColors.current.topBar)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
    ) { content() }
}
