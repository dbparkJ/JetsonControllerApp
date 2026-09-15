package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.alerts.AlertIconButton
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors

/**
 * The persistent answer to "어느 장치를 조작하고 있나?".
 *
 * Every operational screen carries this, because the product allows several registered
 * devices and operates one at a time. Showing the device name as a tappable control
 * with an explicit connection badge means the operator never has to infer the target
 * from the content below, and never sees device A's cached numbers under device B's
 * name without a visible reason.
 *
 * Heights are intentionally unconstrained: the device name, title and connection label
 * must all survive a 200% system font without clipping.
 */
@Composable
fun DeviceContextHeader(
    title: String,
    deviceName: String,
    connectionLabel: String,
    onDevices: () -> Unit,
    unreadCount: Int = 0,
    onAlerts: () -> Unit = {},
    showLogo: Boolean = false,
    connectionTone: StatusTone = StatusTone.UNKNOWN,
    actions: @Composable () -> Unit = {}
) {
    val c = LocalGeoColors.current
    val expandedType = LocalDensity.current.fontScale > 1.3f
    Surface(color = c.canvas, contentColor = c.ink) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = GeoSpace.gutter, vertical = GeoSpace.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onDevices,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = c.ink,
                    modifier = Modifier.weight(1f).heightIn(min = GeoSize.minTouchTarget)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            deviceName,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "다른 장치 선택",
                            modifier = Modifier.size(GeoSize.iconSm)
                        )
                    }
                }
                if (!expandedType) StatusBadge(connectionLabel, connectionTone)
            }
            if (expandedType) {
                StatusBadge(connectionLabel, connectionTone)
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    actions()
                    AlertIconButton(unreadCount, onAlerts)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    if (showLogo) GeoLogo(Modifier.width(72.dp))
                    actions()
                    AlertIconButton(unreadCount, onAlerts)
                }
            }
        }
    }
}
