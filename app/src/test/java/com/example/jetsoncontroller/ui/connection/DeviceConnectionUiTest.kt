package com.example.jetsoncontroller.ui.connection

import com.example.jetsoncontroller.data.transport.TransportType
import com.example.jetsoncontroller.ui.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceConnectionUiTest {
    @Test
    fun `online means a transport is held, and only LAN or Wi-Fi Direct grants full control`() {
        assertFalse(deviceIsOnline(null))
        assertTrue(deviceIsOnline(TransportType.BLE))
        assertTrue(hasFullControl(TransportType.LAN))
        assertTrue(hasFullControl(TransportType.WIFI_DIRECT))
        assertFalse(hasFullControl(TransportType.BLE))
        assertFalse(hasFullControl(null))
    }

    @Test
    fun `the vocabulary is the five fixed words`() {
        assertEquals(
            setOf("연결됨", "연결 가능", "오프라인", "응답 없음", "확인 중"),
            DeviceConnectionState.entries.map { it.label }.toSet()
        )
    }

    @Test
    fun `a LAN link is connected and a BLE link is connected with a warning`() {
        val lan = deviceConnectionUi(isOnline = true, transportType = TransportType.LAN)
        assertEquals(DeviceConnectionState.CONNECTED, lan.state)
        assertEquals(Tone.SUCCESS, lan.tone)

        val ble = deviceConnectionUi(isOnline = true, transportType = TransportType.BLE)
        assertEquals("연결됨", ble.label)
        assertEquals(Tone.WARNING, ble.tone)
    }

    @Test
    fun `an offline device shows its last response, or says it was never connected`() {
        val withHistory = deviceConnectionUi(isOnline = false, transportType = null, lastSeenText = "10월 15일 10:39")
        assertEquals(DeviceConnectionState.OFFLINE, withHistory.state)
        assertEquals("마지막 응답 10월 15일 10:39", withHistory.detail)

        assertEquals(
            "이 휴대폰과 연결한 기록이 없습니다.",
            deviceConnectionUi(isOnline = false, transportType = null).detail
        )
    }

    @Test
    fun `offline history uses the supplied local device word`() {
        assertEquals(
            "이 태블릿과 연결한 기록이 없습니다.",
            deviceConnectionUi(isOnline = false, transportType = null, localDeviceWord = "태블릿").detail
        )
    }

    @Test
    fun `a running attempt outranks discovery, and a failed attempt reads as no response`() {
        assertEquals(
            DeviceConnectionState.CHECKING,
            deviceConnectionUi(isOnline = false, transportType = null, checking = true, available = true).state
        )
        assertEquals(
            DeviceConnectionState.AVAILABLE,
            deviceConnectionUi(isOnline = false, transportType = null, available = true).state
        )
        assertEquals(
            DeviceConnectionState.NO_RESPONSE,
            deviceConnectionUi(isOnline = false, transportType = null, connectFailed = true).state
        )
    }

    @Test
    fun `no connection text uses the forbidden words of the device console`() {
        val all = listOf(
            deviceConnectionUi(true, TransportType.LAN),
            deviceConnectionUi(true, TransportType.BLE),
            deviceConnectionUi(true, TransportType.WIFI_DIRECT),
            deviceConnectionUi(false, null, checking = true),
            deviceConnectionUi(false, null, available = true),
            deviceConnectionUi(false, null, connectFailed = true),
            deviceConnectionUi(false, null, lastSeenText = "방금")
        )
        all.forEach { ui ->
            listOf(ui.label, ui.detail).forEach { text ->
                assertFalse("장비 must not appear in: $text", text.contains("장비"))
                assertFalse("확인 필요 must not appear in: $text", text.contains("확인 필요"))
            }
        }
    }
}
