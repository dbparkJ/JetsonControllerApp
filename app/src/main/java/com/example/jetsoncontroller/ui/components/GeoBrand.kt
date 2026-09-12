package com.example.jetsoncontroller.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.R
import com.example.jetsoncontroller.ui.dashboard.*
import com.example.jetsoncontroller.ui.theme.LocalCobaltColors

@Composable
fun GeoDeviceSummary(state: DashboardUiState, onClick: () -> Unit, showMetrics: Boolean = true) {
    val c = LocalCobaltColors.current
    Surface(onClick = onClick, color = c.sectionSoft, contentColor = c.ink,
        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.geo_device), "GEO& 도로관리장치", Modifier.size(72.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.deviceName, style = MaterialTheme.typography.titleMedium)
                    Text("GEO& · 도로관리장치", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Text(if (state.isOnline) state.endpoint ?: "온라인" else "연결 대기", style = MaterialTheme.typography.bodySmall)
                }
                StatusBadge(if (state.isOnline) "온라인" else "오프라인", if (state.isOnline) StatusTone.SUCCESS else StatusTone.INFO)
            }
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
            Text("GEO&", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
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
