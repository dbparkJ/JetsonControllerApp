package com.example.jetsoncontroller.ui.transfers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoTopBar
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoWindow
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoChrome
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.tasks.WorkDeviceSwitcher

@Composable
internal fun TransferPage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalGeoColors.current
    val chrome = LocalGeoChrome.current
    val density = LocalDensity.current
    val detailRoot = com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoDetailRoot.current
    val back = onBack.takeUnless { detailRoot }
    var headerDp by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        GeoTopBar(Modifier.onSizeChanged { headerDp = it.height / density.density }) {
            if (header != null) header() else
            Row(Modifier.fillMaxWidth().heightIn(min = if (detailRoot || onBack == null) 64.dp else 56.dp)
                .padding(start = if (detailRoot) 0.dp else 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (back != null) GeoIconButton(GeoIcons.back, "뒤로", back, chrome = true)
                Text(title, Modifier.weight(1f).padding(start = if (detailRoot) 0.dp else if (onBack == null) 20.dp else 12.dp)
                    .semantics { heading() }, color = c.onTopBar,
                    style = if (onBack == null) LocalGeoType.current.heading else LocalGeoType.current.title)
                actions()
            }
        }
        CompositionLocalProvider(LocalGeoChrome provides chrome.copy(topDp = chrome.topDp + headerDp)) {
            content()
        }
    }
}

@Composable
internal fun TransferRootHeader(deviceName: String, onDevicePicker: () -> Unit, onAlerts: () -> Unit,
    connectionTone: Tone = Tone.UNKNOWN) {
    val c = LocalGeoColors.current
    val t = LocalGeoType.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = transferGutter, end = 4.dp)) {
        val needed = with(density) {
            (measurer.measure(AnnotatedString("전송"), t.heading).size.width +
                measurer.measure(AnnotatedString(deviceName), t.titleSm).size.width).toDp()
        } + 132.dp
        val switcher: @Composable () -> Unit = {
            WorkDeviceSwitcher(deviceName, connectionTone, onDevicePicker)
        }
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("전송", Modifier.weight(1f).semantics { heading() }, style = t.heading, color = c.onTopBar)
                if (needed <= this@BoxWithConstraints.maxWidth) Box(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth * 0.6f)) { switcher() }
                GeoIconButton(GeoIcons.bell, "알림", onAlerts, chrome = true)
            }
            if (needed > this@BoxWithConstraints.maxWidth) switcher()
        }
    }
}

internal val transferGutter: androidx.compose.ui.unit.Dp
    @Composable get() = LocalGeoWindow.current.gutterDp.dp

private val EmptyTransferBadge: @Composable () -> Unit = {}

@Composable
internal fun TransferHeading(title: String, meta: String? = null, badge: @Composable () -> Unit = EmptyTransferBadge) {
    val line = with(LocalDensity.current) { LocalGeoType.current.boardTitle.lineHeight.toDp() }
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = LocalGeoType.current.boardTitle,
                color = LocalGeoColors.current.text)
            if (badge !== EmptyTransferBadge) Box(Modifier.width(88.dp).heightIn(min = line), contentAlignment = Alignment.CenterEnd) { badge() }
        }
        meta?.let { Text(it, style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary) }
    }
}
