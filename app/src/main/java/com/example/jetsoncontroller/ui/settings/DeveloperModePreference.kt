package com.example.jetsoncontroller.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val DEVELOPER_PREFERENCES = "geo_developer"
private const val DEVELOPER_ENABLED = "enabled"

/** App-scoped, observable developer-mode preference. */
@Stable
class DeveloperModePreference internal constructor(
    private val preferences: SharedPreferences
) {
    var enabled by mutableStateOf(preferences.getBoolean(DEVELOPER_ENABLED, false))
        private set

    fun updateEnabled(value: Boolean) {
        enabled = value
        preferences.edit().putBoolean(DEVELOPER_ENABLED, value).apply()
    }

    internal fun refresh() {
        enabled = preferences.getBoolean(DEVELOPER_ENABLED, false)
    }
}

@Composable
fun rememberDeveloperModePreference(): DeveloperModePreference {
    val context = LocalContext.current.applicationContext
    val preference = remember(context) {
        DeveloperModePreference(
            context.getSharedPreferences(DEVELOPER_PREFERENCES, Context.MODE_PRIVATE)
        )
    }
    DisposableEffect(preference) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DEVELOPER_ENABLED) preference.refresh()
        }
        val preferences = context.getSharedPreferences(DEVELOPER_PREFERENCES, Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return preference
}
