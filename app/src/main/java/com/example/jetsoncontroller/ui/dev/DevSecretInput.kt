package com.example.jetsoncontroller.ui.dev

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KProperty

/** Ephemeral input only. Never use this object with rememberSaveable or a state handle. */
internal class DevSecretInput {
    private var value by mutableStateOf("")
    operator fun getValue(owner: Any?, property: KProperty<*>): String = value
    operator fun setValue(owner: Any?, property: KProperty<*>, entered: String) { value = entered }
    fun consume(): String = value.also { value = "" }
    override fun toString(): String = "DevSecretInput([redacted])"
}
