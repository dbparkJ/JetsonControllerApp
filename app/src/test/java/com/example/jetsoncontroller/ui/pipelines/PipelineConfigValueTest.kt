package com.example.jetsoncontroller.ui.pipelines

import com.example.jetsoncontroller.model.PipelineConfigValueType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineConfigValueTest {
    @Test
    fun validatesValuesUsingTheServerDeclaredType() {
        assertTrue(configFieldValueValid(PipelineConfigValueType.BOOLEAN, "true"))
        assertFalse(configFieldValueValid(PipelineConfigValueType.BOOLEAN, "yes"))
        assertTrue(configFieldValueValid(PipelineConfigValueType.INTEGER, "-12"))
        assertFalse(configFieldValueValid(PipelineConfigValueType.INTEGER, "1.2"))
        assertTrue(configFieldValueValid(PipelineConfigValueType.DECIMAL, "1.25"))
        assertFalse(configFieldValueValid(PipelineConfigValueType.DECIMAL, "NaN"))
        assertTrue(configFieldValueValid(PipelineConfigValueType.STRING, "front camera"))
    }
}
