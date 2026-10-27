package com.example.jetsoncontroller.ui.onboarding

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane
import com.example.jetsoncontroller.ui.navigateSingle
import com.example.jetsoncontroller.ui.navigateToTab
import com.example.jetsoncontroller.ui.navigation.AppNavigationState
import com.example.jetsoncontroller.ui.navigation.Routes

@Composable
internal fun FirstDeviceOnboardingRoute(state: AppNavigationState) {
    FirstDeviceOnboardingScreen(
        onScanQr = {
            state.pairingViewModel.beginQrPairing()
            state.navController.navigateSingle(Routes.QR_SCANNER)
        },
        onBack = { state.navController.popBackStack() },
        onOpenSettings = { navigateToTab(state.navController, Routes.SETTINGS) },
        onViewServerFiles = { state.navController.navigateSingle(Routes.SERVER_STORAGE) }
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun FirstDeviceOnboardingScreen(
    onScanQr: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onViewServerFiles: (() -> Unit)? = null
) {
    GeoLargeTitleScaffold("장치 등록",
        primaryAction = GeoButtonAction("QR 코드 스캔", onScanQr),
        secondaryAction = onViewServerFiles?.let {
            GeoButtonAction("장치 없이 서버 파일 보기", it, GeoButtonStyle.Tertiary)
        },
        actions = { onOpenSettings?.let { GeoIconButton(GeoIcons.tabSettings, "설정", it) } }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (LocalGeoPane.current) 0.dp else 24.dp)
            .padding(bottom = 24.dp)) {
            Image(painterResource(R.drawable.geo_device), null, Modifier.size(120.dp), contentScale = ContentScale.Fit)
            GeoStateBlock(GeoStateContent(GeoStateKind.EMPTY, "GEO-X에 장치를 등록하세요",
                "장치 본체의 QR 코드를 스캔하면 GEO-X에서 장치를 관리할 수 있습니다."))
            GeoGroupedSection("등록에 필요한 권한") {
                PermissionRow(GeoIcons.camera, "카메라", "QR 코드를 읽습니다.")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    PermissionRow(GeoIcons.bluetooth, "위치",
                        "블루투스로 가까운 장치를 찾습니다. 위치 서비스도 켜세요.", divider = false)
                } else {
                    PermissionRow(GeoIcons.bluetooth, "블루투스", "가까운 장치를 찾아 인증합니다.", divider = false)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, explanation: String, divider: Boolean = true) {
    GeoRow(title, {}, meta = explanation, leading = { GeoIconTile(icon) }, navigable = false, divider = divider)
}
