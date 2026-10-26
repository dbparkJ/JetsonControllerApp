package com.example.jetsoncontroller.ui.components.geo

import androidx.compose.runtime.saveable.Saver
import com.google.gson.Gson

/** Saves an immutable confirmation snapshot; callers still validate it before any action. */
internal fun <T : Any> geoSnapshotSaver(type: Class<T>): Saver<T?, String> = Saver(
    save = { it?.let { snapshot -> Gson().toJson(snapshot) } },
    restore = { json -> runCatching { Gson().fromJson(json, type) }.getOrNull() }
)
