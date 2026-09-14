package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors

@Composable
fun GeoLogo(modifier: Modifier = Modifier, backgroundColor: Color = MaterialTheme.colorScheme.background) {
    val logo = ImageBitmap.imageResource(R.drawable.geo_logo)
    val wordmarkFilter = if (backgroundColor.luminance() < 0.3f)
        ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
    // The original PNG is transparent. Render its symbol and wordmark without the
    // embedded microprint; the screen's native title supplies a readable subtitle.
    Canvas(modifier.aspectRatio(1000f / 338f).semantics { contentDescription = "GEO& 로고" }) {
        fun part(x: Int, y: Int, width: Int, height: Int) {
            drawImage(logo, srcOffset = IntOffset(x, y), srcSize = IntSize(width, height),
                dstOffset = IntOffset((x * size.width / 1000).toInt(), (y * size.height / 338).toInt()),
                dstSize = IntSize((width * size.width / 1000).toInt(), (height * size.height / 338).toInt()),
                colorFilter = if (x >= 310) wordmarkFilter else null)
        }
        part(0, 0, 290, 338)
        part(310, 0, 690, 260)
    }
}

@Composable
fun GeoDeviceSummary(state: DashboardUiState, onClick: () -> Unit, showMetrics: Boolean = true) {
    val c = LocalCobaltColors.current
    val connection = com.example.jetsoncontroller.ui.connection.userConnectionStage(
        state.isOnline,
        state.transportType
    )
    Surface(onClick = onClick, color = c.sectionSoft, contentColor = c.ink,
        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.geo_device), "GEO& 도로관리장치", Modifier.size(72.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.deviceName, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    StatusBadge(
                        connection.label,
                        if (state.fullControlAvailable) StatusTone.SUCCESS
                        else if (state.isOnline) StatusTone.WARNING else StatusTone.INFO
                    )
                }
            }
            Text("GEO& · 도로관리장치", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Text(
                if (state.isOnline) state.endpoint ?: connection.detail else connection.detail,
                style = MaterialTheme.typography.bodyMedium
            )
            if (showMetrics) {
            HorizontalDivider(color = c.sectionBorder)
            val fresh = state.isOnline && state.statusFreshness == StatusFreshness.CURRENT
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("CPU" to (if (fresh && state.status.metricIsValid("cpuPercent")) "${state.status.cpuPercent}%" else "—"),
                    "GPU" to (if (fresh && state.status.metricIsValid("gpuPercent")) "${state.status.gpuPercent}%" else "—"),
                    "온도" to (if (fresh && state.status.metricIsValid("temperatureC")) "${state.status.temperatureC}°C" else "—")
                ).forEach { (label, value) -> Column {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Text(value, style = MaterialTheme.typography.titleLarge)
                } }
            }
            }
        }
    }
}

@Composable
fun GeoWelcomeScene() {
    val c = LocalCobaltColors.current
    Surface(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }, color = c.hero, contentColor = c.heroText) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            GeoLogo(Modifier.widthIn(max = 300.dp).fillMaxWidth(), backgroundColor = c.hero)
            Spacer(Modifier.height(12.dp))
            Text("도로관리장치 제어", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(40.dp))
            Image(painterResource(R.drawable.geo_device), "GEO& 도로관리장치", Modifier.size(240.dp))
            Spacer(Modifier.height(32.dp))
            Text("현장의 데이터를, 더 빠르고 더 안전하게", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(Modifier.size(24.dp), color = c.heroText, strokeWidth = 2.dp)
        }
    }
}
