package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.*
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Formats
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
internal fun StoragePage(
    title: String,
    onBack: () -> Unit,
    media: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalGeoColors.current
    val chrome = LocalGeoChrome.current
    val density = LocalDensity.current
    var topDp by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().background(if (media) androidx.compose.ui.graphics.Color.Black else colors.surface)) {
        GeoTopBar(Modifier.onSizeChanged { topDp = it.height / density.density }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GeoIconButton(GeoIcons.back, "뒤로", onBack, chrome = true)
                Text(title, Modifier.weight(1f).padding(start = 12.dp).semantics { heading() },
                    style = LocalGeoType.current.title, color = colors.onTopBar)
                actions()
            }
        }
        CompositionLocalProvider(LocalGeoChrome provides chrome.copy(topDp = chrome.topDp + topDp)) {
            content()
        }
    }
}

internal val storageGutter: androidx.compose.ui.unit.Dp
    @Composable get() = LocalGeoWindow.current.gutterDp.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageRefresh(
    refreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    PullToRefreshBox(refreshing, { if (enabled) onRefresh() }, modifier.semantics {
        customActions = listOf(CustomAccessibilityAction("새로 고침") {
            if (enabled) onRefresh()
            enabled
        })
    }, content = content)
}

@Composable
internal fun StorageFreshness(updatedAt: Long?, onRefresh: () -> Unit, prefix: String? = null, server: Boolean = true) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(updatedAt) {
        now = System.currentTimeMillis()
        while (true) { delay(60_000); now = System.currentTimeMillis() }
    }
    val instant = updatedAt?.let(Instant::ofEpochMilli)
    val age = instant?.let { Duration.between(it, Instant.ofEpochMilli(now)).coerceAtLeast(Duration.ZERO) }
    if (prefix != null || age == null || age >= Duration.ofSeconds(60)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(if (prefix != null && server) "$prefix · 마지막 동기화 ${Formats.time(instant)}"
                else if (prefix != null) "$prefix · ${Formats.freshness(age, instant)}"
                else Formats.freshness(age, instant),
                style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary)
            GeoButton("다시 확인", onRefresh, style = GeoButtonStyle.Tertiary)
        }
    }
}
