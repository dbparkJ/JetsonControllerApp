package com.example.jetsoncontroller.ui.dashboard

import com.example.jetsoncontroller.model.JetsonStatus

internal fun JetsonStatus.metricIsValid(name: String): Boolean =
    metricValidity[name]?.validity?.let { it == "valid" } ?: true

/** The memory summary contains both the used and total readings. */
internal fun JetsonStatus.metricDisplay(name: String, value: String): String =
    if (metricIsValid(name) && (name != "ramUsedMb" || metricIsValid("ramTotalMb"))) value else "미확인"
