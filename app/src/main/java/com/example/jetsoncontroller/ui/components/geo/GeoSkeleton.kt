package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.theme.GeoShape
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

enum class GeoSkeletonKind { DeviceCard, Figures, Rows }

/** Blocks never animate. The caller supplies the actual loading words for this request. */
@Composable
fun GeoSkeleton(
    loadingText: String,
    modifier: Modifier = Modifier,
    kind: GeoSkeletonKind = GeoSkeletonKind.DeviceCard,
    rowCount: Int = 3
) {
    require(loadingText.isNotBlank())
    require(rowCount >= 0)
    Column(modifier.fillMaxWidth().geoGroupInset(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CompositionLocalProvider(LocalGeoGroupInset provides false) {
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GeonLoader(size = 40.dp)
                Text(loadingText, style = LocalGeoType.current.body, color = LocalGeoColors.current.textSecondary)
            }
            Column(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                when (kind) {
                    GeoSkeletonKind.DeviceCard -> Column(Modifier.fillMaxWidth()
                        .background(LocalGeoColors.current.surface, GeoShape.card).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonBlock(Modifier.size(72.dp, 64.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkeletonBlock(Modifier.fillMaxWidth(.7f).height(28.dp))
                                SkeletonBlock(Modifier.fillMaxWidth().height(20.dp), field = true)
                            }
                        }
                        FigureSkeleton()
                    }
                    GeoSkeletonKind.Figures -> FigureSkeleton()
                    GeoSkeletonKind.Rows -> repeat(rowCount) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            SkeletonBlock(Modifier.size(24.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SkeletonBlock(Modifier.fillMaxWidth(.7f).height(24.dp))
                                SkeletonBlock(Modifier.fillMaxWidth().height(20.dp), field = true)
                            }
                            SkeletonBlock(Modifier.width(72.dp).height(24.dp), field = true)
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FigureSkeleton() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = figureGridColumns(maxWidth.value, androidx.compose.ui.platform.LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until 4).toList().chunked(columns).forEach { cells ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cells.forEach { SkeletonBlock(Modifier.weight(1f).height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier, field: Boolean = false) {
    val c = LocalGeoColors.current
    Box(modifier.background(if (field) c.field else c.sunken, GeoShape.control))
}
