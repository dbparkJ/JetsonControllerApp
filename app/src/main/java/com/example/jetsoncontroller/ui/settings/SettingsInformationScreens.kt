package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import com.example.jetsoncontroller.ui.components.geo.GeoSection
import com.example.jetsoncontroller.ui.components.geo.GeoSnackbar
import com.example.jetsoncontroller.ui.components.geo.showGeoSnackbar
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.devices.DeviceTitleBar
import kotlinx.coroutines.launch

@Composable
internal fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    DeviceTitleBar(title, onBack)
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

    Column(Modifier.fillMaxSize().background(LocalGeoColors.current.surface)) {
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
    Column(Modifier.fillMaxSize().background(LocalGeoColors.current.surface)) {
        SettingsPageHeader("도움말", onBack)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp
            )
        ) {
            settingsHelpSections.forEachIndexed { index, section ->
                item {
                    GeoSection(section.title, first = index == 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            section.paragraphs.forEach { paragraph ->
                                Text(paragraph, color = LocalGeoColors.current.textSecondary,
                                    style = LocalGeoType.current.body)
                            }
                        }
                    }
                }
            }
        }
    }
}
