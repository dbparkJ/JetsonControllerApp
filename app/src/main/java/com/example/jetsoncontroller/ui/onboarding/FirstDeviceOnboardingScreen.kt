package com.example.jetsoncontroller.ui.onboarding

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoActionArea
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.navigateSingle
import com.example.jetsoncontroller.ui.navigateToTab
import com.example.jetsoncontroller.ui.navigation.AppNavigationState
import com.example.jetsoncontroller.ui.navigation.Routes
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.tasks.WorkSurfaceCard

// The graph owner can adopt the complete first-run actions without changing the legacy signature.
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

@Composable
fun FirstDeviceOnboardingScreen(
    onScanQr: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onViewServerFiles: (() -> Unit)? = null
) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    val gutter = LocalGeoWindow.current.gutterDp.dp
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        // GeoAppShell owns system insets. A first-run scene has no back title bar.
        if (onOpenSettings != null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.End) {
                GeoIconButton(GeoIcons.tabSettings, "설정", onOpenSettings)
            }
        }
        GeoActionArea(
            visibilityKey = "first-device",
            scene = true,
            action = {
                // On a tablet the scene keeps the 720 dp content column; buttons cap themselves at 560 dp.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        GeoButton("QR 코드 스캔", onScanQr, Modifier.fillMaxWidth(), actionArea = true)
                        // The tertiary hugs its label and sits centred under the primary (board r3-pair-first-run).
                        if (onViewServerFiles != null) GeoButton(
                            "장치 없이 서버 파일 보기", onViewServerFiles, style = GeoButtonStyle.Tertiary
                        )
                    }
                }
            }
        ) { inlineAction ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter)) {
                Image(painterResource(R.drawable.geo_device), null, Modifier.size(120.dp))
                Spacer(Modifier.height(32.dp))
                Text("GEO-X에 장치를 등록하세요", Modifier.semantics { heading() }, style = type.heading, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text("장치 본체의 QR 코드를 스캔하면 GEO-X에서 장치를 관리할 수 있습니다.", style = type.body, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                GeoSection("등록에 필요한 권한", first = true, modifier = Modifier.padding(top = 24.dp)) {
                  WorkSurfaceCard {
                    PermissionRow(GeoIcons.camera, "카메라", "QR 코드를 읽습니다.")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        PermissionRow(GeoIcons.bluetooth, "위치", "블루투스로 가까운 장치를 찾습니다. 위치 서비스도 켜세요.", divider = false)
                    } else {
                        PermissionRow(GeoIcons.bluetooth, "블루투스", "가까운 장치를 찾아 인증합니다.", divider = false)
                    }
                  }
                }
                inlineAction()
            }
            }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, explanation: String, divider: Boolean = true) {
    val colors = LocalGeoColors.current
    val type = LocalGeoType.current
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp)
        .then(if (divider) Modifier.contentDivider() else Modifier).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        GeoIconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = type.titleSm, color = colors.text)
            Text(explanation, style = type.supporting, color = colors.textSecondary)
        }
    }
}
