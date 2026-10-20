package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

/** The readiness line above an action area's primary (KIT §3.12): 16 dp tone icon, 6 dp gap, `body` text. */
@Composable
fun GeoReadinessLine(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GeoSpace.captionIconGap),
        verticalAlignment = Alignment.Top) {
        GeoToneIcon(tone, Modifier.padding(top = GeoSpace.space2).size(16.dp))
        Text(text, color = LocalGeoColors.current.textSecondary, style = LocalGeoType.current.body)
    }
}
