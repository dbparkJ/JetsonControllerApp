package com.example.jetsoncontroller.ui.network

import com.example.jetsoncontroller.model.WifiProvisionPhase
import com.example.jetsoncontroller.model.WifiProvisionStatus
import com.example.jetsoncontroller.model.phase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiProvisionPollingTest {
    @Test
    fun `polls connecting until connected terminal state`() = runBlocking {
        val statuses = mutableListOf(
            WifiProvisionStatus("CONNECTING", "FieldNet"),
            WifiProvisionStatus("CONNECTING", "FieldNet"),
            WifiProvisionStatus("CONNECTED", "FieldNet", "Wi-Fi connection completed")
        )
        var pauses = 0

        val result = awaitWifiProvisionCompletion(
            expectedSsid = "FieldNet",
            maxAttempts = 5,
            pollIntervalMillis = 1,
            fetchStatus = { Result.success(statuses.removeAt(0)) },
            pause = { pauses += 1 }
        )

        assertTrue(result.isSuccess)
        assertEquals(WifiProvisionPhase.CONNECTED, result.getOrThrow().phase())
        assertEquals(2, pauses)
    }

    @Test
    fun `failed terminal state is returned for the UI to report`() = runBlocking {
        val result = awaitWifiProvisionCompletion(
            expectedSsid = "FieldNet",
            maxAttempts = 2,
            fetchStatus = {
                Result.success(
                    WifiProvisionStatus(
                        state = "FAILED",
                        ssid = "FieldNet",
                        message = "NetworkManager rejected the connection"
                    )
                )
            },
            pause = {}
        )

        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertEquals(WifiProvisionPhase.FAILED, status.phase())
        assertTrue(wifiProvisionFailedMessage(status, "FieldNet").contains("연결 실패"))
        assertTrue(wifiProvisionFailedMessage(status, "FieldNet").contains("비밀번호"))
    }

    @Test
    fun `transient status error can recover before its limit`() = runBlocking {
        var calls = 0
        val result = awaitWifiProvisionCompletion(
            expectedSsid = "FieldNet",
            maxAttempts = 3,
            maxConsecutiveErrors = 2,
            fetchStatus = {
                calls += 1
                if (calls == 1) {
                    Result.failure(IllegalStateException("temporary"))
                } else {
                    Result.success(WifiProvisionStatus("CONNECTED", "FieldNet"))
                }
            },
            pause = {}
        )

        assertTrue(result.isSuccess)
        assertEquals(2, calls)
    }

    @Test
    fun `repeated status errors report an unverified accepted request`() = runBlocking {
        var calls = 0
        val result = awaitWifiProvisionCompletion(
            expectedSsid = "FieldNet",
            maxAttempts = 5,
            maxConsecutiveErrors = 2,
            fetchStatus = {
                calls += 1
                Result.failure(IllegalStateException("route lost"))
            },
            pause = {}
        )

        assertTrue(result.isFailure)
        assertEquals(2, calls)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("접수됐지만"))
    }

    @Test
    fun `connecting status times out instead of reporting success`() = runBlocking {
        val result = awaitWifiProvisionCompletion(
            expectedSsid = "FieldNet",
            maxAttempts = 3,
            fetchStatus = {
                Result.success(WifiProvisionStatus("CONNECTING", "FieldNet"))
            },
            pause = {}
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("초과"))
        assertFalse(result.isSuccess)
    }

    @Test
    fun `connected message names the confirmed network`() {
        val status = WifiProvisionStatus("CONNECTED", "FieldNet")

        assertEquals(
            "FieldNet Wi-Fi 연결에 성공했습니다.",
            wifiProvisionConnectedMessage(status, "fallback")
        )
    }

    @Test
    fun `authentication failure is translated without exposing backend detail`() {
        val message = wifiProvisionFailedMessage(
            WifiProvisionStatus(
                "FAILED",
                "FieldNet",
                "Wi-Fi authentication failed; check the password"
            ),
            "fallback"
        )

        assertTrue(message.contains("비밀번호"))
        assertFalse(message.contains("authentication"))
    }

    @Test
    fun `missing network explains that Jetson could not scan it`() {
        val message = wifiProvisionFailedMessage(
            WifiProvisionStatus(
                "FAILED",
                "FieldNet",
                "Wi-Fi network was not found after scanning"
            ),
            "fallback"
        )

        assertTrue(message.contains("찾지 못했습니다"))
        assertTrue(message.startsWith("FieldNet Wi-Fi 연결 실패"))
    }
}
