package com.example.jetsoncontroller.model

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/** Strict adapters for the optional GNSS diagnostics; legacy fields keep their existing parsing. */
internal abstract class GnssDiagnosticAdapter<T> : TypeAdapter<T>() {
    final override fun read(reader: JsonReader): T = parse(JsonParser.parseReader(reader))

    abstract fun parse(value: JsonElement): T

    final override fun write(writer: JsonWriter, value: T) {
        when (value) {
            null -> writer.nullValue()
            is String -> writer.value(value)
            is Number -> writer.value(value)
            is List<*> -> {
                writer.beginArray()
                value.forEach {
                    when (it) {
                        is String -> writer.value(it)
                        is Number -> writer.value(it)
                        else -> writer.nullValue()
                    }
                }
                writer.endArray()
            }
            else -> writer.nullValue()
        }
    }
}

private fun JsonElement.strictLong(): Long? = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    ?.asString?.takeIf { INTEGER.matches(it) }?.toLongOrNull()

private val INTEGER = Regex("-?(0|[1-9][0-9]*)")

internal class NtripSelectionAdapter : GnssDiagnosticAdapter<String?>() {
    override fun parse(value: JsonElement): String? =
        value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?.takeIf { it == "client" || it == "caster" }
}

internal class NtripErrorAdapter : GnssDiagnosticAdapter<String?>() {
    override fun parse(value: JsonElement): String? =
        value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?.filter { it.code >= 32 }?.trim()?.take(256)?.takeIf { it.isNotEmpty() }
}

internal class GnssDiagnosticTimestampAdapter : GnssDiagnosticAdapter<Long?>() {
    override fun parse(value: JsonElement): Long? = value.strictLong()
}

internal class RtcmMessageTypesAdapter : GnssDiagnosticAdapter<List<Int>>() {
    override fun parse(value: JsonElement): List<Int> = if (value.isJsonArray) {
        value.asJsonArray.mapNotNull { it.strictLong()?.takeIf { number -> number in 1L..4095L }?.toInt() }
            .distinct().sorted().take(32)
    } else emptyList()
}

internal class RtcmSignalsAdapter : GnssDiagnosticAdapter<List<String>>() {
    override fun parse(value: JsonElement): List<String> = if (value.isJsonArray) {
        value.asJsonArray.mapNotNull {
            it.takeIf { item -> item.isJsonPrimitive && item.asJsonPrimitive.isString }?.asString
                ?.takeIf(RTCM_SIGNAL::matches)
        }.distinct().sorted().take(48)
    } else emptyList()
}

private val RTCM_SIGNAL = Regex("(GPS|GLO|GAL|SBS|QZS|BDS|IRN) ([1-9][A-Z]|\\?[0-9]{1,2})")

internal class RtcmBaseStationIdAdapter : GnssDiagnosticAdapter<Int?>() {
    override fun parse(value: JsonElement): Int? = value.strictLong()?.takeIf { it in 0L..4095L }?.toInt()
}

internal class RtcmBaseDistanceAdapter : GnssDiagnosticAdapter<Double?>() {
    override fun parse(value: JsonElement): Double? =
        value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            ?.takeIf { it.isFinite() && it >= 0.0 }
}
