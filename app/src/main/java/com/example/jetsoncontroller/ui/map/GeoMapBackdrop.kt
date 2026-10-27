package com.example.jetsoncontroller.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.BuildConfig
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.theme.*

/** The run host keeps the same data adapter while choosing a full map instead of a preview. */
internal val LocalGeoMapBackground = staticCompositionLocalOf { false }

@Composable
internal fun GeoMapControl(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true) {
    val c = LocalGeoColors.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.size(48.dp).clip(GeoShape.circle)
        .geoClickable(enabled = enabled, shape = GeoShape.circle,
            background = if (selected) c.primaryTint else c.mapPlate,
            interactionSource = source, onClick = onClick)
        .semantics { contentDescription = label; this.selected = selected }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = when {
            !enabled -> c.disabledText
            pressed -> c.text
            selected -> c.primaryTintText
            else -> c.text
        })
    }
}

@Composable
internal fun GeoRunMapBackdrop(data: GeoMapData, onOpen: () -> Unit, modifier: Modifier = Modifier,
    mapClientId: String = BuildConfig.NAVER_MAP_CLIENT_ID) {
    val camera = remember { GeoMapCamera() }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(LocalGeoColors.current.sunken)) {
        GeoMapRenderer(data, Modifier.fillMaxSize(), followDevice = true, camera = camera, clientId = mapClientId,
            onLoading = { loading = it }, onBaseMapFailureChanged = { failed = it })
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GeoMapControl(GeoIcons.myLocation, "장치 위치로", camera::device,
                    selected = camera.following, enabled = data.device != null)
                GeoMapControl(GeoIcons.fitZone, "지도 크게 보기", onOpen)
                if (loading) GeoSkeleton("지도를 불러오는 중입니다",
                    Modifier.background(LocalGeoColors.current.mapPlate, GeoShape.card).padding(16.dp),
                    kind = GeoSkeletonKind.Rows, rowCount = 0)
                else if (failed || mapClientId.isBlank()) GeoInlineNote(
                    if (failed) "배경 지도를 불러오지 못했습니다. 구역은 그대로 표시합니다."
                    else "배경 지도를 쓸 수 없습니다. 구역은 그대로 표시합니다.")
            }
        }
    }
}
