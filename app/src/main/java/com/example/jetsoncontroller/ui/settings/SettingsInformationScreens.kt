package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.BuildConfig
import com.example.jetsoncontroller.ui.components.GeoLogo
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.dev.DevSection
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import kotlinx.coroutines.launch

@Composable
fun SettingsAboutScreen(onBack: () -> Unit, developerModeEnabled: Boolean, onDeveloperUnlocked: () -> Unit) {
    var versionTaps by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        GeoLargeTitleScaffold("앱 정보", onBack = onBack) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
                start = LocalGeoWindow.current.gutterDp.dp, end = LocalGeoWindow.current.gutterDp.dp, bottom = 24.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
                        GeoLogo(Modifier.width(176.dp), backgroundColor = LocalGeoColors.current.canvas)
                    }
                    DevSection("앱", first = true) {
                        GeoPropertyList(listOf("앱 이름", "버전", "글꼴")) {
                            GeoFactRow("앱 이름", "GEO-X")
                            GeoPropertyRow("버전", BuildConfig.VERSION_NAME,
                                modifier = Modifier.testTag("developer-unlock").geoClickable(enabled = !developerModeEnabled) {
                                    versionTaps = (versionTaps + 1).coerceAtMost(7)
                                    val remaining = 7 - versionTaps
                                    if (remaining in 1..3) scope.launch {
                                        snackbar.showGeoSnackbar("개발자 모드까지 " + remaining + "번 남았습니다.")
                                    }
                                    if (remaining == 0) onDeveloperUnlocked()
                                }, valueContent = { GeoCopyableValue(BuildConfig.VERSION_NAME) })
                            GeoFactRow("글꼴", "Pretendard (SIL OFL)")
                        }
                    }
                }
            }
        }
        GeoSnackbar(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun SettingsHelpScreen(onBack: () -> Unit) {
    GeoLargeTitleScaffold("도움말", onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
            start = LocalGeoWindow.current.gutterDp.dp, end = LocalGeoWindow.current.gutterDp.dp, bottom = 24.dp)) {
            settingsHelpSections.forEachIndexed { index, section ->
                item {
                    DevSection(section.title, first = index == 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            section.paragraphs.forEach { paragraph ->
                                Text(paragraph, color = LocalGeoColors.current.textSecondary, style = LocalGeoType.current.body)
                            }
                        }
                    }
                }
            }
        }
    }
}
