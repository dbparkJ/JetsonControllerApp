package com.example.jetsoncontroller.ui.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.jetsoncontroller.ui.components.geo.GeoStateBlock
import com.example.jetsoncontroller.ui.components.geo.GeoStateContent
import com.example.jetsoncontroller.ui.components.geo.GeoStateKind

/** Inserted by the device-root owner when its discovery action lacks permission. */
@Composable
fun LocalNetworkPermissionBlock(deviceName: String, onPermissionClick: () -> Unit, modifier: Modifier = Modifier) {
    GeoStateBlock(
        GeoStateContent(
            GeoStateKind.PERMISSION,
            "$deviceName · 연결 권한이 필요합니다",
            "같은 네트워크의 장치를 찾으려면 로컬 네트워크 권한이 필요합니다.",
            "권한 허용"
        ), modifier, onAction = onPermissionClick
    )
}
