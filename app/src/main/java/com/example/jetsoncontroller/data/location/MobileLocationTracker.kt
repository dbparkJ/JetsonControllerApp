package com.example.jetsoncontroller.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

data class MobileLocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null,
    val provider: String? = null,
    val timestampEpochMillis: Long,
    val elapsedRealtimeNanos: Long
) {
    fun hasValidCoordinates(): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class MobileLocationEnvironment(
    val permissionGranted: Boolean,
    val providerAvailable: Boolean,
    val providerEnabled: Boolean
)

data class MobileLocationCallbacks(
    val onEnvironmentChanged: (MobileLocationEnvironment) -> Unit,
    val onLocation: (MobileLocationFix) -> Unit,
    val onError: (String) -> Unit
)

interface MobileLocationTracker {
    fun start(callbacks: MobileLocationCallbacks)
    fun stop()
}

/**
 * Lightweight platform tracker used only while the GNSS map is visible.
 *
 * Both GPS and network/fused providers are observed so the mobile marker can
 * still obtain a fix indoors. Permission and provider changes are surfaced
 * separately instead of being collapsed into an empty coordinate.
 */
class AndroidMobileLocationTracker(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeClock: () -> Long = android.os.SystemClock::elapsedRealtimeNanos
) : MobileLocationTracker {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var callbacks: MobileLocationCallbacks? = null
    private var receiverRegistered = false

    private val candidateProviders: List<String>
        get() = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.FUSED_PROVIDER
        ).distinct()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            location.toMobileLocationFix(clock, elapsedRealtimeClock)?.let { fix ->
                callbacks?.onLocation?.invoke(fix)
            }
        }

        override fun onProviderEnabled(provider: String) {
            refreshProviderRegistrations()
        }

        override fun onProviderDisabled(provider: String) {
            refreshProviderRegistrations()
        }

        @Deprecated("Deprecated by the Android framework")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val providerChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshProviderRegistrations()
        }
    }

    override fun start(callbacks: MobileLocationCallbacks) {
        stop()
        this.callbacks = callbacks
        registerProviderReceiver()
        refreshProviderRegistrations()
    }

    override fun stop() {
        runCatching { locationManager?.removeUpdates(locationListener) }
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(providerChangeReceiver) }
            receiverRegistered = false
        }
        callbacks = null
    }

    private fun registerProviderReceiver() {
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(
                    providerChangeReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(providerChangeReceiver, filter)
            }
        }.onSuccess {
            receiverRegistered = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshProviderRegistrations() {
        val manager = locationManager
        val environment = currentEnvironment(manager)
        callbacks?.onEnvironmentChanged?.invoke(environment)
        runCatching { manager?.removeUpdates(locationListener) }

        if (manager == null || !environment.permissionGranted || !environment.providerEnabled) {
            return
        }

        val enabledProviders = candidateProviders.filter { provider ->
            manager.allProviders.contains(provider) &&
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        val latest = enabledProviders.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull(Location::getElapsedRealtimeNanos)
        latest?.toMobileLocationFix(clock, elapsedRealtimeClock)?.let { fix ->
            callbacks?.onLocation?.invoke(fix)
        }

        var registeredProviderCount = 0
        var lastFailure: Throwable? = null
        enabledProviders.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    LOCATION_UPDATE_INTERVAL_MS,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }.onSuccess {
                registeredProviderCount += 1
            }.onFailure { error ->
                lastFailure = error
            }
        }
        if (enabledProviders.isNotEmpty() && registeredProviderCount == 0) {
            callbacks?.onError?.invoke(
                lastFailure?.message ?: "모바일 위치 추적을 시작하지 못했습니다."
            )
        }
    }

    private fun currentEnvironment(manager: LocationManager?): MobileLocationEnvironment {
        val permissionGranted = hasLocationPermission()
        val availableProviders = manager?.allProviders.orEmpty()
        val providerAvailable = candidateProviders.any(availableProviders::contains)
        val providerEnabled = manager != null &&
            runCatching { manager.isLocationEnabled }.getOrDefault(false) &&
            candidateProviders.any { provider ->
                availableProviders.contains(provider) &&
                    runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }
        return MobileLocationEnvironment(
            permissionGranted = permissionGranted,
            providerAvailable = providerAvailable,
            providerEnabled = providerEnabled
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
}

private fun Location.toMobileLocationFix(
    clock: () -> Long,
    elapsedRealtimeClock: () -> Long
): MobileLocationFix? {
    val observedAt = time.takeIf { it > 0L } ?: clock()
    val observedElapsedRealtime = elapsedRealtimeNanos.takeIf { it > 0L }
        ?: elapsedRealtimeClock()
    return MobileLocationFix(
        latitude = latitude,
        longitude = longitude,
        altitudeM = altitude.takeIf { hasAltitude() && it.isFinite() },
        accuracyM = accuracy.takeIf { hasAccuracy() && it.isFinite() },
        provider = provider,
        timestampEpochMillis = observedAt,
        elapsedRealtimeNanos = observedElapsedRealtime
    ).takeIf(MobileLocationFix::hasValidCoordinates)
}

internal const val LOCATION_UPDATE_INTERVAL_MS = 1_000L
