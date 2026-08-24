package com.example.jetsoncontroller.ui.sensors

import com.example.jetsoncontroller.data.location.MobileLocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileLocationPresentationTest {
    private val freshFix = MobileLocationFix(
        latitude = 37.5,
        longitude = 127.0,
        accuracyM = 2.5f,
        timestampEpochMillis = 10_000L,
        elapsedRealtimeNanos = 10_000_000_000L
    )

    @Test
    fun distinguishesEveryMobileLocationFailureMode() {
        assertEquals(
            MobileLocationAvailability.OFF,
            mobileLocationAvailability(MobileLocationUiState(trackingEnabled = false))
        )
        assertEquals(
            MobileLocationAvailability.PERMISSION_REQUIRED,
            mobileLocationAvailability(
                MobileLocationUiState(
                    trackingEnabled = true,
                    permissionGranted = false
                )
            )
        )
        assertEquals(
            MobileLocationAvailability.OFF,
            mobileLocationAvailability(
                MobileLocationUiState(
                    trackingEnabled = true,
                    permissionGranted = true,
                    providerAvailable = false
                )
            )
        )
        assertEquals(
            MobileLocationAvailability.PROVIDER_DISABLED,
            mobileLocationAvailability(
                MobileLocationUiState(
                    trackingEnabled = true,
                    permissionGranted = true,
                    providerAvailable = true,
                    providerEnabled = false
                )
            )
        )
        assertEquals(
            MobileLocationAvailability.NO_FIX,
            mobileLocationAvailability(activeState(fix = null))
        )
        assertEquals(
            MobileLocationAvailability.STALE,
            mobileLocationAvailability(
                activeState(fix = freshFix, nowElapsedRealtimeNanos = 15_001_000_000L)
            )
        )
    }

    @Test
    fun reportsARecentValidFixAsActive() {
        assertEquals(
            MobileLocationAvailability.ACTIVE,
            mobileLocationAvailability(
                activeState(fix = freshFix, nowElapsedRealtimeNanos = 15_000_000_000L)
            )
        )
        assertEquals("37.5000000, 127.0000000", mobileCoordinateText(freshFix))
    }

    @Test
    fun rejectsNonFiniteOrOutOfRangeMobileCoordinates() {
        assertTrue(freshFix.hasValidCoordinates())
        assertFalse(freshFix.copy(latitude = Double.NaN).hasValidCoordinates())
        assertFalse(freshFix.copy(longitude = 181.0).hasValidCoordinates())
    }

    @Test
    fun futureOrOutOfOrderMonotonicTimesCannotLookFresh() {
        assertEquals(
            MobileLocationAvailability.STALE,
            mobileLocationAvailability(
                activeState(fix = freshFix, nowElapsedRealtimeNanos = 9_999_999_999L)
            )
        )
        val older = freshFix.copy(elapsedRealtimeNanos = 9_000_000_000L)
        assertEquals(freshFix, newestMobileLocationFix(freshFix, older))
        val newer = freshFix.copy(elapsedRealtimeNanos = 11_000_000_000L)
        assertEquals(newer, newestMobileLocationFix(freshFix, newer))
    }

    private fun activeState(
        fix: MobileLocationFix?,
        nowElapsedRealtimeNanos: Long = 10_000_000_000L
    ) = MobileLocationUiState(
        trackingEnabled = true,
        trackerOperational = true,
        permissionGranted = true,
        providerAvailable = true,
        providerEnabled = true,
        fix = fix,
        nowEpochMillis = 10_000L,
        nowElapsedRealtimeNanos = nowElapsedRealtimeNanos
    )
}
