package com.example.jetsoncontroller.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawOutline

/** A selection of existing palette roles, never an additional palette. */
data class GeoWorkCardColors(
    val background: Color,
    val content: Color,
    val action: Color,
    val onAction: Color
)

fun geoWorkCardColors(palette: GeoPalette): GeoWorkCardColors = with(palette.colors) {
    if (palette.isDark) GeoWorkCardColors(sunken, text, primary, onPrimary)
    else GeoWorkCardColors(inverse, onInverse, inverseAction, inverse)
}

/** Draw exactly one y4 / blur16 / text6% shadow outside the surface. */
@Composable
fun Modifier.geoCardShadow(shape: Shape = GeoShape.card): Modifier {
    val shadow = GeoElevation.shadow(LocalGeoPalette.current)
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val paint = Paint().apply {
            color = shadow.color
            asFrameworkPaint().maskFilter = BlurMaskFilter(shadow.blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        onDrawBehind {
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(0f, shadow.offsetY.toPx())
                canvas.drawOutline(outline, paint)
                canvas.restore()
            }
        }
    }
}
