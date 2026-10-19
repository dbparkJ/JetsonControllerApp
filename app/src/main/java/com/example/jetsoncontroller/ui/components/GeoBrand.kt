package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.GeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoDarkTheme

/**
 * The GEO& corporate mark.
 *
 * The source PNG carries an English strapline under the wordmark that is unreadable
 * below about 200dp, so only the symbol and the wordmark are drawn; the screen's own
 * title supplies the description instead of a line of 6px type.
 */
@Composable
fun GeoLogo(modifier: Modifier = Modifier, backgroundColor: Color = MaterialTheme.colorScheme.background) {
    val logo = ImageBitmap.imageResource(R.drawable.geo_logo)
    val wordmarkFilter = if (LocalGeoDarkTheme.current && backgroundColor.luminance() < 0.3f)
        ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
    Canvas(modifier.aspectRatio(1000f / 338f).semantics { contentDescription = "GEO& 로고" }) {
        fun part(x: Int, y: Int, width: Int, height: Int) {
            drawImage(
                logo, srcOffset = IntOffset(x, y), srcSize = IntSize(width, height),
                dstOffset = IntOffset((x * size.width / 1000).toInt(), (y * size.height / 338).toInt()),
                dstSize = IntSize((width * size.width / 1000).toInt(), (height * size.height / 338).toInt()),
                colorFilter = if (x >= 310) wordmarkFilter else null
            )
        }
        part(0, 0, 290, 338)
        part(310, 0, 690, 260)
    }
}
