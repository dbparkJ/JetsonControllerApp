package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.theme.GeoMotion
import com.example.jetsoncontroller.ui.theme.geoReducedMotion
import kotlin.math.roundToInt

internal data class LoaderFrame(val reveal: Float, val foregroundAlpha: Float, val clipTop: Float)

internal fun loaderFrame(size: Float, fraction: Float, reducedMotion: Boolean = false): LoaderFrame {
    if (reducedMotion) return LoaderFrame(1f, 1f, 0f)
    val t = fraction.coerceIn(0f, 1f)
    val reveal = (t / .7f).coerceAtMost(1f)
    return LoaderFrame(reveal, if (t <= .9f) 1f else ((1f - t) / .1f).coerceIn(0f, 1f), size * (1f - reveal))
}

/** Original pixels, decoded once per resource set; the static branch owns no transition. */
@Composable
fun GeonLoader(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    reducedMotion: Boolean = geoReducedMotion()
) {
    val resources = LocalContext.current.resources
    val symbol = remember(resources) { ImageBitmap.imageResource(resources, R.drawable.geo_symbol) }
    val fraction = if (reducedMotion) 1f else {
        val transition = rememberInfiniteTransition(label = "geon-loader")
        val progress by transition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(GeoMotion.loaderCycle, easing = LinearEasing)), label = "reveal")
        progress
    }
    Canvas(modifier.size(size)) {
        val scale = minOf(this.size.width / symbol.width, this.size.height / symbol.height)
        val width = (symbol.width * scale).roundToInt()
        val height = (symbol.height * scale).roundToInt()
        val offset = IntOffset(((this.size.width - width) / 2f).roundToInt(), ((this.size.height - height) / 2f).roundToInt())
        val destination = IntSize(width, height)
        if (reducedMotion) {
            drawImage(symbol, dstOffset = offset, dstSize = destination)
        } else {
            drawImage(symbol, dstOffset = offset, dstSize = destination, alpha = .16f)
            val frame = loaderFrame(height.toFloat(), fraction)
            clipRect(left = offset.x.toFloat(), top = offset.y + frame.clipTop,
                right = (offset.x + width).toFloat(), bottom = (offset.y + height).toFloat()) {
                drawImage(symbol, dstOffset = offset, dstSize = destination, alpha = frame.foregroundAlpha)
            }
        }
    }
}
