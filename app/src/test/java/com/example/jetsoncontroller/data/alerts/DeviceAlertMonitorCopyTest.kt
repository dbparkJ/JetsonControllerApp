package com.example.jetsoncontroller.data.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAlertMonitorCopyTest {
    @Test
    fun temperatureTitlesUseWholeDegreesWhenPossibleAndKeepTheDeviceFirst() {
        assertEquals("GEO-X-1 온도가 82°C입니다", temperatureAlertTitle("GEO-X-1", 82f))
        assertEquals("GEO-X-1 온도가 81.5°C입니다", temperatureAlertTitle("GEO-X-1", 81.5f))
    }

    @Test
    fun healthAndPipelineTitlesUseTheR3OperatorCopy() {
        assertEquals("GEO-X-1 저장 공간 사용량 85%입니다", storageAlertTitle("GEO-X-1", 85))
        assertEquals(
            "GEO-X-1 데이터 수집 작업이 시작되었습니다",
            pipelineStartedAlertTitle("GEO-X-1", "데이터 수집")
        )
        assertEquals(
            "GEO-X-1 데이터 수집 작업이 오류로 끝났습니다",
            pipelineFailedAlertTitle("GEO-X-1", "데이터 수집")
        )
    }

    @Test
    fun uploadTitlesNameTheTransfer() {
        assertEquals(
            "GEO-X-1 20261019_1636 전송을 시작했습니다",
            uploadStartedAlertTitle("GEO-X-1", "20261019_1636")
        )
        assertEquals(
            "GEO-X-1 20261019_1636 전송을 마쳤습니다",
            uploadEndedAlertTitle("GEO-X-1", "20261019_1636", completed = true, cancelled = false)
        )
    }
}
