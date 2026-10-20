package com.example.jetsoncontroller.ui.pipelines

import com.example.jetsoncontroller.ui.model.Formats
import java.math.BigDecimal

internal fun policyUnitsToBytes(value: String, multiplier: Long): String? = runCatching {
    require(multiplier > 0)
    BigDecimal(value).multiply(BigDecimal.valueOf(multiplier)).longValueExact().takeIf { it >= 0 }?.toString()
}.getOrNull()

internal fun policyBytesToUnits(value: String, divisor: Long): String = runCatching {
    BigDecimal(value).divide(BigDecimal.valueOf(divisor)).stripTrailingZeros().toPlainString()
}.getOrDefault("")

internal data class PolicySizeInput(val originalBytes: String, val value: String, val unit: String, val multiplier: Long) {
    fun bytesForEdit(text: String): String? {
        val converted = policyUnitsToBytes(text, multiplier) ?: return null
        // Formats rounds for readability. An unchanged field must retain every stored byte.
        return if (value.toBigDecimalOrNull()?.compareTo(text.toBigDecimalOrNull() ?: return null) == 0)
            originalBytes else converted
    }
}

internal fun policySizeInput(bytes: String): PolicySizeInput {
    val stored = bytes.toLongOrNull()?.takeIf { it >= 0 } ?: return PolicySizeInput(bytes, "", "B", 1)
    val (value, unit) = Formats.size(stored).split('\u00a0')
    val multiplier = when (unit) {
        "KB" -> 1_000L
        "MB" -> 1_000_000L
        "GB" -> 1_000_000_000L
        "TB" -> 1_000_000_000_000L
        else -> 1L
    }
    return PolicySizeInput(bytes, value, unit, multiplier)
}

internal data class PolicySizeEdit(val input: PolicySizeInput, val text: String = input.value, val sourceBytes: String = input.originalBytes) {
    val bytes: String? get() = input.bytesForEdit(text)
    fun edit(text: String): PolicySizeEdit = copy(text = text, sourceBytes = input.bytesForEdit(text) ?: sourceBytes)
}
