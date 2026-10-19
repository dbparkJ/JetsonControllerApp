package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MetricValidity
import com.example.jetsoncontroller.ui.components.geo.figurePresentation
import org.junit.Assert.*
import org.junit.Test

class DeviceFigurePresentationTest {
    @Test fun `storage shows available bytes first and retains a valid zero`() {
        val status = JetsonStatus(storageTotalBytes = 1_000_000_000_000L, storageAvailableBytes = 512_000_000_000L,
            storagePercent = 43, cpuPercent = 0, gpuPercent = 0)
        val storage = deviceFigureItems(status, null)[1]
        assertEquals("512\u00a0GB", storage.value)
        assertEquals("57% 남음", storage.detail)
        assertEquals("0\u00a0B", deviceFigureItems(status.copy(storageAvailableBytes = 0), null)[1].value)
    }

    @Test fun `offline valid numbers are stale and missing values stay unknown`() {
        val stored = DeviceSnapshot("A", "GEO-X-1", 10L, temperatureC = 54f,
            storageAvailableBytes = 512_000_000_000L, storagePercent = 43)
        val figures = deviceFigureItems(null, stored)
        assertEquals("54°C", figures[0].value)
        assertEquals("마지막 값 기준", figurePresentation(figures[0]).detail)
        assertNull(figures[2].value)
        assertEquals("미확인", figurePresentation(figures[2]).value)
    }

    @Test fun `invalid live storage cannot fall back to the saved snapshot`() {
        val live = JetsonStatus(storageTotalBytes = 100L, storageAvailableBytes = 90L,
            metricValidity = mapOf("storageAvailableBytes" to MetricValidity("unavailable")))
        val stored = DeviceSnapshot("A", "GEO-X-1", 10L, storageAvailableBytes = 512_000_000_000L)
        assertNull(deviceFigureItems(live, stored)[1].value)
        assertFalse(deviceFigureItems(live, stored)[1].stale)
    }
}
