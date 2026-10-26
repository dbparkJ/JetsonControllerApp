package com.example.jetsoncontroller.data.alerts

import com.example.jetsoncontroller.model.FanStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FanStallEvaluatorTest {
    private val stopped = FanStatus(true, "auto", percent = 62, rpm = 0, pwm = 158)

    @Test fun `two fresh driven zero samples trigger and positive rpm recovers`() {
        val evaluator = FanStallEvaluator()
        assertEquals(FanStallDecision.NONE, evaluator.evaluate(stopped))
        assertEquals(FanStallDecision.STALLED, evaluator.evaluate(stopped))
        assertEquals(FanStallDecision.RECOVERED, evaluator.evaluate(stopped.copy(rpm = 1200)))
        assertEquals(FanStallDecision.NONE, evaluator.evaluate(stopped))
        assertEquals(FanStallDecision.STALLED, evaluator.evaluate(stopped))
    }

    @Test fun `unknown unsupported failed invalid and idle samples break the sequence`() {
        val invalid = listOf(
            null, stopped.copy(available = false), stopped.copy(rpm = null),
            stopped.copy(rpm = -1), stopped.copy(mode = "unknown"),
            stopped.copy(percent = null, pwm = null), stopped.copy(percent = 0),
            stopped.copy(pwm = 0), stopped.copy(percent = -1), stopped.copy(pwm = -1),
            stopped.copy(percent = 101), stopped.copy(percent = 0, pwm = 0)
        )
        invalid.forEach { sample ->
            val evaluator = FanStallEvaluator()
            evaluator.evaluate(stopped)
            assertEquals("invalid sample $sample", FanStallDecision.NONE, evaluator.evaluate(sample))
            assertEquals("sequence reset for $sample", FanStallDecision.NONE, evaluator.evaluate(stopped))
        }
    }

    @Test fun `cached zeros and cached recovery are ignored`() {
        val evaluator = FanStallEvaluator()
        evaluator.evaluate(stopped)
        assertEquals(FanStallDecision.NONE, evaluator.evaluate(stopped, fresh = false))
        assertEquals(FanStallDecision.NONE, evaluator.evaluate(stopped))
        assertEquals(FanStallDecision.NONE, evaluator.evaluate(stopped.copy(rpm = 800), fresh = false))
        assertEquals(FanStallDecision.NONE, evaluator.evaluate(stopped))
    }

    @Test fun `manual drive and pwm only drive are supported`() {
        listOf(stopped.copy(mode = "MANUAL", percent = 100), stopped.copy(percent = null)).forEach {
            val evaluator = FanStallEvaluator()
            assertEquals(FanStallDecision.NONE, evaluator.evaluate(it))
            assertEquals(FanStallDecision.STALLED, evaluator.evaluate(it))
        }
    }

    @Test fun `fan title names the affected device`() {
        assertEquals("GEO-X-1 냉각 팬이 멈췄습니다", fanStallAlertTitle("GEO-X-1"))
    }
}
