package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.BuildConfig
import com.example.jetsoncontroller.ui.components.geo.GeoFactRow
import com.example.jetsoncontroller.ui.components.geo.GeoIconButton
import com.example.jetsoncontroller.ui.components.geo.GeoIcons
import com.example.jetsoncontroller.ui.components.geo.GeoSection
import com.example.jetsoncontroller.ui.components.geo.GeoSnackbar
import com.example.jetsoncontroller.ui.components.geo.showGeoSnackbar
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import kotlinx.coroutines.launch

@Composable
internal fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GeoIconButton(GeoIcons.back, "뒤로", onBack)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 20.dp),
            color = LocalGeoColors.current.text,
            style = LocalGeoType.current.title
        )
    }
}

@Composable
fun SettingsAboutScreen(
    onBack: () -> Unit,
    developerModeEnabled: Boolean,
    onDeveloperUnlocked: () -> Unit
) {
    var versionTaps by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("앱 정보", onBack)
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                GeoFactRow("앱 이름", "GEO-X")
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("developer-unlock")
                        .clickable(
                            enabled = !developerModeEnabled,
                            role = Role.Button
                        ) {
                            versionTaps = (versionTaps + 1).coerceAtMost(7)
                            val tapsRemaining = 7 - versionTaps
                            if (tapsRemaining in 1..3) {
                                scope.launch {
                                    snackbarHostState.showGeoSnackbar(
                                        "개발자 모드까지 " + tapsRemaining + "번 남았습니다."
                                    )
                                }
                            }
                            if (tapsRemaining == 0) onDeveloperUnlocked()
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("버전", Modifier.weight(1f), color = LocalGeoColors.current.textSecondary,
                        style = LocalGeoType.current.body)
                    Text(BuildConfig.VERSION_NAME, color = LocalGeoColors.current.text,
                    style = LocalGeoType.current.body)
                }
                GeoFactRow("글꼴", "Pretendard (SIL OFL)")
            }
            GeoSnackbar(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
fun SettingsHelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("도움말", onBack)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp
            )
        ) {
            item {
                GeoSection("수집", first = true) {
                    Text(
                        "장치를 연결하고 수집 구간과 작업을 고른 뒤 수집 시작을 누르세요. 시작 전 점검을 통과하면 수집을 시작합니다.",
                        color = LocalGeoColors.current.textSecondary,
                        style = LocalGeoType.current.body
                    )
                }
            }
            item {
                GeoSection("전송") {
                    Text(
                        "수집 후 파일에서 저장 결과와 서버 수신을 확인할 수 있습니다. 같은 네트워크에서 수집 결과를 서버로 보낼 수 있습니다.",
                        color = LocalGeoColors.current.textSecondary,
                        style = LocalGeoType.current.body
                    )
                }
            }
            item {
                GeoSection("연결 문제") {
                    Text(
                        "연결이 끊기면 다시 연결을 누르세요. 주변에서 찾을 수 없으면 연결 방법에서 장치 Wi-Fi 또는 직접 연결을 확인하세요. 인증 정보가 거절되면 QR 코드로 다시 등록하세요.",
                        color = LocalGeoColors.current.textSecondary,
                        style = LocalGeoType.current.body
                    )
                }
            }
        }
    }
}
