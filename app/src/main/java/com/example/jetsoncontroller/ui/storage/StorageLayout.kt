package com.example.jetsoncontroller.ui.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
    showHeader: Boolean = true,
    primaryAction: GeoButtonAction? = null,
    maxContentWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Infinity,
    content: @Composable ColumnScope.() -> Unit
) {
    if (media) {
        GeoImmersiveMediaFrame(controls = {
            GeoMediaIconButton(GeoIcons.back, "뒤로", onBack, Modifier.align(Alignment.TopStart))
        }) {
            Column(Modifier.fillMaxSize(), content = content)
        }
    } else if (showHeader) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            GeoLargeTitleScaffold(title, modifier = Modifier.widthIn(max = maxContentWidth).fillMaxSize(),
                onBack = onBack, actions = actions, primaryAction = primaryAction) {
                Column(Modifier.fillMaxSize(), content = content)
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), content = content)
    }
}

/** Footer consumes pane space, so the browser never scrolls behind the send controls. */
@Composable
internal fun StorageActionLayout(
    visible: Boolean,
    action: (@Composable () -> Unit)? = null,
    primaryAction: GeoButtonAction? = null,
    note: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val summaryLimit = maxHeight * 0.5f
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { body() }
        if (visible) {
            if (action != null) Column(Modifier.fillMaxWidth().heightIn(max = summaryLimit).geoContainScroll()
                .verticalScroll(rememberScrollState()).padding(horizontal = storageGutter, vertical = 12.dp)) {
                action()
            }
            primaryAction?.let { GeoFixedActionBar(it, note = note) }
        }
    }
    }
}

internal val storageGutter: androidx.compose.ui.unit.Dp
    @Composable get() = if (LocalGeoPane.current) 0.dp else 24.dp

/** Header actions are composed twice; the owning screen presents one action sheet. */
@Composable
internal fun StorageMenuSheet(
    title: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<GeoMenuItem>,
    openerFocusRequester: FocusRequester? = null
) {
    if (expanded) GeoSheet(title, onDismiss, kind = GeoSheetKind.CHOICE,
        openerFocusRequester = openerFocusRequester) {
        items.forEachIndexed { index, item ->
            GeoRow(item.label, { onDismiss(); item.onClick() }, enabled = item.enabled,
                disabledReason = item.disabledReason, divider = index < items.lastIndex,
                leading = { GeoIconTile(if (item.destructive) GeoIcons.trash else GeoIcons.more,
                    tone = if (item.destructive) com.example.jetsoncontroller.ui.model.Tone.ERROR else null) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageRefresh(
    refreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var userRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (!refreshing) userRefresh = false }
    val refresh = {
        if (enabled && !refreshing) {
            userRefresh = true
            onRefresh()
        }
    }
    PullToRefreshBox(refreshing && userRefresh, refresh, modifier.semantics {
        customActions = listOf(CustomAccessibilityAction("새로 고침") {
            refresh()
            enabled && !refreshing
        })
    }, content = content)
}

@Composable
internal fun StorageFreshness(updatedAt: Long?, onRefresh: () -> Unit, prefix: String? = null, server: Boolean = true,
    loading: Boolean = false, forceVisible: Boolean = false) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(updatedAt) {
        now = System.currentTimeMillis()
        while (true) { delay(60_000); now = System.currentTimeMillis() }
    }
    val instant = updatedAt?.let(Instant::ofEpochMilli)
    val age = instant?.let { Duration.between(it, Instant.ofEpochMilli(now)).coerceAtLeast(Duration.ZERO) }
    if (loading && instant == null) return
    if (forceVisible || prefix != null || age == null || age >= Duration.ofSeconds(60)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (prefix != null && server) "$prefix · 마지막 동기화 ${Formats.time(instant)}"
                else if (prefix != null) "$prefix · ${Formats.freshness(age, instant)}"
                else Formats.freshness(age, instant),
                modifier = Modifier.weight(1f), style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary)
            GeoIconButton(GeoIcons.refresh, "다시 확인", onRefresh,
                enabled = !loading)
        }
    }
}
