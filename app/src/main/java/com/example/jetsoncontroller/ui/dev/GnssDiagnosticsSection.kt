package com.example.jetsoncontroller.ui.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.jetsoncontroller.model.GnssSensorStatus
import com.example.jetsoncontroller.ui.components.geo.GeoFactRow
import com.example.jetsoncontroller.ui.components.geo.GeoPropertyList
import kotlinx.coroutines.delay

@Composable
internal fun GnssDiagnosticsSection(gnss: GnssSensorStatus, fresh: Boolean) {
    val currentTime = System.currentTimeMillis()
    val now = if (gnssDiagnosticsNeedsTicker(gnss, currentTime)) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val tick by produceState(currentTime, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = System.currentTimeMillis()
                    delay(1_000)
                }
            }
        }
        tick
    } else currentTime
    val presentation = gnssDiagnosticsPresentation(gnss, now, fresh)
    if (presentation.rows.isEmpty() && presentation.error == null) return
    DevSection("기준국 진단") {
        if (presentation.lastKnown) DevText("마지막 값 기준", secondary = true)
        GeoPropertyList(presentation.rows.map { it.label }) {
            presentation.rows.forEach { GeoFactRow(it.label, it.value) }
        }
        DevFeedback(presentation.error, true)
    }
}
