package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoColors
import com.example.jetsoncontroller.ui.theme.GeoFixedColors
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

internal fun GeoColors.tone(tone: Tone): Color = when (tone) {
    Tone.SUCCESS -> success
    Tone.WARNING -> warning
    Tone.ERROR -> danger
    Tone.INFO -> info
    Tone.PENDING -> pending
    Tone.UNKNOWN -> neutral
}

internal fun GeoColors.soft(tone: Tone): Color = when (tone) {
    Tone.SUCCESS -> successSoft
    Tone.WARNING -> warningSoft
    Tone.ERROR -> dangerSoft
    Tone.INFO -> infoSoft
    Tone.PENDING -> pendingSoft
    Tone.UNKNOWN -> neutralSoft
}

internal fun mediaTone(tone: Tone): Color = when (tone) {
    Tone.SUCCESS -> GeoFixedColors.mediaLive
    Tone.WARNING -> GeoFixedColors.mediaDelayed
    Tone.ERROR -> GeoFixedColors.mediaFailed
    Tone.INFO -> GeoFixedColors.mediaText
    Tone.PENDING, Tone.UNKNOWN -> GeoFixedColors.mediaPending
}

internal fun toneIcon(tone: Tone): ImageVector = when (tone) {
    Tone.SUCCESS -> GeoIcons.success
    Tone.WARNING -> GeoIcons.warning
    Tone.ERROR -> GeoIcons.danger
    Tone.INFO -> GeoIcons.info
    Tone.PENDING -> GeoIcons.pending
    Tone.UNKNOWN -> GeoIcons.unknown
}

@Composable
internal fun GeoToneIcon(tone: Tone, modifier: Modifier = Modifier) {
    Icon(toneIcon(tone), null, modifier.size(20.dp), tint = LocalGeoColors.current.tone(tone))
}
