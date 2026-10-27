package com.example.jetsoncontroller.ui.transfers

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.components.geo.*
import com.example.jetsoncontroller.ui.components.geo.shell.GeoLargeTitleScaffold
import com.example.jetsoncontroller.ui.components.geo.shell.LocalGeoPane
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Composable
internal fun TransferPage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    primaryAction: GeoButtonAction? = null,
    subtitle: String? = null,
    subtitleStatus: com.example.jetsoncontroller.ui.components.geo.shell.GeoStatusSpec? = null,
    note: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GeoLargeTitleScaffold(title, onBack = onBack, subtitle = subtitle,
        primaryAction = primaryAction, actions = actions, subtitleStatus = subtitleStatus, note = note) {
        Column(Modifier.fillMaxSize(), content = content)
    }
}

internal val transferGutter: androidx.compose.ui.unit.Dp
    @Composable get() = if (LocalGeoPane.current) 0.dp else 24.dp

private val EmptyTransferBadge: @Composable () -> Unit = {}

@Composable
internal fun TransferHeading(title: String, meta: String? = null, badge: @Composable () -> Unit = EmptyTransferBadge) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = LocalGeoType.current.titleSm, color = LocalGeoColors.current.text)
        badge()
        meta?.let { Text(it, style = LocalGeoType.current.caption, color = LocalGeoColors.current.textSecondary) }
    }
}
