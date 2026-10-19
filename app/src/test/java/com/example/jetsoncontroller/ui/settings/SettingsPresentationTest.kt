package com.example.jetsoncontroller.ui.settings

import com.example.jetsoncontroller.data.alerts.AlertSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPresentationTest {
    @Test
    fun settingsSummaryCountsTheSixSwitches() {
        assertEquals("6개 중 6개", alertSettingsSummary(AlertSettings()))
        assertEquals(
            "6개 중 5개",
            alertSettingsSummary(AlertSettings(uploadStartedEnabled = false))
        )
        assertEquals(
            "6개 중 0개",
            alertSettingsSummary(
                AlertSettings(
                    storageEnabled = false,
                    temperatureEnabled = false,
                    pipelineStartedEnabled = false,
                    pipelineFailedEnabled = false,
                    uploadStartedEnabled = false,
                    uploadEndedEnabled = false
                )
            )
        )
    }
}
