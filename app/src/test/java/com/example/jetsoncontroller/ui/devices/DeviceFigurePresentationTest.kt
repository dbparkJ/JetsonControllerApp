package com.example.jetsoncontroller.ui.devices

import com.example.jetsoncontroller.data.devices.DeviceSnapshot
import com.example.jetsoncontroller.model.JetsonStatus
import com.example.jetsoncontroller.model.MetricValidity
import com.example.jetsoncontroller.ui.components.geo.figurePresentation
import org.junit.Assert.*
import org.junit.Test

class DeviceFigurePresentationTest {
    @Test fun `photo card separates storage temperature CPU and GPU from one reading`() {
        val live = JetsonStatus(storageTotalBytes = 1_000_000_000_000L, storageAvailableBytes = 512_000_000_000L,
            temperatureC = 49f, cpuPercent = 0, gpuPercent = 48)
        val figures = devicePhotoFigures(live, null)
        assertEquals(listOf("저장 여유", "온도", "CPU", "GPU"), figures.map { it.label })
        assertEquals("512\u00a0GB", figures[0].value)
        assertEquals("49°C", figures[1].value)
        assertEquals("0", figures[2].value)
        assertEquals("48", figures[3].value)
        assertEquals(listOf("%", "%"), figures.drop(2).map { it.unit })
        assertTrue(figures.none { it.stale })
    }

    @Test fun `invalid live CPU and GPU cannot borrow cached photo card values`() {
        val live = JetsonStatus(cpuPercent = 32, gpuPercent = 48,
            metricValidity = mapOf("cpuPercent" to MetricValidity("unavailable"),
                "gpuPercent" to MetricValidity("unavailable")))
        val stored = DeviceSnapshot("A", "GEO-X-1", 10L, cpuPercent = 21, gpuPercent = 35)
        val current = devicePhotoFigures(live, stored).drop(2)
        assertTrue(current.all { it.value == null && !it.stale })
        val cached = devicePhotoFigures(null, stored).drop(2)
        assertEquals(listOf("21", "35"), cached.map { it.value })
        assertTrue(cached.all { it.stale })
        assertEquals("마지막 값 기준", figurePresentation(cached.first()).detail)
    }

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

    @Test fun `memory keeps the used amount in the figure and the total in its detail`() {
        val status = JetsonStatus(ramUsedMb = 2_048, ramTotalMb = 15_462)
        val memory = deviceFigureItems(status, null).last()
        assertEquals("2.0\u00a0GB", memory.value)
        assertEquals("전체 15.1\u00a0GB", memory.detail)
        assertEquals(memory.detail, figurePresentation(memory).detail)
        assertEquals("0.0\u00a0GB", deviceFigureItems(status.copy(ramUsedMb = 0), null).last().value)
    }

    @Test fun `cached memory stays stale and invalid live memory never borrows the cache`() {
        val stored = DeviceSnapshot("A", "GEO-X-1", 10L, memoryUsedMb = 2_048, memoryTotalMb = 15_462)
        val cached = deviceFigureItems(null, stored).last()
        assertEquals("2.0\u00a0GB", cached.value)
        assertEquals("전체 15.1\u00a0GB", cached.detail)
        assertEquals("마지막 값 기준", figurePresentation(cached).detail)
        assertEquals("전체 15.1\u00a0GB", deviceFigurePresentation(cached).detail)
        assertEquals("마지막 값 기준", deviceFigurePresentation(cached).staleDetail)
        val invalid = JetsonStatus(ramUsedMb = 2_048, ramTotalMb = 15_462,
            metricValidity = mapOf("ramUsedMb" to MetricValidity("unavailable")))
        val live = deviceFigureItems(invalid, stored).last()
        assertNull(live.value)
        assertNull(live.detail)
        assertFalse(live.stale)
        assertTrue(deviceFigurePresentation(live).unknown)
        assertNull(deviceFigurePresentation(live).staleDetail)
    }
}
