package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus
import java.text.DateFormat
import java.util.Date

internal fun JetsonStatus.metricIsValid(name: String): Boolean =
    metricValidity[name]?.validity?.let { it == "valid" } ?: true

internal fun JetsonStatus.metricDisplay(name: String, value: String): String {
    val metric = metricValidity[name] ?: return value
    return when (metric.validity) {
        "valid" -> value
        "stale" -> {
            val observed = metric.observedAtEpochMillis?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
            }
            "$value · 이전 측정${observed?.let { " $it" }.orEmpty()}"
        }
        else -> "확인 불가"
    }
}
