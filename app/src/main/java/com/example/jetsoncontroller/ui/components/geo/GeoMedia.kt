package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane
import com.example.jetsoncontroller.ui.theme.*

/** 48dp circular media control on the fixed mediaPlate role. */
@Composable
fun GeoMediaIconButton(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.size(48.dp).clip(GeoShape.circle)
        .geoClickable(enabled, shape = GeoShape.circle, background = GeoFixedColors.mediaPlate,
            interactionSource = source, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (pressed && enabled) LocalGeoColors.current.text else GeoFixedColors.mediaText)
    }
}

/** 80dp capture target; enabled state comes from the existing camera freshness policy. */
@Composable
fun GeoCaptureButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val ink = if (pressed && enabled) LocalGeoColors.current.text else GeoFixedColors.mediaText
    Box(modifier.size(80.dp).clip(GeoShape.circle)
        .border(4.dp, ink, GeoShape.circle)
        .geoClickable(enabled, shape = GeoShape.circle, background = GeoFixedColors.mediaPlate,
            interactionSource = source, onClick = onClick)
        .semantics { contentDescription = "캡처" }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(60.dp).background(
            if (enabled) ink else GeoFixedColors.mediaPending, GeoShape.circle))
    }
}

/** Full-bleed media with inset-safe overlays and phone-bottom/tablet-right capture placement. */
@Composable
fun GeoImmersiveMediaFrame(
    modifier: Modifier = Modifier,
    captureAction: (() -> Unit)? = null,
    captureEnabled: Boolean = true,
    controls: @Composable BoxScope.() -> Unit = {},
    media: @Composable BoxScope.() -> Unit
) {
    if (!LocalGeoPane.current) GeoSystemBars(navigationBarUsesSurface = false, media = true)
    val tablet = LocalGeoWindow.current.widthDp >= 600f
    Box(modifier.fillMaxSize().background(GeoFixedColors.mediaStage)) {
        media()
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), content = controls)
        captureAction?.let {
            GeoCaptureButton(it, Modifier.align(if (tablet) Alignment.CenterEnd else Alignment.BottomCenter)
                .safeDrawingPadding().padding(24.dp), captureEnabled)
        }
    }
}
