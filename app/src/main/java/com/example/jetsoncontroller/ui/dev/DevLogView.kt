package com.example.jetsoncontroller.ui.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.model.Formats
import com.example.jetsoncontroller.ui.theme.*

/** The page supplies a bounded viewport; the shared log owns selection and follow state. */
@Composable
internal fun DevLogView(lines: List<GeoLogLine>, live: Boolean, sizeBytes: Long?, modifier: Modifier = Modifier) {
    Column(modifier.clip(GeoShape.card).background(LocalGeoColors.current.surface).padding(16.dp)) {
        Text(Formats.size(sizeBytes), color = LocalGeoColors.current.textSecondary,
            style = LocalGeoType.current.caption.copy(fontFeatureSettings = "tnum"))
        GeoLogView(lines, live, Modifier.weight(1f).fillMaxWidth().geoContainScroll())
    }
}
