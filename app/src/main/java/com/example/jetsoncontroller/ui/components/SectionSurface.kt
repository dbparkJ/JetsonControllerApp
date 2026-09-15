package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

/**
 * A short group within its existing lazy item; no extra click or state owner.
 *
 * The `color` parameter is retained for source compatibility with screens that have not
 * migrated to [GeoSection], but it is intentionally ignored. Callers used to pass a
 * different tinted background per section, which produced the patchwork of coloured
 * blocks the redesign is meant to remove: sections now separate by a hairline border on
 * a single surface, and colour is reserved for status.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SectionSurface(color: Color, content: @Composable () -> Unit) {
    val c = LocalGeoColors.current
    Surface(
        color = c.surface,
        contentColor = c.ink,
        border = BorderStroke(GeoSize.hairline, c.border),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.padding(GeoSpace.lg)) { content() }
    }
}
