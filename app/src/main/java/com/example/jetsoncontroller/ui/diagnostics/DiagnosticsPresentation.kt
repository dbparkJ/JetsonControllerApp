package com.example.jetsoncontroller.ui.diagnostics

import com.example.jetsoncontroller.ui.model.Formats
import java.time.Instant

internal fun diagnosticTime(value: Long?, now: Instant = Instant.now()): String =
    value?.let { Formats.time(Instant.ofEpochMilli(it), now) } ?: "아직 없음"

internal fun diagnosticSize(bytes: Long, limitBytes: Long): String =
    "${Formats.size(bytes)} / 최대 ${Formats.size(limitBytes)}"
