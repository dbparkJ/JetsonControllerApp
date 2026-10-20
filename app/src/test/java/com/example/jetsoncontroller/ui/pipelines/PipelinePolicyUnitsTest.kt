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

    @Test fun choosesSensibleSiUnitsWithoutLongFractions() {
        val binaryGb = policySizeInput("1073741824")
        assertEquals("1.1", binaryGb.value)
        assertEquals("GB", binaryGb.unit)
        assertEquals("1.0", policySizeInput("1000000").value)
        assertEquals("MB", policySizeInput("1000000").unit)
        assertEquals("1", policySizeInput("1").value)
        assertEquals("B", policySizeInput("1").unit)
        assertEquals("0", policySizeInput("0").value)
        assertEquals("B", policySizeInput("0").unit)
    }

    @Test fun unchangedRoundedValuesKeepEveryByte() {
        for (bytes in listOf("1073741824", "999600000", "1234", "0", "1", Long.MAX_VALUE.toString())) {
            val field = policySizeInput(bytes)
            assertEquals(bytes, field.bytesForEdit(field.value))
        }
        assertEquals("1073741824", policySizeInput("1073741824").bytesForEdit("1.10"))
    }

    @Test fun editsUseTheDisplayedUnitAndRejectFractionalBytesOrOverflow() {
        val field = policySizeInput("1073741824")
        assertEquals("1500000000", field.bytesForEdit("1.5"))
        assertNull(field.bytesForEdit("-1"))
        assertNull(field.bytesForEdit(""))
        assertNull(field.bytesForEdit("NaN"))
        assertNull(field.bytesForEdit("0.0000000001"))
        assertNull(field.bytesForEdit("9223372036854775808"))
        assertNull(policySizeInput("1").bytesForEdit("1.5"))
        assertEquals("10", policySizeInput("").bytesForEdit("10"))
    }

    @Test fun localEditsKeepTheTypedValueAndTrackTheExactDraft() {
        val edit = PolicySizeEdit(policySizeInput("1073741824")).edit("1.25")
        assertEquals("1.25", edit.text)
        assertEquals("1250000000", edit.sourceBytes)
        assertEquals("1250000000", edit.bytes)
        val invalid = edit.edit("")
        assertNull(invalid.bytes)
        assertEquals("1250000000", invalid.sourceBytes)
    }
}
