package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.ui.alerts.AlertIconButton
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.TextButton

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
    Surface(color = c.canvas, contentColor = c.ink) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = GeoSpace.gutter, vertical = GeoSpace.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onDevices,
                        modifier = Modifier.weight(1f).heightIn(min = GeoSize.minTouchTarget),
                        contentPadding = PaddingValues(vertical = GeoSpace.xs, horizontal = 0.dp)
                    ) {
                        Text(
                            deviceName,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "다른 장치 선택",
                            modifier = Modifier.size(GeoSize.iconMd)
                        )
                    }
                    actions()
                    AlertIconButton(unreadCount, onAlerts)
                }
                StatusBadge(connectionLabel, connectionTone)
                Spacer(Modifier.height(GeoSpace.sm))
                if (showLogo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
                    ) {
                        GeoLogo(Modifier.width(104.dp))
                        Text(
                            title,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                } else {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(GeoSpace.sm))
            }
            GeoRowDivider()
        }
    }
}
