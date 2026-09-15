package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.GeoSize
import com.example.jetsoncontroller.ui.theme.GeoSpace
import com.example.jetsoncontroller.ui.theme.GeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.theme.LocalGeoDarkTheme

/**
 * The GEO& corporate mark.
 *
 * The source PNG carries an English strapline under the wordmark that is unreadable
 * below about 200dp, so only the symbol and the wordmark are drawn; the screen's own
 * title supplies the description instead of a line of 6px type.
 */
@Composable
fun GeoLogo(modifier: Modifier = Modifier, backgroundColor: Color = MaterialTheme.colorScheme.background) {
    val logo = ImageBitmap.imageResource(R.drawable.geo_logo)
    val wordmarkFilter = if (LocalGeoDarkTheme.current && backgroundColor.luminance() < 0.3f)
        ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
    Canvas(modifier.aspectRatio(1000f / 338f).semantics { contentDescription = "GEO& 로고" }) {
        fun part(x: Int, y: Int, width: Int, height: Int) {
            drawImage(
                logo, srcOffset = IntOffset(x, y), srcSize = IntSize(width, height),
                dstOffset = IntOffset((x * size.width / 1000).toInt(), (y * size.height / 338).toInt()),
                dstSize = IntSize((width * size.width / 1000).toInt(), (height * size.height / 338).toInt()),
                colorFilter = if (x >= 310) wordmarkFilter else null
            )
        }
        part(0, 0, 290, 338)
        part(310, 0, 690, 260)
    }
}

/**
 * The selected device, as an identity card rather than a metrics panel.
 *
 * CPU, GPU and temperature used to sit at the bottom of this card at headline size,
 * which put developer telemetry at the visual centre of a card whose job is to say
 * *which device you are operating*. They are still available behind `showMetrics`, but
 * the connection state and the device's identity lead.
 */
@Composable
fun GeoDeviceSummary(state: DashboardUiState, onClick: () -> Unit, showMetrics: Boolean = true) {
    val c = LocalGeoColors.current
    val connection = com.example.jetsoncontroller.ui.connection.userConnectionStage(
        state.isOnline,
        state.transportType
    )
    Surface(
        onClick = onClick,
        color = c.surface,
        contentColor = c.ink,
        border = BorderStroke(GeoSize.hairline, c.border),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(GeoSpace.lg), verticalArrangement = Arrangement.spacedBy(GeoSpace.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GeoSpace.md)
            ) {
                Image(painterResource(R.drawable.geo_device), "GEO& 도로관리장치", Modifier.size(64.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.deviceName, style = MaterialTheme.typography.titleMedium)
                    Text("GEO& 도로관리장치", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    StatusBadge(connection.label, connection.tone)
                }
            }
            Text(
                if (state.isOnline) state.endpoint ?: connection.detail else connection.detail,
                style = MaterialTheme.typography.bodySmall,
                color = c.muted
            )
            if (showMetrics) {
                GeoRowDivider()
                val fresh = state.isOnline && state.statusFreshness == StatusFreshness.CURRENT
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(
                        "CPU" to (if (fresh && state.status.metricIsValid("cpuPercent")) "${state.status.cpuPercent}%" else null),
                        "GPU" to (if (fresh && state.status.metricIsValid("gpuPercent")) "${state.status.gpuPercent}%" else null),
                        "온도" to (if (fresh && state.status.metricIsValid("temperatureC")) "${state.status.temperatureC}°C" else null)
                    ).forEach { (label, value) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
                            Text(
                                value ?: "미확인",
                                style = GeoType.numeric,
                                color = if (value == null) c.unknown else c.ink
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The launch screen, shown while the app restores its device list and connection.
 *
 * It is a brand moment and a loading state at once, so it says what the product does in
 * one line and shows that work is in progress. It does not claim a connection or a
 * device state, because at this point the app has established neither.
 */
@Composable
fun GeoWelcomeScene() {
    val c = LocalGeoColors.current
    Surface(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) { awaitPointerEvent().changes.forEach { it.consume() } }
                }
            },
        color = c.hero,
        contentColor = c.heroText
    ) {
        Column(
            Modifier.fillMaxSize().padding(GeoSpace.xxxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (LocalGeoDarkTheme.current) {
                GeoLogo(Modifier.widthIn(max = 260.dp).fillMaxWidth(), backgroundColor = c.hero)
            } else {
                Surface(color = c.surface, shape = MaterialTheme.shapes.medium) {
                    GeoLogo(
                        Modifier.padding(horizontal = GeoSpace.lg, vertical = GeoSpace.sm)
                            .widthIn(max = 260.dp).fillMaxWidth(),
                        backgroundColor = c.surface
                    )
                }
            }
            Spacer(Modifier.height(GeoSpace.md))
            Text("도로관리장치 제어", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(GeoSpace.huge))
            Image(painterResource(R.drawable.geo_device), "GEO& 도로관리장치", Modifier.size(220.dp))
            Spacer(Modifier.height(GeoSpace.xxxl))
            Text(
                "현장의 데이터를, 더 빠르고 더 안전하게",
                style = MaterialTheme.typography.bodyMedium,
                color = c.heroMuted
            )
            Spacer(Modifier.height(GeoSpace.xl))
            CircularProgressIndicator(Modifier.size(24.dp), color = c.heroText, strokeWidth = 2.dp)
        }
    }
}
