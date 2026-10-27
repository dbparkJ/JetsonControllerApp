package com.example.jetsoncontroller.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/** R3 §3.10. Numeric readings change immediately; timings apply to state changes. */
object GeoMotion {
    const val fast = 120
    const val normal = 180
    const val slow = 240
    const val loaderCycle = 2400
    const val acceptedHaptic = HapticFeedbackConstants.CONFIRM
    const val zoneExitHaptic = HapticFeedbackConstants.REJECT
    const val qrReadHaptic = HapticFeedbackConstants.LONG_PRESS
    val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val screenShift = 24.dp

    fun duration(milliseconds: Int, reducedMotion: Boolean): Int = if (reducedMotion) 0 else milliseconds

    fun <T> transition(milliseconds: Int = normal, reducedMotion: Boolean = false) =
        tween<T>(durationMillis = duration(milliseconds, reducedMotion), easing = easing)
}

internal val LocalGeoReducedMotion = staticCompositionLocalOf<Boolean?> { null }

/** Observe once per theme and share removal of motion with every interactive child. */
@Composable
fun geoReducedMotion(): Boolean {
    LocalGeoReducedMotion.current?.let { return it }
    if (LocalView.current.isInEditMode) return true
    val resolver = LocalContext.current.contentResolver
    fun readScale() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(readScale()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readScale()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = readScale()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
