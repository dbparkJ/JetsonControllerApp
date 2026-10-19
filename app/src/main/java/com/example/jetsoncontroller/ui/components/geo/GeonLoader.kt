package com.example.jetsoncontroller.ui.components.geo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.theme.GeoMotion
import com.example.jetsoncontroller.ui.theme.GeoPalette
import com.example.jetsoncontroller.ui.theme.LocalGeoPalette
import com.example.jetsoncontroller.ui.theme.geoReducedMotion
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val WaveEasing = CubicBezierEasing(0.45f, 0.05f, 0.35f, 1f)
private val ShineEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

internal data class LoaderFrame(
    val period: Float, val maskHeight: Float, val top: Float,
    val mainX: Float, val mainY: Float, val mainAlpha: Float,
    val secondX: Float, val secondY: Float, val secondAlpha: Float,
    val shineX: Float
)

internal fun loaderFrame(size: Float, fraction: Float): LoaderFrame {
    val t = fraction.coerceIn(0f, 1f)
    val p = (0.8f * size).roundToInt().toFloat()
    val h = (1.2f * size).roundToInt().toFloat()
    val top = -(0.14f * size).roundToInt().toFloat()
    fun eased(start: Float, end: Float, easing: Easing = WaveEasing): Float =
        easing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))
    val main = eased(0f, 0.72f)
    val second = eased(0f, 0.66f)
    return LoaderFrame(p, h, top,
        mainX = -3f * p * main, mainY = size + (top - size) * main,
        mainAlpha = 1f - eased(0.88f, 1f),
        secondX = p / 2f + 2f * p * second,
        secondY = 0.92f * size + (2f * top - 0.92f * size) * second,
        secondAlpha = 0.32f * (1f - eased(0.66f, 1f)),
        shineX = -2f * size * (1f - eased(0.74f, 0.96f, ShineEasing)))
}

/** The existing 340 px symbol, in one Canvas. No animation is created in reduced motion. */
@Composable
fun GeonLoader(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    reducedMotion: Boolean = geoReducedMotion()
) {
    val resources = LocalContext.current.resources
    val artwork = remember(resources) {
        LoaderArtwork(BitmapFactory.decodeResource(resources, R.drawable.geo_symbol,
            BitmapFactory.Options().apply { inScaled = false }))
    }
    val palette = LocalGeoPalette.current
    val fraction = if (reducedMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "geon-loader")
        val progress by transition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(GeoMotion.loaderCycle, easing = LinearEasing)), label = "fill")
        progress
    }
    val drawingSize = size.value
    Canvas(modifier.size(size)) {
        val canvas = drawContext.canvas.nativeCanvas
        val checkpoint = canvas.save()
        canvas.scale(this.size.width / drawingSize, this.size.height / drawingSize)
        artwork.draw(canvas, drawingSize, fraction, palette, reducedMotion)
        canvas.restoreToCount(checkpoint)
    }
}

private class LoaderArtwork(private val symbol: Bitmap) {
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val wave = Path()
    private val bounds = RectF()
    private val base = GeoPalette.entries.associateWith { palette ->
        val brightness = if (!palette.isDark) 1.74f else if (palette == GeoPalette.MIST) 0.96f else 0.9f
        // CSS contrast(0) makes every channel 0.5 before brightness is applied.
        val grey = 127.5f * brightness
        ColorMatrixColorFilter(floatArrayOf(
            0f, 0f, 0f, 0f, grey, 0f, 0f, 0f, 0f, grey,
            0f, 0f, 0f, 0f, grey, 0f, 0f, 0f, 1f, 0f))
    }
    private val shine = ColorMatrixColorFilter(AndroidColorMatrix().apply {
        setSaturation(0.6f)
        postConcat(AndroidColorMatrix(floatArrayOf(
            1.9f, 0f, 0f, 0f, 0f, 0f, 1.9f, 0f, 0f, 0f,
            0f, 0f, 1.9f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    })

    fun draw(canvas: AndroidCanvas, size: Float, fraction: Float, palette: GeoPalette, reduced: Boolean) {
        val scale = size / maxOf(symbol.width, symbol.height)
        val width = symbol.width * scale
        val height = symbol.height * scale
        bounds.set((size - width) / 2f, (size - height) / 2f, (size + width) / 2f, (size + height) / 2f)
        image.colorFilter = null
        image.alpha = 255
        if (reduced) {
            canvas.drawBitmap(symbol, null, bounds, image)
            return
        }
        image.colorFilter = base.getValue(palette)
        image.alpha = if (palette.isDark) (255f * 0.28f).roundToInt() else 255
        canvas.drawBitmap(symbol, null, bounds, image)
        image.colorFilter = null
        val frame = loaderFrame(size, fraction)
        drawWave(canvas, size, frame.period, frame.maskHeight, frame.secondX, frame.secondY, frame.secondAlpha)
        drawWave(canvas, size, frame.period, frame.maskHeight, frame.mainX, frame.mainY, frame.mainAlpha)

        val layer = canvas.saveLayer(0f, 0f, size, size, null)
        image.colorFilter = shine
        image.alpha = (255f * 0.7f).roundToInt()
        canvas.drawBitmap(symbol, null, bounds, image)
        val angle = Math.toRadians(62.0)
        val dx = sin(angle).toFloat()
        val dy = -cos(angle).toFloat()
        val length = 3f * size * dx - size * dy
        val cx = frame.shineX + 1.5f * size
        val cy = size / 2f
        mask.shader = LinearGradient(cx - dx * length / 2f, cy - dy * length / 2f,
            cx + dx * length / 2f, cy + dy * length / 2f,
            intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0.42f, 0.5f, 0.58f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size, size, mask)
        canvas.restoreToCount(layer)
    }

    private fun drawWave(canvas: AndroidCanvas, size: Float, p: Float, h: Float, x: Float, y: Float, alpha: Float) {
        if (alpha <= 0f || p <= 0f) return
        wave.reset()
        var left = (x % p) - p
        while (left < size) {
            wave.moveTo(left, y + h * 0.05f)
            wave.quadTo(left + p * 0.25f, y, left + p * 0.5f, y + h * 0.05f)
            wave.quadTo(left + p * 0.75f, y + h * 0.1f, left + p, y + h * 0.05f)
            wave.lineTo(left + p, y + h)
            wave.lineTo(left, y + h)
            wave.close()
            left += p
        }
        val checkpoint = canvas.save()
        canvas.clipPath(wave)
        image.alpha = (255f * alpha).roundToInt()
        canvas.drawBitmap(symbol, null, bounds, image)
        canvas.restoreToCount(checkpoint)
    }
}
