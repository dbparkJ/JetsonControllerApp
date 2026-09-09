package com.example.jetsoncontroller.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode(val label: String) { SYSTEM("시스템 따름"), LIGHT("밝게"), DARK("어둡게") }

@Composable
fun rememberThemePreference(): Pair<ThemeMode, (ThemeMode) -> Unit> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    fun read() = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.SYSTEM
    var mode by remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme") mode = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode to { next -> prefs.edit().putString("theme", next.name).apply(); mode = next }
}
