package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.example.jetsoncontroller.ui.model.Tone
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
    Box(modifier.size(48.dp).clip(GeoShape.circle).background(GeoFixedColors.mediaPlate, GeoShape.circle)
        .geoClickable(enabled, shape = GeoShape.circle, background = GeoFixedColors.mediaPlate,
            interactionSource = source, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (pressed && enabled) LocalGeoPalette.current.colors.text else GeoFixedColors.mediaText)
    }
}

/** 80dp capture target; enabled state comes from the existing camera freshness policy. */
@Composable
fun GeoCaptureButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val ink = if (pressed && enabled) LocalGeoPalette.current.colors.text else GeoFixedColors.mediaText
    Box(modifier.size(80.dp).clip(GeoShape.circle).background(GeoFixedColors.mediaPlate, GeoShape.circle)
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
    info: GeoMediaInfoSpec? = null,
    caption: (@Composable () -> Unit)? = null,
    state: GeoStateContent? = null,
    onRetry: (() -> Unit)? = null,
    retryEnabled: Boolean = true,
    loadingText: String? = null,
    media: @Composable BoxScope.() -> Unit
) {
    if (!LocalGeoPane.current) GeoSystemBars(navigationBarUsesSurface = false, media = true)
    val tablet = LocalGeoWindow.current.widthDp >= 600f
    val colors = LocalGeoColors.current
    CompositionLocalProvider(LocalGeoMediaStage provides true, LocalGeoGroupInset provides false,
        LocalGeoColors provides colors.copy(text = GeoFixedColors.mediaText,
            textSecondary = GeoFixedColors.mediaText, textSubtle = GeoFixedColors.mediaText)) {
        Box(modifier.fillMaxSize().background(GeoFixedColors.mediaStage)) {
            media()
            info?.let {
                Box(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 24.dp, start = 80.dp, end = 80.dp)) {
                    GeoMediaInfo(it)
                }
            }
            caption?.let {
                Box(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(24.dp)
                    .padding(bottom = if (tablet) 56.dp else 104.dp).widthIn(max = 360.dp)
                    .background(GeoFixedColors.mediaPlate, GeoShape.card).padding(12.dp)) { it() }
            }
            if (state != null || loadingText != null) {
                Column(Modifier.align(Alignment.Center).safeDrawingPadding().padding(24.dp).widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state != null) {
                        GeoStateBlock(state)
                        if (onRetry != null) {
                            CompositionLocalProvider(LocalGeoColors provides LocalGeoColors.current.copy(fieldPressed = GeoFixedColors.mediaBadge)) {
                            GeoButton(state.actionLabel ?: "다시 시도", onRetry,
                                style = GeoButtonStyle.Secondary, enabled = retryEnabled, contentColor = GeoFixedColors.mediaText,
                                    containerColor = GeoFixedColors.mediaPlate)
                            }
                        }
                    } else {
                        GeonLoader(media = true)
                        Text(loadingText.orEmpty(), style = LocalGeoType.current.supporting, color = GeoFixedColors.mediaText)
                    }
                }
            }
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), content = controls)
            captureAction?.let {
                GeoCaptureButton(it, Modifier.align(if (tablet) Alignment.CenterEnd else Alignment.BottomCenter)
                    .safeDrawingPadding().padding(24.dp), captureEnabled)
            }
        }
    }
}

data class GeoMediaInfoSpec(val status: String, val tone: Tone, val device: String,
    val resolution: String? = null, val fps: String? = null)

/** Content-width summary; long summaries occupy at most two lines. */
@Composable
fun GeoMediaInfo(info: GeoMediaInfoSpec, modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = 560.dp).background(GeoFixedColors.mediaBadge, GeoShape.pill)
        .padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).background(mediaTone(info.tone), GeoShape.circle))
        Text(listOfNotNull(info.status, info.device, info.resolution, info.fps).joinToString(" · "),
            style = LocalGeoType.current.supporting, color = GeoFixedColors.mediaText, maxLines = 2)
    }
}
