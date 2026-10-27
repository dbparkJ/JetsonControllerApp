package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Circular progress is confined to busy controls; Compose observes animator duration scale. */
@Composable
internal fun GeoBusyIndicator(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "geoBusy")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rotation"
    )
    Icon(GeoIcons.spinner, null, modifier.size(16.dp).rotate(angle), tint = color)
}

/** Null/non-finite progress is indeterminate; finite progress is clamped and never rounded up. */
@Composable
fun GeoProgress(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true
) {
    val c = LocalGeoColors.current
    val inverse = LocalGeoInverseSurface.current
    val track = if (inverse) c.text.copy(alpha = .20f) else c.field
    val fill = if (inverse) c.text else c.primary
    val fraction = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val percent = fraction?.let { (it * 100).toInt() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val barModifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
            .semantics {
                progressBarRangeInfo = fraction?.let { ProgressBarRangeInfo(it, 0f..1f) }
                    ?: ProgressBarRangeInfo.Indeterminate
            }
        if (fraction != null) {
            val animated by animateFloatAsState(
                fraction, tween(180, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)), label = "geoProgress"
            )
            Canvas(barModifier) {
                drawRoundRect(track, cornerRadius = CornerRadius(2.dp.toPx()))
                if (animated > 0f) {
                    drawRoundRect(fill, size = Size(size.width * animated, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
        } else {
            val transition = rememberInfiniteTransition(label = "geoProgress")
            val position by transition.animateFloat(
                -0.3f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "position"
            )
            Canvas(barModifier) {
                drawRoundRect(track, cornerRadius = CornerRadius(2.dp.toPx()))
                drawRoundRect(fill, topLeft = Offset(size.width * position, 0f),
                    size = Size(size.width * 0.3f, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
            }
        }
        if (percent != null && showPercentage) {
            Text("$percent%", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = c.textSecondary)
        }
        // Announce a milestone only once per ten percent, independently of visual interpolation.
        Spacer(Modifier.size(0.dp).semantics {
            if (percent != null) {
                liveRegion = LiveRegionMode.Polite
                stateDescription = "${if (percent == 100) 100 else percent / 10 * 10}%"
            }
        })
    }
}

/** Row-shaped, static placeholders, shown only after the first 300 ms of a load. */
@Composable
fun GeoSkeleton(
    loading: Boolean,
    modifier: Modifier = Modifier,
    rows: Int = 3,
    loadingText: String = "불러오는 중"
) {
    require(rows > 0)
    var visible by remember(loading) { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(300)
            visible = true
        }
    }
    if (!loading || !visible) return
    GeoSkeleton(loadingText, modifier, kind = GeoSkeletonKind.Rows, rowCount = rows)
}
