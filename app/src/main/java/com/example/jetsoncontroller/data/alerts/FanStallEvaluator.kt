package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.FanStatus

internal enum class FanStallDecision { NONE, STALLED, RECOVERED }

/** One instance per connection. Only newly completed API reads may be fresh samples. */
internal class FanStallEvaluator {
    private var zeroSamples = 0

    fun evaluate(status: FanStatus?, fresh: Boolean = true): FanStallDecision {
        if (!fresh || status == null || !status.available || status.rpm == null || status.rpm < 0) {
            reset()
            return FanStallDecision.NONE
        }
        if (status.rpm > 0) {
            reset()
            return FanStallDecision.RECOVERED
        }
        val knownMode = status.mode.equals("auto", true) || status.mode.equals("manual", true)
        val outputKnownPositive = (status.percent != null || status.pwm != null) &&
            (status.percent == null || status.percent in 1..100) &&
            (status.pwm == null || status.pwm > 0)
        if (!knownMode || !outputKnownPositive) {
            reset()
            return FanStallDecision.NONE
        }
        zeroSamples = (zeroSamples + 1).coerceAtMost(2)
        return if (zeroSamples == 2) FanStallDecision.STALLED else FanStallDecision.NONE
    }

    fun reset() { zeroSamples = 0 }
}
