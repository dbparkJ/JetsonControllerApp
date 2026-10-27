package com.example.jetsoncontroller.ui.field

/** Keep the complete elapsed value and capacity visible at the current measured text size. */
internal fun runClockStacks(availableDp: Float, fontScale: Float, clockDp: Float, capacityDp: Float): Boolean =
    fontScale >= 1.3f || clockDp + capacityDp + 16f > availableDp
