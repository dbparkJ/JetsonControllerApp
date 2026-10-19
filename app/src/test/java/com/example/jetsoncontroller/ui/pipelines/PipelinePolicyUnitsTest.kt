package com.example.jetsoncontroller.ui.pipelines

import org.junit.Assert.*
import org.junit.Test

class PipelinePolicyUnitsTest {
    @Test fun displayedGbAndMbRoundTripToExactBytesIncludingZero() {
        assertEquals("1500000000", policyUnitsToBytes("1.5", 1_000_000_000))
        assertEquals("1.5", policyBytesToUnits("1500000000", 1_000_000_000))
        assertEquals("896000000", policyUnitsToBytes("896", 1_000_000))
        assertEquals("0", policyUnitsToBytes("0", 1_000_000))
        assertNull(policyUnitsToBytes("-1", 1_000_000))
        assertNull(policyUnitsToBytes("", 1_000_000))
        assertNull(policyUnitsToBytes("9223372036854775808", 1_000_000))
        assertNull(policyUnitsToBytes("0.0000001", 1_000_000))
    }
}
